package com.hwb.aianswerer.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import com.tencent.mmkv.MMKV

/**
 * 厂商数据持久化管理
 *
 * 存储策略：
 * - 云端同步数据（厂商列表 + 模型）→ MMKV 存 JSON 字符串
 * - 用户 API Key → EncryptedSharedPreferences 加密存储
 * - 用户偏好（enabled/sortOrder）→ MMKV 存 JSON 字符串
 *
 * 同步时只覆盖厂商 metadata，保留用户 apiKey/enabled 状态
 */
object ProviderStorage {

    // MMKV keys
    private const val KEY_PROVIDER_DATA = "provider_data_json"
    private const val KEY_PROVIDER_DATA_VERSION = "provider_data_version"
    private const val KEY_USER_CONFIGS = "provider_user_configs"
    private const val KEY_SYNC_ETAG = "provider_sync_etag"
    private const val KEY_SYNC_LAST_MODIFIED = "provider_sync_last_modified"
    private const val KEY_SYNC_TIMESTAMP = "provider_sync_timestamp"

    private var mmkv: MMKV? = null
    private var securePrefs: SharedPreferences? = null

    fun init(context: Context) {
        mmkv = MMKV.defaultMMKV()
        // Load provider data from assets if not already present
        if (getDataVersion() <= 0) {
            try {
                val json = context.assets.open("provider_data.json").bufferedReader().use { it.readText() }
                val data = com.hwb.aianswerer.utils.JsonUtil.gson.fromJson(json, ProviderDataJson::class.java)
                if (data != null) {
                    saveProviderData(data)
                    AppLog.d("ProviderStorage: Loaded from assets: v${data.version}, ${data.providerCount} providers")
                }
            } catch (e: Exception) {
                AppLog.e("ProviderStorage: Failed to load assets/provider_data.json", e)
            }
        }
    }

    /**
     * 初始化EncryptedSharedPreferences（在onCreate中调用，需要可用的Application context）
     */
    fun initSecurePrefs(context: Context) {
        if (securePrefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            securePrefs = EncryptedSharedPreferences.create(
                context,
                "ai_answerer_provider_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to init EncryptedSharedPreferences", e)
        }
    }

    private fun requireMmkv(): MMKV {
        return mmkv ?: throw IllegalStateException("ProviderStorage.init() must be called first")
    }

    // ── 云端数据读写 ──────────────────────────────────────────────────

    fun saveProviderData(data: ProviderDataJson) {
        val json = JsonUtil.gson.toJson(data)
        requireMmkv().apply {
            encode(KEY_PROVIDER_DATA, json)
            encode(KEY_PROVIDER_DATA_VERSION, data.version)
        }
        AppLog.d("ProviderStorage: Saved provider data: v${data.version}, ${data.providerCount} providers, ${data.modelCount} models")
    }

    fun getProviderData(): ProviderDataJson? {
        val json = requireMmkv().decodeString(KEY_PROVIDER_DATA) ?: return null
        return try {
            JsonUtil.gson.fromJson(json, ProviderDataJson::class.java)
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to parse provider data", e)
            null
        }
    }

    fun getDataVersion(): Int = requireMmkv().decodeInt(KEY_PROVIDER_DATA_VERSION, 0)

    // ── 同步元数据 ────────────────────────────────────────────────────

