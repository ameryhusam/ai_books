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
import com.example.model.LearnerProfile
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
    val sourceHash: String? = null,
    val templateId: String = "",
    val templateVersion: Int = 1,
    val responseSchemaHash: String = "",
    val requestFingerprint: String = "",
    val contentHash: String = "",
    val sourcePdfHash: String = ""
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
    val createdAt: String,
    val conceptTag: String = ""
)

@Serializable
data class BackupQuestionAttemptDto(
    val id: String,
    val workspaceId: String,
    val userId: String = "learner_default",
    val questionId: String,
    val conceptTag: String,
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val sourceMode: String = "lesson_quiz",
    val answeredAt: String
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
    val completedAt: String,
    val userId: String = "learner_default"
)

@Serializable
data class BackupProgressDto(
    val workspaceId: String,
    val bookKey: String,
    val unitKey: String,
    val lessonKey: String,
    val completed: Boolean,
    val pointsEarned: Int,
    val completedAt: String? = null,
    val userId: String = "learner_default"
)

@Serializable
data class BackupStudyDayDto(
    val activityDate: String,
    val actionsCount: Int,
    val lastUpdatedAt: String,
    val userId: String = "learner_default"
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
    val createdAt: String,
    val responseSchemaJson: String = "",
    val responseMappingJson: String = "",
    val responseFormat: String = "text",
    val sourceAttachmentMode: String = "pdf_native",
    val version: Int = 1
)

@Serializable
data class BackupLearnerProfileDto(
    val userId: String,
    val displayName: String
)

@Serializable
data class EduMiniBackupBundle(
    val formatVersion: String = "edu-mini-backup-1.3",
    val exportedAt: String = "",
    val workspaceId: String = "",
    val learnerProfiles: List<BackupLearnerProfileDto> = emptyList(),
    val activeLearnerId: String = LearnerProfile.DEFAULT_USER_ID,
    val drafts: List<BackupDraftDto> = emptyList(),
    val questions: List<BackupQuestionDto> = emptyList(),
    val questionAttempts: List<BackupQuestionAttemptDto> = emptyList(),
    val assessments: List<BackupAssessmentDto> = emptyList(),
    val attempts: List<BackupAttemptDto> = emptyList(),
    val progress: List<BackupProgressDto> = emptyList(),
    val teacherNotes: List<BackupNoteDto> = emptyList(),
    val studyDays: List<String> = emptyList(),
    val promptTemplates: List<BackupPromptTemplateDto> = emptyList(),
    val studyDayRecords: List<BackupStudyDayDto> = emptyList()
)

object WorkspaceExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private data class RecordedArtifact(
        val path: String,
        val bytes: Long,
        val sha256: String
    )

    fun exportIndexer2CompatibleZip(
        outputStream: OutputStream,
        workspace: ImportedWorkspace,
        drafts: List<DraftEntity>,
        questions: List<QuestionItem>,
        assessments: List<AssessmentEntity> = emptyList(),
        fullBackupJson: String? = null,
        context: android.content.Context? = null
    ): ExportReceipt {
        val recordedArtifacts = mutableListOf<RecordedArtifact>()
        val writtenPaths = HashSet<String>()

        ZipOutputStream(outputStream).use { zos ->
            fun writeBytesEntry(path: String, data: ByteArray) {
                if (!writtenPaths.add(path)) return
                val hash = sha256(data)
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
                recordedArtifacts.add(RecordedArtifact(path, data.size.toLong(), hash))
            }

            fun writeStreamEntry(path: String, input: java.io.InputStream) {
                if (!writtenPaths.add(path)) {
                    input.close()
                    return
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var countBytes = 0L
                val buf = ByteArray(32 * 1024)
                zos.putNextEntry(ZipEntry(path))
                input.use { stream ->
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        digest.update(buf, 0, read)
                        zos.write(buf, 0, read)
                        countBytes += read
                    }
                }
                zos.closeEntry()
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                recordedArtifacts.add(RecordedArtifact(path, countBytes, hash))
            }

            // Stream source workspace files straight to ZIP without buffering everything in RAM
            if (UriIO.isContentUri(workspace.rootPath) && context != null) {
                val treeUri = android.net.Uri.parse(workspace.rootPath)
                streamSafTreeIntoZip(context, treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri), "") { rel, docUri ->
                    context.contentResolver.openInputStream(docUri)?.let { stream ->
                        writeStreamEntry(rel, stream)
                    }
                }
            } else {
                val workspaceRoot = File(workspace.rootPath)
                require(workspaceRoot.isDirectory) { "Workspace source directory is missing: ${workspace.rootPath}" }
                val rootPath = workspaceRoot.toPath()
                val canonicalWorkspaceRoot = workspaceRoot.canonicalPath + File.separator
                workspaceRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                    require(file.canonicalPath.startsWith(canonicalWorkspaceRoot)) {
                        "Workspace contains a file reference outside its root: ${file.path}"
                    }
                    val relative = rootPath.relativize(file.toPath()).toString().replace('\\', '/')
                    require(!relative.startsWith("../") && !relative.startsWith("/")) { "Unsafe workspace path: $relative" }
                    writeStreamEntry(relative, file.inputStream().buffered())
                }
            }

            if (!fullBackupJson.isNullOrBlank()) {
                writeBytesEntry("full_backup.json", fullBackupJson.toByteArray(Charsets.UTF_8))
            }

            // 1. Export drafts per lesson
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

                    writeBytesEntry("edu_mini_generated/$bookKey/$unitKey/$lessonKey/$fileName", draftPayload)
                    writeBytesEntry("ai_content/$bookKey/$unitKey/$lessonKey/$fileName", draftPayload)
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
                    writeBytesEntry("edu_mini_generated/$bookKey/$unitKey/$lessonKey/$legacyName", payload)
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
                                put("conceptTag", q.conceptTag)
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
                writeBytesEntry("questions/$bookKey/$unitKey/$lessonKey/ai_generated.json", qPayload)
            }

            // 3. Export assessments
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
                writeBytesEntry("assessments/assessments.json", assessmentsPayload)
            }

            // 4. Write manifest.json last using recorded SHA-256 checksums
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
                    recordedArtifacts.forEach { item ->
                        add(buildJsonObject {
                            put("path", item.path)
                            put("bytes", item.bytes)
                            put("sha256", item.sha256)
                        })
                    }
                })
            }
            val manifestBytes = json.encodeToString(manifestPayload).toByteArray(Charsets.UTF_8)
            writeBytesEntry("manifest.json", manifestBytes)
        }

        return ExportReceipt(
            entriesCount = recordedArtifacts.size,
            draftsCount = drafts.size,
            questionsCount = questions.size
        )
    }

    private fun streamSafTreeIntoZip(
        context: android.content.Context,
        treeUri: android.net.Uri,
        parentDocId: String,
        parentRel: String,
        onFile: (relativePath: String, documentUri: android.net.Uri) -> Unit
    ) {
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else continue
                    if (name.startsWith(".")) continue
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                    val rel = if (parentRel.isBlank()) name else "$parentRel/$name"
                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                        streamSafTreeIntoZip(context, treeUri, docId, rel, onFile)
                    } else {
                        onFile(rel, android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                    }
                }
            }
        }
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
        templates: List<PromptTemplateEntity>,
        questionAttempts: List<com.example.data.local.QuestionAttemptEntity> = emptyList(),
        learnerProfiles: List<LearnerProfile> = emptyList(),
        activeLearnerId: String = LearnerProfile.DEFAULT_USER_ID
    ): String {
        val bundle = EduMiniBackupBundle(
            exportedAt = Instant.now().toString(),
            workspaceId = workspaceId,
            learnerProfiles = learnerProfiles.distinctBy { it.userId }.map {
                BackupLearnerProfileDto(userId = it.userId, displayName = it.displayName)
            },
            activeLearnerId = activeLearnerId,
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
                    sourceHash = it.sourceHash,
                    templateId = it.templateId,
                    templateVersion = it.templateVersion,
                    responseSchemaHash = it.responseSchemaHash,
                    requestFingerprint = it.requestFingerprint,
                    contentHash = it.contentHash,
                    sourcePdfHash = it.sourcePdfHash
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
                    createdAt = it.createdAt,
                    conceptTag = it.conceptTag
                )
            },
            questionAttempts = questionAttempts.map {
                BackupQuestionAttemptDto(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    userId = it.userId,
                    questionId = it.questionId,
                    conceptTag = it.conceptTag,
                    selectedAnswer = it.selectedAnswer,
                    isCorrect = it.isCorrect,
                    sourceMode = it.sourceMode,
                    answeredAt = it.answeredAt
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
                    completedAt = it.completedAt,
                    userId = it.userId
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
                    completedAt = it.completedAt,
                    userId = it.userId
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
            studyDays = studyDays.map { it.activityDate }.distinct(),
            promptTemplates = templates.map {
                BackupPromptTemplateDto(
                    id = it.id,
                    title = it.title,
                    task = it.task,
                    instructions = it.instructions,
                    createdAt = it.createdAt,
                    responseSchemaJson = it.responseSchemaJson,
                    responseMappingJson = it.responseMappingJson,
                    responseFormat = it.responseFormat,
                    sourceAttachmentMode = it.sourceAttachmentMode,
                    version = it.version
                )
            },
            studyDayRecords = studyDays.map {
                BackupStudyDayDto(
                    activityDate = it.activityDate,
                    actionsCount = it.actionsCount,
                    lastUpdatedAt = it.lastUpdatedAt,
                    userId = it.userId
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
