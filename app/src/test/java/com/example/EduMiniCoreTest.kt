package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiGenerationService
import com.example.data.ai.Indexer2PromptRegistry
import com.example.data.local.DraftEntity
import com.example.data.local.EduMiniDatabase
import com.example.data.local.LessonProgressEntity
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.EduMiniRepository
import com.example.data.repository.InteractiveActivityParser
import com.example.data.repository.MilestoneBadgeEngine
import com.example.data.repository.QuestionBankEngine
import com.example.data.security.ProviderStorage
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.WorkspaceExporter
import com.example.data.workspace.WorkspaceImporter
import com.example.model.AssessmentKind
import com.example.model.BookRef
import com.example.model.ImportedWorkspace
import com.example.model.LearningNodeStatus
import com.example.model.LessonRef
import com.example.model.PackageKind
import com.example.model.PdfBookInfo
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.SecuritySeverity
import com.example.model.TaskType
import com.example.ui.components.LearningProgressCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        val service = AiGenerationService()
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
    }

    @Test
    fun pdfBookSplitterAutoSplitsAndCreatesWorkspaceWithLessonPdfs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val splitter = PdfBookSplitter(context)

        val pdfFile = File(context.cacheDir, "sample_book.pdf")
        createSamplePdf(pdfFile, pageCount = 6)

        val info = PdfBookInfo(
            fileName = "sample_book.pdf",
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
        assertTrue(indexFile.readText().contains("lesson_01"))
        assertTrue(manifestFile.readText().contains("العلوم"))
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
        repo.savePromptTemplate("خريطة مفاهيم", TaskType.LESSON_REVIEW, "لخّص المفاهيم في نقاط هرمية")

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
        assertTrue(templates.any { it.title == "خريطة مفاهيم" && !it.isBuiltIn })
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

        val interactivePrompt = service.buildPrompt(lesson, TaskType.INTERACTIVE, "نص", "", questionCount = 3)
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
        TaskType.entries.forEach { task ->
            val blueprint = Indexer2PromptRegistry.blueprintFor(task)
            assertEquals(task, blueprint.task)
            assertTrue(blueprint.sourcePath.startsWith("prompts/indexer2/"))
            assertTrue(blueprint.outputContract.isNotBlank())
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
        val header = "%PDF-1.4\n% Sample $pageCount pages\n%%EOF\n"
        FileOutputStream(outFile).use { fos ->
            fos.write(header.toByteArray(Charsets.UTF_8))
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
