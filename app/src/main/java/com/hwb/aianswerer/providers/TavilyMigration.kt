package com.hwb.aianswerer.providers

import com.tencent.mmkv.MMKV

object TavilyMigration {
    fun run() {
        try {
            val mmkv = MMKV.defaultMMKV()
            if (mmkv.decodeBool("migrated_tavily_to_websearch", false)) return

            val oldApiKey = mmkv.decodeString("tavily_api_key", null)
            val oldEnabled = mmkv.decodeBool("tavily_enabled", false)

            if (oldEnabled || !oldApiKey.isNullOrBlank()) {
                val existingConfig = WebSearchStorage.getUserConfig("tavily")
                if (!existingConfig.enabled && existingConfig.apiKey.isBlank()) {
                    WebSearchStorage.saveUserConfig("tavily", WebSearchStorage.UserWebSearchConfig(
                        enabled = oldEnabled,
                        apiKey = oldApiKey ?: ""
                    ))
                    WebSearchStorage.saveSearchEnabled(oldEnabled)
                }
            }
            mmkv.encode("migrated_tavily_to_websearch", true)
        } catch (e: Exception) {
            android.util.Log.w("TavilyMigration", "Migration failed", e)
        }
    }
}
