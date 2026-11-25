package com.example.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull

/**
 * Adapts provider output into the app's stable internal contract.
 *
 * Mapping format:
 * {
 *   "rootArray": "items",
 *   "fields": {
 *     "prompt": "stem",
 *     "options": "choices",
 *     "correctAnswer": "answer"
 *   }
 * }
 *
 * The provider-facing schema and the app-facing model remain separate. A template can
 * change the model's field names without forcing changes in Room entities or parsers.
 */
object TemplateResponseAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    fun adapt(raw: String, mappingJson: String): String {
        if (mappingJson.isBlank() || raw.isBlank()) return raw
        val mapping = runCatching { json.parseToJsonElement(mappingJson) as? JsonObject }.getOrNull()
            ?: return raw
        val aliases = mapping["fields"] as? JsonObject
        val rootArrayName = (mapping["rootArray"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull?.takeIf { it.isNotBlank() }
        val parsed = runCatching { json.parseToJsonElement(stripFence(raw)) }.getOrNull() ?: return raw

        val normalized = when (parsed) {
            is JsonObject -> {
                val root = parsed.toMutableMap()
                if (rootArrayName != null && rootArrayName != "questions") {
                    val array = root[rootArrayName]
                    if (array != null && root["questions"] == null) {
                        root["questions"] = array
                    }
                }
                JsonObject(root.mapValues { (_, value) -> remapRecursively(value, aliases) })
            }
            else -> remapRecursively(parsed, aliases)
        }
        return normalized.toString()
    }

    private fun remapRecursively(value: JsonElement, aliases: JsonObject?): JsonElement {
        if (aliases == null) return value
        return when (value) {
            is JsonObject -> {
                val mutable = value.toMutableMap()
                aliases.forEach { (canonical, sourceElement) ->
                    val source = (sourceElement as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@forEach
                    if (mutable[canonical] == null && mutable[source] != null) {
                        mutable[canonical] = mutable[source]!!
                    }
                }
                JsonObject(mutable.mapValues { (_, nested) -> remapRecursively(nested, aliases) })
            }
            is kotlinx.serialization.json.JsonArray ->
                kotlinx.serialization.json.JsonArray(value.map { remapRecursively(it, aliases) })
            else -> value
        }
    }

    private fun stripFence(value: String): String = value.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}
