package com.example.data.repository

import com.example.model.InteractiveActivityModel
import com.example.model.InteractiveMatchPair
import com.example.model.InteractiveStepItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object InteractiveActivityParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Strict parser for AI-generated or publishable activities.
     * Unlike [parse], this never interprets ordinary numbered prose as an activity.
     */
    fun parseStrictJson(rawText: String, fallbackTitle: String = "نشاط تفاعلي للدرس"): InteractiveActivityModel? {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return null
        return tryParseJson(cleaned, fallbackTitle)
    }

    fun parse(rawText: String, fallbackTitle: String = "نشاط تفاعلي للدرس"): InteractiveActivityModel? {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return null

        val fromJson = tryParseJson(cleaned, fallbackTitle)
        if (fromJson != null) return fromJson

        // Fallback structured text parsing: extract numbered sections as interactive steps
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
        val stepHeaderRegex = Regex("^(?:##+|\\*\\*|\\d+[\\).:\\-])\\s*(.+)$")
        val steps = mutableListOf<InteractiveStepItem>()
        var currentTitle = ""
        val currentBody = StringBuilder()

        for (line in lines) {
            val match = stepHeaderRegex.find(line)
            if (match != null && line.length < 90) {
                if (currentTitle.isNotBlank() || currentBody.isNotBlank()) {
                    steps.add(
                        InteractiveStepItem(
                            title = currentTitle.ifBlank { "الخطوة ${steps.size + 1}" },
                            body = currentBody.toString().trim()
                        )
                    )
                    currentBody.clear()
                }
                currentTitle = match.groupValues[1].replace("**", "").trim()
            } else {
                if (currentBody.isNotEmpty()) currentBody.append('\n')
                currentBody.append(line.replace("**", ""))
            }
        }
        if (currentTitle.isNotBlank() || currentBody.isNotBlank()) {
            steps.add(
                InteractiveStepItem(
                    title = currentTitle.ifBlank { "الخطوة ${steps.size + 1}" },
                    body = currentBody.toString().trim()
                )
            )
        }

        if (steps.isEmpty()) return null
        return InteractiveActivityModel(
            title = fallbackTitle,
            steps = steps
        )
    }

    private fun tryParseJson(rawText: String, fallbackTitle: String): InteractiveActivityModel? {
        val stripped = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val objStart = stripped.indexOf('{')
        val objEnd = stripped.lastIndexOf('}')
        if (objStart < 0 || objEnd <= objStart) return null
        val slice = stripped.substring(objStart, objEnd + 1)

        val root = runCatching { json.parseToJsonElement(slice) }.getOrNull() as? JsonObject ?: return null
        val title = str(root, "title").ifBlank { fallbackTitle }
        val objective = str(root, "objective", "goal")

        val rootConcept = str(root, "conceptTag", "concept", "topic")
        val stepsArr = (root["steps"] as? JsonArray) ?: (root["sections"] as? JsonArray)
        val steps = stepsArr?.mapIndexedNotNull { idx, elem ->
            val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val stepTitle = str(obj, "title", "heading").ifBlank { "الخطوة ${idx + 1}" }
            val stepBody = str(obj, "body", "content", "explanation", "text")
            val cpObj = obj["checkpoint"] as? JsonObject
            val cpQuestion = cpObj?.let { str(it, "question", "prompt") }
                ?: str(obj, "checkpointQuestion", "question")
            val cpOptions = ((cpObj?.get("options") ?: obj["options"]) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val cpAnswer = cpObj?.let { str(it, "answer", "correctAnswer") }
                ?: str(obj, "checkpointAnswer", "answer")
            val stepConcept = cpObj?.let { str(it, "conceptTag", "concept", "topic") }
                ?.ifBlank { str(obj, "conceptTag", "concept", "topic") }
                ?.ifBlank { rootConcept }
                .orEmpty()

            if (stepTitle.isBlank() && stepBody.isBlank()) null
            else InteractiveStepItem(
                title = stepTitle,
                body = stepBody,
                checkpointQuestion = cpQuestion,
                checkpointOptions = cpOptions,
                checkpointAnswer = cpAnswer,
                conceptTag = stepConcept
            )
        }.orEmpty()

        val ordering = (root["ordering"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty()

        val matching = (root["matching"] as? JsonArray)?.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val left = str(obj, "left", "term", "prompt")
            val right = str(obj, "right", "definition", "match", "answer")
            if (left.isBlank() || right.isBlank()) null else InteractiveMatchPair(left, right)
        }.orEmpty()

        if (steps.isEmpty() && ordering.isEmpty() && matching.isEmpty()) return null
        return InteractiveActivityModel(
            title = title,
            objective = objective,
            steps = steps,
            orderingItems = ordering,
            matchingPairs = matching
        )
    }

    private fun str(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val v = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }
}
