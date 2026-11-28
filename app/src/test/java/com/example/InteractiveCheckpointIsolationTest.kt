package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EduMiniDatabase
import com.example.data.local.QuestionEntity
import com.example.data.repository.QuestionBankService
import com.example.model.LessonRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteractiveCheckpointIsolationTest {

    @Test
    fun checkpointWithSamePromptAsOfficialQuestionGetsItsOwnHistoryRow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.eduMiniDao()
            dao.insertQuestions(
                listOf(
                    QuestionEntity(
                        id = "q-official",
                        workspaceId = "ws",
                        gradeKey = "G04",
                        bookKey = "ARAB",
                        unitKey = "unit_01",
                        lessonKey = "lesson_01",
                        lessonTitle = "الدرس",
                        type = "mcq",
                        prompt = "ما ناتج 2 + 2؟",
                        optionsJson = """["3","4"]""",
                        correctAnswer = "4",
                        rationale = "",
                        difficulty = "medium",
                        sourcePages = null,
                        fingerprint = "",
                        reviewed = true,
                        createdAt = "2026-01-01T00:00:00Z",
                        sourceType = "AI_GENERATED"
                    )
                )
            )
            val lesson = LessonRef(
                grade = "G04",
                rootPath = "",
                bookKey = "ARAB",
                bookTitle = "اللغة العربية",
                subjectKey = "ARAB",
                unitKey = "unit_01",
                unitTitle = "الوحدة الأولى",
                lessonKey = "lesson_01",
                title = "الدرس",
                pageRange = null,
                workspaceId = "ws"
            )

            val attempt = QuestionBankService(dao).recordInteractiveCheckpointResultForUser(
                workspaceId = "ws",
                userId = "learner-1",
                lesson = lesson,
                prompt = "ما ناتج 2 + 2؟",
                conceptTag = "الجمع",
                selectedAnswer = "4",
                correctAnswer = "4",
                isCorrect = true,
                options = listOf("3", "4"),
                sourceMode = "interactive_preview"
            )

            val questions = dao.observeQuestionsForWorkspace("ws").first()
            assertEquals(2, questions.size)
            assertEquals("q-official", questions.single { it.id == "q-official" }.id)
            assertTrue(attempt!!.questionId.startsWith("q-int-"))
            assertEquals("INTERACTIVE_CHECKPOINT", questions.single { it.id == attempt.questionId }.sourceType)
        } finally {
            db.close()
        }
    }
}
