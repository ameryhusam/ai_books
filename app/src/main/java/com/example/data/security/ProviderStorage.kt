package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.data.ai.AiModelCatalogDefaults
import com.example.data.ai.AiModelDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted provider profiles plus atomic request-attempt telemetry.
 *
 * Daily limit fields remain in ProviderConfig for backward compatibility, but the
 * app does not enforce provider quota locally. The provider response is authoritative.
 * The same process-wide instance serializes profile state updates.
 */
class ProviderStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    suspend fun loadSelectedProviderType(): ProviderType = withContext(Dispatchers.IO) {
        synchronized(lock) {
            ProviderType.fromId(prefs.getString(KEY_SELECTED_PROVIDER, ProviderType.GEMINI.id).orEmpty())
        }
    }

    suspend fun saveSelectedProviderType(type: ProviderType) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(prefs.edit().putString(KEY_SELECTED_PROVIDER, type.id).commit()) {
                "تعذر حفظ مزود الذكاء الاصطناعي الافتراضي"
            }
        }
    }

    /**
     * Model metadata is stored separately from encrypted API credentials.
     * The catalog is user-editable; profile credentials remain in KEY_PROFILES.
     */
    suspend fun loadModelCatalog(): List<AiModelDefinition> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val raw = prefs.getString(KEY_MODEL_CATALOG, null)
            val decoded = if (raw == null) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<AiModelDefinition>>(raw)
                } catch (error: Exception) {
                    throw IllegalStateException("تعذر قراءة فهرس نماذج AI المحفوظ؛ لم يتم استبدال البيانات.", error)
                }
            }
            // One-time catalog migration: restore the official Gemini seed models
            // for installations that persisted an empty/older catalog. Preserve
            // administrator choices and custom models; never replace API credentials.
            val seedVersion = prefs.getInt(KEY_MODEL_CATALOG_SEED_VERSION, 0)
            val stored = if (seedVersion < MODEL_CATALOG_SEED_VERSION) {
                val defaults = AiModelCatalogDefaults.entries
                val withDefaults = decoded.toMutableList()
                defaults.forEach { seed ->
                    val existingIndex = withDefaults.indexOfFirst {
                        it.provider == seed.provider && it.modelId == seed.modelId
                    }
                    if (existingIndex < 0) {
                        withDefaults += seed.copy(
                            isDefault = seed.isDefault &&
                                withDefaults.none { it.provider == seed.provider && it.isDefault }
                        )
                    } else {
                        val existing = withDefaults[existingIndex]
                        if (existing.displayName == existing.modelId) {
                            withDefaults[existingIndex] = existing.copy(displayName = seed.displayName)
                        }
                    }
                }
                if (withDefaults.none { it.provider == ProviderType.GEMINI && it.isDefault }) {
                    val defaultIndex = withDefaults.indexOfFirst {
                        it.provider == ProviderType.GEMINI &&
                            it.modelId == AiModelCatalogDefaults.DEFAULT_GEMINI_MODEL_ID
                    }
                    if (defaultIndex >= 0) {
                        withDefaults[defaultIndex] = withDefaults[defaultIndex].copy(isDefault = true)
                    }
                }
                withDefaults
            } else decoded
            // Preserve model IDs already used by legacy API profiles by importing
            // their references into the editable catalog instead of silently dropping them.
            val referencedModels = readProfilesLocked().flatMap { profile ->
                (listOf(profile.model) + profile.fallbackModels).filter { it.isNotBlank() }
                    .map { profile.provider to it.trim() }
            }.distinct()
            val additions = referencedModels
                .filterNot { (provider, modelId) -> stored.any { it.provider == provider && it.modelId == modelId } }
                .map { (provider, modelId) ->
                    AiModelDefinition(
                        modelId = modelId,
                        displayName = modelId,
                        provider = provider,
                        source = AiModelDefinition.SOURCE_USER
                    )
                }
            val merged = stored + additions
            if (raw == null || merged != decoded || seedVersion < MODEL_CATALOG_SEED_VERSION) {
                writeModelCatalogLocked(merged)
                check(prefs.edit().putInt(KEY_MODEL_CATALOG_SEED_VERSION, MODEL_CATALOG_SEED_VERSION).commit()) {
                    "تعذر حفظ إصدار ترحيل كتالوج نماذج AI."
                }
            }
            merged
        }
    }

    suspend fun addModelDefinition(model: AiModelDefinition) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readModelCatalogLocked()
            require(current.none { it.modelId.equals(model.modelId.trim(), ignoreCase = true) }) {
                "معرّف النموذج موجود مسبقاً."
            }
            val normalized = model.copy(modelId = model.modelId.trim(), displayName = model.displayName.trim())
            require(normalized.modelId.isNotBlank() && normalized.displayName.isNotBlank()) {
                "معرّف النموذج واسمه مطلوبان."
            }
            val updated = if (normalized.isDefault) {
                current.map { if (it.provider == normalized.provider) it.copy(isDefault = false) else it } + normalized
            } else current + normalized
            validateModelCatalog(updated)
            writeModelCatalogLocked(updated)
        }
    }

    suspend fun updateModelDefinition(originalModelId: String, updatedModel: AiModelDefinition) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readModelCatalogLocked()
            val original = current.firstOrNull { it.modelId == originalModelId }
                ?: throw IllegalArgumentException("النموذج المراد تعديله غير موجود.")
            val normalized = updatedModel.copy(
                modelId = updatedModel.modelId.trim(),
                displayName = updatedModel.displayName.trim()
            )
            require(normalized.provider == original.provider) {
                "لا يمكن تغيير مزود النموذج أثناء تعديله؛ أضف نموذجاً جديداً للمزود الآخر."
            }
            require(normalized.modelId.isNotBlank() && normalized.displayName.isNotBlank()) {
                "معرّف النموذج واسمه مطلوبان."
            }
            require(current.none { it.modelId != originalModelId && it.modelId.equals(normalized.modelId, true) }) {
                "معرّف النموذج الجديد مستخدم بالفعل."
            }
            var updated = current.map { entry ->
                when {
                    entry.modelId == originalModelId -> normalized
                    normalized.isDefault && entry.provider == normalized.provider -> entry.copy(isDefault = false)
                    else -> entry
                }
            }
            if (original.provider != normalized.provider) {
                updated = updated.map { entry ->
                    if (entry.modelId == normalized.modelId) entry else entry
                }
            }
            val profiles = readProfilesLocked()
            val migratedProfiles = profiles.map { profile ->
                if (profile.provider != original.provider) profile else profile.copy(
                    model = if (profile.model == originalModelId) normalized.modelId else profile.model,
                    fallbackModels = profile.fallbackModels.map { if (it == originalModelId) normalized.modelId else it }.distinct()
                )
            }
            validateModelCatalog(updated)
            writeModelCatalogLocked(updated)
            if (migratedProfiles != profiles) writeProfilesLocked(migratedProfiles)
        }
    }

    suspend fun deleteModelDefinition(modelId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readModelCatalogLocked()
            val model = current.firstOrNull { it.modelId == modelId }
                ?: throw IllegalArgumentException("النموذج غير موجود.")
            require(!model.isDefault) { "عيّن نموذجاً افتراضياً بديلاً قبل حذف النموذج الافتراضي." }
            val profiles = readProfilesLocked()
            require(profiles.none { profile ->
                profile.provider == model.provider &&
                    (profile.model == modelId || modelId in profile.fallbackModels)
            }) { "لا يمكن حذف النموذج لأنه مستخدم في ملف اتصال API. عدّل ملف الاتصال أولاً." }
            writeModelCatalogLocked(current.filterNot { it.modelId == modelId })
        }
    }

    suspend fun setDefaultModel(provider: ProviderType, modelId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readModelCatalogLocked()
            require(current.any { it.provider == provider && it.modelId == modelId && it.supportsGenerateContent }) {
                "النموذج المحدد غير متاح لمزود الخدمة."
            }
            writeModelCatalogLocked(current.map { entry ->
                if (entry.provider == provider) entry.copy(isDefault = entry.modelId == modelId) else entry
            })
        }
    }

    private fun readModelCatalogLocked(): List<AiModelDefinition> {
        val raw = prefs.getString(KEY_MODEL_CATALOG, null) ?: return AiModelCatalogDefaults.entries
        return try {
            json.decodeFromString<List<AiModelDefinition>>(raw)
        } catch (error: Exception) {
            throw IllegalStateException("تعذر قراءة فهرس نماذج AI المحفوظ؛ لم يتم استبدال البيانات.", error)
        }
    }

    private fun writeModelCatalogLocked(items: List<AiModelDefinition>) {
        validateModelCatalog(items)
        check(prefs.edit().putString(KEY_MODEL_CATALOG, json.encodeToString(items)).commit()) {
            "تعذر حفظ فهرس نماذج AI."
        }
    }

    private fun validateModelCatalog(items: List<AiModelDefinition>) {
        require(items.map { it.modelId.lowercase() }.distinct().size == items.size) {
            "توجد معرّفات نماذج مكررة."
        }
        require(items.all { it.modelId.isNotBlank() && it.displayName.isNotBlank() }) {
            "كل نموذج يحتاج إلى معرّف واسم عرض."
        }
        require(items.filter { it.isDefault }.groupBy { it.provider }.values.all { it.size == 1 }) {
            "يسمح بنموذج افتراضي واحد فقط لكل مزود."
        }
    }

    suspend fun loadProviders(): List<ProviderConfig> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val stored = readProfilesLocked()
            val normalized = stored.map { normalizeForCurrentDay(it, System.currentTimeMillis()) }
            if (normalized != stored) writeProfilesLocked(normalized)
            normalized
        }
    }

    suspend fun saveProviders(items: List<ProviderConfig>) = withContext(Dispatchers.IO) {
        synchronized(lock) { writeProfilesLocked(items) }
    }

    /**
     * Records an outgoing request attempt and checks only local availability state.
     * dailyRequestLimit is intentionally not enforced: actual quota is determined
     * by the provider response, not by a locally estimated counter.
     *
     * Returns false for disabled/invalid profiles, active rate-limit cooldowns, or a
     * provider-confirmed quota-exhausted profile. Quota state is cleared on the next
     * configured quota day; it is not inferred from the local diagnostic request counter.
     */
    suspend fun reserveProviderKey(providerId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val profiles = readProfilesLocked()
                var reserved = false
                val updated = profiles.map { original ->
                    if (original.id != providerId) return@map original
                    var profile = normalizeForCurrentDay(original, nowMillis)
                    val status = profile.runtimeStatus.uppercase()
                    if (!profile.enabled || status == STATUS_DISABLED || status == STATUS_INVALID_KEY ||
                        status == STATUS_QUOTA_EXHAUSTED ||
                        (status == STATUS_RATE_LIMITED && profile.retryAfterEpochMs > nowMillis)
                    ) return@map profile

                    // Diagnostic counter only; the provider response remains authoritative.
                    val nextUsage = profile.dailyRequestsUsed + 1
                    profile = profile.copy(
                        dailyRequestsUsed = nextUsage,
                        lastUsedAtEpochMs = nowMillis,
                        runtimeStatus = STATUS_ACTIVE
                    )
                    reserved = true
                    profile
                }
                if (updated != profiles) writeProfilesLocked(updated)
                reserved
            }
        }

    suspend fun updateProviderRuntimeState(
        providerId: String,
        status: String,
        retryAfterEpochMs: Long = 0L
    ) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val profiles = readProfilesLocked()
            val updated = profiles.map { profile ->
                if (profile.id == providerId && profile.enabled) {
                    profile.copy(
                        runtimeStatus = status,
                        retryAfterEpochMs = retryAfterEpochMs
                    )
                } else profile
            }
            if (updated != profiles) writeProfilesLocked(updated)
        }
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val profiles = readProfilesLocked()
            val updated = profiles.map { profile ->
                if (profile.id != providerId) profile else {
                    val today = currentDate(profile.quotaTimeZone, now)
                    profile.copy(
                        enabled = enabled,
                        runtimeStatus = if (enabled) STATUS_ACTIVE else STATUS_DISABLED,
                        retryAfterEpochMs = 0L,
                        dailyRequestsUsed = if (enabled) 0 else profile.dailyRequestsUsed,
                        quotaDate = today
                    )
                }
            }
            writeProfilesLocked(updated)
        }
    }

    suspend fun manuallyReactivateProvider(providerId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val profiles = readProfilesLocked()
            val updated = profiles.map { profile ->
                if (profile.id != providerId) profile else {
                    val today = currentDate(profile.quotaTimeZone, now)
                    profile.copy(
                        enabled = true,
                        runtimeStatus = STATUS_ACTIVE,
                        retryAfterEpochMs = 0L,
                        dailyRequestsUsed = 0,
                        quotaDate = today
                    )
                }
            }
            writeProfilesLocked(updated)
        }
    }

    private fun normalizeForCurrentDay(profile: ProviderConfig, nowMillis: Long): ProviderConfig {
        val today = currentDate(profile.quotaTimeZone, nowMillis)
        var next = profile
        if (profile.quotaDate != today) {
            next = next.copy(
                dailyRequestsUsed = 0,
                quotaDate = today,
                runtimeStatus = when (profile.runtimeStatus.uppercase()) {
                    STATUS_ERROR, STATUS_QUOTA_EXHAUSTED -> STATUS_ACTIVE
                    STATUS_RATE_LIMITED -> if (profile.retryAfterEpochMs <= nowMillis) STATUS_ACTIVE else STATUS_RATE_LIMITED
                    else -> profile.runtimeStatus
                },
                retryAfterEpochMs = if (profile.runtimeStatus.equals(STATUS_RATE_LIMITED, true) && profile.retryAfterEpochMs > nowMillis) profile.retryAfterEpochMs else 0L
            )
        } else if (profile.runtimeStatus.equals(STATUS_RATE_LIMITED, true) &&
            profile.retryAfterEpochMs <= nowMillis
        ) {
            next = next.copy(runtimeStatus = STATUS_ACTIVE, retryAfterEpochMs = 0L)
        }

        if (!next.enabled) next = next.copy(runtimeStatus = STATUS_DISABLED)

        return next
    }

    private fun currentDate(zone: String, nowMillis: Long): String {
        val zoneId = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of(DEFAULT_TIME_ZONE))
        return Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toString()
    }

    private fun readProfilesLocked(): List<ProviderConfig> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val decrypted = decrypt(raw)
        val profiles = try {
            json.decodeFromString<List<ProviderConfig>>(decrypted)
        } catch (e: Exception) {
            throw IllegalStateException("تعذر قراءة إعدادات مزودي AI المحفوظة؛ لم يتم تجاهل البيانات أو استبدالها.", e)
        }
        if (raw.startsWith(PREFIX_PLAIN)) writeProfilesLocked(profiles)
        return profiles
    }

    private fun writeProfilesLocked(items: List<ProviderConfig>) {
        val serialized = json.encodeToString(items)
        val encrypted = encrypt(serialized)
        check(prefs.edit().putString(KEY_PROFILES, encrypted).commit()) {
            "تعذر حفظ حالة مفاتيح API بأمان"
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrElse { throw IllegalStateException("تعذر الوصول إلى Android Keystore؛ لم يتم تخزين مفتاح API غير مشفر.", it) }
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataEncoded = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        return "$PREFIX_GCM$ivEncoded:$dataEncoded"
    }

    private fun decrypt(payload: String): String {
        if (payload.startsWith(PREFIX_GCM)) {
            val body = payload.removePrefix(PREFIX_GCM)
            val parts = body.split(":", limit = 2)
            require(parts.size == 2) { "بيانات تشفير مزود AI تالفة؛ لم يتم استبدالها." }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
        }
        if (payload.startsWith(PREFIX_PLAIN)) {
            val raw = Base64.decode(payload.removePrefix(PREFIX_PLAIN), Base64.NO_WRAP)
            return String(raw, StandardCharsets.UTF_8)
        }
        throw IllegalStateException("صيغة تخزين مفاتيح API غير معروفة؛ أوقفنا التحميل لحماية البيانات.")
    }

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_DISABLED = "DISABLED"
        const val STATUS_QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED"
        const val STATUS_RATE_LIMITED = "RATE_LIMITED"
        const val STATUS_INVALID_KEY = "INVALID_KEY"
        const val STATUS_ERROR = "ERROR"
        private const val DEFAULT_TIME_ZONE = "Asia/Aden"
        private const val PREFS_NAME = "edu_mini_secure_prefs"
        private const val KEY_PROFILES = "edu_mini_ai_profiles_v1"
        private const val KEY_SELECTED_PROVIDER = "edu_mini_selected_ai_provider_v1"
        private const val KEY_MODEL_CATALOG = "edu_mini_ai_model_catalog_v1"
        private const val KEY_MODEL_CATALOG_SEED_VERSION = "edu_mini_ai_model_catalog_seed_version"
        private const val MODEL_CATALOG_SEED_VERSION = 2
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "edu_mini_providers_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX_GCM = "gcm:v1:"
        private const val PREFIX_PLAIN = "plain:v1:"
    }
}
