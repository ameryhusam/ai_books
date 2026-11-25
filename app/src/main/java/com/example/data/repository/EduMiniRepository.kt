package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.ai.AiGenerationService
import com.example.data.ai.Indexer2PromptRegistry
import com.example.data.ai.TemplateResponseAdapter
import com.example.data.local.AssessmentAttemptEntity
import com.example.data.local.AssessmentEntity
import com.example.data.local.BookEntity
import com.example.data.local.DraftEntity
import com.example.data.local.EduMiniDao
import com.example.data.local.GenerationRunEntity
import com.example.data.local.InteractivePackageEntity
import com.example.data.local.LessonEntity
import com.example.data.local.LessonProgressEntity
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.QuestionAttemptEntity
import com.example.data.local.QuestionEntity
import com.example.data.local.StudyDayEntity
import com.example.data.local.TeacherNoteEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceIndexEntity
import com.example.data.local.WorkspaceUnitEntity
import com.example.data.packageimport.InteractiveBundleBuilder
import com.example.data.packageimport.InteractivePackageImporter
import com.example.model.InteractiveActivityModel
import com.example.model.LearnerProfile
import com.example.data.security.ProviderStorage
import com.example.data.workspace.DirectWorkspaceBinder
import com.example.data.workspace.ExportReceipt
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.UriIO
import com.example.data.workspace.WorkspaceExporter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.AssessmentAttemptRecord
import com.example.model.AssessmentKind
import com.example.model.AssessmentRecord
import com.example.model.BookProgressSummary
import com.example.model.BookRef
import com.example.model.ConceptMasterySummary
import com.example.model.DashboardSummary
import com.example.model.DirectWorkspaceInspection
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
import com.example.model.LessonQuizCompletionResult
import com.example.model.LessonRef
import com.example.model.LessonSplitDraft
import com.example.model.MasteryStatus
import com.example.model.PackageKind
import com.example.model.PdfBookInfo
import com.example.model.PromptTemplateRecord
import com.example.model.ProviderConfig
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.StagedWorkspacePreview
import com.example.model.TaskType
import com.example.model.UnitCompletionStatus
import com.example.model.TeacherNoteRecord
import com.example.model.WorkspaceSummary
import com.example.model.WorkspaceIndexRef
import com.example.model.WorkspaceUnitRef
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val interactivePackageImporter: InteractivePackageImporter,
    private val directWorkspaceBinder: DirectWorkspaceBinder? = null,
    private val appContext: Context? = null
) {

    private val json = Json { ignoreUnknownKeys = true }
    val questionBankService = QuestionBankService(dao, json)

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
                createdAt = entity.createdAt,
                durationMs = entity.durationMs,
                tokensUsed = entity.tokensUsed
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
                    dao.observeLessonsForWorkspace(entity.workspaceId),
                    dao.observeWorkspaceIndexes(entity.workspaceId),
                    dao.observeWorkspaceUnits(entity.workspaceId)
                ) { bookEntities, lessonEntities, indexEntities, unitEntities ->
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
                        importedAt = entity.importedAt,
                        indexRecords = indexEntities.map { it.toModel() },
                        units = unitEntities.map { it.toModel() }
                    )
                }
            }
        }
    }

    suspend fun inspectWorkspaceZip(uri: Uri): StagedWorkspacePreview {
        return workspaceImporter.inspectZipFromUri(uri)
    }

    suspend fun commitStagedWorkspaces(
        preview: StagedWorkspacePreview,
        selectedGrades: List<String>
    ): List<ImportedWorkspace> {
        val committed = workspaceImporter.commitStagedGrades(preview, selectedGrades)
        committed.forEach { saveImportedWorkspace(it) }
        return committed
    }

    fun discardStagedPreview(preview: StagedWorkspacePreview) {
        workspaceImporter.discardStagedPreview(preview)
    }

    suspend fun importWorkspaceZip(uri: Uri): ImportedWorkspace {
        val imported = workspaceImporter.importFromUri(uri)
        saveImportedWorkspace(imported)
        val backupFile = File(imported.rootPath, "full_backup.json")
        if (backupFile.isFile) {
            importFullBackupJson(
                rawJson = backupFile.readText(Charsets.UTF_8),
                targetWorkspaceId = imported.workspaceId
            )
        }
        return imported
    }

    suspend fun inspectDirectTreeUri(treeUri: Uri): DirectWorkspaceInspection {
        val binder = directWorkspaceBinder
            ?: throw IllegalStateException("أداة ربط مساحة العمل المباشرة غير مهيأة")
        return binder.inspectTreeUri(treeUri)
    }

    suspend fun bindDirectWorkspace(rootUriOrPath: String, gradeKey: String): ImportedWorkspace {
        val binder = directWorkspaceBinder
            ?: throw IllegalStateException("أداة ربط مساحة العمل المباشرة غير مهيأة")
        val bound = binder.bindSingleGrade(rootUriOrPath, gradeKey)
        saveImportedWorkspace(bound.workspace)
        if (bound.preloadedQuestions.isNotEmpty()) {
            val existingFingerprints = dao.observeQuestionsForWorkspace(bound.workspace.workspaceId)
                .first()
                .map { "${it.bookKey}|${it.unitKey}|${it.lessonKey}|${it.fingerprint}" }
                .toHashSet()
            val toInsert = bound.preloadedQuestions.filter {
                "${it.bookKey}|${it.unitKey}|${it.lessonKey}|${it.fingerprint}" !in existingFingerprints
            }
            if (toInsert.isNotEmpty()) {
                dao.insertQuestions(toInsert.map { it.toEntity() })
            }
        }
        if (bound.preloadedInteractivePackages.isNotEmpty()) {
            val existingIds = dao.observePackagesForWorkspace(bound.workspace.workspaceId)
                .first()
                .map { it.id }
                .toHashSet()
            bound.preloadedInteractivePackages
                .filter { it.id !in existingIds }
                .forEach { dao.insertInteractivePackage(it) }
        }
        return bound.workspace
    }

    suspend fun deleteWorkspace(workspaceId: String, rootPath: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkspaceGraph(workspaceId)
        if (rootPath.isNotBlank() && !UriIO.isContentUri(rootPath)) {
            val dir = File(rootPath)
            val normalized = dir.canonicalPath.replace('\\', '/')
            if (normalized.contains("/edumini/workspaces/")) {
                runCatching { dir.deleteRecursively() }
            }
        }
    }

    suspend fun computeWorkspaceDiskBytes(rootPath: String): Long = withContext(Dispatchers.IO) {
        if (rootPath.isBlank() || UriIO.isContentUri(rootPath)) return@withContext 0L
        val dir = File(rootPath)
        if (!dir.exists()) return@withContext 0L
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
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
                lessonsCount = book.lessonsCount,
                coverPath = book.coverPath,
                sourcePdfPath = book.sourcePdfPath,
                subjectName = book.subjectName
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
                pdfPath = lesson.pdfPath,
                sourceTextPath = lesson.sourceTextPath,
                interactivePath = lesson.interactivePath,
                printedPageRange = lesson.printedPageRange
            )
        }
        if (imported.indexRecords.isNotEmpty()) {
            val indexEntities = imported.indexRecords.map { index ->
                WorkspaceIndexEntity(
                    workspaceId = imported.workspaceId,
                    bookKey = index.bookKey,
                    indexUri = index.indexUriOrPath,
                    indexRelativePath = index.indexRelativePath,
                    manifestUri = index.manifestUriOrPath,
                    manifestRelativePath = index.manifestRelativePath,
                    indexSha256 = index.indexSha256,
                    manifestSha256 = index.manifestSha256,
                    sourceSha256 = index.sourceSha256,
                    indexSchemaVersion = index.indexSchemaVersion,
                    manifestVersion = index.manifestVersion,
                    lastSyncedAt = index.lastSyncedAt
                )
            }
            val unitEntities = imported.units.map { unit ->
                WorkspaceUnitEntity(
                    workspaceId = imported.workspaceId,
                    bookKey = unit.bookKey,
                    unitKey = unit.unitKey,
                    title = unit.title,
                    partKey = unit.partKey,
                    sortOrder = unit.sortOrder,
                    sourceIndexPath = unit.sourceIndexPath
                )
            }
            dao.syncDirectWorkspaceCatalog(
                workspace = workspaceEntity,
                books = bookEntities,
                lessons = lessonEntities,
                indexes = indexEntities,
                units = unitEntities
            )
        } else {
            dao.saveWorkspaceGraph(workspaceEntity, bookEntities, lessonEntities)
        }
    }

    suspend fun loadProviders(): List<ProviderConfig> = providerStorage.loadProviders()

    suspend fun saveProviders(providers: List<ProviderConfig>) {
        providerStorage.saveProviders(providers)
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean): List<ProviderConfig> {
        providerStorage.setProviderEnabled(providerId, enabled)
        return providerStorage.loadProviders()
    }

    suspend fun reactivateProvider(providerId: String): List<ProviderConfig> {
        providerStorage.manuallyReactivateProvider(providerId)
        return providerStorage.loadProviders()
    }

    suspend fun testProviderConnection(profile: ProviderConfig): String {
        return aiGenerationService.testProviderConnection(profile)
    }

    suspend fun clearGenerationRuns() {
        dao.clearGenerationRuns()
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
        difficultyHint: String = "",
        template: PromptTemplateRecord? = null,
        onProgress: (percent: Int, stage: String) -> Unit = { _, _ -> }
    ): GeneratedResult {
        if (providers.none { it.enabled && it.apiKey.isNotBlank() && it.model.isNotBlank() }) {
            throw IllegalStateException("لا يوجد مفتاح API مفعّل بنموذج صالح. أضف مفتاحاً أو أعد تفعيل أحد المفاتيح من الإعدادات.")
        }
        onProgress(8, "قراءة بيانات الدرس وتجهيز المصدر")
        val effectiveSource = source.trim()
        val activeTemplate = template ?: dao.observePromptTemplates().first()
            .firstOrNull { it.task == task.id }
            ?.let { entity ->
                PromptTemplateRecord(
                    id = entity.id,
                    title = entity.title,
                    task = TaskType.fromId(entity.task),
                    instructions = entity.instructions,
                    isBuiltIn = entity.id.startsWith("builtin_"),
                    responseSchemaJson = entity.responseSchemaJson,
                    responseMappingJson = entity.responseMappingJson,
                    responseFormat = entity.responseFormat,
                    sourceAttachmentMode = entity.sourceAttachmentMode,
                    version = entity.version
                )
            }
        onProgress(18, "تجهيز ملف PDF الأصلي")
        val attachmentMode = activeTemplate?.sourceAttachmentMode ?: "pdf_native"
        val shouldAttachSource = attachPdfPages && attachmentMode != "none"
        val pdfPayload = if (shouldAttachSource && attachmentMode == "pdf_native" &&
            providers.any { it.provider == com.example.model.ProviderType.GEMINI && it.enabled } &&
            !lesson.pdfPath.isNullOrBlank()
        ) {
            aiGenerationService.extractLessonPdfBase64(lesson.pdfPath)
        } else null
        // OpenAI-compatible chat endpoints do not share a universal native-PDF contract.
        // Rendered page images are retained as a compatibility fallback for those providers.
        val needsRenderedPages = shouldAttachSource && (
            attachmentMode == "rendered_pages" ||
                providers.any { it.provider == com.example.model.ProviderType.OPENAI_COMPATIBLE && it.enabled }
            )
        val pageImages = if (needsRenderedPages) {
            aiGenerationService.extractLessonPdfPageImagesBase64(lesson.pdfPath)
        } else emptyList()
        onProgress(32, "تجهيز المرفقات ومخطط الاستجابة")
        require(effectiveSource.isNotBlank() || pdfPayload != null || pageImages.isNotEmpty()) {
            "لم يتم تجهيز نص أو ملف PDF صالح؛ تحقق من مسار ملف الدرس ثم أعد المحاولة."
        }
        val responseSchema = activeTemplate?.responseSchemaJson.orEmpty()
        val responseFormat = activeTemplate?.responseFormat
            ?: if (responseSchema.isNotBlank()) "json" else "text"
        val templateInstructions = activeTemplate?.instructions.orEmpty().trim()
        val effectiveExtra = if (templateInstructions.isNotBlank() && extra.contains(templateInstructions)) {
            extra.trim()
        } else {
            listOf(templateInstructions, extra.trim()).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
        }
        val schemaHash = sha256Full(responseSchema)
        val mappingHash = sha256Full(activeTemplate?.responseMappingJson.orEmpty())
        val pdfHash = pdfPayload?.sha256 ?: if (shouldAttachSource && !lesson.pdfPath.isNullOrBlank()) {
            aiGenerationService.sha256LessonPdf(lesson.pdfPath)
        } else ""
        val requestSeed = listOf(
            activeTemplate?.id.orEmpty(),
            activeTemplate?.version?.toString().orEmpty(),
            attachmentMode,
            task.id,
            lesson.grade,
            lesson.bookKey,
            lesson.unitKey,
            lesson.lessonKey,
            lesson.pageRange.orEmpty(),
            schemaHash,
            mappingHash,
            responseFormat,
            pdfHash,
            effectiveSource,
            effectiveExtra
        ).joinToString("|")
        val requestSeedHash = sha256Full(requestSeed)
        val startedMs = System.currentTimeMillis()
        onProgress(42, "إرسال البرومبت والمرفق إلى مزود الذكاء الاصطناعي")
        val result = aiGenerationService.generate(
            lesson = lesson,
            task = task,
            source = effectiveSource,
            extra = effectiveExtra,
            providers = providers,
            questionCount = questionCount,
            pdfPageImagesBase64 = pageImages,
            pdfDocumentBase64 = pdfPayload?.base64,
            responseSchemaJson = responseSchema,
            responseFormat = responseFormat,
            templateId = activeTemplate?.id.orEmpty(),
            templateVersion = activeTemplate?.version ?: 1,
            responseSchemaHash = schemaHash,
            requestFingerprint = requestSeedHash,
            sourcePdfHash = pdfHash,
            onReserveProvider = { profile -> providerStorage.reserveProviderKey(profile.id) },
            onProviderStateChanged = { providerId, status, retryAfter ->
                providerStorage.updateProviderRuntimeState(providerId, status, retryAfter)
            },
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
                        createdAt = Instant.now().toString(),
                        durationMs = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L),
                        tokensUsed = 0
                    )
                )
            }
        )
        onProgress(88, "التحقق من الاستجابة ومواءمة الحقول مع نموذج التطبيق")
        val adaptedText = TemplateResponseAdapter.adapt(result.text, activeTemplate?.responseMappingJson.orEmpty())
        onProgress(96, "حساب بصمات المصدر والقالب والمحتوى")
        return if (adaptedText == result.text) result else result.copy(
            text = adaptedText,
            contentHash = sha256Full(adaptedText)
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
        status: DraftStatus = DraftStatus.DRAFT,
        generation: com.example.model.GeneratedResult? = null
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
        val provenanceSource = sourceText.trim()
        val sourceHash = sha256Short(provenanceSource)
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
            sourceHash = sourceHash,
            templateId = generation?.templateId.orEmpty(),
            templateVersion = generation?.templateVersion ?: 1,
            responseSchemaHash = generation?.responseSchemaHash.orEmpty(),
            requestFingerprint = generation?.requestFingerprint.orEmpty(),
            contentHash = generation?.contentHash?.takeIf { it.isNotBlank() } ?: sha256Full(text),
            sourcePdfHash = generation?.sourcePdfHash.orEmpty()
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
                sourceHash = record.sourceHash,
                templateId = record.templateId,
                templateVersion = record.templateVersion,
                responseSchemaHash = record.responseSchemaHash,
                requestFingerprint = record.requestFingerprint,
                contentHash = record.contentHash,
                sourcePdfHash = record.sourcePdfHash
            )
        )
        return record
    }

    suspend fun approveDraft(draftId: String): Int {
        dao.updateDraftStatus(draftId, DraftStatus.APPROVED.id)
        val draft = dao.getDraftById(draftId) ?: return 0
        val task = TaskType.fromId(draft.task)
        if (task != TaskType.QUESTIONS && task != TaskType.INTERACTIVE) return 0
        val lessonRef = dao.getLessonsForWorkspaceSnapshot(draft.workspaceId)
            .firstOrNull {
                it.bookKey == draft.bookKey &&
                    it.unitKey == draft.unitKey &&
                    it.lessonKey == draft.lessonKey
            }?.toModel(draft.workspaceId)
            ?: LessonRef(
                grade = draft.gradeKey,
                rootPath = draft.workspaceId,
                bookKey = draft.bookKey,
                bookTitle = draft.bookKey,
                subjectKey = draft.bookKey,
                unitKey = draft.unitKey,
                unitTitle = draft.unitKey,
                lessonKey = draft.lessonKey,
                title = draft.title,
                pageRange = draft.sourcePages,
                workspaceId = draft.workspaceId
            )
        if (task == TaskType.INTERACTIVE) {
            val activity = InteractiveActivityParser.parse(draft.content, lessonRef.title) ?: return 0
            val synced = questionBankService.syncInteractiveCheckpointsToQuestionBank(
                workspaceId = draft.workspaceId,
                lesson = lessonRef,
                activity = activity
            )
            runCatching {
                buildAndPublishInteractiveBundle(
                    workspaceId = draft.workspaceId,
                    lesson = lessonRef,
                    activity = activity,
                    publishForStudents = true
                )
            }
            return synced.size
        }
        return saveQuestionsFromDraftText(
            workspaceId = draft.workspaceId,
            lesson = lessonRef,
            rawText = draft.content
        ).size
    }

    suspend fun buildAndPublishInteractiveBundle(
        workspaceId: String,
        lesson: LessonRef,
        activity: InteractiveActivityModel,
        publishForStudents: Boolean = true
    ): InteractivePackageRecord? {
        val ctx = appContext ?: return null
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val maxVer = dao.getMaxPackageVersion(
            workspaceId = canonicalWs,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey
        )
        val nextVersion = maxVer + 1
        val staged = InteractiveBundleBuilder.materializePackage(
            context = ctx,
            importer = interactivePackageImporter,
            activity = activity,
            lesson = lesson,
            nextVersion = nextVersion
        )
        val id = "pkg-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val createdAt = Instant.now().toString()
        val hasBlockers = staged.report.hasBlockers
        val shouldPublish = publishForStudents && !hasBlockers
        val record = InteractivePackageRecord(
            id = id,
            workspaceId = canonicalWs,
            gradeKey = lesson.grade,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            kind = staged.kind,
            title = staged.title,
            rootDir = staged.rootDir,
            entryFile = staged.entryFile,
            sourcePages = lesson.pageRange,
            version = nextVersion,
            status = if (hasBlockers) "BLOCKED" else if (shouldPublish) "APPROVED" else "PREVIEW",
            publishedForStudents = shouldPublish,
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
        if (shouldPublish) {
            dao.publishPackageVersion(
                workspaceId = canonicalWs,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                packageId = record.id
            )
        }
        questionBankService.syncInteractiveCheckpointsToQuestionBank(
            workspaceId = canonicalWs,
            lesson = lesson,
            activity = activity
        )
        return record
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

    fun observeDraftsForWorkspace(workspaceId: String, legacyRootPath: String): Flow<List<DraftRecord>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        return dao.observeDraftsForWorkspace(workspaceId, legacyRootPath).map { list ->
            list.map { it.toDraftRecord() }.sortedByDescending { it.createdAt }
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

    fun observeDashboardSummary(
        workspace: ImportedWorkspace?,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): Flow<DashboardSummary> {
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        if (workspace == null) {
            return dao.observeStudyDays(safeUserId).map { studyDays ->
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
            dao.observeProgressForWorkspace(workspace.workspaceId, safeUserId),
            dao.observeStudyDays(safeUserId),
            combine(
                dao.observeAssessmentAttempts(workspace.workspaceId, safeUserId),
                dao.observeQuestionsForWorkspace(workspace.workspaceId),
                dao.observeQuestionAttemptsForWorkspaceAndUser(workspace.workspaceId, safeUserId)
            ) { aAttempts, qEntities, qAttempts ->
                Triple(aAttempts, qEntities, qAttempts)
            }
        ) { drafts, packages, progressRows, studyDays, extras ->
            val (aAttempts, qEntities, qAttempts) = extras
            computeDashboardSummary(
                workspace = workspace,
                drafts = drafts,
                packages = packages,
                progressRows = progressRows,
                studyDayStrings = studyDays.map { it.activityDate },
                assessmentAttempts = aAttempts,
                questions = qEntities.map { it.toModel() },
                questionAttempts = qAttempts.map { it.toModel() }
            )
        }
    }

    suspend fun toggleLessonCompleted(
        workspaceId: String,
        lesson: LessonRef,
        currentlyCompleted: Boolean,
        userId: String = LearnerProfile.DEFAULT_USER_ID
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
                completedAt = if (nextCompleted) Instant.now().toString() else null,
                userId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
            )
        )
        if (nextCompleted) {
            recordStudyActivity(userId = userId)
        }
    }

    fun observeQuestionAttempts(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): Flow<List<QuestionAttemptRecord>> {
        return questionBankService.observeUserAttempts(workspaceId, userId)
    }

    suspend fun recordLessonQuizAttempt(
        workspaceId: String,
        lesson: LessonRef,
        questions: List<QuestionItem>,
        selectedAnswers: Map<String, String>,
        sourceMode: String = "lesson_quiz",
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): LessonQuizCompletionResult {
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        val summary = QuestionBankEngine.gradeQuiz(questions, selectedAnswers)
        val passed = summary.totalQuestions > 0 && summary.scorePercent >= LESSON_QUIZ_PASS_PERCENT
        val now = Instant.now().toString()

        val recorded = questionBankService.recordBatchAttemptsForUser(
            workspaceId = canonicalWs,
            userId = safeUserId,
            questions = questions,
            selectedAnswers = selectedAnswers,
            sourceMode = sourceMode
        )

        val existingProgress = dao.observeProgressForWorkspace(canonicalWs, safeUserId).first()
            .firstOrNull {
                it.bookKey == lesson.bookKey &&
                    it.unitKey == lesson.unitKey &&
                    it.lessonKey == lesson.lessonKey
            }

        val bonusXp = if (passed) summary.earnedXp else (summary.correctCount * 5)
        if (passed && existingProgress?.completed != true) {
            dao.upsertLessonProgress(
                LessonProgressEntity(
                    workspaceId = canonicalWs,
                    bookKey = lesson.bookKey,
                    unitKey = lesson.unitKey,
                    lessonKey = lesson.lessonKey,
                    completed = true,
                    pointsEarned = POINTS_LESSON_COMPLETED + bonusXp,
                    completedAt = now,
                    userId = safeUserId
                )
            )
        } else if (existingProgress != null) {
            dao.upsertLessonProgress(
                existingProgress.copy(pointsEarned = existingProgress.pointsEarned + bonusXp)
            )
        } else if (bonusXp > 0) {
            dao.upsertLessonProgress(
                LessonProgressEntity(
                    workspaceId = canonicalWs,
                    bookKey = lesson.bookKey,
                    unitKey = lesson.unitKey,
                    lessonKey = lesson.lessonKey,
                    completed = false,
                    pointsEarned = bonusXp,
                    completedAt = null,
                    userId = safeUserId
                )
            )
        }

        if (recorded.isNotEmpty()) {
            recordStudyActivity(userId = safeUserId)
        }

        val allAttempts = questionBankService.observeUserAttempts(canonicalWs, safeUserId).first()
        val conceptSummaries = ConceptMasteryEngine.computeConceptSummaries(questions, allAttempts)
        return LessonQuizCompletionResult(
            lessonKey = lesson.lessonKey,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            correctCount = summary.correctCount,
            totalQuestions = summary.totalQuestions,
            scorePercent = summary.scorePercent,
            passed = passed,
            lessonMarkedCompleted = existingProgress?.completed == true || passed,
            earnedXp = bonusXp + if (passed && existingProgress?.completed != true) POINTS_LESSON_COMPLETED else 0,
            weakConcepts = conceptSummaries.filter { it.status == MasteryStatus.NEEDS_REINFORCEMENT },
            masteredConcepts = conceptSummaries.filter { it.status == MasteryStatus.MASTERED }
        )
    }

    suspend fun recordStandaloneQuestionAttempts(
        workspaceId: String,
        questions: List<QuestionItem>,
        selectedAnswers: Map<String, String>,
        sourceMode: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ) {
        val recorded = questionBankService.recordBatchAttemptsForUser(
            workspaceId = workspaceId,
            userId = userId,
            questions = questions,
            selectedAnswers = selectedAnswers,
            sourceMode = sourceMode
        )
        val activityRecordedByParent = sourceMode.equals("timed_assessment", ignoreCase = true) ||
            sourceMode.equals("bank_quiz", ignoreCase = true)
        if (recorded.isNotEmpty() && !activityRecordedByParent) {
            recordStudyActivity(userId = userId)
        }
    }

    suspend fun recordInteractiveCheckpointAttempt(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID,
        lesson: LessonRef,
        prompt: String,
        conceptTag: String,
        selectedAnswer: String,
        correctAnswer: String,
        isCorrect: Boolean,
        options: List<String> = emptyList(),
        sourceMode: String = "interactive_html"
    ) {
        val recorded = questionBankService.recordInteractiveCheckpointResultForUser(
            workspaceId = workspaceId,
            userId = userId,
            lesson = lesson,
            prompt = prompt,
            conceptTag = conceptTag,
            selectedAnswer = selectedAnswer,
            correctAnswer = correctAnswer,
            isCorrect = isCorrect,
            options = options,
            sourceMode = sourceMode
        )
        if (recorded != null) {
            recordStudyActivity(userId = userId)
        }
    }

    suspend fun recordStudyActivity(
        date: LocalDate = LocalDate.now(),
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ) {
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        val dateIso = date.toString()
        val existing = dao.getStudyDay(dateIso, safeUserId)
        dao.upsertStudyDay(
            StudyDayEntity(
                activityDate = dateIso,
                actionsCount = (existing?.actionsCount ?: 0) + 1,
                lastUpdatedAt = Instant.now().toString(),
                userId = safeUserId
            )
        )
    }

    fun computeDashboardSummary(
        workspace: ImportedWorkspace,
        drafts: List<DraftEntity>,
        packages: List<InteractivePackageEntity>,
        progressRows: List<LessonProgressEntity>,
        studyDayStrings: List<String> = emptyList(),
        today: LocalDate = LocalDate.now(),
        assessmentAttempts: List<AssessmentAttemptEntity> = emptyList(),
        questions: List<QuestionItem> = emptyList(),
        questionAttempts: List<QuestionAttemptRecord> = emptyList()
    ): DashboardSummary {
        val draftsByLesson = drafts.groupBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        val pkgsByLesson = packages.groupBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        val progressByLesson = progressRows.associateBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        val questionsByLesson = questions.groupBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        val attemptsByQuestionId = questionAttempts.groupBy { it.questionId }

        val nodes = workspace.lessons.map { lesson ->
            val key = Triple(lesson.bookKey, lesson.unitKey, lesson.lessonKey)
            val lDrafts = draftsByLesson[key].orEmpty()
            val lPkgs = pkgsByLesson[key].orEmpty()
            val lQuestions = questionsByLesson[key].orEmpty()
            val prog = progressByLesson[key]

            val lessonAttemptCount = lQuestions.sumOf { q -> attemptsByQuestionId[q.id]?.size ?: 0 }
            val approvedDraftsCount = lDrafts.count { it.status == DraftStatus.APPROVED.id }
            val publishedPkgsCount = lPkgs.count { it.publishedForStudents }
            val isCompleted = prog?.completed == true

            val status = when {
                isCompleted -> LearningNodeStatus.COMPLETED
                approvedDraftsCount > 0 || publishedPkgsCount > 0 || lQuestions.isNotEmpty() -> LearningNodeStatus.READY
                lDrafts.isNotEmpty() || lPkgs.isNotEmpty() || lessonAttemptCount > 0 -> LearningNodeStatus.IN_PROGRESS
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
                maxPossiblePoints = POINTS_MAX_PER_LESSON,
                questionsCount = lQuestions.size,
                quizAttemptedCount = lessonAttemptCount,
                quizPassed = isCompleted,
                hasGeneratedMaterials = lDrafts.isNotEmpty() || lPkgs.isNotEmpty() || lQuestions.isNotEmpty()
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
                val uInProgress = uNodes.count {
                    !it.isCompleted && (it.status == LearningNodeStatus.IN_PROGRESS || it.quizAttemptedCount > 0)
                }
                val uQuestions = uNodes.sumOf { it.questionsCount }
                val uGenerated = uNodes.sumOf { it.draftsCount + it.packagesCount + it.questionsCount }
                val uStatus = when {
                    uTotal > 0 && uCompleted == uTotal -> UnitCompletionStatus.COMPLETED
                    uCompleted > 0 || uInProgress > 0 -> UnitCompletionStatus.IN_PROGRESS
                    else -> UnitCompletionStatus.NOT_STARTED
                }
                LearningUnitPath(
                    unitKey = "${key.first}_${key.second}",
                    unitTitle = key.third,
                    bookTitle = uNodes.firstOrNull()?.lesson?.bookTitle.orEmpty(),
                    completedCount = uCompleted,
                    totalCount = uTotal,
                    completionPercent = if (uTotal == 0) 0 else ((uCompleted * 100f) / uTotal).toInt(),
                    nodes = uNodes,
                    status = uStatus,
                    questionsCount = uQuestions,
                    generatedContentCount = uGenerated,
                    inProgressCount = uInProgress
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

        val bookTitleByKey = workspace.books.associate { it.bookKey to it.title }
        val conceptSummaries = ConceptMasteryEngine.computeConceptSummaries(
            questions = questions,
            attempts = questionAttempts,
            bookTitleByKey = bookTitleByKey
        )
        val weakConcepts = conceptSummaries.filter { it.status == MasteryStatus.NEEDS_REINFORCEMENT }
        val masteredConceptsCount = conceptSummaries.count { it.status == MasteryStatus.MASTERED }

        val lessonPointsSum = nodes.sumOf { node ->
            val bonus = progressByLesson[Triple(node.lesson.bookKey, node.lesson.unitKey, node.lesson.lessonKey)]
                ?.pointsEarned
                ?.let { saved ->
                    if (node.isCompleted) (saved - POINTS_LESSON_COMPLETED).coerceAtLeast(0) else saved.coerceAtLeast(0)
                } ?: 0
            node.pointsEarned + bonus
        }
        val assessmentXpSum = assessmentAttempts.sumOf { it.earnedXp.coerceAtLeast(0) }
        val badgeBonusSum = badges.filter { it.unlocked }.sumOf { it.pointsBonus }
        val totalPoints = lessonPointsSum + badgeBonusSum + assessmentXpSum
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
            nextRecommendedLesson = nextRecommended,
            weakConcepts = weakConcepts,
            masteredConceptsCount = masteredConceptsCount,
            weakConceptsCount = weakConcepts.size
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
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val parsed = QuestionBankEngine.parseQuestionsFromText(rawText, canonicalWs, lesson)
        if (parsed.isEmpty()) return emptyList()
        val saved = questionBankService.saveQuestionsWithConceptTags(canonicalWs, lesson, parsed)
        return saved
    }

    suspend fun addManualQuestion(
        workspaceId: String,
        lesson: LessonRef,
        type: QuestionType,
        prompt: String,
        options: List<String>,
        correctAnswer: String,
        rationale: String,
        difficulty: QuestionDifficulty,
        conceptTag: String = ""
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
            createdAt = Instant.now().toString(),
            conceptTag = conceptTag.trim().ifBlank { lesson.title }
        )
        dao.insertQuestions(listOf(item.toEntity()))
        return item
    }

    suspend fun updateQuestion(
        questionId: String,
        type: QuestionType,
        prompt: String,
        options: List<String>,
        correctAnswer: String,
        rationale: String,
        difficulty: QuestionDifficulty,
        conceptTag: String,
        reviewed: Boolean
    ) {
        val cleanOpts = options.map { it.trim() }.filter { it.isNotBlank() }
        dao.updateQuestionDetails(
            questionId = questionId,
            prompt = prompt.trim(),
            optionsJson = json.encodeToString(cleanOpts),
            correctAnswer = correctAnswer.trim(),
            rationale = rationale.trim(),
            difficulty = difficulty.id,
            conceptTag = conceptTag.trim(),
            type = type.id,
            reviewed = reviewed
        )
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
        val questionEntities = dao.observeQuestionsForWorkspace(workspace.workspaceId).first()
        val questions = questionEntities.map { it.toModel() }
        val assessments = dao.observeAssessmentsForWorkspace(workspace.workspaceId).first()
        val attempts = dao.observeAllAssessmentAttempts(workspace.workspaceId).first()
        val qAttempts = dao.observeQuestionAttemptsForWorkspace(workspace.workspaceId).first()
        val progress = dao.observeAllProgressForWorkspace(workspace.workspaceId).first()
        val notes = dao.observeTeacherNotesForWorkspace(workspace.workspaceId).first()
        val studyDays = dao.observeAllStudyDays().first()
        val templates = dao.observePromptTemplates().first()
        val fullBackup = WorkspaceExporter.serializeFullBackup(
            workspaceId = workspace.workspaceId,
            drafts = drafts,
            questions = questionEntities,
            assessments = assessments,
            attempts = attempts,
            progress = progress,
            notes = notes,
            studyDays = studyDays,
            templates = templates,
            questionAttempts = qAttempts
        )
        WorkspaceExporter.exportIndexer2CompatibleZip(
            outputStream = outputStream,
            workspace = workspace,
            drafts = drafts,
            questions = questions,
            assessments = assessments,
            fullBackupJson = fullBackup,
            context = appContext
        )
    }

    suspend fun exportFullBackupJson(
        outputStream: OutputStream,
        workspace: ImportedWorkspace
    ): Int = withContext(Dispatchers.IO) {
        val drafts = dao.observeDraftsForWorkspace(workspace.workspaceId, workspace.rootPath).first()
        val questions = dao.observeQuestionsForWorkspace(workspace.workspaceId).first()
        val assessments = dao.observeAssessmentsForWorkspace(workspace.workspaceId).first()
        val attempts = dao.observeAllAssessmentAttempts(workspace.workspaceId).first()
        val qAttempts = dao.observeQuestionAttemptsForWorkspace(workspace.workspaceId).first()
        val progress = dao.observeAllProgressForWorkspace(workspace.workspaceId).first()
        val notes = dao.observeTeacherNotesForWorkspace(workspace.workspaceId).first()
        val studyDays = dao.observeAllStudyDays().first()
        val templates = dao.observePromptTemplates().first()
        val payload = WorkspaceExporter.serializeFullBackup(
            workspaceId = workspace.workspaceId,
            drafts = drafts,
            questions = questions,
            assessments = assessments,
            attempts = attempts,
            progress = progress,
            notes = notes,
            studyDays = studyDays,
            templates = templates,
            questionAttempts = qAttempts
        )
        outputStream.writer(Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        drafts.size + questions.size + assessments.size + notes.size + qAttempts.size
    }

    suspend fun importFullBackupJson(
        rawJson: String,
        targetWorkspaceId: String
    ): String = withContext(Dispatchers.IO) {
        val bundle = WorkspaceExporter.parseFullBackup(rawJson)
        val wsId = targetWorkspaceId.ifBlank { bundle.workspaceId }
        val remapIds = wsId != bundle.workspaceId
        val draftIdMap = bundle.drafts.associate { it.id to if (remapIds) "draft-import-${UUID.randomUUID()}" else it.id }
        val questionIdMap = bundle.questions.associate { it.id to if (remapIds) "q-import-${UUID.randomUUID()}" else it.id }
        val assessmentIdMap = bundle.assessments.associate { it.id to if (remapIds) "assessment-import-${UUID.randomUUID()}" else it.id }
        val attemptIdMap = bundle.attempts.associate { it.id to if (remapIds) "attempt-import-${UUID.randomUUID()}" else it.id }
        bundle.drafts.forEach { d ->
            dao.insertDraft(
                DraftEntity(
                    id = draftIdMap[d.id] ?: d.id,
                    workspaceId = wsId.ifBlank { d.workspaceId },
                    gradeKey = d.gradeKey,
                    bookKey = d.bookKey,
                    unitKey = d.unitKey,
                    lessonKey = d.lessonKey,
                    task = d.task,
                    title = d.title,
                    provider = d.provider,
                    model = d.model,
                    createdAt = d.createdAt,
                    content = d.content,
                    status = d.status,
                    version = d.version,
                    sourcePages = d.sourcePages,
                    sourceHash = d.sourceHash,
                    templateId = d.templateId,
                    templateVersion = d.templateVersion,
                    responseSchemaHash = d.responseSchemaHash,
                    requestFingerprint = d.requestFingerprint,
                    contentHash = d.contentHash,
                    sourcePdfHash = d.sourcePdfHash
                )
            )
        }
        if (bundle.questions.isNotEmpty()) {
            dao.insertQuestions(
                bundle.questions.map { q ->
                    QuestionEntity(
                        id = questionIdMap[q.id] ?: q.id,
                        workspaceId = wsId.ifBlank { q.workspaceId },
                        gradeKey = q.gradeKey,
                        bookKey = q.bookKey,
                        unitKey = q.unitKey,
                        lessonKey = q.lessonKey,
                        lessonTitle = q.lessonTitle,
                        type = q.type,
                        prompt = q.prompt,
                        optionsJson = q.optionsJson,
                        correctAnswer = q.correctAnswer,
                        rationale = q.rationale,
                        difficulty = q.difficulty,
                        sourcePages = q.sourcePages,
                        fingerprint = q.fingerprint,
                        reviewed = q.reviewed,
                        createdAt = q.createdAt,
                        conceptTag = q.conceptTag
                    )
                }
            )
        }
        if (bundle.questionAttempts.isNotEmpty()) {
            dao.insertQuestionAttempts(
                bundle.questionAttempts.map { qa ->
                    QuestionAttemptEntity(
                        id = if (remapIds) "qa-import-${UUID.randomUUID()}" else qa.id,
                        workspaceId = wsId.ifBlank { qa.workspaceId },
                        userId = qa.userId.ifBlank { LearnerProfile.DEFAULT_USER_ID },
                        questionId = questionIdMap[qa.questionId] ?: qa.questionId,
                        conceptTag = qa.conceptTag,
                        selectedAnswer = qa.selectedAnswer,
                        isCorrect = qa.isCorrect,
                        sourceMode = qa.sourceMode,
                        answeredAt = qa.answeredAt
                    )
                }
            )
        }
        bundle.assessments.forEach { a ->
            dao.insertAssessment(
                AssessmentEntity(
                    id = assessmentIdMap[a.id] ?: a.id,
                    workspaceId = wsId.ifBlank { a.workspaceId },
                    title = a.title,
                    kind = a.kind,
                    questionIdsJson = runCatching {
                        json.encodeToString(
                            json.decodeFromString<List<String>>(a.questionIdsJson)
                                .map { questionIdMap[it] ?: it }
                        )
                    }.getOrDefault(a.questionIdsJson),
                    timeLimitMinutes = a.timeLimitMinutes,
                    passScorePercent = a.passScorePercent,
                    createdAt = a.createdAt
                )
            )
        }
        bundle.attempts.forEach { at ->
            dao.insertAssessmentAttempt(
                AssessmentAttemptEntity(
                    id = attemptIdMap[at.id] ?: at.id,
                    assessmentId = assessmentIdMap[at.assessmentId] ?: at.assessmentId,
                    assessmentTitle = at.assessmentTitle,
                    workspaceId = wsId.ifBlank { at.workspaceId },
                    correctCount = at.correctCount,
                    totalQuestions = at.totalQuestions,
                    scorePercent = at.scorePercent,
                    passed = at.passed,
                    durationSeconds = at.durationSeconds,
                    earnedXp = at.earnedXp,
                    completedAt = at.completedAt,
                    userId = at.userId
                )
            )
        }
        bundle.progress.forEach { p ->
            dao.upsertLessonProgress(
                LessonProgressEntity(
                    workspaceId = wsId.ifBlank { p.workspaceId },
                    bookKey = p.bookKey,
                    unitKey = p.unitKey,
                    lessonKey = p.lessonKey,
                    completed = p.completed,
                    pointsEarned = p.pointsEarned,
                    completedAt = p.completedAt,
                    userId = p.userId
                )
            )
        }
        bundle.teacherNotes.forEach { n ->
            dao.upsertTeacherNote(
                TeacherNoteEntity(
                    workspaceId = wsId.ifBlank { n.workspaceId },
                    bookKey = n.bookKey,
                    unitKey = n.unitKey,
                    lessonKey = n.lessonKey,
                    noteText = n.noteText,
                    bookmarked = n.bookmarked,
                    updatedAt = n.updatedAt
                )
            )
        }
        if (bundle.studyDayRecords.isNotEmpty()) {
            bundle.studyDayRecords.forEach { day ->
                if (day.activityDate.isNotBlank() && dao.getStudyDay(day.activityDate, day.userId) == null) {
                    dao.upsertStudyDay(
                        StudyDayEntity(
                            activityDate = day.activityDate,
                            actionsCount = day.actionsCount,
                            lastUpdatedAt = day.lastUpdatedAt,
                            userId = day.userId
                        )
                    )
                }
            }
        } else {
            // Backward compatibility with edu-mini-backup-1.1 files that stored dates only.
            bundle.studyDays.forEach { dateIso ->
                if (dateIso.isNotBlank() && dao.getStudyDay(dateIso) == null) {
                    dao.upsertStudyDay(
                        StudyDayEntity(
                            activityDate = dateIso,
                            actionsCount = 1,
                            lastUpdatedAt = Instant.now().toString()
                        )
                    )
                }
            }
        }
        bundle.promptTemplates.forEach { t ->
            dao.insertPromptTemplate(
                PromptTemplateEntity(
                    id = t.id,
                    title = t.title,
                    task = t.task,
                    instructions = t.instructions,
                    createdAt = t.createdAt,
                    responseSchemaJson = t.responseSchemaJson,
                    responseMappingJson = t.responseMappingJson,
                    responseFormat = t.responseFormat,
                    sourceAttachmentMode = t.sourceAttachmentMode,
                    version = t.version
                )
            )
        }
        "تمت استعادة ${bundle.drafts.size} مسودة، ${bundle.questions.size} سؤال، ${bundle.assessments.size} تقييم، و${bundle.teacherNotes.size} ملاحظة بنجاح."
    }

    fun observeAssessmentsForWorkspace(workspaceId: String): Flow<List<AssessmentRecord>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        return dao.observeAssessmentsForWorkspace(workspaceId).map { list ->
            list.map { e ->
                val ids = runCatching { json.decodeFromString<List<String>>(e.questionIdsJson) }.getOrDefault(emptyList())
                AssessmentRecord(
                    id = e.id,
                    workspaceId = e.workspaceId,
                    title = e.title,
                    kind = AssessmentKind.fromId(e.kind),
                    questionIds = ids,
                    timeLimitMinutes = e.timeLimitMinutes,
                    passScorePercent = e.passScorePercent,
                    createdAt = e.createdAt
                )
            }
        }
    }

    suspend fun createAssessment(
        workspaceId: String,
        title: String,
        kind: AssessmentKind,
        questionIds: List<String>,
        timeLimitMinutes: Int,
        passScorePercent: Int
    ): AssessmentRecord {
        val record = AssessmentRecord(
            id = "asm-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            workspaceId = workspaceId,
            title = title.trim(),
            kind = kind,
            questionIds = questionIds.distinct(),
            timeLimitMinutes = timeLimitMinutes.coerceIn(0, 180),
            passScorePercent = passScorePercent.coerceIn(10, 100),
            createdAt = Instant.now().toString()
        )
        dao.insertAssessment(
            AssessmentEntity(
                id = record.id,
                workspaceId = record.workspaceId,
                title = record.title,
                kind = record.kind.id,
                questionIdsJson = json.encodeToString(record.questionIds),
                timeLimitMinutes = record.timeLimitMinutes,
                passScorePercent = record.passScorePercent,
                createdAt = record.createdAt
            )
        )
        return record
    }

    suspend fun deleteAssessment(assessmentId: String) {
        dao.deleteAssessmentById(assessmentId)
    }

    fun observeAssessmentAttempts(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): Flow<List<AssessmentAttemptRecord>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        return dao.observeAssessmentAttempts(workspaceId, safeUserId).map { list ->
            list.map { e ->
                AssessmentAttemptRecord(
                    id = e.id,
                    assessmentId = e.assessmentId,
                    assessmentTitle = e.assessmentTitle,
                    workspaceId = e.workspaceId,
                    correctCount = e.correctCount,
                    totalQuestions = e.totalQuestions,
                    scorePercent = e.scorePercent,
                    passed = e.passed,
                    durationSeconds = e.durationSeconds,
                    earnedXp = e.earnedXp,
                    completedAt = e.completedAt
                )
            }
        }
    }

    suspend fun recordAssessmentAttempt(
        assessment: AssessmentRecord,
        correctCount: Int,
        totalQuestions: Int,
        scorePercent: Int,
        durationSeconds: Int,
        earnedXp: Int,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): AssessmentAttemptRecord {
        val passed = scorePercent >= assessment.passScorePercent
        val record = AssessmentAttemptRecord(
            id = "att-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            assessmentId = assessment.id,
            assessmentTitle = assessment.title,
            workspaceId = assessment.workspaceId,
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            scorePercent = scorePercent,
            passed = passed,
            durationSeconds = durationSeconds,
            earnedXp = earnedXp,
            completedAt = Instant.now().toString()
        )
        dao.insertAssessmentAttempt(
            AssessmentAttemptEntity(
                id = record.id,
                assessmentId = record.assessmentId,
                assessmentTitle = record.assessmentTitle,
                workspaceId = record.workspaceId,
                correctCount = record.correctCount,
                totalQuestions = record.totalQuestions,
                scorePercent = record.scorePercent,
                passed = record.passed,
                durationSeconds = record.durationSeconds,
                earnedXp = record.earnedXp,
                completedAt = record.completedAt,
                userId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
            )
        )
        recordStudyActivity(userId = userId)
        return record
    }

    fun observeTeacherNote(workspaceId: String, lesson: LessonRef): Flow<TeacherNoteRecord?> {
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        return dao.observeTeacherNote(canonicalWs, lesson.bookKey, lesson.unitKey, lesson.lessonKey).map { e ->
            e?.let {
                TeacherNoteRecord(
                    workspaceId = it.workspaceId,
                    bookKey = it.bookKey,
                    unitKey = it.unitKey,
                    lessonKey = it.lessonKey,
                    noteText = it.noteText,
                    bookmarked = it.bookmarked,
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    fun observeTeacherNotesForWorkspace(workspaceId: String): Flow<List<TeacherNoteRecord>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        return dao.observeTeacherNotesForWorkspace(workspaceId).map { list ->
            list.map { e ->
                TeacherNoteRecord(
                    workspaceId = e.workspaceId,
                    bookKey = e.bookKey,
                    unitKey = e.unitKey,
                    lessonKey = e.lessonKey,
                    noteText = e.noteText,
                    bookmarked = e.bookmarked,
                    updatedAt = e.updatedAt
                )
            }
        }
    }

    suspend fun saveTeacherNote(
        workspaceId: String,
        lesson: LessonRef,
        noteText: String,
        bookmarked: Boolean
    ) {
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        dao.upsertTeacherNote(
            TeacherNoteEntity(
                workspaceId = canonicalWs,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                noteText = noteText.trim(),
                bookmarked = bookmarked,
                updatedAt = Instant.now().toString()
            )
        )
    }

    fun observePromptTemplates(): Flow<List<PromptTemplateRecord>> {
        return dao.observePromptTemplates().map { custom ->
            custom.map { e ->
                PromptTemplateRecord(
                    id = e.id,
                    title = e.title,
                    task = TaskType.fromId(e.task),
                    instructions = e.instructions,
                    isBuiltIn = e.id.startsWith("builtin_"),
                    responseSchemaJson = e.responseSchemaJson,
                    responseMappingJson = e.responseMappingJson,
                    responseFormat = e.responseFormat,
                    sourceAttachmentMode = e.sourceAttachmentMode,
                    version = e.version
                )
            }
        }
    }

    suspend fun ensureDefaultPromptTemplates() {
        val now = Instant.now().toString()
        dao.insertPromptTemplates(
            DEFAULT_PROMPT_TEMPLATES.map { template ->
                PromptTemplateEntity(
                    id = template.id,
                    title = template.title,
                    task = template.task.id,
                    instructions = template.instructions,
                    createdAt = now,
                    responseSchemaJson = template.responseSchemaJson,
                    responseMappingJson = template.responseMappingJson,
                    responseFormat = template.responseFormat,
                    sourceAttachmentMode = template.sourceAttachmentMode,
                    version = template.version
                )
            }
        )
    }

    suspend fun savePromptTemplate(
        title: String,
        task: TaskType,
        instructions: String,
        responseSchemaJson: String = "",
        responseMappingJson: String = "",
        responseFormat: String = if (responseSchemaJson.isBlank()) "text" else "json",
        sourceAttachmentMode: String = "pdf_native",
        templateId: String? = null
    ) {
        require(responseSchemaJson.isBlank() || runCatching { json.parseToJsonElement(responseSchemaJson).jsonObject }.isSuccess) {
            "مخطط الاستجابة يجب أن يكون JSON object صالحاً."
        }
        require(responseMappingJson.isBlank() || runCatching { json.parseToJsonElement(responseMappingJson).jsonObject }.isSuccess) {
            "خريطة توافق الحقول يجب أن تكون JSON object صالحة."
        }
        if (!templateId.isNullOrBlank()) {
            dao.updatePromptTemplate(
                templateId = templateId,
                title = title.trim(),
                task = task.id,
                instructions = instructions.trim(),
                responseSchemaJson = responseSchemaJson.trim(),
                responseMappingJson = responseMappingJson.trim(),
                responseFormat = responseFormat,
                sourceAttachmentMode = sourceAttachmentMode
            )
            return
        }
        dao.insertPromptTemplate(
            PromptTemplateEntity(
                id = "tpl-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(5)}",
                title = title.trim(),
                task = task.id,
                instructions = instructions.trim(),
                createdAt = Instant.now().toString(),
                responseSchemaJson = responseSchemaJson.trim(),
                responseMappingJson = responseMappingJson.trim(),
                responseFormat = responseFormat,
                sourceAttachmentMode = sourceAttachmentMode
            )
        )
    }

    suspend fun deletePromptTemplate(id: String) {
        dao.deletePromptTemplateById(id)
    }

    private fun DraftEntity.toDraftRecord() = DraftRecord(
        id = id,
        workspaceId = workspaceId,
        grade = gradeKey,
        bookKey = bookKey,
        unitKey = unitKey,
        lessonKey = lessonKey,
        task = TaskType.fromId(task),
        title = title,
        provider = provider,
        model = model,
        createdAt = createdAt,
        content = content,
        status = status,
        version = version,
        sourcePages = sourcePages,
        sourceHash = sourceHash,
        templateId = templateId,
        templateVersion = templateVersion,
        responseSchemaHash = responseSchemaHash,
        requestFingerprint = requestFingerprint,
        contentHash = contentHash,
        sourcePdfHash = sourcePdfHash
    )

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
            createdAt = createdAt,
            conceptTag = conceptTag.ifBlank { lessonTitle }
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
        createdAt = createdAt,
        conceptTag = conceptTag.ifBlank { lessonTitle }
    )

    private fun QuestionAttemptEntity.toModel() = QuestionAttemptRecord(
        id = id,
        workspaceId = workspaceId,
        userId = userId,
        questionId = questionId,
        conceptTag = conceptTag,
        selectedAnswer = selectedAnswer,
        isCorrect = isCorrect,
        sourceMode = sourceMode,
        answeredAt = answeredAt
    )

    private fun sha256Full(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun sha256Short(text: String): String? {
        if (text.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun WorkspaceIndexEntity.toModel() = WorkspaceIndexRef(
        bookKey = bookKey,
        indexUriOrPath = indexUri,
        indexRelativePath = indexRelativePath,
        manifestUriOrPath = manifestUri,
        manifestRelativePath = manifestRelativePath,
        indexSha256 = indexSha256,
        manifestSha256 = manifestSha256,
        sourceSha256 = sourceSha256,
        indexSchemaVersion = indexSchemaVersion,
        manifestVersion = manifestVersion,
        lastSyncedAt = lastSyncedAt
    )

    private fun WorkspaceUnitEntity.toModel() = WorkspaceUnitRef(
        bookKey = bookKey,
        unitKey = unitKey,
        title = title,
        partKey = partKey,
        sortOrder = sortOrder,
        sourceIndexPath = sourceIndexPath
    )

    private fun BookEntity.toModel() = BookRef(
        bookKey = bookKey,
        title = title,
        subjectKey = subjectKey,
        componentKey = componentKey,
        relativePath = relativePath,
        lessonsCount = lessonsCount,
        coverPath = coverPath,
        sourcePdfPath = sourcePdfPath,
        subjectName = subjectName
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
        workspaceId = wsId,
        sourceTextPath = sourceTextPath,
        interactivePath = interactivePath,
        printedPageRange = printedPageRange
    )

    fun openOutputStreamForUri(uri: Uri): OutputStream? {
        return appContext?.contentResolver?.openOutputStream(uri)
    }

    fun readUtf8FromUri(uri: Uri): String? {
        return appContext?.contentResolver?.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    }

    fun readLearnerPref(prefsName: String, key: String): String? {
        return appContext?.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            ?.getString(key, null)
    }

    fun writeLearnerPrefs(prefsName: String, pairs: Map<String, String>) {
        val prefs = appContext?.getSharedPreferences(prefsName, Context.MODE_PRIVATE) ?: return
        prefs.edit().apply {
            pairs.forEach { (k, v) -> putString(k, v) }
        }.apply()
    }

    suspend fun getWorkspaceDraftsSnapshot(workspaceId: String, rootPath: String): List<DraftRecord> {
        return dao.observeDraftsForWorkspace(workspaceId, rootPath).first().map { it.toDraftRecord() }
    }

    suspend fun getWorkspaceProgressSnapshot(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): List<LessonProgressEntity> {
        return dao.observeProgressForWorkspace(
            workspaceId,
            userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        ).first()
    }

    companion object {
        const val LESSON_QUIZ_PASS_PERCENT = 60
        const val POINTS_LESSON_COMPLETED = 50
        const val POINTS_APPROVED_DRAFT = 20
        const val POINTS_DRAFT_CREATED = 10
        const val POINTS_PUBLISHED_PACKAGE = 25
        const val POINTS_PACKAGE_UPLOADED = 15
        const val POINTS_MAX_PER_LESSON = POINTS_LESSON_COMPLETED + POINTS_APPROVED_DRAFT + POINTS_PUBLISHED_PACKAGE

        val DEFAULT_PROMPT_TEMPLATES = listOf(
            PromptTemplateRecord(
                id = "builtin_scaffolding",
                title = "شرح مبسط مع مثال حياتي",
                task = TaskType.EXPLANATION,
                instructions = "قسّم الشرح إلى 3 خطوات واضحة، أضف مثالاً من الحياة اليومية، واختم بقاعدة ذهبية للحفظ.",
                isBuiltIn = true,
                responseSchemaJson = Indexer2PromptRegistry.blueprintFor(TaskType.EXPLANATION).schemaSnippet
            ),
            PromptTemplateRecord(
                id = "builtin_vocab_cards",
                title = "مسرد مصطلحات سياقي",
                task = TaskType.VOCABULARY,
                instructions = "استخرج أهم المصطلحات في جدول منظم: المصطلح، المعنى المبسط، ومثال من سياق الدرس.",
                isBuiltIn = true,
                responseSchemaJson = Indexer2PromptRegistry.blueprintFor(TaskType.VOCABULARY).schemaSnippet
            ),
            PromptTemplateRecord(
                id = "builtin_bloom_questions",
                title = "أسئلة متدرجة (تذكر/فهم/تطبيق)",
                task = TaskType.QUESTIONS,
                instructions = "وزّع الأسئلة بين مستويات بلوم: سؤال تذكر مباشر، سؤال فهم واستنتاج، وسؤال تطبيقي مع الإجابة والتفسير.",
                isBuiltIn = true,
                responseSchemaJson = """
                    {
                      "type": "OBJECT",
                      "properties": {
                        "questions": {
                          "type": "ARRAY",
                          "items": {
                            "type": "OBJECT",
                            "properties": {
                              "type": { "type": "STRING" },
                              "conceptTag": { "type": "STRING" },
                              "prompt": { "type": "STRING" },
                              "options": { "type": "ARRAY", "items": { "type": "STRING" } },
                              "correctAnswer": { "type": "STRING" },
                              "rationale": { "type": "STRING" },
                              "difficulty": { "type": "STRING" },
                              "sourcePages": { "type": "STRING" }
                            },
                            "required": ["type", "prompt", "correctAnswer", "difficulty"]
                          }
                        }
                      },
                      "required": ["questions"]
                    }
                """.trimIndent(),
                responseFormat = "json"
            ),
            PromptTemplateRecord(
                id = "builtin_interactive_steps",
                title = "درس تفاعلي متكامل (JSON + JS + HTML)",
                task = TaskType.INTERACTIVE,
                instructions = "أنشئ 3 محطات تفاعلية بصيغة JSON بحيث تحتوي كل محطة conceptTag صريحاً ونقطة تحقق اختيار من متعدد مرتبطة ببنك الأسئلة ومهمة ترتيب وتوصيل ليُعرض عبر محرك JS و HTML.",
                isBuiltIn = true,
                responseSchemaJson = """
                    {
                      "type": "OBJECT",
                      "properties": {
                        "schemaVersion": { "type": "INTEGER" },
                        "title": { "type": "STRING" },
                        "objective": { "type": "STRING" },
                        "steps": {
                          "type": "ARRAY",
                          "items": {
                            "type": "OBJECT",
                            "properties": {
                              "title": { "type": "STRING" },
                              "conceptTag": { "type": "STRING" },
                              "body": { "type": "STRING" },
                              "checkpoint": {
                                "type": "OBJECT",
                                "properties": {
                                  "conceptTag": { "type": "STRING" },
                                  "question": { "type": "STRING" },
                                  "options": { "type": "ARRAY", "items": { "type": "STRING" } },
                                  "answer": { "type": "STRING" }
                                },
                                "required": ["question", "options", "answer"]
                              }
                            },
                            "required": ["title", "conceptTag", "body"]
                          }
                        },
                        "ordering": { "type": "ARRAY", "items": { "type": "STRING" } },
                        "matching": {
                          "type": "ARRAY",
                          "items": {
                            "type": "OBJECT",
                            "properties": {
                              "left": { "type": "STRING" },
                              "right": { "type": "STRING" }
                            },
                            "required": ["left", "right"]
                          }
                        }
                      },
                      "required": ["title", "steps"]
                    }
                """.trimIndent(),
                responseFormat = "json"
            )
        )
    }
}
