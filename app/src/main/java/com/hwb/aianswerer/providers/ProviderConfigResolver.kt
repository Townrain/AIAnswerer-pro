package com.hwb.aianswerer.providers

import com.hwb.aianswerer.BuildConfig
import com.hwb.aianswerer.api.vision.VisionProviderFactory
import com.hwb.aianswerer.config.ConfigStorage
import com.tencent.mmkv.MMKV

/**
 * Centralizes fallback resolution logic for API/Vision config methods,
 * keeping cross-layer dependencies (providers, api) out of the config files.
 */
object ProviderConfigResolver {

    // ============================================================
    //  API (LLM) fallback resolution
    // ============================================================

    /**
     * Find the enabled provider that owns the currently selected model.
     * Falls back to the first enabled provider if no match.
     */
    fun resolveApiProvider(): LocalProviderConfig? {
        return try {
            val modelName = readRawModelName()
            val mergedProviders = ProviderStorage.getMergedProviders()
                .filter { it.enabled && it.apiKey.isNotBlank() }
            if (mergedProviders.isEmpty()) return null

            if (modelName.isNotBlank()) {
                val userConfigs = ProviderStorage.getEnabledProvidersFromUserConfigs()
                val targetId = userConfigs.firstOrNull { it.selectedModels.contains(modelName) }?.id
                if (targetId != null) {
                    mergedProviders.firstOrNull { it.id == targetId } ?: mergedProviders.first()
                } else {
                    mergedProviders.first()
                }
            } else {
                mergedProviders.first()
            }
        } catch (_: Exception) { null }
    }

    /**
     * Resolve API URL from the first enabled provider in the provider system.
     * @return resolved URL or null if no provider is available
     */
    fun resolveApiUrl(): String? {
        return try {
            val provider = resolveApiProvider() ?: return null
            val host = provider.apiHost.trimEnd('/')
            when {
                host.endsWith("/v1") -> "$host/chat/completions"
                host.contains("/v1/") -> "${host}chat/completions"
                else -> "$host/v1/chat/completions"
            }
        } catch (_: Exception) { null }
    }

    /**
     * Resolve API Key from the first enabled provider in the provider system.
     * @return API Key or empty string
     */
    fun resolveApiKey(): String {
        return try {
            resolveApiProvider()?.apiKey ?: ""
        } catch (_: Exception) { "" }
    }

    /**
     * Resolve model name from the first enabled provider in the provider system.
     * @return model name or empty string
     */
    fun resolveModelName(): String {
        return try {
            ProviderStorage.getEnabledProviders()
                .firstOrNull()?.selectedModels?.firstOrNull() ?: ""
        } catch (_: Exception) { "" }
    }

    /**
     * Delegate to ProviderStorage to check if any provider is configured.
     */
    fun isAnyProviderConfigured(): Boolean {
        return try {
            ProviderStorage.isAnyProviderConfigured()
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  Vision fallback resolution
    // ============================================================

    /**
     * Find the enabled provider whose selectedModels match the current vision model name.
     * Falls back to the first enabled provider if no match.
     */
    fun resolveVisionProvider(): LocalProviderConfig? {
        return try {
            val visionModel = readRawVisionModelName()
            val userConfigs = ProviderStorage.getEnabledProvidersFromUserConfigs()
            val allProviders = ProviderStorage.getMergedProviders()
                .filter { it.enabled && it.apiKey.isNotBlank() }
            if (visionModel.isNotBlank()) {
                val targetId = userConfigs.firstOrNull { it.selectedModels.contains(visionModel) }?.id
                if (targetId != null) {
                    allProviders.firstOrNull { it.id == targetId }
                } else {
                    allProviders.firstOrNull()
                }
            } else {
                allProviders.firstOrNull()
            }
        } catch (_: Exception) { null }
    }

    /**
     * Resolve vision base URL from provider system, falling back to VisionProviderFactory defaults.
     */
    fun resolveVisionBaseUrl(): String {
        val provider = resolveVisionProvider()
        if (provider != null) {
            val host = provider.apiHost.trimEnd('/')
            return when {
                host.endsWith("/v1") -> "$host/chat/completions"
                host.contains("/v1/") -> "${host}chat/completions"
                else -> "$host/v1/chat/completions"
            }
        }
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[readVisionProviderId()]
        return meta?.defaultBaseUrl ?: "https://api.deepseek.com/v1/chat/completions"
    }

    /**
     * Resolve vision API key from the first enabled provider.
     */
    fun resolveVisionApiKey(): String {
        return resolveVisionProvider()?.apiKey ?: ""
    }

    /**
     * Resolve vision model name from VisionProviderFactory defaults.
     */
    fun resolveVisionModelName(): String {
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[readVisionProviderId()]
        return meta?.defaultModel ?: "deepseek-chat"
    }

    /**
     * Resolve default vision base URL from VisionProviderFactory.
     */
    fun resolveVisionDefaultBaseUrl(): String {
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[readVisionProviderId()]
        return meta?.defaultBaseUrl ?: "https://api.deepseek.com/v1/chat/completions"
    }

    /**
     * Resolve default vision model name from VisionProviderFactory.
     */
    fun resolveVisionDefaultModel(): String {
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[readVisionProviderId()]
        return meta?.defaultModel ?: "deepseek-chat"
    }

    // ============================================================
    //  Raw MMKV reads (avoid circular calls with config getters)
    // ============================================================

    private fun readRawModelName(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_MODEL_NAME, BuildConfig.API_MODEL) ?: ""
    }

    private fun readRawVisionModelName(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, null) ?: ""
    }

    private fun readVisionProviderId(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, "openai_compat") ?: "openai_compat"
    }
}
