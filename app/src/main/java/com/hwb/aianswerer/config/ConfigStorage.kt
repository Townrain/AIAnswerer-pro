package com.hwb.aianswerer.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tencent.mmkv.MMKV

/**
 * Shared MMKV + EncryptedSharedPreferences instances used by all domain configs.
 */
internal object ConfigStorage {

    // MMKV存储键名
    internal const val KEY_API_URL = "api_url"
    internal const val KEY_API_KEY = "api_key"
    internal const val KEY_MODEL_NAME = "model_name"
    internal const val KEY_LANGUAGE = "language"
    internal const val KEY_AUTO_SUBMIT = "auto_submit"
    internal const val KEY_AUTO_COPY = "auto_copy"
    internal const val KEY_QUESTION_TYPES = "question_types"
    internal const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    internal const val KEY_CROP_MODE = "crop_mode"
    internal const val KEY_SHOW_ANSWER_CARD_QUESTION = "show_answer_card_question"
    internal const val KEY_SHOW_ANSWER_CARD_OPTIONS = "show_answer_card_options"
    internal const val KEY_FLOAT_BUTTON_SIZE = "float_button_size"
    internal const val KEY_FLOAT_BUTTON_ALPHA = "float_button_alpha"
    internal const val KEY_FLOAT_CARD_ALPHA = "float_card_alpha"
    internal const val KEY_VISION_ENABLED = "vision_enabled"
    internal const val KEY_VISION_PROVIDER_ID = "vision_provider_id"
    internal const val KEY_VISION_BASE_URL = "vision_base_url"
    internal const val KEY_VISION_API_KEY = "vision_api_key"
    internal const val KEY_VISION_MODEL_NAME = "vision_model_name"
    internal const val KEY_VISION_TEMPERATURE = "vision_temperature"
    internal const val KEY_VISION_MAX_TOKENS = "vision_max_tokens"
    internal const val KEY_VISION_JSON_MODE = "vision_json_mode"
    internal const val KEY_DARK_MODE = "dark_mode"
    internal const val KEY_PARALLEL_MODE = "parallel_mode"
    internal const val KEY_MAX_CONCURRENCY = "max_concurrency"
    internal const val KEY_LLM_TEMPERATURE = "llm_temperature"
    internal const val KEY_REGEX_FILTER_ENABLED = "regex_filter_enabled"
    internal const val KEY_REASONING_EFFORT = "reasoning_effort"
    internal const val KEY_CAPTURE_MODE = "capture_mode"
    internal const val KEY_STEALTH_MODE = "stealth_mode"
    internal const val KEY_WEB_SEARCH_PROVIDER = "web_search_provider"
    internal const val KEY_OUTPUT_LANGUAGE = "output_language"
    internal const val KEY_QUICK_BUTTON_LAYOUT = "quick_button_layout"
    internal const val KEY_DYNAMIC_VISION_MODELS = "dynamic_vision_models"
    internal const val KEY_DYNAMIC_VISION_EXCLUDED = "dynamic_vision_excluded"
    internal const val KEY_DYNAMIC_PROVIDER_MODELS = "dynamic_provider_models"
    internal const val KEY_DYNAMIC_PROVIDER_CONFIGS = "dynamic_provider_configs"
    internal const val KEY_THEME_PRESET = "theme_preset"
    internal const val KEY_CUSTOM_THEMES = "custom_themes"

    // 语言代码常量
    internal const val LANGUAGE_ZH = "zh"
    internal const val LANGUAGE_EN = "en"

    // 截图识别模式常量
    internal const val CROP_MODE_FULL = "full"           // 全屏
    internal const val CROP_MODE_EACH = "each"           // 部分识别（每次）
    internal const val CROP_MODE_ONCE = "once"           // 部分识别（单次）

    // 采集模式常量
    internal const val CAPTURE_MODE_SCREENSHOT = "screenshot"  // 截图 + OCR/VLM
    internal const val CAPTURE_MODE_ACCESSIBILITY = "accessibility"  // 无障碍读取屏幕

    // 快捷按钮布局模式常量
    internal const val QUICK_BUTTON_LAYOUT_ARC = "arc"           // 半圆弧形排列
    internal const val QUICK_BUTTON_LAYOUT_HORIZONTAL = "horizontal"  // 横向排列

    private var mmkv: MMKV? = null
    private var securePrefs: SharedPreferences? = null

    /**
     * 安全获取MMKV实例，未初始化时抛出明确异常
     */
    internal fun requireMmkv(): MMKV {
        return mmkv ?: throw IllegalStateException("AppConfig.init() must be called before accessing MMKV")
    }

    internal fun getSecurePrefs(): SharedPreferences? = securePrefs

    /**
     * 初始化MMKV（在attachBaseContext中调用）
     */
    fun init(context: Context) {
        MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()
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
                "ai_answerer_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // 迁移旧的明文API Key到加密存储
            migrateApiKeyIfNeeded()
        } catch (e: Exception) {
            securePrefs = null
            android.util.Log.w("AppConfig", "EncryptedSharedPreferences初始化失败，API密钥将以明文形式存储在MMKV中", e)
        }
    }

    /**
     * 将旧的明文API Key迁移到加密存储
     */
    private fun migrateApiKeyIfNeeded() {
        val prefs = securePrefs ?: return
        val mmkvKey = requireMmkv().decodeString(KEY_API_KEY, null)
        if (!mmkvKey.isNullOrEmpty() && prefs.getString(KEY_API_KEY, null).isNullOrEmpty()) {
            // 旧的明文Key存在且加密存储中没有，执行迁移
            prefs.edit().putString(KEY_API_KEY, mmkvKey).apply()
            // 删除旧的明文Key
            requireMmkv().removeValueForKey(KEY_API_KEY)
        }
    }
}
