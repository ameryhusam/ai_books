package com.example.data.repository

import android.net.Uri
import com.example.data.ai.AiGenerationService
import com.example.data.local.BookEntity
import com.example.data.local.DraftEntity
import com.example.data.local.EduMiniDao
import com.example.data.local.LessonEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.security.ProviderStorage
import com.example.data.workspace.WorkspaceImporter
import com.example.model.BookRef
import com.example.model.DraftRecord
import com.example.model.GeneratedResult
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import com.example.model.ProviderConfig
import com.example.model.TaskType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

class EduMiniRepository(
    private val dao: EduMiniDao,
    private val providerStorage: ProviderStorage,
    private val workspaceImporter: WorkspaceImporter,
    private val aiGenerationService: AiGenerationService
) {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestWorkspace: Flow<ImportedWorkspace?> = dao.observeLatestWorkspace().flatMapLatest { entity ->
        if (entity == null) {
            flowOf(null)
        } else {
            combine(
                dao.observeBooksForWorkspace(entity.workspaceId),
                dao.observeLessonsForWorkspace(entity.workspaceId)
            ) { bookEntities, lessonEntities ->
                val warnings = runCatching {
                    json.decodeFromString<List<String>>(entity.warningsJson)
                }.getOrDefault(emptyList())

                ImportedWorkspace(
                    workspaceId = entity.workspaceId,
                    gradeKey = entity.gradeKey,
                    rootPath = entity.rootPath,
                    title = entity.title,
                    books = bookEntities.map { it.toModel() },
                    lessons = lessonEntities.map { it.toModel() },
                    filesCount = entity.filesCount,
                    warnings = warnings,
                    importedAt = entity.importedAt
                )
            }
        }
    }

    suspend fun importWorkspaceZip(uri: Uri): ImportedWorkspace {
        val imported = workspaceImporter.importFromUri(uri)
        saveImportedWorkspace(imported)
        return imported
    }

    suspend fun saveImportedWorkspace(imported: ImportedWorkspace) {
        val workspaceEntity = WorkspaceEntity(
            workspaceId = imported.workspaceId,
            gradeKey = imported.gradeKey,
            rootPath = imported.rootPath,
            title = imported.title,
            filesCount = imported.filesCount,
            warningsJson = json.encodeToString(imported.warnings),
            importedAt = imported.importedAt
        )
        val bookEntities = imported.books.map { book ->
            BookEntity(
                workspaceId = imported.workspaceId,
                bookKey = book.bookKey,
                title = book.title,
                subjectKey = book.subjectKey,
                componentKey = book.componentKey,
                relativePath = book.relativePath,
                lessonsCount = book.lessonsCount
            )
        }
        val lessonEntities = imported.lessons.map { lesson ->
            LessonEntity(
                workspaceId = imported.workspaceId,
                grade = lesson.grade,
                rootPath = lesson.rootPath,
                bookKey = lesson.bookKey,
                bookTitle = lesson.bookTitle,
                subjectKey = lesson.subjectKey,
                componentKey = lesson.componentKey,
                unitKey = lesson.unitKey,
                unitTitle = lesson.unitTitle,
                lessonKey = lesson.lessonKey,
                title = lesson.title,
                partKey = lesson.partKey,
                pageRange = lesson.pageRange,
                pdfPath = lesson.pdfPath
            )
        }
        dao.saveWorkspaceGraph(workspaceEntity, bookEntities, lessonEntities)
    }

    suspend fun loadProviders(): List<ProviderConfig> = providerStorage.loadProviders()

    suspend fun saveProviders(providers: List<ProviderConfig>) {
        providerStorage.saveProviders(providers)
    }

    suspend fun generateContent(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        providers: List<ProviderConfig>
    ): GeneratedResult {
        return aiGenerationService.generate(lesson, task, source, extra, providers)
    }

    suspend fun persistDraft(
        lesson: LessonRef,
        task: TaskType,
        providerLabel: String,
        model: String,
        text: String,
        title: String
    ): DraftRecord {
        val id = "draft-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val createdAt = Instant.now().toString()
        val record = DraftRecord(
            id = id,
            workspaceId = lesson.rootPath,
            grade = lesson.grade,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey,
            task = task,
            title = title,
            provider = providerLabel,
            model = model,
            createdAt = createdAt,
            content = text,
            status = "DRAFT"
        )
        dao.insertDraft(
            DraftEntity(
                id = record.id,
                workspaceId = record.workspaceId,
                gradeKey = record.grade,
                bookKey = record.bookKey,
                unitKey = record.unitKey,
                lessonKey = record.lessonKey,
                task = record.task.id,
                title = record.title,
                provider = record.provider,
                model = record.model,
                createdAt = record.createdAt,
                content = record.content,
                status = record.status
            )
        )
        return record
    }

    fun observeDraftsForLesson(lesson: LessonRef): Flow<List<DraftRecord>> {
        return dao.observeDraftsForLesson(
            workspaceId = lesson.rootPath,
            bookKey = lesson.bookKey,
            unitKey = lesson.unitKey,
            lessonKey = lesson.lessonKey
        ).map { list ->
            list.map { entity ->
                DraftRecord(
                    id = entity.id,
                    workspaceId = entity.workspaceId,
                    grade = entity.gradeKey,
                    bookKey = entity.bookKey,
                    unitKey = entity.unitKey,
                    lessonKey = entity.lessonKey,
                    task = TaskType.fromId(entity.task),
                    title = entity.title,
                    provider = entity.provider,
                    model = entity.model,
                    createdAt = entity.createdAt,
                    content = entity.content,
                    status = entity.status
                )
            }
        }
    }

    suspend fun deleteDraft(draftId: String) {
        dao.deleteDraftById(draftId)
    }

    private fun BookEntity.toModel() = BookRef(
        bookKey = bookKey,
        title = title,
        subjectKey = subjectKey,
        componentKey = componentKey,
        relativePath = relativePath,
        lessonsCount = lessonsCount
    )

    private fun LessonEntity.toModel() = LessonRef(
        grade = grade,
        rootPath = rootPath,
        bookKey = bookKey,
        bookTitle = bookTitle,
        subjectKey = subjectKey,
        componentKey = componentKey,
        unitKey = unitKey,
        unitTitle = unitTitle,
        lessonKey = lessonKey,
        title = title,
        partKey = partKey,
        pageRange = pageRange,
        pdfPath = pdfPath
    )
}
