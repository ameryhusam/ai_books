package com.example.data.workspace

import com.example.data.local.DraftEntity
import com.example.model.ImportedWorkspace
import com.example.model.QuestionItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

object WorkspaceExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportIndexer2CompatibleZip(
        outputStream: OutputStream,
        workspace: ImportedWorkspace,
        drafts: List<DraftEntity>,
        questions: List<QuestionItem>
    ): ExportReceipt {
        val entriesMap = linkedMapOf<String, ByteArray>()

        // 1. Export drafts per lesson under edu_mini_generated/<unitKey>/<lessonKey>/ + ai_content/
        val draftsByLesson = drafts.groupBy { Triple(it.bookKey, it.unitKey, it.lessonKey) }
        draftsByLesson.forEach { (key, list) ->
            val unitKey = key.second
            val lessonKey = key.third
            val latest = list.maxByOrNull { it.version } ?: list.first()
            val fileName = when (latest.task) {
                "interactive" -> "interactive-lesson-draft.json"
                "questions" -> "ai-question-drafts.json"
                else -> "lesson-explanation-draft.json"
            }
            val draftPayload = buildJsonObject {
                put("workspaceId", workspace.workspaceId)
                put("gradeKey", workspace.gradeKey)
                put("bookKey", key.first)
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

            entriesMap["edu_mini_generated/$unitKey/$lessonKey/$fileName"] = draftPayload
            entriesMap["ai_content/${key.first}/$unitKey/$lessonKey/${latest.task}_v${latest.version}.json"] = draftPayload
        }

        // 2. Export canonical questions/<unitKey>/<lessonKey>/ai_generated.json
        val questionsByLesson = questions.groupBy { it.unitKey to it.lessonKey }
        questionsByLesson.forEach { (key, qList) ->
            val qPayload = buildJsonObject {
                put("unitKey", key.first)
                put("lessonKey", key.second)
                put("count", qList.size)
                put("questions", buildJsonArray {
                    qList.forEach { q ->
                        add(buildJsonObject {
                            put("id", q.id)
                            put("type", q.type.id)
                            put("prompt", q.prompt)
                            put("options", buildJsonArray {
                                q.options.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
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
            entriesMap["questions/${key.first}/${key.second}/ai_generated.json"] = qPayload
        }

        // 3. Export manifest.json with SHA-256 checksums for every exported artifact
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

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
