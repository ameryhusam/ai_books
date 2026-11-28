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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.ConceptMasteryEngine
import com.example.data.repository.QuestionBankEngine
import com.example.model.AssessmentAttemptRecord
import com.example.model.AssessmentKind
import com.example.model.AssessmentRecord
import com.example.model.LessonRef
import com.example.model.MasteryStatus
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionSourceType
import com.example.model.QuestionMasteryStats
import com.example.model.QuestionType
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.QuestionAnswerInputArea
import com.example.ui.components.QuestionMasteryDiagnosticBanner
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionBankScreen(
    questions: List<QuestionItem>,
    assessments: List<AssessmentRecord> = emptyList(),
    attempts: List<AssessmentAttemptRecord> = emptyList(),
    questionStats: Map<String, QuestionMasteryStats> = emptyMap(),
    selectedLesson: LessonRef?,
    hasWorkspace: Boolean,
    onAddManualQuestion: (QuestionType, String, List<String>, String, String, QuestionDifficulty, String) -> Unit,
    onUpdateQuestion: (String, QuestionType, String, List<String>, String, String, QuestionDifficulty, String, Boolean) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onToggleReviewed: (String, Boolean) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onQuizCompleted: (Int, Int, Int) -> Unit,
    onRecordQuestionAttempts: (List<QuestionItem>, Map<String, String>, String) -> Unit = { _, _, _ -> },
    onCreateAssessment: (String, AssessmentKind, List<String>, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onDeleteAssessment: (String) -> Unit = {},
    onRecordAssessmentAttempt: (AssessmentRecord, Int, Int, Int, Int, Int) -> Unit = { _, _, _, _, _, _ -> },
    onExportAssessmentMarkdown: (AssessmentRecord) -> Unit = {},
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: Questions, 1: Assessments
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<QuestionType?>(null) }
    var selectedDifficulty by remember { mutableStateOf<QuestionDifficulty?>(null) }
    var onlyCurrentLesson by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<QuestionItem?>(null) }
    var showQuizDialog by remember { mutableStateOf(false) }
    var showFlashcardsDialog by remember { mutableStateOf(false) }
    var showCreateAssessmentDialog by remember { mutableStateOf(false) }
    var activeTimedAssessment by remember { mutableStateOf<AssessmentRecord?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val filtered = remember(questions, searchQuery, selectedType, selectedDifficulty, onlyCurrentLesson, selectedLesson) {
        val q = searchQuery.trim()
        questions.filter { item ->
            if (item.sourceType == QuestionSourceType.INTERACTIVE_CHECKPOINT || item.id.startsWith("q-int-")) return@filter false
            val matchesLesson = !onlyCurrentLesson || selectedLesson == null ||
                (item.bookKey == selectedLesson.bookKey && item.unitKey == selectedLesson.unitKey && item.lessonKey == selectedLesson.lessonKey)
            val matchesType = selectedType == null || item.type == selectedType
            val matchesDiff = selectedDifficulty == null || item.difficulty == selectedDifficulty
            val matchesSearch = q.isEmpty() ||
                item.prompt.contains(q, ignoreCase = true) ||
                item.lessonTitle.contains(q, ignoreCase = true) ||
                item.conceptTag.contains(q, ignoreCase = true) ||
                item.rationale.contains(q, ignoreCase = true)
            matchesLesson && matchesType && matchesDiff && matchesSearch
        }
    }

    val reviewedCount = remember(questions) { questions.count { it.reviewed } }
    val duplicatesCount = remember(questions) { questions.count { it.isDuplicate } }
    val playableQuestions = remember(filtered) {
        filtered.filter { it.reviewed && (it.options.isNotEmpty() || it.correctAnswer.isNotBlank()) }
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
                            text = "${questions.size} سؤال • $reviewedCount معتمد • ${assessments.size} تقييم مؤقت • $duplicatesCount تكرار",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduHeroSub
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EduPill(
                                text = "بنك الأسئلة (${questions.size})",
                                active = activeTab == 0,
                                onClick = { activeTab = 0 },
                                testTag = "qb_tab_questions"
                            )
                            EduPill(
                                text = "التقييمات المؤقتة (${assessments.size})",
                                active = activeTab == 1,
                                onClick = { activeTab = 1 },
                                testTag = "qb_tab_assessments"
                            )
                        }
                        if (activeTab == 0) {
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
                        } else if (hasWorkspace && questions.isNotEmpty()) {
                            EduYellowButton(
                                text = "إنشاء تقييم مؤقت جديد",
                                onClick = { showCreateAssessmentDialog = true },
                                icon = Icons.Outlined.Timer,
                                testTag = "create_assessment_button"
                            )
                        }
                    }
                }
            }

            if (activeTab == 0) {
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
                            stats = questionStats[item.id],
                            onEdit = { editingQuestion = item },
                            onToggleReviewed = { onToggleReviewed(item.id, item.reviewed) },
                            onDelete = { onDeleteQuestion(item.id) }
                        )
                    }
                }
            } else {
                if (assessments.isEmpty()) {
                    item(key = "asm_empty") {
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "لا توجد تقييمات مؤقتة بعد",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Text(
                                text = "أنشئ اختباراً قبلياً أو تكوينياً أو نهائياً من أسئلة البنك مع مؤقت زمني ونسبة نجاح.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                } else {
                    items(items = assessments, key = { "asm_${it.id}" }) { asm ->
                        val resolvedQs = remember(asm, questions) {
                            val byId = questions.associateBy { it.id }
                            asm.questionIds.mapNotNull { byId[it] }
                        }
                        AssessmentCard(
                            assessment = asm,
                            resolvedCount = resolvedQs.size,
                            onStartExam = { activeTimedAssessment = asm },
                            onExportMarkdown = { onExportAssessmentMarkdown(asm) },
                            onCopyMarkdown = {
                                val md = QuestionBankEngine.exportAssessmentMarkdown(asm, resolvedQs)
                                clipboardManager.setText(AnnotatedString(md))
                            },
                            onDelete = { onDeleteAssessment(asm.id) }
                        )
                    }
                }

                if (attempts.isNotEmpty()) {
                    item(key = "attempts_heading") {
                        Text(
                            text = "سجل محاولات التقييم (${attempts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(items = attempts, key = { "att_${it.id}" }) { att ->
                        EduPanel(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (att.passed) EduGreen else EduOrange,
                            containerColor = if (att.passed) EduMint.copy(alpha = 0.25f) else EduWhite
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.assessmentTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EduInk
                                    )
                                    Text(
                                        text = "${att.correctCount}/${att.totalQuestions} (${att.scorePercent}%) • ${att.durationSeconds}ث • +${att.earnedXp} XP",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduMuted
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (att.passed) EduMint else EduOrange.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (att.passed) "ناجح" else "يحتاج مراجعة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (att.passed) EduGreenDark else EduOrange,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        QuestionEditorDialog(
            dialogTitle = "إضافة سؤال جديد",
            initialConcept = selectedLesson?.title.orEmpty(),
            onDismiss = { showAddDialog = false },
            onSave = { type, prompt, options, correctAnswer, rationale, diff, conceptTag, _ ->
                onAddManualQuestion(type, prompt, options, correctAnswer, rationale, diff, conceptTag)
                showAddDialog = false
            }
        )
    }

    editingQuestion?.let { qToEdit ->
        QuestionEditorDialog(
            dialogTitle = "تعديل السؤال والمفهوم",
            initialType = qToEdit.type,
            initialDifficulty = qToEdit.difficulty,
            initialPrompt = qToEdit.prompt,
            initialOptions = qToEdit.options,
            initialCorrectAnswer = qToEdit.correctAnswer,
            initialRationale = qToEdit.rationale,
            initialConcept = qToEdit.conceptTag.ifBlank { qToEdit.lessonTitle },
            initialReviewed = qToEdit.reviewed,
            showReviewedToggle = true,
            onDismiss = { editingQuestion = null },
            onSave = { type, prompt, options, correctAnswer, rationale, diff, conceptTag, reviewed ->
                onUpdateQuestion(qToEdit.id, type, prompt, options, correctAnswer, rationale, diff, conceptTag, reviewed)
                editingQuestion = null
            }
        )
    }

    if (showQuizDialog && playableQuestions.isNotEmpty()) {
        InteractiveQuizDialog(
            questions = playableQuestions.take(15),
            questionStats = questionStats,
            onDismiss = { showQuizDialog = false },
            onFinished = { correct, total, xp, recordedAnswers ->
                onRecordQuestionAttempts(playableQuestions.take(15), recordedAnswers, "bank_quiz")
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

    if (showCreateAssessmentDialog) {
        CreateAssessmentDialog(
            questions = questions.filter { it.reviewed },
            onDismiss = { showCreateAssessmentDialog = false },
            onSave = { title, kind, qIds, minutes, passPercent ->
                onCreateAssessment(title, kind, qIds, minutes, passPercent)
                showCreateAssessmentDialog = false
            }
        )
    }

    activeTimedAssessment?.let { asm ->
        val byId = questions.associateBy { it.id }
        val examQuestions = asm.questionIds.mapNotNull { byId[it] }.filter { it.reviewed }
        if (examQuestions.size != asm.questionIds.size) {
            AlertDialog(
                onDismissRequest = { activeTimedAssessment = null },
                title = { Text("تعذر بدء التقييم") },
                text = { Text("بعض أسئلة هذا التقييم لم تعد معتمدة للمراجعة. راجع بنك الأسئلة وحدّث التقييم قبل تشغيله.") },
                confirmButton = {
                    TextButton(onClick = { activeTimedAssessment = null }) {
                        Text("حسناً")
                    }
                }
            )
        } else if (examQuestions.isNotEmpty()) {
            TimedExamRunnerDialog(
                assessment = asm,
                questions = examQuestions,
                questionStats = questionStats,
                onDismiss = { activeTimedAssessment = null },
                onCompleted = { correct, total, percent, elapsedSec, xp, recordedAnswers ->
                    onRecordQuestionAttempts(examQuestions, recordedAnswers, "timed_assessment")
                    onRecordAssessmentAttempt(asm, correct, total, percent, elapsedSec, xp)
                }
            )
        } else {
            activeTimedAssessment = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionBankCard(
    item: QuestionItem,
    stats: QuestionMasteryStats?,
    onEdit: () -> Unit,
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
                val conceptLabel = ConceptMasteryEngine.effectiveConceptTag(item)
                if (conceptLabel.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = EduMint) {
                        Text(
                            text = conceptLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = EduGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (stats != null && stats.attemptsCount > 0) {
                    val masteryBg = when (stats.status) {
                        MasteryStatus.MASTERED -> EduMint
                        MasteryStatus.NEEDS_REINFORCEMENT -> EduOrange.copy(alpha = 0.16f)
                        MasteryStatus.UNSEEN -> EduBg
                    }
                    val masteryColor = when (stats.status) {
                        MasteryStatus.MASTERED -> EduGreenDark
                        MasteryStatus.NEEDS_REINFORCEMENT -> EduRed
                        MasteryStatus.UNSEEN -> EduMuted
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = masteryBg) {
                        Text(
                            text = "${stats.status.labelAr} (${stats.correctCount}/${stats.attemptsCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = masteryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
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
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_question_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "تعديل السؤال",
                        tint = EduPurple
                    )
                }
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
private fun QuestionEditorDialog(
    dialogTitle: String,
    initialType: QuestionType = QuestionType.MCQ,
    initialDifficulty: QuestionDifficulty = QuestionDifficulty.MEDIUM,
    initialPrompt: String = "",
    initialOptions: List<String> = emptyList(),
    initialCorrectAnswer: String = "",
    initialRationale: String = "",
    initialConcept: String = "",
    initialReviewed: Boolean = false,
    showReviewedToggle: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (QuestionType, String, List<String>, String, String, QuestionDifficulty, String, Boolean) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var optionsRaw by remember { mutableStateOf(initialOptions.joinToString("\n")) }
    var correctAnswer by remember { mutableStateOf(initialCorrectAnswer) }
    var rationale by remember { mutableStateOf(initialRationale) }
    var conceptTag by remember { mutableStateOf(initialConcept) }
    var reviewed by remember { mutableStateOf(initialReviewed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(dialogTitle, style = MaterialTheme.typography.titleMedium, color = EduInk)
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
                OutlinedTextField(
                    value = conceptTag,
                    onValueChange = { conceptTag = it },
                    label = { Text("المفهوم التعليمي المرتبط (لتتبع الإتقان والتقوية)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val optionsHint = when (type) {
                    QuestionType.MCQ -> "الخيارات (سطر لكل خيار)"
                    QuestionType.ORDERING -> "العناصر بالترتيب الصحيح (سطر لكل عنصر)"
                    QuestionType.MATCHING -> "أزواج التوصيل (مثال: المفهوم = التعريف في كل سطر)"
                    else -> ""
                }
                if (optionsHint.isNotBlank()) {
                    OutlinedTextField(
                        value = optionsRaw,
                        onValueChange = { optionsRaw = it },
                        label = { Text(optionsHint) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type != QuestionType.ORDERING && type != QuestionType.MATCHING) {
                    OutlinedTextField(
                        value = correctAnswer,
                        onValueChange = { correctAnswer = it },
                        label = { Text(if (type == QuestionType.TRUE_FALSE) "الإجابة (صح أو خطأ)" else "الإجابة الصحيحة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = rationale,
                    onValueChange = { rationale = it },
                    label = { Text("التفسير (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showReviewedToggle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reviewed = !reviewed },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = reviewed,
                            onCheckedChange = { reviewed = it },
                            colors = CheckboxDefaults.colors(checkedColor = EduGreen)
                        )
                        Text("سؤال معتمد ومُراجع", style = MaterialTheme.typography.bodyMedium, color = EduInk)
                    }
                }
            }
        },
        confirmButton = {
            val rawLines = optionsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
            val ready = prompt.isNotBlank() && when (type) {
                QuestionType.ORDERING -> rawLines.size >= 2
                QuestionType.MATCHING -> rawLines.count { it.contains("=") } >= 2
                else -> correctAnswer.isNotBlank()
            }
            TextButton(
                enabled = ready,
                onClick = {
                    val opts = when (type) {
                        QuestionType.TRUE_FALSE -> listOf("صح", "خطأ")
                        QuestionType.MCQ, QuestionType.ORDERING, QuestionType.MATCHING -> rawLines
                        QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER -> emptyList()
                    }
                    val finalAnswer = when (type) {
                        QuestionType.ORDERING -> rawLines.joinToString(" | ")
                        QuestionType.MATCHING -> rawLines.joinToString(";")
                        else -> correctAnswer.trim()
                    }
                    onSave(type, prompt, opts, finalAnswer, rationale, difficulty, conceptTag.trim(), reviewed)
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
    questionStats: Map<String, QuestionMasteryStats>,
    onDismiss: () -> Unit,
    onFinished: (correct: Int, total: Int, xp: Int, answers: Map<String, String>) -> Unit
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
                val stats = questionStats[current.id]
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
                    QuestionMasteryDiagnosticBanner(
                        stats = stats,
                        currentAnswer = picked
                    )
                    Text(text = current.prompt, style = MaterialTheme.typography.titleSmall, color = EduInk)

                    QuestionAnswerInputArea(
                        question = current,
                        currentAnswer = picked,
                        locked = checkedCurrent,
                        previousSelectedAnswer = stats?.lastSelectedAnswer,
                        onAnswerChange = { answers[current.id] = it }
                    )

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
                        onFinished(summary.correctCount, summary.totalQuestions, summary.earnedXp, answers.toMap())
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


@Composable
private fun AssessmentCard(
    assessment: AssessmentRecord,
    resolvedCount: Int,
    onStartExam: () -> Unit,
    onExportMarkdown: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onDelete: () -> Unit
) {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(assessment.title, style = MaterialTheme.typography.titleSmall, color = EduInk)
                Text(
                    text = "${assessment.kind.labelAr} • $resolvedCount سؤال • ${if (assessment.timeLimitMinutes > 0) "${assessment.timeLimitMinutes} دقيقة" else "بدون حد زمني"} • النجاح ${assessment.passScorePercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Row {
                IconButton(onClick = onCopyMarkdown, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "نسخ Markdown", tint = EduGreenDark)
                }
                IconButton(onClick = onExportMarkdown, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Outlined.Description, contentDescription = "تصدير Markdown", tint = EduBlue)
                }
                IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف التقييم", tint = EduRed)
                }
            }
        }
        Button(
            onClick = onStartExam,
            enabled = resolvedCount > 0,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("start_assessment_${assessment.id}")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("بدء التقييم المؤقت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAssessmentDialog(
    questions: List<QuestionItem>,
    onDismiss: () -> Unit,
    onSave: (String, AssessmentKind, List<String>, Int, Int) -> Unit
) {
    var title by remember { mutableStateOf("تقييم الدرس التكويني") }
    var kind by remember { mutableStateOf(AssessmentKind.FORMATIVE) }
    var minutesText by remember { mutableStateOf("10") }
    var passText by remember { mutableStateOf("60") }
    var filterQuery by remember { mutableStateOf("") }
    val selectedIds = remember(questions) {
        mutableStateListOf<String>().apply {
            addAll(questions.take(10).map { it.id })
        }
    }
    val visibleQuestions = remember(questions, filterQuery) {
        val q = filterQuery.trim()
        if (q.isEmpty()) questions
        else questions.filter {
            it.prompt.contains(q, ignoreCase = true) ||
                it.lessonTitle.contains(q, ignoreCase = true) ||
                it.conceptTag.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = { Text("إنشاء تقييم مؤقت جديد", style = MaterialTheme.typography.titleMedium, color = EduInk) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان التقييم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssessmentKind.entries.forEach { k ->
                        EduPill(text = k.labelAr, active = kind == k, onClick = { kind = k })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                        label = { Text("المدة (دقائق)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = passText,
                        onValueChange = { passText = it.filter { c -> c.isDigit() } },
                        label = { Text("درجة النجاح %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = EduMuted) },
                    placeholder = { Text("تصفية الأسئلة حسب النص أو المفهوم...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الأسئلة المختارة (${selectedIds.size}/${questions.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            visibleQuestions.forEach { q ->
                                if (!selectedIds.contains(q.id)) selectedIds.add(q.id)
                            }
                        }) {
                            Text("تحديد المعروض", style = MaterialTheme.typography.labelSmall, color = EduGreenDark)
                        }
                        TextButton(onClick = { selectedIds.clear() }) {
                            Text("إلغاء الكل", style = MaterialTheme.typography.labelSmall, color = EduMuted)
                        }
                    }
                }
                visibleQuestions.forEach { q ->
                    val checked = selectedIds.contains(q.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (checked) selectedIds.remove(q.id) else selectedIds.add(q.id)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (checked) selectedIds.remove(q.id) else selectedIds.add(q.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = EduGreen)
                        )
                        Text(q.prompt, style = MaterialTheme.typography.bodySmall, color = EduInk, maxLines = 2)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && selectedIds.isNotEmpty(),
                onClick = {
                    val mins = minutesText.toIntOrNull() ?: 10
                    val pass = passText.toIntOrNull() ?: 60
                    onSave(title, kind, selectedIds.toList(), mins, pass)
                }
            ) {
                Text("حفظ التقييم", color = EduGreenDark, fontWeight = FontWeight.Bold)
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
private fun TimedExamRunnerDialog(
    assessment: AssessmentRecord,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    onDismiss: () -> Unit,
    onCompleted: (correct: Int, total: Int, scorePercent: Int, elapsedSeconds: Int, xp: Int, answers: Map<String, String>) -> Unit
) {
    val answers = remember(assessment.id) { mutableStateMapOf<String, String>() }
    val totalAllowedSeconds = (assessment.timeLimitMinutes * 60).coerceAtLeast(0)
    var remainingSeconds by remember(assessment.id) { mutableIntStateOf(totalAllowedSeconds) }
    var elapsedSeconds by remember(assessment.id) { mutableIntStateOf(0) }
    var finished by remember(assessment.id) { mutableStateOf(false) }

    fun finishExam() {
        if (finished) return
        finished = true
        val summary = QuestionBankEngine.gradeQuiz(questions, answers)
        onCompleted(
            summary.correctCount,
            summary.totalQuestions,
            summary.scorePercent,
            elapsedSeconds,
            summary.earnedXp,
            answers.toMap()
        )
    }

    LaunchedEffect(assessment.id, finished) {
        while (!finished) {
            delay(1000L)
            elapsedSeconds++
            if (totalAllowedSeconds > 0) {
                remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
                if (remainingSeconds == 0) {
                    finishExam()
                    break
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(assessment.title, style = MaterialTheme.typography.titleMedium, color = EduInk, modifier = Modifier.weight(1f))
                if (totalAllowedSeconds > 0 && !finished) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (remainingSeconds <= 60) EduRed.copy(alpha = 0.14f) else EduMint
                    ) {
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (remainingSeconds <= 60) EduRed else EduGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (finished) {
                    val summary = QuestionBankEngine.gradeQuiz(questions, answers)
                    val passed = summary.scorePercent >= assessment.passScorePercent
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadialProgressIndicator(
                            percentage = summary.scorePercent,
                            size = 96.dp,
                            strokeWidth = 9.dp,
                            label = if (passed) "ناجح" else "لم يجتز"
                        )
                        Text(
                            text = "${summary.correctCount}/${summary.totalQuestions} • الزمن: ${elapsedSeconds}ث",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduInk
                        )
                        Text(
                            text = "نسبة النجاح المطلوبة: ${assessment.passScorePercent}% • +${summary.earnedXp} XP",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (passed) EduGreenDark else EduOrange
                        )
                    }
                } else {
                    questions.forEachIndexed { idx, q ->
                        val picked = answers[q.id].orEmpty()
                        val stats = questionStats[q.id]
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            QuestionMasteryDiagnosticBanner(stats = stats, currentAnswer = picked)
                            Text("${idx + 1}. ${q.prompt}", style = MaterialTheme.typography.titleSmall, color = EduInk)
                            QuestionAnswerInputArea(
                                question = q,
                                currentAnswer = picked,
                                locked = false,
                                previousSelectedAnswer = stats?.lastSelectedAnswer,
                                onAnswerChange = { answers[q.id] = it }
                            )
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
            } else {
                TextButton(onClick = { finishExam() }) {
                    Text("تسليم التقييم", color = EduGreenDark, fontWeight = FontWeight.Bold)
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
