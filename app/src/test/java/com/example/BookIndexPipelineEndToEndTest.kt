package com.example

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiGenerationService
import com.example.data.firebase.AnalyticsTracker
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.local.EduMiniDatabase
import com.example.data.packageimport.InteractivePackageImporter
import com.example.data.repository.EduMiniRepository
import com.example.data.security.ProviderStorage
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexPromptTemplate
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.DirectWorkspaceBinder
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexDiscoveryMethod
import com.example.data.workspace.IndexSourceLayout
import com.example.data.workspace.PdfBookSplitter
import com.example.data.workspace.RoomBookIndexProjection
import com.example.data.workspace.WorkspaceContractValidator
import com.example.data.workspace.WorkspaceImporter
import com.example.model.UserRole
import com.example.ui.MainViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookIndexPipelineEndToEndTest {

    @org.junit.Before
    fun registerTestKeyStore() {
        ensureTestAndroidKeyStore()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun createSamplePdf(outFile: File, pageCount: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        outFile.parentFile?.mkdirs()
        PDDocument().use { document ->
            repeat(pageCount) { index ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(48f, 740f)
                    stream.showText("Sample textbook page ${index + 1} with searchable content for EduMini.")
                    stream.endText()
                }
            }
            document.save(outFile)
        }
    }

    private fun buildTestRepository(context: Context, db: EduMiniDatabase): EduMiniRepository =
        EduMiniRepository(
            dao = db.eduMiniDao(),
            providerStorage = ProviderStorage(context),
            workspaceImporter = WorkspaceImporter(context),
            aiGenerationService = AiGenerationService(context),
            pdfBookSplitter = PdfBookSplitter(context),
            interactivePackageImporter = InteractivePackageImporter(context),
            directWorkspaceBinder = DirectWorkspaceBinder(context),
            appContext = context
        )

    private fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun stage3_discoverySchemaIsProjectedFromRoomEntitiesAndPreservesContract() {
        val projected = RoomBookIndexProjection.buildDiscoveryResponseSchemaJson()
        assertEquals(projected, BookIndexPromptTemplate.responseSchemaJson)

        val root = json.parseToJsonElement(projected).jsonObject
        assertEquals("object", root["type"]?.jsonPrimitive?.content)

        val requiredRoot = root["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(requiredRoot.containsAll(listOf(
            "bookTitle",
            "gradeName",
            "gradeKey",
            "subjectName",
            "subjectKey",
            "sourceLayout",
            "sourceLayoutEvidence",
            "partKey",
            "offset",
            "units",
            "discoveryNotes"
        )))

        val rootProps = root["properties"]!!.jsonObject
        assertTrue(rootProps.containsKey("componentKey"))
        assertTrue(rootProps.containsKey("discoveryConfidence"))

        val unitObj = rootProps["units"]!!.jsonObject["items"]!!.jsonObject
        val unitProps = unitObj["properties"]!!.jsonObject
        assertTrue(unitProps.containsKey("unitKey"))
        assertTrue(unitProps.containsKey("partKey"))
        assertTrue(unitProps.containsKey("proposedPartKey"))
        assertTrue(unitProps.containsKey("assignmentStatus"))
        assertTrue(unitProps.containsKey("assignmentSource"))
        assertTrue(unitProps.containsKey("assignmentEvidence"))

        val lessonObj = unitProps["lessons"]!!.jsonObject["items"]!!.jsonObject
        val lessonProps = lessonObj["properties"]!!.jsonObject
        assertTrue(lessonProps.containsKey("lessonKey"))
        assertTrue(lessonProps.containsKey("printedPageStart"))
        assertTrue(lessonProps.containsKey("printedPageEnd"))
        assertTrue(lessonProps.containsKey("pdfPageStart"))
        assertTrue(lessonProps.containsKey("pdfPageEnd"))
    }

    @Test
    fun stage2Guard_unconfirmedDraftCannotPersistOrSplit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tempSrc = File(context.cacheDir, "unconfirmed_src_${System.currentTimeMillis()}.pdf")
        try {
            createSamplePdf(tempSrc, pageCount = 4)
            val splitter = PdfBookSplitter(context)
            val pdfInfo = splitter.ingestBookPdf(Uri.fromFile(tempSrc))
            assertEquals(4, pdfInfo.totalPages)
            assertTrue(File(pdfInfo.storedPdfPath).isFile)

            val repo = buildTestRepository(context, db)
            val unconfirmedDraft = BookIndexIntakeDraft(
                sourceFileName = pdfInfo.fileName,
                sourceSha256 = pdfInfo.sha256,
                totalPdfPages = pdfInfo.totalPages,
                bookTitle = "العلوم",
                gradeKey = "G07",
                gradeName = "الصف السابع الأساسي",
                subjectKey = "SCI",
                subjectName = "العلوم",
                sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
                sourceLayoutEvidence = "غلاف الجزء الأول",
                partKey = "P01",
                offset = 0,
                units = listOf(
                    BookIndexUnitDraft(
                        unitKey = "unit_01",
                        title = "الوحدة الأولى",
                        partKey = "P01",
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.REVIEWER,
                        assignmentEvidence = "تحقق بشري",
                        lessons = listOf(
                            BookIndexLessonDraft(
                                lessonKey = "lesson_01",
                                title = "الدرس الأول",
                                printedPageStart = 1,
                                printedPageEnd = 4
                            )
                        )
                    )
                ),
                reviewerConfirmed = false,
                reviewerId = null
            )

            val failure = runCatching {
                repo.approveBookIndexAndSplitWorkspace(unconfirmedDraft, pdfInfo)
            }
            assertTrue(failure.isFailure)
            assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, db.eduMiniDao().observeAllWorkspaces().first().size)
            // Staging file stays intact so reviewer can fix and retry
            assertTrue(File(pdfInfo.storedPdfPath).isFile)
            splitter.discardIngestedBookPdf(pdfInfo)
        } finally {
            tempSrc.delete()
            db.close()
        }
    }

    @Test
    fun stage4And5_singlePartBookPersistsRoomFirstGeneratesCanonicalFilesSplitsPdfAndBindsCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tempSrc = File(context.cacheDir, "single_part_science_${System.currentTimeMillis()}.pdf")
        var generatedRootDir: File? = null
        try {
            createSamplePdf(tempSrc, pageCount = 6)
            val splitter = PdfBookSplitter(context)
            val pdfInfo = splitter.ingestBookPdf(Uri.fromFile(tempSrc))
            assertEquals(6, pdfInfo.totalPages)

            val repo = buildTestRepository(context, db)
            val draft = BookIndexIntakeDraft(
                sourceFileName = pdfInfo.fileName,
                sourceSha256 = pdfInfo.sha256,
                totalPdfPages = pdfInfo.totalPages,
                bookTitle = "العلوم - الجزء الأول",
                gradeKey = "G07",
                gradeName = "الصف السابع الأساسي",
                subjectKey = "SCI",
                subjectName = "العلوم",
                sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
                sourceLayoutEvidence = "عنوان الغلاف يشير للجزء الأول P01",
                partKey = "P01",
                offset = 2,
                units = listOf(
                    BookIndexUnitDraft(
                        unitKey = "unit_01",
                        title = "المادة وتفاعلاتها",
                        partKey = "P01",
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.REVIEWER,
                        assignmentEvidence = "تأكيد المراجع على الجزء الأول",
                        lessons = listOf(
                            BookIndexLessonDraft(
                                lessonKey = "lesson_01",
                                title = "الخصائص الفيزيائية",
                                printedPageStart = 1,
                                printedPageEnd = 2,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER,
                                assignmentEvidence = "صفحة 1-2 مطبوعة = 3-4 فعلية"
                            ),
                            BookIndexLessonDraft(
                                lessonKey = "lesson_02",
                                title = "التغيرات الكيميائية",
                                printedPageStart = 3,
                                printedPageEnd = 4,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER,
                                assignmentEvidence = "صفحة 3-4 مطبوعة = 5-6 فعلية"
                            )
                        )
                    )
                ),
                discoveryMethod = IndexDiscoveryMethod.AI_PROMPT,
                reviewerConfirmed = true,
                reviewerId = "reviewer_01"
            )

            val workspace = repo.approveBookIndexAndSplitWorkspace(draft, pdfInfo)
            val destDir = File(workspace.rootPath)
            generatedRootDir = destDir

            // 1. Verify temporary staging PDF deleted, original user PDF untouched
            assertFalse(File(pdfInfo.storedPdfPath).exists())
            assertTrue(tempSrc.isFile)

            // 2. Verify Room persistence FIRST and final paths
            val dao = db.eduMiniDao()
            val persistedBook = dao.getBookSnapshot(workspace.workspaceId, "SCI|P01")
            assertNotNull(persistedBook)
            assertEquals("APPROVED", persistedBook!!.approvalStatus)
            assertFalse(persistedBook.requiresReview)
            assertEquals("SINGLE_PART_FILE", persistedBook.sourceLayout)
            assertEquals("P01", persistedBook.partKey)
            assertEquals(2, persistedBook.offset)
            assertEquals("VERIFIED", persistedBook.sourceReviewStatus)

            val persistedUnits = dao.getWorkspaceUnitsForBookSnapshot(workspace.workspaceId, "SCI|P01")
            assertEquals(1, persistedUnits.size)
            assertEquals("assigned", persistedUnits.single().assignmentStatus)

            val persistedLessons = dao.getLessonsForBookSnapshot(workspace.workspaceId, "SCI|P01")
                .sortedBy { it.sortOrder }
            assertEquals(2, persistedLessons.size)
            assertEquals(3, persistedLessons[0].pdfPageStart)
            assertEquals(4, persistedLessons[0].pdfPageEnd)
            assertEquals(5, persistedLessons[1].pdfPageStart)
            assertEquals(6, persistedLessons[1].pdfPageEnd)

            // 3. Verify canonical SINGLE_PART_FILE index & manifest under P01/
            val indexFile = File(destDir, "workspace/G07/SCI/P01/index.json")
            val manifestFile = File(destDir, "workspace/G07/SCI/P01/book_manifest.json")
            assertTrue(indexFile.isFile)
            assertTrue(manifestFile.isFile)

            val indexRaw = indexFile.readText(Charsets.UTF_8)
            val manifestRaw = manifestFile.readText(Charsets.UTF_8)
            val manifestObj = json.parseToJsonElement(manifestRaw).jsonObject
            assertEquals("edu.book_manifest.v2", manifestObj["manifestVersion"]?.jsonPrimitive?.content)
            assertEquals("P01/index.json", manifestObj["canonicalIndexPath"]?.jsonPrimitive?.content)
            assertEquals(sha256Hex(indexRaw), manifestObj["canonicalIndexSha256"]?.jsonPrimitive?.content)

            val indexRecord = dao.getWorkspaceIndexForBook(workspace.workspaceId, "SCI|P01")
            assertNotNull(indexRecord)
            assertEquals(sha256Hex(indexRaw), indexRecord!!.indexSha256)
            assertEquals(sha256Hex(manifestRaw), indexRecord.manifestSha256)

            // 4. Verify sliced PDFs (no double offset!) & extracted text
            val lesson1Pdf = File(destDir, "workspace/G07/SCI/P01/units/unit_01/lesson_01.pdf")
            val lesson2Pdf = File(destDir, "workspace/G07/SCI/P01/units/unit_01/lesson_02.pdf")
            val lesson1Txt = File(destDir, "workspace/G07/SCI/P01/units/unit_01/lesson_01.txt")
            assertTrue(lesson1Pdf.isFile)
            assertTrue(lesson2Pdf.isFile)
            assertTrue(lesson1Txt.isFile)
            assertTrue(lesson1Txt.readText().contains("Sample textbook page 3"))

            PDDocument.load(lesson1Pdf, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                assertEquals(2, doc.numberOfPages)
            }
            PDDocument.load(lesson2Pdf, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                assertEquals(2, doc.numberOfPages)
            }

            // 5. Verify WorkspaceContractValidator & DirectWorkspaceBinder round-trip
            val availablePaths = destDir.walkTopDown()
                .filter { it.isFile }
                .map { destDir.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
                .toSet()
            val contract = WorkspaceContractValidator.validate(
                indexRaw = indexRaw,
                manifestRaw = manifestRaw,
                expectedGrade = "G07",
                expectedSubject = "SCI",
                bookRelativePath = "workspace/G07/SCI/P01",
                availableRelativePaths = availablePaths,
                availableIndexSha256 = mapOf("workspace/g07/sci/p01/index.json" to sha256Hex(indexRaw))
            )
            assertTrue(contract.isValid)
            assertTrue(contract.errors.isEmpty())
            assertTrue(contract.reviewRequiredErrors.isEmpty())

            val binder = DirectWorkspaceBinder(context)
            val inspection = binder.inspectDirectory(destDir)
            assertEquals(1, inspection.grades.size)
            assertEquals(1, inspection.grades.single().booksCount)
            assertTrue(inspection.errors.isEmpty())
            assertTrue(inspection.warnings.isEmpty())

            val bound = binder.bindSingleGrade(destDir.absolutePath, "G07")
            assertEquals(1, bound.workspace.books.size)
            assertEquals(2, bound.workspace.lessons.size)
            assertEquals(lesson1Pdf.absolutePath, bound.workspace.lessons[0].pdfPath)
            assertEquals(lesson1Txt.absolutePath, bound.workspace.lessons[0].sourceTextPath)
        } finally {
            generatedRootDir?.deleteRecursively()
            tempSrc.delete()
            db.close()
        }
    }

    @Test
    fun stage4And5_mergedPartsBookPersistsRoomFirstGeneratesRootAndPartIndexesSplitsBothPartsAndBindsCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tempSrc = File(context.cacheDir, "merged_geo_${System.currentTimeMillis()}.pdf")
        var generatedRootDir: File? = null
        try {
            createSamplePdf(tempSrc, pageCount = 8)
            val splitter = PdfBookSplitter(context)
            val pdfInfo = splitter.ingestBookPdf(Uri.fromFile(tempSrc))
            assertEquals(8, pdfInfo.totalPages)

            val repo = buildTestRepository(context, db)
            val mergedDraft = BookIndexIntakeDraft(
                sourceFileName = pdfInfo.fileName,
                sourceSha256 = pdfInfo.sha256,
                totalPdfPages = pdfInfo.totalPages,
                bookTitle = "الجغرافيا - الجزآن الأول والثاني",
                gradeKey = "G08",
                gradeName = "الصف الثامن الأساسي",
                subjectKey = "SOC",
                subjectName = "الاجتماعيات",
                componentKey = "GEO",
                sourceLayout = IndexSourceLayout.MERGED_PARTS_FILE,
                sourceLayoutEvidence = "الملف يضم وحدات الفصلين الأول والثاني معاً",
                partKey = "BOTH",
                offset = 1,
                units = listOf(
                    BookIndexUnitDraft(
                        unitKey = "unit_01",
                        title = "وحدة الجزء الأول",
                        partKey = "P01",
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.REVIEWER,
                        assignmentEvidence = "تنتمي للجزء الأول",
                        lessons = listOf(
                            BookIndexLessonDraft(
                                lessonKey = "lesson_01",
                                title = "تضاريس العالم",
                                printedPageStart = 1,
                                printedPageEnd = 2,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER,
                                assignmentEvidence = "فهرس الجزء الأول"
                            )
                        )
                    ),
                    BookIndexUnitDraft(
                        unitKey = "unit_02",
                        title = "وحدة الجزء الثاني",
                        partKey = "P02",
                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                        assignmentSource = IndexAssignmentSource.REVIEWER,
                        assignmentEvidence = "تنتمي للجزء الثاني",
                        lessons = listOf(
                            BookIndexLessonDraft(
                                lessonKey = "lesson_01",
                                title = "المناخ والموارد",
                                printedPageStart = 5,
                                printedPageEnd = 7,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER,
                                assignmentEvidence = "فهرس الجزء الثاني"
                            )
                        )
                    )
                ),
                discoveryMethod = IndexDiscoveryMethod.AI_PROMPT,
                reviewerConfirmed = true,
                reviewerId = "reviewer_02"
            )

            val workspace = repo.approveBookIndexAndSplitWorkspace(mergedDraft, pdfInfo)
            val destDir = File(workspace.rootPath)
            generatedRootDir = destDir

            val dao = db.eduMiniDao()
            val persistedBook = dao.getBookSnapshot(workspace.workspaceId, "SOC/GEO")
            assertNotNull(persistedBook)
            assertEquals("MERGED_PARTS_FILE", persistedBook!!.sourceLayout)
            assertEquals("BOTH", persistedBook.partKey)
            assertEquals("APPROVED", persistedBook.approvalStatus)

            val rootIndexFile = File(destDir, "workspace/G08/SOC/GEO/index.json")
            val rootManifestFile = File(destDir, "workspace/G08/SOC/GEO/book_manifest.json")
            val p01IndexFile = File(destDir, "workspace/G08/SOC/GEO/P01/index.json")
            val p02IndexFile = File(destDir, "workspace/G08/SOC/GEO/P02/index.json")
            assertTrue(rootIndexFile.isFile)
            assertTrue(rootManifestFile.isFile)
            assertTrue(p01IndexFile.isFile)
            assertTrue(p02IndexFile.isFile)

            // Derived part views filter units per part
            val p01Units = json.parseToJsonElement(p01IndexFile.readText()).jsonObject["units"]!!.jsonArray
            val p02Units = json.parseToJsonElement(p02IndexFile.readText()).jsonObject["units"]!!.jsonArray
            assertEquals(1, p01Units.size)
            assertEquals("unit_01", p01Units[0].jsonObject["unitKey"]?.jsonPrimitive?.content)
            assertEquals(1, p02Units.size)
            assertEquals("unit_02", p02Units[0].jsonObject["unitKey"]?.jsonPrimitive?.content)

            // Manifest records canonical root index + SHA hashes for P01 & P02 derived indexes
            val manifestObj = json.parseToJsonElement(rootManifestFile.readText()).jsonObject
            assertEquals("index.json", manifestObj["canonicalIndexPath"]?.jsonPrimitive?.content)
            val partIndexes = manifestObj["partIndexes"]!!.jsonArray
            assertEquals(2, partIndexes.size)

            // Lesson PDFs sliced into P01/units/... and P02/units/... from the single merged PDF
            val p01Pdf = File(destDir, "workspace/G08/SOC/GEO/P01/units/unit_01/lesson_01.pdf")
            val p02Pdf = File(destDir, "workspace/G08/SOC/GEO/P02/units/unit_02/lesson_01.pdf")
            assertTrue(p01Pdf.isFile)
            assertTrue(p02Pdf.isFile)
            PDDocument.load(p01Pdf, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                assertEquals(2, doc.numberOfPages)
            }
            PDDocument.load(p02Pdf, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                assertEquals(3, doc.numberOfPages)
            }

            val binder = DirectWorkspaceBinder(context)
            val inspection = binder.inspectDirectory(destDir)
            assertEquals(1, inspection.grades.size)
            assertEquals(1, inspection.grades.single().booksCount)
            assertTrue(inspection.errors.isEmpty())
            assertTrue(inspection.warnings.isEmpty())

            val bound = binder.bindSingleGrade(destDir.absolutePath, "G08")
            assertEquals(1, bound.workspace.books.size)
            assertEquals(2, bound.workspace.lessons.size)
            assertTrue(bound.workspace.lessons.any { it.partKey == "P01" && it.pdfPath == p01Pdf.absolutePath })
            assertTrue(bound.workspace.lessons.any { it.partKey == "P02" && it.pdfPath == p02Pdf.absolutePath })
        } finally {
            generatedRootDir?.deleteRecursively()
            tempSrc.delete()
            db.close()
        }
    }

    @Test
    fun stage1And2_mainViewModelOpensReviewDraftAllowsEditsAndCleansStagingPdfOnDismiss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tempSrc = File(context.cacheDir, "vm_intake_${System.currentTimeMillis()}.pdf")
        try {
            createSamplePdf(tempSrc, pageCount = 6)
            val repo = buildTestRepository(context, db)
            val nullSync = FirestoreSyncRepository(db = null, auth = null)
            val vm = MainViewModel(
                repository = repo,
                firestoreSync = nullSync,
                currentUserId = "local_reviewer",
                isCloudAuthenticated = false,
                analyticsTracker = AnalyticsTracker(context)
            )

            vm.startBookPdfDiscoveryIntake(Uri.fromFile(tempSrc))
            val deadline = System.currentTimeMillis() + 8_000L
            while (System.currentTimeMillis() < deadline &&
                vm.uiState.value.bookIndexReview == null &&
                vm.uiState.value.alert == null
            ) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(40)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val alert = vm.uiState.value.alert
            val openedReview = vm.uiState.value.bookIndexReview
            assertNotNull(
                "Expected BookIndexReviewUiState to open after PDF intake (isBusy=${vm.uiState.value.isBusy}, alert=${alert?.title}: ${alert?.message})",
                openedReview
            )
            assertEquals(6, openedReview!!.pdfInfo.totalPages)
            assertFalse(openedReview.draft.reviewerConfirmed)
            val stagingFile = File(openedReview.pdfInfo.storedPdfPath)
            assertTrue(stagingFile.isFile)

            vm.populateStarterUnitsForReview(pagesPerLesson = 3)
            val afterStarter = vm.uiState.value.bookIndexReview!!.draft
            assertEquals(1, afterStarter.units.size)
            assertEquals(2, afterStarter.units.single().lessons.size)

            vm.updateBookIndexDraft { d ->
                d.copy(
                    bookTitle = "العلوم المعدل",
                    gradeKey = "G06",
                    gradeName = "الصف السادس الأساسي",
                    subjectKey = "SCI",
                    subjectName = "العلوم",
                    sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
                    partKey = "P01",
                    sourceLayoutEvidence = "تحقق المراجع من الغلاف",
                    units = d.units.map { u ->
                        u.copy(
                            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                            assignmentSource = IndexAssignmentSource.REVIEWER,
                            assignmentEvidence = "مؤكد من المراجع",
                            lessons = u.lessons.map { l ->
                                l.copy(
                                    assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                    assignmentSource = IndexAssignmentSource.REVIEWER
                                )
                            }
                        )
                    },
                    reviewerConfirmed = true,
                    reviewerId = "tester"
                )
            }
            val edited = vm.uiState.value.bookIndexReview!!.draft
            assertEquals(emptyList<String>(), edited.validationErrors())
            assertTrue(edited.canApprove())

            vm.dismissBookIndexReview()
            assertEquals(null, vm.uiState.value.bookIndexReview)
            assertFalse(stagingFile.exists())
            assertTrue(tempSrc.isFile)
        } finally {
            tempSrc.delete()
            db.close()
        }
    }

    @Test
    fun stage1To5_learnerLocalPdfIntakeApprovesAndEnrollsGradeWhenUnassigned() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EduMiniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tempSrc = File(context.cacheDir, "learner_math_${System.currentTimeMillis()}.pdf")
        var generatedRootDir: File? = null
        try {
            createSamplePdf(tempSrc, pageCount = 4)
            val repo = buildTestRepository(context, db)
            val nullSync = FirestoreSyncRepository(db = null, auth = null)
            val vm = MainViewModel(
                repository = repo,
                firestoreSync = nullSync,
                currentUserId = "student_01",
                isCloudAuthenticated = false,
                analyticsTracker = AnalyticsTracker(context)
            )
            vm.selectRole(UserRole.LEARNER)
            assertEquals(UserRole.LEARNER, vm.uiState.value.userRole)

            vm.startBookPdfDiscoveryIntake(Uri.fromFile(tempSrc))
            val intakeDeadline = System.currentTimeMillis() + 8_000L
            while (System.currentTimeMillis() < intakeDeadline &&
                vm.uiState.value.bookIndexReview == null &&
                vm.uiState.value.alert == null
            ) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(40)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(vm.uiState.value.bookIndexReview)

            vm.populateStarterUnitsForReview(pagesPerLesson = 2)
            vm.updateBookIndexDraft { d ->
                d.copy(
                    bookTitle = "الرياضيات للصف الخامس",
                    gradeKey = "G05",
                    gradeName = "الصف الخامس الأساسي",
                    subjectKey = "MATH",
                    subjectName = "الرياضيات",
                    sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
                    partKey = "P01",
                    sourceLayoutEvidence = "اختار الطالب كتاب الرياضيات الجزء الأول",
                    units = d.units.map { u ->
                        u.copy(
                            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                            assignmentSource = IndexAssignmentSource.REVIEWER,
                            assignmentEvidence = "تأكيد الطالب",
                            lessons = u.lessons.map { l ->
                                l.copy(
                                    assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                    assignmentSource = IndexAssignmentSource.REVIEWER
                                )
                            }
                        )
                    },
                    reviewerConfirmed = true,
                    reviewerId = "student_01"
                )
            }

            vm.approveAndSplitBookIndex()
            val approveDeadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < approveDeadline &&
                (vm.uiState.value.bookIndexReview != null || vm.uiState.value.isBusy) &&
                vm.uiState.value.alert == null
            ) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(50)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertEquals(null, vm.uiState.value.alert)
            assertEquals(null, vm.uiState.value.bookIndexReview)
            assertEquals("MATH|P01", vm.uiState.value.selectedBookKey)
            assertEquals("G05", vm.uiState.value.activeLearner.enrolledGradeKey)

            val wsId = vm.uiState.value.selectedWorkspaceId
            assertNotNull(wsId)
            val wsEntity = db.eduMiniDao().getWorkspaceByIdSnapshot(wsId!!)
            assertNotNull(wsEntity)
            generatedRootDir = File(wsEntity!!.rootPath)
            assertTrue(File(generatedRootDir, "workspace/G05/MATH/P01/index.json").isFile)
            assertTrue(File(generatedRootDir, "workspace/G05/MATH/P01/units/unit_01/lesson_01.pdf").isFile)
        } finally {
            generatedRootDir?.deleteRecursively()
            tempSrc.delete()
            db.close()
        }
    }
}
