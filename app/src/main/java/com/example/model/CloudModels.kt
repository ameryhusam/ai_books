package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudDraft(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val grade: String = "",
    val bookKey: String = "",
    val unitKey: String = "",
    val lessonKey: String = "",
    val task: String = TaskType.EXPLANATION.id,
    val title: String = "",
    val provider: String = "",
    val model: String = "",
    val content: String = "",
    val templateId: String = "",
    val templateVersion: Long = 1L,
    val responseSchemaHash: String = "",
    val requestFingerprint: String = "",
    val contentHash: String = "",
    val sourcePdfHash: String = "",
    val promptUsed: String = "",
    val responseSchemaJson: String = "",
    val sourceIndexSha256: String = "",
    val status: String = DraftStatus.DRAFT.id,
    val version: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun taskTypeOrDefault(): TaskType = TaskType.fromId(task)
    fun statusOrDefault(): DraftStatus = DraftStatus.fromId(status)
}

@Keep
@IgnoreExtraProperties
data class LessonProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val bookKey: String = "",
    val unitKey: String = "",
    val lessonKey: String = "",
    val lessonTitle: String = "",
    val completed: Boolean = false,
    val xpEarned: Long = 0L,
    val scorePercent: Long = 0L,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudUserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = ROLE_STUDENT,
    val displayName: String? = null,
    val enrolledGradeKey: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_STUDENT = "student"
    }
}

enum class AuthRoleDestination {
    ADMIN_DASHBOARD,
    STUDENT_VIEW
}

data class UserRoleResolution(
    val rawRole: String,
    val resolvedRole: UserRole,
    val destination: AuthRoleDestination,
    val enrolledGradeKey: String? = null,
    val displayName: String? = null,
    val documentExists: Boolean = false
)

sealed interface AuthRoleCheckState {
    data object Idle : AuthRoleCheckState
    data object Checking : AuthRoleCheckState
    data class Resolved(val resolution: UserRoleResolution) : AuthRoleCheckState
    data class Error(val message: String) : AuthRoleCheckState
}

sealed interface SyncUiState<out T> {
    data object Loading : SyncUiState<Nothing>
    data class Success<T>(val data: T) : SyncUiState<T>
    data class Error(val message: String) : SyncUiState<Nothing>
}
