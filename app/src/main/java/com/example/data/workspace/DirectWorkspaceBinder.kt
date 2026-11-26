package com.example.data.workspace

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.DraftEntity
import com.example.data.local.InteractivePackageEntity
import com.example.data.repository.QuestionBankEngine
import com.example.model.BookRef
import com.example.model.DirectGradeChoice
import com.example.model.DirectWorkspaceInspection
import com.example.model.ImportedWorkspace
import com.example.model.InteractivePackageReport
import com.example.model.LessonRef
import com.example.model.WorkspaceIndexRef
import com.example.model.WorkspaceUnitRef
import com.example.model.PackageKind
import com.example.model.QuestionItem
import com.example.model.SecurityFinding
import com.example.model.SecuritySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class DirectBoundWorkspaceResult(
    val workspace: ImportedWorkspace,
    val preloadedQuestions: List<QuestionItem>,
    val preloadedDrafts: List<DraftEntity>,
    val preloadedInteractivePackages: List<InteractivePackageEntity>
)

/**
 * Permissioned, zero-copy workspace binder.
 * Works directly on Android SAF DocumentTree URIs (`OpenDocumentTree`) or local filesystem directories.
 * PDF, cover, and lesson assets remain in the selected source; only metadata and file references are stored in Room.
 */
class DirectWorkspaceBinder(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    data class IndexedEntry(
        val relativePath: String,
        val uriOrPath: String,
        val sizeBytes: Long
    )

    fun takePersistableReadPermission(treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            val alreadyGranted = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission
            }
            if (!alreadyGranted) {
                throw IllegalStateException(
                    "لم يتمكن التطبيق من حفظ صلاحية الوصول الدائم إلى مساحة العمل. أعد اختيار المجلد وامنح صلاحية القراءة.",
                    e
                )
            }
        }
    }

    suspend fun inspectTreeUri(treeUri: Uri): DirectWorkspaceInspection = withContext(Dispatchers.IO) {
        takePersistableReadPermission(treeUri)
        val entries = indexSafTree(treeUri)
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val folderName = UriIO.displayName(context, rootDocUri.toString())
            .ifBlank { rootDocId.substringAfterLast('/').substringAfterLast(':') }
            .ifBlank { "مساحة العمل المحلية" }
        inspectIndexedEntries(
            rootUriOrPath = treeUri.toString(),
            folderDisplayName = folderName,
            isSafUri = true,
            entries = entries
        )
    }

    suspend fun inspectDirectory(directory: File): DirectWorkspaceInspection = withContext(Dispatchers.IO) {
        require(directory.isDirectory) { "المجلد المحدد غير موجود: ${directory.absolutePath}" }
        val entries = indexLocalDirectory(directory)
        inspectIndexedEntries(
            rootUriOrPath = directory.absolutePath,
            folderDisplayName = directory.name.ifBlank { "مساحة العمل" },
            isSafUri = false,
            entries = entries
        )
    }

    suspend fun bindSingleGrade(
        rootUriOrPath: String,
        gradeKey: String
    ): DirectBoundWorkspaceResult = withContext(Dispatchers.IO) {
        val isSaf = UriIO.isContentUri(rootUriOrPath)
        if (isSaf) takePersistableReadPermission(Uri.parse(rootUriOrPath))
        val entries: List<IndexedEntry>
        val folderName: String
        if (isSaf) {
            val treeUri = Uri.parse(rootUriOrPath)
            entries = indexSafTree(treeUri)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            folderName = UriIO.displayName(context, rootDocUri.toString())
                .ifBlank { rootDocId.substringAfterLast('/') }
        } else {
            val dir = File(rootUriOrPath)
            require(dir.isDirectory) { "تعذر فتح مجلد مساحة العمل: $rootUriOrPath" }
            entries = indexLocalDirectory(dir)
            folderName = dir.name
        }

        val entryMap = entries.associateBy { it.relativePath.lowercase() }
        val warnings = mutableListOf<String>()
        val rejectionErrors = mutableListOf<String>()
        val discoveredIndexes = findBookIndexes(entries, folderName, entryMap)
            .filter { it.gradeKey.equals(gradeKey, ignoreCase = true) }

        require(discoveredIndexes.isNotEmpty()) {
            "لم يتم العثور على كتب صالحة للصف $gradeKey داخل المجلد المحدد."
        }

        val workspaceId = computeStableWorkspaceId(rootUriOrPath, gradeKey)
        val books = mutableListOf<BookRef>()
        val lessons = mutableListOf<LessonRef>()
        val indexRecords = mutableListOf<WorkspaceIndexRef>()
        val unitRefs = mutableListOf<WorkspaceUnitRef>()
        val preloadedQuestions = mutableListOf<QuestionItem>()
        val preloadedDrafts = mutableListOf<DraftEntity>()
        val preloadedPackages = mutableListOf<InteractivePackageEntity>()

        for (slot in discoveredIndexes) {
            val bookRel = slot.bookRelPath.ifBlank { slot.bookKey }
            val componentError = componentIdentityError(slot)
            if (componentError != null) {
                rejectionErrors.add("$bookRel: $componentError")
                continue
            }
            val manifestEntry = firstExisting(
                entryMap,
                joinRel(slot.bookRelPath, "book_manifest.json"),
                joinRel(slot.bookRelPath, "manifest.json")
            )
            val manifestRaw = manifestEntry?.let { entry ->
                val raw = UriIO.readText(context, entry.uriOrPath)
                if (raw.isNullOrBlank()) {
                    rejectionErrors.add("$bookRel: تعذر قراءة book_manifest.json")
                    continue
                }
                raw
            }
            val manifestObj: JsonObject? = manifestRaw?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    json.parseToJsonElement(raw).jsonObject
                }.onFailure {
                    warnings.add("$bookRel: تعذر قراءة manifest")
                }.getOrNull()
            }

            val indexRaw = UriIO.readText(context, slot.indexEntry.uriOrPath).orEmpty()
            val indexObj: JsonObject = runCatching {
                json.parseToJsonElement(indexRaw).jsonObject
            }.getOrElse {
                rejectionErrors.add("$bookRel: index.json غير صالح")
                continue
            }

            val validation = WorkspaceContractValidator.validate(
                indexRaw = indexRaw,
                manifestRaw = manifestRaw,
                expectedGrade = gradeKey,
                expectedSubject = slot.subjectKey,
                bookRelativePath = slot.bookRelPath,
                availableRelativePaths = entries.map { it.relativePath }.toSet()
            )
            warnings.addAll(validation.warnings.map { "$bookRel: $it" })
            if (!validation.isValid) {
                rejectionErrors.addAll(validation.errors.map { "$bookRel: $it" })
                continue
            }

            val indexMetadata = indexObj["metadata"] as? JsonObject
            val indexBook = indexObj["book"] as? JsonObject
            val declaredGradeName = firstNonBlank(
                indexObj.str("gradeName"),
                indexMetadata?.str("gradeName"),
                indexBook?.str("gradeName"),
                manifestObj?.str("gradeName")
            )
            val declaredSubjectName = firstNonBlank(
                indexMetadata?.str("subjectName"),
                indexBook?.str("subjectName"),
                manifestObj?.str("subjectName"),
                indexObj.str("subjectName")
            )
            val subjectName = CurriculumDisplayMapper.subjectDisplayName(
                subjectKey = slot.subjectKey,
                componentKey = slot.componentKey,
                declaredSubjectName = declaredSubjectName
            )
            val componentSuffix = CurriculumDisplayMapper.componentDisplayName(slot.componentKey)
            val fallbackTitle = if (componentSuffix != null && !subjectName.contains(componentSuffix)) {
                "$subjectName • $componentSuffix"
            } else {
                subjectName
            }
            val bookTitle = firstNonBlank(
                indexObj.str("title"),
                indexObj.str("displayName"),
                indexBook?.str("title"),
                manifestObj?.str("bookTitle"),
                manifestObj?.str("title"),
                manifestObj?.str("displayTitle"),
                manifestObj?.str("subjectTitle"),
                fallbackTitle
            )

            val manifestCover = manifestObj?.str("cover")?.trim()?.removePrefix("./")?.takeIf { it.isNotBlank() }
            val resolvedCoverEntry = firstExisting(
                entryMap,
                *buildList {
                    if (manifestCover != null) add(joinRel(bookRel, manifestCover))
                    add(joinRel(bookRel, "cover.png"))
                    add(joinRel(bookRel, "cover.jpg"))
                    add(joinRel(bookRel, "cover.jpeg"))
                    add(joinRel(bookRel, "cover.webp"))
                    add(joinRel(bookRel, "assets/cover.png"))
                    add(joinRel(bookRel, "P01/cover.png"))
                    if (slot.gradePrefixRel.isNotBlank()) {
                        add(joinRel(slot.gradePrefixRel, "${slot.subjectKey}/cover.png"))
                    }
                }.toTypedArray()
            )

            val resolvedBookPdfEntry = firstExisting(
                entryMap,
                joinRel(bookRel, "book.pdf"),
                joinRel(bookRel, "${slot.subjectKey.lowercase()}.pdf"),
                joinRel(bookRel, "P01/book.pdf"),
                joinRel(bookRel, "source.pdf")
            ) ?: entries.firstOrNull { entry ->
                val relLower = entry.relativePath.lowercase()
                val prefixLower = if (bookRel.isBlank()) "" else "${bookRel.lowercase()}/"
                (prefixLower.isEmpty() || relLower.startsWith(prefixLower)) &&
                    relLower.endsWith(".pdf") &&
                    !relLower.contains("/units/")
            }

            val parsedLessons = runCatching {
                parseLessonsForBook(
                    index = indexObj,
                    manifest = manifestObj,
                    grade = gradeKey,
                    workspaceId = workspaceId,
                    rootUriOrPath = rootUriOrPath,
                    bookKey = slot.bookKey,
                    bookTitle = bookTitle,
                    subjectKey = slot.subjectKey,
                    componentKey = slot.componentKey,
                    bookRel = slot.bookRelPath,
                    entries = entries,
                    entryMap = entryMap
                )
            }.getOrElse {
                rejectionErrors.add("$bookRel: تعذر ربط الدروس (${slot.indexEntry.relativePath}): ${it.message}")
                continue
            }

            val missingLessonPdfs = parsedLessons.filter { it.pdfPath.isNullOrBlank() }
            val unreadableLessonPdfs = parsedLessons.filter { lesson ->
                !lesson.pdfPath.isNullOrBlank() && !hasPdfHeaderOrExists(lesson.pdfPath)
            }
            if (validation.canonicalContract && (missingLessonPdfs.isNotEmpty() || unreadableLessonPdfs.isNotEmpty())) {
                val sampleBad = (missingLessonPdfs + unreadableLessonPdfs).take(2).joinToString("، ") {
                    "${it.unitKey}/${it.lessonKey} (${it.title})"
                }
                rejectionErrors.add("$bookRel: تعذر فتح أو مطابقة ملفات PDF لبعض الدروس [$sampleBad]؛ تحقق من مسار P01/P02 أو sliceFile.workspaceUri.")
                continue
            }
            unreadableLessonPdfs.forEach { lesson ->
                warnings.add("$bookRel: ملف PDF غير قابل للفتح للدرس ${lesson.title}.")
            }

            val syncTimestamp = System.currentTimeMillis()
            indexRecords.add(
                WorkspaceIndexRef(
                    bookKey = slot.bookKey,
                    indexUriOrPath = slot.indexEntry.uriOrPath,
                    indexRelativePath = slot.indexEntry.relativePath,
                    manifestUriOrPath = manifestEntry?.uriOrPath,
                    manifestRelativePath = manifestEntry?.relativePath,
                    indexSha256 = sha256(indexRaw),
                    manifestSha256 = manifestRaw?.let(::sha256),
                    sourceSha256 = manifestObj?.str("sourceSha256")?.takeIf { it.isNotBlank() },
                    indexSchemaVersion = firstNonBlank(indexObj.str("indexVersion"), indexObj.str("schemaVersion")).takeIf { it.isNotBlank() },
                    manifestVersion = manifestObj?.str("manifestVersion")?.takeIf { it.isNotBlank() },
                    lastSyncedAt = syncTimestamp
                )
            )
            (indexObj["units"] as? JsonArray).orEmpty().forEachIndexed { unitOrder, unitElement ->
                val unit = unitElement as? JsonObject ?: return@forEachIndexed
                val unitKey = WorkspaceContractValidator.sourceUnitKey(unit)
                if (unitKey.isBlank()) return@forEachIndexed
                val resolvedUnitPart = WorkspaceContractValidator.resolveUnitPartKey(unit, indexObj, manifestObj)
                    .takeIf { it.isNotBlank() }
                unitRefs.add(
                    WorkspaceUnitRef(
                        bookKey = slot.bookKey,
                        unitKey = unitKey,
                        title = firstNonBlank(unit.str("title"), unit.str("unitTitle"), unit.str("name"), unitKey),
                        partKey = resolvedUnitPart,
                        sortOrder = unitOrder,
                        sourceIndexPath = slot.indexEntry.uriOrPath
                    )
                )
            }

            books.add(
                BookRef(
                    bookKey = slot.bookKey,
                    title = bookTitle,
                    subjectKey = slot.subjectKey,
                    componentKey = slot.componentKey,
                    relativePath = bookRel.ifBlank { slot.bookKey },
                    lessonsCount = parsedLessons.size,
                    coverPath = resolvedCoverEntry?.uriOrPath,
                    sourcePdfPath = resolvedBookPdfEntry?.uriOrPath,
                    subjectName = subjectName
                )
            )
            lessons.addAll(parsedLessons)
        }

        if (books.isEmpty()) {
            val details = rejectionErrors.distinct().take(6).ifEmpty { warnings.distinct().take(6) }
            throw IllegalArgumentException(
                "تعذر ربط أي كتاب صالح للصف $gradeKey (${discoveredIndexes.size} فهرس مكتشف):\n" +
                    details.joinToString("\n")
            )
        }
        val skippedBooksCount = (discoveredIndexes.size - books.size).coerceAtLeast(0)
        val finalWarnings = buildList {
            if (skippedBooksCount > 0) {
                add("تم ربط ${books.size} من ${discoveredIndexes.size} كتاباً؛ تعذر ربط $skippedBooksCount كتاباً:")
                addAll(rejectionErrors.distinct().take(6))
            }
            addAll(warnings.distinct())
        }.distinct()

        // Load pre-generated Question Bank JSON and Interactive HTML packages directly in-place
        val now = Instant.now().toString()
        val seenQuestionFingerprints = HashSet<String>()
        for (lesson in lessons) {
            findPreGeneratedQuestions(entries, lesson).forEach { qEntry ->
                val raw = UriIO.readText(context, qEntry.uriOrPath).orEmpty()
                if (raw.isNotBlank()) {
                    val parsed = QuestionBankEngine.parseQuestionsFromText(raw, workspaceId, lesson)
                    parsed.forEach { item ->
                        val dedupKey = "${lesson.bookKey}|${lesson.unitKey}|${lesson.lessonKey}|${item.fingerprint}"
                        if (seenQuestionFingerprints.add(dedupKey)) {
                            preloadedQuestions.add(item.copy(reviewed = true))
                        }
                    }
                }
            }

            val htmlEntryPath = lesson.interactivePath
            if (!htmlEntryPath.isNullOrBlank()) {
                val htmlEntryObj = entries.firstOrNull { it.uriOrPath == htmlEntryPath }
                val baseRel = htmlEntryObj?.relativePath?.substringBeforeLast('/', "") ?: ""
                val entryName = htmlEntryObj?.relativePath?.substringAfterLast('/') ?: "index.html"
                val siblingEntries = if (baseRel.isBlank()) {
                    listOfNotNull(htmlEntryObj)
                } else {
                    entries.filter { it.relativePath.startsWith("$baseRel/") }
                }
                val packageRootRef = if (isSaf) {
                    buildVirtualSafPackageRoot(rootUriOrPath, baseRel)
                } else {
                    val f = File(htmlEntryPath)
                    f.parentFile?.absolutePath ?: rootUriOrPath
                }
                val report = InteractivePackageReport(
                    kind = PackageKind.STANDALONE_HTML,
                    fileName = entryName,
                    entryRelativePath = entryName,
                    totalFiles = siblingEntries.size.coerceAtLeast(1),
                    totalBytes = siblingEntries.sumOf { it.sizeBytes },
                    has3dAssets = siblingEntries.any {
                        it.relativePath.substringAfterLast('.', "").lowercase() in setOf("glb", "gltf", "obj", "hdr", "usdz")
                    },
                    assetExtensions = siblingEntries.map {
                        it.relativePath.substringAfterLast('.', "").lowercase()
                    }.filter { it.isNotBlank() }.distinct(),
                    externalUrls = emptyList(),
                    missingReferencedAssets = emptyList(),
                    findings = listOf(
                        SecurityFinding(
                            severity = SecuritySeverity.PASS,
                            code = "DIRECT_WORKSPACE_HTML",
                            message = "مرتبط مباشرة من مجلد مساحة العمل المحلية بدون نسخ."
                        )
                    ),
                    linkedLessonMatch = true
                )
                preloadedPackages.add(
                    InteractivePackageEntity(
                        id = "pkg-direct-${computeStableWorkspaceId(workspaceId, "${lesson.bookKey}:${lesson.unitKey}:${lesson.lessonKey}")}",
                        workspaceId = workspaceId,
                        gradeKey = gradeKey,
                        bookKey = lesson.bookKey,
                        unitKey = lesson.unitKey,
                        lessonKey = lesson.lessonKey,
                        kind = PackageKind.STANDALONE_HTML.id,
                        title = "الدرس التفاعلي: ${lesson.title}",
                        rootDir = packageRootRef,
                        entryFile = entryName,
                        sourcePages = lesson.pageRange,
                        version = 1,
                        status = "APPROVED",
                        publishedForStudents = true,
                        reportJson = json.encodeToString(report),
                        createdAt = now
                    )
                )
            }
        }

        val boundBookPrefixes = discoveredIndexes.map { it.bookRelPath.trim('/').lowercase() }
        val relevantFilesCount = entries.count { entry ->
            val relLower = entry.relativePath.lowercase()
            boundBookPrefixes.any { prefix ->
                prefix.isEmpty() || relLower == prefix || relLower.startsWith("$prefix/")
            } || relLower.contains("/${gradeKey.lowercase()}/") || relLower.startsWith("${gradeKey.lowercase()}/")
        }.coerceAtLeast(lessons.size)

        val gradeBadge = CurriculumDisplayMapper.gradeBadgeLabel(gradeKey)
        val workspace = ImportedWorkspace(
            workspaceId = workspaceId,
            gradeKey = gradeKey,
            rootPath = rootUriOrPath,
            title = "$gradeBadge • ${books.size} كتاب (مباشر بدون نسخ)",
            books = books,
            lessons = lessons,
            filesCount = relevantFilesCount,
            warnings = finalWarnings,
            importedAt = System.currentTimeMillis(),
            indexRecords = indexRecords,
            units = unitRefs
        )

        DirectBoundWorkspaceResult(
            workspace = workspace,
            preloadedQuestions = preloadedQuestions,
            preloadedDrafts = preloadedDrafts,
            preloadedInteractivePackages = preloadedPackages
        )
    }

    private fun inspectIndexedEntries(
        rootUriOrPath: String,
        folderDisplayName: String,
        isSafUri: Boolean,
        entries: List<IndexedEntry>
    ): DirectWorkspaceInspection {
        if (entries.isEmpty()) {
            throw IllegalArgumentException("المجلد المختار فارغ ولا يحتوي ملفات المنهج.")
        }
        val entryMap = entries.associateBy { it.relativePath.lowercase() }
        val slots = findBookIndexes(entries, folderDisplayName, entryMap)
        if (slots.isEmpty()) {
            throw IllegalArgumentException(
                "لم يتم العثور على index.json داخل المجلد. اختر مجلد مساحة العمل أو مجلد الصف (مثل G04 أو G07)."
            )
        }

        val warnings = mutableListOf<String>()
        val rejectionErrors = mutableListOf<String>()
        val validSlots = mutableListOf<BookIndexSlot>()
        val titleByBook = mutableMapOf<String, String>()
        val lessonCountByBook = mutableMapOf<String, Int>()

        slots.forEach { slot ->
            val bookLabel = slot.bookRelPath.ifBlank { slot.bookKey }
            val componentError = componentIdentityError(slot)
            if (componentError != null) {
                rejectionErrors.add("$bookLabel: $componentError")
                return@forEach
            }
            val manifestEntry = firstExisting(
                entryMap,
                joinRel(slot.bookRelPath, "book_manifest.json"),
                joinRel(slot.bookRelPath, "manifest.json")
            )
            val manifestRaw = manifestEntry?.let { UriIO.readText(context, it.uriOrPath) }
            if (manifestEntry != null && manifestRaw.isNullOrBlank()) {
                rejectionErrors.add("$bookLabel: تعذر قراءة book_manifest.json.")
                return@forEach
            }
            val manifestObj = manifestRaw?.let { raw ->
                runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
                    rejectionErrors.add("$bookLabel: book_manifest.json غير صالح.")
                    return@forEach
                }
            }
            val indexRaw = UriIO.readText(context, slot.indexEntry.uriOrPath)
            if (indexRaw.isNullOrBlank()) {
                rejectionErrors.add("$bookLabel: تعذر قراءة index.json.")
                return@forEach
            }
            val indexObj = runCatching {
                json.parseToJsonElement(indexRaw).jsonObject
            }.getOrElse {
                rejectionErrors.add("$bookLabel: index.json غير صالح.")
                return@forEach
            }

            val validation = WorkspaceContractValidator.validate(
                indexRaw = indexRaw,
                manifestRaw = manifestRaw,
                expectedGrade = slot.gradeKey,
                expectedSubject = slot.subjectKey,
                bookRelativePath = slot.bookRelPath,
                availableRelativePaths = entries.map { it.relativePath }.toSet()
            )
            warnings.addAll(validation.warnings.map { "$bookLabel: $it" })
            if (!validation.isValid) {
                rejectionErrors.addAll(validation.errors.map { "$bookLabel: $it" })
                return@forEach
            }

            val indexMetadata = indexObj["metadata"] as? JsonObject
            val indexBook = indexObj["book"] as? JsonObject
            val declaredSubjectName = firstNonBlank(
                indexMetadata?.str("subjectName"),
                indexBook?.str("subjectName"),
                manifestObj?.str("subjectName"),
                indexObj.str("subjectName")
            )
            val fallbackSubjectName = CurriculumDisplayMapper.subjectDisplayName(
                subjectKey = slot.subjectKey,
                componentKey = slot.componentKey,
                declaredSubjectName = declaredSubjectName
            )
            val bookTitle = firstNonBlank(
                manifestObj?.str("title"),
                manifestObj?.str("bookTitle"),
                manifestObj?.str("displayTitle"),
                indexObj.str("displayName"),
                indexObj.str("title"),
                indexBook?.str("title"),
                fallbackSubjectName
            )
            val parsedLessons = runCatching {
                parseLessonsForBook(
                    index = indexObj,
                    manifest = manifestObj,
                    grade = slot.gradeKey,
                    workspaceId = "inspection",
                    rootUriOrPath = rootUriOrPath,
                    bookKey = slot.bookKey,
                    bookTitle = bookTitle,
                    subjectKey = slot.subjectKey,
                    componentKey = slot.componentKey,
                    bookRel = slot.bookRelPath,
                    entries = entries,
                    entryMap = entryMap
                )
            }.getOrElse {
                rejectionErrors.add("$bookLabel: تعذر ربط الدروس بمفاتيحها المرجعية (${slot.indexEntry.relativePath}): ${it.message}")
                return@forEach
            }

            val invalidPdfs = parsedLessons.filter { lesson ->
                lesson.pdfPath.isNullOrBlank() || !hasPdfHeaderOrExists(lesson.pdfPath)
            }
            if (validation.canonicalContract && invalidPdfs.isNotEmpty()) {
                val sampleBad = invalidPdfs.take(2).joinToString("، ") { "${it.unitKey}/${it.lessonKey}" }
                rejectionErrors.add("$bookLabel: لم تجتز ملفات PDF فحص القراءة ($sampleBad)؛ تم استبعاد الكتاب من خيارات الربط.")
                return@forEach
            }
            if (invalidPdfs.isNotEmpty()) {
                warnings.add("$bookLabel: توجد ${invalidPdfs.size} دروس بلا PDF صالح؛ راجع التحذيرات قبل اعتماد الربط.")
            }

            validSlots.add(slot)
            val bookIdentity = "${slot.gradeKey}|${slot.bookKey}|${slot.bookRelPath}"
            titleByBook[bookIdentity] = bookTitle
            lessonCountByBook[bookIdentity] = parsedLessons.size
        }

        if (validSlots.isEmpty()) {
            val details = rejectionErrors.distinct().take(8).ifEmpty { warnings.distinct().take(8) }
                .joinToString("\n")
            throw IllegalArgumentException(
                "لم يجتز أي كتاب التحقق الأولي؛ لم يتم تنفيذ الربط." +
                    if (details.isBlank()) "" else "\n$details"
            )
        }

        val gradeChoices = validSlots.groupBy { it.gradeKey }.entries
            .sortedBy { it.key }
            .map { (grade, gradeSlots) ->
                val titles = mutableListOf<String>()
                var lessonSum = 0
                gradeSlots.forEach { slot ->
                    val bookIdentity = "${slot.gradeKey}|${slot.bookKey}|${slot.bookRelPath}"
                    titles.add(titleByBook[bookIdentity] ?: slot.subjectKey)
                    lessonSum += lessonCountByBook[bookIdentity] ?: 0
                }
                DirectGradeChoice(
                    gradeKey = grade,
                    booksCount = gradeSlots.size,
                    lessonsCount = lessonSum,
                    bookTitles = titles,
                    gradeDisplayName = CurriculumDisplayMapper.gradeDisplayName(grade)
                )
            }

        return DirectWorkspaceInspection(
            rootUriOrPath = rootUriOrPath,
            displayTitle = folderDisplayName,
            isSafUri = isSafUri,
            grades = gradeChoices,
            totalFilesIndexed = entries.size,
            warnings = warnings.distinct(),
            errors = rejectionErrors.distinct()
        )
    }

    private data class BookIndexSlot(
        val gradeKey: String,
        val gradePrefixRel: String,
        val subjectKey: String,
        val componentKey: String?,
        val pathSubjectKey: String?,
        val pathComponentKey: String?,
        val componentPathIsAuthoritative: Boolean,
        val componentMetadataMismatch: Boolean,
        val bookKey: String,
        val bookRelPath: String,
        val indexEntry: IndexedEntry
    )

    private fun findBookIndexes(
        entries: List<IndexedEntry>,
        folderDisplayName: String,
        entryMap: Map<String, IndexedEntry>
    ): List<BookIndexSlot> {
        val rawIndexFiles = entries.filter { entry ->
            val rel = entry.relativePath
            rel.equals("index.json", ignoreCase = true) ||
                rel.endsWith("/index.json", ignoreCase = true)
        }.filterNot { entry ->
            PART_INDEX_SUFFIX_REGEX.containsMatchIn(entry.relativePath) ||
                AUX_INDEX_DIR_REGEX.containsMatchIn(entry.relativePath)
        }

        val result = mutableListOf<BookIndexSlot>()
        for (entry in rawIndexFiles) {
            val rel = entry.relativePath
            val bookRel = if (rel.equals("index.json", ignoreCase = true)) {
                ""
            } else {
                rel.removeSuffix("/index.json").removeSuffix("/INDEX.JSON")
            }
            val segments = if (bookRel.isBlank()) emptyList() else bookRel.split('/').filter { it.isNotBlank() }
            val manifestEntry = firstExisting(
                entryMap,
                joinRel(bookRel, "book_manifest.json"),
                joinRel(bookRel, "manifest.json")
            )
            val manifestObj = manifestEntry?.let { manifest ->
                runCatching {
                    json.parseToJsonElement(UriIO.readText(context, manifest.uriOrPath).orEmpty()).jsonObject
                }.getOrNull()
            }
            val indexObj = runCatching {
                json.parseToJsonElement(UriIO.readText(context, entry.uriOrPath).orEmpty()).jsonObject
            }.getOrNull()
            val indexMetadata = indexObj?.get("metadata") as? JsonObject
            val indexBook = indexObj?.get("book") as? JsonObject

            fun componentValue(source: JsonObject?): String? =
                firstNonBlank(source?.str("componentKey"), source?.str("component"))
                    .takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }

            val indexComponentValue = componentValue(indexObj) ?: componentValue(indexMetadata) ?: componentValue(indexBook)
            val manifestComponentValue = componentValue(manifestObj)
            val componentMetadataMismatch =
                indexComponentValue != null && manifestComponentValue != null &&
                    !indexComponentValue.equals(manifestComponentValue, ignoreCase = true)

            fun resolveComponent(pathFallback: String?, allowPathFallback: Boolean): Pair<String?, String?> {
                val declared = firstNonBlank(indexComponentValue, manifestComponentValue).takeIf { it.isNotBlank() }
                val cleanPathComp = pathFallback?.trim()?.takeIf { it.isNotBlank() }
                val isSourceDirMarker = cleanPathComp != null && cleanPathComp.startsWith("source-", ignoreCase = true)
                val effectivePathComp = if (isSourceDirMarker && declared == null) null else cleanPathComp
                val resolved = declared ?: if (allowPathFallback) effectivePathComp else null
                return resolved to effectivePathComp
            }

            when {
                // Canonical indexer2 layout: workspace/<grade>/<subject>/[component]/index.json.
                segments.size >= 3 &&
                    segments[0].equals("workspace", ignoreCase = true) &&
                    GRADE_SEGMENT_REGEX.matches(segments[1]) -> {
                    val grade = CurriculumDisplayMapper.normalizeGradeKey(segments[1])
                    val subject = firstNonBlank(
                        indexObj?.str("subjectKey"),
                        indexMetadata?.str("subjectKey"),
                        manifestObj?.str("subjectKey"),
                        segments[2]
                    ).uppercase()
                    val rawPathComponent = segments.getOrNull(3)?.takeIf { it.isNotBlank() }
                    val (component, effectivePathComponent) = resolveComponent(rawPathComponent, allowPathFallback = true)
                    val bookKey = segments.drop(2).joinToString("/")
                    result.add(
                        BookIndexSlot(
                            gradeKey = grade,
                            gradePrefixRel = "workspace/$grade",
                            subjectKey = subject,
                            componentKey = component,
                            pathSubjectKey = segments[2],
                            pathComponentKey = effectivePathComponent,
                            componentPathIsAuthoritative = effectivePathComponent != null || rawPathComponent == null,
                            componentMetadataMismatch = componentMetadataMismatch,
                            bookKey = bookKey,
                            bookRelPath = bookRel,
                            indexEntry = entry
                        )
                    )
                }
                // Grade-root layout: <grade>/<subject>/[component]/index.json.
                segments.size >= 2 && GRADE_SEGMENT_REGEX.matches(segments[0]) -> {
                    val grade = CurriculumDisplayMapper.normalizeGradeKey(segments[0])
                    val subject = firstNonBlank(
                        indexObj?.str("subjectKey"),
                        indexMetadata?.str("subjectKey"),
                        manifestObj?.str("subjectKey"),
                        segments[1]
                    ).uppercase()
                    val rawPathComponent = segments.getOrNull(2)?.takeIf { it.isNotBlank() }
                    val (component, effectivePathComponent) = resolveComponent(rawPathComponent, allowPathFallback = true)
                    val bookKey = segments.drop(1).joinToString("/")
                    result.add(
                        BookIndexSlot(
                            gradeKey = grade,
                            gradePrefixRel = grade,
                            subjectKey = subject,
                            componentKey = component,
                            pathSubjectKey = segments[1],
                            pathComponentKey = effectivePathComponent,
                            componentPathIsAuthoritative = effectivePathComponent != null || rawPathComponent == null,
                            componentMetadataMismatch = componentMetadataMismatch,
                            bookKey = bookKey,
                            bookRelPath = bookRel,
                            indexEntry = entry
                        )
                    )
                }
                // Grade folder selected directly or a single book/component root: extract identity dynamically from index.json / book_manifest.json.
                else -> {
                    val rawGrade = firstNonBlank(
                        indexObj?.str("gradeKey"),
                        indexMetadata?.str("gradeKey"),
                        indexBook?.str("gradeKey"),
                        manifestObj?.str("gradeKey"),
                        indexObj?.str("grade"),
                        manifestObj?.str("grade"),
                        GRADE_EMBEDDED_REGEX.find(folderDisplayName)?.value
                    )
                    val detectedGrade = CurriculumDisplayMapper.normalizeGradeKey(rawGrade)
                    require(detectedGrade.isNotBlank()) {
                        "[gradeKey] لا يمكن تحديد الصف من مسار المجلد أو index.json أو book_manifest.json؛ لن يتم تخمين الصف."
                    }
                    val subject = firstNonBlank(
                        indexObj?.str("subjectKey"),
                        indexMetadata?.str("subjectKey"),
                        indexBook?.str("subjectKey"),
                        manifestObj?.str("subjectKey"),
                        segments.getOrNull(0)
                    ).uppercase()
                    require(subject.isNotBlank()) {
                        "[subjectKey] مفتاح المادة غير موجود في index.json أو book_manifest.json أو مسار الكتاب."
                    }
                    val relativeBookSegments = if (segments.firstOrNull()?.equals(subject, ignoreCase = true) == true) {
                        segments.drop(1)
                    } else {
                        segments
                    }
                    val rawPathComponent = relativeBookSegments.firstOrNull()?.takeIf { it.isNotBlank() }
                    val (component, effectivePathComponent) = resolveComponent(rawPathComponent, allowPathFallback = true)
                    val bookKeyParts = if (relativeBookSegments.isEmpty() && component != null) {
                        listOf(subject, component)
                    } else {
                        listOf(subject) + relativeBookSegments
                    }
                    val bookKey = bookKeyParts.distinct().joinToString("/")
                    result.add(
                        BookIndexSlot(
                            gradeKey = detectedGrade,
                            gradePrefixRel = "",
                            subjectKey = subject,
                            componentKey = component,
                            pathSubjectKey = relativeBookSegments.firstOrNull()?.takeIf { BookMetadataMapper.normalizeSubjectKey(it) != null },
                            pathComponentKey = effectivePathComponent,
                            componentPathIsAuthoritative = effectivePathComponent != null && relativeBookSegments.isNotEmpty(),
                            componentMetadataMismatch = componentMetadataMismatch,
                            bookKey = bookKey,
                            bookRelPath = bookRel,
                            indexEntry = entry
                        )
                    )
                }
            }
        }
        return result.distinctBy { "${it.gradeKey}|${it.bookKey}|${it.bookRelPath}" }
    }

    private fun componentIdentityError(slot: BookIndexSlot): String? {
        if (slot.pathSubjectKey != null && !slot.subjectKey.equals(slot.pathSubjectKey, ignoreCase = true)) {
            return "[subjectKey] لا يطابق مجلد المادة في المسار (${slot.subjectKey} مقابل ${slot.pathSubjectKey})."
        }
        if (!BookMetadataMapper.isValidBookIdentity(slot.gradeKey, slot.subjectKey, slot.componentKey)) {
            return "[book identity] gradeKey أو subjectKey أو componentKey غير معروف أو غير متوافق مع فهرس Indexer2."
        }
        if (slot.componentMetadataMismatch) {
            return "[index.json vs book_manifest.json] componentKey غير متطابق بين الفهرس والبطاقة."
        }
        if (slot.componentPathIsAuthoritative && !slot.componentKey.equals(slot.pathComponentKey, ignoreCase = true)) {
            return "[index.json → componentKey] لا يطابق مجلد المكون في المسار " +
                "(${slot.componentKey ?: "بدون مكون"} مقابل ${slot.pathComponentKey ?: "بدون مكون"})."
        }
        return null
    }

    private fun hasPdfHeaderOrExists(path: String?): Boolean {
        val stream = UriIO.openInputStream(context, path) ?: return false
        return runCatching {
            stream.use { input ->
                val header = ByteArray(5)
                val read = input.read(header)
                read >= 5 && String(header, Charsets.US_ASCII) == "%PDF-"
            }
        }.getOrDefault(false)
    }

    private fun isReadablePdf(path: String?): Boolean {
        val descriptor = UriIO.openReadPfd(context, path) ?: return false
        return runCatching {
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount > 0 }
            }
        }.getOrDefault(false)
    }

    private fun parseLessonsForBook(
        index: JsonObject,
        manifest: JsonObject? = null,
        grade: String,
        workspaceId: String,
        rootUriOrPath: String,
        bookKey: String,
        bookTitle: String,
        subjectKey: String,
        componentKey: String?,
        bookRel: String,
        entries: List<IndexedEntry>,
        entryMap: Map<String, IndexedEntry>
    ): List<LessonRef> {
        val units = (index["units"] as? JsonArray) ?: return emptyList()
        val result = mutableListOf<LessonRef>()

        units.forEachIndexed { ui, unitElem ->
            val u = unitElem as? JsonObject ?: return@forEachIndexed
            val unitFolderKey = WorkspaceContractValidator.sourceUnitFolderKey(u)
            val uk = WorkspaceContractValidator.sourceUnitKey(u)
            if (uk.isBlank() || unitFolderKey.isBlank()) {
                throw IllegalArgumentException("الوحدة رقم ${ui + 1} لا تحتوي مفتاحاً ثابتاً أو unitNumber صالحاً.")
            }
            val ut = firstNonBlank(
                u.str("title"),
                u.str("unitTitle"),
                u.str("name"),
                uk
            )
            val unitPart = WorkspaceContractValidator.resolveUnitPartKey(u, index, manifest)
            val unitLessons = (u["lessons"] as? JsonArray) ?: return@forEachIndexed

            unitLessons.forEachIndexed { li, lessonElem ->
                val l = lessonElem as? JsonObject ?: return@forEachIndexed
                val lk = WorkspaceContractValidator.sourceLessonKey(l)
                if (lk.isBlank()) {
                    throw IllegalArgumentException("درس رقم ${li + 1} في الوحدة $uk لا يحتوي مفتاحاً ثابتاً أو lessonNumber صالحاً.")
                }
                val lt = firstNonBlank(
                    l.str("title"),
                    l.str("lessonTitle"),
                    l.str("name"),
                    lk
                )
                val startPage = firstNonBlank(l.str("pdfPageStart"), l.str("pdfPage"), l.str("pageStart"), l.str("startPage"))
                val endPage = firstNonBlank(l.str("pdfPageEnd"), l.str("pdfPage"), l.str("pageEnd"), l.str("endPage"))
                val printedStartPage = l.str("printedPageStart")
                val printedEndPage = l.str("printedPageEnd")
                val printedPageRange = if (printedStartPage.isNotBlank() && printedEndPage.isNotBlank()) {
                    "$printedStartPage–$printedEndPage"
                } else null
                val p = firstNonBlank(l.str("partKey"), l.str("part"), unitPart).uppercase()
                val lessonNumStr = (l.str("lessonNumber").toIntOrNull() ?: lessonNumber(lk)).let { num ->
                    if (num >= 0) num.toString().padStart(2, '0') else "-1"
                }

                val bookEntries = if (bookRel.isBlank()) {
                    entries
                } else {
                    val bookPrefix = "${bookRel.trim('/').lowercase()}/"
                    entries.filter { it.relativePath.lowercase().startsWith(bookPrefix) }
                }

                val linkedPdf = firstNonBlank(
                    (l["sliceFile"] as? JsonObject)?.str("workspaceUri"),
                    (l["sliceFile"] as? JsonObject)?.str("relativePath"),
                    l.str("pdfPath"),
                    l.str("path"),
                    l.str("filePath")
                ).trim()
                    .replace('\\', '/')
                    .removePrefix("./")
                    .trimStart('/')
                    .takeIf { it.isNotBlank() && !it.split('/').contains("..") }
                    .orEmpty()
                val pdfCandidates = WorkspaceContractValidator.lessonPdfCandidates(
                    lesson = l,
                    unit = u,
                    lessonPart = p,
                    bookRelativePath = bookRel,
                    expectedGrade = grade,
                    expectedSubject = subjectKey
                )
                val matchedPdf = firstExisting(entryMap, *pdfCandidates.toTypedArray())
                    ?: if (linkedPdf.isNotBlank()) {
                        val normLinked = linkedPdf.lowercase()
                        bookEntries.firstOrNull { entry ->
                            val eRel = entry.relativePath.lowercase()
                            eRel == normLinked || eRel.endsWith("/$normLinked")
                        }
                    } else null

                val explicitTextRel = l.str("sourceTextPath").trim().replace('\\', '/')
                    .removePrefix("./")
                    .trimStart('/')
                    .takeIf { it.isNotBlank() && !it.split('/').contains("..") }
                val txtCandidates = buildList {
                    if (explicitTextRel != null) {
                        add(explicitTextRel)
                        if (bookRel.isNotBlank()) add(joinRel(bookRel, explicitTextRel))
                    }
                    if (p == "P01" || p == "P02") {
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.txt"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.md"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.txt"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.md"))
                    }
                    add(joinRel(bookRel, "P01/units/$unitFolderKey/lesson_$lessonNumStr.txt"))
                    add(joinRel(bookRel, "P01/units/$unitFolderKey/lesson_$lessonNumStr.md"))
                    add(joinRel(bookRel, "P02/units/$unitFolderKey/lesson_$lessonNumStr.txt"))
                    add(joinRel(bookRel, "P02/units/$unitFolderKey/lesson_$lessonNumStr.md"))
                    add(joinRel(bookRel, "units/$unitFolderKey/lesson_$lessonNumStr.txt"))
                    add(joinRel(bookRel, "units/$unitFolderKey/lesson_$lessonNumStr.md"))
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk.txt"))
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk.md"))
                }
                val matchedTxt = firstExisting(entryMap, *txtCandidates.toTypedArray())
                    ?: bookEntries.firstOrNull { entry ->
                        val lower = entry.relativePath.lowercase()
                        val matchesPart = p.isBlank() || lower.contains("/${p.lowercase()}/") || !lower.contains("/p0")
                        matchesPart &&
                            (lower.endsWith(".txt") || lower.endsWith(".md")) &&
                            lower.contains("/$unitFolderKey/", ignoreCase = true) &&
                            (lower.contains("/$lk.", ignoreCase = true) ||
                                (lessonNumStr != "-1" && lower.contains("lesson_$lessonNumStr.", ignoreCase = true)))
                    }

                val explicitHtmlRel = firstNonBlank(l.str("interactivePath"), l.str("htmlPath"))
                    .trim()
                    .replace('\\', '/')
                    .takeIf { it.isNotBlank() && !it.split('/').contains("..") }
                val htmlCandidates = buildList {
                    if (explicitHtmlRel != null) {
                        add(explicitHtmlRel)
                        if (bookRel.isNotBlank()) add(joinRel(bookRel, explicitHtmlRel))
                    }
                    if (p == "P01" || p == "P02") {
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr/index.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_${lessonNumStr}_interactive.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk/index.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.html"))
                    }
                    add(joinRel(bookRel, "interactive/$unitFolderKey/$lk/index.html"))
                    add(joinRel(bookRel, "interactive/$unitFolderKey/$lk.html"))
                    add("interactive/$bookKey/$unitFolderKey/$lk/index.html")
                    add("interactive/$bookKey/$unitFolderKey/$lk.html")
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk/index.html"))
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk.html"))
                    add(joinRel(bookRel, "units/$unitFolderKey/lesson_$lessonNumStr.html"))
                }
                val matchedHtml = firstExisting(entryMap, *htmlCandidates.toTypedArray())
                    ?: bookEntries.firstOrNull { entry ->
                        val lower = entry.relativePath.lowercase()
                        val matchesPart = p.isBlank() || lower.contains("/${p.lowercase()}/") || !lower.contains("/p0")
                        matchesPart &&
                            lower.endsWith(".html") &&
                            lower.contains("/$unitFolderKey/", ignoreCase = true) &&
                            (lower.contains("/$lk", ignoreCase = true) ||
                                (lessonNumStr != "-1" && lower.contains("lesson_$lessonNumStr", ignoreCase = true)))
                    }

                val pageRange = if (startPage.isNotBlank() && endPage.isNotBlank()) "$startPage–$endPage" else null

                result.add(
                    LessonRef(
                        grade = grade,
                        rootPath = rootUriOrPath,
                        bookKey = bookKey,
                        bookTitle = bookTitle,
                        subjectKey = subjectKey,
                        componentKey = componentKey,
                        unitKey = uk,
                        unitTitle = ut,
                        lessonKey = lk,
                        title = lt,
                        partKey = p.ifBlank { null },
                        pageRange = pageRange,
                        pdfPath = matchedPdf?.uriOrPath,
                        workspaceId = workspaceId,
                        sourceTextPath = matchedTxt?.uriOrPath,
                        interactivePath = matchedHtml?.uriOrPath,
                        printedPageRange = printedPageRange
                    )
                )
            }
        }
        return result
    }

    private fun findPreGeneratedQuestions(entries: List<IndexedEntry>, lesson: LessonRef): List<IndexedEntry> {
        val uk = lesson.unitKey.lowercase()
        val sourceUnitFolder = uk.removePrefix("p01_").removePrefix("p02_")
        val lk = lesson.lessonKey.lowercase()
        val bk = lesson.bookKey.lowercase()
        val gradeLower = lesson.grade.lowercase()
        val otherGradeRegex = Regex("(?:^|/)g\\d{2}(?:/|$)")
        return entries.filter { entry ->
            val p = entry.relativePath.lowercase()
            val matchesGrade = !otherGradeRegex.containsMatchIn(p) || p.contains("/$gradeLower/") || p.startsWith("$gradeLower/")
            val matchesBook = p.contains("/$bk/") || p.startsWith("$bk/") || p.startsWith("questions/$uk/$lk/")
            matchesGrade &&
                matchesBook &&
                p.endsWith(".json") &&
                (p.contains("/questions/") || p.startsWith("questions/")) &&
                (p.contains("/$uk/") || p.contains("/$sourceUnitFolder/")) &&
                p.contains("/$lk/")
        }
    }

    private fun indexSafTree(treeUri: Uri): List<IndexedEntry> {
        val results = mutableListOf<IndexedEntry>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        walkSafChildren(treeUri, rootDocId, "", results)
        return results
    }

    private fun walkSafChildren(
        treeUri: Uri,
        parentDocId: String,
        parentRel: String,
        out: MutableList<IndexedEntry>
    ) {
        if (out.size >= MAX_INDEXED_FILES) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext() && out.size < MAX_INDEXED_FILES) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else continue
                    if (name.startsWith(".")) continue
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val rel = if (parentRel.isBlank()) name else "$parentRel/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walkSafChildren(treeUri, docId, rel, out)
                    } else {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out.add(IndexedEntry(rel, docUri.toString(), size))
                    }
                }
            }
        }
    }

    private fun indexLocalDirectory(root: File): List<IndexedEntry> {
        val basePath = root.toPath()
        return root.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") }
            .filter { it.isFile }
            .take(MAX_INDEXED_FILES)
            .map { file ->
                val rel = basePath.relativize(file.toPath()).toString().replace('\\', '/')
                IndexedEntry(rel, file.absolutePath, file.length())
            }
            .toList()
    }

    private fun joinRel(prefix: String, tail: String): String {
        val cleanPrefix = prefix.trim('/')
        val cleanTail = tail.trimStart('/')
        return if (cleanPrefix.isBlank()) cleanTail else "$cleanPrefix/$cleanTail"
    }

    private fun firstExisting(
        entryMap: Map<String, IndexedEntry>,
        vararg candidates: String
    ): IndexedEntry? {
        for (c in candidates) {
            val found = entryMap[c.lowercase()]
            if (found != null) return found
        }
        return null
    }

    private fun buildVirtualSafPackageRoot(treeUri: String, baseRel: String): String {
        val encodedRel = Uri.encode(baseRel)
        return "safroot::$treeUri::$encodedRel"
    }

    private fun subjectDisplayName(subjectKey: String, componentKey: String?): String =
        CurriculumDisplayMapper.subjectDisplayName(subjectKey, componentKey)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun computeStableWorkspaceId(rootUriOrPath: String, gradeKey: String): String {
        val stableHash = UUID.nameUUIDFromBytes("$rootUriOrPath|$gradeKey".toByteArray(Charsets.UTF_8))
            .toString()
            .replace("-", "")
            .take(10)
        return "direct-$gradeKey-$stableHash"
    }

    private fun lessonNumber(key: String): Int {
        val match = TRAILING_NUMBER_REGEX.find(key) ?: return -1
        return match.groupValues[1].toIntOrNull() ?: -1
    }

    private fun JsonObject.str(key: String): String {
        val elem: JsonElement = this[key] ?: return ""
        return (elem as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (v in values) {
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }

    companion object {
        private const val MAX_INDEXED_FILES = 8000
        private val GRADE_SEGMENT_REGEX = Regex("^G\\d{2}$", RegexOption.IGNORE_CASE)
        private val GRADE_EMBEDDED_REGEX = Regex("\\bG\\d{2}\\b", RegexOption.IGNORE_CASE)
        private val TRAILING_NUMBER_REGEX = Regex("(\\d+)$")
        private val PART_INDEX_SUFFIX_REGEX = Regex("(?:^|/)(?:P01|P02|BOTH)/index\\.json$", RegexOption.IGNORE_CASE)
        private val AUX_INDEX_DIR_REGEX = Regex("(?:^|/)(?:units|questions|ai_content|manual_review|interactive|edu_mini_generated)/", RegexOption.IGNORE_CASE)

        /**
         * Resolves a relative path inside a virtual SAF package root (`safroot::<treeUri>::<encodedBaseRel>`)
         * back to an actual readable SAF document URI inside that tree!
         */
        fun resolveSafPackageAssetUri(
            context: Context,
            rootDir: String,
            relativeAssetPath: String
        ): Uri? {
            if (!rootDir.startsWith("safroot::")) return null
            val parts = rootDir.removePrefix("safroot::").split("::", limit = 2)
            val treeUriStr = parts.getOrNull(0) ?: return null
            val baseRel = Uri.decode(parts.getOrNull(1).orEmpty()).trim('/')
            val cleanAsset = relativeAssetPath.trim('/')
            val fullRel = if (baseRel.isBlank()) cleanAsset else "$baseRel/$cleanAsset"
            val segments = fullRel.split('/').filter { it.isNotBlank() && it != "." }
            if (segments.any { it == ".." }) return null

            val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return null
            var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            for (seg in segments) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                var nextDocId: String? = null
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = if (idIdx >= 0) cursor.getString(idIdx) else continue
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else continue
                        if (name.equals(seg, ignoreCase = true)) {
                            nextDocId = id
                            break
                        }
                    }
                }
                currentDocId = nextDocId ?: return null
            }
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
        }
    }
}
