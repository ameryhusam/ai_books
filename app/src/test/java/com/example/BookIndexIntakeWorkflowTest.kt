package com.example

import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexSourceLayout
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
}
