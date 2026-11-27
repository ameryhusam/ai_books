package com.example.data.workspace

import com.example.model.PdfBookInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * App-owned intake contract for discovering and reviewing a school-book index.
 * Discovery results are proposals only; explicit reviewer confirmation is required
 * before the canonical index is approved or PDF slicing is allowed.
 */
enum class IndexDiscoveryMethod { BUILT_IN_TEMPLATE, AI_PROMPT, MANUAL }
enum class IndexSourceLayout { SINGLE_PART_FILE, MERGED_PARTS_FILE, UNKNOWN }
enum class IndexAssignmentStatus { ASSIGNED, NEEDS_REVIEW }
enum class IndexAssignmentSource { INDEX_EVIDENCE, REVIEWER, DEFAULT_PROPOSAL, LEGACY_UNVERIFIED }

data class BookIndexTemplate(
    val id: String,
    val title: String,
    val description: String,
    val discoveryMethod: IndexDiscoveryMethod
) {
    companion object {
        val BUILT_INS = listOf(
            BookIndexTemplate("standard_units_lessons", "قالب الوحدات والدروس القياسي",
                "استخراج أسماء الوحدات والدروس وربطها بالجزء والصفحات", IndexDiscoveryMethod.BUILT_IN_TEMPLATE),
            BookIndexTemplate("table_of_contents", "قالب تحليل فهرس المحتويات",
                "تحليل صفحات الفهرس والعناوين المطبوعة ومطابقتها مع صفحات PDF", IndexDiscoveryMethod.BUILT_IN_TEMPLATE),
            BookIndexTemplate("ai_book_index_discovery", "اكتشاف الفهرس بالذكاء الاصطناعي",
                "إرسال صفحات الفهرس والسياق إلى قالب AI وإرجاع مسودة قابلة للمراجعة", IndexDiscoveryMethod.AI_PROMPT)
        )
    }
}

data class BookIndexLessonDraft(
    val lessonKey: String,
    val title: String,
    val printedPageStart: Int? = null,
    val printedPageEnd: Int? = null,
    /** Physical PDF pages. When present, these values must not be offset again. */
    val pdfPageStart: Int? = null,
    val pdfPageEnd: Int? = null,
    val assignmentStatus: IndexAssignmentStatus = IndexAssignmentStatus.ASSIGNED,
    val assignmentSource: IndexAssignmentSource = IndexAssignmentSource.INDEX_EVIDENCE,
    val assignmentEvidence: String = ""
)

data class BookIndexUnitDraft(
    val unitKey: String,
    val title: String,
    val partKey: String? = null,
    val proposedPartKey: String? = null,
    val assignmentStatus: IndexAssignmentStatus = IndexAssignmentStatus.NEEDS_REVIEW,
    val assignmentSource: IndexAssignmentSource = IndexAssignmentSource.LEGACY_UNVERIFIED,
    val assignmentEvidence: String = "",
    val lessons: List<BookIndexLessonDraft> = emptyList()
)

