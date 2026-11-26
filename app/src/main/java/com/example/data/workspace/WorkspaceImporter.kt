package com.example.data.workspace

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import java.util.Locale

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
                    if (!seenNames.add(normalized.lowercase(Locale.ROOT))) {
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
                val details = warnings.distinct().take(8).joinToString("\n")
                throw IllegalArgumentException(
                    "لم يتم العثور على كتاب اجتاز التحقق في workspace/<grade>/<subject>[/<component>]/." +
                        if (details.isBlank()) "" else "\n$details"
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
        require(selectedGrades.isNotEmpty()) {
            "يجب اختيار صف واحد على الأقل من شاشة المعاينة قبل الاستيراد."
        }
        val availableGrades = preview.grades.map { it.gradeKey }.toSet()
        val gradesToImport = selectedGrades.distinct()
        require(gradesToImport.all { it in availableGrades }) {
            "تم طلب استيراد صف غير موجود في معاينة الحزمة."
        }
        val committed = mutableListOf<ImportedWorkspace>()
        try {
            val allFiles = collectRelativeFiles(tempDir)
            val allFileSet = allFiles.toHashSet()
            val baseStamp = System.currentTimeMillis()
            gradesToImport.forEachIndexed { idx, gradeKey ->
                val gradeFiles = allFiles.filter { it.startsWith("workspace/$gradeKey/") }
                val validationWarnings = mutableListOf<String>()
                val validatedPreview = parseGradeFromDir(
                    baseDir = tempDir,
                    destRootPath = tempDir.absolutePath,
                    gradeKey = gradeKey,
                    zipNames = gradeFiles,
                    zipNameSet = allFileSet,
                    warningsOut = validationWarnings
                )
                require(validatedPreview.books.isNotEmpty()) {
                    "لم يجتز أي كتاب في الصف $gradeKey التحقق النهائي قبل النسخ."
                }

                val workspaceId = "import-${baseStamp}-$gradeKey-${idx + 1}"
                val destDir = File(workspacesRoot.apply { mkdirs() }, workspaceId)
                try {
                    val validBookFiles = validatedPreview.books.flatMap { book ->
                        val bookPrefix = book.relativePath.trimEnd('/')
                        allFiles.filter { it == bookPrefix || it.startsWith("$bookPrefix/") }
                    }.distinct()
                    require(validBookFiles.isNotEmpty()) {
                        "لم يتم تحديد ملفات الكتب الصالحة للنسخ في الصف $gradeKey."
                    }

                    validatedPreview.books.forEach { book ->
                        val sourceBookDir = File(tempDir, book.relativePath)
                        val destinationBookDir = File(destDir, book.relativePath)
                        val sourceCanonical = sourceBookDir.canonicalPath
                        val tempCanonical = tempDir.canonicalPath + File.separator
                        require(sourceCanonical.startsWith(tempCanonical) && sourceBookDir.isDirectory) {
                            "مسار كتاب غير آمن أو غير موجود: ${book.relativePath}"
                        }
                        sourceBookDir.copyRecursively(destinationBookDir, overwrite = true)
                    }
                    listOf("full_backup.json", "manifest.json").forEach { name ->
                        val source = File(tempDir, name)
                        if (source.isFile) source.copyTo(File(destDir, name), overwrite = true)
                    }

                    val commitWarnings = validationWarnings.toMutableList()
                    val parsed = parseGradeFromDir(
                        baseDir = destDir,
                        destRootPath = destDir.absolutePath,
                        gradeKey = gradeKey,
                        zipNames = validBookFiles,
                        zipNameSet = validBookFiles.toSet(),
                        warningsOut = commitWarnings
                    )
                    require(parsed.books.isNotEmpty()) {
                        "فشل التحقق من الكتب بعد نسخها إلى مساحة العمل الداخلية للصف $gradeKey."
                    }
                    committed.add(
                        ImportedWorkspace(
                            workspaceId = workspaceId,
                            gradeKey = gradeKey,
                            rootPath = destDir.absolutePath,
                            title = "$gradeKey • ${parsed.books.size} كتاب",
                            books = parsed.books,
                            lessons = parsed.lessons,
                            filesCount = validBookFiles.size,
                            warnings = commitWarnings.distinct(),
                            importedAt = baseStamp + idx
                        )
                    )
                } catch (e: Exception) {
                    destDir.deleteRecursively()
                    throw e
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

    suspend fun importFromUri(uri: Uri): ImportedWorkspace =
        importFirstGrade(inspectZipFromUri(uri))

    suspend fun importFromStream(
        inputStream: InputStream,
        declaredSizeBytes: Long? = null
    ): ImportedWorkspace = importFirstGrade(
        inspectZipFromStream(inputStream, declaredSizeBytes)
    )

    private suspend fun importFirstGrade(preview: StagedWorkspacePreview): ImportedWorkspace {
        val firstGrade = preview.grades.firstOrNull()?.gradeKey
            ?: throw IllegalArgumentException("لم يتم العثور على صف دراسي صالح داخل ملف ZIP")
        val first = commitStagedGrades(preview, listOf(firstGrade)).firstOrNull()
            ?: throw IllegalStateException("تعذر إكمال استيراد الصف المحدد")
        return if (preview.grades.size > 1) {
            first.copy(
                warnings = (first.warnings + "الأرشيف يحتوي أكثر من صف؛ تم عرض الصف الأول في الاستيراد السريع.").distinct()
            )
        } else {
            first
        }
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
            path.startsWith("workspace/$gradeKey/") && CANONICAL_INDEX_REGEX.matches(path)
        }
        val availableIndexSha256 = zipNames
            .filter { it.equals("index.json", ignoreCase = true) || it.endsWith("/index.json", ignoreCase = true) }
            .mapNotNull { relative ->
                val file = File(baseDir, relative)
                val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
                val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                relative.replace('\\\\', '/').trimStart('/').lowercase(Locale.ROOT) to hash
            }.toMap()

        val books = mutableListOf<BookRef>()
        val lessons = mutableListOf<LessonRef>()

        for (indexPath in bookIndexPaths) {
            val bookRel = indexPath.removeSuffix("/index.json")
            val lastSegment = bookRel.substringAfterLast('/')
            val partKeyFromPath = lastSegment.takeIf { it == "P01" || it == "P02" }
            val identityBookRel = if (partKeyFromPath != null) bookRel.substringBeforeLast('/') else bookRel
            val segments = identityBookRel.split("/").drop(2)
            val subjectKey = segments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
            val componentKey = segments.getOrNull(1)?.takeIf { it.isNotBlank() }
            val bookKey = segments.joinToString("/") + (partKeyFromPath?.let { "|$it" } ?: "")

            val localManifestFile = File(baseDir, "$bookRel/book_manifest.json").let { primary ->
                if (primary.exists()) primary else File(baseDir, "$bookRel/manifest.json")
            }
            val parentManifestFile = File(baseDir, "$identityBookRel/book_manifest.json")
            val hasLocalManifest = localManifestFile.isFile
            val manifestFile = when {
                hasLocalManifest -> localManifestFile
                partKeyFromPath != null && parentManifestFile.isFile -> parentManifestFile
                else -> localManifestFile
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

            val parentIndexFile = File(baseDir, "$identityBookRel/index.json")
            if (partKeyFromPath != null && parentIndexFile.isFile &&
                (!hasLocalManifest ||
                    manifestObj?.str("sourceLayout").equals("MERGED_PARTS_FILE", ignoreCase = true))
            ) {
                // Merged part indexes are derived views; import the merged root once.
                continue
            }

            val indexFile = File(baseDir, indexPath)
            val indexRaw = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrElse {
                warningsOut.add("تعذر قراءة index.json: $bookRel")
                continue
            }
            val indexObj: JsonObject = try {
                json.parseToJsonElement(indexRaw).jsonObject
            } catch (_: Exception) {
                warningsOut.add("index.json غير صالح: $bookRel")
                continue
            }

            val manifestRaw = if (manifestFile.isFile) {
                try {
                    manifestFile.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    warningsOut.add("تعذر قراءة book_manifest.json: $bookRel")
                    continue
                }
            } else null
            val validation = WorkspaceContractValidator.validate(
                indexRaw = indexRaw,
                manifestRaw = manifestRaw,
                expectedGrade = gradeKey,
                expectedSubject = subjectKey,
                bookRelativePath = bookRel,
                availableRelativePaths = zipNameSet,
                availableIndexSha256 = availableIndexSha256
            )
            warningsOut.addAll(validation.warnings)
            if (!validation.isValid) {
                warningsOut.addAll(validation.errors.map { "$bookRel: $it" })
                continue
            }

            val fallbackTitle = subjectKey + (if (componentKey != null) " / $componentKey" else "")
            val title = firstNonBlank(
                indexObj.str("displayName"),
                indexObj.str("title"),
                manifestObj?.str("displayName"),
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

            val missingLessonPdfs = parsedLessons.filter { it.pdfPath.isNullOrBlank() }
            val unreadableLessonPdfs = parsedLessons.filter { lesson ->
                !lesson.pdfPath.isNullOrBlank() && !isReadablePdf(lesson.pdfPath)
            }
            if (validation.canonicalContract && (missingLessonPdfs.isNotEmpty() || unreadableLessonPdfs.isNotEmpty())) {
                warningsOut.add("$bookRel: تعذر فتح أو مطابقة ملفات PDF لبعض الدروس؛ تم إيقاف استيراد هذا الكتاب.")
                continue
            }
            unreadableLessonPdfs.forEach { lesson ->
                warningsOut.add("$bookRel: ملف PDF غير قابل للفتح للدرس ${lesson.title}.")
            }

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
                    sourcePdfPath = resolvedBookPdfPath,
                    subjectName = firstNonBlank(indexObj.str("subjectName"), manifestObj?.str("subjectName")).ifBlank { null }
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
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
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
            val unitFolderKey = WorkspaceContractValidator.sourceUnitFolderKey(u)
            val uk = WorkspaceContractValidator.sourceUnitKey(u)
            require(uk.isNotBlank() && unitFolderKey.isNotBlank()) {
                "الوحدة رقم ${ui + 1} لا تحتوي مفتاحاً ثابتاً أو unitNumber صالحاً."
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
                require(lk.isNotBlank()) {
                    "درس رقم ${li + 1} في الوحدة $uk لا يحتوي مفتاحاً ثابتاً أو lessonNumber صالحاً."
                }
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
                val lessonNumStr = (l.str("lessonNumber").toIntOrNull() ?: lessonNumber(lk)).let { num ->
                    if (num >= 0) num.toString().padStart(2, '0') else "-1"
                }

                val linkedPdf = (l["sliceFile"] as? JsonObject)?.str("workspaceUri").orEmpty()
                val candidates = buildList {
                    if (linkedPdf.isNotBlank()) {
                        add(linkedPdf)
                        if (linkedPdf.startsWith("workspace/", ignoreCase = true)) {
                            add(linkedPdf.removePrefix("workspace/"))
                        }
                    }
                    if (p == "P01" || p == "P02") {
                        add("$bookRel/$p/units/$unitFolderKey/lesson_$lessonNumStr.pdf")
                        add("$bookRel/$p/units/$unitFolderKey/$lk.pdf")
                    }
                }
                val matchedPdfRel = candidates.firstOrNull { candidate ->
                    zipNameSet.any { it.equals(candidate, ignoreCase = true) }
                }
                val txtCandidates = buildList {
                    if (p == "P01" || p == "P02") {
                        add("$bookRel/$p/units/$unitFolderKey/lesson_$lessonNumStr.txt")
                        add("$bookRel/$p/units/$unitFolderKey/lesson_$lessonNumStr.md")
                        add("$bookRel/$p/units/$unitFolderKey/$lk.txt")
                    }
                    add("$bookRel/P01/units/$unitFolderKey/lesson_$lessonNumStr.txt")
                    add("$bookRel/P02/units/$unitFolderKey/lesson_$lessonNumStr.txt")
                    add("$bookRel/units/$unitFolderKey/lesson_$lessonNumStr.txt")
                    add("$bookRel/units/$unitFolderKey/lesson_$lessonNumStr.md")
                }
                val matchedTxtRel = txtCandidates.firstOrNull { it in zipNameSet }
                val htmlCandidates = buildList {
                    if (p == "P01" || p == "P02") {
                        add("$bookRel/$p/units/$unitFolderKey/lesson_$lessonNumStr/index.html")
                        add("$bookRel/$p/units/$unitFolderKey/lesson_$lessonNumStr.html")
                    }
                    add("$bookRel/interactive/$unitFolderKey/$lk/index.html")
                    add("$bookRel/units/$unitFolderKey/$lk/index.html")
                    add("$bookRel/units/$unitFolderKey/lesson_$lessonNumStr.html")
                }
                val matchedHtmlRel = htmlCandidates.firstOrNull { it in zipNameSet }
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
                        pdfPath = matchedPdfRel?.let { File(rootPath, it).absolutePath },
                        sourceTextPath = matchedTxtRel?.let { File(rootPath, it).absolutePath },
                        interactivePath = matchedHtmlRel?.let { File(rootPath, it).absolutePath }
                    )
                )
            }
        }
        return result
    }

    private fun isReadablePdf(path: String): Boolean {
        val file = File(path)
        if (!file.isFile || file.length() < 8L) return false
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderer.pageCount > 0 }
            }
        }.getOrDefault(false)
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
            "^workspace/G\\d{2}/[^/]+(?:/[^/]+){0,2}/index\\.json$",
            RegexOption.IGNORE_CASE
        )
        private val PART_INDEX_REGEX = Regex(
            "^workspace/G\\d{2}/.+/(?:P01|P02|BOTH)/index\\.json$",
            RegexOption.IGNORE_CASE
        )
    }
}
