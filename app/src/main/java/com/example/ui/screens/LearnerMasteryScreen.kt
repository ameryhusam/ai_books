package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConceptMasterySummary
import com.example.model.LearnerProfile
import com.example.model.LearningProgressReport
import com.example.model.MasteryStatus
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduFieldBg
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

private enum class ConceptFilterMode(val labelAr: String) {
    ALL("الكل"),
    NEEDS_REVIEW("يحتاج تقوية"),
    MASTERED("متقن")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnerMasteryScreen(
    concepts: List<ConceptMasterySummary>,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    totalPoints: Int,
    onLaunchReinforcement: (String?) -> Unit,
    activeLearner: LearnerProfile = LearnerProfile.DEFAULT,
    learningProgressReport: LearningProgressReport = LearningProgressReport(),
    modifier: Modifier = Modifier
) {
    val stableCount = learningProgressReport.stableConceptsCount
    val latestAccuracy = learningProgressReport.latestAccuracyPercent
    val reviewedQuestionsCount = remember(questions) { questions.count { it.reviewed } }
    val attemptedQuestionsCount = remember(questionStats) {
        questionStats.values.count { it.attemptsCount > 0 }
    }
    val weakConcepts = remember(concepts) {
        concepts.filter { it.status == MasteryStatus.NEEDS_REINFORCEMENT || (it.attemptsCount > 0 && it.accuracyPercent < 75) }
    }
    val masteredConcepts = remember(concepts) {
        concepts.filter { it.status == MasteryStatus.MASTERED || (it.attemptsCount > 0 && it.accuracyPercent >= 75) }
    }
    var filterMode by remember { mutableStateOf(ConceptFilterMode.ALL) }

    val visibleConcepts = remember(concepts, weakConcepts, masteredConcepts, filterMode) {
        when (filterMode) {
            ConceptFilterMode.ALL -> concepts.sortedWith(
                compareByDescending<ConceptMasterySummary> { it.status == MasteryStatus.NEEDS_REINFORCEMENT }
                    .thenBy { it.accuracyPercent }
            )
            ConceptFilterMode.NEEDS_REVIEW -> weakConcepts.sortedBy { it.accuracyPercent }
            ConceptFilterMode.MASTERED -> masteredConcepts.sortedByDescending { it.accuracyPercent }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "mastery_hero") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = EduGreenDark
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        EduGreenDark,
                                        EduGreen,
                                        EduPurple.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EduWhite.copy(alpha = 0.18f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoGraph,
                                        contentDescription = null,
                                        tint = EduYellow,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "رحلة الإتقان والتقييم الذاتي",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = EduWhite
                                    )
                                    Text(
                                        text = activeLearner.displayName.takeIf { it.isNotBlank() }?.let { "مسار الطالب: $it" }
                                            ?: "تتبع مفاهيمك وتعزيز نقاط القوة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EduHeroKicker
                                    )
                                }
                            }

