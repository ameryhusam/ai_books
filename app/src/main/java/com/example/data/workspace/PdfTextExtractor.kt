package com.example.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class PdfTextExtractionResult(
    val text: String,
    val method: String,
    val ocrPages: Int,
    val warning: String? = null
)

/**
 * Text-first extraction compatible with the indexer2 intake philosophy:
 * preserve the original PDF, prefer its embedded text, and use OCR only when
 * the selected pages are image-only. OCR models are cached locally after the
 * first successful download; no extracted text is treated as reviewed content.
 */
class PdfTextExtractor(private val context: Context) {

    fun extract(pdfFile: File, startPage: Int, endPage: Int): PdfTextExtractionResult {
        require(pdfFile.isFile) { "ملف PDF غير موجود لاستخراج النص" }
        require(startPage >= 1 && endPage >= startPage) { "نطاق صفحات PDF غير صالح" }

        val nativeText = runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(pdfFile).use { document ->
                require(endPage <= document.numberOfPages) { "نطاق الصفحات يتجاوز عدد صفحات PDF" }
                PDFTextStripper().apply {
                    setStartPage(startPage)
                    setEndPage(endPage)
                    sortByPosition = true
                }.getText(document).trim()
            }
        }.getOrElse { "" }

        if (nativeText.count { !it.isWhitespace() } >= MIN_NATIVE_TEXT_CHARS) {
            return PdfTextExtractionResult(
                text = nativeText,
                method = "NATIVE_PDF_TEXT",
                ocrPages = 0
            )
        }

        return runCatching {
            val ocrText = recognizePages(pdfFile, startPage, endPage)
            if (ocrText.count { !it.isWhitespace() } < MIN_OCR_TEXT_CHARS) {
                PdfTextExtractionResult(
                    text = ocrText,
                    method = "OCR_LOW_CONFIDENCE",
                    ocrPages = endPage - startPage + 1,
                    warning = "لم يستخرج OCR نصاً كافياً؛ يلزم فحص الصفحات يدوياً."
                )
            } else {
                PdfTextExtractionResult(
                    text = ocrText,
                    method = "TESSERACT_ARA_ENG",
                    ocrPages = endPage - startPage + 1
                )
            }
        }.getOrElse { error ->
            PdfTextExtractionResult(
                text = "",
                method = "OCR_REQUIRED",
                ocrPages = 0,
                warning = "تعذر استخراج النص المضمن وتشغيل OCR: ${error.message ?: "خطأ غير معروف"}"
            )
        }
    }

    private fun recognizePages(pdfFile: File, startPage: Int, endPage: Int): String {
        val tessRoot = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessData = File(tessRoot, "tessdata").apply { mkdirs() }
        ensureModel(tessData, "ara")
        ensureModel(tessData, "eng")

        val api = TessBaseAPI()
        check(api.init(tessRoot.absolutePath, "ara+eng")) {
            "تعذر تهيئة محرك Tesseract للغتين العربية والإنجليزية"
        }
        val output = StringBuilder()
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(endPage <= renderer.pageCount) { "نطاق الصفحات يتجاوز عدد صفحات PDF" }
                    for (pageNumber in startPage..endPage) {
                        renderer.openPage(pageNumber - 1).use { page ->
                            val scale = 3.0f
                            val width = (page.width * scale).toInt().coerceIn(1200, 2600)
                            val height = (page.height * scale).toInt().coerceIn(1500, 3400)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                api.setImage(bitmap)
                                val pageText = api.getUTF8Text().orEmpty().trim()
                                if (pageText.isNotBlank()) {
                                    output.append("\n\n[PDF page ${pageNumber}]\n")
                                    output.append(pageText)
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            api.end()
        }
        return output.toString().trim()
    }

    private fun ensureModel(tessData: File, language: String) {
        val target = File(tessData, "$language.traineddata")
        if (target.isFile && target.length() > MIN_MODEL_BYTES) return
        val temporary = File(tessData, "$language.traineddata.download")
        val url = URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$language.traineddata")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "تعذر تنزيل بيانات OCR للغة $language (HTTP ${connection.responseCode})"
            }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_MODEL_BYTES) { "حجم بيانات OCR غير متوقع للغة $language" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temporary.length() > MIN_MODEL_BYTES) { "ملف بيانات OCR للغة $language غير مكتمل" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "تعذر تثبيت بيانات OCR للغة $language" }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    companion object {
        private const val MIN_NATIVE_TEXT_CHARS = 40
        private const val MIN_OCR_TEXT_CHARS = 20
        private const val MIN_MODEL_BYTES = 100_000L
        private const val MAX_MODEL_BYTES = 50L * 1024L * 1024L
    }
}
