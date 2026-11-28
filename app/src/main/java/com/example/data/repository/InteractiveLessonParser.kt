package com.example.data.repository

import com.example.model.InteractiveLessonModel
import com.example.model.InteractiveLessonSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object InteractiveLessonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseStrictJson(
        rawText: String,
        allowedBookQuestionIds: Set<String>? = null
    ): InteractiveLessonModel? {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(cleaned) as? JsonObject }.getOrNull() ?: return null
        val allowedRoot = setOf("schemaVersion", "title", "objective", "introduction", "sections", "keyPoints", "summary", "bookQuestionIds")
        if (root.keys.any { it !in allowedRoot }) return null
        val title = string(root, "title")
        val objective = string(root, "objective")
        val introduction = string(root, "introduction")
        val sectionsArray = root["sections"] as? JsonArray ?: return null
        val sections = sectionsArray.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val sectionTitle = string(item, "title")
            val explanation = string(item, "explanation")
            if (sectionTitle.isBlank() || explanation.isBlank()) return@mapIndexedNotNull null
            val checkpoint = item["checkpoint"] as? JsonObject
            InteractiveLessonSection(
                title = sectionTitle,
                conceptTag = string(item, "conceptTag"),
                explanation = explanation,
                visualType = string(item, "visualType"),
                visualDescription = string(item, "visualDescription"),
                workedExample = string(item, "workedExample"),
                checkpointQuestion = checkpoint?.let { string(it, "question") }.orEmpty(),
                checkpointOptions = checkpoint?.let { stringList(it["options"]) }.orEmpty(),
                checkpointAnswer = checkpoint?.let { string(it, "answer") }.orEmpty()
            )
        }
        if (title.isBlank() || sections.isEmpty()) return null
        val keyPoints = stringList(root["keyPoints"])
        val questionIds = stringList(root["bookQuestionIds"]).distinct()
        if (allowedBookQuestionIds != null && questionIds.any { it !in allowedBookQuestionIds }) return null
        return InteractiveLessonModel(
            title = title,
            objective = objective,
            introduction = introduction,
            sections = sections,
            keyPoints = keyPoints,
            summary = string(root, "summary"),
            bookQuestionIds = questionIds
        )
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> =
        (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.filter { it.isNotBlank() }.orEmpty()
}