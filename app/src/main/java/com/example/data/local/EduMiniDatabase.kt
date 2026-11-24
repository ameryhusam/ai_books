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
    indices = [Index(value = ["workspaceId"])]
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
    val sourcePdfPath: String? = null
)

@Entity(
    tableName = "lessons",
    indices = [Index(value = ["workspaceId", "bookKey"])]
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
    val pdfPath: String?
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
    @ColumnInfo(name = "source_hash") val sourceHash: String? = null
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
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(
    tableName = "lesson_progress",
    primaryKeys = ["workspace_id", "book_key", "unit_key", "lesson_key"]
)
data class LessonProgressEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "lesson_key") val lessonKey: String,
    @ColumnInfo(name = "completed") val completed: Boolean = false,
    @ColumnInfo(name = "points_earned") val pointsEarned: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: String? = null
)

@Entity(tableName = "study_days")
data class StudyDayEntity(
    @PrimaryKey @ColumnInfo(name = "activity_date") val activityDate: String,
    @ColumnInfo(name = "actions_count") val actionsCount: Int = 1,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: String
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
    @ColumnInfo(name = "created_at") val createdAt: String
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
    indices = [Index(name = "idx_attempts_ws", value = ["workspace_id", "completed_at"])]
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
    @ColumnInfo(name = "completed_at") val completedAt: String
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
    @ColumnInfo(name = "created_at") val createdAt: String
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

    @Query("SELECT * FROM lesson_progress WHERE workspace_id = :workspaceId")
    fun observeProgressForWorkspace(workspaceId: String): Flow<List<LessonProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonProgress(progress: LessonProgressEntity)

    @Query("SELECT * FROM study_days ORDER BY activity_date DESC")
    fun observeStudyDays(): Flow<List<StudyDayEntity>>

    @Query("SELECT * FROM study_days WHERE activity_date = :dateIso LIMIT 1")
    suspend fun getStudyDay(dateIso: String): StudyDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyDay(studyDay: StudyDayEntity)

    @Query("SELECT * FROM questions WHERE workspace_id = :workspaceId ORDER BY created_at DESC")
    fun observeQuestionsForWorkspace(workspaceId: String): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("UPDATE questions SET reviewed = :reviewed WHERE id = :questionId")
    suspend fun updateQuestionReviewed(questionId: String, reviewed: Boolean)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestionById(questionId: String)

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

    @Query("SELECT * FROM assessment_attempts WHERE workspace_id = :workspaceId ORDER BY completed_at DESC LIMIT 30")
    fun observeAssessmentAttempts(workspaceId: String): Flow<List<AssessmentAttemptEntity>>

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
    }
}

@Database(
    entities = [
        WorkspaceEntity::class,
        BookEntity::class,
        LessonEntity::class,
        DraftEntity::class,
        InteractivePackageEntity::class,
        GenerationRunEntity::class,
        LessonProgressEntity::class,
        StudyDayEntity::class,
        QuestionEntity::class,
        AssessmentEntity::class,
        AssessmentAttemptEntity::class,
        TeacherNoteEntity::class,
        PromptTemplateEntity::class
    ],
    version = 7,
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

        fun getInstance(context: Context): EduMiniDatabase {
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
                        MIGRATION_6_7
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
