package com.example.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val workspaceId: String,
    val gradeKey: String,
    val rootPath: String,
    val title: String,
    val filesCount: Int,
    val warningsJson: String,
    val importedAt: Long
)

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["workspaceId"]),
        Index(name = "index_books_workspaceId_bookKey", value = ["workspaceId", "bookKey"], unique = true)
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val workspaceId: String,
    val bookKey: String,
    val title: String,
    val subjectKey: String,
    val componentKey: String?,
    val relativePath: String,
    val lessonsCount: Int,
    val coverPath: String? = null,
    val sourcePdfPath: String? = null,
    val subjectName: String? = null
)

@Entity(
    tableName = "lessons",
    indices = [
        Index(value = ["workspaceId", "bookKey"]),
        Index(
            name = "index_lessons_workspaceId_bookKey_unitKey_lessonKey",
            value = ["workspaceId", "bookKey", "unitKey", "lessonKey"],
            unique = true
        )
    ]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val workspaceId: String,
    val grade: String,
    val rootPath: String,
    val bookKey: String,
    val bookTitle: String,
    val subjectKey: String,
    val componentKey: String?,
    val unitKey: String,
    val unitTitle: String,
    val lessonKey: String,
    val title: String,
    val partKey: String?,
    val pageRange: String?,
    val pdfPath: String?,
    @ColumnInfo(name = "source_text_path") val sourceTextPath: String? = null,
    @ColumnInfo(name = "interactive_path") val interactivePath: String? = null,
    @ColumnInfo(name = "printed_page_range") val printedPageRange: String? = null
)

@Entity(
    tableName = "workspace_indexes",
    primaryKeys = ["workspace_id", "book_key"]
)
data class WorkspaceIndexEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "index_uri") val indexUri: String,
    @ColumnInfo(name = "index_relative_path") val indexRelativePath: String,
    @ColumnInfo(name = "manifest_uri") val manifestUri: String?,
    @ColumnInfo(name = "manifest_relative_path") val manifestRelativePath: String?,
    @ColumnInfo(name = "index_sha256") val indexSha256: String,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String?,
    @ColumnInfo(name = "source_sha256") val sourceSha256: String? = null,
    @ColumnInfo(name = "index_schema_version") val indexSchemaVersion: String?,
    @ColumnInfo(name = "manifest_version") val manifestVersion: String?,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long
)

@Entity(
    tableName = "workspace_units",
    primaryKeys = ["workspace_id", "book_key", "unit_key"],
    indices = [Index(name = "idx_workspace_units_part", value = ["workspace_id", "book_key", "part_key"])]
)
data class WorkspaceUnitEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "part_key") val partKey: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "source_index_path") val sourceIndexPath: String
)

@Entity(
    tableName = "generation_drafts",
    indices = [
        Index(
            name = "idx_drafts_lesson",
            value = ["workspace_id", "book_key", "unit_key", "lesson_key", "created_at"]
        )
    ]
)
data class DraftEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "grade_key") val gradeKey: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "task") val task: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "status") val status: String = "DRAFT",
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "source_pages") val sourcePages: String? = null,
    @ColumnInfo(name = "source_hash") val sourceHash: String? = null,
    @ColumnInfo(name = "template_id", defaultValue = "''") val templateId: String = "",
    @ColumnInfo(name = "template_version", defaultValue = "1") val templateVersion: Int = 1,
    @ColumnInfo(name = "response_schema_hash", defaultValue = "''") val responseSchemaHash: String = "",
    @ColumnInfo(name = "request_fingerprint", defaultValue = "''") val requestFingerprint: String = "",
    @ColumnInfo(name = "content_hash", defaultValue = "''") val contentHash: String = "",
    @ColumnInfo(name = "source_pdf_hash", defaultValue = "''") val sourcePdfHash: String = ""
)

@Entity(
    tableName = "interactive_packages",
    indices = [
        Index(
            name = "idx_packages_lesson",
            value = ["workspace_id", "book_key", "unit_key", "lesson_key", "version"]
        )
    ]
)
data class InteractivePackageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "grade_key") val gradeKey: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "root_dir") val rootDir: String,
    @ColumnInfo(name = "entry_file") val entryFile: String,
    @ColumnInfo(name = "source_pages") val sourcePages: String?,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "published_for_students") val publishedForStudents: Boolean,
    @ColumnInfo(name = "report_json") val reportJson: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(
    tableName = "generation_runs",
    indices = [Index(name = "idx_runs_created", value = ["created_at"])]
)
data class GenerationRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "task") val task: String,
    @ColumnInfo(name = "provider_label") val providerLabel: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "error_summary") val errorSummary: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "tokens_used") val tokensUsed: Int = 0
)

