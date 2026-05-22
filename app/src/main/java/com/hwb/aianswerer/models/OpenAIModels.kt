package com.hwb.aianswerer.models

import com.google.gson.annotations.SerializedName
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R

/**
 * OpenAI API请求模型
 */
data class ChatRequest(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<ChatMessage>,

    @SerializedName("temperature")
    val temperature: Double = 0.7,

    @SerializedName("max_tokens")
    val maxTokens: Int? = null,

    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null,

    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = null
)

/**
 * 聊天消息
 */
data class ChatMessage(
    @SerializedName("role")
    val role: String,  // "system", "user", "assistant"

    @SerializedName("content")
    val content: String
)

/**
 * 响应格式配置
 */
data class ResponseFormat(
    @SerializedName("type")
    val type: String = "json_object"
)

/**
 * OpenAI API响应模型
 */
data class ChatResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val objectType: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<Choice>,

    @SerializedName("usage")
    val usage: Usage? = null
)

/**
 * 选择项
 */
data class Choice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("message")
    val message: ChatMessage,

    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * Token使用情况
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * AI答案解析结果
 */
data class AIAnswer(
    val question: String,
    val questionType: String,  // 选择题/问答题/填空题
    val answer: String,
    val options: List<String>? = null
) {
    /**
     * 格式化显示答案
     */
    fun formatAnswer(): String {
        val labels = AnswerSectionLabels
        return buildString {
            appendLine(labels.question)
            appendLine(question)
            appendLine()

            if (!options.isNullOrEmpty()) {
                appendLine(labels.options)
                options.forEach { appendLine(it) }
                appendLine()
            }

            appendLine(labels.answer)
            append(answer)
        }
    }
}

/**
 * 根据显示配置格式化答案
 *
 * 此扩展函数提供了可配置的答案格式化功能，允许根据用户设置选择性地显示题目、选项和答案。
 * 保持与原formatAnswer()方法相同的格式风格，确保UI显示一致性。
 *
 * @param showQuestion 是否显示题目内容，默认为true
 * @param showOptions 是否显示选项内容，默认为true。注意：仅当options非空时此参数才有效
 * @return 格式化后的答案字符串，至少包含【答案】部分
 *
 * @see formatAnswer 原始的完整格式化方法
 */
fun AIAnswer.formatAnswerWithConfig(
    showQuestion: Boolean = true,
    showOptions: Boolean = true
): String {
    val labels = AnswerSectionLabels
    return buildString {
        // 根据配置显示题目
        if (showQuestion) {
            appendLine(labels.question)
            appendLine(question)
            appendLine()
        }

        // 根据配置和选项存在性显示选项
        if (showOptions && !options.isNullOrEmpty()) {
            appendLine(labels.options)
            options.forEach { appendLine(it) }
            appendLine()
        }

        // 始终显示答案
        appendLine(labels.answer)
        append(answer)
    }
}

// 使用 computed property 而非 val，确保语言切换后获取到最新的字符串资源
private object AnswerSectionLabels {
    val question: String
        get() = MyApplication.getString(R.string.answer_section_question_title)
    val options: String
        get() = MyApplication.getString(R.string.answer_section_options_title)
    val answer: String
        get() = MyApplication.getString(R.string.answer_section_answer_title)
}
