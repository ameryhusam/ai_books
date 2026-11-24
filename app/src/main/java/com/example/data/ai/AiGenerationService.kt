package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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

    open class ProviderCallException(
        message: String,
        val isTransient: Boolean
    ) : IllegalStateException(message)

    fun buildPrompt(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        questionCount: Int = 5,
        attachedPdfPagesCount: Int = 0
    ): String {
        val safeCount = questionCount.coerceIn(1, 30)
        val taskInstruction = when (task) {
            TaskType.QUESTIONS ->
                "بنك أسئلة مكوّن من $safeCount أسئلة متنوعة مع الخيارات والإجابة الصحيحة والتفسير وربط كل سؤال بالصفحة المرجعية"
            TaskType.INTERACTIVE ->
                "درس تفاعلي منظم يتضمن شرحاً قصيراً وأمثلة واضحة مع $safeCount نقاط تحقق تفاعلية"
            TaskType.SUMMARY ->
                "ملخص مركز ومنظم لأهم أفكار ومفاهيم الدرس في نقاط واضحة"
            TaskType.OBJECTIVES ->
                "أهداف تعلم سلوكية قابلة للقياس ومفردات مفتاحية مع تعريفاتها"
            TaskType.EXPLANATION ->
                "شرح مبسط ومنظم مع أمثلة محلولة وملخص ختامي"
        }
        return buildString {
            append("أنت مساعد إعداد محتوى تعليمي. أنشئ ")
            append(taskInstruction)
            append(" للصف ")
            append(lesson.grade)
            append("، الكتاب ")
            append(lesson.bookTitle)
            append("، الوحدة ")
            append(lesson.unitTitle)
            append("، الدرس ")
            append(lesson.title)
            if (!lesson.pageRange.isNullOrBlank()) {
                append(" (الصفحات المرجعية: ")
                append(lesson.pageRange)
                append(")")
            }
            append(".\n")
            append("التزم بالمصدر ولا تخترع معلومات أو صفحات. اجعل المحتوى منظمًا وقابلاً للتحرير.\n")
            if (task == TaskType.QUESTIONS) {
                append("عدد الأسئلة المطلوب بدقة: $safeCount.\n")
            }
            if (attachedPdfPagesCount > 0) {
                append("ملاحظة: مرفق مع هذا الطلب $attachedPdfPagesCount صفحة مصورة من ملف PDF الخاص بالدرس لاستخراج المحتوى والاعتماد عليها.\n")
            }
            if (source.isNotBlank()) {
                append("المصدر النصي:\n")
                append(source)
            } else {
                append("المصدر النصي: اعتمد على صفحات PDF المرفقة وبيانات الدرس.\n")
            }
            if (extra.isNotBlank()) {
                append("\nتوجيهات إضافية:\n")
                append(extra)
            }
        }
    }

    suspend fun extractLessonPdfPageImagesBase64(
        pdfPath: String?,
        maxPages: Int = 4
    ): List<String> = withContext(Dispatchers.IO) {
        if (pdfPath.isNullOrBlank()) return@withContext emptyList()
        val file = File(pdfPath)
        if (!file.exists()) return@withContext emptyList()

        val encodedPages = mutableListOf<String>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtMost(maxPages)
                    for (idx in 0 until count) {
                        renderer.openPage(idx).use { page ->
                            val scale = (1100f / page.width.coerceAtLeast(1)).coerceAtMost(1.6f)
                            val w = (page.width * scale).toInt().coerceIn(400, 1400)
                            val h = (page.height * scale).toInt().coerceIn(500, 1800)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                            bitmap.recycle()
                            encodedPages.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                        }
                    }
                }
            }
        }
        encodedPages
    }

    suspend fun generate(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        providers: List<ProviderConfig>,
        questionCount: Int = 5,
        pdfPageImagesBase64: List<String> = emptyList(),
        onAttemptFinished: (suspend (providerLabel: String, model: String, success: Boolean, error: String?) -> Unit)? = null
    ): GeneratedResult = withContext(Dispatchers.IO) {
        val active = providers
            .filter { it.enabled && it.apiKey.isNotBlank() && it.model.isNotBlank() }
            .sortedWith(compareBy<ProviderConfig> { it.priority }.thenBy { it.createdAt })

        if (active.isEmpty()) {
            throw IllegalStateException("أضف مفتاح API فعّال في الإعدادات")
        }

        val prompt = buildPrompt(
            lesson = lesson,
            task = task,
            source = source,
            extra = extra,
            questionCount = questionCount,
            attachedPdfPagesCount = pdfPageImagesBase64.size
        )
        val errors = mutableListOf<String>()

        for (profile in active) {
            val modelCandidates = (listOf(profile.model.trim()) + profile.fallbackModels.map { it.trim() })
                .filter { it.isNotBlank() }
                .distinct()

            for (modelName in modelCandidates) {
                val candidateProfile = profile.copy(model = modelName)
                var retry = 0
                while (retry < 2) {
                    try {
                        val outputText = callProvider(candidateProfile, prompt, pdfPageImagesBase64)
                        onAttemptFinished?.invoke(profile.label, modelName, true, null)
                        return@withContext GeneratedResult(
                            text = outputText,
                            providerLabel = profile.label,
                            model = modelName,
                            createdAt = Instant.now().toString()
                        )
                    } catch (e: ProviderCallException) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        if (!e.isTransient) {
                            break // Do not retry permanent 400/401/403/404 errors
                        }
                        if (retry == 0) {
                            delay(650L)
                        }
                    } catch (e: IOException) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "خطأ شبكة"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        if (retry == 0) {
                            delay(650L)
                        }
                    } catch (e: Exception) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        break
                    }
                    retry++
                }
            }
        }

        throw IllegalStateException("تعذر التوليد من المزودات: ${errors.joinToString(" | ")}")
    }

    private fun callProvider(
        profile: ProviderConfig,
        prompt: String,
        pdfPageImagesBase64: List<String>
    ): String {
        return when (profile.provider) {
            ProviderType.GEMINI -> callGemini(profile, prompt, pdfPageImagesBase64)
            ProviderType.OPENAI_COMPATIBLE -> callOpenAiCompatible(profile, prompt, pdfPageImagesBase64)
        }
    }

    private fun callGemini(
        profile: ProviderConfig,
        prompt: String,
        pdfPageImagesBase64: List<String>
    ): String {
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
                        pdfPageImagesBase64.forEach { base64Jpeg ->
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
                throw ProviderCallException(errMsg, isTransientHttp(response.code))
            }

            val candidates = jsonObj?.get("candidates") as? JsonArray
            val firstCandidate = candidates?.firstOrNull() as? JsonObject
            val contentObj = firstCandidate?.get("content") as? JsonObject
            val parts = contentObj?.get("parts") as? JsonArray
            val text = parts?.joinToString("") { part ->
                ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }.orEmpty()

            if (text.isBlank()) {
                throw ProviderCallException("استجابة فارغة", isTransient = true)
            }
            return text
        }
    }

    private fun callOpenAiCompatible(
        profile: ProviderConfig,
        prompt: String,
        pdfPageImagesBase64: List<String>
    ): String {
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
                throw ProviderCallException(errMsg, isTransientHttp(response.code))
            }

            val choices = jsonObj?.get("choices") as? JsonArray
            val firstChoice = choices?.firstOrNull() as? JsonObject
            val messageObj = firstChoice?.get("message") as? JsonObject
            val text = (messageObj?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()

            if (text.isBlank()) {
                throw ProviderCallException("استجابة فارغة", isTransient = true)
            }
            return text
        }
    }

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
