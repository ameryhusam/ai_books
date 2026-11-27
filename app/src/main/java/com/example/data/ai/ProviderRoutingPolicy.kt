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
                it.apiKey.isNotBlank()
        }
        .sortedWith(
            compareBy<ProviderConfig> { it.lastUsedAtEpochMs }
                .thenBy { it.priority }
                .thenBy { it.createdAt }
        )
        .toList()

    fun exactModelCandidate(requestedModelId: String): List<String> {
        val exact = requestedModelId.trim()
        require(exact.isNotBlank()) { "An explicit catalogue model ID is required." }
        return listOf(exact)
    }

    fun orderedProfiles(
        providers: List<ProviderConfig>,
        selectedProvider: ProviderType,
        preferredProfileId: String
    ): List<ProviderConfig> = activeProfiles(providers, selectedProvider)
        .sortedWith(
            compareBy<ProviderConfig> { if (it.id == preferredProfileId) 0 else 1 }
                .thenBy { it.priority }
                .thenBy { it.lastUsedAtEpochMs }
                .thenBy { it.createdAt }
        )

    fun modelCandidates(profile: ProviderConfig): List<String> {
        val primary = profile.model.trim()
        if (primary.isBlank()) return emptyList()
        if (profile.strictModel) return listOf(primary)
        return (listOf(primary) + profile.fallbackModels.map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** The UI-selected model is always first; same-provider configured fallbacks follow. */
    fun modelCandidates(profile: ProviderConfig, preferredModelId: String): List<String> {
        val preferred = preferredModelId.trim()
        require(preferred.isNotBlank()) { "An explicit catalogue model ID is required." }
        if (profile.strictModel) return listOf(preferred)
        return (listOf(preferred) + modelCandidates(profile))
            .filter { it.isNotBlank() }
            .distinct()
    }
}
