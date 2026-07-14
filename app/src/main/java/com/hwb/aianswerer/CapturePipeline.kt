package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.search.WebSearchClientFactory
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.api.vision.VisionProviderFactory
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.providers.WebSearchStorage
import com.hwb.aianswerer.utils.AppLog

/**
 * 答题流水线 — 纯粹的识别、搜索、AI 调用逻辑。
 *
 * 依赖通过构造函数注入，支持单元测试用 fake 替换。
 * 生产环境使用默认参数（全局单例），测试环境可注入 mock/fake。
 */
class CapturePipeline(
    private val textRecognitionManager: TextRecognitionManager,
    private val openAiClient: OpenAIClient = OpenAIClient.getInstance(),
    private val createVisionProvider: () -> com.hwb.aianswerer.api.vision.VisionProvider? = { VisionProviderFactory.create() }
) {
    /** ML Kit OCR 识别图片中的文字 */
    suspend fun recognizeOcr(bitmap: Bitmap): Result<String> =
        textRecognitionManager.recognizeText(bitmap)

    /**
     * 视觉模型识别图片。
     * 如果 VLM provider 不可用，返回 Result.failure。
     * 调用方需自行处理降级到 OCR。
     */
    suspend fun recognizeVlm(bitmap: Bitmap): Result<VisionFilterResult> {
        val provider = createVisionProvider()
            ?: return Result.failure(Exception("VLM provider not available"))
        return provider.analyze(bitmap)
    }

    /** 调用大模型获取答案 */
    suspend fun askLlm(
        text: String,
        questionTypes: Set<String>,
        searchContext: String,
        systemPrompt: String? = null
    ): Result<List<AIAnswer>> {
        return openAiClient.analyzeQuestion(
            text, questionTypes, searchContext, systemPrompt = systemPrompt
        )
    }

    /**
     * 联网搜索。
     * 调用前请先检查 searchEnabled，本方法不判断开关状态。
     */
    suspend fun searchWeb(query: String, maxResults: Int = 2): String {
        val providers = WebSearchStorage.getEnabledProviders()
        if (providers.isEmpty()) {
            AppLog.w("CapturePipeline", "searchWeb: no enabled providers, skipping")
            return ""
        }
        val selectedName = AppConfig.getWebSearchProvider()
        val selected = providers.find { it.name == selectedName } ?: providers.first()
        AppLog.d("CapturePipeline", "searchWeb: using provider=${selected.name}, query=$query")
        val provider = WebSearchClientFactory.create(selected)
        val results = provider.search(query, maxResults)
        if (results.isEmpty()) {
            AppLog.w("CapturePipeline", "searchWeb: provider returned empty results")
            return ""
        }
        return results.joinToString("\n") { "【${it.title}】${it.snippet}" }
    }

    /** 判断文本是否像一道题目 */
    fun looksLikeQuestion(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        if (t.contains("?") || t.contains("？")) return true
        if (Regex("""[A-Da-d][.、．)\s]""").containsMatchIn(t)) return true
        val keywords = listOf(
            "下列", "以下", "属于", "不属于", "正确", "错误", "哪个", "哪些",
            "什么", "如何", "为什么", "原因是", "主要", "关于", "说法", "选项", "答案",
            "which", "what", "how", "why", "correct", "incorrect", "true", "false"
        )
        return keywords.any { t.lowercase().contains(it) }
    }
}
