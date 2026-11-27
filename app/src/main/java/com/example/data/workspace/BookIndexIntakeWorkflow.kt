package com.example.data.workspace

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
    val partKey: String? = null,
    /** Physical PDF page = printed page + offset. Applied only if physical page is absent. */
    val offset: Int = 0,
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
        if (bookTitle.isBlank()) add("عنوان الكتاب مطلوب")
        if (gradeKey.isBlank()) add("مفتاح الصف مطلوب")
        if (subjectKey.isBlank()) add("مفتاح المادة مطلوب")
        if (subjectName.isBlank()) add("اسم المادة مطلوب")
        if (sourceLayout == IndexSourceLayout.UNKNOWN) add("يجب تحديد بنية المصدر: جزء مستقل أم ملف مدمج")
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
            if (unit.title.isBlank()) add("اسم الوحدة مطلوب (\${unit.unitKey})")
            if (unit.assignmentStatus != IndexAssignmentStatus.ASSIGNED) {
                add("تعيين الجزء للوحدة \${unit.title} لم يؤكد بعد")
            }
            if (unit.partKey !in setOf("P01", "P02")) {
                add("الجزء المعتمد للوحدة \${unit.title} يجب أن يكون P01 أو P02")
            }
            if (unit.assignmentEvidence.isBlank()) add("دليل تعيين الجزء مطلوب للوحدة \${unit.title}")
            if (unit.lessons.isEmpty()) add("الوحدة \${unit.title} لا تحتوي دروساً")
            if (unit.lessons.map { it.lessonKey }.distinct().size != unit.lessons.size) {
                add("توجد مفاتيح دروس مكررة في الوحدة \${unit.title}")
            }
            unit.lessons.forEach { lesson ->
                if (lesson.lessonKey.isBlank()) add("مفتاح الدرس مطلوب في الوحدة \${unit.title}")
                if (lesson.title.isBlank()) add("اسم الدرس مطلوب في الوحدة \${unit.title}")
                val start = physicalStartPage(lesson)
                val end = physicalEndPage(lesson)
                if (start == null || end == null || start < 1 || end < start || end > totalPdfPages) {
                    add("نطاق صفحات PDF غير صالح للدرس \${lesson.title} في الوحدة \${unit.title}")
                }
                if (lesson.assignmentStatus != IndexAssignmentStatus.ASSIGNED) {
                    add("إسناد صفحات الدرس \${lesson.title} يحتاج إلى مراجعة")
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


/** Shared AI extraction contract. Both prompt and deterministic parsers must populate BookIndexIntakeDraft. */
object BookIndexPromptTemplate {
    const val SOURCE_PATH = "prompts/book_indexing/book_index_discovery.prompt.md"

    val responseSchemaJson: String = """
        {
          "bookTitle": "اسم الكتاب",
          "gradeName": "اسم الصف",
          "gradeKey": "G07",
          "subjectName": "اسم المادة",
          "subjectKey": "SCI",
          "componentKey": null,
          "sourceLayout": "SINGLE_PART_FILE | MERGED_PARTS_FILE | UNKNOWN",
          "partKey": "P01 | P02 | BOTH | null",
          "offset": 4,
          "units": [
            {
              "unitKey": "unit_01",
              "title": "اسم الوحدة",
              "partKey": "P01 | P02 | null",
              "proposedPartKey": null,
              "assignmentStatus": "ASSIGNED | NEEDS_REVIEW",
              "assignmentSource": "INDEX_EVIDENCE | DEFAULT_PROPOSAL | LEGACY_UNVERIFIED",
              "assignmentEvidence": "الدليل النصي",
              "lessons": [
                {
                  "lessonKey": "lesson_01",
                  "title": "اسم الدرس",
                  "printedPageStart": 1,
                  "printedPageEnd": 6,
                  "pdfPageStart": 5,
                  "pdfPageEnd": 10,
                  "assignmentStatus": "ASSIGNED | NEEDS_REVIEW",
                  "assignmentEvidence": "الدليل النصي"
                }
              ]
            }
          ],
          "discoveryNotes": []
        }
    """.trimIndent()
}
