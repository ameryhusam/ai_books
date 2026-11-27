package com.example.data.workspace

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.model.BookRef
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import com.example.model.StagedGradeSummary
import com.example.model.StagedWorkspacePreview
import com.example.model.WorkspaceIndexRef
import com.example.model.WorkspaceUnitRef
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

    suspend fun inspectZipFromUri(
        uri: Uri,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): StagedWorkspacePreview = withContext(Dispatchers.IO) {
        val declaredSize = queryFileSize(uri)
        if (declaredSize != null && declaredSize > MAX_ZIP_BYTES) {
            throw IllegalArgumentException("حجم ZIP أكبر من الحد التجريبي (120 MB)")
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح ملف ZIP المختار")
        stream.use { input ->
            inspectZipFromStream(input, declaredSize, onProgress)
        }
    }

    suspend fun inspectZipFromStream(
        inputStream: InputStream,
        declaredSizeBytes: Long? = null,
        onProgress: (String, Int) -> Unit = { _, _ -> }
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
        onProgress("تهيئة مساحة الفحص المؤقتة", 5)

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
                    onProgress(
                        "فك ضغط الملفات (${zipNames.size})",
                        (10 + zipNames.size * 40 / MAX_FILES).coerceIn(10, 50)
                    )
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
            onProgress("التحقق من ملفات الحزمة وبصماتها", 58)
            val manifestReviewPaths = mutableSetOf<String>()
            val manifestReviewFindings = verifyExportManifest(tempDir, manifestReviewPaths)
            onProgress("اكتشاف الصفوف والمواد", 68)

            val gradeRoots = zipNames.mapNotNull { path ->
                GRADE_ROOT_REGEX.find(path)?.groupValues?.get(1)
            }.distinct()

            if (gradeRoots.isEmpty()) {
                throw IllegalArgumentException("ZIP يجب أن يحتوي workspace/Gxx/")
            }

            val zipNameSet = zipNames.toHashSet()
            val warnings = mutableListOf<String>().apply { addAll(manifestReviewFindings) }
            val reviewFindings = manifestReviewFindings.toMutableList()
            val gradeSummaries = gradeRoots.mapIndexedNotNull { gradeIndex, grade ->
                onProgress(
                    "تحليل فهارس الصف ${grade}",
                    (70 + (gradeIndex * 20 / gradeRoots.size.coerceAtLeast(1))).coerceAtMost(90)
                )
                val parsed = parseGradeFromDir(
                    baseDir = tempDir,
                    destRootPath = tempDir.absolutePath,
                    gradeKey = grade,
                    zipNames = zipNames,
                    zipNameSet = zipNameSet,
                    warningsOut = warnings,
                    reviewFindingsOut = reviewFindings,
                    reviewIndexPaths = manifestReviewPaths
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

            onProgress("اكتملت معاينة الصفوف والمواد", 100)
            StagedWorkspacePreview(
                stagingId = stagingId,
                tempDirPath = tempDir.absolutePath,
                fileCount = zipNames.size,
                expandedBytes = expandedBytes,
                grades = gradeSummaries,
                warnings = warnings.distinct(),
                requiresReviewConfirmation = reviewFindings.isNotEmpty(),
                reviewFindings = reviewFindings.distinct(),
                reviewIndexPaths = manifestReviewPaths.toList()
            )
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    suspend fun commitStagedGrades(
        preview: StagedWorkspacePreview,
        selectedGrades: List<String>,
        allowReviewImport: Boolean = false,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): List<ImportedWorkspace> = withContext(Dispatchers.IO) {
        val tempDir = File(preview.tempDirPath)
        if (!tempDir.exists()) {
            throw IllegalStateException("انتهت صلاحية المعاينة المؤقتة؛ أعد اختيار الملف.")
        }
        require(selectedGrades.isNotEmpty()) {
            "يجب اختيار صف واحد على الأقل من شاشة المعاينة قبل الاستيراد."
        }
        require(!preview.requiresReviewConfirmation || allowReviewImport) {
            "تحتوي الحزمة على فهارس تحتاج إلى مراجعة أو بصمات index غير مطابقة. اعرض التحذيرات وأكد الاستيراد الاستثنائي أولاً."
        }
        val availableGrades = preview.grades.map { it.gradeKey }.toSet()
        val previewReviewIndexPaths = preview.reviewIndexPaths.map { it.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT) }.toSet()
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
                onProgress(
                    "التحقق من بيانات الصف ${gradeKey}",
                    (10 + idx * 70 / gradesToImport.size.coerceAtLeast(1)).coerceAtMost(80)
                )
                val gradeFiles = allFiles.filter { it.startsWith("workspace/$gradeKey/") }
                val validationWarnings = mutableListOf<String>()
                val commitReviewFindings = mutableListOf<String>()
                val validatedPreview = parseGradeFromDir(
                    baseDir = tempDir,
                    destRootPath = tempDir.absolutePath,
                    gradeKey = gradeKey,
                    zipNames = gradeFiles,
                    zipNameSet = allFileSet,
                    warningsOut = validationWarnings,
                    reviewFindingsOut = commitReviewFindings,
                    reviewIndexPaths = previewReviewIndexPaths
                )
                require(allowReviewImport || commitReviewFindings.isEmpty()) {
                    "تغيرت حالة مراجعة الفهرس أثناء الاستيراد للصف $gradeKey؛ يلزم تأكيد الاستيراد بوضع Review."
                }
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

                    validatedPreview.books.forEachIndexed { bookIndex, book ->
                        val bookProgress = 20 + (idx * 70 / gradesToImport.size.coerceAtLeast(1)) +
                            ((bookIndex + 1) * 45 / validatedPreview.books.size.coerceAtLeast(1) / gradesToImport.size.coerceAtLeast(1))
                        onProgress("نسخ المادة ${book.title}", bookProgress.coerceAtMost(88))
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
                        warningsOut = commitWarnings,
                        reviewIndexPaths = previewReviewIndexPaths
                    )
                    require(parsed.books.isNotEmpty()) {
                        "فشل التحقق من الكتب بعد نسخها إلى مساحة العمل الداخلية للصف $gradeKey."
                    }
                    onProgress("تثبيت مواد ودروس الصف ${gradeKey}", (10 + (idx + 1) * 70 / gradesToImport.size.coerceAtLeast(1)).coerceAtMost(95))
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
                            importedAt = baseStamp + idx,
                            indexRecords = parsed.indexRecords,
                            units = parsed.units
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
            onProgress("اكتمل تجهيز الصفوف المحددة", 100)
            committed
        } catch (error: Exception) {
            // Multi-grade imports are staged as one package operation: remove earlier grade copies
            // if a later grade fails before the repository commits the Room transaction.
            committed.forEach { imported ->
                runCatching { File(imported.rootPath).deleteRecursively() }
            }
            throw error
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
        val lessons: List<LessonRef>,
        val indexRecords: List<WorkspaceIndexRef> = emptyList(),
        val units: List<WorkspaceUnitRef> = emptyList()
    )

    private fun parseGradeFromDir(
        baseDir: File,
        destRootPath: String,
        gradeKey: String,
        zipNames: List<String>,
        zipNameSet: Set<String>,
        warningsOut: MutableList<String>,
        reviewFindingsOut: MutableList<String>? = null,
        reviewIndexPaths: Set<String> = emptySet()
    ): ParsedGradeTree {
        val bookIndexPaths = zipNames.filter { path ->
            path.startsWith("workspace/$gradeKey/") && CANONICAL_INDEX_REGEX.matches(path)
        }
        val availableIndexSha256 = zipNames
            .filter { it.equals("index.json", ignoreCase = true) || it.endsWith("/index.json", ignoreCase = true) }
            .mapNotNull { relative ->
                val file = File(baseDir, relative)
                val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
                val hash = sha256(raw)
                relative.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT) to hash
            }.toMap()

        val books = mutableListOf<BookRef>()
        val lessons = mutableListOf<LessonRef>()
        val indexRecords = mutableListOf<WorkspaceIndexRef>()
        val unitRefs = mutableListOf<WorkspaceUnitRef>()

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
            if (!validation.isImportableWithReview) {
                warningsOut.addAll(validation.errors.map { "$bookRel: $it" })
                continue
            }
            val normalizedIndexPath = indexPath.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)
            val manifestIndexMismatch = normalizedIndexPath in reviewIndexPaths
            val bookReviewFindings = validation.reviewRequiredErrors.map { "$bookRel: $it" } +
                if (manifestIndexMismatch) listOf("$bookRel: بصمة SHA-256 في manifest.json لا تطابق ملف index.json.") else emptyList()
            if (bookReviewFindings.isNotEmpty()) {
                reviewFindingsOut?.addAll(bookReviewFindings)
                warningsOut.addAll(bookReviewFindings)
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
                manifest = manifestObj,
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

            val destIndexFile = File(destRootPath, indexPath)
            val destManifestRel = if (manifestFile.isFile) {
                runCatching {
                    manifestFile.relativeTo(baseDir).path.replace('\\', '/')
                }.getOrNull()
            } else null
            val destManifestFile = destManifestRel?.let { File(destRootPath, it) }
            val syncTimestamp = System.currentTimeMillis()
            indexRecords.add(
                WorkspaceIndexRef(
                    bookKey = bookKey,
                    indexUriOrPath = destIndexFile.absolutePath,
                    indexRelativePath = indexPath,
                    manifestUriOrPath = destManifestFile?.absolutePath,
                    manifestRelativePath = destManifestRel,
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
                val uk = WorkspaceContractValidator.sourceUnitKey(unit)
                if (uk.isBlank()) return@forEachIndexed
                val resolvedUnitPart = WorkspaceContractValidator.resolveUnitPartKey(unit, indexObj, manifestObj)
                    .takeIf { it.isNotBlank() }
                unitRefs.add(
                    WorkspaceUnitRef(
                        bookKey = bookKey,
                        unitKey = uk,
                        title = firstNonBlank(unit.str("title"), unit.str("unitTitle"), unit.str("name"), uk),
                        partKey = resolvedUnitPart,
                        sortOrder = unitOrder,
                        sourceIndexPath = destIndexFile.absolutePath,
                        proposedPartKey = unit.str("proposedPartKey").ifBlank { null },
                        assignmentStatus = unit.str("assignmentStatus").ifBlank { "need_review" },
                        assignmentSource = unit.str("assignmentSource").ifBlank { "LEGACY_UNVERIFIED" },
                        assignmentEvidence = unit.str("assignmentEvidence")
                    )
                )
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
                    subjectName = BookMetadataMapper.subjectDisplayName(
                        subjectKey,
                        firstNonBlank(indexObj.str("subjectName"), manifestObj?.str("subjectName")).ifBlank { null }
                    ),
                    approvalStatus = if (bookReviewFindings.isNotEmpty()) "NEEDS_REVIEW" else indexObj.str("approvalStatus").ifBlank { "LEGACY_UNVERIFIED" },
                    requiresReview = bookReviewFindings.isNotEmpty() || indexObj.str("requiresReview").equals("true", true),
                    sourceReviewStatus = manifestObj?.str("sourceReviewStatus")?.ifBlank { null }
                )
            )
            lessons.addAll(parsedLessons)
        }

        return ParsedGradeTree(
            books = books,
            lessons = lessons,
            indexRecords = indexRecords,
            units = unitRefs
        )
    }

    private fun verifyExportManifest(root: File, reviewIndexPathsOut: MutableSet<String> = mutableSetOf()): List<String> {
        val reviewFindings = mutableListOf<String>()
        val manifestFile = File(root, "manifest.json")
        if (!manifestFile.isFile) return emptyList()
        val manifest = runCatching {
            json.parseToJsonElement(manifestFile.readText()).jsonObject
        }.getOrElse { throw IllegalArgumentException("manifest.json غير صالح: ${it.message}") }
        val artifacts = manifest["artifacts"] as? JsonArray ?: return emptyList()
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
            if (actualHash != expectedHash) {
                if (safeRelative.substringAfterLast('/').equals("index.json", ignoreCase = true)) {
                    reviewIndexPathsOut.add(safeRelative.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT))
                    reviewFindings.add("بصمة SHA-256 لملف الفهرس $relative غير مطابقة؛ سيُستورد بوضع Review بعد موافقتك.")
                } else {
                    throw IllegalArgumentException("فشل التحقق من SHA-256 للملف: $relative")
                }
            }
        }
        return reviewFindings.distinct()
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
        manifest: JsonObject? = null,
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
        val cleanBookPrefix = bookRel.trim('/').lowercase(Locale.ROOT).let { if (it.isBlank()) "" else "$it/" }
        val bookEntries = zipNameSet.mapNotNull { rel ->
            val norm = rel.replace('\\', '/').trimStart('/')
            val lower = norm.lowercase(Locale.ROOT)
            if (cleanBookPrefix.isEmpty() || lower.startsWith(cleanBookPrefix)) lower to norm else null
        }.toMap()

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
            val unitPart = WorkspaceContractValidator.resolveUnitPartKey(u, index, manifest)

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
                    l.str("pdfPage"),
                    l.str("pageStart"),
                    l.str("startPage")
                )
                val endPage = firstNonBlank(
                    l.str("pdfPageEnd"),
                    l.str("pdfPage"),
                    l.str("pageEnd"),
                    l.str("endPage")
                )
                val printedStartPage = l.str("printedPageStart")
                val printedEndPage = l.str("printedPageEnd")
                val printedPageRange = if (printedStartPage.isNotBlank() && printedEndPage.isNotBlank()) {
                    "$printedStartPage–$printedEndPage"
                } else null
                val p = firstNonBlank(l.str("partKey"), l.str("part"), unitPart).uppercase(Locale.ROOT)
                val lessonNumStr = (l.str("lessonNumber").toIntOrNull() ?: lessonNumber(lk)).let { num ->
                    if (num >= 0) num.toString().padStart(2, '0') else "-1"
                }

                val candidates = WorkspaceContractValidator.lessonPdfCandidates(
                    lesson = l,
                    unit = u,
                    lessonPart = p,
                    bookRelativePath = bookRel,
                    expectedGrade = grade,
                    expectedSubject = subjectKey
                )
                val matchedPdfRel = candidates.firstNotNullOfOrNull { candidate ->
                    val normCand = candidate.replace('\\', '/').trim().trimStart('/').lowercase(Locale.ROOT)
                    bookEntries[normCand]
                        ?: (if (cleanBookPrefix.isNotEmpty() && !normCand.startsWith(cleanBookPrefix)) bookEntries["$cleanBookPrefix$normCand"] else null)
                        ?: bookEntries.entries.firstOrNull { (k, _) -> k.endsWith("/$normCand") }?.value
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
                        interactivePath = matchedHtmlRel?.let { File(rootPath, it).absolutePath },
                        printedPageRange = printedPageRange
                    )
                )
            }
        }
        return result
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun isReadablePdf(path: String): Boolean {
        val file = File(path)
        if (!file.isFile || file.length() < 8L) return false
        val hasPdfHeader = runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(5)
                val read = input.read(header)
                read >= 5 && String(header, Charsets.US_ASCII) == "%PDF-"
            }
        }.getOrDefault(false)
        if (!hasPdfHeader) return false
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return true
        }
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
