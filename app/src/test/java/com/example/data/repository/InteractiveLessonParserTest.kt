package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveLessonParserTest {
    private val valid = """
        {
          "schemaVersion": 1,
          "title": "الكسور",
          "objective": "فهم الكسر",
          "introduction": "نبدأ بتقسيم الكل إلى أجزاء متساوية.",
          "sections": [
            {
              "title": "مفهوم الكسر",
              "conceptTag": "الكسر",
              "explanation": "الكسر يمثل جزءاً من كل.",
              "visualType": "diagram",
              "visualDescription": "دائرة مقسمة إلى أربعة أجزاء متساوية.",
              "workedExample": "إذا أخذنا جزءاً واحداً من أربعة فالكسر هو ١/٤.",
              "checkpoint": {
                "question": "كم جزءاً يمثل ١/٤؟",
                "options": ["جزء واحد", "جزآن"],
                "answer": "جزء واحد"
              }
            }
          ],
          "keyPoints": ["المقام يحدد عدد الأجزاء المتساوية."],
          "summary": "الكسر يصف جزءاً من كل.",
          "bookQuestionIds": ["book-q-1"]
        }
    """.trimIndent()

    @Test
    fun parsesCompleteLessonAndFormatsStudyPreview() {
        val lesson = InteractiveLessonParser.parseStrictJson(valid, setOf("book-q-1"))
        assertNotNull(lesson)
        assertEquals("الكسور", lesson?.title)
        assertEquals(1, lesson?.sections?.size)
        assertEquals(listOf("book-q-1"), lesson?.bookQuestionIds)
        assertEquals("جزء واحد", lesson?.sections?.single()?.checkpointAnswer)
        val preview = InteractiveLessonParser.formatForPreview(valid)
        assertTrue(preview.contains("أهم نقاط المذاكرة"))
        assertTrue(preview.contains("ملخص الدرس"))
    }

    @Test
    fun rejectsQuestionIdsOutsideApprovedBookQuestionSet() {
        assertNull(InteractiveLessonParser.parseStrictJson(valid, setOf("another-question")))
    }

    @Test
    fun rejectsIncompleteSectionsAndInvalidCheckpointAnswers() {
        val incomplete = valid.replace("\"explanation\": \"الكسر يمثل جزءاً من كل.\",", "\"explanation\": \"\",")
        assertNull(InteractiveLessonParser.parseStrictJson(incomplete, setOf("book-q-1")))
        val invalidCheckpoint = valid.replace("\"answer\": \"جزء واحد\"", "\"answer\": \"خيار غير موجود\"")
        assertNull(InteractiveLessonParser.parseStrictJson(invalidCheckpoint, setOf("book-q-1")))
    }
}