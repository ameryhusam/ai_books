package com.example.data.ai

import com.example.model.ProviderConfig
import com.example.model.ProviderType

/**
 * Keeps provider selection and candidate ordering deterministic and provider-scoped.
 * Fallback model names are read only from profiles belonging to the selected provider.
 */
internal object ProviderRoutingPolicy {
    fun activeProfiles(
        providers: List<ProviderConfig>,
        selectedProvider: ProviderType
    ): List<ProviderConfig> = providers
        .asSequence()
        .filter {
            it.provider == selectedProvider &&
                it.enabled &&
                it.apiKey.isNotBlank() &&
                it.model.isNotBlank()
        }
        .sortedWith(
            compareBy<ProviderConfig> { it.lastUsedAtEpochMs }
                .thenBy { it.priority }
                .thenBy { it.createdAt }
        )
        .toList()

    fun modelCandidates(profile: ProviderConfig): List<String> {
        val primary = profile.model.trim()
        if (primary.isBlank()) return emptyList()
        if (profile.strictModel) return listOf(primary)
        return (listOf(primary) + profile.fallbackModels.map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()
    }
}
