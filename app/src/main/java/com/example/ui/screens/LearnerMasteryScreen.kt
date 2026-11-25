package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.ConceptMasteryEngine
import com.example.data.repository.QuestionBankEngine
import com.example.model.ConceptMasterySummary
import com.example.model.LearnerProfile
import com.example.model.MasteryStatus
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.QuestionAnswerInputArea
import com.example.ui.components.QuestionMasteryDiagnosticBanner
import com.example.ui.components.RadialProgressIndicator
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduHeroKicker
import com.example.ui.theme.EduHeroSub
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnerMasteryScreen(
    concepts: List<ConceptMasterySummary>,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    totalPoints: Int,
    onLaunchReinforcement: (String?) -> Unit,
    activeLearner: LearnerProfile = LearnerProfile.DEFAULT,
    modifier: Modifier = Modifier
) {
    var statusFilter by remember { mutableStateOf<MasteryStatus?>(null) }

    val filteredConcepts = remember(concepts, statusFilter) {
        if (statusFilter == null) concepts else concepts.filter { it.status == statusFilter }
    }
    val masteredCount = remember(concepts) { concepts.count { it.status == MasteryStatus.MASTERED } }
    val weakCount = remember(concepts) { concepts.count { it.status == MasteryStatus.NEEDS_REINFORCEMENT } }
    val totalAttempts = remember(questionStats) { questionStats.values.sumOf { it.attemptsCount } }
    val totalCorrect = remember(questionStats) { questionStats.values.sumOf { it.correctCount } }
    val accuracy = if (totalAttempts == 0) 0 else ((totalCorrect * 100f) / totalAttempts).toInt()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "mastery_hero") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = EduPurple
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DIAGNOSTIC & CONCEPT MASTERY",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduHeroKicker
                        )
                        Text(
                            text = "تشخيص مستوى المتعلّم والمفاهيم (${activeLearner.displayName})",
                            style = MaterialTheme.typography.headlineMedium,
                            color = EduWhite
                        )
                        Text(
                            text = "الدقة العامة: $accuracy% • $masteredCount مفهوم مُتقَن • $weakCount يحتاج تقوية • $totalPoints XP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduHeroSub
                        )
                        if (weakCount > 0 || questions.isNotEmpty()) {
                            Button(
                                onClick = { onLaunchReinforcement(null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EduYellow,
                                    contentColor = EduInk
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_mixed_reinforcement_button")
                            ) {
                                Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (weakCount > 0) {
                                        "بدء جلسة تقوية للمفاهيم الضعيفة ($weakCount)"
                                    } else {
                                        "مراجعة سريعة لأسئلة الصف (${questions.size})"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            item(key = "mastery_kpis") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasteryStatBox(
                        value = "$accuracy%",
                        label = "دقة الإجابات",
                        accent = EduGreenDark,
                        modifier = Modifier.weight(1f)
                    )
                    MasteryStatBox(
                        value = masteredCount.toString(),
                        label = "مفهوم مُتقَن",
                        accent = EduGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MasteryStatBox(
                        value = weakCount.toString(),
                        label = "يحتاج تقوية",
                        accent = EduOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "mastery_filters") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    EduPill(
                        text = "كل المفاهيم (${concepts.size})",
                        active = statusFilter == null,
                        onClick = { statusFilter = null }
                    )
                    EduPill(
                        text = "يحتاج تقوية ($weakCount)",
                        active = statusFilter == MasteryStatus.NEEDS_REINFORCEMENT,
                        onClick = { statusFilter = MasteryStatus.NEEDS_REINFORCEMENT }
                    )
                    EduPill(
                        text = "مُتقَن ($masteredCount)",
                        active = statusFilter == MasteryStatus.MASTERED,
                        onClick = { statusFilter = MasteryStatus.MASTERED }
                    )
                    val unseenCount = concepts.count { it.status == MasteryStatus.UNSEEN }
                    EduPill(
                        text = "لم يُختبَر ($unseenCount)",
                        active = statusFilter == MasteryStatus.UNSEEN,
                        onClick = { statusFilter = MasteryStatus.UNSEEN }
                    )
                }
            }

            if (filteredConcepts.isEmpty()) {
                item(key = "mastery_empty") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = EduPurple)
                            Text(
                                text = "أجب عن اختبارات الدروس ليتم تحليل إتقان كل مفهوم بدقة وتمييز نقاط القوة والتقوية.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduMuted
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredConcepts,
                    key = { "concept_${it.conceptTag}" }
                ) { summary ->
                    ConceptMasteryCard(
                        summary = summary,
                        onPracticeConcept = { onLaunchReinforcement(summary.conceptTag) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MasteryStatBox(
    value: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    EduPanel(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = accent
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = EduMuted)
    }
}

@Composable
private fun ConceptMasteryCard(
    summary: ConceptMasterySummary,
    onPracticeConcept: () -> Unit
) {
    val (accent, containerBg) = when (summary.status) {
        MasteryStatus.MASTERED -> EduGreenDark to EduMint.copy(alpha = 0.35f)
        MasteryStatus.NEEDS_REINFORCEMENT -> EduOrange to EduOrange.copy(alpha = 0.08f)
        MasteryStatus.UNSEEN -> EduMuted to EduWhite
    }

    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("concept_card_${summary.conceptTag}"),
        containerColor = containerBg,
        borderColor = accent.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (summary.status) {
                            MasteryStatus.MASTERED -> Icons.Rounded.CheckCircle
                            MasteryStatus.NEEDS_REINFORCEMENT -> Icons.Rounded.WarningAmber
                            MasteryStatus.UNSEEN -> Icons.Rounded.AutoGraph
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.conceptTag,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                val meta = listOfNotNull(
                    summary.bookTitle?.takeIf { it.isNotBlank() },
                    summary.lessonTitle?.takeIf { it.isNotBlank() },
                    "${summary.questionsCount} سؤال"
                ).joinToString(" • ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = EduWhite
            ) {
                Text(
                    text = summary.status.labelAr,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (summary.attemptsCount > 0) {
            val ratio = (summary.accuracyPercent / 100f).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {
                drawRoundRect(
                    color = EduLine,
                    cornerRadius = CornerRadius(8f, 8f),
                    size = size
                )
                if (ratio > 0f) {
                    drawRoundRect(
                        color = accent,
                        cornerRadius = CornerRadius(8f, 8f),
                        size = Size(width = size.width * ratio, height = size.height)
                    )
                }
            }
            Text(
                text = "المحاولات: ${summary.correctCount} صحيحة • ${summary.wrongCount} خاطئة • الدقة ${summary.accuracyPercent}%",
                style = MaterialTheme.typography.labelSmall,
                color = EduInk
            )
        }

        Button(
            onClick = onPracticeConcept,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (summary.status == MasteryStatus.NEEDS_REINFORCEMENT) EduOrange else EduGreen,
                contentColor = EduWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("practice_concept_${summary.conceptTag}")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (summary.status == MasteryStatus.NEEDS_REINFORCEMENT) {
                    "تدريب تقوية على هذا المفهوم"
                } else {
                    "مراجعة أسئلة هذا المفهوم"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ConceptReinforcementQuizDialog(
    title: String,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    onRecordAttempts: (List<QuestionItem>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var index by remember(questions) { mutableIntStateOf(0) }
    val answers = remember(questions) { mutableStateMapOf<String, String>() }
    var checkedCurrent by remember(questions) { mutableStateOf(false) }
    var finished by remember(questions) { mutableStateOf(false) }

    val current = questions.getOrNull(index)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = if (finished) "ملخص جلسة التقوية" else "$title (${index + 1}/${questions.size})",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk
            )
        },
        text = {
            if (finished) {
                val summary = QuestionBankEngine.gradeQuiz(questions, answers)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadialProgressIndicator(
                        percentage = summary.scorePercent,
                        size = 96.dp,
                        strokeWidth = 9.dp,
                        label = "الدقة"
                    )
                    Text(
                        text = "أجبت بشكل صحيح على ${summary.correctCount} من ${summary.totalQuestions} أسئلة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EduInk
                    )
                    Surface(shape = RoundedCornerShape(10.dp), color = EduMint) {
                        Text(
                            text = "تم تحديث سجل الإتقان والمقارنة لكل سؤال وحساب +${summary.earnedXp} XP",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduGreenDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            } else if (current != null) {
                val picked = answers[current.id].orEmpty()
                val stats = questionStats[current.id]
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (index + 1).toFloat() / questions.size.coerceAtLeast(1) },
                        color = EduPurple,
                        trackColor = EduBg,
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuestionMasteryDiagnosticBanner(
                        stats = stats,
                        currentAnswer = picked
                    )
                    Text(
                        text = current.prompt,
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk
                    )
                    QuestionAnswerInputArea(
                        question = current,
                        currentAnswer = picked,
                        locked = checkedCurrent,
                        previousSelectedAnswer = stats?.lastSelectedAnswer,
                        onAnswerChange = { answers[current.id] = it }
                    )
                    if (checkedCurrent) {
                        val correctNow = QuestionBankEngine.isAnswerCorrect(current, picked)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (correctNow) EduMint else EduRed.copy(alpha = 0.12f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (correctNow) {
                                        "إجابة صحيحة ✓"
                                    } else {
                                        "الإجابة الصحيحة: ${current.correctAnswer}"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (correctNow) EduGreenDark else EduRed
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
            if (finished) {
                TextButton(onClick = onDismiss) {
                    Text("تم", color = EduGreenDark, fontWeight = FontWeight.Bold)
                }
            } else if (!checkedCurrent) {
                TextButton(
                    enabled = current != null && !answers[current.id].isNullOrBlank(),
                    onClick = { checkedCurrent = true }
                ) {
                    Text("تحقق من الإجابة", color = EduGreenDark, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = {
                    if (index < questions.lastIndex) {
                        index++
                        checkedCurrent = false
                    } else {
                        finished = true
                        onRecordAttempts(questions, answers.toMap())
                    }
                }) {
                    Text(
                        text = if (index < questions.lastIndex) "السؤال التالي" else "إنهاء وتحديث التقييم",
                        color = EduGreenDark,
                        fontWeight = FontWeight.Bold
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
