package com.example.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.model.BookRef
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import com.example.model.LessonSplitDraft
import com.example.model.PdfBookInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.security.MessageDigest
import java.time.Instant

class PdfBookSplitter(private val context: Context) {

    private val json = Json { prettyPrint = true }

    private val booksStorageDir: File
        get() = File(context.filesDir, "edumini/source_books")

    private val workspacesRoot: File
        get() = File(context.filesDir, "edumini/workspaces")

    suspend fun ingestBookPdf(uri: Uri): PdfBookInfo = withContext(Dispatchers.IO) {
        val fileName = queryFileName(uri) ?: "book_${System.currentTimeMillis()}.pdf"
        val dir = booksStorageDir.apply { mkdirs() }
        val storedFile = File(dir, "book_${System.currentTimeMillis()}.pdf")

        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح ملف PDF المختار")

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        stream.use { input ->
            FileOutputStream(storedFile).use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    totalBytes += read
                    if (totalBytes > MAX_BOOK_PDF_BYTES) {
                        storedFile.delete()
                        throw IllegalArgumentException("حجم الكتاب يتجاوز الحد الأقصى (150 MB)")
                    }
                    digest.update(buf, 0, read)
                    out.write(buf, 0, read)
                }
            }
        }

        val pageCount = countPages(storedFile)
        if (pageCount <= 0) {
            storedFile.delete()
            throw IllegalArgumentException("ملف PDF لا يحتوي صفحات صالحة")
        }

        val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
        PdfBookInfo(
            fileName = fileName,
            storedPdfPath = storedFile.absolutePath,
            totalPages = pageCount,
            sha256 = shaHex
        )
    }

    /**
     * Removes only the app-owned temporary staging copy after the source has been consumed.
     * The selected external PDF is never deleted.
     */
    fun discardIngestedBookPdf(pdfInfo: PdfBookInfo) {
        runCatching {
            val root = booksStorageDir.canonicalFile
            val candidate = File(pdfInfo.storedPdfPath).canonicalFile
            if (candidate.path.startsWith(root.path + File.separator)) {
                candidate.delete()
            }
        }
    }

    fun generateAutoSplits(
        totalPages: Int,
        pagesPerLesson: Int
    ): List<LessonSplitDraft> {
        if (totalPages <= 0) return emptyList()
        val step = pagesPerLesson.coerceIn(1, totalPages)
        val result = mutableListOf<LessonSplitDraft>()
        var page = 1
        var index = 1
        while (page <= totalPages) {
            val end = (page + step - 1).coerceAtMost(totalPages)
            val num = index.toString().padStart(2, '0')
            result.add(
                LessonSplitDraft(
                    id = "split-$index-$page-$end",
                    unitKey = "unit_01",
                    unitTitle = "الوحدة الأولى",
                    lessonKey = "lesson_$num",
                    title = "الدرس $index",
                    startPage = page,
                    endPage = end
                )
            )
            page = end + 1
            index++
        }
        return result
    }

    suspend fun buildWorkspaceFromSplits(
        pdfInfo: PdfBookInfo,
        gradeKey: String,
        subjectKey: String,
        bookTitle: String,
        splits: List<LessonSplitDraft>
    ): ImportedWorkspace = withContext(Dispatchers.IO) {
        val cleanGrade = gradeKey.trim().uppercase().ifBlank { "G04" }
        val cleanSubject = subjectKey.trim().uppercase()
            .replace(Regex("[^A-Z0-9_-]"), "")
            .ifBlank { "BOOK" }
        val cleanTitle = bookTitle.trim().ifBlank { pdfInfo.fileName.removeSuffix(".pdf") }

        require(splits.isNotEmpty()) { "أضف درساً واحداً على الأقل قبل إنشاء مساحة العمل" }
        splits.forEach { split ->
            require(split.title.isNotBlank()) { "عنوان الدرس لا يمكن أن يكون فارغاً (${split.lessonKey})" }
            require(split.startPage in 1..pdfInfo.totalPages) {
                "صفحة البداية غير صالحة للدرس ${split.title}"
            }
            require(split.endPage in split.startPage..pdfInfo.totalPages) {
                "صفحة النهاية غير صالحة للدرس ${split.title}"
            }
        }

        val timestamp = System.currentTimeMillis()
        val workspaceId = "local-$timestamp"
        val destDir = File(workspacesRoot.apply { mkdirs() }, workspaceId)
        val bookRel = "workspace/$cleanGrade/$cleanSubject"
        val bookDir = File(destDir, bookRel).apply { mkdirs() }

        val sourcePdf = File(pdfInfo.storedPdfPath)
        require(sourcePdf.isFile) { "ملف PDF الأصلي غير موجود" }
        PDFBoxResourceLoader.init(context.applicationContext)
        val actualPageCount = runCatching {
            PDDocument.load(sourcePdf, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        }.getOrElse { error ->
            throw IllegalArgumentException("ملف PDF غير صالح أو غير قابل للقراءة: ${error.message ?: "خطأ غير معروف"}", error)
        }
        require(actualPageCount > 0) { "ملف PDF لا يحتوي صفحات" }
        require(actualPageCount == pdfInfo.totalPages) {
            "عدد صفحات PDF لا يطابق بيانات الملف: المتوقع ${pdfInfo.totalPages}، الفعلي $actualPageCount"
        }
        require(splits.isNotEmpty()) { "لم يتم تحديد أي حدود للدروس" }
        splits.forEach { split ->
            require(split.startPage >= 1 && split.endPage >= split.startPage && split.endPage <= actualPageCount) {
                "نطاق صفحات غير صالح للدرس ${split.title}: ${split.startPage}–${split.endPage}"
            }
        }

        val proposedPartKey = inferPartKeyFromFileName(pdfInfo.fileName)
        val partFolder = proposedPartKey.takeIf { it == "P01" || it == "P02" } ?: "REVIEW"
        val partAssignmentEvidence = if (proposedPartKey == "UNKNOWN") {
            "No reliable P01/P02 marker found in source filename; manual review is required."
        } else {
            "Filename suggests $proposedPartKey, but the part assignment is provisional and requires review."
        }
        val warnings = mutableListOf<String>()
        warnings.add("حدود الصفحات المقترحة لا تمثل اكتشافاً مؤكداً لبداية الدروس؛ راجعها مقابل فهرس الكتاب قبل الاعتماد.")
        val lessons = mutableListOf<LessonRef>()
        var filesCount = 2 // book_manifest.json + index.json; original PDF remains a temporary input

        val coverFile = File(bookDir, "cover.png")
        val coverSaved = renderCoverPng(sourcePdf, coverFile)
        if (coverSaved) {
            filesCount++
        }

        val manifestJson = buildJsonObject {
            put("manifestVersion", "edu.book_manifest.draft.v1")
            put("sourceLayout", "UNKNOWN")
            put("sourceReviewStatus", "NEEDS_REVIEW")
            put("sourceReviewReason", "Source layout and curriculum metadata have not been explicitly confirmed.")
            put("offset", 0)
            put("title", cleanTitle)
            put("gradeKey", cleanGrade)
            put("subjectKey", cleanSubject)
            put("sourceFile", pdfInfo.fileName)
            put("sourceSha256", pdfInfo.sha256)
            put("totalPages", pdfInfo.totalPages)
            put("partKey", "UNKNOWN")
            if (proposedPartKey != "UNKNOWN") put("proposedPartKey", proposedPartKey)
            put("assignmentStatus", "need_review")
            put("assignmentEvidence", partAssignmentEvidence)
            if (coverSaved) {
                put("cover", "cover.png")
            }
            put("createdAt", Instant.now().toString())
        }
        File(bookDir, "book_manifest.json").writeText(json.encodeToString(manifestJson))

        val groupedUnits = splits.groupBy { it.unitKey.ifBlank { "unit_01" } }
        val unitsArray = buildJsonArray {
            groupedUnits.forEach { (unitKey, unitSplits) ->
                val unitTitle = unitSplits.firstOrNull()?.unitTitle?.ifBlank { unitKey } ?: unitKey
                add(buildJsonObject {
                    put("unitKey", unitKey)
                    put("title", unitTitle)
                    put("partKey", "unknown")
                    if (proposedPartKey != "UNKNOWN") put("proposedPartKey", proposedPartKey)
                    put("assignmentStatus", "need_review")
                    put("assignmentSource", if (proposedPartKey == "UNKNOWN") "LEGACY_UNVERIFIED" else "DEFAULT_PROPOSAL")
                    put("assignmentEvidence", partAssignmentEvidence)
                    put("lessons", buildJsonArray {
                        unitSplits.forEachIndexed { idx, split ->
                            val lessonKey = split.lessonKey.ifBlank {
                                "lesson_${(idx + 1).toString().padStart(2, '0')}"
                            }
                            add(buildJsonObject {
                                put("lessonKey", lessonKey)
                                put("title", split.title.trim())
                                put("contentType", "lesson")
                                put("segmentationStatus", "need_review")
                                put("segmentationEvidence", "Suggested by fixed page count; verify against the printed table of contents.")
                                put("printedPageStart", null as String?)
                                put("printedPageEnd", null as String?)
                                put("pdfPageStart", split.startPage)
                                put("pdfPageEnd", split.endPage)
                                val lessonNum = extractTrailingNumber(lessonKey, idx + 1)
                                put("sourceTextPath", "$partFolder/units/$unitKey/lesson_${lessonNum.toString().padStart(2, '0')}.txt")
                            })
                        }
                    })
                })
            }
        }
        val indexJson = buildJsonObject {
            put("schemaVersion", "edu.book_index.draft.v1")
            put("approvalStatus", "NEEDS_REVIEW")
            put("requiresReview", true)
            put("reviewReason", "Generated page chunks are provisional until the book intake metadata is confirmed.")
            put("offset", 0)
            put("gradeKey", cleanGrade)
            put("subjectKey", cleanSubject)
            put("sourceSha256", pdfInfo.sha256)
            put("sourceLayout", "UNKNOWN")
            put("segmentationStrategy", "fixed_page_chunk_suggestion")
            put("segmentationStatus", "need_review")
            put("units", unitsArray)
        }
        File(bookDir, "index.json").writeText(json.encodeToString(indexJson))

        val splitFailureMessages = splitPdfIntoLessons(
            sourcePdf = sourcePdf,
            bookDir = bookDir,
            groupedUnits = groupedUnits,
            partKey = partFolder
        )
        warnings.addAll(splitFailureMessages)

        groupedUnits.forEach { (unitKey, unitSplits) ->
            val unitTitle = unitSplits.firstOrNull()?.unitTitle?.ifBlank { unitKey } ?: unitKey
            unitSplits.forEachIndexed { idx, split ->
                val lessonKey = split.lessonKey.ifBlank {
                    "lesson_${(idx + 1).toString().padStart(2, '0')}"
                }
                val num = extractTrailingNumber(lessonKey, idx + 1)
                val relPdf = "$partFolder/units/$unitKey/lesson_${num.toString().padStart(2, '0')}.pdf"
                val lessonPdf = File(bookDir, relPdf)
                val lessonBase = relPdf.removeSuffix(".pdf")
                filesCount += listOf(
                    lessonPdf,
                    File(bookDir, "$lessonBase.txt"),
                    File(bookDir, "$lessonBase.extraction.json")
                ).count { it.isFile }
                lessons.add(
                    LessonRef(
                        grade = cleanGrade,
                        rootPath = destDir.absolutePath,
                        bookKey = cleanSubject,
                        bookTitle = cleanTitle,
                        subjectKey = cleanSubject,
                        componentKey = null,
                        unitKey = unitKey,
                        unitTitle = unitTitle,
                        lessonKey = lessonKey,
                        title = split.title.trim(),
                        partKey = null,
                        pageRange = "${split.startPage}–${split.endPage}",
                        pdfPath = lessonPdf.takeIf { it.exists() }?.absolutePath,
                        workspaceId = workspaceId
                    )
                )
            }
        }

        val book = BookRef(
            bookKey = cleanSubject,
            title = cleanTitle,
            subjectKey = cleanSubject,
            componentKey = null,
            relativePath = bookRel,
            lessonsCount = lessons.size,
            coverPath = coverFile.takeIf { coverSaved && it.exists() }?.absolutePath,
            sourcePdfPath = null
        )

        ImportedWorkspace(
            workspaceId = workspaceId,
            gradeKey = cleanGrade,
            rootPath = destDir.absolutePath,
            title = "$cleanGrade • $cleanTitle",
            books = listOf(book),
            lessons = lessons,
            filesCount = filesCount,
            warnings = warnings,
            importedAt = timestamp
        )
    }

    data class ApprovedBookSplitResult(
        val updatedBook: com.example.data.local.BookEntity,
        val updatedLessons: List<com.example.data.local.LessonEntity>,
        val filesCount: Int,
        val warnings: List<String>
    )

    fun managedWorkspaceDir(workspaceId: String): File =
        File(workspacesRoot.apply { mkdirs() }, workspaceId).apply { mkdirs() }

    fun renderBookCoverPng(sourcePdf: File, outCoverFile: File): Boolean {
        outCoverFile.parentFile?.mkdirs()
        return renderCoverPng(sourcePdf, outCoverFile)
    }

    /**
     * Stage 5: Slices an approved, Room-persisted book into per-part unit/lesson PDFs,
     * extracted `.txt` files, and `.extraction.json` metadata.
     * Supports both `SINGLE_PART_FILE` and `MERGED_PARTS_FILE` (P01 + P02 in one source PDF).
     * Lesson page ranges are read directly from Room (`pdfPageStart..pdfPageEnd`) without re-applying offset.
     */
    suspend fun splitApprovedBookFromDatabase(
        workspace: com.example.data.local.WorkspaceEntity,
        book: com.example.data.local.BookEntity,
        units: List<com.example.data.local.WorkspaceUnitEntity>,
        lessons: List<com.example.data.local.LessonEntity>,
        pdfInfo: PdfBookInfo,
        destDir: File,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): ApprovedBookSplitResult = withContext(Dispatchers.IO) {
        require(book.approvalStatus.equals("APPROVED", ignoreCase = true) && !book.requiresReview) {
            "لا يمكن تقطيع كتاب غير معتمد في قاعدة البيانات (${book.bookKey})."
        }
        val layout = runCatching { IndexSourceLayout.valueOf(book.sourceLayout) }
            .getOrDefault(IndexSourceLayout.UNKNOWN)
        require(layout == IndexSourceLayout.SINGLE_PART_FILE || layout == IndexSourceLayout.MERGED_PARTS_FILE) {
            "بنية مصدر الكتاب غير محددة للتقطيع: ${book.sourceLayout}"
        }
        val sourcePdf = File(pdfInfo.storedPdfPath)
        require(sourcePdf.isFile) { "ملف PDF الأصلي غير موجود" }

        onProgress("التحقق من ملف PDF وصفحاته", 58)
        PDFBoxResourceLoader.init(context.applicationContext)
        val actualPageCount = runCatching {
            PDDocument.load(sourcePdf, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        }.getOrElse { error ->
            throw IllegalArgumentException("ملف PDF غير صالح أو غير قابل للقراءة: ${error.message ?: "خطأ غير معروف"}", error)
        }
        require(actualPageCount > 0) { "ملف PDF لا يحتوي صفحات" }
        require(actualPageCount == pdfInfo.totalPages) {
            "عدد صفحات PDF لا يطابق بيانات الملف: المتوقع ${pdfInfo.totalPages}، الفعلي $actualPageCount"
        }

        val identity = RoomBookIndexProjection.canonicalBookIdentity(
            gradeKey = workspace.gradeKey,
            subjectKey = book.subjectKey,
            componentKey = book.componentKey,
            sourceLayout = layout,
            partKey = book.partKey
        )
        val bookRootDir = File(destDir, identity.bookRootRel).apply { mkdirs() }
        val canonicalBookDir = File(destDir, identity.canonicalBookRel).apply { mkdirs() }
        val coverFile = File(canonicalBookDir, "cover.png")
        val existingCover = coverFile.takeIf { it.isFile && it.length() > 0L }
            ?: if (renderBookCoverPng(sourcePdf, coverFile)) coverFile else null

        val unitSortByKey = units.sortedBy { it.sortOrder }
            .mapIndexed { index, unit -> unit.unitKey to index }
            .toMap()
        val unitPartByKey = units.associate { it.unitKey to (it.partKey ?: "") }
        val warnings = mutableListOf<String>()
        val updatedLessons = mutableListOf<com.example.data.local.LessonEntity>()
        var generatedFilesCount = if (existingCover != null) 1 else 0

        val sortedLessons = lessons.sortedWith(
            compareBy<com.example.data.local.LessonEntity> { unitSortByKey[it.unitKey] ?: Int.MAX_VALUE }
                .thenBy { it.sortOrder }
                .thenBy { it.rowId }
        )
        require(sortedLessons.isNotEmpty()) { "لا توجد دروس معتمدة للكتاب ${book.title}" }

        onProgress("تقطيع صفحات الدروس واستخراج النصوص", 64)
        PDDocument.load(sourcePdf, MemoryUsageSetting.setupTempFileOnly()).use { sourceDocument ->
            val total = sortedLessons.size.coerceAtLeast(1)
            sortedLessons.forEachIndexed { idx, lesson ->
                val startPage = lesson.pdfPageStart
                val endPage = lesson.pdfPageEnd
                require(startPage != null && endPage != null && startPage >= 1 && endPage >= startPage && endPage <= sourceDocument.numberOfPages) {
                    "نطاق صفحات PDF غير صالح للدرس ${lesson.title}: ${startPage ?: 0}–${endPage ?: 0} (إجمالي ${sourceDocument.numberOfPages})"
                }
                val resolvedPart = (lesson.partKey?.takeIf { it in setOf("P01", "P02") }
                    ?: unitPartByKey[lesson.unitKey]?.takeIf { it in setOf("P01", "P02") }
                    ?: book.partKey?.takeIf { it in setOf("P01", "P02") }
                    ?: throw IllegalStateException("تعذر تحديد الجزء (P01/P02) للدرس ${lesson.title}"))
                    .uppercase()
                if (layout == IndexSourceLayout.SINGLE_PART_FILE) {
                    require(resolvedPart == book.partKey?.uppercase()) {
                        "الجزء $resolvedPart للدرس ${lesson.title} لا يطابق جزء الكتاب المستقل ${book.partKey}"
                    }
                }

                val unitOrder = unitSortByKey[lesson.unitKey] ?: 0
                val unitFolder = RoomBookIndexProjection.unitFolderKey(lesson.unitKey, unitOrder)
                val lessonNum = RoomBookIndexProjection.extractTrailingNumber(lesson.lessonKey, lesson.sortOrder + 1)
                val baseRel = "$resolvedPart/units/$unitFolder/lesson_${lessonNum.toString().padStart(2, '0')}"
                val outPdf = File(bookRootDir, "$baseRel.pdf")
                val outText = File(bookRootDir, "$baseRel.txt")
                val outMeta = File(bookRootDir, "$baseRel.extraction.json")
                outPdf.parentFile?.mkdirs()

                val pct = 64 + ((idx + 1) * 28 / total)
                onProgress("تقطيع ${lesson.title} (${idx + 1}/$total)", pct.coerceAtMost(92))

                PDDocument().use { lessonDocument ->
                    for (pageNumber in startPage..endPage) {
                        val sourcePage = sourceDocument.getPage(pageNumber - 1)
                        val importedPage = lessonDocument.importPage(sourcePage)
                        sourcePage.resources?.let { resources -> importedPage.setResources(resources) }
                    }
                    lessonDocument.save(outPdf)
                }

                val extracted = runCatching {
                    PdfTextExtractor(context).extract(
                        pdfFile = sourcePdf,
                        startPage = startPage,
                        endPage = endPage
                    )
                }.getOrElse { err ->
                    PdfTextExtractionResult(
                        text = "",
                        method = "error",
                        ocrPages = 0,
                        warning = "تعذر استخراج النص: ${err.message ?: "خطأ غير معروف"}"
                    )
                }
                outText.writeText(extracted.text, Charsets.UTF_8)
                val extractionMeta = buildJsonObject {
                    put("method", extracted.method)
                    put("ocrPages", extracted.ocrPages)
                    put("warning", extracted.warning ?: "")
                    put("pdfPageStart", startPage)
                    put("pdfPageEnd", endPage)
                    if (lesson.printedPageStart != null) put("printedPageStart", lesson.printedPageStart)
                    if (lesson.printedPageEnd != null) put("printedPageEnd", lesson.printedPageEnd)
                    put("partKey", resolvedPart)
                }
                outMeta.writeText(json.encodeToString(extractionMeta), Charsets.UTF_8)

                if (extracted.warning != null) warnings.add("${lesson.title}: ${extracted.warning}")
                if (extracted.text.isBlank()) warnings.add("${lesson.title}: لم يتم استخراج نص؛ راجع صفحات PDF يدوياً.")

                generatedFilesCount += listOf(outPdf, outText, outMeta).count { it.isFile }
                updatedLessons.add(
                    lesson.copy(
                        rootPath = destDir.absolutePath,
                        partKey = resolvedPart,
                        pageRange = "$startPage–$endPage",
                        pdfPath = outPdf.takeIf { it.isFile }?.absolutePath,
                        sourceTextPath = outText.takeIf { it.isFile }?.absolutePath ?: lesson.sourceTextPath
                    )
                )
            }
        }

        val updatedBook = book.copy(
            lessonsCount = updatedLessons.size,
            coverPath = existingCover?.absolutePath ?: book.coverPath,
            sourcePdfPath = null
        )
        ApprovedBookSplitResult(
            updatedBook = updatedBook,
            updatedLessons = updatedLessons,
            filesCount = generatedFilesCount,
            warnings = warnings.distinct()
        )
    }

    private fun splitPdfIntoLessons(
        sourcePdf: File,
        bookDir: File,
        groupedUnits: Map<String, List<LessonSplitDraft>>,
        partKey: String
    ): List<String> {
        val warnings = mutableListOf<String>()
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(sourcePdf, MemoryUsageSetting.setupTempFileOnly()).use { sourceDocument ->
            groupedUnits.forEach { (unitKey, unitSplits) ->
                unitSplits.forEachIndexed { idx, split ->
                    val lessonKey = split.lessonKey.ifBlank {
                        "lesson_${(idx + 1).toString().padStart(2, '0')}"
                    }
                    val num = extractTrailingNumber(lessonKey, idx + 1)
                    val base = "$partKey/units/$unitKey/lesson_${num.toString().padStart(2, '0')}"
                    val outFile = File(bookDir, "$base.pdf")
                    val textFile = File(bookDir, "$base.txt")
                    outFile.parentFile?.mkdirs()
                    try {
                        require(split.endPage <= sourceDocument.numberOfPages) {
                            "نطاق صفحات الدرس ${split.title} يتجاوز عدد صفحات المصدر"
                        }
                        PDDocument().use { lessonDocument ->
                            for (pageNumber in split.startPage..split.endPage) {
                                val sourcePage = sourceDocument.getPage(pageNumber - 1)
                                val importedPage = lessonDocument.importPage(sourcePage)
                                // PDFBox does not automatically copy inherited page resources.
                                sourcePage.resources?.let { resources -> importedPage.setResources(resources) }
                            }
                            lessonDocument.save(outFile)
                        }
                        val extracted = PdfTextExtractor(context).extract(
                            pdfFile = sourcePdf,
                            startPage = split.startPage,
                            endPage = split.endPage
                        )
                        textFile.writeText(extracted.text, Charsets.UTF_8)
                        val extractionMeta = buildJsonObject {
                            put("method", extracted.method)
                            put("ocrPages", extracted.ocrPages)
                            put("warning", extracted.warning ?: "")
                        }
                        File(bookDir, "$base.extraction.json").writeText(json.encodeToString(extractionMeta))
                        if (extracted.warning != null) warnings.add("${split.title}: ${extracted.warning}")
                        if (extracted.text.isBlank()) warnings.add("${split.title}: لم يتم استخراج نص؛ راجع صفحات PDF يدوياً.")
                    } catch (error: Exception) {
                        warnings.add("تعذر تجهيز الدرس ${split.title}: ${error.message ?: "خطأ غير معروف"}")
                    }
                }
            }
            }
        }.onFailure { error ->
            warnings.add("تعذر قراءة أو فصل صفحات PDF الأصلية: ${error.message ?: "ملف PDF غير صالح"}")
        }
        return warnings
    }

    private fun renderCoverPng(sourcePdf: File, outCoverFile: File): Boolean {
        return runCatching {
            ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return false
                    renderer.openPage(0).use { page ->
                        val scale = (640f / page.width.coerceAtLeast(1)).coerceIn(0.6f, 1.8f)
                        val w = (page.width * scale).toInt().coerceAtLeast(240)
                        val h = (page.height * scale).toInt().coerceAtLeast(320)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileOutputStream(outCoverFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 92, fos)
                            }
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
            }
            outCoverFile.exists() && outCoverFile.length() > 0L
        }.getOrDefault(false)
    }

    private fun countPages(pdfFile: File): Int {
        val androidRendererCount = runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
        if (androidRendererCount > 0) return androidRendererCount
        return runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        }.getOrDefault(0)
    }

    private fun queryFileName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private fun inferPartKeyFromFileName(fileName: String): String {
        val match = Regex("(?:^|[^a-z0-9])(?:part|p)[ _.-]*0?([12])(?:[^0-9]|$)", RegexOption.IGNORE_CASE)
            .find(fileName)
        return when (match?.groupValues?.getOrNull(1)) {
            "1" -> "P01"
            "2" -> "P02"
            else -> "UNKNOWN"
        }
    }

    private fun extractTrailingNumber(key: String, fallback: Int): Int {
        val match = Regex("(\\d+)$").find(key) ?: return fallback
        return match.groupValues[1].toIntOrNull() ?: fallback
    }

    companion object {
        const val MAX_BOOK_PDF_BYTES = 150L * 1024L * 1024L
    }
}
