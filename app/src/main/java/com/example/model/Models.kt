package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType(val id: String, val labelAr: String) {
    EXPLANATION("explanation", "شرح الدرس"),
    SUMMARY("summary", "ملخص الدرس"),
    OBJECTIVES("objectives", "أهداف التعلم"),
    VOCABULARY("vocabulary", "باني المفردات"),
    WORKED_EXAMPLES("worked_examples", "أمثلة محلولة"),
    LESSON_REVIEW("lesson_review", "مراجعة الدرس"),
    DIFFICULTY_ADAPTER("difficulty_adapter", "تكييف الصعوبة"),
    INTERACTIVE("interactive", "درس تفاعلي"),
    QUESTIONS("questions", "بنك الأسئلة");

    companion object {
        fun fromId(id: String): TaskType = entries.firstOrNull { it.id == id } ?: EXPLANATION
    }
}

@Serializable
enum class DraftStatus(val id: String, val labelAr: String) {
    DRAFT("DRAFT", "مسودة"),
    APPROVED("APPROVED", "معتمد"),
    ARCHIVED("ARCHIVED", "مؤرشف");

    companion object {
        fun fromId(id: String): DraftStatus = entries.firstOrNull { it.id == id } ?: DRAFT
    }
}

@Serializable
enum class ProviderType(val id: String, val displayName: String) {
    GEMINI("gemini", "Gemini"),
    OPENAI_COMPATIBLE("openai-compatible", "OpenAI Compatible");

