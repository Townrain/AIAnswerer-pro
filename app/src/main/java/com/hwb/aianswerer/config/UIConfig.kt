package com.hwb.aianswerer.config

import com.hwb.aianswerer.ui.theme.ThemePresets
/**
 * UI / Theme / Language 配置
 */
internal object UIConfig {

    // ========== 语言设置相关 ==========

    /**
     * 保存语言设置
     * @param languageCode 语言代码 (zh/en)
     */
    fun saveLanguage(languageCode: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_LANGUAGE, languageCode)
    }

    /**
     * 获取当前设置的语言
     * @return 语言代码，默认为中文
     */
    fun getLanguage(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_LANGUAGE, ConfigStorage.LANGUAGE_ZH) ?: ConfigStorage.LANGUAGE_ZH
    }

    // ========== 暗色模式相关 ==========

    /**
     * 保存暗色模式设置
     * @param mode 暗色模式：0=跟随系统, 1=亮色, 2=暗色
     */
    fun saveDarkMode(mode: Int) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_DARK_MODE, mode)
    }

    /**
     * 获取暗色模式设置
     * @return 暗色模式：0=跟随系统, 1=亮色, 2=暗色，默认为0
     */
    fun getDarkMode(): Int {
        return ConfigStorage.requireMmkv().decodeInt(ConfigStorage.KEY_DARK_MODE, 0)
    }

    // ========== 快捷按钮布局模式相关 ==========

    /**
     * 获取快捷按钮布局模式
     * @return 布局模式，默认为横向排列
     */
    fun getQuickButtonLayout(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_QUICK_BUTTON_LAYOUT, ConfigStorage.QUICK_BUTTON_LAYOUT_HORIZONTAL) ?: ConfigStorage.QUICK_BUTTON_LAYOUT_HORIZONTAL
    }

    /**
     * 保存快捷按钮布局模式
     * @param mode 布局模式（QUICK_BUTTON_LAYOUT_ARC 或 QUICK_BUTTON_LAYOUT_HORIZONTAL）
     */
    fun saveQuickButtonLayout(mode: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_QUICK_BUTTON_LAYOUT, mode)
    }

    // ========== 悬浮窗外观相关 ==========

    /** 悬浮按钮大小（dp），默认 40 */
    fun getFloatButtonSize(): Int {
        return ConfigStorage.requireMmkv().decodeInt(ConfigStorage.KEY_FLOAT_BUTTON_SIZE, 40)
    }

    fun saveFloatButtonSize(size: Int) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_FLOAT_BUTTON_SIZE, size.coerceIn(32, 80))
    }

    /** 放大镜图标缩放 0.5~2.0，默认 1.0 */
    fun getFloatIconScale(): Float {
        return ConfigStorage.requireMmkv().decodeFloat(ConfigStorage.KEY_FLOAT_ICON_SCALE, 1.0f)
    }

    fun saveFloatIconScale(scale: Float) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_FLOAT_ICON_SCALE, scale.coerceIn(0.5f, 2.0f))
    }

    /** 悬浮按钮透明度 0.1~1.0，默认 0.9 */
    fun getFloatButtonAlpha(): Float {
        return ConfigStorage.requireMmkv().decodeFloat(ConfigStorage.KEY_FLOAT_BUTTON_ALPHA, 0.9f)
    }

    fun saveFloatButtonAlpha(alpha: Float) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_FLOAT_BUTTON_ALPHA, alpha.coerceIn(0.1f, 1.0f))
    }

    /** 卡片透明度 0.1~1.0，默认 0.85 */
    fun getFloatCardAlpha(): Float {
        return ConfigStorage.requireMmkv().decodeFloat(ConfigStorage.KEY_FLOAT_CARD_ALPHA, 0.85f)
    }

    fun saveFloatCardAlpha(alpha: Float) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_FLOAT_CARD_ALPHA, alpha.coerceIn(0.1f, 1.0f))
    }

    /** 悬浮窗隐身模式是否启用，默认 true */
    fun isStealthModeEnabled(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_STEALTH_MODE, true)
    }

    fun saveStealthMode(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_STEALTH_MODE, enabled)
    }

    /**
     * 检查是否为首次启动
     * @return true表示首次启动，false表示已启动过
     */
    fun isFirstLaunch(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 标记首次启动完成
     */
    fun setFirstLaunchComplete() {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_IS_FIRST_LAUNCH, false)
    }

    fun saveOutputLanguage(lang: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_OUTPUT_LANGUAGE, lang)
    }

    fun getOutputLanguage(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_OUTPUT_LANGUAGE, "中文") ?: "中文"
    }

    // ========== 主题预设相关 ==========

    fun saveThemePresetId(presetId: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_THEME_PRESET, presetId)
    }

    fun getThemePresetId(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_THEME_PRESET, ThemePresets.WARM_AUTUMN) ?: ThemePresets.WARM_AUTUMN
    }

    fun saveCustomThemes(json: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_CUSTOM_THEMES, json)
    }

    fun getCustomThemes(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_CUSTOM_THEMES, "") ?: ""
    }

    /** 长按触发时长（毫秒），默认 1000ms，范围 300~3000 */
    fun getLongPressDuration(): Int {
        return ConfigStorage.requireMmkv().decodeInt(ConfigStorage.KEY_LONG_PRESS_DURATION, 1000)
    }

    fun saveLongPressDuration(ms: Int) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_LONG_PRESS_DURATION, ms.coerceIn(300, 3000))
    }

    // ========== 调试日志 ==========

    fun isDebugLogEnabled(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_DEBUG_LOG, false)
    }

    fun saveDebugLogEnabled(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_DEBUG_LOG, enabled)
    }
}
