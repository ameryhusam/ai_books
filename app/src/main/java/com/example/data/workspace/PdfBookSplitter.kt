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
        require(sourcePdf.exists()) { "ملف PDF الأصلي غير موجود" }

        val sourceCopy = File(bookDir, "source.pdf")
        sourcePdf.copyTo(sourceCopy, overwrite = true)
        val warnings = mutableListOf<String>()
        val lessons = mutableListOf<LessonRef>()
        var filesCount = 3 // book_manifest.json + index.json + original source PDF

        val coverFile = File(bookDir, "cover.png")
        val coverSaved = renderCoverPng(sourcePdf, coverFile)
        if (coverSaved) {
            filesCount++
        }

        val manifestJson = buildJsonObject {
            put("title", cleanTitle)
            put("gradeKey", cleanGrade)
            put("subjectKey", cleanSubject)
            put("sourceFile", pdfInfo.fileName)
            put("sourcePdf", "source.pdf")
            put("sourceSha256", pdfInfo.sha256)
            put("totalPages", pdfInfo.totalPages)
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
                    put("partKey", "P01")
                    put("assignmentStatus", "assigned")
                    put("lessons", buildJsonArray {
                        unitSplits.forEachIndexed { idx, split ->
                            val lessonKey = split.lessonKey.ifBlank {
                                "lesson_${(idx + 1).toString().padStart(2, '0')}"
                            }
                            add(buildJsonObject {
                                put("lessonKey", lessonKey)
                                put("title", split.title.trim())
                                put("contentType", "lesson")
                                put("printedPageStart", null as String?)
                                put("printedPageEnd", null as String?)
                                put("pdfPageStart", split.startPage)
                                put("pdfPageEnd", split.endPage)
                                val lessonNum = extractTrailingNumber(lessonKey, idx + 1)
                                put("sourceTextPath", "P01/units/$unitKey/lesson_${lessonNum.toString().padStart(2, '0')}.txt")
                            })
                        }
                    })
                })
            }
        }
        val indexJson = buildJsonObject {
            put("schemaVersion", "edu.book_index.v2")
            put("gradeKey", cleanGrade)
            put("subjectKey", cleanSubject)
            put("sourceSha256", pdfInfo.sha256)
            put("sourceLayout", "single_part_pdf")
            put("units", unitsArray)
        }
        File(bookDir, "index.json").writeText(json.encodeToString(indexJson))

        val splitFailureMessages = splitPdfIntoLessons(
            sourcePdf = sourcePdf,
            bookDir = bookDir,
            groupedUnits = groupedUnits
        )
        warnings.addAll(splitFailureMessages)

        groupedUnits.forEach { (unitKey, unitSplits) ->
            val unitTitle = unitSplits.firstOrNull()?.unitTitle?.ifBlank { unitKey } ?: unitKey
            unitSplits.forEachIndexed { idx, split ->
                val lessonKey = split.lessonKey.ifBlank {
                    "lesson_${(idx + 1).toString().padStart(2, '0')}"
                }
                val num = extractTrailingNumber(lessonKey, idx + 1)
                val relPdf = "P01/units/$unitKey/lesson_${num.toString().padStart(2, '0')}.pdf"
                val lessonPdf = File(bookDir, relPdf)
                if (lessonPdf.exists()) {
                    filesCount++
                }
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
                        partKey = "P01",
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
            sourcePdfPath = sourceCopy.absolutePath
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

    private fun splitPdfIntoLessons(
        sourcePdf: File,
        bookDir: File,
        groupedUnits: Map<String, List<LessonSplitDraft>>
    ): List<String> {
        val warnings = mutableListOf<String>()
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(sourcePdf).use { sourceDocument ->
            groupedUnits.forEach { (unitKey, unitSplits) ->
                unitSplits.forEachIndexed { idx, split ->
                    val lessonKey = split.lessonKey.ifBlank {
                        "lesson_${(idx + 1).toString().padStart(2, '0')}"
                    }
                    val num = extractTrailingNumber(lessonKey, idx + 1)
                    val base = "P01/units/$unitKey/lesson_${num.toString().padStart(2, '0')}"
                    val outFile = File(bookDir, "$base.pdf")
                    val textFile = File(bookDir, "$base.txt")
                    outFile.parentFile?.mkdirs()
                    try {
                        require(split.endPage <= sourceDocument.numberOfPages) {
                            "نطاق صفحات الدرس ${split.title} يتجاوز عدد صفحات المصدر"
                        }
                        PDDocument().use { lessonDocument ->
                            for (pageNumber in split.startPage..split.endPage) {
                                lessonDocument.importPage(sourceDocument.getPage(pageNumber - 1))
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
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
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

    private fun extractTrailingNumber(key: String, fallback: Int): Int {
        val match = Regex("(\\d+)$").find(key) ?: return fallback
        return match.groupValues[1].toIntOrNull() ?: fallback
    }

    companion object {
        const val MAX_BOOK_PDF_BYTES = 150L * 1024L * 1024L
    }
}
