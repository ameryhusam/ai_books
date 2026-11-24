package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiGenerationService
import com.example.data.local.EduMiniDatabase
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.EduMiniRepository
import com.example.data.security.ProviderStorage
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.AppPage
import com.example.model.DashboardSummary
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.GenerationRunRecord
import com.example.model.ImportedWorkspace
import com.example.model.InteractivePackageRecord
import com.example.model.LessonRef
import com.example.model.LessonSplitDraft
import com.example.model.PdfBookInfo
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.TaskType
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

data class ProviderFormState(
    val provider: ProviderType = ProviderType.GEMINI,
    val label: String = "Gemini",
    val model: String = "gemini-2.5-flash",
    val fallbackModels: String = "",
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
    val pdfSplitter: PdfSplitterUiState? = null,
    val providers: List<ProviderConfig> = emptyList(),
    val providerForm: ProviderFormState = ProviderFormState(),
    val recentRuns: List<GenerationRunRecord> = emptyList(),
    val isBusy: Boolean = false,
    val alert: AlertMessage? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EduMiniRepository = EduMiniRepository(
        dao = EduMiniDatabase.getInstance(application).eduMiniDao(),
        providerStorage = ProviderStorage(application),
        workspaceImporter = WorkspaceImporter(application),
        aiGenerationService = AiGenerationService(),
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
                    _uiState.update { state ->
                        val currentSelectedBook = state.selectedBookKey
                        val validSelectedBook = when {
                            ws == null -> ""
                            ws.books.any { it.bookKey == currentSelectedBook } -> currentSelectedBook
                            else -> ws.books.firstOrNull()?.bookKey.orEmpty()
                        }
                        state.copy(
                            workspace = ws,
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
                packageSourcePages = lesson.pageRange.orEmpty(),
                page = AppPage.STUDIO
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
                it.copy(alert = AlertMessage("تم الحفظ", "تم حفظ نسخة جديدة من المسودة بنجاح."))
            }
        }
    }

    fun approveDraft(draftId: String) {
        viewModelScope.launch {
            repository.approveDraft(draftId)
            _uiState.update {
                it.copy(alert = AlertMessage("تم الاعتماد", "تم اعتماد هذه النسخة للدرس."))
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
            state.copy(
                providerForm = state.providerForm.copy(
                    provider = type,
                    label = defaultLabel
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

    fun importWorkspaceZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val imported = repository.importWorkspaceZip(uri)
                val warningSuffix = if (imported.warnings.isNotEmpty()) {
                    "\n\nتنبيهات:\n• ${imported.warnings.joinToString("\n• ")}"
                } else ""
                _uiState.update {
                    it.copy(
                        selectedWorkspaceId = imported.workspaceId,
                        workspace = imported,
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

    fun addProvider() {
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
            val next = _uiState.value.providers + ProviderConfig(
                id = System.currentTimeMillis().toString(),
                provider = form.provider,
                label = form.label.trim(),
                model = form.model.trim(),
                fallbackModels = fallbacks,
                baseUrl = form.baseUrl.trim(),
                apiKey = form.apiKey.trim(),
                enabled = true,
                createdAt = System.currentTimeMillis(),
                priority = _uiState.value.providers.size
            )
            repository.saveProviders(next)
            _uiState.update {
                it.copy(
                    providers = next,
                    providerForm = it.providerForm.copy(apiKey = "", fallbackModels = ""),
                    alert = AlertMessage("حفظ", "تم حفظ مفتاح المزود محلياً.")
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
