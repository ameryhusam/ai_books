package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DraftEntity
import com.example.data.local.EduMiniDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationDraftTaskIsolationTest {

    @Test
    fun draftVersionsAreIndependentForEachTask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val base = DraftEntity(
                id = "draft-questions-v3",
                workspaceId = "ws",
                gradeKey = "G04",
                bookKey = "ARAB",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                task = "questions",
                title = "الدرس",
                provider = "test",
                model = "test",
                createdAt = "2026-01-01T00:00:00Z",
                content = "questions",
                status = "DRAFT",
                version = 3
            )
            db.eduMiniDao().insertDraft(base)
            db.eduMiniDao().insertDraft(
                base.copy(
                    id = "draft-summary-v1",
                    task = "summary",
                    content = "summary",
                    version = 1
                )
            )

            val questionsVersion = db.eduMiniDao().getMaxDraftVersion(
                workspaceId = "ws",
                legacyRootPath = "",
                bookKey = "ARAB",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                task = "questions"
            )
            val summaryVersion = db.eduMiniDao().getMaxDraftVersion(
                workspaceId = "ws",
                legacyRootPath = "",
                bookKey = "ARAB",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                task = "summary"
            )

            assertEquals(3, questionsVersion)
            assertEquals(1, summaryVersion)
        } finally {
            db.close()
        }
    }
}
