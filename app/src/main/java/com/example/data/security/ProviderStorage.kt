package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ProviderStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadProviders(): List<ProviderConfig> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return@withContext emptyList()
        val decrypted = runCatching { decrypt(raw) }.getOrNull() ?: return@withContext emptyList()
        runCatching { json.decodeFromString<List<ProviderConfig>>(decrypted) }.getOrDefault(emptyList())
    }

    suspend fun saveProviders(items: List<ProviderConfig>) = withContext(Dispatchers.IO) {
        val serialized = json.encodeToString(items)
        val encrypted = encrypt(serialized)
        prefs.edit().putString(KEY_PROFILES, encrypted).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
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
        }.getOrNull()
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        if (secretKey != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataEncoded = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            return "$PREFIX_GCM$ivEncoded:$dataEncoded"
        }
        val fallback = Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "$PREFIX_PLAIN$fallback"
    }

    private fun decrypt(payload: String): String {
        if (payload.startsWith(PREFIX_GCM)) {
            val body = payload.removePrefix(PREFIX_GCM)
            val parts = body.split(":", limit = 2)
            if (parts.size != 2) return "[]"
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey() ?: return "[]"
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
        }
        if (payload.startsWith(PREFIX_PLAIN)) {
            val raw = Base64.decode(payload.removePrefix(PREFIX_PLAIN), Base64.NO_WRAP)
            return String(raw, StandardCharsets.UTF_8)
        }
        return payload
    }

    companion object {
        private const val PREFS_NAME = "edu_mini_secure_prefs"
        private const val KEY_PROFILES = "edu_mini_ai_profiles_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "edu_mini_providers_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX_GCM = "gcm:v1:"
        private const val PREFIX_PLAIN = "plain:v1:"
    }
}
