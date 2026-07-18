package com.hwb.aianswerer

import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.AppLog
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * 应用级常量：通知配置、Intent Action 定义、系统提示词构建。
 */
object Constants {
    // 通知渠道配置
    const val NOTIFICATION_CHANNEL_ID = "ai_answerer_service"
    const val NOTIFICATION_ID = 1001

    // 提示词版本（修改提示词后递增，便于追踪效果变化）
    const val PROMPT_VERSION = 2

    // ── 提示词语言解析 ──
    /**
     * 提示词语言的 Resources 对象。
     * AI 提示词语言独立于 UI 语言：中文 UI → 中文提示词，其他 → 英文提示词。
     * 英文是 LLM 的通用指令语言，非中英文用户用英文提示词效果最优。
     */
    val promptResources: Resources by lazy {
        val config = Configuration(MyApplication.getAppContext().resources.configuration)
        val promptLocale = when (AppConfig.getLanguage()) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "es" -> Locale("es")
            "pt" -> Locale("pt")
            "ru" -> Locale("ru")
            "ar" -> Locale("ar")
            "other" -> Locale.ENGLISH
            "system" -> Locale.getDefault()
            else -> Locale.ENGLISH
        }
        config.setLocale(promptLocale)
        MyApplication.getAppContext().createConfigurationContext(config).resources
    }

    private fun promptStr(resId: Int): String = promptResources.getString(resId)
    private fun promptStr(resId: Int, vararg formatArgs: Any): String = promptResources.getString(resId, *formatArgs)
    // Intent Actions
    const val ACTION_SHOW_ANSWER = "com.hwb.aianswerer.SHOW_ANSWER"
    const val ACTION_REQUEST_ANSWER = "com.hwb.aianswerer.REQUEST_ANSWER"
    const val ACTION_REFRESH_SETTINGS = "com.hwb.aianswerer.REFRESH_SETTINGS"
    const val EXTRA_ANSWER_TEXT = "answer_text"
    const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
    const val EXTRA_QUESTION_TEXT = "question_text"


    /**
     * 动态构建系统提示词：
     *   基础 prompt + 可选约束段（题型限制 + 输出语言 + 搜索上下文）。
     */
    fun buildSystemPrompt(questionTypes: Set<String>, searchContext: String = ""): String {
        AppLog.d("Prompt", "Build v$PROMPT_VERSION types=$questionTypes")
        val basePrompt = getBaseSystemPrompt()
        val promptBuilder = StringBuilder(basePrompt)
        val normalizedTypes = normalizeQuestionTypes(questionTypes)

        val hasConstraints = normalizedTypes.isNotEmpty() || searchContext.isNotBlank()
        if (!hasConstraints) {
            // 即使没有约束，也添加输出语言
            val lang = AppConfig.getOutputLanguage()
            if (lang != "中文" && lang != "自动识别" && lang.isNotBlank()) {
                promptBuilder.append("\n\n")
                promptBuilder.append(promptStr(R.string.system_prompt_language_instruction, lang))
            }
            return promptBuilder.toString()
        }

        promptBuilder.append("\n\n")
        promptBuilder.append(promptStr(R.string.system_prompt_limit_header))

        val typeSeparator = promptStr(R.string.system_prompt_type_separator)
        val essayType = promptStr(R.string.ai_question_type_essay)

        // 添加题型限制
        if (normalizedTypes.isNotEmpty()) {
            promptBuilder.append(
                promptStr(
                    R.string.system_prompt_type_template,
                    normalizedTypes.joinToString(typeSeparator),
                    essayType
                )
            )
        }

        // 添加输出语言
        val lang = AppConfig.getOutputLanguage()
        if (lang != "中文" && lang != "自动识别" && lang.isNotBlank()) {
            promptBuilder.append("\n")
            promptBuilder.append(promptStr(R.string.system_prompt_language_instruction, lang))
        }

        // 添加搜索上下文
        if (searchContext.isNotBlank()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(promptStr(R.string.system_prompt_search_header))
            promptBuilder.append('\n')
            promptBuilder.append(searchContext)
        }

        return promptBuilder.toString()
    }

    /**
     * 构建录制模式的系统提示词：
     *   在基础 prompt 之上增加批处理上下文（当前题号 / 总题数），
     *   并指示 LLM 在答案中标注题号，便于结果自动汇总。
     */
    fun buildRecordingSystemPrompt(
        questionIndex: Int,
        totalQuestions: Int,
        questionTypes: Set<String>,
        searchContext: String = ""
    ): String {
        AppLog.d("Prompt", "BuildRecording v$PROMPT_VERSION Q$questionIndex/$totalQuestions")
        val basePrompt = getBaseSystemPrompt()
        val promptBuilder = StringBuilder(basePrompt)

        val normalizedTypes = normalizeQuestionTypes(questionTypes)

        // 批处理模式上下文
        promptBuilder.append("\n\n")
        promptBuilder.append(
            promptStr(
                R.string.system_prompt_recording_header,
                questionIndex,
                totalQuestions
            )
        )

        // 题型限制
        if (normalizedTypes.isNotEmpty()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(promptStr(R.string.system_prompt_limit_header))
            promptBuilder.append('\n')
            val typeSeparator = promptStr(R.string.system_prompt_type_separator)
            val essayType = promptStr(R.string.ai_question_type_essay)
            promptBuilder.append(
                promptStr(
                    R.string.system_prompt_type_template,
                    normalizedTypes.joinToString(typeSeparator),
                    essayType
                )
            )
        }

        // 输出语言
        val lang = AppConfig.getOutputLanguage()
        if (lang != "中文" && lang != "自动识别" && lang.isNotBlank()) {
            promptBuilder.append("\n")
            promptBuilder.append(promptStr(R.string.system_prompt_language_instruction, lang))
        }

        // 搜索上下文
        if (searchContext.isNotBlank()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(promptStr(R.string.system_prompt_search_header))
            promptBuilder.append('\n')
            promptBuilder.append(searchContext)
        }

        return promptBuilder.toString()
    }

    private fun getBaseSystemPrompt(): String {
        val custom = AppConfig.getCustomSystemPrompt()
        if (custom.isNotBlank()) return custom
        val choiceType = promptStr(R.string.ai_question_type_choice)
        val essayType = promptStr(R.string.ai_question_type_essay)
        val blankType = promptStr(R.string.ai_question_type_blank)
        return promptStr(R.string.system_prompt_base, choiceType, essayType, blankType)
    }

    /**
     * 将主页选择题的细粒度标签（单选题/多选题/不定项选择题）
     * 翻译为 AI 认识的内部类型（选择题/填空题/问答题）。
     */
    private fun normalizeQuestionTypes(types: Set<String>): Set<String> {
        val choiceType = MyApplication.getString(R.string.ai_question_type_choice) // "选择题"
        return types.map { type ->
            when {
                type.contains("选") -> choiceType       // 单选题、多选题、不定项选择题 → 选择题
                else -> type                            // 填空题、问答题 → 不变
            }
        }.toSet()
    }
}

