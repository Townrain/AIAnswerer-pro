package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.providers.LocalWebSearchConfig

/**
 * 联网搜索结果
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 联网搜索 Provider 基类
 * 每种搜索源实现各自的 API 调用逻辑
 */
abstract class BaseWebSearchProvider(protected val config: LocalWebSearchConfig) {

    /** 当前配置的 API Key */
    protected val apiKey: String get() = config.apiKey

    /** 当前配置的 Host（自定义优先，否则默认） */
    protected val apiHost: String get() = config.customApiHost?.takeIf { it.isNotBlank() } ?: config.apiHost

    /**
     * 执行搜索
     * @param query 搜索关键词
     * @param maxResults 最大结果数
     * @return 搜索结果列表，失败返回空
     */
    abstract suspend fun search(query: String, maxResults: Int): List<WebSearchResult>
}
