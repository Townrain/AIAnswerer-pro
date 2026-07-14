package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.providers.LocalWebSearchConfig
import com.hwb.aianswerer.providers.WebSearchWebsites
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

    @Test
    fun `WebSearchClientFactory - 返回的provider都是BaseWebSearchProvider子类`() {
        listOf("tavily", "zhipu", "bocha", "exa", "querit", "searxng", "local-google", "local-bing", "local-baidu").forEach { id ->
            val provider = WebSearchClientFactory.create(createConfig(id))
            assertTrue("$id 不是 BaseWebSearchProvider 的子类", provider is BaseWebSearchProvider)
        }
    }

    // ═══ LocalSearchProvider 子类验证 ═══

    @Test
    fun `LocalGoogleSearchProvider - 类型继承`() {
        val config = createConfig("local-google", apiHost = "https://www.google.com")
        val provider = LocalGoogleSearchProvider(config)
        assertTrue(provider is LocalSearchProvider)
        assertTrue(provider is BaseWebSearchProvider)
    }

    @Test
    fun `LocalBingSearchProvider - 类型继承`() {
        val config = createConfig("local-bing", apiHost = "https://www.bing.com")
        val provider = LocalBingSearchProvider(config)
        assertTrue(provider is LocalSearchProvider)
        assertTrue(provider is BaseWebSearchProvider)
    }

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
