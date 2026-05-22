package com.hwb.aianswerer

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
    const val EXTRA_ANSWER_TEXT = "answer_text"
    const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
    const val EXTRA_QUESTION_TEXT = "question_text"


    /**
     * 动态构建系统提示词：
     *   基础 prompt + 可选约束段（题型限制）。
     *   约束段仅当用户设定了题型时追加，避免给 AI 无关指令。
     */
    fun buildSystemPrompt(questionTypes: Set<String>, searchContext: String = ""): String {
        val basePrompt = getBaseSystemPrompt()
        if (questionTypes.isEmpty() && searchContext.isBlank()) {
            return basePrompt
        }

        val promptBuilder = StringBuilder(basePrompt)
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

        // 添加搜索上下文
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

