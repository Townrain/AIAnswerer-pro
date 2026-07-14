package com.hwb.aianswerer.config

/**
 * Web search 配置（多供应商通用）
 */
internal object SearchConfig {
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
