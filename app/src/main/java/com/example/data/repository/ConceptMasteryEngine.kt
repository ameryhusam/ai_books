package com.example.data.repository

import com.example.model.ConceptMasterySummary
import com.example.model.MasteryStatus
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.domain.progress.LearningProgressCalculator

/**
 * Computes per-question answer history and concept-level mastery diagnostics.
 *
 * Rules requested by the educator/learner specification:
 * - Never attempted -> UNSEEN
 * - Attempted at least once and NEVER answered wrongly (100% correct) -> MASTERED ("مُتقَن")
 * - Mixed right/wrong OR any wrong attempt on that question/concept -> NEEDS_REINFORCEMENT ("يحتاج تقوية")
 */
object ConceptMasteryEngine {

    fun effectiveConceptTag(question: QuestionItem): String {
        return question.conceptTag.trim().ifBlank {
            question.lessonTitle.trim().ifBlank { question.lessonKey }
        }
    }

    fun computeQuestionStats(
        questions: List<QuestionItem>,
        attempts: List<QuestionAttemptRecord>
    ): Map<String, QuestionMasteryStats> {
        val grouped = attempts
            .filterNot { it.sourceMode.equals("studio_preview", ignoreCase = true) }
            .sortedBy { it.answeredAt }
            .groupBy { it.questionId }

        return questions.associate { q ->
            val history = grouped[q.id].orEmpty()
            val correctCount = history.count { it.isCorrect }
            val wrongCount = history.count { !it.isCorrect }
            val last = history.lastOrNull()
            val status = when {
                history.isEmpty() -> MasteryStatus.UNSEEN
                wrongCount == 0 && correctCount >= 1 -> MasteryStatus.MASTERED
                else -> MasteryStatus.NEEDS_REINFORCEMENT
            }
            q.id to QuestionMasteryStats(
                questionId = q.id,
                conceptTag = effectiveConceptTag(q),
                attemptsCount = history.size,
                correctCount = correctCount,
                wrongCount = wrongCount,
                lastSelectedAnswer = last?.selectedAnswer,
                lastIsCorrect = last?.isCorrect,
                status = status
            )
        }
    }

    fun computeConceptSummaries(
        questions: List<QuestionItem>,
        attempts: List<QuestionAttemptRecord>,
        bookTitleByKey: Map<String, String> = emptyMap()
    ): List<ConceptMasterySummary> {
        if (questions.isEmpty()) return emptyList()
        val questionById = questions.associateBy { it.id }
        val learnerAttempts = attempts.filterNot {
            it.sourceMode.equals("studio_preview", ignoreCase = true)
        }
        val attemptsByConcept = learnerAttempts.groupBy { att ->
            att.conceptTag.trim().ifBlank {
                questionById[att.questionId]?.let { effectiveConceptTag(it) } ?: att.questionId
            }
        }
        val questionsByConcept = questions.groupBy { effectiveConceptTag(it) }

        return questionsByConcept.map { (concept, qList) ->
            val qIds = qList.map { it.id }.toSet()
            val relevantAttempts = (attemptsByConcept[concept].orEmpty() +
                learnerAttempts.filter { it.questionId in qIds })
                .distinctBy { it.id }

            val correct = relevantAttempts.count { it.isCorrect }
            val wrong = relevantAttempts.count { !it.isCorrect }
            val total = relevantAttempts.size
            val accuracy = if (total == 0) 0 else LearningProgressCalculator.calculateCompletionPercent(correct, total)

            val status = when {
                total == 0 -> MasteryStatus.UNSEEN
                wrong == 0 && correct >= 1 -> MasteryStatus.MASTERED
                else -> MasteryStatus.NEEDS_REINFORCEMENT
            }

            val rep = qList.first()
            ConceptMasterySummary(
                conceptTag = concept,
                lessonKey = rep.lessonKey,
                lessonTitle = rep.lessonTitle,
                bookTitle = bookTitleByKey[rep.bookKey] ?: rep.bookKey,
                questionsCount = qList.size,
                attemptsCount = total,
                correctCount = correct,
                wrongCount = wrong,
                accuracyPercent = accuracy,
                status = status
            )
        }.sortedWith(
            compareBy<ConceptMasterySummary> {
                when (it.status) {
                    MasteryStatus.NEEDS_REINFORCEMENT -> 0
                    MasteryStatus.UNSEEN -> 1
                    MasteryStatus.MASTERED -> 2
                }
            }
                .thenByDescending { it.wrongCount }
                .thenBy { it.accuracyPercent }
        )
    }

    fun selectReinforcementQuestions(
        questions: List<QuestionItem>,
        attempts: List<QuestionAttemptRecord>,
        targetConceptTag: String? = null
    ): List<QuestionItem> {
        // Unreviewed AI-generated questions must not enter learner practice automatically.
        val approvedQuestions = questions.filter { it.reviewed }
        val statsByQ = computeQuestionStats(approvedQuestions, attempts)
        val filtered = if (!targetConceptTag.isNullOrBlank()) {
            approvedQuestions.filter { effectiveConceptTag(it).equals(targetConceptTag.trim(), ignoreCase = true) }
        } else {
            approvedQuestions
        }
        return filtered.sortedWith(
            compareBy<QuestionItem> { q ->
                when (statsByQ[q.id]?.status) {
                    MasteryStatus.NEEDS_REINFORCEMENT -> 0
                    MasteryStatus.UNSEEN -> 1
                    MasteryStatus.MASTERED -> 2
                    null -> 1
                }
            }.thenByDescending { q -> statsByQ[q.id]?.wrongCount ?: 0 }
        )
    }
}
