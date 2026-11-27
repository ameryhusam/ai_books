package com.example.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.json.Json

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
    QUESTIONS("questions", "بنك الأسئلة"),
    LESSON_TEXT("lesson_text", "حفظ نص الدرس");

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
    OPENAI_COMPATIBLE("openai-compatible", "OpenAI");

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
    val strictModel: Boolean = false,
    val dailyRequestLimit: Int = 0,
    val dailyRequestsUsed: Int = 0,
    val quotaDate: String = "",
    val runtimeStatus: String = "ACTIVE",
    val retryAfterEpochMs: Long = 0L,
    val quotaTimeZone: String = "Asia/Aden",
    val lastUsedAtEpochMs: Long = 0L
)

/**
 * Stable, provider-scoped aliases for API keys. These aliases identify credentials,
 * not API connections or model selections.
 */
object ProviderApiKeyNames {
    fun prefix(provider: ProviderType): String = when (provider) {
        ProviderType.GEMINI -> "GEMINI_API_KEY"
        ProviderType.OPENAI_COMPATIBLE -> "OPENAI_API_KEY"
    }

    fun nextName(provider: ProviderType, existing: List<ProviderConfig>): String {
        val prefix = prefix(provider)
        val pattern = Regex("^${prefix}_(\\d+)$")
        val next = existing.asSequence()
            .filter { it.provider == provider }
            .mapNotNull { pattern.matchEntire(it.label.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()?.plus(1) ?: 1
        return "${prefix}_${next}"
    }

    /**
     * Gives legacy records stable API_KEY_N aliases without changing their IDs,
     * credentials, enablement, quota state, or provider ordering.
     */
    fun normalizeLegacyNames(profiles: List<ProviderConfig>): List<ProviderConfig> {
        val result = profiles.toMutableList()
        ProviderType.entries.forEach { provider ->
            val scoped = profiles.filter { it.provider == provider }
                .sortedWith(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt }.thenBy { it.id })
            val prefix = prefix(provider)
            val pattern = Regex("^${prefix}_(\\d+)$")
            val used = scoped.mapNotNull {
                pattern.matchEntire(it.label.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
            }.toMutableSet()
            var next = 1
            scoped.forEach { profile ->
                val currentNumber = pattern.matchEntire(profile.label.trim())
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (currentNumber == null) {
                    while (next in used) next++
                    val index = result.indexOfFirst { it.id == profile.id }
                    if (index >= 0) result[index] = profile.copy(label = "${prefix}_${next}")
                    used += next
                    next++
                }
            }
        }
        return result
    }
}

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
    val displayName: String,
    val enrolledGradeKey: String? = null
) {
    companion object {
        const val DEFAULT_USER_ID = "learner_default"
        val DEFAULT = LearnerProfile(
            userId = DEFAULT_USER_ID,
            displayName = "المتعلّم الأساسي",
            enrolledGradeKey = null
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
    val bookKey: String = "",
    val unitKey: String = "",
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
    val sourcePdfPath: String? = null,
    val subjectName: String? = null,
    val approvalStatus: String? = null,
    val requiresReview: Boolean = true,
    val sourceReviewStatus: String? = null,
    val schemaVersion: String = "edu.book_index.v2",
    val gradeName: String = "",
    val sourceLayout: String = "UNKNOWN",
    val sourceLayoutEvidence: String = "",
    val partKey: String? = null,
    val offset: Int = 1,
    val totalPdfPages: Int = 0,
    val sourceFileName: String? = null,
    val sourceSha256: String? = null
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
    val interactivePath: String? = null,
    val printedPageRange: String? = null,
    val printedPageStart: Int? = null,
    val printedPageEnd: Int? = null,
    val pdfPageStart: Int? = null,
    val pdfPageEnd: Int? = null,
    val sortOrder: Int = 0
)

data class DirectGradeChoice(
    val gradeKey: String,
    val booksCount: Int,
    val lessonsCount: Int,
    val bookTitles: List<String>,
    val gradeDisplayName: String = ""
)

data class DirectWorkspaceInspection(
    val rootUriOrPath: String,
    val displayTitle: String,
    val isSafUri: Boolean,
    val grades: List<DirectGradeChoice>,
    val totalFilesIndexed: Int,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

data class WorkspaceIndexRef(
    val bookKey: String,
    val indexUriOrPath: String,
    val indexRelativePath: String,
    val manifestUriOrPath: String? = null,
    val manifestRelativePath: String? = null,
    val indexSha256: String,
    val manifestSha256: String? = null,
    val sourceSha256: String? = null,
    val indexSchemaVersion: String? = null,
    val manifestVersion: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

data class WorkspaceUnitRef(
    val bookKey: String,
    val unitKey: String,
    val title: String,
    val partKey: String? = null,
    val sortOrder: Int,
    val sourceIndexPath: String,
    val proposedPartKey: String? = null,
    val assignmentStatus: String = "need_review",
    val assignmentSource: String = "LEGACY_UNVERIFIED",
    val assignmentEvidence: String = ""
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
    val importedAt: Long = System.currentTimeMillis(),
    val indexRecords: List<WorkspaceIndexRef> = emptyList(),
    val units: List<WorkspaceUnitRef> = emptyList()
)

data class WorkspaceSummary(
    val workspaceId: String,
    val gradeKey: String,
    val title: String,
    val filesCount: Int,
    val importedAt: Long
)

@Serializable
data class LessonGenerationMetadataDto(
    val templateId: String = "",
    val templateVersion: Int = 1,
    val promptUsed: String = "",
    val responseSchemaJson: String = "",
    val responseSchemaHash: String = "",
    val requestFingerprint: String = "",
    val contentHash: String = "",
    val sourcePdfHash: String = "",
    val sourceIndexSha256: String = "",
    val provider: String = "",
    val model: String = "",
    val taskType: String = "",
    val createdAt: String = ""
) {
    fun toJsonString(): String = Json.encodeToString(this)

    companion object {
        fun fromJsonString(json: String): LessonGenerationMetadataDto {
            return try {
                Json.decodeFromString<LessonGenerationMetadataDto>(json)
            } catch (e: Exception) {
                LessonGenerationMetadataDto()
            }
        }
    }
}

data class GeneratedResult(
    val text: String,
    val providerLabel: String,
    val model: String,
    val createdAt: String,
    val durationMs: Long = 0L,
    val tokensUsed: Int = 0,
    val templateId: String = "",
    val templateVersion: Int = 1,
    val responseSchemaHash: String = "",
    val requestFingerprint: String = "",
    val contentHash: String = "",
    val sourcePdfHash: String = "",
    val promptUsed: String = "",
    val responseSchemaJson: String = "",
    /** Feature-owned contract/domain validation findings; raw text remains reviewable. */
    val validationIssues: List<String> = emptyList()
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
    val sourceHash: String? = null,
    val templateId: String = "",
    val templateVersion: Int = 1,
    val responseSchemaHash: String = "",
    val requestFingerprint: String = "",
    val contentHash: String = "",
    val sourcePdfHash: String = "",
    val promptUsed: String = "",
    val responseSchemaJson: String = "",
    val sourceIndexSha256: String = ""
)

data class LessonTextRecord(
    val workspaceId: String,
    val gradeKey: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val lessonTitle: String,
    val content: String,
    val extractionMethod: String = "manual",
    val sourcePages: String? = null,
    val sourcePdfHash: String = "",
    val sourceIndexSha256: String = "",
    val templateId: String = "",
    val promptUsed: String = "",
    val responseSchemaJson: String = "",
    val contentHash: String = "",
    val version: Int = 1,
    val updatedAt: String = ""
)

fun GeneratedResult.toMetadataDto(taskType: String = ""): LessonGenerationMetadataDto =
    LessonGenerationMetadataDto(
        templateId = templateId,
        templateVersion = templateVersion,
        promptUsed = promptUsed,
        responseSchemaJson = responseSchemaJson,
        responseSchemaHash = responseSchemaHash,
        requestFingerprint = requestFingerprint,
        contentHash = contentHash,
        sourcePdfHash = sourcePdfHash,
        provider = providerLabel,
        model = model,
        taskType = taskType,
        createdAt = createdAt
    )

fun DraftRecord.toMetadataDto(): LessonGenerationMetadataDto =
    LessonGenerationMetadataDto(
        templateId = templateId,
        templateVersion = templateVersion,
        promptUsed = promptUsed,
        responseSchemaJson = responseSchemaJson,
        responseSchemaHash = responseSchemaHash,
        requestFingerprint = requestFingerprint,
        contentHash = contentHash,
        sourcePdfHash = sourcePdfHash,
        sourceIndexSha256 = sourceIndexSha256,
        provider = provider,
        model = model,
        taskType = task.id,
        createdAt = createdAt
    )

fun LessonTextRecord.toMetadataDto(): LessonGenerationMetadataDto =
    LessonGenerationMetadataDto(
        templateId = templateId,
        templateVersion = version,
        promptUsed = promptUsed,
        responseSchemaJson = responseSchemaJson,
        contentHash = contentHash,
        sourcePdfHash = sourcePdfHash,
        sourceIndexSha256 = sourceIndexSha256,
        taskType = "lesson_text",
        createdAt = updatedAt
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

enum class PdfSplitTemplate(
    val id: String,
    val labelAr: String,
    val descriptionAr: String
) {
    FIXED_CHUNK(
        id = "fixed_chunk",
        labelAr = "تقسيم متساوٍ بالصفحات",
        descriptionAr = "تقسيم الكتاب إلى دروس متساوية الطول بعدد صفحات ثابت"
    ),
    UNIT_GROUPED(
        id = "unit_grouped",
        labelAr = "قالب وحدات ودروس قياسي",
        descriptionAr = "تجميع الدروس في وحدات منظمة (4 دروس/وحدة) متوافقة مع مخطط الفهرس المعتمد"
    ),
    AI_TOC_SCHEMA(
        id = "ai_toc_schema",
        labelAr = "تجزئة ذكية بالبرومبت ومخطط الفهرس",
        descriptionAr = "تحليل فهرس الكتاب بالذكاء الاصطناعي وفق مخطط index.json المعتمد داخل التطبيق"
    )
}

data class LessonSplitDraft(
    val id: String,
    val unitKey: String,
    val unitTitle: String,
    val lessonKey: String,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val partKey: String = "P01"
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
) {
    /** Excludes authoring/creation badge bonuses from the learner's own score. */
    val learnerPoints: Int
        get() = (totalPoints - badges
            .filter { it.unlocked && it.category == BadgeCategory.CREATION }
            .sumOf { it.pointsBonus })
            .coerceAtLeast(0)
}

enum class QuestionType(val id: String, val labelAr: String) {
    MCQ("mcq", "اختيار من متعدد"),
    TRUE_FALSE("true_false", "صح أم خطأ"),
    FILL_BLANK("fill_blank", "إكمال الفراغ"),
    ORDERING("ordering", "الترتيب"),
    MATCHING("matching", "التوصيل"),
    SHORT_ANSWER("short_answer", "إجابة قصيرة");

    companion object {
        val DEFAULT_GENERATION_IDS: List<String> = listOf(MCQ.id, TRUE_FALSE.id, SHORT_ANSWER.id)

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

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class QuestionRequiredField

@Serializable
data class QuestionContentPayload(
    @QuestionRequiredField val type: String,
    @QuestionRequiredField val prompt: String,
    @QuestionRequiredField val options: List<String>,
    @QuestionRequiredField val correctAnswer: String,
    @QuestionRequiredField val difficulty: String,
    val conceptTag: String = "",
    val rationale: String = "",
    val sourcePages: String = ""
)

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
    val conceptTag: String = "",
    val draftId: String = "",
    val templateId: String = "",
    val promptUsed: String = "",
    val responseSchemaJson: String = "",
    val sourceIndexSha256: String = ""
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

data class WeeklyAssessmentSummary(
    val weekStart: String,
    val label: String,
    val averageScorePercent: Int,
    val attemptsCount: Int,
    val passedCount: Int
)

data class RepeatedMistakeSummary(
    val conceptTag: String,
    val questionPrompt: String,
    val selectedAnswer: String,
    val expectedAnswer: String,
    val occurrences: Int,
    val lastSeenAt: String
)

/**
 * Report data shown in different levels of detail by learner and parent/teacher dashboards.
 * Null percentages mean there is not enough comparable evidence; they must not be displayed as 0%.
 */
data class LearningProgressReport(
    val questionAttemptsCount: Int = 0,
    val conceptsObserved: Int = 0,
    val firstTryAccuracyPercent: Int? = null,
    val latestAccuracyPercent: Int? = null,
    val stableConceptsCount: Int = 0,
    val eligibleConceptsCount: Int = 0,
    val stableConceptsPercent: Int? = null,
    val delayedRecallPercent: Int? = null,
    val estimatedRecallDropPercent: Int? = null,
    val delayedRecallSampleCount: Int = 0,
    val recurringMistakes: List<RepeatedMistakeSummary> = emptyList(),
    val recurringConceptTags: List<String> = emptyList(),
    val conceptsWithRecurringErrorsCount: Int = 0,
    val weeklyAssessments: List<WeeklyAssessmentSummary> = emptyList(),
    val reinforcementImpactPercent: Int? = null,
    val reinforcementImpactConceptCount: Int = 0,
    val hasEnoughDataForRetention: Boolean = false
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
    val isBuiltIn: Boolean = false,
    val responseSchemaJson: String = "",
    val responseMappingJson: String = "",
    val responseFormat: String = "text",
    val sourceAttachmentMode: String = "pdf_native",
    val version: Int = 1
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
    val warnings: List<String>,
    val requiresReviewConfirmation: Boolean = false,
    val reviewFindings: List<String> = emptyList(),
    val reviewIndexPaths: List<String> = emptyList()
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

data class BookIndexSchemaSpec(
    val id: String,
    val fileName: String,
    val titleAr: String,
    val descriptionAr: String,
    val schemaJson: String
)

enum class AppPage {
    DASHBOARD,
    LEARNER_MASTERY,
    ADMIN_DASHBOARD,
    LIBRARY,
    STUDIO,
    QUIZ_BANK,
    SETTINGS
}
