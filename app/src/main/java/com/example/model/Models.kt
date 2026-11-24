package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType(val id: String, val labelAr: String) {
    EXPLANATION("explanation", "شرح الدرس"),
    INTERACTIVE("interactive", "درس تفاعلي"),
    QUESTIONS("questions", "بنك الأسئلة");

    companion object {
        fun fromId(id: String): TaskType = entries.firstOrNull { it.id == id } ?: EXPLANATION
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
    val createdAt: Long
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
    val pdfPath: String? = null
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
    val status: String = "DRAFT"
)

enum class AppPage {
    LIBRARY,
    STUDIO,
    SETTINGS
}
