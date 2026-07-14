package com.hwb.aianswerer.utils

import com.hwb.aianswerer.providers.*
import org.junit.Assert.*
import org.junit.Test

/**
 * WebSearchData 单元测试
 * 覆盖：联网搜索服务商数据类及内置提供商列表
 */
class WebSearchDataTest {

    // ── WebSearchProviderEntry ──

    @Test
    fun `WebSearchProviderEntry - 基础构建`() {
        val entry = WebSearchProviderEntry(
            id = "tavily",
            name = "Tavily",
            apiHost = "https://api.tavily.com",
            requiresApiKey = true,
            websites = WebSearchWebsites(
                official = "https://tavily.com",
                apiKey = "https://app.tavily.com/home"
            )
        )
        assertEquals("tavily", entry.id)
        assertEquals("Tavily", entry.name)
        assertEquals("https://api.tavily.com", entry.apiHost)
        assertTrue(entry.requiresApiKey)
        assertFalse(entry.requiresHost)
        assertFalse(entry.supportsBasicAuth)
        assertNotNull(entry.websites)
    }

    @Test
    fun `WebSearchProviderEntry - 默认值验证`() {
        val entry = WebSearchProviderEntry(
            id = "custom",
            name = "Custom"
        )
        assertEquals("", entry.apiHost)             // 默认空字符串
        assertEquals("", entry.url)                 // 默认空字符串
        assertTrue(entry.requiresApiKey)            // 默认true
        assertFalse(entry.requiresHost)             // 默认false
        assertFalse(entry.supportsBasicAuth)        // 默认false
        assertNull(entry.websites)                  // 默认null
    }

    @Test
    fun `WebSearchProviderEntry - searxng 特殊配置`() {
        val entry = WebSearchProviderEntry(
            id = "searxng",
            name = "Searxng",
            apiHost = "",
            requiresApiKey = false,
            requiresHost = true,
            supportsBasicAuth = true,
            websites = WebSearchWebsites(official = "https://docs.searxng.org")
        )
        assertEquals("searxng", entry.id)
        assertFalse(entry.requiresApiKey)
        assertTrue(entry.requiresHost)
        assertTrue(entry.supportsBasicAuth)
    }

    @Test
    fun `WebSearchProviderEntry - 搜索引擎类条目有url而非apiHost`() {
        val google = WebSearchProviderEntry(
            id = "local-google", name = "Google",
            url = "https://www.google.com/search?q=%s",
            requiresApiKey = false
        )
        assertEquals("https://www.google.com/search?q=%s", google.url)
        assertEquals("", google.apiHost)
    }

    // ── LocalWebSearchConfig ──

    @Test
    fun `LocalWebSearchConfig - 完整构建及默认值`() {
        val config = LocalWebSearchConfig(
            id = "tavily",
            name = "Tavily",
            apiHost = "https://api.tavily.com",
            url = "",
            requiresApiKey = true,
            requiresHost = false,
            supportsBasicAuth = false,
            websites = WebSearchWebsites("https://tavily.com", "https://app.tavily.com/home")
        )
        assertEquals("tavily", config.id)
        assertEquals("Tavily", config.name)
        assertEquals("https://api.tavily.com", config.apiHost)
        assertTrue(config.requiresApiKey)
        assertNull(config.customApiHost)       // 默认null
        assertEquals("", config.apiKey)         // 默认空
        assertFalse(config.enabled)             // 默认false
        assertEquals("", config.basicAuthUsername)
        assertEquals("", config.basicAuthPassword)
    }

    @Test
    fun `LocalWebSearchConfig - 用户配置覆盖`() {
        val config = LocalWebSearchConfig(
            id = "tavily",
            name = "Tavily",
            apiHost = "https://api.tavily.com",
            url = "",
            requiresApiKey = true,
            requiresHost = false,
            supportsBasicAuth = false,
            websites = null,
            apiKey = "sk-test-key",
            enabled = true,
            customApiHost = "https://custom.tavily.com",
            basicAuthUsername = "user",
            basicAuthPassword = "pass"
        )
        assertEquals("sk-test-key", config.apiKey)
        assertTrue(config.enabled)
        assertEquals("https://custom.tavily.com", config.customApiHost)
        assertEquals("user", config.basicAuthUsername)
        assertEquals("pass", config.basicAuthPassword)
    }