                            Text(
                                text = when {
                                    latestAccuracy == null -> "واصل حل الاختبارات والأنشطة التفاعلية لبدء بناء خريطة إتقان مفاهيمك."
                                    latestAccuracy >= 85 -> "أداؤك متميز! حافظ على هذا المستوى بالمراجعة المتباعدة للمفاهيم."
                                    latestAccuracy >= 65 -> "تقدمك ثابت وواضح. جلسات التقوية القصيرة سترفع دقة مفاهيمك سريعاً."
                                    else -> "كل محاولة تقرّبك من الإتقان. ابدأ بالمفاهيم ذات الأولوية خطوة بخطوة."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduHeroSub
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MasteryHeroMetricTile(
                                    value = latestAccuracy?.let { "$it%" } ?: "—",
                                    label = "أحدث دقة",
                                    accent = EduYellow,
                                    modifier = Modifier.weight(1f)
                                )
                                MasteryHeroMetricTile(
                                    value = if (learningProgressReport.eligibleConceptsCount > 0) "$stableCount" else "${masteredConcepts.size}",
                                    label = "مفاهيم متقنة",
                                    accent = EduHeroKicker,
                                    modifier = Modifier.weight(1f)
                                )
                                MasteryHeroMetricTile(
                                    value = "$totalPoints",
                                    label = "نقاط XP",
                                    accent = EduWhite,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "mastery_next_step_card") {
                EduPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = EduMint.copy(alpha = 0.45f),
                    borderColor = EduGreen.copy(alpha = 0.55f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "خطوتك القادمة للتقوية",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk
                            )
                            Text(
                                text = if (weakConcepts.isNotEmpty()) {
                                    "لديك ${weakConcepts.size} مفهوم يحتاج مراجعة مركزة؛ ابدأ جلسة قصيرة الآن لتعزيز التذكر."
                                } else {
                                    "المراجعة القصيرة المنتظمة تساعدك على تثبيت المعلومات واستخدامها في أسئلة جديدة."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MasteryMiniStatPill(
                            icon = Icons.Rounded.Quiz,
                            text = "أسئلة متاحة: $reviewedQuestionsCount"
                        )
                        MasteryMiniStatPill(
                            icon = Icons.Rounded.Bolt,
                            text = "أسئلة مُجابة: $attemptedQuestionsCount"
                        )
                    }
                    EduYellowButton(
                        text = if (weakConcepts.isNotEmpty()) "ابدأ جلسة تقوية مركزة" else "ابدأ جلسة مراجعة عامة",
                        icon = Icons.Rounded.Psychology,
                        enabled = reviewedQuestionsCount > 0,
                        onClick = { onLaunchReinforcement(weakConcepts.firstOrNull()?.conceptTag) },
                        testTag = "learner_start_reinforcement_button"
                    )
                    if (reviewedQuestionsCount == 0) {
                        Text(
                            text = "ستتوفر جلسات المراجعة بمجرد اعتماد أسئلة التقييم للدروس.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    }
                }
            }

            item(key = "mastery_concepts_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TrendingUp,
                                contentDescription = null,
                                tint = EduPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "تحليل إتقان المفاهيم (${concepts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk
                            )
                        }
                    }
                    if (concepts.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EduPill(
                                text = "${ConceptFilterMode.ALL.labelAr} (${concepts.size})",
                                active = filterMode == ConceptFilterMode.ALL,
                                onClick = { filterMode = ConceptFilterMode.ALL },
                                testTag = "mastery_filter_all"
                            )
                            EduPill(
                                text = "${ConceptFilterMode.NEEDS_REVIEW.labelAr} (${weakConcepts.size})",
                                active = filterMode == ConceptFilterMode.NEEDS_REVIEW,
                                onClick = { filterMode = ConceptFilterMode.NEEDS_REVIEW },
                                testTag = "mastery_filter_weak"
                            )
                            EduPill(
                                text = "${ConceptFilterMode.MASTERED.labelAr} (${masteredConcepts.size})",
                                active = filterMode == ConceptFilterMode.MASTERED,
                                onClick = { filterMode = ConceptFilterMode.MASTERED },
                                testTag = "mastery_filter_mastered"
                            )
                        }
                    }
                }
            }

            if (visibleConcepts.isEmpty()) {
                item(key = "mastery_concepts_empty") {
                    EduPanel(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = EduWhite
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EduLilac,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = EduPurple,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (concepts.isEmpty()) "لا توجد قياسات مفاهيم مسجّلة بعد" else "لا توجد مفاهيم ضمن هذا التصنيف",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk
                                )
                                Text(
                                    text = if (concepts.isEmpty()) {
                                        "عند حل اختبارات الدروس ستظهر هنا نسبة إتقان كل مفهوم وفرص التقوية المخصصة لك."
                                    } else {
                                        "اختر تصنيفاً آخر لعرض بقية المفاهيم."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = visibleConcepts,
                    key = { idx, item -> "concept_${idx}_${item.lessonKey.orEmpty()}_${item.conceptTag}" }
                ) { _, concept ->
                    ConceptMasteryRowCard(
                        concept = concept,
                        canPractice = reviewedQuestionsCount > 0,
                        onPracticeConcept = { onLaunchReinforcement(concept.conceptTag) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MasteryHeroMetricTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = EduWhite.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = EduHeroSub
            )
        }
    }
}

@Composable
private fun MasteryMiniStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EduWhite,
        border = BorderStroke(1.dp, EduLine)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = EduInk)
        }
    }
}

@Composable
private fun ConceptMasteryRowCard(
    concept: ConceptMasterySummary,
    canPractice: Boolean,
    onPracticeConcept: () -> Unit
) {
    val needsHelp = concept.status == MasteryStatus.NEEDS_REINFORCEMENT || (concept.attemptsCount > 0 && concept.accuracyPercent < 75)
    val statusColor = when {
        concept.attemptsCount == 0 -> EduMuted
        concept.accuracyPercent >= 80 -> EduGreenDark
        concept.accuracyPercent >= 55 -> EduOrange
        else -> EduRed
    }
    val statusBg = when {
        concept.attemptsCount == 0 -> EduBg
        concept.accuracyPercent >= 80 -> EduMint
        concept.accuracyPercent >= 55 -> EduOrange.copy(alpha = 0.16f)
        else -> EduRed.copy(alpha = 0.14f)
    }

    EduPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (needsHelp) EduOrange.copy(alpha = 0.45f) else EduLine
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = concept.conceptTag,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                val subtitle = listOfNotNull(
                    concept.bookTitle?.takeIf { it.isNotBlank() },
                    concept.lessonTitle?.takeIf { it.isNotBlank() },
                    if (concept.attemptsCount > 0) "${concept.correctCount}/${concept.attemptsCount} محاولة صحيحة" else "${concept.questionsCount} سؤال متاح"
                ).joinToString(" • ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!needsHelp) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = "${concept.accuracyPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { (concept.accuracyPercent / 100f).coerceIn(0f, 1f) },
            color = statusColor,
            trackColor = EduBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        if (needsHelp && canPractice) {
            OutlinedButton(
                onClick = onPracticeConcept,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduPurple),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EduPurple),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تدريب سريع على هذا المفهوم",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
