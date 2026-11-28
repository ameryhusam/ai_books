package com.example

import com.example.data.repository.InteractiveActivityParser
import org.junit.Assert.assertNotNull
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
