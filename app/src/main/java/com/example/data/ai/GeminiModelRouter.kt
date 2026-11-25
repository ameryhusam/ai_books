package com.example.data.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local cooldowns for Gemini model saturation.
 * Cooldowns are model-scoped (profile + model), not provider-wide, so another
 * Gemini model on the same key can still be attempted immediately.
 */
internal class GeminiModelRouter(
    private val defaultCooldownMs: Long = 60_000L
) {
    private val cooldowns = ConcurrentHashMap<String, Long>()

    fun markCoolingDown(
        profileId: String,
        model: String,
        retryAfterEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        val until = maxOf(retryAfterEpochMs, nowEpochMs + defaultCooldownMs)
        cooldowns[key(profileId, model)] = until
    }

    fun isCoolingDown(
        profileId: String,
        model: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val key = key(profileId, model)
        val until = cooldowns[key] ?: return false
        if (until <= nowEpochMs) {
            cooldowns.remove(key, until)
            return false
        }
        return true
    }

    private fun key(profileId: String, model: String): String = "$profileId::$model"
}
