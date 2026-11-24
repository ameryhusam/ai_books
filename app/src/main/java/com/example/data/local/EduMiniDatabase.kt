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
    @ColumnInfo(name = "status") val status: String = "DRAFT"
)

@Dao
interface EduMiniDao {
    @Query("SELECT * FROM workspaces ORDER BY importedAt DESC LIMIT 1")
    fun observeLatestWorkspace(): Flow<WorkspaceEntity?>

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
        WHERE workspace_id = :workspaceId
          AND book_key = :bookKey
          AND unit_key = :unitKey
          AND lesson_key = :lessonKey
        ORDER BY created_at DESC
        """
    )
    fun observeDraftsForLesson(
        workspaceId: String,
        bookKey: String,
        unitKey: String,
        lessonKey: String
    ): Flow<List<DraftEntity>>

    @Query("DELETE FROM generation_drafts WHERE id = :draftId")
    suspend fun deleteDraftById(draftId: String)
}

@Database(
    entities = [
        WorkspaceEntity::class,
        BookEntity::class,
        LessonEntity::class,
        DraftEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EduMiniDatabase : RoomDatabase() {
    abstract fun eduMiniDao(): EduMiniDao

    companion object {
        @Volatile
        private var instance: EduMiniDatabase? = null

        fun getInstance(context: Context): EduMiniDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EduMiniDatabase::class.java,
                    "edu-mini-local.db"
                ).build().also { instance = it }
            }
        }
    }
}
