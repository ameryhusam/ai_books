package com.example.data.workspace

import kotlin.math.max
import kotlin.math.min

/**
 * Computed live diagnostics for the Review and Edit screen so reviewers can verify
 * PDF page coverage, P01/P02 distribution, and overlapping ranges before final splitting.
 */
data class BookIndexCoverageSummary(
    val totalUnits: Int,
    val totalLessons: Int,
    val assignedUnits: Int,
    val assignedLessons: Int,
    val p01Units: Int,
    val p02Units: Int,
    val unassignedUnits: Int,
    val coveredPagesCount: Int,
    val totalPdfPages: Int,
    val coverageFraction: Float,
    val overlappingRanges: List<String>,
    val invalidPageLessonsCount: Int
)

/**
 * Pure, immutable state transformations and diagnostics for [BookIndexIntakeDraft]
 * used by the Review and Edit UI components and unit tests.
 */
object BookIndexDraftEditor {

    fun summarize(draft: BookIndexIntakeDraft): BookIndexCoverageSummary {
        val totalPages = draft.totalPdfPages.coerceAtLeast(1)
        val coveredPages = BooleanArray(totalPages + 1)
        val occupiedRanges = mutableListOf<Triple<Int, Int, String>>()
        val overlaps = mutableListOf<String>()
        var invalidPages = 0
        var assignedLessons = 0

        draft.units.forEach { unit ->
            unit.lessons.forEach { lesson ->
                if (lesson.assignmentStatus == IndexAssignmentStatus.ASSIGNED) {
                    assignedLessons++
                }
                val start = draft.physicalStartPage(lesson)
                val end = draft.physicalEndPage(lesson)
                if (start == null || end == null || start < 1 || end < start || end > draft.totalPdfPages) {
                    invalidPages++
                } else {
                    for (page in start..end) {
                        coveredPages[page] = true
                    }
                    val label = "${unit.title.ifBlank { unit.unitKey }} › ${lesson.title.ifBlank { lesson.lessonKey }} ($start–$end)"
                    occupiedRanges.forEach { (prevStart, prevEnd, prevLabel) ->
                        if (max(start, prevStart) <= min(end, prevEnd)) {
                            overlaps += "تداخل صفحات بين $prevLabel و $label"
                        }
                    }
                    occupiedRanges += Triple(start, end, label)
                }
            }
        }

        val coveredCount = (1..totalPages).count { coveredPages[it] }
        val p01Units = draft.units.count { it.partKey.equals("P01", ignoreCase = true) }
        val p02Units = draft.units.count { it.partKey.equals("P02", ignoreCase = true) }
        val unassignedUnits = draft.units.count { it.partKey !in setOf("P01", "P02") }
        val assignedUnits = draft.units.count {
            it.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
                it.partKey in setOf("P01", "P02") &&
                it.assignmentEvidence.isNotBlank() &&
                it.assignmentSource in setOf(IndexAssignmentSource.INDEX_EVIDENCE, IndexAssignmentSource.REVIEWER)
        }

        return BookIndexCoverageSummary(
            totalUnits = draft.units.size,
            totalLessons = draft.units.sumOf { it.lessons.size },
            assignedUnits = assignedUnits,
            assignedLessons = assignedLessons,
            p01Units = p01Units,
            p02Units = p02Units,
            unassignedUnits = unassignedUnits,
            coveredPagesCount = coveredCount,
            totalPdfPages = draft.totalPdfPages,
            coverageFraction = (coveredCount.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f),
            overlappingRanges = overlaps.distinct().take(4),
            invalidPageLessonsCount = invalidPages
        )
    }

    fun updateBookMetadata(
        draft: BookIndexIntakeDraft,
        bookTitle: String = draft.bookTitle,
        gradeKey: String = draft.gradeKey,
        gradeName: String = draft.gradeName,
        subjectKey: String = draft.subjectKey,
        subjectName: String = draft.subjectName,
        componentKey: String? = draft.componentKey
    ): BookIndexIntakeDraft = draft.copy(
        bookTitle = bookTitle,
        gradeKey = gradeKey,
        gradeName = gradeName,
        subjectKey = subjectKey,
        subjectName = subjectName,
        componentKey = componentKey?.trim()?.takeIf { it.isNotEmpty() },
        reviewerConfirmed = false
    )

    fun setSourceLayout(
        draft: BookIndexIntakeDraft,
        layout: IndexSourceLayout
    ): BookIndexIntakeDraft = when (layout) {
        IndexSourceLayout.SINGLE_PART_FILE -> {
            val part = draft.partKey?.takeIf { it in setOf("P01", "P02") } ?: "P01"
            draft.copy(
                sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
                partKey = part,
                units = draft.units.map { unit -> unit.copy(partKey = part) },
                reviewerConfirmed = false
            )
        }
        IndexSourceLayout.MERGED_PARTS_FILE -> draft.copy(
            sourceLayout = IndexSourceLayout.MERGED_PARTS_FILE,
            partKey = "BOTH",
            reviewerConfirmed = false
        )
        IndexSourceLayout.UNKNOWN -> draft.copy(
            sourceLayout = IndexSourceLayout.UNKNOWN,
            partKey = null,
            reviewerConfirmed = false
        )
    }