data class BookIndexIntakeDraft(
    val sourceFileName: String,
    val sourceSha256: String,
    val totalPdfPages: Int,
    val bookTitle: String,
    val gradeKey: String,
    val gradeName: String = "",
    val subjectKey: String,
    val subjectName: String,
    val componentKey: String? = null,
    val sourceLayout: IndexSourceLayout = IndexSourceLayout.UNKNOWN,
    val sourceLayoutEvidence: String = "",
    val partKey: String? = null,
    /** Physical PDF page = printed page + offset. Applied only if physical page is absent. */
    val offset: Int = 1,
    val selectedTemplateId: String = BookIndexTemplate.BUILT_INS.first().id,
    val discoveryMethod: IndexDiscoveryMethod = IndexDiscoveryMethod.BUILT_IN_TEMPLATE,
    val discoveryConfidence: Double? = null,
    val units: List<BookIndexUnitDraft> = emptyList(),
    val reviewerConfirmed: Boolean = false,
    val reviewerId: String? = null,
    val reviewNotes: String = ""
) {
    fun physicalStartPage(lesson: BookIndexLessonDraft): Int? =
        lesson.pdfPageStart ?: lesson.printedPageStart?.plus(offset)

    fun physicalEndPage(lesson: BookIndexLessonDraft): Int? =
        lesson.pdfPageEnd ?: lesson.printedPageEnd?.plus(offset)

    fun validationErrors(): List<String> = buildList {
        if (sourceFileName.isBlank()) add("اسم ملف المصدر مطلوب")
        if (!sourceSha256.matches(Regex("(?i)^[a-f0-9]{64}$"))) add("بصمة المصدر SHA-256 غير صالحة")
        if (totalPdfPages <= 0) add("عدد صفحات PDF غير صالح")
        if (discoveryConfidence != null && discoveryConfidence !in 0.0..1.0) {
            add("درجة ثقة الاكتشاف يجب أن تكون بين 0 و1")
        }
        if (bookTitle.isBlank()) add("عنوان الكتاب مطلوب")
        if (gradeKey.isBlank()) add("مفتاح الصف مطلوب")
        if (subjectKey.isBlank()) add("مفتاح المادة مطلوب")
        if (subjectName.isBlank()) add("اسم المادة مطلوب")
        if (sourceLayout == IndexSourceLayout.UNKNOWN) add("يجب تحديد بنية المصدر: جزء مستقل أم ملف مدمج")
        if (sourceLayoutEvidence.isBlank()) add("دليل تحديد بنية ملف المصدر مطلوب")
        if (units.isEmpty()) add("يجب اكتشاف وحدة واحدة على الأقل أو إضافتها يدوياً")

        if (sourceLayout == IndexSourceLayout.SINGLE_PART_FILE && partKey !in setOf("P01", "P02")) {
            add("يجب تحديد الجزء للملف المستقل (P01 أو P02)")
        }
        if (sourceLayout == IndexSourceLayout.MERGED_PARTS_FILE && partKey != "BOTH") {
            add("مفتاح مصدر الملف المدمج يجب أن يكون BOTH")
        }
        if (units.map { it.unitKey }.distinct().size != units.size) add("توجد مفاتيح وحدات مكررة")

        units.forEach { unit ->
            if (unit.unitKey.isBlank()) add("مفتاح الوحدة لا يمكن أن يكون فارغاً")
            if (unit.title.isBlank()) add("اسم الوحدة مطلوب (${unit.unitKey})")
            if (unit.assignmentStatus != IndexAssignmentStatus.ASSIGNED) {
                add("تعيين الجزء للوحدة ${unit.title} لم يؤكد بعد")
            }
            if (unit.partKey !in setOf("P01", "P02")) {
                add("الجزء المعتمد للوحدة ${unit.title} يجب أن يكون P01 أو P02")
            }
            if (sourceLayout == IndexSourceLayout.SINGLE_PART_FILE && unit.partKey != partKey) {
                add("الجزء المعتمد للوحدة ${unit.title} يجب أن يطابق جزء ملف المصدر")
            }
            if (unit.assignmentEvidence.isBlank()) add("دليل تعيين الجزء مطلوب للوحدة ${unit.title}")
            if (unit.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
                unit.assignmentSource !in setOf(IndexAssignmentSource.INDEX_EVIDENCE, IndexAssignmentSource.REVIEWER)
            ) {
                add("مصدر تعيين الجزء للوحدة ${unit.title} غير موثوق للاعتماد")
            }
            if (unit.lessons.isEmpty()) add("الوحدة ${unit.title} لا تحتوي دروساً")
            if (unit.lessons.map { it.lessonKey }.distinct().size != unit.lessons.size) {
                add("توجد مفاتيح دروس مكررة في الوحدة ${unit.title}")
            }
            unit.lessons.forEach { lesson ->
                if (lesson.lessonKey.isBlank()) add("مفتاح الدرس مطلوب في الوحدة ${unit.title}")
                if (lesson.title.isBlank()) add("اسم الدرس مطلوب في الوحدة ${unit.title}")
                val start = physicalStartPage(lesson)
                val end = physicalEndPage(lesson)
                if (start == null || end == null || start < 1 || end < start || end > totalPdfPages) {
                    add("نطاق صفحات PDF غير صالح للدرس ${lesson.title} في الوحدة ${unit.title}")
                }
                if (lesson.assignmentStatus != IndexAssignmentStatus.ASSIGNED) {
                    add("إسناد صفحات الدرس ${lesson.title} يحتاج إلى مراجعة")
                }
            }
        }
        if (!reviewerConfirmed) add("لم يؤكد المستخدم صحة بيانات الفهرس بعد")
        if (reviewerConfirmed && reviewerId.isNullOrBlank()) add("يجب تسجيل هوية المراجع الذي أكد الفهرس")
    }

    fun canApprove(): Boolean = validationErrors().isEmpty()

    fun requireApproved(): BookIndexIntakeDraft {
        val errors = validationErrors()
        require(errors.isEmpty()) { errors.joinToString(separator = "؛ ") }
        return this
    }
}


/** Shared AI extraction contract. Schema is projected directly from the Room database entities. */
object BookIndexPromptTemplate {
    const val SOURCE_PATH = "prompts/book_indexing/book_index_discovery.prompt.md"

    val responseSchemaJson: String
        get() = RoomBookIndexProjection.buildDiscoveryResponseSchemaJson()
}

/**
 * Converts provider output into the app-owned review draft. AI output is never approved here.
 * Printed page numbers remain separate from physical PDF page numbers.
 */
object BookIndexAiResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun parse(rawResponse: String, pdfInfo: PdfBookInfo): BookIndexIntakeDraft {
        val firstObject = rawResponse.indexOf('{')
        val lastObject = rawResponse.lastIndexOf('}')
        require(firstObject >= 0 && lastObject > firstObject) {
            "لم يتم العثور على كائن JSON في استجابة اكتشاف الفهرس."
        }
        val root = json.parseToJsonElement(rawResponse.substring(firstObject, lastObject + 1)).jsonObject

