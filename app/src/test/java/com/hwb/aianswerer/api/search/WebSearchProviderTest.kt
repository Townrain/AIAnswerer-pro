package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.providers.LocalWebSearchConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WebSearchProviderTest {

    // ═══ WebSearchResult ═══

    @Test
    fun `WebSearchResult - 数据类构造`() {
        val result = WebSearchResult(
            title = "测试标题",
            url = "https://example.com",
            snippet = "测试摘要内容"
        )
        assertEquals("测试标题", result.title)
        assertEquals("https://example.com", result.url)
        assertEquals("测试摘要内容", result.snippet)
    }

    @Test
    fun `WebSearchResult - 空字符串字段`() {
        val result = WebSearchResult(title = "", url = "", snippet = "")
        assertEquals("", result.title)
        assertEquals("", result.url)
        assertEquals("", result.snippet)
    }

    @Test
    fun `WebSearchResult - equals和hashCode`() {
        val a = WebSearchResult("Title", "https://x.com", "Snippet")
        val b = WebSearchResult("Title", "https://x.com", "Snippet")
        val c = WebSearchResult("Other", "https://y.com", "Other")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `WebSearchResult - 复制修改`() {
        val original = WebSearchResult("原标题", "https://a.com", "原摘要")
        val modified = original.copy(url = "https://b.com")
        assertEquals("原标题", modified.title)
        assertEquals("https://b.com", modified.url)
        assertEquals("原摘要", modified.snippet)
    }

    // ═══ BaseWebSearchProvider（通过子类行为验证） ═══

    @Test
    fun `BaseWebSearchProvider - API Key为空时search返回空列表`() {
        val config = LocalWebSearchConfig(
            id = "tavily", name = "Tavily",
            apiHost = "https://api.tavily.com", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null,
            apiKey = ""
        )
        val provider = TavilySearchProvider(config)
        val results = runBlocking { provider.search("test", 5) }
        assertTrue("空API Key应返回空结果", results.isEmpty())
    }

    @Test
    fun `BaseWebSearchProvider - 非Tavily子类空API Key返回空`() {
        val config = LocalWebSearchConfig(
            id = "zhipu", name = "Zhipu",
            apiHost = "https://open.bigmodel.cn/api/paas/v4/web_search", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null,
            apiKey = ""
        )
        val provider = ZhipuSearchProvider(config)
        val results = runBlocking { provider.search("test", 5) }
        assertTrue("空API Key应返回空结果", results.isEmpty())
    }

    @Test
    fun `BaseWebSearchProvider - BochaProvider也继承自BaseWebSearchProvider`() {
        val config = LocalWebSearchConfig(
            id = "bocha", name = "Bocha",
            apiHost = "https://api.bochaai.com", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null
        )
        val provider = BochaSearchProvider(config)
        assertTrue(provider is BaseWebSearchProvider)
    }

    @Test
    fun `TavilySearchProvider - search不抛异常`() {
        val config = LocalWebSearchConfig(
            id = "tavily", name = "Tavily",
            apiHost = "https://api.tavily.com", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null,
            apiKey = ""
        )
        // apiKey为空时search不发起网络请求，直接返回空列表
        val provider = TavilySearchProvider(config)
        val results = runBlocking { provider.search("test", 5) }
        assertTrue(results.isEmpty())
    }
}
