package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AnalyticsTracker
import com.example.data.ai.AiModelDefinition
import com.example.data.ai.AiModelCatalogDefaults
import com.example.data.ai.AiExecutionException
import com.example.data.ai.AiExecutionFailureKind
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.repository.ConceptMasteryEngine
import com.example.data.repository.EduMiniRepository
import com.example.data.repository.InteractiveActivityParser
import com.example.data.repository.LearningProgressAnalytics
import com.example.data.repository.LessonSequenceGate
import com.example.data.repository.QuestionBankEngine
import com.example.data.workspace.BookIndexAiResponseParser
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexDiscoveryMethod
import com.example.data.workspace.IndexSourceLayout
import com.example.data.workspace.RoomBookIndexProjection
import com.example.model.AppPage
import com.example.model.AssessmentAttemptRecord
import com.example.model.AssessmentKind
import com.example.model.AssessmentRecord
import com.example.model.AuthRoleCheckState
import com.example.model.AuthRoleDestination
import com.example.model.BookRef
import com.example.model.CloudDraft
import com.example.model.ConceptMasterySummary
import com.example.model.DashboardLessonFilter
import com.example.model.DashboardSummary
import com.example.model.DirectWorkspaceInspection
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.GenerationRunRecord
import com.example.model.GeneratedResult
import com.example.model.ImportedWorkspace
import com.example.model.InteractiveActivityModel
import com.example.model.InteractivePackageRecord
import com.example.model.LearnerProfile
import com.example.model.LearningProgressReport
import com.example.model.LessonProgress
import com.example.model.LessonQuizCompletionResult
import com.example.model.LessonRef
import com.example.model.LessonSplitDraft
import com.example.model.PdfBookInfo
import com.example.model.PromptBlueprintSpec
import com.example.model.PromptTemplateRecord
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.ProviderApiKeyNames
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.model.QuestionType
import com.example.model.StagedWorkspacePreview
import com.example.model.StudentDashboardState
import com.example.model.SyncUiState
import com.example.model.TaskType
import com.example.model.TeacherNoteRecord
import com.example.model.UnitCompletionStatus
import com.example.model.UserRole
import com.example.model.UserRoleResolution
import com.example.model.WorkspaceSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MainViewModel(
    private val repository: EduMiniRepository,
    private val firestoreSync: FirestoreSyncRepository,
    val currentUserId: String,
    val currentUserEmail: String? = null,
    val currentUserDisplayName: String? = null,
    private val isCloudAuthenticated: Boolean = false,
    private val analyticsTracker: AnalyticsTracker? = null
) : ViewModel() {

    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState(isCloudAuthenticated = isCloudAuthenticated))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _studentDashboardState = MutableStateFlow(StudentDashboardState())
    val studentDashboardState: StateFlow<StudentDashboardState> = _studentDashboardState.asStateFlow()

    val cloudDraftsState: StateFlow<SyncUiState<List<CloudDraft>>> = if (!isCloudAuthenticated) {
        flowOf<SyncUiState<List<CloudDraft>>>(SyncUiState.Success(emptyList()))
    } else {
        firestoreSync
            .observeDrafts(currentUserId)
            .map<List<CloudDraft>, SyncUiState<List<CloudDraft>>> { SyncUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cloud drafts", rootError)
                emit(SyncUiState.Error(rootError.message ?: "تعذر مزامنة المسودات السحابية"))
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = if (isCloudAuthenticated) SyncUiState.Loading else SyncUiState.Success(emptyList())
    )

    val cloudProgressState: StateFlow<SyncUiState<List<LessonProgress>>> = if (!isCloudAuthenticated) {
        flowOf<SyncUiState<List<LessonProgress>>>(SyncUiState.Success(emptyList()))
    } else {
        firestoreSync
            .observeProgress(currentUserId)
            .map<List<LessonProgress>, SyncUiState<List<LessonProgress>>> { SyncUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cloud progress", rootError)
                emit(SyncUiState.Error(rootError.message ?: "تعذر مزامنة تقدم الدروس السحابي"))
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = if (isCloudAuthenticated) SyncUiState.Loading else SyncUiState.Success(emptyList())
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val lessonDrafts: StateFlow<List<DraftRecord>> = _uiState
        .map { (it.workspace?.workspaceId.orEmpty()) to it.selectedLesson }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, lesson) ->
            if (lesson == null) flowOf(emptyList()) else repository.observeDraftsForLesson(wsId, lesson)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val lessonPackages: StateFlow<List<InteractivePackageRecord>> = _uiState
        .map { (it.workspace?.workspaceId.orEmpty()) to it.selectedLesson }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, lesson) ->
            if (lesson == null) flowOf(emptyList()) else repository.observePackagesForLesson(wsId, lesson)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardSummary: StateFlow<DashboardSummary> = _uiState
        .map { it.workspace to it.activeLearner.userId }
        .distinctUntilChanged()
        .flatMapLatest { (ws, userId) -> repository.observeDashboardSummary(ws, userId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardSummary()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceQuestions: StateFlow<List<QuestionItem>> = _uiState
        .map { it.workspace?.workspaceId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { wsId -> repository.observeQuestionsForWorkspace(wsId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceDrafts: StateFlow<List<DraftRecord>> = _uiState
        .map { (it.workspace?.workspaceId.orEmpty()) to (it.workspace?.rootPath.orEmpty()) }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, rootPath) ->
            repository.observeDraftsForWorkspace(wsId, rootPath)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceAssessments: StateFlow<List<AssessmentRecord>> = _uiState
        .map { it.workspace?.workspaceId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { wsId -> repository.observeAssessmentsForWorkspace(wsId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceAttempts: StateFlow<List<AssessmentAttemptRecord>> = _uiState
        .map { it.workspace?.workspaceId.orEmpty() to it.activeLearner.userId }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, userId) -> repository.observeAssessmentAttempts(wsId, userId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceQuestionAttempts: StateFlow<List<QuestionAttemptRecord>> = _uiState
        .map { it.workspace?.workspaceId.orEmpty() to it.activeLearner.userId }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, userId) -> repository.observeQuestionAttempts(wsId, userId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val learningProgressReport: StateFlow<LearningProgressReport> =
        combine(workspaceQuestions, workspaceQuestionAttempts, workspaceAttempts) { questions, questionAttempts, assessmentAttempts ->
            LearningProgressAnalytics.calculate(
                questions = questions,
                attempts = questionAttempts,
                assessments = assessmentAttempts
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LearningProgressReport()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceTeacherNotes: StateFlow<List<TeacherNoteRecord>> = _uiState
        .map { it.workspace?.workspaceId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { wsId -> repository.observeTeacherNotesForWorkspace(wsId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTeacherNote: StateFlow<TeacherNoteRecord?> = _uiState
        .map { (it.workspace?.workspaceId.orEmpty()) to it.selectedLesson }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, lesson) ->
            if (lesson == null) flowOf(null) else repository.observeTeacherNote(wsId, lesson)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLessonText: StateFlow<com.example.model.LessonTextRecord?> = _uiState
        .map { (it.workspace?.workspaceId.orEmpty()) to it.selectedLesson }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, lesson) ->
            if (lesson == null) flowOf(null) else repository.observeLessonText(wsId, lesson)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val studentStudyLessonText: StateFlow<com.example.model.LessonTextRecord?> = _uiState
        .map { (it.workspace?.workspaceId.orEmpty()) to it.studentStudyLesson }
        .distinctUntilChanged()
        .flatMapLatest { (wsId, lesson) ->
            if (lesson == null) flowOf(null) else repository.observeLessonText(wsId, lesson)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val promptTemplates: StateFlow<List<PromptTemplateRecord>> = repository
        .observePromptTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EduMiniRepository.DEFAULT_PROMPT_TEMPLATES
        )

    val questionMasteryStats: StateFlow<Map<String, QuestionMasteryStats>> =
        combine(workspaceQuestions, workspaceQuestionAttempts) { questions, attempts ->
            ConceptMasteryEngine.computeQuestionStats(questions, attempts)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val conceptMasterySummaries: StateFlow<List<ConceptMasterySummary>> =
        combine(workspaceQuestions, workspaceQuestionAttempts, _uiState.map { it.workspace }) { questions, attempts, ws ->
            val bookTitles = ws?.books?.associate { it.bookKey to it.title }.orEmpty()
            ConceptMasteryEngine.computeConceptSummaries(questions, attempts, bookTitles)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val profileJson = Json { ignoreUnknownKeys = true }

    init {
        loadLearnerProfiles()
        viewModelScope.launch {
            // A local seed is not an authority for a system-managed catalogue.
            // Without an authenticated account and a readable Firestore catalogue,
            // no model is offered for generation.
            _uiState.update { it.copy(aiModelCatalog = emptyList()) }
            if (isCloudAuthenticated) {
                val canManageCentralModels = runCatching { firestoreSync.isSystemAdmin() }
                    .getOrDefault(false)
                _uiState.update { it.copy(isSystemAdmin = canManageCentralModels) }
                runCatching {
                    var catalog = firestoreSync.fetchAiModelCatalog()
                    // Seed missing built-in entries for an explicitly authorized
                    // system administrator. Merge by provider + exact model ID so
                    // existing central settings and custom entries are never overwritten.
                    if (canManageCentralModels) {
                        val missingSeeds = AiModelCatalogDefaults.entries.filter { seed ->
                            catalog.none { it.provider == seed.provider && it.modelId == seed.modelId }
                        }
                        missingSeeds.forEach { seed ->
                            firestoreSync.upsertAiModelDefinition(seed)
                        }
                        if (missingSeeds.isNotEmpty()) {
                            catalog = firestoreSync.fetchAiModelCatalog()
                        }
                    }
                    catalog
                }
                    .onSuccess { catalog -> _uiState.update { it.copy(aiModelCatalog = catalog) } }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                aiModelCatalog = emptyList(),
                                alert = AlertMessage(
                                    "تعذر تحميل قائمة النماذج المركزية",
                                    error.message ?: "تحقق من الاتصال أو صلاحية قراءة الكتالوج."
                                )
                            )
                        }
                    }
                runCatching {
                    firestoreSync.observeAiModelCatalog().collect { catalog ->
                        _uiState.update { it.copy(aiModelCatalog = catalog) }
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            aiModelCatalog = emptyList(),
                            alert = AlertMessage(
                                "توقف تحديث قائمة النماذج",
                                error.message ?: "تعذر متابعة مزامنة النماذج المركزية."
                            )
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            runCatching { repository.ensureDefaultPromptTemplates() }
            runCatching { repository.loadProviders() }
                .onSuccess { loaded ->
                    _uiState.update { it.copy(providers = loaded) }
                }
            runCatching { repository.loadSelectedProviderType() }
                .onSuccess { selected ->
                    _uiState.update { it.copy(selectedProvider = selected) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            alert = AlertMessage(
                                title = "تعذر تحميل إعدادات مزودي AI",
                                message = error.message ?: "تعذر فك تشفير الإعدادات المحفوظة. لم يتم حذفها."
                            )
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.allWorkspaces.collect { list ->
                _uiState.update { it.copy(workspaces = list) }
            }
        }
        viewModelScope.launch {
            repository.recentRuns.collect { runs ->
                _uiState.update { it.copy(recentRuns = runs) }
            }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _uiState
                .map { state ->
                    resolveEffectiveWorkspaceId(
                        userRole = state.userRole,
                        activeLearner = state.activeLearner,
                        workspaces = state.workspaces,
                        adminSelectedWorkspaceId = state.selectedWorkspaceId
                    )
                }
                .distinctUntilChanged()
                .flatMapLatest { wsId ->
                    if (wsId == BLOCKED_UNASSIGNED_WORKSPACE_ID) {
                        flowOf(null)
                    } else {
                        repository.observeWorkspace(wsId)
                    }
                }
                .collect { ws ->
                    val diskBytes = repository.computeWorkspaceDiskBytes(ws?.rootPath.orEmpty())
                    _uiState.update { state ->
                        val currentSelectedBook = state.selectedBookKey
                        val validSelectedBook = when {
                            ws == null -> ""
                            ws.books.any { it.bookKey == currentSelectedBook } -> currentSelectedBook
                            else -> ws.books.firstOrNull()?.bookKey.orEmpty()
                        }
                        val validSelectedLesson = state.selectedLesson?.takeIf { sel ->
                            ws != null && ws.lessons.any {
                                it.bookKey == sel.bookKey &&
                                    it.unitKey == sel.unitKey &&
                                    it.lessonKey == sel.lessonKey
                            }
                        }
                        state.copy(
                            workspace = ws,
                            activeWorkspaceDiskBytes = diskBytes,
                            selectedBookKey = validSelectedBook,
                            selectedLesson = validSelectedLesson
                        )
                    }
                }
        }
    }

    fun resolveSignedInUserRole(force: Boolean = false) {
        if (!isCloudAuthenticated) return
        val currentCheck = _uiState.value.authRoleCheckState
        if (!force && (currentCheck is AuthRoleCheckState.Checking || currentCheck is AuthRoleCheckState.Resolved)) {
            return
        }
        _uiState.update { it.copy(authRoleCheckState = AuthRoleCheckState.Checking) }
        viewModelScope.launch {
            firestoreSync.resolveUserRole(currentUserId, currentUserDisplayName)
                .onSuccess { resolution ->
                    applyResolvedUserRole(resolution)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            authRoleCheckState = AuthRoleCheckState.Error(
                                error.localizedMessage ?: "تعذر التحقق من صلاحية الحساب في Firestore"
                            )
                        )
                    }
                }
        }
    }

    internal fun applyResolvedUserRole(resolution: UserRoleResolution) {
        val nextRole = when (resolution.destination) {
            AuthRoleDestination.ADMIN_DASHBOARD -> UserRole.ADMIN
            AuthRoleDestination.STUDENT_VIEW -> UserRole.LEARNER
        }
        val targetPage = when (resolution.destination) {
            AuthRoleDestination.ADMIN_DASHBOARD -> AppPage.ADMIN_DASHBOARD
            AuthRoleDestination.STUDENT_VIEW -> AppPage.DASHBOARD
        }
        if (nextRole == UserRole.LEARNER) {
            syncAuthenticatedLearnerProfile(
                displayName = resolution.displayName ?: currentUserDisplayName ?: "الطالب",
                enrolledGradeKey = resolution.enrolledGradeKey
            )
        }
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update { state ->
            state.copy(
                userRole = nextRole,
                authenticatedUserRole = nextRole,
                authRoleCheckState = AuthRoleCheckState.Resolved(resolution),
                page = targetPage,
                studentStudyLesson = null,
                lastLessonQuizResult = null,
                globalSearchOpen = false,
                promptSourcesOpen = false,
                toastMessage = if (nextRole == UserRole.ADMIN) {
                    "تم التحقق من الحساب: لوحة المدير"
                } else {
                    "تم التحقق من الحساب: واجهة الطالب"
                }
            )
        }
    }

    private fun syncAuthenticatedLearnerProfile(
        displayName: String,
        enrolledGradeKey: String?
    ) {
        val cleanName = displayName.trim().ifBlank { "الطالب" }
        val cleanGrade = enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val existing = _uiState.value.availableLearners
        val existingMatch = existing.firstOrNull { it.userId == currentUserId }
        val targetProfile = if (existingMatch != null) {
            existingMatch.copy(
                displayName = cleanName,
                enrolledGradeKey = cleanGrade ?: existingMatch.enrolledGradeKey
            )
        } else {
            LearnerProfile(
                userId = currentUserId,
                displayName = cleanName,
                enrolledGradeKey = cleanGrade
            )
        }
        val updatedList = if (existingMatch != null) {
            existing.map { if (it.userId == currentUserId) targetProfile else it }
        } else {
            existing + targetProfile
        }
        saveLearnerProfiles(updatedList, targetProfile.userId)
        _uiState.update {
            it.copy(
                availableLearners = updatedList,
                activeLearner = targetProfile
            )
        }
    }

    fun selectRole(role: UserRole) {
        if (_uiState.value.authenticatedUserRole == UserRole.LEARNER && role == UserRole.ADMIN) {
            _uiState.update {
                it.copy(toastMessage = "حسابك مسجّل كطالب ولا يملك صلاحية الدخول كمدير.")
            }
            return
        }
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update { state ->
            val targetPage = when (role) {
                UserRole.LEARNER -> AppPage.DASHBOARD
                UserRole.ADMIN -> AppPage.ADMIN_DASHBOARD
                UserRole.ENTRY -> state.page
            }
            state.copy(
                userRole = role,
                page = targetPage,
                studentStudyLesson = null,
                lastLessonQuizResult = null,
                globalSearchOpen = false,
                promptSourcesOpen = false
            )
        }
    }

    fun switchToRoleEntry() {
        if (_uiState.value.authenticatedUserRole == UserRole.LEARNER) {
            _uiState.update {
                it.copy(toastMessage = "استخدم تسجيل الخروج للتبديل إلى حساب آخر.")
            }
            return
        }
        _uiState.update {
            it.copy(
                userRole = UserRole.ENTRY,
                studentStudyLesson = null,
                globalSearchOpen = false,
                promptSourcesOpen = false,
                reinforcementQuestions = emptyList(),
                reinforcementConceptTitle = null
            )
        }
    }

    fun selectLearnerProfile(userId: String) {
        val current = _uiState.value.availableLearners
        val match = current.firstOrNull { it.userId == userId } ?: return
        repository.writeLearnerPrefs(LEARNER_PREFS_NAME, mapOf(KEY_ACTIVE_LEARNER_ID to match.userId))
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update {
            it.copy(
                activeLearner = match,
                toastMessage = "تم التبديل إلى ملف المتعلّم: ${match.displayName}"
            )
        }
    }

    fun addLearnerProfile(displayName: String, enrolledGradeKey: String? = null) {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return
        val cleanGrade = enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val existing = _uiState.value.availableLearners
        val duplicate = existing.firstOrNull { it.displayName.equals(cleanName, ignoreCase = true) }
        if (duplicate != null) {
            if (cleanGrade != null && !duplicate.enrolledGradeKey.equals(cleanGrade, ignoreCase = true)) {
                updateLearnerGrade(duplicate.userId, cleanGrade)
            }
            selectLearnerProfile(duplicate.userId)
            return
        }
        val newProfile = LearnerProfile(
            userId = "learner_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
            displayName = cleanName,
            enrolledGradeKey = cleanGrade
        )
        val updatedList = existing + newProfile
        saveLearnerProfiles(updatedList, newProfile.userId)
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update {
            it.copy(
                availableLearners = updatedList,
                activeLearner = newProfile,
                toastMessage = if (cleanGrade != null) {
                    "تم إنشاء ملف المتعلّم «${newProfile.displayName}» وتعيينه للصف $cleanGrade"
                } else {
                    "تم إنشاء وتفعيل ملف المتعلّم «${newProfile.displayName}»"
                }
            )
        }
    }

    fun updateLearnerGrade(userId: String, enrolledGradeKey: String?) {
        val cleanGrade = enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val existing = _uiState.value.availableLearners
        var updatedProfile: LearnerProfile? = null
        val updatedList = existing.map { profile ->
            if (profile.userId == userId) {
                profile.copy(enrolledGradeKey = cleanGrade).also { updatedProfile = it }
            } else {
                profile
            }
        }
        val target = updatedProfile ?: return
        val currentActive = _uiState.value.activeLearner
        val nextActive = if (currentActive.userId == userId) target else currentActive
        saveLearnerProfiles(updatedList, nextActive.userId)
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update {
            it.copy(
                availableLearners = updatedList,
                activeLearner = nextActive,
                toastMessage = if (cleanGrade != null) {
                    "تم تسجيل الطالب «${target.displayName}» في الصف $cleanGrade"
                } else {
                    "تم إلغاء تحديد الصف للطالب «${target.displayName}»"
                }
            )
        }
    }

    fun deleteLearnerProfile(userId: String) {
        if (userId == LearnerProfile.DEFAULT_USER_ID) return
        val existing = _uiState.value.availableLearners
        val updatedList = existing.filterNot { it.userId == userId }
        if (updatedList.size == existing.size) return
        val nextActive = if (_uiState.value.activeLearner.userId == userId) {
            updatedList.firstOrNull() ?: LearnerProfile.DEFAULT
        } else {
            _uiState.value.activeLearner
        }
        saveLearnerProfiles(updatedList, nextActive.userId)
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update {
            it.copy(
                availableLearners = updatedList,
                activeLearner = nextActive,
                toastMessage = "تم حذف ملف المتعلّم"
            )
        }
    }

    private fun loadLearnerProfiles() {
        val raw = repository.readLearnerPref(LEARNER_PREFS_NAME, KEY_LEARNER_PROFILES_JSON)
        val parsed = if (!raw.isNullOrBlank()) {
            runCatching { profileJson.decodeFromString<List<LearnerProfile>>(raw) }
                .getOrDefault(emptyList())
        } else emptyList()
        val list = parsed
            .distinctBy { it.userId }
            .ifEmpty { listOf(LearnerProfile.DEFAULT) }
            .let { items ->
                if (items.any { it.userId == LearnerProfile.DEFAULT_USER_ID }) items
                else listOf(LearnerProfile.DEFAULT) + items
            }
        val savedActiveId = repository.readLearnerPref(LEARNER_PREFS_NAME, KEY_ACTIVE_LEARNER_ID)
            ?: LearnerProfile.DEFAULT_USER_ID
        val active = list.firstOrNull { it.userId == savedActiveId } ?: list.first()
        _uiState.update {
            it.copy(availableLearners = list, activeLearner = active)
        }
    }

    private fun saveLearnerProfiles(profiles: List<LearnerProfile>, activeId: String) {
        runCatching {
            repository.writeLearnerPrefs(
                LEARNER_PREFS_NAME,
                mapOf(
                    KEY_LEARNER_PROFILES_JSON to profileJson.encodeToString(profiles),
                    KEY_ACTIVE_LEARNER_ID to activeId
                )
            )
        }
    }

    fun navigateTo(page: AppPage) {
        _uiState.update { state ->
            val allowed = when (state.userRole) {
                UserRole.LEARNER -> if (page == AppPage.DASHBOARD || page == AppPage.LEARNER_MASTERY) page else AppPage.DASHBOARD
                UserRole.ADMIN -> if (
                    page == AppPage.ADMIN_DASHBOARD ||
                    page == AppPage.LIBRARY ||
                    page == AppPage.STUDIO ||
                    page == AppPage.QUIZ_BANK ||
                    page == AppPage.SETTINGS
                ) page else AppPage.ADMIN_DASHBOARD
                UserRole.ENTRY -> page
            }
            state.copy(page = allowed)
        }
    }

    fun createManualClassroom(gradeKey: String, title: String) {
        val cleanGrade = gradeKey.trim().uppercase()
        if (cleanGrade.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val created = repository.createClassroom(cleanGrade, title)
                _uiState.update { state ->
                    state.copy(
                        selectedWorkspaceId = created.workspaceId,
                        selectedBookKey = "",
                        selectedLesson = null,
                        toastMessage = "تمت إضافة الصف «${created.title}» (${created.gradeKey})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تعذر إضافة الصف",
                            e.message ?: "حدث خطأ أثناء إنشاء الصف الدراسي"
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun inspectDirectFolderUri(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    directInspection = null,
                    workspaceOperation = WorkspaceOperationProgress("فحص مجلد مساحة العمل", "قراءة محتويات المجلد", 5)
                )
            }
            try {
                val inspection = repository.inspectDirectTreeUri(treeUri) { stage, percent ->
                    _uiState.update {
                        it.copy(workspaceOperation = WorkspaceOperationProgress("فحص مجلد مساحة العمل", stage, percent))
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        directInspection = inspection
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        directInspection = null,
                        alert = AlertMessage("تعذر فحص المجلد", e.message ?: "لم يتم العثور على ملفات منهج صالحة")
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    fun confirmDirectGradeBinding(gradeKey: String) {
        confirmDirectGradesBinding(listOf(gradeKey))
    }

    fun confirmDirectGradesBinding(gradeKeys: List<String>) {
        val inspection = _uiState.value.directInspection ?: return
        val targets = gradeKeys.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    workspaceOperation = WorkspaceOperationProgress("ربط الصفوف والمواد", "بدء ربط الصفوف المحددة", 3)
                )
            }
            try {
                val boundList = repository.bindDirectWorkspaces(inspection.rootUriOrPath, targets) { stage, percent ->
                    _uiState.update {
                        it.copy(workspaceOperation = WorkspaceOperationProgress("ربط الصفوف والمواد", stage, percent))
                    }
                }
                val primary = boundList.first()
                _studentDashboardState.value = StudentDashboardState()
                _uiState.update { state ->
                    val nextPage = when (state.userRole) {
                        UserRole.LEARNER -> AppPage.DASHBOARD
                        UserRole.ADMIN -> if (state.page == AppPage.ADMIN_DASHBOARD) AppPage.ADMIN_DASHBOARD else AppPage.LIBRARY
                        UserRole.ENTRY -> state.page
                    }
                    val totalBooks = boundList.sumOf { it.books.size }
                    val totalLessons = boundList.sumOf { it.lessons.size }
                    val gradesSummary = boundList.joinToString("، ") { "${it.gradeKey} (${it.books.size} كتاب)" }
                    val warningPreview = boundList.flatMap { it.warnings }.distinct().take(3)
                    val summaryMessage = buildString {
                        append("تم ربط ${boundList.size} صف دراسي بنجاح ($gradesSummary) • $totalBooks كتاب • $totalLessons درس. ملفات PDF بقيت في موقعها الأصلي بدون نسخ.")
                        if (warningPreview.isNotEmpty()) {
                            append("\n\nتنبيهات:\n")
                            append(warningPreview.joinToString("\n"))
                        }
                    }
                    state.copy(
                        directInspection = null,
                        selectedWorkspaceId = primary.workspaceId,
                        selectedBookKey = primary.books.firstOrNull()?.bookKey.orEmpty(),
                        selectedLesson = null,
                        page = nextPage,
                        alert = AlertMessage(
                            title = if (boundList.size == 1) "تمت مزامنة الصف ${primary.gradeKey}" else "تمت مزامنة ${boundList.size} صفوف دراسية",
                            message = summaryMessage
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        directInspection = null,
                        alert = AlertMessage("تعذر ربط الصف", e.message ?: "حدث خطأ غير متوقع")
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    fun refreshDirectWorkspaceCatalog() {
        val currentWorkspace = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    workspaceOperation = WorkspaceOperationProgress("تحديث مساحة العمل", "قراءة فهارس الصف ومواده", 5)
                )
            }
            try {
                val refreshed = repository.bindDirectWorkspace(
                    rootUriOrPath = currentWorkspace.rootPath,
                    gradeKey = currentWorkspace.gradeKey
                ) { stage, percent ->
                    _uiState.update {
                        it.copy(workspaceOperation = WorkspaceOperationProgress("تحديث مساحة العمل", stage, percent))
                    }
                }
                _studentDashboardState.value = StudentDashboardState()
                _uiState.update { state ->
                    val nextBookKey = refreshed.books
                        .firstOrNull { it.bookKey == state.selectedBookKey }
                        ?.bookKey
                        ?: refreshed.books.firstOrNull()?.bookKey.orEmpty()
                    state.copy(
                        workspace = refreshed,
                        selectedWorkspaceId = refreshed.workspaceId,
                        selectedBookKey = nextBookKey,
                        selectedLesson = null,
                        alert = AlertMessage(
                            "تم تحديث فهرس مساحة العمل",
                            "${refreshed.indexRecords.size} فهرس • ${refreshed.books.size} مادة/كتاب • ${refreshed.units.size} وحدة • ${refreshed.lessons.size} درس. لم يتم نسخ ملفات PDF."
                        )
                    )
                }
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "انتهت صلاحية الوصول إلى المجلد",
                            "تعذر قراءة مجلد مساحة العمل المحلي (${e.message ?: "SecurityException"}). أعد اختيار المجلد عبر زر «ربط مجلد مساحة العمل» لاستعادة صلاحية القراءة دون فقدان بياناتك."
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تعذر تحديث الفهرس",
                            e.message ?: "تعذر قراءة مساحة العمل؛ بقيت بيانات الفهرس السابقة دون تغيير."
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    fun dismissDirectInspection() {
        _uiState.update { it.copy(directInspection = null) }
    }

    fun selectWorkspace(workspaceId: String) {
        _studentDashboardState.value = StudentDashboardState()
        _uiState.update {
            it.copy(
                selectedWorkspaceId = workspaceId,
                selectedBookKey = "",
                selectedLesson = null
            )
        }
    }

    fun selectBook(bookKey: String) {
        _uiState.update {
            it.copy(
                selectedBookKey = bookKey,
                selectedLesson = null
            )
        }
    }

    fun selectDashboardBookFilter(bookKey: String) {
        _studentDashboardState.update { it.copy(selectedBookKey = bookKey) }
    }

    fun selectDashboardLessonFilter(filter: DashboardLessonFilter) {
        _studentDashboardState.update { it.copy(lessonFilter = filter) }
    }

    fun toggleDashboardUnitExpanded(unitKey: String) {
        _studentDashboardState.update { state ->
            val next = if (unitKey in state.collapsedUnitKeys) {
                state.collapsedUnitKeys - unitKey
            } else {
                state.collapsedUnitKeys + unitKey
            }
            state.copy(collapsedUnitKeys = next)
        }
    }

    fun toggleExpandCollapseAllDashboardUnits(allUnitKeys: List<String>) {
        _studentDashboardState.update { state ->
            val allSet = allUnitKeys.toSet()
            val shouldExpandAll = state.collapsedUnitKeys.size >= (allSet.size + 1) / 2
            state.copy(collapsedUnitKeys = if (shouldExpandAll) emptySet() else allSet)
        }
    }

    fun dismissCompletedUnitBanner() {
        _studentDashboardState.update { it.copy(recentlyCompletedUnitTitle = null) }
    }

    fun openLessonQuestionsInStudio(lesson: LessonRef) {
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                task = TaskType.QUESTIONS,
                studentStudyLesson = null,
                lastLessonQuizResult = null,
                page = AppPage.STUDIO
            )
        }
    }

    fun selectLessonAndOpenStudio(lesson: LessonRef) {
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                generatedResult = "",
                activeDraftProvider = "تحرير يدوي",
                activeDraftModel = "local",
                activeDraftTemplateId = "",
                activeDraftTemplateVersion = 1,
                activeDraftResponseSchemaHash = "",
                activeDraftPromptUsed = "",
                activeDraftResponseSchemaJson = "",
                activeDraftRequestFingerprint = "",
                activeDraftContentHash = "",
                activeDraftSourcePdfHash = "",
                generatedQuestionExpectedCount = null,
                generatedQuestionExpectedTypeIds = emptyList(),
                generatedQuestionExpectedDifficultyId = "",
                page = AppPage.STUDIO
            )
        }
    }

    fun selectLessonInStudio(lesson: LessonRef) {
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                generatedResult = "",
                activeDraftProvider = "تحرير يدوي",
                activeDraftModel = "local",
                activeDraftTemplateId = "",
                activeDraftTemplateVersion = 1,
                activeDraftResponseSchemaHash = "",
                activeDraftPromptUsed = "",
                activeDraftResponseSchemaJson = "",
                activeDraftRequestFingerprint = "",
                activeDraftContentHash = "",
                activeDraftSourcePdfHash = "",
                generatedQuestionExpectedCount = null,
                generatedQuestionExpectedTypeIds = emptyList(),
                generatedQuestionExpectedDifficultyId = ""
            )
        }
    }

    fun openPdfPreview(path: String, title: String, subtitle: String? = null) {
        _uiState.update {
            it.copy(previewingPdf = PdfPreviewPayload(path = path, title = title, subtitle = subtitle))
        }
    }

    fun openLocalPdfUriPreview(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val info = repository.ingestBookPdf(uri)
                _uiState.update {
                    it.copy(
                        previewingPdf = PdfPreviewPayload(
                            path = info.storedPdfPath,
                            title = info.fileName,
                            subtitle = "${info.totalPages} صفحة • ملف PDF محلي"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر فتح PDF", e.message ?: "ملف PDF غير صالح"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    /**
     * Stage 1: Ingests the picked book PDF, invokes AI index discovery when a configured
     * provider + catalog model are available, and opens the editable Review UI (`BookIndexReviewUiState`).
     * Never silently approves or discards the PDF if AI fails; falls back to an editable manual draft.
     */
    fun startBookPdfDiscoveryIntake(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    workspaceOperation = WorkspaceOperationProgress(
                        title = "استيراد واكتشاف فهرس كتاب PDF",
                        stage = "نسخ ملف PDF مؤقتاً وحساب البصمة...",
                        percent = 8
                    )
                )
            }
            try {
                val pdfInfo = repository.ingestBookPdf(uri)
                val defaultReviewer = resolveReviewerIdentity()
                val snapshot = _uiState.value
                val resolvedAiTarget = resolveIndexDiscoveryAiTarget(snapshot)

                if (resolvedAiTarget == null) {
                    val manualDraft = buildEmptyManualDraft(pdfInfo, defaultReviewer)
                    _uiState.update {
                        it.copy(
                            bookIndexReview = BookIndexReviewUiState(
                                pdfInfo = pdfInfo,
                                draft = manualDraft,
                                discoveryError = "لا يوجد مزود AI ومفتاح API مفعّل حالياً؛ يمكنك إدخال بيانات الفهرس يدوياً أو إضافة مفتاح من الإعدادات ثم إعادة الاكتشاف."
                            ),
                            toastMessage = "تم استيراد ${pdfInfo.fileName} (${pdfInfo.totalPages} صفحة) للمراجعة اليدوية"
                        )
                    }
                    return@launch
                }

                runDiscoveryAndOpenReview(
                    pdfInfo = pdfInfo,
                    providerType = resolvedAiTarget.first,
                    modelId = resolvedAiTarget.second,
                    profileId = resolvedAiTarget.third,
                    defaultReviewer = defaultReviewer
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر استيراد كتاب PDF", e.message ?: "ملف PDF غير صالح"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    fun retryBookIndexAiDiscovery(extraInstructions: String = "") {
        val current = _uiState.value.bookIndexReview ?: return
        val snapshot = _uiState.value
        val resolvedAiTarget = resolveIndexDiscoveryAiTarget(snapshot)
        if (resolvedAiTarget == null) {
            _uiState.update {
                it.copy(
                    bookIndexReview = current.copy(
                        discoveryError = "لا يوجد مزود AI أو مفتاح API مفعّل لإعادة الاكتشاف. أضف مفتاحاً من الإعدادات أولاً."
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    bookIndexReview = current.copy(isDiscovering = true, discoveryError = null),
                    workspaceOperation = WorkspaceOperationProgress(
                        title = "إعادة اكتشاف الفهرس بالذكاء الاصطناعي",
                        stage = "إرسال صفحات الفهرس إلى ${resolvedAiTarget.second}...",
                        percent = 25
                    )
                )
            }
            try {
                runDiscoveryAndOpenReview(
                    pdfInfo = current.pdfInfo,
                    providerType = resolvedAiTarget.first,
                    modelId = resolvedAiTarget.second,
                    profileId = resolvedAiTarget.third,
                    defaultReviewer = current.draft.reviewerId ?: resolveReviewerIdentity(),
                    extraInstructions = extraInstructions
                )
            } finally {
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        workspaceOperation = null,
                        bookIndexReview = state.bookIndexReview?.copy(isDiscovering = false)
                    )
                }
            }
        }
    }

    private suspend fun runDiscoveryAndOpenReview(
        pdfInfo: PdfBookInfo,
        providerType: ProviderType,
        modelId: String,
        profileId: String,
        defaultReviewer: String,
        extraInstructions: String = ""
    ) {
        val snapshot = _uiState.value
        val discoveryOutcome = runCatching {
            repository.discoverBookIndexFromPdf(
                pdfInfo = pdfInfo,
                providers = snapshot.providers,
                selectedProvider = providerType,
                selectedModelId = modelId,
                selectedProfileId = profileId,
                catalogModels = snapshot.aiModelCatalog,
                extraInstructions = extraInstructions,
                onProgress = { pct, stage ->
                    _uiState.update { state ->
                        state.copy(
                            workspaceOperation = WorkspaceOperationProgress(
                                title = "اكتشاف فهرس الكتاب (${pdfInfo.fileName})",
                                stage = stage,
                                percent = pct
                            )
                        )
                    }
                }
            )
        }
        if (discoveryOutcome.isFailure) {
            val errMsg = discoveryOutcome.exceptionOrNull()?.message ?: "تعذر إتمام الاكتشاف بالذكاء الاصطناعي"
            val fallbackDraft = _uiState.value.bookIndexReview?.draft
                ?: buildEmptyManualDraft(pdfInfo, defaultReviewer)
            _uiState.update {
                it.copy(
                    bookIndexReview = BookIndexReviewUiState(
                        pdfInfo = pdfInfo,
                        draft = fallbackDraft,
                        discoveryError = "تعذر الاكتشاف التلقائي: $errMsg — يمكنك تعديل الفهرس يدوياً والمراجعة قبل الاعتماد."
                    )
                )
            }
            return
        }

        val generated = discoveryOutcome.getOrThrow()
        val parsed = runCatching {
            BookIndexAiResponseParser.parse(generated.text, pdfInfo)
        }.map { draft ->
            draft.copy(
                reviewerId = draft.reviewerId ?: defaultReviewer,
                reviewerConfirmed = false
            )
        }

        if (parsed.isSuccess) {
            _uiState.update {
                it.copy(
                    providers = repository.loadProviders(),
                    bookIndexReview = BookIndexReviewUiState(
                        pdfInfo = pdfInfo,
                        draft = parsed.getOrThrow(),
                        aiProviderLabel = generated.providerLabel,
                        aiModel = generated.model,
                        discoveryValidationIssues = generated.validationIssues,
                        discoveryError = null
                    ),
                    toastMessage = "تم استلام مسودة الفهرس للمراجعة"
                )
            }
        } else {
            val parseError = parsed.exceptionOrNull()?.message ?: "استجابة JSON غير مكتملة"
            val fallbackDraft = _uiState.value.bookIndexReview?.draft
                ?: buildEmptyManualDraft(pdfInfo, defaultReviewer)
            _uiState.update {
                it.copy(
                    providers = repository.loadProviders(),
                    bookIndexReview = BookIndexReviewUiState(
                        pdfInfo = pdfInfo,
                        draft = fallbackDraft.copy(
                            reviewNotes = (fallbackDraft.reviewNotes + "\n" + generated.text.take(400)).trim()
                        ),
                        aiProviderLabel = generated.providerLabel,
                        aiModel = generated.model,
                        discoveryValidationIssues = generated.validationIssues,
                        discoveryError = "تعذر تحليل مخرجات AI ($parseError)؛ أُنشئت مسودة قابلة للتحرير اليدوي."
                    )
                )
            }
        }
    }

    fun updateBookIndexDraft(transform: (BookIndexIntakeDraft) -> BookIndexIntakeDraft) {
        _uiState.update { state ->
            val current = state.bookIndexReview ?: return@update state
            state.copy(bookIndexReview = current.copy(draft = transform(current.draft)))
        }
    }

    fun populateStarterUnitsForReview(pagesPerLesson: Int = 5) {
        val current = _uiState.value.bookIndexReview ?: return
        val pdfInfo = current.pdfInfo
        val splits = repository.generateAutoSplits(pdfInfo.totalPages, pagesPerLesson)
        if (splits.isEmpty()) return
        val effectivePart = when (current.draft.sourceLayout) {
            IndexSourceLayout.SINGLE_PART_FILE -> current.draft.partKey ?: "P01"
            IndexSourceLayout.MERGED_PARTS_FILE -> "P01"
            IndexSourceLayout.UNKNOWN -> current.draft.partKey?.takeIf { it in setOf("P01", "P02") } ?: "P01"
        }
        val lessons = splits.mapIndexed { idx, s ->
            BookIndexLessonDraft(
                lessonKey = s.lessonKey,
                title = s.title,
                printedPageStart = null,
                printedPageEnd = null,
                pdfPageStart = s.startPage,
                pdfPageEnd = s.endPage,
                assignmentStatus = IndexAssignmentStatus.NEEDS_REVIEW,
                assignmentSource = IndexAssignmentSource.DEFAULT_PROPOSAL,
                assignmentEvidence = "تقسيم مبدئي (${s.startPage}–${s.endPage}) يحتاج تأكيد المراجع"
            )
        }
        val starterUnit = BookIndexUnitDraft(
            unitKey = "unit_01",
            title = "الوحدة الأولى",
            partKey = effectivePart,
            proposedPartKey = effectivePart,
            assignmentStatus = IndexAssignmentStatus.NEEDS_REVIEW,
            assignmentSource = IndexAssignmentSource.DEFAULT_PROPOSAL,
            assignmentEvidence = "وحدة مبدئية مقترحة بانتظار تأكيد المراجع",
            lessons = lessons
        )
        updateBookIndexDraft { draft ->
            draft.copy(
                units = listOf(starterUnit),
                reviewerConfirmed = false
            )
        }
    }

    fun dismissBookIndexReview() {
        val review = _uiState.value.bookIndexReview
        _uiState.update { it.copy(bookIndexReview = null) }
        if (review != null) {
            repository.discardIngestedBookPdf(review.pdfInfo)
        }
    }

    fun approveAndSplitBookIndex() {
        val review = _uiState.value.bookIndexReview ?: return
        val reviewerId = review.draft.reviewerId?.takeIf { it.isNotBlank() } ?: resolveReviewerIdentity()
        val draftToApprove = review.draft.copy(reviewerId = reviewerId)
        val errors = draftToApprove.validationErrors()
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        title = "لا يمكن الاعتماد قبل استكمال المراجعة",
                        message = errors.joinToString("\n") { "• $it" }
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    workspaceOperation = WorkspaceOperationProgress(
                        title = "اعتماد الفهرس وتقطيع الكتاب",
                        stage = "حفظ الفهرس المعتمد في Room...",
                        percent = 12
                    )
                )
            }
            try {
                val updatedWorkspace = repository.approveBookIndexAndSplitWorkspace(
                    draft = draftToApprove,
                    pdfInfo = review.pdfInfo,
                    onProgress = { stage, pct ->
                        _uiState.update { state ->
                            state.copy(
                                workspaceOperation = WorkspaceOperationProgress(
                                    title = "اعتماد الفهرس وتقطيع ${draftToApprove.bookTitle}",
                                    stage = stage,
                                    percent = pct
                                )
                            )
                        }
                    }
                )
                val approvedBookKey = RoomBookIndexProjection.canonicalBookIdentity(
                    gradeKey = draftToApprove.gradeKey,
                    subjectKey = draftToApprove.subjectKey,
                    componentKey = draftToApprove.componentKey,
                    sourceLayout = draftToApprove.sourceLayout,
                    partKey = draftToApprove.partKey
                ).bookKey
                val firstBookLesson = updatedWorkspace.lessons.firstOrNull { it.bookKey == approvedBookKey }
                    ?: updatedWorkspace.lessons.firstOrNull()
                val snapshotBeforeUpdate = _uiState.value
                if (snapshotBeforeUpdate.userRole == UserRole.LEARNER &&
                    snapshotBeforeUpdate.activeLearner.enrolledGradeKey.isNullOrBlank()
                ) {
                    updateLearnerGrade(snapshotBeforeUpdate.activeLearner.userId, updatedWorkspace.gradeKey)
                }
                _uiState.update {
                    it.copy(
                        bookIndexReview = null,
                        selectedWorkspaceId = updatedWorkspace.workspaceId,
                        selectedBookKey = approvedBookKey,
                        selectedLesson = firstBookLesson,
                        packageSourcePages = firstBookLesson?.pageRange.orEmpty(),
                        toastMessage = "تم حفظ الفهرس المعتمد وتقطيع ${updatedWorkspace.lessons.count { l -> l.bookKey == approvedBookKey }} درساً بنجاح"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            title = "تعذر اعتماد الفهرس وتقطيع الكتاب",
                            message = e.message ?: "حدث خطأ غير متوقع أثناء الحفظ أو التقطيع"
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    private fun resolveReviewerIdentity(): String =
        currentUserEmail?.takeIf { it.isNotBlank() }
            ?: currentUserDisplayName?.takeIf { it.isNotBlank() }
            ?: currentUserId.takeIf { it.isNotBlank() }
            ?: "admin_reviewer"

    private fun buildEmptyManualDraft(pdfInfo: PdfBookInfo, reviewerId: String): BookIndexIntakeDraft {
        val defaultGrade = _uiState.value.workspace?.gradeKey
            ?: _uiState.value.activeLearner.enrolledGradeKey?.takeIf { it.isNotBlank() }
            ?: "G04"
        return BookIndexIntakeDraft(
            sourceFileName = pdfInfo.fileName,
            sourceSha256 = pdfInfo.sha256,
            totalPdfPages = pdfInfo.totalPages,
            bookTitle = pdfInfo.fileName.removeSuffix(".pdf").removeSuffix(".PDF"),
            gradeKey = defaultGrade,
            gradeName = com.example.data.workspace.BookMetadataMapper.gradeDisplayName(defaultGrade).orEmpty(),
            subjectKey = "ARAB",
            subjectName = com.example.data.workspace.BookMetadataMapper.subjectDisplayName("ARAB").orEmpty(),
            sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
            sourceLayoutEvidence = "",
            partKey = "P01",
            offset = 1,
            discoveryMethod = IndexDiscoveryMethod.MANUAL,
            units = emptyList(),
            reviewerConfirmed = false,
            reviewerId = reviewerId
        )
    }

    private fun resolveIndexDiscoveryAiTarget(snapshot: MainUiState): Triple<ProviderType, String, String>? {
        val preferredProvider = snapshot.selectedProvider
        val enabledProfile = snapshot.providers
            .filter { it.provider == preferredProvider && it.enabled && it.apiKey.isNotBlank() }
            .minWithOrNull(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt })
            ?: snapshot.providers
                .filter { it.enabled && it.apiKey.isNotBlank() }
                .minWithOrNull(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt })
            ?: return null
        val targetProvider = enabledProfile.provider
        val models = snapshot.aiModelCatalog.filter {
            it.provider == targetProvider &&
                it.isEnabled &&
                it.supportsGenerateContent &&
                it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        }
        val selectedModelId = models.firstOrNull {
            targetProvider == preferredProvider && it.modelId == snapshot.selectedGenerationModelId
        }?.modelId
            ?: models.firstOrNull { it.isDefault }?.modelId
            ?: models.firstOrNull()?.modelId
            ?: enabledProfile.model.takeIf { it.isNotBlank() }
            ?: return null
        return Triple(targetProvider, selectedModelId, enabledProfile.id)
    }

    fun dismissPdfPreview() {
        _uiState.update { it.copy(previewingPdf = null) }
    }

    fun openBookCoverPreview(book: BookRef) {
        _uiState.update { it.copy(previewingBookCover = book) }
    }

    fun dismissBookCoverPreview() {
        _uiState.update { it.copy(previewingBookCover = null) }
    }

    fun openPromptSourcesDialog() {
        _uiState.update { it.copy(promptSourcesOpen = true) }
    }

    fun dismissPromptSourcesDialog() {
        _uiState.update { it.copy(promptSourcesOpen = false) }
    }

    fun applyBookIndexBlueprint(spec: PromptBlueprintSpec) {
        _uiState.update { state ->
            state.copy(
                task = spec.task,
                extraInstructions = spec.quickInstructions,
                promptSourcesOpen = false,
                toastMessage = "تم تطبيق مخطط «${spec.titleAr}»"
            )
        }
    }

    fun loadSchemaSampleIntoEditor(task: TaskType, sample: String) {
        _uiState.update { state ->
            state.copy(
                task = task,
                generatedResult = sample,
                generatedQuestionExpectedCount = null,
                generatedQuestionExpectedTypeIds = emptyList(),
                generatedQuestionExpectedDifficultyId = "",
                activeDraftProvider = "Book index schema",
                activeDraftModel = "template",
                promptSourcesOpen = false,
                page = AppPage.STUDIO,
                toastMessage = "تم تحميل نموذج المخطط في الاستوديو"
            )
        }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun openStudentStudy(lesson: LessonRef) {
        val snapshot = _uiState.value
        if (snapshot.userRole == UserRole.LEARNER) {
            val orderedBookLessons = snapshot.workspace?.lessons.orEmpty()
                .filter { it.bookKey == lesson.bookKey }
            val completedKeys = dashboardSummary.value.unitPaths
                .flatMap { it.nodes }
                .filter { it.isCompleted }
                .map { LessonSequenceGate.key(it.lesson) }
                .toMutableSet()
            snapshot.lastLessonQuizResult?.takeIf { it.passed }?.let { result ->
                completedKeys += "${result.bookKey}::${result.unitKey}::${result.lessonKey}"
            }
            if (!LessonSequenceGate.isUnlocked(lesson, orderedBookLessons, completedKeys)) {
                _uiState.update {
                    it.copy(toastMessage = "هذا الدرس لم يُفتح بعد. أكمل الدروس السابقة واجتز اختبار كل درس للمتابعة.")
                }
                return
            }
        }
        analyticsTracker?.trackLessonOpened(
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            lessonTitle = lesson.title
        )
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                studentStudyLesson = lesson,
                lastLessonQuizResult = null
            )
        }
    }
    fun closeStudentStudy() {
        _uiState.update { it.copy(studentStudyLesson = null, lastLessonQuizResult = null) }
    }

    fun submitLessonQuiz(lesson: LessonRef, questions: List<QuestionItem>, answers: Map<String, String>) {
        val wsId = _uiState.value.workspace?.workspaceId.orEmpty()
        val activeUserId = _uiState.value.activeLearner.userId
        val unitCompoundKey = "${lesson.bookKey}_${lesson.unitKey}"
        val unitBefore = dashboardSummary.value.unitPaths.firstOrNull { it.unitKey == unitCompoundKey }
        val unitAlreadyCompleted = unitBefore?.status == UnitCompletionStatus.COMPLETED
        val otherIncompleteCount = unitBefore?.nodes?.count {
            !it.isCompleted && it.lesson.lessonKey != lesson.lessonKey
        } ?: 1

        viewModelScope.launch {
            val result = repository.recordLessonQuizAttempt(
                workspaceId = wsId,
                lesson = lesson,
                questions = questions,
                selectedAnswers = answers,
                userId = activeUserId
            )
            analyticsTracker?.trackQuizAttemptSubmitted(
                lessonKey = lesson.lessonKey,
                score = result.scorePercent,
                passed = result.passed
            )
            val completedWholeUnitNow = result.passed &&
                !unitAlreadyCompleted &&
                unitBefore != null &&
                unitBefore.totalCount > 0 &&
                otherIncompleteCount == 0

            if (completedWholeUnitNow) {
                _studentDashboardState.update {
                    it.copy(recentlyCompletedUnitTitle = unitBefore?.unitTitle)
                }
            }

            // The current Firestore progress document is account-scoped, not local-learner-scoped.
            // Sync only the legacy default profile when cloud auth is active until a per-learner cloud contract exists.
            if (isCloudAuthenticated && activeUserId == LearnerProfile.DEFAULT_USER_ID) {
                firestoreSync.upsertLessonProgress(
                    userId = currentUserId,
                    workspaceId = wsId,
                    lesson = lesson,
                    completed = result.lessonMarkedCompleted,
                    xpEarned = result.earnedXp,
                    scorePercent = result.scorePercent
                )
            }

            _uiState.update {
                it.copy(
                    lastLessonQuizResult = result,
                    toastMessage = when {
                        completedWholeUnitNow ->
                            "🎉 مبروك! اجتزت الاختبار وأكملت وحدة «${unitBefore?.unitTitle}» بالكامل (+${result.earnedXp} XP)"
                        result.passed ->
                            "اجتزت الاختبار واكتمل الدرس (+${result.earnedXp} XP)"
                        else ->
                            "نتيجتك ${result.scorePercent}% — أعد المحاولة (${EduMiniRepository.LESSON_QUIZ_PASS_PERCENT}%+) لإكمال الدرس"
                    }
                )
            }
        }
    }

    fun openNextLessonInStudy(currentLesson: LessonRef) {
        val snapshot = _uiState.value
        val currentNode = dashboardSummary.value.unitPaths
            .flatMap { it.nodes }
            .firstOrNull {
                it.lesson.bookKey == currentLesson.bookKey &&
                    it.lesson.unitKey == currentLesson.unitKey &&
                    it.lesson.lessonKey == currentLesson.lessonKey
            }
        val passedInCurrentSession = snapshot.lastLessonQuizResult?.let {
            it.passed &&
                it.bookKey == currentLesson.bookKey &&
                it.unitKey == currentLesson.unitKey &&
                it.lessonKey == currentLesson.lessonKey
        } == true
        if (snapshot.userRole == UserRole.LEARNER && currentNode?.isCompleted != true && !passedInCurrentSession) {
            _uiState.update { it.copy(toastMessage = "لا يمكن الانتقال إلى الدرس التالي قبل اجتياز اختبار هذا الدرس.") }
            return
        }

        val orderedBookLessons = snapshot.workspace?.lessons.orEmpty()
            .filter { it.bookKey == currentLesson.bookKey }
        val currentIndex = orderedBookLessons.indexOfFirst {
            it.unitKey == currentLesson.unitKey && it.lessonKey == currentLesson.lessonKey
        }
        if (currentIndex < 0) {
            _uiState.update { it.copy(toastMessage = "تعذر تحديد ترتيب هذا الدرس داخل الكتاب.") }
            return
        }
        val nextLesson = orderedBookLessons.getOrNull(currentIndex + 1)
        if (nextLesson != null) {
            openStudentStudy(nextLesson)
        } else {
            closeStudentStudy()
            _uiState.update { it.copy(toastMessage = "أحسنت! أكملت دروس هذا الكتاب المتاحة.") }
        }
    }
    fun launchConceptReinforcement(conceptTag: String?) {
        val questions = workspaceQuestions.value
        val attempts = workspaceQuestionAttempts.value
        val selected = ConceptMasteryEngine.selectReinforcementQuestions(
            questions = questions,
            attempts = attempts,
            targetConceptTag = conceptTag
        ).take(12)
        if (selected.isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("لا توجد أسئلة", "لا توجد أسئلة مرتبطة بهذا المفهوم حالياً."))
            }
            return
        }
        val title = if (conceptTag.isNullOrBlank()) "جلسة تقوية شاملة" else "تقوية: $conceptTag"
        _uiState.update {
            it.copy(
                reinforcementConceptTitle = title,
                reinforcementQuestions = selected
            )
        }
    }

    fun generateConceptRemediation(conceptTag: String?, task: TaskType) {
        val snapshot = _uiState.value
        val workspace = snapshot.workspace
        if (workspace == null) {
            _uiState.update { it.copy(alert = AlertMessage("لا توجد مكتبة", "اربط كتاباً أولاً حتى يمكن بناء علاج تعليمي من محتوى الدرس.")) }
            return
        }
        if (task != TaskType.EXPLANATION && task != TaskType.QUESTIONS) {
            _uiState.update { it.copy(alert = AlertMessage("نوع غير مدعوم", "اختر شرح تقوية أو أسئلة تكيفية.")) }
            return
        }

        val questions = workspaceQuestions.value
        val attempts = workspaceQuestionAttempts.value
            .filter { it.userId == snapshot.activeLearner.userId }
        val targetTag = conceptTag?.trim().orEmpty()
        val wrongAttempts = attempts.filterNot { it.isCorrect }
        val wrongCounts = wrongAttempts.groupingBy { it.questionId }.eachCount()
        val candidates = questions
            .filter { question ->
                wrongCounts.containsKey(question.id) &&
                    (targetTag.isBlank() ||
                        ConceptMasteryEngine.effectiveConceptTag(question).equals(targetTag, ignoreCase = true))
            }
            .sortedByDescending { wrongCounts[it.id] ?: 0 }
            .take(5)

        if (candidates.isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage(
                    "لا توجد أدلة كافية",
                    "لم نعثر على إجابات خاطئة مسجلة لهذا المفهوم لدى المتعلم الحالي. نفّذ اختباراً تشخيصياً أولاً حتى لا نولّد علاجاً مبنياً على تخمين."
                ))
            }
            return
        }

        val primaryQuestion = candidates.first()
        val lesson = workspace.lessons.firstOrNull { candidateLesson ->
            primaryQuestion.bookKey == candidateLesson.bookKey &&
                primaryQuestion.unitKey == candidateLesson.unitKey &&
                primaryQuestion.lessonKey == candidateLesson.lessonKey
        }
        if (lesson == null) {
            _uiState.update { it.copy(alert = AlertMessage("تعذر تحديد الدرس", "الأسئلة المرتبطة بالمفهوم لا تطابق درساً موجوداً في فهرس الكتاب.")) }
            return
        }
        val lessonQuestions = candidates.filter { question ->
            question.bookKey == lesson.bookKey &&
                question.unitKey == lesson.unitKey &&
                question.lessonKey == lesson.lessonKey
        }

        val evidence = buildString {
            appendLine("المفهوم المستهدف: ${targetTag.ifBlank { ConceptMasteryEngine.effectiveConceptTag(lessonQuestions.first()) }}")
            appendLine("هذه بيانات تشخيصية من بنك أسئلة الدرس، وليست مصدراً لتغيير المنهج:")
            lessonQuestions.forEachIndexed { index, question ->
                appendLine()
                appendLine("السؤال ${index + 1}: ${question.prompt}")
                if (question.options.isNotEmpty()) appendLine("الخيارات: ${question.options.joinToString(" | ")}")
                appendLine("الإجابة المعتمدة: ${question.correctAnswer}")
                appendLine("التفسير المعتمد: ${question.rationale}")
                val learnerErrors = wrongAttempts
                    .filter { it.questionId == question.id }
                    .groupingBy { it.selectedAnswer }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                if (learnerErrors.isNotEmpty()) {
                    appendLine("إجابات المتعلم الخاطئة المسجلة: " +
                        learnerErrors.joinToString("؛ ") { "${it.key} (تكررت ${it.value} مرة)" })
                }
            }
        }

        val conceptTitle = targetTag.ifBlank { ConceptMasteryEngine.effectiveConceptTag(lessonQuestions.first()) }
        val taskInstructions = if (task == TaskType.EXPLANATION) {
            """
            أنشئ شرح تقوية علاجي باللغة العربية لهذا المفهوم، لا تعِد شرح الدرس كاملاً.
            ابدأ بتحديد التصور الخاطئ المحتمل من الإجابات المسجلة مع التصريح بأنه احتمال لا حقيقة مؤكدة.
            اربط التصحيح بالمعرفة السابقة، ثم اشرح الفكرة بخطوات قصيرة ومثال محلول، وقارن بين الإجابة الخاطئة والفهم الصحيح.
            اختم بسؤال تحقق قصير ومهمة نقل إلى موقف جديد. استخدم لغة مناسبة للصف الدراسي، ولا تخترع حقائق خارج المصدر.
            """.trimIndent()
        } else {
            """
            أنشئ 5 أسئلة تكيفية جديدة باللغة العربية لقياس وتصحيح المفهوم المستهدف.
            ابدأ بسؤال تأسيسي، ثم ارفع الصعوبة تدريجياً، واستخدم مشتتات تمثل الأخطاء الظاهرة في البيانات.
            لا تكرر الأسئلة الأصلية ولا تغيّر الأرقام فقط. أرفق الإجابة الصحيحة وتفسيراً تعليمياً لكل سؤال.
            يجب أن تكون الأسئلة قابلة للإجابة من المصدر، واضحة، ولكل سؤال إجابة واحدة صحيحة ما لم يحدد نوعه خلاف ذلك.
            """.trimIndent()
        }

        val selectedModel = snapshot.aiModelCatalog.firstOrNull {
            it.provider == snapshot.selectedProvider && it.isDefault && it.isEnabled &&
                it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        } ?: snapshot.aiModelCatalog.firstOrNull {
            it.provider == snapshot.selectedProvider && it.isEnabled &&
                it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        }
        val selectedApiKey = snapshot.providers
            .filter { it.provider == snapshot.selectedProvider && it.enabled && it.apiKey.isNotBlank() }
            .minWithOrNull(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt })
        if (selectedModel == null || selectedApiKey == null) {
            _uiState.update {
                it.copy(alert = AlertMessage(
                    "تعذر بدء التقوية",
                    "تأكد من وجود نموذج مفعّل ومفتاح API مفعّل لهذا المزود."
                ))
            }
            return
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, alert = null) }
            try {
                val output = repository.generateContent(
                    workspaceId = workspace.workspaceId,
                    lesson = lesson,
                    task = task,
                    source = evidence,
                    extra = taskInstructions,
                    providers = snapshot.providers,
                    selectedProvider = snapshot.selectedProvider,
                    selectedModelId = selectedModel.modelId,
                    selectedProfileId = selectedApiKey.id,
                    questionCount = 5,
                    attachPdfPages = true,
                    questionStyleHint = if (task == TaskType.QUESTIONS) "أسئلة تشخيصية تكيفية ومشتتات قائمة على الخطأ" else "",
                    difficultyHint = if (task == TaskType.QUESTIONS) "متدرجة من التأسيسي إلى التطبيق" else "",
                    template = promptTemplates.value.firstOrNull { it.task == task },
                    onProgress = { percent, stage ->
                        _uiState.update { it.copy(generationProgressPercent = percent, generationStage = stage) }
                    }
                )
                val draft = repository.persistDraft(
                    workspaceId = workspace.workspaceId,
                    lesson = lesson,
                    task = task,
                    providerLabel = output.providerLabel,
                    model = output.model,
                    text = output.text,
                    title = "علاج المفهوم: $conceptTitle",
                    sourceText = evidence,
                    status = DraftStatus.DRAFT,
                    generation = output
                )
                _uiState.update { it.copy(generationProgressPercent = 98, generationStage = "حفظ مهمة التقوية والمسودة") }
                if (isCloudAuthenticated) {
                    firestoreSync.upsertDraft(currentUserId, draft)
                }
                analyticsTracker?.trackAiExplanationGenerated(
                    providerId = output.providerLabel,
                    taskType = task.id,
                    success = true
                )
                val latestProviders = runCatching { repository.loadProviders() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(snapshot.providers)
                _uiState.update { it.copy(providers = latestProviders, generationProgressPercent = 100, generationStage = "اكتمل التوليد والحفظ") }

                val generatedQuestions = if (task == TaskType.QUESTIONS) {
                    repository.saveQuestionsFromDraftText(workspace.workspaceId, lesson, output.text)
                } else emptyList()
                generatedQuestions.forEach { question ->
                    repository.updateQuestion(
                        questionId = question.id,
                        type = question.type,
                        prompt = question.prompt,
                        options = question.options,
                        correctAnswer = question.correctAnswer,
                        rationale = question.rationale,
                        difficulty = question.difficulty,
                        conceptTag = conceptTitle,
                        reviewed = false
                    )
                }

                _uiState.update {
                    it.copy(
                        page = AppPage.STUDIO,
                        selectedLesson = lesson,
                        task = task,
                        sourceText = evidence,
                        extraInstructions = taskInstructions,
                        generatedResult = output.text,
                        activeDraftProvider = output.providerLabel,
                        activeDraftModel = output.model,
                        activeDraftTemplateId = output.templateId,
                        activeDraftTemplateVersion = output.templateVersion,
                        activeDraftResponseSchemaHash = output.responseSchemaHash,
                        activeDraftRequestFingerprint = output.requestFingerprint,
                        activeDraftContentHash = output.contentHash,
                        activeDraftSourcePdfHash = output.sourcePdfHash,
                        toastMessage = if (task == TaskType.QUESTIONS) {
                            "تم إنشاء مسودة علاجية وإضافة ${generatedQuestions.size} سؤالاً تكيفياً للمفهوم: $conceptTitle"
                        } else {
                            "تم إنشاء شرح تقوية للمفهوم وحفظه كمسودة للمراجعة والاعتماد."
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                analyticsTracker?.trackAiExplanationGenerated(
                    providerId = snapshot.selectedProvider.id,
                    taskType = task.id,
                    success = false
                )
                _uiState.update {
                    it.copy(alert = AlertMessage(
                        "تعذر إنشاء العلاج التعليمي",
                        error.message ?: "تحقق من مصدر الدرس ومزود AI ثم أعد المحاولة."
                    ))
                }
            } finally {
                val latestProviders = runCatching { repository.loadProviders() }.getOrDefault(_uiState.value.providers)
                _uiState.update { it.copy(providers = latestProviders, isBusy = false, generationProgressPercent = null, generationStage = null) }
            }
        }
    }

    fun dismissReinforcementQuiz() {
        _uiState.update {
            it.copy(
                reinforcementConceptTitle = null,
                reinforcementQuestions = emptyList()
            )
        }
    }

    fun recordStandaloneQuestionAttempts(
        questions: List<QuestionItem>,
        answers: Map<String, String>,
        sourceMode: String
    ) {
        val snapshot = _uiState.value
        val wsId = snapshot.workspace?.workspaceId.orEmpty()
        val activeUserId = snapshot.activeLearner.userId
        viewModelScope.launch {
            repository.recordStandaloneQuestionAttempts(
                workspaceId = wsId,
                questions = questions,
                selectedAnswers = answers,
                sourceMode = sourceMode,
                userId = activeUserId
            )
        }
    }

    fun recordInteractiveQuestionAttempt(
        lesson: LessonRef,
        prompt: String,
        conceptTag: String,
        selectedAnswer: String,
        correctAnswer: String,
        isCorrect: Boolean,
        options: List<String> = emptyList(),
        sourceMode: String = "interactive_html"
    ) {
        val snapshot = _uiState.value
        val wsId = snapshot.workspace?.workspaceId.orEmpty().ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val activeUserId = snapshot.activeLearner.userId
        viewModelScope.launch {
            repository.recordInteractiveCheckpointAttempt(
                workspaceId = wsId,
                userId = activeUserId,
                lesson = lesson,
                prompt = prompt,
                conceptTag = conceptTag,
                selectedAnswer = selectedAnswer,
                correctAnswer = correctAnswer,
                isCorrect = isCorrect,
                options = options,
                sourceMode = sourceMode
            )
        }
    }

    fun saveTeacherNote(noteText: String, bookmarked: Boolean) {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson ?: return
        val previous = currentTeacherNote.value
        viewModelScope.launch {
            repository.saveTeacherNote(
                workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                lesson = lesson,
                noteText = noteText,
                bookmarked = bookmarked
            )
            val bookmarkOnlyToggle = previous != null && previous.bookmarked != bookmarked && previous.noteText.trim() == noteText.trim()
            val msg = when {
                bookmarkOnlyToggle && bookmarked -> "تمت إضافة «${lesson.title}» للمفضلة"
                bookmarkOnlyToggle && !bookmarked -> "تمت إزالة الإشارة المرجعية"
                else -> "تم حفظ ملاحظة الدرس بنجاح"
            }
            _uiState.update { it.copy(toastMessage = msg) }
        }
    }

    fun applyPromptTemplate(template: PromptTemplateRecord, openStudio: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                task = template.task,
                activePromptTemplateId = template.id,
                extraInstructions = template.instructions,
                page = if (openStudio) AppPage.STUDIO else state.page,
                toastMessage = "تم تطبيق قالب «${template.title}»"
            )
        }
    }

    fun launchInteractiveActivity(rawText: String, title: String) {
        val parsed = InteractiveActivityParser.parse(rawText, title.ifBlank { "نشاط تفاعلي للدرس" })
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "تعذر تشغيل النشاط التفاعلي",
                        "لم يتم العثور على خطوات منظمة أو بنية JSON صالحة للنشاط. ولّد نشاطاً تفاعلياً أو استخدم قسماً مرقّماً."
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    activeInteractiveActivity = parsed,
                    activeInteractiveLesson = it.selectedLesson
                )
            }
        }
    }

    fun dismissInteractiveActivity() {
        _uiState.update {
            it.copy(activeInteractiveActivity = null, activeInteractiveLesson = null)
        }
    }

    fun recordInteractiveActivityCompleted(earnedXp: Int) {
        val activeUserId = _uiState.value.activeLearner.userId
        viewModelScope.launch {
            repository.recordStudyActivity(userId = activeUserId)
            _uiState.update {
                it.copy(
                    activeInteractiveActivity = null,
                    activeInteractiveLesson = null,
                    alert = AlertMessage(
                        "إنجاز رائع!",
                        "أتممت النشاط التفاعلي وحصلت على +$earnedXp XP."
                    )
                )
            }
        }
    }

    fun recordStudentInteractiveCompletion(earnedXp: Int) {
        val activeUserId = _uiState.value.activeLearner.userId
        viewModelScope.launch {
            repository.recordStudyActivity(userId = activeUserId)
            _uiState.update {
                it.copy(
                    toastMessage = "أحسنت! أتممت النشاط التفاعلي (+$earnedXp XP) — انتقل الآن لاختبار نهاية الدرس"
                )
            }
        }
    }

    fun openGlobalSearch() {
        _uiState.update { it.copy(globalSearchOpen = true) }
    }

    fun closeGlobalSearch() {
        _uiState.update { it.copy(globalSearchOpen = false) }
    }

    fun handleSearchSelectLesson(lesson: LessonRef) {
        if (_uiState.value.userRole == UserRole.LEARNER) {
            _uiState.update { it.copy(globalSearchOpen = false) }
            openStudentStudy(lesson)
            return
        }
        _uiState.update {
            it.copy(
                globalSearchOpen = false,
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                page = AppPage.STUDIO
            )
        }
    }

    fun handleSearchSelectDraft(draft: DraftRecord, lesson: LessonRef?) {
        if (_uiState.value.userRole == UserRole.LEARNER) {
            _uiState.update { it.copy(globalSearchOpen = false) }
            if (lesson != null) openStudentStudy(lesson)
            return
        }
        _uiState.update {
            it.copy(
                globalSearchOpen = false,
                selectedLesson = lesson ?: it.selectedLesson,
                selectedBookKey = lesson?.bookKey ?: it.selectedBookKey,
                task = draft.task,
                generatedResult = draft.content,
                activeDraftProvider = draft.provider,
                activeDraftModel = draft.model,
                activeDraftTemplateId = draft.templateId,
                activeDraftTemplateVersion = draft.templateVersion,
                activeDraftResponseSchemaHash = draft.responseSchemaHash,
                activeDraftPromptUsed = draft.promptUsed,
                activeDraftResponseSchemaJson = draft.responseSchemaJson,
                activeDraftRequestFingerprint = draft.requestFingerprint,
                activeDraftContentHash = draft.contentHash,
                activeDraftSourcePdfHash = draft.sourcePdfHash,
                page = AppPage.STUDIO
            )
        }
    }

    fun handleSearchSelectQuestion(question: QuestionItem) {
        if (_uiState.value.userRole == UserRole.LEARNER) {
            val matchedLesson = _uiState.value.workspace?.lessons?.firstOrNull {
                it.bookKey == question.bookKey &&
                    it.unitKey == question.unitKey &&
                    it.lessonKey == question.lessonKey
            }
            _uiState.update { it.copy(globalSearchOpen = false) }
            if (matchedLesson != null) {
                openStudentStudy(matchedLesson)
            } else {
                launchConceptReinforcement(ConceptMasteryEngine.effectiveConceptTag(question))
            }
            return
        }
        _uiState.update {
            it.copy(
                globalSearchOpen = false,
                page = AppPage.QUIZ_BANK
            )
        }
    }

    fun toggleLessonCompletion(lesson: LessonRef, currentlyCompleted: Boolean) {
        if (!currentlyCompleted) {
            _uiState.update {
                it.copy(
                    toastMessage = "يكتمل الدرس تلقائياً فقط بعد اجتياز اختبار نهاية الدرس (${EduMiniRepository.LESSON_QUIZ_PASS_PERCENT}%+)"
                )
            }
            openStudentStudy(lesson)
            return
        }
        openStudentStudy(lesson)
    }

    fun selectTask(task: TaskType) {
        val defaultTemplate = promptTemplates.value.firstOrNull { it.task == task }
        _uiState.update {
            it.copy(
                task = task,
                activePromptTemplateId = defaultTemplate?.id.orEmpty(),
                generatedResult = "",
                activeDraftProvider = "تحرير يدوي",
                activeDraftModel = "local",
                activeDraftTemplateId = "",
                activeDraftTemplateVersion = 1,
                activeDraftResponseSchemaHash = "",
                activeDraftRequestFingerprint = "",
                activeDraftContentHash = "",
                activeDraftSourcePdfHash = "",
                generatedQuestionExpectedCount = null,
                generatedQuestionExpectedTypeIds = emptyList(),
                generatedQuestionExpectedDifficultyId = ""
            )
        }
    }

    fun updateQuestionCount(count: Int) {
        _uiState.update { it.copy(questionCount = count.coerceIn(1, 30)) }
    }

    fun updateQuestionTypes(typeIds: List<String>) {
        val allowed = com.example.model.QuestionType.entries.map { it.id }.toSet()
        val normalized = typeIds.map { it.trim() }.filter { it in allowed }.distinct()
        if (normalized.isNotEmpty()) {
            _uiState.update { it.copy(selectedQuestionTypeIds = normalized) }
        }
    }

    fun updateQuestionLanguage(language: String) {
        if (language in setOf("ar", "en", "ar_en")) {
            _uiState.update { it.copy(questionLanguage = language) }
        }
    }

    fun updateQuestionStyle(style: String) {
        _uiState.update { it.copy(questionStyle = style) }
    }

    fun updateQuestionDifficulty(difficulty: QuestionDifficulty) {
        _uiState.update { it.copy(questionDifficulty = difficulty) }
    }

    fun toggleAttachPdfPages() {
        _uiState.update { it.copy(attachPdfPages = !it.attachPdfPages) }
    }

    fun updateSourceText(value: String) {
        _uiState.update { it.copy(sourceText = value) }
    }

    fun updateExtraInstructions(value: String) {
        _uiState.update { it.copy(extraInstructions = value) }
    }

    fun updateGeneratedResult(value: String) {
        _uiState.update { it.copy(generatedResult = value) }
    }

    fun updatePackageSourcePages(value: String) {
        _uiState.update { it.copy(packageSourcePages = value) }
    }

    fun loadDraftIntoEditor(draft: DraftRecord) {
        _uiState.update {
            it.copy(
                task = draft.task,
                generatedResult = draft.content,
                activeDraftProvider = draft.provider,
                activeDraftModel = draft.model,
                activeDraftTemplateId = draft.templateId,
                activeDraftTemplateVersion = draft.templateVersion,
                activeDraftResponseSchemaHash = draft.responseSchemaHash,
                activeDraftRequestFingerprint = draft.requestFingerprint,
                activeDraftContentHash = draft.contentHash,
                activeDraftSourcePdfHash = draft.sourcePdfHash
            )
        }
    }

    fun saveEditedDraftVersion() {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson ?: return
        val content = snapshot.generatedResult.trim()
        if (content.isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("المحتوى فارغ", "اكتب أو عدّل المحتوى قبل حفظ نسخة جديدة."))
            }
            return
        }
        viewModelScope.launch {
            val wsId = snapshot.workspace?.workspaceId.orEmpty()
            val savedDraft = repository.persistDraft(
                workspaceId = wsId,
                lesson = lesson,
                task = snapshot.task,
                providerLabel = snapshot.activeDraftProvider,
                model = snapshot.activeDraftModel,
                text = content,
                title = lesson.title,
                sourceText = snapshot.sourceText,
                status = DraftStatus.DRAFT,
                generation = GeneratedResult(
                    text = content,
                    providerLabel = snapshot.activeDraftProvider,
                    model = snapshot.activeDraftModel,
                    createdAt = java.time.Instant.now().toString(),
                    templateId = snapshot.activeDraftTemplateId,
                    templateVersion = snapshot.activeDraftTemplateVersion,
                    responseSchemaHash = snapshot.activeDraftResponseSchemaHash,
                    promptUsed = snapshot.activeDraftPromptUsed,
                    responseSchemaJson = snapshot.activeDraftResponseSchemaJson,
                    requestFingerprint = snapshot.activeDraftRequestFingerprint,
                    sourcePdfHash = snapshot.activeDraftSourcePdfHash
                )
            )
            _uiState.update { it.copy(activeDraftContentHash = savedDraft.contentHash) }
            val cloudSyncResult = if (isCloudAuthenticated) {
                firestoreSync.upsertDraft(currentUserId, savedDraft)
            } else {
                Result.failure(IllegalStateException("local mode"))
            }
            val cloudNote = if (cloudSyncResult.isSuccess) " • مزامنة سحابية ✓" else ""
            _uiState.update {
                it.copy(toastMessage = "تم حفظ نسخة جديدة من «${snapshot.task.labelAr}» كمسودة للمراجعة$cloudNote")
            }
        }
    }

    fun buildAndPublishInteractiveBundle(rawTextOverride: String? = null) {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson
        if (lesson == null) {
            _uiState.update {
                it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً من المكتبة أولاً."))
            }
            return
        }
        val raw = (rawTextOverride ?: snapshot.generatedResult).trim()
        val activity = InteractiveActivityParser.parseStrictJson(raw, lesson.title)
        if (activity == null) {
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "تعذر بناء الحزمة التفاعلية",
                        "أدخل JSON تفاعلياً صالحاً أو محطات مرقمة قبل التصدير إلى حزمة HTML + JS + JSON."
                    )
                )
            }
            return
        }
        val wsId = snapshot.workspace?.workspaceId.orEmpty().ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val pkg = repository.buildAndPublishInteractiveBundle(
                    workspaceId = wsId,
                    lesson = lesson,
                    activity = activity,
                    publishForStudents = true
                )
                if (pkg == null) {
                    _uiState.update {
                        it.copy(alert = AlertMessage("تعذر الحفظ", "تعذر الوصول إلى مجلد الحزم المحلي."))
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            previewingPackage = pkg,
                            studentPreviewMode = false,
                            toastMessage = "تم بناء ونشر حزمة HTML + JS + JSON (v${pkg.version}) للطالب ✓"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر بناء الحزمة", e.message ?: "حدث خطأ غير متوقع"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun approveDraft(draftId: String) {
        viewModelScope.launch {
            val syncedQuestions = repository.approveDraft(draftId)
            if (isCloudAuthenticated) {
                firestoreSync.updateDraftStatus(currentUserId, draftId, DraftStatus.APPROVED)
            }
            _uiState.update {
                it.copy(
                    toastMessage = if (syncedQuestions > 0) {
                        "تم اعتماد النسخة ومزامنة $syncedQuestions أسئلة مع اختبار الدرس ✓"
                    } else {
                        "تم اعتماد النسخة للدرس ومزامنتها ✓"
                    }
                )
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            repository.deleteDraft(draftId)
            if (isCloudAuthenticated) {
                firestoreSync.deleteDraft(currentUserId, draftId)
            }
        }
    }

    fun syncWorkspaceToCloud() {
        val ws = _uiState.value.workspace
        if (ws == null) {
            _uiState.update {
                it.copy(alert = AlertMessage("لا توجد مساحة عمل", "اربط أو استورد مساحة عمل أولاً لمزامنتها مع السحابة."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val localDrafts = repository.getWorkspaceDraftsSnapshot(ws.workspaceId, ws.rootPath)
                var draftsSynced = 0
                for (draft in localDrafts) {
                    if (firestoreSync.upsertDraft(currentUserId, draft).isSuccess) {
                        draftsSynced++
                    }
                }
                val activeLearner = _uiState.value.activeLearner
                val activeLearnerId = activeLearner.userId
                // Firestore progress remains account-scoped. Until its contract carries a local
                // learner ID, only the legacy default learner can safely use this sync path.
                val localProgress = if (activeLearnerId == LearnerProfile.DEFAULT_USER_ID) {
                    repository.getWorkspaceProgressSnapshot(ws.workspaceId, activeLearnerId)
                } else {
                    emptyList()
                }
                var progressSynced = 0
                val lessonsByKey = ws.lessons.associateBy { "${it.bookKey}|${it.unitKey}|${it.lessonKey}" }
                for (prog in localProgress) {
                    val matchedLesson = lessonsByKey["${prog.bookKey}|${prog.unitKey}|${prog.lessonKey}"]
                        ?: LessonRef(
                            grade = ws.gradeKey,
                            rootPath = ws.rootPath,
                            bookKey = prog.bookKey,
                            bookTitle = prog.bookKey,
                            subjectKey = prog.bookKey,
                            unitKey = prog.unitKey,
                            unitTitle = prog.unitKey,
                            lessonKey = prog.lessonKey,
                            title = prog.lessonKey,
                            workspaceId = ws.workspaceId
                        )
                    val res = firestoreSync.upsertLessonProgress(
                        userId = currentUserId,
                        workspaceId = ws.workspaceId,
                        lesson = matchedLesson,
                        completed = prog.completed,
                        xpEarned = if (prog.completed) EduMiniRepository.POINTS_LESSON_COMPLETED else 0,
                        scorePercent = if (prog.completed) 100 else 0
                    )
                    if (res.isSuccess) progressSynced++
                }
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تمت المزامنة السحابية",
                            if (activeLearnerId == LearnerProfile.DEFAULT_USER_ID) {
                                "تمت مزامنة $draftsSynced مسودة و $progressSynced سجل تقدم للملف الافتراضي إلى Firebase Firestore."
                            } else {
                                "تمت مزامنة $draftsSynced مسودة. لم تتم مزامنة تقدم «${activeLearner.displayName}» لأن Firestore الحالي لا يفصل ملفات المتعلمين المحلية؛ بقي التقدم محفوظاً محلياً."
                            }
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذرت المزامنة", e.message ?: "حدث خطأ أثناء الاتصال بالسحابة"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun loadCloudDraftIntoEditor(cloudDraft: CloudDraft) {
        _uiState.update {
            it.copy(
                task = cloudDraft.taskTypeOrDefault(),
                generatedResult = cloudDraft.content,
                activeDraftProvider = cloudDraft.provider,
                activeDraftModel = cloudDraft.model,
                activeDraftTemplateId = cloudDraft.templateId,
                activeDraftTemplateVersion = cloudDraft.templateVersion.toInt().coerceAtLeast(1),
                activeDraftResponseSchemaHash = cloudDraft.responseSchemaHash,
                activeDraftPromptUsed = cloudDraft.promptUsed,
                activeDraftResponseSchemaJson = cloudDraft.responseSchemaJson,
                activeDraftRequestFingerprint = cloudDraft.requestFingerprint,
                activeDraftContentHash = cloudDraft.contentHash,
                activeDraftSourcePdfHash = cloudDraft.sourcePdfHash,
                page = AppPage.STUDIO,
                toastMessage = "تم تحميل المسودة السحابية «${cloudDraft.title}»"
            )
        }
    }

    fun saveCurrentContentAsLessonText(fromGeneratedOutput: Boolean) {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson ?: return
        val content = if (fromGeneratedOutput) snapshot.generatedResult else snapshot.sourceText
        if (content.isBlank()) {
            _uiState.update { it.copy(alert = AlertMessage("محتوى فارغ", "لا يوجد نص لحفظه كنص للدرس.")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveLessonText(
                    workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                    lesson = lesson,
                    content = repository.extractLessonTextFromOutput(content),
                    extractionMethod = if (fromGeneratedOutput) "ai_generated" else "manual",
                    sourcePdfHash = snapshot.activeDraftSourcePdfHash,
                    templateId = snapshot.activeDraftTemplateId,
                    promptUsed = snapshot.activeDraftPromptUsed,
                    responseSchemaJson = snapshot.activeDraftResponseSchemaJson
                )
            }.onSuccess { saved ->
                _uiState.update { it.copy(toastMessage = "تم حفظ نص الدرس بنجاح (الإصدار ${saved.version}) ✓") }
            }.onFailure { err ->
                _uiState.update { it.copy(alert = AlertMessage("تعذر الحفظ", err.message ?: "خطأ في حفظ نص الدرس")) }
            }
        }
    }

    fun useStoredLessonTextAsSource() {
        val stored = currentLessonText.value?.content ?: return
        if (stored.isBlank()) return
        _uiState.update {
            it.copy(
                sourceText = stored,
                toastMessage = "تم إدراج النص المحفوظ في مصدر الاستوديو ✓"
            )
        }
    }

    fun approveCloudDraft(cloudDraft: CloudDraft) {
        viewModelScope.launch {
            val res = firestoreSync.updateDraftStatus(currentUserId, cloudDraft.id, DraftStatus.APPROVED)
            _uiState.update {
                it.copy(
                    toastMessage = if (res.isSuccess) {
                        "تم اعتماد المسودة السحابية ✓"
                    } else {
                        "تعذر تحديث حالة المسودة السحابية"
                    }
                )
            }
        }
    }

    fun deleteCloudDraft(cloudDraft: CloudDraft) {
        viewModelScope.launch {
            val res = firestoreSync.deleteDraft(currentUserId, cloudDraft.id)
            _uiState.update {
                it.copy(
                    toastMessage = if (res.isSuccess) {
                        "تم حذف المسودة من السحابة"
                    } else {
                        "تعذر حذف المسودة السحابية"
                    }
                )
            }
        }
    }

    private fun canManageSystemAiModels(): Boolean =
        isCloudAuthenticated && _uiState.value.authenticatedUserRole == UserRole.ADMIN && _uiState.value.isSystemAdmin

    fun addAiModel(model: AiModelDefinition) {
        if (!canManageSystemAiModels()) {
            _uiState.update { it.copy(alert = AlertMessage("صلاحية غير كافية", "إدارة كتالوج النماذج متاحة لمدير النظام فقط.")) }
            return
        }
        viewModelScope.launch {
            runCatching { firestoreSync.upsertAiModelDefinition(model) }
                .onSuccess {
                    val catalog = runCatching { firestoreSync.fetchAiModelCatalog() }.getOrDefault(_uiState.value.aiModelCatalog)
                    _uiState.update { it.copy(aiModelCatalog = catalog, toastMessage = "تمت إضافة النموذج إلى القائمة المركزية.") }
                }
                .onFailure { error -> _uiState.update { it.copy(alert = AlertMessage("تعذر إضافة النموذج", error.message ?: "تحقق من صلاحيات مدير النظام.")) } }
        }
    }

    fun updateAiModel(originalModelId: String, model: AiModelDefinition) {
        if (!canManageSystemAiModels()) {
            _uiState.update { it.copy(alert = AlertMessage("صلاحية غير كافية", "إدارة كتالوج النماذج متاحة لمدير النظام فقط.")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                firestoreSync.renameAiModelDefinition(originalModelId, model)
            }.onSuccess {
                val catalog = runCatching { firestoreSync.fetchAiModelCatalog() }.getOrDefault(_uiState.value.aiModelCatalog)
                _uiState.update { it.copy(aiModelCatalog = catalog, toastMessage = "تم تحديث النموذج المركزي.") }
            }.onFailure { error ->
                _uiState.update { it.copy(alert = AlertMessage("تعذر تعديل النموذج", error.message ?: "تحقق من بيانات النموذج.")) }
            }
        }
    }

    fun deleteAiModel(modelId: String) {
        if (!canManageSystemAiModels()) {
            _uiState.update { it.copy(alert = AlertMessage("صلاحية غير كافية", "إدارة كتالوج النماذج متاحة لمدير النظام فقط.")) }
            return
        }
        viewModelScope.launch {
            runCatching { firestoreSync.deleteAiModelDefinition(modelId) }
                .onSuccess {
                    val catalog = runCatching { firestoreSync.fetchAiModelCatalog() }.getOrDefault(_uiState.value.aiModelCatalog)
                    _uiState.update { it.copy(aiModelCatalog = catalog, toastMessage = "تم حذف النموذج من القائمة المركزية.") }
                }
                .onFailure { error -> _uiState.update { it.copy(alert = AlertMessage("تعذر حذف النموذج", error.message ?: "قد يكون النموذج افتراضياً.")) } }
        }
    }

    fun setDefaultAiModel(provider: ProviderType, modelId: String) {
        if (!canManageSystemAiModels()) {
            _uiState.update { it.copy(alert = AlertMessage("صلاحية غير كافية", "تحديد النموذج الافتراضي متاح لمدير النظام فقط.")) }
            return
        }
        viewModelScope.launch {
            runCatching { firestoreSync.setDefaultAiModel(provider, modelId) }
                .onSuccess {
                    val catalog = runCatching { firestoreSync.fetchAiModelCatalog() }.getOrDefault(_uiState.value.aiModelCatalog)
                    _uiState.update { it.copy(aiModelCatalog = catalog, toastMessage = "تم تغيير النموذج الافتراضي المركزي.") }
                }
                .onFailure { error -> _uiState.update { it.copy(alert = AlertMessage("تعذر تغيير النموذج الافتراضي", error.message ?: "النموذج غير متاح.")) } }
        }
    }

    fun selectDefaultAiProvider(type: ProviderType) {
        viewModelScope.launch {
            runCatching { repository.saveSelectedProviderType(type) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            selectedProvider = type,
                            toastMessage = "تم تغيير مزود التوليد الافتراضي إلى ${type.displayName}. الطلبات الجديدة ستستخدم هذا المزود فقط."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(alert = AlertMessage("تعذر تغيير مزود AI", error.message ?: "تعذر حفظ الاختيار"))
                    }
                }
        }
    }

    fun updateProviderType(type: ProviderType) {
        _uiState.update { state ->
            state.copy(providerForm = state.providerForm.copy(provider = type))
        }
    }

    fun updateFormBaseUrl(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(baseUrl = value)) }
    }

    fun updateFormApiKey(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(apiKey = value)) }
    }

    fun startEditProvider(profile: ProviderConfig) {
        _uiState.update {
            it.copy(
                providerForm = ProviderFormState(
                    editingProviderId = profile.id,
                    provider = profile.provider,
                    baseUrl = profile.baseUrl.ifBlank { "https://api.openai.com/v1" },
                    apiKey = profile.apiKey,
                    dailyRequestLimit = profile.dailyRequestLimit.toString(),
                    quotaTimeZone = profile.quotaTimeZone
                )
            )
        }
    }

    fun cancelEditProvider() {
        _uiState.update { it.copy(providerForm = ProviderFormState()) }
    }

    fun testFormProvider() {
        val state = _uiState.value
        val form = state.providerForm
        if (form.apiKey.trim().isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("بيانات ناقصة", "أدخل مفتاح API أولاً."))
            }
            return
        }
        val selectedModel = state.aiModelCatalog.firstOrNull {
            it.provider == form.provider && it.isDefault && it.isEnabled &&
                it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        }?.modelId ?: state.aiModelCatalog.firstOrNull {
            it.provider == form.provider && it.isEnabled &&
                it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        }?.modelId.orEmpty()
        if (selectedModel.isBlank()) {
            _uiState.update {
                it.copy(alert = AlertMessage("لا يوجد نموذج متاح", "أضف نموذجاً مفعّلاً لهذا المزود إلى كتالوج النماذج أولاً."))
            }
            return
        }
        val probe = ProviderConfig(
            id = form.editingProviderId ?: "probe",
            provider = form.provider,
            label = state.providers.firstOrNull { it.id == form.editingProviderId && it.provider == form.provider }?.label
                ?: ProviderApiKeyNames.nextName(form.provider, state.providers),
            model = selectedModel,
            baseUrl = form.baseUrl.trim(),
            apiKey = form.apiKey.trim(),
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        testSavedProvider(probe)
    }

    fun testSavedProvider(profile: ProviderConfig) {
        val state = _uiState.value
        if (state.isTestingProvider) return
        val testModel = profile.model.ifBlank {
            state.aiModelCatalog.firstOrNull {
                it.provider == profile.provider && it.isDefault && it.isEnabled &&
                    it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
            }?.modelId ?: state.aiModelCatalog.firstOrNull {
                it.provider == profile.provider && it.isEnabled &&
                    it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
            }?.modelId.orEmpty()
        }
        if (testModel.isBlank()) {
            _uiState.update {
                it.copy(alert = AlertMessage("لا يوجد نموذج متاح", "أضف نموذجاً مفعّلاً لهذا المزود إلى كتالوج النماذج أولاً."))
            }
            return
        }
        val profileToTest = profile.copy(model = testModel)
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingProvider = true) }
            try {
                val report = repository.testProviderConnection(profileToTest)
                _uiState.update {
                    it.copy(alert = AlertMessage("نجاح اختبار النموذج", report))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AiExecutionException) {
                val title = when (error.kind) {
                    AiExecutionFailureKind.INVALID_CREDENTIALS -> "مفتاح API غير صالح"
                    AiExecutionFailureKind.QUOTA_EXHAUSTED -> "حصة API مستنفدة"
                    AiExecutionFailureKind.RATE_LIMITED -> "النموذج مزدحم أو تجاوز حد الطلبات"
                    AiExecutionFailureKind.MODEL_UNAVAILABLE -> "النموذج غير متاح"
                    AiExecutionFailureKind.NETWORK_ERROR -> "خطأ في الاتصال"
                    AiExecutionFailureKind.EMPTY_RESPONSE -> "استجابة فارغة"
                    AiExecutionFailureKind.INVALID_REQUEST -> "طلب API غير صالح"
                    AiExecutionFailureKind.PROVIDER_UNAVAILABLE -> "تعذر الوصول إلى المزود"
                }
                _uiState.update {
                    it.copy(alert = AlertMessage(title, error.message ?: "تعذر اختبار النموذج"))
                }
            } catch (error: Exception) {
                val safeMessage = error.message.orEmpty()
                    .replace(profile.apiKey, "[مفتاح مخفي]")
                    .ifBlank { "تعذر اختبار النموذج بسبب خطأ غير متوقع." }
                _uiState.update {
                    it.copy(alert = AlertMessage("فشل اختبار النموذج", safeMessage))
                }
            } finally {
                _uiState.update { it.copy(isTestingProvider = false) }
            }
        }
    }

    fun clearRecentRuns() {
        viewModelScope.launch {
            repository.clearGenerationRuns()
            _uiState.update {
                it.copy(alert = AlertMessage("تم المسح", "تم مسح سجل آخر المحاولات."))
            }
        }
    }

    fun savePromptTemplate(title: String, task: TaskType, instructions: String, responseSchemaJson: String, responseMappingJson: String, templateId: String?) {
        if (title.isBlank() || instructions.isBlank()) return
        viewModelScope.launch {
            try {
                repository.savePromptTemplate(
                    title = title,
                    task = task,
                    instructions = instructions,
                    responseSchemaJson = if (task == TaskType.QUESTIONS) "" else responseSchemaJson,
                    responseMappingJson = if (task == TaskType.QUESTIONS) "" else responseMappingJson,
                    responseFormat = if (task == TaskType.QUESTIONS || responseSchemaJson.isNotBlank()) "json" else "text",
                    templateId = templateId
                )
                _uiState.update {
                    it.copy(alert = AlertMessage("تم الحفظ", if (templateId == null) {
                        "تمت إضافة قالب التوجيه «${title.trim()}»."
                    } else {
                        "تم تحديث القالب «${title.trim()}» وزيادة إصداره."
                    }))
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر حفظ القالب", error.message ?: "تحقق من JSON الخاص بمخطط الاستجابة وخريطة الحقول."))
                }
            }
        }
    }

    fun deletePromptTemplate(id: String) {
        viewModelScope.launch {
            repository.deletePromptTemplate(id)
        }
    }

    fun saveGeneratedDraftToQuestionBank() {
        val snapshot = _uiState.value
        if (snapshot.task != TaskType.QUESTIONS) {
            _uiState.update {
                it.copy(alert = AlertMessage("إجراء غير متاح", "استخراج بنك الأسئلة متاح لمهمة بنك الأسئلة فقط."))
            }
            return
        }
        val lesson = snapshot.selectedLesson
        if (lesson == null) {
            _uiState.update {
                it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً من المكتبة أولاً."))
            }
            return
        }
        val text = snapshot.generatedResult.trim()
        if (text.isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("لا يوجد نص", "ولّد الأسئلة أو أدخلها في المحرر قبل الاستخراج لبنك الأسئلة."))
            }
            return
        }
        val canonicalWs = snapshot.workspace?.workspaceId.orEmpty().ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val expectedQuestionCount = snapshot.generatedQuestionExpectedCount ?: snapshot.questionCount
        val expectedQuestionTypeIds = snapshot.generatedQuestionExpectedTypeIds.ifEmpty { snapshot.selectedQuestionTypeIds }
        val expectedQuestionDifficultyId = snapshot.generatedQuestionExpectedDifficultyId.ifBlank { snapshot.questionDifficulty.id }
        val parsedResult = runCatching {
            QuestionBankEngine.parseGeneratedQuestions(
                rawText = text,
                workspaceId = canonicalWs,
                lesson = lesson,
                expectedCount = expectedQuestionCount,
                selectedTypeIds = expectedQuestionTypeIds,
                expectedDifficultyId = expectedQuestionDifficultyId
            )
        }
        if (parsedResult.isFailure) {
            _uiState.update {
                it.copy(alert = AlertMessage(
                    "لم تُحفظ الأسئلة",
                    parsedResult.exceptionOrNull()?.message ?: "استجابة الأسئلة لا تطابق عقد بنك الأسئلة."
                ))
            }
            return
        }
        val parsed = parsedResult.getOrThrow()
        viewModelScope.launch {
            val savedResult = runCatching {
                repository.saveQuestionsFromDraftText(
                    workspaceId = canonicalWs,
                    lesson = lesson,
                    rawText = text,
                    expectedQuestionCount = expectedQuestionCount,
                    expectedQuestionTypeIds = expectedQuestionTypeIds,
                    expectedQuestionDifficultyId = expectedQuestionDifficultyId
                )
            }
            val saved = savedResult.getOrElse { error ->
                _uiState.update {
                    it.copy(alert = AlertMessage("لم تُحفظ الأسئلة", error.message ?: "تعذر إدراج دفعة الأسئلة."))
                }
                return@launch
            }
            if (saved.isEmpty()) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "الأسئلة محفوظة مسبقاً",
                            "جميع الأسئلة المستخرجة (${parsed.size}) مرتبطة بالفعل ببنك أسئلة واختبار درس «${lesson.title}»."
                        )
                    )
                }
            } else {
                val dups = QuestionBankEngine.markDuplicates(saved).count { it.isDuplicate }
                val dupNote = if (dups > 0) " (بينها $dups تشابه داخلي)" else ""
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تم الحفظ في بنك الأسئلة",
                            "تمت إضافة ${saved.size} سؤال جديد لدرس «${lesson.title}» وربطها باختبار الطالب$dupNote."
                        )
                    )
                }
            }
        }
    }

    fun addManualQuestion(
        type: QuestionType,
        prompt: String,
        options: List<String>,
        correctAnswer: String,
        rationale: String,
        difficulty: QuestionDifficulty,
        conceptTag: String = ""
    ) {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson ?: return
        viewModelScope.launch {
            repository.addManualQuestion(
                workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                lesson = lesson,
                type = type,
                prompt = prompt,
                options = options,
                correctAnswer = correctAnswer,
                rationale = rationale,
                difficulty = difficulty,
                conceptTag = conceptTag
            )
        }
    }

    fun updateQuestion(
        questionId: String,
        type: QuestionType,
        prompt: String,
        options: List<String>,
        correctAnswer: String,
        rationale: String,
        difficulty: QuestionDifficulty,
        conceptTag: String,
        reviewed: Boolean
    ) {
        viewModelScope.launch {
            repository.updateQuestion(
                questionId = questionId,
                type = type,
                prompt = prompt,
                options = options,
                correctAnswer = correctAnswer,
                rationale = rationale,
                difficulty = difficulty,
                conceptTag = conceptTag,
                reviewed = reviewed
            )
            _uiState.update { it.copy(toastMessage = "تم تحديث السؤال والمفهوم المرتبط") }
        }
    }

    fun toggleQuestionReviewed(questionId: String, currentlyReviewed: Boolean) {
        viewModelScope.launch {
            repository.toggleQuestionReviewed(questionId, currentlyReviewed)
        }
    }

    fun deleteQuestion(questionId: String) {
        viewModelScope.launch {
            repository.deleteQuestion(questionId)
        }
    }

    fun recordQuizCompleted(correct: Int, total: Int, earnedXp: Int) {
        val activeUserId = _uiState.value.activeLearner.userId
        val selectedLessonKey = _uiState.value.selectedLesson?.lessonKey ?: "question_bank"
        val scorePercent = if (total > 0) ((correct * 100f) / total).toInt().coerceIn(0, 100) else 0
        viewModelScope.launch {
            repository.recordStudyActivity(userId = activeUserId)
            analyticsTracker?.trackQuizAttemptSubmitted(
                lessonKey = selectedLessonKey,
                score = scorePercent,
                passed = scorePercent >= EduMiniRepository.LESSON_QUIZ_PASS_PERCENT
            )
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "أحسنت!",
                        "أنهيت الاختبار بنتيجة $correct/$total وحصلت على +$earnedXp XP."
                    )
                )
            }
        }
    }

    fun createAssessment(
        title: String,
        kind: AssessmentKind,
        questionIds: List<String>,
        timeLimitMinutes: Int,
        passScorePercent: Int
    ) {
        val wsId = _uiState.value.workspace?.workspaceId.orEmpty()
        if (wsId.isBlank() || title.isBlank() || questionIds.isEmpty()) return
        viewModelScope.launch {
            val created = repository.createAssessment(
                workspaceId = wsId,
                title = title,
                kind = kind,
                questionIds = questionIds,
                timeLimitMinutes = timeLimitMinutes,
                passScorePercent = passScorePercent
            )
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "تم إنشاء التقييم",
                        "حُفظ «${created.title}» (${created.questionIds.size} سؤال • ${created.kind.labelAr})."
                    )
                )
            }
        }
    }

    fun deleteAssessment(assessmentId: String) {
        viewModelScope.launch {
            repository.deleteAssessment(assessmentId)
        }
    }

    fun recordAssessmentAttempt(
        assessment: AssessmentRecord,
        correctCount: Int,
        totalQuestions: Int,
        scorePercent: Int,
        durationSeconds: Int,
        earnedXp: Int
    ) {
        val activeUserId = _uiState.value.activeLearner.userId
        viewModelScope.launch {
            val record = repository.recordAssessmentAttempt(
                assessment = assessment,
                correctCount = correctCount,
                totalQuestions = totalQuestions,
                scorePercent = scorePercent,
                durationSeconds = durationSeconds,
                earnedXp = earnedXp,
                userId = activeUserId
            )
            val passLabel = if (record.passed) "اجتزت التقييم بنجاح" else "لم تصل لدرجة النجاح (${assessment.passScorePercent}%)"
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "نتيجة ${assessment.title}",
                        "$passLabel • $correctCount/$totalQuestions ($scorePercent%) • +$earnedXp XP."
                    )
                )
            }
        }
    }

    fun prepareExportAssessmentMarkdown(assessment: AssessmentRecord) {
        _uiState.update { it.copy(pendingExportAssessment = assessment) }
    }

    fun exportPendingAssessmentMarkdown(uri: Uri) {
        val assessment = _uiState.value.pendingExportAssessment ?: return
        val questionsById = workspaceQuestions.value.associateBy { it.id }
        val assessmentQuestions = assessment.questionIds.mapNotNull { questionsById[it] }
        val markdown = QuestionBankEngine.exportAssessmentMarkdown(assessment, assessmentQuestions)
        viewModelScope.launch {
            try {
                val out = repository.openOutputStreamForUri(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف التصدير")
                out.writer(Charsets.UTF_8).use { it.write(markdown) }
                _uiState.update {
                    it.copy(
                        pendingExportAssessment = null,
                        alert = AlertMessage("تم تصدير Markdown", "تم حفظ ملف «${assessment.title}» بصيغة Markdown بنجاح.")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        pendingExportAssessment = null,
                        alert = AlertMessage("تعذر التصدير", e.message ?: "حدث خطأ أثناء الحفظ")
                    )
                }
            }
        }
    }

    fun exportLearningProgressReport(uri: Uri) {
        val snapshot = _uiState.value
        val learner = snapshot.activeLearner
        val report = learningProgressReport.value
        val summary = dashboardSummary.value
        val markdown = buildString {
            appendLine("# تقرير متابعة التعلّم")
            appendLine()
            appendLine("- المتعلّم: ${learner.displayName}")
            appendLine("- المكتبة: ${snapshot.workspace?.title.orEmpty()}")
            appendLine("- تاريخ التقرير: ${java.time.LocalDate.now()}")
            appendLine()
            appendLine("## ملخص الأداء")
            appendLine("- الدروس المكتملة: ${summary.completedLessons} من ${summary.totalLessons}")
            appendLine("- المحاولات المسجلة: ${report.questionAttemptsCount}")
            appendLine("- المفاهيم التي جرى قياسها: ${report.conceptsObserved}")
            appendLine("- دقة المحاولة الأولى: ${report.firstTryAccuracyPercent?.let { "$it%" } ?: "بيانات غير كافية"}")
            appendLine("- دقة أحدث المحاولات: ${report.latestAccuracyPercent?.let { "$it%" } ?: "بيانات غير كافية"}")
            appendLine("- المفاهيم ذات الإتقان المستقر: ${report.stableConceptsCount} من ${report.eligibleConceptsCount}")
            appendLine("- نسبة الإتقان المستقر: ${report.stableConceptsPercent?.let { "$it%" } ?: "لم تتوفر عينة كافية"}")
            appendLine("- الاسترجاع بعد أسبوع أو أكثر: ${report.delayedRecallPercent?.let { "$it%" } ?: "لم تتوفر عينة كافية"}")
            appendLine("- فارق الاسترجاع المؤجل: ${report.estimatedRecallDropPercent?.let { "$it%" } ?: "غير قابل للحساب حالياً"}")
            appendLine()
            appendLine("## أثر جلسات التقوية")
            appendLine(
                report.reinforcementImpactPercent?.let {
                    "- التغير في الأداء اللاحق على الأسئلة نفسها: ${if (it >= 0) "+" else ""}$it% عبر ${report.reinforcementImpactConceptCount} مفهوماً."
                } ?: "- لا توجد قياسات قبل جلسة التقوية وبعدها على عدد كافٍ من الأسئلة نفسها."
            )
            appendLine("هذا مؤشر ارتباطي بعد التدريب، وليس دليلاً سببياً على أثر الشرح وحده.")
            appendLine()
            appendLine("## الأخطاء المتكررة")
            appendLine("- المفاهيم ذات الأخطاء المتكررة: ${report.conceptsWithRecurringErrorsCount}")
            if (report.recurringConceptTags.isNotEmpty()) {
                appendLine("- المفاهيم: ${report.recurringConceptTags.joinToString("، ")}")
            }
            report.recurringMistakes.forEach { mistake ->
                appendLine("- ${mistake.conceptTag}: تكررت الإجابة «${mistake.selectedAnswer}» ${mistake.occurrences} مرات.")
                if (mistake.questionPrompt.isNotBlank()) appendLine("  - السؤال: ${mistake.questionPrompt}")
                if (mistake.expectedAnswer.isNotBlank()) appendLine("  - الإجابة المعتمدة: ${mistake.expectedAnswer}")
            }
            appendLine()
            appendLine("## نتائج التقييم الأسبوعية")
            if (report.weeklyAssessments.isEmpty()) appendLine("- لا توجد اختبارات مسجلة خلال الأسابيع الثمانية الأخيرة.")
            report.weeklyAssessments.forEach { week ->
                appendLine("- أسبوع ${week.label}: متوسط ${week.averageScorePercent}%، ${week.attemptsCount} اختبار، ${week.passedCount} اجتياز.")
            }
            appendLine()
            appendLine("## حدود التقرير")
            appendLine("- نسبة الاسترجاع المؤجل تُحسب فقط عند إعادة قياس السؤال نفسه بعد سبعة أيام على الأقل، وبعينة لا تقل عن ثلاثة أسئلة.")
            appendLine("- الإتقان المستقر يتطلب ثلاثة أسئلة على الأقل وقياساً في يومين مختلفين.")
            appendLine("- بيانات جلسات التقوية تقيس التحسن اللاحق في الأسئلة المشتركة، ولا تثبت بمفردها أن الشرح المولد هو سبب التحسن.")
            appendLine("- غياب البيانات لا يعني أن مستوى الطالب صفر؛ بل يعني أن القياس لم يكتمل.")
        }
        viewModelScope.launch {
            try {
                val out = repository.openOutputStreamForUri(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف التقرير")
                out.writer(Charsets.UTF_8).use { it.write(markdown) }
                _uiState.update {
                    it.copy(alert = AlertMessage("تم إنشاء التقرير", "تم تصدير تقرير متابعة التعلّم للمتعلّم «${learner.displayName}»."))
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر تصدير التقرير", error.message ?: "حدث خطأ أثناء حفظ التقرير"))
                }
            }
        }
    }

    fun exportLegacyWorkspaceZip(uri: Uri) {
        val workspace = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val out = repository.openOutputStreamForUri(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف الحفظ المختار")
                val snapshot = _uiState.value
                val receipt = out.use { stream ->
                    repository.exportLegacyWorkspaceZip(
                        stream,
                        workspace,
                        learnerProfiles = snapshot.availableLearners,
                        activeLearnerId = snapshot.activeLearner.userId
                    )
                }
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تم تصدير الحزمة",
                            "تم إنشاء ملف ZIP بصيغة التوافق القديمة ويحتوي على ${receipt.entriesCount} ملف (${receipt.draftsCount} مسودة، ${receipt.questionsCount} سؤال) مع التحقق SHA-256."
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر التصدير", e.message ?: "حدث خطأ أثناء إنشاء ملف ZIP"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun exportFullBackupJson(uri: Uri) {
        val workspace = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val out = repository.openOutputStreamForUri(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية")
                val snapshot = _uiState.value
                val itemsCount = out.use { stream ->
                    repository.exportFullBackupJson(
                        stream,
                        workspace,
                        learnerProfiles = snapshot.availableLearners,
                        activeLearnerId = snapshot.activeLearner.userId
                    )
                }
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تم تصدير النسخة الاحتياطية",
                            "تم حفظ $itemsCount عنصر (المسودات، الأسئلة، التقييمات، الملاحظات) في ملف JSON محلي آمن بدون مفاتيح API."
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر النسخ الاحتياطي", e.message ?: "حدث خطأ أثناء التصدير"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun restoreFullBackupJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val raw = repository.readUtf8FromUri(uri)
                    ?: throw IllegalStateException("تعذر قراءة ملف النسخة الاحتياطية")
                val summaryMsg = repository.importFullBackupJson(
                    rawJson = raw,
                    targetWorkspaceId = _uiState.value.workspace?.workspaceId.orEmpty()
                )
                loadLearnerProfiles()
                _uiState.update {
                    it.copy(alert = AlertMessage("تمت الاستعادة", summaryMsg))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("فشلت الاستعادة", e.message ?: "ملف النسخة الاحتياطية غير صالح"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun inspectWorkspaceZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    workspaceOperation = WorkspaceOperationProgress("فحص حزمة المحتوى", "فتح ملف ZIP وتجهيز الفحص", 3)
                )
            }
            try {
                val staged = repository.inspectWorkspaceZip(uri) { stage, percent ->
                    _uiState.update {
                        it.copy(workspaceOperation = WorkspaceOperationProgress("فحص حزمة المحتوى", stage, percent))
                    }
                }
                if (staged.grades.isEmpty()) {
                    repository.discardStagedPreview(staged)
                    _uiState.update {
                        it.copy(
                            alert = AlertMessage(
                                "حزمة غير مكتملة",
                                "لم يتم العثور على فهارس صفوف وكتب (index.json) صالحة داخل ملف ZIP."
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(stagedPreview = staged) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("خطأ في ZIP", e.message ?: "تعذر فحص ملف ZIP"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    fun confirmStagedImport(selectedGrades: List<String>, allowReviewImport: Boolean = false) {
        val preview = _uiState.value.stagedPreview ?: return
        if (selectedGrades.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    workspaceOperation = WorkspaceOperationProgress("استيراد الصفوف والمواد", "بدء تجهيز الصفوف المحددة", 2)
                )
            }
            try {
                val committed = repository.commitStagedWorkspaces(
                    preview = preview,
                    selectedGrades = selectedGrades,
                    allowReviewImport = allowReviewImport
                ) { stage, percent ->
                    _uiState.update {
                        it.copy(workspaceOperation = WorkspaceOperationProgress("استيراد الصفوف والمواد", stage, percent))
                    }
                }
                val primary = committed.lastOrNull()
                if (primary != null) {
                    val diskBytes = repository.computeWorkspaceDiskBytes(primary.rootPath)
                    val warningsCount = committed.sumOf { it.warnings.size }
                    val warningNote = if (warningsCount > 0) " (مع $warningsCount تنبيه)" else ""
                    _uiState.update {
                        it.copy(
                            stagedPreview = null,
                            selectedWorkspaceId = primary.workspaceId,
                            workspace = primary,
                            activeWorkspaceDiskBytes = diskBytes,
                            selectedBookKey = primary.books.firstOrNull()?.bookKey.orEmpty(),
                            selectedLesson = null,
                            alert = AlertMessage(
                                "تم استيراد ${committed.size} صف",
                                "نشط الآن: ${primary.gradeKey} • ${primary.books.size} كتاب • ${primary.lessons.size} درس$warningNote"
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(stagedPreview = null) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        stagedPreview = null,
                        alert = AlertMessage("خطأ في الاستيراد", e.message ?: "تعذر اعتماد الحزمة")
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false, workspaceOperation = null) }
            }
        }
    }

    fun dismissStagedPreview() {
        val preview = _uiState.value.stagedPreview
        _uiState.update { it.copy(stagedPreview = null) }
        if (preview != null) {
            repository.discardStagedPreview(preview)
        }
    }

    fun deleteWorkspace(workspaceId: String, rootPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                repository.deleteWorkspace(workspaceId, rootPath)
                _uiState.update { state ->
                    val deletingActive = state.workspace?.workspaceId == workspaceId || state.selectedWorkspaceId == workspaceId
                    state.copy(
                        selectedWorkspaceId = if (deletingActive) null else state.selectedWorkspaceId,
                        workspace = if (deletingActive) null else state.workspace,
                        activeWorkspaceDiskBytes = if (deletingActive) 0L else state.activeWorkspaceDiskBytes,
                        selectedBookKey = if (deletingActive) "" else state.selectedBookKey,
                        selectedLesson = if (deletingActive) null else state.selectedLesson,
                        alert = AlertMessage("تم الحذف", "تم حذف مساحة العمل وملفاتها المحلية من الجهاز.")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر الحذف", e.message ?: "حدث خطأ أثناء حذف مساحة العمل"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun importWorkspaceZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val imported = repository.importWorkspaceZip(uri)
                loadLearnerProfiles()
                val diskBytes = repository.computeWorkspaceDiskBytes(imported.rootPath)
                val warningSuffix = if (imported.warnings.isNotEmpty()) {
                    "\n\nتنبيهات:\n• ${imported.warnings.joinToString("\n• ")}"
                } else ""
                _uiState.update {
                    it.copy(
                        selectedWorkspaceId = imported.workspaceId,
                        workspace = imported,
                        activeWorkspaceDiskBytes = diskBytes,
                        selectedBookKey = imported.books.firstOrNull()?.bookKey.orEmpty(),
                        selectedLesson = null,
                        alert = AlertMessage(
                            title = "تم الاستيراد",
                            message = "${imported.gradeKey} • ${imported.books.size} كتاب • ${imported.lessons.size} درس$warningSuffix"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            title = "خطأ في ZIP",
                            message = e.message ?: "تعذر قراءة ملف ZIP"
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun generateContent() {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson
        if (lesson == null) {
            _uiState.update { it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً من المكتبة أولاً.")) }
            return
        }
        val hasPdfSource = snapshot.attachPdfPages && !lesson.pdfPath.isNullOrBlank()
        if (snapshot.sourceText.trim().isEmpty() && !hasPdfSource) {
            _uiState.update {
                it.copy(alert = AlertMessage("أدخل نص المصدر", "اكتب مقتطف الدرس أو فعّل إرفاق صفحات PDF للدرس."))
            }
            return
        }
        val availableModels = snapshot.aiModelCatalog.filter {
            it.provider == snapshot.selectedProvider && it.isEnabled &&
                it.supportsGenerateContent && it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        }
        if (availableModels.isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage(
                    "لا توجد نماذج متاحة",
                    "لم يضف مدير النظام نموذجاً مفعّلاً لمزود ${snapshot.selectedProvider.displayName}."
                ))
            }
            return
        }
        val availableApiKeys = snapshot.providers.filter {
            it.provider == snapshot.selectedProvider && it.enabled && it.apiKey.isNotBlank()
        }
        if (availableApiKeys.isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("لا يوجد مفتاح API", "أضف أو فعّل مفتاح API لهذا المزود في الإعدادات."))
            }
            return
        }
        val defaultModel = availableModels.firstOrNull { it.isDefault } ?: availableModels.first()
        _uiState.update {
            it.copy(
                isGenerationModelPickerOpen = true,
                selectedGenerationModelId = availableModels.firstOrNull {
                    it.modelId == snapshot.selectedGenerationModelId
                }?.modelId ?: defaultModel.modelId
            )
        }
    }

    fun selectGenerationModel(modelId: String) {
        _uiState.update { it.copy(selectedGenerationModelId = modelId) }
    }

    fun dismissGenerationModelPicker() {
        _uiState.update { it.copy(isGenerationModelPickerOpen = false) }
    }

    fun confirmGenerationSelection(modelId: String) {
        val snapshot = _uiState.value
        val model = snapshot.aiModelCatalog.firstOrNull {
            it.modelId == modelId && it.provider == snapshot.selectedProvider &&
                it.isEnabled && it.supportsGenerateContent &&
                it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
        }
        val availableApiKey = snapshot.providers
            .filter { it.provider == snapshot.selectedProvider && it.enabled && it.apiKey.isNotBlank() }
            .minWithOrNull(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt })
        if (model == null || availableApiKey == null) {
            _uiState.update {
                it.copy(alert = AlertMessage(
                    "اختيار غير صالح",
                    "النموذج أو مفاتيح API المفعّلة لم تعد متاحة. حدّث القائمة ثم أعد المحاولة."
                ))
            }
            return
        }
        _uiState.update {
            it.copy(
                isGenerationModelPickerOpen = false,
                selectedGenerationModelId = modelId
            )
        }
        startGeneration(snapshot, modelId)
    }

    private fun startGeneration(
        snapshot: MainUiState,
        selectedModelId: String
    ) {
        val selectedApiKey = snapshot.providers
            .filter { it.provider == snapshot.selectedProvider && it.enabled && it.apiKey.isNotBlank() }
            .minWithOrNull(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt })
        if (selectedApiKey == null) {
            _uiState.update { it.copy(alert = AlertMessage("لا يوجد مفتاح API", "أضف أو فعّل مفتاح API لهذا المزود في الإعدادات.")) }
            return
        }
        val lesson = snapshot.selectedLesson ?: run {
            _uiState.update { it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً من المكتبة أولاً.")) }
            return
        }
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, generationProgressPercent = 2, generationStage = "بدء تجهيز الطلب") }
            try {
                val out = repository.generateContent(
                    workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                    lesson = lesson,
                    task = snapshot.task,
                    source = snapshot.sourceText,
                    extra = snapshot.extraInstructions,
                    providers = snapshot.providers,
                    selectedProvider = snapshot.selectedProvider,
                    selectedModelId = selectedModelId,
                    selectedProfileId = selectedApiKey.id,
                    questionCount = snapshot.questionCount,
                    questionTypeIds = snapshot.selectedQuestionTypeIds,
                    questionLanguage = snapshot.questionLanguage,
                    attachPdfPages = snapshot.attachPdfPages,
                    questionStyleHint = snapshot.questionStyle.takeIf { snapshot.task == TaskType.QUESTIONS && it != "متنوعة" }.orEmpty(),
                    difficultyHint = if (snapshot.task == TaskType.QUESTIONS) snapshot.questionDifficulty.id else "",
                    template = promptTemplates.value.firstOrNull { it.id == snapshot.activePromptTemplateId }
                        ?: promptTemplates.value.firstOrNull { it.task == snapshot.task },
                    catalogModels = snapshot.aiModelCatalog,
                    onProgress = { percent, stage ->
                        _uiState.update { it.copy(generationProgressPercent = percent, generationStage = stage) }
                    }
                )
                val wsId = snapshot.workspace?.workspaceId.orEmpty()
                // Surface the provider response immediately. Draft persistence and later
                // task/database validation must not hide content that the provider returned.
                _uiState.update {
                    it.copy(
                        generatedResult = out.text,
                        generatedQuestionExpectedCount = if (snapshot.task == TaskType.QUESTIONS) snapshot.questionCount else null,
                        generatedQuestionExpectedTypeIds = if (snapshot.task == TaskType.QUESTIONS) snapshot.selectedQuestionTypeIds else emptyList(),
                        generatedQuestionExpectedDifficultyId = if (snapshot.task == TaskType.QUESTIONS) snapshot.questionDifficulty.id else "",
                        activeDraftProvider = out.providerLabel,
                        activeDraftModel = out.model,
                        activeDraftTemplateId = out.templateId,
                        activeDraftTemplateVersion = out.templateVersion,
                        activeDraftResponseSchemaHash = out.responseSchemaHash,
                        activeDraftPromptUsed = out.promptUsed,
                        activeDraftResponseSchemaJson = out.responseSchemaJson,
                        activeDraftRequestFingerprint = out.requestFingerprint,
                        activeDraftContentHash = out.contentHash,
                        activeDraftSourcePdfHash = out.sourcePdfHash,
                        generationProgressPercent = 94,
                        generationStage = "تم استلام المحتوى؛ جارٍ حفظ المسودة والتحقق"
                    )
                }
                val draftSaveResult = runCatching {
                    repository.persistDraft(
                        workspaceId = wsId,
                        lesson = lesson,
                        task = snapshot.task,
                        providerLabel = out.providerLabel,
                        model = out.model,
                        text = out.text,
                        title = lesson.title,
                        sourceText = snapshot.sourceText,
                        status = DraftStatus.DRAFT,
                        generation = out
                    )
                }.onFailure { if (it is CancellationException) throw it }
                val persistedDraft = draftSaveResult.getOrNull()
                if (persistedDraft == null) {
                    val saveError = draftSaveResult.exceptionOrNull()?.message ?: "خطأ غير محدد أثناء حفظ المسودة"
                    val diagnostics = (out.validationIssues + saveError).distinct()
                    _uiState.update {
                        it.copy(
                            alert = AlertMessage(
                                title = "تم استلام المحتوى لكن تعذر حفظ المسودة",
                                message = "الاستجابة الخام ما زالت معروضة ويمكن نسخها أو مراجعتها. لم يتم إدراجها في قاعدة البيانات.\n\n" +
                                    diagnostics.joinToString("\n") { "• $it" }
                            )
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(generationProgressPercent = 98, generationStage = "حفظ المسودة وبصمة المحتوى") }
                val cloudSyncError = if (isCloudAuthenticated) {
                    runCatching { firestoreSync.upsertDraft(currentUserId, persistedDraft) }
                        .onFailure { if (it is CancellationException) throw it }
                        .exceptionOrNull()?.message
                } else null
                analyticsTracker?.trackAiExplanationGenerated(
                    providerId = out.providerLabel,
                    taskType = snapshot.task.id,
                    success = true
                )
                analyticsTracker?.trackLessonCreateCompleted(
                    bookKey = lesson.bookKey,
                    unitKey = lesson.unitKey,
                    lessonKey = lesson.lessonKey,
                    success = true
                )
                _uiState.update { it.copy(providers = repository.loadProviders(), generationProgressPercent = 100, generationStage = "اكتمل التوليد والحفظ") }
                val questionSaveResult = if (false && snapshot.task == TaskType.QUESTIONS) {
                    runCatching {
                        repository.saveQuestionsFromDraftText(
                            workspaceId = wsId,
                            lesson = lesson,
                            rawText = out.text,
                            draftId = persistedDraft.id,
                            templateId = out.templateId,
                            promptUsed = out.promptUsed,
                            responseSchemaJson = out.responseSchemaJson,
                            sourceIndexSha256 = persistedDraft.sourceIndexSha256,
                            expectedQuestionCount = snapshot.questionCount,
                            expectedQuestionTypeIds = snapshot.selectedQuestionTypeIds,
                            expectedQuestionDifficultyId = snapshot.questionDifficulty.id
                        )
                    }
                } else null
                val syncedQuestionsCount = questionSaveResult?.getOrNull()?.size ?: 0
                val questionSaveError = questionSaveResult?.exceptionOrNull()?.message
                val lessonTextSaved = if (false && snapshot.task == TaskType.LESSON_TEXT) {
                    val extracted = repository.extractLessonTextFromOutput(out.text)
                    runCatching {
                        repository.saveLessonText(
                            workspaceId = wsId,
                            lesson = lesson,
                            content = extracted,
                            extractionMethod = "ai_generated",
                            sourcePdfHash = out.sourcePdfHash,
                            templateId = out.templateId,
                            promptUsed = out.promptUsed,
                            responseSchemaJson = out.responseSchemaJson
                        )
                    }.getOrNull()
                } else null
                val interactiveParsed = if (snapshot.task == TaskType.INTERACTIVE) {
                    InteractiveActivityParser.parseStrictJson(out.text, lesson.title)
                } else {
                    null
                }
                val publishedBundle = if (false && interactiveParsed != null) {
                    runCatching {
                        repository.buildAndPublishInteractiveBundle(
                            workspaceId = wsId,
                            lesson = lesson,
                            activity = interactiveParsed,
                            publishForStudents = true
                        )
                    }.getOrNull()
                } else null
                val resultSummary = when {
                    snapshot.task == TaskType.QUESTIONS && questionSaveError != null ->
                        "تم استلام الاستجابة من ${out.providerLabel} (${out.model}) وحفظ نصها كمسودة، لكن لم تُدرج الأسئلة في بنك الأسئلة: $questionSaveError"
                    snapshot.task == TaskType.QUESTIONS && syncedQuestionsCount > 0 ->
                        "تم التوليد عبر ${out.providerLabel} (${out.model})، وحُفظت المسودة وتم التحقق من العدد والأنواع قبل إضافة $syncedQuestionsCount سؤالاً إلى بنك الأسئلة واختبار الدرس."
                    snapshot.task == TaskType.QUESTIONS ->
                        "تم حفظ مسودة الأسئلة فقط. لن تُدرج في بنك الأسئلة أو اختبار الطالب قبل اعتمادها صراحةً."
                    snapshot.task == TaskType.LESSON_TEXT && lessonTextSaved != null ->
                        "تم استخراج وحفظ المحتوى النصي للدرس بنجاح في قاعدة البيانات وتحديث مصدر الاستوديو (v${lessonTextSaved.version})."
                    snapshot.task == TaskType.INTERACTIVE && publishedBundle != null ->
                        "تم التوليد عبر ${out.providerLabel} (${out.model}) وبناء حزمة HTML + JS + JSON (${interactiveParsed?.steps?.size ?: 0} محطة) ونشرها للطالب مع مزامنة المفاهيم."
                    snapshot.task == TaskType.INTERACTIVE && interactiveParsed != null ->
                        "تم حفظ النشاط التفاعلي (${interactiveParsed.steps.size} محطة) كمسودة قابلة للمعاينة في الاستوديو؛ لن يُنشر للطالب قبل الاعتماد."
                    else ->
                        "تم استلام الاستجابة عبر ${out.providerLabel} (${out.model}) وحفظها كمسودة."
                }
                val reviewIssues = (out.validationIssues + listOfNotNull(questionSaveError, cloudSyncError))
                    .filter { it.isNotBlank() }
                    .distinct()
                val resultMessage = if (reviewIssues.isEmpty()) {
                    resultSummary
                } else {
                    resultSummary + "\n\nالمحتوى المستلم محفوظ ويمكن مراجعته، لكن توجد مشكلات تمنع اعتماده أو إدراجه في قاعدة البيانات:\n" +
                        reviewIssues.joinToString("\n") { "• $it" }
                }
                _uiState.update {
                    it.copy(
                        generatedResult = out.text,
                        generatedQuestionExpectedCount = if (snapshot.task == TaskType.QUESTIONS) snapshot.questionCount else null,
                        generatedQuestionExpectedTypeIds = if (snapshot.task == TaskType.QUESTIONS) snapshot.selectedQuestionTypeIds else emptyList(),
                        generatedQuestionExpectedDifficultyId = if (snapshot.task == TaskType.QUESTIONS) snapshot.questionDifficulty.id else "",
                        sourceText = if (snapshot.task == TaskType.LESSON_TEXT && lessonTextSaved != null) lessonTextSaved.content else it.sourceText,
                        activeDraftProvider = out.providerLabel,
                        activeDraftModel = out.model,
                        activeDraftTemplateId = out.templateId,
                        activeDraftTemplateVersion = out.templateVersion,
                        activeDraftResponseSchemaHash = out.responseSchemaHash,
                        activeDraftRequestFingerprint = out.requestFingerprint,
                        activeDraftContentHash = out.contentHash,
                        activeDraftSourcePdfHash = out.sourcePdfHash,
                        alert = AlertMessage(
                            title = if (reviewIssues.isEmpty()) "تم الحفظ والربط" else "تم استلام المحتوى - يحتاج مراجعة",
                            message = resultMessage
                        )
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(toastMessage = "تم إلغاء عملية التوليد")
                }
            } catch (e: Exception) {
                analyticsTracker?.trackAiExplanationGenerated(
                    providerId = snapshot.selectedProvider.id,
                    taskType = snapshot.task.id,
                    success = false
                )
                analyticsTracker?.trackLessonCreateCompleted(
                    bookKey = lesson.bookKey,
                    unitKey = lesson.unitKey,
                    lessonKey = lesson.lessonKey,
                    success = false
                )
                val failureTitle = when ((e as? AiExecutionException)?.kind) {
                    AiExecutionFailureKind.QUOTA_EXHAUSTED -> "حصة API اليومية مستنفدة"
                    AiExecutionFailureKind.RATE_LIMITED -> "النموذج مزدحم أو تجاوز حد المعدل"
                    AiExecutionFailureKind.INVALID_CREDENTIALS -> "مفتاح API غير صالح"
                    AiExecutionFailureKind.INVALID_REQUEST -> "رفض مزود AI الطلب"
                    AiExecutionFailureKind.EMPTY_RESPONSE -> "لم يُرجع النموذج محتوى"
                    AiExecutionFailureKind.NETWORK_ERROR -> "تعذر الاتصال بمزود AI"
                    AiExecutionFailureKind.MODEL_UNAVAILABLE -> "النموذج غير متاح"
                    AiExecutionFailureKind.PROVIDER_UNAVAILABLE -> "تعذر التوليد عبر مزود AI"
                    null -> "تعذر التوليد"
                }
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            title = failureTitle,
                            message = e.message ?: "حدث خطأ غير متوقع أثناء التوليد"
                        )
                    )
                }
            } finally {
                generationJob = null
                val latestProviders = runCatching { repository.loadProviders() }.getOrDefault(_uiState.value.providers)
                _uiState.update { it.copy(providers = latestProviders, isBusy = false, generationProgressPercent = null, generationStage = null) }
            }
        }
    }

    fun cancelActiveGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isBusy = false, generationProgressPercent = null, generationStage = null, toastMessage = "تم إيقاف التوليد") }
    }

    fun importInteractivePackage(uri: Uri) {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson
        if (lesson == null) {
            _uiState.update {
                it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً أولاً قبل رفع حزمة HTML/ZIP."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val record = repository.importInteractivePackage(
                    uri = uri,
                    workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                    lesson = lesson,
                    sourcePages = snapshot.packageSourcePages
                )
                _uiState.update {
                    it.copy(
                        previewingPackage = record,
                        studentPreviewMode = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            title = "فشل فحص الحزمة",
                            message = e.message ?: "تعذر استيراد ملف HTML/ZIP"
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun openPackagePreview(pkg: InteractivePackageRecord, studentMode: Boolean = false) {
        _uiState.update {
            it.copy(
                previewingPackage = pkg,
                studentPreviewMode = studentMode
            )
        }
    }

    fun closePackagePreview() {
        _uiState.update {
            it.copy(
                previewingPackage = null,
                studentPreviewMode = false
            )
        }
    }

    fun toggleStudentPreviewMode() {
        _uiState.update { it.copy(studentPreviewMode = !it.studentPreviewMode) }
    }

    fun approveAndPublishPackage(pkg: InteractivePackageRecord) {
        if (pkg.report.hasBlockers) {
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "مرفوض أمنياً",
                        "لا يمكن اعتماد أو نشر الحزمة لوجود مخالفات أمنية حرجة في التقرير."
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            repository.publishInteractivePackage(pkg)
            _uiState.update { state ->
                val updatedPreview = if (state.previewingPackage?.id == pkg.id) {
                    pkg.copy(status = "APPROVED", publishedForStudents = true)
                } else state.previewingPackage
                state.copy(
                    previewingPackage = updatedPreview,
                    alert = AlertMessage(
                        "تم الاعتماد والنشر",
                        "الإصدار v${pkg.version} معتمد ومنشور للطلاب، مع الاحتفاظ بجميع الإصدارات السابقة."
                    )
                )
            }
        }
    }

    fun deletePackage(pkg: InteractivePackageRecord) {
        viewModelScope.launch {
            repository.deleteInteractivePackage(pkg)
            _uiState.update { state ->
                if (state.previewingPackage?.id == pkg.id) {
                    state.copy(previewingPackage = null)
                } else state
            }
        }
    }

    fun addOrUpdateProvider() {
        val state = _uiState.value
        val form = state.providerForm
        if (form.apiKey.trim().isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("بيانات ناقصة", "أدخل مفتاح API أولاً."))
            }
            return
        }
        viewModelScope.launch {
            val currentList = _uiState.value.providers
            val existingId = form.editingProviderId
            val existing = currentList.firstOrNull { it.id == existingId }
            if (existing != null && existing.provider != form.provider) {
                _uiState.update {
                    it.copy(alert = AlertMessage(
                        "لا يمكن تغيير المزوّد",
                        "مفتاح API تابع للمزوّد الذي أُنشئ له. أضف مفتاحاً جديداً للمزوّد الآخر."
                    ))
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            val next = if (existing != null) {
                currentList.map { p ->
                    if (p.id == existing.id) {
                        val keyChanged = form.apiKey.trim() != p.apiKey
                        p.copy(
                            label = p.label,
                            model = "",
                            fallbackModels = emptyList(),
                            strictModel = false,
                            baseUrl = form.baseUrl.trim(),
                            apiKey = form.apiKey.trim(),
                            dailyRequestLimit = form.dailyRequestLimit.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            quotaTimeZone = form.quotaTimeZone.ifBlank { "Asia/Aden" },
                            runtimeStatus = if (keyChanged) "ACTIVE" else p.runtimeStatus,
                            dailyRequestsUsed = if (keyChanged) 0 else p.dailyRequestsUsed,
                            quotaDate = if (keyChanged) "" else p.quotaDate,
                            retryAfterEpochMs = if (keyChanged) 0L else p.retryAfterEpochMs,
                            lastUsedAtEpochMs = if (keyChanged) 0L else p.lastUsedAtEpochMs
                        )
                    } else p
                }
            } else {
                val keyName = ProviderApiKeyNames.nextName(form.provider, currentList)
                val providerPriority = currentList
                    .filter { it.provider == form.provider }
                    .maxOfOrNull { it.priority + 1 } ?: 0
                currentList + ProviderConfig(
                    id = "api-key-${java.util.UUID.randomUUID()}",
                    provider = form.provider,
                    label = keyName,
                    model = "",
                    fallbackModels = emptyList(),
                    strictModel = false,
                    baseUrl = form.baseUrl.trim(),
                    apiKey = form.apiKey.trim(),
                    enabled = true,
                    createdAt = now,
                    priority = providerPriority,
                    dailyRequestLimit = form.dailyRequestLimit.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    quotaTimeZone = form.quotaTimeZone.ifBlank { "Asia/Aden" }
                )
            }
            repository.saveProviders(next)
            val savedKeyName = next.firstOrNull { it.id == (existing?.id ?: next.last().id) }?.label.orEmpty()
            _uiState.update {
                it.copy(
                    providers = next,
                    providerForm = ProviderFormState(),
                    alert = AlertMessage(
                        if (existing != null) "تم التحديث" else "تمت إضافة المفتاح",
                        if (existing != null) "تم حفظ تعديلات مفتاح API." else "تم حفظ $savedKeyName."
                    )
                )
            }
        }
    }

    fun toggleProvider(id: String) {
        viewModelScope.launch {
            val current = _uiState.value.providers.firstOrNull { it.id == id } ?: return@launch
            val updated = repository.setProviderEnabled(id, !current.enabled)
            _uiState.update { it.copy(providers = updated) }
        }
    }

    fun reactivateProvider(id: String) {
        viewModelScope.launch {
            val updated = repository.reactivateProvider(id)
            _uiState.update { it.copy(providers = updated, toastMessage = "تمت إعادة تنشيط المفتاح يدوياً") }
        }
    }

    fun moveProviderPriority(id: String, delta: Int) {
        val all = _uiState.value.providers
        val target = all.firstOrNull { it.id == id } ?: return
        val scoped = all.filter { it.provider == target.provider }.sortedBy { it.priority }.toMutableList()
        val idx = scoped.indexOfFirst { it.id == id }
        if (idx < 0) return
        val targetIdx = (idx + delta).coerceIn(0, scoped.lastIndex)
        if (idx == targetIdx) return
        val item = scoped.removeAt(idx)
        scoped.add(targetIdx, item)
        val priorities = scoped.mapIndexed { index, profile -> profile.id to index }.toMap()
        val reindexed = all.map { profile ->
            priorities[profile.id]?.let { profile.copy(priority = it) } ?: profile
        }
        viewModelScope.launch {
            repository.saveProviders(reindexed)
            _uiState.update { it.copy(providers = reindexed) }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            val next = _uiState.value.providers
                .filterNot { it.id == id }
                .sortedBy { it.priority }
                .mapIndexed { i, p -> p.copy(priority = i) }
            repository.saveProviders(next)
            _uiState.update { it.copy(providers = next) }
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(alert = null) }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val LEARNER_PREFS_NAME = "edumini_learner_profiles"
        private const val KEY_LEARNER_PROFILES_JSON = "profiles_json"
        private const val KEY_ACTIVE_LEARNER_ID = "active_learner_id"
        internal const val BLOCKED_UNASSIGNED_WORKSPACE_ID = "__blocked_unassigned_grade__"

        internal fun resolveEffectiveWorkspaceId(
            userRole: UserRole,
            activeLearner: LearnerProfile,
            workspaces: List<WorkspaceSummary>,
            adminSelectedWorkspaceId: String?
        ): String? {
            if (workspaces.isEmpty()) return null
            if (userRole == UserRole.LEARNER) {
                val enrolled = activeLearner.enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                if (enrolled != null) {
                    return workspaces.firstOrNull { it.gradeKey.equals(enrolled, ignoreCase = true) }?.workspaceId
                        ?: BLOCKED_UNASSIGNED_WORKSPACE_ID
                }
                return if (workspaces.size == 1) {
                    workspaces.first().workspaceId
                } else {
                    BLOCKED_UNASSIGNED_WORKSPACE_ID
                }
            }
            return adminSelectedWorkspaceId?.takeIf { selected ->
                workspaces.any { it.workspaceId == selected }
            } ?: workspaces.firstOrNull()?.workspaceId
        }
    }
}
