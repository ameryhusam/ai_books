package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.AiGenerationService
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.local.EduMiniDatabase
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.EduMiniRepository
import com.example.data.security.ProviderStorage
import com.example.data.workspace.DirectWorkspaceBinder
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.AppPage
import com.example.model.UserRole
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutFromFirebase
import com.example.ui.components.BookCoverPreviewDialog
import com.example.ui.components.GlobalSearchDialog
import com.example.ui.components.InteractiveJsonActivityDialog
import com.example.ui.components.InteractivePackagePreviewDialog
import com.example.ui.components.PdfBookSplitterDialog
import com.example.ui.components.PdfReaderDialog
import com.example.ui.components.PreImportPreviewDialog
import com.example.ui.components.PromptSourcesAndSchemasDialog
import com.example.ui.components.SingleGradePickerDialog
import com.example.ui.components.StudentStudyDialog
import com.example.ui.screens.ConceptReinforcementQuizDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LearnerMasteryScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.QuestionBankScreen
import com.example.ui.screens.RoleEntryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMiniTheme
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

@Composable
fun EduMiniApp(
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    EduMiniTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    onAuthSuccess = { /* authStateFlow emits signed-in user automatically */ }
                )
            } else {
                key(user.uid) {
                    val context = LocalContext.current
                    val credentialManager = remember(context) { CredentialManager.create(context) }
                    val scope = rememberCoroutineScope()
                    val viewModel: MainViewModel = viewModel(
                        key = user.uid,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                                val dbId = app.getString(R.string.firestore_database_id)
                                val firestoreDb = FirebaseFirestore.getInstance(dbId)
                                val repository = EduMiniRepository(
                                    dao = EduMiniDatabase.getInstance(app).eduMiniDao(),
                                    providerStorage = ProviderStorage(app),
                                    workspaceImporter = WorkspaceImporter(app),
                                    aiGenerationService = AiGenerationService(app),
                                    pdfBookSplitter = PdfBookSplitter(app),
                                    interactivePackageImporter = InteractivePackageImporter(app),
                                    directWorkspaceBinder = DirectWorkspaceBinder(app),
                                    appContext = app
                                )
                                val firestoreSync = FirestoreSyncRepository(firestoreDb, auth)
                                MainViewModel(
                                    repository = repository,
                                    firestoreSync = firestoreSync,
                                    currentUserId = user.uid,
                                    currentUserEmail = user.email,
                                    currentUserDisplayName = user.displayName
                                )
                            }
                        }
                    )
                    AuthenticatedEduMiniContent(
                        viewModel = viewModel,
                        userEmail = user.email,
                        userDisplayName = user.displayName,
                        onSignOut = { signOutFromFirebase(credentialManager, scope) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedEduMiniContent(
    viewModel: MainViewModel,
    userEmail: String?,
    userDisplayName: String?,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboardState by viewModel.studentDashboardState.collectAsStateWithLifecycle()
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
    val questionStats by viewModel.questionMasteryStats.collectAsStateWithLifecycle()
    val conceptSummaries by viewModel.conceptMasterySummaries.collectAsStateWithLifecycle()
    val cloudDraftsState by viewModel.cloudDraftsState.collectAsStateWithLifecycle()
    val cloudProgressState by viewModel.cloudProgressState.collectAsStateWithLifecycle()

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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.inspectDirectFolderUri(uri)
        }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

                if (state.userRole == UserRole.ENTRY) {
                    RoleEntryScreen(
                        workspace = state.workspace,
                        isBusy = state.isBusy,
                        onSelectRole = viewModel::selectRole,
                        onBindDirectFolder = { folderPickerLauncher.launch(null) }
                    )
                } else {
                    val isSecondaryTab = when (state.userRole) {
                        UserRole.LEARNER -> state.page == AppPage.LEARNER_MASTERY
                        UserRole.ADMIN -> state.page == AppPage.STUDIO ||
                            state.page == AppPage.QUIZ_BANK ||
                            state.page == AppPage.SETTINGS
                        UserRole.ENTRY -> false
                    }
                    BackHandler(enabled = isSecondaryTab) {
                        val fallback = if (state.userRole == UserRole.LEARNER) AppPage.DASHBOARD else AppPage.LIBRARY
                        viewModel.navigateTo(fallback)
                    }

                    Scaffold(
                        containerColor = EduBg,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            EduTopBar(
                                userRole = state.userRole,
                                hasWorkspace = state.workspace != null,
                                onOpenSearch = viewModel::openGlobalSearch,
                                onOpenSettings = { viewModel.navigateTo(AppPage.SETTINGS) },
                                onSwitchRole = viewModel::switchToRoleEntry
                            )
                        },
                        bottomBar = {
                            if (!isWide) {
                                EduBottomNavigationBar(
                                    userRole = state.userRole,
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
                                    userRole = state.userRole,
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
                                            userRole = state.userRole,
                                            dashboardState = dashboardState,
                                            conceptMasterySummaries = conceptSummaries,
                                            activeLearner = state.activeLearner,
                                            availableLearners = state.availableLearners,
                                            selectedBookFilterKey = dashboardState.selectedBookKey,
                                            bookmarkedLessons = bookmarkedLessons,
                                            bookCoversByKey = bookCoversByKey,
                                            onSelectLearner = viewModel::selectLearnerProfile,
                                            onAddLearner = viewModel::addLearnerProfile,
                                            onSelectBookFilter = viewModel::selectDashboardBookFilter,
                                            onSelectLessonFilter = viewModel::selectDashboardLessonFilter,
                                            onToggleUnitExpanded = viewModel::toggleDashboardUnitExpanded,
                                            onToggleAllUnitsExpanded = viewModel::toggleExpandCollapseAllDashboardUnits,
                                            onDismissCompletedUnitBanner = viewModel::dismissCompletedUnitBanner,
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
                                            onLaunchConceptReinforcement = viewModel::launchConceptReinforcement,
                                            onOpenGlobalSearch = viewModel::openGlobalSearch,
                                            onBindDirectFolder = { folderPickerLauncher.launch(null) },
                                            onOpenLibrary = { viewModel.navigateTo(AppPage.LIBRARY) }
                                        )

                                        AppPage.LEARNER_MASTERY -> LearnerMasteryScreen(
                                            concepts = conceptSummaries,
                                            questions = workspaceQuestions,
                                            questionStats = questionStats,
                                            totalPoints = dashboardSummary.totalPoints,
                                            onLaunchReinforcement = viewModel::launchConceptReinforcement,
                                            activeLearner = state.activeLearner
                                        )

                                        AppPage.LIBRARY -> LibraryScreen(
                                            workspace = state.workspace,
                                            workspaces = state.workspaces,
                                            selectedBookKey = state.selectedBookKey,
                                            selectedLesson = state.selectedLesson,
                                            activeProvidersCount = state.providers.count { it.enabled },
                                            diskUsageBytes = state.activeWorkspaceDiskBytes,
                                            bookProgress = dashboardSummary.bookProgress,
                                            cloudDraftsState = cloudDraftsState,
                                            cloudProgressState = cloudProgressState,
                                            onSyncWorkspaceToCloud = viewModel::syncWorkspaceToCloud,
                                            onBindDirectFolder = { folderPickerLauncher.launch(null) },
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
                                            cloudDraftsState = cloudDraftsState,
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
                                            onCancelGeneration = viewModel::cancelActiveGeneration,
                                            onSaveEditedDraft = viewModel::saveEditedDraftVersion,
                                            onSaveToQuestionBank = viewModel::saveGeneratedDraftToQuestionBank,
                                            onLaunchInteractiveActivity = viewModel::launchInteractiveActivity,
                                            onPublishInteractiveBundle = viewModel::buildAndPublishInteractiveBundle,
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
                                            onLoadCloudDraft = viewModel::loadCloudDraftIntoEditor,
                                            onApproveCloudDraft = viewModel::approveCloudDraft,
                                            onDeleteCloudDraft = viewModel::deleteCloudDraft,
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
                                            questionStats = questionStats,
                                            selectedLesson = state.selectedLesson,
                                            hasWorkspace = state.workspace != null,
                                            onAddManualQuestion = viewModel::addManualQuestion,
                                            onUpdateQuestion = viewModel::updateQuestion,
                                            onToggleReviewed = viewModel::toggleQuestionReviewed,
                                            onDeleteQuestion = viewModel::deleteQuestion,
                                            onQuizCompleted = viewModel::recordQuizCompleted,
                                            onRecordQuestionAttempts = viewModel::recordStandaloneQuestionAttempts,
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
                                            userEmail = userEmail,
                                            userDisplayName = userDisplayName,
                                            cloudDraftsState = cloudDraftsState,
                                            cloudProgressState = cloudProgressState,
                                            onSyncWorkspaceToCloud = viewModel::syncWorkspaceToCloud,
                                            onSignOut = onSignOut,
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
                }

                state.directInspection?.let { inspection ->
                    SingleGradePickerDialog(
                        inspection = inspection,
                        isBusy = state.isBusy,
                        onConfirmGrade = viewModel::confirmDirectGradeBinding,
                        onDismiss = viewModel::dismissDirectInspection
                    )
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
                    val allLessons = state.workspace?.lessons.orEmpty()
                    val currentIdx = remember(allLessons, studyLesson) {
                        allLessons.indexOfFirst {
                            it.bookKey == studyLesson.bookKey &&
                                it.unitKey == studyLesson.unitKey &&
                                it.lessonKey == studyLesson.lessonKey
                        }
                    }
                    val hasNextLesson = currentIdx >= 0 && currentIdx + 1 < allLessons.size
                    StudentStudyDialog(
                        lesson = studyLesson,
                        drafts = lessonDrafts,
                        packages = lessonPackages,
                        lessonQuestions = lessonQuestions,
                        questionStats = questionStats,
                        isCompleted = isCompleted,
                        hasNextLesson = hasNextLesson,
                        lastQuizResult = state.lastLessonQuizResult,
                        showAdminShortcuts = state.userRole == UserRole.ADMIN,
                        onOpenGenerateQuestions = { viewModel.openLessonQuestionsInStudio(studyLesson) },
                        onSubmitLessonQuiz = { answers ->
                            viewModel.submitLessonQuiz(studyLesson, lessonQuestions, answers)
                        },
                        onOpenNextLesson = { viewModel.openNextLessonInStudy(studyLesson) },
                        onInteractiveXpEarned = viewModel::recordStudentInteractiveCompletion,
                        onInteractiveQuestionAnswered = { prompt, conceptTag, selectedAnswer, correctAnswer, isCorrect, options, sourceMode ->
                            viewModel.recordInteractiveQuestionAttempt(
                                lesson = studyLesson,
                                prompt = prompt,
                                conceptTag = conceptTag,
                                selectedAnswer = selectedAnswer,
                                correctAnswer = correctAnswer,
                                isCorrect = isCorrect,
                                options = options,
                                sourceMode = sourceMode
                            )
                        },
                        onDismiss = viewModel::closeStudentStudy
                    )
                }

                if (state.reinforcementQuestions.isNotEmpty()) {
                    ConceptReinforcementQuizDialog(
                        title = state.reinforcementConceptTitle ?: "جلسة تقوية",
                        questions = state.reinforcementQuestions,
                        questionStats = questionStats,
                        onRecordAttempts = { qs, ans ->
                            viewModel.recordStandaloneQuestionAttempts(qs, ans, "reinforcement")
                        },
                        onDismiss = viewModel::dismissReinforcementQuiz
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

@Composable
private fun EduTopBar(
    userRole: UserRole,
    hasWorkspace: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit
) {
    val isAdmin = userRole == UserRole.ADMIN
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isAdmin) EduLilac else EduMint,
                border = BorderStroke(1.dp, if (isAdmin) EduPurple.copy(alpha = 0.4f) else EduGreen.copy(alpha = 0.4f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onSwitchRole)
                    .testTag("top_role_switch_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.School,
                        contentDescription = "تبديل المسار",
                        tint = if (isAdmin) EduPurple else EduGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = userRole.labelAr,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAdmin) EduPurple else EduGreenDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (hasWorkspace) {
                Spacer(modifier = Modifier.width(6.dp))
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
            }
            if (isAdmin) {
                Spacer(modifier = Modifier.width(6.dp))
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
}

@Composable
private fun EduBottomNavigationBar(
    userRole: UserRole,
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = EduGreenDark,
        selectedTextColor = EduGreenDark,
        indicatorColor = EduMint,
        unselectedIconColor = EduMuted,
        unselectedTextColor = EduMuted
    )

    Column {
        HorizontalDivider(color = EduLine, thickness = 1.dp)
        NavigationBar(
            containerColor = EduWhite,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 0.dp
        ) {
            if (userRole == UserRole.LEARNER) {
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
                    colors = navColors,
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = currentPage == AppPage.LEARNER_MASTERY,
                    onClick = { onSelectPage(AppPage.LEARNER_MASTERY) },
                    icon = {
                        Icon(
                            imageVector = if (currentPage == AppPage.LEARNER_MASTERY) {
                                Icons.Rounded.Psychology
                            } else {
                                Icons.Outlined.Psychology
                            },
                            contentDescription = stringResource(R.string.nav_mastery)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_mastery),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = navColors,
                    modifier = Modifier.testTag("nav_mastery")
                )
            } else {
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
                    colors = navColors,
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
                    colors = navColors,
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
                    colors = navColors,
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
                    colors = navColors,
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    }
}

@Composable
private fun EduNavigationRail(
    userRole: UserRole,
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    val railColors = NavigationRailItemDefaults.colors(
        selectedIconColor = EduGreenDark,
        selectedTextColor = EduGreenDark,
        indicatorColor = EduMint,
        unselectedIconColor = EduMuted,
        unselectedTextColor = EduMuted
    )

    NavigationRail(
        containerColor = EduWhite,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.size(12.dp))
        if (userRole == UserRole.LEARNER) {
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
                colors = railColors
            )
            NavigationRailItem(
                selected = currentPage == AppPage.LEARNER_MASTERY,
                onClick = { onSelectPage(AppPage.LEARNER_MASTERY) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.LEARNER_MASTERY) {
                            Icons.Rounded.Psychology
                        } else {
                            Icons.Outlined.Psychology
                        },
                        contentDescription = stringResource(R.string.nav_mastery)
                    )
                },
                label = { Text(stringResource(R.string.nav_mastery)) },
                colors = railColors
            )
        } else {
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
                colors = railColors
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
                colors = railColors
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
                colors = railColors
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
                colors = railColors
            )
        }
    }
}