    fun setSinglePartKey(
        draft: BookIndexIntakeDraft,
        partKey: String
    ): BookIndexIntakeDraft {
        val cleanPart = partKey.trim().uppercase().takeIf { it in setOf("P01", "P02") } ?: return draft
        return draft.copy(
            sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
            partKey = cleanPart,
            units = draft.units.map { it.copy(partKey = cleanPart) },
            reviewerConfirmed = false
        )
    }

    fun setOffset(draft: BookIndexIntakeDraft, newOffset: Int): BookIndexIntakeDraft =
        draft.copy(offset = newOffset, reviewerConfirmed = false)

    fun adjustOffset(draft: BookIndexIntakeDraft, delta: Int): BookIndexIntakeDraft =
        setOffset(draft, draft.offset + delta)

    fun addUnit(draft: BookIndexIntakeDraft): Pair<BookIndexIntakeDraft, BookIndexUnitDraft> {
        val nextNumber = draft.units.size + 1
        var candidateKey = "unit_${nextNumber.toString().padStart(2, '0')}"
        val existingKeys = draft.units.map { it.unitKey }.toSet()
        var suffix = nextNumber
        while (candidateKey in existingKeys) {
            suffix++
            candidateKey = "unit_${suffix.toString().padStart(2, '0')}"
        }
        val defaultPart = when (draft.sourceLayout) {
            IndexSourceLayout.SINGLE_PART_FILE -> draft.partKey?.takeIf { it in setOf("P01", "P02") } ?: "P01"
            else -> "P01"
        }
        val lastCoveredPage = draft.units
            .flatMap { it.lessons }
            .mapNotNull { draft.physicalEndPage(it) }
            .maxOrNull() ?: 0
        val nextStart = (lastCoveredPage + 1).coerceIn(1, draft.totalPdfPages.coerceAtLeast(1))
        val newUnit = BookIndexUnitDraft(
            unitKey = candidateKey,
            title = "الوحدة $nextNumber",
            partKey = defaultPart,
            proposedPartKey = defaultPart,
            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
            assignmentSource = IndexAssignmentSource.REVIEWER,
            assignmentEvidence = "أضيفت يدوياً بواسطة المراجع",
            lessons = listOf(
                BookIndexLessonDraft(
                    lessonKey = "lesson_01",
                    title = "الدرس الأول",
                    pdfPageStart = nextStart,
                    pdfPageEnd = nextStart,
                    assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                    assignmentSource = IndexAssignmentSource.REVIEWER,
                    assignmentEvidence = "إضافة يدوية"
                )
            )
        )
        return draft.copy(units = draft.units + newUnit, reviewerConfirmed = false) to newUnit
    }

    fun removeUnit(draft: BookIndexIntakeDraft, unitIndex: Int): BookIndexIntakeDraft {
        if (unitIndex !in draft.units.indices) return draft
        val mutable = draft.units.toMutableList()
        mutable.removeAt(unitIndex)
        return draft.copy(units = mutable, reviewerConfirmed = false)
    }

    fun moveUnit(draft: BookIndexIntakeDraft, fromIndex: Int, toIndex: Int): BookIndexIntakeDraft {
        if (fromIndex !in draft.units.indices || toIndex !in draft.units.indices || fromIndex == toIndex) {
            return draft
        }
        val mutable = draft.units.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return draft.copy(units = mutable, reviewerConfirmed = false)
    }

    fun updateUnit(
        draft: BookIndexIntakeDraft,
        unitIndex: Int,
        transform: (BookIndexUnitDraft) -> BookIndexUnitDraft
    ): BookIndexIntakeDraft {
        if (unitIndex !in draft.units.indices) return draft
        val mutable = draft.units.toMutableList()
        mutable[unitIndex] = transform(mutable[unitIndex])
        return draft.copy(units = mutable, reviewerConfirmed = false)
    }

    fun assignUnitPart(
        draft: BookIndexIntakeDraft,
        unitIndex: Int,
        partKey: String
    ): BookIndexIntakeDraft {
        val cleanPart = partKey.trim().uppercase().takeIf { it in setOf("P01", "P02") } ?: return draft
        return updateUnit(draft, unitIndex) { unit ->
            unit.copy(
                partKey = cleanPart,
                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                assignmentSource = IndexAssignmentSource.REVIEWER,
                assignmentEvidence = unit.assignmentEvidence.ifBlank {
                    "تأكيد المراجع لإسناد الوحدة إلى $cleanPart"
                }
            )
        }
    }