@Entity(
    tableName = "lesson_progress",
    primaryKeys = ["workspace_id", "book_key", "unit_key", "lesson_key", "user_id"],
    indices = [Index(name = "idx_lesson_progress_ws_user", value = ["workspace_id", "user_id"])]
)
data class LessonProgressEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "completed") val completed: Boolean = false,
    @ColumnInfo(name = "points_earned") val pointsEarned: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: String? = null,
    @ColumnInfo(name = "user_id", defaultValue = "'learner_default'") val userId: String = "learner_default"
)

@Entity(tableName = "study_days", primaryKeys = ["activity_date", "user_id"], indices = [Index(name = "idx_study_days_user_date", value = ["user_id", "activity_date"])])
data class StudyDayEntity(
    @ColumnInfo(name = "activity_date") val activityDate: String,
    @ColumnInfo(name = "actions_count") val actionsCount: Int = 1,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: String,
    @ColumnInfo(name = "user_id", defaultValue = "'learner_default'") val userId: String = "learner_default"
)

@Entity(
    tableName = "questions",
    indices = [
        Index(
            name = "idx_questions_workspace_lesson",
            value = ["workspace_id", "book_key", "unit_key", "lesson_key"]
        )
    ]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "grade_key") val gradeKey: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "lesson_title") val lessonTitle: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "prompt") val prompt: String,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    @ColumnInfo(name = "correct_answer") val correctAnswer: String,
    @ColumnInfo(name = "rationale") val rationale: String,
    @ColumnInfo(name = "difficulty") val difficulty: String,
    @ColumnInfo(name = "source_pages") val sourcePages: String?,
    @ColumnInfo(name = "fingerprint") val fingerprint: String,
    @ColumnInfo(name = "reviewed") val reviewed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "concept_tag") val conceptTag: String = ""
)

@Entity(
    tableName = "question_attempts",
    indices = [
        Index(
            name = "idx_q_attempts_ws_question",
            value = ["workspace_id", "question_id", "answered_at"]
        ),
        Index(
            name = "idx_q_attempts_ws_concept",
            value = ["workspace_id", "concept_tag"]
        ),
        Index(
            name = "idx_q_attempts_ws_user",
            value = ["workspace_id", "user_id", "answered_at"]
        )
    ]
)
data class QuestionAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "user_id") val userId: String = "learner_default",
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "concept_tag") val conceptTag: String,
    @ColumnInfo(name = "selected_answer") val selectedAnswer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "source_mode") val sourceMode: String = "lesson_quiz",
    @ColumnInfo(name = "answered_at") val answeredAt: String
)

@Entity(
    tableName = "assessments",
    indices = [Index(name = "idx_assessments_ws", value = ["workspace_id"])]
)
data class AssessmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "question_ids_json") val questionIdsJson: String,
    @ColumnInfo(name = "time_limit_minutes") val timeLimitMinutes: Int,
    @ColumnInfo(name = "pass_score_percent") val passScorePercent: Int,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(
    tableName = "assessment_attempts",
    indices = [
        Index(name = "idx_attempts_ws", value = ["workspace_id", "completed_at"]),
        Index(name = "idx_attempts_ws_user", value = ["workspace_id", "user_id", "completed_at"])
    ]
)
data class AssessmentAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "assessment_id") val assessmentId: String,
    @ColumnInfo(name = "assessment_title") val assessmentTitle: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "correct_count") val correctCount: Int,
    @ColumnInfo(name = "total_questions") val totalQuestions: Int,
    @ColumnInfo(name = "score_percent") val scorePercent: Int,
    @ColumnInfo(name = "passed") val passed: Boolean,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "earned_xp") val earnedXp: Int,
    @ColumnInfo(name = "completed_at") val completedAt: String,
    @ColumnInfo(name = "user_id", defaultValue = "'learner_default'") val userId: String = "learner_default"
)

