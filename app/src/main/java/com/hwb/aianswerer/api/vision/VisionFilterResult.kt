package com.hwb.aianswerer.api.vision

import com.google.gson.annotations.SerializedName

/**
 * 视觉模型统一返回结果
 *
 * 所有 Provider 无论后端是什么 API 格式，都映射为此结构。
 * 这是接口契约的一部分。
 */
data class VisionFilterResult(
    /** 截图是否包含可识别题目 */
    @SerializedName("has_questions")
    val hasQuestions: Boolean = false,

    /** 题目数量 */
    @SerializedName("question_count")
    val questionCount: Int = 0,

    /** 题型列表 */
    @SerializedName("question_types")
    val questionTypes: List<String> = emptyList(),

    /** 提炼后的搜索关键词（用于 Tavily 搜索） */
    @SerializedName("search_keywords")
    val searchKeywords: String = "",

    /** 是否为多题截图 */
    @SerializedName("is_multi_question")
    val isMultiQuestion: Boolean = false,

    /** 干扰内容描述（调试用） */
    @SerializedName("noise_description")
    val noiseDescription: String = "",

    /** 可选：VLM 直接提取的题目文本（替代 OCR 模式时使用） */
    @SerializedName("extracted_text")
    val extractedText: String = "",

    /** 分离后的题目列表（多题模式使用） */
    @SerializedName("questions")
    val questions: List<SeparatedQuestion> = emptyList(),

    /** 原始响应（调试用，不序列化） */
    @Transient
    val rawResponse: String = ""
)

/**
 * 分离后的单个题目
 */
data class SeparatedQuestion(
    /** 题目序号 */
    @SerializedName("index")
    val index: Int = 0,

    /** 题目文本 */
    @SerializedName("text")
    val text: String = "",

    /** 题型 */
    @SerializedName("question_type")
    val questionType: String = "",

    /** 该题的搜索关键词 */
    @SerializedName("search_keywords")
    val searchKeywords: String = ""
)
