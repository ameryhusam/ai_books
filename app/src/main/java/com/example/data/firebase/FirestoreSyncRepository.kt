package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.model.AuthRoleDestination
import com.example.model.CloudDraft
import com.example.model.CloudUserAccount
import com.example.model.DraftRecord
import com.example.model.DraftStatus
import com.example.model.LessonProgress
import com.example.model.LessonRef
import com.example.model.TaskType
import com.example.model.UserRole
import com.example.model.UserRoleResolution
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun normalizeSha256(value: String, fieldName: String): String {
        val normalized = value.trim().lowercase()
        require(normalized.isEmpty() || normalized.matches(Regex("^[a-f0-9]{64}$"))) {
            "$fieldName must be empty or a 64-character SHA-256 hex digest."
        }
        return normalized
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google قبل المزامنة مع السحابة.")
    }

    suspend fun resolveUserRole(
        userId: String,
        defaultDisplayName: String? = null
    ): Result<UserRoleResolution> {
        var docPath = "users/$userId"
        var currentOp = OperationType.GET
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تم تبديل الحساب؛ تم رفض قراءة صلاحية مستخدم آخر." }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val resolution = resolveUserRoleFromDocument(
                    roleValue = snapshot.getString("role"),
                    enrolledGradeKey = snapshot.getString("enrolledGradeKey"),
                    displayName = snapshot.getString("displayName") ?: defaultDisplayName,
                    documentExists = true
                )
                Result.success(resolution)
            } else {
                currentOp = OperationType.CREATE
                val cleanDisplayName = defaultDisplayName?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
                val createPayload = mapOf(
                    "userId" to uid,
                    "role" to CloudUserAccount.ROLE_STUDENT,
                    "displayName" to cleanDisplayName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
                Result.success(
                    resolveUserRoleFromDocument(
                        roleValue = CloudUserAccount.ROLE_STUDENT,
                        enrolledGradeKey = null,
                        displayName = cleanDisplayName,
                        documentExists = false
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, currentOp, docPath)
            Result.failure(e)
        }
    }

    fun observeDrafts(userId: String): Flow<List<CloudDraft>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "تم تبديل الحساب؛ تم رفض قراءة مسودات مستخدم آخر." }
        val path = "users/$uid/drafts"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("drafts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudDraft::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProgress(userId: String): Flow<List<LessonProgress>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "تم تبديل الحساب؛ تم رفض قراءة تقدم مستخدم آخر." }
        val path = "users/$uid/progress"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("progress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LessonProgress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertDraft(
        userId: String,
        draft: DraftRecord
    ): Result<String> {
        val docId = draftDocId(draft.id)
        var docPath = "users/$userId/drafts/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeWorkspace = draft.workspaceId.trim().ifBlank { "default_ws" }.take(128)
            val safeGrade = draft.grade.trim().ifBlank { "G00" }.take(32)
            val safeBook = draft.bookKey.trim().ifBlank { "BOOK" }.take(64)
            val safeUnit = draft.unitKey.trim().ifBlank { "unit_01" }.take(64)
            val safeLesson = draft.lessonKey.trim().ifBlank { "lesson_01" }.take(64)
            val safeTitle = draft.title.trim().ifBlank { safeLesson }.take(200)
            val safeProvider = draft.provider.trim().ifBlank { "local" }.take(100)
            val safeModel = draft.model.trim().ifBlank { "local" }.take(120)
            val safeContent = draft.content.trim().take(200_000)
            require(safeContent.isNotEmpty()) { "محتوى المسودة فارغ ولا يمكن مزامنته." }
            val safeStatus = when (draft.status.uppercase()) {
                DraftStatus.APPROVED.id, DraftStatus.ARCHIVED.id, DraftStatus.DRAFT.id -> draft.status.uppercase()
                else -> DraftStatus.DRAFT.id
            }
            val safeVersion = draft.version.coerceIn(1, 10_000).toLong()
            val safeTemplateId = draft.templateId.trim().take(160)
            val safeTemplateVersion = draft.templateVersion.coerceIn(1, 10_000).toLong()
            val safeResponseSchemaHash = normalizeSha256(draft.responseSchemaHash, "responseSchemaHash")
            val safeRequestFingerprint = normalizeSha256(draft.requestFingerprint, "requestFingerprint")
            val safeContentHash = normalizeSha256(draft.contentHash, "contentHash")
            val safeSourcePdfHash = normalizeSha256(draft.sourcePdfHash, "sourcePdfHash")

            val docRef = db.collection("users")
                .document(uid)
                .collection("drafts")
                .document(docId)
            docPath = docRef.path

            val existingSnapshot = docRef.get().await()
            if (existingSnapshot.exists()) {
                docRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "content" to safeContent,
                        "templateId" to safeTemplateId,
                        "templateVersion" to safeTemplateVersion,
                        "responseSchemaHash" to safeResponseSchemaHash,
                        "requestFingerprint" to safeRequestFingerprint,
                        "contentHash" to safeContentHash,
                        "sourcePdfHash" to safeSourcePdfHash,
                        "status" to safeStatus,
                        "version" to safeVersion,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "workspaceId" to safeWorkspace,
                        "grade" to safeGrade,
                        "bookKey" to safeBook,
                        "unitKey" to safeUnit,
                        "lessonKey" to safeLesson,
                        "task" to draft.task.id,
                        "title" to safeTitle,
                        "provider" to safeProvider,
                        "model" to safeModel,
                        "content" to safeContent,
                        "templateId" to safeTemplateId,
                        "templateVersion" to safeTemplateVersion,
                        "responseSchemaHash" to safeResponseSchemaHash,
                        "requestFingerprint" to safeRequestFingerprint,
                        "contentHash" to safeContentHash,
                        "sourcePdfHash" to safeSourcePdfHash,
                        "status" to safeStatus,
                        "version" to safeVersion,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDraftStatus(
        userId: String,
        draftId: String,
        status: DraftStatus
    ): Result<Unit> {
        val validId = draftDocId(draftId)
        var docPath = "users/$userId/drafts/$validId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user update." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("drafts")
                .document(validId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to status.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDraft(userId: String, draftId: String): Result<Unit> {
        val validId = draftDocId(draftId)
        var docPath = "users/$userId/drafts/$validId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("drafts")
                .document(validId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertLessonProgress(
        userId: String,
        workspaceId: String,
        lesson: LessonRef,
        completed: Boolean,
        xpEarned: Int,
        scorePercent: Int,
        notes: String? = null
    ): Result<Unit> {
        val docId = progressDocId(workspaceId, lesson.bookKey, lesson.unitKey, lesson.lessonKey)
        var docPath = "users/$userId/progress/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeWorkspace = workspaceId.trim().ifBlank { lesson.workspaceId }.ifBlank { "default_ws" }.take(128)
            val safeBook = lesson.bookKey.trim().ifBlank { "BOOK" }.take(64)
            val safeUnit = lesson.unitKey.trim().ifBlank { "unit_01" }.take(64)
            val safeLesson = lesson.lessonKey.trim().ifBlank { "lesson_01" }.take(64)
            val safeTitle = lesson.title.trim().ifBlank { safeLesson }.take(200)
            val safeXp = xpEarned.coerceIn(0, 100_000).toLong()
            val safeScore = scorePercent.coerceIn(0, 100).toLong()
            val cleanNotes = notes?.trim()?.take(2000)?.takeIf { it.isNotEmpty() }

            val docRef = db.collection("users")
                .document(uid)
                .collection("progress")
                .document(docId)
            docPath = docRef.path

            val existingSnapshot = docRef.get().await()
            if (existingSnapshot.exists()) {
                val updatePayload = mutableMapOf<String, Any>(
                    "completed" to completed,
                    "xpEarned" to safeXp,
                    "scorePercent" to safeScore,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanNotes != null) {
                    updatePayload["notes"] = cleanNotes
                }
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "workspaceId" to safeWorkspace,
                    "bookKey" to safeBook,
                    "unitKey" to safeUnit,
                    "lessonKey" to safeLesson,
                    "lessonTitle" to safeTitle,
                    "completed" to completed,
                    "xpEarned" to safeXp,
                    "scorePercent" to safeScore,
                    "notes" to cleanNotes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

        fun resolveUserRoleFromDocument(
            roleValue: String?,
            enrolledGradeKey: String? = null,
            displayName: String? = null,
            documentExists: Boolean = true
        ): UserRoleResolution {
            val normalizedRole = roleValue?.trim()?.lowercase().orEmpty()
            val cleanDisplayName = displayName?.trim()?.takeIf { it.isNotBlank() }
            return if (normalizedRole == CloudUserAccount.ROLE_ADMIN) {
                UserRoleResolution(
                    rawRole = CloudUserAccount.ROLE_ADMIN,
                    resolvedRole = UserRole.ADMIN,
                    destination = AuthRoleDestination.ADMIN_DASHBOARD,
                    enrolledGradeKey = null,
                    displayName = cleanDisplayName,
                    documentExists = documentExists
                )
            } else {
                UserRoleResolution(
                    rawRole = CloudUserAccount.ROLE_STUDENT,
                    resolvedRole = UserRole.LEARNER,
                    destination = AuthRoleDestination.STUDENT_VIEW,
                    enrolledGradeKey = enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                    displayName = cleanDisplayName,
                    documentExists = documentExists
                )
            }
        }

        fun draftDocId(rawDraftId: String): String {
            val trimmed = rawDraftId.trim()
            return if (VALID_ID_REGEX.matches(trimmed)) trimmed else sanitizeId("draft", trimmed)
        }

        fun progressDocId(
            workspaceId: String,
            bookKey: String,
            unitKey: String,
            lessonKey: String
        ): String {
            return sanitizeId("prog", workspaceId, bookKey, unitKey, lessonKey)
        }

        private fun sanitizeId(prefix: String, vararg parts: String): String {
            val joined = parts.joinToString("_") { it.trim() }
            val cleaned = joined.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            val hash = sha1Short(joined)
            val base = if (cleaned.isEmpty()) prefix else "${prefix}_${cleaned}"
            val maxBaseLen = 128 - hash.length - 1
            return "${base.take(maxBaseLen)}_$hash"
        }

        private fun sha1Short(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.take(6).joinToString("") { "%02x".format(it) }
        }
    }
}
