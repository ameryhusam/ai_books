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
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AiExecutionRequest(
    val prompt: String,
    val providers: List<ProviderConfig>,
    val selectedProvider: ProviderType,
    val requestedModelId: String,
    val requestedProfileId: String,
    val pdfPageImagesBase64: List<String> = emptyList(),
    val pdfDocumentBase64: String? = null,
    val responseSchemaJson: String = "",
    val responseFormat: String = "text",
    val templateId: String = "",
    val templateVersion: Int = 1,
    val responseSchemaHash: String = "",
    val requestFingerprint: String = "",
    val sourcePdfHash: String = "",
    val onReserveProvider: (suspend (ProviderConfig) -> Boolean)? = null,
    val onProviderStateChanged: (suspend (providerId: String, status: String, retryAfterEpochMs: Long) -> Unit)? = null,
    val onAttemptFinished: (suspend (providerLabel: String, model: String, success: Boolean, error: String?) -> Unit)? = null
)

data class PdfDocumentPayload(val base64: String, val sha256: String, val byteCount: Int)

class AiGenerationService(
    private val context: Context? = null,
    private val client: OkHttpClient = defaultClient(),
    private val geminiEndpointBase: String = "https://generativelanguage.googleapis.com/v1beta/models"
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
    private val geminiModelRouter = GeminiModelRouter()

    open class ProviderCallException(
        message: String,
        val isTransient: Boolean,
        val httpCode: Int? = null,
        val retryAfterEpochMs: Long = 0L,
        val providerReason: String = ""
    ) : IllegalStateException(message)

    fun buildPrompt(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        questionCount: Int = 5,
        questionTypeIds: List<String> = emptyList(),
        questionLanguage: String = "ar",
        attachedPdfPagesCount: Int = 0,
        questionStyleHint: String = "",
        difficultyHint: String = "",
        responseSchemaJson: String = "",
        responseFormat: String = "text"
    ): String {
        val blueprint = BookIndexPromptRegistry.blueprintFor(task)
        val promptTemplate = context?.let { appContext ->
            appContext.assets.open(blueprint.sourcePath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.orEmpty()
        require(context == null || promptTemplate.isNotBlank()) {
            "ملف قالب المهمة غير موجود أو فارغ: ${blueprint.sourcePath}"
        }
        val safeLesson = lesson.copy(
            bookTitle = PromptPrivacySanitizer.sanitize(lesson.bookTitle),
            unitTitle = PromptPrivacySanitizer.sanitize(lesson.unitTitle),
            title = PromptPrivacySanitizer.sanitize(lesson.title)
        )
        return BookIndexPromptRegistry.assemblePrompt(
        lesson = safeLesson,
        task = task,
        source = PromptPrivacySanitizer.sanitize(source),
        extra = PromptPrivacySanitizer.sanitize(extra),
        questionCount = questionCount,
        questionTypeIds = questionTypeIds,
        questionLanguage = questionLanguage,
        attachedPdfPagesCount = attachedPdfPagesCount,
        questionStyleHint = PromptPrivacySanitizer.sanitize(questionStyleHint),
        difficultyHint = PromptPrivacySanitizer.sanitize(difficultyHint),
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

    suspend fun sha256LessonPdf(pdfPath: String?): String = withContext(Dispatchers.IO) {
        require(!pdfPath.isNullOrBlank()) { "مسار PDF غير محدد" }
        val input = if (context != null) {
            UriIO.openInputStream(context, pdfPath)
        } else {
            File(pdfPath).takeIf { it.isFile }?.inputStream()
        } ?: throw IllegalStateException("تعذر فتح ملف PDF لحساب البصمة")
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
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
            UriIO.openInputStream(context, pdfPath)?.use { readBounded(it, maxBytes) }
        } else {
            File(pdfPath).takeIf { it.isFile }?.inputStream()?.use { readBounded(it, maxBytes) }
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

    /**
     * Compatibility adapter for lesson/task callers. New feature adapters should prepare
     * their own prompt and call execute(AiExecutionRequest) directly.
     */
    suspend fun generate(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        providers: List<ProviderConfig>,
        selectedProvider: ProviderType,
        requestedModelId: String,
        requestedProfileId: String,
        questionCount: Int = 5,
        questionTypeIds: List<String> = emptyList(),
        questionLanguage: String = "ar",
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
    ): GeneratedResult {
        val prompt = withContext(Dispatchers.IO) {
            buildPrompt(
                lesson = lesson,
                task = task,
                source = source,
                extra = extra,
                questionCount = questionCount,
                questionTypeIds = questionTypeIds,
                questionLanguage = questionLanguage,
                attachedPdfPagesCount = if (!pdfDocumentBase64.isNullOrBlank()) 1 else pdfPageImagesBase64.size,
                questionStyleHint = questionStyleHint,
                difficultyHint = difficultyHint,
                responseSchemaJson = responseSchemaJson,
                responseFormat = responseFormat
            )
        }
        return execute(
            AiExecutionRequest(
                prompt = prompt,
                providers = providers,
                selectedProvider = selectedProvider,
                requestedModelId = requestedModelId,
                requestedProfileId = requestedProfileId,
                pdfPageImagesBase64 = pdfPageImagesBase64,
                pdfDocumentBase64 = pdfDocumentBase64,
                responseSchemaJson = responseSchemaJson,
                responseFormat = responseFormat,
                templateId = templateId,
                templateVersion = templateVersion,
                responseSchemaHash = responseSchemaHash,
                requestFingerprint = requestFingerprint,
                sourcePdfHash = sourcePdfHash,
                onReserveProvider = onReserveProvider,
                onProviderStateChanged = onProviderStateChanged,
                onAttemptFinished = onAttemptFinished
            )
        )
    }

    /**
     * Task-neutral provider execution. It does not know LessonRef, TaskType, Room entities,
     * question parsing, index parsing, assessment interpretation or UI destinations.
     */
    suspend fun execute(request: AiExecutionRequest): GeneratedResult = withContext(Dispatchers.IO) {
        val providers = request.providers
        val selectedProvider = request.selectedProvider
        val requestedModelId = request.requestedModelId
        val requestedProfileId = request.requestedProfileId
        val pdfPageImagesBase64 = request.pdfPageImagesBase64
        val pdfDocumentBase64 = request.pdfDocumentBase64
        val responseSchemaJson = request.responseSchemaJson
        val responseFormat = request.responseFormat
        val templateId = request.templateId
        val templateVersion = request.templateVersion
        val responseSchemaHash = request.responseSchemaHash
        val requestFingerprint = request.requestFingerprint
        val sourcePdfHash = request.sourcePdfHash
        val onReserveProvider = request.onReserveProvider
        val onProviderStateChanged = request.onProviderStateChanged
        val onAttemptFinished = request.onAttemptFinished
        val prompt = request.prompt

        require(prompt.isNotBlank()) { "يجب تجهيز البرومبت قبل تنفيذ طلب AI." }
        require(requestedModelId.isNotBlank()) { "يجب اختيار نموذج من كتالوج مدير النظام." }
        require(requestedProfileId.isNotBlank()) { "يجب اختيار ملف اتصال API." }

        val active = ProviderRoutingPolicy.orderedProfiles(
            providers = providers,
            selectedProvider = selectedProvider,
            preferredProfileId = requestedProfileId
        )
        require(active.isNotEmpty()) {
            "لا توجد ملفات API مفعّلة للمزود ${selectedProvider.displayName}."
        }
        val errors = mutableListOf<String>()
        val prompt = buildPrompt(
            lesson = lesson,
            task = task,
            source = source,
            extra = extra,
            questionCount = questionCount,
            questionTypeIds = questionTypeIds,
            questionLanguage = questionLanguage,
            attachedPdfPagesCount = if (!pdfDocumentBase64.isNullOrBlank()) 1 else pdfPageImagesBase64.size,
            questionStyleHint = questionStyleHint,
            difficultyHint = difficultyHint,
            responseSchemaJson = responseSchemaJson,
            responseFormat = responseFormat
        )
        val expectJson = responseFormat.equals("json", ignoreCase = true) || responseSchemaJson.isNotBlank()

        profileLoop@ for (profile in active) {
            val modelCandidates = when (profile.provider) {
                ProviderType.GEMINI -> ProviderRoutingPolicy.modelCandidates(profile, requestedModelId)
                ProviderType.OPENAI_COMPATIBLE -> listOf(requestedModelId.trim())
            }.distinct()
            if (modelCandidates.isEmpty()) {
                errors.add("${profile.label}: لا توجد نماذج مرشحة صالحة لهذا الملف.")
                continue@profileLoop
            }

            var skipProfile = false
            var profileHadRetryableFailure = false

            modelLoop@ for (modelName in modelCandidates) {
                if (profile.provider == ProviderType.GEMINI &&
                    geminiModelRouter.isCoolingDown(profile.id, modelName)
                ) {
                    errors.add("${profile.label}/$modelName: النموذج في فترة تهدئة بعد ازدحام سابق.")
                    profileHadRetryableFailure = true
                    continue@modelLoop
                }

                val candidateProfile = profile.copy(
                    model = modelName,
                    fallbackModels = emptyList(),
                    strictModel = true
                )
                var attempt = 0
                var moveToNextModel = false

                while (attempt < 3 && !moveToNextModel) {
                    val callStart = System.currentTimeMillis()
                    try {
                        val reserved = onReserveProvider?.invoke(candidateProfile) ?: true
                        if (!reserved) {
                            val state = profile.runtimeStatus.uppercase()
                            errors.add(
                                "${profile.label}: لم يُرسل الطلب لأن ملف API غير متاح حالياً (الحالة: $state)."
                            )
                            skipProfile = true
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
                        onProviderStateChanged?.invoke(profile.id, "ACTIVE", 0L)
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
                            sourcePdfHash = sourcePdfHash,
                            promptUsed = prompt,
                            responseSchemaJson = responseSchemaJson
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ProviderCallException) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                        errors.add("${profile.label}/$modelName (المحاولة ${attempt + 1}/3): $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        val failureStatus = classifyProviderFailure(e, reason, profile.provider)

                        when (failureStatus) {
                            "QUOTA_EXHAUSTED" -> {
                                onProviderStateChanged?.invoke(profile.id, failureStatus, e.retryAfterEpochMs)
                                errors.add("${profile.label}: تم استنفاد الحصة؛ سيتم تجربة ملف API آخر للمزود نفسه.")
                                skipProfile = true
                                break@modelLoop
                            }
                            "INVALID_KEY" -> {
                                onProviderStateChanged?.invoke(profile.id, failureStatus, 0L)
                                errors.add("${profile.label}: المفتاح غير صالح؛ سيتم تجربة ملف API آخر للمزود نفسه.")
                                skipProfile = true
                                break@modelLoop
                            }
                            "INVALID_REQUEST" -> {
                                onProviderStateChanged?.invoke(profile.id, failureStatus, 0L)
                                throw IllegalStateException(
                                    "${profile.label}/$modelName: رفض المزود الطلب بسبب إعداد أو مدخل غير صالح: $reason",
                                    e
                                )
                            }
                            "MODEL_UNAVAILABLE" -> {
                                onProviderStateChanged?.invoke(profile.id, failureStatus, 0L)
                                moveToNextModel = true
                            }
                            "RATE_LIMITED" -> {
                                onProviderStateChanged?.invoke(profile.id, failureStatus, e.retryAfterEpochMs)
                                if (profile.provider == ProviderType.GEMINI) {
                                    profileHadRetryableFailure = true
                                    if (attempt == 2) {
                                        geminiModelRouter.markCoolingDown(profile.id, modelName, e.retryAfterEpochMs)
                                        moveToNextModel = true
                                    }
                                } else if (attempt == 2) {
                                    // OpenAI-compatible profiles may represent separate projects/keys.
                                    skipProfile = true
                                    break@modelLoop
                                }
                            }
                            else -> {
                                if (!e.isTransient) {
                                    onProviderStateChanged?.invoke(profile.id, failureStatus, e.retryAfterEpochMs)
                                    throw IllegalStateException(
                                        "${profile.label}/$modelName: فشل غير قابل لإعادة المحاولة: $reason",
                                        e
                                    )
                                }
                                profileHadRetryableFailure = true
                                if (attempt == 2) {
                                    if (profile.provider == ProviderType.GEMINI) {
                                        moveToNextModel = true
                                    } else {
                                        skipProfile = true
                                        break@modelLoop
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "خطأ شبكة"
                        errors.add("${profile.label}/$modelName (المحاولة ${attempt + 1}/3): $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        onProviderStateChanged?.invoke(profile.id, "NETWORK_ERROR", 0L)
                        profileHadRetryableFailure = true
                        if (attempt == 2) {
                            if (profile.provider == ProviderType.GEMINI) {
                                moveToNextModel = true
                            } else {
                                skipProfile = true
                                break@modelLoop
                            }
                        }
                    } catch (e: Exception) {
                        val reason = e.message?.takeIf { it.isNotBlank() } ?: "فشل"
                        errors.add("${profile.label}/$modelName: $reason")
                        onAttemptFinished?.invoke(profile.label, modelName, false, reason)
                        onProviderStateChanged?.invoke(profile.id, "ERROR", 0L)
                        throw e
                    }

                    if (attempt < 2 && !moveToNextModel && !skipProfile) {
                        val retryAfter = (800L shl attempt).coerceAtMost(8_000L)
                        delay(retryAfter + kotlin.random.Random.nextLong(0L, 251L))
                    }
                    attempt++
                }
            }

            if (skipProfile) continue@profileLoop

            if (profile.provider == ProviderType.GEMINI && profileHadRetryableFailure) {
                throw IllegalStateException(
                    "تعذر استخدام نماذج Gemini المهيأة على الملف ${profile.label} بعد ثلاث محاولات لكل نموذج. لم يتم تدوير مفاتيح Google بسبب الازدحام؛ تدوير الملف الآخر مخصص لحالة نفاد الحصة أو عدم صلاحية المفتاح. التفاصيل: ${errors.joinToString(" | ")}"
                )
            }

            if (profile.provider == ProviderType.GEMINI && errors.isNotEmpty()) {
                throw IllegalStateException(
                    "تعذر التوليد عبر Gemini بعد تجربة النماذج المهيأة: ${errors.joinToString(" | ")}"
                )
            }
        }

        throw IllegalStateException(
            "تعذر التوليد عبر ${selectedProvider.displayName}. جُرّبت ملفات API المفعّلة التابعة للمزود نفسه فقط. التفاصيل: ${errors.joinToString(" | ")}"
        )
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
                val errorObject = jsonObj?.get("error") as? JsonObject
                val errMsg = (errorObject?.get("message") as? JsonPrimitive)?.contentOrNull
                    ?: "Gemini HTTP ${response.code}"
                val providerReason = extractGeminiErrorSignals(errorObject)
                throw ProviderCallException(
                    message = errMsg,
                    isTransient = isTransientHttp(response.code),
                    httpCode = response.code,
                    retryAfterEpochMs = retryAfterEpochMs(response),
                    providerReason = providerReason
                )
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
                val finishReason = (firstCandidate?.get("finishReason") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val blockReason = ((jsonObj?.get("promptFeedback") as? JsonObject)
                    ?.get("blockReason") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val diagnostic = listOf(
                    "لم يُرجع Gemini محتوى نصياً قابلاً للاستخراج",
                    finishReason.takeIf { it.isNotBlank() }?.let { "finishReason=$it" },
                    blockReason.takeIf { it.isNotBlank() }?.let { "blockReason=$it" }
                ).filterNotNull().joinToString("؛ ")
                throw ProviderCallException(
                    message = diagnostic,
                    isTransient = false,
                    providerReason = listOf(finishReason, blockReason).filter { it.isNotBlank() }.joinToString(" ")
                )
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
            val content = messageObj?.get("content")
            val text = when (content) {
                is JsonPrimitive -> content.contentOrNull.orEmpty()
                is JsonArray -> content.joinToString("") { segment ->
                    val item = segment as? JsonObject
                    ((item?.get("text") as? JsonPrimitive)?.contentOrNull).orEmpty()
                }
                else -> ""
            }
            val tokens = (jsonObj?.get("usage") as? JsonObject)
                ?.get("total_tokens")
                ?.let { it as? JsonPrimitive }
                ?.intOrNull
                ?: 0

            if (text.isBlank()) {
                val finishReason = (firstChoice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val refusal = (messageObj?.get("refusal") as? JsonPrimitive)?.contentOrNull.orEmpty()
                throw ProviderCallException(
                    message = listOf(
                        "لم يُرجع OpenAI-compatible محتوى نصياً قابلاً للاستخراج",
                        finishReason.takeIf { it.isNotBlank() }?.let { "finish_reason=$it" },
                        refusal.takeIf { it.isNotBlank() }?.let { "refusal=$it" }
                    ).filterNotNull().joinToString("؛ "),
                    isTransient = false,
                    providerReason = listOf(finishReason, refusal).filter { it.isNotBlank() }.joinToString(" ")
                )
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

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) {
                "حجم PDF يتجاوز الحد المحلي ${maxBytes / (1024 * 1024)} MB؛ اختر صفحات أقل أو ملفاً أصغر."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun normalizeGeminiSchema(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key == "type" && value is JsonPrimitive) JsonPrimitive(value.content.uppercase())
            else normalizeGeminiSchema(value)
        })
        is JsonArray -> JsonArray(element.map(::normalizeGeminiSchema))
        else -> element
    }

    private fun extractGeminiErrorSignals(error: JsonObject?): String {
        if (error == null) return ""
        val status = (error["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val details = (error["details"] as? JsonArray).orEmpty()
        val reasons = details.mapNotNull { detail ->
            val item = detail as? JsonObject ?: return@mapNotNull null
            listOf("reason", "domain", "metadata")
                .mapNotNull { key -> item[key]?.toString() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ")
        }
        return (listOf(status) + reasons).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun classifyProviderFailure(
        error: ProviderCallException,
        message: String,
        provider: ProviderType
    ): String {
        val signals = "${error.providerReason} $message".lowercase()
        return when {
            error.httpCode == 401 || error.httpCode == 403 ||
                Regex("invalid api.?key|api key not valid|unauthorized|authentication failed")
                    .containsMatchIn(signals) -> "INVALID_KEY"
            Regex("quota_exceeded|quota exceeded|quota exhausted|daily quota|insufficient quota|insufficient_quota|billing limit|billing_hard_limit|exceeded your current quota")
                .containsMatchIn(signals) -> "QUOTA_EXHAUSTED"
            error.httpCode == 404 ||
                Regex("model[^ ]*(not found|does not exist)|model_not_found|unsupported model")
                    .containsMatchIn(signals) -> "MODEL_UNAVAILABLE"
            error.httpCode == 400 -> "INVALID_REQUEST"
            Regex("rate_limit_exceeded|rate.?limit|too many requests|requests per minute|tokens per minute|retry after")
                .containsMatchIn(signals) -> "RATE_LIMITED"
            error.httpCode == 429 -> "RATE_LIMITED"
            else -> "ERROR"
        }
    }

    private fun retryAfterEpochMs(response: Response): Long {
        val now = System.currentTimeMillis()
        val header = response.header("Retry-After")?.trim().orEmpty()
        val retryAt = header.toLongOrNull()?.let { seconds ->
            now + seconds.coerceAtLeast(1L) * 1000L
        } ?: runCatching {
            ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

        return retryAt?.coerceAtLeast(now + 1_000L) ?: (now + 60_000L)
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
