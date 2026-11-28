package com.example.data.repository

import com.example.model.LessonRef
import com.example.model.QuestionSourceType
import com.example.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceQuestionExtractionTest {
    private val lesson = LessonRef(
        grade = "G06", rootPath = "/workspace", bookKey = "MATH",
        bookTitle = "الرياضيات", subjectKey = "MATH", unitKey = "unit_01",
        unitTitle = "الجبر", lessonKey = "lesson_01", title = "المعادلات", workspaceId = "ws-1"
    )

    @Test
    fun preservesVerbatimQuestionAndOptionsAndDoesNotInventAnswerOrPage() {
        val raw = """
            {
              "questions": [
                {
                  "type": "mcq",
                  "prompt": "  ما ناتج ٢ + ٣ ؟  ",
                  "options": [" ٤ ", "٥", "٦"]
                }
              ]
            }
        """.trimIndent()
        val question = QuestionBankEngine.parseSourceQuestions(raw, "ws-1", lesson).single()
        assertEquals("  ما ناتج ٢ + ٣ ؟  ", question.prompt)
        assertEquals(listOf(" ٤ ", "٥", "٦"), question.options)
        assertEquals("", question.correctAnswer)
        assertNull(question.sourcePages)
        assertEquals(QuestionType.MCQ, question.type)
        assertEquals(QuestionSourceType.BOOK_EXTRACTED, question.sourceType)
        assertFalse(question.reviewed)
    }

    @Test
    fun keepsExplicitSourcePageAndAnswerExactlyAsProvided() {
        val raw = """
            {
              "questions": [
                {
                  "type": "short_answer",
                  "prompt": "عرّف المتغير.",
                  "correctAnswer": "رمز يمثل قيمة مجهولة.",
                  "sourcePages": "17"
                }
              ]
            }
        """.trimIndent()
        val question = QuestionBankEngine.parseSourceQuestions(raw, "ws-1", lesson).single()
        assertEquals("عرّف المتغير.", question.prompt)
        assertEquals("رمز يمثل قيمة مجهولة.", question.correctAnswer)
        assertEquals("17", question.sourcePages)
        assertEquals(QuestionSourceType.BOOK_EXTRACTED, question.sourceType)
    }

    @Test
    fun rejectsUnknownFieldsAndDuplicateSourcePrompts() {
        val unknown = """{"questions":[{"prompt":"سؤال؟","invented":"value"}]}"""
        assertThrows(IllegalArgumentException::class.java) { QuestionBankEngine.parseSourceQuestions(unknown, "ws-1", lesson) }
        val duplicate = """{"questions":[{"prompt":"سؤال؟"},{"prompt":"سؤال؟"}]}"""
        assertThrows(IllegalArgumentException::class.java) { QuestionBankEngine.parseSourceQuestions(duplicate, "ws-1", lesson) }
    }
}