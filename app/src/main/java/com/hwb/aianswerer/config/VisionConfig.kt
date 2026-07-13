package com.hwb.aianswerer.config

import com.hwb.aianswerer.api.vision.VisionProviderFactory

/**
 * Vision / VLM 视觉模型配置
 */
internal object VisionConfig {

    // ========== 视觉模型统一配置 ==========

    /**
     * 是否启用视觉过滤
     */
    fun isVisionEnabled(): Boolean {
        val configured = getVisionBaseUrl().isNotBlank() && getVisionApiKey().isNotBlank()
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_VISION_ENABLED, configured)
    }

    /**
     * 保存视觉过滤启用状态
     */
    fun saveVisionEnabled(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_ENABLED, enabled)
    }

    /**
     * 获取视觉模型 Provider ID
     */
    fun getVisionProviderId(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, "openai_compat") ?: "openai_compat"
    }

    /**
     * 保存视觉模型 Provider ID
     */
    fun saveVisionProviderId(id: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_PROVIDER_ID, id)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 API 地址
     */
    fun getVisionBaseUrl(): String {
        val saved = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_BASE_URL, null)
        if (!saved.isNullOrBlank()) return saved
        // Fallback to provider system — resolve by vision model name
        val provider = resolveVisionProvider()
        if (provider != null) {
            val host = provider.apiHost.trimEnd('/')
            return when {
                host.endsWith("/v1") -> "$host/chat/completions"
                host.contains("/v1/") -> "${host}chat/completions"
                else -> "$host/v1/chat/completions"
            }
        }
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[getVisionProviderId()]
        return meta?.defaultBaseUrl ?: "https://api.deepseek.com/v1/chat/completions"
    }

    private fun resolveVisionProvider(): com.hwb.aianswerer.providers.LocalProviderConfig? {
        return try {
            val visionModel = getVisionModelName()
            val userConfigs = com.hwb.aianswerer.providers.ProviderStorage.getEnabledProvidersFromUserConfigs()
            val allProviders = com.hwb.aianswerer.providers.ProviderStorage.getMergedProviders()
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
     * 保存视觉模型 API 地址
     */
    fun saveVisionBaseUrl(url: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_BASE_URL, url)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 API Key（加密存储）
     */
    fun getVisionApiKey(): String {
        val prefs = ConfigStorage.getSecurePrefs()
        val stored = prefs?.getString(ConfigStorage.KEY_VISION_API_KEY, null)
            ?: ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_API_KEY, null)
        val result = stored?.takeIf { it.isNotEmpty() } ?: ""
        if (result.isNotBlank()) return result
        // Fallback to provider system
        return try { com.hwb.aianswerer.providers.ProviderStorage.getEnabledProviders().firstOrNull()?.apiKey ?: "" } catch (_: Exception) { "" }
    }

    /**
     * 保存视觉模型 API Key（加密存储）
     */
    fun saveVisionApiKey(key: String) {
        val prefs = ConfigStorage.getSecurePrefs()
        if (prefs != null) {
            prefs.edit().putString(ConfigStorage.KEY_VISION_API_KEY, key).apply()
        } else {
            ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_API_KEY, key)
        }
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型名称
     */
    fun getVisionModelName(): String {
        val saved = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[getVisionProviderId()]
        return meta?.defaultModel ?: "deepseek-chat"
    }

    /**
     * 保存视觉模型名称
     */
    fun saveVisionModelName(name: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_MODEL_NAME, name)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 Temperature
     */
    fun getVisionTemperature(): Double {
        return ConfigStorage.requireMmkv().decodeFloat(ConfigStorage.KEY_VISION_TEMPERATURE, 0.0f).toDouble()
    }

    /**
     * 保存视觉模型 Temperature
     */
    fun saveVisionTemperature(t: Double) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_TEMPERATURE, t.toFloat())
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 Max Tokens
     */
    fun getVisionMaxTokens(): Int {
        return ConfigStorage.requireMmkv().decodeInt(ConfigStorage.KEY_VISION_MAX_TOKENS, 4096)
    }

    /**
     * 保存视觉模型 Max Tokens
     */
    fun saveVisionMaxTokens(n: Int) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_MAX_TOKENS, n)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 JSON 模式
     */
    fun getVisionJsonMode(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_VISION_JSON_MODE, true)
    }

    /**
     * 保存视觉模型 JSON 模式
     */
    fun saveVisionJsonMode(v: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_JSON_MODE, v)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 一键重置视觉模型配置到当前 Provider 默认值
     */
    fun resetVisionToProviderDefaults() {
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[getVisionProviderId()]
        if (meta != null) {
            saveVisionBaseUrl(meta.defaultBaseUrl)
            saveVisionModelName(meta.defaultModel)
        }
    }
}
