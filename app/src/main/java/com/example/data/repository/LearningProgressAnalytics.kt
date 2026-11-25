package com.example.data.repository

import com.example.model.AssessmentAttemptRecord
import com.example.model.LearningProgressReport
import com.example.model.RepeatedMistakeSummary
import com.example.model.WeeklyAssessmentSummary
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Evidence-based progress indicators. These are descriptive indicators, not a clinical
 * diagnosis and not a causal estimate of the effect of AI-generated explanations.
 */
object LearningProgressAnalytics {
    private val dateLabel = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)

    fun calculate(
        questions: List<QuestionItem>,
        attempts: List<QuestionAttemptRecord>,
        assessments: List<AssessmentAttemptRecord>,
        now: Instant = Instant.now()
    ): LearningProgressReport {
        val questionById = questions.associateBy { it.id }
        val ordered = attempts
            .filter { it.questionId.isNotBlank() && it.answeredAt.isNotBlank() }
            .sortedBy { parseInstant(it.answeredAt) ?: Instant.MIN }

        if (ordered.isEmpty() && assessments.isEmpty()) return LearningProgressReport()

        // Do not let reinforcement-practice responses inflate the main learning score.
        // Practice attempts remain available for error-pattern and support-impact analysis.
        val measuredAttempts = ordered.filterNot { isPracticeOnly(it.sourceMode) }
        val byQuestion = measuredAttempts.groupBy { it.questionId }
        val firstPerQuestion = byQuestion.values.mapNotNull { rows ->
            rows.minByOrNull { parseInstant(it.answeredAt) ?: Instant.MAX }
        }
        val latestPerQuestion = byQuestion.values.mapNotNull { rows ->
            rows.maxByOrNull { parseInstant(it.answeredAt) ?: Instant.MIN }
        }
        val initialAccuracy = percent(firstPerQuestion.count { it.isCorrect }, firstPerQuestion.size)
        val latestAccuracy = percent(latestPerQuestion.count { it.isCorrect }, latestPerQuestion.size)

        val concepts = measuredAttempts.groupBy { it.conceptTag.trim().ifBlank { "غير مصنف" } }
        val stableConcepts = concepts.count { (_, rows) ->
            val latestByQuestion = rows.groupBy { it.questionId }.values.mapNotNull { group ->
                group.maxByOrNull { parseInstant(it.answeredAt) ?: Instant.MIN }
            }
            val days = rows.mapNotNull { parseInstant(it.answeredAt)?.atZone(ZoneOffset.UTC)?.toLocalDate() }.distinct()
            latestByQuestion.size >= MIN_QUESTIONS_FOR_STABILITY &&
                days.size >= 2 &&
                latestByQuestion.count { it.isCorrect } * 100 >= latestByQuestion.size * STABLE_ACCURACY_PERCENT
        }
        val eligibleConcepts = concepts.count { (_, rows) -> rows.map { it.questionId }.distinct().size >= MIN_QUESTIONS_FOR_STABILITY }
        val stablePercent = if (eligibleConcepts == 0) null else stableConcepts * 100 / eligibleConcepts

        val delayedPairs = byQuestion.values.mapNotNull { rows ->
            val sorted = rows.sortedBy { parseInstant(it.answeredAt) ?: Instant.MAX }
            val first = sorted.firstOrNull() ?: return@mapNotNull null
            val firstAt = parseInstant(first.answeredAt) ?: return@mapNotNull null
            val delayedThreshold = firstAt.plusSeconds(DELAYED_RECALL_DAYS * SECONDS_PER_DAY)
            val delayed = sorted.lastOrNull { attempt ->
                parseInstant(attempt.answeredAt)?.let { !it.isBefore(delayedThreshold) } == true
            } ?: return@mapNotNull null
            first to delayed
        }
        val delayedRecall = if (delayedPairs.size < MIN_DELAYED_QUESTIONS) null
            else percent(delayedPairs.count { it.second.isCorrect }, delayedPairs.size)
        val delayedBaseline = if (delayedPairs.size < MIN_DELAYED_QUESTIONS) null
            else percent(delayedPairs.count { it.first.isCorrect }, delayedPairs.size)
        val recallDrop = if (delayedRecall == null || delayedBaseline == null) null
            else (delayedBaseline - delayedRecall).coerceAtLeast(0)

        val recurringMistakes = ordered
            .filter { !it.isCorrect && it.selectedAnswer.isNotBlank() }
            .groupBy { it.questionId to normalize(it.selectedAnswer) }
            .mapNotNull { (key, rows) ->
                if (rows.size < 2) return@mapNotNull null
                val question = questionById[key.first]
                RepeatedMistakeSummary(
                    conceptTag = rows.last().conceptTag.ifBlank { "غير مصنف" },
                    questionPrompt = question?.prompt.orEmpty(),
                    selectedAnswer = rows.last().selectedAnswer,
                    expectedAnswer = question?.correctAnswer.orEmpty(),
                    occurrences = rows.size,
                    lastSeenAt = rows.maxByOrNull { it.answeredAt }?.answeredAt.orEmpty()
                )
            }
            .sortedByDescending { it.occurrences }
            .take(8)

        val recurringConceptTags = ordered
            .filterNot { it.isCorrect }
            .groupBy { it.conceptTag.trim().ifBlank { "غير مصنف" } }
            .filter { (_, rows) ->
                rows.map { it.questionId }.distinct().size >= 2 ||
                    rows.groupBy { normalize(it.selectedAnswer) }.values.any { it.size >= 2 }
            }
            .keys
            .sorted()

        val reinforcementImpact = calculateReinforcementImpact(ordered)
        val weekly = calculateWeeklyAssessments(assessments, now)

        return LearningProgressReport(
            questionAttemptsCount = ordered.size,
            conceptsObserved = concepts.size,
            firstTryAccuracyPercent = initialAccuracy,
            latestAccuracyPercent = latestAccuracy,
            stableConceptsCount = stableConcepts,
            eligibleConceptsCount = eligibleConcepts,
            stableConceptsPercent = stablePercent,
            delayedRecallPercent = delayedRecall,
            estimatedRecallDropPercent = recallDrop,
            delayedRecallSampleCount = delayedPairs.size,
            recurringMistakes = recurringMistakes,
            recurringConceptTags = recurringConceptTags,
            conceptsWithRecurringErrorsCount = recurringConceptTags.size,
            weeklyAssessments = weekly,
            reinforcementImpactPercent = reinforcementImpact.first,
            reinforcementImpactConceptCount = reinforcementImpact.second,
            hasEnoughDataForRetention = delayedPairs.size >= MIN_DELAYED_QUESTIONS
        )
    }

    private fun calculateReinforcementImpact(
        attempts: List<QuestionAttemptRecord>
    ): Pair<Int?, Int> {
        val changes = attempts
            .groupBy { it.conceptTag.trim().ifBlank { "غير مصنف" } }
            .mapNotNull { (_, rows) ->
                val firstSupport = rows
                    .filter { it.sourceMode.equals("reinforcement", ignoreCase = true) }
                    .mapNotNull { parseInstant(it.answeredAt) }
                    .minOrNull() ?: return@mapNotNull null
                val before = latestPerQuestion(rows.filter {
                    !isPracticeOnly(it.sourceMode) &&
                        (parseInstant(it.answeredAt)?.isBefore(firstSupport) == true)
                }).associateBy { it.questionId }
                val after = latestPerQuestion(rows.filter {
                    !isPracticeOnly(it.sourceMode) &&
                        (parseInstant(it.answeredAt)?.isAfter(firstSupport) == true)
                }).associateBy { it.questionId }
                val sharedIds = before.keys.intersect(after.keys)
                if (sharedIds.size < MIN_QUESTIONS_FOR_IMPACT) return@mapNotNull null
                val beforeScore = sharedIds.count { before[it]?.isCorrect == true } * 100 / sharedIds.size
                val afterScore = sharedIds.count { after[it]?.isCorrect == true } * 100 / sharedIds.size
                afterScore - beforeScore
            }
        return if (changes.isEmpty()) null to 0 else changes.average().toInt() to changes.size
    }

    private fun latestPerQuestion(rows: List<QuestionAttemptRecord>): List<QuestionAttemptRecord> =
        rows.groupBy { it.questionId }.values.mapNotNull { group ->
            group.maxByOrNull { parseInstant(it.answeredAt) ?: Instant.MIN }
        }

    private fun calculateWeeklyAssessments(
        attempts: List<AssessmentAttemptRecord>,
        now: Instant
    ): List<WeeklyAssessmentSummary> {
        val currentWeek = now.atZone(ZoneOffset.UTC).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val earliestWeek = currentWeek.minusWeeks(WEEK_WINDOW - 1L)
        return attempts.mapNotNull { attempt ->
            val date = parseInstant(attempt.completedAt)?.atZone(ZoneOffset.UTC)?.toLocalDate()
                ?: return@mapNotNull null
            val week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (week.isBefore(earliestWeek) || week.isAfter(currentWeek)) null else week to attempt
        }.groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (week, rows) ->
                WeeklyAssessmentSummary(
                    weekStart = week.toString(),
                    label = week.format(dateLabel),
                    averageScorePercent = rows.map { it.scorePercent.coerceIn(0, 100) }.average().toInt(),
                    attemptsCount = rows.size,
                    passedCount = rows.count { it.passed }
                )
            }
    }

    private fun isPracticeOnly(sourceMode: String): Boolean =
        sourceMode.equals("reinforcement", ignoreCase = true) ||
            sourceMode.equals("bank_quiz", ignoreCase = true)

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
    private fun percent(correct: Int, total: Int): Int? =
        if (total <= 0) null else (correct * 100 / total).coerceIn(0, 100)
    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    private const val MIN_QUESTIONS_FOR_STABILITY = 3
    private const val MIN_DELAYED_QUESTIONS = 3
    private const val MIN_QUESTIONS_FOR_IMPACT = 2
    private const val STABLE_ACCURACY_PERCENT = 80
    private const val DELAYED_RECALL_DAYS = 7L
    private const val SECONDS_PER_DAY = 86_400L
    private const val WEEK_WINDOW = 8
}
