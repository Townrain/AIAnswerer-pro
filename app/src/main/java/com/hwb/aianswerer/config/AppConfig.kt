package com.hwb.aianswerer.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hwb.aianswerer.BuildConfig
import com.hwb.aianswerer.api.vision.VisionProviderFactory
import com.tencent.mmkv.MMKV

/**
 * 应用配置管理类
 * 负责保存和读取用户的API配置、语言设置等
 * 使用MMKV作为底层存储，提供高性能的key-value数据持久化
 * API Key使用EncryptedSharedPreferences加密存储
 */
object AppConfig {

    // MMKV存储键名
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_NAME = "model_name"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_AUTO_SUBMIT = "auto_submit"
    private const val KEY_AUTO_COPY = "auto_copy"
    private const val KEY_QUESTION_TYPES = "question_types"
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_CROP_MODE = "crop_mode"
    private const val KEY_SHOW_ANSWER_CARD_QUESTION = "show_answer_card_question"
    private const val KEY_SHOW_ANSWER_CARD_OPTIONS = "show_answer_card_options"
    private const val KEY_TAVILY_API_KEY = "tavily_api_key"
    private const val KEY_TAVILY_ENABLED = "tavily_enabled"
    private const val KEY_FLOAT_BUTTON_SIZE = "float_button_size"
    private const val KEY_FLOAT_BUTTON_ALPHA = "float_button_alpha"
    private const val KEY_FLOAT_CARD_ALPHA = "float_card_alpha"
    private const val KEY_VISION_ENABLED = "vision_enabled"
    private const val KEY_VISION_PROVIDER_ID = "vision_provider_id"
    private const val KEY_VISION_BASE_URL = "vision_base_url"
    private const val KEY_VISION_API_KEY = "vision_api_key"
    private const val KEY_VISION_MODEL_NAME = "vision_model_name"
    private const val KEY_VISION_TEMPERATURE = "vision_temperature"
    private const val KEY_VISION_MAX_TOKENS = "vision_max_tokens"
    private const val KEY_VISION_JSON_MODE = "vision_json_mode"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_PARALLEL_MODE = "parallel_mode"
    private const val KEY_MAX_CONCURRENCY = "max_concurrency"
    private const val KEY_LLM_TEMPERATURE = "llm_temperature"
    private const val KEY_REGEX_FILTER_ENABLED = "regex_filter_enabled"
    private const val KEY_REASONING_EFFORT = "reasoning_effort"
    private const val KEY_CAPTURE_MODE = "capture_mode"
    private const val KEY_STEALTH_MODE = "stealth_mode"

    // 语言代码常量
    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"

    // 截图识别模式常量
    const val CROP_MODE_FULL = "full"           // 全屏
    const val CROP_MODE_EACH = "each"           // 部分识别（每次）
    const val CROP_MODE_ONCE = "once"           // 部分识别（单次）

    // 采集模式常量
    const val CAPTURE_MODE_SCREENSHOT = "screenshot"  // 截图 + OCR/VLM
    const val CAPTURE_MODE_ACCESSIBILITY = "accessibility"  // 无障碍读取屏幕

    private lateinit var mmkv: MMKV
    private var securePrefs: SharedPreferences? = null

