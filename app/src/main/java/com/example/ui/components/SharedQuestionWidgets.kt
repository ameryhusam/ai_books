package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.QuestionBankEngine
import com.example.model.MasteryStatus
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.model.QuestionType
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
fun QuestionMasteryDiagnosticBanner(
    stats: QuestionMasteryStats?,
    currentAnswer: String = "",
    modifier: Modifier = Modifier
) {
    if (stats == null || stats.attemptsCount == 0) return

    val statusColor = when (stats.status) {
        MasteryStatus.MASTERED -> EduGreenDark
        MasteryStatus.NEEDS_REINFORCEMENT -> EduOrange
        MasteryStatus.UNSEEN -> EduMuted
    }
    val statusBg = when (stats.status) {
        MasteryStatus.MASTERED -> EduMint
        MasteryStatus.NEEDS_REINFORCEMENT -> EduOrange.copy(alpha = 0.14f)
        MasteryStatus.UNSEEN -> EduBg
    }
    val prevAnswer = stats.lastSelectedAnswer.orEmpty()
    val hasCandidate = currentAnswer.trim().isNotEmpty()
    val matchesPrev = hasCandidate && stats.sameAsPrevious(currentAnswer)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = statusBg,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("question_mastery_banner_${stats.questionId}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(shape = RoundedCornerShape(7.dp), color = EduWhite) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (stats.status == MasteryStatus.MASTERED) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (stats.status == MasteryStatus.MASTERED) {
                                "حالة المفهوم: مُتقَن"
                            } else {
                                "حالة المفهوم: يحتاج تقوية"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (stats.conceptTag.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(7.dp), color = EduWhite) {
                        Text(
                            text = stats.conceptTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = EduPurple,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(7.dp), color = EduWhite) {
                    Text(
                        text = "${stats.correctCount} صح • ${stats.wrongCount} خطأ",
                        style = MaterialTheme.typography.labelSmall,
                        color = EduInk,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            if (prevAnswer.isNotBlank()) {
                val prevVerdict = if (stats.lastIsCorrect == true) "صحيحة ✓" else "خاطئة ✕"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = EduInk,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "إجابتك السابقة: «$prevAnswer» ($prevVerdict)",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduInk
                    )
                }
            }

            if (hasCandidate && prevAnswer.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (matchesPrev) EduBlue.copy(alpha = 0.14f) else EduLilac
                ) {
                    Text(
                        text = if (matchesPrev) {
                            "الاختيار الحالي: مطابق لإجابتك السابقة"
                        } else {
                            "الاختيار الحالي: مختلف عن إجابتك السابقة"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (matchesPrev) EduBlue else EduPurple,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionAnswerInputArea(
    question: QuestionItem,
    currentAnswer: String,
    locked: Boolean,
    previousSelectedAnswer: String? = null,
    onAnswerChange: (String) -> Unit
) {
    when {
        question.type == QuestionType.ORDERING -> {
            val baseItems = remember(question.id) {
                val src = question.options.ifEmpty {
                    question.correctAnswer.split("|", "->", "←").map { it.trim() }.filter { it.isNotBlank() }
                }
                if (src.size >= 2) src.reversed() else src
            }
            val currentList = remember(question.id, currentAnswer) {
                if (currentAnswer.isBlank()) baseItems
                else currentAnswer.split("|").map { it.trim() }.filter { it.isNotBlank() }
            }
            LaunchedEffect(question.id) {
                if (currentAnswer.isBlank() && baseItems.isNotEmpty()) {
                    onAnswerChange(baseItems.joinToString(" | "))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("رتّب العناصر بالأعلى والأسفل:", style = MaterialTheme.typography.labelMedium, color = EduMuted)
                currentList.forEachIndexed { idx, item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EduBg,
                        border = BorderStroke(1.dp, EduLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}. $item",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduInk,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                enabled = !locked && idx > 0,
                                onClick = {
                                    val mutable = currentList.toMutableList()
                                    val moved = mutable.removeAt(idx)
                                    mutable.add(idx - 1, moved)
                                    onAnswerChange(mutable.joinToString(" | "))
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "أعلى", tint = EduGreenDark)
                            }
                            IconButton(
                                enabled = !locked && idx < currentList.lastIndex,
                                onClick = {
                                    val mutable = currentList.toMutableList()
                                    val moved = mutable.removeAt(idx)
                                    mutable.add(idx + 1, moved)
                                    onAnswerChange(mutable.joinToString(" | "))
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "أسفل", tint = EduGreenDark)
                            }
                        }
                    }
                }
            }
        }

        question.type == QuestionType.MATCHING -> {
            val pairs = remember(question.id) {
                val src = question.options.ifEmpty { question.correctAnswer.split(";").map { it.trim() } }
                src.mapNotNull { line ->
                    val parts = line.split("=", "->").map { it.trim() }
                    if (parts.size >= 2) parts[0] to parts[1] else null
                }
            }
            val rightPool = remember(question.id) { pairs.map { it.second }.reversed() }
            var selectedLeft by remember(question.id) { mutableIntStateOf(0) }
            val chosen = remember(question.id) { mutableStateMapOf<String, String>() }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("اختر العبارة ثم اختر ما يناسبها:", style = MaterialTheme.typography.labelMedium, color = EduMuted)
                pairs.forEachIndexed { idx, (left, _) ->
                    val pickedRight = chosen[left].orEmpty()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedLeft == idx) EduLilac else EduBg,
                        border = BorderStroke(1.dp, if (selectedLeft == idx) EduPurple else EduLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !locked) { selectedLeft = idx }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = left,
                                style = MaterialTheme.typography.bodySmall,
                                color = EduInk,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (pickedRight.isBlank()) EduWhite else EduMint
                            ) {
                                Text(
                                    text = pickedRight.ifBlank { "اختر مطابقة" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (pickedRight.isBlank()) EduMuted else EduGreenDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rightPool.forEach { r ->
                        val activeLeft = pairs.getOrNull(selectedLeft)?.first.orEmpty()
                        EduPill(
                            text = r,
                            active = activeLeft.isNotBlank() && chosen[activeLeft] == r,
                            onClick = {
                                if (!locked && activeLeft.isNotBlank()) {
                                    chosen[activeLeft] = r
                                    val encoded = pairs.mapNotNull { (l, _) ->
                                        chosen[l]?.let { "$l=$it" }
                                    }.joinToString(";")
                                    onAnswerChange(encoded)
                                }
                            }
                        )
                    }
                }
            }
        }

        question.options.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                question.options.forEach { opt ->
                    val isSelected = currentAnswer == opt
                    val isCorrect = QuestionBankEngine.isAnswerCorrect(question, opt)
                    val wasPreviouslyPicked = !previousSelectedAnswer.isNullOrBlank() &&
                        previousSelectedAnswer.trim().equals(opt.trim(), ignoreCase = true)
                    val bg = when {
                        locked && isCorrect -> EduMint
                        locked && isSelected && !isCorrect -> EduRed.copy(alpha = 0.14f)
                        isSelected -> EduLilac
                        else -> EduBg
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bg,
                        border = BorderStroke(
                            if (wasPreviouslyPicked || isSelected) 1.4.dp else 1.dp,
                            when {
                                locked && isCorrect -> EduGreen
                                isSelected -> EduPurple
                                wasPreviouslyPicked -> EduBlue.copy(alpha = 0.65f)
                                else -> EduLine
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !locked) { onAnswerChange(opt) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduInk,
                                modifier = Modifier.weight(1f)
                            )
                            if (wasPreviouslyPicked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = EduWhite) {
                                    Text(
                                        text = "إجابتك السابقة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EduBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        else -> {
            OutlinedTextField(
                value = currentAnswer,
                onValueChange = { if (!locked) onAnswerChange(it) },
                label = { Text(if (question.type == QuestionType.FILL_BLANK) "أكمل الفراغ" else "اكتب إجابتك") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = EduFieldBg,
                    unfocusedContainerColor = EduFieldBg,
                    focusedBorderColor = EduGreen,
                    unfocusedBorderColor = EduLine
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
