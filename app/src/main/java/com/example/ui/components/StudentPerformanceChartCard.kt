package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConceptMasterySummary
import com.example.model.LearnerProfile
import com.example.model.LearningUnitPath
import com.example.model.MasteryStatus
import com.example.model.UnitCompletionStatus
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentPerformanceChartCard(
    concepts: List<ConceptMasterySummary>,
    unitPaths: List<LearningUnitPath>,
    overallCompletionPercent: Int,
    activeLearner: LearnerProfile,
    availableLearners: List<LearnerProfile>,
    onSelectLearner: (String) -> Unit,
    onAddLearner: (String) -> Unit,
    onSelectConceptForReinforcement: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddLearnerDialog by remember { mutableStateOf(false) }
    var newLearnerName by remember { mutableStateOf("") }

    val masteredCount = remember(concepts) { concepts.count { it.status == MasteryStatus.MASTERED } }
    val weakCount = remember(concepts) { concepts.count { it.status == MasteryStatus.NEEDS_REINFORCEMENT } }
    val unseenCount = remember(concepts) { concepts.count { it.status == MasteryStatus.UNSEEN } }
    val totalAttempts = remember(concepts) { concepts.sumOf { it.attemptsCount } }
    val totalCorrect = remember(concepts) { concepts.sumOf { it.correctCount } }
    val overallAccuracyPercent = if (totalAttempts == 0) 0 else ((totalCorrect * 100f) / totalAttempts).toInt()

    val sortedConcepts = remember(concepts) {
        concepts.sortedWith(
            compareBy<ConceptMasterySummary> {
                when (it.status) {
                    MasteryStatus.NEEDS_REINFORCEMENT -> 0
                    MasteryStatus.UNSEEN -> 1
                    MasteryStatus.MASTERED -> 2
                }
            }.thenBy { it.accuracyPercent }
                .thenByDescending { it.wrongCount }
        )
    }

    EduPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("student_performance_chart_card"),
        containerColor = EduWhite,
        borderColor = EduPurple.copy(alpha = 0.28f),
        contentPadding = PaddingValues(15.dp)
    ) {
        // 1. Header & Active Learner Profile Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = EduLilac,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoGraph,
                            contentDescription = null,
                            tint = EduPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "تحليل الأداء وإتقان المفاهيم",
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "متابعة التقدّم والمفاهيم المتعثرة حسب سجل المتعلّم",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                }
            }
            OutlinedButton(
                onClick = { showAddLearnerDialog = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduPurple.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_learner_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "إضافة متعلّم جديد",
                    tint = EduPurple,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "متعلّم",
                    style = MaterialTheme.typography.labelSmall,
                    color = EduPurple,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableLearners.forEach { profile ->
                val isSelected = profile.userId == activeLearner.userId
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) EduPurple else EduFieldBg,
                    border = BorderStroke(1.dp, if (isSelected) EduPurple else EduLine),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectLearner(profile.userId) }
                        .testTag("learner_profile_chip_${profile.userId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = if (isSelected) EduWhite else EduMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) EduWhite else EduInk,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Donut Chart + Concept Struggle Bar Chart (responsive grid)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 640.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MasteryDistributionPanel(
                        masteredCount = masteredCount,
                        weakCount = weakCount,
                        unseenCount = unseenCount,
                        overallAccuracyPercent = overallAccuracyPercent,
                        totalAttempts = totalAttempts,
                        overallCompletionPercent = overallCompletionPercent,
                        modifier = Modifier.weight(0.42f)
                    )
                    ConceptStruggleBarsPanel(
                        concepts = sortedConcepts,
                        onSelectConceptForReinforcement = onSelectConceptForReinforcement,
                        modifier = Modifier.weight(0.58f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MasteryDistributionPanel(
                        masteredCount = masteredCount,
                        weakCount = weakCount,
                        unseenCount = unseenCount,
                        overallAccuracyPercent = overallAccuracyPercent,
                        totalAttempts = totalAttempts,
                        overallCompletionPercent = overallCompletionPercent,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ConceptStruggleBarsPanel(
                        concepts = sortedConcepts,
                        onSelectConceptForReinforcement = onSelectConceptForReinforcement,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Unit Completion Horizontal Progress Bars
        if (unitPaths.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EduBg,
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "تقدّم الوحدات التعليمية (${unitPaths.count { it.status == UnitCompletionStatus.COMPLETED }}/${unitPaths.size} وحدة مكتملة)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                    unitPaths.take(6).forEach { unit ->
                        UnitProgressBarRow(unit = unit)
                    }
                }
            }
        }
    }

    if (showAddLearnerDialog) {
        AlertDialog(
            onDismissRequest = { showAddLearnerDialog = false },
            containerColor = EduWhite,
            title = {
                Text(
                    text = "إضافة ملف متعلّم جديد",
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "يُحفظ سجل الإجابات وإتقان المفاهيم بشكل مستقل لكل متعلّم.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                    OutlinedTextField(
                        value = newLearnerName,
                        onValueChange = { newLearnerName = it },
                        label = { Text("اسم المتعلّم") },
                        placeholder = { Text("مثال: سارة أحمد") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduPurple,
                            unfocusedBorderColor = EduLine
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_learner_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newLearnerName.trim().isNotEmpty(),
                    onClick = {
                        onAddLearner(newLearnerName.trim())
                        newLearnerName = ""
                        showAddLearnerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EduPurple, contentColor = EduWhite),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_add_learner_button")
                ) {
                    Text("إضافة وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLearnerDialog = false }) {
                    Text("إلغاء", color = EduMuted)
                }
            }
        )
    }
}

@Composable
private fun MasteryDistributionPanel(
    masteredCount: Int,
    weakCount: Int,
    unseenCount: Int,
    overallAccuracyPercent: Int,
    totalAttempts: Int,
    overallCompletionPercent: Int,
    modifier: Modifier = Modifier
) {
    val totalConcepts = (masteredCount + weakCount + unseenCount).coerceAtLeast(1)
    val masteredFraction by animateFloatAsState(
        targetValue = masteredCount.toFloat() / totalConcepts,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "mastered_arc"
    )
    val weakFraction by animateFloatAsState(
        targetValue = weakCount.toFloat() / totalConcepts,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "weak_arc"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EduBg,
        border = BorderStroke(1.dp, EduLine),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "توزيع إتقان المفاهيم",
                style = MaterialTheme.typography.labelLarge,
                color = EduInk,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .semantics {
                            contentDescription = "مخطط دقة الإجابات $overallAccuracyPercent بالمئة"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 13.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val startOffset = Offset(inset, inset)

                        // Track for unseen / empty concepts
                        drawArc(
                            color = EduLine,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = startOffset,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )

                        if (masteredCount + weakCount + unseenCount > 0) {
                            val masteredSweep = 360f * masteredFraction
                            val weakSweep = 360f * weakFraction

                            if (masteredSweep > 0.5f) {
                                drawArc(
                                    color = EduGreen,
                                    startAngle = -90f,
                                    sweepAngle = masteredSweep,
                                    useCenter = false,
                                    topLeft = startOffset,
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                            }
                            if (weakSweep > 0.5f) {
                                drawArc(
                                    color = EduOrange,
                                    startAngle = -90f + masteredSweep,
                                    sweepAngle = weakSweep,
                                    useCenter = false,
                                    topLeft = startOffset,
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalAttempts > 0) "$overallAccuracyPercent%" else "${overallCompletionPercent}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = EduInk
                        )
                        Text(
                            text = if (totalAttempts > 0) "دقة الاختبار" else "الإنجاز",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduMuted
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChartLegendRow(
                        color = EduGreen,
                        label = "مفهوم مُتقَن",
                        count = masteredCount
                    )
                    ChartLegendRow(
                        color = EduOrange,
                        label = "يحتاج تقوية",
                        count = weakCount
                    )
                    ChartLegendRow(
                        color = EduLine,
                        label = "لم يُختبر بعد",
                        count = unseenCount
                    )
                    Text(
                        text = "إجمالي المحاولات: $totalAttempts",
                        style = MaterialTheme.typography.labelSmall,
                        color = EduMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendRow(
    color: Color,
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = color)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = EduInk
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = EduInk
        )
    }
}

@Composable
private fun ConceptStruggleBarsPanel(
    concepts: List<ConceptMasterySummary>,
    onSelectConceptForReinforcement: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EduBg,
        border = BorderStroke(1.dp, EduLine),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "مؤشر المفاهيم ونقاط التعثّر",
                    style = MaterialTheme.typography.labelLarge,
                    color = EduInk,
                    fontWeight = FontWeight.Bold
                )
                if (concepts.any { it.status == MasteryStatus.NEEDS_REINFORCEMENT }) {
                    Text(
                        text = "اضغط للتقوية",
                        style = MaterialTheme.typography.labelSmall,
                        color = EduPurple
                    )
                }
            }

            if (concepts.isEmpty()) {
                Text(
                    text = "لا توجد أسئلة مصنّفة بمفاهيم بعد. أنشئ أسئلة أو أجب عن اختبارات الدروس ليظهر الرسم البياني.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            } else {
                val visibleConcepts = concepts.take(5)
                visibleConcepts.forEach { concept ->
                    ConceptBarRow(
                        concept = concept,
                        onClick = { onSelectConceptForReinforcement(concept.conceptTag) }
                    )
                }
                val weakTotal = concepts.count { it.status == MasteryStatus.NEEDS_REINFORCEMENT }
                if (weakTotal > 0) {
                    Button(
                        onClick = { onSelectConceptForReinforcement(null) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EduOrange, contentColor = EduWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("chart_reinforce_all_weak_button")
                    ) {
                        Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تدريب مركّز على المفاهيم الضعيفة ($weakTotal)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptBarRow(
    concept: ConceptMasterySummary,
    onClick: () -> Unit
) {
    val barColor = when (concept.status) {
        MasteryStatus.MASTERED -> EduGreen
        MasteryStatus.NEEDS_REINFORCEMENT -> if (concept.accuracyPercent < 50) EduRed else EduOrange
        MasteryStatus.UNSEEN -> EduBlue.copy(alpha = 0.45f)
    }
    val accuracyFraction = if (concept.attemptsCount == 0) 0f else (concept.accuracyPercent / 100f).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EduWhite,
        border = BorderStroke(1.dp, EduLine),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("concept_chart_bar_${concept.conceptTag}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = when (concept.status) {
                        MasteryStatus.MASTERED -> Icons.Rounded.CheckCircle
                        MasteryStatus.NEEDS_REINFORCEMENT -> Icons.Rounded.WarningAmber
                        MasteryStatus.UNSEEN -> Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = concept.conceptTag,
                    style = MaterialTheme.typography.labelMedium,
                    color = EduInk,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (concept.attemptsCount > 0) {
                        "${concept.accuracyPercent}% (${concept.correctCount}/${concept.attemptsCount})"
                    } else {
                        "${concept.questionsCount} س"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                drawRoundRect(
                    color = EduLine,
                    cornerRadius = CornerRadius(10f, 10f),
                    size = size
                )
                if (concept.attemptsCount > 0) {
                    val wrongWidth = size.width * ((1f - accuracyFraction).coerceIn(0f, 1f))
                    val rightWidth = size.width * accuracyFraction
                    if (wrongWidth > 0f && concept.status == MasteryStatus.NEEDS_REINFORCEMENT) {
                        drawRoundRect(
                            color = EduRed.copy(alpha = 0.22f),
                            cornerRadius = CornerRadius(10f, 10f),
                            size = size
                        )
                    }
                    if (rightWidth > 0f) {
                        drawRoundRect(
                            color = barColor,
                            cornerRadius = CornerRadius(10f, 10f),
                            size = Size(width = rightWidth, height = size.height)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitProgressBarRow(unit: LearningUnitPath) {
    val ratio = (unit.completionPercent / 100f).coerceIn(0f, 1f)
    val barColor = when (unit.status) {
        UnitCompletionStatus.COMPLETED -> EduGreen
        UnitCompletionStatus.IN_PROGRESS -> EduOrange
        UnitCompletionStatus.NOT_STARTED -> EduBlue
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${unit.bookTitle} • ${unit.unitTitle}",
                style = MaterialTheme.typography.labelSmall,
                color = EduInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${unit.completedCount}/${unit.totalCount} (${unit.completionPercent}%)",
                style = MaterialTheme.typography.labelSmall,
                color = if (unit.status == UnitCompletionStatus.COMPLETED) EduGreenDark else EduMuted,
                fontWeight = FontWeight.Bold
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
        ) {
            drawRoundRect(
                color = EduLine,
                cornerRadius = CornerRadius(8f, 8f),
                size = size
            )
            if (ratio > 0f) {
                drawRoundRect(
                    color = barColor,
                    cornerRadius = CornerRadius(8f, 8f),
                    size = Size(width = size.width * ratio, height = size.height)
                )
            }
        }
    }
}
