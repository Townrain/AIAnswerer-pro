package com.hwb.aianswerer.config

/**
 * Web search / Tavily 配置
 */
internal object SearchConfig {

    // ========== Tavily 联网搜索相关 ==========

    /**
     * 保存 Tavily API Key（加密存储，降级时使用MMKV）
     */
    fun saveTavilyApiKey(key: String) {
        val prefs = ConfigStorage.getSecurePrefs()
        if (prefs != null) {
            prefs.edit().putString(ConfigStorage.KEY_TAVILY_API_KEY, key).apply()
        } else {
            ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_TAVILY_API_KEY, key)
        }
    }

    /**
     * 获取 Tavily API Key（优先从加密存储读取，降级时从MMKV读取）
     */
    fun getTavilyApiKey(): String {
        val prefs = ConfigStorage.getSecurePrefs()
        val stored = prefs?.getString(ConfigStorage.KEY_TAVILY_API_KEY, null)
            ?: ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_TAVILY_API_KEY, null)
        return stored?.takeIf { it.isNotEmpty() } ?: ""
    }

    /**
     * 保存 Tavily 启用状态
     */
    fun saveTavilyEnabled(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_TAVILY_ENABLED, enabled)
    }

    /**
     * 获取 Tavily 启用状态，默认为 false
     */
    fun getTavilyEnabled(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_TAVILY_ENABLED, false)
    }

    /**
     * 验证 Tavily 配置是否完整且启用
     */
    fun isTavilyConfigValid(): Boolean {
        return getTavilyEnabled() && getTavilyApiKey().isNotBlank()
    }

    // ── Web search / Output language ──

    fun saveWebSearchProvider(name: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_WEB_SEARCH_PROVIDER, name)
    }

    fun getWebSearchProvider(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_WEB_SEARCH_PROVIDER, "") ?: ""
    }

    // ========== 正则过滤相关 ==========

    /**
     * 获取正则过滤是否启用
     * @return true表示启用正则过滤（检测到多题时跳过搜索），默认为true
     */
    fun isRegexFilterEnabled(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_REGEX_FILTER_ENABLED, true)
    }

    /**
     * 保存正则过滤启用状态
     */
    fun saveRegexFilterEnabled(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_REGEX_FILTER_ENABLED, enabled)
    }
}
