package com.example.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIndexMigration17To18Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "book-index-migration-17-18-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationAddsIndexFieldsAndPreservesLegacyRows() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE books (
                            rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            workspaceId TEXT NOT NULL,
                            bookKey TEXT NOT NULL,
                            title TEXT NOT NULL,
                            subjectKey TEXT NOT NULL,
                            componentKey TEXT,
                            relativePath TEXT NOT NULL,
                            lessonsCount INTEGER NOT NULL,
                            coverPath TEXT,
                            sourcePdfPath TEXT,
                            subjectName TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE workspace_units (
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
                        CREATE TABLE lessons (
                            rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            workspaceId TEXT NOT NULL,
                            grade TEXT NOT NULL,
                            rootPath TEXT NOT NULL,
                            bookKey TEXT NOT NULL,
                            bookTitle TEXT NOT NULL,
                            subjectKey TEXT NOT NULL,
                            componentKey TEXT,
                            unitKey TEXT NOT NULL,
                            unitTitle TEXT NOT NULL,
                            lessonKey TEXT NOT NULL,
                            title TEXT NOT NULL,
                            partKey TEXT,
                            pageRange TEXT,
                            pdfPath TEXT,
                            source_text_path TEXT,
                            interactive_path TEXT,
                            printed_page_range TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO books
                        (rowId, workspaceId, bookKey, title, subjectKey, relativePath, lessonsCount, subjectName)
                        VALUES (7, 'ws-legacy', 'SCI-G07', 'Science', 'SCI', 'G07/SCI', 1, 'العلوم')
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO workspace_units
                        (workspace_id, book_key, unit_key, title, part_key, sort_order, source_index_path)
                        VALUES ('ws-legacy', 'SCI-G07', 'unit_01', 'الوحدة الأولى', 'P01', 0, 'index.json')
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO lessons
                        (rowId, workspaceId, grade, rootPath, bookKey, bookTitle, subjectKey,
                         unitKey, unitTitle, lessonKey, title, partKey, pageRange, printed_page_range)
                        VALUES (9, 'ws-legacy', 'G07', '/legacy', 'SCI-G07', 'Science', 'SCI',
                                'unit_01', 'الوحدة الأولى', 'lesson_01', 'الدرس الأول', 'P01',
                                '5-9', '1-5')
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)

        try {
            val db = helper.writableDatabase
            EduMiniDatabase.MIGRATION_17_18.migrate(db)

            fun scalar(sql: String): Int = db.query(sql).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

            fun string(sql: String): String = db.query(sql).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }

            assertEquals(1, scalar("SELECT COUNT(*) FROM books WHERE rowId = 7 AND title = 'Science'"))
            assertEquals("LEGACY_UNVERIFIED", string("SELECT approvalStatus FROM books WHERE rowId = 7"))
            assertEquals(0, scalar("SELECT offset FROM books WHERE rowId = 7"))
            assertEquals(1, scalar("SELECT requiresReview FROM books WHERE rowId = 7"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM workspace_units WHERE unit_key = 'unit_01'"))
            assertEquals("LEGACY_UNVERIFIED", string("SELECT assignment_source FROM workspace_units WHERE unit_key = 'unit_01'"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM lessons WHERE rowId = 9 AND pageRange = '5-9' AND printed_page_range = '1-5'"))
            assertEquals(0, scalar("SELECT sort_order FROM lessons WHERE rowId = 9"))
            assertEquals(0, scalar("SELECT COUNT(*) FROM lessons WHERE rowId = 9 AND pdf_page_start IS NOT NULL"))
            assertEquals(
                1,
                scalar(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_lessons_workspace_book_unit_sort'"
                )
            )
        } finally {
            helper.close()
        }
    }
}
