package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AppPage
import com.example.ui.components.BookCoverPreviewDialog
import com.example.ui.components.GlobalSearchDialog
import com.example.ui.components.InteractiveJsonActivityDialog
import com.example.ui.components.InteractivePackagePreviewDialog
import com.example.ui.components.PdfBookSplitterDialog
import com.example.ui.components.PdfReaderDialog
import com.example.ui.components.PreImportPreviewDialog
import com.example.ui.components.PromptSourcesAndSchemasDialog
import com.example.ui.components.StudentStudyDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.QuestionBankScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMiniTheme
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduWhite
import kotlinx.coroutines.delay

@Composable
fun EduMiniApp(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lessonDrafts by viewModel.lessonDrafts.collectAsStateWithLifecycle()
    val lessonPackages by viewModel.lessonPackages.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val workspaceQuestions by viewModel.workspaceQuestions.collectAsStateWithLifecycle()
    val workspaceDrafts by viewModel.workspaceDrafts.collectAsStateWithLifecycle()
    val workspaceAssessments by viewModel.workspaceAssessments.collectAsStateWithLifecycle()
    val workspaceAttempts by viewModel.workspaceAttempts.collectAsStateWithLifecycle()
    val workspaceTeacherNotes by viewModel.workspaceTeacherNotes.collectAsStateWithLifecycle()
    val currentTeacherNote by viewModel.currentTeacherNote.collectAsStateWithLifecycle()
    val promptTemplates by viewModel.promptTemplates.collectAsStateWithLifecycle()

    val bookmarkedLessons = remember(state.workspace, workspaceTeacherNotes) {
        val lessons = state.workspace?.lessons.orEmpty()
        val bookmarkedKeys = workspaceTeacherNotes
            .filter { it.bookmarked }
            .map { Triple(it.bookKey, it.unitKey, it.lessonKey) }
            .toSet()
        lessons.filter { Triple(it.bookKey, it.unitKey, it.lessonKey) in bookmarkedKeys }
    }

    val bookCoversByKey = remember(state.workspace) {
        state.workspace?.books?.associate { it.bookKey to it.coverPath }.orEmpty()
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.inspectWorkspaceZip(uri)
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportIndexer2Zip(uri)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportFullBackupJson(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreFullBackupJson(uri)
        }
    }

    val exportAssessmentMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportPendingAssessmentMarkdown(uri)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.openBookPdfForSplitting(uri)
        }
    }

    val standalonePdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.openLocalPdfUriPreview(uri)
        }
    }

    val packagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importInteractivePackage(uri)
        }
    }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2600L)
            viewModel.dismissToast()
        }
    }

    val haptic = LocalHapticFeedback.current

    EduMiniTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 720.dp

                Scaffold(
                    containerColor = EduBg,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        EduTopBar(
                            hasWorkspace = state.workspace != null,
                            onOpenSearch = viewModel::openGlobalSearch,
                            onOpenSettings = { viewModel.navigateTo(AppPage.SETTINGS) }
                        )
                    },
                    bottomBar = {
                        if (!isWide) {
                            EduBottomNavigationBar(
                                currentPage = state.page,
                                onSelectPage = { page ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.navigateTo(page)
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWide) {
                            EduNavigationRail(
                                currentPage = state.page,
                                onSelectPage = viewModel::navigateTo
                            )
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AnimatedVisibility(visible = state.isBusy) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = EduGreen,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.busy_working),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduMuted
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (state.page) {
                                    AppPage.DASHBOARD -> DashboardScreen(
                                        summary = dashboardSummary,
                                        bookmarkedLessons = bookmarkedLessons,
                                        bookCoversByKey = bookCoversByKey,
                                        onOpenLessonInStudio = viewModel::selectLessonAndOpenStudio,
                                        onOpenStudentStudy = viewModel::openStudentStudy,
                                        onPreviewLessonPdf = { lesson ->
                                            lesson.pdfPath?.takeIf { it.isNotBlank() }?.let { pdf ->
                                                viewModel.openPdfPreview(
                                                    path = pdf,
                                                    title = lesson.title,
                                                    subtitle = "${lesson.bookTitle} • ${lesson.unitTitle}"
                                                )
                                            }
                                        },
                                        onToggleLessonCompleted = viewModel::toggleLessonCompletion,
                                        onOpenGlobalSearch = viewModel::openGlobalSearch,
                                        onPickBookPdf = {
                                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        onPickZip = {
                                            zipPickerLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream"
                                                )
                                            )
                                        },
                                        onOpenLibrary = { viewModel.navigateTo(AppPage.LIBRARY) }
                                    )

                                    AppPage.LIBRARY -> LibraryScreen(
                                        workspace = state.workspace,
                                        workspaces = state.workspaces,
                                        selectedBookKey = state.selectedBookKey,
                                        selectedLesson = state.selectedLesson,
                                        activeProvidersCount = state.providers.count { it.enabled },
                                        diskUsageBytes = state.activeWorkspaceDiskBytes,
                                        bookProgress = dashboardSummary.bookProgress,
                                        onPickZip = {
                                            zipPickerLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream"
                                                )
                                            )
                                        },
                                        onPickBookPdf = {
                                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        onPickStandalonePdf = {
                                            standalonePdfPickerLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        onSelectWorkspace = viewModel::selectWorkspace,
                                        onDeleteWorkspace = viewModel::deleteWorkspace,
                                        onSelectBook = viewModel::selectBook,
                                        onPreviewBookCover = viewModel::openBookCoverPreview,
                                        onOpenLesson = viewModel::selectLessonAndOpenStudio,
                                        onOpenStudentStudy = viewModel::openStudentStudy,
                                        onPreviewPdf = viewModel::openPdfPreview,
                                        onOpenGlobalSearch = viewModel::openGlobalSearch,
                                        onExportWorkspace = {
                                            val grade = state.workspace?.gradeKey ?: "workspace"
                                            exportZipLauncher.launch("edu-mini-$grade-indexer2.zip")
                                        },
                                        onExportBackupJson = {
                                            val grade = state.workspace?.gradeKey ?: "workspace"
                                            exportBackupLauncher.launch("edu-mini-$grade-backup.json")
                                        },
                                        onRestoreBackupJson = {
                                            restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        }
                                    )

                                    AppPage.STUDIO -> StudioScreen(
                                        selectedLesson = state.selectedLesson,
                                        workspaceBooks = state.workspace?.books.orEmpty(),
                                        workspaceLessons = state.workspace?.lessons.orEmpty(),
                                        task = state.task,
                                        questionCount = state.questionCount,
                                        questionStyle = state.questionStyle,
                                        questionDifficulty = state.questionDifficulty,
                                        attachPdfPages = state.attachPdfPages,
                                        sourceText = state.sourceText,
                                        extraInstructions = state.extraInstructions,
                                        generatedResult = state.generatedResult,
                                        packageSourcePages = state.packageSourcePages,
                                        lessonDrafts = lessonDrafts,
                                        lessonPackages = lessonPackages,
                                        teacherNote = currentTeacherNote,
                                        promptTemplates = promptTemplates,
                                        isBusy = state.isBusy,
                                        onBackToLibrary = { viewModel.navigateTo(AppPage.LIBRARY) },
                                        onSelectLesson = viewModel::selectLessonInStudio,
                                        onOpenPromptSourcesDialog = viewModel::openPromptSourcesDialog,
                                        onPreviewPdf = viewModel::openPdfPreview,
                                        onShowFeedback = viewModel::showToast,
                                        onSelectTask = viewModel::selectTask,
                                        onQuestionCountChange = viewModel::updateQuestionCount,
                                        onQuestionStyleChange = viewModel::updateQuestionStyle,
                                        onQuestionDifficultyChange = viewModel::updateQuestionDifficulty,
                                        onToggleAttachPdfPages = viewModel::toggleAttachPdfPages,
                                        onSourceChange = viewModel::updateSourceText,
                                        onExtraChange = viewModel::updateExtraInstructions,
                                        onGeneratedResultChange = viewModel::updateGeneratedResult,
                                        onPackageSourcePagesChange = viewModel::updatePackageSourcePages,
                                        onGenerate = viewModel::generateContent,
                                        onSaveEditedDraft = viewModel::saveEditedDraftVersion,
                                        onSaveToQuestionBank = viewModel::saveGeneratedDraftToQuestionBank,
                                        onLaunchInteractiveActivity = viewModel::launchInteractiveActivity,
                                        onOpenStudentStudy = {
                                            state.selectedLesson?.let(viewModel::openStudentStudy)
                                        },
                                        onSaveTeacherNote = viewModel::saveTeacherNote,
                                        onApplyPromptTemplate = { tpl ->
                                            viewModel.applyPromptTemplate(tpl, openStudio = false)
                                        },
                                        onApproveDraft = viewModel::approveDraft,
                                        onLoadDraft = viewModel::loadDraftIntoEditor,
                                        onDeleteDraft = viewModel::deleteDraft,
                                        onPickInteractivePackage = {
                                            packagePickerLauncher.launch(
                                                arrayOf(
                                                    "text/html",
                                                    "application/xhtml+xml",
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream"
                                                )
                                            )
                                        },
                                        onPreviewPackage = viewModel::openPackagePreview,
                                        onPublishPackage = viewModel::approveAndPublishPackage,
                                        onDeletePackage = viewModel::deletePackage
                                    )

                                    AppPage.QUIZ_BANK -> QuestionBankScreen(
                                        questions = workspaceQuestions,
                                        assessments = workspaceAssessments,
                                        attempts = workspaceAttempts,
                                        selectedLesson = state.selectedLesson,
                                        hasWorkspace = state.workspace != null,
                                        onAddManualQuestion = viewModel::addManualQuestion,
                                        onToggleReviewed = viewModel::toggleQuestionReviewed,
                                        onDeleteQuestion = viewModel::deleteQuestion,
                                        onQuizCompleted = viewModel::recordQuizCompleted,
                                        onCreateAssessment = viewModel::createAssessment,
                                        onDeleteAssessment = viewModel::deleteAssessment,
                                        onRecordAssessmentAttempt = viewModel::recordAssessmentAttempt,
                                        onExportAssessmentMarkdown = { assessment ->
                                            viewModel.prepareExportAssessmentMarkdown(assessment)
                                            val safeName = assessment.title
                                                .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
                                                .take(36)
                                                .ifBlank { "assessment" }
                                            exportAssessmentMdLauncher.launch("$safeName.md")
                                        },
                                        onOpenStudio = { viewModel.navigateTo(AppPage.STUDIO) }
                                    )

                                    AppPage.SETTINGS -> SettingsScreen(
                                        form = state.providerForm,
                                        providers = state.providers,
                                        recentRuns = state.recentRuns,
                                        promptTemplates = promptTemplates,
                                        onBackToLibrary = { viewModel.navigateTo(AppPage.LIBRARY) },
                                        onSelectProviderType = viewModel::updateProviderType,
                                        onLabelChange = viewModel::updateFormLabel,
                                        onModelChange = viewModel::updateFormModel,
                                        onFallbackModelsChange = viewModel::updateFormFallbackModels,
                                        onBaseUrlChange = viewModel::updateFormBaseUrl,
                                        onApiKeyChange = viewModel::updateFormApiKey,
                                        onToggleStrictModel = viewModel::toggleFormStrictModel,
                                        onAddOrUpdateProvider = viewModel::addOrUpdateProvider,
                                        onCancelEditProvider = viewModel::cancelEditProvider,
                                        onEditProvider = viewModel::startEditProvider,
                                        onTestFormProvider = viewModel::testFormProvider,
                                        onTestSavedProvider = viewModel::testSavedProvider,
                                        onToggleProvider = viewModel::toggleProvider,
                                        onMoveProvider = viewModel::moveProviderPriority,
                                        onDeleteProvider = viewModel::deleteProvider,
                                        onClearRecentRuns = viewModel::clearRecentRuns,
                                        onSavePromptTemplate = viewModel::savePromptTemplate,
                                        onDeletePromptTemplate = viewModel::deletePromptTemplate,
                                        onUseTemplateInStudio = { tpl ->
                                            viewModel.applyPromptTemplate(tpl, openStudio = true)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                state.stagedPreview?.let { preview ->
                    PreImportPreviewDialog(
                        preview = preview,
                        isBusy = state.isBusy,
                        onConfirmImport = viewModel::confirmStagedImport,
                        onDismiss = viewModel::dismissStagedPreview
                    )
                }

                state.pdfSplitter?.let { splitterState ->
                    PdfBookSplitterDialog(
                        state = splitterState,
                        isBusy = state.isBusy,
                        onDismiss = viewModel::dismissPdfSplitter,
                        onGradeChange = viewModel::updateSplitterGrade,
                        onSubjectChange = viewModel::updateSplitterSubject,
                        onBookTitleChange = viewModel::updateSplitterBookTitle,
                        onPagesPerLessonChange = viewModel::updateSplitterPagesPerLesson,
                        onUpdateSplit = viewModel::updateSplitLesson,
                        onAddSplit = viewModel::addSplitLesson,
                        onRemoveSplit = viewModel::removeSplitLesson,
                        onCreateWorkspace = viewModel::createWorkspaceFromPdfSplits
                    )
                }

                state.previewingPackage?.let { pkg ->
                    InteractivePackagePreviewDialog(
                        pkg = pkg,
                        studentMode = state.studentPreviewMode,
                        onToggleStudentMode = viewModel::toggleStudentPreviewMode,
                        onApproveAndPublish = { viewModel.approveAndPublishPackage(pkg) },
                        onDismiss = viewModel::closePackagePreview
                    )
                }

                state.activeInteractiveActivity?.let { activity ->
                    InteractiveJsonActivityDialog(
                        activity = activity,
                        onCompleted = viewModel::recordInteractiveActivityCompleted,
                        onDismiss = viewModel::dismissInteractiveActivity
                    )
                }

                state.studentStudyLesson?.let { studyLesson ->
                    val lessonQuestions = remember(workspaceQuestions, studyLesson) {
                        workspaceQuestions.filter {
                            it.bookKey == studyLesson.bookKey &&
                                it.unitKey == studyLesson.unitKey &&
                                it.lessonKey == studyLesson.lessonKey
                        }
                    }
                    val isCompleted = remember(dashboardSummary, studyLesson) {
                        dashboardSummary.unitPaths
                            .flatMap { it.nodes }
                            .firstOrNull {
                                it.lesson.bookKey == studyLesson.bookKey &&
                                    it.lesson.unitKey == studyLesson.unitKey &&
                                    it.lesson.lessonKey == studyLesson.lessonKey
                            }
                            ?.isCompleted == true
                    }
                    StudentStudyDialog(
                        lesson = studyLesson,
                        drafts = lessonDrafts,
                        packages = lessonPackages,
                        lessonQuestions = lessonQuestions,
                        isCompleted = isCompleted,
                        onToggleCompleted = { viewModel.toggleLessonCompletion(studyLesson, isCompleted) },
                        onQuizCompleted = viewModel::recordQuizCompleted,
                        onDismiss = viewModel::closeStudentStudy
                    )
                }

                state.previewingPdf?.let { pdf ->
                    PdfReaderDialog(
                        pdfPath = pdf.path,
                        title = pdf.title,
                        subtitle = pdf.subtitle,
                        onDismiss = viewModel::dismissPdfPreview
                    )
                }

                state.previewingBookCover?.let { book ->
                    val bookProg = remember(dashboardSummary, book) {
                        dashboardSummary.bookProgress.firstOrNull { it.book.bookKey == book.bookKey }
                    }
                    BookCoverPreviewDialog(
                        book = book,
                        progress = bookProg,
                        onOpenBookPdf = book.sourcePdfPath?.let { pdf ->
                            { viewModel.openPdfPreview(pdf, book.title, "${book.subjectKey} • الكتاب الكامل") }
                        },
                        onSelectBook = { viewModel.selectBook(book.bookKey) },
                        onDismiss = viewModel::dismissBookCoverPreview
                    )
                }

                if (state.promptSourcesOpen) {
                    PromptSourcesAndSchemasDialog(
                        selectedLesson = state.selectedLesson,
                        activeTask = state.task,
                        questionCount = state.questionCount,
                        sourceText = state.sourceText,
                        extraInstructions = state.extraInstructions,
                        onApplyBlueprint = viewModel::applyIndexer2Blueprint,
                        onLoadSampleIntoEditor = viewModel::loadSchemaSampleIntoEditor,
                        onShowFeedback = viewModel::showToast,
                        onDismiss = viewModel::dismissPromptSourcesDialog
                    )
                }

                if (state.globalSearchOpen) {
                    GlobalSearchDialog(
                        lessons = state.workspace?.lessons.orEmpty(),
                        drafts = workspaceDrafts,
                        questions = workspaceQuestions,
                        onSelectLesson = viewModel::handleSearchSelectLesson,
                        onSelectDraft = viewModel::handleSearchSelectDraft,
                        onSelectQuestion = viewModel::handleSearchSelectQuestion,
                        onDismiss = viewModel::closeGlobalSearch
                    )
                }

                AnimatedVisibility(
                    visible = state.toastMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 84.dp)
                ) {
                    state.toastMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = EduGreenDark,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = EduMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EduWhite
                                )
                            }
                        }
                    }
                }

                state.alert?.let { alert ->
                    AlertDialog(
                        onDismissRequest = viewModel::dismissAlert,
                        title = {
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk
                            )
                        },
                        text = {
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduInk
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissAlert) {
                                Text(
                                    text = stringResource(R.string.alert_ok),
                                    color = EduGreenDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        containerColor = EduWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun EduTopBar(
    hasWorkspace: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EduBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(13.dp),
                color = EduGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.headlineMedium,
                        color = EduWhite
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = EduInk
                )
                Text(
                    text = stringResource(R.string.brand_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            if (hasWorkspace) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EduWhite
                ) {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "البحث الشامل",
                            tint = EduGreenDark
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduWhite
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_settings_button),
                        tint = EduInk
                    )
                }
            }
        }
    }
}

