package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.ai.AiGenerationService
import com.example.data.ai.AiExecutionException
import com.example.data.ai.AiExecutionFailureKind
import com.example.data.ai.BookIndexPromptRegistry
import com.example.data.ai.TemplateResponseAdapter
import com.example.data.ai.TemplateOutputValidator
import com.example.data.ai.QuestionResponseContract
import com.example.data.ai.PromptPrivacySanitizer
import com.example.data.local.DraftEntity
import com.example.data.local.BookEntity
import com.example.data.local.LessonEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceIndexEntity
import com.example.data.local.WorkspaceUnitEntity
import com.example.data.local.GenerationRunEntity
import com.example.data.local.AssessmentAttemptEntity
import com.example.data.local.EduMiniDatabase
import com.example.data.local.InteractivePackageEntity
import com.example.data.local.LessonProgressEntity
import com.example.data.local.StudyDayEntity
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.ConceptMasteryEngine
import com.example.data.repository.EduMiniRepository
import com.example.data.repository.InteractiveActivityParser
import com.example.data.repository.LearningProgressAnalytics
import com.example.data.repository.LessonSequenceGate
import com.example.data.repository.MilestoneBadgeEngine
import com.example.data.repository.QuestionBankEngine
import com.example.data.repository.QuestionBankService
import com.example.data.security.ProviderStorage
import com.example.data.workspace.DirectWorkspaceBinder
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceExporter
import com.example.data.workspace.WorkspaceImporter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.example.model.AchievementBadge
import com.example.model.BadgeCategory
import com.example.model.DashboardSummary
import com.example.model.DraftStatus
import com.example.model.GeneratedResult
import com.example.model.AssessmentKind
import com.example.model.AssessmentAttemptRecord
import com.example.model.BookRef
import com.example.model.BookProgressSummary
import com.example.model.ImportedWorkspace
import com.example.model.LearningNodeStatus
import com.example.model.LearnerProfile
import com.example.model.LessonRef
import com.example.model.MasteryStatus
import com.example.model.PackageKind
import com.example.model.PdfBookInfo
import com.example.model.PromptTemplateRecord
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionContentPayload
import com.example.model.QuestionRequiredField
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.SecuritySeverity
import com.example.model.TaskType
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.ProviderApiKeyNames
import com.example.model.UnitCompletionStatus
import com.example.ui.components.LearningProgressCalculator
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EduMiniCoreTest {

    @org.junit.Before
    fun registerTestAndroidKeyStore() {
        ensureTestAndroidKeyStore()
    }

    @Test
    fun importerParsesMergedCanonicalIndexValidatesDerivedHashAndIgnoresPartView() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)
        val indexRaw = """
            {
              "gradeKey": "G04",
              "subjectKey": "ARAB",
              "partKey": "BOTH",
              "sourceLayout": "MERGED_PARTS_FILE",
              "sourceReviewStatus": "VERIFIED",
              "approvalStatus": "APPROVED",
              "sourceMetadata": {"sourceRequiresReview": false, "partEvidence": "PDF discovery found both part sections in the source."},
              "units": [{
                "unitKey": "unit_01",
                "unitNumber": 1,
                "title": "الوحدة الأولى",
                "partKey": "P01",
                "assignmentStatus": "assigned",
                "assignmentSource": "INDEX_EVIDENCE",
                "assignmentEvidence": "Unit section explicitly appears under P01 in the merged source TOC.",
                "lessons": [
                  {
                    "lessonKey": "lesson_01",
                    "lessonNumber": 1,
                    "title": "الدرس الأول",
                    "pdfPageStart": 5,
                    "pdfPageEnd": 10,
                    "sliceFile": {"workspaceUri":"workspace/G04/ARAB/P01/units/unit_01/lesson_01.pdf"}
                  },
                  {
                    "lessonKey": "lesson_02",
                    "lessonNumber": 2,
                    "title": "الدرس الثاني",
                    "pdfPageStart": 11,
                    "pdfPageEnd": 14,
                    "sliceFile": {"workspaceUri":"workspace/G04/ARAB/P01/units/unit_01/lesson_02.pdf"}
                  }
                ]
              }]
            }
        """.trimIndent()
        val partIndexRaw = """
            {
              "gradeKey": "G04",
              "subjectKey": "ARAB",
              "partKey": "P01",
              "sourceLayout": "MERGED_PARTS_FILE",
              "units": [{
                "unitKey": "unit_01",
                "unitNumber": 1,
                "title": "الوحدة الأولى",
                "partKey": "P01",
                "assignmentStatus": "assigned",
                "lessons": []
              }]
            }
        """.trimIndent()
        fun hash(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val indexHash = hash(indexRaw)
        val partIndexHash = hash(partIndexRaw)
        val manifestRaw = """
            {
              "manifestVersion": "edu.book_manifest.v2",
              "title": "لغتي العربية",
              "gradeKey": "G04",
              "subjectKey": "ARAB",
              "partKey": "BOTH",
              "sourceLayout": "MERGED_PARTS_FILE",
              "sourceReviewStatus": "VERIFIED",
              "sourceSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
              "canonicalIndexPath": "index.json",
              "canonicalIndexSha256": "$indexHash",
              "indexSha256": "$indexHash",
              "partIndexes": [{"path":"P01/index.json","sha256":"$partIndexHash"}]
            }
        """.trimIndent()
        val zipBytes = createTestZip(
            mapOf(
                "workspace/G04/ARAB/book_manifest.json" to manifestRaw,
                "workspace/G04/ARAB/index.json" to indexRaw,
                "workspace/G04/ARAB/P01/index.json" to partIndexRaw,
                "workspace/G04/ARAB/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 sample",
                "workspace/G04/ARAB/P01/units/unit_01/lesson_02.pdf" to "%PDF-1.4 sample two"
            )
        )

        val imported = importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        assertEquals("G04", imported.gradeKey)
        assertEquals(1, imported.books.size)
        assertEquals("لغتي العربية", imported.books.single().title)
        assertEquals(2, imported.lessons.size)
        assertNotNull(imported.lessons[0].pdfPath)
        assertEquals("5–10", imported.lessons[0].pageRange)
        assertNotNull(imported.lessons[1].pdfPath)
        assertEquals("11–14", imported.lessons[1].pageRange)
        assertEquals(1, imported.indexRecords.size)
        assertEquals(indexHash, imported.indexRecords.single().indexSha256)
        assertEquals(1, imported.units.size)
    }

    @Test
    fun importerRejectsCanonicalV2BookWithMissingLessonPdf() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)
        val indexRaw = """
            {
              "gradeKey": "G04",
              "subjectKey": "ARAB",
              "partKey": "BOTH",
              "sourceLayout": "MERGED_PARTS_FILE",
              "sourceReviewStatus": "VERIFIED",
              "approvalStatus": "APPROVED",
              "sourceMetadata": {"sourceRequiresReview": false, "partEvidence": "Both parts found in PDF."},
              "units": [{
                "unitKey": "unit_01",
                "unitNumber": 1,
                "title": "الوحدة الأولى",
                "partKey": "P01",
                "assignmentStatus": "assigned",
                "assignmentSource": "INDEX_EVIDENCE",
                "assignmentEvidence": "TOC evidence",
                "lessons": [
                  {
                    "lessonKey": "lesson_01",
                    "lessonNumber": 1,
                    "title": "الدرس الأول",
                    "pdfPageStart": 5,
                    "pdfPageEnd": 10,
                    "sliceFile": {"workspaceUri":"workspace/G04/ARAB/P01/units/unit_01/lesson_01.pdf"}
                  },
                  {
                    "lessonKey": "lesson_02",
                    "lessonNumber": 2,
                    "title": "الدرس الثاني",
                    "pdfPageStart": 11,
                    "pdfPageEnd": 14,
                    "sliceFile": {"workspaceUri":"workspace/G04/ARAB/P01/units/unit_01/lesson_02.pdf"}
                  }
                ]
              }]
            }
        """.trimIndent()
        val indexHash = MessageDigest.getInstance("SHA-256")
            .digest(indexRaw.toByteArray()).joinToString("") { "%02x".format(it) }
        val manifestRaw = """
            {
              "manifestVersion": "edu.book_manifest.v2",
              "title": "لغتي العربية",
              "gradeKey": "G04",
              "subjectKey": "ARAB",
              "partKey": "BOTH",
              "sourceLayout": "MERGED_PARTS_FILE",
              "sourceReviewStatus": "VERIFIED",
              "sourceSha256": "${"c".repeat(64)}",
              "canonicalIndexPath": "index.json",
              "canonicalIndexSha256": "$indexHash",
              "indexSha256": "$indexHash",
              "partIndexes": []
            }
        """.trimIndent()
        val zipBytes = createTestZip(
            mapOf(
                "workspace/G04/ARAB/book_manifest.json" to manifestRaw,
                "workspace/G04/ARAB/index.json" to indexRaw,
                "workspace/G04/ARAB/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 sample"
            )
        )
        val failure = runCatching {
            importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure?.message?.contains("lesson_02") == true || failure?.message?.contains("PDF") == true)
    }

    @Test
    fun unresolvedUnitProposalIsNotPromotedFromLessonPath() {
        val unit = Json.parseToJsonElement(
            """{"partKey":"unknown","proposedPartKey":"P01","assignmentStatus":"need_review","lessons":[{"sliceFile":{"workspaceUri":"workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf"}}]}"""
        ).jsonObject
        val resolved = WorkspaceContractValidator.resolveUnitPartKey(unit)
        assertEquals("UNKNOWN", resolved)
    }

    @Test
    fun workspaceContractValidatorMarksUnverifiedSourceForReview() {
        val index = """
            {
              "gradeKey": "G04",
              "subjectKey": "SCI",
              "partKey": "BOTH",
              "approvalStatus": "APPROVED",
              "sourceLayout": "MERGED_PARTS_FILE",
              "units": [{
                "unitNumber": 1,
                "title": "الوحدة الأولى",
                "partKey": "P01",
                "assignmentStatus": "assigned",
                "lessons": [{
                  "lessonNumber": 1,
                  "title": "الدرس الأول",
                  "pdfPageStart": 2,
                  "pdfPageEnd": 5,
                  "sliceFile": {"workspaceUri": "workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf"}
                }]
              }]
            }
        """.trimIndent()
        val indexHash = MessageDigest.getInstance("SHA-256").digest(index.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val manifest = """
            {
              "manifestVersion": "edu.book_manifest.v1",
              "gradeKey": "G04",
              "subjectKey": "SCI",
              "partKey": "BOTH",
              "sourceLayout": "MERGED_PARTS_FILE",
              "sourceReviewStatus": "NEEDS_REVIEW",
              "sourceSha256": "${"a".repeat(64)}",
              "indexSha256": "$indexHash"
            }
        """.trimIndent()
        val result = com.example.data.workspace.WorkspaceContractValidator.validate(
            indexRaw = index,
            manifestRaw = manifest,
            expectedGrade = "G04",
            expectedSubject = "SCI",
            bookRelativePath = "workspace/G04/SCI",
            availableRelativePaths = setOf("workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf")
        )
        assertFalse(result.isValid)
        assertTrue(result.isImportableWithReview)
        assertTrue(result.reviewRequiredErrors.any { it.contains("NEEDS_REVIEW") || it.contains("غير معتمد للمراجعة النهائية") })
    }

    @Test
    fun workspaceContractValidatorMarksMissingUnitAssignmentProvenanceForReview() {
        val index = """
            {
              "gradeKey": "G04",
              "subjectKey": "SCI",
              "partKey": "P01",
              "sourceLayout": "SINGLE_PART_FILE",
              "sourceReviewStatus": "VERIFIED",
              "approvalStatus": "APPROVED",
              "sourceMetadata": {"sourceRequiresReview": false, "partEvidence": "Reviewer confirmed standalone P01."},
              "units": [{
                "unitNumber": 1,
                "title": "الوحدة الأولى",
                "partKey": "P01",
                "assignmentStatus": "assigned",
                "lessons": [{
                  "lessonNumber": 1,
                  "title": "الدرس الأول",
                  "pdfPageStart": 1,
                  "pdfPageEnd": 2,
                  "sliceFile": {"workspaceUri":"workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf"}
                }]
              }]
            }
        """.trimIndent()
        val indexHash = MessageDigest.getInstance("SHA-256").digest(index.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val manifest = """
            {
              "manifestVersion": "edu.book_manifest.v2",
              "gradeKey": "G04",
              "subjectKey": "SCI",
              "partKey": "P01",
              "sourceLayout": "SINGLE_PART_FILE",
              "sourceReviewStatus": "VERIFIED",
              "sourceSha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
              "canonicalIndexPath": "P01/index.json",
              "canonicalIndexSha256": "$indexHash",
              "indexSha256": "$indexHash",
              "partIndexes": []
            }
        """.trimIndent()

        val result = com.example.data.workspace.WorkspaceContractValidator.validate(
            indexRaw = index,
            manifestRaw = manifest,
            expectedGrade = "G04",
            expectedSubject = "SCI",
            bookRelativePath = "workspace/G04/SCI/P01",
            availableRelativePaths = setOf(
                "workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf"
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.isImportableWithReview)
        assertTrue(result.reviewRequiredErrors.any { it.contains("assignmentSource") })
        assertTrue(result.reviewRequiredErrors.any { it.contains("assignmentEvidence") })
    }

    @Test
    fun workspaceContractValidatorUsesSourceNumbersInsteadOfArrayPosition() {
        val unit = Json.parseToJsonElement("""{"unitNumber":7,"title":"الوحدة"}""").jsonObject
        val lesson = Json.parseToJsonElement("""{"lessonNumber":12,"title":"درس"}""").jsonObject
        assertEquals("unit_07", com.example.data.workspace.WorkspaceContractValidator.sourceUnitKey(unit))
        assertEquals("lesson_12", com.example.data.workspace.WorkspaceContractValidator.sourceLessonKey(lesson))

        val p01Unit = Json.parseToJsonElement("""{"unitNumber":1,"partKey":"P01"}""").jsonObject
        val p02Unit = Json.parseToJsonElement("""{"unitNumber":1,"partKey":"P02"}""").jsonObject
        assertEquals("P01_unit_01", com.example.data.workspace.WorkspaceContractValidator.sourceUnitKey(p01Unit))
        assertEquals("P02_unit_01", com.example.data.workspace.WorkspaceContractValidator.sourceUnitKey(p02Unit))
        assertEquals(
            "unit_01",
            com.example.data.workspace.WorkspaceContractValidator.sourceUnitFolderKey(p01Unit)
        )
        val explicitLesson = Json.parseToJsonElement("""{"lessonKey":"stable_lesson_alpha","lessonNumber":4}""").jsonObject
        assertEquals(4, com.example.data.workspace.WorkspaceContractValidator.sourceLessonNumber(explicitLesson))
    }

    @Test
    fun importerSupportsIndependentPartSourceDirectory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)
        val zipBytes = createTestZip(
            mapOf(
                "workspace/G07/ENG/COURSE/source-key-P01/book_manifest.json" to """{"title":"English Part One"}""",
                "workspace/G07/ENG/COURSE/source-key-P01/index.json" to """
                    {
                      "units": [{
                        "unitKey": "unit_01",
                        "title": "Unit One",
                        "partKey": "P01",
                        "lessons": [{"lessonKey":"lesson_01","title":"Lesson One"}]
                      }]
                    }
                """.trimIndent(),
                "workspace/G07/ENG/COURSE/source-key-P01/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 sample"
            )
        )
        val imported = importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        assertEquals("G07", imported.gradeKey)
        assertEquals(1, imported.books.size)
        assertEquals("ENG", imported.books.first().subjectKey)
        assertEquals("COURSE", imported.books.first().componentKey)
        assertEquals("ENG/COURSE/source-key-P01", imported.books.first().bookKey)
    }

    @Test
    fun importerAcceptsStandalonePartLocalCanonicalIndexAndUsesExplicitLessonUri() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)
        val indexRaw = """
            {
              "gradeKey": "G07",
              "subjectKey": "ENG",
              "componentKey": "COURSE",
              "gradeName": "الصف السابع",
              "subjectName": "اللغة الإنجليزية",
              "displayName": "اللغة الإنجليزية — الجزء الأول",
              "title": "Student Book",
              "partKey": "P01",
              "sourceLayout": "SINGLE_PART_FILE",
              "sourceReviewStatus": "VERIFIED",
              "approvalStatus": "APPROVED",
              "sourceMetadata": {"sourceRequiresReview": false, "partEvidence": "Reviewer confirmed the PDF is a standalone P01 source."},
              "units": [{
                "unitNumber": 1,
                "unitKey": "unit_01",
                "title": "Unit One",
                "partKey": "P01",
                "assignmentStatus": "assigned",
                "assignmentSource": "INDEX_EVIDENCE",
                "assignmentEvidence": "Standalone P01 source",
                "lessons": [{
                  "lessonNumber": 1,
                  "lessonKey": "lesson_01",
                  "title": "Lesson One",
                  "pdfPageStart": 3,
                  "pdfPageEnd": 7,
                  "sliceFile": {
                    "workspaceUri": "workspace/G07/ENG/COURSE/P01/units/unit_01/lesson_01.pdf"
                  }
                }]
              }]
            }
        """.trimIndent()
        val indexHash = MessageDigest.getInstance("SHA-256").digest(indexRaw.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val manifestRaw = """
            {
              "manifestVersion": "edu.book_manifest.v2",
              "gradeKey": "G07",
              "subjectKey": "ENG",
              "componentKey": "COURSE",
              "partKey": "P01",
              "sourceLayout": "SINGLE_PART_FILE",
              "sourceReviewStatus": "VERIFIED",
              "sourceSha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
              "canonicalIndexPath": "P01/index.json",
              "canonicalIndexSha256": "$indexHash",
              "indexSha256": "$indexHash",
              "partIndexes": []
            }
        """.trimIndent()
        val zipBytes = createTestZip(
            mapOf(
                "workspace/G07/ENG/COURSE/P01/index.json" to indexRaw,
                "workspace/G07/ENG/COURSE/P01/book_manifest.json" to manifestRaw,
                "workspace/G07/ENG/COURSE/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 sample"
            )
        )

        val imported = importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())

        assertEquals("G07", imported.gradeKey)
        assertEquals(1, imported.books.size)
        assertEquals("ENG", imported.books.single().subjectKey)
        assertEquals("COURSE", imported.books.single().componentKey)
        assertEquals("ENG/COURSE|P01", imported.books.single().bookKey)
        assertEquals("اللغة الإنجليزية — الجزء الأول", imported.books.single().title)
        assertEquals("اللغة الإنجليزية", imported.books.single().subjectName)
        assertTrue(imported.books.single().relativePath.endsWith("/P01"))
        assertEquals(1, imported.lessons.size)
        assertNotNull(imported.lessons.single().pdfPath)
        assertEquals("3–7", imported.lessons.single().pageRange)
    }

    @Test
    fun explicitWorkspaceUriPreventsLessonPathReconstructionFallback() {
        val lesson = Json.parseToJsonElement(
            """{"lessonNumber":1,"title":"Lesson","sliceFile":{"workspaceUri":"workspace/G07/ENG/COURSE/P01/custom/unit-a/lesson-source.pdf"}}"""
        ).jsonObject
        val unit = Json.parseToJsonElement(
            """{"unitNumber":1,"partKey":"P01","title":"Unit"}"""
        ).jsonObject

        val candidates = com.example.data.workspace.WorkspaceContractValidator.lessonPdfCandidates(
            lesson = lesson,
            unit = unit,
            lessonPart = "P01",
            bookRelativePath = "workspace/G07/ENG/COURSE/P01",
            expectedGrade = "G07",
            expectedSubject = "ENG"
        )

        assertTrue(candidates.contains("workspace/G07/ENG/COURSE/P01/custom/unit-a/lesson-source.pdf"))
        assertTrue(candidates.contains("P01/custom/unit-a/lesson-source.pdf"))
        assertTrue(candidates.contains("custom/unit-a/lesson-source.pdf"))
        assertFalse(candidates.any { it.endsWith("P01/units/unit_01/lesson_01.pdf") })

        val unsafeLesson = Json.parseToJsonElement(
            """{"lessonNumber":1,"sliceFile":{"workspaceUri":"/storage/emulated/0/lesson.pdf"}}"""
        ).jsonObject
        val unsafeCandidates = com.example.data.workspace.WorkspaceContractValidator.lessonPdfCandidates(
            lesson = unsafeLesson,
            unit = unit,
            lessonPart = "P01",
            bookRelativePath = "workspace/G07/ENG/COURSE/P01",
            expectedGrade = "G07",
            expectedSubject = "ENG"
        )
        assertTrue(unsafeCandidates.isEmpty())
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
    fun promptBuilderIncludesLessonMetadataTaskAndQuestionCount() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = AiGenerationService(context)
        val lesson = sampleLesson()
        val prompt = service.buildPrompt(
            lesson = lesson,
            task = TaskType.QUESTIONS,
            source = "نص تجريبي",
            extra = "أسئلة اختيار من متعدد",
            questionCount = 12,
            attachedPdfPagesCount = 2
        )
        assertTrue(prompt.contains("12"))
        assertTrue(prompt.contains("G04"))
        assertTrue(prompt.contains("الدرس الأول"))
        assertTrue(prompt.contains("نص تجريبي"))
        assertTrue(prompt.contains("ملف PDF الأصلي للدرس مرفق"))
        assertTrue(prompt.contains("التزم بالمصدر ولا تخترع معلومات أو صفحات."))
    }

    @Test
    fun questionPromptIncludesSelectedTypesDistributionLanguageDifficultyAndTemplateSchema() {
        val service = AiGenerationService()
        val schema = """{"type":"OBJECT","properties":{"questions":{"type":"ARRAY"}}}"""
        val prompt = service.buildPrompt(
            lesson = sampleLesson(),
            task = TaskType.QUESTIONS,
            source = "مصدر الدرس",
            extra = "",
            questionCount = 5,
            questionTypeIds = listOf("mcq", "true_false", "short_answer"),
            questionLanguage = "en",
            difficultyHint = "hard",
            responseSchemaJson = schema,
            responseFormat = "json"
        )

        assertTrue(prompt.contains("العدد الإجمالي المطلوب من الأسئلة: 5"))
        assertTrue(prompt.contains("mcq=2"))
        assertTrue(prompt.contains("true_false=2"))
        assertTrue(prompt.contains("short_answer=1"))
        assertTrue(prompt.contains("English"))
        assertTrue(prompt.contains("hard"))
        assertTrue(prompt.contains(schema))
    }

    @Test
    fun questionPromptRejectsMoreSelectedTypesThanRequestedQuestions() {
        val service = AiGenerationService()
        val failed = runCatching {
            service.buildPrompt(
                lesson = sampleLesson(),
                task = TaskType.QUESTIONS,
                source = "مصدر الدرس",
                extra = "",
                questionCount = 2,
                questionTypeIds = listOf("mcq", "true_false", "short_answer")
            )
        }.isFailure

        assertTrue(failed)
    }

    @Test
    fun canonicalQuestionSchemaValidatesAndExposesOnlyPersistedQuestionContentFields() {
        val schema = Json.parseToJsonElement(QuestionResponseContract.schemaJson).jsonObject
        assertTrue(TemplateOutputValidator.validateSchema(QuestionResponseContract.schemaJson).isEmpty())
        val itemProperties = schema["properties"]!!.jsonObject["questions"]!!
            .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject
        val payloadDescriptor = QuestionContentPayload.serializer().descriptor
        val payloadFields = (0 until payloadDescriptor.elementsCount)
            .map { payloadDescriptor.getElementName(it) }.toSet()
        assertEquals(payloadFields, itemProperties.keys)
        val requiredFields = (0 until payloadDescriptor.elementsCount)
            .filter { index -> payloadDescriptor.getElementAnnotations(index).any { it is QuestionRequiredField } }
            .map { payloadDescriptor.getElementName(it) }.toSet()
        val schemaRequiredFields = schema["properties"]!!.jsonObject["questions"]!!
            .jsonObject["items"]!!.jsonObject["required"]!!.jsonArray
            .map { it.jsonPrimitive.content }.toSet()
        assertEquals(requiredFields, schemaRequiredFields)
        assertEquals(
            QuestionType.entries.map { it.id }.toSet(),
            itemProperties["type"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        )
        val selectedSchema = Json.parseToJsonElement(
            QuestionResponseContract.schemaFor(listOf("mcq", "short_answer"))
        ).jsonObject
        val selectedTypeIds = selectedSchema["properties"]!!.jsonObject["questions"]!!
            .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject["type"]!!
            .jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("mcq", "short_answer"), selectedTypeIds)
        val selectedDifficultySchema = Json.parseToJsonElement(
            QuestionResponseContract.schemaFor(listOf("mcq"), "hard")
        ).jsonObject
        val allowedDifficulties = selectedDifficultySchema["properties"]!!.jsonObject["questions"]!!
            .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject["difficulty"]!!
            .jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("hard"), allowedDifficulties)
    }

    @Test
    fun strictGeneratedQuestionParserEnforcesExactCountAndTypeDistribution() {
        val raw = """{"questions":[
            {"type":"mcq","prompt":"سؤال اختيار 1؟","options":["أ","ب"],"correctAnswer":"أ","difficulty":"easy"},
            {"type":"mcq","prompt":"سؤال اختيار 2؟","options":["ج","د"],"correctAnswer":"ج","difficulty":"medium"},
            {"type":"true_false","prompt":"عبارة صحيحة؟","options":["صح","خطأ"],"correctAnswer":"صح","difficulty":"easy"},
            {"type":"true_false","prompt":"عبارة خاطئة؟","options":["صح","خطأ"],"correctAnswer":"خطأ","difficulty":"hard"},
            {"type":"short_answer","prompt":"ما المفهوم؟","options":[],"correctAnswer":"المفهوم","difficulty":"medium"}
        ]}"""
        assertTrue(TemplateOutputValidator.validateOutput(raw, QuestionResponseContract.schemaJson).isEmpty())
        val parsed = QuestionBankEngine.parseGeneratedQuestions(
            rawText = raw,
            workspaceId = "ws-test",
            lesson = sampleLesson(),
            expectedCount = 5,
            selectedTypeIds = listOf("mcq", "true_false", "short_answer")
        )
        assertEquals(5, parsed.size)
        assertEquals(mapOf("mcq" to 2, "true_false" to 2, "short_answer" to 1), parsed.groupingBy { it.type.id }.eachCount())
    }

    @Test
    fun savedGenerationPromptRetainsExpectedQuestionBatchForLaterDraftApproval() {
        val prompt = """
            العدد الإجمالي المطلوب من الأسئلة: 5، وليس العدد لكل نوع.
            التوزيع المطلوب للعدد الإجمالي حسب النوع: mcq=2, true_false=2, short_answer=1. التزم بالأعداد المحددة.
            مستوى الصعوبة المطلوب (قيمة difficulty في JSON): hard. استخدم هذه القيمة المعيارية كما هي.
        """.trimIndent()
        val expected = QuestionResponseContract.expectedBatchFromPrompt(prompt)
        assertNotNull(expected)
        assertEquals(5, expected?.count)
        assertEquals(listOf("mcq", "true_false", "short_answer"), expected?.typeIds)
        assertEquals("hard", expected?.difficultyId)
    }

    @Test
    fun canonicalQuestionPayloadMapsToRoomAndRejectsDuplicateBatchWithoutPartialWrites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val lesson = sampleLesson()
            val raw = """{"questions":[{"type":"mcq","conceptTag":"الجمع","prompt":"ما ناتج 2 + 2؟","options":["3","4"],"correctAnswer":"4","rationale":"نجمع العددين.","difficulty":"easy","sourcePages":"5"}]}"""
            val parsed = QuestionBankEngine.parseGeneratedQuestions(
                raw, "ws-contract", lesson, 1, listOf("mcq")
            )
            val service = QuestionBankService(db.eduMiniDao())
            val saved = service.saveQuestionsWithConceptTags(
                workspaceId = "ws-contract",
                lesson = lesson,
                parsedQuestions = parsed,
                requireAllNew = true
            )
            assertEquals(1, saved.size)
            val stored = db.eduMiniDao().observeQuestionsForWorkspace("ws-contract").first().single()
            assertEquals("mcq", stored.type)
            assertEquals("ما ناتج 2 + 2؟", stored.prompt)
            assertEquals("4", stored.correctAnswer)
            assertEquals("easy", stored.difficulty)
            assertEquals(listOf("3", "4"), Json.decodeFromString<List<String>>(stored.optionsJson))
            assertEquals("الجمع", stored.conceptTag)

            val duplicateResult = service.saveQuestionsWithConceptTags(
                workspaceId = "ws-contract",
                lesson = lesson,
                parsedQuestions = parsed,
                requireAllNew = true
            )
            assertTrue(duplicateResult.isEmpty())
            val partialDuplicateFailure = runCatching {
                service.saveQuestionsWithConceptTags(
                    workspaceId = "ws-contract",
                    lesson = lesson,
                    parsedQuestions = listOf(
                        parsed.single(),
                        parsed.single().copy(
                            id = "q-new",
                            prompt = "سؤال جديد لا يكرر السابق؟",
                            fingerprint = QuestionBankEngine.computeFingerprint("سؤال جديد لا يكرر السابق؟")
                        )
                    ),
                    requireAllNew = true
                )
            }.isFailure
            assertTrue(partialDuplicateFailure)
            assertEquals(1, db.eduMiniDao().observeQuestionsForWorkspace("ws-contract").first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun strictGeneratedQuestionParserRejectsCountAndTypeMixMismatch() {
        val raw = """{"questions":[
            {"type":"mcq","prompt":"سؤال 1؟","options":["أ","ب"],"correctAnswer":"أ","difficulty":"easy"},
            {"type":"mcq","prompt":"سؤال 2؟","options":["ج","د"],"correctAnswer":"ج","difficulty":"medium"},
            {"type":"mcq","prompt":"سؤال 3؟","options":["هـ","و"],"correctAnswer":"هـ","difficulty":"hard"}
        ]}"""
        val countFailure = runCatching {
            QuestionBankEngine.parseGeneratedQuestions(raw, "ws-test", sampleLesson(), 2, listOf("mcq"))
        }.isFailure
        val typeFailure = runCatching {
            QuestionBankEngine.parseGeneratedQuestions(raw, "ws-test", sampleLesson(), 3, listOf("mcq", "true_false"))
        }.isFailure
        val difficultyFailure = runCatching {
            QuestionBankEngine.parseGeneratedQuestions(raw, "ws-test", sampleLesson(), 3, listOf("mcq"), "easy")
        }.isFailure
        assertTrue(countFailure)
        assertTrue(typeFailure)
        assertTrue(difficultyFailure)
    }

    @Test
    fun pdfBookSplitterAutoSplitsAndCreatesWorkspaceWithLessonPdfs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val splitter = PdfBookSplitter(context)

        val pdfFile = File(context.cacheDir, "sample_book_part1.pdf")
        createSamplePdf(pdfFile, pageCount = 6)

        val info = PdfBookInfo(
            fileName = "sample_book_part1.pdf",
            storedPdfPath = pdfFile.absolutePath,
            totalPages = 6,
            sha256 = "abcdef1234567890"
        )
        val splits = splitter.generateAutoSplits(totalPages = 6, pagesPerLesson = 3)
        assertEquals(2, splits.size)
        assertEquals(1, splits[0].startPage)
        assertEquals(3, splits[0].endPage)
        assertEquals(4, splits[1].startPage)
        assertEquals(6, splits[1].endPage)

        val workspace = splitter.buildWorkspaceFromSplits(
            pdfInfo = info,
            gradeKey = "G05",
            subjectKey = "SCI",
            bookTitle = "العلوم",
            splits = splits
        )
        assertEquals("G05", workspace.gradeKey)
        assertEquals(1, workspace.books.size)
        assertEquals(2, workspace.lessons.size)
        assertEquals("1–3", workspace.lessons[0].pageRange)
        assertEquals("4–6", workspace.lessons[1].pageRange)
        val indexFile = File(workspace.rootPath, "workspace/G05/SCI/index.json")
        val manifestFile = File(workspace.rootPath, "workspace/G05/SCI/book_manifest.json")
        assertTrue(indexFile.exists())
        assertTrue(manifestFile.exists())
        assertTrue(indexFile.readText().contains("edu.book_index.draft.v1"))
        assertTrue(indexFile.readText().contains("assignmentStatus"))
        assertTrue(indexFile.readText().contains("need_review"))
        assertTrue(indexFile.readText().contains("lesson_01"))
        assertTrue(manifestFile.readText().contains("العلوم"))
        assertTrue(File(workspace.books.first().sourcePdfPath!!).isFile)
        val extractedText = File(workspace.rootPath, "workspace/G05/SCI/P01/units/unit_01/lesson_01.txt")
        assertTrue(extractedText.isFile)
        assertTrue(extractedText.readText().contains("Sample textbook lesson page"))
    }

    @Test
    fun interactivePackageImporterAcceptsZipWith3dGlbAndBlocksMissingStandaloneAssets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = InteractivePackageImporter(context)
        val lesson = sampleLesson()

        // 1. Standalone HTML referencing missing local GLB should produce BLOCKER
        val badHtml = """
            <html><head><title>3D Cell</title></head>
            <body><model-viewer src="models/cell.glb"></model-viewer></body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val htmlStage = importer.importFromStream(
            inputStream = ByteArrayInputStream(badHtml),
            fileName = "cell.html",
            isZip = false,
            declaredSizeBytes = badHtml.size.toLong(),
            lesson = lesson,
            sourcePages = "5–10",
            version = 1
        )
        assertEquals(PackageKind.STANDALONE_HTML, htmlStage.kind)
        assertTrue(htmlStage.report.hasBlockers)
        assertTrue(htmlStage.report.missingReferencedAssets.contains("models/cell.glb"))

        // 2. ZIP bundle containing index.html + models/cell.glb should PASS
        val goodZip = createTestZip(
            mapOf(
                "index.html" to """<html><head><title>الخلية ثلاثية الأبعاد</title></head><body><model-viewer src="models/cell.glb"></model-viewer></body></html>""",
                "models/cell.glb" to "glTF-binary-stub"
            )
        )
        val zipStage = importer.importFromStream(
            inputStream = ByteArrayInputStream(goodZip),
            fileName = "cell_3d.zip",
            isZip = true,
            declaredSizeBytes = goodZip.size.toLong(),
            lesson = lesson,
            sourcePages = "6–8",
            version = 2
        )
        assertEquals(PackageKind.ZIP_BUNDLE, zipStage.kind)
        assertFalse(zipStage.report.hasBlockers)
        assertTrue(zipStage.report.has3dAssets)
        assertTrue(zipStage.report.linkedLessonMatch)

        // 3. Dangerous eval() inside HTML triggers BLOCKER
        val unsafeHtml = """<html><body><script>eval('alert(1)')</script></body></html>""".toByteArray(Charsets.UTF_8)
        val unsafeStage = importer.importFromStream(
            inputStream = ByteArrayInputStream(unsafeHtml),
            fileName = "unsafe.html",
            isZip = false,
            declaredSizeBytes = unsafeHtml.size.toLong(),
            lesson = lesson,
            sourcePages = "5–10",
            version = 3
        )
        assertTrue(unsafeStage.report.findings.any { it.severity == SecuritySeverity.BLOCKER && it.code == "UNSAFE_DYNAMIC_EVAL" })
    }

    @Test
    fun dashboardSummaryComputesCompletionPercentagesPointsAndBadges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = EduMiniRepository(
            dao = EduMiniDatabase.getInstance(context).eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )
        val lesson1 = sampleLesson()
        val lesson2 = lesson1.copy(lessonKey = "lesson_02", title = "الدرس الثاني")
        val ws = ImportedWorkspace(
            workspaceId = "ws-test",
            gradeKey = "G04",
            rootPath = "/tmp/ws",
            title = "G04 • لغتي العربية",
            books = listOf(
                BookRef(
                    bookKey = "ARAB",
                    title = "لغتي العربية",
                    subjectKey = "ARAB",
                    relativePath = "workspace/G04/ARAB",
                    lessonsCount = 2
                )
            ),
            lessons = listOf(lesson1, lesson2),
            filesCount = 4,
            warnings = emptyList()
        )
        val drafts = listOf(
            DraftEntity(
                id = "d1",
                workspaceId = "ws-test",
                gradeKey = "G04",
                bookKey = "ARAB",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                task = "questions",
                title = "الدرس الأول",
                provider = "Gemini",
                model = "gemini-2.5-flash",
                createdAt = "2026-01-01T00:00:00Z",
                content = "أسئلة",
                status = "APPROVED"
            )
        )
        val progress = listOf(
            LessonProgressEntity(
                workspaceId = "ws-test",
                bookKey = "ARAB",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                completed = true,
                pointsEarned = 50
            )
        )

        val summary = repo.computeDashboardSummary(ws, drafts, emptyList(), progress)
        assertTrue(summary.hasWorkspace)
        assertEquals(2, summary.totalLessons)
        assertEquals(1, summary.completedLessons)
        assertEquals(50, summary.overallCompletionPercent)
        assertEquals(1, summary.unitPaths.size)
        assertEquals(LearningNodeStatus.COMPLETED, summary.unitPaths[0].nodes[0].status)
        assertEquals(LearningNodeStatus.NOT_STARTED, summary.unitPaths[0].nodes[1].status)
        // 50 (completed) + 20 (approved draft) + 40 (first lesson) + 25 (workspace) + 30 (ai author) + 50 (halfway) + 10 (25% subject) + 20 (50% subject) = 245 XP
        assertEquals(245, summary.totalPoints)
        assertEquals(2, summary.level)
        assertEquals(6, summary.unlockedBadgesCount)
        assertTrue(summary.badges.first { it.id == "badge_first_lesson" }.unlocked)
        assertFalse(summary.badges.first { it.id == "badge_5_day_streak" }.unlocked)
        assertEquals(lesson2.lessonKey, summary.nextRecommendedLesson?.lessonKey)
    }

    @Test
    fun milestoneBadgeSystemUnlocksFirstLessonFiveDayStreakAndBookCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )

        val today = LocalDate.of(2026, 11, 24)
        val fourDayDates = setOf(
            today.minusDays(3),
            today.minusDays(2),
            today.minusDays(1),
            today
        )
        val fourDayStreak = MilestoneBadgeEngine.computeStudyStreak(fourDayDates, today = today)
        assertEquals(4, fourDayStreak.currentStreakDays)
        assertEquals(4, fourDayStreak.longestStreakDays)

        val fiveDayDates = fourDayDates + today.minusDays(4)
        val fiveDayStreak = MilestoneBadgeEngine.computeStudyStreak(fiveDayDates, today = today)
        assertEquals(5, fiveDayStreak.currentStreakDays)
        assertEquals(5, fiveDayStreak.longestStreakDays)

        // Yesterday-grace rule: streak active yesterday still counts as 5-day streak today before first session
        val yesterdayFiveStreak = MilestoneBadgeEngine.computeStudyStreak(
            activityDates = (1L..5L).map { today.minusDays(it) }.toSet(),
            today = today
        )
        assertEquals(5, yesterdayFiveStreak.currentStreakDays)

        val lesson1 = sampleLesson()
        val lesson2 = lesson1.copy(lessonKey = "lesson_02", title = "الدرس الثاني")
        val ws = ImportedWorkspace(
            workspaceId = "ws-streak",
            gradeKey = "G04",
            rootPath = "/tmp/ws-streak",
            title = "G04 • لغتي العربية",
            books = listOf(
                BookRef(
                    bookKey = "ARAB",
                    title = "لغتي العربية",
                    subjectKey = "ARAB",
                    relativePath = "workspace/G04/ARAB",
                    lessonsCount = 2
                )
            ),
            lessons = listOf(lesson1, lesson2),
            filesCount = 4,
            warnings = emptyList()
        )

        val lockedSummary = repo.computeDashboardSummary(
            workspace = ws,
            drafts = emptyList(),
            packages = emptyList(),
            progressRows = emptyList(),
            studyDayStrings = fourDayDates.map { it.toString() },
            today = today
        )
        assertFalse(lockedSummary.badges.first { it.id == "badge_first_lesson" }.unlocked)
        assertFalse(lockedSummary.badges.first { it.id == "badge_5_day_streak" }.unlocked)
        assertEquals(4, lockedSummary.badges.first { it.id == "badge_5_day_streak" }.currentProgress)

        val fullProgress = listOf(
            LessonProgressEntity("ws-streak", "ARAB", "unit_01", "lesson_01", completed = true, pointsEarned = 50, completedAt = today.toString()),
            LessonProgressEntity("ws-streak", "ARAB", "unit_01", "lesson_02", completed = true, pointsEarned = 50, completedAt = today.toString())
        )
        val unlockedSummary = repo.computeDashboardSummary(
            workspace = ws,
            drafts = emptyList(),
            packages = emptyList(),
            progressRows = fullProgress,
            studyDayStrings = fiveDayDates.map { it.toString() },
            today = today
        )
        assertEquals(5, unlockedSummary.currentStreakDays)
        assertTrue(unlockedSummary.badges.first { it.id == "badge_first_lesson" }.unlocked)
        assertTrue(unlockedSummary.badges.first { it.id == "badge_5_day_streak" }.unlocked)
        assertTrue(unlockedSummary.badges.first { it.id == "badge_unit_master" }.unlocked)
        assertTrue(unlockedSummary.badges.first { it.id == "badge_book_champion" }.unlocked)

        // Verify Room study_days persistence
        repo.recordStudyActivity(today)
        assertNotNull(db.eduMiniDao().getStudyDay(today.toString()))
    }

    @Test
    fun learningProgressCalculatorComputesBoundsRoundingAndMultiBookSnapshot() {
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(0, 0))
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(0, 5))
        assertEquals(0, LearningProgressCalculator.calculateCompletionPercent(-2, 4))
        assertEquals(67, LearningProgressCalculator.calculateCompletionPercent(2, 3))
        assertEquals(100, LearningProgressCalculator.calculateCompletionPercent(4, 4))
        assertEquals(100, LearningProgressCalculator.calculateCompletionPercent(7, 4))

        val book1 = BookRef(
            bookKey = "ARAB",
            title = "لغتي العربية",
            subjectKey = "ARAB",
            relativePath = "workspace/G04/ARAB",
            lessonsCount = 3
        )
        val book2 = BookRef(
            bookKey = "SCI",
            title = "العلوم",
            subjectKey = "SCI",
            relativePath = "workspace/G04/SCI",
            lessonsCount = 2
        )
        val lessons = listOf(
            sampleLesson(),
            sampleLesson().copy(lessonKey = "lesson_02", title = "الدرس الثاني"),
            sampleLesson().copy(lessonKey = "lesson_03", title = "الدرس الثالث"),
            sampleLesson().copy(bookKey = "SCI", bookTitle = "العلوم", subjectKey = "SCI", lessonKey = "lesson_01"),
            sampleLesson().copy(bookKey = "SCI", bookTitle = "العلوم", subjectKey = "SCI", lessonKey = "lesson_02")
        )
        val completed = setOf(
            Triple("ARAB", "unit_01", "lesson_01"),
            Triple("ARAB", "unit_01", "lesson_02"),
            Triple("SCI", "unit_01", "lesson_01"),
            Triple("SCI", "unit_01", "lesson_02")
        )

        val snapshot = LearningProgressCalculator.buildSnapshot(
            books = listOf(book1, book2),
            lessons = lessons,
            completedLessonKeys = completed
        )

        assertEquals(2, snapshot.totalBooks)
        assertEquals(5, snapshot.totalLessons)
        assertEquals(4, snapshot.completedLessons)
        assertEquals(80, snapshot.overallCompletionPercent)
        assertEquals(67, snapshot.books[0].completionPercent)
        assertEquals(100, snapshot.books[1].completionPercent)
    }

    @Test
    fun questionBankEngineParsesJsonAndArabicTextDetectsDuplicatesAndGradesQuiz() {
        val lesson = sampleLesson()

        val jsonInput = """
            ```json
            [
              {
                "type": "mcq",
                "prompt": "ما عاصمة المملكة العربية السعودية؟",
                "options": ["الرياض", "جدة", "الدمام", "مكة"],
                "correctAnswer": "الرياض",
                "rationale": "الرياض هي العاصمة الإدارية والسياسية.",
                "difficulty": "easy"
              },
              {
                "type": "true_false",
                "prompt": "تدور الأرض حول الشمس مرة كل سنة.",
                "options": ["صح", "خطأ"],
                "correctAnswer": "صح",
                "difficulty": "medium"
              }
            ]
            ```
        """.trimIndent()

        val jsonQuestions = QuestionBankEngine.parseQuestionsFromText(jsonInput, "ws-test", lesson)
        assertEquals(2, jsonQuestions.size)
        assertEquals(QuestionType.MCQ, jsonQuestions[0].type)
        assertEquals("الرياض", jsonQuestions[0].correctAnswer)
        assertEquals(QuestionDifficulty.EASY, jsonQuestions[0].difficulty)
        assertEquals(QuestionType.TRUE_FALSE, jsonQuestions[1].type)

        val missingAnswer = """[{"type":"mcq","prompt":"سؤال بلا إجابة","options":["أ","ب","ج","د"]}]"""
        assertTrue(QuestionBankEngine.parseQuestionsFromText(missingAnswer, "ws-test", lesson).isEmpty())
        val missingTextAnswer = """
            1. اختر الإجابة الصحيحة؟
            أ) خيار أول
            ب) خيار ثان
        """.trimIndent()
        assertTrue(QuestionBankEngine.parseQuestionsFromText(missingTextAnswer, "ws-test", lesson).isEmpty())

        val arabicTextInput = """
            1. ما ناتج جمع 4 + 5؟
            أ) 8
            ب) 9
            ج) 10
            الإجابة: ب
            التفسير: 4 + 5 يساوي 9.
            الصعوبة: سهل

            2. ما عاصمة المملكة العربية السعودية؟!
            أ) جدة
            ب) الرياض
            الإجابة: الرياض
            الصعوبة: صعب
        """.trimIndent()

        val textQuestions = QuestionBankEngine.parseQuestionsFromText(arabicTextInput, "ws-test", lesson)
        assertEquals(2, textQuestions.size)
        assertEquals("9", textQuestions[0].correctAnswer)
        assertEquals(QuestionDifficulty.EASY, textQuestions[0].difficulty)
        assertEquals(QuestionDifficulty.HARD, textQuestions[1].difficulty)

        // Check duplicate detection across the combined list (same normalized prompt ignoring punctuation)
        val combined = QuestionBankEngine.markDuplicates(jsonQuestions + textQuestions)
        assertTrue(combined[0].isDuplicate)
        assertFalse(combined[1].isDuplicate)
        assertFalse(combined[2].isDuplicate)
        assertTrue(combined[3].isDuplicate)

        // Grade interactive quiz
        val selected = mapOf(
            jsonQuestions[0].id to "الرياض",
            jsonQuestions[1].id to "خطأ"
        )
        val summary = QuestionBankEngine.gradeQuiz(jsonQuestions, selected)
        assertEquals(2, summary.totalQuestions)
        assertEquals(1, summary.correctCount)
        assertEquals(50, summary.scorePercent)
        assertEquals(15, summary.earnedXp)
    }

    @Test
    fun repositoryQuestionBankCrudAndLegacyWorkspaceZip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )

        val lesson = sampleLesson()
        val workspaceRoot = File(context.cacheDir, "ws-qb-export").apply {
            deleteRecursively()
            mkdirs()
        }
        val bookRoot = File(workspaceRoot, "workspace/G04/ARAB").apply { mkdirs() }
        File(bookRoot, "index.json").writeText(
            """{"schemaVersion":"edu.book_index.v2","gradeKey":"G04","subjectKey":"ARAB","units":[{"unitKey":"unit_01","title":"الوحدة الأولى","partKey":"P01","assignmentStatus":"assigned","lessons":[{"lessonKey":"lesson_01","title":"الدرس الأول","pdfPageStart":1,"pdfPageEnd":2}]}]}"""
        )
        File(bookRoot, "book_manifest.json").writeText("""{"title":"لغتي العربية","sourceSha256":"test"}""")
        val ws = ImportedWorkspace(
            workspaceId = "ws-qb-export",
            gradeKey = "G04",
            rootPath = workspaceRoot.absolutePath,
            title = "G04 • لغتي العربية",
            books = listOf(
                BookRef(
                    bookKey = "ARAB",
                    title = "لغتي العربية",
                    subjectKey = "ARAB",
                    relativePath = "workspace/G04/ARAB",
                    lessonsCount = 1
                )
            ),
            lessons = listOf(lesson),
            filesCount = 2,
            warnings = emptyList()
        )
        repo.saveImportedWorkspace(ws)

        val draft = repo.persistDraft(
            workspaceId = ws.workspaceId,
            lesson = lesson,
            task = TaskType.QUESTIONS,
            providerLabel = "Gemini",
            model = "gemini-2.5-flash",
            text = "1. كم عدد أركان الإسلام؟\nأ) 4\nب) 5\nالإجابة: ب",
            title = lesson.title,
            sourceText = "نص الدرس"
        )
        assertEquals("ws-qb-export", draft.workspaceId)

        val savedFromDraft = repo.saveQuestionsFromDraftText(ws.workspaceId, lesson, draft.content)
        assertEquals(1, savedFromDraft.size)

        val manual = repo.addManualQuestion(
            workspaceId = ws.workspaceId,
            lesson = lesson,
            type = QuestionType.TRUE_FALSE,
            prompt = "الماء سائل في درجة حرارة الغرفة.",
            options = listOf("صح", "خطأ"),
            correctAnswer = "صح",
            rationale = "حقيقة علمية.",
            difficulty = QuestionDifficulty.EASY
        )

        var current = repo.observeQuestionsForWorkspace(ws.workspaceId).first()
        assertEquals(2, current.size)

        repo.toggleQuestionReviewed(savedFromDraft.first().id, false)
        current = repo.observeQuestionsForWorkspace(ws.workspaceId).first()
        assertTrue(current.first { it.id == savedFromDraft.first().id }.reviewed)

        val baos = ByteArrayOutputStream()
        val receipt = repo.exportLegacyWorkspaceZip(baos, ws)
        assertTrue(receipt.entriesCount >= 4)
        assertEquals(1, receipt.draftsCount)
        assertEquals(2, receipt.questionsCount)

        val zipNames = mutableListOf<String>()
        var manifestContent = ""
        java.util.zip.ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipNames.add(entry.name)
                if (entry.name == "manifest.json") {
                    manifestContent = zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(zipNames.contains("manifest.json"))
        assertTrue(zipNames.contains("questions/ARAB/unit_01/lesson_01/ai_generated.json"))
        assertTrue(zipNames.any { it.startsWith("edu_mini_generated/ARAB/unit_01/lesson_01/") })
        assertTrue(manifestContent.contains("sha256"))
        assertTrue(manifestContent.contains("edu-mini-indexer2-1.0"))
        assertTrue(zipNames.contains("workspace/G04/ARAB/index.json"))
        assertTrue(zipNames.contains("full_backup.json"))

        // Exercise the complete ZIP round-trip: checksum verification, canonical
        // workspace import, then restore of generated records without ID collisions.
        val workspaceImporter = WorkspaceImporter(context)
        val staged = workspaceImporter.inspectZipFromStream(ByteArrayInputStream(baos.toByteArray()))
        val imported = workspaceImporter.commitStagedGrades(staged, listOf("G04")).first()
        repo.saveImportedWorkspace(imported)
        val backupFile = File(imported.rootPath, "full_backup.json")
        assertTrue(backupFile.isFile)
        repo.importFullBackupJson(backupFile.readText(Charsets.UTF_8), imported.workspaceId)
        assertEquals(2, repo.observeQuestionsForWorkspace(imported.workspaceId).first().size)
        assertEquals(2, repo.observeQuestionsForWorkspace(ws.workspaceId).first().size)

        repo.deleteQuestion(manual.id)
        current = repo.observeQuestionsForWorkspace(ws.workspaceId).first()
        assertEquals(1, current.size)

        db.eduMiniDao().insertGenerationRun(
            GenerationRunEntity(
                id = "run-workspace-delete",
                workspaceId = ws.workspaceId,
                lessonKey = lesson.lessonKey,
                task = TaskType.QUESTIONS.id,
                providerLabel = "test",
                model = "test-model",
                status = "SUCCESS",
                errorSummary = null,
                createdAt = java.time.Instant.now().toString()
            )
        )
        assertTrue(repo.recentRuns.first().any { it.workspaceId == ws.workspaceId })
        repo.deleteWorkspace(ws.workspaceId, ws.rootPath)
        assertFalse(repo.recentRuns.first().any { it.workspaceId == ws.workspaceId })
    }

    @Test
    fun questionBankSupportsFillBlankOrderingMatchingAndAssessmentMarkdown() {
        val fillBlank = QuestionItem(
            id = "q-fb",
            workspaceId = "ws",
            gradeKey = "G04",
            bookKey = "SCI",
            unitKey = "unit_01",
            lessonKey = "lesson_01",
            lessonTitle = "الدرس الأول",
            type = QuestionType.FILL_BLANK,
            prompt = "تُسمى عملية تحول الماء إلى بخار ___.",
            options = emptyList(),
            correctAnswer = "التبخر",
            rationale = "التبخر هو تحول السائل إلى غاز.",
            difficulty = QuestionDifficulty.EASY
        )
        assertTrue(QuestionBankEngine.isAnswerCorrect(fillBlank, "التبخر!"))
        assertFalse(QuestionBankEngine.isAnswerCorrect(fillBlank, "التكثف"))

        val ordering = QuestionItem(
            id = "q-ord",
            workspaceId = "ws",
            gradeKey = "G04",
            bookKey = "SCI",
            unitKey = "unit_01",
            lessonKey = "lesson_01",
            lessonTitle = "الدرس الأول",
            type = QuestionType.ORDERING,
            prompt = "رتب مراحل دورة الماء:",
            options = listOf("التكثف", "الهطول", "التبخر"),
            correctAnswer = "التبخر | التكثف | الهطول",
            rationale = "",
            difficulty = QuestionDifficulty.MEDIUM
        )
        assertTrue(QuestionBankEngine.isAnswerCorrect(ordering, "التبخر -> التكثف -> الهطول"))
        assertFalse(QuestionBankEngine.isAnswerCorrect(ordering, "التكثف | التبخر | الهطول"))

        val matching = QuestionItem(
            id = "q-mat",
            workspaceId = "ws",
            gradeKey = "G04",
            bookKey = "SCI",
            unitKey = "unit_01",
            lessonKey = "lesson_01",
            lessonTitle = "الدرس الأول",
            type = QuestionType.MATCHING,
            prompt = "طابق الكوكب بوصفه:",
            options = listOf("المريخ -> الكوكب الأحمر", "زحل -> كوكب الحلقات"),
            correctAnswer = "المريخ=الكوكب الأحمر;زحل=كوكب الحلقات",
            rationale = "",
            difficulty = QuestionDifficulty.MEDIUM
        )
        assertTrue(QuestionBankEngine.isAnswerCorrect(matching, "زحل=كوكب الحلقات;المريخ=الكوكب الأحمر"))
        assertFalse(QuestionBankEngine.isAnswerCorrect(matching, "المريخ=كوكب الحلقات;زحل=الكوكب الأحمر"))

        val assessment = com.example.model.AssessmentRecord(
            id = "asm-1",
            workspaceId = "ws",
            title = "اختبار العلوم التكويني",
            kind = AssessmentKind.FORMATIVE,
            questionIds = listOf("q-fb", "q-ord", "q-mat"),
            timeLimitMinutes = 15,
            passScorePercent = 70,
            createdAt = "2026-11-24T00:00:00Z"
        )
        val md = QuestionBankEngine.exportAssessmentMarkdown(assessment, listOf(fillBlank, ordering, matching))
        assertTrue(md.contains("# اختبار العلوم التكويني"))
        assertTrue(md.contains("15 دقيقة"))
        assertTrue(md.contains("70%"))
        assertTrue(md.contains("التبخر"))
    }

    @Test
    fun interactiveActivityParserParsesDeclarativeJsonAndTextFallback() {
        val jsonActivity = """
            ```json
            {
              "title": "رحلة الجهاز الهضمي",
              "objective": "تتبع مسار الطعام في جسم الإنسان",
              "steps": [
                {
                  "title": "المحطة الأولى: الفم",
                  "body": "يبدأ الهضم الميكانيكي والكيميائي في الفم.",
                  "checkpoint": {
                    "question": "أين يبدأ هضم النشويات؟",
                    "options": ["الفم", "المعدة", "الأمعاء"],
                    "answer": "الفم"
                  }
                }
              ],
              "ordering": ["الفم", "المريء", "المعدة"],
              "matching": [
                {"left": "اللعاب", "right": "إنزيم الأميليز"}
              ]
            }
            ```
        """.trimIndent()

        val parsed = InteractiveActivityParser.parse(jsonActivity)
        assertNotNull(parsed)
        assertEquals("رحلة الجهاز الهضمي", parsed!!.title)
        assertEquals(1, parsed.steps.size)
        assertEquals("الفم", parsed.steps[0].checkpointAnswer)
        assertEquals(listOf("الفم", "المريء", "المعدة"), parsed.orderingItems)
        assertEquals(1, parsed.matchingPairs.size)

        val fallbackText = """
            1. التمهيد
            فكرة رئيسية حول الطاقة الشمسية.
            2. التطبيق
            استخدام الخلايا الكهروضوئية.
        """.trimIndent()
        val parsedText = InteractiveActivityParser.parse(fallbackText, "نشاط الطاقة")
        assertNotNull(parsedText)
        assertEquals("نشاط الطاقة", parsedText!!.title)
        assertEquals(2, parsedText.steps.size)
        assertEquals("التمهيد", parsedText.steps[0].title)
    }

    @Test
    fun multiGradeZipStagedPreviewInspectsGradesAndCommitsOnlySelected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)

        val zipBytes = createTestZip(
            mapOf(
                "workspace/G04/ARAB/book_manifest.json" to """{"title":"العربية 4"}""",
                "workspace/G04/ARAB/index.json" to """{"units":[{"unitKey":"u1","title":"و1","lessons":[{"lessonKey":"l1","title":"درس 4"}]}]}""",
                "workspace/G05/SCI/book_manifest.json" to """{"title":"العلوم 5"}""",
                "workspace/G05/SCI/index.json" to """{"units":[{"unitKey":"u1","title":"و1","lessons":[{"lessonKey":"l1","title":"درس 5"}]}]}"""
            )
        )

        val staged = importer.inspectZipFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        assertEquals(2, staged.grades.size)
        assertTrue(File(staged.tempDirPath).exists())

        val committed = importer.commitStagedGrades(staged, listOf("G05"))
        assertEquals(1, committed.size)
        assertEquals("G05", committed.first().gradeKey)
        assertEquals("العلوم 5", committed.first().books.first().title)
        assertFalse(File(staged.tempDirPath).exists())
    }

    @Test
    fun importerAllowsIndexHashMismatchOnlyAfterExplicitReviewConsent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)
        val indexRaw = """
            {
              "gradeKey":"G05",
              "subjectKey":"SCI",
              "partKey":"BOTH",
              "sourceLayout":"MERGED_PARTS_FILE",
              "approvalStatus":"NEEDS_REVIEW",
              "requiresReview":true,
              "units":[{
                "unitKey":"unit_01",
                "title":"الوحدة الأولى",
                "partKey":"unknown",
                "proposedPartKey":"P01",
                "assignmentStatus":"need_review",
                "assignmentSource":"DEFAULT_PROPOSAL",
                "assignmentEvidence":"تحتاج إلى مراجعة المستخدم",
                "lessons":[{
                  "lessonKey":"lesson_01",
                  "title":"الدرس الأول",
                  "pdfPageStart":1,
                  "pdfPageEnd":2,
                  "sliceFile":{"workspaceUri":"workspace/G05/SCI/P01/units/unit_01/lesson_01.pdf"}
                }]
              }]
            }
        """.trimIndent()
        val manifestRaw = """
            {
              "manifestVersion":"edu.book_manifest.v1",
              "gradeKey":"G05",
              "subjectKey":"SCI",
              "partKey":"P01",
              "sourceLayout":"MERGED_PARTS_FILE",
              "sourceReviewStatus":"VERIFIED",
              "sourceSha256":"${"a".repeat(64)}",
              "indexSha256":"${"b".repeat(64)}"
            }
        """.trimIndent()
        val zipBytes = createTestZip(
            mapOf(
                "manifest.json" to """{"artifacts":[{"path":"workspace/G05/SCI/index.json","sha256":"${"c".repeat(64)}"}]}""",
                "workspace/G05/SCI/book_manifest.json" to manifestRaw,
                "workspace/G05/SCI/index.json" to indexRaw,
                "workspace/G05/SCI/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 lesson"
            )
        )

        val preview = importer.inspectZipFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        assertTrue(preview.requiresReviewConfirmation)
        assertTrue(preview.reviewFindings.any { it.contains("SHA-256") || it.contains("approvalStatus") })

        val withoutConsent = runCatching {
            importer.commitStagedGrades(preview, listOf("G05"))
        }.exceptionOrNull()
        assertNotNull(withoutConsent)

        val imported = importer.commitStagedGrades(
            preview = preview,
            selectedGrades = listOf("G05"),
            allowReviewImport = true
        ).single()
        assertTrue(imported.books.single().requiresReview)
        assertEquals("NEEDS_REVIEW", imported.books.single().approvalStatus)
        assertEquals("UNKNOWN", imported.units.single().partKey)
        assertEquals("P01", imported.units.single().proposedPartKey)
        assertEquals("need_review", imported.units.single().assignmentStatus)
        assertTrue(imported.warnings.any { it.contains("SHA-256") || it.contains("approvalStatus") })
    }

    @Test
    fun stagedImportUpdatesExistingGradeAndPreservesWorkspaceIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val importer = WorkspaceImporter(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = importer,
            aiGenerationService = AiGenerationService(context),
            interactivePackageImporter = InteractivePackageImporter(context),
            appContext = context
        )
        try {
            val existing = repo.createClassroom("G05", "الصف الخامس")
            val zipBytes = createTestZip(
                mapOf(
                    "workspace/G05/SCI/book_manifest.json" to """{"title":"العلوم 5"}""",
                    "workspace/G05/SCI/index.json" to """{"units":[{"unitKey":"u1","title":"الوحدة الأولى","lessons":[{"lessonKey":"l1","title":"الدرس الأول"}]}]}""",
                    "workspace/G06/MATH/book_manifest.json" to """{"title":"الرياضيات 6"}""",
                    "workspace/G06/MATH/index.json" to """{"units":[{"unitKey":"u2","title":"الوحدة الثانية","lessons":[{"lessonKey":"l2","title":"الدرس الثاني"}]}]}"""
                )
            )
            val preview = importer.inspectZipFromStream(
                ByteArrayInputStream(zipBytes),
                zipBytes.size.toLong()
            )

            val imported = repo.commitStagedWorkspaces(preview, listOf("G05", "G06"))
            val savedGrades = repo.allWorkspaces.first()
            val savedG05 = savedGrades.single { it.gradeKey == "G05" }
            val savedG06 = savedGrades.single { it.gradeKey == "G06" }
            val updated = imported.single { it.gradeKey == "G05" }
            val added = imported.single { it.gradeKey == "G06" }
            val observed = repo.observeWorkspace(existing.workspaceId).first()

            assertEquals(2, imported.size)
            assertEquals(existing.workspaceId, updated.workspaceId)
            assertEquals(existing.workspaceId, savedG05.workspaceId)
            assertTrue(existing.workspaceId != savedG06.workspaceId)
            assertNotNull(observed)
            assertEquals("العلوم 5", observed!!.books.single().title)
            assertEquals(1, observed.lessons.size)
            assertEquals(existing.workspaceId, observed.lessons.single().workspaceId)
            assertTrue(File(updated.rootPath).isDirectory)
            assertTrue(File(added.rootPath).isDirectory)
        } finally {
            db.close()
        }
    }

    @Test
    fun directFolderBindingUpdatesExistingGradeFromAnotherSourceFolder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(context),
            interactivePackageImporter = InteractivePackageImporter(context),
            directWorkspaceBinder = DirectWorkspaceBinder(context),
            appContext = context
        )
        val firstRoot = File(context.cacheDir, "direct_grade_source_a_${System.currentTimeMillis()}").apply { mkdirs() }
        val secondRoot = File(context.cacheDir, "direct_grade_source_b_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val existing = repo.createClassroom("G04", "الصف الرابع")
            fun createBook(root: File, subject: String, title: String) {
                val bookDir = File(root, "workspace/G04/$subject").apply { mkdirs() }
                val indexRaw = """
                    {
                      "schemaVersion":"edu.book_index.v2",
                      "gradeKey":"G04",
                      "gradeName":"الصف الرابع",
                      "subjectKey":"$subject",
                      "partKey":"BOTH",
                      "sourceLayout":"MERGED_PARTS_FILE",
                      "sourceLayoutEvidence":"مصدر PDF موحد",
                      "approvalStatus":"APPROVED",
                      "requiresReview":false,
                      "offset":2,
                      "totalPdfPages":300,
                      "sourceFileName":"source.pdf",
                      "units":[{
                        "unitKey":"unit_01",
                        "title":"الوحدة الأولى",
                        "partKey":"P01",
                        "assignmentStatus":"assigned",
                        "assignmentSource":"INDEX_EVIDENCE",
                        "assignmentEvidence":"عنوان الوحدة في الفهرس",
                        "lessons":[{
                          "lessonKey":"lesson_01",
                          "title":"الدرس الأول",
                          "partKey":"P01",
                          "pdfPageStart":2,
                          "pdfPageEnd":4,
                          "printedPageStart":5,
                          "printedPageEnd":7,
                          "sortOrder":4,
                          "sliceFile":{"workspaceUri":"workspace/G04/$subject/units/unit_01/lesson_01.pdf"}
                        }]
                      }]
                    }
                """.trimIndent()
                val indexSha = MessageDigest.getInstance("SHA-256")
                    .digest(indexRaw.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                File(bookDir, "index.json").writeText(indexRaw)
                File(bookDir, "book_manifest.json").writeText(
                    """{"manifestVersion":"edu.book_manifest.v1","gradeKey":"G04","gradeName":"الصف الرابع","subjectKey":"$subject","partKey":"BOTH","sourceLayout":"MERGED_PARTS_FILE","sourceLayoutEvidence":"مصدر PDF موحد","sourceReviewStatus":"VERIFIED","sourceSha256":"${"c".repeat(64)}","indexSha256":"$indexSha"}"""
                )
                File(bookDir, "units/unit_01/lesson_01.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("%PDF-1.4 sample lesson content")
                }
            }
            createBook(firstRoot, "MATH", "الرياضيات - المصدر الأول")
            val first = repo.bindDirectWorkspace(firstRoot.absolutePath, "G04")
            assertEquals(existing.workspaceId, first.workspaceId)

            createBook(secondRoot, "SCI", "العلوم - المصدر الثاني")
            val second = repo.bindDirectWorkspace(secondRoot.absolutePath, "G04")
            val saved = repo.allWorkspaces.first().filter { it.gradeKey == "G04" }
            val observed = repo.observeWorkspace(existing.workspaceId).first()

            assertEquals(1, saved.size)
            assertEquals(existing.workspaceId, second.workspaceId)
            assertNotNull(observed)
            assertEquals("العلوم - المصدر الثاني", observed!!.books.single().title)
            assertEquals(secondRoot.absolutePath, observed.rootPath)
            assertEquals("MERGED_PARTS_FILE", observed.books.single().sourceLayout)
            assertEquals(2, observed.books.single().offset)
            assertEquals(300, observed.books.single().totalPdfPages)
            assertEquals("source.pdf", observed.books.single().sourceFileName)
            assertEquals("assigned", observed.units.single().assignmentStatus)
            assertEquals("INDEX_EVIDENCE", observed.units.single().assignmentSource)
            assertEquals("عنوان الوحدة في الفهرس", observed.units.single().assignmentEvidence)
            assertEquals(2, observed.lessons.single().pdfPageStart)
            assertEquals(4, observed.lessons.single().pdfPageEnd)
            assertEquals(5, observed.lessons.single().printedPageStart)
            assertEquals(7, observed.lessons.single().printedPageEnd)
            assertEquals(4, observed.lessons.single().sortOrder)
        } finally {
            firstRoot.deleteRecursively()
            secondRoot.deleteRecursively()
            db.close()
        }
    }

    @Test
    fun repositoryAssessmentsNotesTemplatesAndFullBackupRoundtrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )

        val lesson = sampleLesson().copy(workspaceId = "ws-v6-test")
        val ws = ImportedWorkspace(
            workspaceId = "ws-v6-test",
            gradeKey = "G04",
            rootPath = File(context.filesDir, "edumini/workspaces/ws-v6-test").absolutePath,
            title = "G04 • اختبار شامل",
            books = listOf(
                BookRef(
                    bookKey = "ARAB",
                    title = "لغتي العربية",
                    subjectKey = "ARAB",
                    relativePath = "workspace/G04/ARAB",
                    lessonsCount = 1
                )
            ),
            lessons = listOf(lesson),
            filesCount = 2,
            warnings = emptyList()
        )
        repo.saveImportedWorkspace(ws)

        val q = repo.addManualQuestion(
            workspaceId = ws.workspaceId,
            lesson = lesson,
            type = QuestionType.FILL_BLANK,
            prompt = "عاصمة مصر هي ___.",
            options = emptyList(),
            correctAnswer = "القاهرة",
            rationale = "",
            difficulty = QuestionDifficulty.EASY
        )
        val asm = repo.createAssessment(
            workspaceId = ws.workspaceId,
            title = "اختبار تشخيصي",
            kind = AssessmentKind.DIAGNOSTIC,
            questionIds = listOf(q.id),
            timeLimitMinutes = 10,
            passScorePercent = 60
        )
        repo.recordAssessmentAttempt(asm, 1, 1, 100, 42, 30)
        repo.saveTeacherNote(ws.workspaceId, lesson, "مراجعة القاعدة النحوية أولاً", bookmarked = true)
        repo.savePromptTemplate(
            "خريطة مفاهيم",
            TaskType.LESSON_REVIEW,
            "لخّص المفاهيم في نقاط هرمية",
            responseSchemaJson = """{"type":"object","properties":{"concepts":{"type":"array"}}}""",
            responseMappingJson = """{"rootArray":"items","targetArray":"concepts"}"""
        )

        val backupStream = ByteArrayOutputStream()
        val exportedCount = repo.exportFullBackupJson(backupStream, ws)
        assertTrue(exportedCount >= 3)
        val backupJson = backupStream.toString(Charsets.UTF_8.name())
        assertFalse(backupJson.contains("apiKey"))

        repo.deleteWorkspace(ws.workspaceId, ws.rootPath)
        assertTrue(repo.observeAssessmentsForWorkspace(ws.workspaceId).first().isEmpty())

        repo.saveImportedWorkspace(ws)
        val restoreMsg = repo.importFullBackupJson(backupJson, ws.workspaceId)
        assertTrue(restoreMsg.contains("تمت استعادة"))

        val restoredAssessments = repo.observeAssessmentsForWorkspace(ws.workspaceId).first()
        assertEquals(1, restoredAssessments.size)
        assertEquals("اختبار تشخيصي", restoredAssessments.first().title)

        val restoredNote = repo.observeTeacherNote(ws.workspaceId, lesson).first()
        assertNotNull(restoredNote)
        assertTrue(restoredNote!!.bookmarked)
        assertEquals("مراجعة القاعدة النحوية أولاً", restoredNote.noteText)

        val templates = repo.observePromptTemplates().first()
        val restoredTemplate = templates.first { it.title == "خريطة مفاهيم" && !it.isBuiltIn }
        assertTrue(restoredTemplate.responseSchemaJson.contains("concepts"))
        assertTrue(restoredTemplate.responseMappingJson.contains("targetArray"))
        assertEquals(1, restoredTemplate.version)
    }

    @Test
    fun promptBuilderCoversExtendedTaskTypesAndInteractiveJsonSchema() {
        val service = AiGenerationService()
        val lesson = sampleLesson()

        val vocabPrompt = service.buildPrompt(lesson, TaskType.VOCABULARY, "نص", "", questionCount = 4)
        assertTrue(vocabPrompt.contains("باني المفردات"))

        val workedPrompt = service.buildPrompt(lesson, TaskType.WORKED_EXAMPLES, "نص", "", questionCount = 4)
        assertTrue(workedPrompt.contains("أمثلة محلولة"))

        val reviewPrompt = service.buildPrompt(lesson, TaskType.LESSON_REVIEW, "نص", "", questionCount = 4)
        assertTrue(reviewPrompt.contains("مراجعة الدرس"))

        val adapterPrompt = service.buildPrompt(lesson, TaskType.DIFFICULTY_ADAPTER, "نص", "", questionCount = 4)
        assertTrue(adapterPrompt.contains("تكييف الصعوبة"))

        val interactivePrompt = service.buildPrompt(
            lesson,
            TaskType.INTERACTIVE,
            "نص",
            "",
            questionCount = 3,
            responseSchemaJson = """{"type":"object","properties":{"steps":{"type":"array"},"ordering":{"type":"array"}}}""",
            responseFormat = "json"
        )
        assertTrue(interactivePrompt.contains("\"steps\""))
        assertTrue(interactivePrompt.contains("\"ordering\""))
    }

    @Test
    fun importerDetectsBookCoverPngAndSourcePdfAndPersistsInRoom() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = WorkspaceImporter(context)
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = importer,
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )

        val zipBytes = createTestZip(
            mapOf(
                "workspace/G06/MATH/book_manifest.json" to """{"title":"الرياضيات","cover":"cover.png"}""",
                "workspace/G06/MATH/cover.png" to "PNG-MOCK-DATA",
                "workspace/G06/MATH/book.pdf" to "%PDF-1.4 full-book",
                "workspace/G06/MATH/index.json" to """
                    {
                      "units": [
                        {
                          "unitKey": "unit_01",
                          "title": "الجبر",
                          "partKey": "P01",
                          "lessons": [
                            {"lessonKey": "lesson_01", "title": "المعادلات", "pdfPageStart": 4, "pdfPageEnd": 9}
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
                "workspace/G06/MATH/P01/units/unit_01/lesson_01.pdf" to "%PDF-1.4 lesson"
            )
        )

        val imported = importer.importFromStream(ByteArrayInputStream(zipBytes), zipBytes.size.toLong())
        assertEquals(1, imported.books.size)
        val book = imported.books.first()
        assertNotNull(book.coverPath)
        assertTrue(File(book.coverPath!!).exists())
        assertNotNull(book.sourcePdfPath)
        assertTrue(File(book.sourcePdfPath!!).exists())

        repo.saveImportedWorkspace(imported)
        val observed = repo.observeWorkspace(imported.workspaceId).first()
        assertNotNull(observed)
        assertEquals(book.coverPath, observed!!.books.first().coverPath)
        assertEquals(book.sourcePdfPath, observed.books.first().sourcePdfPath)
    }

    @Test
    fun bookIndexPromptRegistryCoversAllTasksAndStandardSchemas() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        TaskType.entries.forEach { task ->
            val blueprint = BookIndexPromptRegistry.blueprintFor(task)
            assertEquals(task, blueprint.task)
            assertTrue(blueprint.sourcePath.startsWith("prompts/book_indexing/"))
            assertTrue(blueprint.outputContract.isNotBlank())
            context.assets.open(blueprint.sourcePath).use { asset ->
                assertTrue(asset.readBytes().isNotEmpty())
            }
        }

        assertEquals(6, BookIndexPromptRegistry.schemas.size)
        assertTrue(BookIndexPromptRegistry.schemas.any { it.fileName.contains("index.json") })
        assertTrue(BookIndexPromptRegistry.schemas.any { it.fileName.contains("book_manifest.json") })
        assertTrue(BookIndexPromptRegistry.schemas.any { it.fileName.contains("interactive.json") })
        assertTrue(BookIndexPromptRegistry.schemas.any { it.fileName == "manifest.json" })

        val prompt = BookIndexPromptRegistry.assemblePrompt(
            lesson = sampleLesson(),
            task = TaskType.EXPLANATION,
            source = "مفهوم القسمة المطولة",
            extra = "تبسيط الخطوات",
            questionCount = 5,
            attachedPdfPagesCount = 1
        )
        assertTrue(prompt.contains("prompts/book_indexing/lesson_explanation.prompt.md"))
        assertTrue(prompt.contains("مفهوم القسمة المطولة"))
        assertTrue(prompt.contains("ملف PDF الأصلي للدرس مرفق"))
    }

    @Test
    fun directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDir = File(context.cacheDir, "direct_ws_sample_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val g04Math = File(rootDir, "workspace/G04/MATH").apply { mkdirs() }
            File(g04Math, "book_manifest.json").writeText("""{"title":"الرياضيات - الرابع"}""")
            File(g04Math, "index.json").writeText(
                """
                {
                  "metadata": {"subjectName": "الرياضيات"},
                  "units": [
                    {
                      "unitKey": "unit_01",
                      "title": "الكسور",
                      "partKey": "P01",
                      "lessons": [
                        {"lessonKey": "lesson_01", "title": "مقارنة الكسور", "printedPageStart": 6, "printedPageEnd": 10, "pdfPageStart": 8, "pdfPageEnd": 12}
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
            val g04LessonDir = File(g04Math, "P01/units/unit_01").apply { mkdirs() }
            val pdfFile = File(g04LessonDir, "lesson_01.pdf").apply { writeText("%PDF-1.4 sample") }
            val txtFile = File(g04LessonDir, "lesson_01.txt").apply { writeText("نص درس الكسور") }
            val htmlFile = File(g04LessonDir, "lesson_01_interactive.html").apply {
                writeText("<!doctype html><html><body><h1>تفاعلي الكسور</h1></body></html>")
            }
            val qDir = File(g04Math, "questions/unit_01/lesson_01").apply { mkdirs() }
            File(qDir, "ai_generated.json").writeText(
                """
                {
                  "questions": [
                    {
                      "type": "mcq",
                      "conceptTag": "مقارنة الكسور المتكافئة",
                      "prompt": "أي كسر يساوي النصف؟",
                      "options": ["2/4", "1/3", "3/4", "2/5"],
                      "correctAnswer": "2/4",
                      "rationale": "بقسمة البسط والمقام على 2",
                      "difficulty": "easy"
                    }
                  ]
                }
                """.trimIndent()
            )

            fun addBookFixture(subjectKey: String, componentKey: String?, title: String) {
                val componentFolder = componentKey ?: ""
                val componentPath = if (componentKey == null) "" else componentKey + "/"
                val bookDir = File(rootDir, "workspace/G07/" + subjectKey + "/" + componentFolder).apply { mkdirs() }
                val componentJson = componentKey?.let { "\"$it\"" } ?: "null"
                val componentLabel = componentKey ?: "BASE"
                val indexRaw = """
                    {
                      "templateId": "$subjectKey-$componentLabel",
                      "title": "$title",
                      "displayName": "$title",
                      "gradeKey": "G07",
                      "subjectKey": "$subjectKey",
                      "componentKey": $componentJson,
                      "partKey": "P01",
                      "units": [{
                        "unitNumber": 1,
                        "title": "الوحدة الأولى",
                        "partKey": "P01",
                        "lessons": [{
                          "lessonNumber": 1,
                          "title": "الدرس الأول"
                        }]
                      }]
                    }
                """.trimIndent()
                val manifestRaw = """
                    {
                      "bookTitle": "$title",
                      "gradeKey": "G07",
                      "subjectKey": "$subjectKey",
                      "componentKey": $componentJson
                    }
                """.trimIndent()
                File(bookDir, "index.json").writeText(indexRaw)
                File(bookDir, "book_manifest.json").writeText(manifestRaw)
            }

            addBookFixture("SCI", null, "العلوم والحياة")
            addBookFixture("ENG", "COURSE", "English Course")
            addBookFixture("ENG", "WORKBOOK", "English Workbook")
            addBookFixture("SOC", "GEO", "الجغرافيا")
            addBookFixture("SOC", "HIST", "التاريخ")
            addBookFixture("SOC", "TARBIA", "التربية الوطنية")

            val binder = DirectWorkspaceBinder(context)
            val inspection = binder.inspectDirectory(rootDir)
            assertEquals(2, inspection.grades.size)
            assertTrue(inspection.grades.any { it.gradeKey == "G04" && it.lessonsCount == 1 })
            assertTrue(inspection.grades.any { it.gradeKey == "G07" && it.booksCount == 5 && it.lessonsCount == 5 })
            assertTrue(inspection.errors.any { it.contains("WORKBOOK") })

            val bound = binder.bindSingleGrade(rootDir.absolutePath, "G04")
            assertEquals("G04", bound.workspace.gradeKey)
            assertEquals(1, bound.workspace.books.size)
            assertEquals(1, bound.workspace.lessons.size)
            assertEquals("الرياضيات", bound.workspace.books.first().subjectName)
            assertEquals(1, bound.workspace.indexRecords.size)
            assertTrue(bound.workspace.indexRecords.first().indexSha256.matches(Regex("^[a-f0-9]{64}$")))
            assertEquals(1, bound.workspace.units.size)
            assertEquals("الكسور", bound.workspace.units.first().title)

            val boundLesson = bound.workspace.lessons.first()
            assertEquals(pdfFile.absolutePath, boundLesson.pdfPath)
            assertEquals("8–12", boundLesson.pageRange)
            assertEquals("6–10", boundLesson.printedPageRange)
            assertEquals(txtFile.absolutePath, boundLesson.sourceTextPath)
            assertEquals(htmlFile.absolutePath, boundLesson.interactivePath)
            assertEquals(1, bound.preloadedInteractivePackages.size)
            assertEquals(1, bound.preloadedQuestions.size)
            assertEquals("مقارنة الكسور المتكافئة", bound.preloadedQuestions.first().conceptTag)

            val boundScience = binder.bindSingleGrade(rootDir.absolutePath, "G07")
            assertEquals(5, boundScience.workspace.books.size)
            assertEquals(5, boundScience.workspace.indexRecords.size)
            assertTrue(boundScience.workspace.warnings.any { it.contains("WORKBOOK") })
            val scienceBook = boundScience.workspace.books.single { it.subjectKey == "SCI" }
            assertEquals("العلوم", scienceBook.subjectName)
            assertEquals("العلوم والحياة", scienceBook.title)
            assertNull(scienceBook.componentKey)
            assertEquals(
                setOf("COURSE"),
                boundScience.workspace.books.filter { it.subjectKey == "ENG" }.mapNotNull { it.componentKey }.toSet()
            )
            assertEquals(
                setOf("GEO", "HIST", "TARBIA"),
                boundScience.workspace.books.filter { it.subjectKey == "SOC" }.mapNotNull { it.componentKey }.toSet()
            )
            assertEquals(
                setOf("SCI", "ENG/COURSE", "SOC/GEO", "SOC/HIST", "SOC/TARBIA"),
                boundScience.workspace.books.map { it.bookKey }.toSet()
            )
            val englishCourse = boundScience.workspace.books.single { it.subjectKey == "ENG" && it.componentKey == "COURSE" }
            assertEquals("اللغة الإنجليزية", englishCourse.subjectName)
            assertEquals("English Course", englishCourse.title)
            val geoBook = boundScience.workspace.books.single { it.componentKey == "GEO" }
            val histBook = boundScience.workspace.books.single { it.componentKey == "HIST" }
            val tarbiaBook = boundScience.workspace.books.single { it.componentKey == "TARBIA" }
            assertEquals("الدراسات الاجتماعية", geoBook.subjectName)
            assertEquals("الجغرافيا", geoBook.title)
            assertEquals("الدراسات الاجتماعية", histBook.subjectName)
            assertEquals("التاريخ", histBook.title)
            assertEquals("الدراسات الاجتماعية", tarbiaBook.subjectName)
            assertEquals("التربية الوطنية", tarbiaBook.title)

            // Binding the selected component folder itself must retain its identity from index.json.
            val courseRoot = File(rootDir, "workspace/G07/ENG/COURSE")
            val courseRootBound = binder.bindSingleGrade(courseRoot.absolutePath, "G07")
            assertEquals(1, courseRootBound.workspace.books.size)
            assertEquals("ENG/COURSE", courseRootBound.workspace.books.single().bookKey)
            assertEquals("COURSE", courseRootBound.workspace.books.single().componentKey)

            // Verify zero-copy: PDF file was NOT duplicated inside filesDir/edumini/workspaces
            val privateWorkspacesDir = File(context.filesDir, "edumini/workspaces/${bound.workspace.workspaceId}")
            assertFalse(privateWorkspacesDir.exists())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun directWorkspaceBinderRejectsComponentKeyThatConflictsWithIndexPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDir = File(context.cacheDir, "direct_ws_component_mismatch_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val bookDir = File(rootDir, "workspace/G07/ENG/COURSE").apply { mkdirs() }
            File(bookDir, "index.json").writeText(
                """
                {
                  "gradeKey": "G07",
                  "subjectKey": "ENG",
                  "componentKey": "WORKBOOK",
                  "units": [{
                    "unitKey": "unit_01",
                    "title": "Unit One",
                    "lessons": [{"lessonKey": "lesson_01", "title": "Lesson One"}]
                  }]
                }
                """.trimIndent()
            )
            File(bookDir, "book_manifest.json").writeText(
                """{"gradeKey":"G07","subjectKey":"ENG","componentKey":"WORKBOOK","title":"English Workbook"}"""
            )

            val failure = runCatching {
                DirectWorkspaceBinder(context).inspectDirectory(rootDir)
            }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(failure?.message?.contains("componentKey") == true)
            assertTrue(failure?.message?.contains("COURSE") == true)
            assertTrue(failure?.message?.contains("WORKBOOK") == true)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.eduMiniDao()
            val workspace = WorkspaceEntity(
                workspaceId = "direct-G07-test",
                gradeKey = "G07",
                rootPath = "content://test/tree/root",
                title = "G07",
                filesCount = 4,
                warningsJson = "[]",
                importedAt = 1L
            )
            val book = BookEntity(
                workspaceId = workspace.workspaceId,
                bookKey = "SCI",
                title = "العلوم والحياة",
                subjectKey = "SCI",
                componentKey = null,
                relativePath = "G07/SCI",
                lessonsCount = 1,
                subjectName = "العلوم والحياة"
            )
            val lesson = LessonEntity(
                workspaceId = workspace.workspaceId,
                grade = "G07",
                rootPath = workspace.rootPath,
                bookKey = book.bookKey,
                bookTitle = book.title,
                subjectKey = book.subjectKey,
                componentKey = book.componentKey,
                unitKey = "P01_unit_01",
                unitTitle = "الوحدة الأولى",
                lessonKey = "lesson_01",
                title = "الدرس الأول",
                partKey = "P01",
                pageRange = "1–3",
                pdfPath = "content://test/document/lesson01.pdf"
            )
            val index = WorkspaceIndexEntity(
                workspaceId = workspace.workspaceId,
                bookKey = book.bookKey,
                indexUri = "content://test/document/index.json",
                indexRelativePath = "G07/SCI/index.json",
                manifestUri = "content://test/document/book_manifest.json",
                manifestRelativePath = "G07/SCI/book_manifest.json",
                indexSha256 = "a".repeat(64),
                manifestSha256 = "b".repeat(64),
                indexSchemaVersion = "1",
                manifestVersion = "edu.book_manifest.v1",
                lastSyncedAt = 10L
            )
            val unit = WorkspaceUnitEntity(
                workspaceId = workspace.workspaceId,
                bookKey = book.bookKey,
                unitKey = "P01_unit_01",
                title = "الوحدة الأولى",
                partKey = "P01",
                sortOrder = 0,
                sourceIndexPath = index.indexUri
            )

            dao.syncDirectWorkspaceCatalog(workspace, listOf(book), listOf(lesson), listOf(index), listOf(unit))
            val originalBookRowId = dao.observeBooksForWorkspace(workspace.workspaceId).first().single().rowId
            val originalLessonRowId = dao.observeLessonsForWorkspace(workspace.workspaceId).first().single().rowId
            val changedIndex = index.copy(indexSha256 = "c".repeat(64), lastSyncedAt = 20L)
            val changedUnit = unit.copy(title = "الوحدة الأولى المعدلة")
            val changedLesson = lesson.copy(title = "الدرس الأول المعدل")
            dao.syncDirectWorkspaceCatalog(
                workspace.copy(importedAt = 2L),
                listOf(book),
                listOf(changedLesson),
                listOf(changedIndex),
                listOf(changedUnit)
            )

            assertEquals(1, dao.observeBooksForWorkspace(workspace.workspaceId).first().size)
            assertEquals(originalBookRowId, dao.observeBooksForWorkspace(workspace.workspaceId).first().single().rowId)
            assertEquals(originalLessonRowId, dao.observeLessonsForWorkspace(workspace.workspaceId).first().single().rowId)
            assertEquals("الدرس الأول المعدل", dao.observeLessonsForWorkspace(workspace.workspaceId).first().single().title)
            assertEquals("c".repeat(64), dao.observeWorkspaceIndexes(workspace.workspaceId).first().single().indexSha256)
            assertEquals("الوحدة الأولى المعدلة", dao.observeWorkspaceUnits(workspace.workspaceId).first().single().title)
        } finally {
            database.close()
        }
    }

    @Test
    fun conceptMasteryEngineDistinguishesMasteredVsNeedsReinforcementAndPreviousChoice() {
        val q1 = QuestionItem(
            id = "q-1",
            workspaceId = "ws-mastery",
            gradeKey = "G04",
            bookKey = "MATH",
            unitKey = "unit_01",
            lessonKey = "lesson_01",
            lessonTitle = "الكسور",
            type = QuestionType.MCQ,
            prompt = "سؤال 1",
            options = listOf("أ", "ب", "ج"),
            correctAnswer = "أ",
            rationale = "تفسير",
            difficulty = QuestionDifficulty.EASY,
            reviewed = true,
            conceptTag = "توحيد المقامات"
        )
        val q2 = q1.copy(id = "q-2", prompt = "سؤال 2", conceptTag = "توحيد المقامات")
        val q3 = q1.copy(id = "q-3", prompt = "سؤال 3", conceptTag = "الكسور المتكافئة")
        val q4 = q1.copy(id = "q-4", prompt = "سؤال 4", conceptTag = "ضرب الكسور")

        val attempts = listOf(
            // q3 is always answered correctly -> MASTERED
            QuestionAttemptRecord(id = "a1", workspaceId = "ws-mastery", questionId = "q-3", conceptTag = "الكسور المتكافئة", selectedAnswer = "أ", isCorrect = true, answeredAt = "2026-01-01T10:00:00Z"),
            QuestionAttemptRecord(id = "a2", workspaceId = "ws-mastery", questionId = "q-3", conceptTag = "الكسور المتكافئة", selectedAnswer = "أ", isCorrect = true, answeredAt = "2026-01-02T10:00:00Z"),
            // q1 was wrong once then right -> mixed => NEEDS_REINFORCEMENT
            QuestionAttemptRecord(id = "a3", workspaceId = "ws-mastery", questionId = "q-1", conceptTag = "توحيد المقامات", selectedAnswer = "ب", isCorrect = false, answeredAt = "2026-01-01T11:00:00Z"),
            QuestionAttemptRecord(id = "a4", workspaceId = "ws-mastery", questionId = "q-1", conceptTag = "توحيد المقامات", selectedAnswer = "أ", isCorrect = true, answeredAt = "2026-01-02T11:00:00Z")
        )

        val stats = ConceptMasteryEngine.computeQuestionStats(listOf(q1, q2, q3, q4), attempts)
        assertEquals(MasteryStatus.MASTERED, stats["q-3"]?.status)
        assertEquals("أ", stats["q-3"]?.lastSelectedAnswer)
        assertTrue(stats["q-3"]?.sameAsPrevious(" أ ") == true)
        assertFalse(stats["q-3"]?.sameAsPrevious("ب") == true)

        assertEquals(MasteryStatus.NEEDS_REINFORCEMENT, stats["q-1"]?.status)
        assertEquals(1, stats["q-1"]?.correctCount)
        assertEquals(1, stats["q-1"]?.wrongCount)
        assertEquals(MasteryStatus.UNSEEN, stats["q-4"]?.status)

        val summaries = ConceptMasteryEngine.computeConceptSummaries(listOf(q1, q2, q3, q4), attempts)
        assertEquals(MasteryStatus.NEEDS_REINFORCEMENT, summaries.first().status)
        assertEquals("توحيد المقامات", summaries.first().conceptTag)
        assertEquals(50, summaries.first().accuracyPercent)

        val mastered = summaries.first { it.conceptTag == "الكسور المتكافئة" }
        assertEquals(MasteryStatus.MASTERED, mastered.status)
        assertEquals(100, mastered.accuracyPercent)

        val targeted = ConceptMasteryEngine.selectReinforcementQuestions(
            questions = listOf(q1, q2, q3, q4),
            attempts = attempts,
            targetConceptTag = "توحيد المقامات"
        )
        assertEquals(listOf("q-1", "q-2"), targeted.map { it.id })
    }

    @Test
    fun lessonQuizGatesLessonCompletionAndPersistsQuestionAttempts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(context),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context),
            directWorkspaceBinder = DirectWorkspaceBinder(context),
            appContext = context
        )

        val wsId = "ws-quiz-gate-${System.currentTimeMillis()}"
        val lesson = sampleLesson().copy(workspaceId = wsId)
        val ws = ImportedWorkspace(
            workspaceId = wsId,
            gradeKey = "G04",
            rootPath = "/tmp/$wsId",
            title = "اختبار بوابة الإكمال",
            books = listOf(BookRef("ARAB", "لغتي", "ARAB", null, "ARAB", 1)),
            lessons = listOf(lesson),
            filesCount = 1,
            warnings = emptyList()
        )
        repo.saveImportedWorkspace(ws)

        val q1 = QuestionItem(
            id = "qg-1-$wsId",
            workspaceId = wsId,
            gradeKey = "G04",
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            lessonTitle = lesson.title,
            type = QuestionType.MCQ,
            prompt = "السؤال الأول",
            options = listOf("صحيح", "خطأ 1"),
            correctAnswer = "صحيح",
            rationale = "تفسير 1",
            difficulty = QuestionDifficulty.EASY,
            conceptTag = "المبتدأ والخبر"
        )
        val q2 = q1.copy(
            id = "qg-2-$wsId",
            prompt = "السؤال الثاني",
            options = listOf("نعم", "لا"),
            correctAnswer = "نعم",
            conceptTag = "علامات الإعراب"
        )

        // Failing attempt (0/2 -> 0% < 60%) must NOT mark lesson completed
        val failResult = repo.recordLessonQuizAttempt(
            workspaceId = wsId,
            lesson = lesson,
            questions = listOf(q1, q2),
            selectedAnswers = mapOf(q1.id to "خطأ 1", q2.id to "لا")
        )
        assertFalse(failResult.passed)
        assertFalse(failResult.lessonMarkedCompleted)
        assertEquals(0, failResult.scorePercent)

        val progressAfterFail = db.eduMiniDao().observeProgressForWorkspace(wsId).first()
        assertTrue(progressAfterFail.none { it.completed })

        // Passing attempt (2/2 -> 100% >= 60%) MUST mark lesson completed & award XP
        val passResult = repo.recordLessonQuizAttempt(
            workspaceId = wsId,
            lesson = lesson,
            questions = listOf(q1, q2),
            selectedAnswers = mapOf(q1.id to "صحيح", q2.id to "نعم")
        )
        assertTrue(passResult.passed)
        assertTrue(passResult.lessonMarkedCompleted)
        assertEquals(100, passResult.scorePercent)
        assertTrue(passResult.earnedXp >= EduMiniRepository.POINTS_LESSON_COMPLETED)

        val progressAfterPass = db.eduMiniDao().observeProgressForWorkspace(wsId).first()
        assertTrue(progressAfterPass.any { it.lessonKey == lesson.lessonKey && it.completed })

        // A failed review retake must not revoke a lesson that was already passed.
        val failedReview = repo.recordLessonQuizAttempt(
            workspaceId = wsId,
            lesson = lesson,
            questions = listOf(q1, q2),
            selectedAnswers = mapOf(q1.id to "خطأ 1", q2.id to "لا")
        )
        assertFalse(failedReview.passed)
        assertTrue(failedReview.lessonMarkedCompleted)
        assertTrue(
            db.eduMiniDao().observeProgressForWorkspace(wsId).first()
                .any { it.lessonKey == lesson.lessonKey && it.completed }
        )

        // Mixed history on both concepts keeps them in NEEDS_REINFORCEMENT (because earlier attempt was wrong)
        val recordedAttempts = db.eduMiniDao().observeQuestionAttemptsForWorkspace(wsId).first()
        assertEquals(6, recordedAttempts.size)
        assertEquals(2, passResult.weakConcepts.size)
    }

    @Test
    fun interactiveHtmlScannerAllowsSandboxedInlineHandlersAndBlocksEval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = InteractivePackageImporter(context)
        val lesson = sampleLesson()

        val safeHtml = """
            <!doctype html>
            <html><body>
              <button onclick="document.getElementById('hint').style.display='block'">تلميح</button>
              <div id="hint" style="display:none">تلميح تعليمي</div>
            </body></html>
        """.trimIndent()
        val safeStage = importer.importFromStream(
            inputStream = ByteArrayInputStream(safeHtml.toByteArray(Charsets.UTF_8)),
            fileName = "interactive.html",
            isZip = false,
            declaredSizeBytes = safeHtml.length.toLong(),
            lesson = lesson,
            sourcePages = "5–10",
            version = 1
        )
        assertFalse(safeStage.report.hasBlockers)
        assertTrue(safeStage.report.findings.any { it.code == "INLINE_EVENT_HANDLER_SANDBOXED" && it.severity == SecuritySeverity.PASS })

        val dangerousHtml = """
            <!doctype html><html><body><script>eval("1+1")</script></body></html>
        """.trimIndent()
        val blockedStage = importer.importFromStream(
            inputStream = ByteArrayInputStream(dangerousHtml.toByteArray(Charsets.UTF_8)),
            fileName = "danger.html",
            isZip = false,
            declaredSizeBytes = dangerousHtml.length.toLong(),
            lesson = lesson,
            sourcePages = "5–10",
            version = 2
        )
        assertTrue(blockedStage.report.hasBlockers)
        assertTrue(blockedStage.report.findings.any { it.code == "UNSAFE_DYNAMIC_EVAL" && it.severity == SecuritySeverity.BLOCKER })
    }

    @Test
    fun workspaceExporterWritesManifestWithAccurateSha256Checksums() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wsDir = File(context.cacheDir, "export_src_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            File(wsDir, "book_manifest.json").writeText("""{"title":"كتاب الاختبار"}""")
            val workspace = ImportedWorkspace(
                workspaceId = "ws-export",
                gradeKey = "G04",
                rootPath = wsDir.absolutePath,
                title = "تصدير SHA256",
                books = listOf(BookRef("ARAB", "كتاب الاختبار", "ARAB", null, ".", 1)),
                lessons = listOf(sampleLesson().copy(rootPath = wsDir.absolutePath, workspaceId = "ws-export")),
                filesCount = 1,
                warnings = emptyList()
            )
            val baos = ByteArrayOutputStream()
            val receipt = WorkspaceExporter.exportLegacyWorkspaceZip(
                outputStream = baos,
                workspace = workspace,
                drafts = emptyList(),
                questions = emptyList(),
                context = context
            )
            assertTrue(receipt.entriesCount >= 2)

            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    entries[entry.name] = zis.readBytes()
                    zis.closeEntry()
                }
            }
            val manifestBytes = entries["manifest.json"]
            assertNotNull(manifestBytes)
            val manifestJson = Json.parseToJsonElement(manifestBytes!!.toString(Charsets.UTF_8)).jsonObject
            val artifacts = manifestJson["artifacts"]!!.jsonArray
            assertEquals(1, artifacts.size)
            val bookEntry = artifacts.first().jsonObject
            val recordedPath = bookEntry["path"]!!.jsonPrimitive.content
            val recordedHash = bookEntry["sha256"]!!.jsonPrimitive.content
            val actualBytes = entries[recordedPath]!!
            val expectedHash = MessageDigest.getInstance("SHA-256")
                .digest(actualBytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(expectedHash, recordedHash)
        } finally {
            wsDir.deleteRecursively()
        }
    }

    @Test
    fun multiUnitStateProgressionTracksQuizAttemptsAndRequiresAllLessonsPassedForUnitCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )
        val u1Lesson1 = sampleLesson().copy(
            workspaceId = "ws-multi-unit",
            unitKey = "unit_01",
            unitTitle = "الوحدة الأولى",
            lessonKey = "lesson_01",
            title = "درس ١-١"
        )
        val u1Lesson2 = u1Lesson1.copy(lessonKey = "lesson_02", title = "درس ١-٢")
        val u2Lesson1 = u1Lesson1.copy(unitKey = "unit_02", unitTitle = "الوحدة الثانية", lessonKey = "lesson_03", title = "درس ٢-١")
        val ws = ImportedWorkspace(
            workspaceId = "ws-multi-unit",
            gradeKey = "G04",
            rootPath = "/tmp/ws-multi-unit",
            title = "G04 • العلوم",
            books = listOf(
                BookRef(
                    bookKey = "ARAB",
                    title = "لغتي العربية",
                    subjectKey = "ARAB",
                    relativePath = "workspace/G04/ARAB",
                    lessonsCount = 3
                )
            ),
            lessons = listOf(u1Lesson1, u1Lesson2, u2Lesson1),
            filesCount = 3,
            warnings = emptyList()
        )
        repo.saveImportedWorkspace(ws)

        val q1 = repo.addManualQuestion(
            workspaceId = ws.workspaceId,
            lesson = u1Lesson1,
            type = QuestionType.TRUE_FALSE,
            prompt = "السؤال الأول للدرس ١-١",
            options = listOf("صح", "خطأ"),
            correctAnswer = "صح",
            rationale = "تفسير",
            difficulty = QuestionDifficulty.EASY
        )
        val q2 = repo.addManualQuestion(
            workspaceId = ws.workspaceId,
            lesson = u1Lesson2,
            type = QuestionType.TRUE_FALSE,
            prompt = "السؤال الأول للدرس ١-٢",
            options = listOf("صح", "خطأ"),
            correctAnswer = "صح",
            rationale = "تفسير",
            difficulty = QuestionDifficulty.EASY
        )

        // Initially: Unit 1 has questions (lessons are READY, unit NOT_STARTED), Unit 2 is NOT_STARTED
        val initialSummary = repo.observeDashboardSummary(ws).first()
        val initialUnit1 = initialSummary.unitPaths.first { it.unitKey == "ARAB_unit_01" }
        val initialUnit2 = initialSummary.unitPaths.first { it.unitKey == "ARAB_unit_02" }
        assertEquals(UnitCompletionStatus.NOT_STARTED, initialUnit1.status)
        assertEquals(UnitCompletionStatus.NOT_STARTED, initialUnit2.status)
        assertEquals(LearningNodeStatus.READY, initialUnit1.nodes[0].status)
        assertEquals(1, initialUnit1.nodes[0].questionsCount)

        // Failed attempt on lesson 1 keeps lesson incomplete and marks unit IN_PROGRESS via quizAttemptedCount
        val failRes = repo.recordLessonQuizAttempt(ws.workspaceId, u1Lesson1, listOf(q1), mapOf(q1.id to "خطأ"))
        assertFalse(failRes.passed)
        val afterFailSummary = repo.observeDashboardSummary(ws).first()
        val afterFailUnit1 = afterFailSummary.unitPaths.first { it.unitKey == "ARAB_unit_01" }
        assertEquals(0, afterFailUnit1.completedCount)
        assertFalse(afterFailUnit1.nodes[0].isCompleted)
        assertEquals(1, afterFailUnit1.nodes[0].quizAttemptedCount)
        assertEquals(UnitCompletionStatus.IN_PROGRESS, afterFailUnit1.status)

        // Pass lesson 1 quiz -> lesson 1 COMPLETED, Unit 1 stays IN_PROGRESS because lesson 2 is not passed
        val pass1 = repo.recordLessonQuizAttempt(ws.workspaceId, u1Lesson1, listOf(q1), mapOf(q1.id to "صح"))
        assertTrue(pass1.passed)
        val midSummary = repo.observeDashboardSummary(ws).first()
        val midUnit1 = midSummary.unitPaths.first { it.unitKey == "ARAB_unit_01" }
        assertEquals(1, midUnit1.completedCount)
        assertEquals(UnitCompletionStatus.IN_PROGRESS, midUnit1.status)
        assertTrue(midUnit1.nodes[0].quizPassed)
        assertFalse(midUnit1.nodes[1].quizPassed)

        // Pass lesson 2 quiz -> ALL lessons in Unit 1 passed -> Unit 1 becomes COMPLETED; Unit 2 remains NOT_STARTED
        val pass2 = repo.recordLessonQuizAttempt(ws.workspaceId, u1Lesson2, listOf(q2), mapOf(q2.id to "صح"))
        assertTrue(pass2.passed)
        val finalSummary = repo.observeDashboardSummary(ws).first()
        val finalUnit1 = finalSummary.unitPaths.first { it.unitKey == "ARAB_unit_01" }
        val finalUnit2 = finalSummary.unitPaths.first { it.unitKey == "ARAB_unit_02" }
        assertEquals(UnitCompletionStatus.COMPLETED, finalUnit1.status)
        assertEquals(100, finalUnit1.completionPercent)
        assertEquals(UnitCompletionStatus.NOT_STARTED, finalUnit2.status)
    }

    @Test
    fun questionGenerationDraftApprovalDeduplicatesAndPopulatesQuizAndDashboardState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = EduMiniDatabase.getInstance(context)
        val repo = EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context)
        )
        val lesson = sampleLesson().copy(workspaceId = "ws-gen-sync", lessonKey = "lesson_sync", title = "حالات المادة")
        val ws = ImportedWorkspace(
            workspaceId = "ws-gen-sync",
            gradeKey = "G04",
            rootPath = "/tmp/ws-gen-sync",
            title = "G04 • العلوم",
            books = listOf(
                BookRef(
                    bookKey = "ARAB",
                    title = "لغتي العربية",
                    subjectKey = "ARAB",
                    relativePath = "workspace/G04/ARAB",
                    lessonsCount = 1
                )
            ),
            lessons = listOf(lesson),
            filesCount = 1,
            warnings = emptyList()
        )
        repo.saveImportedWorkspace(ws)

        val generatedQuestionsText = """
            1. ما الحالة التي لها شكل وحجم ثابت؟
            أ) الصلبة
            ب) السائلة
            ج) الغازية
            الإجابة: أ

            2. البخار مثال على الحالة السائلة.
            أ) صح
            ب) خطأ
            الإجابة: ب
        """.trimIndent()

        val draft = repo.persistDraft(
            workspaceId = ws.workspaceId,
            lesson = lesson,
            task = TaskType.QUESTIONS,
            providerLabel = "Gemini",
            model = "gemini-2.5-flash",
            text = generatedQuestionsText,
            title = lesson.title,
            sourceText = "حالات المادة ثلاث"
        )

        // A saved draft must remain outside the question bank until explicit approval.
        val questionsBeforeApproval = repo.observeQuestionsForWorkspace(ws.workspaceId).first()
        assertTrue(questionsBeforeApproval.isEmpty())

        // Approval is the only step that imports the generated questions into the bank.
        val addedOnApprove = repo.approveDraft(draft.id)
        assertEquals(2, addedOnApprove)

        val storedQuestions = repo.observeQuestionsForWorkspace(ws.workspaceId).first()
        assertEquals(2, storedQuestions.size)

        val beforeQuizSummary = repo.observeDashboardSummary(ws).first()
        val nodeBefore = beforeQuizSummary.unitPaths.first().nodes.first()
        assertEquals(2, nodeBefore.questionsCount)
        assertTrue(nodeBefore.hasGeneratedMaterials)
        assertEquals(LearningNodeStatus.READY, nodeBefore.status)
        assertFalse(nodeBefore.isCompleted)

        // Passing the generated questions completes the lesson and the unit
        val answers = storedQuestions.associate { q ->
            val correctOpt = if (q.prompt.contains("ثابت")) q.options.first() else "خطأ"
            q.id to correctOpt
        }
        val quizRes = repo.recordLessonQuizAttempt(ws.workspaceId, lesson, storedQuestions, answers)
        assertTrue(quizRes.passed)

        val afterQuizSummary = repo.observeDashboardSummary(ws).first()
        assertEquals(1, afterQuizSummary.completedLessons)
        assertEquals(UnitCompletionStatus.COMPLETED, afterQuizSummary.unitPaths.first().status)
    }

    @Test
    fun roomEnforcesStableBookAndLessonIdentityWithinWorkspace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.eduMiniDao()
            val workspaceId = "ws-unique-${System.nanoTime()}"

            dao.insertBooks(
                listOf(
                    BookEntity(
                        workspaceId = workspaceId,
                        bookKey = "ENG/COURSE",
                        title = "English Course - old",
                        subjectKey = "ENG",
                        componentKey = "COURSE",
                        relativePath = "G07/ENG/COURSE",
                        lessonsCount = 1
                    ),
                    BookEntity(
                        workspaceId = workspaceId,
                        bookKey = "ENG/COURSE",
                        title = "English Course - updated",
                        subjectKey = "ENG",
                        componentKey = "COURSE",
                        relativePath = "G07/ENG/COURSE",
                        lessonsCount = 2
                    )
                )
            )
            val books = dao.observeBooksForWorkspace(workspaceId).first()
            assertEquals(1, books.size)
            assertEquals("English Course - updated", books.single().title)

            dao.insertLessons(
                listOf(
                    LessonEntity(
                        workspaceId = workspaceId,
                        grade = "G07",
                        rootPath = "/workspace",
                        bookKey = "ENG/COURSE",
                        bookTitle = "English Course",
                        subjectKey = "ENG",
                        componentKey = "COURSE",
                        unitKey = "unit_01",
                        unitTitle = "Unit 1",
                        lessonKey = "lesson_01",
                        title = "Lesson old",
                        partKey = "P01",
                        pageRange = "1-2",
                        pdfPath = "lesson-old.pdf"
                    ),
                    LessonEntity(
                        workspaceId = workspaceId,
                        grade = "G07",
                        rootPath = "/workspace",
                        bookKey = "ENG/COURSE",
                        bookTitle = "English Course",
                        subjectKey = "ENG",
                        componentKey = "COURSE",
                        unitKey = "unit_01",
                        unitTitle = "Unit 1",
                        lessonKey = "lesson_01",
                        title = "Lesson updated",
                        partKey = "P01",
                        pageRange = "1-3",
                        pdfPath = "lesson-updated.pdf"
                    )
                )
            )
            val lessons = dao.observeLessonsForWorkspace(workspaceId).first()
            assertEquals(1, lessons.size)
            assertEquals("Lesson updated", lessons.single().title)
        } finally {
            db.close()
        }
    }

    @Test
    fun learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.eduMiniDao()
            val workspaceId = "ws-learner-isolation"
            val lessonProgressA = LessonProgressEntity(
                workspaceId = workspaceId,
                bookKey = "SCI",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                completed = true,
                pointsEarned = 80,
                completedAt = "2026-11-25T10:00:00Z",
                userId = "learner_a"
            )
            val lessonProgressB = lessonProgressA.copy(
                completed = false,
                pointsEarned = 10,
                completedAt = null,
                userId = "learner_b"
            )
            dao.upsertLessonProgress(lessonProgressA)
            dao.upsertLessonProgress(lessonProgressB)

            assertEquals(1, dao.observeProgressForWorkspace(workspaceId, "learner_a").first().size)
            assertTrue(dao.observeProgressForWorkspace(workspaceId, "learner_a").first().single().completed)
            assertFalse(dao.observeProgressForWorkspace(workspaceId, "learner_b").first().single().completed)

            dao.upsertStudyDay(
                StudyDayEntity(
                    activityDate = "2026-11-25",
                    actionsCount = 4,
                    lastUpdatedAt = "2026-11-25T10:00:00Z",
                    userId = "learner_a"
                )
            )
            dao.upsertStudyDay(
                StudyDayEntity(
                    activityDate = "2026-11-25",
                    actionsCount = 1,
                    lastUpdatedAt = "2026-11-25T10:01:00Z",
                    userId = "learner_b"
                )
            )
            assertEquals(4, dao.getStudyDay("2026-11-25", "learner_a")?.actionsCount)
            assertEquals(1, dao.getStudyDay("2026-11-25", "learner_b")?.actionsCount)

            val attemptA = AssessmentAttemptEntity(
                id = "attempt-a",
                assessmentId = "assessment-1",
                assessmentTitle = "اختبار العلوم",
                workspaceId = workspaceId,
                correctCount = 8,
                totalQuestions = 10,
                scorePercent = 80,
                passed = true,
                durationSeconds = 300,
                earnedXp = 40,
                completedAt = "2026-11-25T10:00:00Z",
                userId = "learner_a"
            )
            dao.insertAssessmentAttempt(attemptA)
            dao.insertAssessmentAttempt(attemptA.copy(
                id = "attempt-b",
                correctCount = 3,
                scorePercent = 30,
                passed = false,
                userId = "learner_b"
            ))
            assertEquals(1, dao.observeAssessmentAttempts(workspaceId, "learner_a").first().size)
            assertEquals(1, dao.observeAssessmentAttempts(workspaceId, "learner_b").first().size)
            assertEquals(80, dao.observeAssessmentAttempts(workspaceId, "learner_a").first().single().scorePercent)
            assertEquals(30, dao.observeAssessmentAttempts(workspaceId, "learner_b").first().single().scorePercent)
        } finally {
            db.close()
        }
    }

    @Test
    fun reinforcementSelectionExcludesUnreviewedGeneratedQuestions() {
        fun question(id: String, reviewed: Boolean) = QuestionItem(
            id = id,
            workspaceId = "ws-review",
            gradeKey = "G04",
            bookKey = "MATH",
            unitKey = "unit_01",
            lessonKey = "lesson_01",
            lessonTitle = "الكسور",
            type = QuestionType.MCQ,
            prompt = "ما ناتج نصف الواحد؟",
            options = listOf("1/2", "1/3"),
            correctAnswer = "1/2",
            rationale = "النصف جزء من جزأين متساويين",
            difficulty = QuestionDifficulty.EASY,
            reviewed = reviewed,
            conceptTag = "fractions"
        )
        val approved = question("approved-q", true)
        val pending = question("pending-q", false)
        val selected = ConceptMasteryEngine.selectReinforcementQuestions(
            questions = listOf(approved, pending),
            attempts = emptyList(),
            targetConceptTag = "fractions"
        )
        assertEquals(listOf("approved-q"), selected.map { it.id })
    }

    @Test
    fun learnerPointsExcludeAuthoringOnlyBadgeBonuses() {
        val summary = DashboardSummary(
            totalPoints = 300,
            badges = listOf(
                AchievementBadge(
                    id = "author",
                    title = "مؤلف ذكي",
                    subtitle = "إنشاء محتوى",
                    unlocked = true,
                    pointsBonus = 40,
                    category = BadgeCategory.CREATION
                ),
                AchievementBadge(
                    id = "mastery",
                    title = "إتقان",
                    subtitle = "إكمال مادة",
                    unlocked = true,
                    pointsBonus = 60,
                    category = BadgeCategory.MASTERY
                )
            )
        )
        assertEquals(260, summary.learnerPoints)
    }

    @Test
    fun subjectMilestoneBadgesReflectPerBookCompletionThresholds() {
        val book = BookRef(
            bookKey = "SCI",
            title = "العلوم",
            subjectKey = "SCI",
            relativePath = "SCI",
            lessonsCount = 4,
            subjectName = "العلوم"
        )
        val progress = BookProgressSummary(
            book = book,
            completedLessons = 2,
            preparedLessons = 0,
            totalLessons = 4,
            completionPercent = 50,
            preparationPercent = 0,
            earnedPoints = 100
        )
        val badges = MilestoneBadgeEngine.evaluateBadges(
            totalLessons = 4,
            completedLessons = 2,
            overallCompletionPercent = 50,
            draftsCount = 0,
            approvedDraftsCount = 0,
            packagesCount = 0,
            publishedPackagesCount = 0,
            bookProgress = listOf(progress),
            unitPaths = emptyList(),
            streak = com.example.data.repository.StudyStreakSnapshot(0, 0)
        )
        assertTrue(badges.any { it.id == "badge_subject_SCI_25" && it.unlocked })
        assertTrue(badges.any { it.id == "badge_subject_SCI_50" && it.unlocked })
        assertFalse(badges.any { it.id == "badge_subject_SCI_75" && it.unlocked })
        assertFalse(badges.any { it.id == "badge_subject_SCI_100" && it.unlocked })
    }

    @Test
    fun lessonSequenceGateRequiresEveryEarlierLessonToBePassed() {
        val first = sampleLesson()
        val second = first.copy(lessonKey = "lesson_02", title = "الدرس الثاني")
        val third = first.copy(lessonKey = "lesson_03", title = "الدرس الثالث")
        val ordered = listOf(first, second, third)

        assertTrue(LessonSequenceGate.isUnlocked(first, ordered, emptySet()))
        assertFalse(LessonSequenceGate.isUnlocked(second, ordered, emptySet()))
        assertFalse(
            LessonSequenceGate.isUnlocked(
                third,
                ordered,
                setOf(LessonSequenceGate.key(second))
            )
        )
        assertTrue(
            LessonSequenceGate.isUnlocked(
                third,
                ordered,
                setOf(LessonSequenceGate.key(first), LessonSequenceGate.key(second))
            )
        )
    }

    @Test
    fun learningProgressReportSeparatesInitialAccuracyDelayedRecallAndRecurringMistakes() {
        val questions = (1..3).map { index ->
            QuestionItem(
                id = "report-q$index",
                workspaceId = "ws-report",
                gradeKey = "G07",
                bookKey = "SCI",
                unitKey = "unit-01",
                lessonKey = "lesson-01",
                lessonTitle = "الكسور",
                type = QuestionType.MCQ,
                prompt = "سؤال $index",
                options = listOf("1/2", "1/3", "1/4"),
                correctAnswer = "1/2",
                rationale = "التفسير",
                difficulty = QuestionDifficulty.MEDIUM,
                conceptTag = "fractions"
            )
        }
        fun attempt(id: String, questionId: String, answer: String, correct: Boolean, at: String) =
            QuestionAttemptRecord(
                id = id,
                workspaceId = "ws-report",
                userId = "learner_a",
                questionId = questionId,
                conceptTag = "fractions",
                selectedAnswer = answer,
                isCorrect = correct,
                sourceMode = "lesson_quiz",
                answeredAt = at
            )
        val attempts = listOf(
            attempt("a1", "report-q1", "1/2", true, "2026-11-10T10:00:00Z"),
            attempt("a2", "report-q2", "1/2", true, "2026-11-10T10:00:00Z"),
            attempt("a3", "report-q3", "1/2", true, "2026-11-10T10:00:00Z"),
            attempt("a4", "report-q1", "1/2", true, "2026-11-19T10:00:00Z"),
            attempt("a5", "report-q2", "1/3", false, "2026-11-19T10:00:00Z"),
            attempt("a6", "report-q2", "1/3", false, "2026-11-20T10:00:00Z"),
            attempt("a7", "report-q3", "1/4", false, "2026-11-20T10:00:00Z")
        )
        val assessments = listOf(
            AssessmentAttemptRecord(
                id = "weekly-1",
                assessmentId = "weekly",
                assessmentTitle = "اختبار أسبوعي",
                workspaceId = "ws-report",
                correctCount = 7,
                totalQuestions = 10,
                scorePercent = 70,
                passed = true,
                durationSeconds = 300,
                earnedXp = 10,
                completedAt = "2026-11-24T10:00:00Z"
            ),
            AssessmentAttemptRecord(
                id = "weekly-2",
                assessmentId = "weekly",
                assessmentTitle = "اختبار أسبوعي",
                workspaceId = "ws-report",
                correctCount = 9,
                totalQuestions = 10,
                scorePercent = 90,
                passed = true,
                durationSeconds = 280,
                earnedXp = 15,
                completedAt = "2026-11-25T10:00:00Z"
            )
        )

        val report = LearningProgressAnalytics.calculate(
            questions = questions,
            attempts = attempts,
            assessments = assessments,
            now = java.time.Instant.parse("2026-11-25T12:00:00Z")
        )

        assertEquals(100, report.firstTryAccuracyPercent)
        assertEquals(33, report.latestAccuracyPercent)
        assertEquals(33, report.delayedRecallPercent)
        assertEquals(67, report.estimatedRecallDropPercent)
        assertEquals(3, report.delayedRecallSampleCount)
        assertEquals(1, report.recurringMistakes.size)
        assertEquals(listOf("fractions"), report.recurringConceptTags)
        assertEquals("1/3", report.recurringMistakes.single().selectedAnswer)
        assertEquals(2, report.recurringMistakes.single().occurrences)
        assertEquals(80, report.weeklyAssessments.single().averageScorePercent)
    }

    @Test
    fun practiceOnlyAttemptsDoNotInflateMainMasteryAccuracy() {
        val question = QuestionItem(
            id = "practice-only-q",
            workspaceId = "ws-practice",
            gradeKey = "G07",
            bookKey = "SCI",
            unitKey = "unit-01",
            lessonKey = "lesson-01",
            lessonTitle = "الطاقة",
            type = QuestionType.MCQ,
            prompt = "سؤال تدريبي",
            options = listOf("أ", "ب"),
            correctAnswer = "أ",
            rationale = "التفسير",
            difficulty = QuestionDifficulty.EASY,
            conceptTag = "energy"
        )
        val practiceAttempt = QuestionAttemptRecord(
            id = "practice-only-a",
            workspaceId = "ws-practice",
            userId = "learner_a",
            questionId = question.id,
            conceptTag = "energy",
            selectedAnswer = "أ",
            isCorrect = true,
            sourceMode = "reinforcement",
            answeredAt = "2026-11-25T10:00:00Z"
        )
        val report = LearningProgressAnalytics.calculate(
            questions = listOf(question),
            attempts = listOf(practiceAttempt),
            assessments = emptyList(),
            now = java.time.Instant.parse("2026-11-25T12:00:00Z")
        )
        assertEquals(1, report.questionAttemptsCount)
        assertNull(report.firstTryAccuracyPercent)
        assertNull(report.latestAccuracyPercent)
    }

    @Test
    fun learningProgressReportMeasuresFollowUpAfterReinforcementOnlyOnSharedQuestions() {
        val questions = (1..2).map { index ->
            QuestionItem(
                id = "support-q$index",
                workspaceId = "ws-support",
                gradeKey = "G07",
                bookKey = "SCI",
                unitKey = "unit-01",
                lessonKey = "lesson-01",
                lessonTitle = "الكسور",
                type = QuestionType.MCQ,
                prompt = "سؤال $index",
                options = listOf("1/2", "1/3"),
                correctAnswer = "1/2",
                rationale = "التفسير",
                difficulty = QuestionDifficulty.MEDIUM,
                conceptTag = "fractions"
            )
        }
        fun attempt(id: String, questionId: String, answer: String, correct: Boolean, source: String, at: String) =
            QuestionAttemptRecord(
                id = id,
                workspaceId = "ws-support",
                userId = "learner_a",
                questionId = questionId,
                conceptTag = "fractions",
                selectedAnswer = answer,
                isCorrect = correct,
                sourceMode = source,
                answeredAt = at
            )
        val attempts = listOf(
            attempt("b1", "support-q1", "1/3", false, "lesson_quiz", "2026-11-01T10:00:00Z"),
            attempt("b2", "support-q2", "1/3", false, "lesson_quiz", "2026-11-01T10:00:00Z"),
            attempt("b3", "support-q1", "1/2", true, "reinforcement", "2026-11-02T10:00:00Z"),
            attempt("b4", "support-q2", "1/2", true, "reinforcement", "2026-11-02T10:00:00Z"),
            attempt("b5", "support-q1", "1/2", true, "weekly_test", "2026-11-05T10:00:00Z"),
            attempt("b6", "support-q2", "1/2", true, "weekly_test", "2026-11-05T10:00:00Z")
        )
        val report = LearningProgressAnalytics.calculate(
            questions = questions,
            attempts = attempts,
            assessments = emptyList(),
            now = java.time.Instant.parse("2026-11-25T12:00:00Z")
        )
        assertEquals(100, report.reinforcementImpactPercent)
        assertEquals(1, report.reinforcementImpactConceptCount)
    }

    @Test
    fun fullBackupPreservesLocalLearnerOwnership() {
        val attempts = listOf(
            AssessmentAttemptEntity(
                id = "backup-attempt-a",
                assessmentId = "assessment",
                assessmentTitle = "اختبار",
                workspaceId = "ws-backup",
                correctCount = 4,
                totalQuestions = 5,
                scorePercent = 80,
                passed = true,
                durationSeconds = 120,
                earnedXp = 20,
                completedAt = "2026-11-25T10:00:00Z",
                userId = "learner_a"
            ),
            AssessmentAttemptEntity(
                id = "backup-attempt-b",
                assessmentId = "assessment",
                assessmentTitle = "اختبار",
                workspaceId = "ws-backup",
                correctCount = 2,
                totalQuestions = 5,
                scorePercent = 40,
                passed = false,
                durationSeconds = 180,
                earnedXp = 10,
                completedAt = "2026-11-25T10:01:00Z",
                userId = "learner_b"
            )
        )
        val progress = listOf(
            LessonProgressEntity(
                workspaceId = "ws-backup",
                bookKey = "SCI",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                completed = true,
                pointsEarned = 50,
                userId = "learner_a"
            ),
            LessonProgressEntity(
                workspaceId = "ws-backup",
                bookKey = "SCI",
                unitKey = "unit_01",
                lessonKey = "lesson_01",
                completed = false,
                pointsEarned = 0,
                userId = "learner_b"
            )
        )
        val studyDays = listOf(
            StudyDayEntity(
                activityDate = "2026-11-25",
                actionsCount = 3,
                lastUpdatedAt = "2026-11-25T10:00:00Z",
                userId = "learner_a"
            ),
            StudyDayEntity(
                activityDate = "2026-11-25",
                actionsCount = 1,
                lastUpdatedAt = "2026-11-25T10:01:00Z",
                userId = "learner_b"
            )
        )

        val raw = WorkspaceExporter.serializeFullBackup(
            workspaceId = "ws-backup",
            drafts = emptyList(),
            questions = emptyList(),
            assessments = emptyList(),
            attempts = attempts,
            progress = progress,
            notes = emptyList(),
            studyDays = studyDays,
            templates = emptyList(),
            learnerProfiles = listOf(
                LearnerProfile.DEFAULT,
                LearnerProfile(userId = "learner_a", displayName = "أحمد"),
                LearnerProfile(userId = "learner_b", displayName = "سارة")
            ),
            activeLearnerId = "learner_b"
        )
        val restored = WorkspaceExporter.parseFullBackup(raw)

        assertEquals("edu-mini-backup-1.4", restored.formatVersion)
        assertEquals(setOf("learner_default", "learner_a", "learner_b"), restored.learnerProfiles.map { it.userId }.toSet())
        assertEquals("learner_b", restored.activeLearnerId)
        assertEquals(setOf("learner_a", "learner_b"), restored.attempts.map { it.userId }.toSet())
        assertEquals(setOf("learner_a", "learner_b"), restored.progress.map { it.userId }.toSet())
        assertEquals(setOf("learner_a", "learner_b"), restored.studyDayRecords.map { it.userId }.toSet())
        assertEquals(3, restored.studyDayRecords.first { it.userId == "learner_a" }.actionsCount)
        val legacy = WorkspaceExporter.parseFullBackup(
            """{"formatVersion":"edu-mini-backup-1.2","workspaceId":"ws-legacy"}"""
        )
        assertTrue(legacy.learnerProfiles.isEmpty())
        assertEquals(LearnerProfile.DEFAULT_USER_ID, legacy.activeLearnerId)
    }

    @Test
    fun providerQuotaReservationTracksDiagnosticsAndRespectsCooldownAndInvalidState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = ProviderStorage(context)
        val todayUtc = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val dayOne = todayUtc.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val provider = ProviderConfig(
            id = "quota-test-key",
            provider = ProviderType.GEMINI,
            label = "Quota test",
            model = "gemini-test",
            baseUrl = "",
            apiKey = "not-a-real-key",
            enabled = true,
            createdAt = dayOne,
            dailyRequestLimit = 1,
            quotaTimeZone = "UTC"
        )

        runBlocking {
            storage.saveProviders(listOf(provider))
            val sameDayReservations = coroutineScope {
                (1..8).map { async { storage.reserveProviderKey(provider.id, dayOne) } }.awaitAll()
            }
            // Local dailyRequestLimit is diagnostic only; all active concurrent reservations succeed.
            assertEquals(8, sameDayReservations.count { it })
            val loadedKey = storage.loadProviders().single { it.id == provider.id }
            assertEquals(8, loadedKey.dailyRequestsUsed)
            assertEquals("GEMINI_API_KEY_1", loadedKey.label)

            // A provider-confirmed exhausted quota blocks this API key for the current quota day.
            storage.updateProviderRuntimeState(provider.id, ProviderStorage.STATUS_QUOTA_EXHAUSTED, 0L)
            assertFalse(storage.reserveProviderKey(provider.id, dayOne + 30_000L))

            // Active RATE_LIMITED blocks until retryAfterEpochMs expires.
            val cooldownEnd = dayOne + 120_000L
            storage.updateProviderRuntimeState(provider.id, ProviderStorage.STATUS_RATE_LIMITED, cooldownEnd)
            assertFalse(storage.reserveProviderKey(provider.id, dayOne + 60_000L))
            assertTrue(storage.reserveProviderKey(provider.id, cooldownEnd + 1_000L))

            // Quota exhaustion clears at the next configured quota day; invalid keys do not.
            storage.updateProviderRuntimeState(provider.id, ProviderStorage.STATUS_QUOTA_EXHAUSTED, 0L)
            val nextDay = todayUtc.plusDays(1).atTime(0, 1).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            assertTrue(storage.reserveProviderKey(provider.id, nextDay))
            storage.updateProviderRuntimeState(provider.id, ProviderStorage.STATUS_INVALID_KEY, 0L)
            assertFalse(storage.reserveProviderKey(provider.id, nextDay + 60_000L))
        }
    }

    @Test
    fun templateResponseAdapterMapsProviderSpecificQuestionFields() {
        val raw = """{"items":[{"stem":"ما ناتج 2+2؟","choices":["3","4"],"answer":"4","explanation":"الجمع","level":"easy"}]}"""
        val mapping = """{"rootArray":"items","fields":{"prompt":"stem","options":"choices","correctAnswer":"answer","rationale":"explanation","difficulty":"level"}}"""

        val adapted = TemplateResponseAdapter.adapt(raw, mapping)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(adapted).jsonObject
        val questions = parsed["questions"] as kotlinx.serialization.json.JsonArray
        val question = questions.first().jsonObject

        assertEquals("ما ناتج 2+2؟", question["prompt"]?.jsonPrimitive?.content)
        assertEquals("4", question["correctAnswer"]?.jsonPrimitive?.content)
        assertEquals("الجمع", question["rationale"]?.jsonPrimitive?.content)
        assertEquals(2, (question["options"] as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun templateResponseAdapterLeavesPlainTextAndUnmappedJsonUntouched() {
        val plain = "شرح تعليمي عادي"
        assertEquals(plain, TemplateResponseAdapter.adapt(plain, """{"rootArray":"items"}"""))
        val raw = """{"title":"نشاط","steps":[]}"""
        assertEquals(raw, TemplateResponseAdapter.adapt(raw, ""))
    }

    @Test
    fun extractLessonPdfBase64PreservesOriginalPdfDocument() {
        val file = File.createTempFile("edu-mini-original-", ".pdf")
        try {
            createSamplePdf(file, 2)
            val payload = kotlinx.coroutines.runBlocking {
                AiGenerationService().extractLessonPdfBase64(file.absolutePath)
            }
            assertTrue(payload.base64.startsWith("JVBER"))
            assertTrue(payload.byteCount > 0)
            assertEquals(64, payload.sha256.length)
            val streamedHash = kotlinx.coroutines.runBlocking {
                AiGenerationService().sha256LessonPdf(file.absolutePath)
            }
            assertEquals(payload.sha256, streamedHash)
        } finally {
            file.delete()
        }
    }

    @Test
    fun providerRoutingNeverMixesGeminiAndOpenAiProfiles() {
        val profiles = listOf(
            ProviderConfig(
                id = "gemini-key",
                provider = ProviderType.GEMINI,
                label = "Gemini",
                model = "gemini-primary",
                baseUrl = "",
                apiKey = "gemini-test",
                enabled = true,
                createdAt = 1L,
                fallbackModels = listOf("gemini-fallback")
            ),
            ProviderConfig(
                id = "openai-key",
                provider = ProviderType.OPENAI_COMPATIBLE,
                label = "OpenAI",
                model = "gpt-test",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "openai-test",
                enabled = true,
                createdAt = 2L,
                fallbackModels = listOf("gpt-fallback")
            )
        )

        val geminiProfiles = com.example.data.ai.ProviderRoutingPolicy.activeProfiles(
            profiles,
            ProviderType.GEMINI
        )
        val openAiProfiles = com.example.data.ai.ProviderRoutingPolicy.activeProfiles(
            profiles,
            ProviderType.OPENAI_COMPATIBLE
        )

        assertEquals(listOf("gemini-key"), geminiProfiles.map { it.id })
        assertEquals(listOf("openai-key"), openAiProfiles.map { it.id })
        assertEquals(
            listOf("gemini-primary", "gemini-fallback"),
            com.example.data.ai.ProviderRoutingPolicy.modelCandidates(geminiProfiles.single())
        )
        assertEquals(
            listOf("gemini-selected", "gemini-primary", "gemini-fallback"),
            com.example.data.ai.ProviderRoutingPolicy.modelCandidates(geminiProfiles.single(), "gemini-selected")
        )
        val credentialOnlyProfile = geminiProfiles.single().copy(
            model = "",
            fallbackModels = listOf("gemini-primary", "gemini-fallback")
        )
        // API-key records are credential-only; model fallbacks come from the provider catalogue.
        assertEquals(
            listOf("gemini-selected"),
            com.example.data.ai.ProviderRoutingPolicy.modelCandidates(credentialOnlyProfile, "gemini-selected")
        )
        assertEquals(
            listOf("gemini-selected", "gemini-catalog-fallback"),
            com.example.data.ai.ProviderRoutingPolicy.modelCandidates(
                credentialOnlyProfile,
                "gemini-selected",
                listOf("gemini-catalog-fallback")
            )
        )
        assertEquals(
            listOf("gemini-key"),
            com.example.data.ai.ProviderRoutingPolicy.orderedProfiles(
                profiles,
                ProviderType.GEMINI,
                "gemini-key"
            ).map { it.id }
        )
        assertEquals(
            listOf("gpt-test", "gpt-fallback"),
            com.example.data.ai.ProviderRoutingPolicy.modelCandidates(openAiProfiles.single())
        )
    }

    @Test
    fun providerApiKeyAliasesAreProviderScopedAndNumbered() {
        val profiles = listOf(
            ProviderConfig(
                id = "gemini-old",
                provider = ProviderType.GEMINI,
                label = "Gemini",
                model = "",
                baseUrl = "",
                apiKey = "gemini-secret",
                enabled = true,
                createdAt = 1L
            ),
            ProviderConfig(
                id = "gemini-three",
                provider = ProviderType.GEMINI,
                label = "GEMINI_API_KEY_3",
                model = "",
                baseUrl = "",
                apiKey = "gemini-secret-3",
                enabled = true,
                createdAt = 2L
            ),
            ProviderConfig(
                id = "openai-old",
                provider = ProviderType.OPENAI_COMPATIBLE,
                label = "OpenAI",
                model = "",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "openai-secret",
                enabled = true,
                createdAt = 3L
            )
        )

        assertEquals("GEMINI_API_KEY_4", ProviderApiKeyNames.nextName(ProviderType.GEMINI, profiles))
        assertEquals("OPENAI_API_KEY_1", ProviderApiKeyNames.nextName(ProviderType.OPENAI_COMPATIBLE, profiles))

        val normalized = ProviderApiKeyNames.normalizeLegacyNames(profiles)
        assertEquals("GEMINI_API_KEY_1", normalized.first { it.id == "gemini-old" }.label)
        assertEquals("GEMINI_API_KEY_3", normalized.first { it.id == "gemini-three" }.label)
        assertEquals("OPENAI_API_KEY_1", normalized.first { it.id == "openai-old" }.label)
        assertEquals("gemini-secret", normalized.first { it.id == "gemini-old" }.apiKey)
    }

    @Test
    fun providerConnectionTestShowsCompleteGeminiResponseAndRequestMetadata() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"جاهز. هذه استجابة اختبار كاملة تتجاوز أربعين حرفاً للتأكد من عدم اقتطاع النص."}]}}]}"""
            )
        )
        server.start()
        try {
            val profile = ProviderConfig(
                id = "gemini-test",
                provider = ProviderType.GEMINI,
                label = "Gemini test",
                model = "gemini-test-model",
                baseUrl = "",
                apiKey = "gemini-secret-test-key",
                enabled = true,
                createdAt = 1L
            )
            val service = AiGenerationService(
                context = null,
                client = OkHttpClient(),
                geminiEndpointBase = server.url("/v1beta/models").toString().trimEnd('/')
            )

            val result = service.testProviderConnection(profile)
            val request = server.takeRequest()

            assertTrue(result.contains("المزود: Gemini"))
            assertTrue(result.contains("النموذج: gemini-test-model"))
            assertTrue(result.contains("زمن الاستجابة:"))
            assertTrue(result.contains("هذه استجابة اختبار كاملة تتجاوز أربعين حرفاً للتأكد من عدم اقتطاع النص."))
            assertFalse(result.contains(profile.apiKey))
            assertEquals("/v1beta/models/gemini-test-model:generateContent", request.path)
            assertEquals(profile.apiKey, request.getHeader("x-goog-api-key"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun providerConnectionTestClassifiesCredentialsQuotaModelAndRateLimitErrors() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody(
            """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(429).setBody(
            """{"error":{"message":"Quota exceeded for this project."}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(404).setBody(
            """{"error":{"message":"Model not found."}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(429).setBody(
            """{"error":{"message":"Too many requests. Please retry later."}}"""
        ))
        server.start()
        try {
            val profile = ProviderConfig(
                id = "gemini-test",
                provider = ProviderType.GEMINI,
                label = "Gemini test",
                model = "gemini-test-model",
                baseUrl = "",
                apiKey = "gemini-secret-test-key",
                enabled = true,
                createdAt = 1L
            )
            val service = AiGenerationService(
                context = null,
                client = OkHttpClient(),
                geminiEndpointBase = server.url("/v1beta/models").toString().trimEnd('/')
            )

            val invalidKey = runCatching { service.testProviderConnection(profile) }.exceptionOrNull()
            assertTrue(invalidKey is AiExecutionException)
            assertEquals(AiExecutionFailureKind.INVALID_CREDENTIALS, (invalidKey as AiExecutionException).kind)
            assertFalse(invalidKey.message.orEmpty().contains(profile.apiKey))

            val quota = runCatching { service.testProviderConnection(profile) }.exceptionOrNull()
            assertTrue(quota is AiExecutionException)
            assertEquals(AiExecutionFailureKind.QUOTA_EXHAUSTED, (quota as AiExecutionException).kind)

            val missingModel = runCatching { service.testProviderConnection(profile) }.exceptionOrNull()
            assertTrue(missingModel is AiExecutionException)
            assertEquals(AiExecutionFailureKind.MODEL_UNAVAILABLE, (missingModel as AiExecutionException).kind)

            val rateLimited = runCatching { service.testProviderConnection(profile) }.exceptionOrNull()
            assertTrue(rateLimited is AiExecutionException)
            assertEquals(AiExecutionFailureKind.RATE_LIMITED, (rateLimited as AiExecutionException).kind)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun geminiResourceExhaustedWithoutQuotaMetadataRemainsRateLimited() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(429).setBody(
                """{"error":{"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED"}}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"message":"Permission denied","status":"PERMISSION_DENIED"}}"""
            )
        )
        server.start()
        try {
            val profile = ProviderConfig(
                id = "gemini-classifier-test",
                provider = ProviderType.GEMINI,
                label = "Gemini classifier test",
                model = "gemini-test-model",
                baseUrl = "",
                apiKey = "gemini-classifier-secret",
                enabled = true,
                createdAt = 1L
            )
            val service = AiGenerationService(
                context = null,
                client = OkHttpClient(),
                geminiEndpointBase = server.url("/v1beta/models").toString().trimEnd('/')
            )

            val ambiguousExhaustion = runCatching {
                service.testProviderConnection(profile)
            }.exceptionOrNull()
            assertTrue(ambiguousExhaustion is AiExecutionException)
            assertEquals(
                AiExecutionFailureKind.RATE_LIMITED,
                (ambiguousExhaustion as AiExecutionException).kind
            )

            val permissionDenied = runCatching {
                service.testProviderConnection(profile)
            }.exceptionOrNull()
            assertTrue(permissionDenied is AiExecutionException)
            assertEquals(
                AiExecutionFailureKind.PROVIDER_UNAVAILABLE,
                (permissionDenied as AiExecutionException).kind
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun providerConnectionTestRejectsEmptyOpenAiCompatibleResponse() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"content":""},"finish_reason":"stop"}]}"""
            )
        )
        server.start()
        try {
            val profile = ProviderConfig(
                id = "openai-test",
                provider = ProviderType.OPENAI_COMPATIBLE,
                label = "OpenAI test",
                model = "gpt-test-model",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                apiKey = "openai-secret-test-key",
                enabled = true,
                createdAt = 1L
            )
            val service = AiGenerationService(context = null, client = OkHttpClient())

            val error = runCatching { service.testProviderConnection(profile) }.exceptionOrNull()
            assertTrue(error is AiExecutionException)
            assertEquals(AiExecutionFailureKind.EMPTY_RESPONSE, (error as AiExecutionException).kind)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer ${profile.apiKey}", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun providerConnectionTestMapsNetworkFailureSeparately() = runBlocking {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/v1beta/models").toString().trimEnd('/')
        server.shutdown()
        val profile = ProviderConfig(
            id = "gemini-network-test",
            provider = ProviderType.GEMINI,
            label = "Gemini network test",
            model = "gemini-test-model",
            baseUrl = "",
            apiKey = "gemini-secret-test-key",
            enabled = true,
            createdAt = 1L
        )
        val service = AiGenerationService(
            context = null,
            client = OkHttpClient(),
            geminiEndpointBase = baseUrl
        )

        val error = runCatching { service.testProviderConnection(profile) }.exceptionOrNull()
        assertTrue(error is AiExecutionException)
        assertEquals(AiExecutionFailureKind.NETWORK_ERROR, (error as AiExecutionException).kind)
    }

    @Test
    fun strictProviderProfileDoesNotUseFallbackModels() {
        val profile = ProviderConfig(
            id = "strict-gemini",
            provider = ProviderType.GEMINI,
            label = "Strict Gemini",
            model = "gemini-primary",
            baseUrl = "",
            apiKey = "test",
            enabled = true,
            createdAt = 1L,
            fallbackModels = listOf("gemini-fallback"),
            strictModel = true
        )

        assertEquals(
            listOf("gemini-primary"),
            com.example.data.ai.ProviderRoutingPolicy.modelCandidates(profile)
        )
    }

    @Test
    fun explicitCatalogueModelSelectionNeverAddsProfileFallbacks() {
        assertEquals(
            listOf("gemini-3.8-flash"),
            com.example.data.ai.ProviderRoutingPolicy.exactModelCandidate("gemini-3.8-flash")
        )
        assertEquals(
            listOf("gpt-enterprise-v7"),
            com.example.data.ai.ProviderRoutingPolicy.exactModelCandidate(" gpt-enterprise-v7 ")
        )
        assertTrue(runCatching {
            com.example.data.ai.ProviderRoutingPolicy.exactModelCandidate(" ")
        }.isFailure)
    }

    @Test
    fun geminiModelCooldownDoesNotBlockSiblingModelsOrOtherKeys() {
        val router = com.example.data.ai.GeminiModelRouter(defaultCooldownMs = 60_000L)
        val now = 1_000_000L
        router.markCoolingDown("key-a", "gemini-3.8-flash", now + 30_000L, now)

        assertTrue(router.isCoolingDown("key-a", "gemini-3.8-flash", now + 1_000L))
        assertFalse(router.isCoolingDown("key-a", "gemini-3.7-flash", now + 1_000L))
        assertFalse(router.isCoolingDown("key-b", "gemini-3.8-flash", now + 1_000L))
        assertFalse(router.isCoolingDown("key-a", "gemini-3.8-flash", now + 61_000L))
    }

    @Test
    fun geminiRetriesThreeTimesThenUsesNextCatalogueModel() {
        val server = ServerSocket(0)
        val requestLines = java.util.Collections.synchronizedList(mutableListOf<String>())
        val serverFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val successBody = """{"candidates":[{"content":{"parts":[{"text":"fallback model response"}]}}],"usageMetadata":{"totalTokenCount":3}}"""
        val thread = Thread {
            try {
                repeat(4) { index ->
                    server.accept().use { socket ->
                        val request = readTestHttpRequest(socket)
                        requestLines.add(request.lineSequence().firstOrNull().orEmpty())
                        if (index < 3) {
                            writeTestHttpResponse(socket, 503, """{"error":{"message":"Service unavailable","status":"UNAVAILABLE"}}""")
                        } else {
                            writeTestHttpResponse(socket, 200, successBody)
                        }
                    }
                }
            } catch (error: Throwable) {
                serverFailure.set(error)
            }
        }
        thread.start()
        try {
            val profile = ProviderConfig(
                id = "gemini-retry-profile",
                provider = ProviderType.GEMINI,
                label = "GEMINI_API_KEY_1",
                model = "",
                baseUrl = "",
                apiKey = "gemini-retry-key",
                enabled = true,
                createdAt = 1L
            )
            val generated = runBlocking {
                AiGenerationService(
                    geminiEndpointBase = "http://127.0.0.1:${server.localPort}/v1beta/models"
                ).execute(
                    com.example.data.ai.AiExecutionRequest(
                        prompt = "Return a short response",
                        providers = listOf(profile),
                        selectedProvider = ProviderType.GEMINI,
                        requestedModelId = "gemini-primary",
                        requestedProfileId = profile.id,
                        modelFallbackIds = listOf("gemini-fallback")
                    )
                )
            }
            thread.join(5_000L)
            assertEquals("fallback model response", generated.text)
            assertEquals("gemini-fallback", generated.model)
            assertEquals(4, requestLines.size)
            assertEquals(
                listOf(
                    "POST /v1beta/models/gemini-primary:generateContent HTTP/1.1",
                    "POST /v1beta/models/gemini-primary:generateContent HTTP/1.1",
                    "POST /v1beta/models/gemini-primary:generateContent HTTP/1.1",
                    "POST /v1beta/models/gemini-fallback:generateContent HTTP/1.1"
                ),
                requestLines.toList()
            )
            assertNull(serverFailure.get())
        } finally {
            runCatching { server.close() }
            thread.join(2_000L)
        }
    }

    @Test
    fun geminiQuotaExhaustionMovesDirectlyToNextSameProviderProfile() {
        val server = ServerSocket(0)
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val statuses = java.util.Collections.synchronizedList(mutableListOf<String>())
        val thread = Thread {
            repeat(2) { index ->
                server.accept().use { socket ->
                    requests.add(readTestHttpRequest(socket))
                    if (index == 0) {
                        writeTestHttpResponse(
                            socket,
                            429,
                            """{"error":{"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED","details":[{"metadata":{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests"}}]}}"""
                        )
                    } else {
                        writeTestHttpResponse(
                            socket,
                            200,
                            """{"candidates":[{"content":{"parts":[{"text":"second key success"}]}}]}"""
                        )
                    }
                }
            }
        }
        thread.start()
        try {
            val profiles = listOf(
                ProviderConfig("gemini-a", ProviderType.GEMINI, "Gemini A", "gemini-primary", "", "key-a", true, 1L),
                ProviderConfig("gemini-b", ProviderType.GEMINI, "Gemini B", "gemini-primary", "", "key-b", true, 2L)
            )
            val generated = runBlocking {
                AiGenerationService(
                    geminiEndpointBase = "http://127.0.0.1:${server.localPort}/v1beta/models"
                ).execute(
                    com.example.data.ai.AiExecutionRequest(
                        prompt = "Return a short response",
                        providers = profiles,
                        selectedProvider = ProviderType.GEMINI,
                        requestedModelId = "gemini-primary",
                        requestedProfileId = "gemini-a",
                        onProviderStateChanged = { _, status, _ -> statuses.add(status) }
                    )
                )
            }
            thread.join(5_000L)
            assertEquals("second key success", generated.text)
            assertEquals("Gemini B", generated.providerLabel)
            assertEquals(2, requests.size)
            assertTrue(requests[0].contains("x-goog-api-key: key-a", ignoreCase = true))
            assertTrue(requests[1].contains("x-goog-api-key: key-b", ignoreCase = true))
            assertTrue(statuses.contains("QUOTA_EXHAUSTED"))
        } finally {
            runCatching { server.close() }
            thread.join(2_000L)
        }
    }

    @Test
    fun openAiQuotaExhaustionMovesToNextOpenAiKeyWithoutChangingProvider() {
        val server = ServerSocket(0)
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val thread = Thread {
            repeat(2) { index ->
                server.accept().use { socket ->
                    requests.add(readTestHttpRequest(socket))
                    if (index == 0) {
                        writeTestHttpResponse(
                            socket,
                            429,
                            """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota","code":"insufficient_quota"}}"""
                        )
                    } else {
                        writeTestHttpResponse(
                            socket,
                            200,
                            """{"choices":[{"message":{"content":"openai second key success"}}]}"""
                        )
                    }
                }
            }
        }
        thread.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.localPort}/v1"
            val profiles = listOf(
                ProviderConfig("openai-a", ProviderType.OPENAI_COMPATIBLE, "OpenAI A", "gpt-test", baseUrl, "key-a", true, 1L),
                ProviderConfig("openai-b", ProviderType.OPENAI_COMPATIBLE, "OpenAI B", "gpt-test", baseUrl, "key-b", true, 2L)
            )
            val generated = runBlocking {
                AiGenerationService().execute(
                    com.example.data.ai.AiExecutionRequest(
                        prompt = "Return a short response",
                        providers = profiles,
                        selectedProvider = ProviderType.OPENAI_COMPATIBLE,
                        requestedModelId = "gpt-test",
                        requestedProfileId = "openai-a"
                    )
                )
            }
            thread.join(5_000L)
            assertEquals("openai second key success", generated.text)
            assertEquals("OpenAI B", generated.providerLabel)
            assertEquals(2, requests.size)
            assertTrue(requests[0].contains("Authorization: Bearer key-a", ignoreCase = true))
            assertTrue(requests[1].contains("Authorization: Bearer key-b", ignoreCase = true))
        } finally {
            runCatching { server.close() }
            thread.join(2_000L)
        }
    }

    private fun readTestHttpRequest(socket: Socket): String {
        val input = BufferedInputStream(socket.getInputStream())
        val headers = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val value = input.read()
            if (value < 0) break
            headers.write(value)
            matched = when {
                matched == 0 && value == 13 -> 1
                matched == 1 && value == 10 -> 2
                matched == 2 && value == 13 -> 3
                matched == 3 && value == 10 -> 4
                value == 13 -> 1
                else -> 0
            }
        }
        val headerText = headers.toString(Charsets.US_ASCII.name())
        val contentLength = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(headerText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        var remaining = contentLength
        val buffer = ByteArray(8 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            remaining -= count
        }
        return headerText
    }

    private fun writeTestHttpResponse(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else if (status == 429) "Too Many Requests" else "Service Unavailable"
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        val output = socket.getOutputStream()
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    @Test
    fun selectedProviderPreferencePersistsIndependentlyFromProviderProfiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = ProviderStorage(context)
        storage.saveSelectedProviderType(ProviderType.OPENAI_COMPATIBLE)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, storage.loadSelectedProviderType())
        storage.saveSelectedProviderType(ProviderType.GEMINI)
        assertEquals(ProviderType.GEMINI, storage.loadSelectedProviderType())
    }

    private fun sampleLesson() = LessonRef(
        grade = "G04",
        rootPath = "/tmp/ws",
        bookKey = "ARAB",
        bookTitle = "لغتي العربية",
        subjectKey = "ARAB",
        unitKey = "unit_01",
        unitTitle = "الوحدة الأولى",
        lessonKey = "lesson_01",
        title = "الدرس الأول",
        pageRange = "5–10",
        workspaceId = "ws-test"
    )

    private fun createSamplePdf(outFile: File, pageCount: Int) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            repeat(pageCount) { index ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(48f, 740f)
                    stream.showText("Sample textbook lesson page ${index + 1} with searchable PDF text.")
                    stream.endText()
                }
            }
            document.save(outFile)
        }
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

    @Test
    fun learningProgressAnalyticsUsesOneExplicitZoneForDayAndWeekBoundaries() {
        val zone = java.time.ZoneId.of("Pacific/Kiritimati")
        val questions = (1..3).map { index ->
            QuestionItem(
                id = "zone-q$index",
                workspaceId = "ws-zone",
                gradeKey = "G07",
                bookKey = "SCI",
                unitKey = "unit-01",
                lessonKey = "lesson-01",
                lessonTitle = "الكسور",
                type = QuestionType.MCQ,
                prompt = "سؤال $index",
                options = listOf("أ", "ب"),
                correctAnswer = "أ",
                rationale = "التفسير",
                difficulty = QuestionDifficulty.MEDIUM,
                conceptTag = "fractions"
            )
        }
        val attempts = listOf(
            QuestionAttemptRecord(
                id = "zone-a1", workspaceId = "ws-zone", userId = "learner_a",
                questionId = "zone-q1", conceptTag = "fractions", selectedAnswer = "أ",
                isCorrect = true, sourceMode = "lesson_quiz", answeredAt = "2026-11-01T09:30:00Z"
            ),
            QuestionAttemptRecord(
                id = "zone-a2", workspaceId = "ws-zone", userId = "learner_a",
                questionId = "zone-q2", conceptTag = "fractions", selectedAnswer = "أ",
                isCorrect = true, sourceMode = "lesson_quiz", answeredAt = "2026-11-01T10:30:00Z"
            ),
            QuestionAttemptRecord(
                id = "zone-a3", workspaceId = "ws-zone", userId = "learner_a",
                questionId = "zone-q3", conceptTag = "fractions", selectedAnswer = "أ",
                isCorrect = true, sourceMode = "lesson_quiz", answeredAt = "2026-11-01T10:45:00Z"
            )
        )
        val report = LearningProgressAnalytics.calculate(
            questions = questions,
            attempts = attempts,
            assessments = emptyList(),
            now = java.time.Instant.parse("2026-11-02T12:00:00Z"),
            zoneId = zone
        )
        // The first attempt is still on Nov 1 locally, while the next two are on Nov 2.
        assertEquals(1, report.stableConceptsCount)
    }


    @Test
    fun studioPreviewCheckpointAttemptsDoNotAffectLearnerMasteryOrProgress() {
        val question = QuestionItem(
            id = "studio-preview-q",
            workspaceId = "ws-preview",
            gradeKey = "G07",
            bookKey = "SCI",
            unitKey = "unit-01",
            lessonKey = "lesson-01",
            lessonTitle = "الطاقة",
            type = QuestionType.MCQ,
            prompt = "ما مصدر الطاقة؟",
            options = listOf("الشمس", "القمر"),
            correctAnswer = "الشمس",
            rationale = "التفسير",
            difficulty = QuestionDifficulty.EASY,
            conceptTag = "energy"
        )
        val previewAttempt = QuestionAttemptRecord(
            id = "studio-preview-attempt",
            workspaceId = "ws-preview",
            userId = "learner_default",
            questionId = question.id,
            conceptTag = "energy",
            selectedAnswer = "القمر",
            isCorrect = false,
            sourceMode = "studio_preview",
            answeredAt = "2026-11-25T10:00:00Z"
        )
        val mastery = ConceptMasteryEngine.computeQuestionStats(listOf(question), listOf(previewAttempt))
        assertEquals(MasteryStatus.UNSEEN, mastery.getValue(question.id).status)
        val report = LearningProgressAnalytics.calculate(
            questions = listOf(question),
            attempts = listOf(previewAttempt),
            assessments = emptyList()
        )
        assertEquals(0, report.questionAttemptsCount)
        assertNull(report.firstTryAccuracyPercent)
        assertNull(report.latestAccuracyPercent)
    }


    @Test
    fun templateOutputValidatorRejectsInvalidSchemaAndMalformedAiOutput() {
        val schema = """
            {
              "type": "OBJECT",
              "properties": {
                "questions": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "prompt": { "type": "STRING" },
                      "correctAnswer": { "type": "STRING" }
                    },
                    "required": ["prompt", "correctAnswer"]
                  }
                }
              },
              "required": ["questions"]
            }
        """.trimIndent()
        assertTrue(TemplateOutputValidator.validateSchema(schema).isEmpty())
        val valid = """{"questions":[{"prompt":"ما ناتج 2+2؟","correctAnswer":"4"}]}"""
        assertTrue(TemplateOutputValidator.validateOutput(valid, schema).isEmpty())
        val invalid = """{"questions":[{"prompt":"ما ناتج 2+2؟","correctAnswer":4}]}"""
        val issues = TemplateOutputValidator.validateOutput(invalid, schema)
        assertTrue(issues.any { it.contains("correctAnswer") && it.contains("expected string") })
        val malformedSchema = """{"type":"OBJECT","properties":{"prompt":{"type":"MAGIC"}}}"""
        assertTrue(TemplateOutputValidator.validateSchema(malformedSchema).isNotEmpty())
        assertTrue(TemplateOutputValidator.validateOutput("not-json", schema).isNotEmpty())
    }


    @Test
    fun roomMigrations13To16PreserveRowsAndApplyLearnerAndTemplateDefaults() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-13-16-test.db"
        context.deleteDatabase(dbName)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE books (rowId INTEGER NOT NULL PRIMARY KEY, workspaceId TEXT NOT NULL, bookKey TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE lessons (rowId INTEGER NOT NULL PRIMARY KEY, workspaceId TEXT NOT NULL, bookKey TEXT NOT NULL, unitKey TEXT NOT NULL, lessonKey TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE lesson_progress (workspace_id TEXT NOT NULL, book_key TEXT NOT NULL, unit_key TEXT NOT NULL, lesson_key TEXT NOT NULL, completed INTEGER NOT NULL, points_earned INTEGER NOT NULL, completed_at TEXT, PRIMARY KEY(workspace_id, book_key, unit_key, lesson_key))")
                    db.execSQL("CREATE TABLE study_days (activity_date TEXT NOT NULL PRIMARY KEY, actions_count INTEGER NOT NULL, last_updated_at TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE assessment_attempts (id TEXT NOT NULL PRIMARY KEY, workspace_id TEXT NOT NULL, completed_at TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE prompt_templates (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("CREATE TABLE generation_drafts (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("INSERT INTO books (rowId, workspaceId, bookKey) VALUES (1, 'ws', 'SCI'), (2, 'ws', 'SCI')")
                    db.execSQL("INSERT INTO lessons (rowId, workspaceId, bookKey, unitKey, lessonKey) VALUES (1, 'ws', 'SCI', 'u1', 'l1'), (2, 'ws', 'SCI', 'u1', 'l1')")
                    db.execSQL("INSERT INTO lesson_progress VALUES ('ws', 'SCI', 'u1', 'l1', 1, 50, '2026-11-25T10:00:00Z')")
                    db.execSQL("INSERT INTO study_days VALUES ('2026-11-25', 3, '2026-11-25T10:00:00Z')")
                    db.execSQL("INSERT INTO assessment_attempts (id, workspace_id, completed_at) VALUES ('attempt-1', 'ws', '2026-11-25T10:00:00Z')")
                    db.execSQL("INSERT INTO prompt_templates (id) VALUES ('template-1')")
                    db.execSQL("INSERT INTO generation_drafts (id) VALUES ('draft-1')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            val db = helper.writableDatabase
            EduMiniDatabase.MIGRATION_13_14.migrate(db)
            EduMiniDatabase.MIGRATION_14_15.migrate(db)
            EduMiniDatabase.MIGRATION_15_16.migrate(db)

            fun scalar(sql: String): Int = db.query(sql).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
            assertEquals(1, scalar("SELECT COUNT(*) FROM books"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM lessons"))
            assertEquals(1, scalar("SELECT rowId FROM books"))
            assertEquals(1, scalar("SELECT rowId FROM lessons"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM lesson_progress WHERE user_id = 'learner_default' AND completed = 1 AND points_earned = 50"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM study_days WHERE user_id = 'learner_default' AND actions_count = 3"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM assessment_attempts WHERE user_id = 'learner_default'"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM prompt_templates WHERE response_schema_json = '' AND response_mapping_json = '' AND response_format = 'text' AND source_attachment_mode = 'pdf_native' AND version = 1"))
            assertEquals(1, scalar("SELECT COUNT(*) FROM generation_drafts WHERE template_id = '' AND template_version = 1 AND response_schema_hash = '' AND request_fingerprint = '' AND content_hash = '' AND source_pdf_hash = ''"))
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }


    @Test
    fun promptPrivacySanitizerRedactsDirectIdentifiersWithoutRemovingLessonNumbers() {
        val source = "البريد student@example.com، الهاتف +967 777 123 456، رقم الهوية: AB-12345678. ناتج 12 × 8 = 96 والمسافة 1200 متر."
        val sanitized = PromptPrivacySanitizer.sanitize(source)
        assertFalse(sanitized.contains("student@example.com"))
        assertFalse(sanitized.contains("+967 777 123 456"))
        assertFalse(sanitized.contains("AB-12345678"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
        assertTrue(sanitized.contains("[REDACTED_PHONE]"))
        assertTrue(sanitized.contains("[REDACTED_ID]"))
        assertTrue(sanitized.contains("12 × 8 = 96"))
        assertTrue(sanitized.contains("1200 متر"))

        val prompt = AiGenerationService().buildPrompt(
            lesson = sampleLesson(),
            task = TaskType.QUESTIONS,
            source = "درس تجريبي يتضمن teacher@example.com ومعادلة 5 + 7 = 12",
            extra = "رقم الهاتف: 777123456"
        )
        assertFalse(prompt.contains("teacher@example.com"))
        assertFalse(prompt.contains("777123456"))
        assertTrue(prompt.contains("5 + 7 = 12"))
    }


    @Test
    fun fullBackupRestoreWritesLearnerProfilesAndActiveIdToLocalPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = EduMiniDatabase.getInstance(context)
        val repository = EduMiniRepository(
            dao = database.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context),
            appContext = context
        )
        val backup = WorkspaceExporter.serializeFullBackup(
            workspaceId = "ws-profile-restore",
            drafts = emptyList(),
            questions = emptyList(),
            assessments = emptyList(),
            attempts = emptyList(),
            progress = emptyList(),
            notes = emptyList(),
            studyDays = emptyList(),
            templates = emptyList(),
            learnerProfiles = listOf(
                LearnerProfile.DEFAULT,
                LearnerProfile(userId = "learner_restore", displayName = "ملف مستعاد")
            ),
            activeLearnerId = "learner_restore"
        )
        repository.importFullBackupJson(backup, "ws-profile-restore")
        val profilesJson = repository.readLearnerPref("edumini_learner_profiles", "profiles_json")
        val activeId = repository.readLearnerPref("edumini_learner_profiles", "active_learner_id")
        val restored = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<LearnerProfile>>(profilesJson.orEmpty())
        assertEquals(setOf("learner_default", "learner_restore"), restored.map { it.userId }.toSet())
        assertEquals("learner_restore", activeId)
    }

    @Test
    fun questionBankConceptInferencePreservesExplicitTagsAndUsesArabicKeywordFallbacks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = EduMiniDatabase.getInstance(context)
        val service = com.example.data.repository.QuestionBankService(database.eduMiniDao())
        assertEquals(
            "مفهوم الضرب",
            service.inferConceptTag(
                explicitTag = "",
                prompt = "احسب جداء 4 في 5",
                rationale = "نضرب العددين",
                lessonTitle = "العمليات"
            )
        )
        assertEquals(
            "الطاقة الحركية",
            service.inferConceptTag(
                explicitTag = "الطاقة الحركية",
                prompt = "ما ناتج جمع 2 + 3؟",
                rationale = "جمع عددين",
                lessonTitle = "العمليات"
            )
        )
        assertEquals(
            "درس بلا كلمات مفتاحية",
            service.inferConceptTag(
                explicitTag = "",
                prompt = "صف الظاهرة المذكورة",
                rationale = "إجابة وصفية",
                lessonTitle = "درس بلا كلمات مفتاحية"
            )
        )
    }

    @Test
    fun observeDraftsForLessonPreservesAllProvenanceFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = EduMiniRepository(
                dao = database.eduMiniDao(),
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                appContext = context
            )
            val lesson = sampleLesson()
            val generated = GeneratedResult(
                text = "محتوى مولّد",
                providerLabel = "Gemini",
                model = "gemini-2.5-flash",
                createdAt = "2026-11-25T12:00:00Z",
                templateId = "tpl-custom-1",
                templateVersion = 4,
                responseSchemaHash = "a".repeat(64),
                requestFingerprint = "b".repeat(64),
                contentHash = "c".repeat(64),
                sourcePdfHash = "d".repeat(64)
            )
            val saved = repository.persistDraft(
                workspaceId = lesson.workspaceId,
                lesson = lesson,
                task = TaskType.EXPLANATION,
                providerLabel = generated.providerLabel,
                model = generated.model,
                text = generated.text,
                title = lesson.title,
                sourceText = "نص الدرس",
                status = DraftStatus.DRAFT,
                generation = generated
            )
            val observed = repository.observeDraftsForLesson(lesson.workspaceId, lesson).first().single { it.id == saved.id }
            assertEquals("tpl-custom-1", observed.templateId)
            assertEquals(4, observed.templateVersion)
            assertEquals("a".repeat(64), observed.responseSchemaHash)
            assertEquals("b".repeat(64), observed.requestFingerprint)
            assertEquals("d".repeat(64), observed.sourcePdfHash)
            assertEquals(64, observed.contentHash.length)
        } finally {
            database.close()
        }
    }

    @Test
    fun generateContentAllowsOpenAiTextOnlyRunWithoutPdfPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val server = java.net.ServerSocket(0)
        val serverThread = Thread {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val headerBuf = ByteArrayOutputStream()
                    var prev3 = 0
                    var prev2 = 0
                    var prev1 = 0
                    while (true) {
                        val b = input.read()
                        if (b == -1) break
                        headerBuf.write(b)
                        if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break
                        prev3 = prev2
                        prev2 = prev1
                        prev1 = b
                    }
                    val headersText = headerBuf.toString(Charsets.US_ASCII.name())
                    val contentLength = headersText.lineSequence()
                        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                        ?.substringAfter(":")
                        ?.trim()
                        ?.toIntOrNull()
                        ?: 0
                    var remaining = contentLength
                    val drainBuf = ByteArray(1024)
                    while (remaining > 0) {
                        val read = input.read(drainBuf, 0, minOf(remaining, drainBuf.size))
                        if (read <= 0) break
                        remaining -= read
                    }
                    val payload = """{"choices":[{"message":{"content":"شرح نصي من OpenAI بدون PDF"}}]}"""
                    val responseBytes = payload.toByteArray(Charsets.UTF_8)
                    val out = socket.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                    )
                    out.write(responseBytes)
                    out.flush()
                }
            }
        }
        serverThread.start()
        try {
            val providerStorage = ProviderStorage(context)
            val providers = listOf(
                ProviderConfig(
                    id = "openai-text-only",
                    provider = ProviderType.OPENAI_COMPATIBLE,
                    label = "Local Mock OpenAI",
                    model = "gpt-test",
                    baseUrl = "http://127.0.0.1:${server.localPort}/v1",
                    apiKey = "test-openai-key",
                    enabled = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            providerStorage.saveProviders(providers)
            val repository = EduMiniRepository(
                dao = database.eduMiniDao(),
                providerStorage = providerStorage,
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                appContext = context
            )
            val lessonWithoutPdf = sampleLesson().copy(pdfPath = null)
            val template = PromptTemplateRecord(
                id = "tpl-text-only",
                title = "Text Only",
                task = TaskType.EXPLANATION,
                instructions = "اشرح الدرس بوضوح",
                responseFormat = "text",
                sourceAttachmentMode = "pdf_native"
            )
            val generated = repository.generateContent(
                workspaceId = lessonWithoutPdf.workspaceId,
                lesson = lessonWithoutPdf,
                task = TaskType.EXPLANATION,
                source = "محتوى نصي كافٍ للشرح بدون ملف PDF",
                extra = "",
                providers = providers,
                selectedProvider = ProviderType.OPENAI_COMPATIBLE,
                selectedModelId = "gpt-test",
                selectedProfileId = "openai-text-only",
                questionCount = 5,
                attachPdfPages = true,
                template = template
            )
            assertEquals("شرح نصي من OpenAI بدون PDF", generated.text)
            assertEquals("Local Mock OpenAI", generated.providerLabel)
            assertEquals("gpt-test", generated.model)
        } finally {
            runCatching { server.close() }
            serverThread.join(2000)
            database.close()
        }
    }

    @Test
    fun openAiCompatibleContentArraySegmentsAreConcatenated() {
        val server = ServerSocket(0)
        val thread = Thread {
            server.accept().use { socket ->
                readTestHttpRequest(socket)
                writeTestHttpResponse(
                    socket,
                    200,
                    """{"choices":[{"message":{"content":[{"type":"text","text":"segment one"},{"type":"text","text":" segment two"}]}}]}"""
                )
            }
        }
        thread.start()
        try {
            val profile = ProviderConfig(
                id = "openai-array-content",
                provider = ProviderType.OPENAI_COMPATIBLE,
                label = "OpenAI array content",
                model = "gpt-test",
                baseUrl = "http://127.0.0.1:${server.localPort}/v1",
                apiKey = "test-openai-key",
                enabled = true,
                createdAt = 1L
            )
            val generated = runBlocking {
                AiGenerationService().execute(
                    com.example.data.ai.AiExecutionRequest(
                        prompt = "Return text",
                        providers = listOf(profile),
                        selectedProvider = ProviderType.OPENAI_COMPATIBLE,
                        requestedModelId = "gpt-test",
                        requestedProfileId = profile.id,
                        onProviderStateChanged = { _, _, _ -> throw IllegalStateException("state store unavailable") },
                        onAttemptFinished = { _, _, _, _ -> throw IllegalStateException("attempt log unavailable") }
                    )
                )
            }
            thread.join(3_000L)
            assertEquals("segment one segment two", generated.text)
        } finally {
            runCatching { server.close() }
            thread.join(2_000L)
        }
    }

    @Test
    fun questionResponseWithContractErrorsIsReturnedForReviewInsteadOfThrownAway() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val server = ServerSocket(0)
        val rawResponse = """{"questions":[]}"""
        val serverThread = Thread {
            server.accept().use { socket ->
                readTestHttpRequest(socket)
                writeTestHttpResponse(socket, 200, """{"choices":[{"message":{"content":"{\"questions\":[]}"}}]}""")
            }
        }
        serverThread.start()
        try {
            val providerStorage = ProviderStorage(context)
            val providers = listOf(
                ProviderConfig(
                    id = "openai-invalid-content",
                    provider = ProviderType.OPENAI_COMPATIBLE,
                    label = "OpenAI mock",
                    model = "gpt-test",
                    baseUrl = "http://127.0.0.1:${server.localPort}/v1",
                    apiKey = "test-openai-key",
                    enabled = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            providerStorage.saveProviders(providers)
            val repository = EduMiniRepository(
                dao = database.eduMiniDao(),
                providerStorage = providerStorage,
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                appContext = context
            )
            val lesson = sampleLesson().copy(pdfPath = null)
            val template = PromptTemplateRecord(
                id = "tpl-invalid-question-batch",
                title = "Invalid question batch",
                task = TaskType.QUESTIONS,
                instructions = "أنشئ أسئلة وفق العقد",
                responseFormat = "json",
                sourceAttachmentMode = "none"
            )
            val generated = repository.generateContent(
                workspaceId = lesson.workspaceId,
                lesson = lesson,
                task = TaskType.QUESTIONS,
                source = "مصدر تعليمي كافٍ لإنشاء الأسئلة",
                extra = "",
                providers = providers,
                selectedProvider = ProviderType.OPENAI_COMPATIBLE,
                selectedModelId = "gpt-test",
                selectedProfileId = "openai-invalid-content",
                questionCount = 2,
                attachPdfPages = false,
                questionTypeIds = listOf("mcq"),
                difficultyHint = "easy",
                template = template
            )

            serverThread.join(3_000L)
            assertEquals(rawResponse, generated.text)
            assertTrue(generated.validationIssues.isNotEmpty())
            assertTrue(generated.validationIssues.any { it.contains("العدد الناتج") || it.contains("questions") })
            assertEquals("OpenAI mock", generated.providerLabel)
        } finally {
            runCatching { server.close() }
            serverThread.join(2_000L)
            database.close()
        }
    }

    @Test
    fun directWorkspaceBinderIsolatesBooksAndGradesAndCountsGradeRootFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDir = File(context.cacheDir, "direct_ws_isolation_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val g04Dir = File(rootDir, "workspace/G04").apply { mkdirs() }
            val g04Math = File(g04Dir, "MATH").apply { mkdirs() }
            val g04Sci = File(g04Dir, "SCI").apply { mkdirs() }
            val g07Math = File(rootDir, "workspace/G07/MATH").apply { mkdirs() }

            fun writeBook(dir: File, grade: String, subject: String, sliceUri: String? = null) {
                val sliceJson = if (sliceUri != null) ""","sliceFile":{"workspaceUri":"$sliceUri"}""" else ""
                File(dir, "book_manifest.json").writeText("""{"title":"$grade-$subject","gradeKey":"$grade","subjectKey":"$subject"}""")
                File(dir, "index.json").writeText(
                    """
                    {
                      "gradeKey": "$grade",
                      "subjectKey": "$subject",
                      "units": [{
                        "unitKey": "unit_01",
                        "title": "الوحدة 1",
                        "partKey": "P01",
                        "lessons": [{
                          "lessonKey": "lesson_01",
                          "title": "الدرس 1"
                          $sliceJson
                        }]
                      }]
                    }
                    """.trimIndent()
                )
            }

            writeBook(g04Math, "G04", "MATH", sliceUri = "custom_slices/l1.pdf")
            writeBook(g04Sci, "G04", "SCI")
            writeBook(g07Math, "G07", "MATH")

            val g04MathSlice = File(g04Math, "custom_slices/l1.pdf").apply {
                parentFile?.mkdirs()
                writeText("%PDF-1.4 math")
            }
            val sciTxt = File(g04Sci, "P01/units/unit_01/lesson_01.txt").apply {
                parentFile?.mkdirs()
                writeText("نص العلوم فقط")
            }
            val sciHtml = File(g04Sci, "P01/units/unit_01/lesson_01_interactive.html").apply {
                writeText("<!doctype html><html><body>SCI</body></html>")
            }
            File(g07Math, "questions/unit_01/lesson_01").apply { mkdirs() }
                .let { File(it, "ai_generated.json") }
                .writeText("""{"questions":[{"prompt":"سؤال سابع","options":["أ","ب"],"correctAnswer":"أ"}]}""")

            val binder = DirectWorkspaceBinder(context)
            val boundG04 = binder.bindSingleGrade(rootDir.absolutePath, "G04")
            val mathLesson = boundG04.workspace.lessons.single { it.bookKey == "MATH" }
            val sciLesson = boundG04.workspace.lessons.single { it.bookKey == "SCI" }

            // Book-relative sliceFile.workspaceUri resolves within G04/MATH without leaking SCI assets or G07 questions.
            assertEquals(g04MathSlice.absolutePath, mathLesson.pdfPath)
            assertNull(mathLesson.sourceTextPath)
            assertNull(mathLesson.interactivePath)
            assertTrue(boundG04.preloadedQuestions.isEmpty())
            assertEquals(sciTxt.absolutePath, sciLesson.sourceTextPath)
            assertEquals(sciHtml.absolutePath, sciLesson.interactivePath)

            // Direct binding when user picks workspace/G04 itself reports positive non-zero filesCount.
            val directGradeInspection = binder.inspectDirectory(g04Dir)
            assertEquals(1, directGradeInspection.grades.size)
            val directGradeBound = binder.bindSingleGrade(g04Dir.absolutePath, "G04")
            assertTrue(directGradeBound.workspace.filesCount >= 6)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun directWorkspaceCatalogRefreshUpdatesPreloadedHtmlPackageInPlace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val rootDir = File(context.cacheDir, "direct_ws_refresh_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val mathDir = File(rootDir, "workspace/G04/MATH").apply { mkdirs() }
            File(mathDir, "book_manifest.json").writeText("""{"title":"الرياضيات","gradeKey":"G04","subjectKey":"MATH"}""")
            File(mathDir, "index.json").writeText(
                """
                {
                  "gradeKey": "G04",
                  "subjectKey": "MATH",
                  "units": [{
                    "unitKey": "unit_01",
                    "title": "الوحدة 1",
                    "partKey": "P01",
                    "lessons": [{"lessonKey": "lesson_01", "title": "الدرس 1"}]
                  }]
                }
                """.trimIndent()
            )
            val htmlV1 = File(mathDir, "P01/units/unit_01/lesson_01_interactive.html").apply {
                parentFile?.mkdirs()
                writeText("<!doctype html><html><body>V1</body></html>")
            }

            val dao = database.eduMiniDao()
            val repository = EduMiniRepository(
                dao = dao,
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                directWorkspaceBinder = DirectWorkspaceBinder(context),
                appContext = context
            )
            val ws1 = repository.bindDirectWorkspace(rootDir.absolutePath, "G04")
            val pkg1 = dao.observePackagesForWorkspace(ws1.workspaceId).first().single()
            assertEquals(htmlV1.parentFile!!.absolutePath, pkg1.rootDir)
            assertEquals("lesson_01_interactive.html", pkg1.entryFile)

            // Replace HTML entry with folder-style index.html and re-bind in place.
            htmlV1.delete()
            val htmlV2 = File(mathDir, "P01/units/unit_01/lesson_01/index.html").apply {
                parentFile?.mkdirs()
                writeText("<!doctype html><html><body>V2</body></html>")
            }
            val ws2 = repository.bindDirectWorkspace(rootDir.absolutePath, "G04")
            assertEquals(ws1.workspaceId, ws2.workspaceId)
            val pkg2 = dao.observePackagesForWorkspace(ws2.workspaceId).first().single()
            assertEquals(pkg1.id, pkg2.id)
            assertEquals(htmlV2.parentFile!!.absolutePath, pkg2.rootDir)
            assertEquals("index.html", pkg2.entryFile)
        } finally {
            rootDir.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun directWorkspaceBinderAggregatesAssignmentWarningsSeparatesErrorsAndBindsValidBooks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDir = File(context.cacheDir, "direct_ws_partial_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val arabDir = File(rootDir, "workspace/G04/ARAB").apply { mkdirs() }
            // Six units all missing assignmentStatus: should emit ONE aggregated warning, not 6.
            val unitsJson = (1..6).joinToString(",") { u ->
                val unitFolder = "unit_${u.toString().padStart(2, '0')}"
                File(arabDir, "P01/units/$unitFolder").apply { mkdirs() }
                File(arabDir, "P01/units/$unitFolder/lesson_01.pdf").writeText("%PDF-1.4 unit $u")
                """{"unitNumber":$u,"title":"الوحدة $u","partKey":"P01","lessons":[{"lessonNumber":1,"title":"درس $u","pdfPageStart":1,"pdfPageEnd":2}]}"""
            }
            val arabIndexRaw = """{"gradeKey":"G04","subjectKey":"ARAB","approvalStatus":"APPROVED","sourceLayout":"MERGED_PARTS_FILE","units":[$unitsJson]}"""
            val arabIndexSha = MessageDigest.getInstance("SHA-256")
                .digest(arabIndexRaw.toByteArray())
                .joinToString("") { "%02x".format(it) }
            File(arabDir, "index.json").writeText(arabIndexRaw)
            File(arabDir, "book_manifest.json").writeText(
                """{"manifestVersion":"edu.book_manifest.v1","title":"لغتي العربية","gradeKey":"G04","subjectKey":"ARAB","sourceLayout":"MERGED_PARTS_FILE","sourceReviewStatus":"VERIFIED","sourceSha256":"${"c".repeat(64)}","indexSha256":"$arabIndexSha"}"""
            )

            // A broken sibling book in the same grade (unapproved sourceReviewStatus)
            val brokenSciDir = File(rootDir, "workspace/G04/SCI").apply { mkdirs() }
            File(brokenSciDir, "book_manifest.json").writeText(
                """{"manifestVersion":"edu.book_manifest.v1","gradeKey":"G04","subjectKey":"SCI","sourceLayout":"MERGED_PARTS_FILE","sourceReviewStatus":"NEEDS_REVIEW","sourceSha256":"${"a".repeat(64)}","indexSha256":"${"b".repeat(64)}"}"""
            )
            File(brokenSciDir, "index.json").writeText(
                """{"gradeKey":"G04","subjectKey":"SCI","approvalStatus":"APPROVED","sourceLayout":"MERGED_PARTS_FILE","units":[{"unitNumber":1,"title":"و1","partKey":"P01","assignmentStatus":"assigned","lessons":[{"lessonNumber":1,"title":"درس 1","pdfPageStart":1,"pdfPageEnd":2}]}]}"""
            )

            val binder = DirectWorkspaceBinder(context)
            val inspection = binder.inspectDirectory(rootDir)

            assertEquals(1, inspection.grades.size)
            assertEquals("G04", inspection.grades.single().gradeKey)
            // Fatal errors separated into inspection.errors
            assertTrue(inspection.errors.isNotEmpty())
            assertTrue(inspection.errors.any { it.contains("G04/SCI") })
            // assignmentStatus notices aggregated into 1 concise summary line
            val assignmentWarnings = inspection.warnings.filter { it.contains("assignmentStatus") }
            assertEquals(1, assignmentWarnings.size)
            assertTrue(assignmentWarnings.single().contains("6"))

            // Binding G04 accepts ARAB cleanly and reports SCI rejection in workspace.warnings
            val bound = binder.bindSingleGrade(rootDir.absolutePath, "G04")
            assertEquals(1, bound.workspace.books.size)
            assertEquals("ARAB", bound.workspace.books.single().bookKey)
            assertEquals(6, bound.workspace.lessons.size)
            assertTrue(bound.workspace.warnings.any { it.contains("G04/SCI") })
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun directBinderBindsStandalonePartLocalIndexWithoutTreatingPartFolderAsComponent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDir = File(context.cacheDir, "direct_part_local_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val partDir = File(rootDir, "workspace/G07/ENG/COURSE/P01").apply { mkdirs() }
            val lessonRelative = "workspace/G07/ENG/COURSE/P01/units/unit_01/lesson_01.pdf"
            File(rootDir, lessonRelative).apply {
                parentFile?.mkdirs()
                writeText("%PDF-1.4 standalone lesson")
            }
            val indexRaw = """
                {
                  "gradeKey": "G07",
                  "subjectKey": "ENG",
                  "componentKey": "COURSE",
                  "subjectName": "اللغة الإنجليزية",
                  "displayName": "كتاب الطالب — الجزء الأول",
                  "partKey": "P01",
                  "sourceLayout": "SINGLE_PART_FILE",
                  "sourceReviewStatus": "VERIFIED",
                  "approvalStatus": "APPROVED",
                  "sourceMetadata": {"sourceRequiresReview": false, "partEvidence": "Standalone P01 coursebook verified."},
                  "units": [{
                    "unitNumber": 1,
                    "unitKey": "unit_01",
                    "title": "Unit One",
                    "partKey": "P01",
                    "assignmentStatus": "assigned",
                    "assignmentSource": "REVIEWER",
                    "assignmentEvidence": "Reviewer confirmed the unit belongs to P01.",
                    "lessons": [{
                      "lessonNumber": 1,
                      "lessonKey": "lesson_01",
                      "title": "Lesson One",
                      "pdfPageStart": 2,
                      "pdfPageEnd": 4,
                      "sliceFile": {"workspaceUri": "$lessonRelative"}
                    }]
                  }]
                }
            """.trimIndent()
            val indexSha = MessageDigest.getInstance("SHA-256").digest(indexRaw.toByteArray())
                .joinToString("") { "%02x".format(it) }
            File(partDir, "index.json").writeText(indexRaw)
            File(partDir, "book_manifest.json").writeText(
                """
                    {
                      "manifestVersion": "edu.book_manifest.v2",
                      "gradeKey": "G07",
                      "subjectKey": "ENG",
                      "componentKey": "COURSE",
                      "partKey": "P01",
                      "sourceLayout": "SINGLE_PART_FILE",
                      "sourceReviewStatus": "VERIFIED",
                      "sourceSha256": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                      "canonicalIndexPath": "P01/index.json",
                      "canonicalIndexSha256": "$indexSha",
                      "indexSha256": "$indexSha"
                    }
                """.trimIndent()
            )

            val binder = DirectWorkspaceBinder(context)
            val bound = binder.bindSingleGrade(rootDir.absolutePath, "G07")

            assertEquals(1, bound.workspace.books.size)
            val book = bound.workspace.books.single()
            assertEquals("ENG", book.subjectKey)
            assertEquals("COURSE", book.componentKey)
            assertTrue(book.bookKey.endsWith("|P01"))
            assertEquals("اللغة الإنجليزية", book.subjectName)
            assertEquals(1, bound.workspace.lessons.size)
            assertNotNull(bound.workspace.lessons.single().pdfPath)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun multiGradeAdminBindingIsolatesLearnersToEnrolledGradeAndPreservesBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val rootDir = File(context.cacheDir, "multi_grade_isolation_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            fun createValidBook(grade: String, subject: String, title: String) {
                val bookDir = File(rootDir, "workspace/$grade/$subject").apply { mkdirs() }
                File(bookDir, "book_manifest.json").writeText(
                    """{"title":"$title","gradeKey":"$grade","subjectKey":"$subject"}"""
                )
                File(bookDir, "index.json").writeText(
                    """{"gradeKey":"$grade","subjectKey":"$subject","units":[{"unitKey":"unit_01","title":"الوحدة 1","partKey":"P01","assignmentStatus":"assigned","lessons":[{"lessonKey":"lesson_01","title":"الدرس 1"}]}]}"""
                )
            }
            createValidBook("G04", "ARAB", "العربية 4")
            createValidBook("G07", "ENG", "الإنجليزية 7")

            val dao = database.eduMiniDao()
            val repository = EduMiniRepository(
                dao = dao,
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                directWorkspaceBinder = DirectWorkspaceBinder(context),
                appContext = context
            )

            // Admin binds multiple grades in one step
            val boundWorkspaces = repository.bindDirectWorkspaces(rootDir.absolutePath, listOf("G04", "G07"))
            assertEquals(setOf("G04", "G07"), boundWorkspaces.map { it.gradeKey }.toSet())

            val summaries = repository.allWorkspaces.first()
            val wsG04 = summaries.single { it.gradeKey == "G04" }.workspaceId
            val wsG07 = summaries.single { it.gradeKey == "G07" }.workspaceId

            // Admin can freely switch between G04 and G07
            assertEquals(
                wsG04,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.ADMIN,
                    activeLearner = LearnerProfile.DEFAULT,
                    workspaces = summaries,
                    adminSelectedWorkspaceId = wsG04
                )
            )
            assertEquals(
                wsG07,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.ADMIN,
                    activeLearner = LearnerProfile.DEFAULT,
                    workspaces = summaries,
                    adminSelectedWorkspaceId = wsG07
                )
            )

            // Learner A enrolled in G04 sees ONLY G04 even if Admin last viewed G07
            val studentG04 = LearnerProfile(userId = "s_g04", displayName = "أحمد", enrolledGradeKey = "G04")
            assertEquals(
                wsG04,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.LEARNER,
                    activeLearner = studentG04,
                    workspaces = summaries,
                    adminSelectedWorkspaceId = wsG07
                )
            )

            // Learner B enrolled in G07 sees ONLY G07
            val studentG07 = LearnerProfile(userId = "s_g07", displayName = "سارة", enrolledGradeKey = "G07")
            assertEquals(
                wsG07,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.LEARNER,
                    activeLearner = studentG07,
                    workspaces = summaries,
                    adminSelectedWorkspaceId = wsG04
                )
            )

            // Unassigned learner when 2+ grades exist is blocked from leaking another grade
            val unassigned = LearnerProfile(userId = "s_none", displayName = "غير معيّن", enrolledGradeKey = null)
            assertEquals(
                com.example.ui.MainViewModel.BLOCKED_UNASSIGNED_WORKSPACE_ID,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.LEARNER,
                    activeLearner = unassigned,
                    workspaces = summaries,
                    adminSelectedWorkspaceId = wsG04
                )
            )

            // Learner assigned to an unbound grade (G09) is also blocked
            val unboundGradeStudent = LearnerProfile(userId = "s_g09", displayName = "عمر", enrolledGradeKey = "G09")
            assertEquals(
                com.example.ui.MainViewModel.BLOCKED_UNASSIGNED_WORKSPACE_ID,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.LEARNER,
                    activeLearner = unboundGradeStudent,
                    workspaces = summaries,
                    adminSelectedWorkspaceId = wsG04
                )
            )

            // Full backup round-trip preserves enrolledGradeKey
            val backupJson = WorkspaceExporter.serializeFullBackup(
                workspaceId = wsG04,
                drafts = emptyList(),
                questions = emptyList(),
                assessments = emptyList(),
                attempts = emptyList(),
                progress = emptyList(),
                notes = emptyList(),
                studyDays = emptyList(),
                templates = emptyList(),
                learnerProfiles = listOf(LearnerProfile.DEFAULT, studentG04, studentG07),
                activeLearnerId = studentG04.userId
            )
            repository.importFullBackupJson(backupJson, wsG04)
            val restoredProfiles = Json { ignoreUnknownKeys = true }.decodeFromString<List<LearnerProfile>>(
                repository.readLearnerPref("edumini_learner_profiles", "profiles_json").orEmpty()
            )
            assertEquals("G04", restoredProfiles.single { it.userId == "s_g04" }.enrolledGradeKey)
            assertEquals("G07", restoredProfiles.single { it.userId == "s_g07" }.enrolledGradeKey)
        } finally {
            rootDir.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun adminClassroomCreationAndStudentAssignmentLifecycle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val rootDir = File(context.cacheDir, "admin_class_lifecycle_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val dao = database.eduMiniDao()
            val repository = EduMiniRepository(
                dao = dao,
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                directWorkspaceBinder = DirectWorkspaceBinder(context),
                appContext = context
            )

            // 1. Admin creates two new classrooms manually
            val class5 = repository.createClassroom("G05", "الصف الخامس - أ")
            val class6 = repository.createClassroom("g06", "الصف السادس - ب")
            assertEquals("G05", class5.gradeKey)
            assertEquals("G06", class6.gradeKey)

            val allClasses = repository.allWorkspaces.first()
            assertEquals(setOf("G05", "G06"), allClasses.map { it.gradeKey }.toSet())

            // Duplicate grade key is rejected cleanly
            var duplicateRejected = false
            try {
                repository.createClassroom("G05", "مكرر")
            } catch (e: IllegalArgumentException) {
                duplicateRejected = true
            }
            assertTrue(duplicateRejected)

            // 2. Assign student to G05 and verify Learner isolation resolves to class5
            val studentA = LearnerProfile(userId = "stu_5", displayName = "خالد", enrolledGradeKey = "G05")
            assertEquals(
                class5.workspaceId,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.LEARNER,
                    activeLearner = studentA,
                    workspaces = allClasses,
                    adminSelectedWorkspaceId = class6.workspaceId
                )
            )

            // 3. Binding a direct folder for G05 upgrades/replaces the empty placeholder without duplicates
            val bookDir = File(rootDir, "workspace/G05/SCI").apply { mkdirs() }
            File(bookDir, "book_manifest.json").writeText(
                """{"title":"العلوم 5","gradeKey":"G05","subjectKey":"SCI"}"""
            )
            File(bookDir, "index.json").writeText(
                """{"gradeKey":"G05","subjectKey":"SCI","units":[{"unitKey":"unit_01","title":"و1","partKey":"P01","assignmentStatus":"assigned","lessons":[{"lessonKey":"lesson_01","title":"درس 1"}]}]}"""
            )
            val boundClass5 = repository.bindDirectWorkspace(rootDir.absolutePath, "G05")
            val classesAfterBind = repository.allWorkspaces.first()
            assertEquals(2, classesAfterBind.size)
            assertEquals(boundClass5.workspaceId, classesAfterBind.single { it.gradeKey == "G05" }.workspaceId)
            assertEquals(
                boundClass5.workspaceId,
                com.example.ui.MainViewModel.resolveEffectiveWorkspaceId(
                    userRole = com.example.model.UserRole.LEARNER,
                    activeLearner = studentA,
                    workspaces = classesAfterBind,
                    adminSelectedWorkspaceId = class6.workspaceId
                )
            )
        } finally {
            rootDir.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun authUserRoleResolutionRoutesAdminToDashboardStudentToStudentViewAndBlocksEscalation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(
                context,
                com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("test-api-key-only")
                    .setProjectId("demo-edumini-test")
                    .build()
            )
        }

        // 1. Role string resolution mapping ('admin' -> Admin Dashboard, 'student'/unknown -> Student View)
        val adminRes = com.example.data.firebase.FirestoreSyncRepository.resolveUserRoleFromDocument(
            roleValue = " ADMIN ",
            displayName = "مدير المدرسة"
        )
        assertEquals(com.example.model.UserRole.ADMIN, adminRes.resolvedRole)
        assertEquals(com.example.model.AuthRoleDestination.ADMIN_DASHBOARD, adminRes.destination)
        assertEquals("admin", adminRes.rawRole)

        val studentRes = com.example.data.firebase.FirestoreSyncRepository.resolveUserRoleFromDocument(
            roleValue = "student",
            enrolledGradeKey = "g04",
            displayName = "سارة"
        )
        assertEquals(com.example.model.UserRole.LEARNER, studentRes.resolvedRole)
        assertEquals(com.example.model.AuthRoleDestination.STUDENT_VIEW, studentRes.destination)
        assertEquals("G04", studentRes.enrolledGradeKey)

        val unknownRes = com.example.data.firebase.FirestoreSyncRepository.resolveUserRoleFromDocument(
            roleValue = "moderator",
            enrolledGradeKey = null
        )
        assertEquals(com.example.model.UserRole.LEARNER, unknownRes.resolvedRole)
        assertEquals(com.example.model.AuthRoleDestination.STUDENT_VIEW, unknownRes.destination)
        assertEquals("student", unknownRes.rawRole)

        // 2. MainViewModel routing + student privilege-escalation lock
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = EduMiniRepository(
                dao = database.eduMiniDao(),
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                directWorkspaceBinder = DirectWorkspaceBinder(context),
                appContext = context
            )
            val firestoreDb = com.google.firebase.firestore.FirebaseFirestore.getInstance(
                context.getString(R.string.firestore_database_id)
            )
            val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val firestoreSync = com.example.data.firebase.FirestoreSyncRepository(firestoreDb, firebaseAuth)

            val adminVm = com.example.ui.MainViewModel(
                repository = repository,
                firestoreSync = firestoreSync,
                currentUserId = "uid_admin_1",
                currentUserEmail = "admin@school.edu",
                currentUserDisplayName = "المدير",
                isCloudAuthenticated = true
            )
            adminVm.applyResolvedUserRole(adminRes)
            assertEquals(com.example.model.UserRole.ADMIN, adminVm.uiState.value.userRole)
            assertEquals(com.example.model.AppPage.ADMIN_DASHBOARD, adminVm.uiState.value.page)
            assertEquals(com.example.model.UserRole.ADMIN, adminVm.uiState.value.authenticatedUserRole)

            val studentVm = com.example.ui.MainViewModel(
                repository = repository,
                firestoreSync = firestoreSync,
                currentUserId = "uid_student_1",
                currentUserEmail = "sara@school.edu",
                currentUserDisplayName = "سارة",
                isCloudAuthenticated = true
            )
            studentVm.applyResolvedUserRole(studentRes)
            assertEquals(com.example.model.UserRole.LEARNER, studentVm.uiState.value.userRole)
            assertEquals(com.example.model.AppPage.DASHBOARD, studentVm.uiState.value.page)
            assertEquals("uid_student_1", studentVm.uiState.value.activeLearner.userId)
            assertEquals("G04", studentVm.uiState.value.activeLearner.enrolledGradeKey)

            // Authenticated student cannot escalate to Admin or navigate to Admin Dashboard
            studentVm.selectRole(com.example.model.UserRole.ADMIN)
            studentVm.navigateTo(com.example.model.AppPage.ADMIN_DASHBOARD)
            studentVm.switchToRoleEntry()
            assertEquals(com.example.model.UserRole.LEARNER, studentVm.uiState.value.userRole)
            assertEquals(com.example.model.AppPage.DASHBOARD, studentVm.uiState.value.page)
        } finally {
            database.close()
        }
    }

    @Test
    fun localModeWithoutFirebaseAndAnalyticsWiringDoesNotCrash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // CI may inject google-services.json and generate default_web_client_id.
        // Its presence is build configuration, not evidence that Firebase services are wired
        // into this local-mode test. Exercise the resolver without assuming the resource is absent.
        com.example.ui.auth.resolveDefaultWebClientId(context)

        // Null-backed FirestoreSyncRepository must fail gracefully instead of throwing IllegalStateException
        val nullSync = com.example.data.firebase.FirestoreSyncRepository(db = null, auth = null)
        val roleResult = nullSync.resolveUserRole("local_user", "متعلم محلي")
        assertTrue(roleResult.isFailure)
        val progressResult = nullSync.upsertLessonProgress(
            userId = "local_user",
            workspaceId = "ws_1",
            lesson = LessonRef(
                grade = "G04",
                rootPath = "",
                bookKey = "ARAB",
                bookTitle = "اللغة العربية",
                subjectKey = "ARAB",
                unitKey = "unit_01",
                unitTitle = "الوحدة 1",
                lessonKey = "lesson_01",
                title = "الدرس 1",
                workspaceId = "ws_1"
            ),
            completed = true,
            xpEarned = 30,
            scorePercent = 100
        )
        assertTrue(progressResult.isFailure)

        // AnalyticsTracker must tolerate missing FirebaseApp or Robolectric context cleanly and sanitize/clamp params
        val tracker = com.example.data.firebase.AnalyticsTracker(context)
        tracker.trackLessonOpened("ARAB", "unit_01", "lesson_01", "أ".repeat(140))
        tracker.trackAiExplanationGenerated("gemini", TaskType.EXPLANATION.id, true)
        tracker.trackLessonCreateCompleted("ARAB", "unit_01", "lesson_01", true)
        tracker.trackQuizAttemptSubmitted("lesson_01", 150, true)

        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = EduMiniRepository(
                dao = database.eduMiniDao(),
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                directWorkspaceBinder = DirectWorkspaceBinder(context),
                appContext = context
            )
            val offlineVm = com.example.ui.MainViewModel(
                repository = repository,
                firestoreSync = nullSync,
                currentUserId = "local_offline_user",
                isCloudAuthenticated = false,
                analyticsTracker = tracker
            )
            val draftsState = offlineVm.cloudDraftsState.value
            val progressState = offlineVm.cloudProgressState.value
            assertTrue(draftsState is com.example.model.SyncUiState.Success)
            assertTrue(progressState is com.example.model.SyncUiState.Success)

            val lesson = LessonRef(
                grade = "G04",
                rootPath = "",
                bookKey = "ARAB",
                bookTitle = "اللغة العربية",
                subjectKey = "ARAB",
                unitKey = "unit_01",
                unitTitle = "الوحدة 1",
                lessonKey = "lesson_01",
                title = "الدرس 1",
                workspaceId = "ws_local"
            )
            offlineVm.openStudentStudy(lesson)
            assertEquals(lesson, offlineVm.uiState.value.studentStudyLesson)
            offlineVm.recordQuizCompleted(correct = 4, total = 5, earnedXp = 40)
        } finally {
            database.close()
        }
    }

    @Test
    fun bookMetadataMapperUsesOnlyCanonicalCurriculumKeys() {
        val mapper = com.example.data.workspace.BookMetadataMapper
        assertEquals("الصف السابع الأساسي", mapper.gradeDisplayName("G07"))
        assertEquals("الرياضيات", mapper.subjectDisplayName("MATH"))
        assertEquals("الجغرافيا", mapper.componentDisplayName("GEO"))
        assertEquals("كتاب الطالب (Course)", mapper.componentDisplayName("COURSE"))
        assertEquals("العلوم والحياة", mapper.subjectDisplayName("SCI", "العلوم والحياة"))
        assertEquals("الصف التجريبي", mapper.gradeDisplayName("G07", "الصف التجريبي"))
        val declaredDisplay = mapper.mapBookMetadata(
            gradeKey = "G07",
            subjectKey = "SCI",
            componentKey = null,
            declaredGradeName = "الصف التجريبي",
            declaredSubjectName = "العلوم والحياة"
        )
        assertEquals("العلوم والحياة", declaredDisplay.subjectName)
        assertEquals("الصف التجريبي", declaredDisplay.gradeName)
        assertTrue(declaredDisplay.isKnown)
        assertNull(mapper.gradeDisplayName("G99"))
        assertNull(mapper.subjectDisplayName("PHY"))
        assertNull(mapper.subjectDisplayName("PHY", "اسم عرض غير معتمد"))
        assertNull(mapper.componentDisplayName("STUDENT"))
        assertFalse(mapper.isValidBookIdentity("G07", "SCI", "WB"))
        assertFalse(mapper.isValidBookIdentity("G10", "SCI", null))
        assertFalse(mapper.isValidBookIdentity("G07", "BIO", null))
        assertTrue(mapper.isValidBookIdentity("G07", "ENG", "COURSE"))
        assertTrue(mapper.isValidBookIdentity("G07", "SOC", "TARBIA"))
    }

    @Test
    fun bindWorkspacePersistsSubjectIndexesAndPreservesLinkedGeneratedContentOnRebind() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val rootDir = File(context.cacheDir, "bind_relink_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val mathDir = File(rootDir, "workspace/G04/MATH").apply { mkdirs() }
            File(mathDir, "P01/units/unit_01").mkdirs()
            File(mathDir, "P01/units/unit_01/lesson_01.pdf").writeText("%PDF-1.4 lesson 1")
            File(mathDir, "P01/units/unit_01/lesson_02.pdf").writeText("%PDF-1.4 lesson 2")
            File(mathDir, "book_manifest.json").writeText("""{"title":"الرياضيات","gradeKey":"G04","subjectKey":"MATH"}""")
            val indexV1 = """
                {
                  "gradeKey": "G04",
                  "subjectKey": "MATH",
                  "units": [{
                    "unitKey": "unit_01",
                    "title": "الأعداد والعمليات",
                    "partKey": "P01",
                    "lessons": [{"lessonKey": "lesson_01", "title": "الجمع", "pdfPageStart": 1, "pdfPageEnd": 4}]
                  }]
                }
            """.trimIndent()
            File(mathDir, "index.json").writeText(indexV1)

            val dao = database.eduMiniDao()
            val repository = EduMiniRepository(
                dao = dao,
                providerStorage = ProviderStorage(context),
                workspaceImporter = WorkspaceImporter(context),
                aiGenerationService = AiGenerationService(context),
                interactivePackageImporter = InteractivePackageImporter(context),
                directWorkspaceBinder = DirectWorkspaceBinder(context),
                appContext = context
            )

            val boundV1 = repository.bindDirectWorkspace(rootDir.absolutePath, "G04")
            assertEquals(1, boundV1.indexRecords.size)
            assertEquals(1, boundV1.units.size)
            val savedIndexesV1 = dao.observeWorkspaceIndexes(boundV1.workspaceId).first()
            assertEquals(1, savedIndexesV1.size)
            assertEquals("MATH", savedIndexesV1.single().bookKey)

            val lesson1 = boundV1.lessons.single()
            val savedDraft = repository.persistDraft(
                workspaceId = boundV1.workspaceId,
                lesson = lesson1,
                task = TaskType.EXPLANATION,
                providerLabel = "Gemini",
                model = "gemini-2.5-flash",
                text = "شرح درس الجمع",
                title = "شرح الجمع",
                sourceText = "نص الدرس"
            )
            repository.addManualQuestion(
                workspaceId = boundV1.workspaceId,
                lesson = lesson1,
                type = QuestionType.MCQ,
                prompt = "ما ناتج 2+3؟",
                options = listOf("4", "5", "6"),
                correctAnswer = "5",
                rationale = "جمع مباشر",
                difficulty = QuestionDifficulty.EASY
            )

            // Update subject index.json with an extra lesson and re-bind workspace.
            val indexV2 = """
                {
                  "gradeKey": "G04",
                  "subjectKey": "MATH",
                  "units": [{
                    "unitKey": "unit_01",
                    "title": "الأعداد والعمليات",
                    "partKey": "P01",
                    "lessons": [
                      {"lessonKey": "lesson_01", "title": "الجمع المحدّث", "pdfPageStart": 1, "pdfPageEnd": 4},
                      {"lessonKey": "lesson_02", "title": "الطرح", "pdfPageStart": 5, "pdfPageEnd": 8}
                    ]
                  }]
                }
            """.trimIndent()
            File(mathDir, "index.json").writeText(indexV2)

            val boundV2 = repository.bindDirectWorkspace(rootDir.absolutePath, "G04")
            assertEquals(boundV1.workspaceId, boundV2.workspaceId)
            assertEquals(2, boundV2.lessons.size)
            val updatedIndex = dao.observeWorkspaceIndexes(boundV2.workspaceId).first().single()
            assertTrue(updatedIndex.indexSha256 != savedIndexesV1.single().indexSha256)

            // Generated draft & question remain linked to the updated lesson via (workspaceId, bookKey, unitKey, lessonKey).
            val updatedLesson1 = boundV2.lessons.first { it.lessonKey == "lesson_01" }
            val linkedDrafts = repository.observeDraftsForLesson(boundV2.workspaceId, updatedLesson1).first()
            val linkedQuestions = repository.observeQuestionsForWorkspace(boundV2.workspaceId).first()
            assertEquals(1, linkedDrafts.size)
            assertEquals(savedDraft.id, linkedDrafts.single().id)
            assertEquals(1, linkedQuestions.size)
        } finally {
            rootDir.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun aiModelCatalogPersistsDefaultsAndSupportsCrudSafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("edu_mini_secure_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val storage = ProviderStorage(context)

        val initial = storage.loadModelCatalog()
        assertTrue(initial.any { it.modelId == "gemini-3.5-flash" && it.isDefault })
        assertTrue(initial.any { it.modelId == "gemini-3.6-flash" && it.displayName == "Gemini 3.6 Flash" })
        assertTrue(initial.any { it.modelId == "gemini-3.7-flash" && it.displayName == "Gemini 3.7 Flash" })
        assertTrue(initial.any { it.modelId == "gemini-3.8-flash" && it.displayName == "Gemini 3.8 Flash" })
        assertEquals(4, initial.count { it.provider == ProviderType.GEMINI })

        val custom = com.example.data.ai.AiModelDefinition(
            modelId = "gemini-custom-test",
            displayName = "Gemini Custom Test",
            provider = ProviderType.GEMINI
        )
        storage.addModelDefinition(custom)
        storage.setDefaultModel(ProviderType.GEMINI, custom.modelId)
        val afterDefault = storage.loadModelCatalog()
        assertTrue(afterDefault.any { it.modelId == custom.modelId && it.isDefault })
        assertFalse(afterDefault.any { it.modelId == "gemini-3.5-flash" && it.isDefault })

        val renamed = custom.copy(modelId = "gemini-custom-renamed", displayName = "Renamed model")
        storage.updateModelDefinition(custom.modelId, renamed)
        assertTrue(storage.loadModelCatalog().any { it.modelId == renamed.modelId && it.displayName == renamed.displayName })
        storage.setDefaultModel(ProviderType.GEMINI, "gemini-3.5-flash")
        storage.deleteModelDefinition(renamed.modelId)
        assertFalse(storage.loadModelCatalog().any { it.modelId == renamed.modelId })
    }

    @Test
    fun aiModelCatalogMigrationRestoresGeminiSeedsWhenLegacyCatalogWasEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("edu_mini_secure_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear()
            .putString("edu_mini_ai_model_catalog_v1", "[]")
            .commit()
        val storage = ProviderStorage(context)

        val migrated = storage.loadModelCatalog()
        assertTrue(migrated.any { it.modelId == "gemini-3.5-flash" && it.isDefault })
        assertTrue(migrated.any { it.modelId == "gemini-3.6-flash" })
        assertTrue(migrated.any { it.modelId == "gemini-3.7-flash" })
        assertTrue(migrated.any { it.modelId == "gemini-3.8-flash" })
        assertEquals(4, migrated.count { it.provider == ProviderType.GEMINI })
        assertEquals(1, migrated.count { it.provider == ProviderType.GEMINI && it.isDefault })
    }

    @Test
    fun aiModelCatalogPreventsDeletingDefaultOrProfileReferencedModels() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("edu_mini_secure_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val storage = ProviderStorage(context)
        val catalog = storage.loadModelCatalog()
        assertTrue(runCatching { storage.deleteModelDefinition(catalog.first { it.isDefault }.modelId) }.isFailure)

        val referencedModel = catalog.first { it.provider == ProviderType.GEMINI && !it.isDefault }
        storage.saveProviders(
            listOf(
                ProviderConfig(
                    id = "catalog-test-profile",
                    provider = ProviderType.GEMINI,
                    label = "Catalog test",
                    model = referencedModel.modelId,
                    baseUrl = "",
                    apiKey = "test-key",
                    enabled = true,
                    createdAt = 1L
                )
            )
        )
        assertTrue(runCatching { storage.deleteModelDefinition(referencedModel.modelId) }.isFailure)
    }

}

