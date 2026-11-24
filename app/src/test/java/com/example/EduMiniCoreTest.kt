package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiGenerationService
import com.example.data.workspace.WorkspaceImporter
import com.example.model.LessonRef
import com.example.model.TaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EduMiniCoreTest {

    @Test
    fun importerParsesCanonicalBookIndexAndIgnoresPartDerivedIndex() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)

        val zipBytes = createTestZip(
            mapOf(
                "workspace/G04/ARAB/book_manifest.json" to """{"title":"لغتي العربية"}""",
                "workspace/G04/ARAB/index.json" to """
                    {
                      "units": [
                        {
                          "unitKey": "unit_01",
                          "title": "الوحدة الأولى",
                          "partKey": "P01",
                          "lessons": [
                            {
                              "lessonKey": "lesson_01",
                              "title": "الدرس الأول",
                              "pdfPageStart": 5,
                              "pdfPageEnd": 10
                            },
                            {
                              "lessonKey": "lesson_02",
                              "title": "الدرس الثاني"
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
                "workspace/G04/ARAB/P01/index.json" to """{"units":[]}""",
                "workspace/G04/ARAB/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 sample"
            )
        )

        val imported = importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        assertEquals("G04", imported.gradeKey)
        assertEquals(1, imported.books.size)
        assertEquals("لغتي العربية", imported.books.first().title)
        assertEquals(2, imported.lessons.size)
        assertNotNull(imported.lessons[0].pdfPath)
        assertEquals("5–10", imported.lessons[0].pageRange)
        assertNull(imported.lessons[1].pdfPath)
    }

    @Test
    fun importerRejectsZipSlipPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)

        val zipBytes = createTestZip(
            mapOf(
                "workspace/G04/../../evil.txt" to "bad"
            )
        )

        var failed = false
        try {
            importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        } catch (e: IllegalArgumentException) {
            failed = e.message?.contains("غير آمن") == true
        }
        assertTrue(failed)
    }

    @Test
    fun promptBuilderIncludesLessonMetadataAndTask() {
        val service = AiGenerationService()
        val lesson = LessonRef(
            grade = "G04",
            rootPath = "/tmp/ws",
            bookKey = "ARAB",
            bookTitle = "لغتي العربية",
            subjectKey = "ARAB",
            unitKey = "unit_01",
            unitTitle = "الوحدة الأولى",
            lessonKey = "lesson_01",
            title = "الدرس الأول"
        )
        val prompt = service.buildPrompt(
            lesson = lesson,
            task = TaskType.QUESTIONS,
            source = "نص تجريبي",
            extra = "5 أسئلة"
        )
        assertTrue(prompt.contains("بنك أسئلة"))
        assertTrue(prompt.contains("G04"))
        assertTrue(prompt.contains("الدرس الأول"))
        assertTrue(prompt.contains("نص تجريبي"))
        assertTrue(prompt.contains("5 أسئلة"))
    }

    private fun createTestZip(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
