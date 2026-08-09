package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.providers.LocalWebSearchConfig
import com.hwb.aianswerer.providers.WebSearchStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * WebSearchToolExecutor tests — search execution and tool-mode activation.
 */
class WebSearchToolExecutorTest {

    private lateinit var mockSearchProvider: BaseWebSearchProvider

    @Before
    fun setUp() {
        mockSearchProvider = mockk(relaxed = true)
        mockkObject(WebSearchStorage)
        mockkObject(AppConfig)
        mockkObject(WebSearchClientFactory)
        mockkObject(ModelCapabilityChecker)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun provider(id: String, name: String, apiKey: String = "k") = LocalWebSearchConfig(
        id = id, name = name, apiHost = "https://api.$id.com",
        url = "", requiresApiKey = true, requiresHost = false,
        supportsBasicAuth = false, websites = null,
        apiKey = apiKey, enabled = true
    )

    // ── execute ─────────────────────────────────────────────────────

    @Test
    fun `execute returns empty when no providers enabled`() = runBlocking {
        every { WebSearchStorage.getEnabledProviders() } returns emptyList()

        assertEquals("", WebSearchToolExecutor.execute("查询"))
    }

    @Test
    fun `execute returns empty for blank query`() = runBlocking {
        assertEquals("", WebSearchToolExecutor.execute("  "))
    }

    @Test
    fun `execute uses selected provider and joins results`() = runBlocking {
        val p = provider("tavily", "Tavily")
        every { WebSearchStorage.getEnabledProviders() } returns listOf(p)
        every { AppConfig.getWebSearchProvider() } returns "Tavily"
        every { WebSearchClientFactory.create(p) } returns mockSearchProvider
        coEvery { mockSearchProvider.search("光合作用", 2) } returns listOf(
            WebSearchResult(title = "标题A", url = "https://a.com", snippet = "片段A"),
            WebSearchResult(title = "标题B", url = "https://b.com", snippet = "片段B")
        )

        val result = WebSearchToolExecutor.execute("光合作用", 2)

        assertTrue(result.contains("标题A"))
        assertTrue(result.contains("片段A"))
        assertTrue(result.contains("标题B"))
        assertTrue(result.contains("片段B"))
        assertTrue(result.lines().size == 2)
    }

    @Test
    fun `execute falls back to first provider when selected not found`() = runBlocking {
        val p = provider("bocha", "Bocha")
        every { WebSearchStorage.getEnabledProviders() } returns listOf(p)
        every { AppConfig.getWebSearchProvider() } returns "NonExistent"
        every { WebSearchClientFactory.create(p) } returns mockSearchProvider
        coEvery { mockSearchProvider.search(any(), any()) } returns listOf(
            WebSearchResult(title = "回退结果", url = "https://b.com", snippet = "内容")
        )

        val result = WebSearchToolExecutor.execute("查询")

        assertTrue(result.contains("回退结果"))
    }

    @Test
    fun `execute returns empty when provider returns empty results`() = runBlocking {
        val p = provider("tavily", "Tavily")
        every { WebSearchStorage.getEnabledProviders() } returns listOf(p)
        every { AppConfig.getWebSearchProvider() } returns "Tavily"
        every { WebSearchClientFactory.create(p) } returns mockSearchProvider
        coEvery { mockSearchProvider.search(any(), any()) } returns emptyList()

        assertEquals("", WebSearchToolExecutor.execute("查询"))
    }

    // ── isToolModeActive ────────────────────────────────────────────

    @Test
    fun `tool mode inactive when search toggle off`() {
        every { WebSearchStorage.isSearchEnabled() } returns false
        assertFalse(WebSearchToolExecutor.isToolModeActive())
    }

    @Test
    fun `tool mode inactive when no enabled providers`() {
        every { WebSearchStorage.isSearchEnabled() } returns true
        every { WebSearchStorage.getEnabledProviders() } returns emptyList()
        assertFalse(WebSearchToolExecutor.isToolModeActive())
    }

    @Test
    fun `tool mode inactive when model explicitly does not support function calling`() {
        every { WebSearchStorage.isSearchEnabled() } returns true
        every { WebSearchStorage.getEnabledProviders() } returns listOf(provider("tavily", "Tavily"))
        every { AppConfig.getModelName() } returns "deepseek-r1-0528"
        every { ModelCapabilityChecker.isFunctionCallingModel(any()) } returns false
        assertFalse(WebSearchToolExecutor.isToolModeActive())
    }

    @Test
    fun `tool mode active when all conditions met`() {
        every { WebSearchStorage.isSearchEnabled() } returns true
        every { WebSearchStorage.getEnabledProviders() } returns listOf(provider("tavily", "Tavily"))
        every { AppConfig.getModelName() } returns "deepseek-chat"
        every { ModelCapabilityChecker.isFunctionCallingModel(any()) } returns true
        assertTrue(WebSearchToolExecutor.isToolModeActive())
    }

    @Test
    fun `tool mode active for unknown model name (defaults to supported)`() {
        every { WebSearchStorage.isSearchEnabled() } returns true
        every { WebSearchStorage.getEnabledProviders() } returns listOf(provider("tavily", "Tavily"))
        every { AppConfig.getModelName() } returns ""
        assertTrue(WebSearchToolExecutor.isToolModeActive())
    }
}
