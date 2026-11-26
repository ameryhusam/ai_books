package com.example.ui

import com.example.model.*
import com.example.data.ai.AiModelDefinition

/**
 * UI-facing state models shared by Compose screens and MainViewModel.
 * Kept separate from orchestration logic to make state contracts easier to review.
 */

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
    val model: String = com.example.data.ai.AiModelCatalogDefaults.DEFAULT_GEMINI_MODEL_ID,
    val fallbackModels: String = "",
    val strictModel: Boolean = false,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val dailyRequestLimit: String = "0",
    val quotaTimeZone: String = "Asia/Aden"
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
    val userRole: UserRole = UserRole.ENTRY,
    val isCloudAuthenticated: Boolean = false,
    val authenticatedUserRole: UserRole? = null,
    val authRoleCheckState: AuthRoleCheckState = AuthRoleCheckState.Idle,
    val availableLearners: List<LearnerProfile> = listOf(LearnerProfile.DEFAULT),
    val activeLearner: LearnerProfile = LearnerProfile.DEFAULT,
    val page: AppPage = AppPage.DASHBOARD,
    val selectedWorkspaceId: String? = null,
    val workspace: ImportedWorkspace? = null,
    val workspaces: List<WorkspaceSummary> = emptyList(),
    val activeWorkspaceDiskBytes: Long = 0L,
    val selectedBookKey: String = "",
    val selectedLesson: LessonRef? = null,
    val task: TaskType = TaskType.QUESTIONS,
    val activePromptTemplateId: String = "",
    val questionCount: Int = 5,
    val questionStyle: String = "متنوعة",
    val questionDifficulty: QuestionDifficulty = QuestionDifficulty.MEDIUM,
    val attachPdfPages: Boolean = true,
    val sourceText: String = "",
    val extraInstructions: String = "",
    val generatedResult: String = "",
    val activeDraftProvider: String = "تحرير يدوي",
    val activeDraftModel: String = "local",
    val activeDraftTemplateId: String = "",
    val activeDraftTemplateVersion: Int = 1,
    val activeDraftResponseSchemaHash: String = "",
    val activeDraftRequestFingerprint: String = "",
    val activeDraftContentHash: String = "",
    val activeDraftSourcePdfHash: String = "",
    val packageSourcePages: String = "",
    val previewingPackage: InteractivePackageRecord? = null,
    val studentPreviewMode: Boolean = false,
    val activeInteractiveActivity: InteractiveActivityModel? = null,
    val activeInteractiveLesson: LessonRef? = null,
    val studentStudyLesson: LessonRef? = null,
    val lastLessonQuizResult: LessonQuizCompletionResult? = null,
    val reinforcementConceptTitle: String? = null,
    val reinforcementQuestions: List<QuestionItem> = emptyList(),
    val directInspection: DirectWorkspaceInspection? = null,
    val previewingPdf: PdfPreviewPayload? = null,
    val previewingBookCover: BookRef? = null,
    val promptSourcesOpen: Boolean = false,
    val stagedPreview: StagedWorkspacePreview? = null,
    val globalSearchOpen: Boolean = false,
    val pendingExportAssessment: AssessmentRecord? = null,
    val providers: List<ProviderConfig> = emptyList(),
    val aiModelCatalog: List<AiModelDefinition> = emptyList(),
    val selectedProvider: ProviderType = ProviderType.GEMINI,
    val providerForm: ProviderFormState = ProviderFormState(),
    val recentRuns: List<GenerationRunRecord> = emptyList(),
    val isBusy: Boolean = false,
    val generationProgressPercent: Int? = null,
    val generationStage: String? = null,
    val toastMessage: String? = null,
    val alert: AlertMessage? = null
) {
    val availableBoundGrades: List<String>
        get() = workspaces.map { it.gradeKey.uppercase() }.distinct().sorted()
}
