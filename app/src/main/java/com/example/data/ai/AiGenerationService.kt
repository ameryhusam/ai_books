package com.example.data.ai

import com.example.model.GeneratedResult
import com.example.model.LessonRef
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.TimeUnit

class AiGenerationService(
    private val client: OkHttpClient = defaultClient(),
    private val geminiEndpointBase: String = "https://generativelanguage.googleapis.com/v1beta/models"
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    fun buildPrompt(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String
    ): String {
        val taskInstruction = when (task) {
            TaskType.QUESTIONS -> "بنك أسئلة مع الإجابات والتفسير"
            TaskType.INTERACTIVE -> "درس تفاعلي يتضمن شرحاً قصيراً وأمثلة ونقاط تحقق"
            TaskType.EXPLANATION -> "شرح مبسط وملخص وأمثلة"
        }
        return buildString {
            append("أنت مساعد إعداد محتوى تعليمي. أنشئ ")
            append(taskInstruction)
            append(" للصف ")
            append(lesson.grade)
            append("، الوحدة ")
            append(lesson.unitTitle)
            append("، الدرس ")
            append(lesson.title)
            append(".\n")
            append("التزم بالمصدر ولا تخترع معلومات أو صفحات. اجعل المحتوى منظمًا وقابلاً للتحرير.\n")
            append("المصدر:\n")
            append(source)
            append("\nتوجيهات إضافية:\n")
            append(extra)
        }
    }

    suspend fun generate(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        providers: List<ProviderConfig>
    ): GeneratedResult = withContext(Dispatchers.IO) {
        val active = providers.filter {
            it.enabled && it.apiKey.isNotBlank() && it.model.isNotBlank()
        }
        if (active.isEmpty()) {
            throw IllegalStateException("أضف مفتاح API فعّال في الإعدادات")
        }

        val prompt = buildPrompt(lesson, task, source, extra)
        val errors = mutableListOf<String>()

        for (profile in active) {
            for (retry in 0 until 2) {
                try {
                    val outputText = callProvider(profile, prompt)
                    return@withContext GeneratedResult(
                        text = outputText,
                        providerLabel = profile.label,
                        model = profile.model,
                        createdAt = Instant.now().toString()
                    )
                } catch (e: Exception) {
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                    errors.add("${profile.label}: $reason")
                    if (retry == 0) {
                        delay(650L)
                    }
                }
            }
        }

        throw IllegalStateException("تعذر التوليد من المزودات: ${errors.joinToString(" | ")}")
    }

    private fun callProvider(profile: ProviderConfig, prompt: String): String {
        return when (profile.provider) {
            ProviderType.GEMINI -> callGemini(profile, prompt)
            ProviderType.OPENAI_COMPATIBLE -> callOpenAiCompatible(profile, prompt)
        }
    }

    private fun callGemini(profile: ProviderConfig, prompt: String): String {
        val encodedModel = URLEncoder.encode(profile.model.trim(), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val url = "${geminiEndpointBase.trimEnd('/')}/$encodedModel:generateContent"

        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.3)
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", profile.apiKey.trim())
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val jsonObj = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val errMsg = jsonObj?.get("error")
                    ?.let { it as? JsonObject }
                    ?.get("message")
                    ?.let { it as? JsonPrimitive }
                    ?.contentOrNull
                    ?: "Gemini HTTP ${response.code}"
                throw IllegalStateException(errMsg)
            }

            val candidates = jsonObj?.get("candidates") as? JsonArray
            val firstCandidate = candidates?.firstOrNull() as? JsonObject
            val contentObj = firstCandidate?.get("content") as? JsonObject
            val parts = contentObj?.get("parts") as? JsonArray
            val text = parts?.joinToString("") { part ->
                ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }.orEmpty()

            if (text.isBlank()) {
                throw IllegalStateException("استجابة فارغة")
            }
            return text
        }
    }

    private fun callOpenAiCompatible(profile: ProviderConfig, prompt: String): String {
        val cleanBase = profile.baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) {
            throw IllegalArgumentException("يرجى إدخال Base URL صالح")
        }
        val url = "$cleanBase/chat/completions"

        val payload = buildJsonObject {
            put("model", profile.model.trim())
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "Educational content author. Respond in Arabic.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${profile.apiKey.trim()}")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val jsonObj = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val errMsg = jsonObj?.get("error")
                    ?.let { it as? JsonObject }
                    ?.get("message")
                    ?.let { it as? JsonPrimitive }
                    ?.contentOrNull
                    ?: "API HTTP ${response.code}"
                throw IllegalStateException(errMsg)
            }

            val choices = jsonObj?.get("choices") as? JsonArray
            val firstChoice = choices?.firstOrNull() as? JsonObject
            val messageObj = firstChoice?.get("message") as? JsonObject
            val text = (messageObj?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()

            if (text.isBlank()) {
                throw IllegalStateException("استجابة فارغة")
            }
            return text
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