    companion object {
        fun fromId(id: String): ProviderType = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

@Serializable
data class ProviderConfig(
    val id: String,
    val provider: ProviderType,
    val label: String,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val enabled: Boolean,
    val createdAt: Long,
    val priority: Int = 0,
    val fallbackModels: List<String> = emptyList(),
    val strictModel: Boolean = false
)

@Serializable
enum class UserRole(val id: String, val labelAr: String) {
    ENTRY("entry", "اختيار الدخول"),
    LEARNER("learner", "متعلّم"),
    ADMIN("admin", "مدير / معلم");

    companion object {
        fun fromId(id: String): UserRole = entries.firstOrNull { it.id == id } ?: ENTRY
    }
}

enum class MasteryStatus(val labelAr: String) {
    UNSEEN("لم يُختبَر"),
    MASTERED("مُتقَن"),
    NEEDS_REINFORCEMENT("يحتاج تقوية")
}

@Serializable
data class LearnerProfile(
    val userId: String,
    val displayName: String
) {
    companion object {
        const val DEFAULT_USER_ID = "learner_default"
        val DEFAULT = LearnerProfile(
            userId = DEFAULT_USER_ID,
            displayName = "المتعلّم الأساسي"
        )
    }
}

data class QuestionAttemptRecord(
    val id: String,
    val workspaceId: String,
    val userId: String = LearnerProfile.DEFAULT_USER_ID,
    val questionId: String,
    val conceptTag: String,
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val sourceMode: String = "lesson_quiz",
    val answeredAt: String
)

data class QuestionMasteryStats(
    val questionId: String,
    val conceptTag: String,
    val attemptsCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val lastSelectedAnswer: String?,
    val lastIsCorrect: Boolean?,
    val status: MasteryStatus
) {
    fun sameAsPrevious(candidateAnswer: String): Boolean {
        val prev = lastSelectedAnswer?.trim().orEmpty()
        val curr = candidateAnswer.trim()
        return prev.isNotEmpty() && curr.isNotEmpty() && prev.equals(curr, ignoreCase = true)
    }
}

data class ConceptMasterySummary(
    val conceptTag: String,
    val lessonKey: String? = null,
    val lessonTitle: String? = null,
    val bookTitle: String? = null,
    val questionsCount: Int,
    val attemptsCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val accuracyPercent: Int,
    val status: MasteryStatus
)

data class LessonQuizCompletionResult(
    val lessonKey: String,
    val correctCount: Int,
    val totalQuestions: Int,
    val scorePercent: Int,
    val passed: Boolean,
    val lessonMarkedCompleted: Boolean,
    val earnedXp: Int,
    val weakConcepts: List<ConceptMasterySummary>,
    val masteredConcepts: List<ConceptMasterySummary>
)

data class BookRef(
    val bookKey: String,
    val title: String,
    val subjectKey: String,
    val componentKey: String? = null,
    val relativePath: String,
    val lessonsCount: Int,
    val coverPath: String? = null,
    val sourcePdfPath: String? = null
)

data class LessonRef(
    val grade: String,
    val rootPath: String,
    val bookKey: String,
    val bookTitle: String,
    val subjectKey: String,
    val componentKey: String? = null,
    val unitKey: String,
    val unitTitle: String,
    val lessonKey: String,
    val title: String,
    val partKey: String? = null,
    val pageRange: String? = null,
    val pdfPath: String? = null,
    val workspaceId: String = "",
    val sourceTextPath: String? = null,
    val interactivePath: String? = null
)

data class DirectGradeChoice(
    val gradeKey: String,
    val booksCount: Int,
    val lessonsCount: Int,
    val bookTitles: List<String>
)

data class DirectWorkspaceInspection(
    val rootUriOrPath: String,
    val displayTitle: String,
    val isSafUri: Boolean,
    val grades: List<DirectGradeChoice>,
    val totalFilesIndexed: Int,
    val warnings: List<String> = emptyList()
)

data class ImportedWorkspace(
    val workspaceId: String,
    val gradeKey: String,
    val rootPath: String,
    val title: String,
    val books: List<BookRef>,
    val lessons: List<LessonRef>,
    val filesCount: Int,
    val warnings: List<String>,
    val importedAt: Long = System.currentTimeMillis()
)

data class WorkspaceSummary(
    val workspaceId: String,
    val gradeKey: String,
    val title: String,
    val filesCount: Int,
    val importedAt: Long
)

data class GeneratedResult(
    val text: String,
    val providerLabel: String,
    val model: String,
    val createdAt: String,
    val durationMs: Long = 0L,
    val tokensUsed: Int = 0
)

data class DraftRecord(
    val id: String,
    val workspaceId: String,
    val grade: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val task: TaskType,
    val title: String,
    val provider: String,
    val model: String,
    val createdAt: String,
    val content: String,
    val status: String = DraftStatus.DRAFT.id,
    val version: Int = 1,
    val sourcePages: String? = null,
    val sourceHash: String? = null
)

data class GenerationRunRecord(
    val id: String,
    val workspaceId: String,
    val lessonKey: String,
    val task: TaskType,
    val providerLabel: String,
    val model: String,
    val status: String,
    val errorSummary: String?,
    val createdAt: String,
    val durationMs: Long = 0L,
    val tokensUsed: Int = 0
)

@Serializable
enum class PackageKind(val id: String, val labelAr: String) {
    STANDALONE_HTML("standalone_html", "ملف HTML مستقل"),
    ZIP_BUNDLE("zip_bundle", "حزمة ZIP (HTML + 3D/وسائط)");

    companion object {
        fun fromId(id: String): PackageKind = entries.firstOrNull { it.id == id } ?: STANDALONE_HTML
    }
}

@Serializable
enum class SecuritySeverity {
    PASS,
    WARNING,
    BLOCKER
}

@Serializable
data class SecurityFinding(
    val severity: SecuritySeverity,
    val code: String,
    val message: String
)

@Serializable
data class InteractivePackageReport(
    val kind: PackageKind,
    val fileName: String,
    val entryRelativePath: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val has3dAssets: Boolean,
    val assetExtensions: List<String>,
    val externalUrls: List<String>,
    val missingReferencedAssets: List<String>,
    val findings: List<SecurityFinding>,
    val linkedLessonMatch: Boolean
) {
    val hasBlockers: Boolean
        get() = findings.any { it.severity == SecuritySeverity.BLOCKER }
}

data class InteractivePackageRecord(
    val id: String,
    val workspaceId: String,
    val gradeKey: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val kind: PackageKind,
    val title: String,
    val rootDir: String,
    val entryFile: String,
    val sourcePages: String?,
    val version: Int,
    val status: String,
    val publishedForStudents: Boolean,
    val report: InteractivePackageReport,
    val createdAt: String
)

data class PdfBookInfo(
    val fileName: String,
    val storedPdfPath: String,
    val totalPages: Int,
    val sha256: String
)

data class LessonSplitDraft(
    val id: String,
    val unitKey: String,
    val unitTitle: String,
    val lessonKey: String,
    val title: String,
    val startPage: Int,
    val endPage: Int
)

enum class LearningNodeStatus(val labelAr: String) {
    NOT_STARTED("لم يبدأ"),
    IN_PROGRESS("قيد الإعداد"),
    READY("مُجهّز"),
    COMPLETED("مكتمل")
}

enum class UnitCompletionStatus(val labelAr: String) {
    NOT_STARTED("لم تبدأ"),
    IN_PROGRESS("قيد الدراسة"),
    COMPLETED("مكتملة بالاختبار")
}

enum class DashboardLessonFilter(val labelAr: String) {
    ALL("الكل"),
    PENDING("المتبقية"),
    IN_PROGRESS("قيد التعلّم"),
    READY_FOR_QUIZ("جاهزة للاختبار"),
    COMPLETED("المكتملة")
}

data class StudentDashboardState(
    val selectedBookKey: String = "",
    val lessonFilter: DashboardLessonFilter = DashboardLessonFilter.ALL,
    val collapsedUnitKeys: Set<String> = emptySet(),
    val recentlyCompletedUnitTitle: String? = null
)

data class LearningLessonNode(
    val lesson: LessonRef,
    val status: LearningNodeStatus,
    val isCompleted: Boolean,
    val draftsCount: Int,
    val approvedDraftsCount: Int,
    val packagesCount: Int,
    val publishedPackagesCount: Int,
    val pointsEarned: Int,
    val maxPossiblePoints: Int,
    val questionsCount: Int = 0,
    val quizAttemptedCount: Int = 0,
    val quizPassed: Boolean = isCompleted,
    val hasGeneratedMaterials: Boolean = draftsCount > 0 ||
        approvedDraftsCount > 0 ||
        packagesCount > 0 ||
        publishedPackagesCount > 0 ||
        questionsCount > 0
)

data class LearningUnitPath(
    val unitKey: String,
    val unitTitle: String,
    val bookTitle: String,
    val completedCount: Int,
    val totalCount: Int,
    val completionPercent: Int,
    val nodes: List<LearningLessonNode>,
    val status: UnitCompletionStatus = when {
        totalCount > 0 && completedCount == totalCount -> UnitCompletionStatus.COMPLETED
        completedCount > 0 -> UnitCompletionStatus.IN_PROGRESS
        else -> UnitCompletionStatus.NOT_STARTED
    },
    val questionsCount: Int = 0,
    val generatedContentCount: Int = 0,
    val inProgressCount: Int = 0
)

data class BookProgressSummary(
    val book: BookRef,
    val completedLessons: Int,
    val preparedLessons: Int,
    val totalLessons: Int,
    val completionPercent: Int,
    val preparationPercent: Int,
    val earnedPoints: Int
)

enum class BadgeCategory(val labelAr: String) {
    MILESTONE("إنجاز التعلّم"),
    STREAK("الاستمرارية"),
    CREATION("الإنتاج الذكي"),
    MASTERY("الإتقان")
}

data class AchievementBadge(
    val id: String,
    val title: String,
    val subtitle: String,
    val unlocked: Boolean,
    val pointsBonus: Int,
    val category: BadgeCategory = BadgeCategory.MILESTONE,
    val currentProgress: Int = if (unlocked) 1 else 0,
    val targetProgress: Int = 1,
    val requirementLabel: String = subtitle
)

data class DashboardSummary(
    val hasWorkspace: Boolean = false,
    val gradeKey: String = "—",
    val workspaceTitle: String = "",
    val totalLessons: Int = 0,
    val completedLessons: Int = 0,
    val preparedLessons: Int = 0,
    val approvedItemsCount: Int = 0,
    val overallCompletionPercent: Int = 0,
    val preparationPercent: Int = 0,
    val totalPoints: Int = 0,
    val level: Int = 1,
    val levelTitle: String = "مستكشف المعرفة",
    val pointsIntoLevel: Int = 0,
    val pointsPerLevel: Int = 150,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val unlockedBadgesCount: Int = 0,
    val bookProgress: List<BookProgressSummary> = emptyList(),
    val unitPaths: List<LearningUnitPath> = emptyList(),
    val badges: List<AchievementBadge> = emptyList(),
    val nextRecommendedLesson: LessonRef? = null,
    val weakConcepts: List<ConceptMasterySummary> = emptyList(),
    val masteredConceptsCount: Int = 0,
    val weakConceptsCount: Int = 0
)

enum class QuestionType(val id: String, val labelAr: String) {
    MCQ("mcq", "اختيار من متعدد"),
    TRUE_FALSE("true_false", "صح أم خطأ"),
    FILL_BLANK("fill_blank", "إكمال الفراغ"),
    ORDERING("ordering", "الترتيب"),
    MATCHING("matching", "التوصيل"),
    SHORT_ANSWER("short_answer", "إجابة قصيرة");

    companion object {
        fun fromId(id: String): QuestionType = entries.firstOrNull {
            it.id.equals(id.trim(), ignoreCase = true)
        } ?: MCQ
    }
}

enum class QuestionDifficulty(val id: String, val labelAr: String) {
    EASY("easy", "سهل"),
    MEDIUM("medium", "متوسط"),
    HARD("hard", "متقدم");

    companion object {
        fun fromId(id: String): QuestionDifficulty = entries.firstOrNull {
            it.id.equals(id.trim(), ignoreCase = true)
        } ?: MEDIUM
    }
}

data class QuestionItem(
    val id: String,
    val workspaceId: String,
    val gradeKey: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val lessonTitle: String,
    val type: QuestionType,
    val prompt: String,
    val options: List<String>,
    val correctAnswer: String,
    val rationale: String,
    val difficulty: QuestionDifficulty,
    val sourcePages: String? = null,
    val fingerprint: String = "",
    val reviewed: Boolean = false,
    val createdAt: String = "",
    val isDuplicate: Boolean = false,
    val conceptTag: String = ""
)

enum class AssessmentKind(val id: String, val labelAr: String) {
    DIAGNOSTIC("diagnostic", "قبلي"),
    FORMATIVE("formative", "تكويني"),
    QUIZ("quiz", "قصير"),
    COMPREHENSIVE("comprehensive", "شامل"),
    FINAL("final", "نهائي"),
    SELF("self", "ذاتي");

    companion object {
        fun fromId(id: String): AssessmentKind = entries.firstOrNull {
            it.id.equals(id.trim(), ignoreCase = true)
        } ?: QUIZ
    }
}

data class AssessmentRecord(
    val id: String,
    val workspaceId: String,
    val title: String,
    val kind: AssessmentKind,
    val questionIds: List<String>,
    val timeLimitMinutes: Int,
    val passScorePercent: Int,
    val createdAt: String
)

data class AssessmentAttemptRecord(
    val id: String,
    val assessmentId: String,
    val assessmentTitle: String,
    val workspaceId: String,
    val correctCount: Int,
    val totalQuestions: Int,
    val scorePercent: Int,
    val passed: Boolean,
    val durationSeconds: Int,
    val earnedXp: Int,
    val completedAt: String
)

data class TeacherNoteRecord(
    val workspaceId: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val noteText: String,
    val bookmarked: Boolean,
    val updatedAt: String
)

data class PromptTemplateRecord(
    val id: String,
    val title: String,
    val task: TaskType,
    val instructions: String,
    val isBuiltIn: Boolean = false
)

data class InteractiveStepItem(
    val title: String,
    val body: String,
    val checkpointQuestion: String = "",
    val checkpointOptions: List<String> = emptyList(),
    val checkpointAnswer: String = "",
    val conceptTag: String = ""
)

data class InteractiveMatchPair(
    val left: String,
    val right: String
)

data class InteractiveActivityModel(
    val title: String,
    val objective: String = "",
    val steps: List<InteractiveStepItem> = emptyList(),
    val orderingItems: List<String> = emptyList(),
    val matchingPairs: List<InteractiveMatchPair> = emptyList()
)

data class StagedGradeSummary(
    val gradeKey: String,
    val booksCount: Int,
    val lessonsCount: Int,
    val bookTitles: List<String>
)

data class StagedWorkspacePreview(
    val stagingId: String,
    val tempDirPath: String,
    val fileCount: Int,
    val expandedBytes: Long,
    val grades: List<StagedGradeSummary>,
    val warnings: List<String>
)

sealed interface GlobalSearchHit {
    val id: String
    val title: String
    val subtitle: String

    data class LessonHit(val lesson: LessonRef) : GlobalSearchHit {
        override val id: String = "hit_lesson_${lesson.bookKey}_${lesson.unitKey}_${lesson.lessonKey}"
        override val title: String = lesson.title
        override val subtitle: String = "${lesson.bookTitle} • ${lesson.unitTitle}"
    }

    data class DraftHit(val draft: DraftRecord, val lesson: LessonRef?) : GlobalSearchHit {
        override val id: String = "hit_draft_${draft.id}"
        override val title: String = "${draft.task.labelAr}: ${draft.title}"
        override val subtitle: String = draft.content.take(90).replace('\n', ' ')
    }

    data class QuestionHit(val question: QuestionItem) : GlobalSearchHit {
        override val id: String = "hit_q_${question.id}"
        override val title: String = question.prompt
        override val subtitle: String = "${question.type.labelAr} • ${question.lessonTitle}"
    }
}

enum class LibraryPartFilter(val labelAr: String) {
    ALL("الكل"),
    P01("الفصل الأول P01"),
    P02("الفصل الثاني P02"),
    WITH_PDF("PDF متاح")
}

data class PromptBlueprintSpec(
    val id: String,
    val task: TaskType,
    val titleAr: String,
    val subtitleAr: String,
    val sourcePath: String,
    val roleInstruction: String,
    val outputContract: String,
    val schemaSnippet: String = "",
    val quickInstructions: String = ""
)

data class Indexer2SchemaSpec(
    val id: String,
    val fileName: String,
    val titleAr: String,
    val descriptionAr: String,
    val schemaJson: String
)

enum class AppPage {
    DASHBOARD,
    LEARNER_MASTERY,
    LIBRARY,
    STUDIO,
    QUIZ_BANK,
    SETTINGS
}
