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
    val lessonsCount: Int
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
}

@Database(
    entities = [
        WorkspaceEntity::class,
        BookEntity::class,
        LessonEntity::class,
        DraftEntity::class,
        InteractivePackageEntity::class,
        GenerationRunEntity::class
    ],
    version = 2,
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

        fun getInstance(context: Context): EduMiniDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EduMiniDatabase::class.java,
                    "edu-mini-local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
