package com.example.data.repository

import com.example.model.LessonRef

/**
 * A learner may open a lesson only after every earlier lesson in the same book
 * has a recorded passing completion. The input order is the canonical Workspace index order.
 */
object LessonSequenceGate {
    fun isUnlocked(
        lesson: LessonRef,
        orderedBookLessons: List<LessonRef>,
        completedLessonKeys: Set<String>
    ): Boolean {
        val targetIndex = orderedBookLessons.indexOfFirst { sameLesson(it, lesson) }
        if (targetIndex < 0) return false
        return orderedBookLessons.take(targetIndex).all { key(it) in completedLessonKeys }
    }

    fun key(lesson: LessonRef): String =
        listOf(lesson.bookKey, lesson.unitKey, lesson.lessonKey).joinToString("::")

    private fun sameLesson(left: LessonRef, right: LessonRef): Boolean =
        left.bookKey == right.bookKey &&
            left.unitKey == right.unitKey &&
            left.lessonKey == right.lessonKey
}
