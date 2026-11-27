package com.example

import com.example.data.workspace.BookIndexDraftEditor
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexSourceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIndexReviewEditorTest {

    private fun sampleDraft(
        totalPages: Int = 20,
        layout: IndexSourceLayout = IndexSourceLayout.MERGED_PARTS_FILE,
        partKey: String? = "BOTH"
    ): BookIndexIntakeDraft = BookIndexIntakeDraft(
        sourceFileName = "science_g06.pdf",
        sourceSha256 = "a".repeat(64),
        totalPdfPages = totalPages,
        bookTitle = "العلوم للصف السادس",
        gradeKey = "G06",
        gradeName = "الصف السادس الأساسي",
        subjectKey = "SCI",
        subjectName = "العلوم",
        componentKey = null,
        sourceLayout = layout,
        sourceLayoutEvidence = "غلاف مدمج يذكر الجزأين الأول والثاني",
        partKey = partKey,
        offset = 2,
        units = listOf(
            BookIndexUnitDraft(
                unitKey = "unit_01",
                title = "الوحدة الأولى",
                partKey = "P01",
                assignmentStatus = IndexAssignmentStatus.NEEDS_REVIEW,
                assignmentSource = IndexAssignmentSource.DEFAULT_PROPOSAL,
                assignmentEvidence = "",
                lessons = listOf(
                    BookIndexLessonDraft(
                        lessonKey = "lesson_01",
                        title = "الخلية النباتية",
                        printedPageStart = 1,
                        printedPageEnd = 4,
                        assignmentStatus = IndexAssignmentStatus.NEEDS_REVIEW,
                        assignmentSource = IndexAssignmentSource.DEFAULT_PROPOSAL
                    ),
                    BookIndexLessonDraft(
                        lessonKey = "lesson_02",
                        title = "البناء الضوئي",
                        printedPageStart = 5,
                        printedPageEnd = 8,
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.INDEX_EVIDENCE
                    )
                )
            ),
            BookIndexUnitDraft(
                unitKey = "unit_02",
                title = "الوحدة الثانية",
                partKey = "P02",
                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                assignmentSource = IndexAssignmentSource.INDEX_EVIDENCE,
                assignmentEvidence = "دليل الفهرس للجزء الثاني",
                lessons = listOf(
                    BookIndexLessonDraft(
                        lessonKey = "lesson_01",
                        title = "المادة وتغيراتها",
                        printedPageStart = 9,
                        printedPageEnd = 12,
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.INDEX_EVIDENCE
                    )
                )
            )
        ),
        reviewerConfirmed = true,
        reviewerId = "reviewer_01"
    )

    @Test
    fun updateMetadataAndOffset_resetsReviewerConfirmationAndShiftsPhysicalPages() {
        val initial = sampleDraft()
        val updatedMeta = BookIndexDraftEditor.updateBookMetadata(
            draft = initial,
            bookTitle = "الدراسات الاجتماعية - الجغرافيا",
            gradeKey = "G08",
            gradeName = "الصف الثامن الأساسي",
            subjectKey = "SOC",
            subjectName = "الدراسات الاجتماعية",
            componentKey = "GEO"
        )
        assertFalse(updatedMeta.reviewerConfirmed)
        assertEquals("الدراسات الاجتماعية - الجغرافيا", updatedMeta.bookTitle)
        assertEquals("G08", updatedMeta.gradeKey)
        assertEquals("SOC", updatedMeta.subjectKey)
        assertEquals("GEO", updatedMeta.componentKey)

        val shifted = BookIndexDraftEditor.adjustOffset(updatedMeta, delta = 3)
        assertEquals(5, shifted.offset)
        val firstLesson = shifted.units.first().lessons.first()
        assertEquals(6, shifted.physicalStartPage(firstLesson))
        assertEquals(9, shifted.physicalEndPage(firstLesson))
    }

    @Test
    fun sourceLayoutAndPartSwitching_synchronizesSinglePartUnits() {
        val merged = sampleDraft(layout = IndexSourceLayout.MERGED_PARTS_FILE, partKey = "BOTH")
        val singleP02 = BookIndexDraftEditor.setSinglePartKey(
            BookIndexDraftEditor.setSourceLayout(merged, IndexSourceLayout.SINGLE_PART_FILE),
            "P02"
        )
        assertEquals(IndexSourceLayout.SINGLE_PART_FILE, singleP02.sourceLayout)
        assertEquals("P02", singleP02.partKey)
        assertTrue(singleP02.units.all { it.partKey == "P02" })

        val backToMerged = BookIndexDraftEditor.setSourceLayout(singleP02, IndexSourceLayout.MERGED_PARTS_FILE)
        assertEquals(IndexSourceLayout.MERGED_PARTS_FILE, backToMerged.sourceLayout)
        assertEquals("BOTH", backToMerged.partKey)
    }

    @Test
    fun unitAndLessonReorderAddRemoveAndBatchConfirm_producesValidApprovableDraft() {
        val initial = sampleDraft()
        val (withNewUnit, addedUnit) = BookIndexDraftEditor.addUnit(initial)
        assertEquals(3, withNewUnit.units.size)
        assertEquals("unit_03", addedUnit.unitKey)

        val movedUnits = BookIndexDraftEditor.moveUnit(withNewUnit, fromIndex = 2, toIndex = 0)
        assertEquals("unit_03", movedUnits.units.first().unitKey)

        val addedLesson = BookIndexDraftEditor.addLesson(movedUnits, unitIndex = 1)
        assertEquals(3, addedLesson.units[1].lessons.size)

        val reorderedLessons = BookIndexDraftEditor.moveLesson(addedLesson, unitIndex = 1, fromIndex = 2, toIndex = 0)
        assertEquals("lesson_03", reorderedLessons.units[1].lessons.first().lessonKey)

        val removedLesson = BookIndexDraftEditor.removeLesson(reorderedLessons, unitIndex = 1, lessonIndex = 0)
        assertEquals(2, removedLesson.units[1].lessons.size)

        val removedUnit = BookIndexDraftEditor.removeUnit(removedLesson, unitIndex = 0)
        assertEquals(2, removedUnit.units.size)

        val confirmedAll = BookIndexDraftEditor.confirmAllUnitsAndLessons(removedUnit)
            .copy(reviewerConfirmed = true, reviewerId = "reviewer_01")
        assertEquals(emptyList<String>(), confirmedAll.validationErrors())
        assertTrue(confirmedAll.canApprove())
    }

    @Test
    fun summarize_calculatesCoveragePartsAndOverlaps() {
        val overlappingDraft = sampleDraft(totalPages = 16).let { draft ->
            BookIndexDraftEditor.updateLesson(draft, unitIndex = 1, lessonIndex = 0) { lesson ->
                // Physical range with offset=2 becomes 8..11 which overlaps unit_01 lesson_02 (7..10)
                lesson.copy(printedPageStart = 6, printedPageEnd = 9)
            }
        }
        val summary = BookIndexDraftEditor.summarize(overlappingDraft)
        assertEquals(2, summary.totalUnits)
        assertEquals(3, summary.totalLessons)
        assertEquals(1, summary.p01Units)
        assertEquals(1, summary.p02Units)
        assertEquals(0, summary.unassignedUnits)
        // Physical pages covered: (3..6), (7..10), (8..11) -> 3..11 = 9 pages out of 16
        assertEquals(9, summary.coveredPagesCount)
        assertEquals(9f / 16f, summary.coverageFraction, 0.001f)
        assertTrue(summary.overlappingRanges.isNotEmpty())
        assertNull(overlappingDraft.units[0].lessons[0].pdfPageStart)
    }
}