        fun JsonObject.text(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        fun JsonObject.number(key: String): Int? =
            (this[key] as? JsonPrimitive)?.intOrNull

        fun JsonObject.array(key: String): JsonArray =
            (this[key] as? JsonArray) ?: JsonArray(emptyList())

        fun part(value: String): String? =
            value.trim().uppercase().takeIf { it == "P01" || it == "P02" }

        fun status(value: String): IndexAssignmentStatus =
            if (value.equals("assigned", true)) IndexAssignmentStatus.ASSIGNED
            else IndexAssignmentStatus.NEEDS_REVIEW

        fun source(value: String, assignmentStatus: IndexAssignmentStatus): IndexAssignmentSource =
            when (value.trim().uppercase()) {
                "INDEX_EVIDENCE" -> IndexAssignmentSource.INDEX_EVIDENCE
                "REVIEWER" -> IndexAssignmentSource.REVIEWER
                "DEFAULT_PROPOSAL" -> IndexAssignmentSource.DEFAULT_PROPOSAL
                else -> if (assignmentStatus == IndexAssignmentStatus.ASSIGNED) {
                    IndexAssignmentSource.INDEX_EVIDENCE
                } else IndexAssignmentSource.LEGACY_UNVERIFIED
            }

        val layout = when (root.text("sourceLayout").uppercase()) {
            "SINGLE_PART_FILE" -> IndexSourceLayout.SINGLE_PART_FILE
            "MERGED_PARTS_FILE" -> IndexSourceLayout.MERGED_PARTS_FILE
            else -> IndexSourceLayout.UNKNOWN
        }
        val sourcePart = root.text("partKey").uppercase().let {
            when {
                layout == IndexSourceLayout.MERGED_PARTS_FILE && it == "BOTH" -> "BOTH"
                it == "P01" || it == "P02" -> it
                else -> null
            }
        }
        val units = root.array("units").mapIndexed { unitIndex, element ->
            val unit = element as? JsonObject ?: JsonObject(emptyMap())
            val unitStatus = status(unit.text("assignmentStatus"))
            val unitPart = part(unit.text("partKey"))
            val proposedPart = part(unit.text("proposedPartKey"))
            val lessons = unit.array("lessons").mapIndexed { lessonIndex, lessonElement ->
                val lesson = lessonElement as? JsonObject ?: JsonObject(emptyMap())
                BookIndexLessonDraft(
                    lessonKey = lesson.text("lessonKey").ifBlank {
                        "lesson_" + (lessonIndex + 1).toString().padStart(2, '0')
                    },
                    title = lesson.text("title"),
                    printedPageStart = lesson.number("printedPageStart")?.takeIf { it > 0 },
                    printedPageEnd = lesson.number("printedPageEnd")?.takeIf { it > 0 },
                    pdfPageStart = lesson.number("pdfPageStart")?.takeIf { it > 0 },
                    pdfPageEnd = lesson.number("pdfPageEnd")?.takeIf { it > 0 },
                    assignmentStatus = status(lesson.text("assignmentStatus")),
                    assignmentSource = IndexAssignmentSource.INDEX_EVIDENCE,
                    assignmentEvidence = lesson.text("assignmentEvidence")
                )
            }
            BookIndexUnitDraft(
                unitKey = unit.text("unitKey").ifBlank {
                    "unit_" + (unitIndex + 1).toString().padStart(2, '0')
                },
                title = unit.text("title"),
                partKey = unitPart,
                proposedPartKey = proposedPart,
                assignmentStatus = unitStatus,
                assignmentSource = source(unit.text("assignmentSource"), unitStatus),
                assignmentEvidence = unit.text("assignmentEvidence"),
                lessons = lessons
            )
        }

        return BookIndexIntakeDraft(
            sourceFileName = pdfInfo.fileName,
            sourceSha256 = pdfInfo.sha256,
            totalPdfPages = pdfInfo.totalPages,
            bookTitle = root.text("bookTitle"),
            gradeKey = root.text("gradeKey"),
            gradeName = root.text("gradeName"),
            subjectKey = root.text("subjectKey"),
            subjectName = root.text("subjectName"),
            componentKey = root.text("componentKey").ifBlank { null },
            sourceLayout = layout,
            sourceLayoutEvidence = root.text("sourceLayoutEvidence"),
            partKey = sourcePart,
            offset = root.number("offset") ?: 1,
            selectedTemplateId = "ai_book_index_discovery",
            discoveryMethod = IndexDiscoveryMethod.AI_PROMPT,
            discoveryConfidence = (root["discoveryConfidence"] as? JsonPrimitive)?.doubleOrNull,
            units = units,
            reviewerConfirmed = false,
            reviewerId = null,
            reviewNotes = root.array("discoveryNotes").mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.joinToString("\n")
        )
    }
}