private val testKeyStoreMap = java.util.concurrent.ConcurrentHashMap<String, java.security.Key>()

private fun ensureTestAndroidKeyStore() {
    if (java.security.Security.getProvider("AndroidKeyStore") != null) return
    val provider = object : java.security.Provider("AndroidKeyStore", 1.0, "Robolectric in-memory KeyStore") {
        init {
            put("KeyStore.AndroidKeyStore", RobolectricAndroidKeyStoreSpi::class.java.name)
            put("KeyGenerator.AES", RobolectricAndroidAesKeyGeneratorSpi::class.java.name)
        }
    }
    java.security.Security.addProvider(provider)
}

class RobolectricAndroidKeyStoreSpi : java.security.KeyStoreSpi() {
    override fun engineGetKey(alias: String?, password: CharArray?): java.security.Key? =
        if (alias == null) null else testKeyStoreMap[alias]

    override fun engineGetCertificateChain(alias: String?): Array<java.security.cert.Certificate>? = null
    override fun engineGetCertificate(alias: String?): java.security.cert.Certificate? = null
    override fun engineGetCreationDate(alias: String?): java.util.Date? = null
    override fun engineSetKeyEntry(
        alias: String?,
        key: java.security.Key?,
        password: CharArray?,
        chain: Array<java.security.cert.Certificate>?
    ) {
        if (alias != null && key != null) testKeyStoreMap[alias] = key
    }