@Entity(
    tableName = "teacher_notes",
    primaryKeys = ["workspace_id", "book_key", "unit_key", "lesson_key"]
)
data class TeacherNoteEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "note_text") val noteText: String,
    @ColumnInfo(name = "bookmarked") val bookmarked: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "task") val task: String,
    @ColumnInfo(name = "instructions") val instructions: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "response_schema_json", defaultValue = "''") val responseSchemaJson: String = "",
    @ColumnInfo(name = "response_mapping_json", defaultValue = "''") val responseMappingJson: String = "",
    @ColumnInfo(name = "response_format", defaultValue = "'text'") val responseFormat: String = "text",
    @ColumnInfo(name = "source_attachment_mode", defaultValue = "'pdf_native'") val sourceAttachmentMode: String = "pdf_native",
    @ColumnInfo(name = "version", defaultValue = "1") val version: Int = 1
)

@Dao
interface EduMiniDao {
    @Query("SELECT * FROM workspaces ORDER BY importedAt DESC LIMIT 1")
    fun observeLatestWorkspace(): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspaces ORDER BY importedAt DESC")
    fun observeAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE workspaceId = :workspaceId LIMIT 1")
    fun observeWorkspaceById(workspaceId: String): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM books WHERE workspaceId = :workspaceId ORDER BY rowId ASC")
    fun observeBooksForWorkspace(workspaceId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM lessons WHERE workspaceId = :workspaceId ORDER BY rowId ASC")
    fun observeLessonsForWorkspace(workspaceId: String): Flow<List<LessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query("SELECT * FROM books WHERE workspaceId = :workspaceId")
    suspend fun getBooksForWorkspaceSnapshot(workspaceId: String): List<BookEntity>

    @Query("DELETE FROM books WHERE rowId = :rowId")
    suspend fun deleteBookByRowId(rowId: Long)

    @Query("DELETE FROM lessons WHERE rowId = :rowId")
    suspend fun deleteLessonByRowId(rowId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Transaction
    suspend fun saveWorkspaceGraph(
        workspace: WorkspaceEntity,
        books: List<BookEntity>,
        lessons: List<LessonEntity>
    ) {
        insertWorkspace(workspace)
        insertBooks(books)
        insertLessons(lessons)
    }

    @Query("DELETE FROM workspace_indexes WHERE workspace_id = :workspaceId")
    suspend fun deleteWorkspaceIndexes(workspaceId: String)

    @Query("DELETE FROM workspace_units WHERE workspace_id = :workspaceId")
    suspend fun deleteWorkspaceUnits(workspaceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaceIndexes(indexes: List<WorkspaceIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaceUnits(units: List<WorkspaceUnitEntity>)

    @Transaction
    suspend fun syncDirectWorkspaceCatalog(
        workspace: WorkspaceEntity,
        books: List<BookEntity>,
        lessons: List<LessonEntity>,
        indexes: List<WorkspaceIndexEntity>,
        units: List<WorkspaceUnitEntity>
    ) {
        insertWorkspace(workspace)

        // Keep stable Room row IDs for identities that still exist in the refreshed index.
        val existingBooks = getBooksForWorkspaceSnapshot(workspace.workspaceId)
        val incomingBooks = books.distinctBy { it.bookKey }
        val incomingBookKeys = incomingBooks.map { it.bookKey }.toSet()
        existingBooks.filterNot { it.bookKey in incomingBookKeys }.forEach {
            deleteBookByRowId(it.rowId)
        }
        val existingBooksByKey = existingBooks.associateBy { it.bookKey }
        val stableBooks = incomingBooks.map { incoming ->
            existingBooksByKey[incoming.bookKey]?.let { incoming.copy(rowId = it.rowId) } ?: incoming
        }

        val existingLessons = getLessonsForWorkspaceSnapshot(workspace.workspaceId)
        val incomingLessons = lessons.distinctBy {
            Triple(it.bookKey, it.unitKey, it.lessonKey)
        }
        val incomingLessonKeys = incomingLessons.map {
            Triple(it.bookKey, it.unitKey, it.lessonKey)
        }.toSet()
        existingLessons.filterNot {
            Triple(it.bookKey, it.unitKey, it.lessonKey) in incomingLessonKeys
        }.forEach {
            deleteLessonByRowId(it.rowId)
        }
        val existingLessonsByKey = existingLessons.associateBy {
            Triple(it.bookKey, it.unitKey, it.lessonKey)
        }
        val stableLessons = incomingLessons.map { incoming ->
            val key = Triple(incoming.bookKey, incoming.unitKey, incoming.lessonKey)
            existingLessonsByKey[key]?.let { incoming.copy(rowId = it.rowId) } ?: incoming
        }

        if (stableBooks.isNotEmpty()) insertBooks(stableBooks)
        if (stableLessons.isNotEmpty()) insertLessons(stableLessons)

        deleteWorkspaceIndexes(workspace.workspaceId)
        deleteWorkspaceUnits(workspace.workspaceId)
        if (indexes.isNotEmpty()) insertWorkspaceIndexes(indexes)
        if (units.isNotEmpty()) insertWorkspaceUnits(units)
    }

    @Query("SELECT * FROM workspace_indexes WHERE workspace_id = :workspaceId ORDER BY book_key")
    fun observeWorkspaceIndexes(workspaceId: String): Flow<List<WorkspaceIndexEntity>>

    @Query("SELECT * FROM workspace_units WHERE workspace_id = :workspaceId ORDER BY book_key, sort_order")
    fun observeWorkspaceUnits(workspaceId: String): Flow<List<WorkspaceUnitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftEntity)

    @Query(
        """
        SELECT * FROM generation_drafts
        WHERE (workspace_id = :workspaceId OR workspace_id = :legacyRootPath)
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
        ORDER BY version DESC, created_at DESC
        """
    )
    fun observeDraftsForLesson(
        workspaceId: String,
        legacyRootPath: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String
    ): Flow<List<DraftEntity>>

    @Query(
        """
        SELECT COALESCE(MAX(version), 0) FROM generation_drafts
        WHERE (workspace_id = :workspaceId OR workspace_id = :legacyRootPath)
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
        """
    )
    suspend fun getMaxDraftVersion(
        workspaceId: String,
        legacyRootPath: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String
    ): Int

    @Query("UPDATE generation_drafts SET status = :status WHERE id = :draftId")
    suspend fun updateDraftStatus(draftId: String, status: String)

    @Query("SELECT * FROM generation_drafts WHERE id = :draftId LIMIT 1")
    suspend fun getDraftById(draftId: String): DraftEntity?

    @Query("SELECT * FROM lessons WHERE workspaceId = :workspaceId")
    suspend fun getLessonsForWorkspaceSnapshot(workspaceId: String): List<LessonEntity>

    @Query("DELETE FROM generation_drafts WHERE id = :draftId")
    suspend fun deleteDraftById(draftId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractivePackage(pkg: InteractivePackageEntity)

    @Query(
        """
        SELECT * FROM interactive_packages
        WHERE workspace_id = :workspaceId
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
        ORDER BY version DESC
        """
    )
    fun observePackagesForLesson(
        workspaceId: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String
    ): Flow<List<InteractivePackageEntity>>

    @Query(
        """
        SELECT COALESCE(MAX(version), 0) FROM interactive_packages
        WHERE workspace_id = :workspaceId
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
        """
    )
    suspend fun getMaxPackageVersion(
        workspaceId: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String
    ): Int

    @Query(
        """
        UPDATE interactive_packages
        SET published_for_students = 0,
            status = CASE WHEN status = 'APPROVED' THEN 'ARCHIVED' ELSE status END
        WHERE workspace_id = :workspaceId
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
          AND id != :activePackageId
        """
    )
    suspend fun archiveOtherPackageVersions(
        workspaceId: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String,
        activePackageId: String
    )

    @Query(
        """
        UPDATE interactive_packages
        SET status = 'APPROVED',
            published_for_students = :publishForStudents
        WHERE id = :packageId
        """
    )
    suspend fun approvePackageVersion(packageId: String, publishForStudents: Boolean)

    @Transaction
    suspend fun publishPackageVersion(
        workspaceId: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String,
        packageId: String
    ) {
        archiveOtherPackageVersions(workspaceId, bookKey, unitKey, lessonKey, packageId)
        approvePackageVersion(packageId, true)
    }

    @Query("DELETE FROM interactive_packages WHERE id = :packageId")
    suspend fun deletePackageById(packageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenerationRun(run: GenerationRunEntity)

    @Query("SELECT * FROM generation_runs ORDER BY created_at DESC LIMIT 25")
    fun observeRecentRuns(): Flow<List<GenerationRunEntity>>

    @Query(
        """
        SELECT * FROM generation_drafts
        WHERE workspace_id = :workspaceId OR workspace_id = :legacyRootPath
        """
    )
    fun observeDraftsForWorkspace(
        workspaceId: String,
        legacyRootPath: String
    ): Flow<List<DraftEntity>>

    @Query("SELECT * FROM interactive_packages WHERE workspace_id = :workspaceId")
    fun observePackagesForWorkspace(workspaceId: String): Flow<List<InteractivePackageEntity>>

    @Query("SELECT * FROM lesson_progress WHERE workspace_id = :workspaceId AND user_id = :userId")
    fun observeProgressForWorkspace(workspaceId: String, userId: String = "learner_default"): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE workspace_id = :workspaceId")
    fun observeAllProgressForWorkspace(workspaceId: String): Flow<List<LessonProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonProgress(progress: LessonProgressEntity)

    @Query("SELECT * FROM study_days WHERE user_id = :userId ORDER BY activity_date DESC")
    fun observeStudyDays(userId: String = "learner_default"): Flow<List<StudyDayEntity>>

    @Query("SELECT * FROM study_days ORDER BY activity_date DESC")
    fun observeAllStudyDays(): Flow<List<StudyDayEntity>>

    @Query("SELECT * FROM study_days WHERE activity_date = :dateIso AND user_id = :userId LIMIT 1")
    suspend fun getStudyDay(dateIso: String, userId: String = "learner_default"): StudyDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyDay(studyDay: StudyDayEntity)

    @Query("SELECT * FROM questions WHERE workspace_id = :workspaceId ORDER BY created_at DESC")
    fun observeQuestionsForWorkspace(workspaceId: String): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("UPDATE questions SET reviewed = :reviewed WHERE id = :questionId")
    suspend fun updateQuestionReviewed(questionId: String, reviewed: Boolean)

    @Query(
        """
        UPDATE questions
        SET prompt = :prompt,
            options_json = :optionsJson,
            correct_answer = :correctAnswer,
            rationale = :rationale,
            difficulty = :difficulty,
            concept_tag = :conceptTag,
            type = :type,
            reviewed = :reviewed
        WHERE id = :questionId
        """
    )
    suspend fun updateQuestionDetails(
        questionId: String,
        prompt: String,
        optionsJson: String,
        correctAnswer: String,
        rationale: String,
        difficulty: String,
        conceptTag: String,
        type: String,
        reviewed: Boolean
    )

    @Query("UPDATE questions SET concept_tag = :conceptTag WHERE id = :questionId")
    suspend fun updateQuestionConceptTag(questionId: String, conceptTag: String)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestionById(questionId: String)

    @Query("SELECT * FROM question_attempts WHERE workspace_id = :workspaceId ORDER BY answered_at DESC")
    fun observeQuestionAttemptsForWorkspace(workspaceId: String): Flow<List<QuestionAttemptEntity>>

    @Query("SELECT * FROM question_attempts WHERE workspace_id = :workspaceId AND user_id = :userId ORDER BY answered_at DESC")
    fun observeQuestionAttemptsForWorkspaceAndUser(workspaceId: String, userId: String): Flow<List<QuestionAttemptEntity>>

    @Query("SELECT * FROM question_attempts WHERE workspace_id = :workspaceId AND user_id = :userId AND concept_tag = :conceptTag ORDER BY answered_at DESC")
    fun observeQuestionAttemptsForConceptAndUser(
        workspaceId: String,
        userId: String,
        conceptTag: String
    ): Flow<List<QuestionAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionAttempts(attempts: List<QuestionAttemptEntity>)

    @Query("DELETE FROM question_attempts WHERE workspace_id = :workspaceId")
    suspend fun deleteQuestionAttemptsByWorkspace(workspaceId: String)

    @Query("DELETE FROM generation_runs")
    suspend fun clearGenerationRuns()

    @Query("DELETE FROM generation_runs WHERE workspace_id = :workspaceId")
    suspend fun deleteGenerationRunsByWorkspace(workspaceId: String)

    @Query("SELECT * FROM assessments WHERE workspace_id = :workspaceId ORDER BY created_at DESC")
    fun observeAssessmentsForWorkspace(workspaceId: String): Flow<List<AssessmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: AssessmentEntity)

    @Query("DELETE FROM assessments WHERE id = :assessmentId")
    suspend fun deleteAssessmentById(assessmentId: String)

    @Query("SELECT * FROM assessment_attempts WHERE workspace_id = :workspaceId AND user_id = :userId ORDER BY completed_at DESC")
    fun observeAssessmentAttempts(workspaceId: String, userId: String = "learner_default"): Flow<List<AssessmentAttemptEntity>>

    @Query("SELECT * FROM assessment_attempts WHERE workspace_id = :workspaceId ORDER BY completed_at DESC")
    fun observeAllAssessmentAttempts(workspaceId: String): Flow<List<AssessmentAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessmentAttempt(attempt: AssessmentAttemptEntity)

    @Query(
        """
        SELECT * FROM teacher_notes
        WHERE workspace_id = :workspaceId
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
        LIMIT 1
        """
    )
    fun observeTeacherNote(
        workspaceId: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String
    ): Flow<TeacherNoteEntity?>

    @Query("SELECT * FROM teacher_notes WHERE workspace_id = :workspaceId")
    fun observeTeacherNotesForWorkspace(workspaceId: String): Flow<List<TeacherNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTeacherNote(note: TeacherNoteEntity)

    @Query("SELECT * FROM prompt_templates ORDER BY created_at DESC")
    fun observePromptTemplates(): Flow<List<PromptTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromptTemplate(template: PromptTemplateEntity)

    @Query("UPDATE prompt_templates SET title = :title, instructions = :instructions, response_schema_json = :responseSchemaJson, response_mapping_json = :responseMappingJson, response_format = :responseFormat, source_attachment_mode = :sourceAttachmentMode, version = version + 1 WHERE id = :templateId")
    suspend fun updatePromptTemplate(templateId: String, title: String, instructions: String, responseSchemaJson: String, responseMappingJson: String, responseFormat: String, sourceAttachmentMode: String)

    @Query("DELETE FROM prompt_templates WHERE id = :templateId")
    suspend fun deletePromptTemplateById(templateId: String)

    @Query("DELETE FROM workspaces WHERE workspaceId = :workspaceId")
    suspend fun deleteWorkspaceById(workspaceId: String)

    @Query("DELETE FROM books WHERE workspaceId = :workspaceId")
    suspend fun deleteBooksByWorkspace(workspaceId: String)

    @Query("DELETE FROM lessons WHERE workspaceId = :workspaceId")
    suspend fun deleteLessonsByWorkspace(workspaceId: String)

    @Query("DELETE FROM generation_drafts WHERE workspace_id = :workspaceId")
    suspend fun deleteDraftsByWorkspace(workspaceId: String)

    @Query("DELETE FROM interactive_packages WHERE workspace_id = :workspaceId")
    suspend fun deletePackagesByWorkspace(workspaceId: String)

    @Query("DELETE FROM lesson_progress WHERE workspace_id = :workspaceId")
    suspend fun deleteProgressByWorkspace(workspaceId: String)

    @Query("DELETE FROM questions WHERE workspace_id = :workspaceId")
    suspend fun deleteQuestionsByWorkspace(workspaceId: String)

    @Query("DELETE FROM assessments WHERE workspace_id = :workspaceId")
    suspend fun deleteAssessmentsByWorkspace(workspaceId: String)

    @Query("DELETE FROM assessment_attempts WHERE workspace_id = :workspaceId")
    suspend fun deleteAttemptsByWorkspace(workspaceId: String)

    @Query("DELETE FROM teacher_notes WHERE workspace_id = :workspaceId")
    suspend fun deleteNotesByWorkspace(workspaceId: String)

    @Transaction
    suspend fun deleteWorkspaceGraph(workspaceId: String) {
        deleteWorkspaceById(workspaceId)
        deleteBooksByWorkspace(workspaceId)
        deleteLessonsByWorkspace(workspaceId)
        deleteDraftsByWorkspace(workspaceId)
        deletePackagesByWorkspace(workspaceId)
        deleteProgressByWorkspace(workspaceId)
        deleteQuestionsByWorkspace(workspaceId)
        deleteAssessmentsByWorkspace(workspaceId)
        deleteAttemptsByWorkspace(workspaceId)
        deleteNotesByWorkspace(workspaceId)
        deleteGenerationRunsByWorkspace(workspaceId)
        deleteQuestionAttemptsByWorkspace(workspaceId)
    }
}

@Database(
    entities = [
        WorkspaceEntity::class,
        BookEntity::class,
        LessonEntity::class,
        WorkspaceIndexEntity::class,
        WorkspaceUnitEntity::class,
        DraftEntity::class,
        InteractivePackageEntity::class,
        GenerationRunEntity::class,
        LessonProgressEntity::class,
        StudyDayEntity::class,
        QuestionEntity::class,
        QuestionAttemptEntity::class,
        AssessmentEntity::class,
        AssessmentAttemptEntity::class,
        TeacherNoteEntity::class,
        PromptTemplateEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class EduMiniDatabase : RoomDatabase() {
    abstract fun eduMiniDao(): EduMiniDao

    companion object {
        @Volatile
        private var instance: EduMiniDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN source_pages TEXT")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN source_hash TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS interactive_packages (
                        id TEXT NOT NULL PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        grade_key TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        unit_key TEXT NOT NULL,
                        lesson_key TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        root_dir TEXT NOT NULL,
                        entry_file TEXT NOT NULL,
                        source_pages TEXT,
                        version INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        published_for_students INTEGER NOT NULL,
                        report_json TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_packages_lesson
                    ON interactive_packages (workspace_id, book_key, unit_key, lesson_key, version)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS generation_runs (
                        id TEXT NOT NULL PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        lesson_key TEXT NOT NULL,
                        task TEXT NOT NULL,
                        provider_label TEXT NOT NULL,
                        model TEXT NOT NULL,
                        status TEXT NOT NULL,
                        error_summary TEXT,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_runs_created
                    ON generation_runs (created_at)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lesson_progress (
                        workspace_id TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        unit_key TEXT NOT NULL,
                        lesson_key TEXT NOT NULL,
                        completed INTEGER NOT NULL DEFAULT 0,
                        points_earned INTEGER NOT NULL DEFAULT 0,
                        completed_at TEXT,
                        PRIMARY KEY (workspace_id, book_key, unit_key, lesson_key)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_days (
                        activity_date TEXT NOT NULL PRIMARY KEY,
                        actions_count INTEGER NOT NULL DEFAULT 1,
                        last_updated_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS questions (
                        id TEXT NOT NULL PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        grade_key TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        unit_key TEXT NOT NULL,
                        lesson_key TEXT NOT NULL,
                        lesson_title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        options_json TEXT NOT NULL,
                        correct_answer TEXT NOT NULL,
                        rationale TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        source_pages TEXT,
                        fingerprint TEXT NOT NULL,
                        reviewed INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_questions_workspace_lesson
                    ON questions (workspace_id, book_key, unit_key, lesson_key)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assessments (
                        id TEXT NOT NULL PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        question_ids_json TEXT NOT NULL,
                        time_limit_minutes INTEGER NOT NULL,
                        pass_score_percent INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_assessments_ws ON assessments (workspace_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assessment_attempts (
                        id TEXT NOT NULL PRIMARY KEY,
                        assessment_id TEXT NOT NULL,
                        assessment_title TEXT NOT NULL,
                        workspace_id TEXT NOT NULL,
                        correct_count INTEGER NOT NULL,
                        total_questions INTEGER NOT NULL,
                        score_percent INTEGER NOT NULL,
                        passed INTEGER NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        earned_xp INTEGER NOT NULL,
                        completed_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_attempts_ws ON assessment_attempts (workspace_id, completed_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS teacher_notes (
                        workspace_id TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        unit_key TEXT NOT NULL,
                        lesson_key TEXT NOT NULL,
                        note_text TEXT NOT NULL,
                        bookmarked INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY (workspace_id, book_key, unit_key, lesson_key)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS prompt_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        task TEXT NOT NULL,
                        instructions TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN sourcePdfPath TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN source_text_path TEXT")
                db.execSQL("ALTER TABLE lessons ADD COLUMN interactive_path TEXT")
                db.execSQL("ALTER TABLE generation_runs ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE generation_runs ADD COLUMN tokens_used INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN concept_tag TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS question_attempts (
                        id TEXT NOT NULL PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        question_id TEXT NOT NULL,
                        concept_tag TEXT NOT NULL,
                        selected_answer TEXT NOT NULL,
                        is_correct INTEGER NOT NULL,
                        source_mode TEXT NOT NULL DEFAULT 'lesson_quiz',
                        answered_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_q_attempts_ws_question
                    ON question_attempts (workspace_id, question_id, answered_at)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_q_attempts_ws_concept
                    ON question_attempts (workspace_id, concept_tag)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE question_attempts ADD COLUMN user_id TEXT NOT NULL DEFAULT 'learner_default'")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_q_attempts_ws_user
                    ON question_attempts (workspace_id, user_id, answered_at)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_indexes (
                        workspace_id TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        index_uri TEXT NOT NULL,
                        index_relative_path TEXT NOT NULL,
                        manifest_uri TEXT,
                        manifest_relative_path TEXT,
                        index_sha256 TEXT NOT NULL,
                        manifest_sha256 TEXT,
                        index_schema_version TEXT,
                        manifest_version TEXT,
                        last_synced_at INTEGER NOT NULL,
                        PRIMARY KEY(workspace_id, book_key)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_units (
                        workspace_id TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        unit_key TEXT NOT NULL,
                        title TEXT NOT NULL,
                        part_key TEXT,
                        sort_order INTEGER NOT NULL,
                        source_index_path TEXT NOT NULL,
                        PRIMARY KEY(workspace_id, book_key, unit_key)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_workspace_units_part
                    ON workspace_units (workspace_id, book_key, part_key)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN subjectName TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN printed_page_range TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workspace_indexes ADD COLUMN source_sha256 TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep the oldest row for each stable workspace/book identity.
                db.execSQL(
                    """
                    DELETE FROM books
                    WHERE rowId NOT IN (
                        SELECT MIN(rowId)
                        FROM books
                        GROUP BY workspaceId, bookKey
                    )
                    """.trimIndent()
                )
                // A lesson is identified within its workspace, book and unit.
                db.execSQL(
                    """
                    DELETE FROM lessons
                    WHERE rowId NOT IN (
                        SELECT MIN(rowId)
                        FROM lessons
                        GROUP BY workspaceId, bookKey, unitKey, lessonKey
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_books_workspaceId_bookKey
                    ON books (workspaceId, bookKey)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_lessons_workspaceId_bookKey_unitKey_lessonKey
                    ON lessons (workspaceId, bookKey, unitKey, lessonKey)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve legacy learning history under the default local learner.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS lesson_progress_new (
                        workspace_id TEXT NOT NULL,
                        book_key TEXT NOT NULL,
                        unit_key TEXT NOT NULL,
                        lesson_key TEXT NOT NULL,
                        completed INTEGER NOT NULL,
                        points_earned INTEGER NOT NULL,
                        completed_at TEXT,
                        user_id TEXT NOT NULL DEFAULT 'learner_default',
                        PRIMARY KEY(workspace_id, book_key, unit_key, lesson_key, user_id)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO lesson_progress_new
                    (workspace_id, book_key, unit_key, lesson_key, completed, points_earned, completed_at, user_id)
                    SELECT workspace_id, book_key, unit_key, lesson_key, completed, points_earned, completed_at, 'learner_default'
                    FROM lesson_progress
                """.trimIndent())
                db.execSQL("DROP TABLE lesson_progress")
                db.execSQL("ALTER TABLE lesson_progress_new RENAME TO lesson_progress")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS study_days_new (
                        activity_date TEXT NOT NULL,
                        actions_count INTEGER NOT NULL,
                        last_updated_at TEXT NOT NULL,
                        user_id TEXT NOT NULL DEFAULT 'learner_default',
                        PRIMARY KEY(activity_date, user_id)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO study_days_new (activity_date, actions_count, last_updated_at, user_id)
                    SELECT activity_date, actions_count, last_updated_at, 'learner_default' FROM study_days
                """.trimIndent())
                db.execSQL("DROP TABLE study_days")
                db.execSQL("ALTER TABLE study_days_new RENAME TO study_days")
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_lesson_progress_ws_user
                    ON lesson_progress (workspace_id, user_id)
                """.trimIndent())
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_study_days_user_date
                    ON study_days (user_id, activity_date)
                """.trimIndent())

                db.execSQL("ALTER TABLE assessment_attempts ADD COLUMN user_id TEXT NOT NULL DEFAULT 'learner_default'")
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_attempts_ws_user
                    ON assessment_attempts (workspace_id, user_id, completed_at)
                """.trimIndent())
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prompt_templates ADD COLUMN response_schema_json TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE prompt_templates ADD COLUMN response_mapping_json TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE prompt_templates ADD COLUMN response_format TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE prompt_templates ADD COLUMN source_attachment_mode TEXT NOT NULL DEFAULT 'pdf_native'")
                db.execSQL("ALTER TABLE prompt_templates ADD COLUMN version INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN template_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN template_version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN response_schema_hash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN request_fingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE generation_drafts ADD COLUMN source_pdf_hash TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context) {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EduMiniDatabase::class.java,
                    "edu-mini-local.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
