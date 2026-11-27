package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.QuestionBankEngine
import com.example.model.InteractiveActivityModel
import com.example.model.InteractiveStepItem
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@Composable
fun InteractiveJsonActivityDialog(
    activity: InteractiveActivityModel,
    onCompleted: (earnedXp: Int) -> Unit,
    onDismiss: () -> Unit,
    onCheckpointAnswered: ((step: InteractiveStepItem, selectedOption: String, isCorrect: Boolean) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = EduGreen, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activity.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite
                            )
                            if (activity.objective.isNotBlank()) {
                                Text(
                                    text = activity.objective,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduWhite.copy(alpha = 0.9f)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_interactive_json_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "إغلاق النشاط",
                                tint = EduWhite
                            )
                        }
                    }
                }
                InteractiveJsonActivityPlayer(
                    activity = activity,
                    onCompleted = onCompleted,
                    onCheckpointAnswered = onCheckpointAnswered,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveJsonActivityPlayer(
    activity: InteractiveActivityModel,
    onCompleted: (earnedXp: Int) -> Unit,
    modifier: Modifier = Modifier,
    onCheckpointAnswered: ((step: InteractiveStepItem, selectedOption: String, isCorrect: Boolean) -> Unit)? = null
) {
    val hasOrdering = activity.orderingItems.isNotEmpty()
    val hasMatching = activity.matchingPairs.isNotEmpty()
    val totalStations = activity.steps.size + (if (hasOrdering) 1 else 0) + (if (hasMatching) 1 else 0)
    var currentStation by remember(activity) { mutableIntStateOf(0) }
    var completed by remember(activity) { mutableStateOf(false) }

    val checkpointSelections = remember(activity) { mutableStateMapOf<Int, String>() }
    val checkpointVerified = remember(activity) { mutableStateMapOf<Int, Boolean>() }

    val orderingState = remember(activity) {
        mutableStateListOf<String>().apply {
            val items = activity.orderingItems
            if (items.size >= 2) {
                addAll(items.reversed())
            } else {
                addAll(items)
            }
        }
    }
    var orderingChecked by remember(activity) { mutableStateOf(false) }

    val shuffledRights = remember(activity) {
        activity.matchingPairs.map { it.right }.reversed()
    }
    var activeLeftIndex by remember(activity) { mutableIntStateOf(0) }
    val matchSelections = remember(activity) { mutableStateMapOf<Int, String>() }
    var matchingChecked by remember(activity) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (completed || totalStations == 0) {
            EduPanel(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EduGreen,
                containerColor = EduMint.copy(alpha = 0.35f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = EduGreenDark,
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text(
                            text = "أحسنت! أتممت جميع محطات النشاط التفاعلي",
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        Text(
                            text = "+35 XP مكتسبة • تم تسجيل التفاعل في سجل الدراسة",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduGreenDark
                        )
                    }
                }
            }
            return@Column
        }

        LinearProgressIndicator(
            progress = { (currentStation + 1).toFloat() / totalStations.coerceAtLeast(1) },
            color = EduGreen,
            trackColor = EduLine,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "المحطة ${currentStation + 1} من $totalStations",
            style = MaterialTheme.typography.labelMedium,
            color = EduMuted
        )

        val stepsCount = activity.steps.size
        val orderingStationIdx = if (hasOrdering) stepsCount else -1
        val matchingStationIdx = if (hasMatching) stepsCount + (if (hasOrdering) 1 else 0) else -1

        when {
            currentStation < stepsCount -> {
                val step = activity.steps[currentStation]
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    if (step.body.isNotBlank()) {
                        Text(
                            text = step.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduInk
                        )
                    }
                    if (step.checkpointQuestion.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EduLilac.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (step.conceptTag.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EduWhite
                                    ) {
                                        Text(
                                            text = "المفهوم: ${step.conceptTag}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EduPurple,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "نقطة تحقق سريعة: ${step.checkpointQuestion}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EduPurple
                                )
                                val picked = checkpointSelections[currentStation].orEmpty()
                                val verified = checkpointVerified[currentStation] == true
                                step.checkpointOptions.forEach { opt ->
                                    val selected = picked == opt
                                    val isRight = QuestionBankEngine.normalizeForFingerprint(opt) ==
                                        QuestionBankEngine.normalizeForFingerprint(step.checkpointAnswer)
                                    val bg = when {
                                        verified && isRight -> EduMint
                                        verified && selected && !isRight -> EduRed.copy(alpha = 0.16f)
                                        selected -> EduWhite
                                        else -> EduBg
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = bg,
                                        border = BorderStroke(
                                            1.dp,
                                            if (verified && isRight) EduGreen else if (selected) EduPurple else EduLine
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .minimumInteractiveComponentSize()
                                            .clickable(enabled = !verified) {
                                                checkpointSelections[currentStation] = opt
                                            }
                                    ) {
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduInk,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                                if (!verified && step.checkpointOptions.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            checkpointVerified[currentStation] = true
                                            val rightNow = QuestionBankEngine.normalizeForFingerprint(picked) ==
                                                QuestionBankEngine.normalizeForFingerprint(step.checkpointAnswer)
                                            onCheckpointAnswered?.invoke(step, picked, rightNow)
                                        },
                                        enabled = picked.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, EduPurple),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("تحقق", color = EduPurple)
                                    }
                                }
                                if (verified && step.checkpointAnswer.isNotBlank()) {
                                    Text(
                                        text = "الإجابة الصحيحة: ${step.checkpointAnswer}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduGreenDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            currentStation == orderingStationIdx -> {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "تحدي الترتيب التسلسلي",
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    Text(
                        text = "رتّب العناصر التالية بالترتيب الصحيح باستخدام الأسهم:",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                    orderingState.forEachIndexed { idx, item ->
                        val isInExpectedSlot = activity.orderingItems.getOrNull(idx) == item
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (orderingChecked && isInExpectedSlot) EduMint else EduBg,
                            border = BorderStroke(1.dp, if (orderingChecked && isInExpectedSlot) EduGreen else EduLine),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}. $item",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EduInk,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (idx > 0 && !orderingChecked) {
                                            val moved = orderingState.removeAt(idx)
                                            orderingState.add(idx - 1, moved)
                                        }
                                    },
                                    enabled = idx > 0 && !orderingChecked,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "أعلى", tint = EduGreenDark)
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < orderingState.lastIndex && !orderingChecked) {
                                            val moved = orderingState.removeAt(idx)
                                            orderingState.add(idx + 1, moved)
                                        }
                                    },
                                    enabled = idx < orderingState.lastIndex && !orderingChecked,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "أسفل", tint = EduGreenDark)
                                }
                            }
                        }
                    }
                    if (!orderingChecked) {
                        OutlinedButton(
                            onClick = { orderingChecked = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EduGreen),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("تحقق من الترتيب", color = EduGreenDark)
                        }
                    } else {
                        val allRight = orderingState.toList() == activity.orderingItems
                        Text(
                            text = if (allRight) "ترتيب صحيح بالكامل!" else "الترتيب الصحيح: ${activity.orderingItems.joinToString(" ← ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allRight) EduGreenDark else EduBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            currentStation == matchingStationIdx -> {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "تحدي التوصيل بين المفهوم ومعناه",
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    Text(
                        text = "اختر المفهوم ثم اضغط التعريف المقابل له:",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                    activity.matchingPairs.forEachIndexed { idx, pair ->
                        val chosen = matchSelections[idx].orEmpty()
                        val isActive = activeLeftIndex == idx
                        val isMatchRight = matchingChecked && chosen == pair.right
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isMatchRight -> EduMint
                                isActive -> EduLilac.copy(alpha = 0.5f)
                                else -> EduBg
                            },
                            border = BorderStroke(1.dp, if (isActive) EduPurple else EduLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .minimumInteractiveComponentSize()
                                .clickable(enabled = !matchingChecked) { activeLeftIndex = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pair.left, style = MaterialTheme.typography.bodyMedium, color = EduInk)
                                Text(
                                    text = chosen.ifBlank { "اختر..." },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (chosen.isBlank()) EduMuted else EduGreenDark
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        shuffledRights.forEach { rightText ->
                            EduPill(
                                text = rightText,
                                active = matchSelections[activeLeftIndex] == rightText,
                                onClick = {
                                    if (!matchingChecked) {
                                        matchSelections[activeLeftIndex] = rightText
                                    }
                                }
                            )
                        }
                    }
                    if (!matchingChecked) {
                        OutlinedButton(
                            onClick = { matchingChecked = true },
                            enabled = matchSelections.size == activity.matchingPairs.size,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EduGreen),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("تحقق من التوصيل", color = EduGreenDark)
                        }
                    } else {
                        val correctPairs = activity.matchingPairs.indices.count { i ->
                            matchSelections[i] == activity.matchingPairs[i].right
                        }
                        Text(
                            text = "طابقت $correctPairs من ${activity.matchingPairs.size} بنجاح.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentStation > 0) {
                OutlinedButton(
                    onClick = { currentStation-- },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("المحطة السابقة", color = EduInk)
                }
            }
            Button(
                onClick = {
                    if (currentStation < totalStations - 1) {
                        currentStation++
                    } else {
                        completed = true
                        onCompleted(35)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("interactive_next_station_button")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentStation < totalStations - 1) "التالي" else "إنهاء النشاط (+35 XP)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
