package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.QuestionBankEngine
import com.example.model.LessonRef
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.RadialProgressIndicator
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionBankScreen(
    questions: List<QuestionItem>,
    selectedLesson: LessonRef?,
    hasWorkspace: Boolean,
    onAddManualQuestion: (QuestionType, String, List<String>, String, String, QuestionDifficulty) -> Unit,
    onToggleReviewed: (String, Boolean) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onQuizCompleted: (Int, Int, Int) -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<QuestionType?>(null) }
    var selectedDifficulty by remember { mutableStateOf<QuestionDifficulty?>(null) }
    var onlyCurrentLesson by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQuizDialog by remember { mutableStateOf(false) }
    var showFlashcardsDialog by remember { mutableStateOf(false) }

    val filtered = remember(questions, searchQuery, selectedType, selectedDifficulty, onlyCurrentLesson, selectedLesson) {
        val q = searchQuery.trim()
        questions.filter { item ->
            val matchesLesson = !onlyCurrentLesson || selectedLesson == null ||
                (item.bookKey == selectedLesson.bookKey && item.unitKey == selectedLesson.unitKey && item.lessonKey == selectedLesson.lessonKey)
            val matchesType = selectedType == null || item.type == selectedType
            val matchesDiff = selectedDifficulty == null || item.difficulty == selectedDifficulty
            val matchesSearch = q.isEmpty() ||
                item.prompt.contains(q, ignoreCase = true) ||
                item.lessonTitle.contains(q, ignoreCase = true) ||
                item.rationale.contains(q, ignoreCase = true)
            matchesLesson && matchesType && matchesDiff && matchesSearch
        }
    }

    val reviewedCount = remember(questions) { questions.count { it.reviewed } }
    val duplicatesCount = remember(questions) { questions.count { it.isDuplicate } }
    val playableQuestions = remember(filtered) {
        filtered.filter { it.options.isNotEmpty() || it.correctAnswer.isNotBlank() }
    }

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
            item(key = "qb_hero") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = EduGreen
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "QUESTION BANK & ASSESSMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduHeroKicker
                        )
                        Text(
                            text = stringResource(R.string.quiz_bank_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = EduWhite
                        )
                        Text(
                            text = "${questions.size} سؤال • $reviewedCount معتمد للمراجعة • $duplicatesCount تكرار محتمل",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduHeroSub
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showQuizDialog = true },
                                enabled = playableQuestions.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EduWhite,
                                    contentColor = EduGreenDark
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_interactive_quiz_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اختبار تفاعلي", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showFlashcardsDialog = true },
                                enabled = playableQuestions.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_flashcards_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Style,
                                    contentDescription = null,
                                    tint = EduWhite,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("بطاقات مراجعة", color = EduWhite, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (hasWorkspace && selectedLesson != null) {
                            EduYellowButton(
                                text = "إضافة سؤال يدوي للدرس الحالي",
                                onClick = { showAddDialog = true },
                                icon = Icons.Rounded.Add,
                                testTag = "add_manual_question_button"
                            )
                        }
                    }
                }
            }

            item(key = "qb_filters") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = EduMuted)
                        },
                        placeholder = {
                            Text("ابحث في الأسئلة أو الدروس...", style = MaterialTheme.typography.bodyMedium, color = EduMuted)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine,
                            focusedTextColor = EduInk,
                            unfocusedTextColor = EduInk
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("question_search_input")
                    )

                    if (selectedLesson != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            EduPill(
                                text = "كل الدروس (${questions.size})",
                                active = !onlyCurrentLesson,
                                onClick = { onlyCurrentLesson = false }
                            )
                            EduPill(
                                text = "الدرس: ${selectedLesson.title}",
                                active = onlyCurrentLesson,
                                onClick = { onlyCurrentLesson = true }
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        EduPill(
                            text = "كل الأنواع",
                            active = selectedType == null,
                            onClick = { selectedType = null }
                        )
                        QuestionType.entries.forEach { t ->
                            EduPill(
                                text = t.labelAr,
                                active = selectedType == t,
                                onClick = { selectedType = t }
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        EduPill(
                            text = "كل المستويات",
                            active = selectedDifficulty == null,
                            onClick = { selectedDifficulty = null }
                        )
                        QuestionDifficulty.entries.forEach { d ->
                            EduPill(
                                text = d.labelAr,
                                active = selectedDifficulty == d,
                                onClick = { selectedDifficulty = d }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(key = "qb_empty") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Quiz,
                                contentDescription = null,
                                tint = EduGreenDark,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "لا توجد أسئلة محفوظة حتى الآن",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk
                                )
                                Text(
                                    text = "ولّد أسئلة من Studio ثم اضغط «حفظ في بنك الأسئلة»، أو أضف سؤالاً يدوياً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                        }
                        if (selectedLesson != null) {
                            OutlinedButton(
                                onClick = onOpenStudio,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EduGreen),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("الذهاب إلى Studio للتوليد", color = EduGreenDark)
                            }
                        }
                    }
                }
            } else {
                items(
                    items = filtered,
                    key = { it.id }
                ) { item ->
                    QuestionBankCard(
                        item = item,
                        onToggleReviewed = { onToggleReviewed(item.id, item.reviewed) },
                        onDelete = { onDeleteQuestion(item.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddManualQuestionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { type, prompt, options, correctAnswer, rationale, diff ->
                onAddManualQuestion(type, prompt, options, correctAnswer, rationale, diff)
                showAddDialog = false
            }
        )
    }

    if (showQuizDialog && playableQuestions.isNotEmpty()) {
        InteractiveQuizDialog(
            questions = playableQuestions.take(15),
            onDismiss = { showQuizDialog = false },
            onFinished = { correct, total, xp ->
                onQuizCompleted(correct, total, xp)
            }
        )
    }

    if (showFlashcardsDialog && playableQuestions.isNotEmpty()) {
        FlashcardsDialog(
            questions = playableQuestions.take(20),
            onDismiss = { showFlashcardsDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionBankCard(
    item: QuestionItem,
    onToggleReviewed: () -> Unit,
    onDelete: () -> Unit
) {
    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("question_card_${item.id}"),
        borderColor = if (item.reviewed) EduGreen else EduLine,
        containerColor = if (item.reviewed) EduMint.copy(alpha = 0.25f) else EduWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = EduLilac) {
                    Text(
                        text = item.type.labelAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = EduPurple,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(8.dp), color = EduBg) {
                    Text(
                        text = item.difficulty.labelAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = EduBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (item.isDuplicate) {
                    Surface(shape = RoundedCornerShape(8.dp), color = EduOrange.copy(alpha = 0.16f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = EduOrange,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("تكرار محتمل", style = MaterialTheme.typography.labelSmall, color = EduOrange)
                        }
                    }
                }
            }
            Row {
                IconButton(
                    onClick = onToggleReviewed,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = if (item.reviewed) "إلغاء اعتماد المراجعة" else "اعتماد المراجعة",
                        tint = if (item.reviewed) EduGreenDark else EduMuted
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "حذف السؤال",
                        tint = EduRed
                    )
                }
            }
        }

        Text(
            text = item.prompt,
            style = MaterialTheme.typography.titleSmall,
            color = EduInk
        )

        if (item.options.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.options.forEach { opt ->
                    val isCorrect = opt.equals(item.correctAnswer, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCorrect) EduMint else EduBg.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, if (isCorrect) EduGreen else EduLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isCorrect) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "إجابة صحيحة",
                                    tint = EduGreenDark,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.bodySmall,
                                color = EduInk
                            )
                        }
                    }
                }
            }
        } else if (item.correctAnswer.isNotBlank()) {
            Text(
                text = "الإجابة النموذجية: ${item.correctAnswer}",
                style = MaterialTheme.typography.bodySmall,
                color = EduGreenDark,
                fontWeight = FontWeight.Bold
            )
        }

        if (item.rationale.isNotBlank()) {
            Text(
                text = "التفسير: ${item.rationale}",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        }

        val meta = buildString {
            append(item.lessonTitle)
            if (!item.sourcePages.isNullOrBlank()) append(" • ص ${item.sourcePages}")
            if (item.reviewed) append(" • مُراجَع ✓")
        }
        Text(text = meta, style = MaterialTheme.typography.labelSmall, color = EduMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddManualQuestionDialog(
    onDismiss: () -> Unit,
    onSave: (QuestionType, String, List<String>, String, String, QuestionDifficulty) -> Unit
) {
    var type by remember { mutableStateOf(QuestionType.MCQ) }
    var difficulty by remember { mutableStateOf(QuestionDifficulty.MEDIUM) }
    var prompt by remember { mutableStateOf("") }
    var optionsRaw by remember { mutableStateOf("") }
    var correctAnswer by remember { mutableStateOf("") }
    var rationale by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text("إضافة سؤال جديد", style = MaterialTheme.typography.titleMedium, color = EduInk)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuestionType.entries.forEach { t ->
                        EduPill(text = t.labelAr, active = type == t, onClick = {
                            type = t
                            if (t == QuestionType.TRUE_FALSE && correctAnswer.isBlank()) {
                                correctAnswer = "صح"
                            }
                        })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuestionDifficulty.entries.forEach { d ->
                        EduPill(text = d.labelAr, active = difficulty == d, onClick = { difficulty = d })
                    }
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("نص السؤال") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == QuestionType.MCQ) {
                    OutlinedTextField(
                        value = optionsRaw,
                        onValueChange = { optionsRaw = it },
                        label = { Text("الخيارات (سطر لكل خيار)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = correctAnswer,
                    onValueChange = { correctAnswer = it },
                    label = { Text(if (type == QuestionType.TRUE_FALSE) "الإجابة (صح أو خطأ)" else "الإجابة الصحيحة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rationale,
                    onValueChange = { rationale = it },
                    label = { Text("التفسير (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = prompt.isNotBlank() && correctAnswer.isNotBlank(),
                onClick = {
                    val opts = when (type) {
                        QuestionType.TRUE_FALSE -> listOf("صح", "خطأ")
                        QuestionType.MCQ -> optionsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
                        QuestionType.SHORT_ANSWER -> emptyList()
                    }
                    onSave(type, prompt, opts, correctAnswer, rationale, difficulty)
                }
            ) {
                Text("حفظ", color = EduGreenDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = EduMuted)
            }
        }
    )
}

@Composable
private fun InteractiveQuizDialog(
    questions: List<QuestionItem>,
    onDismiss: () -> Unit,
    onFinished: (correct: Int, total: Int, xp: Int) -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var checkedCurrent by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val current = questions.getOrNull(index)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = if (finished) "نتيجة الاختبار التفاعلي" else "سؤال ${index + 1} من ${questions.size}",
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
                        label = "النتيجة"
                    )
                    Text(
                        text = "أجبت بشكل صحيح على ${summary.correctCount} من ${summary.totalQuestions} أسئلة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EduInk
                    )
                    Surface(shape = RoundedCornerShape(12.dp), color = EduMint) {
                        Text(
                            text = "+${summary.earnedXp} XP مكتسبة في رصيد التعلم",
                            style = MaterialTheme.typography.labelLarge,
                            color = EduGreenDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            } else if (current != null) {
                val picked = answers[current.id].orEmpty()
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (index + 1).toFloat() / questions.size.coerceAtLeast(1) },
                        color = EduGreen,
                        trackColor = EduBg,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(text = current.prompt, style = MaterialTheme.typography.titleSmall, color = EduInk)

                    if (current.options.isNotEmpty()) {
                        current.options.forEach { opt ->
                            val isSelected = picked == opt
                            val isCorrect = opt.equals(current.correctAnswer, ignoreCase = true)
                            val bg = when {
                                checkedCurrent && isCorrect -> EduMint
                                checkedCurrent && isSelected && !isCorrect -> EduRed.copy(alpha = 0.14f)
                                isSelected -> EduLilac
                                else -> EduBg
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bg,
                                border = BorderStroke(
                                    1.dp,
                                    if (checkedCurrent && isCorrect) EduGreen else if (isSelected) EduPurple else EduLine
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .minimumInteractiveComponentSize()
                                    .clickable(enabled = !checkedCurrent) {
                                        answers[current.id] = opt
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
                    } else {
                        OutlinedTextField(
                            value = picked,
                            onValueChange = { if (!checkedCurrent) answers[current.id] = it },
                            label = { Text("اكتب إجابتك") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (checkedCurrent) {
                        Surface(shape = RoundedCornerShape(10.dp), color = EduMint.copy(alpha = 0.5f)) {
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
            if (finished) {
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = EduGreenDark, fontWeight = FontWeight.Bold)
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
                        val summary = QuestionBankEngine.gradeQuiz(questions, answers)
                        finished = true
                        onFinished(summary.correctCount, summary.totalQuestions, summary.earnedXp)
                    }
                }) {
                    Text(
                        text = if (index < questions.lastIndex) "السؤال التالي" else "إنهاء الاختبار",
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

@Composable
private fun FlashcardsDialog(
    questions: List<QuestionItem>,
    onDismiss: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val current = questions.getOrNull(index)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = "بطاقة مراجعة ${index + 1} / ${questions.size}",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk
            )
        },
        text = {
            if (current != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (flipped) EduMint.copy(alpha = 0.5f) else EduLilac.copy(alpha = 0.55f),
                    border = BorderStroke(1.2.dp, if (flipped) EduGreen else EduPurple),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { flipped = !flipped }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (flipped) "الإجابة والشرح" else "السؤال (اضغط لقلب البطاقة)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (flipped) EduGreenDark else EduPurple
                        )
                        Text(
                            text = if (flipped) current.correctAnswer else current.prompt,
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        if (flipped && current.rationale.isNotBlank()) {
                            Text(
                                text = current.rationale,
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (index > 0) {
                    TextButton(onClick = {
                        index--
                        flipped = false
                    }) {
                        Text("السابق", color = EduMuted)
                    }
                }
                TextButton(onClick = {
                    if (index < questions.lastIndex) {
                        index++
                        flipped = false
                    } else {
                        onDismiss()
                    }
                }) {
                    Text(
                        text = if (index < questions.lastIndex) "التالي" else "تم",
                        color = EduGreenDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = EduMuted)
            }
        }
    )
}
