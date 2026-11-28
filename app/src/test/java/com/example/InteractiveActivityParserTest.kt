package com.example

import com.example.data.repository.InteractiveActivityParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractiveActivityParserTest {

    @Test
    fun strictParserRejectsNumberedExplanatoryText() {
        val prose = """
            ## مقدمة الدرس
            1. تعريف المفهوم
            2. أمثلة تطبيقية
        """.trimIndent()

        assertNull(InteractiveActivityParser.parseStrictJson(prose))
    }

    @Test
    fun strictParserPreservesActivityTypeAndBookProvenance() {
        val json = """
            {
              "title": "أنشطة الدرس",
              "steps": [{
                "title": "تجربة",
                "body": "نفذ الخطوات مع المعلم.",
                "activityType": "experiment",
                "sourceType": "BOOK_EXTRACTED",
                "sourcePages": "15"
              }]
            }
        """.trimIndent()
        val activity = InteractiveActivityParser.parseStrictJson(json)
        assertNotNull(activity)
        assertEquals("experiment", activity?.steps?.single()?.activityType)
        assertEquals("BOOK_EXTRACTED", activity?.steps?.single()?.sourceType)
        assertEquals("15", activity?.steps?.single()?.sourcePages)
    }

    @Test
    fun strictParserAcceptsStructuredActivityJson() {
        val json = """
            {
              "title": "نشاط مراجعة",
              "steps": [
                {
                  "title": "المحطة الأولى",
                  "body": "اقرأ السؤال",
                  "checkpoint": {
                    "question": "ما ناتج 2 + 2؟",
                    "options": ["3", "4"],
                    "answer": "4"
                  }
                }
              ]
            }
        """.trimIndent()

        assertNotNull(InteractiveActivityParser.parseStrictJson(json))
    }
}