    fun saveSyncMeta(etag: String?, lastModified: String?) {
        requireMmkv().apply {
            etag?.let { encode(KEY_SYNC_ETAG, it) }
            lastModified?.let { encode(KEY_SYNC_LAST_MODIFIED, it) }
            encode(KEY_SYNC_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun getSyncEtag(): String? = requireMmkv().decodeString(KEY_SYNC_ETAG)
    fun getSyncLastModified(): String? = requireMmkv().decodeString(KEY_SYNC_LAST_MODIFIED)
    fun getSyncTimestamp(): Long = requireMmkv().decodeLong(KEY_SYNC_TIMESTAMP, 0)

    // ── 用户配置（API Key / enabled / 自定义厂商）───────────────────

    fun saveUserApiKey(providerId: String, apiKey: String) {
        val key = "provider_key_$providerId"
        try {
            securePrefs?.edit()?.putString(key, apiKey)?.apply()
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to save API key for $providerId", e)
        }
    }

    fun getUserApiKey(providerId: String): String {
        val key = "provider_key_$providerId"
        return try {
            securePrefs?.getString(key, "") ?: ""
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to read API key for $providerId", e)
            ""
        }
    }

    /**
     * 用户配置 per-provider: enabled 状态 + 自定义 apiHost + 选中的模型列表
     */
    data class UserProviderConfig(
        val enabled: Boolean = false,
        val customApiHost: String? = null,
        val customAnthropicApiHost: String? = null,
        val selectedModels: List<String> = listOf(),
        val availableModels: List<String> = listOf()
    )

    fun saveUserConfig(providerId: String, config: UserProviderConfig) {
        try {
            val configs = loadAllUserConfigs().toMutableMap()
            configs[providerId] = config
            val json = JsonUtil.gson.toJson(configs)
            requireMmkv().encode(KEY_USER_CONFIGS, json)
            AppLog.d("ProviderStorage: Saved user config for $providerId: enabled=${config.enabled}, models=${config.selectedModels.size}")
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to save user config for $providerId", e)
        }
    }

    fun getUserConfig(providerId: String): UserProviderConfig {
        return loadAllUserConfigs()[providerId] ?: UserProviderConfig()
    }

    private fun loadAllUserConfigs(): Map<String, UserProviderConfig> {
        val json = requireMmkv().decodeString(KEY_USER_CONFIGS) ?: return emptyMap()
        return try {
            val type = com.google.gson.reflect.TypeToken
                .getParameterized(Map::class.java, String::class.java, UserProviderConfig::class.java)
                .type
            val configs: Map<String, UserProviderConfig> = JsonUtil.gson.fromJson(json, type) ?: emptyMap()
            // 迁移旧数据：selectedModel (String) → selectedModels (List)
            var migrated = false
            val rawMap: Map<String, Map<String, Any>>? = try {
                val rawType = com.google.gson.reflect.TypeToken
                    .getParameterized(Map::class.java, String::class.java, Object::class.java)
                    .type
                @Suppress("UNCHECKED_CAST")
                JsonUtil.gson.fromJson<Map<String, Any>>(json, rawType) as? Map<String, Map<String, Any>>
            } catch (_: Exception) { null }
            val migratedConfigs = configs.mapValues { (id, config) ->
                if (config.selectedModels.isEmpty()) {
                    val oldModel = rawMap?.get(id)?.get("selectedModel") as? String
                    if (!oldModel.isNullOrBlank()) {
                        migrated = true
                        config.copy(selectedModels = listOf(oldModel))
                    } else config
                } else config
            }
            if (migrated) {
                val newJson = JsonUtil.gson.toJson(migratedConfigs)
                requireMmkv().encode(KEY_USER_CONFIGS, newJson)
            }
            migratedConfigs
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to parse user configs", e)
            emptyMap()
        }
    }

    // ── 合并后的完整列表 ──────────────────────────────────────────────

    /**
     * 获取合并后的厂商列表（云端数据 + 用户配置）
     * 如果没有云端数据，返回空列表（调用方应 fallback 到 assets）
     */
    fun getMergedProviders(): List<LocalProviderConfig> {
        val data = getProviderData() ?: return emptyList()
        return data.providers.map { entry ->
            val userConfig = getUserConfig(entry.id)
            val apiKey = getUserApiKey(entry.id)
            LocalProviderConfig(
                id = entry.id,
                name = entry.name,
                type = entry.type,
                apiHost = userConfig.customApiHost ?: entry.apiHost,
                anthropicApiHost = userConfig.customAnthropicApiHost ?: entry.anthropicApiHost,
                models = entry.models,
                websites = entry.websites,
                apiKey = apiKey,
                enabled = userConfig.enabled,
                isSystem = true,
                dataVersion = data.version,
                selectedModels = userConfig.selectedModels
            )
        }
    }

    /**
     * 获取已启用的厂商列表（需要云端数据）
     */
    fun getEnabledProviders(): List<LocalProviderConfig> {
        return getMergedProviders().filter { it.enabled && it.apiKey.isNotBlank() }
    }

    /**
     * 从用户配置中读取已启用的厂商（不需要云端数据，用于主页菜单回退）
     */
    private val PROVIDER_DISPLAY_NAMES = mapOf(
        "openai" to "OpenAI", "anthropic" to "Anthropic", "deepseek" to "DeepSeek",
        "gemini" to "Google Gemini", "silicon" to "硅基流动", "qwen" to "通义千问",
        "dashscope" to "阿里百炼", "zhipu" to "智谱清言", "moonshot" to "Kimi",
        "mimo" to "MiMo", "openrouter" to "OpenRouter",
        "ollama" to "Ollama (本地)", "azure-openai" to "Azure OpenAI", "custom" to "自定义"
    )

    fun getEnabledProvidersFromUserConfigs(): List<LocalProviderConfig> {
        val allConfigs = loadAllUserConfigs()
        return allConfigs.mapNotNull { (id, config) ->
            if (!config.enabled) return@mapNotNull null
            val apiKey = getUserApiKey(id)
            // 尝试从云端数据获取显示名称，fallback 到硬编码映射
            val mergedName = getMergedProviders().find { it.id == id }?.name
                ?: PROVIDER_DISPLAY_NAMES[id] ?: id
            LocalProviderConfig(
                id = id, name = mergedName, type = "",
                apiHost = config.customApiHost ?: "", anthropicApiHost = null,
                models = listOf(), websites = null,
                apiKey = apiKey, enabled = true, selectedModels = config.selectedModels
            )
        }
    }

    /**
     * 轻量级检查：是否有任何已启用且配置了 API Key 的厂商
     * 不依赖云端数据，用于 AppConfig.isApiConfigValid() 快速判断
     */
    fun isAnyProviderConfigured(): Boolean {
        val allConfigs = loadAllUserConfigs()
        android.util.Log.d("ProviderStorage", "isAnyProviderConfigured: ${allConfigs.size} configs found, ids=${allConfigs.keys}")
        for ((id, config) in allConfigs) {
            val apiKey = getUserApiKey(id)
            android.util.Log.d("ProviderStorage", "  $id: enabled=${config.enabled}, apiKey=${apiKey.take(8)}...")
            if (apiKey.isNotBlank()) return true
        }
        return false
    }

    /**
     * 根据 ID 查找厂商配置
     */
    fun getProvider(id: String): LocalProviderConfig? {
        return getMergedProviders().find { it.id == id }
    }

    /**
     * 清除所有数据
     */
    fun clearAll() {
        requireMmkv().apply {
            remove(KEY_PROVIDER_DATA)
            remove(KEY_PROVIDER_DATA_VERSION)
            remove(KEY_USER_CONFIGS)
            remove(KEY_SYNC_ETAG)
            remove(KEY_SYNC_LAST_MODIFIED)
            remove(KEY_SYNC_TIMESTAMP)
        }
        try {
            securePrefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            AppLog.e("ProviderStorage: Failed to clear secure prefs", e)
        }
    }
}
