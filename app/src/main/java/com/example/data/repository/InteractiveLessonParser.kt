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
        val sections = sectionsArray.mapIndexed { index, element ->
            val item = element as? JsonObject ?: return null
            val sectionTitle = string(item, "title")
            val explanation = string(item, "explanation")
            if (sectionTitle.isBlank() || explanation.isBlank()) return null
            val checkpoint = item["checkpoint"] as? JsonObject
            val checkpointQuestion = checkpoint?.let { string(it, "question") }.orEmpty()
            val checkpointOptions = checkpoint?.let { stringList(it["options"]) }.orEmpty()
            val checkpointAnswer = checkpoint?.let { string(it, "answer") }.orEmpty()
            if (checkpoint != null && (checkpointQuestion.isBlank() || checkpointOptions.size < 2 || checkpointAnswer !in checkpointOptions)) return null
            InteractiveLessonSection(
                title = sectionTitle,
                conceptTag = string(item, "conceptTag"),
                explanation = explanation,
                visualType = string(item, "visualType"),
                visualDescription = string(item, "visualDescription"),
                workedExample = string(item, "workedExample"),
                checkpointQuestion = checkpointQuestion,
                checkpointOptions = checkpointOptions,
                checkpointAnswer = checkpointAnswer
            )
        }
        if (sections.size != sectionsArray.size) return null
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

    fun formatForPreview(rawText: String): String {
        val model = parseStrictJson(rawText) ?: return rawText
        return buildString {
            append("# ").append(model.title).append("\n\n")
            if (model.objective.isNotBlank()) append("**هدف الدرس:** ").append(model.objective).append("\n\n")
            if (model.introduction.isNotBlank()) append(model.introduction).append("\n\n")
            model.sections.forEachIndexed { index, section ->
                append("## ").append(index + 1).append(". ").append(section.title).append("\n\n")
                if (section.conceptTag.isNotBlank()) append("**المفهوم:** ").append(section.conceptTag).append("\n\n")
                append(section.explanation).append("\n\n")
                if (section.visualDescription.isNotBlank()) append("**وسيلة التوضيح (${section.visualType}):** ").append(section.visualDescription).append("\n\n")
                if (section.workedExample.isNotBlank()) append("**مثال تطبيقي:**\n").append(section.workedExample).append("\n\n")
                if (section.checkpointQuestion.isNotBlank()) append("**نقطة تحقق:** ").append(section.checkpointQuestion).append("\n\n")
            }
            if (model.keyPoints.isNotEmpty()) {
                append("## أهم نقاط المذاكرة\n\n")
                model.keyPoints.forEach { append("- ").append(it).append("\n") }
                append("\n")
            }
            if (model.summary.isNotBlank()) append("## ملخص الدرس\n\n").append(model.summary).append("\n\n")
            if (model.bookQuestionIds.isNotEmpty()) append("**أسئلة الكتاب المرتبطة:** ").append(model.bookQuestionIds.size).append(" سؤالاً من بنك الأسئلة.")
        }.trim()
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> =
        (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.filter { it.isNotBlank() }.orEmpty()
}