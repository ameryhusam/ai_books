package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiGenerationService
import com.example.data.local.EduMiniDatabase
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.security.ProviderStorage
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.LessonRef
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LessonSourceTextRepositoryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun repository(appContext: Context? = context): EduMiniRepository =
        EduMiniRepository(
            dao = EduMiniDatabase.getInstance(context).eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context),
            appContext = appContext
        )

    private fun lesson(
        sourceTextPath: String? = null,
        pdfPath: String? = null
    ) = LessonRef(
        grade = "G04",
        rootPath = "",
        bookKey = "SCI",
        bookTitle = "العلوم",
        subjectKey = "SCI",
        unitKey = "unit_01",
        unitTitle = "الوحدة الأولى",
        lessonKey = "lesson_01",
        title = "الدرس الأول",
        sourceTextPath = sourceTextPath,
        pdfPath = pdfPath
    )

    private fun tempDirectory(): File =
        Files.createTempDirectory("lesson-source-test").toFile()

    @Test
    fun sourceTextPathHasPriorityOverSiblingFilesAndIsTrimmed() = runBlocking {
        val dir = tempDirectory()
        try {
            val source = File(dir, "lesson.txt").apply { writeText("  النص الأساسي  ", Charsets.UTF_8) }
            File(dir, "lesson.pdf").writeBytes(byteArrayOf(1, 2, 3))
            File(dir, "lesson.md").writeText("نص Markdown البديل", Charsets.UTF_8)

            assertEquals(
                "النص الأساسي",
                repository().readLessonSourceText(lesson(sourceTextPath = source.absolutePath, pdfPath = File(dir, "lesson.pdf").absolutePath))
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blankSourceTextFallsBackToTxtSibling() = runBlocking {
        val dir = tempDirectory()
        try {
            val source = File(dir, "empty.txt").apply { writeText("   ", Charsets.UTF_8) }
            val pdf = File(dir, "lesson.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            File(dir, "lesson.txt").writeText("  نص TXT  ", Charsets.UTF_8)

            assertEquals("نص TXT", repository().readLessonSourceText(lesson(source.absolutePath, pdf.absolutePath)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun txtSiblingIsPreferredWhenBothTxtAndMarkdownExist() = runBlocking {
        val dir = tempDirectory()
        try {
            val pdf = File(dir, "lesson.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            File(dir, "lesson.txt").writeText("نص TXT", Charsets.UTF_8)
            File(dir, "lesson.md").writeText("نص Markdown", Charsets.UTF_8)

            assertEquals("نص TXT", repository().readLessonSourceText(lesson(pdfPath = pdf.absolutePath)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun markdownSiblingIsUsedWhenTxtDoesNotExist() = runBlocking {
        val dir = tempDirectory()
        try {
            val pdf = File(dir, "lesson.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            File(dir, "lesson.md").writeText("  نص Markdown  ", Charsets.UTF_8)

            assertEquals("نص Markdown", repository().readLessonSourceText(lesson(pdfPath = pdf.absolutePath)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingSourcesAndContentUriPdfReturnNull() = runBlocking {
        val dir = tempDirectory()
        try {
            val pdf = File(dir, "missing.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            assertNull(repository().readLessonSourceText(lesson(pdfPath = pdf.absolutePath)))
            assertNull(repository().readLessonSourceText(lesson(pdfPath = "content://provider/document/book.pdf")))
            assertNull(repository(appContext = null).readLessonSourceText(lesson(pdfPath = pdf.absolutePath)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
