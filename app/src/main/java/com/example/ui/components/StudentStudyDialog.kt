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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.InteractiveActivityParser
import com.example.data.repository.QuestionBankEngine
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.InteractivePackageRecord
import com.example.model.LessonRef
import com.example.model.QuestionItem
import com.example.model.TaskType
import com.example.ui.theme.EduBg
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentStudyDialog(
    lesson: LessonRef,
    drafts: List<DraftRecord>,
    packages: List<InteractivePackageRecord>,
    lessonQuestions: List<QuestionItem>,
    isCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    onQuizCompleted: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val approvedDrafts = remember(drafts) {
        drafts.filter { it.status == DraftStatus.APPROVED.id }.ifEmpty { drafts }
    }
    val publishedPkg = remember(packages) {
        packages.firstOrNull { it.publishedForStudents }
    }
    val interactiveDraftActivity = remember(approvedDrafts, lesson) {
        approvedDrafts.firstOrNull { it.task == TaskType.INTERACTIVE }
            ?.let { InteractiveActivityParser.parse(it.content, lesson.title) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
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
                                text = "وضع الطالب • ${lesson.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite
                            )
                            Text(
                                text = "${lesson.bookTitle} • ${lesson.unitTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduWhite.copy(alpha = 0.9f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_student_study_dialog")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "إغلاق", tint = EduWhite)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "الكتاب (PDF)",
                        "الشرح والملخص (${approvedDrafts.size})",
                        "التفاعلي",
                        "اختبار الدرس (${lessonQuestions.size})"
                    ).forEachIndexed { idx, label ->
                        EduPill(
                            text = label,
                            active = activeTab == idx,
                            onClick = { activeTab = idx },
                            testTag = "student_study_tab_$idx"
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LessonPdfViewer(
                                pdfPath = lesson.pdfPath,
                                title = lesson.title,
                                maxHeightDp = 520
                            )
                        }

                        1 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (approvedDrafts.isEmpty()) {
                                EduPanel(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = EduMuted)
                                        Text(
                                            text = "لا توجد مسودات أو شروحات محفوظة لهذا الدرس بعد.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduMuted
                                        )
                                    }
                                }
                            } else {
                                approvedDrafts.forEach { d ->
                                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "${d.task.labelAr} (v${d.version} • ${DraftStatus.fromId(d.status).labelAr})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = EduGreenDark
                                        )
                                        Text(
                                            text = d.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduInk
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            when {
                                publishedPkg != null -> SandboxedPackageWebView(
                                    rootDir = publishedPkg.rootDir,
                                    entryFile = publishedPkg.entryFile,
                                    modifier = Modifier.fillMaxSize()
                                )
                                interactiveDraftActivity != null -> InteractiveJsonActivityPlayer(
                                    activity = interactiveDraftActivity,
                                    onCompleted = { xp -> onQuizCompleted(1, 1, xp) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "لا يوجد نشاط تفاعلي منشور أو مولّد لهذا الدرس بعد.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduMuted
                                        )
                                    }
                                }
                            }
                        }

                        else -> StudentLessonQuizTab(
                            questions = lessonQuestions,
                            onCompleted = onQuizCompleted,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Surface(
                    color = EduWhite,
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onToggleCompleted,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCompleted) EduMint else EduGreen,
                                contentColor = if (isCompleted) EduGreenDark else EduWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("student_toggle_complete_button")
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCompleted) "تم إكمال الدرس ✓" else "تحديد الدرس كمكتمل (+50 XP)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentLessonQuizTab(
    questions: List<QuestionItem>,
    onCompleted: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val answers = remember(questions) { mutableStateMapOf<String, String>() }
    var submitted by remember(questions) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (questions.isEmpty()) {
            EduPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "لا توجد أسئلة محفوظة لهذا الدرس في بنك الأسئلة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduMuted
                )
            }
            return@Column
        }

        if (submitted) {
            val summary = QuestionBankEngine.gradeQuiz(questions, answers)
            EduPanel(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EduGreen,
                containerColor = EduMint.copy(alpha = 0.4f)
            ) {
                Text(
                    text = "النتيجة: ${summary.correctCount} / ${summary.totalQuestions} (${summary.scorePercent}%)",
                    style = MaterialTheme.typography.titleMedium,
                    color = EduGreenDark
                )
                Text(
                    text = "+${summary.earnedXp} XP مكتسبة",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduInk
                )
            }
        }

        questions.forEachIndexed { idx, q ->
            val picked = answers[q.id].orEmpty()
            EduPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${idx + 1}. ${q.prompt}",
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                if (q.options.isNotEmpty() && q.type != com.example.model.QuestionType.ORDERING && q.type != com.example.model.QuestionType.MATCHING) {
                    q.options.forEach { opt ->
                        val isSelected = picked == opt
                        val isCorrect = QuestionBankEngine.isAnswerCorrect(q, opt)
                        val bg = when {
                            submitted && isCorrect -> EduMint
                            submitted && isSelected && !isCorrect -> EduRed.copy(alpha = 0.14f)
                            isSelected -> EduLilac
                            else -> EduBg
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.dp, if (isSelected) EduPurple else EduLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .minimumInteractiveComponentSize()
                                .clickable(enabled = !submitted) { answers[q.id] = opt }
                        ) {
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.bodySmall,
                                color = EduInk,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = picked,
                        onValueChange = { if (!submitted) answers[q.id] = it },
                        label = { Text("اكتب إجابتك") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (submitted) {
                    Text(
                        text = "الإجابة الصحيحة: ${q.correctAnswer}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduGreenDark
                    )
                }
            }
        }

        if (!submitted) {
            EduYellowButton(
                text = "اعتماد إجابات الدرس",
                onClick = {
                    submitted = true
                    val summary = QuestionBankEngine.gradeQuiz(questions, answers)
                    onCompleted(summary.correctCount, summary.totalQuestions, summary.earnedXp)
                }
            )
        } else {
            OutlinedButton(
                onClick = {
                    answers.clear()
                    submitted = false
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EduGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Text("إعادة المحاولة", color = EduGreenDark)
            }
        }
    }
}
