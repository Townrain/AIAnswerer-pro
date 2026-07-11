package com.hwb.aianswerer

import com.hwb.aianswerer.config.AppConfig

/**
 * 应用级常量：通知配置、Intent Action 定义、系统提示词构建。
 */
object Constants {
    // 通知渠道配置
    const val NOTIFICATION_CHANNEL_ID = "ai_answerer_service"
    const val NOTIFICATION_ID = 1001

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
        val basePrompt = getBaseSystemPrompt()
        val promptBuilder = StringBuilder(basePrompt)

        val hasConstraints = questionTypes.isNotEmpty() || searchContext.isNotBlank()
        if (!hasConstraints) {
            // 即使没有约束，也添加输出语言
            val lang = AppConfig.getOutputLanguage()
            if (lang != "中文" && lang != "自动识别" && lang.isNotBlank()) {
                promptBuilder.append("\n\n请用${lang}回答。")
            }
            return promptBuilder.toString()
        }

        promptBuilder.append("\n\n")
        promptBuilder.append(MyApplication.getString(R.string.system_prompt_limit_header))
        promptBuilder.append('\n')

        val typeSeparator = MyApplication.getString(R.string.system_prompt_type_separator)
        val essayType = MyApplication.getString(R.string.ai_question_type_essay)

        // 添加题型限制
        if (questionTypes.isNotEmpty()) {
            promptBuilder.append(
                MyApplication.getString(
                    R.string.system_prompt_type_template,
                    questionTypes.joinToString(typeSeparator),
                    essayType
                )
            )
        }

        // 添加输出语言
        val lang = AppConfig.getOutputLanguage()
        if (lang != "中文" && lang != "自动识别" && lang.isNotBlank()) {
            promptBuilder.append("\n")
            promptBuilder.append("请用${lang}回答。")
        }

        // 添加搜索上下文
        if (searchContext.isNotBlank()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(MyApplication.getString(R.string.system_prompt_search_header))
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
        val basePrompt = getBaseSystemPrompt()
        val promptBuilder = StringBuilder(basePrompt)

        // 批处理模式上下文
        promptBuilder.append("\n\n")
        promptBuilder.append(
            MyApplication.getString(
                R.string.system_prompt_recording_header,
                questionIndex,
                totalQuestions
            )
        )

        // 题型限制
        if (questionTypes.isNotEmpty()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(MyApplication.getString(R.string.system_prompt_limit_header))
            promptBuilder.append('\n')
            val typeSeparator = MyApplication.getString(R.string.system_prompt_type_separator)
            val essayType = MyApplication.getString(R.string.ai_question_type_essay)
            promptBuilder.append(
                MyApplication.getString(
                    R.string.system_prompt_type_template,
                    questionTypes.joinToString(typeSeparator),
                    essayType
                )
            )
        }

        // 输出语言
        val lang = AppConfig.getOutputLanguage()
        if (lang != "中文" && lang != "自动识别" && lang.isNotBlank()) {
            promptBuilder.append("\n")
            promptBuilder.append("请用${lang}回答。")
        }

        // 搜索上下文
        if (searchContext.isNotBlank()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(MyApplication.getString(R.string.system_prompt_search_header))
            promptBuilder.append('\n')
            promptBuilder.append(searchContext)
        }

        return promptBuilder.toString()
    }

    private fun getBaseSystemPrompt(): String {
        val choiceType = MyApplication.getString(R.string.ai_question_type_choice)
        val essayType = MyApplication.getString(R.string.ai_question_type_essay)
        val blankType = MyApplication.getString(R.string.ai_question_type_blank)
        return MyApplication.getString(
            R.string.system_prompt_base,
            choiceType,
            essayType,
            blankType
        )
    }
}

