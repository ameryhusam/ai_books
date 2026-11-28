package com.example.domain.progress

import com.example.model.BookRef
import com.example.model.LessonRef
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningProgressCalculatorTest {

    @Test
    fun completionPercentHandlesInvalidBoundsRoundingAndOverflow() {
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(0, 0))
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(3, 0))
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(-1, 4))
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(2, -4))
        assertEquals(67, LearningProgressCalculator.calculateCompletionPercent(2, 3))
        assertEquals(100, LearningProgressCalculator.calculateCompletionPercent(4, 4))
        assertEquals(100, LearningProgressCalculator.calculateCompletionPercent(7, 4))
    }

    @Test
    fun snapshotSeparatesCompletionFromPreparationAndAggregatesBooks() {
        val arabic = book("ARAB", lessonsCount = 2)
        val science = book("SCI", lessonsCount = 1)
        val lessons = listOf(
            lesson("ARAB", "unit_01", "lesson_01"),
            lesson("ARAB", "unit_01", "lesson_02"),
            lesson("SCI", "unit_01", "lesson_01")
        )
        val completed = setOf(Triple("ARAB", "unit_01", "lesson_01"))
        val prepared = setOf(Triple("ARAB", "unit_01", "lesson_02"))

        val snapshot = LearningProgressCalculator.buildSnapshot(
            books = listOf(arabic, science),
            lessons = lessons,
            completedLessonKeys = completed,
            preparedLessonKeys = prepared
        )

        assertEquals(2, snapshot.totalBooks)
        assertEquals(3, snapshot.totalLessons)
        assertEquals(1, snapshot.completedLessons)
        assertEquals(33, snapshot.overallCompletionPercent)
        assertEquals(50, snapshot.books[0].completionPercent)
        assertEquals(100, snapshot.books[0].preparationPercent)
        assertEquals(50, snapshot.books[0].earnedPoints)
        assertEquals(0, snapshot.books[1].completionPercent)
        assertEquals(0, snapshot.books[1].preparationPercent)
    }

    @Test
    fun snapshotUsesDeclaredLessonCountAsMinimumAndIgnoresUnknownCompletionKeys() {
        val book = book("ARAB", lessonsCount = 3)
        val snapshot = LearningProgressCalculator.buildSnapshot(
            books = listOf(book),
            lessons = listOf(lesson("ARAB", "unit_01", "lesson_01")),
            completedLessonKeys = setOf(
                Triple("ARAB", "unit_01", "lesson_01"),
                Triple("ARAB", "unit_99", "missing")
            )
        )

        assertEquals(3, snapshot.totalLessons)
        assertEquals(1, snapshot.completedLessons)
        assertEquals(33, snapshot.overallCompletionPercent)
        assertEquals(1, snapshot.books.single().completedLessons)
        assertEquals(3, snapshot.books.single().totalLessons)
    }

    private fun book(bookKey: String, lessonsCount: Int) = BookRef(
        bookKey = bookKey,
        title = bookKey,
        subjectKey = bookKey,
        relativePath = "workspace/G04/$bookKey",
        lessonsCount = lessonsCount
    )

    private fun lesson(bookKey: String, unitKey: String, lessonKey: String) = LessonRef(
        grade = "G04",
        rootPath = "workspace/G04/$bookKey",
        bookKey = bookKey,
        bookTitle = bookKey,
        subjectKey = bookKey,
        unitKey = unitKey,
        unitTitle = unitKey,
        lessonKey = lessonKey,
        title = lessonKey
    )
}