    /**
     * 初始化MMKV和EncryptedSharedPreferences
     * 应该在Application.attachBaseContext()中调用
     */
    fun init(context: Context) {
        MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()

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
            // EncryptedSharedPreferences 初始化失败时降级到 MMKV
            // 常见原因：Android Keystore 不可用、模拟器兼容性问题
            securePrefs = null
        }
    }

    /**
     * 将旧的明文API Key迁移到加密存储
     */
    private fun migrateApiKeyIfNeeded() {
        val prefs = securePrefs ?: return
        val mmkvKey = mmkv.decodeString(KEY_API_KEY, null)
        if (!mmkvKey.isNullOrEmpty() && prefs.getString(KEY_API_KEY, null).isNullOrEmpty()) {
            // 旧的明文Key存在且加密存储中没有，执行迁移
            prefs.edit().putString(KEY_API_KEY, mmkvKey).apply()
            // 删除旧的明文Key
            mmkv.removeValueForKey(KEY_API_KEY)
        }
    }

    // ========== API配置相关 ==========

    /**
     * 保存API URL
     */
    fun saveApiUrl(url: String) {
        mmkv.encode(KEY_API_URL, url)
    }

    /**
     * 获取API URL
     * @return API URL，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getApiUrl(): String {
        return mmkv.decodeString(KEY_API_URL, BuildConfig.API_URL) ?: ""
    }

    /**
     * 保存API Key（加密存储，降级时使用MMKV）
     */
    fun saveApiKey(key: String) {
        val prefs = securePrefs
        if (prefs != null) {
            prefs.edit().putString(KEY_API_KEY, key).apply()
        } else {
            // 降级到 MMKV
            mmkv.encode(KEY_API_KEY, key)
        }
    }

    /**
     * 获取API Key（优先从加密存储读取，降级时从MMKV读取）
     * @return API Key，优先返回BuildConfig配置，其次返回用户设置值，最后返回空值
     */
    fun getApiKey(): String {
        val prefs = securePrefs
        val stored = prefs?.getString(KEY_API_KEY, null)
            ?: mmkv.decodeString(KEY_API_KEY, null)
        return stored?.takeIf { it.isNotEmpty() } ?: BuildConfig.API_KEY
    }

    /**
     * 保存模型名称
     */
    fun saveModelName(model: String) {
        mmkv.encode(KEY_MODEL_NAME, model)
    }

    /**
     * 获取模型名称
     * @return 模型名称，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getModelName(): String {
        return mmkv.decodeString(KEY_MODEL_NAME, BuildConfig.API_MODEL) ?: ""
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

        return url.isNotBlank() && key.isNotBlank() && model.isNotBlank() && url.startsWith("http")
    }

    // ========== 语言设置相关 ==========

    /**
     * 保存语言设置
     * @param languageCode 语言代码 (zh/en)
     */
    fun saveLanguage(languageCode: String) {
        mmkv.encode(KEY_LANGUAGE, languageCode)
    }

    /**
     * 获取当前设置的语言
     * @return 语言代码，默认为中文
     */
    fun getLanguage(): String {
        return mmkv.decodeString(KEY_LANGUAGE, LANGUAGE_ZH) ?: LANGUAGE_ZH
    }

    // ========== 应用设置相关 ==========

    /**
     * 保存自动提交设置
     * @param enabled 是否启用自动提交（识别后直接获取答案，不显示确认对话框）
     */
    fun saveAutoSubmit(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_SUBMIT, enabled)
    }

    /**
     * 获取自动提交设置
     * @return 是否启用自动提交，默认为true
     */
    fun getAutoSubmit(): Boolean {
        return mmkv.decodeBool(KEY_AUTO_SUBMIT, true)
    }

    /**
     * 保存自动复制到剪贴板设置
     * @param enabled 是否启用自动复制（生成答案后自动复制到剪贴板）
     */
    fun saveAutoCopy(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_COPY, enabled)
    }

    /**
     * 获取自动复制到剪贴板设置
     * @return 是否启用自动复制，默认为false
     */
    fun getAutoCopy(): Boolean {
        return mmkv.decodeBool(KEY_AUTO_COPY, false)
    }

    // ========== 暗色模式相关 ==========

    /**
     * 保存暗色模式设置
     * @param mode 暗色模式：0=跟随系统, 1=亮色, 2=暗色
     */
    fun saveDarkMode(mode: Int) {
        mmkv.encode(KEY_DARK_MODE, mode)
    }

    /**
     * 获取暗色模式设置
     * @return 暗色模式：0=跟随系统, 1=亮色, 2=暗色，默认为0
     */
    fun getDarkMode(): Int {
        return mmkv.decodeInt(KEY_DARK_MODE, 0)
    }

    // ========== 答题设置相关 ==========

    /**
     * 保存题型设置
     * @param types 题型集合（如：单选题、多选题等）
     */
    fun saveQuestionTypes(types: Set<String>) {
        val typesString = types.joinToString(",")
        mmkv.encode(KEY_QUESTION_TYPES, typesString)
    }

    /**
     * 获取题型设置
     * @return 题型集合，默认为单选题
     */
    fun getQuestionTypes(): Set<String> {
        val typesString = mmkv.decodeString(KEY_QUESTION_TYPES, "单选题") ?: "单选题"
        return if (typesString.isBlank()) {
            setOf("单选题")
        } else {
            typesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    // ========== 答题卡片显示控制相关 ==========
    /**
     * 保存答题卡片是否显示题目设置
     * @param show 是否显示题目
     */
    fun saveShowAnswerCardQuestion(show: Boolean) {
        mmkv.encode(KEY_SHOW_ANSWER_CARD_QUESTION, show)
    }

    /**
     * 获取答题卡片是否显示题目设置
     * @return 是否显示题目，默认为true
     */
    fun getShowAnswerCardQuestion(): Boolean {
        return mmkv.decodeBool(KEY_SHOW_ANSWER_CARD_QUESTION, true)
    }

    /**
     * 保存答题卡片是否显示选项设置
     * @param show 是否显示选项
     */
    fun saveShowAnswerCardOptions(show: Boolean) {
        mmkv.encode(KEY_SHOW_ANSWER_CARD_OPTIONS, show)
    }

    /**
     * 获取答题卡片是否显示选项设置
     * @return 是否显示选项，默认为true
     */
    fun getShowAnswerCardOptions(): Boolean {
        return mmkv.decodeBool(KEY_SHOW_ANSWER_CARD_OPTIONS, true)
    }

    // ========== 截图识别模式相关 ==========

    /**
     * 保存截图识别模式
     * @param mode 识别模式（CROP_MODE_FULL/CROP_MODE_EACH/CROP_MODE_ONCE）
     */
    fun saveCropMode(mode: String) {
        mmkv.encode(KEY_CROP_MODE, mode)
    }

    /**
     * 获取截图识别模式
     * @return 识别模式，默认为全屏模式
     */
    fun getCropMode(): String {
        return mmkv.decodeString(KEY_CROP_MODE, CROP_MODE_FULL) ?: CROP_MODE_FULL
    }

    // ========== 采集模式相关 ==========

    /**
     * 保存采集模式
     * @param mode CAPTURE_MODE_SCREENSHOT 或 CAPTURE_MODE_ACCESSIBILITY
     */
    fun saveCaptureMode(mode: String) {
        mmkv.encode(KEY_CAPTURE_MODE, mode)
    }

    /**
     * 获取采集模式
     * @return 采集模式，默认为截图模式
     */
    fun getCaptureMode(): String {
        return mmkv.decodeString(KEY_CAPTURE_MODE, CAPTURE_MODE_SCREENSHOT) ?: CAPTURE_MODE_SCREENSHOT
    }

    /**
     * 是否使用无障碍模式采集
     */
    fun isAccessibilityCaptureMode(): Boolean {
        return getCaptureMode() == CAPTURE_MODE_ACCESSIBILITY
    }

    // ========== 首次启动相关 ==========

    // ========== Tavily 联网搜索相关 ==========

    /**
     * 保存 Tavily API Key（加密存储，降级时使用MMKV）
     */
    fun saveTavilyApiKey(key: String) {
        val prefs = securePrefs
        if (prefs != null) {
            prefs.edit().putString(KEY_TAVILY_API_KEY, key).apply()
        } else {
            mmkv.encode(KEY_TAVILY_API_KEY, key)
        }
    }

    /**
     * 获取 Tavily API Key（优先从加密存储读取，降级时从MMKV读取）
     */
    fun getTavilyApiKey(): String {
        val prefs = securePrefs
        val stored = prefs?.getString(KEY_TAVILY_API_KEY, null)
            ?: mmkv.decodeString(KEY_TAVILY_API_KEY, null)
        return stored?.takeIf { it.isNotEmpty() } ?: ""
    }

    /**
     * 保存 Tavily 启用状态
     */
    fun saveTavilyEnabled(enabled: Boolean) {
        mmkv.encode(KEY_TAVILY_ENABLED, enabled)
    }

    /**
     * 获取 Tavily 启用状态，默认为 false
     */
    fun getTavilyEnabled(): Boolean {
        return mmkv.decodeBool(KEY_TAVILY_ENABLED, false)
    }

    /**
     * 验证 Tavily 配置是否完整且启用
     */
    fun isTavilyConfigValid(): Boolean {
        return getTavilyEnabled() && getTavilyApiKey().isNotBlank()
    }

    // ========== 视觉模型统一配置 ==========

    /**
     * 是否启用视觉过滤
     */
    fun isVisionEnabled(): Boolean {
        return mmkv.decodeBool(KEY_VISION_ENABLED, false)
    }

    /**
     * 保存视觉过滤启用状态
     */
    fun saveVisionEnabled(enabled: Boolean) {
        mmkv.encode(KEY_VISION_ENABLED, enabled)
    }

    /**
     * 获取视觉模型 Provider ID
     */
    fun getVisionProviderId(): String {
        return mmkv.decodeString(KEY_VISION_PROVIDER_ID, "openai_compat") ?: "openai_compat"
    }

    /**
     * 保存视觉模型 Provider ID
     */
    fun saveVisionProviderId(id: String) {
        mmkv.encode(KEY_VISION_PROVIDER_ID, id)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 API 地址
     */
    fun getVisionBaseUrl(): String {
        val saved = mmkv.decodeString(KEY_VISION_BASE_URL, null)
        if (!saved.isNullOrBlank()) return saved
        // 根据 provider 返回默认值
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[getVisionProviderId()]
        return meta?.defaultBaseUrl ?: "https://api.deepseek.com/v1/chat/completions"
    }

    /**
     * 保存视觉模型 API 地址
     */
    fun saveVisionBaseUrl(url: String) {
        mmkv.encode(KEY_VISION_BASE_URL, url)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 API Key（加密存储）
     */
    fun getVisionApiKey(): String {
        val prefs = securePrefs
        val stored = prefs?.getString(KEY_VISION_API_KEY, null)
            ?: mmkv.decodeString(KEY_VISION_API_KEY, null)
        return stored?.takeIf { it.isNotEmpty() } ?: ""
    }

    /**
     * 保存视觉模型 API Key（加密存储）
     */
    fun saveVisionApiKey(key: String) {
        val prefs = securePrefs
        if (prefs != null) {
            prefs.edit().putString(KEY_VISION_API_KEY, key).apply()
        } else {
            mmkv.encode(KEY_VISION_API_KEY, key)
        }
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型名称
     */
    fun getVisionModelName(): String {
        val saved = mmkv.decodeString(KEY_VISION_MODEL_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS[getVisionProviderId()]
        return meta?.defaultModel ?: "deepseek-chat"
    }

    /**
     * 保存视觉模型名称
     */
    fun saveVisionModelName(name: String) {
        mmkv.encode(KEY_VISION_MODEL_NAME, name)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 Temperature
     */
    fun getVisionTemperature(): Double {
        return mmkv.decodeFloat(KEY_VISION_TEMPERATURE, 0.0f).toDouble()
    }

    /**
     * 保存视觉模型 Temperature
     */
    fun saveVisionTemperature(t: Double) {
        mmkv.encode(KEY_VISION_TEMPERATURE, t.toFloat())
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 Max Tokens
     */
    fun getVisionMaxTokens(): Int {
        return mmkv.decodeInt(KEY_VISION_MAX_TOKENS, 4096)
    }

    /**
     * 保存视觉模型 Max Tokens
     */
    fun saveVisionMaxTokens(n: Int) {
        mmkv.encode(KEY_VISION_MAX_TOKENS, n)
        VisionProviderFactory.invalidateCache()
    }

    /**
     * 获取视觉模型 JSON 模式
     */
    fun getVisionJsonMode(): Boolean {
        return mmkv.decodeBool(KEY_VISION_JSON_MODE, true)
    }

    /**
     * 保存视觉模型 JSON 模式
     */
    fun saveVisionJsonMode(v: Boolean) {
        mmkv.encode(KEY_VISION_JSON_MODE, v)
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

    // ========== 并发答题设置相关 ==========

    /**
     * 获取并发模式是否启用
     * @return true表示启用并发模式，false表示使用串行模式，默认为false
     */
    fun isParallelModeEnabled(): Boolean {
        return mmkv.decodeBool(KEY_PARALLEL_MODE, false)
    }

    /**
     * 保存并发模式启用状态
     * @param enabled 是否启用并发模式
     */
    fun saveParallelMode(enabled: Boolean) {
        mmkv.encode(KEY_PARALLEL_MODE, enabled)
    }

    /**
     * 获取最大并发数
     * @return 最大并发数，默认为3，范围1-10
     */
    fun getMaxConcurrency(): Int {
        return mmkv.decodeInt(KEY_MAX_CONCURRENCY, 3)
    }

    /**
     * 保存最大并发数
     * @param count 最大并发数，会被限制在1-10范围内
     */
    fun saveMaxConcurrency(count: Int) {
        mmkv.encode(KEY_MAX_CONCURRENCY, count.coerceIn(1, 10))
    }

    /**
     * 获取隐身模式是否启用
     * @return true表示启用隐身模式，false表示关闭，默认为true
     */
    fun isStealthModeEnabled(): Boolean {
        return mmkv.decodeBool(KEY_STEALTH_MODE, true)
    }

    /**
     * 保存隐身模式启用状态
     * @param enabled 是否启用隐身模式
     */
    fun saveStealthMode(enabled: Boolean) {
        mmkv.encode(KEY_STEALTH_MODE, enabled)
    }

    // ========== LLM Temperature 相关 ==========

    /**
     * 获取LLM Temperature
     * @return Temperature值，默认为0.3（适合答题场景）
     */
    fun getLlmTemperature(): Double {
        return mmkv.decodeFloat(KEY_LLM_TEMPERATURE, 0.3f).toDouble()
    }

    /**
     * 保存LLM Temperature
     * @param temperature Temperature值，会被限制在0.0-2.0范围内
     */
    fun saveLlmTemperature(temperature: Double) {
        mmkv.encode(KEY_LLM_TEMPERATURE, temperature.coerceIn(0.0, 2.0).toFloat())
    }

    // ========== 正则过滤相关 ==========

    /**
     * 获取正则过滤是否启用
     * @return true表示启用正则过滤（检测到多题时跳过搜索），默认为true
     */
    fun isRegexFilterEnabled(): Boolean {
        return mmkv.decodeBool(KEY_REGEX_FILTER_ENABLED, true)
    }

    /**
     * 保存正则过滤启用状态
     */
    fun saveRegexFilterEnabled(enabled: Boolean) {
        mmkv.encode(KEY_REGEX_FILTER_ENABLED, enabled)
    }

    // ========== 思考模式相关 ==========

    /**
     * 获取思考模式的reasoning_effort值
     * @return "medium" 当启用，null 当禁用
     */
    fun getReasoningEffort(): String? {
        return if (mmkv.decodeBool(KEY_REASONING_EFFORT, false)) "medium" else null
    }

    /**
     * 保存思考模式启用状态
     */
    fun saveReasoningEffort(enabled: Boolean) {
        mmkv.encode(KEY_REASONING_EFFORT, enabled)
    }

    // ========== 悬浮窗外观相关 ==========

    /** 悬浮按钮大小（dp），默认 48 */
    fun getFloatButtonSize(): Int {
        return mmkv.decodeInt(KEY_FLOAT_BUTTON_SIZE, 56)
    }

    fun saveFloatButtonSize(size: Int) {
        mmkv.encode(KEY_FLOAT_BUTTON_SIZE, size.coerceIn(32, 80))
    }

    /** 悬浮按钮透明度 0.1~1.0，默认 0.9 */
    fun getFloatButtonAlpha(): Float {
        return mmkv.decodeFloat(KEY_FLOAT_BUTTON_ALPHA, 0.9f)
    }

    fun saveFloatButtonAlpha(alpha: Float) {
        mmkv.encode(KEY_FLOAT_BUTTON_ALPHA, alpha.coerceIn(0.1f, 1.0f))
    }

    /** 卡片透明度 0.1~1.0，默认 0.85 */
    fun getFloatCardAlpha(): Float {
        return mmkv.decodeFloat(KEY_FLOAT_CARD_ALPHA, 0.85f)
    }

    fun saveFloatCardAlpha(alpha: Float) {
        mmkv.encode(KEY_FLOAT_CARD_ALPHA, alpha.coerceIn(0.1f, 1.0f))
    }

    /**
     * 检查是否为首次启动
     * @return true表示首次启动，false表示已启动过
     */
    fun isFirstLaunch(): Boolean {
        return mmkv.decodeBool(KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 标记首次启动完成
     */
    fun setFirstLaunchComplete() {
        mmkv.encode(KEY_IS_FIRST_LAUNCH, false)
    }
}

