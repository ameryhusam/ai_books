package com.example.data.repository

import android.net.Uri
import com.example.data.ai.AiGenerationService
import com.example.data.local.BookEntity
import com.example.data.local.DraftEntity
import com.example.data.local.EduMiniDao
import com.example.data.local.GenerationRunEntity
import com.example.data.local.InteractivePackageEntity
import com.example.data.local.LessonEntity
import com.example.data.local.LessonProgressEntity
import com.example.data.local.QuestionEntity
import com.example.data.local.StudyDayEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.security.ProviderStorage
import com.example.data.workspace.ExportReceipt
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceExporter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.BookProgressSummary
import com.example.model.BookRef
import com.example.model.DashboardSummary
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.GeneratedResult
import com.example.model.GenerationRunRecord
import com.example.model.ImportedWorkspace
import com.example.model.InteractivePackageRecord
import com.example.model.InteractivePackageReport
import com.example.model.LearningLessonNode
import com.example.model.LearningNodeStatus
import com.example.model.LearningUnitPath
import com.example.model.LessonRef
import com.example.model.LessonSplitDraft
import com.example.model.PackageKind
import com.example.model.PdfBookInfo
import com.example.model.ProviderConfig
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.TaskType
import com.example.model.WorkspaceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EduMiniRepository(
    private val dao: EduMiniDao,
    private val providerStorage: ProviderStorage,
    private val workspaceImporter: WorkspaceImporter,
    private val aiGenerationService: AiGenerationService,
    private val pdfBookSplitter: PdfBookSplitter,
    private val interactivePackageImporter: InteractivePackageImporter
) {

    private val json = Json { ignoreUnknownKeys = true }

    val allWorkspaces: Flow<List<WorkspaceSummary>> = dao.observeAllWorkspaces().map { entities ->
        entities.map { entity ->
            WorkspaceSummary(
                workspaceId = entity.workspaceId,
                gradeKey = entity.gradeKey,
                title = entity.title,
                filesCount = entity.filesCount,
                importedAt = entity.importedAt
            )
        }
    }

    val recentRuns: Flow<List<GenerationRunRecord>> = dao.observeRecentRuns().map { runs ->
        runs.map { entity ->
            GenerationRunRecord(
                id = entity.id,
                workspaceId = entity.workspaceId,
                lessonKey = entity.lessonKey,
                task = TaskType.fromId(entity.task),
                providerLabel = entity.providerLabel,
                model = entity.model,
                status = entity.status,
                errorSummary = entity.errorSummary,
                createdAt = entity.createdAt
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWorkspace(selectedWorkspaceId: String?): Flow<ImportedWorkspace?> {
        val workspaceEntityFlow = if (selectedWorkspaceId.isNullOrBlank()) {
            dao.observeLatestWorkspace()
        } else {
            dao.observeWorkspaceById(selectedWorkspaceId)
        }
        return workspaceEntityFlow.flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                combine(
                    dao.observeBooksForWorkspace(entity.workspaceId),
                    dao.observeLessonsForWorkspace(entity.workspaceId)
                ) { bookEntities, lessonEntities ->
                    val warnings = runCatching {
                        json.decodeFromString<List<String>>(entity.warningsJson)
                    }.getOrDefault(emptyList())

                    ImportedWorkspace(
                        workspaceId = entity.workspaceId,
                        gradeKey = entity.gradeKey,
                        rootPath = entity.rootPath,
                        title = entity.title,
                        books = bookEntities.map { it.toModel() },
                        lessons = lessonEntities.map { it.toModel(entity.workspaceId) },
                        filesCount = entity.filesCount,
                        warnings = warnings,
                        importedAt = entity.importedAt
                    )
                }
            }
        }
    }

    suspend fun importWorkspaceZip(uri: Uri): ImportedWorkspace {
        val imported = workspaceImporter.importFromUri(uri)
        saveImportedWorkspace(imported)
        return imported
    }

    suspend fun ingestBookPdf(uri: Uri): PdfBookInfo = pdfBookSplitter.ingestBookPdf(uri)

    fun generateAutoSplits(totalPages: Int, pagesPerLesson: Int): List<LessonSplitDraft> {
        return pdfBookSplitter.generateAutoSplits(totalPages, pagesPerLesson)
    }

    suspend fun createWorkspaceFromPdfSplits(
        pdfInfo: PdfBookInfo,
        gradeKey: String,
        subjectKey: String,
        bookTitle: String,
        splits: List<LessonSplitDraft>
    ): ImportedWorkspace {
        val workspace = pdfBookSplitter.buildWorkspaceFromSplits(
            pdfInfo = pdfInfo,
            gradeKey = gradeKey,
            subjectKey = subjectKey,
            bookTitle = bookTitle,
            splits = splits
        )
        saveImportedWorkspace(workspace)
        return workspace
    }

    suspend fun saveImportedWorkspace(imported: ImportedWorkspace) {
        val workspaceEntity = WorkspaceEntity(
            workspaceId = imported.workspaceId,
            gradeKey = imported.gradeKey,
            rootPath = imported.rootPath,
            title = imported.title,
            filesCount = imported.filesCount,
            warningsJson = json.encodeToString(imported.warnings),
            importedAt = imported.importedAt
        )
        val bookEntities = imported.books.map { book ->
            BookEntity(
                workspaceId = imported.workspaceId,
                bookKey = book.bookKey,
                title = book.title,
                subjectKey = book.subjectKey,
                componentKey = book.componentKey,
                relativePath = book.relativePath,
                lessonsCount = book.lessonsCount
            )
        }
        val lessonEntities = imported.lessons.map { lesson ->
            LessonEntity(
                workspaceId = imported.workspaceId,
                grade = lesson.grade,
                rootPath = lesson.rootPath,
                bookKey = lesson.bookKey,
                bookTitle = lesson.bookTitle,
                subjectKey = lesson.subjectKey,
                componentKey = lesson.componentKey,
                unitKey = lesson.unitKey,
                unitTitle = lesson.unitTitle,
                lessonKey = lesson.lessonKey,
                title = lesson.title,
                partKey = lesson.partKey,
                pageRange = lesson.pageRange,
                pdfPath = lesson.pdfPath
            )
        }
        dao.saveWorkspaceGraph(workspaceEntity, bookEntities, lessonEntities)
        recordStudyActivity()
    }

    suspend fun loadProviders(): List<ProviderConfig> = providerStorage.loadProviders()

    suspend fun saveProviders(providers: List<ProviderConfig>) {
        providerStorage.saveProviders(providers)
    }

    suspend fun generateContent(
        workspaceId: String,
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        providers: List<ProviderConfig>,
        questionCount: Int,
        attachPdfPages: Boolean,
        questionStyleHint: String = "",
        difficultyHint: String = ""
    ): GeneratedResult {
        val pageImages = if (attachPdfPages) {
            aiGenerationService.extractLessonPdfPageImagesBase64(lesson.pdfPath)
        } else {
            emptyList()
        }
        return aiGenerationService.generate(
            lesson = lesson,
            task = task,
            source = source,
            extra = extra,
            providers = providers,
            questionCount = questionCount,
            pdfPageImagesBase64 = pageImages,
            questionStyleHint = questionStyleHint,
            difficultyHint = difficultyHint,
            onAttemptFinished = { providerLabel, model, success, error ->
                dao.insertGenerationRun(
                    GenerationRunEntity(
                        id = "run-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                        workspaceId = workspaceId.ifBlank { lesson.rootPath },
                        lessonKey = lesson.lessonKey,
                        task = task.id,
                        providerLabel = providerLabel,
                        model = model,
                        status = if (success) "SUCCESS" else "FAILED",
                        errorSummary = error?.take(220),
                        createdAt = Instant.now().toString()
                    )
                )
            }
        )
    }

    suspend fun persistDraft(
        workspaceId: String,
        lesson: LessonRef,
        task: TaskType,
        providerLabel: String,
        model: String,
        text: String,
        title: String,
        sourceText: String,
        status: DraftStatus = DraftStatus.DRAFT
    ): DraftRecord {
        val canonicalWorkspaceId = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val maxVer = dao.getMaxDraftVersion(
            workspaceId = canonicalWorkspaceId,
            legacyRootPath = lesson.rootPath,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey
        )
        val nextVersion = maxVer + 1
        val id = "draft-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val createdAt = Instant.now().toString()
        val sourceHash = sha256Short(sourceText)
        val record = DraftRecord(
            id = id,
            workspaceId = canonicalWorkspaceId,
            grade = lesson.grade,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            task = task,
            title = title,
            provider = providerLabel,
            model = model,
            createdAt = createdAt,
            content = text,
            status = status.id,
            version = nextVersion,
            sourcePages = lesson.pageRange,
            sourceHash = sourceHash
        )
        dao.insertDraft(
            DraftEntity(
                id = record.id,
                workspaceId = record.workspaceId,
                gradeKey = record.grade,
                bookKey = record.bookKey,
                unitKey = record.unitKey,
                lessonKey = record.lessonKey,
                task = record.task.id,
                title = record.title,
                provider = record.provider,
                model = record.model,
                createdAt = record.createdAt,
                content = record.content,
                status = record.status,
                version = record.version,
                sourcePages = record.sourcePages,
                sourceHash = record.sourceHash
            )
        )
        recordStudyActivity()
        return record
    }

    suspend fun approveDraft(draftId: String) {
        dao.updateDraftStatus(draftId, DraftStatus.APPROVED.id)
    }

    fun observeDraftsForLesson(workspaceId: String, lesson: LessonRef): Flow<List<DraftRecord>> {
        val canonicalWorkspaceId = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        return dao.observeDraftsForLesson(
            workspaceId = canonicalWorkspaceId,
            legacyRootPath = lesson.rootPath,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey
        ).map { list ->
            list.map { entity ->
                DraftRecord(
                    id = entity.id,
                    workspaceId = entity.workspaceId,
                    grade = entity.gradeKey,
                    bookKey = entity.bookKey,
                    unitKey = entity.unitKey,
                    lessonKey = entity.lessonKey,
                    task = TaskType.fromId(entity.task),
                    title = entity.title,
                    provider = entity.provider,
                    model = entity.model,
                    createdAt = entity.createdAt,
                    content = entity.content,
                    status = entity.status,
                    version = entity.version,
                    sourcePages = entity.sourcePages,
                    sourceHash = entity.sourceHash
                )
            }
        }
    }

    suspend fun deleteDraft(draftId: String) {
        dao.deleteDraftById(draftId)
    }

    suspend fun importInteractivePackage(
        uri: Uri,
        workspaceId: String,
        lesson: LessonRef,
        sourcePages: String?
    ): InteractivePackageRecord {
        val canonicalWorkspaceId = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val maxVer = dao.getMaxPackageVersion(
            workspaceId = canonicalWorkspaceId,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey
        )
        val nextVersion = maxVer + 1
        val staged = interactivePackageImporter.importFromUri(
            uri = uri,
            lesson = lesson,
            sourcePages = sourcePages,
            nextVersion = nextVersion
        )
        val id = "pkg-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val createdAt = Instant.now().toString()
        val record = InteractivePackageRecord(
            id = id,
            workspaceId = canonicalWorkspaceId,
            gradeKey = lesson.grade,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            kind = staged.kind,
            title = staged.title,
            rootDir = staged.rootDir,
            entryFile = staged.entryFile,
            sourcePages = sourcePages?.takeIf { it.isNotBlank() } ?: lesson.pageRange,
            version = nextVersion,
            status = if (staged.report.hasBlockers) "BLOCKED" else "PREVIEW",
            publishedForStudents = false,
            report = staged.report,
            createdAt = createdAt
        )
        dao.insertInteractivePackage(
            InteractivePackageEntity(
                id = record.id,
                workspaceId = record.workspaceId,
                gradeKey = record.gradeKey,
                bookKey = record.bookKey,
                unitKey = record.unitKey,
                lessonKey = record.lessonKey,
                kind = record.kind.id,
                title = record.title,
                rootDir = record.rootDir,
                entryFile = record.entryFile,
                sourcePages = record.sourcePages,
                version = record.version,
                status = record.status,
                publishedForStudents = record.publishedForStudents,
                reportJson = json.encodeToString(record.report),
                createdAt = record.createdAt
            )
        )
        recordStudyActivity()
        return record
    }

    fun observePackagesForLesson(
        workspaceId: String,
        lesson: LessonRef
    ): Flow<List<InteractivePackageRecord>> {
        val canonicalWorkspaceId = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        return dao.observePackagesForLesson(
            workspaceId = canonicalWorkspaceId,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey
        ).map { list ->
            list.map { entity ->
                val report = runCatching {
                    json.decodeFromString<InteractivePackageReport>(entity.reportJson)
                }.getOrElse {
                    InteractivePackageReport(
                        kind = PackageKind.fromId(entity.kind),
                        fileName = entity.entryFile,
                        entryRelativePath = entity.entryFile,
                        totalFiles = 1,
                        totalBytes = 0L,
                        has3dAssets = false,
                        assetExtensions = emptyList(),
                        externalUrls = emptyList(),
                        missingReferencedAssets = emptyList(),
                        findings = emptyList(),
                        linkedLessonMatch = true
                    )
                }
                InteractivePackageRecord(
                    id = entity.id,
                    workspaceId = entity.workspaceId,
                    gradeKey = entity.gradeKey,
                    bookKey = entity.bookKey,
                    unitKey = entity.unitKey,
                    lessonKey = entity.lessonKey,
                    kind = PackageKind.fromId(entity.kind),
                    title = entity.title,
                    rootDir = entity.rootDir,
                    entryFile = entity.entryFile,
                    sourcePages = entity.sourcePages,
                    version = entity.version,
                    status = entity.status,
                    publishedForStudents = entity.publishedForStudents,
                    report = report,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun publishInteractivePackage(pkg: InteractivePackageRecord) {
        require(!pkg.report.hasBlockers) { "لا يمكن نشر حزمة تحتوي مخالفات أمنية حرجة" }
        dao.publishPackageVersion(
            workspaceId = pkg.workspaceId,
            bookKey = pkg.bookKey,
            unitKey = pkg.unitKey,
            lessonKey = pkg.lessonKey,
            packageId = pkg.id
        )
    }

    suspend fun deleteInteractivePackage(pkg: InteractivePackageRecord) {
        dao.deletePackageById(pkg.id)
        runCatching { File(pkg.rootDir).deleteRecursively() }
    }

    fun observeDashboardSummary(workspace: ImportedWorkspace?): Flow<DashboardSummary> {
        if (workspace == null) {
            return dao.observeStudyDays().map { studyDays ->
                val activityDates = MilestoneBadgeEngine.collectActivityDates(
                    studyDayStrings = studyDays.map { it.activityDate },
                    drafts = emptyList(),
                    packages = emptyList(),
                    progressRows = emptyList()
                )
                val streak = MilestoneBadgeEngine.computeStudyStreak(activityDates)
                val badges = MilestoneBadgeEngine.evaluateBadges(
                    totalLessons = 0,
                    completedLessons = 0,
                    overallCompletionPercent = 0,
                    draftsCount = 0,
                    approvedDraftsCount = 0,
                    packagesCount = 0,
                    publishedPackagesCount = 0,
                    bookProgress = emptyList(),
                    unitPaths = emptyList(),
                    streak = streak
                )
                DashboardSummary(
                    hasWorkspace = false,
                    currentStreakDays = streak.currentStreakDays,
                    longestStreakDays = streak.longestStreakDays,
                    unlockedBadgesCount = badges.count { it.unlocked },
                    badges = badges
                )
            }
        }
        return combine(
            dao.observeDraftsForWorkspace(workspace.workspaceId, workspace.rootPath),
            dao.observePackagesForWorkspace(workspace.workspaceId),
            dao.observeProgressForWorkspace(workspace.workspaceId),
            dao.observeStudyDays()
        ) { drafts, packages, progressRows, studyDays ->
            computeDashboardSummary(
                workspace = workspace,
                drafts = drafts,
                packages = packages,
                progressRows = progressRows,
                studyDayStrings = studyDays.map { it.activityDate }
            )
        }
    }

    suspend fun toggleLessonCompleted(
        workspaceId: String,
        lesson: LessonRef,
        currentlyCompleted: Boolean
    ) {
        val nextCompleted = !currentlyCompleted
        val canonicalWorkspaceId = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        dao.upsertLessonProgress(
            LessonProgressEntity(
                workspaceId = canonicalWorkspaceId,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                completed = nextCompleted,
                pointsEarned = if (nextCompleted) POINTS_LESSON_COMPLETED else 0,
                completedAt = if (nextCompleted) Instant.now().toString() else null
            )
        )
        if (nextCompleted) {
            recordStudyActivity()
        }
    }

    suspend fun recordStudyActivity(date: LocalDate = LocalDate.now()) {
        val dateIso = date.toString()
        val existing = dao.getStudyDay(dateIso)
        dao.upsertStudyDay(
            StudyDayEntity(
                activityDate = dateIso,
                actionsCount = (existing?.actionsCount ?: 0) + 1,
                lastUpdatedAt = Instant.now().toString()
            )
        )
    }

    fun computeDashboardSummary(
        workspace: ImportedWorkspace,
        drafts: List<DraftEntity>,
        packages: List<InteractivePackageEntity>,
        progressRows: List<LessonProgressEntity>,
        studyDayStrings: List<String> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): DashboardSummary {
        val draftsByLesson = drafts.groupBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        val pkgsByLesson = packages.groupBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        val progressByLesson = progressRows.associateBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }

        val nodes = workspace.lessons.map { lesson ->
            val key = Triple(lesson.bookKey, lesson.unitKey, lesson.lessonKey)
            val lDrafts = draftsByLesson[key].orEmpty()
            val lPkgs = pkgsByLesson[key].orEmpty()
            val prog = progressByLesson[key]

            val approvedDraftsCount = lDrafts.count { it.status == DraftStatus.APPROVED.id }
            val publishedPkgsCount = lPkgs.count { it.publishedForStudents }
            val isCompleted = prog?.completed == true

            val status = when {
                isCompleted -> LearningNodeStatus.COMPLETED
                approvedDraftsCount > 0 || publishedPkgsCount > 0 -> LearningNodeStatus.READY
                lDrafts.isNotEmpty() || lPkgs.isNotEmpty() -> LearningNodeStatus.IN_PROGRESS
                else -> LearningNodeStatus.NOT_STARTED
            }

            val draftPoints = when {
                approvedDraftsCount > 0 -> POINTS_APPROVED_DRAFT
                lDrafts.isNotEmpty() -> POINTS_DRAFT_CREATED
                else -> 0
            }
            val pkgPoints = when {
                publishedPkgsCount > 0 -> POINTS_PUBLISHED_PACKAGE
                lPkgs.isNotEmpty() -> POINTS_PACKAGE_UPLOADED
                else -> 0
            }
            val completionPoints = if (isCompleted) POINTS_LESSON_COMPLETED else 0
            val lessonPoints = draftPoints + pkgPoints + completionPoints

            LearningLessonNode(
                lesson = lesson,
                status = status,
                isCompleted = isCompleted,
                draftsCount = lDrafts.size,
                approvedDraftsCount = approvedDraftsCount,
                packagesCount = lPkgs.size,
                publishedPackagesCount = publishedPkgsCount,
                pointsEarned = lessonPoints,
                maxPossiblePoints = POINTS_MAX_PER_LESSON
            )
        }

        val totalLessons = nodes.size
        val completedLessons = nodes.count { it.isCompleted }
        val preparedLessons = nodes.count {
            it.status == LearningNodeStatus.COMPLETED || it.status == LearningNodeStatus.READY
        }
        val approvedItemsCount = drafts.count { it.status == DraftStatus.APPROVED.id } +
            packages.count { it.publishedForStudents }

        val overallCompletionPercent = if (totalLessons == 0) 0 else ((completedLessons * 100f) / totalLessons).toInt()
        val preparationPercent = if (totalLessons == 0) 0 else ((preparedLessons * 100f) / totalLessons).toInt()

        val nodesByBook = nodes.groupBy { it.lesson.bookKey }
        val bookProgress = workspace.books.map { book ->
            val bNodes = nodesByBook[book.bookKey].orEmpty()
            val bTotal = bNodes.size.coerceAtLeast(book.lessonsCount)
            val bCompleted = bNodes.count { it.isCompleted }
            val bPrepared = bNodes.count {
                it.status == LearningNodeStatus.COMPLETED || it.status == LearningNodeStatus.READY
            }
            BookProgressSummary(
                book = book,
                completedLessons = bCompleted,
                preparedLessons = bPrepared,
                totalLessons = bTotal,
                completionPercent = if (bTotal == 0) 0 else ((bCompleted * 100f) / bTotal).toInt(),
                preparationPercent = if (bTotal == 0) 0 else ((bPrepared * 100f) / bTotal).toInt(),
                earnedPoints = bNodes.sumOf { it.pointsEarned }
            )
        }

        val unitPaths = nodes
            .groupBy { Triple(it.lesson.bookKey, it.lesson.unitKey, it.lesson.unitTitle) }
            .map { (key, uNodes) ->
                val uCompleted = uNodes.count { it.isCompleted }
                val uTotal = uNodes.size
                LearningUnitPath(
                    unitKey = "${key.first}_${key.second}",
                    unitTitle = key.third,
                    bookTitle = uNodes.firstOrNull()?.lesson?.bookTitle.orEmpty(),
                    completedCount = uCompleted,
                    totalCount = uTotal,
                    completionPercent = if (uTotal == 0) 0 else ((uCompleted * 100f) / uTotal).toInt(),
                    nodes = uNodes
                )
            }

        val activityDates = MilestoneBadgeEngine.collectActivityDates(
            studyDayStrings = studyDayStrings,
            drafts = drafts,
            packages = packages,
            progressRows = progressRows
        )
        val streak = MilestoneBadgeEngine.computeStudyStreak(
            activityDates = activityDates,
            today = today
        )
        val approvedDraftsCount = drafts.count { it.status == DraftStatus.APPROVED.id }
        val publishedPackagesCount = packages.count { it.publishedForStudents }
        val badges = MilestoneBadgeEngine.evaluateBadges(
            totalLessons = totalLessons,
            completedLessons = completedLessons,
            overallCompletionPercent = overallCompletionPercent,
            draftsCount = drafts.size,
            approvedDraftsCount = approvedDraftsCount,
            packagesCount = packages.size,
            publishedPackagesCount = publishedPackagesCount,
            bookProgress = bookProgress,
            unitPaths = unitPaths,
            streak = streak
        )

        val lessonPointsSum = nodes.sumOf { it.pointsEarned }
        val badgeBonusSum = badges.filter { it.unlocked }.sumOf { it.pointsBonus }
        val totalPoints = lessonPointsSum + badgeBonusSum
        val pointsPerLevel = 150
        val level = (totalPoints / pointsPerLevel) + 1
        val pointsIntoLevel = totalPoints % pointsPerLevel
        val levelTitle = when (level) {
            1 -> "مستكشف المعرفة"
            2 -> "باني المسار"
            3 -> "مُصمّم الدروس"
            4 -> "خبير التعلّم الذكي"
            else -> "رائد الإبداع التعليمي"
        }

        val nextRecommended = nodes.firstOrNull { !it.isCompleted }?.lesson ?: nodes.firstOrNull()?.lesson

        return DashboardSummary(
            hasWorkspace = true,
            gradeKey = workspace.gradeKey,
            workspaceTitle = workspace.title,
            totalLessons = totalLessons,
            completedLessons = completedLessons,
            preparedLessons = preparedLessons,
            approvedItemsCount = approvedItemsCount,
            overallCompletionPercent = overallCompletionPercent,
            preparationPercent = preparationPercent,
            totalPoints = totalPoints,
            level = level,
            levelTitle = levelTitle,
            pointsIntoLevel = pointsIntoLevel,
            pointsPerLevel = pointsPerLevel,
            currentStreakDays = streak.currentStreakDays,
            longestStreakDays = streak.longestStreakDays,
            unlockedBadgesCount = badges.count { it.unlocked },
            bookProgress = bookProgress,
            unitPaths = unitPaths,
            badges = badges,
            nextRecommendedLesson = nextRecommended
        )
    }

    fun observeQuestionsForWorkspace(workspaceId: String): Flow<List<QuestionItem>> {
        if (workspaceId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.observeQuestionsForWorkspace(workspaceId).map { entities ->
            val mapped = entities.map { it.toModel() }
            QuestionBankEngine.markDuplicates(mapped)
        }
    }

    suspend fun saveQuestionsFromDraftText(
        workspaceId: String,
        lesson: LessonRef,
        rawText: String
    ): List<QuestionItem> {
        val parsed = QuestionBankEngine.parseQuestionsFromText(rawText, workspaceId, lesson)
        if (parsed.isNotEmpty()) {
            dao.insertQuestions(parsed.map { it.toEntity() })
            recordStudyActivity()
        }
        return parsed
    }

    suspend fun addManualQuestion(
        workspaceId: String,
        lesson: LessonRef,
        type: QuestionType,
        prompt: String,
        options: List<String>,
        correctAnswer: String,
        rationale: String,
        difficulty: QuestionDifficulty
    ): QuestionItem {
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val item = QuestionItem(
            id = "q-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            workspaceId = canonicalWs,
            gradeKey = lesson.grade,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            lessonTitle = lesson.title,
            type = type,
            prompt = prompt.trim(),
            options = options.map { it.trim() }.filter { it.isNotBlank() },
            correctAnswer = correctAnswer.trim(),
            rationale = rationale.trim(),
            difficulty = difficulty,
            sourcePages = lesson.pageRange,
            fingerprint = QuestionBankEngine.computeFingerprint(prompt),
            reviewed = true,
            createdAt = Instant.now().toString()
        )
        dao.insertQuestions(listOf(item.toEntity()))
        recordStudyActivity()
        return item
    }

    suspend fun toggleQuestionReviewed(questionId: String, reviewed: Boolean) {
        dao.updateQuestionReviewed(questionId, !reviewed)
    }

    suspend fun deleteQuestion(questionId: String) {
        dao.deleteQuestionById(questionId)
    }

    suspend fun exportIndexer2Zip(
        outputStream: OutputStream,
        workspace: ImportedWorkspace
    ): ExportReceipt = withContext(Dispatchers.IO) {
        val drafts = dao.observeDraftsForWorkspace(workspace.workspaceId, workspace.rootPath).first()
        val questions = dao.observeQuestionsForWorkspace(workspace.workspaceId).first().map { it.toModel() }
        WorkspaceExporter.exportIndexer2CompatibleZip(
            outputStream = outputStream,
            workspace = workspace,
            drafts = drafts,
            questions = questions
        )
    }

    private fun QuestionEntity.toModel(): QuestionItem {
        val opts = runCatching { json.decodeFromString<List<String>>(optionsJson) }.getOrDefault(emptyList())
        return QuestionItem(
            id = id,
            workspaceId = workspaceId,
            gradeKey = gradeKey,
            bookKey = bookKey,
            unitKey = unitKey,
            lessonKey = lessonKey,
            lessonTitle = lessonTitle,
            type = QuestionType.fromId(type),
            prompt = prompt,
            options = opts,
            correctAnswer = correctAnswer,
            rationale = rationale,
            difficulty = QuestionDifficulty.fromId(difficulty),
            sourcePages = sourcePages,
            fingerprint = fingerprint,
            reviewed = reviewed,
            createdAt = createdAt
        )
    }

    private fun QuestionItem.toEntity() = QuestionEntity(
        id = id,
        workspaceId = workspaceId,
        gradeKey = gradeKey,
        bookKey = bookKey,
        unitKey = unitKey,
        lessonKey = lessonKey,
        lessonTitle = lessonTitle,
        type = type.id,
        prompt = prompt,
        optionsJson = json.encodeToString(options),
        correctAnswer = correctAnswer,
        rationale = rationale,
        difficulty = difficulty.id,
        sourcePages = sourcePages,
        fingerprint = fingerprint,
        reviewed = reviewed,
        createdAt = createdAt
    )

    private fun sha256Short(text: String): String? {
        if (text.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun BookEntity.toModel() = BookRef(
        bookKey = bookKey,
        title = title,
        subjectKey = subjectKey,
        componentKey = componentKey,
        relativePath = relativePath,
        lessonsCount = lessonsCount
    )

    private fun LessonEntity.toModel(wsId: String) = LessonRef(
        grade = grade,
        rootPath = rootPath,
        bookKey = bookKey,
        bookTitle = bookTitle,
        subjectKey = subjectKey,
        componentKey = componentKey,
        unitKey = unitKey,
        unitTitle = unitTitle,
        lessonKey = lessonKey,
        title = title,
        partKey = partKey,
        pageRange = pageRange,
        pdfPath = pdfPath,
        workspaceId = wsId
    )

    companion object {
        const val POINTS_LESSON_COMPLETED = 50
        const val POINTS_APPROVED_DRAFT = 20
        const val POINTS_DRAFT_CREATED = 10
        const val POINTS_PUBLISHED_PACKAGE = 25
        const val POINTS_PACKAGE_UPLOADED = 15
        const val POINTS_MAX_PER_LESSON = POINTS_LESSON_COMPLETED + POINTS_APPROVED_DRAFT + POINTS_PUBLISHED_PACKAGE
    }
}