@Composable
private fun EduBottomNavigationBar(
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    Column {
        HorizontalDivider(color = EduLine, thickness = 1.dp)
        NavigationBar(
            containerColor = EduWhite,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = currentPage == AppPage.DASHBOARD,
                onClick = { onSelectPage(AppPage.DASHBOARD) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.DASHBOARD) {
                            Icons.Rounded.Dashboard
                        } else {
                            Icons.Outlined.Dashboard
                        },
                        contentDescription = stringResource(R.string.nav_dashboard)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_dashboard),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_dashboard")
            )
            NavigationBarItem(
                selected = currentPage == AppPage.LIBRARY,
                onClick = { onSelectPage(AppPage.LIBRARY) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.LIBRARY) {
                            Icons.Rounded.LibraryBooks
                        } else {
                            Icons.Outlined.LibraryBooks
                        },
                        contentDescription = stringResource(R.string.nav_library)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_library),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_library")
            )
            NavigationBarItem(
                selected = currentPage == AppPage.STUDIO,
                onClick = { onSelectPage(AppPage.STUDIO) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.STUDIO) {
                            Icons.Rounded.AutoAwesome
                        } else {
                            Icons.Outlined.AutoAwesome
                        },
                        contentDescription = stringResource(R.string.nav_studio)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_studio),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_studio")
            )
            NavigationBarItem(
                selected = currentPage == AppPage.QUIZ_BANK,
                onClick = { onSelectPage(AppPage.QUIZ_BANK) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.QUIZ_BANK) {
                            Icons.Rounded.Quiz
                        } else {
                            Icons.Outlined.Quiz
                        },
                        contentDescription = stringResource(R.string.nav_quiz_bank)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_quiz_bank),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_quiz_bank")
            )
            NavigationBarItem(
                selected = currentPage == AppPage.SETTINGS,
                onClick = { onSelectPage(AppPage.SETTINGS) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.SETTINGS) {
                            Icons.Rounded.Settings
                        } else {
                            Icons.Outlined.Settings
                        },
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_settings")
            )
        }
    }
}

