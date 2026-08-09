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
}
