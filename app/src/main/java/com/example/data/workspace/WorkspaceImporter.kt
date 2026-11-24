package com.example.data.workspace

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.BookRef
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import com.example.model.StagedGradeSummary
import com.example.model.StagedWorkspacePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class WorkspaceImporter(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val workspacesRoot: File
        get() = File(context.filesDir, "edumini/workspaces")

    suspend fun inspectZipFromUri(uri: Uri): StagedWorkspacePreview = withContext(Dispatchers.IO) {
        val declaredSize = queryFileSize(uri)
        if (declaredSize != null && declaredSize > MAX_ZIP_BYTES) {
            throw IllegalArgumentException("حجم ZIP أكبر من الحد التجريبي (120 MB)")
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح ملف ZIP المختار")
        stream.use { input ->
            inspectZipFromStream(input, declaredSize)
        }
    }

    suspend fun inspectZipFromStream(
        inputStream: InputStream,
        declaredSizeBytes: Long? = null
    ): StagedWorkspacePreview = withContext(Dispatchers.IO) {
        if (declaredSizeBytes != null && declaredSizeBytes > MAX_ZIP_BYTES) {
            throw IllegalArgumentException("حجم ZIP أكبر من الحد التجريبي (120 MB)")
        }

        val timestamp = System.currentTimeMillis()
        val stagingId = "stage-$timestamp"
        val rootDir = workspacesRoot.apply { mkdirs() }
        val tempDir = File(rootDir, ".tmp-$stagingId")

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
            verifyExportManifest(tempDir)

            val gradeRoots = zipNames.mapNotNull { path ->
                GRADE_ROOT_REGEX.find(path)?.groupValues?.get(1)
            }.distinct()

            if (gradeRoots.isEmpty()) {
                throw IllegalArgumentException("ZIP يجب أن يحتوي workspace/Gxx/")
            }

            val zipNameSet = zipNames.toHashSet()
            val warnings = mutableListOf<String>()
            val gradeSummaries = gradeRoots.mapNotNull { grade ->
                val parsed = parseGradeFromDir(
                    baseDir = tempDir,
                    destRootPath = tempDir.absolutePath,
                    gradeKey = grade,
                    zipNames = zipNames,
                    zipNameSet = zipNameSet,
                    warningsOut = warnings
                )
                if (parsed.books.isEmpty()) null else StagedGradeSummary(
                    gradeKey = grade,
                    booksCount = parsed.books.size,
                    lessonsCount = parsed.lessons.size,
                    bookTitles = parsed.books.map { it.title }
                )
            }

            if (gradeSummaries.isEmpty()) {
                throw IllegalArgumentException(
                    "لم يتم العثور على index.json مرجعي في workspace/<grade>/<subject>[/<component>]/"
                )
            }

            StagedWorkspacePreview(
                stagingId = stagingId,
                tempDirPath = tempDir.absolutePath,
                fileCount = zipNames.size,
                expandedBytes = expandedBytes,
                grades = gradeSummaries,
                warnings = warnings.distinct()
            )
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    suspend fun commitStagedGrades(
        preview: StagedWorkspacePreview,
        selectedGrades: List<String>
    ): List<ImportedWorkspace> = withContext(Dispatchers.IO) {
        val tempDir = File(preview.tempDirPath)
        if (!tempDir.exists()) {
            throw IllegalStateException("انتهت صلاحية المعاينة المؤقتة؛ أعد اختيار الملف.")
        }
        val gradesToImport = selectedGrades.ifEmpty {
            listOfNotNull(preview.grades.firstOrNull()?.gradeKey)
        }.distinct()
        val committed = mutableListOf<ImportedWorkspace>()
        try {
            val allFiles = collectRelativeFiles(tempDir)
            val allFileSet = allFiles.toHashSet()
            val baseStamp = System.currentTimeMillis()
            gradesToImport.forEachIndexed { idx, gradeKey ->
                val workspaceId = "import-${baseStamp}-$gradeKey-${idx + 1}"
                val destDir = File(workspacesRoot.apply { mkdirs() }, workspaceId)
                // Copy grade subtree + shared top metadata into dedicated workspace folder
                val gradeSourceDir = File(tempDir, "workspace/$gradeKey")
                if (gradeSourceDir.exists()) {
                    val destGradeDir = File(destDir, "workspace/$gradeKey")
                    gradeSourceDir.copyRecursively(destGradeDir, overwrite = true)
                }
                listOf("full_backup.json", "manifest.json").forEach { name ->
                    val source = File(tempDir, name)
                    if (source.isFile) source.copyTo(File(destDir, name), overwrite = true)
                }
                val warnings = mutableListOf<String>()
                val parsed = parseGradeFromDir(
                    baseDir = destDir,
                    destRootPath = destDir.absolutePath,
                    gradeKey = gradeKey,
                    zipNames = allFiles.filter { it.startsWith("workspace/$gradeKey/") },
                    zipNameSet = allFileSet,
                    warningsOut = warnings
                )
                if (parsed.books.isNotEmpty()) {
                    committed.add(
                        ImportedWorkspace(
                            workspaceId = workspaceId,
                            gradeKey = gradeKey,
                            rootPath = destDir.absolutePath,
                            title = "$gradeKey • ${parsed.books.size} كتاب",
                            books = parsed.books,
                            lessons = parsed.lessons,
                            filesCount = allFiles.count { it.startsWith("workspace/$gradeKey/") },
                            warnings = warnings.distinct(),
                            importedAt = baseStamp + idx
                        )
                    )
                }
            }
            if (committed.isEmpty()) {
                throw IllegalArgumentException("لم يتم العثور على كتب صالحة في الصفوف المحددة")
            }
            committed
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun discardStagedPreview(preview: StagedWorkspacePreview) {
        runCatching { File(preview.tempDirPath).deleteRecursively() }
    }

    suspend fun importFromUri(uri: Uri): ImportedWorkspace {
        val preview = inspectZipFromUri(uri)
        val firstGrade = listOf(preview.grades.first().gradeKey)
        val results = commitStagedGrades(preview, firstGrade)
        val first = results.first()
        return if (preview.grades.size > 1) {
            first.copy(warnings = (first.warnings + "الأرشيف يحتوي أكثر من صف؛ تم عرض الصف الأول في الاستيراد السريع.").distinct())
        } else first
    }

    suspend fun importFromStream(
        inputStream: InputStream,
        declaredSizeBytes: Long? = null
    ): ImportedWorkspace {
        val preview = inspectZipFromStream(inputStream, declaredSizeBytes)
        val firstGrade = listOf(preview.grades.first().gradeKey)
        val results = commitStagedGrades(preview, firstGrade)
        val first = results.first()
        return if (preview.grades.size > 1) {
            first.copy(warnings = (first.warnings + "الأرشيف يحتوي أكثر من صف؛ تم عرض الصف الأول في الاستيراد السريع.").distinct())
        } else first
    }

    private data class ParsedGradeTree(
        val books: List<BookRef>,
        val lessons: List<LessonRef>
    )

    private fun parseGradeFromDir(
        baseDir: File,
        destRootPath: String,
        gradeKey: String,
        zipNames: List<String>,
        zipNameSet: Set<String>,
        warningsOut: MutableList<String>
    ): ParsedGradeTree {
        val bookIndexPaths = zipNames.filter { path ->
            path.startsWith("workspace/$gradeKey/") &&
                CANONICAL_INDEX_REGEX.matches(path) &&
                !PART_INDEX_REGEX.matches(path)
        }

        val books = mutableListOf<BookRef>()
        val lessons = mutableListOf<LessonRef>()

        for (indexPath in bookIndexPaths) {
            val bookRel = indexPath.removeSuffix("/index.json")
            val segments = bookRel.split("/").drop(2)
            val subjectKey = segments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
            val componentKey = segments.getOrNull(1)?.takeIf { it.isNotBlank() }
            val bookKey = segments.joinToString("/")

            val manifestFile = File(baseDir, "$bookRel/book_manifest.json").let { primary ->
                if (primary.exists()) primary else File(baseDir, "$bookRel/manifest.json")
            }

            val manifestObj: JsonObject? = if (manifestFile.exists()) {
                runCatching {
                    json.parseToJsonElement(manifestFile.readText()).jsonObject
                }.onFailure {
                    warningsOut.add("manifest غير موجود أو غير صالح: $bookRel")
                }.getOrNull()
            } else {
                warningsOut.add("manifest غير موجود أو غير صالح: $bookRel")
                null
            }

            val indexFile = File(baseDir, indexPath)
            val indexObj: JsonObject = try {
                json.parseToJsonElement(indexFile.readText()).jsonObject
            } catch (_: Exception) {
                warningsOut.add("index.json غير صالح: $bookRel")
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
                grade = gradeKey,
                rootPath = destRootPath,
                bookKey = bookKey,
                bookTitle = title,
                subjectKey = subjectKey,
                componentKey = componentKey,
                bookRel = bookRel,
                zipNameSet = zipNameSet
            )

            val manifestCover = manifestObj?.str("cover")
                ?.trim()
                ?.removePrefix("./")
                ?.takeIf { it.isNotBlank() }
            val coverRel = buildList {
                if (manifestCover != null) add("$bookRel/$manifestCover")
                add("$bookRel/cover.png")
                add("$bookRel/cover.jpg")
                add("$bookRel/cover.jpeg")
                add("$bookRel/cover.webp")
                add("$bookRel/assets/cover.png")
                add("$bookRel/P01/cover.png")
                add("workspace/$gradeKey/$subjectKey/cover.png")
            }.firstOrNull { candidate ->
                zipNameSet.any { it.equals(candidate, ignoreCase = true) }
            }?.let { matchedKey ->
                zipNameSet.first { it.equals(matchedKey, ignoreCase = true) }
            }
            val resolvedCoverPath = coverRel?.let { rel ->
                File(baseDir, rel).takeIf { it.exists() }?.absolutePath
            }

            val bookPdfRel = buildList {
                add("$bookRel/book.pdf")
                add("$bookRel/${subjectKey.lowercase()}.pdf")
                add("$bookRel/P01/book.pdf")
                add("$bookRel/source.pdf")
            }.firstOrNull { candidate ->
                zipNameSet.any { it.equals(candidate, ignoreCase = true) }
            }?.let { matchedKey ->
                zipNameSet.first { it.equals(matchedKey, ignoreCase = true) }
            } ?: zipNames.firstOrNull { path ->
                path.startsWith("$bookRel/", ignoreCase = true) &&
                    path.endsWith(".pdf", ignoreCase = true) &&
                    !path.contains("/units/", ignoreCase = true)
            }
            val resolvedBookPdfPath = bookPdfRel?.let { rel ->
                File(baseDir, rel).takeIf { it.exists() }?.absolutePath
            }

            books.add(
                BookRef(
                    bookKey = bookKey,
                    title = title,
                    subjectKey = subjectKey,
                    componentKey = componentKey,
                    relativePath = bookRel,
                    lessonsCount = parsedLessons.size,
                    coverPath = resolvedCoverPath,
                    sourcePdfPath = resolvedBookPdfPath
                )
            )
            lessons.addAll(parsedLessons)
        }

        return ParsedGradeTree(books = books, lessons = lessons)
    }

    private fun verifyExportManifest(root: File) {
        val manifestFile = File(root, "manifest.json")
        if (!manifestFile.isFile) return
        val manifest = runCatching {
            json.parseToJsonElement(manifestFile.readText()).jsonObject
        }.getOrElse { throw IllegalArgumentException("manifest.json غير صالح: ${it.message}") }
        val artifacts = manifest["artifacts"] as? JsonArray ?: return
        artifacts.forEach { element ->
            val artifact = element as? JsonObject
                ?: throw IllegalArgumentException("عنصر artifacts غير صالح في manifest.json")
            val relative = artifact.str("path")
            val expectedHash = artifact.str("sha256").lowercase()
            val expectedBytes = artifact.str("bytes").toLongOrNull()
            require(relative.isNotBlank() && expectedHash.matches(Regex("^[a-f0-9]{64}$"))) {
                "بيانات بصمة غير صالحة في manifest.json"
            }
            val safeRelative = safePath(relative)
            val file = File(root, safeRelative)
            val canonicalRoot = root.canonicalPath + File.separator
            require(file.canonicalPath.startsWith(canonicalRoot) && file.isFile) {
                "ملف مفقود أو مسار غير آمن في الحزمة: $relative"
            }
            if (expectedBytes != null) {
                require(file.length() == expectedBytes) { "حجم الملف لا يطابق manifest: $relative" }
            }
            val actualHash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            require(actualHash == expectedHash) { "فشل التحقق من SHA-256 للملف: $relative" }
        }
    }

    private fun collectRelativeFiles(root: File): List<String> {
        val base = root.toPath()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { base.relativize(it.toPath()).toString().replace('\\', '/') }
            .toList()
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
        val hasUnsafeSegment = normalized.split('/').any { it == ".." || it == "." }
        if (normalized.startsWith("/") ||
            DRIVE_PREFIX_REGEX.containsMatchIn(normalized) ||
            hasUnsafeSegment ||
            normalized.contains('\u0000') ||
            normalized.length > 420
        ) {
            throw IllegalArgumentException("ZIP يحتوي مساراً غير آمن: $rawPath")
        }
        return normalized
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
