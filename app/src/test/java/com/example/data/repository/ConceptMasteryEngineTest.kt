package com.example.data.repository

import com.example.model.MasteryStatus
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConceptMasteryEngineTest {
    @Test
    fun questionStatsClassifyMasteredWeakAndUnseenAndIgnoreStudioPreview() {
        val questions = listOf(question("mastered", "fractions"), question("weak", "fractions"), question("unseen", "fractions"))
        val attempts = listOf(
            attempt("a1", "mastered", "fractions", true, "2026-01-01T10:00:00Z"),
            attempt("a2", "weak", "fractions", true, "2026-01-01T10:00:00Z"),
            attempt("a3", "weak", "fractions", false, "2026-01-01T11:00:00Z"),
            attempt("preview", "unseen", "fractions", false, "2026-01-01T12:00:00Z", "studio_preview")
        )
        val stats = ConceptMasteryEngine.computeQuestionStats(questions, attempts)
        assertEquals(MasteryStatus.MASTERED, stats.getValue("mastered").status)
        assertEquals(MasteryStatus.NEEDS_REINFORCEMENT, stats.getValue("weak").status)
        assertEquals(2, stats.getValue("weak").attemptsCount)
        assertEquals("ب", stats.getValue("weak").lastSelectedAnswer)
        assertEquals(MasteryStatus.UNSEEN, stats.getValue("unseen").status)
        assertEquals(0, stats.getValue("unseen").attemptsCount)
    }

    @Test
    fun conceptSummaryAggregatesAttemptsAndCalculatesAccuracy() {
        val questions = listOf(question("q1", "fractions"), question("q2", "fractions"))
        val attempts = listOf(
            attempt("a1", "q1", "fractions", true, "2026-01-01T10:00:00Z"),
            attempt("a2", "q1", "fractions", true, "2026-01-01T11:00:00Z"),
            attempt("a3", "q2", "fractions", false, "2026-01-01T12:00:00Z")
        )
        val summary = ConceptMasteryEngine.computeConceptSummaries(
            questions, attempts, mapOf("SCI" to "العلوم")
        ).single()
        assertEquals("fractions", summary.conceptTag)
        assertEquals(2, summary.questionsCount)
        assertEquals(3, summary.attemptsCount)
        assertEquals(2, summary.correctCount)
        assertEquals(1, summary.wrongCount)
        assertEquals(67, summary.accuracyPercent)
        assertEquals(MasteryStatus.NEEDS_REINFORCEMENT, summary.status)
        assertEquals("العلوم", summary.bookTitle)
    }

    @Test
    fun reinforcementSelectionPrioritizesWeakQuestionsAndExcludesUnreviewedQuestions() {
        val weak = question("weak", "fractions", reviewed = true)
        val unseen = question("unseen", "fractions", reviewed = true)
        val mastered = question("mastered", "fractions", reviewed = true)
        val pending = question("pending", "fractions", reviewed = false)
        val other = question("other", "geometry", reviewed = true)
        val attempts = listOf(
            attempt("a1", "weak", "fractions", false, "2026-01-01T10:00:00Z"),
            attempt("a2", "mastered", "fractions", true, "2026-01-01T10:00:00Z")
        )
        val selected = ConceptMasteryEngine.selectReinforcementQuestions(
            listOf(mastered, pending, unseen, other, weak), attempts, "fractions"
        )
        assertEquals(listOf("weak", "unseen", "mastered"), selected.map { it.id })
        assertFalse(selected.any { it.id == "pending" || it.id == "other" })
    }

    @Test
    fun effectiveConceptTagFallsBackToLessonTitleThenLessonKey() {
        val byTitle = question("q1", "  ", lessonTitle = " الكسور ")
        val byKey = question("q2", "", lessonTitle = " ")
        assertEquals("الكسور", ConceptMasteryEngine.effectiveConceptTag(byTitle))
        assertEquals("lesson-q2", ConceptMasteryEngine.effectiveConceptTag(byKey))
        assertTrue(ConceptMasteryEngine.computeConceptSummaries(emptyList(), emptyList()).isEmpty())
    }

    private fun question(
        id: String,
        conceptTag: String,
        reviewed: Boolean = true,
        lessonTitle: String = "درس $id"
    ) = QuestionItem(
        id = id, workspaceId = "ws-test", gradeKey = "G04", bookKey = "SCI",
        unitKey = "unit_01", lessonKey = "lesson-$id", lessonTitle = lessonTitle,
        type = QuestionType.MCQ, prompt = "سؤال $id", options = listOf("أ", "ب", "ج"),
        correctAnswer = "أ", rationale = "", difficulty = QuestionDifficulty.MEDIUM,
        reviewed = reviewed, conceptTag = conceptTag
    )

    private fun attempt(
        id: String, questionId: String, conceptTag: String, correct: Boolean,
        at: String, source: String = "lesson_quiz"
    ) = QuestionAttemptRecord(
        id = id, workspaceId = "ws-test", questionId = questionId,
        conceptTag = conceptTag, selectedAnswer = if (correct) "أ" else "ب",
        isCorrect = correct, sourceMode = source, answeredAt = at
    )
}