    @Test
    fun `LocalWebSearchConfig - copy() 修改部分字段`() {
        val original = LocalWebSearchConfig(
            id = "bocha", name = "Bocha",
            apiHost = "https://api.bochaai.com", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null
        )
        val modified = original.copy(enabled = true, apiKey = "bocha-key")
        assertFalse(original.enabled)
        assertEquals("", original.apiKey)
        assertTrue(modified.enabled)
        assertEquals("bocha-key", modified.apiKey)
        assertEquals("bocha", modified.id)  // 未修改
    }

    // ── WebSearchWebsites ──

    @Test
    fun `WebSearchWebsites - 可空字段`() {
        val ws = WebSearchWebsites()
        assertNull(ws.official)
        assertNull(ws.apiKey)
    }

    @Test
    fun `WebSearchWebsites - 部分字段填充`() {
        val ws = WebSearchWebsites(official = "https://example.com")
        assertEquals("https://example.com", ws.official)
        assertNull(ws.apiKey)
    }

    // ── WebSearchProviders 内置列表 ──

    @Test
    fun `WebSearchProviders_PROVIDERS - 包含tavily条目`() {
        val tavily = WebSearchProviders.PROVIDERS.find { it.id == "tavily" }
        assertNotNull(tavily)
        assertEquals("Tavily", tavily!!.name)
        assertEquals("https://api.tavily.com", tavily.apiHost)
        assertTrue(tavily.requiresApiKey)
    }

    @Test
    fun `WebSearchProviders_PROVIDERS - 包含所有预期id`() {
        val ids = WebSearchProviders.PROVIDERS.map { it.id }.toSet()
        assertTrue(ids.contains("tavily"))
        assertTrue(ids.contains("zhipu"))
        assertTrue(ids.contains("bocha"))
        assertTrue(ids.contains("exa"))
        assertTrue(ids.contains("querit"))
        assertTrue(ids.contains("searxng"))
        assertTrue(ids.contains("exa-mcp"))
        assertTrue(ids.contains("local-google"))
        assertTrue(ids.contains("local-bing"))
        assertTrue(ids.contains("local-baidu"))
        assertEquals(10, ids.size)
    }

    @Test
    fun `WebSearchProviders_PROVIDERS - searxng配置正确`() {
        val searxng = WebSearchProviders.PROVIDERS.find { it.id == "searxng" }
        assertNotNull(searxng)
        assertTrue(searxng!!.requiresHost)
        assertFalse(searxng.requiresApiKey)
        assertTrue(searxng.supportsBasicAuth)
    }

    @Test
    fun `WebSearchProviders_PROVIDERS - 本地搜索引擎url不为空`() {
        val google = WebSearchProviders.PROVIDERS.find { it.id == "local-google" }
        val bing = WebSearchProviders.PROVIDERS.find { it.id == "local-bing" }
        val baidu = WebSearchProviders.PROVIDERS.find { it.id == "local-baidu" }

        assertTrue(google!!.url.isNotBlank())
        assertTrue(bing!!.url.isNotBlank())
        assertTrue(baidu!!.url.isNotBlank())
        assertFalse(google.requiresApiKey)
        assertFalse(bing.requiresApiKey)
        assertFalse(baidu.requiresApiKey)
    }

    // ── 边界情况 ──

    @Test
    fun `WebSearchProviderEntry - 空apiHost`() {
        val entry = WebSearchProviderEntry(
            id = "test", name = "Test",
            apiHost = "",
            websites = WebSearchWebsites()
        )
        assertEquals("", entry.apiHost)
    }

    @Test
    fun `LocalWebSearchConfig - 空API URL`() {
        val config = LocalWebSearchConfig(
            id = "x", name = "X",
            apiHost = "", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null
        )
        assertEquals("", config.apiHost)
        assertEquals("", config.url)
    }

    @Test
    fun `LocalWebSearchConfig - missing key 默认空字符串`() {
        val config = LocalWebSearchConfig(
            id = "test", name = "Test",
            apiHost = "https://api.test.com", url = "",
            requiresApiKey = true, requiresHost = false,
            supportsBasicAuth = false, websites = null
        )
        // 未传 apiKey，应使用默认值 ""
        assertEquals("", config.apiKey)
    }
}
