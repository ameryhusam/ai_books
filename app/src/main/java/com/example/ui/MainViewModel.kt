package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiGenerationService
import com.example.data.local.EduMiniDatabase
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.EduMiniRepository
import com.example.data.repository.InteractiveActivityParser
import com.example.data.repository.QuestionBankEngine
import com.example.data.security.ProviderStorage
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.AppPage
import com.example.model.AssessmentAttemptRecord
import com.example.model.AssessmentKind
import com.example.model.AssessmentRecord
import com.example.model.BookRef
import com.example.model.DashboardSummary
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.GenerationRunRecord
import com.example.model.ImportedWorkspace
import com.example.model.InteractiveActivityModel
import com.example.model.InteractivePackageRecord
import com.example.model.LessonRef
import com.example.model.LessonSplitDraft
import com.example.model.PdfBookInfo
import com.example.model.PromptBlueprintSpec
import com.example.model.PromptTemplateRecord
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.StagedWorkspacePreview
import com.example.model.TaskType
import com.example.model.TeacherNoteRecord
import com.example.model.WorkspaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertMessage(
    val title: String,
    val message: String
)

data class PdfPreviewPayload(
    val path: String,
    val title: String,
    val subtitle: String? = null
)

data class ProviderFormState(
    val editingProviderId: String? = null,
    val provider: ProviderType = ProviderType.GEMINI,
    val label: String = "Gemini",
    val model: String = "gemini-2.5-flash",
    val fallbackModels: String = "",
    val strictModel: Boolean = false,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = ""
)

data class PdfSplitterUiState(
    val pdfInfo: PdfBookInfo,
    val gradeKey: String = "G04",
    val subjectKey: String = "BOOK",
    val bookTitle: String = pdfInfo.fileName.removeSuffix(".pdf"),
    val pagesPerLesson: Int = 5,
    val splits: List<LessonSplitDraft> = emptyList()
)

