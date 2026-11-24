package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.InteractivePackageRecord
import com.example.model.LessonRef
import com.example.model.QuestionDifficulty
import com.example.model.TaskType
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.LessonPdfViewer
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
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    selectedLesson: LessonRef?,
    task: TaskType,
    questionCount: Int,
    questionStyle: String,
    questionDifficulty: QuestionDifficulty,
    attachPdfPages: Boolean,
    sourceText: String,
    extraInstructions: String,
    generatedResult: String,
    packageSourcePages: String,
    lessonDrafts: List<DraftRecord>,
    lessonPackages: List<InteractivePackageRecord>,
    isBusy: Boolean,
    onBackToLibrary: () -> Unit,
    onSelectTask: (TaskType) -> Unit,
    onQuestionCountChange: (Int) -> Unit,
    onQuestionStyleChange: (String) -> Unit,
    onQuestionDifficultyChange: (QuestionDifficulty) -> Unit,
    onToggleAttachPdfPages: () -> Unit,
    onSourceChange: (String) -> Unit,
    onExtraChange: (String) -> Unit,
    onGeneratedResultChange: (String) -> Unit,
    onPackageSourcePagesChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onSaveEditedDraft: () -> Unit,
    onSaveToQuestionBank: () -> Unit,
    onApproveDraft: (String) -> Unit,
    onLoadDraft: (DraftRecord) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onPickInteractivePackage: () -> Unit,
    onPreviewPackage: (InteractivePackageRecord, Boolean) -> Unit,
    onPublishPackage: (InteractivePackageRecord) -> Unit,
    onDeletePackage: (InteractivePackageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToLibrary)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSplitPane = maxWidth >= 820.dp && selectedLesson != null

        if (useSplitPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioBackLink(onBackToLibrary = onBackToLibrary)
                    LessonPdfViewer(
                        pdfPath = selectedLesson?.pdfPath,
                        title = selectedLesson?.title ?: stringResource(R.string.studio_pdf_viewer_title),
                        maxHeightDp = 560
                    )
                }
                StudioMainList(
                    selectedLesson = selectedLesson,
                    showInlinePdf = false,
                    showBackLink = false,
                    task = task,
                    questionCount = questionCount,
                    questionStyle = questionStyle,
                    questionDifficulty = questionDifficulty,
                    attachPdfPages = attachPdfPages,
                    sourceText = sourceText,
                    extraInstructions = extraInstructions,
                    generatedResult = generatedResult,
                    packageSourcePages = packageSourcePages,
                    lessonDrafts = lessonDrafts,
                    lessonPackages = lessonPackages,
                    isBusy = isBusy,
                    onBackToLibrary = onBackToLibrary,
                    onSelectTask = onSelectTask,
                    onQuestionCountChange = onQuestionCountChange,
                    onQuestionStyleChange = onQuestionStyleChange,
                    onQuestionDifficultyChange = onQuestionDifficultyChange,
                    onToggleAttachPdfPages = onToggleAttachPdfPages,
                    onSourceChange = onSourceChange,
                    onExtraChange = onExtraChange,
                    onGeneratedResultChange = onGeneratedResultChange,
                    onPackageSourcePagesChange = onPackageSourcePagesChange,
                    onGenerate = onGenerate,
                    onSaveEditedDraft = onSaveEditedDraft,
                    onSaveToQuestionBank = onSaveToQuestionBank,
                    onApproveDraft = onApproveDraft,
                    onLoadDraft = onLoadDraft,
                    onDeleteDraft = onDeleteDraft,
                    onPickInteractivePackage = onPickInteractivePackage,
                    onPreviewPackage = onPreviewPackage,
                    onPublishPackage = onPublishPackage,
                    onDeletePackage = onDeletePackage,
                    modifier = Modifier.weight(0.58f)
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                StudioMainList(
                    selectedLesson = selectedLesson,
                    showInlinePdf = selectedLesson != null,
                    showBackLink = true,
                    task = task,
                    questionCount = questionCount,
                    questionStyle = questionStyle,
                    questionDifficulty = questionDifficulty,
                    attachPdfPages = attachPdfPages,
                    sourceText = sourceText,
                    extraInstructions = extraInstructions,
                    generatedResult = generatedResult,
                    packageSourcePages = packageSourcePages,
                    lessonDrafts = lessonDrafts,
                    lessonPackages = lessonPackages,
                    isBusy = isBusy,
                    onBackToLibrary = onBackToLibrary,
                    onSelectTask = onSelectTask,
                    onQuestionCountChange = onQuestionCountChange,
                    onQuestionStyleChange = onQuestionStyleChange,
                    onQuestionDifficultyChange = onQuestionDifficultyChange,
                    onToggleAttachPdfPages = onToggleAttachPdfPages,
                    onSourceChange = onSourceChange,
                    onExtraChange = onExtraChange,
                    onGeneratedResultChange = onGeneratedResultChange,
                    onPackageSourcePagesChange = onPackageSourcePagesChange,
                    onGenerate = onGenerate,
                    onSaveEditedDraft = onSaveEditedDraft,
                    onSaveToQuestionBank = onSaveToQuestionBank,
                    onApproveDraft = onApproveDraft,
                    onLoadDraft = onLoadDraft,
                    onDeleteDraft = onDeleteDraft,
                    onPickInteractivePackage = onPickInteractivePackage,
                    onPreviewPackage = onPreviewPackage,
                    onPublishPackage = onPublishPackage,
                    onDeletePackage = onDeletePackage,
                    modifier = Modifier.widthIn(max = 1000.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioMainList(
    selectedLesson: LessonRef?,
    showInlinePdf: Boolean,
    showBackLink: Boolean,
    task: TaskType,
    questionCount: Int,
    questionStyle: String,
    questionDifficulty: QuestionDifficulty,
    attachPdfPages: Boolean,
    sourceText: String,
    extraInstructions: String,
    generatedResult: String,
    packageSourcePages: String,
    lessonDrafts: List<DraftRecord>,
    lessonPackages: List<InteractivePackageRecord>,
    isBusy: Boolean,
    onBackToLibrary: () -> Unit,
    onSelectTask: (TaskType) -> Unit,
    onQuestionCountChange: (Int) -> Unit,
    onQuestionStyleChange: (String) -> Unit,
    onQuestionDifficultyChange: (QuestionDifficulty) -> Unit,
    onToggleAttachPdfPages: () -> Unit,
    onSourceChange: (String) -> Unit,
    onExtraChange: (String) -> Unit,
    onGeneratedResultChange: (String) -> Unit,
    onPackageSourcePagesChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onSaveEditedDraft: () -> Unit,
    onSaveToQuestionBank: () -> Unit,
    onApproveDraft: (String) -> Unit,
    onLoadDraft: (DraftRecord) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onPickInteractivePackage: () -> Unit,
    onPreviewPackage: (InteractivePackageRecord, Boolean) -> Unit,
    onPublishPackage: (InteractivePackageRecord) -> Unit,
    onDeletePackage: (InteractivePackageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showBackLink) {
            item(key = "back_link") {
                StudioBackLink(onBackToLibrary = onBackToLibrary)
            }
        }

        item(key = "studio_hero") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(19.dp),
                color = EduGreen
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.studio_kicker),
                        style = MaterialTheme.typography.labelSmall,
                        color = EduHeroKicker
                    )
                    Text(
                        text = stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = EduWhite
                    )
                    Text(
                        text = selectedLesson?.title ?: stringResource(R.string.studio_pick_lesson),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EduHeroSub
                    )
                    if (selectedLesson != null) {
                        Text(
                            text = "${selectedLesson.grade} • ${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}" +
                                (selectedLesson.pageRange?.let { " • ص $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = EduHeroKicker
                        )
                    }
                }
            }
        }

        if (showInlinePdf && selectedLesson != null) {
            item(key = "inline_pdf_viewer") {
                LessonPdfViewer(
                    pdfPath = selectedLesson.pdfPath,
                    title = stringResource(R.string.studio_pdf_viewer_title),
                    maxHeightDp = 300
                )
            }
        }

        item(key = "studio_form") {
            EduPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.studio_task_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TaskType.entries.forEach { item ->
                        EduPill(
                            text = item.labelAr,
                            active = task == item,
                            onClick = { onSelectTask(item) },
                            testTag = "task_pill_${item.id}"
                        )
                    }
                }

                if (task == TaskType.QUESTIONS || task == TaskType.INTERACTIVE) {
                    Text(
                        text = stringResource(R.string.studio_question_count_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(3, 5, 10, 15, 20).forEach { count ->
                            EduPill(
                                text = "$count أسئلة",
                                active = questionCount == count,
                                onClick = { onQuestionCountChange(count) },
                                testTag = "question_count_$count"
                            )
                        }
                    }
                }

                if (task == TaskType.QUESTIONS) {
                    Text(
                        text = "نمط الأسئلة والمستوى",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf("متنوعة", "اختيار من متعدد", "صح أم خطأ", "إجابة قصيرة").forEach { style ->
                            EduPill(
                                text = style,
                                active = questionStyle == style,
                                onClick = { onQuestionStyleChange(style) }
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        QuestionDifficulty.entries.forEach { diff ->
                            EduPill(
                                text = diff.labelAr,
                                active = questionDifficulty == diff,
                                onClick = { onQuestionDifficultyChange(diff) }
                            )
                        }
                    }
                }

                if (!selectedLesson?.pdfPath.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleAttachPdfPages)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = attachPdfPages,
                            onCheckedChange = { onToggleAttachPdfPages() },
                            colors = CheckboxDefaults.colors(checkedColor = EduGreen),
                            modifier = Modifier.testTag("attach_pdf_checkbox")
                        )
                        Text(
                            text = stringResource(R.string.studio_attach_pdf_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduInk
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.studio_source_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = EduInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = onSourceChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.studio_source_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduMuted
                        )
                    },
                    minLines = 3,
                    maxLines = 8,
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
                        .heightIn(min = 90.dp)
                        .testTag("source_input")
                )

                Text(
                    text = stringResource(R.string.studio_extra_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = EduInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = extraInstructions,
                    onValueChange = onExtraChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.studio_extra_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduMuted
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
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
                        .heightIn(min = 76.dp)
                        .testTag("extra_input")
                )

                EduYellowButton(
                    text = if (isBusy) {
                        stringResource(R.string.studio_generating_cta)
                    } else {
                        stringResource(R.string.studio_generate_cta)
                    },
                    onClick = onGenerate,
                    enabled = !isBusy,
                    icon = Icons.Rounded.AutoAwesome,
                    testTag = "generate_button",
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        if (generatedResult.isNotBlank()) {
            item(key = "generated_result") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.studio_draft_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        Row {
                            IconButton(
                                onClick = onSaveEditedDraft,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_edited_draft_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Save,
                                    contentDescription = "حفظ كنسخة جديدة",
                                    tint = EduGreenDark
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedResult))
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "نسخ المحتوى",
                                    tint = EduGreenDark
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = generatedResult,
                        onValueChange = onGeneratedResultChange,
                        minLines = 6,
                        maxLines = 18,
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
                            .testTag("editable_draft_output")
                    )
                    OutlinedButton(
                        onClick = onSaveToQuestionBank,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_to_question_bank_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Quiz,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "استخراج وحفظ في بنك الأسئلة",
                            color = EduGreenDark,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        item(key = "interactive_package_import") {
            EduPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.package_section_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
                Text(
                    text = stringResource(R.string.package_section_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
                OutlinedTextField(
                    value = packageSourcePages,
                    onValueChange = onPackageSourcePagesChange,
                    label = {
                        Text(
                            text = stringResource(R.string.package_source_pages_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
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
                        .testTag("package_pages_input")
                )
                Button(
                    onClick = onPickInteractivePackage,
                    enabled = !isBusy && selectedLesson != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EduGreen,
                        contentColor = EduWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_interactive_package_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.package_upload_cta),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (lessonPackages.isNotEmpty()) {
            item(key = "packages_history_heading") {
                Text(
                    text = stringResource(R.string.package_versions_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
            }
            items(
                items = lessonPackages,
                key = { "pkg_${it.id}" }
            ) { pkg ->
                InteractivePackageVersionCard(
                    pkg = pkg,
                    onPreviewTeacher = { onPreviewPackage(pkg, false) },
                    onPreviewStudent = { onPreviewPackage(pkg, true) },
                    onPublish = { onPublishPackage(pkg) },
                    onDelete = { onDeletePackage(pkg) }
                )
            }
        }

        if (lessonDrafts.isNotEmpty()) {
            item(key = "saved_drafts_heading") {
                Text(
                    text = stringResource(R.string.studio_saved_drafts_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = lessonDrafts,
                key = { it.id }
            ) { draft ->
                val statusEnum = DraftStatus.fromId(draft.status)
                EduPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onLoadDraft(draft) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (statusEnum == DraftStatus.APPROVED) EduMint else EduLilac
                        ) {
                            Text(
                                text = "v${draft.version} • ${draft.task.labelAr}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (statusEnum == DraftStatus.APPROVED) EduGreenDark else EduPurple,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${draft.provider} • ${draft.model} • ${statusEnum.labelAr}",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Text(
                                text = draft.content.take(90).replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted,
                                maxLines = 2
                            )
                        }
                        if (statusEnum != DraftStatus.APPROVED) {
                            IconButton(
                                onClick = { onApproveDraft(draft.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "اعتماد المسودة",
                                    tint = EduGreenDark
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteDraft(draft.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "حذف المسودة",
                                tint = EduRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioBackLink(onBackToLibrary: () -> Unit) {
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onBackToLibrary)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("studio_back_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = EduGreenDark,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.back_to_library),
            style = MaterialTheme.typography.labelLarge,
            color = EduGreenDark
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractivePackageVersionCard(
    pkg: InteractivePackageRecord,
    onPreviewTeacher: () -> Unit,
    onPreviewStudent: () -> Unit,
    onPublish: () -> Unit,
    onDelete: () -> Unit
) {
    EduPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (pkg.publishedForStudents) EduGreen else EduLine,
        containerColor = if (pkg.publishedForStudents) EduMint.copy(alpha = 0.45f) else EduWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "v${pkg.version} • ${pkg.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                Text(
                    text = "${pkg.kind.labelAr} • ${pkg.report.totalFiles} ملف" +
                        (if (pkg.report.has3dAssets) " • 3D/GLB" else "") +
                        (if (pkg.publishedForStudents) " • منشور للطلاب" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pkg.publishedForStudents) EduGreenDark else EduMuted
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "حذف الإصدار",
                    tint = EduRed
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onPreviewTeacher,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduGreen),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Preview,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("معاينة وفحص", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onPreviewStudent,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("عرض الطالب", color = EduInk, style = MaterialTheme.typography.labelMedium)
            }
            if (!pkg.publishedForStudents && !pkg.report.hasBlockers) {
                Button(
                    onClick = onPublish,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EduGreen,
                        contentColor = EduWhite
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Publish,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نشر / استرجاع", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
