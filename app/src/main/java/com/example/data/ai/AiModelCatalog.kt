package com.example.data.ai

import com.example.model.ProviderType
import kotlinx.serialization.Serializable

/**
 * Single source of truth for model identifiers exposed by the application.
 * API profiles still own credentials and may keep a model override, but model
 * metadata and system-admin-managed model definitions live in this catalog.
 */
@Serializable
data class AiModelDefinition(
    val modelId: String,
    val displayName: String,
    val provider: ProviderType,
    val lifecycleStatus: String = STATUS_AVAILABLE,
    val replacementModelId: String? = null,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val supportsGenerateContent: Boolean = true,
    val source: String = SOURCE_USER
) {
    companion object {
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_PREVIEW = "PREVIEW"
        const val STATUS_ROUTED = "ROUTED"
        const val STATUS_DEPRECATED = "DEPRECATED"
        const val SOURCE_GOOGLE = "GOOGLE_CATALOG"
        const val SOURCE_USER = "USER_DEFINED"
        const val SOURCE_SYSTEM_ADMIN = "SYSTEM_ADMIN"
    }
}

/**
 * Built-in seed values only. Runtime code must resolve model IDs from the
 * persisted catalog or from an explicitly configured API profile; never add
 * model IDs to request methods, retry loops, or UI callbacks.
 */
object AiModelCatalogDefaults {
    const val DEFAULT_GEMINI_MODEL_ID = "gemini-3.5-flash"

    val entries = listOf(
        AiModelDefinition(
            modelId = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            provider = ProviderType.GEMINI,
            lifecycleStatus = AiModelDefinition.STATUS_AVAILABLE,
            isDefault = true,
            source = AiModelDefinition.SOURCE_GOOGLE
        ),
        AiModelDefinition(
            modelId = "gemini-3.6-flash",
            displayName = "Gemini 3.6 Flash",
            provider = ProviderType.GEMINI,
            lifecycleStatus = AiModelDefinition.STATUS_AVAILABLE,
            source = AiModelDefinition.SOURCE_GOOGLE
        ),
        AiModelDefinition(
            modelId = "gemini-3.7-flash",
            displayName = "Gemini 3.7 Flash",
            provider = ProviderType.GEMINI,
            lifecycleStatus = AiModelDefinition.STATUS_AVAILABLE,
            source = AiModelDefinition.SOURCE_GOOGLE
        ),
        AiModelDefinition(
            modelId = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            provider = ProviderType.GEMINI,
            lifecycleStatus = AiModelDefinition.STATUS_AVAILABLE,
            source = AiModelDefinition.SOURCE_GOOGLE
        )
    )
}
