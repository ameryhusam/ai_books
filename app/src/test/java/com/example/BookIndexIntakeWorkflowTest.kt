package com.example

import com.example.data.workspace.BookIndexAiResponseParser
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexSourceLayout
import com.example.model.PdfBookInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIndexIntakeWorkflowTest {
    private fun validDraft() = BookIndexIntakeDraft(
        sourceFileName = "science.pdf",
        sourceSha256 = "a".repeat(64),
        totalPdfPages = 120,
        bookTitle = "Science",
        gradeKey = "G07",
        gradeName = "الصف السابع",
        subjectKey = "SCI",
        subjectName = "العلوم",
        sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
        sourceLayoutEvidence = "عنوان الكتاب يوضح أنه الجزء الأول",
        partKey = "P01",
        offset = 4,
        units = listOf(
            BookIndexUnitDraft(
                unitKey = "unit_01",
                title = "الوحدة الأولى",
                partKey = "P01",
                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                assignmentSource = IndexAssignmentSource.REVIEWER,
                assignmentEvidence = "أكد المستخدم أن الوحدة ضمن الجزء الأول",
                lessons = listOf(
                    BookIndexLessonDraft(
                        lessonKey = "lesson_01",
                        title = "الدرس الأول",
                        printedPageStart = 1,
                        printedPageEnd = 5
                    )
                )
            )
        ),
        reviewerConfirmed = true,
        reviewerId = "admin"
    )

    @Test
    fun printedPagesApplyOffsetExactlyOnce() {
        val draft = validDraft()
        val lesson = draft.units.single().lessons.single()
        assertEquals(5, draft.physicalStartPage(lesson))
        assertEquals(9, draft.physicalEndPage(lesson))
    }

    @Test
    fun explicitPdfPagesAreNotOffsetAgain() {
        val draft = validDraft().copy(offset = 4)
        val lesson = draft.units.single().lessons.single().copy(
            pdfPageStart = 12,
            pdfPageEnd = 16
        )
        assertEquals(12, draft.physicalStartPage(lesson))
        assertEquals(16, draft.physicalEndPage(lesson))
    }

    @Test
    fun approvalRequiresExplicitUserConfirmation() {
        val draft = validDraft().copy(reviewerConfirmed = false, reviewerId = null)
        assertFalse(draft.canApprove())
        assertTrue(draft.validationErrors().any { it.contains("لم يؤكد المستخدم") })
    }

    @Test
    fun validReviewedDraftCanBeApproved() {
        val draft = validDraft()
        assertTrue(draft.validationErrors().isEmpty())
        assertTrue(draft.canApprove())
    }
    @Test
    fun unconfirmedDefaultPartProposalCannotBeApproved() {
        val draft = validDraft().copy(
            units = validDraft().units.map { unit ->
                unit.copy(
                    assignmentSource = IndexAssignmentSource.DEFAULT_PROPOSAL,
                    assignmentEvidence = "Suggested by default; awaiting user confirmation"
                )
            }
        )
        assertFalse(draft.canApprove())
        assertTrue(draft.validationErrors().any { it.contains("غير موثوق") })
    }

    @Test
    fun aiDiscoveryContractHasReusablePromptAndVersionedSchema() {
        assertTrue(com.example.data.workspace.BookIndexPromptTemplate.SOURCE_PATH.endsWith("book_index_discovery.prompt.md"))
        assertTrue(com.example.data.workspace.BookIndexPromptTemplate.responseSchemaJson.contains("subjectKey"))
        assertTrue(com.example.data.workspace.BookIndexPromptTemplate.responseSchemaJson.contains("printedPageStart"))
        assertTrue(com.example.data.workspace.BookIndexPromptTemplate.responseSchemaJson.contains("assignmentEvidence"))
    }


    @Test
    fun aiResponseBecomesUnapprovedDraftAndKeepsPrintedAndPhysicalPagesSeparate() {
        val pdf = PdfBookInfo(
            fileName = "science.pdf",
            storedPdfPath = "/tmp/science.pdf",
            totalPages = 120,
            sha256 = "b".repeat(64)
        )
        val raw = """
            {
              "bookTitle": "كتاب العلوم",
              "gradeName": "الصف السابع",
              "gradeKey": "G07",
              "subjectName": "العلوم",
              "subjectKey": "SCI",
              "sourceLayout": "SINGLE_PART_FILE",
              "sourceLayoutEvidence": "عنوان الغلاف",
              "partKey": "P01",
              "offset": 1,
              "units": [{
                "unitKey": "unit_01",
                "title": "الوحدة الأولى",
                "partKey": "P01",
                "proposedPartKey": "",
                "assignmentStatus": "assigned",
                "assignmentSource": "INDEX_EVIDENCE",
                "assignmentEvidence": "عنوان الجزء في الفهرس",
                "lessons": [{
                  "lessonKey": "lesson_01",
                  "title": "الدرس الأول",
                  "printedPageStart": 3,
                  "printedPageEnd": 8,
                  "pdfPageStart": 0,
                  "pdfPageEnd": 0,
                  "assignmentStatus": "assigned",
                  "assignmentEvidence": "مدخل الفهرس"
                }]
              }],
              "discoveryNotes": ["يحتاج مراجعة بشرية"]
            }
        """.trimIndent()

        val draft = BookIndexAiResponseParser.parse(raw, pdf)

        assertFalse(draft.reviewerConfirmed)
        assertEquals("b".repeat(64), draft.sourceSha256)
        assertEquals(1, draft.offset)
        val lesson = draft.units.single().lessons.single()
        assertEquals(3, lesson.printedPageStart)
        assertEquals(null, lesson.pdfPageStart)
        assertEquals(4, draft.physicalStartPage(lesson))
        assertEquals(9, draft.physicalEndPage(lesson))
        assertEquals("يحتاج مراجعة بشرية", draft.reviewNotes)
    }

    @Test
    fun aiResponseDefaultsOffsetToOneWhenNotProvided() {
        val pdf = PdfBookInfo("science.pdf", "/tmp/science.pdf", 20, "c".repeat(64))
        val raw = """{"bookTitle":"Science","gradeName":"G04","gradeKey":"G04","subjectName":"Science","subjectKey":"SCI","sourceLayout":"UNKNOWN","sourceLayoutEvidence":"","partKey":"UNKNOWN","units":[],"discoveryNotes":[]}"""
        val draft = BookIndexAiResponseParser.parse(raw, pdf)
        assertEquals(1, draft.offset)
        assertFalse(draft.reviewerConfirmed)
    }

}