    override fun engineSetKeyEntry(
        alias: String?,
        key: ByteArray?,
        chain: Array<java.security.cert.Certificate>?
    ) = Unit

    override fun engineSetCertificateEntry(alias: String?, cert: java.security.cert.Certificate?) = Unit
    override fun engineDeleteEntry(alias: String?) {
        if (alias != null) testKeyStoreMap.remove(alias)
    }

    override fun engineAliases(): java.util.Enumeration<String> =
        java.util.Collections.enumeration(testKeyStoreMap.keys)

    override fun engineContainsAlias(alias: String?): Boolean =
        alias != null && testKeyStoreMap.containsKey(alias)

    override fun engineSize(): Int = testKeyStoreMap.size
    override fun engineIsKeyEntry(alias: String?): Boolean = engineContainsAlias(alias)
    override fun engineIsCertificateEntry(alias: String?): Boolean = false
    override fun engineGetCertificateAlias(cert: java.security.cert.Certificate?): String? = null
    override fun engineStore(stream: java.io.OutputStream?, password: CharArray?) = Unit
    override fun engineLoad(stream: java.io.InputStream?, password: CharArray?) = Unit
}

class RobolectricAndroidAesKeyGeneratorSpi : javax.crypto.KeyGeneratorSpi() {
    private var alias: String = "edu_mini_providers_aes_key"
    private var keySize: Int = 256

    override fun engineInit(random: java.security.SecureRandom?) = Unit

    override fun engineInit(params: java.security.spec.AlgorithmParameterSpec?, random: java.security.SecureRandom?) {
        if (params is android.security.keystore.KeyGenParameterSpec) {
            alias = params.keystoreAlias
            if (params.keySize > 0) keySize = params.keySize
        }
    }

    override fun engineInit(keysize: Int, random: java.security.SecureRandom?) {
        if (keysize > 0) keySize = keysize
    }

    override fun engineGenerateKey(): javax.crypto.SecretKey {
        val generator = javax.crypto.KeyGenerator.getInstance("AES")
        generator.init(keySize)
        val secretKey = generator.generateKey()
        testKeyStoreMap[alias] = secretKey
        return secretKey
    }
}
