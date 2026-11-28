package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentStudyPresentationPolicyTest {

    @Test
    fun pdfKeepsPdfAsInitialStudyTabEvenWhenInteractiveContentExists() {
        assertEquals(0, initialStudentStudyTab(hasPdf = true, hasInteractive = true))
    }

    @Test
    fun missingPdfStartsPublishedInteractiveLessonDirectly() {
        assertEquals(1, initialStudentStudyTab(hasPdf = false, hasInteractive = true))
    }

    @Test
    fun missingPdfAndInteractiveContentKeepsUnavailableSourceTab() {
        assertEquals(0, initialStudentStudyTab(hasPdf = false, hasInteractive = false))
    }
}
