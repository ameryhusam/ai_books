package com.example.data.workspace

import com.example.data.local.AssessmentAttemptEntity
import com.example.data.local.AssessmentEntity
import com.example.data.local.DraftEntity
import com.example.data.local.LessonProgressEntity
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.QuestionEntity
import com.example.data.local.StudyDayEntity
import com.example.data.local.TeacherNoteEntity
import com.example.model.ImportedWorkspace
import com.example.model.QuestionItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportReceipt(
    val entriesCount: Int,
    val draftsCount: Int,
    val questionsCount: Int
)

@Serializable
data class BackupDraftDto(
    val id: String,
    val workspaceId: String,
    val gradeKey: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val task: String,
    val title: String,
    val provider: String,
    val model: String,
    val createdAt: String,
    val content: String,
    val status: String,
    val version: Int,
    val sourcePages: String? = null,
    val sourceHash: String? = null
)

@Serializable
data class BackupQuestionDto(
    val id: String,
    val workspaceId: String,
    val gradeKey: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val lessonTitle: String,
    val type: String,
    val prompt: String,
    val optionsJson: String,
    val correctAnswer: String,
    val rationale: String,
    val difficulty: String,
    val sourcePages: String? = null,
    val fingerprint: String,
    val reviewed: Boolean,
    val createdAt: String
)

@Serializable
data class BackupAssessmentDto(
    val id: String,
    val workspaceId: String,
    val title: String,
    val kind: String,
    val questionIdsJson: String,
    val timeLimitMinutes: Int,
    val passScorePercent: Int,
    val createdAt: String
)

@Serializable
data class BackupAttemptDto(
    val id: String,
    val assessmentId: String,
    val assessmentTitle: String,
    val workspaceId: String,
    val correctCount: Int,
    val totalQuestions: Int,
    val scorePercent: Int,
    val passed: Boolean,
    val durationSeconds: Int,
    val earnedXp: Int,
    val completedAt: String
)

@Serializable
data class BackupProgressDto(
    val workspaceId: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val completed: Boolean,
    val pointsEarned: Int,
    val completedAt: String? = null
)

@Serializable
data class BackupNoteDto(
    val workspaceId: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val noteText: String,
    val bookmarked: Boolean,
    val updatedAt: String
)

@Serializable
data class BackupPromptTemplateDto(
    val id: String,
    val title: String,
    val task: String,
    val instructions: String,
    val createdAt: String
)

@Serializable
data class EduMiniBackupBundle(
    val formatVersion: String = "edu-mini-backup-1.0",
    val exportedAt: String = "",
    val workspaceId: String = "",
    val drafts: List<BackupDraftDto> = emptyList(),
    val questions: List<BackupQuestionDto> = emptyList(),
    val assessments: List<BackupAssessmentDto> = emptyList(),
    val attempts: List<BackupAttemptDto> = emptyList(),
    val progress: List<BackupProgressDto> = emptyList(),
    val teacherNotes: List<BackupNoteDto> = emptyList(),
    val studyDays: List<String> = emptyList(),
    val promptTemplates: List<BackupPromptTemplateDto> = emptyList()
)

object WorkspaceExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportIndexer2CompatibleZip(
        outputStream: OutputStream,
        workspace: ImportedWorkspace,
        drafts: List<DraftEntity>,
        questions: List<QuestionItem>,
        assessments: List<AssessmentEntity> = emptyList(),
        fullBackupJson: String? = null
    ): ExportReceipt {
        val entriesMap = linkedMapOf<String, ByteArray>()

        // Include the canonical source workspace (index, manifests, original PDFs,
        // lesson PDFs and assets). Generated artifacts alone are not importable.
        val workspaceRoot = File(workspace.rootPath)
        require(workspaceRoot.isDirectory) { "Workspace source directory is missing: ${workspace.rootPath}" }
        val rootPath = workspaceRoot.toPath()
        workspaceRoot.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = rootPath.relativize(file.toPath()).toString().replace('\\', '/')
            require(!relative.startsWith("../") && !relative.startsWith("/")) { "Unsafe workspace path: $relative" }
            entriesMap[relative] = file.readBytes()
        }
        if (!fullBackupJson.isNullOrBlank()) {
            entriesMap["full_backup.json"] = fullBackupJson.toByteArray(Charsets.UTF_8)
        }

        // 1. Export drafts per lesson under edu_mini_generated/<unitKey>/<lessonKey>/ + ai_content/
        val draftsByLesson = drafts.groupBy { listOf(it.workspaceId, it.bookKey, it.unitKey, it.lessonKey).joinToString("|") }
        draftsByLesson.forEach { (key, list) ->
            val identity = key.split("|")
            val bookKey = identity.getOrElse(1) { "book" }
            val unitKey = identity.getOrElse(2) { "unit" }
            val lessonKey = identity.getOrElse(3) { "lesson" }
            val orderedDrafts = list.sortedWith(compareBy<DraftEntity> { it.task }.thenBy { it.version })
            orderedDrafts.forEach { draft ->
                val fileName = "${draft.task.replace('_', '-')}-v${draft.version}.json"
                val draftPayload = buildJsonObject {
                    put("workspaceId", workspace.workspaceId)
                    put("gradeKey", workspace.gradeKey)
                    put("bookKey", bookKey)
                    put("unitKey", unitKey)
                    put("lessonKey", lessonKey)
                    put("task", draft.task)
                    put("version", draft.version)
                    put("status", draft.status)
                    put("provider", draft.provider)
                    put("model", draft.model)
                    put("sourcePages", draft.sourcePages.orEmpty())
                    put("sourceHash", draft.sourceHash.orEmpty())
                    put("createdAt", draft.createdAt)
                    put("content", draft.content)
                }.toString().toByteArray(Charsets.UTF_8)

                entriesMap["edu_mini_generated/$bookKey/$unitKey/$lessonKey/$fileName"] = draftPayload
                entriesMap["ai_content/$bookKey/$unitKey/$lessonKey/$fileName"] = draftPayload
            }
            val latestByTask = orderedDrafts.groupBy { it.task }.mapValues { (_, versions) ->
                versions.maxByOrNull { it.version }!!
            }
            latestByTask.forEach { (_, latest) ->
                val legacyName = when (latest.task) {
                    "interactive" -> "interactive-lesson-draft.json"
                    "questions" -> "ai-question-drafts.json"
                    else -> "${latest.task.replace('_', '-')}-draft.json"
                }
                val payload = buildJsonObject {
                    put("workspaceId", workspace.workspaceId)
                    put("gradeKey", workspace.gradeKey)
                    put("bookKey", bookKey)
                    put("unitKey", unitKey)
                    put("lessonKey", lessonKey)
                    put("task", latest.task)
                    put("version", latest.version)
                    put("status", latest.status)
                    put("provider", latest.provider)
                    put("model", latest.model)
                    put("sourcePages", latest.sourcePages.orEmpty())
                    put("sourceHash", latest.sourceHash.orEmpty())
                    put("createdAt", latest.createdAt)
                    put("content", latest.content)
                }.toString().toByteArray(Charsets.UTF_8)
                entriesMap["edu_mini_generated/$bookKey/$unitKey/$lessonKey/$legacyName"] = payload
            }
        }

        // 2. Export canonical questions/<unitKey>/<lessonKey>/ai_generated.json
        val questionsByLesson = questions.groupBy { listOf(it.workspaceId, it.bookKey, it.unitKey, it.lessonKey).joinToString("|") }
        questionsByLesson.forEach { (key, qList) ->
            val identity = key.split("|")
            val bookKey = identity.getOrElse(1) { "book" }
            val unitKey = identity.getOrElse(2) { "unit" }
            val lessonKey = identity.getOrElse(3) { "lesson" }
            val qPayload = buildJsonObject {
                put("workspaceId", identity.firstOrNull().orEmpty())
                put("bookKey", bookKey)
                put("unitKey", unitKey)
                put("lessonKey", lessonKey)
                put("count", qList.size)
                put("questions", buildJsonArray {
                    qList.forEach { q ->
                        add(buildJsonObject {
                            put("id", q.id)
                            put("type", q.type.id)
                            put("prompt", q.prompt)
                            put("options", buildJsonArray {
                                q.options.forEach { add(JsonPrimitive(it)) }
                            })
                            put("correctAnswer", q.correctAnswer)
                            put("rationale", q.rationale)
                            put("difficulty", q.difficulty.id)
                            put("sourcePages", q.sourcePages.orEmpty())
                            put("fingerprint", q.fingerprint)
                            put("reviewed", q.reviewed)
                        })
                    }
                })
            }.toString().toByteArray(Charsets.UTF_8)
            entriesMap["questions/$bookKey/$unitKey/$lessonKey/ai_generated.json"] = qPayload
        }

        // 3. Export assessments if present
        if (assessments.isNotEmpty()) {
            val assessmentsPayload = buildJsonObject {
                put("workspaceId", workspace.workspaceId)
                put("count", assessments.size)
                put("assessments", buildJsonArray {
                    assessments.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("title", a.title)
                            put("kind", a.kind)
                            put("questionIdsJson", a.questionIdsJson)
                            put("timeLimitMinutes", a.timeLimitMinutes)
                            put("passScorePercent", a.passScorePercent)
                            put("createdAt", a.createdAt)
                        })
                    }
                })
            }.toString().toByteArray(Charsets.UTF_8)
            entriesMap["assessments/assessments.json"] = assessmentsPayload
        }

        // 4. Export manifest.json with SHA-256 checksums for every exported artifact
        val manifestPayload = buildJsonObject {
            put("formatVersion", "edu-mini-indexer2-1.0")
            put("exportedAt", Instant.now().toString())
            put("workspaceId", workspace.workspaceId)
            put("gradeKey", workspace.gradeKey)
            put("workspaceTitle", workspace.title)
            put("booksCount", workspace.books.size)
            put("lessonsCount", workspace.lessons.size)
            put("draftsCount", drafts.size)
            put("questionsCount", questions.size)
            put("assessmentsCount", assessments.size)
            put("artifacts", buildJsonArray {
                entriesMap.forEach { (path, bytes) ->
                    add(buildJsonObject {
                        put("path", path)
                        put("bytes", bytes.size)
                        put("sha256", sha256(bytes))
                    })
                }
            })
        }
        entriesMap["manifest.json"] = json.encodeToString(manifestPayload).toByteArray(Charsets.UTF_8)

        ZipOutputStream(outputStream).use { zos ->
            entriesMap.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        return ExportReceipt(
            entriesCount = entriesMap.size,
            draftsCount = drafts.size,
            questionsCount = questions.size
        )
    }

    fun serializeFullBackup(
        workspaceId: String,
        drafts: List<DraftEntity>,
        questions: List<QuestionEntity>,
        assessments: List<AssessmentEntity>,
        attempts: List<AssessmentAttemptEntity>,
        progress: List<LessonProgressEntity>,
        notes: List<TeacherNoteEntity>,
        studyDays: List<StudyDayEntity>,
        templates: List<PromptTemplateEntity>
    ): String {
        val bundle = EduMiniBackupBundle(
            exportedAt = Instant.now().toString(),
            workspaceId = workspaceId,
            drafts = drafts.map {
                BackupDraftDto(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    gradeKey = it.gradeKey,
                    bookKey = it.bookKey,
                    unitKey = it.unitKey,
                    lessonKey = it.lessonKey,
                    task = it.task,
                    title = it.title,
                    provider = it.provider,
                    model = it.model,
                    createdAt = it.createdAt,
                    content = it.content,
                    status = it.status,
                    version = it.version,
                    sourcePages = it.sourcePages,
                    sourceHash = it.sourceHash
                )
            },
            questions = questions.map {
                BackupQuestionDto(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    gradeKey = it.gradeKey,
                    bookKey = it.bookKey,
                    unitKey = it.unitKey,
                    lessonKey = it.lessonKey,
                    lessonTitle = it.lessonTitle,
                    type = it.type,
                    prompt = it.prompt,
                    optionsJson = it.optionsJson,
                    correctAnswer = it.correctAnswer,
                    rationale = it.rationale,
                    difficulty = it.difficulty,
                    sourcePages = it.sourcePages,
                    fingerprint = it.fingerprint,
                    reviewed = it.reviewed,
                    createdAt = it.createdAt
                )
            },
            assessments = assessments.map {
                BackupAssessmentDto(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    title = it.title,
                    kind = it.kind,
                    questionIdsJson = it.questionIdsJson,
                    timeLimitMinutes = it.timeLimitMinutes,
                    passScorePercent = it.passScorePercent,
                    createdAt = it.createdAt
                )
            },
            attempts = attempts.map {
                BackupAttemptDto(
                    id = it.id,
                    assessmentId = it.assessmentId,
                    assessmentTitle = it.assessmentTitle,
                    workspaceId = it.workspaceId,
                    correctCount = it.correctCount,
                    totalQuestions = it.totalQuestions,
                    scorePercent = it.scorePercent,
                    passed = it.passed,
                    durationSeconds = it.durationSeconds,
                    earnedXp = it.earnedXp,
                    completedAt = it.completedAt
                )
            },
            progress = progress.map {
                BackupProgressDto(
                    workspaceId = it.workspaceId,
                    bookKey = it.bookKey,
                    unitKey = it.unitKey,
                    lessonKey = it.lessonKey,
                    completed = it.completed,
                    pointsEarned = it.pointsEarned,
                    completedAt = it.completedAt
                )
            },
            teacherNotes = notes.map {
                BackupNoteDto(
                    workspaceId = it.workspaceId,
                    bookKey = it.bookKey,
                    unitKey = it.unitKey,
                    lessonKey = it.lessonKey,
                    noteText = it.noteText,
                    bookmarked = it.bookmarked,
                    updatedAt = it.updatedAt
                )
            },
            studyDays = studyDays.map { it.activityDate },
            promptTemplates = templates.map {
                BackupPromptTemplateDto(
                    id = it.id,
                    title = it.title,
                    task = it.task,
                    instructions = it.instructions,
                    createdAt = it.createdAt
                )
            }
        )
        return json.encodeToString(bundle)
    }

    fun parseFullBackup(rawJson: String): EduMiniBackupBundle {
        return json.decodeFromString<EduMiniBackupBundle>(rawJson)
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
