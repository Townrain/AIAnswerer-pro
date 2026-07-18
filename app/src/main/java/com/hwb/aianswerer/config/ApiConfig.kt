package com.hwb.aianswerer.config

import com.hwb.aianswerer.BuildConfig
import com.hwb.aianswerer.providers.ProviderConfigResolver
/**
 * API URL / Key / Model 配置
 */
internal object ApiConfig {

    // ========== API配置相关 ==========

    /**
     * 保存API URL
     */
    fun saveApiUrl(url: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_API_URL, url)
    }

    /**
     * 获取API URL
     * @return API URL，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getApiUrl(): String {
        val stored = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_API_URL, BuildConfig.API_URL) ?: ""
        // If user hasn't explicitly set a custom URL, try provider system
        if (stored == BuildConfig.API_URL || stored.isBlank()) {
            return ProviderConfigResolver.resolveApiUrl() ?: stored
        }
        return stored
    }


    /**
     * 保存API Key（加密存储，降级时使用MMKV）
     */
    fun saveApiKey(key: String) {
        val prefs = ConfigStorage.getSecurePrefs()
        if (prefs != null) {
            prefs.edit().putString(ConfigStorage.KEY_API_KEY, key).apply()
        } else {
            // 降级到 MMKV
            ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_API_KEY, key)
        }
    }

    /**
     * 获取API Key（优先从加密存储读取，降级时从MMKV读取）
     * @return API Key，如果未配置则返回空字符串
     */
    fun getApiKey(): String {
        val prefs = ConfigStorage.getSecurePrefs()
        val stored = prefs?.getString(ConfigStorage.KEY_API_KEY, null)
            ?: ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_API_KEY, null)
        val result = stored?.takeIf { it.isNotEmpty() } ?: ""
        // If no API key set in old config, try provider system
        if (result.isBlank()) {
            return ProviderConfigResolver.resolveApiKey()
        }
        return result
    }

    /**
     * 保存模型名称
     */
    fun saveModelName(model: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_MODEL_NAME, model)
    }

    /**
     * 获取模型名称
     * @return 模型名称，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getModelName(): String {
        val stored = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_MODEL_NAME, BuildConfig.API_MODEL) ?: ""
        // If user hasn't explicitly set a model, try provider system
        if (stored == BuildConfig.API_MODEL || stored.isBlank()) {
            return ProviderConfigResolver.resolveModelName().ifEmpty { stored }
        }
        return stored
    }

    /**
     * 验证API配置是否完整
     * @return true表示配置完整，false表示缺少必要配置
     */
    fun isApiConfigValid(
        url: String = getApiUrl(),
        key: String = getApiKey(),
        model: String = getModelName()
    ): Boolean {
        // 旧系统：检查 AppConfig 里的 api_url / api_key / model_name
        if (url.isNotBlank() && key.isNotBlank() && model.isNotBlank() && url.startsWith("http")) return true
        // 新系统：直接检查 ProviderStorage 里的用户配置
        return try {
            val result = ProviderConfigResolver.isAnyProviderConfigured()
            android.util.Log.d("AppConfig", "isApiConfigValid: ProviderStorage check = $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("AppConfig", "isApiConfigValid: ProviderStorage check failed", e)
            false
        }
    }

    // ========== 并发答题设置相关 ==========

    /**
     * 获取并发模式是否启用
     * @return true表示启用并发模式，false表示使用串行模式，默认为false
     */
    fun isParallelModeEnabled(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_PARALLEL_MODE, true)
    }

    /**
     * 保存并发模式启用状态
     * @param enabled 是否启用并发模式
     */
    fun saveParallelMode(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_PARALLEL_MODE, enabled)
    }

    /**
     * 获取最大并发数
     * @return 最大并发数，默认为10
     */
    fun getMaxConcurrency(): Int {
        return ConfigStorage.requireMmkv().decodeInt(ConfigStorage.KEY_MAX_CONCURRENCY, 10)
    }

    /**
     * 保存最大并发数
     * @param count 最大并发数，会被限制在1-10范围内
     */
    fun saveMaxConcurrency(count: Int) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_MAX_CONCURRENCY, count.coerceIn(1, 50))
    }

    // ========== LLM Temperature 相关 ==========

    /**
     * 获取LLM Temperature
     * @return Temperature值，默认为0.3（适合答题场景）
     */
    fun getLlmTemperature(): Double {
        return ConfigStorage.requireMmkv().decodeFloat(ConfigStorage.KEY_LLM_TEMPERATURE, 0.3f).toDouble()
    }

    /**
     * 保存LLM Temperature
     * @param temperature Temperature值，会被限制在0.0-2.0范围内
     */
    fun saveLlmTemperature(temperature: Double) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_LLM_TEMPERATURE, temperature.coerceIn(0.0, 2.0).toFloat())
    }

    // ========== 思考模式相关 ==========

    /**
     * 获取思考模式的reasoning_effort值
     * @return "medium" 当启用，null 当禁用
     */
    fun getReasoningEffort(): String? {
        return if (ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_REASONING_EFFORT, false)) "medium" else null
    }

    /**
     * 保存思考模式启用状态
     */
    fun saveReasoningEffort(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_REASONING_EFFORT, enabled)
    }

    // ========== 自定义提示词 ==========

    fun saveCustomSystemPrompt(text: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_CUSTOM_SYSTEM_PROMPT, text)
    }

    fun getCustomSystemPrompt(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_CUSTOM_SYSTEM_PROMPT, "") ?: ""
    }

    fun saveCustomVLMPrompt(text: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_CUSTOM_VLM_PROMPT, text)
    }

    fun getCustomVLMPrompt(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_CUSTOM_VLM_PROMPT, "") ?: ""
    }
}
