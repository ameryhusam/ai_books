package com.example.data.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import java.util.Locale

data class WorkspaceContractValidation(
    val canonicalContract: Boolean,
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Validates indexer2 source identity, approval, index fingerprint and lesson file references. */
object WorkspaceContractValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(
        indexRaw: String,
        manifestRaw: String?,
        expectedGrade: String,
        expectedSubject: String,
        bookRelativePath: String,
        availableRelativePaths: Set<String>
    ): WorkspaceContractValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val index = runCatching { json.parseToJsonElement(indexRaw).jsonObject }.getOrElse {
            return WorkspaceContractValidation(false, listOf("index.json غير صالح: ${it.message}"), emptyList())
        }
        val manifest = manifestRaw?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
                errors.add("book_manifest.json غير صالح: ${it.message}")
                null
            }
        }
        if (manifest == null && errors.isEmpty()) warnings.add("ملف book_manifest.json غير موجود؛ تمت قراءة الكتاب بوضع التوافق القديم.")

        val manifestVersion = manifest?.string("manifestVersion").orEmpty()
        val manifestLayout = manifest?.string("sourceLayout").orEmpty()
        val indexLayout = index.string("sourceLayout")
        val sourceLayout = firstNonBlank(manifestLayout, indexLayout)
        val sourceReviewStatus = firstNonBlank(manifest?.string("sourceReviewStatus"), index.string("sourceReviewStatus"))
        val canonical = manifestVersion.startsWith("edu.book_manifest.") || sourceLayout.isNotBlank()

        validateIdentityField(manifest, "gradeKey", expectedGrade, "manifest", errors)
        validateIdentityField(index, "gradeKey", expectedGrade, "index", errors)
        validateIdentityField(manifest, "subjectKey", expectedSubject, "manifest", errors)
        validateIdentityField(index, "subjectKey", expectedSubject, "index", errors)

        if (canonical) {
            if (manifestLayout.isNotBlank() && indexLayout.isNotBlank() && manifestLayout != indexLayout) {
                errors.add("sourceLayout غير متطابق بين index.json وbook_manifest.json.")
            }
            if (sourceLayout !in setOf("SINGLE_PART_FILE", "MERGED_PARTS_FILE")) errors.add("sourceLayout غير معروف أو غير محسوم.")
            if (sourceReviewStatus != "VERIFIED") errors.add("المصدر غير معتمد للمراجعة النهائية: ${sourceReviewStatus.ifBlank { "sourceReviewStatus مفقود" }}")
            if (index.string("approvalStatus") != "APPROVED") errors.add("الفهرس لم يحصل على حالة APPROVED.")
            if (index.boolean("requiresReview") || index.boolean("requires_review")) errors.add("الفهرس يحمل علامة requiresReview ولا يمكن استيراده.")
            if (!manifest?.string("sourceSha256").orEmpty().matches(Regex("^[a-fA-F0-9]{64}$"))) {
                errors.add("بصمة sourceSha256 مفقودة أو غير صالحة في manifest.")
            }
            val expectedIndexSha = manifest?.string("indexSha256").orEmpty()
            if (!expectedIndexSha.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                errors.add("بصمة indexSha256 مفقودة أو غير صالحة في manifest.")
            } else if (!sha256(indexRaw).equals(expectedIndexSha, ignoreCase = true)) {
                errors.add("بصمة index.json لا تطابق indexSha256 في manifest.")
            }
        } else {
            warnings.add("المجلد يستخدم صيغة توافق قديمة؛ لم تتوفر جميع حقول هوية المصدر الحديثة.")
        }

        val units = index["units"] as? JsonArray
        if (units.isNullOrEmpty()) {
            errors.add("الفهرس لا يحتوي مصفوفة units صالحة أو لا يحتوي وحدات.")
            return WorkspaceContractValidation(canonical, errors.distinct(), warnings.distinct())
        }
        val available = availableRelativePaths.map { normalizePath(it) }.toSet()
        val seenUnitKeys = HashSet<String>()
        units.forEachIndexed { unitIndex, unitElement ->
            val unit = unitElement as? JsonObject
            if (unit == null) {
                errors.add("عنصر الوحدة رقم ${unitIndex + 1} ليس كائن JSON.")
                return@forEachIndexed
            }
            val unitKey = sourceUnitKey(unit)
            val unitTitle = firstNonBlank(unit.string("title"), unit.string("unitTitle"), unit.string("name"))
            if (unitKey.isBlank()) errors.add("الوحدة رقم ${unitIndex + 1} لا تحتوي unitKey أو unitNumber ثابتاً.")
            if (unitTitle.isBlank()) errors.add("الوحدة رقم ${unitIndex + 1} لا تحتوي عنواناً.")
            if (unitKey.isNotBlank() && !seenUnitKeys.add(unitKey.lowercase(Locale.ROOT))) errors.add("مفتاح الوحدة مكرر: $unitKey")

            val partKey = firstNonBlank(unit.string("partKey"), unit.string("part")).uppercase(Locale.ROOT)
            val assignmentStatus = unit.string("assignmentStatus")
            if (canonical && partKey !in setOf("P01", "P02")) errors.add("الوحدة ${unitTitle.ifBlank { unitKey }} غير مسندة إلى P01 أو P02.")
            else if (!canonical && partKey.isBlank()) warnings.add("إسناد الجزء غير موجود للوحدة ${unitTitle.ifBlank { unitKey }}.")
            if (canonical && assignmentStatus.isBlank()) warnings.add("حقل assignmentStatus غير موجود للوحدة ${unitTitle.ifBlank { unitKey }}؛ تم الاعتماد على partKey مع حالة مراجعة المصدر.")
            if (canonical && assignmentStatus.isNotBlank() && assignmentStatus != "assigned") errors.add("الوحدة ${unitTitle.ifBlank { unitKey }} تحتاج مراجعة إسناد الجزء.")
            if (unit.boolean("provisionalAssignment")) errors.add("الوحدة ${unitTitle.ifBlank { unitKey }} تحمل provisionalAssignment.")

            val lessons = unit["lessons"] as? JsonArray
            if (lessons.isNullOrEmpty()) {
                errors.add("الوحدة ${unitTitle.ifBlank { unitKey }} لا تحتوي دروساً.")
                return@forEachIndexed
            }
            val seenLessonKeys = HashSet<String>()
            lessons.forEachIndexed lessonLoop@{ lessonIndex, lessonElement ->
                val lesson = lessonElement as? JsonObject
                if (lesson == null) {
                    errors.add("عنصر درس غير صالح في الوحدة ${unitTitle.ifBlank { unitKey }}.")
                    return@lessonLoop
                }
                val lessonKey = sourceLessonKey(lesson)
                val lessonTitle = firstNonBlank(lesson.string("title"), lesson.string("lessonTitle"), lesson.string("name"))
                if (lessonKey.isBlank()) errors.add("درس رقم ${lessonIndex + 1} في ${unitTitle.ifBlank { unitKey }} لا يحتوي lessonKey أو lessonNumber.")
                if (lessonTitle.isBlank()) errors.add("درس ${lessonKey.ifBlank { (lessonIndex + 1).toString() }} لا يحتوي عنواناً.")
                if (lessonKey.isNotBlank() && !seenLessonKeys.add(lessonKey.lowercase(Locale.ROOT))) errors.add("مفتاح الدرس مكرر داخل الوحدة $unitKey: $lessonKey")

                val startPage = firstInt(lesson, "pdfPageStart", "pdfPage", "pageStart", "startPage")
                val endPage = firstInt(lesson, "pdfPageEnd", "pdfPage", "pageEnd", "endPage")
                if (canonical && (startPage == null || endPage == null)) errors.add("نطاق صفحات PDF غير مكتمل للدرس ${lessonTitle.ifBlank { lessonKey }}.")
                else if (startPage != null && endPage != null && (startPage < 1 || endPage < startPage)) errors.add("نطاق صفحات PDF غير صالح للدرس ${lessonTitle.ifBlank { lessonKey }}.")

                val lessonPart = firstNonBlank(lesson.string("partKey"), lesson.string("part"), partKey).uppercase(Locale.ROOT)
                val lessonNumber = sourceLessonNumber(lesson)
                val linkedPath = (lesson["sliceFile"] as? JsonObject)?.string("workspaceUri").orEmpty()
                val candidates = linkedSetOf<String>()
                if (linkedPath.isNotBlank()) {
                    candidates.add(linkedPath)
                    if (linkedPath.startsWith("workspace/", ignoreCase = true)) candidates.add(linkedPath.removePrefix("workspace/"))
                }
                val unitFolderKey = sourceUnitFolderKey(unit)
                if (lessonPart in setOf("P01", "P02") && unitFolderKey.isNotBlank() && lessonNumber != null) {
                    val lessonFile = "lesson_${lessonNumber.toString().padStart(2, '0')}.pdf"
                    candidates.add("$bookRelativePath/$lessonPart/units/$unitFolderKey/$lessonFile")
                    candidates.add("$bookRelativePath/$lessonPart/units/$unitFolderKey/$lessonKey.pdf")
                    candidates.add("$bookRelativePath/$lessonPart/units/$unitFolderKey/$lessonFile".removePrefix("workspace/"))
                }
                val pdfExists = candidates.any { candidate ->
                    val normalized = normalizePath(candidate)
                    normalized in available || available.any { it.endsWith("/$normalized") }
                }
                if (!pdfExists) {
                    val message = "ملف PDF غير موجود للدرس ${lessonTitle.ifBlank { lessonKey }}."
                    if (canonical) errors.add(message) else warnings.add(message)
                }
            }
        }
        return WorkspaceContractValidation(canonical, errors.distinct(), warnings.distinct())
    }

    private fun validateIdentityField(source: JsonObject?, field: String, expected: String, sourceLabel: String, errors: MutableList<String>) {
        val actual = source?.string(field).orEmpty()
        if (actual.isNotBlank() && !actual.equals(expected, ignoreCase = true)) errors.add("$sourceLabel.$field لا يطابق مسار الكتاب: $actual مقابل $expected.")
    }

    fun sourceUnitFolderKey(unit: JsonObject): String {
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
