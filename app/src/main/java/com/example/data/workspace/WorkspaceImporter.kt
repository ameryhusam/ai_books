package com.example.data.workspace

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.BookRef
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class WorkspaceImporter(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val workspacesRoot: File
        get() = File(context.filesDir, "edumini/workspaces")

    suspend fun importFromUri(uri: Uri): ImportedWorkspace = withContext(Dispatchers.IO) {
        val declaredSize = queryFileSize(uri)
        if (declaredSize != null && declaredSize > MAX_ZIP_BYTES) {
            throw IllegalArgumentException("حجم ZIP أكبر من الحد التجريبي (120 MB)")
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح ملف ZIP المختار")
        stream.use { input ->
            importFromStream(input, declaredSize)
        }
    }

    suspend fun importFromStream(
        inputStream: InputStream,
        declaredSizeBytes: Long? = null
    ): ImportedWorkspace = withContext(Dispatchers.IO) {
        if (declaredSizeBytes != null && declaredSizeBytes > MAX_ZIP_BYTES) {
            throw IllegalArgumentException("حجم ZIP أكبر من الحد التجريبي (120 MB)")
        }

        val timestamp = System.currentTimeMillis()
        val workspaceId = "import-$timestamp"
        val rootDir = workspacesRoot.apply { mkdirs() }
        val tempDir = File(rootDir, ".tmp-$workspaceId")
        val destDir = File(rootDir, workspaceId)

        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val zipNames = mutableListOf<String>()
        val seenNames = HashSet<String>()
        var expandedBytes = 0L

        try {
            val countingStream = CountingInputStream(BufferedInputStream(inputStream), MAX_ZIP_BYTES)
            ZipInputStream(countingStream).use { zis ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    val normalized = safePath(entry.name)
                    if (!seenNames.add(normalized)) {
                        throw IllegalArgumentException("ZIP يحتوي ملفاً مكرراً: $normalized")
                    }
                    zipNames.add(normalized)
                    if (zipNames.size > MAX_FILES) {
                        throw IllegalArgumentException("الأرشيف يحتوي عدداً كبيراً جداً من الملفات")
                    }

                    val targetFile = File(tempDir, normalized)
                    val canonicalTemp = tempDir.canonicalPath + File.separator
                    val canonicalTarget = targetFile.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalTemp)) {
                        throw IllegalArgumentException("ZIP يحتوي مساراً غير آمن: ${entry.name}")
                    }
                    targetFile.parentFile?.mkdirs()

                    FileOutputStream(targetFile).use { out ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            expandedBytes += read
                            if (expandedBytes > MAX_EXPANDED_BYTES) {
                                throw IllegalArgumentException("حجم الملفات بعد فك الضغط يتجاوز 350 MB")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                    zis.closeEntry()
                }
            }

            if (zipNames.isEmpty()) {
                throw IllegalArgumentException("ZIP فارغ")
            }

            val gradeRoots = zipNames.mapNotNull { path ->
                GRADE_ROOT_REGEX.find(path)?.groupValues?.get(1)
            }.distinct()

            if (gradeRoots.isEmpty()) {
                throw IllegalArgumentException("ZIP يجب أن يحتوي workspace/Gxx/")
            }

            val bookIndexPaths = zipNames.filter { path ->
                CANONICAL_INDEX_REGEX.matches(path) && !PART_INDEX_REGEX.matches(path)
            }

            val books = mutableListOf<BookRef>()
            val lessons = mutableListOf<LessonRef>()
            val warnings = mutableListOf<String>()
            val zipNameSet = zipNames.toHashSet()
            val primaryGrade = gradeRoots.first()
            val destRootPath = destDir.absolutePath

            for (indexPath in bookIndexPaths) {
                val bookRel = indexPath.removeSuffix("/index.json")
                val segments = bookRel.split("/").drop(2)
                val subjectKey = segments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
                val componentKey = segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                val bookKey = segments.joinToString("/")

                val manifestFile = File(tempDir, "$bookRel/book_manifest.json").let { primary ->
                    if (primary.exists()) primary else File(tempDir, "$bookRel/manifest.json")
                }

                val manifestObj: JsonObject? = if (manifestFile.exists()) {
                    runCatching {
                        json.parseToJsonElement(manifestFile.readText()).jsonObject
                    }.onFailure {
                        warnings.add("manifest غير موجود أو غير صالح: $bookRel")
                    }.getOrNull()
                } else {
                    warnings.add("manifest غير موجود أو غير صالح: $bookRel")
                    null
                }

                val indexFile = File(tempDir, indexPath)
                val indexObj: JsonObject = try {
                    json.parseToJsonElement(indexFile.readText()).jsonObject
                } catch (_: Exception) {
                    warnings.add("index.json غير صالح: $bookRel")
                    continue
                }

                val fallbackTitle = subjectKey + (if (componentKey != null) " / $componentKey" else "")
                val title = firstNonBlank(
                    manifestObj?.str("title"),
                    manifestObj?.str("bookTitle"),
                    manifestObj?.str("displayTitle"),
                    manifestObj?.str("subjectTitle"),
                    fallbackTitle
                )

                val parsedLessons = parseLessons(
                    index = indexObj,
                    grade = primaryGrade,
                    rootPath = destRootPath,
                    bookKey = bookKey,
                    bookTitle = title,
                    subjectKey = subjectKey,
                    componentKey = componentKey,
                    bookRel = bookRel,
                    zipNameSet = zipNameSet
                )

                books.add(
                    BookRef(
                        bookKey = bookKey,
                        title = title,
                        subjectKey = subjectKey,
                        componentKey = componentKey,
                        relativePath = bookRel,
                        lessonsCount = parsedLessons.size
                    )
                )
                lessons.addAll(parsedLessons)
            }

            if (books.isEmpty()) {
                throw IllegalArgumentException(
                    "لم يتم العثور على index.json مرجعي في workspace/<grade>/<subject>[/<component>]/"
                )
            }

            if (!tempDir.renameTo(destDir)) {
                tempDir.copyRecursively(destDir, overwrite = true)
                tempDir.deleteRecursively()
            }

            if (gradeRoots.size > 1) {
                warnings.add("الأرشيف يحتوي أكثر من صف؛ تم عرض الصف الأول فقط في هذه النسخة.")
            }

            ImportedWorkspace(
                workspaceId = workspaceId,
                gradeKey = primaryGrade,
                rootPath = destRootPath,
                title = "$primaryGrade • ${books.size} كتاب",
                books = books,
                lessons = lessons,
                filesCount = zipNames.size,
                warnings = warnings,
                importedAt = timestamp
            )
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    private fun parseLessons(
        index: JsonObject,
        grade: String,
        rootPath: String,
        bookKey: String,
        bookTitle: String,
        subjectKey: String,
        componentKey: String?,
        bookRel: String,
        zipNameSet: Set<String>
    ): List<LessonRef> {
        val units = (index["units"] as? JsonArray) ?: return emptyList()
        val result = mutableListOf<LessonRef>()

        units.forEachIndexed { ui, unitElem ->
            val u = unitElem as? JsonObject ?: return@forEachIndexed
            val uk = firstNonBlank(
                u.str("unitKey"),
                u.str("key"),
                u.str("unitId"),
                u.str("unitNumber"),
                u.str("id"),
                "unit_${(ui + 1).toString().padStart(2, '0')}"
            )
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
                val lk = firstNonBlank(
                    l.str("lessonKey"),
                    l.str("key"),
                    l.str("lessonId"),
                    l.str("lessonNumber"),
                    l.str("id"),
                    "lesson_${(li + 1).toString().padStart(2, '0')}"
                )
                val lt = firstNonBlank(
                    l.str("title"),
                    l.str("lessonTitle"),
                    l.str("name"),
                    lk
                )
                val startPage = firstNonBlank(
                    l.str("pdfPageStart"),
                    l.str("pageStart"),
                    l.str("startPage")
                )
                val endPage = firstNonBlank(
                    l.str("pdfPageEnd"),
                    l.str("pageEnd"),
                    l.str("endPage")
                )
                val p = firstNonBlank(l.str("partKey"), l.str("part"), unitPart)
                val lessonNumStr = lessonNumber(lk).let { num ->
                    if (num >= 0) num.toString().padStart(2, '0') else "-1"
                }

                val candidates = buildList {
                    if (p == "P01" || p == "P02") {
                        add("$bookRel/$p/units/$uk/lesson_$lessonNumStr.pdf")
                    }
                    add("$bookRel/P01/units/$uk/lesson_$lessonNumStr.pdf")
                    add("$bookRel/P02/units/$uk/lesson_$lessonNumStr.pdf")
                }

                val matchedPdfRel = candidates.firstOrNull { it in zipNameSet }
                val pageRange = if (startPage.isNotBlank() && endPage.isNotBlank()) {
                    "$startPage–$endPage"
                } else null

                result.add(
                    LessonRef(
                        grade = grade,
                        rootPath = rootPath,
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
                        pdfPath = matchedPdfRel?.let { File(rootPath, it).absolutePath }
                    )
                )
            }
        }
        return result
    }

    private fun queryFileSize(uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                } else null
            }
        }.getOrNull()
    }

    private fun safePath(rawPath: String): String {
        val normalized = rawPath.replace('\\', '/')
        val hasParentSegment = normalized.split('/').any { it == ".." }
        if (normalized.startsWith("/") ||
            DRIVE_PREFIX_REGEX.containsMatchIn(normalized) ||
            hasParentSegment ||
            normalized.contains('\u0000') ||
            normalized.length > 420
        ) {
            throw IllegalArgumentException("ZIP يحتوي مساراً غير آمن: $rawPath")
        }
        return normalized.replace("./", "")
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

    private class CountingInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private var total = 0L

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) addAndCheck(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = delegate.read(b, off, len)
            if (count > 0) addAndCheck(count.toLong())
            return count
        }

        private fun addAndCheck(delta: Long) {
            total += delta
            if (total > maxBytes) {
                throw IllegalArgumentException("حجم ZIP أكبر من الحد التجريبي (120 MB)")
            }
        }
    }

    companion object {
        const val MAX_ZIP_BYTES = 120L * 1024L * 1024L
        const val MAX_EXPANDED_BYTES = 350L * 1024L * 1024L
        const val MAX_FILES = 6000

        private val DRIVE_PREFIX_REGEX = Regex("^[A-Za-z]:")
        private val TRAILING_NUMBER_REGEX = Regex("(\\d+)$")
        private val GRADE_ROOT_REGEX = Regex("^workspace/(G\\d{2})/")
        private val CANONICAL_INDEX_REGEX = Regex(
            "^workspace/G\\d{2}/[^/]+(?:/[^/]+)?/index\\.json$",
            RegexOption.IGNORE_CASE
        )
        private val PART_INDEX_REGEX = Regex(
            "^workspace/G\\d{2}/[^/]+/(?:P01|P02|BOTH)/index\\.json$",
            RegexOption.IGNORE_CASE
        )
    }
}
