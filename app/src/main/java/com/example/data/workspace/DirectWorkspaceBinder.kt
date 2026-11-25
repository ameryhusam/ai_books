package com.example.data.workspace

import android.content.Context
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
import java.time.Instant
import java.util.UUID

data class DirectBoundWorkspaceResult(
    val workspace: ImportedWorkspace,
    val preloadedQuestions: List<QuestionItem>,
    val preloadedDrafts: List<DraftEntity>,
    val preloadedInteractivePackages: List<InteractivePackageEntity>
)

/**
 * Zero-permission, zero-copy local workspace binder.
 * Works directly on Android SAF DocumentTree URIs (`OpenDocumentTree`) or local filesystem directories
 * without copying PDFs, covers, or HTML lessons into app-private storage.
 */
class DirectWorkspaceBinder(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    data class IndexedEntry(
        val relativePath: String,
        val uriOrPath: String,
        val sizeBytes: Long
    )

    fun takePersistableReadPermission(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
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
        val discoveredIndexes = findBookIndexes(entries, folderName, entryMap)
            .filter { it.gradeKey.equals(gradeKey, ignoreCase = true) }

        require(discoveredIndexes.isNotEmpty()) {
            "لم يتم العثور على كتب صالحة للصف $gradeKey داخل المجلد المحدد."
        }

        val workspaceId = computeStableWorkspaceId(rootUriOrPath, gradeKey)
        val books = mutableListOf<BookRef>()
        val lessons = mutableListOf<LessonRef>()
        val preloadedQuestions = mutableListOf<QuestionItem>()
        val preloadedDrafts = mutableListOf<DraftEntity>()
        val preloadedPackages = mutableListOf<InteractivePackageEntity>()

        for (slot in discoveredIndexes) {
            val bookRel = slot.bookRelPath
            val manifestEntry = firstExisting(
                entryMap,
                joinRel(bookRel, "book_manifest.json"),
                joinRel(bookRel, "manifest.json")
            )
            val manifestRaw = manifestEntry?.let { UriIO.readText(context, it.uriOrPath).orEmpty() }
            val manifestObj: JsonObject? = manifestRaw?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    json.parseToJsonElement(raw).jsonObject
                }.onFailure {
                    warnings.add("تعذر قراءة manifest في $bookRel")
                }.getOrNull()
            }

            val indexRaw = UriIO.readText(context, slot.indexEntry.uriOrPath).orEmpty()
            val indexObj: JsonObject = runCatching {
                json.parseToJsonElement(indexRaw).jsonObject
            }.getOrElse {
                warnings.add("index.json غير صالح: $bookRel")
                continue
            }

            val validation = WorkspaceContractValidator.validate(
                indexRaw = indexRaw,
                manifestRaw = manifestRaw,
                expectedGrade = gradeKey,
                expectedSubject = slot.subjectKey,
                bookRelativePath = bookRel,
                availableRelativePaths = entries.map { it.relativePath }.toSet()
            )
            warnings.addAll(validation.warnings)
            if (!validation.isValid) {
                warnings.addAll(validation.errors.map { "$bookRel: $it" })
                continue
            }

            val fallbackTitle = slot.subjectKey + (if (slot.componentKey != null) " / ${slot.componentKey}" else "")
            val bookTitle = firstNonBlank(
                manifestObj?.str("title"),
                manifestObj?.str("bookTitle"),
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

            val parsedLessons = parseLessonsForBook(
                index = indexObj,
                grade = gradeKey,
                workspaceId = workspaceId,
                rootUriOrPath = rootUriOrPath,
                bookKey = slot.bookKey,
                bookTitle = bookTitle,
                subjectKey = slot.subjectKey,
                componentKey = slot.componentKey,
                bookRel = bookRel,
                entries = entries,
                entryMap = entryMap
            )

            books.add(
                BookRef(
                    bookKey = slot.bookKey,
                    title = bookTitle,
                    subjectKey = slot.subjectKey,
                    componentKey = slot.componentKey,
                    relativePath = bookRel.ifBlank { slot.bookKey },
                    lessonsCount = parsedLessons.size,
                    coverPath = resolvedCoverEntry?.uriOrPath,
                    sourcePdfPath = resolvedBookPdfEntry?.uriOrPath
                )
            )
            lessons.addAll(parsedLessons)
        }

        require(books.isNotEmpty()) {
            "لم يجتز أي كتاب في الصف $gradeKey التحقق من الفهرس والهوية وملفات الدروس. راجع التحذيرات ثم أصلح المصدر."
        }

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
                        id = "pkg-direct-${computeStableWorkspaceId(htmlEntryPath, lesson.lessonKey)}",
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

        val relevantFilesCount = entries.count {
            it.relativePath.contains("/$gradeKey/", ignoreCase = true) ||
                it.relativePath.startsWith("$gradeKey/", ignoreCase = true) ||
                discoveredIndexes.any { idx -> idx.bookRelPath.isBlank() }
        }.coerceAtLeast(lessons.size)

        val workspace = ImportedWorkspace(
            workspaceId = workspaceId,
            gradeKey = gradeKey,
            rootPath = rootUriOrPath,
            title = "$gradeKey • ${books.size} كتاب (مباشر بدون نسخ)",
            books = books,
            lessons = lessons,
            filesCount = relevantFilesCount,
            warnings = warnings.distinct(),
            importedAt = System.currentTimeMillis()
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
        val gradeChoices = slots.groupBy { it.gradeKey }.entries
            .sortedBy { it.key }
            .map { (grade, gradeSlots) ->
                var lessonSum = 0
                val titles = mutableListOf<String>()
                gradeSlots.forEach { slot ->
                    val manifestEntry = firstExisting(
                        entryMap,
                        joinRel(slot.bookRelPath, "book_manifest.json"),
                        joinRel(slot.bookRelPath, "manifest.json")
                    )
                    val manifestObj = manifestEntry?.let { entry ->
                        runCatching {
                            json.parseToJsonElement(UriIO.readText(context, entry.uriOrPath).orEmpty()).jsonObject
                        }.getOrNull()
                    }
                    val indexObj = runCatching {
                        json.parseToJsonElement(UriIO.readText(context, slot.indexEntry.uriOrPath).orEmpty()).jsonObject
                    }.getOrNull()
                    val count = (indexObj?.get("units") as? JsonArray)?.sumOf { u ->
                        ((u as? JsonObject)?.get("lessons") as? JsonArray)?.size ?: 0
                    } ?: 0
                    lessonSum += count
                    val bookTitle = firstNonBlank(
                        manifestObj?.str("title"),
                        manifestObj?.str("bookTitle"),
                        slot.subjectKey
                    )
                    titles.add(bookTitle)
                }
                DirectGradeChoice(
                    gradeKey = grade,
                    booksCount = gradeSlots.size,
                    lessonsCount = lessonSum,
                    bookTitles = titles
                )
            }

        return DirectWorkspaceInspection(
            rootUriOrPath = rootUriOrPath,
            displayTitle = folderDisplayName,
            isSafUri = isSafUri,
            grades = gradeChoices,
            totalFilesIndexed = entries.size,
            warnings = warnings
        )
    }

    private data class BookIndexSlot(
        val gradeKey: String,
        val gradePrefixRel: String,
        val subjectKey: String,
        val componentKey: String?,
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
            PART_INDEX_SUFFIX_REGEX.containsMatchIn(entry.relativePath)
        }

        val result = mutableListOf<BookIndexSlot>()
        for (entry in rawIndexFiles) {
            val rel = entry.relativePath
            val bookRel = if (rel.equals("index.json", ignoreCase = true)) "" else rel.removeSuffix("/index.json").removeSuffix("/INDEX.JSON")
            val segments = if (bookRel.isBlank()) emptyList() else bookRel.split('/').filter { it.isNotBlank() }

            when {
                // Layout A: workspace/G04/MATH[/COMP]/index.json
                segments.size >= 3 &&
                    segments[0].equals("workspace", ignoreCase = true) &&
                    GRADE_SEGMENT_REGEX.matches(segments[1]) -> {
                    val grade = segments[1].uppercase()
                    val subject = segments[2]
                    val comp = segments.getOrNull(3)
                    val bk = listOfNotNull(subject, comp).joinToString("/")
                    result.add(BookIndexSlot(grade, "workspace/$grade", subject, comp, bk, bookRel, entry))
                }
                // Layout B: G04/MATH[/COMP]/index.json
                segments.size >= 2 && GRADE_SEGMENT_REGEX.matches(segments[0]) -> {
                    val grade = segments[0].uppercase()
                    val subject = segments[1]
                    val comp = segments.getOrNull(2)
                    val bk = listOfNotNull(subject, comp).joinToString("/")
                    result.add(BookIndexSlot(grade, grade, subject, comp, bk, bookRel, entry))
                }
                // Layout C/D: User picked G04 directly (<SUBJECT>[/<COMP>]/index.json) or single book root (index.json)
                else -> {
                    val manifestEntry = firstExisting(
                        entryMap,
                        joinRel(bookRel, "book_manifest.json"),
                        joinRel(bookRel, "manifest.json")
                    )
                    val manifestObj = manifestEntry?.let { m ->
                        runCatching {
                            json.parseToJsonElement(UriIO.readText(context, m.uriOrPath).orEmpty()).jsonObject
                        }.getOrNull()
                    }
                    val indexObj = runCatching {
                        json.parseToJsonElement(UriIO.readText(context, entry.uriOrPath).orEmpty()).jsonObject
                    }.getOrNull()

                    val detectedGrade = firstNonBlank(
                        manifestObj?.str("gradeKey"),
                        manifestObj?.str("grade"),
                        indexObj?.str("gradeKey"),
                        indexObj?.str("grade"),
                        GRADE_EMBEDDED_REGEX.find(folderDisplayName)?.value?.uppercase(),
                        "G07"
                    ).uppercase()
                    val subject = firstNonBlank(
                        segments.getOrNull(0),
                        manifestObj?.str("subjectKey"),
                        indexObj?.str("subjectKey"),
                        folderDisplayName.takeIf { it.isNotBlank() },
                        "BOOK"
                    )
                    val comp = segments.getOrNull(1)
                    val bk = listOfNotNull(subject, comp).joinToString("/")
                    result.add(BookIndexSlot(detectedGrade, "", subject, comp, bk, bookRel, entry))
                }
            }
        }
        return result.distinctBy { "${it.gradeKey}|${it.bookKey}|${it.bookRelPath}" }
    }

    private fun parseLessonsForBook(
        index: JsonObject,
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
            val unitPart = firstNonBlank(u.str("partKey"), u.str("part"), "")
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
                val startPage = firstNonBlank(l.str("pdfPageStart"), l.str("pageStart"), l.str("startPage"))
                val endPage = firstNonBlank(l.str("pdfPageEnd"), l.str("pageEnd"), l.str("endPage"))
                val p = firstNonBlank(l.str("partKey"), l.str("part"), unitPart)
                val lessonNumStr = lessonNumber(lk).let { num ->
                    if (num >= 0) num.toString().padStart(2, '0') else "-1"
                }

                val linkedPdf = (l["sliceFile"] as? JsonObject)?.str("workspaceUri").orEmpty()
                val pdfCandidates = buildList {
                    if (linkedPdf.isNotBlank()) {
                        add(linkedPdf)
                        if (linkedPdf.startsWith("workspace/", ignoreCase = true)) {
                            add(linkedPdf.removePrefix("workspace/"))
                        }
                    }
                    if (p == "P01" || p == "P02") {
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.pdf"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.pdf"))
                    }
                }
                val matchedPdf = firstExisting(entryMap, *pdfCandidates.toTypedArray())

                val txtCandidates = buildList {
                    if (p == "P01" || p == "P02") {
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.txt"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.md"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.txt"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.md"))
                    }
                    add(joinRel(bookRel, "P01/units/$uk/lesson_$lessonNumStr.txt"))
                    add(joinRel(bookRel, "P01/units/$uk/lesson_$lessonNumStr.md"))
                    add(joinRel(bookRel, "P02/units/$uk/lesson_$lessonNumStr.txt"))
                    add(joinRel(bookRel, "P02/units/$uk/lesson_$lessonNumStr.md"))
                    add(joinRel(bookRel, "units/$unitFolderKey/lesson_$lessonNumStr.txt"))
                    add(joinRel(bookRel, "units/$unitFolderKey/lesson_$lessonNumStr.md"))
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk.txt"))
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk.md"))
                }
                val matchedTxt = firstExisting(entryMap, *txtCandidates.toTypedArray())
                    ?: entries.firstOrNull { entry ->
                        val lower = entry.relativePath.lowercase()
                        (lower.endsWith(".txt") || lower.endsWith(".md")) &&
                            lower.contains("/$unitFolderKey/", ignoreCase = true) &&
                            (lower.contains("/$lk.", ignoreCase = true) ||
                                (lessonNumStr != "-1" && lower.contains("lesson_$lessonNumStr.", ignoreCase = true)))
                    }

                val htmlCandidates = buildList {
                    if (p == "P01" || p == "P02") {
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr/index.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_${lessonNumStr}_interactive.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/lesson_$lessonNumStr.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk/index.html"))
                        add(joinRel(bookRel, "$p/units/$unitFolderKey/$lk.html"))
                    }
                    add(joinRel(bookRel, "interactive/$uk/$lk/index.html"))
                    add(joinRel(bookRel, "interactive/$uk/$lk.html"))
                    add("interactive/$bookKey/$uk/$lk/index.html")
                    add("interactive/$bookKey/$uk/$lk.html")
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk/index.html"))
                    add(joinRel(bookRel, "units/$unitFolderKey/$lk.html"))
                    add(joinRel(bookRel, "units/$unitFolderKey/lesson_$lessonNumStr.html"))
                }
                val matchedHtml = firstExisting(entryMap, *htmlCandidates.toTypedArray())
                    ?: entries.firstOrNull { entry ->
                        val lower = entry.relativePath.lowercase()
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
                        interactivePath = matchedHtml?.uriOrPath
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
        return entries.filter { entry ->
            val p = entry.relativePath.lowercase()
            p.endsWith(".json") &&
                (p.contains("/questions/") || p.startsWith("questions/")) &&
                (p.contains("/$uk/") || p.contains("/$sourceUnitFolder/")) &&
                p.contains("/$lk/") &&
                (p.contains("/$bk/") || !p.contains("/g"))
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
        private val PART_INDEX_SUFFIX_REGEX = Regex("/(?:P01|P02|BOTH)/index\\.json$", RegexOption.IGNORE_CASE)

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
