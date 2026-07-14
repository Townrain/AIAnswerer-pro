package com.hwb.aianswerer.providers

import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import com.tencent.mmkv.MMKV

/**
 * 联网搜索服务商配置持久化
 *
 * 存储策略：
 * - 用户配置（enabled/apiKey/customApiHost/basicAuth）→ MMKV 存 JSON
 * - 全局搜索设置（regexFilter 等）→ 复用 AppConfig
 */
object WebSearchStorage {

    private const val KEY_USER_CONFIGS = "web_search_user_configs"
    private const val KEY_SEARCH_ENABLED = "web_search_enabled"

    private fun mmkv(): MMKV = MMKV.defaultMMKV()

    /**
     * 用户配置 per-provider
     */
    data class UserWebSearchConfig(
        val enabled: Boolean = false,
        val apiKey: String = "",
        val customApiHost: String? = null,
        val basicAuthUsername: String = "",
        val basicAuthPassword: String = ""
    )

    fun saveUserConfig(providerId: String, config: UserWebSearchConfig) {
        val configs = loadAllConfigs().toMutableMap()
        configs[providerId] = config
        val json = JsonUtil.gson.toJson(configs)
        android.util.Log.d("WebSearchStorage", "saveUserConfig: providerId=$providerId, json=$json")
        mmkv().encode(KEY_USER_CONFIGS, json)
        // 回读验证
        val verify = mmkv().decodeString(KEY_USER_CONFIGS)
        android.util.Log.d("WebSearchStorage", "verify readback: $verify")
    }

    fun getUserConfig(providerId: String): UserWebSearchConfig {
        return loadAllConfigs()[providerId] ?: UserWebSearchConfig()
    }

    private fun loadAllConfigs(): Map<String, UserWebSearchConfig> {
        val json = mmkv().decodeString(KEY_USER_CONFIGS)
        android.util.Log.d("WebSearchStorage", "loadAllConfigs: raw=$json")
        if (json == null) return emptyMap()
        return try {
            val type = com.google.gson.reflect.TypeToken
                .getParameterized(Map::class.java, String::class.java, UserWebSearchConfig::class.java)
                .type
            val result = JsonUtil.gson.fromJson<Map<String, UserWebSearchConfig>>(json, type)
            android.util.Log.d("WebSearchStorage", "loadAllConfigs: parsed=$result")
            result ?: emptyMap()
        } catch (e: Exception) {
            android.util.Log.e("WebSearchStorage", "Failed to parse configs: $json", e)
            emptyMap()
        }
    }

    /**
     * 联网搜索全局开关
     */
    fun isSearchEnabled(): Boolean {
        return mmkv().decodeBool(KEY_SEARCH_ENABLED, false)
    }

    fun saveSearchEnabled(enabled: Boolean) {
        mmkv().encode(KEY_SEARCH_ENABLED, enabled)
    }

    /**
     * 获取合并后的服务商列表（内置数据 + 用户配置）
     */
    fun getMergedProviders(): List<LocalWebSearchConfig> {
        return WebSearchProviders.PROVIDERS.map { entry ->
            val userConfig = getUserConfig(entry.id)
            LocalWebSearchConfig(
                id = entry.id,
                name = entry.name,
                apiHost = userConfig.customApiHost ?: entry.apiHost,
                url = entry.url,
                requiresApiKey = entry.requiresApiKey,
                requiresHost = entry.requiresHost,
                supportsBasicAuth = entry.supportsBasicAuth,
                websites = entry.websites,
                apiKey = userConfig.apiKey,
                enabled = userConfig.enabled,
                customApiHost = userConfig.customApiHost,
                basicAuthUsername = userConfig.basicAuthUsername,
                basicAuthPassword = userConfig.basicAuthPassword
            )
        }
    }

    /**
     * 获取已启用的服务商（需要 API key 的也包含在内）
     */
    fun getEnabledProviders(): List<LocalWebSearchConfig> {
        return getMergedProviders().filter { it.enabled }
    }
}
