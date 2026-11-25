package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.ai.AiGenerationService
import com.example.data.ai.Indexer2PromptRegistry
import com.example.data.ai.TemplateResponseAdapter
import com.example.data.local.DraftEntity
import com.example.data.local.BookEntity
import com.example.data.local.LessonEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceIndexEntity
import com.example.data.local.WorkspaceUnitEntity
import com.example.data.local.GenerationRunEntity
import com.example.data.local.EduMiniDatabase
import com.example.data.local.LessonProgressEntity
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.ConceptMasteryEngine
import com.example.data.repository.EduMiniRepository
import com.example.data.repository.InteractiveActivityParser
import com.example.data.repository.LearningProgressAnalytics
import com.example.data.repository.LessonSequenceGate
import com.example.data.repository.MilestoneBadgeEngine
import com.example.data.repository.QuestionBankEngine
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
import com.example.model.AssessmentKind
import com.example.model.AssessmentAttemptRecord
import com.example.model.BookRef
import com.example.model.BookProgressSummary
import com.example.model.ImportedWorkspace
import com.example.model.LearningNodeStatus
import com.example.model.LessonRef
import com.example.model.MasteryStatus
import com.example.model.PackageKind
import com.example.model.PdfBookInfo
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.SecuritySeverity
import com.example.model.TaskType
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.UnitCompletionStatus
import com.example.ui.components.LearningProgressCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.zip.ZipInputStream
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
import java.time.LocalDate
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
    fun workspaceContractValidatorBlocksUnverifiedIndexer2Source() {
        val index = """
            {
              "gradeKey": "G04",
              "subjectKey": "SCI",
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
        assertTrue(result.errors.any { it.contains("sourceReviewStatus") })
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
    fun importerSupportsIndexer2IndependentSourceDirectory() = runBlocking {
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
        assertTrue(prompt.contains("2 صفحة مصورة"))
        assertTrue(prompt.contains("يجب أن تكون الإجابة الصحيحة صريحة ومسنودة بالمصدر."))
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
        assertTrue(indexFile.readText().contains("edu.book_index.v2"))
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
        // 50 (completed) + 20 (approved draft) + 40 (first lesson badge) + 25 (workspace badge) + 30 (ai author badge) + 50 (halfway badge) = 215 XP
        assertEquals(215, summary.totalPoints)
        assertEquals(2, summary.level)
        assertEquals(4, summary.unlockedBadgesCount)
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
    fun repositoryQuestionBankCrudAndWorkspaceExporterIndexer2Zip() = runBlocking {
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
        val receipt = repo.exportIndexer2Zip(baos, ws)
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
        assertTrue(vocabPrompt.contains("مفردات ومصطلحات"))

        val workedPrompt = service.buildPrompt(lesson, TaskType.WORKED_EXAMPLES, "نص", "", questionCount = 4)
        assertTrue(workedPrompt.contains("أمثلة محلولة"))

        val reviewPrompt = service.buildPrompt(lesson, TaskType.LESSON_REVIEW, "نص", "", questionCount = 4)
        assertTrue(reviewPrompt.contains("مراجعة شاملة"))

        val adapterPrompt = service.buildPrompt(lesson, TaskType.DIFFICULTY_ADAPTER, "نص", "", questionCount = 4)
        assertTrue(adapterPrompt.contains("ثلاثة مستويات"))

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
    fun indexer2PromptRegistryCoversAllTasksAndStandardSchemas() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        TaskType.entries.forEach { task ->
            val blueprint = Indexer2PromptRegistry.blueprintFor(task)
            assertEquals(task, blueprint.task)
            assertTrue(blueprint.sourcePath.startsWith("prompts/indexer2/"))
            assertTrue(blueprint.outputContract.isNotBlank())
            context.assets.open(blueprint.sourcePath).use { asset ->
                assertTrue(asset.readBytes().isNotEmpty())
            }
        }

        assertEquals(5, Indexer2PromptRegistry.schemas.size)
        assertTrue(Indexer2PromptRegistry.schemas.any { it.fileName.contains("index.json") })
        assertTrue(Indexer2PromptRegistry.schemas.any { it.fileName.contains("book_manifest.json") })
        assertTrue(Indexer2PromptRegistry.schemas.any { it.fileName.contains("interactive.json") })
        assertTrue(Indexer2PromptRegistry.schemas.any { it.fileName == "manifest.json" })

        val prompt = Indexer2PromptRegistry.assemblePrompt(
            lesson = sampleLesson(),
            task = TaskType.EXPLANATION,
            source = "مفهوم القسمة المطولة",
            extra = "تبسيط الخطوات",
            questionCount = 5,
            attachedPdfPagesCount = 1
        )
        assertTrue(prompt.contains("prompts/indexer2/lesson_explanation.prompt.md"))
        assertTrue(prompt.contains("مفهوم القسمة المطولة"))
        assertTrue(prompt.contains("1 صفحة مصورة"))
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

            fun addIndexer2Book(subjectKey: String, componentKey: String?, title: String) {
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

            addIndexer2Book("SCI", null, "العلوم والحياة")
            addIndexer2Book("ENG", "COURSE", "English Course")
            addIndexer2Book("ENG", "WORKBOOK", "English Workbook")
            addIndexer2Book("SOC", "GEO", "الجغرافيا")
            addIndexer2Book("SOC", "HIST", "التاريخ")
            addIndexer2Book("SOC", "TARBIA", "التربية الوطنية")

            val binder = DirectWorkspaceBinder(context)
            val inspection = binder.inspectDirectory(rootDir)
            assertEquals(2, inspection.grades.size)
            assertTrue(inspection.grades.any { it.gradeKey == "G04" && it.lessonsCount == 1 })
            assertTrue(inspection.grades.any { it.gradeKey == "G07" && it.booksCount == 6 && it.lessonsCount == 6 })

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
            assertEquals(6, boundScience.workspace.books.size)
            assertEquals(6, boundScience.workspace.indexRecords.size)
            val scienceBook = boundScience.workspace.books.single { it.subjectKey == "SCI" }
            assertEquals("العلوم والحياة", scienceBook.subjectName)
            assertNull(scienceBook.componentKey)
            assertEquals(
                setOf("COURSE", "WORKBOOK"),
                boundScience.workspace.books.filter { it.subjectKey == "ENG" }.mapNotNull { it.componentKey }.toSet()
            )
            assertEquals(
                setOf("GEO", "HIST", "TARBIA"),
                boundScience.workspace.books.filter { it.subjectKey == "SOC" }.mapNotNull { it.componentKey }.toSet()
            )
            assertEquals(
                setOf("SCI", "ENG/COURSE", "ENG/WORKBOOK", "SOC/GEO", "SOC/HIST", "SOC/TARBIA"),
                boundScience.workspace.books.map { it.bookKey }.toSet()
            )
            val englishCourse = boundScience.workspace.books.single { it.subjectKey == "ENG" && it.componentKey == "COURSE" }
            val englishWorkbook = boundScience.workspace.books.single { it.subjectKey == "ENG" && it.componentKey == "WORKBOOK" }
            assertEquals("اللغة الإنجليزية", englishCourse.subjectName)
            assertEquals("English Course", englishCourse.title)
            assertEquals("English Workbook", englishWorkbook.title)
            assertEquals("الجغرافيا", boundScience.workspace.books.single { it.componentKey == "GEO" }.subjectName)
            assertEquals("التاريخ", boundScience.workspace.books.single { it.componentKey == "HIST" }.subjectName)
            assertEquals("التربية الوطنية", boundScience.workspace.books.single { it.componentKey == "TARBIA" }.subjectName)

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
            val receipt = WorkspaceExporter.exportIndexer2CompatibleZip(
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

        // First sync inserts 2 questions
        val firstInserted = repo.saveQuestionsFromDraftText(ws.workspaceId, lesson, draft.content)
        assertEquals(2, firstInserted.size)

        // Approving the same draft deduplicates cleanly and does not insert duplicate rows
        val addedOnApprove = repo.approveDraft(draft.id)
        assertEquals(0, addedOnApprove)

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
            templates = emptyList()
        )
        val restored = WorkspaceExporter.parseFullBackup(raw)

        assertEquals("edu-mini-backup-1.2", restored.formatVersion)
        assertEquals(setOf("learner_a", "learner_b"), restored.attempts.map { it.userId }.toSet())
        assertEquals(setOf("learner_a", "learner_b"), restored.progress.map { it.userId }.toSet())
        assertEquals(setOf("learner_a", "learner_b"), restored.studyDayRecords.map { it.userId }.toSet())
        assertEquals(3, restored.studyDayRecords.first { it.userId == "learner_a" }.actionsCount)
    }

    @Test
    fun providerQuotaReservationFreezesKeyUntilNextTimezoneDay() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = ProviderStorage(context)
        val dayOne = java.time.Instant.parse("2026-11-25T10:00:00Z").toEpochMilli()
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
            assertTrue(storage.reserveProviderKey(provider.id, dayOne))
            assertFalse(storage.reserveProviderKey(provider.id, dayOne + 60_000L))
            val nextDay = java.time.Instant.parse("2026-11-26T00:01:00Z").toEpochMilli()
            assertTrue(storage.reserveProviderKey(provider.id, nextDay))
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
        } finally {
            file.delete()
        }
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
}
