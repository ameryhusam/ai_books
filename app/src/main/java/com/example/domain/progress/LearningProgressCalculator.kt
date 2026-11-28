package com.example.domain.progress

import com.example.model.BookProgressSummary
import com.example.model.BookRef
import com.example.model.LessonRef
import kotlin.math.roundToInt

/**
 * Pure Kotlin calculations for lesson and book progress.
 *
 * This class deliberately has no Android, Compose, database, or UI dependencies so
 * it can be reused by domain logic and tested independently of the presentation layer.
 */
data class LearningProgressSnapshot(
    val totalBooks: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val overallCompletionPercent: Int,
    val books: List<BookProgressSummary>
)

object LearningProgressCalculator {

    fun calculateCompletionPercent(completedCount: Int, totalCount: Int): Int {
        if (totalCount <= 0 || completedCount <= 0) return 0
        return ((completedCount.coerceAtMost(totalCount) * 100f) / totalCount)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun buildSnapshot(
        books: List<BookRef>,
        lessons: List<LessonRef>,
        completedLessonKeys: Set<Triple<String, String, String>>,
        preparedLessonKeys: Set<Triple<String, String, String>> = emptySet()
    ): LearningProgressSnapshot {
        val lessonsByBook = lessons.groupBy { it.bookKey }
        val bookSummaries = books.map { book ->
            val bookLessons = lessonsByBook[book.bookKey].orEmpty()
            val total = bookLessons.size.coerceAtLeast(book.lessonsCount)
            val completed = bookLessons.count {
                Triple(it.bookKey, it.unitKey, it.lessonKey) in completedLessonKeys
            }
            val prepared = bookLessons.count {
                val key = Triple(it.bookKey, it.unitKey, it.lessonKey)
                key in completedLessonKeys || key in preparedLessonKeys
            }
            BookProgressSummary(
                book = book,
                completedLessons = completed,
                preparedLessons = prepared,
                totalLessons = total,
                completionPercent = calculateCompletionPercent(completed, total),
                preparationPercent = calculateCompletionPercent(prepared, total),
                earnedPoints = completed * 50
            )
        }
        val totalLessons = bookSummaries.sumOf { it.totalLessons }
        val completedLessons = bookSummaries.sumOf { it.completedLessons }
        return LearningProgressSnapshot(
            totalBooks = books.size,
            totalLessons = totalLessons,
            completedLessons = completedLessons,
            overallCompletionPercent = calculateCompletionPercent(completedLessons, totalLessons),
            books = bookSummaries
        )
    }
}
