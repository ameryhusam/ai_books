package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.EduMiniRepository
import com.example.data.repository.QuestionBankEngine
import com.example.data.workspace.UriIO
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.InteractivePackageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.model.LessonQuizCompletionResult
import com.example.model.LessonRef
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.model.TaskType
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentStudyDialog(
    lesson: LessonRef,
    drafts: List<DraftRecord>,
    packages: List<InteractivePackageRecord>,
    lessonQuestions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats> = emptyMap(),
    isCompleted: Boolean,
    hasNextLesson: Boolean = false,
    lastQuizResult: LessonQuizCompletionResult? = null,
    showAdminShortcuts: Boolean = false,
    storedLessonText: String? = null,
    onOpenGenerateQuestions: (() -> Unit)? = null,
    onSubmitLessonQuiz: (Map<String, String>) -> Unit,
    onOpenNextLesson: () -> Unit = {},
    onInteractiveXpEarned: (Int) -> Unit = {},
    onInteractiveQuestionAnswered: (
        prompt: String,
        conceptTag: String,
        selectedAnswer: String,
        correctAnswer: String,
        isCorrect: Boolean,
        options: List<String>,
        sourceMode: String
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 0: محتوى الدرس و PDF, 1: الدرس التفاعلي, 2: الشرح والملخص, 3: اختبار الدرس
    var activeTab by remember(lesson.lessonKey, lesson.bookKey, lesson.unitKey) {
        mutableIntStateOf(0)
    }
    var lessonTextContent by remember(lesson.sourceTextPath, lesson.pdfPath, storedLessonText) {
        mutableStateOf<String?>(storedLessonText?.takeIf { it.isNotBlank() })
    }
    LaunchedEffect(lesson.sourceTextPath, lesson.pdfPath, storedLessonText) {
        if (!storedLessonText.isNullOrBlank()) {
            lessonTextContent = storedLessonText
            return@LaunchedEffect
        }
        val loadedText = withContext(Dispatchers.IO) {
            val fromSource = lesson.sourceTextPath?.takeIf { it.isNotBlank() }?.let {
                UriIO.readText(context, it)?.trim()?.takeIf { text -> text.isNotEmpty() }
            }
            if (!fromSource.isNullOrBlank()) return@withContext fromSource
            val rawPdf = lesson.pdfPath?.takeIf { it.isNotBlank() && !UriIO.isContentUri(it) } ?: return@withContext null
            val pdfFile = File(rawPdf)
            val txtSibling = File(pdfFile.parentFile, pdfFile.nameWithoutExtension + ".txt")
            val mdSibling = File(pdfFile.parentFile, pdfFile.nameWithoutExtension + ".md")
            when {
                txtSibling.isFile -> txtSibling.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
                mdSibling.isFile -> mdSibling.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
                else -> null
            }
        }
        lessonTextContent = loadedText
    }
    val studyDrafts = remember(drafts) {
        drafts.filter {
            it.status == DraftStatus.APPROVED.id &&
                it.task in setOf(
                    TaskType.EXPLANATION,
                    TaskType.SUMMARY,
                    TaskType.OBJECTIVES,
                    TaskType.VOCABULARY,
                    TaskType.WORKED_EXAMPLES,
                    TaskType.LESSON_REVIEW,
                    TaskType.DIFFICULTY_ADAPTER
                )
        }.sortedByDescending { it.version }
    }
    val approvedDrafts = studyDrafts
    val publishedPkg = remember(packages) {
        packages.firstOrNull { it.publishedForStudents }
    }
    val fallbackDirectFilePkg = remember(lesson.interactivePath) {
        val raw = lesson.interactivePath
        if (!raw.isNullOrBlank() && !UriIO.isContentUri(raw)) {
            val f = File(raw)
            if (f.isFile && f.parentFile != null) f.parentFile!!.absolutePath to f.name else null
        } else null
    }
    // APPROVED drafts are editorial state, not publication state.
    // Learners can access interactive content only through a published package
    // or the supported legacy direct-file package.
    val hasWebPackage = publishedPkg != null || fallbackDirectFilePkg != null
    val hasInteractive = hasWebPackage
    var interactiveCompletedXp by remember(lesson.lessonKey) { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                                text = lesson.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite
                            )
                            Text(
                                text = "${lesson.bookTitle} • ${lesson.unitTitle}" +
                                    (if (isCompleted) " • مكتمل ✓" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = EduWhite.copy(alpha = 0.92f)
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

                // Compact horizontal 4-step study bar: القراءة <-> التفاعلي <-> الشرح <-> الاختبار
                val studySteps = listOf(
                    Triple("الدرس و PDF", Icons.Rounded.PictureAsPdf, null),
                    Triple("التفاعلي", Icons.Rounded.Code, if (hasInteractive) "متاح" else null),
                    Triple("الشرح والملخص", Icons.Rounded.MenuBook, approvedDrafts.size.takeIf { it > 0 }?.toString()),
                    Triple("الاختبار", Icons.Rounded.Quiz, lessonQuestions.size.takeIf { it > 0 }?.toString())
                )
                Surface(
                    color = EduWhite,
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(studySteps) { idx, (label, icon, badgeText) ->
                            val selected = activeTab == idx
                            val tabShape = RoundedCornerShape(14.dp)
                            Surface(
                                shape = tabShape,
                                color = if (selected) EduGreen else EduBg,
                                border = BorderStroke(1.dp, if (selected) EduGreen else EduLine),
                                modifier = Modifier
                                    .testTag("student_study_tab_$idx")
                                    .minimumInteractiveComponentSize()
                                    .clip(tabShape)
                                    .clickable { activeTab = idx }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) EduWhite else EduGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) EduWhite else EduInk,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!badgeText.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) EduWhite.copy(alpha = 0.22f) else EduMint
                                        ) {
                                            Text(
                                                text = badgeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) EduWhite else EduGreenDark,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0 -> {
                            val hasPdf = !lesson.pdfPath.isNullOrBlank()
                            val readableDraft = remember(approvedDrafts) {
                                approvedDrafts.firstOrNull {
                                    it.task == TaskType.EXPLANATION || it.task == TaskType.SUMMARY || it.task == TaskType.LESSON_REVIEW
                                }
                            }
                            val effectiveText = lessonTextContent ?: readableDraft?.content
                            val hasText = !effectiveText.isNullOrBlank()
                            // 0 = الكل, 1 = PDF فقط, 2 = نص الدرس فقط
                            var contentViewMode by remember(lesson.lessonKey, hasPdf, hasText) {
                                mutableIntStateOf(if (hasPdf && hasText) 0 else if (hasPdf) 1 else 2)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (hasPdf && hasText) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = EduWhite,
                                        border = BorderStroke(1.dp, EduLine),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                Triple(0, "الكل (PDF + نص)", "study_content_mode_both"),
                                                Triple(1, "PDF فقط", "study_content_mode_pdf"),
                                                Triple(2, "النص فقط", "study_content_mode_text")
                                            ).forEach { (modeIdx, title, tag) ->
                                                val isActive = contentViewMode == modeIdx
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isActive) EduMint else EduWhite,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag(tag)
                                                        .minimumInteractiveComponentSize()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { contentViewMode = modeIdx }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = title,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isActive) EduGreenDark else EduMuted,
                                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (hasPdf && (contentViewMode == 0 || contentViewMode == 1)) {
                                    LessonPdfViewer(
                                        pdfPath = lesson.pdfPath,
                                        title = lesson.title,
                                        maxHeightDp = if (contentViewMode == 0 && hasText) 360 else 500
                                    )
                                }

                                if (hasText && (contentViewMode == 0 || contentViewMode == 2)) {
                                    EduMarkdownReader(
                                        text = effectiveText.orEmpty(),
                                        headerLabel = if (!lessonTextContent.isNullOrBlank()) {
                                            "محتوى الدرس النصي من مساحة العمل"
                                        } else {
                                            "${readableDraft?.task?.labelAr ?: "محتوى"} • شرح الدرس"
                                        },
                                        testTag = "study_lesson_text_reader"
                                    )
                                }

                                if (!hasPdf && !hasText) {
                                    LessonPdfViewer(
                                        pdfPath = null,
                                        title = lesson.title,
                                        maxHeightDp = 240
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { activeTab = 1 },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, EduPurple),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("study_toggle_interactive_button")
                                    ) {
                                        Icon(Icons.Rounded.Code, contentDescription = null, tint = EduPurple, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (hasInteractive) "فتح الدرس التفاعلي" else "الدرس التفاعلي",
                                            color = EduPurple,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Button(
                                        onClick = { activeTab = 3 },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EduYellow, contentColor = EduInk),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("study_finish_reading_quiz_button")
                                    ) {
                                        Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("إنهاء القراءة ← الاختبار", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        1 -> Column(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                color = EduWhite,
                                border = BorderStroke(1.dp, EduLine),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { activeTab = 0 },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, EduGreen),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("study_toggle_pdf_button")
                                        ) {
                                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("محتوى الدرس و PDF", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = { activeTab = 3 },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EduYellow, contentColor = EduInk),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("study_interactive_to_quiz_button")
                                        ) {
                                            Text("اختبار الدرس", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                }
                            }

                            AnimatedVisibility(visible = interactiveCompletedXp != null) {
                                Surface(
                                    color = EduMint,
                                    border = BorderStroke(1.dp, EduGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "أتممت النشاط التفاعلي (+${interactiveCompletedXp ?: 0} XP)!",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = EduGreenDark,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = { activeTab = 3 },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("ابدأ الاختبار", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            when {
                                publishedPkg != null -> SandboxedPackageWebView(
                                    rootDir = publishedPkg.rootDir,
                                    entryFile = publishedPkg.entryFile,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    onWebQuestionAnswered = { prompt, conceptTag, selected, correct, isCorrect ->
                                        onInteractiveQuestionAnswered(
                                            prompt,
                                            conceptTag.ifBlank { lesson.title },
                                            selected,
                                            correct,
                                            isCorrect,
                                            emptyList(),
                                            "interactive_html"
                                        )
                                    },
                                    onWebInteractiveCompleted = { _, earnedXp ->
                                        interactiveCompletedXp = earnedXp
                                        onInteractiveXpEarned(earnedXp)
                                    }
                                )
                                fallbackDirectFilePkg != null -> SandboxedPackageWebView(
                                    rootDir = fallbackDirectFilePkg.first,
                                    entryFile = fallbackDirectFilePkg.second,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    onWebQuestionAnswered = { prompt, conceptTag, selected, correct, isCorrect ->
                                        onInteractiveQuestionAnswered(
                                            prompt,
                                            conceptTag.ifBlank { lesson.title },
                                            selected,
                                            correct,
                                            isCorrect,
                                            emptyList(),
                                            "interactive_html"
                                        )
                                    },
                                    onWebInteractiveCompleted = { _, earnedXp ->
                                        interactiveCompletedXp = earnedXp
                                        onInteractiveXpEarned(earnedXp)
                                    }
                                )
                                else -> Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "لا يوجد نشاط تفاعلي مرتبط بهذا الدرس في مساحة العمل حالياً.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduMuted
                                        )
                                    }
                                }
                            }
                        }

                        2 -> Column(
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
                                            text = "لا توجد مسودات أو شروحات منشورة لهذا الدرس بعد.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduMuted
                                        )
                                    }
                                }
                            } else {
                                approvedDrafts.forEach { d ->
                                    val statusBadge = if (d.status == DraftStatus.APPROVED.id) "معتمد ✓" else "مسودة"
                                    EduMarkdownReader(
                                        text = d.content,
                                        headerLabel = "${d.task.labelAr} • v${d.version} • $statusBadge"
                                    )
                                }
                            }
                            Button(
                                onClick = { activeTab = 3 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EduYellow, contentColor = EduInk),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الانتقال لاختبار نهاية الدرس", fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> StudentLessonQuizTab(
                            lesson = lesson,
                            questions = lessonQuestions,
                            questionStats = questionStats,
                            lastQuizResult = lastQuizResult,
                            hasNextLesson = hasNextLesson,
                            showAdminShortcuts = showAdminShortcuts,
                            onOpenGenerateQuestions = onOpenGenerateQuestions,
                            onSubmitQuiz = onSubmitLessonQuiz,
                            onOpenNextLesson = onOpenNextLesson,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Footer strictly enforces quiz-gated completion — no manual toggle
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
                        if (isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EduMint,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("student_quiz_completed_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "تم إكمال الدرس باجتياز الاختبار ✓",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EduGreenDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (hasNextLesson) {
                                Button(
                                    onClick = onOpenNextLesson,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("student_footer_next_lesson_button")
                                ) {
                                    Text("الدرس التالي", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, tint = EduPurple, modifier = Modifier.size(17.dp))
                                Text(
                                    text = "يكتمل الدرس تلقائياً عند اجتياز الاختبار (${EduMiniRepository.LESSON_QUIZ_PASS_PERCENT}%+).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                            if (activeTab != 3) {
                                OutlinedButton(
                                    onClick = { activeTab = 3 },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EduGreen),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("الاختبار", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentLessonQuizTab(
    lesson: LessonRef,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    lastQuizResult: LessonQuizCompletionResult?,
    hasNextLesson: Boolean,
    showAdminShortcuts: Boolean = false,
    onOpenGenerateQuestions: (() -> Unit)? = null,
    onSubmitQuiz: (Map<String, String>) -> Unit,
    onOpenNextLesson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val answers = remember(lesson.lessonKey, questions) { mutableStateMapOf<String, String>() }
    var submitted by remember(lesson.lessonKey, questions) { mutableStateOf(false) }

    LaunchedEffect(lastQuizResult?.lessonKey, lastQuizResult?.scorePercent) {
        if (lastQuizResult != null && lastQuizResult.lessonKey == lesson.lessonKey) {
            submitted = true
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (questions.isEmpty()) {
            EduPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "لا توجد أسئلة مرتبطة بهذا الدرس في بنك الأسئلة حالياً.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduMuted
                )
                if (showAdminShortcuts && onOpenGenerateQuestions != null) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    OutlinedButton(
                        onClick = onOpenGenerateQuestions,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EduPurple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("study_admin_generate_questions_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = EduPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد أسئلة لهذا الدرس في Studio", color = EduPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }

        if (submitted) {
            val graded = QuestionBankEngine.gradeQuiz(questions, answers)
            val displayPercent = lastQuizResult?.takeIf { it.lessonKey == lesson.lessonKey }?.scorePercent ?: graded.scorePercent
            val displayCorrect = lastQuizResult?.takeIf { it.lessonKey == lesson.lessonKey }?.correctCount ?: graded.correctCount
            val displayTotal = lastQuizResult?.takeIf { it.lessonKey == lesson.lessonKey }?.totalQuestions ?: graded.totalQuestions
            val displayXp = lastQuizResult?.takeIf { it.lessonKey == lesson.lessonKey }?.earnedXp ?: graded.earnedXp
            val passed = displayPercent >= EduMiniRepository.LESSON_QUIZ_PASS_PERCENT

            EduPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lesson_quiz_result_panel"),
                borderColor = if (passed) EduGreen else EduOrange,
                containerColor = if (passed) EduMint.copy(alpha = 0.45f) else EduOrange.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadialProgressIndicator(
                        percentage = displayPercent,
                        size = 76.dp,
                        strokeWidth = 7.dp,
                        label = if (passed) "ناجح" else "تقوية"
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = if (passed) {
                                "أحسنت! اجتزت اختبار الدرس ✓"
                            } else {
                                "حاول مرة أخرى لتحقيق ${EduMiniRepository.LESSON_QUIZ_PASS_PERCENT}%+ وإكمال الدرس"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (passed) EduGreenDark else EduInk
                        )
                        Text(
                            text = "النتيجة: $displayCorrect / $displayTotal ($displayPercent%) • +$displayXp XP",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduInk
                        )
                    }
                }

                val weak = lastQuizResult?.weakConcepts.orEmpty()
                if (weak.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EduWhite,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = EduOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = "مفاهيم تحتاج تقوية: ${weak.joinToString("، ") { it.conceptTag }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduInk
                            )
                        }
                    }
                }

                if (passed && hasNextLesson) {
                    Button(
                        onClick = onOpenNextLesson,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("lesson_quiz_next_lesson_button")
                    ) {
                        Text("الانتقال إلى الدرس التالي", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        questions.forEachIndexed { idx, q ->
            val picked = answers[q.id].orEmpty()
            val stats = questionStats[q.id]
            EduPanel(modifier = Modifier.fillMaxWidth()) {
                QuestionMasteryDiagnosticBanner(
                    stats = stats,
                    currentAnswer = picked
                )
                Text(
                    text = "${idx + 1}. ${q.prompt}",
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                QuestionAnswerInputArea(
                    question = q,
                    currentAnswer = picked,
                    locked = submitted,
                    previousSelectedAnswer = stats?.lastSelectedAnswer,
                    onAnswerChange = { answers[q.id] = it }
                )
                if (submitted) {
                    val rightNow = QuestionBankEngine.isAnswerCorrect(q, picked)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (rightNow) EduMint else EduRed.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (rightNow) "إجابة صحيحة ✓" else "الإجابة الصحيحة: ${q.correctAnswer}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (rightNow) EduGreenDark else EduRed
                            )
                            if (q.rationale.isNotBlank()) {
                                Text(
                                    text = q.rationale,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduInk
                                )
                            }
                        }
                    }
                }
            }
        }

        val answeredCount = questions.count { !answers[it.id].isNullOrBlank() }
        if (!submitted) {
            EduYellowButton(
                text = "اعتماد اختبار الدرس (${answeredCount}/${questions.size})",
                onClick = {
                    submitted = true
                    onSubmitQuiz(answers.toMap())
                },
                enabled = answeredCount > 0,
                icon = Icons.Rounded.CheckCircle,
                testTag = "submit_lesson_quiz_button"
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
                    .testTag("retry_lesson_quiz_button")
            ) {
                Text("إعادة محاولة الاختبار", color = EduGreenDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}
