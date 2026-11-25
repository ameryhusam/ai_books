package com.example.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Structural validator for the JSON Schema subset used by persisted AI templates. */
object TemplateOutputValidator {
    private val json = Json { ignoreUnknownKeys = true }

    enum class FailureCode { INVALID_TEMPLATE_SCHEMA, INVALID_AI_OUTPUT }

    class ValidationException(val code: FailureCode, val issues: List<String>) :
        IllegalStateException(issues.joinToString("\n").ifBlank { code.name })

    fun validateSchema(schemaJson: String): List<String> {
        if (schemaJson.isBlank()) return listOf("$: JSON response format requires a schema")
        val root = runCatching { json.parseToJsonElement(schemaJson) }.getOrElse {
            return listOf("$: invalid schema JSON: " + (it.message ?: "parse error"))
        }
        if (root !is JsonObject) return listOf("$: schema root must be an object")
        val issues = mutableListOf<String>()
        validateSchemaNode(root, "$", issues)
        return issues
    }

    fun validateOutput(raw: String, schemaJson: String): List<String> {
        val schemaIssues = validateSchema(schemaJson)
        if (schemaIssues.isNotEmpty()) return schemaIssues
        val value = runCatching { json.parseToJsonElement(stripFence(raw)) }.getOrElse {
            return listOf("$: AI output is not valid JSON: " + (it.message ?: "parse error"))
        }
        val schema = json.parseToJsonElement(schemaJson) as JsonObject
        val issues = mutableListOf<String>()
        validateValue(value, schema, "$", issues)
        return issues
    }

    private fun validateSchemaNode(schema: JsonObject, path: String, issues: MutableList<String>) {
        val allowed = setOf("object", "array", "string", "number", "integer", "boolean", "null")
        val type = (schema["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()
        if (type == null || type !in allowed) {
            issues += "$path.type: expected one of " + allowed.joinToString()
            return
        }
        val properties = schema["properties"]
        if (properties != null && properties !is JsonObject) issues += "$path.properties: must be an object"
        if (properties is JsonObject) properties.forEach { (name, child) ->
            if (child !is JsonObject) issues += "$path.properties.$name: schema must be an object"
            else validateSchemaNode(child, "$path.properties.$name", issues)
        }
        val required = schema["required"]
        if (required != null) {
            if (required !is JsonArray || required.any { (it as? JsonPrimitive)?.isString != true }) {
                issues += "$path.required: must be an array of strings"
            } else if (properties is JsonObject) required.forEach {
                val key = (it as JsonPrimitive).content
                if (key !in properties) issues += "$path.required: '$key' is not declared in properties"
            }
        }
        if (type == "array") {
            val items = schema["items"]
            if (items !is JsonObject) issues += "$path.items: array schema requires an object schema"
            else validateSchemaNode(items, "$path.items", issues)
        }
        val enum = schema["enum"]
        if (enum != null && (enum !is JsonArray || enum.isEmpty())) issues += "$path.enum: must be a non-empty array"
        val additional = schema["additionalProperties"]
        if (additional != null && (additional !is JsonPrimitive || additional.booleanOrNull == null)) {
            issues += "$path.additionalProperties: must be a boolean"
        }
    }

    private fun validateValue(value: JsonElement, schema: JsonObject, path: String, issues: MutableList<String>) {
        val type = (schema["type"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: return
        val matches = when (type) {
            "object" -> value is JsonObject
            "array" -> value is JsonArray
            "string" -> value is JsonPrimitive && value.isString
            "integer" -> value is JsonPrimitive && !value.isString && value.content.toLongOrNull() != null
            "number" -> value is JsonPrimitive && !value.isString && value.content.toDoubleOrNull() != null
            "boolean" -> (value as? JsonPrimitive)?.booleanOrNull != null
            "null" -> value == JsonNull
            else -> false
        }
        if (!matches) { issues += "$path: expected $type"; return }
        val enum = schema["enum"] as? JsonArray
        if (enum != null && value !in enum) issues += "$path: value is not in the allowed enum"
        if (value is JsonObject) {
            val properties = schema["properties"] as? JsonObject
            val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            required.filter { it !in value }.forEach { issues += "$path.$it: required field is missing" }
            if (schema["additionalProperties"] == JsonPrimitive(false) && properties != null) {
                (value.keys - properties.keys).forEach { issues += "$path.$it: additional property is not allowed" }
            }
            if (properties != null) properties.forEach { (name, childSchema) ->
                val childValue = value[name] ?: return@forEach
                if (childSchema is JsonObject) validateValue(childValue, childSchema, "$path.$name", issues)
            }
        } else if (value is JsonArray) {
            val itemSchema = schema["items"] as? JsonObject
            if (itemSchema != null) value.forEachIndexed { index, item -> validateValue(item, itemSchema, "$path[$index]", issues) }
        }
    }

    private fun stripFence(raw: String): String = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}