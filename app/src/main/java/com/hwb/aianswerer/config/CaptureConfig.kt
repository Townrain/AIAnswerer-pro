package com.hwb.aianswerer.config

/**
 * Screenshot / Capture / 截图采集 配置
 */
internal object CaptureConfig {

    // ========== 答题设置相关 ==========

    /**
     * 保存题型设置
     * @param types 题型集合（如：单选题、多选题等）
     */
    fun saveQuestionTypes(types: Set<String>) {
        val typesString = types.joinToString(",")
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_QUESTION_TYPES, typesString)
    }

    /**
     * 获取题型设置
     * @return 题型集合，默认为单选题
     */
    fun getQuestionTypes(): Set<String> {
        val typesString = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_QUESTION_TYPES, "单选题") ?: "单选题"
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
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_SHOW_ANSWER_CARD_QUESTION, show)
    }

    /**
     * 获取答题卡片是否显示题目设置
     * @return 是否显示题目，默认为true
     */
    fun getShowAnswerCardQuestion(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_SHOW_ANSWER_CARD_QUESTION, true)
    }

    /**
     * 保存答题卡片是否显示选项设置
     * @param show 是否显示选项
     */
    fun saveShowAnswerCardOptions(show: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_SHOW_ANSWER_CARD_OPTIONS, show)
    }

    /**
     * 获取答题卡片是否显示选项设置
     * @return 是否显示选项，默认为true
     */
    fun getShowAnswerCardOptions(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_SHOW_ANSWER_CARD_OPTIONS, true)
    }

    // ========== 截图识别模式相关 ==========

    /**
     * 保存截图识别模式
     * @param mode 识别模式（CROP_MODE_FULL/CROP_MODE_EACH/CROP_MODE_ONCE）
     */
    fun saveCropMode(mode: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_CROP_MODE, mode)
    }

    /**
     * 获取截图识别模式
     * @return 识别模式，默认为全屏模式
     */
    fun getCropMode(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_CROP_MODE, ConfigStorage.CROP_MODE_FULL) ?: ConfigStorage.CROP_MODE_FULL
    }

    // ========== 采集模式相关 ==========

    /**
     * 保存采集模式
     * @param mode CAPTURE_MODE_SCREENSHOT 或 CAPTURE_MODE_ACCESSIBILITY
     */
    fun saveCaptureMode(mode: String) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_CAPTURE_MODE, mode)
    }

    /**
     * 获取采集模式
     * @return 采集模式，默认为截图模式
     */
    fun getCaptureMode(): String {
        return ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_CAPTURE_MODE, ConfigStorage.CAPTURE_MODE_SCREENSHOT) ?: ConfigStorage.CAPTURE_MODE_SCREENSHOT
    }

    /**
     * 是否使用无障碍模式采集
     */
    fun isAccessibilityCaptureMode(): Boolean {
        return getCaptureMode() == ConfigStorage.CAPTURE_MODE_ACCESSIBILITY
    }

    // ========== 应用设置相关 ==========

    /**
     * 保存自动提交设置
     * @param enabled 是否启用自动提交（识别后直接获取答案，不显示确认对话框）
     */
    fun saveAutoSubmit(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_AUTO_SUBMIT, enabled)
    }

    /**
     * 获取自动提交设置
     * @return 是否启用自动提交，默认为true
     */
    fun getAutoSubmit(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_AUTO_SUBMIT, true)
    }

    /**
     * 保存自动复制到剪贴板设置
     * @param enabled 是否启用自动复制（生成答案后自动复制到剪贴板）
     */
    fun saveAutoCopy(enabled: Boolean) {
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_AUTO_COPY, enabled)
    }

    /**
     * 获取自动复制到剪贴板设置
     * @return 是否启用自动复制，默认为false
     */
    fun getAutoCopy(): Boolean {
        return ConfigStorage.requireMmkv().decodeBool(ConfigStorage.KEY_AUTO_COPY, false)
    }


}