package com.hwb.aianswerer.config

import com.hwb.aianswerer.providers.ProviderConfigResolver
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
    }

    /**
     * 获取视觉模型 API 地址
     */
    fun getVisionBaseUrl(): String {
        val saved = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_BASE_URL, null)
        if (!saved.isNullOrBlank()) return saved
        // Fallback to provider system — resolve by vision model name
        return ProviderConfigResolver.resolveVisionBaseUrl()
    }

    /**
     * 保存视觉模型 API 地址
     */
    fun saveVisionBaseUrl(url: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_BASE_URL, url)
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
        return ProviderConfigResolver.resolveVisionApiKey()
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
    }

    /**
     * 获取视觉模型名称
     */
    fun getVisionModelName(): String {
        val saved = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        // Fallback to provider default metadata
        return ProviderConfigResolver.resolveVisionModelName()
    }

    /**
     * 保存视觉模型名称
     */
    fun saveVisionModelName(name: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_VISION_MODEL_NAME, name)
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
    }

    /**
     * 一键重置视觉模型配置到当前 Provider 默认值
     */
    fun resetVisionToProviderDefaults() {
        saveVisionBaseUrl(ProviderConfigResolver.resolveVisionDefaultBaseUrl())
        saveVisionModelName(ProviderConfigResolver.resolveVisionDefaultModel())
    }
}