@Composable
private fun EduNavigationRail(
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    NavigationRail(
        containerColor = EduWhite,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.size(12.dp))
        NavigationRailItem(
            selected = currentPage == AppPage.DASHBOARD,
            onClick = { onSelectPage(AppPage.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.DASHBOARD) {
                        Icons.Rounded.Dashboard
                    } else {
                        Icons.Outlined.Dashboard
                    },
                    contentDescription = stringResource(R.string.nav_dashboard)
                )
            },
            label = { Text(stringResource(R.string.nav_dashboard)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
        NavigationRailItem(
            selected = currentPage == AppPage.LIBRARY,
            onClick = { onSelectPage(AppPage.LIBRARY) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.LIBRARY) {
                        Icons.Rounded.LibraryBooks
                    } else {
                        Icons.Outlined.LibraryBooks
                    },
                    contentDescription = stringResource(R.string.nav_library)
                )
            },
            label = { Text(stringResource(R.string.nav_library)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
        NavigationRailItem(
            selected = currentPage == AppPage.STUDIO,
            onClick = { onSelectPage(AppPage.STUDIO) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.STUDIO) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Outlined.AutoAwesome
                    },
                    contentDescription = stringResource(R.string.nav_studio)
                )
            },
            label = { Text(stringResource(R.string.nav_studio)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
        NavigationRailItem(
            selected = currentPage == AppPage.QUIZ_BANK,
            onClick = { onSelectPage(AppPage.QUIZ_BANK) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.QUIZ_BANK) {
                        Icons.Rounded.Quiz
                    } else {
                        Icons.Outlined.Quiz
                    },
                    contentDescription = stringResource(R.string.nav_quiz_bank)
                )
            },
            label = { Text(stringResource(R.string.nav_quiz_bank)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
        NavigationRailItem(
            selected = currentPage == AppPage.SETTINGS,
            onClick = { onSelectPage(AppPage.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.SETTINGS) {
                        Icons.Rounded.Settings
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = stringResource(R.string.nav_settings)
                )
            },
            label = { Text(stringResource(R.string.nav_settings)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
    }
}
