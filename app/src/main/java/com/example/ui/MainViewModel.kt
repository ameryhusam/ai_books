package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiGenerationService
import com.example.data.local.EduMiniDatabase
import com.example.data.repository.EduMiniRepository
import com.example.data.security.ProviderStorage
import com.example.data.workspace.WorkspaceImporter
import com.example.model.AppPage
import com.example.model.DraftRecord
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.TaskType
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
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = ""
)

data class MainUiState(
    val page: AppPage = AppPage.LIBRARY,
    val workspace: ImportedWorkspace? = null,
    val selectedBookKey: String = "",
    val selectedLesson: LessonRef? = null,
    val task: TaskType = TaskType.EXPLANATION,
    val sourceText: String = "",
    val extraInstructions: String = "",
    val generatedResult: String = "",
    val providers: List<ProviderConfig> = emptyList(),
    val providerForm: ProviderFormState = ProviderFormState(),
    val isBusy: Boolean = false,
    val alert: AlertMessage? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EduMiniRepository = EduMiniRepository(
        dao = EduMiniDatabase.getInstance(application).eduMiniDao(),
        providerStorage = ProviderStorage(application),
        workspaceImporter = WorkspaceImporter(application),
        aiGenerationService = AiGenerationService()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val lessonDrafts: StateFlow<List<DraftRecord>> = _uiState
        .map { it.selectedLesson }
        .distinctUntilChanged()
        .flatMapLatest { lesson ->
            if (lesson == null) flowOf(emptyList()) else repository.observeDraftsForLesson(lesson)
        }
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
            repository.latestWorkspace.collect { ws ->
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
                page = AppPage.STUDIO
            )
        }
    }

    fun selectTask(task: TaskType) {
        _uiState.update { it.copy(task = task) }
    }

    fun updateSourceText(value: String) {
        _uiState.update { it.copy(sourceText = value) }
    }

    fun updateExtraInstructions(value: String) {
        _uiState.update { it.copy(extraInstructions = value) }
    }

    fun loadDraftIntoEditor(draft: DraftRecord) {
        _uiState.update {
            it.copy(
                task = draft.task,
                generatedResult = draft.content
            )
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

    fun updateFormBaseUrl(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(baseUrl = value)) }
    }

    fun updateFormApiKey(value: String) {
        _uiState.update { it.copy(providerForm = it.providerForm.copy(apiKey = value)) }
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

    fun generateContent() {
        val snapshot = _uiState.value
        val lesson = snapshot.selectedLesson
        if (lesson == null) {
            _uiState.update {
                it.copy(alert = AlertMessage("حدد الدرس", "اختر درساً من المكتبة أولاً."))
            }
            return
        }
        if (snapshot.sourceText.trim().isEmpty()) {
            _uiState.update {
                it.copy(
                    alert = AlertMessage(
                        "أدخل نص المصدر",
                        "اكتب أو ألصق مقتطف الدرس في خانة المصدر."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val out = repository.generateContent(
                    lesson = lesson,
                    task = snapshot.task,
                    source = snapshot.sourceText,
                    extra = snapshot.extraInstructions,
                    providers = snapshot.providers
                )
                repository.persistDraft(
                    lesson = lesson,
                    task = snapshot.task,
                    providerLabel = out.providerLabel,
                    model = out.model,
                    text = out.text,
                    title = lesson.title
                )
                _uiState.update {
                    it.copy(
                        generatedResult = out.text,
                        alert = AlertMessage(
                            title = "تم حفظ المسودة",
                            message = "تم حفظ الناتج محلياً في قاعدة البيانات وربطه بالدرس."
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

    fun addProvider() {
        val form = _uiState.value.providerForm
        if (form.label.trim().isEmpty() || form.model.trim().isEmpty() || form.apiKey.trim().isEmpty()) {
            _uiState.update {
                it.copy(alert = AlertMessage("بيانات ناقصة", "أدخل الاسم والنموذج والمفتاح"))
            }
            return
        }
        viewModelScope.launch {
            val next = _uiState.value.providers + ProviderConfig(
                id = System.currentTimeMillis().toString(),
                provider = form.provider,
                label = form.label.trim(),
                model = form.model.trim(),
                baseUrl = form.baseUrl.trim(),
                apiKey = form.apiKey.trim(),
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
            repository.saveProviders(next)
            _uiState.update {
                it.copy(
                    providers = next,
                    providerForm = it.providerForm.copy(apiKey = ""),
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

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            val next = _uiState.value.providers.filterNot { it.id == id }
            repository.saveProviders(next)
            _uiState.update { it.copy(providers = next) }
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(alert = null) }
    }
}
