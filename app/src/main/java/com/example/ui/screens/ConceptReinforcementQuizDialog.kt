package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.repository.QuestionBankEngine
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.ui.components.QuestionAnswerInputArea
import com.example.ui.components.QuestionMasteryDiagnosticBanner
import com.example.ui.components.RadialProgressIndicator
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduWhite

/**
 * Focused remediation quiz for questions selected from weak-concept evidence.
 * Attempts are recorded once, when the learner completes the session.
 */
@Composable
fun ConceptReinforcementQuizDialog(
    title: String,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    onRecordAttempts: (List<QuestionItem>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    if (questions.isEmpty()) return

    var index by remember(questions) { mutableIntStateOf(0) }
    val answers = remember(questions) { mutableStateMapOf<String, String>() }
    var checkedCurrent by remember(questions) { mutableStateOf(false) }
    var finished by remember(questions) { mutableStateOf(false) }
    var completionReported by remember(questions) { mutableStateOf(false) }

    val current = questions.getOrNull(index)
    val summary = if (finished) QuestionBankEngine.gradeQuiz(questions, answers.toMap()) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = if (finished) "نتيجة جلسة التقوية" else "$title — سؤال ${index + 1} من ${questions.size}",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk
            )
        },
        text = {
            if (finished && summary != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadialProgressIndicator(
                        percentage = summary.scorePercent,
                        size = 96.dp,
                        strokeWidth = 9.dp,
                        label = "النتيجة"
                    )
                    Text(
                        text = "الإجابات الصحيحة: ${summary.correctCount} من ${summary.totalQuestions}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EduInk
                    )
                    Surface(color = EduMint) {
                        Text(
                            text = "+${summary.earnedXp} XP",
                            style = MaterialTheme.typography.labelLarge,
                            color = EduGreenDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            } else if (current != null) {
                val selectedAnswer = answers[current.id].orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (index + 1).toFloat() / questions.size.coerceAtLeast(1) },
                        color = EduGreen,
                        trackColor = EduBg,
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuestionMasteryDiagnosticBanner(
                        stats = questionStats[current.id],
                        currentAnswer = selectedAnswer
                    )
                    Text(
                        text = current.prompt,
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk
                    )
                    QuestionAnswerInputArea(
                        question = current,
                        currentAnswer = selectedAnswer,
                        locked = checkedCurrent,
                        previousSelectedAnswer = questionStats[current.id]?.lastSelectedAnswer,
                        onAnswerChange = { answers[current.id] = it }
                    )
                    if (checkedCurrent) {
                        Surface(color = EduMint.copy(alpha = 0.5f)) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "الإجابة الصحيحة: ${current.correctAnswer}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EduGreenDark
                                )
                                if (current.rationale.isNotBlank()) {
                                    Text(
                                        text = current.rationale,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                finished -> TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = EduGreenDark)
                }
                !checkedCurrent -> TextButton(
                    enabled = current != null && answers[current.id].orEmpty().isNotBlank(),
                    onClick = { checkedCurrent = true }
                ) {
                    Text("تحقق من الإجابة", color = EduGreenDark)
                }
                else -> TextButton(
                    onClick = {
                        if (index < questions.lastIndex) {
                            index++
                            checkedCurrent = false
                        } else {
                            if (!completionReported) {
                                completionReported = true
                                onRecordAttempts(questions, answers.toMap())
                            }
                            finished = true
                        }
                    }
                ) {
                    Text(
                        text = if (index < questions.lastIndex) "السؤال التالي" else "إنهاء جلسة التقوية",
                        color = EduGreenDark
                    )
                }
            }
        },
        dismissButton = {
            if (!finished) {
                TextButton(onClick = onDismiss) {
                    Text("خروج", color = EduMuted)
                }
            }
        }
    )
}
