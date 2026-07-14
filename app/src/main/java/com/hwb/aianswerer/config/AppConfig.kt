package com.hwb.aianswerer.config

import android.content.Context
import com.hwb.aianswerer.providers.WebSearchStorage
import com.tencent.mmkv.MMKV

object AppConfig {
    // ── Storage init ──
    fun init(context: Context) = ConfigStorage.init(context)
    fun initSecurePrefs(context: Context) = ConfigStorage.initSecurePrefs(context)

    // ── Constants (re-export for backward compat) ──
    const val LANGUAGE_ZH = ConfigStorage.LANGUAGE_ZH
    const val LANGUAGE_EN = ConfigStorage.LANGUAGE_EN
    const val CROP_MODE_FULL = ConfigStorage.CROP_MODE_FULL
    const val CROP_MODE_EACH = ConfigStorage.CROP_MODE_EACH
    const val CROP_MODE_ONCE = ConfigStorage.CROP_MODE_ONCE
    const val CAPTURE_MODE_SCREENSHOT = ConfigStorage.CAPTURE_MODE_SCREENSHOT
    const val CAPTURE_MODE_ACCESSIBILITY = ConfigStorage.CAPTURE_MODE_ACCESSIBILITY
    const val QUICK_BUTTON_LAYOUT_ARC = ConfigStorage.QUICK_BUTTON_LAYOUT_ARC
    const val QUICK_BUTTON_LAYOUT_HORIZONTAL = ConfigStorage.QUICK_BUTTON_LAYOUT_HORIZONTAL

    // ── API ──
    fun saveApiUrl(url: String) = ApiConfig.saveApiUrl(url)
    fun getApiUrl(): String = ApiConfig.getApiUrl()
    fun saveApiKey(key: String) = ApiConfig.saveApiKey(key)
    fun getApiKey(): String = ApiConfig.getApiKey()
    fun saveModelName(model: String) = ApiConfig.saveModelName(model)
    fun getModelName(): String = ApiConfig.getModelName()
    fun isApiConfigValid(url: String = getApiUrl(), key: String = getApiKey(), model: String = getModelName()): Boolean = ApiConfig.isApiConfigValid(url, key, model)
    fun saveLlmTemperature(t: Double) = ApiConfig.saveLlmTemperature(t)
    fun getLlmTemperature(): Double = ApiConfig.getLlmTemperature()
    fun saveReasoningEffort(enabled: Boolean) = ApiConfig.saveReasoningEffort(enabled)
    fun getReasoningEffort(): String? = ApiConfig.getReasoningEffort()
    fun isParallelModeEnabled(): Boolean = ApiConfig.isParallelModeEnabled()
    fun saveParallelMode(enabled: Boolean) = ApiConfig.saveParallelMode(enabled)
    fun getMaxConcurrency(): Int = ApiConfig.getMaxConcurrency()
    fun saveMaxConcurrency(count: Int) = ApiConfig.saveMaxConcurrency(count)

    // ── Vision ──
    fun isVisionEnabled(): Boolean = VisionConfig.isVisionEnabled()
    fun saveVisionEnabled(enabled: Boolean) = VisionConfig.saveVisionEnabled(enabled)
    fun getVisionProviderId(): String = VisionConfig.getVisionProviderId()
    fun saveVisionProviderId(id: String) = VisionConfig.saveVisionProviderId(id)
    fun getVisionBaseUrl(): String = VisionConfig.getVisionBaseUrl()
    fun saveVisionBaseUrl(url: String) = VisionConfig.saveVisionBaseUrl(url)
    fun getVisionApiKey(): String = VisionConfig.getVisionApiKey()
    fun saveVisionApiKey(key: String) = VisionConfig.saveVisionApiKey(key)
    fun getVisionModelName(): String = VisionConfig.getVisionModelName()
    fun saveVisionModelName(name: String) = VisionConfig.saveVisionModelName(name)
    fun getVisionTemperature(): Double = VisionConfig.getVisionTemperature()
    fun saveVisionTemperature(t: Double) = VisionConfig.saveVisionTemperature(t)
    fun getVisionMaxTokens(): Int = VisionConfig.getVisionMaxTokens()
    fun saveVisionMaxTokens(n: Int) = VisionConfig.saveVisionMaxTokens(n)
    fun getVisionJsonMode(): Boolean = VisionConfig.getVisionJsonMode()
    fun saveVisionJsonMode(v: Boolean) = VisionConfig.saveVisionJsonMode(v)
    fun resetVisionToProviderDefaults() = VisionConfig.resetVisionToProviderDefaults()
    // ── Search ──
    fun saveWebSearchProvider(name: String) = SearchConfig.saveWebSearchProvider(name)
    fun getWebSearchProvider(): String = SearchConfig.getWebSearchProvider()
    fun isRegexFilterEnabled(): Boolean = SearchConfig.isRegexFilterEnabled()
    fun saveRegexFilterEnabled(enabled: Boolean) = SearchConfig.saveRegexFilterEnabled(enabled)

