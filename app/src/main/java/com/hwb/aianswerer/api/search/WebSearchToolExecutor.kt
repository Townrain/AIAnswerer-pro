package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.api.search.WebSearchClientFactory
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.providers.WebSearchStorage
import com.hwb.aianswerer.utils.AppLog

/**
 * 联网搜索工具执行器。
 *
 * 唯一使用方：function calling 模式 — [com.hwb.aianswerer.api.OpenAIClient] 工具循环中执行 web_search 工具。
 *
 * 逻辑：读取已启用供应商 → 按用户选择匹配（找不到回退第一个）→ 执行单次搜索 → 拼接结果字符串。
 * 失败/空结果一律返回空串，不抛异常，调用方自行降级。
 */
object WebSearchToolExecutor {

    /** 工具名（与请求中 tools 定义的 name 保持一致） */
    const val TOOL_NAME = "web_search"

    /**
     * 执行一次联网搜索并返回拼接结果。
     * 结果格式：每行 "【title】snippet"。
     */
    suspend fun execute(query: String, maxResults: Int = 2): String {
        if (query.isBlank()) return ""
        val startMs = System.currentTimeMillis()
        val providers = WebSearchStorage.getEnabledProviders()
        if (providers.isEmpty()) {
            AppLog.w("WebSearch", "execute: no enabled providers, skipping")
            return ""
        }
        val selectedName = AppConfig.getWebSearchProvider()
        val selected = providers.find { it.name == selectedName } ?: providers.first()
        AppLog.d("WebSearch", "execute: provider=${selected.name}, queryLen=${query.length}, maxResults=$maxResults")
        val provider = WebSearchClientFactory.create(selected)
        val results = provider.search(query, maxResults)
        if (results.isEmpty()) {
            AppLog.w("WebSearch", "execute: provider returned empty results (elapsed=${System.currentTimeMillis() - startMs}ms)")
            return ""
        }
        val text = results.joinToString("\n") { "【${it.title}】${it.snippet}" }
        AppLog.d("WebSearch", "execute: done provider=${selected.name}, results=${results.size}, chars=${text.length}, elapsed=${System.currentTimeMillis() - startMs}ms")
        return text
    }

    /**
     * 判断搜索工具（function calling）模式是否激活。
     * 需要同时满足：搜索全局开关开启 + 存在已启用供应商 + 模型支持函数调用（不支持则无搜索）。
     */
    fun isToolModeActive(): Boolean {
        if (!WebSearchStorage.isSearchEnabled()) {
            AppLog.d("TOOL", "isToolModeActive=false: web search globally disabled")
            return false
        }
        if (WebSearchStorage.getEnabledProviders().isEmpty()) {
            AppLog.d("TOOL", "isToolModeActive=false: no enabled search providers")
            return false
        }
        val modelName = AppConfig.getModelName()
        if (modelName.isNotBlank() && !ModelCapabilityChecker.isFunctionCallingModel(modelName)) {
            AppLog.d("TOOL", "isToolModeActive=false: model '$modelName' not function-calling capable, search disabled")
            return false
        }
        AppLog.d("TOOL", "isToolModeActive=true: tool mode active (model='$modelName')")
        return true
    }
}
