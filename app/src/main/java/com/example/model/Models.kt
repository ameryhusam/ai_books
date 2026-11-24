package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType(val id: String, val labelAr: String) {
    EXPLANATION("explanation", "شرح الدرس"),
    SUMMARY("summary", "ملخص الدرس"),
    OBJECTIVES("objectives", "أهداف التعلم"),
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
    val fallbackModels: List<String> = emptyList()
)

data class BookRef(
    val bookKey: String,
    val title: String,
    val subjectKey: String,
    val componentKey: String? = null,
    val relativePath: String,
    val lessonsCount: Int
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
    val workspaceId: String = ""
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
    val createdAt: String
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
    val createdAt: String
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

enum class AppPage {
    LIBRARY,
    STUDIO,
    SETTINGS
}
