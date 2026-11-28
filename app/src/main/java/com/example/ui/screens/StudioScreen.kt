package com.example.ui.screens

import com.example.ui.components.QuestionGenerationOptions
import com.example.ui.StudioGenerationMode
import com.example.ui.StudioGenerationOperation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ai.BookIndexPromptRegistry
import com.example.data.ai.QuestionResponseContract
import com.example.model.BookRef
import com.example.model.CloudDraft
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.InteractivePackageRecord
import com.example.model.InteractiveActivityModel
import com.example.data.repository.InteractiveActivityParser
import com.example.model.LessonRef
import com.example.model.PromptTemplateRecord
import com.example.model.QuestionDifficulty
import com.example.model.SyncUiState
import com.example.model.TaskType
import com.example.model.TeacherNoteRecord
import com.example.ui.components.BookCoverImage
import com.example.ui.components.EduMarkdownReader
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.LessonPdfViewer
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
import com.example.ui.theme.EduYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    selectedLesson: LessonRef?,
    workspaceBooks: List<BookRef> = emptyList(),
    selectedBookKey: String = "",
    studioGenerationMode: StudioGenerationMode = StudioGenerationMode.SINGLE_LESSON,
    studioGenerationOperation: StudioGenerationOperation = StudioGenerationOperation.GENERATE,
    onStudioGenerationModeChange: (StudioGenerationMode) -> Unit = {},
    onStudioGenerationOperationChange: (StudioGenerationOperation) -> Unit = {},
    onSelectBulkBook: (String) -> Unit = {},
    workspaceLessons: List<LessonRef> = emptyList(),
    task: TaskType,
    questionCount: Int,
    selectedQuestionTypeIds: List<String>,
    questionLanguage: String,
    questionStyle: String,
    questionDifficulty: QuestionDifficulty,
    attachPdfPages: Boolean,
    sourceText: String,
    extraInstructions: String,
    generatedResult: String,
    displayGeneratedResult: String = generatedResult,
    packageSourcePages: String,
    lessonDrafts: List<DraftRecord>,
    cloudDraftsState: SyncUiState<List<CloudDraft>> = SyncUiState.Loading,
    lessonPackages: List<InteractivePackageRecord>,
    currentLessonTextContent: String? = null,
    onSaveCurrentAsLessonText: (Boolean) -> Unit = {},
    onUseStoredLessonTextAsSource: () -> Unit = {},
    teacherNote: TeacherNoteRecord? = null,
    promptTemplates: List<PromptTemplateRecord> = emptyList(),
    activePromptTemplateId: String = "",
    isBusy: Boolean,
    onBackToLibrary: () -> Unit,
    onSelectLesson: (LessonRef) -> Unit = {},
    onOpenPromptSourcesDialog: () -> Unit = {},
    onPreviewPdf: (String, String, String?) -> Unit = { _, _, _ -> },
    onShowFeedback: (String) -> Unit = {},
    onSelectTask: (TaskType) -> Unit,
    onQuestionCountChange: (Int) -> Unit,
    onQuestionTypesChange: (List<String>) -> Unit,
    onQuestionLanguageChange: (String) -> Unit,
    onQuestionStyleChange: (String) -> Unit,
    onQuestionDifficultyChange: (QuestionDifficulty) -> Unit,
    onToggleAttachPdfPages: () -> Unit,
    onSourceChange: (String) -> Unit,
    onExtraChange: (String) -> Unit,
    onGeneratedResultChange: (String) -> Unit,
    onPackageSourcePagesChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit = {},
    onSaveEditedDraft: () -> Unit,
    onSaveToQuestionBank: () -> Unit,
    onLaunchInteractiveActivity: (String, String) -> Unit = { _, _ -> },
    onPublishInteractiveBundle: (String?) -> Unit = {},
    onOpenStudentStudy: () -> Unit = {},
    onSaveTeacherNote: (String, Boolean) -> Unit = { _, _ -> },
    onApplyPromptTemplate: (PromptTemplateRecord) -> Unit = {},
    onApproveDraft: (String) -> Unit,
    onLoadDraft: (DraftRecord) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onLoadCloudDraft: (CloudDraft) -> Unit = {},
    onApproveCloudDraft: (CloudDraft) -> Unit = {},
    onDeleteCloudDraft: (CloudDraft) -> Unit = {},
    onPickInteractivePackage: () -> Unit,
    onPreviewPackage: (InteractivePackageRecord, Boolean) -> Unit,
    onPublishPackage: (InteractivePackageRecord) -> Unit,
    onDeletePackage: (InteractivePackageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToLibrary)

    var showLessonSwitcher by remember { mutableStateOf(false) }

    val activeBook = remember(workspaceBooks, selectedLesson) {
        workspaceBooks.firstOrNull { it.bookKey == selectedLesson?.bookKey }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSplitPane = maxWidth >= 840.dp && selectedLesson != null

        if (useSplitPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioBackLink(onBackToLibrary = onBackToLibrary)
                    LessonPdfViewer(
                        pdfPath = selectedLesson?.pdfPath,
                        title = selectedLesson?.title ?: stringResource(R.string.studio_pdf_viewer_title),
                        maxHeightDp = 520,
                        onOpenFullscreen = selectedLesson?.pdfPath?.takeIf { it.isNotBlank() }?.let { pdf ->
                            {
                                onPreviewPdf(
                                    pdf,
                                    selectedLesson.title,
                                    "${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}"
                                )
                            }
                        }
                    )
                    if (activeBook?.coverPath != null || activeBook?.sourcePdfPath != null) {
                        StudioBookQuickReferenceCard(
                            book = activeBook,
                            onPreviewFullBookPdf = activeBook?.sourcePdfPath?.let { pdf ->
                                { onPreviewPdf(pdf, activeBook.title, "${activeBook.subjectKey} • الكتاب الكامل") }
                            },
                            onOpenPromptSourcesDialog = onOpenPromptSourcesDialog
                        )
                    }
                }
                StudioWorkbench(
                    selectedLesson = selectedLesson,
                    activeBook = activeBook,
                    workspaceBooks = workspaceBooks,
                    workspaceLessons = workspaceLessons,
                    selectedBookKey = selectedBookKey,
                    studioGenerationMode = studioGenerationMode,
                    studioGenerationOperation = studioGenerationOperation,
                    onStudioGenerationModeChange = onStudioGenerationModeChange,
                    onStudioGenerationOperationChange = onStudioGenerationOperationChange,
                    onSelectBulkBook = onSelectBulkBook,
                    hasWorkspaceLessons = workspaceLessons.isNotEmpty(),
                    showInlinePdf = false,
                    showBackLink = false,
                    task = task,
                    questionCount = questionCount,
                    selectedQuestionTypeIds = selectedQuestionTypeIds,
                    questionLanguage = questionLanguage,
                    questionStyle = questionStyle,
                    questionDifficulty = questionDifficulty,
                    attachPdfPages = attachPdfPages,
                    sourceText = sourceText,
                    extraInstructions = extraInstructions,
                    generatedResult = generatedResult,
                    displayGeneratedResult = displayGeneratedResult,
                    packageSourcePages = packageSourcePages,
                    lessonDrafts = lessonDrafts,
                    cloudDraftsState = cloudDraftsState,
                    lessonPackages = lessonPackages,
                    currentLessonTextContent = currentLessonTextContent,
                    onSaveCurrentAsLessonText = onSaveCurrentAsLessonText,
                    onUseStoredLessonTextAsSource = onUseStoredLessonTextAsSource,
                    teacherNote = teacherNote,
                    promptTemplates = promptTemplates,
                    activePromptTemplateId = activePromptTemplateId,
                    isBusy = isBusy,
                    onBackToLibrary = onBackToLibrary,
                    onOpenLessonSwitcher = { showLessonSwitcher = true },
                    onOpenPromptSourcesDialog = onOpenPromptSourcesDialog,
                    onPreviewPdf = onPreviewPdf,
                    onShowFeedback = onShowFeedback,
                    onSelectTask = onSelectTask,
                    onQuestionCountChange = onQuestionCountChange,
                    onQuestionTypesChange = onQuestionTypesChange,
                    onQuestionLanguageChange = onQuestionLanguageChange,
                    onQuestionStyleChange = onQuestionStyleChange,
                    onQuestionDifficultyChange = onQuestionDifficultyChange,
                    onToggleAttachPdfPages = onToggleAttachPdfPages,
                    onSourceChange = onSourceChange,
                    onExtraChange = onExtraChange,
                    onGeneratedResultChange = onGeneratedResultChange,
                    onPackageSourcePagesChange = onPackageSourcePagesChange,
                    onGenerate = onGenerate,
                    onCancelGeneration = onCancelGeneration,
                    onSaveEditedDraft = onSaveEditedDraft,
                    onSaveToQuestionBank = onSaveToQuestionBank,
                    onLaunchInteractiveActivity = onLaunchInteractiveActivity,
                    onPublishInteractiveBundle = onPublishInteractiveBundle,
                    onOpenStudentStudy = onOpenStudentStudy,
                    onSaveTeacherNote = onSaveTeacherNote,
                    onApplyPromptTemplate = onApplyPromptTemplate,
                    onApproveDraft = onApproveDraft,
                    onLoadDraft = onLoadDraft,
                    onDeleteDraft = onDeleteDraft,
                    onLoadCloudDraft = onLoadCloudDraft,
                    onApproveCloudDraft = onApproveCloudDraft,
                    onDeleteCloudDraft = onDeleteCloudDraft,
                    onPickInteractivePackage = onPickInteractivePackage,
                    onPreviewPackage = onPreviewPackage,
                    onPublishPackage = onPublishPackage,
                    onDeletePackage = onDeletePackage,
                    modifier = Modifier.weight(0.60f)
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                StudioWorkbench(
                    selectedLesson = selectedLesson,
                    activeBook = activeBook,
                    workspaceBooks = workspaceBooks,
                    workspaceLessons = workspaceLessons,
                    selectedBookKey = selectedBookKey,
                    studioGenerationMode = studioGenerationMode,
                    studioGenerationOperation = studioGenerationOperation,
                    onStudioGenerationModeChange = onStudioGenerationModeChange,
                    onStudioGenerationOperationChange = onStudioGenerationOperationChange,
                    onSelectBulkBook = onSelectBulkBook,
                    hasWorkspaceLessons = workspaceLessons.isNotEmpty(),
                    showInlinePdf = selectedLesson != null,
                    showBackLink = true,
                    task = task,
                    questionCount = questionCount,
                    selectedQuestionTypeIds = selectedQuestionTypeIds,
                    questionLanguage = questionLanguage,
                    questionStyle = questionStyle,
                    questionDifficulty = questionDifficulty,
                    attachPdfPages = attachPdfPages,
                    sourceText = sourceText,
                    extraInstructions = extraInstructions,
                    generatedResult = generatedResult,
                    displayGeneratedResult = displayGeneratedResult,
                    packageSourcePages = packageSourcePages,
                    lessonDrafts = lessonDrafts,
                    cloudDraftsState = cloudDraftsState,
                    lessonPackages = lessonPackages,
                    currentLessonTextContent = currentLessonTextContent,
                    onSaveCurrentAsLessonText = onSaveCurrentAsLessonText,
                    onUseStoredLessonTextAsSource = onUseStoredLessonTextAsSource,
                    teacherNote = teacherNote,
                    promptTemplates = promptTemplates,
                    activePromptTemplateId = activePromptTemplateId,
                    isBusy = isBusy,
                    onBackToLibrary = onBackToLibrary,
                    onOpenLessonSwitcher = { showLessonSwitcher = true },
                    onOpenPromptSourcesDialog = onOpenPromptSourcesDialog,
                    onPreviewPdf = onPreviewPdf,
                    onShowFeedback = onShowFeedback,
                    onSelectTask = onSelectTask,
                    onQuestionCountChange = onQuestionCountChange,
                    onQuestionTypesChange = onQuestionTypesChange,
                    onQuestionLanguageChange = onQuestionLanguageChange,
                    onQuestionStyleChange = onQuestionStyleChange,
                    onQuestionDifficultyChange = onQuestionDifficultyChange,
                    onToggleAttachPdfPages = onToggleAttachPdfPages,
                    onSourceChange = onSourceChange,
                    onExtraChange = onExtraChange,
                    onGeneratedResultChange = onGeneratedResultChange,
                    onPackageSourcePagesChange = onPackageSourcePagesChange,
                    onGenerate = onGenerate,
                    onCancelGeneration = onCancelGeneration,
                    onSaveEditedDraft = onSaveEditedDraft,
                    onSaveToQuestionBank = onSaveToQuestionBank,
                    onLaunchInteractiveActivity = onLaunchInteractiveActivity,
                    onPublishInteractiveBundle = onPublishInteractiveBundle,
                    onOpenStudentStudy = onOpenStudentStudy,
                    onSaveTeacherNote = onSaveTeacherNote,
                    onApplyPromptTemplate = onApplyPromptTemplate,
                    onApproveDraft = onApproveDraft,
                    onLoadDraft = onLoadDraft,
                    onDeleteDraft = onDeleteDraft,
                    onLoadCloudDraft = onLoadCloudDraft,
                    onApproveCloudDraft = onApproveCloudDraft,
                    onDeleteCloudDraft = onDeleteCloudDraft,
                    onPickInteractivePackage = onPickInteractivePackage,
                    onPreviewPackage = onPreviewPackage,
                    onPublishPackage = onPublishPackage,
                    onDeletePackage = onDeletePackage,
                    modifier = Modifier.widthIn(max = 1000.dp)
                )
            }
        }
    }

    if (showLessonSwitcher) {
        StudioLessonSwitcherDialog(
            books = workspaceBooks,
            lessons = workspaceLessons,
            selectedLesson = selectedLesson,
            onSelectLesson = { lesson ->
                showLessonSwitcher = false
                onSelectLesson(lesson)
                onShowFeedback("تم اختيار «${lesson.title}» داخل الاستوديو")
            },
            onDismiss = { showLessonSwitcher = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioWorkbench(
    selectedLesson: LessonRef?,
    activeBook: BookRef?,
    workspaceBooks: List<BookRef>,
    workspaceLessons: List<LessonRef>,
    selectedBookKey: String,
    studioGenerationMode: StudioGenerationMode,
    studioGenerationOperation: StudioGenerationOperation,
    onStudioGenerationModeChange: (StudioGenerationMode) -> Unit,
    onStudioGenerationOperationChange: (StudioGenerationOperation) -> Unit,
    onSelectBulkBook: (String) -> Unit,
    hasWorkspaceLessons: Boolean,
    showInlinePdf: Boolean,
    showBackLink: Boolean,
    task: TaskType,
    questionCount: Int,
    selectedQuestionTypeIds: List<String>,
    questionLanguage: String,
    questionStyle: String,
    questionDifficulty: QuestionDifficulty,
    attachPdfPages: Boolean,
    sourceText: String,
    extraInstructions: String,
    generatedResult: String,
    displayGeneratedResult: String,
    packageSourcePages: String,
    lessonDrafts: List<DraftRecord>,
    cloudDraftsState: SyncUiState<List<CloudDraft>>,
    lessonPackages: List<InteractivePackageRecord>,
    currentLessonTextContent: String?,
    onSaveCurrentAsLessonText: (Boolean) -> Unit,
    onUseStoredLessonTextAsSource: () -> Unit,
    teacherNote: TeacherNoteRecord?,
    promptTemplates: List<PromptTemplateRecord>,
    activePromptTemplateId: String,
    isBusy: Boolean,
    onBackToLibrary: () -> Unit,
    onOpenLessonSwitcher: () -> Unit,
    onOpenPromptSourcesDialog: () -> Unit,
    onPreviewPdf: (String, String, String?) -> Unit,
    onShowFeedback: (String) -> Unit,
    onSelectTask: (TaskType) -> Unit,
    onQuestionCountChange: (Int) -> Unit,
    onQuestionTypesChange: (List<String>) -> Unit,
    onQuestionLanguageChange: (String) -> Unit,
    onQuestionStyleChange: (String) -> Unit,
    onQuestionDifficultyChange: (QuestionDifficulty) -> Unit,
    onToggleAttachPdfPages: () -> Unit,
    onSourceChange: (String) -> Unit,
    onExtraChange: (String) -> Unit,
    onGeneratedResultChange: (String) -> Unit,
    onPackageSourcePagesChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit = {},
    onSaveEditedDraft: () -> Unit,
    onSaveToQuestionBank: () -> Unit,
    onLaunchInteractiveActivity: (String, String) -> Unit,
    onPublishInteractiveBundle: (String?) -> Unit,
    onOpenStudentStudy: () -> Unit,
    onSaveTeacherNote: (String, Boolean) -> Unit,
    onApplyPromptTemplate: (PromptTemplateRecord) -> Unit,
    onApproveDraft: (String) -> Unit,
    onLoadDraft: (DraftRecord) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onLoadCloudDraft: (CloudDraft) -> Unit,
    onApproveCloudDraft: (CloudDraft) -> Unit,
    onDeleteCloudDraft: (CloudDraft) -> Unit,
    onPickInteractivePackage: () -> Unit,
    onPreviewPackage: (InteractivePackageRecord, Boolean) -> Unit,
    onPublishPackage: (InteractivePackageRecord) -> Unit,
    onDeletePackage: (InteractivePackageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var workbenchTab by remember { mutableIntStateOf(0) } // 0: Compose, 1: Output & Drafts, 2: Source & Packages
    val studioTask = if (studioGenerationMode == StudioGenerationMode.BULK_QUESTIONS) TaskType.QUESTIONS else task
    var formattedEditMode by remember(studioTask) { mutableStateOf(false) }
    val canEditFormattedContent = studioTask !in setOf(TaskType.QUESTIONS, TaskType.INTERACTIVE, TaskType.LESSON_TEXT)
    var promptPreviewExpanded by remember { mutableStateOf(false) }
    var lastResultSeen by remember(selectedLesson) { mutableStateOf(generatedResult) }

    // Switch seamlessly to Output tab when a new result arrives from generation
    LaunchedEffect(generatedResult) {
        if (generatedResult.isNotBlank() && generatedResult != lastResultSeen) {
            lastResultSeen = generatedResult
            workbenchTab = 1
        }
    }

    val interactiveActivityPreview = remember(task, generatedResult, selectedLesson) {
        if (studioTask == TaskType.INTERACTIVE) {
            InteractiveActivityParser.parseStrictJson(generatedResult, selectedLesson?.title ?: "نشاط الدرس")
        } else null
    }

    val activeBlueprint = remember(studioTask) { BookIndexPromptRegistry.blueprintFor(studioTask) }
    val context = LocalContext.current
    val promptTemplateContent = remember(context, activeBlueprint.sourcePath) {
        runCatching {
            context.assets.open(activeBlueprint.sourcePath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }
    val activePromptTemplate = remember(promptTemplates, activePromptTemplateId, task) {
        promptTemplates.firstOrNull { it.id == activePromptTemplateId && it.task == studioTask }
            ?: promptTemplates.firstOrNull { it.task == studioTask }
    }
    val templateInstructions = activePromptTemplate?.instructions.orEmpty().trim()
    val effectivePreviewExtra = if (templateInstructions.isNotBlank() && extraInstructions.contains(templateInstructions)) {
        extraInstructions.trim()
    } else {
        listOf(templateInstructions, extraInstructions.trim()).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
    }
    val livePromptPreview = remember(
        selectedLesson,
        studioTask,
        sourceText,
        extraInstructions,
        promptTemplates,
        activePromptTemplateId,
        questionCount,
        selectedQuestionTypeIds,
        questionLanguage,
        attachPdfPages,
        questionStyle,
        questionDifficulty
    ) {
        if (selectedLesson != null) {
            BookIndexPromptRegistry.assemblePrompt(
                lesson = selectedLesson,
                task = studioTask,
                source = sourceText,
                extra = effectivePreviewExtra,
                questionCount = questionCount,
                questionTypeIds = selectedQuestionTypeIds,
                questionLanguage = questionLanguage,
                attachedPdfPagesCount = if (attachPdfPages && !selectedLesson.pdfPath.isNullOrBlank()) 1 else 0,
                questionStyleHint = questionStyle.takeIf { studioTask == TaskType.QUESTIONS && it != "متنوعة" }.orEmpty(),
                difficultyHint = if (studioTask == TaskType.QUESTIONS) questionDifficulty.id else "",
                promptTemplate = promptTemplateContent,
                responseSchemaJson = if (studioTask == TaskType.QUESTIONS) QuestionResponseContract.schemaFor(selectedQuestionTypeIds, questionDifficulty.id) else activePromptTemplate?.responseSchemaJson.orEmpty(),
                responseFormat = if (studioTask == TaskType.QUESTIONS) "json" else activePromptTemplate?.responseFormat
                    ?: if (activePromptTemplate?.responseSchemaJson.isNullOrBlank()) "text" else "json"
            )
        } else {
            "${activeBlueprint.roleInstruction}\n${activeBlueprint.outputContract}"
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showBackLink) {
            item(key = "back_link") {
                StudioBackLink(onBackToLibrary = onBackToLibrary)
            }
        }

        item(key = "studio_hero_deck") {
            StudioModernHeroCard(
                selectedLesson = selectedLesson,
                activeBook = activeBook,
                hasWorkspaceLessons = hasWorkspaceLessons,
                draftsCount = lessonDrafts.size,
                onOpenLessonSwitcher = onOpenLessonSwitcher,
                onOpenPromptSourcesDialog = onOpenPromptSourcesDialog,
                onPreviewLessonPdf = selectedLesson?.pdfPath?.takeIf { it.isNotBlank() }?.let { pdf ->
                    {
                        onPreviewPdf(
                            pdf,
                            selectedLesson.title,
                            "${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}"
                        )
                    }
                },
                onOpenStudentStudy = onOpenStudentStudy
            )
        }

        if (selectedLesson == null) {
            item(key = "studio_empty_state") {
                EduPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = EduMint.copy(alpha = 0.4f),
                    borderColor = EduGreen.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "استوديو التوليد مستقل عن مسار التعلّم",
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    Text(
                        text = "يمكنك اختيار أي كتاب أو درس مباشرة من هنا لتوليد الشروح وبنوك الأسئلة والأنشطة التفاعلية، مع الوصول الكامل لمصادر البرومبت ومخططات الفهرس.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EduMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = if (hasWorkspaceLessons) onOpenLessonSwitcher else onBackToLibrary,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("studio_empty_pick_button")
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasWorkspaceLessons) "اختر درساً من هنا" else "استيراد كتاب من المكتبة",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenPromptSourcesDialog,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EduGreen),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مصادر البرومبت", color = EduGreenDark)
                        }
                    }
                }
            }
        }

        item(key = "workbench_segmented_tabs") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EduWhite,
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    StudioSegmentTab(
                        label = "التكوين والتوليد",
                        selected = workbenchTab == 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            workbenchTab = 0
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "studio_tab_compose"
                    )
                    val outBadge = if (lessonDrafts.isNotEmpty()) " (${lessonDrafts.size})" else ""
                    StudioSegmentTab(
                        label = "الناتج والمسودات$outBadge",
                        selected = workbenchTab == 1,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            workbenchTab = 1
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "studio_tab_output"
                    )
                    val pkgBadge = if (lessonPackages.isNotEmpty()) " (${lessonPackages.size})" else ""
                    StudioSegmentTab(
                        label = "المصدر والحزم$pkgBadge",
                        selected = workbenchTab == 2,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            workbenchTab = 2
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "studio_tab_source"
                    )
                }
            }
        }

        item(key = "workbench_tab_body") {
            AnimatedContent(
                targetState = workbenchTab,
                label = "studio_workbench_content"
            ) { tab ->
                when (tab) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioGenerationControlPanel(
                            mode = studioGenerationMode,
                            operation = studioGenerationOperation,
                            books = workspaceBooks,
                            lessons = workspaceLessons,
                            selectedBookKey = selectedBookKey.ifBlank { selectedLesson?.bookKey.orEmpty() },
                            onModeChange = onStudioGenerationModeChange,
                            onOperationChange = onStudioGenerationOperationChange,
                            onSelectBook = onSelectBulkBook
                        )

                        // Task Blueprint Carousel
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.studio_task_heading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EduInk
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = EduLilac,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onOpenPromptSourcesDialog)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = EduPurple, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "مصادر البرومبت",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EduPurple,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(TaskType.entries, key = { it.id }) { item ->
                                    val bp = BookIndexPromptRegistry.blueprintFor(item)
                                    val active = task == item
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (active) EduMint else EduFieldBg,
                                        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) EduGreen else EduLine),
                                        modifier = Modifier
                                            .width(156.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .minimumInteractiveComponentSize()
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelectTask(item)
                                            }
                                            .testTag("task_pill_${item.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.labelAr,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (active) EduGreenDark else EduInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (active) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = EduGreenDark,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = bp.subtitleAr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = EduMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EduMint.copy(alpha = 0.42f),
                                border = BorderStroke(1.dp, EduGreen.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${activeBlueprint.titleAr} • ${activeBlueprint.sourcePath}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = EduGreenDark
                                    )
                                    Text(
                                        text = "معيار الخرج: ${activeBlueprint.outputContract}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduInk
                                    )
                                }
                            }

                            if (studioTask == TaskType.QUESTIONS || studioTask == TaskType.INTERACTIVE) {
                                Text(
                                    text = if (studioTask == TaskType.INTERACTIVE) "عدد المحطات التفاعلية المطلوبة" else stringResource(R.string.studio_question_count_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EduInk,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    listOf(3, 5, 10, 15, 20).forEach { count ->
                                        EduPill(
                                            text = if (studioTask == TaskType.INTERACTIVE) "$count محطات" else "$count أسئلة",
                                            active = questionCount == count,
                                            onClick = { onQuestionCountChange(count) },
                                            testTag = "question_count_$count"
                                        )
                                    }
                                }
                            }

                            if (studioTask == TaskType.QUESTIONS) {
                                Text(
                                    text = "إعدادات توليد الأسئلة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EduInk,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                QuestionGenerationOptions(
                                    questionCount = questionCount,
                                    selectedQuestionTypeIds = selectedQuestionTypeIds,
                                    questionLanguage = questionLanguage,
                                    questionDifficulty = questionDifficulty,
                                    onQuestionCountChange = onQuestionCountChange,
                                    onQuestionTypesChange = onQuestionTypesChange,
                                    onQuestionLanguageChange = onQuestionLanguageChange,
                                    onQuestionDifficultyChange = onQuestionDifficultyChange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Source & Readiness Panel
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.studio_source_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!selectedLesson?.pdfPath.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = EduMint,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedLesson?.pdfPath?.let { pdf ->
                                                        onPreviewPdf(
                                                            pdf,
                                                            selectedLesson.title,
                                                            "${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}"
                                                        )
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = "معاينة PDF",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EduGreenDark,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(8.dp), color = EduFieldBg) {
                                        Text(
                                            text = "${sourceText.length} حرف",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EduMuted,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
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
                                        .padding(vertical = 2.dp),
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
                                if (attachPdfPages) {
                                    Text(
                                        text = stringResource(R.string.studio_pdf_privacy_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduMuted,
                                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                                    )
                                }
                            }

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
                                maxLines = 7,
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

                            if (promptTemplates.isNotEmpty()) {
                                Text(
                                    text = "قوالب التوجيه ومسرّعات الكتابة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EduInk,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    promptTemplates.forEach { tpl ->
                                        EduPill(
                                            text = tpl.title,
                                            active = task == tpl.task && extraInstructions.contains(tpl.instructions),
                                            onClick = { onApplyPromptTemplate(tpl) },
                                            testTag = "template_pill_${tpl.id}"
                                        )
                                    }
                                    listOf(
                                        "أمثلة من واقع الطالب",
                                        "ربط صريح برقم الصفحة",
                                        "تنظيم في نقاط مرقمة"
                                    ).forEach { chip ->
                                        EduPill(
                                            text = "+ $chip",
                                            active = extraInstructions.contains(chip),
                                            onClick = {
                                                val updated = if (extraInstructions.contains(chip)) {
                                                    extraInstructions
                                                } else if (extraInstructions.isBlank()) {
                                                    chip
                                                } else {
                                                    "$extraInstructions\n• $chip"
                                                }
                                                onExtraChange(updated)
                                            }
                                        )
                                    }
                                }
                            }

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
                                    .heightIn(min = 74.dp)
                                    .testTag("extra_input")
                            )

                            // Expandable Live Prompt Preview 
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EduFieldBg,
                                border = BorderStroke(1.dp, EduLine),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { promptPreviewExpanded = !promptPreviewExpanded }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = EduPurple, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "معاينة البرومبت المركّب ",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EduInk,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = if (promptPreviewExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = "توسيع معاينة البرومبت",
                                            tint = EduMuted
                                        )
                                    }
                                    AnimatedVisibility(visible = promptPreviewExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = livePromptPreview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = EduInk
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(livePromptPreview))
                                                        onShowFeedback("تم نسخ البرومبت المركّب")
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, EduGreen),
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("نسخ البرومبت", color = EduGreenDark, style = MaterialTheme.typography.labelSmall)
                                                }
                                                OutlinedButton(
                                                    onClick = onOpenPromptSourcesDialog,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, EduPurple.copy(alpha = 0.5f)),
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Text("كل المصادر والمخططات", color = EduPurple, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            EduYellowButton(
                                text = if (isBusy) {
                                    stringResource(R.string.studio_generating_cta)
                                } else {
                                    when (studioGenerationMode) {
                                        StudioGenerationMode.SINGLE_LESSON -> when (studioGenerationOperation) {
                                            StudioGenerationOperation.GENERATE -> "توليد محتوى الدرس"
                                            StudioGenerationOperation.UPDATE -> "تحديث محتوى الدرس"
                                            StudioGenerationOperation.REVIEW -> "مراجعة محتوى الدرس"
                                        }
                                        StudioGenerationMode.BULK_QUESTIONS -> "بدء توليد بنك الأسئلة للمادة"
                                        StudioGenerationMode.BULK_FULL_CONTENT -> "بدء توليد كامل محتوى المادة"
                                    }
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onGenerate()
                                },
                                enabled = !isBusy,
                                icon = Icons.Rounded.AutoAwesome,
                                testTag = "generate_button",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (isBusy) {
                                OutlinedButton(
                                    onClick = onCancelGeneration,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, EduRed),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("cancel_generation_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = null,
                                        tint = EduRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إيقاف التوليد الحالي",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EduRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (generatedResult.isNotBlank()) {
                            EduPanel(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = EduMint.copy(alpha = 0.5f),
                                borderColor = EduGreen
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "يوجد ناتج جاهز للمراجعة والتحرير",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = EduGreenDark
                                        )
                                        Text(
                                            text = displayGeneratedResult.take(110).replace('\n', ' '),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EduInk,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { workbenchTab = 1 },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("studio_jump_output_button")
                                    ) {
                                        Text("فتح الناتج والمسودات", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(generatedResult))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onShowFeedback("تم نسخ الناتج إلى الحافظة")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, EduGreen),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "نسخ", tint = EduGreenDark, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EduPill(
                                        text = "معاينة",
                                        active = !formattedEditMode,
                                        onClick = { formattedEditMode = false }
                                    )
                                    if (canEditFormattedContent) {
                                        EduPill(
                                            text = "تحرير المحتوى",
                                            active = formattedEditMode,
                                            onClick = { formattedEditMode = true }
                                        )
                                    }
                                }
                            }

                            if (generatedResult.isBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EduFieldBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "لا يوجد ناتج مفتوح حالياً. ولّد محتوى من تبويب التكوين أو اختر مسودة محفوظة بالأسفل.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EduMuted
                                        )
                                        OutlinedButton(
                                            onClick = { workbenchTab = 0 },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, EduGreen),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("العودة إلى التكوين والتوليد", color = EduGreenDark)
                                        }
                                    }
                                }
                            } else {
                                if (formattedEditMode && canEditFormattedContent) {
                                    OutlinedTextField(
                                        value = displayGeneratedResult,
                                        onValueChange = onGeneratedResultChange,
                                        minLines = 8,
                                        maxLines = 22,
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
                                            .testTag("formatted_draft_editor")
                                    )
                                } else {
                                    when {
                                        studioTask == TaskType.INTERACTIVE && interactiveActivityPreview != null ->
                                            InteractiveActivityFormattedPreview(interactiveActivityPreview)
                                        studioTask == TaskType.INTERACTIVE ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = EduFieldBg,
                                                border = BorderStroke(1.dp, EduOrange),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "تعذر عرض النشاط بصيغته التفاعلية؛ لم تطابق الاستجابة مخطط النشاط المعتمد. يمكنك مراجعة المسودة أو إعادة التوليد.",
                                                    modifier = Modifier.padding(14.dp),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = EduInk
                                                )
                                            }
                                        else -> EduMarkdownReader(
                                            text = displayGeneratedResult,
                                            onSwitchToEdit = if (canEditFormattedContent) {
                                                { formattedEditMode = true }
                                            } else null
                                        )
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSaveEditedDraft()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, EduGreen),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("save_edited_draft_button")
                                    ) {
                                        Icon(Icons.Outlined.Save, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حفظ كنسخة", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(generatedResult))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onShowFeedback("تم نسخ المحتوى إلى الحافظة")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, EduLine),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = EduInk, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("نسخ", color = EduInk, style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (studioTask == TaskType.QUESTIONS) {
                                    OutlinedButton(
                                        onClick = onSaveToQuestionBank,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, EduGreen),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("save_to_question_bank_button")
                                    ) {
                                        Icon(Icons.Outlined.Quiz, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اعتماد آخر مسودة محفوظة", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                    }
                                    }
                                }

                                if (studioTask == TaskType.INTERACTIVE) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onLaunchInteractiveActivity(
                                                    generatedResult,
                                                    selectedLesson?.title ?: "نشاط تفاعلي"
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EduPurple, contentColor = EduWhite),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .testTag("run_interactive_json_button")
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تشغيل النشاط التفاعلي (JSON)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = "يتم إنشاء الحزمة ونشرها للطالب بعد اعتماد المسودة من قائمة المسودات.",
                                            color = EduMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (lessonDrafts.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.studio_saved_drafts_heading),
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            lessonDrafts.filter { it.task == task }.forEach { draft ->
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
                                        if (draft.task == TaskType.INTERACTIVE) {
                                            IconButton(
                                                onClick = { onLaunchInteractiveActivity(draft.content, draft.title) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "تشغيل النشاط التفاعلي",
                                                    tint = EduPurple
                                                )
                                            }
                                        }
                                        if (statusEnum != DraftStatus.APPROVED) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onApproveDraft(draft.id)
                                                },
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

                        if (cloudDraftsState is SyncUiState.Success) {
                            val matchingCloudDrafts = cloudDraftsState.data.filter { cd ->
                                selectedLesson == null || (cd.bookKey == selectedLesson.bookKey && cd.lessonKey == selectedLesson.lessonKey)
                            }.take(6)
                            if (matchingCloudDrafts.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDone,
                                        contentDescription = null,
                                        tint = EduGreenDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "المسودات السحابية المتزامنة (Firestore)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EduInk
                                    )
                                }
                                matchingCloudDrafts.forEach { cd ->
                                    val cdStatus = cd.statusOrDefault()
                                    EduPanel(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onLoadCloudDraft(cd) }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (cdStatus == DraftStatus.APPROVED) EduMint else EduLilac
                                            ) {
                                                Text(
                                                    text = "سحابة v${cd.version} • ${cd.taskTypeOrDefault().labelAr}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (cdStatus == DraftStatus.APPROVED) EduGreenDark else EduPurple,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${cd.title} • ${cd.provider} • ${cdStatus.labelAr}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = EduInk
                                                )
                                                Text(
                                                    text = cd.content.take(90).replace('\n', ' '),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EduMuted,
                                                    maxLines = 2
                                                )
                                            }
                                            if (cdStatus != DraftStatus.APPROVED) {
                                                IconButton(
                                                    onClick = { onApproveCloudDraft(cd) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.CheckCircle,
                                                        contentDescription = "اعتماد المسودة السحابية",
                                                        tint = EduGreenDark
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onDeleteCloudDraft(cd) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = "حذف المسودة السحابية",
                                                    tint = EduRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (cloudDraftsState is SyncUiState.Error) {
                            EduPanel(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "تنبيه المزامنة السحابية: ${cloudDraftsState.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduRed
                                )
                            }
                        }
                    }

                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (showInlinePdf && selectedLesson != null) {
                            LessonPdfViewer(
                                pdfPath = selectedLesson.pdfPath,
                                title = stringResource(R.string.studio_pdf_viewer_title),
                                maxHeightDp = 320,
                                onOpenFullscreen = selectedLesson.pdfPath?.takeIf { it.isNotBlank() }?.let { pdf ->
                                    {
                                        onPreviewPdf(
                                            pdf,
                                            selectedLesson.title,
                                            "${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}"
                                        )
                                    }
                                }
                            )
                        }

                        if (selectedLesson != null) {
                            val lessonKeyId = "${selectedLesson.bookKey}_${selectedLesson.unitKey}_${selectedLesson.lessonKey}"
                            var noteDraft by remember(lessonKeyId, teacherNote?.noteText) {
                                mutableStateOf(teacherNote?.noteText.orEmpty())
                            }
                            var isBookmarked by remember(lessonKeyId, teacherNote?.bookmarked) {
                                mutableStateOf(teacherNote?.bookmarked == true)
                            }
                            EduPanel(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "ملاحظات المعلم وإشارة مرجعية",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EduInk
                                    )
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = !isBookmarked
                                            isBookmarked = next
                                            onSaveTeacherNote(noteDraft, next)
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("studio_bookmark_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = if (isBookmarked) "إزالة الإشارة المرجعية" else "إضافة إشارة مرجعية",
                                            tint = if (isBookmarked) EduOrange else EduMuted
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = noteDraft,
                                    onValueChange = { noteDraft = it },
                                    placeholder = { Text("دوّن ملاحظات التدريس أو نقاط التركيز لهذا الدرس...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = EduFieldBg,
                                        unfocusedContainerColor = EduFieldBg,
                                        focusedBorderColor = EduGreen,
                                        unfocusedBorderColor = EduLine
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("teacher_note_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSaveTeacherNote(noteDraft, isBookmarked)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EduGreen),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("save_teacher_note_button")
                                ) {
                                    Text("حفظ الملاحظة والإشارة", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (!currentLessonTextContent.isNullOrBlank()) {
                            EduPanel(modifier = Modifier.fillMaxWidth()) {
                                Text("نص الدرس المحفوظ", style = MaterialTheme.typography.titleMedium, color = EduInk)
                                Text(
                                    text = currentLessonTextContent.take(260),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                OutlinedButton(
                                    onClick = onUseStoredLessonTextAsSource,
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) {
                                    Text("استخدام النص المحفوظ كمصدر")
                                }
                            }
                        }
                        if (
                            generatedResult.isNotBlank() &&
                            task in setOf(
                                TaskType.EXPLANATION,
                                TaskType.SUMMARY,
                                TaskType.OBJECTIVES,
                                TaskType.VOCABULARY,
                                TaskType.WORKED_EXAMPLES,
                                TaskType.LESSON_REVIEW,
                                TaskType.DIFFICULTY_ADAPTER,
                                TaskType.LESSON_TEXT
                            )
                        ) {
                            EduPanel(modifier = Modifier.fillMaxWidth()) {
                                Text("إدارة نص الدرس", style = MaterialTheme.typography.titleMedium, color = EduInk)
                                Text(
                                    "احفظ النص الناتج أو نص المصدر بعد مراجعته. لن يتم اعتماده تلقائياً بمجرد التوليد.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                                Button(
                                    onClick = { onSaveCurrentAsLessonText(true) },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) { Text("حفظ الناتج كنص للدرس") }
                                OutlinedButton(
                                    onClick = { onSaveCurrentAsLessonText(false) },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) { Text("حفظ نص المصدر كنص للدرس") }
                            }
                        }

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
                                    .minimumInteractiveComponentSize()
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

                        if (lessonPackages.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.package_versions_heading),
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk
                            )
                            lessonPackages.forEach { pkg ->
                                InteractivePackageVersionCard(
                                    pkg = pkg,
                                    onPreviewTeacher = { onPreviewPackage(pkg, false) },
                                    onPreviewStudent = { onPreviewPackage(pkg, true) },
                                    onPublish = { onPublishPackage(pkg) },
                                    onDelete = { onDeletePackage(pkg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioModernHeroCard(
    selectedLesson: LessonRef?,
    activeBook: BookRef?,
    hasWorkspaceLessons: Boolean,
    draftsCount: Int,
    onOpenLessonSwitcher: () -> Unit,
    onOpenPromptSourcesDialog: () -> Unit,
    onPreviewLessonPdf: (() -> Unit)?,
    onOpenStudentStudy: () -> Unit
) {
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
                            Color(0xFF0A3A2E),
                            EduGreenDark,
                            Color(0xFF16614D)
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BookCoverImage(
                        coverPath = activeBook?.coverPath,
                        bookTitle = selectedLesson?.bookTitle ?: activeBook?.title ?: "Edu Studio",
                        subjectKey = selectedLesson?.subjectKey ?: activeBook?.subjectKey ?: "AI",
                        cornerRadius = 11.dp,
                        targetWidthPx = 220,
                        modifier = Modifier.size(width = 56.dp, height = 76.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "GENERATION STUDIO • INDEXER2",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduHeroKicker
                        )
                        Text(
                            text = stringResource(R.string.studio_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = EduWhite
                        )
                        Text(
                            text = selectedLesson?.title ?: stringResource(R.string.studio_pick_lesson),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduYellow,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedLesson != null) {
                            Text(
                                text = "${selectedLesson.grade} • ${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}" +
                                    (selectedLesson.pageRange?.let { " • ص $it" } ?: "") +
                                    " • $draftsCount مسودة",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduHeroSub,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasWorkspaceLessons) {
                        Button(
                            onClick = onOpenLessonSwitcher,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EduYellow,
                                contentColor = EduInk
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("studio_switch_lesson_button")
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تبديل الدرس", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenPromptSourcesDialog,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_prompt_sources_button")
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = EduWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مصادر البرومبت والمخططات", style = MaterialTheme.typography.labelMedium, color = EduWhite)
                    }
                    if (onPreviewLessonPdf != null) {
                        OutlinedButton(
                            onClick = onPreviewLessonPdf,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.45f)),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = EduWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("عرض PDF", style = MaterialTheme.typography.labelMedium, color = EduWhite)
                        }
                    }
                    if (selectedLesson != null) {
                        OutlinedButton(
                            onClick = onOpenStudentStudy,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("studio_student_study_button")
                        ) {
                            Icon(Icons.Rounded.School, contentDescription = null, tint = EduWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("معاينة الطالب", style = MaterialTheme.typography.labelMedium, color = EduWhite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EduGreen else Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) EduWhite else EduMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudioBookQuickReferenceCard(
    book: BookRef?,
    onPreviewFullBookPdf: (() -> Unit)?,
    onOpenPromptSourcesDialog: () -> Unit
) {
    if (book == null) return
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BookCoverImage(
                coverPath = book.coverPath,
                bookTitle = book.title,
                subjectKey = book.subjectKey,
                cornerRadius = 8.dp,
                targetWidthPx = 180,
                modifier = Modifier.size(width = 44.dp, height = 60.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, color = EduInk)
                Text("${book.subjectKey} • ${book.lessonsCount} درس", style = MaterialTheme.typography.bodySmall, color = EduMuted)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onPreviewFullBookPdf != null) {
                OutlinedButton(
                    onClick = onPreviewFullBookPdf,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EduGreen),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF الكتاب", color = EduGreenDark, style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedButton(
                onClick = onOpenPromptSourcesDialog,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduPurple.copy(alpha = 0.5f)),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = EduPurple, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مصادر البرومبت", color = EduPurple, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractiveActivityFormattedPreview(activity: InteractiveActivityModel) {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Text(activity.title, style = MaterialTheme.typography.titleMedium, color = EduInk)
        if (activity.objective.isNotBlank()) {
            Text("الهدف: " + activity.objective, style = MaterialTheme.typography.bodyMedium, color = EduMuted)
        }
        activity.steps.forEachIndexed { index, step ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduFieldBg,
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المحطة " + (index + 1) + " • " + step.title, style = MaterialTheme.typography.titleSmall, color = EduGreenDark)
                    if (step.conceptTag.isNotBlank()) {
                        Text("المفهوم: " + step.conceptTag, style = MaterialTheme.typography.labelSmall, color = EduPurple)
                    }
                    Text(step.body, style = MaterialTheme.typography.bodyMedium, color = EduInk)
                    if (step.checkpointQuestion.isNotBlank()) {
                        Text("تحقق من فهمك", style = MaterialTheme.typography.labelLarge, color = EduGreenDark)
                        Text(step.checkpointQuestion, style = MaterialTheme.typography.bodyMedium, color = EduInk)
                        step.checkpointOptions.forEachIndexed { optionIndex, option ->
                            Text(('أ'.code + optionIndex).toChar().toString() + ") " + option, style = MaterialTheme.typography.bodySmall, color = EduMuted)
                        }
                        if (step.checkpointAnswer.isNotBlank()) {
                            Text("الإجابة: " + step.checkpointAnswer, style = MaterialTheme.typography.bodySmall, color = EduGreenDark)
                        }
                    }
                }
            }
        }
        if (activity.orderingItems.isNotEmpty()) {
            Text("مهمة الترتيب", style = MaterialTheme.typography.titleSmall, color = EduGreenDark)
            activity.orderingItems.forEachIndexed { index, item ->
                Text((index + 1).toString() + ". " + item, style = MaterialTheme.typography.bodyMedium, color = EduInk)
            }
        }
        if (activity.matchingPairs.isNotEmpty()) {
            Text("مهمة المطابقة", style = MaterialTheme.typography.titleSmall, color = EduGreenDark)
            activity.matchingPairs.forEach { pair ->
                Text(pair.left + "  ←  " + pair.right, style = MaterialTheme.typography.bodyMedium, color = EduInk)
            }
        }
    }
}

@Composable
private fun StudioGenerationControlPanel(
    mode: StudioGenerationMode,
    operation: StudioGenerationOperation,
    books: List<BookRef>,
    lessons: List<LessonRef>,
    selectedBookKey: String,
    onModeChange: (StudioGenerationMode) -> Unit,
    onOperationChange: (StudioGenerationOperation) -> Unit,
    onSelectBook: (String) -> Unit
) {
    val selectedBook = books.firstOrNull { it.bookKey == selectedBookKey }
    val subjectBooks = books.distinctBy { it.subjectKey + "|" + it.componentKey.orEmpty() }
    val lessonCount = selectedBook?.let { selected ->
        lessons.count { it.subjectKey == selected.subjectKey && it.componentKey == selected.componentKey }
    } ?: 0
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Text("طريقة التوليد", style = MaterialTheme.typography.titleMedium, color = EduInk)
        Text(
            "اختر نطاق العمل. جميع الأنماط تستخدم مسار الذكاء الاصطناعي نفسه وتحفظ النتائج كمسودات.",
            style = MaterialTheme.typography.bodySmall,
            color = EduMuted
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            StudioModeChoice(
                title = "محتوى درس واحد",
                description = "احتفظ بالتوليد الفردي الحالي واختر نوع المحتوى من قائمة المهام أدناه.",
                selected = mode == StudioGenerationMode.SINGLE_LESSON,
                onClick = { onModeChange(StudioGenerationMode.SINGLE_LESSON) }
            )
            StudioModeChoice(
                title = "بنك أسئلة لجميع دروس المادة",
                description = "ينشئ بنك الأسئلة لكل درس، ويستخرج المفاهيم داخلياً عند الحاجة لربط الأسئلة بها.",
                selected = mode == StudioGenerationMode.BULK_QUESTIONS,
                onClick = { onModeChange(StudioGenerationMode.BULK_QUESTIONS) }
            )
            StudioModeChoice(
                title = "كامل محتوى المادة",
                description = "يجهّز النص والمفاهيم والأهداف وأسئلة المصدر وبنك AI والشرح التفاعلي والأنشطة والتقييم والتقوية.",
                selected = mode == StudioGenerationMode.BULK_FULL_CONTENT,
                onClick = { onModeChange(StudioGenerationMode.BULK_FULL_CONTENT) }
            )
        }
        if (mode != StudioGenerationMode.SINGLE_LESSON) {
            Text("المادة المستهدفة", style = MaterialTheme.typography.labelLarge, color = EduInk)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(subjectBooks, key = { it.subjectKey + "|" + it.componentKey.orEmpty() }) { book ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedBook?.subjectKey == book.subjectKey && selectedBook?.componentKey == book.componentKey) EduMint else EduFieldBg,
                        border = BorderStroke(1.dp, if (selectedBook?.subjectKey == book.subjectKey && selectedBook?.componentKey == book.componentKey) EduGreen else EduLine),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelectBook(book.bookKey) }
                    ) {
                        Text(
                            book.subjectName?.takeIf { it.isNotBlank() } ?: book.title,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedBook?.subjectKey == book.subjectKey && selectedBook?.componentKey == book.componentKey) EduGreenDark else EduInk
                        )
                    }
                }
            }
            Text(
                text = if (selectedBook != null) {
                    (selectedBook.subjectName?.takeIf { it.isNotBlank() } ?: selectedBook.title) + " • " + lessonCount + " درس"
                } else {
                    "اختر مادة من القائمة أعلاه"
                },
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        }
        Text("العملية", style = MaterialTheme.typography.labelLarge, color = EduInk)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StudioModeChoiceCompact("إنشاء", operation == StudioGenerationOperation.GENERATE) {
                onOperationChange(StudioGenerationOperation.GENERATE)
            }
            StudioModeChoiceCompact("تحديث", operation == StudioGenerationOperation.UPDATE) {
                onOperationChange(StudioGenerationOperation.UPDATE)
            }
            StudioModeChoiceCompact("مراجعة", operation == StudioGenerationOperation.REVIEW) {
                onOperationChange(StudioGenerationOperation.REVIEW)
            }
        }
        Text(
            text = when (operation) {
                StudioGenerationOperation.GENERATE -> "الإنشاء يتجاوز العناصر التي لها مسودة سابقة؛ استخدم التحديث لإنشاء إصدار جديد."
                StudioGenerationOperation.UPDATE -> "التحديث يستخدم المحتوى السابق كسياق ويحفظ إصداراً جديداً دون حذف النسخة القديمة."
                StudioGenerationOperation.REVIEW -> "المراجعة تنتج تقرير تدقيق منفصلاً ولا تعدّل المحتوى الذي تتم مراجعته."
            },
            style = MaterialTheme.typography.bodySmall,
            color = EduMuted
        )
    }
}

@Composable
private fun StudioModeChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EduMint else EduFieldBg,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EduGreen else EduLine),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) EduGreenDark else EduInk)
            Text(description, style = MaterialTheme.typography.bodySmall, color = EduMuted)
        }
    }
}

@Composable
private fun StudioModeChoiceCompact(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) EduGreen else EduFieldBg,
        border = BorderStroke(1.dp, if (selected) EduGreen else EduLine),
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) EduWhite else EduInk
        )
    }
}

