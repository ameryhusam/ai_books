package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.data.workspace.UriIO
import com.example.model.GeneratedResult
import com.example.model.LessonRef
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.TaskType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PdfDocumentPayload(val base64: String, val sha256: String, val byteCount: Int)

class AiGenerationService(
    private val context: Context? = null,
    private val client: OkHttpClient = defaultClient(),
    private val geminiEndpointBase: String = "https://generativelanguage.googleapis.com/v1beta/models"
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    open class ProviderCallException(
        message: String,
        val isTransient: Boolean,
        val httpCode: Int? = null,
        val retryAfterEpochMs: Long = 0L
    ) : IllegalStateException(message)

    fun buildPrompt(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        questionCount: Int = 5,
        attachedPdfPagesCount: Int = 0,
        questionStyleHint: String = "",
        difficultyHint: String = "",
        responseSchemaJson: String = "",
        responseFormat: String = "text"
    ): String {
        val blueprint = Indexer2PromptRegistry.blueprintFor(task)
        val promptTemplate = context?.let { appContext ->
            appContext.assets.open(blueprint.sourcePath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.orEmpty()
        require(context == null || promptTemplate.isNotBlank()) {
            "ملف قالب المهمة غير موجود أو فارغ: ${blueprint.sourcePath}"
        }
        return Indexer2PromptRegistry.assemblePrompt(
        lesson = lesson,
        task = task,
        source = source,
        extra = extra,
        questionCount = questionCount,
        attachedPdfPagesCount = attachedPdfPagesCount,
        questionStyleHint = questionStyleHint,
        difficultyHint = difficultyHint,
        promptTemplate = promptTemplate,
        responseSchemaJson = responseSchemaJson,
        responseFormat = responseFormat
        )
    }

    suspend fun extractLessonPdfPageImagesBase64(
        pdfPath: String?,
        maxPages: Int = 4
    ): List<String> = withContext(Dispatchers.IO) {
        require(!pdfPath.isNullOrBlank()) { "مسار PDF غير محدد" }
        require(maxPages > 0) { "عدد صفحات PDF المطلوب يجب أن يكون أكبر من صفر" }

        val descriptor = if (context != null) {
            require(UriIO.exists(context, pdfPath)) { "ملف PDF الخاص بالدرس غير موجود: $pdfPath" }
            UriIO.openReadPfd(context, pdfPath)
                ?: throw IllegalStateException("تعذر فتح ملف PDF للقراءة: $pdfPath")
        } else {
            val file = File(pdfPath)
            require(file.isFile) { "ملف PDF الخاص بالدرس غير موجود: $pdfPath" }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        val encodedPages = mutableListOf<String>()
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(renderer.pageCount > 0) { "ملف PDF لا يحتوي صفحات" }
                val selectedCount = renderer.pageCount.coerceAtMost(maxPages)
                val selectedIndexes = if (selectedCount == 1) {
                    listOf(0)
                } else {
                    (0 until selectedCount).map { index ->
                        (index.toLong() * (renderer.pageCount - 1) / (selectedCount - 1)).toInt()
                    }.distinct()
                }
                selectedIndexes.forEach { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val scale = (1100f / page.width.coerceAtLeast(1)).coerceAtMost(1.6f)
                        val width = (page.width * scale).toInt().coerceIn(400, 1400)
                        val height = (page.height * scale).toInt().coerceIn(500, 1800)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val bytes = ByteArrayOutputStream().use { output ->
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                                    "تعذر ضغط صفحة PDF رقم ${pageIndex + 1}"
                                }
                                output.toByteArray()
                            }
                            encodedPages.add(Base64.encodeToString(bytes, Base64.NO_WRAP))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        check(encodedPages.isNotEmpty()) { "تعذر استخراج صور صفحات PDF" }
        encodedPages
    }

    suspend fun extractLessonPdfBase64(
        pdfPath: String?,
        maxBytes: Int = 18 * 1024 * 1024
    ): PdfDocumentPayload = withContext(Dispatchers.IO) {
        require(!pdfPath.isNullOrBlank()) { "مسار PDF غير محدد" }
        val knownLength = if (context != null) UriIO.fileLength(context, pdfPath) else File(pdfPath).length()
        require(knownLength <= maxBytes) {
            "حجم PDF (${knownLength / (1024 * 1024)} MB) يتجاوز الحد المحلي ${maxBytes / (1024 * 1024)} MB؛ اختر صفحات أقل أو ملفاً أصغر."
        }
        val bytes = if (context != null) {
            UriIO.readBytes(context, pdfPath)
        } else {
            File(pdfPath).takeIf { it.isFile }?.readBytes()
        } ?: throw IllegalStateException("تعذر قراءة ملف PDF")
        require(bytes.isNotEmpty()) { "ملف PDF فارغ" }
        require(bytes.size <= maxBytes) {
            "حجم PDF (${bytes.size / (1024 * 1024)} MB) يتجاوز الحد المحلي ${maxBytes / (1024 * 1024)} MB؛ اختر صفحات أقل أو ملفاً أصغر."
        }
        require(bytes.size >= 5 && bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") {
            "الملف المحدد ليس PDF صالحاً"
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        PdfDocumentPayload(
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            sha256 = hash,
            byteCount = bytes.size
        )
    }

    suspend fun generate(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        providers: List<ProviderConfig>,
        questionCount: Int = 5,
        pdfPageImagesBase64: List<String> = emptyList(),
        questionStyleHint: String = "",
        difficultyHint: String = "",
        pdfDocumentBase64: String? = null,
        responseSchemaJson: String = "",
        responseFormat: String = "text",
        templateId: String = "",
        templateVersion: Int = 1,
        responseSchemaHash: String = "",
        requestFingerprint: String = "",
        sourcePdfHash: String = "",
        onReserveProvider: (suspend (ProviderConfig) -> Boolean)? = null,
        onProviderStateChanged: (suspend (providerId: String, status: String, retryAfterEpochMs: Long) -> Unit)? = null,
        onAttemptFinished: (suspend (providerLabel: String, model: String, success: Boolean, error: String?) -> Unit)? = null
    ): GeneratedResult = withContext(Dispatchers.IO) {
        val active = providers
            .filter { it.enabled && it.apiKey.isNotBlank() && it.model.isNotBlank() }
            .sortedWith(compareBy<ProviderConfig> { it.lastUsedAtEpochMs }.thenBy { it.priority }.thenBy { it.createdAt })

        if (active.isEmpty()) {
            throw IllegalStateException("أضف مفتاح API فعّال في الإعدادات")
        }

        val prompt = buildPrompt(
            lesson = lesson,
            task = task,
            source = source,
            extra = extra,
            questionCount = questionCount,
            attachedPdfPagesCount = if (!pdfDocumentBase64.isNullOrBlank()) 1 else pdfPageImagesBase64.size,
            questionStyleHint = questionStyleHint,
            difficultyHint = difficultyHint,
            responseSchemaJson = responseSchemaJson,
            responseFormat = responseFormat
        )
        val errors = mutableListOf<String>()

        for (profile in active) {
            val modelCandidates = if (profile.strictModel) {
                listOf(profile.model.trim())
            } else {
                (listOf(profile.model.trim()) + profile.fallbackModels.map { it.trim() })
                    .filter { it.isNotBlank() }
                    .distinct()
            }

            modelLoop@ for (modelName in modelCandidates) {
                val candidateProfile = profile.copy(model = modelName)
                val expectJson = responseFormat.equals("json", ignoreCase = true) || responseSchemaJson.isNotBlank()
                var retry = 0
                while (retry < 2) {
                    val callStart = System.currentTimeMillis()
                    try {
                        val reserved = onReserveProvider?.invoke(candidateProfile) ?: true
                        if (!reserved) {
                            errors.add("${profile.label}/$modelName: المفتاح معطّل أو مقيّد أو استنفد حصته اليومية")
                            break@modelLoop
                        }
                        val (outputText, tokens) = callProvider(
                            profile = candidateProfile,
                            prompt = prompt,
                            pdfPageImagesBase64 = pdfPageImagesBase64,
                            pdfDocumentBase64 = if (profile.provider == ProviderType.GEMINI) pdfDocumentBase64 else null,
                            responseSchemaJson = responseSchemaJson,
                            expectJson = expectJson
                        )
                        val durationMs = (System.currentTimeMillis() - callStart).coerceAtLeast(1L)
                        onAttemptFinished?.invoke(profile.label, modelName, true, null)
                        return@withContext GeneratedResult(
                            text = outputText,
                            providerLabel = profile.label,
                            model = modelName,
                            createdAt = Instant.now().toString(),
                            durationMs = durationMs,
                            tokensUsed = tokens,
                            templateId = templateId,
                            templateVersion = templateVersion,
                            responseSchemaHash = responseSchemaHash,
                            requestFingerprint = sha256("$requestFingerprint|${profile.provider.id}|$modelName|$prompt"),
                            contentHash = sha256(outputText),
                            sourcePdfHash = sourcePdfHash
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ProviderCallException) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        val failureStatus = when {
                            e.httpCode == 401 || e.httpCode == 403 || Regex("invalid api.?key|api key not valid|unauthorized|authentication failed", RegexOption.IGNORE_CASE).containsMatchIn(reason) -> "INVALID_KEY"
                            e.httpCode == 429 && Regex("quota|daily|resource.?exhausted|limit exceeded", RegexOption.IGNORE_CASE).containsMatchIn(reason) -> "QUOTA_EXHAUSTED"
                            e.httpCode == 429 -> "RATE_LIMITED"
                            else -> "ERROR"
                        }
                        onProviderStateChanged?.invoke(profile.id, failureStatus, e.retryAfterEpochMs)
                        if (!e.isTransient) {
                            break
                        }
                        if (retry == 0) {
                            delay(650L)
                        }
                    } catch (e: IOException) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "خطأ شبكة"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        onProviderStateChanged?.invoke(profile.id, "ERROR", 0L)
                        if (retry == 0) {
                            delay(650L)
                        }
                    } catch (e: Exception) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        onProviderStateChanged?.invoke(profile.id, "ERROR", 0L)
                        break
                    }
                    retry++
                }
            }
        }

        throw IllegalStateException("تعذر التوليد من المزودات: ${errors.joinToString(" | ")}")
    }

    suspend fun testProviderConnection(profile: ProviderConfig): String = withContext(Dispatchers.IO) {
        if (profile.apiKey.isBlank() || profile.model.isBlank()) {
            throw IllegalArgumentException("أدخل النموذج ومفتاح API أولاً")
        }
        val started = System.currentTimeMillis()
        val (reply, _) = callProvider(
            profile = profile,
            prompt = "أجب بكلمة واحدة فقط: جاهز",
            pdfPageImagesBase64 = emptyList(),
            pdfDocumentBase64 = null,
            responseSchemaJson = "",
            expectJson = false
        )
        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        "اتصال ناجح بـ ${profile.model} (${elapsed}ms): ${reply.trim().take(40)}"
    }

    private suspend fun callProvider(
        profile: ProviderConfig,
        prompt: String,
        pdfPageImagesBase64: List<String>,
        pdfDocumentBase64: String?,
        responseSchemaJson: String,
        expectJson: Boolean
    ): Pair<String, Int> {
        return when (profile.provider) {
            ProviderType.GEMINI -> callGemini(profile, prompt, pdfPageImagesBase64, pdfDocumentBase64, responseSchemaJson, expectJson)
            ProviderType.OPENAI_COMPATIBLE -> callOpenAiCompatible(profile, prompt, pdfPageImagesBase64, expectJson)
        }
    }

    private suspend fun callGemini(
        profile: ProviderConfig,
        prompt: String,
        pdfPageImagesBase64: List<String>,
        pdfDocumentBase64: String?,
        responseSchemaJson: String,
        expectJson: Boolean
    ): Pair<String, Int> {
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
                        if (!pdfDocumentBase64.isNullOrBlank()) {
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", "application/pdf")
                                    put("data", pdfDocumentBase64)
                                })
                            })
                        } else pdfPageImagesBase64.forEach { base64Jpeg ->
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Jpeg)
                                })
                            })
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.3)
                if (expectJson) {
                    put("responseMimeType", "application/json")
                    val providerSchema = runCatching {
                        json.parseToJsonElement(responseSchemaJson).jsonObject
                    }.getOrNull()?.takeIf { schema ->
                        schema["type"] != null && schema["properties"] != null
                    }?.let { normalizeGeminiSchema(it) as? JsonObject }
                    if (providerSchema != null) put("responseSchema", providerSchema)
                }
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", profile.apiKey.trim())
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        executeCancellable(request).use { response ->
            val rawBody = response.body?.string().orEmpty()
            val jsonObj = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val errMsg = jsonObj?.get("error")
                    ?.let { it as? JsonObject }
                    ?.get("message")
                    ?.let { it as? JsonPrimitive }
                    ?.contentOrNull
                    ?: "Gemini HTTP ${response.code}"
                throw ProviderCallException(errMsg, isTransientHttp(response.code), response.code, retryAfterEpochMs(response))
            }

            val candidates = jsonObj?.get("candidates") as? JsonArray
            val firstCandidate = candidates?.firstOrNull() as? JsonObject
            val contentObj = firstCandidate?.get("content") as? JsonObject
            val parts = contentObj?.get("parts") as? JsonArray
            val text = parts?.joinToString("") { part ->
                ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }.orEmpty()
            val tokens = (jsonObj?.get("usageMetadata") as? JsonObject)
                ?.get("totalTokenCount")
                ?.let { it as? JsonPrimitive }
                ?.intOrNull
                ?: 0

            if (text.isBlank()) {
                throw ProviderCallException("استجابة فارغة", isTransient = true)
            }
            return text to tokens
        }
    }

    private suspend fun callOpenAiCompatible(
        profile: ProviderConfig,
        prompt: String,
        pdfPageImagesBase64: List<String>,
        expectJson: Boolean
    ): Pair<String, Int> {
        val cleanBase = profile.baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) {
            throw ProviderCallException("يرجى إدخال Base URL صالح", isTransient = false)
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
                    if (pdfPageImagesBase64.isEmpty()) {
                        put("content", prompt)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            })
                            pdfPageImagesBase64.forEach { base64Jpeg ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put("url", "data:image/jpeg;base64,$base64Jpeg")
                                    })
                                })
                            }
                        })
                    }
                })
            })
            put("temperature", 0.3)
            if (expectJson) {
                put("response_format", buildJsonObject {
                    put("type", "json_object")
                })
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${profile.apiKey.trim()}")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        executeCancellable(request).use { response ->
            val rawBody = response.body?.string().orEmpty()
            val jsonObj = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val errMsg = jsonObj?.get("error")
                    ?.let { it as? JsonObject }
                    ?.get("message")
                    ?.let { it as? JsonPrimitive }
                    ?.contentOrNull
                    ?: "API HTTP ${response.code}"
                throw ProviderCallException(errMsg, isTransientHttp(response.code), response.code, retryAfterEpochMs(response))
            }

            val choices = jsonObj?.get("choices") as? JsonArray
            val firstChoice = choices?.firstOrNull() as? JsonObject
            val messageObj = firstChoice?.get("message") as? JsonObject
            val text = (messageObj?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val tokens = (jsonObj?.get("usage") as? JsonObject)
                ?.get("total_tokens")
                ?.let { it as? JsonPrimitive }
                ?.intOrNull
                ?: 0

            if (text.isBlank()) {
                throw ProviderCallException("استجابة فارغة", isTransient = true)
            }
            return text to tokens
        }
    }

    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    private fun normalizeGeminiSchema(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key == "type" && value is JsonPrimitive) JsonPrimitive(value.content.uppercase())
            else normalizeGeminiSchema(value)
        })
        is JsonArray -> JsonArray(element.map(::normalizeGeminiSchema))
        else -> element
    }

    private fun retryAfterEpochMs(response: Response): Long {
        val seconds = response.header("Retry-After")?.toLongOrNull()
        return System.currentTimeMillis() + (seconds?.coerceAtLeast(1L)?.times(1000L) ?: 60_000L)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun isTransientHttp(code: Int): Boolean {
        return code == 408 || code == 429 || code >= 500
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