    // ── Capture ──
    fun saveCropMode(mode: String) = CaptureConfig.saveCropMode(mode)
    fun getCropMode(): String = CaptureConfig.getCropMode()
    fun saveCaptureMode(mode: String) = CaptureConfig.saveCaptureMode(mode)
    fun getCaptureMode(): String = CaptureConfig.getCaptureMode()
    fun isAccessibilityCaptureMode(): Boolean = CaptureConfig.isAccessibilityCaptureMode()
    fun saveQuestionTypes(types: Set<String>) = CaptureConfig.saveQuestionTypes(types)
    fun getQuestionTypes(): Set<String> = CaptureConfig.getQuestionTypes()
    fun saveAutoSubmit(enabled: Boolean) = CaptureConfig.saveAutoSubmit(enabled)
    fun getAutoSubmit(): Boolean = CaptureConfig.getAutoSubmit()
    fun saveAutoCopy(enabled: Boolean) = CaptureConfig.saveAutoCopy(enabled)
    fun getAutoCopy(): Boolean = CaptureConfig.getAutoCopy()
    fun saveShowAnswerCardQuestion(show: Boolean) = CaptureConfig.saveShowAnswerCardQuestion(show)
    fun getShowAnswerCardQuestion(): Boolean = CaptureConfig.getShowAnswerCardQuestion()
    fun saveShowAnswerCardOptions(show: Boolean) = CaptureConfig.saveShowAnswerCardOptions(show)
    fun getShowAnswerCardOptions(): Boolean = CaptureConfig.getShowAnswerCardOptions()
    fun saveStealthMode(enabled: Boolean) = CaptureConfig.saveStealthMode(enabled)
    fun isStealthModeEnabled(): Boolean = CaptureConfig.isStealthModeEnabled()

    // ── UI ──
    fun saveLanguage(code: String) = UIConfig.saveLanguage(code)
    fun getLanguage(): String = UIConfig.getLanguage()
    fun saveDarkMode(mode: Int) = UIConfig.saveDarkMode(mode)
    fun getDarkMode(): Int = UIConfig.getDarkMode()
    fun saveFloatButtonSize(size: Int) = UIConfig.saveFloatButtonSize(size)
    fun getFloatButtonSize(): Int = UIConfig.getFloatButtonSize()
    fun saveFloatButtonAlpha(alpha: Float) = UIConfig.saveFloatButtonAlpha(alpha)
    fun getFloatButtonAlpha(): Float = UIConfig.getFloatButtonAlpha()
    fun saveFloatCardAlpha(alpha: Float) = UIConfig.saveFloatCardAlpha(alpha)
    fun getFloatCardAlpha(): Float = UIConfig.getFloatCardAlpha()
    fun saveQuickButtonLayout(mode: String) = UIConfig.saveQuickButtonLayout(mode)
    fun getQuickButtonLayout(): String = UIConfig.getQuickButtonLayout()
    fun saveOutputLanguage(lang: String) = UIConfig.saveOutputLanguage(lang)
    fun getOutputLanguage(): String = UIConfig.getOutputLanguage()
    fun isFirstLaunch(): Boolean = UIConfig.isFirstLaunch()
    fun setFirstLaunchComplete() = UIConfig.setFirstLaunchComplete()

    // ── Model Whitelist ──
    fun saveDynamicVisionModels(models: List<String>) = ModelWhitelistConfig.saveDynamicVisionModels(models)
    fun getDynamicVisionModels(): List<String> = ModelWhitelistConfig.getDynamicVisionModels()
    fun saveDynamicVisionExcluded(models: List<String>) = ModelWhitelistConfig.saveDynamicVisionExcluded(models)
    fun getDynamicVisionExcluded(): List<String> = ModelWhitelistConfig.getDynamicVisionExcluded()
    fun saveDynamicProviderModels(providerModels: Map<String, List<String>>) = ModelWhitelistConfig.saveDynamicProviderModels(providerModels)
    fun getDynamicProviderModels(): Map<String, List<String>> = ModelWhitelistConfig.getDynamicProviderModels()
    fun getDynamicProviderModels(providerId: String): List<String> = ModelWhitelistConfig.getDynamicProviderModels(providerId)
    fun saveDynamicProviderConfigs(configs: List<com.hwb.aianswerer.utils.ModelWhitelistUpdater.ProviderConfig>) = ModelWhitelistConfig.saveDynamicProviderConfigs(configs)
    fun getDynamicProviderConfigs(): List<com.hwb.aianswerer.utils.ModelWhitelistUpdater.ProviderConfig> = ModelWhitelistConfig.getDynamicProviderConfigs()
    // ── Migration ──
    /**
     * 一次性迁移：将旧 Tavily 配置迁移到新的多供应商 WebSearchStorage。
     * 仅在首次升级时执行，通过 migrate flag 保证只执行一次。
     */
    fun migrateTavilyConfig() {
        try {
            val mmkv = MMKV.defaultMMKV()
            if (mmkv.decodeBool("migrated_tavily_to_websearch", false)) return

            val oldApiKey = ConfigStorage.getSecurePrefs()?.getString("tavily_api_key", null)
                ?: mmkv.decodeString("tavily_api_key", null)
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
            android.util.Log.w("AppConfig", "Tavily config migration failed", e)
        }
    }
}
