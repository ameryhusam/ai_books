package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentStudyPresentationPolicyTest {

    @Test
    fun availablePdfOpensPdfTab() {
        assertEquals(0, initialStudentStudyTab(hasPdf = true))
    }

    @Test
    fun missingPdfOpensInteractiveTab() {
        assertEquals(1, initialStudentStudyTab(hasPdf = false))
    }

}