@Composable
private fun StudioLessonSwitcherDialog(
    books: List<BookRef>,
    lessons: List<LessonRef>,
    selectedLesson: LessonRef?,
    onSelectLesson: (LessonRef) -> Unit,
    onDismiss: () -> Unit
) {
    var activeBookKey by remember(books, selectedLesson) {
        mutableStateOf(selectedLesson?.bookKey ?: books.firstOrNull()?.bookKey.orEmpty())
    }
    var query by remember { mutableStateOf("") }

    val filtered = remember(lessons, activeBookKey, query) {
        val q = query.trim()
        lessons.filter { lesson ->
            val matchBook = activeBookKey.isBlank() || lesson.bookKey == activeBookKey
            val matchQuery = q.isBlank() ||
                lesson.title.contains(q, ignoreCase = true) ||
                lesson.unitTitle.contains(q, ignoreCase = true) ||
                lesson.lessonKey.contains(q, ignoreCase = true)
            matchBook && matchQuery
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(22.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = EduGreenDark, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = EduWhite)
                        Text(
                            text = "اختيار الكتاب والدرس في الاستوديو",
                            style = MaterialTheme.typography.titleMedium,
                            color = EduWhite,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "إغلاق", tint = EduWhite)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (books.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            books.forEach { b ->
                                EduPill(
                                    text = b.title,
                                    active = activeBookKey == b.bookKey,
                                    onClick = { activeBookKey = b.bookKey }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = EduMuted) },
                        placeholder = { Text("ابحث عن درس أو وحدة...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduWhite,
                            unfocusedContainerColor = EduWhite,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { "${it.bookKey}_${it.unitKey}_${it.lessonKey}" }) { lesson ->
                        val isCurrent = selectedLesson?.bookKey == lesson.bookKey &&
                            selectedLesson.unitKey == lesson.unitKey &&
                            selectedLesson.lessonKey == lesson.lessonKey
                        EduPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectLesson(lesson) },
                            containerColor = if (isCurrent) EduMint else EduWhite,
                            borderColor = if (isCurrent) EduGreen else EduLine
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = if (!lesson.pdfPath.isNullOrBlank()) EduGreenDark else EduMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lesson.title, style = MaterialTheme.typography.titleSmall, color = EduInk)
                                    Text(
                                        text = "${lesson.unitTitle}" + (lesson.pageRange?.let { " • ص $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduMuted
                                    )
                                }
                                if (isCurrent) {
                                    Icon(Icons.Rounded.Check, contentDescription = "الدرس النشط", tint = EduGreenDark)
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
