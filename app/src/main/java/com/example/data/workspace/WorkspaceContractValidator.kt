package com.example.data.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Locale

data class WorkspaceContractValidation(
    val canonicalContract: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val reviewRequiredErrors: List<String> = emptyList()
) {
    /** Strict validation used by direct folder binding. */
    val isValid: Boolean get() = errors.isEmpty() && reviewRequiredErrors.isEmpty()

    /** Errors in this set may be imported only after explicit user acceptance and remain flagged for review. */
    val isImportableWithReview: Boolean get() = errors.isEmpty()
}

/** Validates indexer2 source identity, approval, index fingerprint and lesson file references. */
object WorkspaceContractValidator {
    private val json = Json { ignoreUnknownKeys = true }
    private val PART_FROM_PATH_REGEX = Regex("(?:^|/)(P0[12])(?:/|$)", RegexOption.IGNORE_CASE)

    fun validate(
        indexRaw: String,
        manifestRaw: String?,
        expectedGrade: String,
        expectedSubject: String,
        bookRelativePath: String,
        availableRelativePaths: Set<String>,
        availableIndexSha256: Map<String, String> = emptyMap()
    ): WorkspaceContractValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val index = runCatching { json.parseToJsonElement(indexRaw).jsonObject }.getOrElse {
            return WorkspaceContractValidation(
                false,
                listOf("[index.json] ملف JSON غير صالح: ${it.message}"),
                emptyList()
            )
        }
        val manifest = manifestRaw?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
                errors.add("[book_manifest.json] ملف JSON غير صالح: ${it.message}")
                null
            }
        }
        if (manifest == null && errors.isEmpty()) {
            warnings.add("ملف book_manifest.json غير موجود؛ تمت قراءة الكتاب بوضع التوافق القديم.")
        }

        val manifestVersion = manifest?.string("manifestVersion").orEmpty()
        val strictProvenance = manifestVersion.equals("edu.book_manifest.v2", ignoreCase = true)
        val manifestLayout = manifest?.string("sourceLayout").orEmpty()
        val indexLayout = index.string("sourceLayout")
        val sourceLayout = firstNonBlank(manifestLayout, indexLayout)
        val sourceReviewStatus = firstNonBlank(manifest?.string("sourceReviewStatus"), index.string("sourceReviewStatus"))
        if (strictProvenance) {
            val sourceMetadata = index["sourceMetadata"] as? JsonObject
            if (sourceMetadata?.get("sourceRequiresReview")?.jsonPrimitive?.contentOrNull != "false") {
                errors.add("[index.json → sourceMetadata.sourceRequiresReview] يجب أن يكون false بعد اعتماد مراجعة المصدر.")
            }
            if (sourceMetadata?.string("partEvidence").orEmpty().isBlank()) {
                errors.add("[index.json → sourceMetadata.partEvidence] دليل تحديد نمط المصدر مطلوب في manifest v2.")
            }
        }
        val canonical = manifestVersion.startsWith("edu.book_manifest.") || sourceLayout.isNotBlank()

        if (!canonical) {
            val legacyApprovalStatus = index.string("approvalStatus")
            if (legacyApprovalStatus.isNotBlank() && !legacyApprovalStatus.equals("APPROVED", ignoreCase = true)) {
                errors.add("[index.json → approvalStatus] الفهرس غير معتمد: '$legacyApprovalStatus'.")
            }
            if (index.boolean("requiresReview") || index.boolean("requires_review")) {
                errors.add("[index.json → requiresReview] الفهرس يحمل علامة requiresReview=true.")
            }
            if (sourceReviewStatus.isNotBlank() && !sourceReviewStatus.equals("VERIFIED", ignoreCase = true)) {
                errors.add("[book_manifest.json → sourceReviewStatus] المصدر يحتاج إلى مراجعة: '$sourceReviewStatus'.")
            }
            val legacyIndexSha = manifest?.string("indexSha256").orEmpty()
            if (legacyIndexSha.isNotBlank() &&
                (!legacyIndexSha.matches(Regex("^[a-fA-F0-9]{64}$")) || !sha256(indexRaw).equals(legacyIndexSha, ignoreCase = true))
            ) {
                errors.add("[book_manifest.json → indexSha256] بصمة index.json غير صالحة أو غير مطابقة.")
            }
            val legacyCanonicalIndexSha = manifest?.string("canonicalIndexSha256").orEmpty()
            if (legacyCanonicalIndexSha.isNotBlank() &&
                (!legacyCanonicalIndexSha.matches(Regex("^[a-fA-F0-9]{64}$")) || !sha256(indexRaw).equals(legacyCanonicalIndexSha, ignoreCase = true))
            ) {
                errors.add("[book_manifest.json → canonicalIndexSha256] بصمة الفهرس canonical غير صالحة أو غير مطابقة.")
            }
        }

        validateIdentityField(manifest, "gradeKey", expectedGrade, "book_manifest.json", errors)
        validateIdentityField(index, "gradeKey", expectedGrade, "index.json", errors)
        validateIdentityField(manifest, "subjectKey", expectedSubject, "book_manifest.json", errors)
        validateIdentityField(index, "subjectKey", expectedSubject, "index.json", errors)

        if (canonical) {
            if (manifestLayout.isNotBlank() && indexLayout.isNotBlank() && !manifestLayout.equals(indexLayout, ignoreCase = true)) {
                errors.add("[book_manifest.json / index.json] sourceLayout غير متطابق ($manifestLayout مقابل $indexLayout).")
            }
            if (sourceLayout.uppercase(Locale.ROOT) !in setOf("SINGLE_PART_FILE", "MERGED_PARTS_FILE")) {
                errors.add("[index.json → sourceLayout] قيمة غير معروفة: '${sourceLayout.ifBlank { "فارغ" }}' (المتوقع SINGLE_PART_FILE أو MERGED_PARTS_FILE).")
            }
            if (!sourceReviewStatus.equals("VERIFIED", ignoreCase = true)) {
                errors.add("[book_manifest.json → sourceReviewStatus] المصدر غير معتمد: '${sourceReviewStatus.ifBlank { "مفقود" }}' (المطلوب VERIFIED).")
            }
            val approvalStatus = index.string("approvalStatus")
            if (!approvalStatus.equals("APPROVED", ignoreCase = true)) {
                errors.add("[index.json → approvalStatus] الفهرس لم يحصل على اعتماد APPROVED (الحالي: '${approvalStatus.ifBlank { "مفقود" }}').")
            }
            if (index.boolean("requiresReview") || index.boolean("requires_review")) {
                errors.add("[index.json → requiresReview] الفهرس يحمل علامة requiresReview=true ولا يمكن اعتماده قبل المراجعة.")
            }
            val sourceSha = manifest?.string("sourceSha256").orEmpty()
            if (!sourceSha.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                errors.add("[book_manifest.json → sourceSha256] بصمة SHA-256 مفقودة أو غير مكتملة.")
            }
            val expectedIndexSha = manifest?.string("indexSha256").orEmpty()
            if (!expectedIndexSha.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                errors.add("[book_manifest.json → indexSha256] بصمة indexSha256 مفقودة أو غير صالحة.")
            } else if (!sha256(indexRaw).equals(expectedIndexSha, ignoreCase = true)) {
                errors.add("[book_manifest.json → indexSha256] بصمة index.json الحالية لا تطابق indexSha256 المسجلة في book_manifest.json.")
            }

            val canonicalIndexPath = manifest?.string("canonicalIndexPath").orEmpty()
            val canonicalIndexSha = manifest?.string("canonicalIndexSha256").orEmpty()
            if (canonicalIndexSha.isNotBlank() &&
                (!canonicalIndexSha.matches(Regex("^[a-fA-F0-9]{64}$")) ||
                    !sha256(indexRaw).equals(canonicalIndexSha, ignoreCase = true))
            ) {
                errors.add("[book_manifest.json → canonicalIndexSha256] بصمة الفهرس canonical غير صالحة أو لا تطابق ملف index.json الحالي.")
            }
            if (canonicalIndexPath.isNotBlank() || canonicalIndexSha.isNotBlank()) {
                val manifestPartKey = manifest?.string("partKey").orEmpty().uppercase(Locale.ROOT)
                val indexPartKey = index.string("partKey").uppercase(Locale.ROOT)
                val bookPathLeaf = bookRelativePath.trim('/').substringAfterLast('/', "")
                if (sourceLayout.equals("SINGLE_PART_FILE", ignoreCase = true)) {
                    if (manifestPartKey !in setOf("P01", "P02") || indexPartKey != manifestPartKey) {
                        errors.add("[book_manifest.json / index.json → partKey] مصدر مستقل يتطلب partKey متطابقًا P01 أو P02 في manifest والفهرس.")
                    }
                    if (bookPathLeaf.isNotBlank() && !bookPathLeaf.equals(manifestPartKey, ignoreCase = true)) {
                        errors.add("[book_manifest.json → sourceLayout] فهرس المصدر المستقل يجب أن يكون داخل مجلد الجزء نفسه، لا في جذر الكتاب.")
                    }
                    val expectedPath = "$manifestPartKey/index.json"
                    if (canonicalIndexPath.isNotBlank() && !canonicalIndexPath.equals(expectedPath, ignoreCase = true)) {
                        errors.add("[book_manifest.json → canonicalIndexPath] المسار '$canonicalIndexPath' لا يطابق الفهرس المستقل المتوقع '$expectedPath'.")
                    }
                } else if (sourceLayout.equals("MERGED_PARTS_FILE", ignoreCase = true)) {
                    if (bookPathLeaf.equals("P01", ignoreCase = true) || bookPathLeaf.equals("P02", ignoreCase = true)) {
                        errors.add("[book_manifest.json → sourceLayout] الفهرس الأم للمصدر المدمج يجب أن يكون في جذر الكتاب، لا داخل مجلد جزء مشتق.")
                    }
                    if (manifestPartKey != "BOTH" || indexPartKey != "BOTH") {
                        errors.add("[book_manifest.json / index.json → partKey] المصدر المدمج يجب أن يحمل partKey=BOTH على مستوى المصدر.")
                    }
                    if (canonicalIndexPath.isNotBlank() && !canonicalIndexPath.equals("index.json", ignoreCase = true)) {
                        errors.add("[book_manifest.json → canonicalIndexPath] الفهرس الأم للمصدر المدمج يجب أن يكون index.json في الجذر.")
                    }
                }
            }

            val partIndexes = manifest?.get("partIndexes") as? JsonArray
            if (!partIndexes.isNullOrEmpty()) {
                val seenPartIndexPaths = HashSet<String>()
                partIndexes.forEach { element ->
                    val partIndex = element as? JsonObject
                    val relative = partIndex?.string("path").orEmpty().replace('\\', '/').trim()
                    val expectedHash = partIndex?.string("sha256").orEmpty()
                    if (relative.isBlank() || relative.startsWith("/") ||
                        relative.split('/').any { it.isBlank() || it == "." || it == ".." } ||
                        !relative.matches(Regex("P0[12]/index\\.json", RegexOption.IGNORE_CASE)) ||
                        !expectedHash.matches(Regex("^[a-fA-F0-9]{64}$"))
                    ) {
                        errors.add("[book_manifest.json → partIndexes] مسار أو بصمة فهرس جزء غير صالحة: '$relative'.")
                        return@forEach
                    }
                    val normalizedPath = normalizePath(
                        if (bookRelativePath.isBlank()) relative else "$bookRelativePath/$relative"
                    )
                    if (!seenPartIndexPaths.add(normalizedPath)) {
                        errors.add("[book_manifest.json → partIndexes] مسار فهرس جزء مكرر: '$relative'.")
                        return@forEach
                    }
                    if (normalizedPath !in availableRelativePaths.map { normalizePath(it) }.toSet()) {
                        errors.add("[book_manifest.json → partIndexes] ملف الفهرس المشتق غير موجود: '$relative'.")
                        return@forEach
                    }
                    val actualHash = availableIndexSha256[normalizedPath]
                    if (actualHash.isNullOrBlank()) {
                        errors.add("[book_manifest.json → partIndexes] تعذر قراءة بصمة الملف المشتق للتحقق: '$relative'.")
                    } else if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                        errors.add("[book_manifest.json → partIndexes] بصمة '$relative' لا تطابق القيمة المسجلة في manifest.")
                    }
                }
            }
        } else {
            warnings.add("المجلد يستخدم صيغة توافق قديمة أو غير مقيدة؛ لم تتوفر جميع حقول هوية المصدر الحديثة.")
        }

        val units = index["units"] as? JsonArray
        if (units.isNullOrEmpty()) {
            errors.add("[index.json → units] الفهرس لا يحتوي مصفوفة units صالحة أو أن المصفوفة فارغة.")
            return WorkspaceContractValidation(canonical, errors.distinct(), warnings.distinct())
        }
        val available = availableRelativePaths.map { normalizePath(it) }.toSet()
        val seenUnitKeys = HashSet<String>()
        val missingAssignmentUnits = mutableListOf<String>()

        units.forEachIndexed { unitIndex, unitElement ->
            val unit = unitElement as? JsonObject
            if (unit == null) {
                errors.add("[index.json → units[$unitIndex]] العنصر ليس كائن JSON.")
                return@forEachIndexed
            }
            val unitKey = sourceUnitKey(unit)
            val unitTitle = firstNonBlank(unit.string("title"), unit.string("unitTitle"), unit.string("name"))
            if (unitKey.isBlank()) {
                errors.add("[index.json → units[$unitIndex]] الوحدة لا تحتوي unitKey أو unitNumber ثابتاً.")
            }
            if (unitTitle.isBlank()) {
                errors.add("[index.json → units[${unitKey.ifBlank { unitIndex.toString() }}].title] الوحدة لا تحتوي عنواناً.")
            }
            if (unitKey.isNotBlank() && !seenUnitKeys.add(unitKey.lowercase(Locale.ROOT))) {
                errors.add("[index.json → units[$unitIndex].unitKey] مفتاح الوحدة مكرر: $unitKey")
            }

            val partKey = resolveUnitPartKey(unit, index, manifest)
            val assignmentStatus = unit.string("assignmentStatus")
            val assignmentSource = unit.string("assignmentSource").uppercase(Locale.ROOT)
            val assignmentEvidence = unit.string("assignmentEvidence").trim()
            if (strictProvenance) {
                if (assignmentSource !in setOf("INDEX_EVIDENCE", "REVIEWER")) {
                    errors.add("[index.json → units[$unitKey].assignmentSource] مصدر إسناد الوحدة غير موثوق أو غير مسجل.")
                }
                if (assignmentEvidence.isBlank()) {
                    errors.add("[index.json → units[$unitKey].assignmentEvidence] دليل الإسناد مطلوب في manifest v2.")
                }
            }
            if (canonical && partKey !in setOf("P01", "P02")) {
                val proposedPartKey = unit.string("proposedPartKey")
                val unresolvedAssignment = assignmentStatus.equals("need_review", ignoreCase = true) ||
                    proposedPartKey.isNotBlank() || unit.boolean("provisionalAssignment")
                val message = "[index.json → units[${unitKey.ifBlank { unitIndex.toString() }}].partKey] الوحدة «${unitTitle.ifBlank { unitKey }}» غير مسندة إلى P01 أو P02."
                if (unresolvedAssignment) {
                    errors.add("$message [REVIEW_REQUIRED]")
                } else {
                    errors.add(message)
                }
            } else if (!canonical && partKey.isBlank()) {
                warnings.add("إسناد الجزء (partKey) غير موجود للوحدة ${unitTitle.ifBlank { unitKey }}.")
            }
            if (canonical && assignmentStatus.isBlank()) {
                missingAssignmentUnits.add(unitTitle.ifBlank { unitKey })
            }
            if (assignmentStatus.isNotBlank() && !assignmentStatus.equals("assigned", ignoreCase = true)) {
                errors.add("[index.json → units[${unitKey.ifBlank { unitIndex.toString() }}].assignmentStatus] الوحدة «${unitTitle.ifBlank { unitKey }}» حالتها '$assignmentStatus' (المطلوب 'assigned').")
            }
            if (unit.boolean("provisionalAssignment")) {
                errors.add("[index.json → units[${unitKey.ifBlank { unitIndex.toString() }}].provisionalAssignment] الوحدة تحمل علامة provisionalAssignment=true.")
            }

            val lessons = unit["lessons"] as? JsonArray
            if (lessons.isNullOrEmpty()) {
                errors.add("[index.json → units[${unitKey.ifBlank { unitIndex.toString() }}].lessons] الوحدة «${unitTitle.ifBlank { unitKey }}» لا تحتوي دروساً.")
                return@forEachIndexed
            }
            val seenLessonKeys = HashSet<String>()
            lessons.forEachIndexed lessonLoop@{ lessonIndex, lessonElement ->
                val lesson = lessonElement as? JsonObject
                if (lesson == null) {
                    errors.add("[index.json → units[$unitKey].lessons[$lessonIndex]] عنصر درس غير صالح.")
                    return@lessonLoop
                }
                val lessonKey = sourceLessonKey(lesson)
                val lessonTitle = firstNonBlank(lesson.string("title"), lesson.string("lessonTitle"), lesson.string("name"))
                if (lessonKey.isBlank()) {
                    errors.add("[index.json → units[$unitKey].lessons[$lessonIndex]] الدرس لا يحتوي lessonKey أو lessonNumber.")
                }
                if (lessonTitle.isBlank()) {
                    errors.add("[index.json → units[$unitKey].lessons[${lessonKey.ifBlank { lessonIndex.toString() }}].title] الدرس لا يحتوي عنواناً.")
                }
                if (lessonKey.isNotBlank() && !seenLessonKeys.add(lessonKey.lowercase(Locale.ROOT))) {
                    errors.add("[index.json → units[$unitKey].lessons] مفتاح الدرس مكرر: $lessonKey")
                }

                val startPage = firstInt(lesson, "pdfPageStart", "pdfPage", "pageStart", "startPage")
                val endPage = firstInt(lesson, "pdfPageEnd", "pdfPage", "pageEnd", "endPage")
                if (canonical && (startPage == null || endPage == null)) {
                    errors.add("[index.json → $unitKey/$lessonKey] نطاق صفحات PDF (pdfPageStart / pdfPageEnd) غير مكتمل للدرس «${lessonTitle.ifBlank { lessonKey }}».")
                } else if (startPage != null && endPage != null && (startPage < 1 || endPage < startPage)) {
                    errors.add("[index.json → $unitKey/$lessonKey] نطاق صفحات PDF غير صالح ($startPage..$endPage) للدرس «${lessonTitle.ifBlank { lessonKey }}».")
                }

                val lessonPart = firstNonBlank(
                    lesson.string("partKey"),
                    lesson.string("part"),
                    partKey
                ).uppercase(Locale.ROOT)
                val candidates = lessonPdfCandidates(
                    lesson = lesson,
                    unit = unit,
                    lessonPart = lessonPart,
                    bookRelativePath = bookRelativePath,
                    expectedGrade = expectedGrade,
                    expectedSubject = expectedSubject
                )
                val pdfExists = candidates.any { candidate ->
                    val normalized = normalizePath(candidate)
                    normalized in available || available.any { it.endsWith("/$normalized") }
                }
                if (!pdfExists) {
                    val samplePaths = candidates.take(2).joinToString(" | ").ifBlank {
                        "$lessonPart/units/${sourceUnitFolderKey(unit)}/lesson_XX.pdf"
                    }
                    val message = "[index.json → $unitKey/$lessonKey] ملف PDF غير موجود للدرس «${lessonTitle.ifBlank { lessonKey }}» (تم البحث في: $samplePaths)."
                    if (canonical) errors.add(message) else warnings.add(message)
                }
            }
        }
        if (missingAssignmentUnits.isNotEmpty()) {
            val preview = missingAssignmentUnits.take(3).joinToString("، ")
            val suffix = if (missingAssignmentUnits.size > 3) " (+${missingAssignmentUnits.size - 3})" else ""
            warnings.add("حقل assignmentStatus غير موجود في ${missingAssignmentUnits.size} وحدة ($preview$suffix)؛ تم الاعتماد على partKey مع حالة مراجعة المصدر.")
        }
        val reviewMarkers = listOf(
            "sourceMetadata",
            "sourceRequiresReview",
            "partEvidence",
            "sourceReviewStatus",
            "approvalStatus",
            "requiresReview",
            "indexSha256",
            "canonicalIndexSha256",
            "assignmentSource",
            "assignmentEvidence",
            "assignmentStatus",
            "provisionalAssignment",
            "REVIEW_REQUIRED"
        )
        val reviewErrors = errors.filter { message ->
            reviewMarkers.any { marker -> message.contains(marker, ignoreCase = true) } ||
                (message.contains("partIndexes", ignoreCase = true) && message.contains("لا تطابق"))
        }
        val blockingErrors = errors.filterNot { it in reviewErrors }
        return WorkspaceContractValidation(
            canonicalContract = canonical,
            errors = blockingErrors.distinct(),
            warnings = warnings.distinct(),
            reviewRequiredErrors = reviewErrors.distinct()
        )
    }

    /**
     * Resolves a unit's `partKey` ("P01" / "P02") from the unit object, falling back cleanly
     * to top-level book/manifest `partKey` (when single-part) or from the first lesson's PDF path.
     */
    fun resolveUnitPartKey(
        unit: JsonObject,
        index: JsonObject? = null,
        manifest: JsonObject? = null
    ): String {
        val direct = firstNonBlank(unit.string("partKey"), unit.string("part")).uppercase(Locale.ROOT)
        if (direct == "P01" || direct == "P02") return direct

        // A proposal or explicit need_review state must never be promoted from a lesson path.
        val provisionalAssignment = unit.boolean("provisionalAssignment") ||
            unit.string("assignmentStatus").equals("need_review", ignoreCase = true) ||
            unit.string("proposedPartKey").isNotBlank()
        if (provisionalAssignment) return ""

        val lessons = unit["lessons"] as? JsonArray
        if (lessons != null) {
            for (elem in lessons) {
                val l = elem as? JsonObject ?: continue
                val lp = firstNonBlank(l.string("partKey"), l.string("part")).uppercase(Locale.ROOT)
                if (lp == "P01" || lp == "P02") return lp
                val explicitPath = firstNonBlank(
                    (l["sliceFile"] as? JsonObject)?.string("workspaceUri"),
                    (l["sliceFile"] as? JsonObject)?.string("relativePath"),
                    l.string("pdfPath"),
                    l.string("path"),
                    l.string("filePath")
                )
                val pathPart = PART_FROM_PATH_REGEX.find(explicitPath.replace('\\', '/'))
                    ?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
                if (pathPart == "P01" || pathPart == "P02") return pathPart
            }
        }

        val unitDirHint = firstNonBlank(unit.string("unitPath"), unit.string("relativePath"), unit.string("folderPath"))
        val unitPathPart = PART_FROM_PATH_REGEX.find(unitDirHint.replace('\\', '/'))
            ?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
        if (unitPathPart == "P01" || unitPathPart == "P02") return unitPathPart

        val bookPart = firstNonBlank(
            index?.string("partKey"),
            index?.string("part"),
            (index?.get("metadata") as? JsonObject)?.string("partKey"),
            manifest?.string("partKey"),
            manifest?.string("part")
        ).uppercase(Locale.ROOT)
        if (bookPart == "P01" || bookPart == "P02") return bookPart

        return direct
    }

    /**
     * Generates all valid indexer2 candidate relative paths for a lesson PDF, accommodating
     * full `workspace/Gxx/...`, grade-root `MATH/...`, and single-book root selections.
     */
    fun lessonPdfCandidates(
        lesson: JsonObject,
        unit: JsonObject,
        lessonPart: String,
        bookRelativePath: String,
        expectedGrade: String = "",
        expectedSubject: String = ""
    ): List<String> {
        val candidates = linkedSetOf<String>()
        val cleanBookRel = bookRelativePath.trim('/')
        val declaredWorkspaceUri = (lesson["sliceFile"] as? JsonObject)?.string("workspaceUri")
            ?.trim()?.replace('\\', '/').orEmpty()
        if (declaredWorkspaceUri.isNotBlank()) {
            // workspaceUri must be a relative workspace path, never an absolute path or URI.
            if (declaredWorkspaceUri.startsWith("/") ||
                Regex("^[A-Za-z]:").containsMatchIn(declaredWorkspaceUri) ||
                declaredWorkspaceUri.contains("://")
            ) return emptyList()
            val normalizedUri = declaredWorkspaceUri.removePrefix("./").trimStart('/')
            // If the declared path is unsafe or missing, do not silently bind a different
            // PDF by reconstructing a path from unit/lesson numbers.
            if (normalizedUri.split('/').any { it == ".." || it.isBlank() }) return emptyList()

            candidates.add(normalizedUri)
            if (normalizedUri.startsWith("workspace/", ignoreCase = true)) {
                val withoutWorkspace = normalizedUri.substringAfter('/')
                candidates.add(withoutWorkspace)
                if (expectedGrade.isNotBlank() && withoutWorkspace.startsWith("$expectedGrade/", ignoreCase = true)) {
                    val withoutGrade = withoutWorkspace.substringAfter('/')
                    candidates.add(withoutGrade)
                    if (expectedSubject.isNotBlank() && withoutGrade.startsWith("$expectedSubject/", ignoreCase = true)) {
                        candidates.add(withoutGrade.substringAfter('/'))
                    }
                }
            }
            if (cleanBookRel.isNotBlank() && normalizedUri.startsWith("$cleanBookRel/", ignoreCase = true)) {
                candidates.add(normalizedUri.removePrefix("$cleanBookRel/"))
            }
            val partMarker = Regex("(?:^|/)(P0[12])/(.+)$", RegexOption.IGNORE_CASE).find(normalizedUri)
            if (partMarker != null) {
                val partFolder = partMarker.groupValues[1]
                val afterPartFolder = partMarker.groupValues[2]
                candidates.add("$partFolder/$afterPartFolder")
                candidates.add(afterPartFolder)
            }
            val partLocalBookRel = cleanBookRel.substringBeforeLast('/')
            if (partLocalBookRel != cleanBookRel && partLocalBookRel.isNotBlank() &&
                cleanBookRel.substringAfterLast('/').uppercase(Locale.ROOT) in setOf("P01", "P02") &&
                normalizedUri.startsWith("$partLocalBookRel/", ignoreCase = true)
            ) {
                candidates.add(normalizedUri.removePrefix("$partLocalBookRel/"))
            }
            return candidates.toList()
        }

        // Legacy workspaces without workspaceUri may use explicit older path fields
        // and, as a final compatibility fallback, the historical folder convention.
        val explicitPaths = listOfNotNull(
            (lesson["sliceFile"] as? JsonObject)?.string("relativePath"),
            lesson.string("pdfPath"),
            lesson.string("path"),
            lesson.string("filePath"),
            lesson.string("lessonPdf")
        ).map {
            it.trim().replace('\\', '/').removePrefix("./").trimStart('/')
        }.filter { it.isNotBlank() && !it.split('/').contains("..") }

        for (raw in explicitPaths) {
            candidates.add(raw)
            if (cleanBookRel.isNotBlank()) {
                candidates.add("$cleanBookRel/$raw")
            }
            if (raw.startsWith("workspace/", ignoreCase = true)) {
                val noWs = raw.substringAfter('/')
                candidates.add(noWs)
                if (expectedGrade.isNotBlank() && noWs.startsWith("$expectedGrade/", ignoreCase = true)) {
                    val noGrade = noWs.substringAfter('/')
                    candidates.add(noGrade)
                    if (expectedSubject.isNotBlank() && noGrade.startsWith("$expectedSubject/", ignoreCase = true)) {
                        candidates.add(noGrade.substringAfter('/'))
                    }
                }
            }
            val markerIdx = raw.indexOf("/units/", ignoreCase = true)
            if (markerIdx > 0) {
                val partPrefixIdx = raw.lastIndexOf("/P0", markerIdx, ignoreCase = true)
                val tailFromPart = if (partPrefixIdx >= 0) raw.substring(partPrefixIdx + 1) else raw.substring(markerIdx + 1)
                candidates.add(tailFromPart)
                if (cleanBookRel.isNotBlank()) {
                    candidates.add("$cleanBookRel/$tailFromPart")
                }
            }
        }

        val lessonKey = sourceLessonKey(lesson)
        val lessonNumber = sourceLessonNumber(lesson)
        val lessonFile = lessonNumber?.let { "lesson_${it.toString().padStart(2, '0')}.pdf" }
        val folderKeys = linkedSetOf<String>().apply {
            val primary = sourceUnitFolderKey(unit)
            if (primary.isNotBlank()) add(primary)
            val explicitKey = sourceUnitKey(unit)
            if (explicitKey.isNotBlank()) {
                add(explicitKey)
                add(explicitKey.removePrefix("P01_").removePrefix("P02_").removePrefix("p01_").removePrefix("p02_"))
            }
        }.filter { it.isNotBlank() }

        val normalizedPart = lessonPart.uppercase(Locale.ROOT)
        fun addWithBookRel(relUnderBook: String) {
            val full = if (cleanBookRel.isBlank()) relUnderBook else "$cleanBookRel/$relUnderBook"
            candidates.add(full)
            candidates.add(relUnderBook)
            if (full.startsWith("workspace/", ignoreCase = true)) {
                candidates.add(full.removePrefix("workspace/"))
            }
        }

        for (uFolder in folderKeys) {
            if (normalizedPart == "P01" || normalizedPart == "P02") {
                if (lessonFile != null) addWithBookRel("$normalizedPart/units/$uFolder/$lessonFile")
                if (lessonKey.isNotBlank()) addWithBookRel("$normalizedPart/units/$uFolder/$lessonKey.pdf")
            }
            if (lessonFile != null) addWithBookRel("units/$uFolder/$lessonFile")
            if (lessonKey.isNotBlank()) addWithBookRel("units/$uFolder/$lessonKey.pdf")
        }
        return candidates.toList()
    }

    private fun validateIdentityField(source: JsonObject?, field: String, expected: String, sourceLabel: String, errors: MutableList<String>) {
        val metadata = source?.get("metadata") as? JsonObject
        val actual = firstNonBlank(source?.string(field), metadata?.string(field))
        if (actual.isNotBlank() && !actual.equals(expected, ignoreCase = true)) {
            errors.add("[$sourceLabel → $field] القيمة '$actual' لا تطابق مسار الكتاب المتوقع '$expected'.")
        }
    }

    fun sourceUnitFolderKey(unit: JsonObject): String {
        val explicitFolder = firstNonBlank(unit.string("folderKey"), unit.string("unitFolder"), unit.string("dirName"))
        if (explicitFolder.isNotBlank()) return explicitFolder
        val number = unit.string("unitNumber").toIntOrNull()
        if (number != null) return "unit_${number.toString().padStart(2, '0')}"
        return firstNonBlank(unit.string("unitKey"), unit.string("key"), unit.string("unitId"), unit.string("id"))
    }

    fun sourceUnitKey(unit: JsonObject): String {
        val explicit = firstNonBlank(unit.string("unitKey"), unit.string("key"), unit.string("unitId"), unit.string("id"))
        if (explicit.isNotBlank()) return explicit
        val folderKey = sourceUnitFolderKey(unit)
        if (folderKey.isBlank()) return ""
        val partKey = unit.string("partKey").uppercase(Locale.ROOT)
        return if (partKey == "P01" || partKey == "P02") "${partKey}_$folderKey" else folderKey
    }

    fun sourceLessonNumber(lesson: JsonObject): Int? =
        lesson.string("lessonNumber").toIntOrNull() ?: trailingNumber(sourceLessonKey(lesson))

    fun sourceLessonKey(lesson: JsonObject): String {
        val explicit = firstNonBlank(lesson.string("lessonKey"), lesson.string("key"), lesson.string("lessonId"), lesson.string("id"))
        if (explicit.isNotBlank()) return explicit
        val number = lesson.string("lessonNumber").toIntOrNull() ?: return ""
        return "lesson_${number.toString().padStart(2, '0')}"
    }

    private fun firstInt(source: JsonObject, vararg keys: String): Int? = keys.firstNotNullOfOrNull { source.string(it).toIntOrNull() }
    private fun trailingNumber(value: String): Int? = Regex("(\\d+)$").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    private fun JsonObject.string(key: String): String {
        val element: JsonElement = this[key] ?: return ""
        return (element as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull?.equals("true", ignoreCase = true) == true
    private fun normalizePath(value: String): String = value.replace('\\', '/').trim().trimStart('/').lowercase(Locale.ROOT)
    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
    private fun firstNonBlank(vararg values: String?): String = values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}