data class MainUiState(
    val page: AppPage = AppPage.DASHBOARD,
    val selectedWorkspaceId: String? = null,
    val workspace: ImportedWorkspace? = null,
    val workspaces: List<WorkspaceSummary> = emptyList(),
    val activeWorkspaceDiskBytes: Long = 0L,
    val selectedBookKey: String = "",
    val selectedLesson: LessonRef? = null,
    val task: TaskType = TaskType.QUESTIONS,
    val questionCount: Int = 5,
    val questionStyle: String = "متنوعة",
    val questionDifficulty: QuestionDifficulty = QuestionDifficulty.MEDIUM,
    val attachPdfPages: Boolean = true,
    val sourceText: String = "",
    val extraInstructions: String = "",
    val generatedResult: String = "",
    val activeDraftProvider: String = "تحرير يدوي",
    val activeDraftModel: String = "local",
    val packageSourcePages: String = "",
    val previewingPackage: InteractivePackageRecord? = null,
    val studentPreviewMode: Boolean = false,
    val activeInteractiveActivity: InteractiveActivityModel? = null,
    val studentStudyLesson: LessonRef? = null,
    val previewingPdf: PdfPreviewPayload? = null,
    val previewingBookCover: BookRef? = null,
    val promptSourcesOpen: Boolean = false,
    val stagedPreview: StagedWorkspacePreview? = null,
    val globalSearchOpen: Boolean = false,
    val pendingExportAssessment: AssessmentRecord? = null,
    val pdfSplitter: PdfSplitterUiState? = null,
    val providers: List<ProviderConfig> = emptyList(),
    val providerForm: ProviderFormState = ProviderFormState(),
    val recentRuns: List<GenerationRunRecord> = emptyList(),
    val isBusy: Boolean = false,
    val toastMessage: String? = null,
    val alert: AlertMessage? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EduMiniRepository = EduMiniRepository(
        dao = EduMiniDatabase.getInstance(application).eduMiniDao(),
        providerStorage = ProviderStorage(application),
        workspaceImporter = WorkspaceImporter(application),
        aiGenerationService = AiGenerationService(application),
        pdfBookSplitter = PdfBookSplitter(application),
        interactivePackageImporter = InteractivePackageImporter(application)
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
        .map { it.workspace }
        .distinctUntilChanged()
        .flatMapLatest { ws -> repository.observeDashboardSummary(ws) }
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
        .map { it.workspace?.workspaceId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { wsId -> repository.observeAssessmentAttempts(wsId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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

    val promptTemplates: StateFlow<List<PromptTemplateRecord>> = repository
        .observePromptTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EduMiniRepository.DEFAULT_PROMPT_TEMPLATES
        )

    init {
        viewModelScope.launch {
            runCatching { repository.loadProviders() }
                .onSuccess { loaded ->
                    _uiState.update { it.copy(providers = loaded) }
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
                .map { it.selectedWorkspaceId }
                .distinctUntilChanged()
                .flatMapLatest { wsId -> repository.observeWorkspace(wsId) }
                .collect { ws ->
                    val diskBytes = repository.computeWorkspaceDiskBytes(ws?.rootPath.orEmpty())
                    _uiState.update { state ->
                        val currentSelectedBook = state.selectedBookKey
                        val validSelectedBook = when {
                            ws == null -> ""
                            ws.books.any { it.bookKey == currentSelectedBook } -> currentSelectedBook
                            else -> ws.books.firstOrNull()?.bookKey.orEmpty()
                        }
                        state.copy(
                            workspace = ws,
                            activeWorkspaceDiskBytes = diskBytes,
                            selectedBookKey = validSelectedBook
                        )
                    }
                }
        }
    }

    fun navigateTo(page: AppPage) {
        _uiState.update { it.copy(page = page) }
    }

    fun selectWorkspace(workspaceId: String) {
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

    fun selectLessonAndOpenStudio(lesson: LessonRef) {
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                page = AppPage.STUDIO
            )
        }
    }

    fun selectLessonInStudio(lesson: LessonRef) {
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty()
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

    fun applyIndexer2Blueprint(spec: PromptBlueprintSpec) {
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
                activeDraftProvider = "indexer2 Schema",
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
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                selectedBookKey = lesson.bookKey,
                packageSourcePages = lesson.pageRange.orEmpty(),
                studentStudyLesson = lesson
            )
        }
    }

    fun closeStudentStudy() {
        _uiState.update { it.copy(studentStudyLesson = null) }
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
            _uiState.update { it.copy(activeInteractiveActivity = parsed) }
        }
    }

    fun dismissInteractiveActivity() {
        _uiState.update { it.copy(activeInteractiveActivity = null) }
    }

    fun recordInteractiveActivityCompleted(earnedXp: Int) {
        viewModelScope.launch {
            repository.recordStudyActivity()
            _uiState.update {
                it.copy(
                    activeInteractiveActivity = null,
                    alert = AlertMessage(
                        "إنجاز رائع!",
                        "أتممت النشاط التفاعلي وحصلت على +$earnedXp XP."
                    )
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
        _uiState.update {
            it.copy(
                globalSearchOpen = false,
                selectedLesson = lesson ?: it.selectedLesson,
                selectedBookKey = lesson?.bookKey ?: it.selectedBookKey,
                task = draft.task,
                generatedResult = draft.content,
                activeDraftProvider = draft.provider,
                activeDraftModel = draft.model,
                page = AppPage.STUDIO
            )
        }
    }

    fun handleSearchSelectQuestion(@Suppress("UNUSED_PARAMETER") question: QuestionItem) {
        _uiState.update {
            it.copy(
                globalSearchOpen = false,
                page = AppPage.QUIZ_BANK
            )
        }
    }

    fun toggleLessonCompletion(lesson: LessonRef, currentlyCompleted: Boolean) {
        val wsId = _uiState.value.workspace?.workspaceId.orEmpty()
        viewModelScope.launch {
            repository.toggleLessonCompleted(wsId, lesson, currentlyCompleted)
        }
    }

    fun selectTask(task: TaskType) {
        _uiState.update { it.copy(task = task) }
    }

    fun updateQuestionCount(count: Int) {
        _uiState.update { it.copy(questionCount = count.coerceIn(1, 30)) }
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
                activeDraftModel = draft.model
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
            repository.persistDraft(
                workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                lesson = lesson,
                task = snapshot.task,
                providerLabel = snapshot.activeDraftProvider,
                model = snapshot.activeDraftModel,
                text = content,
                title = lesson.title,
                sourceText = snapshot.sourceText,
                status = DraftStatus.DRAFT
            )
            _uiState.update {
                it.copy(toastMessage = "تم حفظ نسخة جديدة من المسودة")
            }
        }
    }

    fun approveDraft(draftId: String) {
        viewModelScope.launch {
            repository.approveDraft(draftId)
            _uiState.update {
                it.copy(toastMessage = "تم اعتماد النسخة للدرس ✓")
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            repository.deleteDraft(draftId)
        }
    }

    fun updateProviderType(type: ProviderType) {
        _uiState.update { state ->
            val defaultLabel = if (type == ProviderType.GEMINI) "Gemini" else "Compatible"
            val updatedLabel = if (state.providerForm.editingProviderId == null) defaultLabel else state.providerForm.label
            state.copy(
                providerForm = state.providerForm.copy(
                    provider = type,
                    label = updatedLabel
                )
            )
        }
    }

    fun updateFormLabel(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(label = value)) }
    }

    fun updateFormModel(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(model = value)) }
    }

    fun updateFormFallbackModels(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(fallbackModels = value)) }
    }

    fun updateFormBaseUrl(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(baseUrl = value)) }
    }

    fun updateFormApiKey(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(apiKey = value)) }
    }

    fun toggleFormStrictModel() {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(strictModel = !it.providerForm.strictModel)) }
    }

    fun startEditProvider(profile: ProviderConfig) {
        _uiState.update {
            it.copy(
                providerForm = ProviderFormState(
                    editingProviderId = profile.id,
                    provider = profile.provider,
                    label = profile.label,
                    model = profile.model,
                    fallbackModels = profile.fallbackModels.joinToString(", "),
                    strictModel = profile.strictModel,
                    baseUrl = profile.baseUrl.ifBlank { "https://api.openai.com/v1" },
                    apiKey = profile.apiKey
                )
            )
        }
    }

    fun cancelEditProvider() {
        _uiState.update { it.copy(providerForm = ProviderFormState()) }
    }

    fun testFormProvider() {
        val form = _uiState.value.providerForm
        if (form.model.trim().isEmpty() || form.apiKey.trim().isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("بيانات ناقصة", "أدخل النموذج ومفتاح API لاختبار الاتصال."))
            }
            return
        }
        val probe = ProviderConfig(
            id = form.editingProviderId ?: "probe",
            provider = form.provider,
            label = form.label.trim().ifBlank { form.provider.displayName },
            model = form.model.trim(),
            strictModel = form.strictModel,
            baseUrl = form.baseUrl.trim(),
            apiKey = form.apiKey.trim(),
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        testSavedProvider(probe)
    }

    fun testSavedProvider(profile: ProviderConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val report = repository.testProviderConnection(profile)
                _uiState.update {
                    it.copy(alert = AlertMessage("نتيجة اختبار الاتصال", report))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("فشل الاتصال", e.message ?: "تعذر الاتصال بالمزود"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
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

    fun savePromptTemplate(title: String, task: TaskType, instructions: String) {
        if (title.isBlank() || instructions.isBlank()) return
        viewModelScope.launch {
            repository.savePromptTemplate(title, task, instructions)
            _uiState.update {
                it.copy(alert = AlertMessage("تم الحفظ", "تمت إضافة قالب التوجيه «${title.trim()}»."))
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
        viewModelScope.launch {
            val saved = repository.saveQuestionsFromDraftText(
                workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                lesson = lesson,
                rawText = text
            )
            if (saved.isEmpty()) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تعذر استخراج الأسئلة",
                            "تأكد من ترقيم الأسئلة (1. ... أو JSON) مع كتابة «الإجابة: ...» ثم أعد المحاولة."
                        )
                    )
                }
            } else {
                val dups = com.example.data.repository.QuestionBankEngine.markDuplicates(saved).count { it.isDuplicate }
                val dupNote = if (dups > 0) " (بينها $dups تكرار محتمل)" else ""
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تم الحفظ في بنك الأسئلة",
                            "تم استخراج وحفظ ${saved.size} سؤال لدرس «${lesson.title}»$dupNote."
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
        difficulty: QuestionDifficulty
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
                difficulty = difficulty
            )
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
        viewModelScope.launch {
            repository.recordStudyActivity()
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
        viewModelScope.launch {
            val record = repository.recordAssessmentAttempt(
                assessment = assessment,
                correctCount = correctCount,
                totalQuestions = totalQuestions,
                scorePercent = scorePercent,
                durationSeconds = durationSeconds,
                earnedXp = earnedXp
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
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
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

    fun exportIndexer2Zip(uri: Uri) {
        val workspace = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف الحفظ المختار")
                val receipt = out.use { stream ->
                    repository.exportIndexer2Zip(stream, workspace)
                }
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            "تم تصدير الحزمة",
                            "تم إنشاء ملف ZIP متوافق مع indexer2 ويحتوي على ${receipt.entriesCount} ملف (${receipt.draftsCount} مسودة، ${receipt.questionsCount} سؤال) مع التحقق SHA-256."
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
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية")
                val itemsCount = out.use { stream ->
                    repository.exportFullBackupJson(stream, workspace)
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
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("تعذر قراءة ملف النسخة الاحتياطية")
                val raw = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val summaryMsg = repository.importFullBackupJson(
                    rawJson = raw,
                    targetWorkspaceId = _uiState.value.workspace?.workspaceId.orEmpty()
                )
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
            _uiState.update { it.copy(isBusy = true) }
            try {
                val staged = repository.inspectWorkspaceZip(uri)
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
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun confirmStagedImport(selectedGrades: List<String>) {
        val preview = _uiState.value.stagedPreview ?: return
        if (selectedGrades.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val committed = repository.commitStagedWorkspaces(preview, selectedGrades)
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
                _uiState.update { it.copy(isBusy = false) }
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

    fun openBookPdfForSplitting(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val info = repository.ingestBookPdf(uri)
                val defaultSplits = repository.generateAutoSplits(info.totalPages, pagesPerLesson = 5)
                _uiState.update {
                    it.copy(
                        pdfSplitter = PdfSplitterUiState(
                            pdfInfo = info,
                            splits = defaultSplits
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("خطأ في PDF", e.message ?: "تعذر قراءة ملف PDF"))
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun updateSplitterGrade(value: String) {
        _uiState.update { it.copy(pdfSplitter = it.pdfSplitter?.copy(gradeKey = value)) }
    }

    fun updateSplitterSubject(value: String) {
        _uiState.update { it.copy(pdfSplitter = it.pdfSplitter?.copy(subjectKey = value)) }
    }

    fun updateSplitterBookTitle(value: String) {
        _uiState.update { it.copy(pdfSplitter = it.pdfSplitter?.copy(bookTitle = value)) }
    }

    fun updateSplitterPagesPerLesson(count: Int) {
        _uiState.update { state ->
            val current = state.pdfSplitter ?: return@update state
            val cleanCount = count.coerceIn(1, current.pdfInfo.totalPages.coerceAtLeast(1))
            val regenerated = repository.generateAutoSplits(current.pdfInfo.totalPages, cleanCount)
            state.copy(
                pdfSplitter = current.copy(
                    pagesPerLesson = cleanCount,
                    splits = regenerated
                )
            )
        }
    }

    fun updateSplitLesson(
        splitId: String,
        title: String,
        unitTitle: String,
        startPage: Int,
        endPage: Int
    ) {
        _uiState.update { state ->
            val current = state.pdfSplitter ?: return@update state
            val maxPage = current.pdfInfo.totalPages
            val nextSplits = current.splits.map { split ->
                if (split.id == splitId) {
                    val safeStart = startPage.coerceIn(1, maxPage)
                    val safeEnd = endPage.coerceIn(safeStart, maxPage)
                    split.copy(
                        title = title,
                        unitTitle = unitTitle,
                        startPage = safeStart,
                        endPage = safeEnd
                    )
                } else split
            }
            state.copy(pdfSplitter = current.copy(splits = nextSplits))
        }
    }

    fun addSplitLesson() {
        _uiState.update { state ->
            val current = state.pdfSplitter ?: return@update state
            val maxPage = current.pdfInfo.totalPages
            val nextIndex = current.splits.size + 1
            val lastEnd = current.splits.lastOrNull()?.endPage ?: 0
            val start = (lastEnd + 1).coerceAtMost(maxPage)
            val end = (start + current.pagesPerLesson - 1).coerceAtMost(maxPage)
            val num = nextIndex.toString().padStart(2, '0')
            val newItem = LessonSplitDraft(
                id = "split-${System.currentTimeMillis()}-$nextIndex",
                unitKey = "unit_01",
                unitTitle = current.splits.lastOrNull()?.unitTitle ?: "الوحدة الأولى",
                lessonKey = "lesson_$num",
                title = "الدرس $nextIndex",
                startPage = start,
                endPage = end
            )
            state.copy(pdfSplitter = current.copy(splits = current.splits + newItem))
        }
    }

    fun removeSplitLesson(splitId: String) {
        _uiState.update { state ->
            val current = state.pdfSplitter ?: return@update state
            if (current.splits.size <= 1) return@update state
            state.copy(
                pdfSplitter = current.copy(splits = current.splits.filterNot { it.id == splitId })
            )
        }
    }

    fun dismissPdfSplitter() {
        _uiState.update { it.copy(pdfSplitter = null) }
    }

    fun createWorkspaceFromPdfSplits(openFirstLessonInStudio: Boolean) {
        val splitter = _uiState.value.pdfSplitter ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val created = repository.createWorkspaceFromPdfSplits(
                    pdfInfo = splitter.pdfInfo,
                    gradeKey = splitter.gradeKey,
                    subjectKey = splitter.subjectKey,
                    bookTitle = splitter.bookTitle,
                    splits = splitter.splits
                )
                val firstLesson = created.lessons.firstOrNull()
                _uiState.update {
                    it.copy(
                        pdfSplitter = null,
                        selectedWorkspaceId = created.workspaceId,
                        workspace = created,
                        selectedBookKey = created.books.firstOrNull()?.bookKey.orEmpty(),
                        selectedLesson = if (openFirstLessonInStudio) firstLesson else null,
                        packageSourcePages = if (openFirstLessonInStudio) firstLesson?.pageRange.orEmpty() else "",
                        page = if (openFirstLessonInStudio && firstLesson != null) AppPage.STUDIO else AppPage.LIBRARY,
                        alert = AlertMessage(
                            title = "تم إنشاء مساحة العمل",
                            message = "تم تجزئة الكتاب إلى ${created.lessons.size} درس وحفظ ملفات PDF الخاصة بكل درس بنجاح."
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(alert = AlertMessage("تعذر تجزئة الكتاب", e.message ?: "خطأ غير متوقع"))
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
            _uiState.update {
                it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً من المكتبة أولاً."))
            }
            return
        }
        val hasPdfSource = snapshot.attachPdfPages && !lesson.pdfPath.isNullOrBlank()
        if (snapshot.sourceText.trim().isEmpty() && !hasPdfSource) {
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "أدخل نص المصدر",
                        "اكتب مقتطف الدرس أو فعّل إرفاق صفحات PDF للدرس."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val out = repository.generateContent(
                    workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                    lesson = lesson,
                    task = snapshot.task,
                    source = snapshot.sourceText,
                    extra = snapshot.extraInstructions,
                    providers = snapshot.providers,
                    questionCount = snapshot.questionCount,
                    attachPdfPages = snapshot.attachPdfPages,
                    questionStyleHint = snapshot.questionStyle.takeIf { snapshot.task == TaskType.QUESTIONS && it != "متنوعة" }.orEmpty(),
                    difficultyHint = if (snapshot.task == TaskType.QUESTIONS) snapshot.questionDifficulty.labelAr else ""
                )
                repository.persistDraft(
                    workspaceId = snapshot.workspace?.workspaceId.orEmpty(),
                    lesson = lesson,
                    task = snapshot.task,
                    providerLabel = out.providerLabel,
                    model = out.model,
                    text = out.text,
                    title = lesson.title,
                    sourceText = snapshot.sourceText,
                    status = DraftStatus.DRAFT
                )
                _uiState.update {
                    it.copy(
                        generatedResult = out.text,
                        activeDraftProvider = out.providerLabel,
                        activeDraftModel = out.model,
                        alert = AlertMessage(
                            title = "تم حفظ المسودة",
                            message = "تم التوليد عبر ${out.providerLabel} (${out.model}) وحفظ النسخة في قاعدة البيانات."
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        alert = AlertMessage(
                            title = "تعذر التوليد",
                            message = e.message ?: "حدث خطأ غير متوقع أثناء التوليد"
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
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
        val form = _uiState.value.providerForm
        if (form.label.trim().isEmpty() || form.model.trim().isEmpty() || form.apiKey.trim().isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("بيانات ناقصة", "أدخل الاسم والنموذج والمفتاح"))
            }
            return
        }
        val fallbacks = form.fallbackModels
            .split(",", "،", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(form.model.trim(), ignoreCase = true) }
            .distinct()
        viewModelScope.launch {
            val existingId = form.editingProviderId
            val currentList = _uiState.value.providers
            val next = if (existingId != null && currentList.any { it.id == existingId }) {
                currentList.map { p ->
                    if (p.id == existingId) {
                        p.copy(
                            provider = form.provider,
                            label = form.label.trim(),
                            model = form.model.trim(),
                            fallbackModels = fallbacks,
                            strictModel = form.strictModel,
                            baseUrl = form.baseUrl.trim(),
                            apiKey = form.apiKey.trim()
                        )
                    } else p
                }
            } else {
                currentList + ProviderConfig(
                    id = System.currentTimeMillis().toString(),
                    provider = form.provider,
                    label = form.label.trim(),
                    model = form.model.trim(),
                    fallbackModels = fallbacks,
                    strictModel = form.strictModel,
                    baseUrl = form.baseUrl.trim(),
                    apiKey = form.apiKey.trim(),
                    enabled = true,
                    createdAt = System.currentTimeMillis(),
                    priority = currentList.size
                )
            }
            repository.saveProviders(next)
            _uiState.update {
                it.copy(
                    providers = next,
                    providerForm = ProviderFormState(),
                    alert = AlertMessage(
                        if (existingId != null) "تم التحديث" else "حفظ",
                        if (existingId != null) "تم حفظ تعديلات المزود محلياً." else "تم حفظ مفتاح المزود محلياً."
                    )
                )
            }
        }
    }

    fun toggleProvider(id: String) {
        viewModelScope.launch {
            val next = _uiState.value.providers.map { p ->
                if (p.id == id) p.copy(enabled = !p.enabled) else p
            }
            repository.saveProviders(next)
            _uiState.update { it.copy(providers = next) }
        }
    }

    fun moveProviderPriority(id: String, delta: Int) {
        val current = _uiState.value.providers.sortedBy { it.priority }.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return
        val targetIdx = (idx + delta).coerceIn(0, current.lastIndex)
        if (idx == targetIdx) return
        val item = current.removeAt(idx)
        current.add(targetIdx, item)
        val reindexed = current.mapIndexed { i, p -> p.copy(priority = i) }
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
}
