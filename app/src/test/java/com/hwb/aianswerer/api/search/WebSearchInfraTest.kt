@file:Suppress("USELESS_IS_CHECK")
package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.providers.LocalWebSearchConfig
import com.hwb.aianswerer.providers.WebSearchWebsites
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WebSearchInfraTest {

    private fun createConfig(id: String, apiHost: String = "https://api.example.com"): LocalWebSearchConfig {
        return LocalWebSearchConfig(
            id = id, name = id,
            apiHost = apiHost, url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null
        )
    }

    // ═══ WebSearchClientFactory ═══

    @Test
    fun `WebSearchClientFactory - tavily返回TavilySearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("tavily"))
        assertTrue(provider is TavilySearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - zhipu返回ZhipuSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("zhipu"))
        assertTrue(provider is ZhipuSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - bocha返回BochaSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("bocha"))
        assertTrue(provider is BochaSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - exa返回ExaSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("exa"))
        assertTrue(provider is ExaSearchProvider)
    }
    @Test
    fun `WebSearchClientFactory - exa-mcp返回ExaMCPSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("exa-mcp"))
        assertTrue(provider is ExaMCPSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - querit返回QueritSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("querit"))
        assertTrue(provider is QueritSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - searxng返回SearXNGSProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("searxng"))
        assertTrue(provider is SearXNGSProvider)
    }

    @Test
    fun `WebSearchClientFactory - local_google返回LocalGoogleSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("local-google"))
        assertTrue(provider is LocalGoogleSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - local_bing返回LocalBingSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("local-bing"))
        assertTrue(provider is LocalBingSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - local_baidu返回LocalBaiduSearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("local-baidu"))
        assertTrue(provider is LocalBaiduSearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - 未知id降级为TavilySearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig("unknown-provider"))
        assertTrue(provider is TavilySearchProvider)
    }

    @Test
    fun `WebSearchClientFactory - 空id也降级为TavilySearchProvider`() {
        val provider = WebSearchClientFactory.create(createConfig(""))
        assertTrue(provider is TavilySearchProvider)
    }

    @Suppress("SENSELESS_COMPARISON")
    @Test
    fun `WebSearchClientFactory - 返回的provider都是BaseWebSearchProvider子类`() {
        listOf("tavily", "zhipu", "bocha", "exa", "exa-mcp", "querit", "searxng", "local-google", "local-bing", "local-baidu").forEach { id ->
            val provider = WebSearchClientFactory.create(createConfig(id))
            assertTrue("$id 不是 BaseWebSearchProvider 的子类", provider is BaseWebSearchProvider)
        }
    }

    // ═══ ExaMCPSearchProvider 真实 MCP 协议 ═══

    @Test
    fun `ExaMCPSearchProvider - JSON 响应执行 initialize 握手并解析工具调用结果`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Mcp-Session-Id", "sess-123")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{},"serverInfo":{"name":"exa"}}}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"{\"results\":[{\"title\":\"T1\",\"url\":\"https://a.com\",\"text\":\"s1\"},{\"title\":\"T2\",\"url\":\"https://b.com\",\"text\":\"s2\"}]}"}]}}""")
        )
        server.start()
        val provider = ExaMCPSearchProvider(createConfig("exa-mcp", apiHost = server.url("/").toString().trimEnd('/')))
        val results = provider.search("光合作用", 2)
        server.shutdown()

        assertEquals(2, results.size)
        assertEquals("T1", results[0].title)
        assertEquals("https://b.com", results[1].url)

        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        val req1Body = req1.body.readUtf8()
        val req2Body = req2.body.readUtf8()
        assertTrue(req1Body.contains("\"method\":\"initialize\""))
        assertEquals("sess-123", req2.getHeader("Mcp-Session-Id"))
        assertTrue(req2Body.contains("\"name\":\"web_search\""))
        assertTrue(req2Body.contains("光合作用"))
    }

    @Test
    fun `ExaMCPSearchProvider - SSE 流响应同样解析`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"))
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
                .setBody("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"results\\\":[{\\\"title\\\":\\\"SSE1\\\",\\\"url\\\":\\\"https://s.com\\\",\\\"text\\\":\\\"x\\\"}]}\"}]}}\n\n")
        )
        server.start()
        val provider = ExaMCPSearchProvider(createConfig("exa-mcp", apiHost = server.url("/").toString().trimEnd('/')))
        val results = provider.search("测试", 1)
        server.shutdown()

        assertEquals(1, results.size)
        assertEquals("SSE1", results[0].title)
    }

    @Test
    fun `ExaMCPSearchProvider - MCP 错误返回空列表不崩溃`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"jsonrpc\":\"2.0\",\"id\":2,\"error\":{\"code\":-32602,\"message\":\"bad args\"}}"))
        server.start()
        val provider = ExaMCPSearchProvider(createConfig("exa-mcp", apiHost = server.url("/").toString().trimEnd('/')))
        val results = provider.search("x", 1)
        server.shutdown()

        assertTrue(results.isEmpty())
    }

    // ═══ LocalSearchProvider 子类验证 ═══

    @Suppress("SENSELESS_COMPARISON")
    @Test
    fun `LocalGoogleSearchProvider - 类型继承`() {
        val config = createConfig("local-google", apiHost = "https://www.google.com")
        val provider = LocalGoogleSearchProvider(config)
        assertTrue(provider is LocalSearchProvider)
        assertTrue(provider is BaseWebSearchProvider)
    }

    @Suppress("SENSELESS_COMPARISON")
    @Test
    fun `LocalBingSearchProvider - 类型继承`() {
        val config = createConfig("local-bing", apiHost = "https://www.bing.com")
        val provider = LocalBingSearchProvider(config)
        assertTrue(provider is LocalSearchProvider)
        assertTrue(provider is BaseWebSearchProvider)
    }

    @Suppress("SENSELESS_COMPARISON")
    @Test
    fun `LocalBaiduSearchProvider - 类型继承`() {
        val config = createConfig("local-baidu", apiHost = "https://www.baidu.com")
        val provider = LocalBaiduSearchProvider(config)
        assertTrue(provider is LocalSearchProvider)
        assertTrue(provider is BaseWebSearchProvider)
    }

    // ═══ LocalWebSearchConfig 构造 ═══

    @Test
    fun `LocalWebSearchConfig - 最小构造`() {
        val config = LocalWebSearchConfig(
            id = "test", name = "测试",
            apiHost = "https://test.com", url = "",
            requiresApiKey = false, requiresHost = false,
            supportsBasicAuth = false, websites = null
        )
        assertEquals("test", config.id)
        assertFalse(config.requiresApiKey)
        assertEquals("", config.apiKey)
        assertFalse(config.enabled)
    }

    @Test
    fun `LocalWebSearchConfig - 启用状态和各种配置`() {
        val websites = WebSearchWebsites(official = "https://official.com", apiKey = "https://api-key.com")
        val config = LocalWebSearchConfig(
            id = "tavily", name = "Tavily",
            apiHost = "https://api.tavily.com", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = websites,
            apiKey = "sk-xxx", enabled = true,
            customApiHost = null,
            basicAuthUsername = "user", basicAuthPassword = "pass"
        )
        assertTrue(config.enabled)
        assertEquals("sk-xxx", config.apiKey)
        assertEquals("user", config.basicAuthUsername)
        assertEquals("pass", config.basicAuthPassword)
        assertNotNull(config.websites)
        assertEquals("https://api-key.com", config.websites!!.apiKey)
    }
}
