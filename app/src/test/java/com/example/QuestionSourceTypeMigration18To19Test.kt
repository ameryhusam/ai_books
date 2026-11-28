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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionSourceTypeMigration18To19Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "question-source-type-migration-18-19-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesLegacyQuestionsAsUnknownAndAddsDefaultForNewRows() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE questions (
                            id TEXT NOT NULL PRIMARY KEY,
                            prompt TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO questions (id, prompt) VALUES ('legacy-q1', 'Old question')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)

        try {
            val db = helper.writableDatabase
            EduMiniDatabase.MIGRATION_18_19.migrate(db)

            fun sourceType(id: String): String = db.query(
                "SELECT source_type FROM questions WHERE id = '$id'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }

            assertEquals("LEGACY_UNKNOWN", sourceType("legacy-q1"))
            db.execSQL("INSERT INTO questions (id, prompt) VALUES ('new-q1', 'New question')")
            assertEquals("LEGACY_UNKNOWN", sourceType("new-q1"))
        } finally {
            helper.close()
        }
    }
}