    fun confirmUnitAndLessons(draft: BookIndexIntakeDraft, unitIndex: Int): BookIndexIntakeDraft =
        updateUnit(draft, unitIndex) { unit ->
            val chosenPart = unit.partKey?.takeIf { it in setOf("P01", "P02") }
                ?: unit.proposedPartKey?.takeIf { it in setOf("P01", "P02") }
                ?: draft.partKey?.takeIf { it in setOf("P01", "P02") }
                ?: "P01"
            unit.copy(
                partKey = chosenPart,
                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                assignmentSource = IndexAssignmentSource.REVIEWER,
                assignmentEvidence = unit.assignmentEvidence.ifBlank {
                    "تأكيد المراجع لإسناد الوحدة إلى $chosenPart"
                },
                lessons = unit.lessons.map { lesson ->
                    lesson.copy(
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.REVIEWER,
                        assignmentEvidence = lesson.assignmentEvidence.ifBlank { "اعتماد المراجع" }
                    )
                }
            )
        }

    fun confirmAllUnitsAndLessons(draft: BookIndexIntakeDraft): BookIndexIntakeDraft {
        var updated = draft
        for (idx in updated.units.indices) {
            updated = confirmUnitAndLessons(updated, idx)
        }
        val resolvedEvidence = updated.sourceLayoutEvidence.ifBlank {
            when (updated.sourceLayout) {
                IndexSourceLayout.SINGLE_PART_FILE -> "تحقق المراجع من أن الملف يمثل الجزء المستقل ${updated.partKey ?: "P01"}"
                IndexSourceLayout.MERGED_PARTS_FILE -> "تحقق المراجع من أن الملف مدمج ويحتوي الجزأين P01 و P02"
                IndexSourceLayout.UNKNOWN -> ""
            }
        }
        return updated.copy(
            sourceLayoutEvidence = resolvedEvidence,
            reviewerConfirmed = false
        )
    }

    fun addLesson(draft: BookIndexIntakeDraft, unitIndex: Int): BookIndexIntakeDraft {
        val unit = draft.units.getOrNull(unitIndex) ?: return draft
        val nextNum = unit.lessons.size + 1
        var candidateKey = "lesson_${nextNum.toString().padStart(2, '0')}"
        val existing = unit.lessons.map { it.lessonKey }.toSet()
        var suffix = nextNum
        while (candidateKey in existing) {
            suffix++
            candidateKey = "lesson_${suffix.toString().padStart(2, '0')}"
        }
        val lastEnd = unit.lessons.lastOrNull()?.let { draft.physicalEndPage(it) } ?: 0
        val nextPage = (lastEnd + 1).coerceIn(1, draft.totalPdfPages.coerceAtLeast(1))
        val added = BookIndexLessonDraft(
            lessonKey = candidateKey,
            title = "الدرس $nextNum",
            pdfPageStart = nextPage,
            pdfPageEnd = nextPage,
            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
            assignmentSource = IndexAssignmentSource.REVIEWER,
            assignmentEvidence = "إضافة يدوية"
        )
        return updateUnit(draft, unitIndex) { current ->
            current.copy(lessons = current.lessons + added)
        }
    }

    fun removeLesson(draft: BookIndexIntakeDraft, unitIndex: Int, lessonIndex: Int): BookIndexIntakeDraft {
        val unit = draft.units.getOrNull(unitIndex) ?: return draft
        if (lessonIndex !in unit.lessons.indices) return draft
        val mutable = unit.lessons.toMutableList()
        mutable.removeAt(lessonIndex)
        return updateUnit(draft, unitIndex) { current -> current.copy(lessons = mutable) }
    }

    fun moveLesson(
        draft: BookIndexIntakeDraft,
        unitIndex: Int,
        fromIndex: Int,
        toIndex: Int
    ): BookIndexIntakeDraft {
        val unit = draft.units.getOrNull(unitIndex) ?: return draft
        if (fromIndex !in unit.lessons.indices || toIndex !in unit.lessons.indices || fromIndex == toIndex) {
            return draft
        }
        val mutable = unit.lessons.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return updateUnit(draft, unitIndex) { current -> current.copy(lessons = mutable) }
    }

    fun updateLesson(
        draft: BookIndexIntakeDraft,
        unitIndex: Int,
        lessonIndex: Int,
        transform: (BookIndexLessonDraft) -> BookIndexLessonDraft
    ): BookIndexIntakeDraft {
        val unit = draft.units.getOrNull(unitIndex) ?: return draft
        if (lessonIndex !in unit.lessons.indices) return draft
        val mutable = unit.lessons.toMutableList()
        mutable[lessonIndex] = transform(mutable[lessonIndex])
        return updateUnit(draft, unitIndex) { current -> current.copy(lessons = mutable) }
    }
}
