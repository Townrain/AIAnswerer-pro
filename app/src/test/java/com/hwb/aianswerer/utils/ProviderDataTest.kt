package com.hwb.aianswerer.utils

import com.hwb.aianswerer.providers.*
import org.junit.Assert.*
import org.junit.Test

/**
 * ProviderData 单元测试
 * 覆盖：云端 provider-data.json 数据结构和本地持久化配置的数据类
 */
class ProviderDataTest {

    // ── ProviderDataJson ──

    @Test
    fun `ProviderDataJson - 完整构建和字段读取`() {
        val entry = ProviderEntry(
            id = "deepseek",
            name = "DeepSeek",
            type = "openai",
            apiHost = "https://api.deepseek.com",
            anthropicApiHost = null,
            models = listOf(ModelEntry("deepseek-chat", "DeepSeek Chat", "chat")),
            websites = WebsiteInfo(
                official = "https://deepseek.com",
                apiKey = "https://platform.deepseek.com/api-keys",
                docs = "https://platform.deepseek.com/docs",
                models = "https://platform.deepseek.com/models"
            )
        )
        val data = ProviderDataJson(
            version = 1,
            generatedAt = "2025-06-01T00:00:00Z",
            providerCount = 1,
            modelCount = 1,
            providers = listOf(entry)
        )

        assertEquals(1, data.version)
        assertEquals("2025-06-01T00:00:00Z", data.generatedAt)
        assertEquals(1, data.providerCount)
        assertEquals(1, data.modelCount)
        assertEquals(1, data.providers.size)
        assertEquals("deepseek", data.providers[0].id)
    }

    @Test
    fun `ProviderDataJson - 空providers列表`() {
        val data = ProviderDataJson(
            version = 0,
            generatedAt = "",
            providerCount = 0,
            modelCount = 0,
            providers = emptyList()
        )
        assertTrue(data.providers.isEmpty())
    }

    // ── ProviderEntry ──

    @Test
    fun `ProviderEntry - 构建与anthropicApiHost为null`() {
        val entry = ProviderEntry(
            id = "openai",
            name = "OpenAI",
            type = "openai",
            apiHost = "https://api.openai.com",
            anthropicApiHost = null,
            models = emptyList(),
            websites = null
        )
        assertEquals("openai", entry.id)
        assertEquals("OpenAI", entry.name)
        assertEquals("openai", entry.type)
        assertEquals("https://api.openai.com", entry.apiHost)
        assertNull(entry.anthropicApiHost)
        assertTrue(entry.models.isEmpty())
        assertNull(entry.websites)
    }

    @Test
    fun `ProviderEntry - anthropicApiHost不为空`() {
        val entry = ProviderEntry(
            id = "anthropic",
            name = "Anthropic",
            type = "anthropic",
            apiHost = "https://api.anthropic.com",
            anthropicApiHost = "https://api.anthropic.com",
            models = emptyList(),
            websites = null
        )
        assertEquals("https://api.anthropic.com", entry.anthropicApiHost)
    }

    // ── ModelEntry ──

    @Test
    fun `ModelEntry - 构建与equals`() {
        val m1 = ModelEntry(id = "gpt-4", name = "GPT-4", group = "chat")
        assertEquals("gpt-4", m1.id)
        assertEquals("GPT-4", m1.name)
        assertEquals("chat", m1.group)
    }

    @Test
    fun `ModelEntry - 相同内容equals为true`() {
        val m1 = ModelEntry("gpt-4", "GPT-4", "chat")
        val m2 = ModelEntry("gpt-4", "GPT-4", "chat")
        assertEquals(m1, m2)
        assertEquals(m1.hashCode(), m2.hashCode())
    }

    // ── WebsiteInfo ──

    @Test
    fun `WebsiteInfo - 所有字段可空`() {
        val ws = WebsiteInfo(null, null, null, null)
        assertNull(ws.official)
        assertNull(ws.apiKey)
        assertNull(ws.docs)
        assertNull(ws.models)
    }

    @Test
    fun `WebsiteInfo - 部分字段填充`() {
        val ws = WebsiteInfo(official = "https://example.com", apiKey = null, docs = null, models = null)
        assertEquals("https://example.com", ws.official)
        assertNull(ws.apiKey)
    }

    // ── LocalProviderConfig ──

    @Test
    fun `LocalProviderConfig - 完整构建及默认值`() {
        val models = listOf(ModelEntry("gpt-4", "GPT-4", "chat"))
        val ws = WebsiteInfo("https://openai.com", null, null, null)
        val config = LocalProviderConfig(
            id = "openai",
            name = "OpenAI",
            type = "openai",
            apiHost = "https://api.openai.com",
            anthropicApiHost = null,
            models = models,
            websites = ws
        )
        assertEquals("openai", config.id)
        assertEquals("OpenAI", config.name)
        assertEquals("openai", config.type)
        assertEquals("https://api.openai.com", config.apiHost)
        assertNull(config.anthropicApiHost)
        assertEquals(models, config.models)
        assertEquals(ws, config.websites)
        // 默认值
        assertEquals("", config.apiKey)
        assertFalse(config.enabled)
        assertTrue(config.isSystem)
        assertEquals(0, config.dataVersion)
        assertTrue(config.selectedModels.isEmpty())
    }

    @Test
    fun `LocalProviderConfig - 自定义用户配置覆盖默认值`() {
        val config = LocalProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            type = "openai",
            apiHost = "https://api.deepseek.com",
            anthropicApiHost = null,
            models = emptyList(),
            websites = null,
            apiKey = "sk-xxx",
            enabled = true,
            isSystem = false,
            dataVersion = 2,
            selectedModels = listOf("deepseek-chat")
        )
        assertEquals("sk-xxx", config.apiKey)
        assertTrue(config.enabled)
        assertFalse(config.isSystem)
        assertEquals(2, config.dataVersion)
        assertEquals(listOf("deepseek-chat"), config.selectedModels)
    }

    @Test
    fun `LocalProviderConfig - copy() 修改部分字段`() {
        val original = LocalProviderConfig(
            id = "openai", name = "OpenAI", type = "openai",
            apiHost = "https://api.openai.com", anthropicApiHost = null,
            models = emptyList(), websites = null
        )
        val modified = original.copy(enabled = true, apiKey = "sk-new")
        assertEquals("openai", modified.id)      // 未修改
        assertTrue(modified.enabled)             // 已修改
        assertEquals("sk-new", modified.apiKey)  // 已修改
        assertEquals("", original.apiKey)        // 原对象不变
    }

    @Test
    fun `LocalProviderConfig - copy() 不修改原对象`() {
        val original = LocalProviderConfig(
            id = "test", name = "Test", type = "openai",
            apiHost = "", anthropicApiHost = null,
            models = emptyList(), websites = null,
            apiKey = "old-key", enabled = true
        )
        val updated = original.copy(apiKey = "new-key", enabled = false)
        assertEquals("old-key", original.apiKey)
        assertTrue(original.enabled)
        assertEquals("new-key", updated.apiKey)
        assertFalse(updated.enabled)
    }

    // ── 边界情况 ──

    @Test
    fun `LocalProviderConfig - 空字段`() {
        val config = LocalProviderConfig(
            id = "", name = "", type = "",
            apiHost = "", anthropicApiHost = null,
            models = emptyList(), websites = null,
            apiKey = "", enabled = false, isSystem = true,
            dataVersion = 0, selectedModels = emptyList()
        )
        assertEquals("", config.id)
        assertEquals("", config.name)
        assertTrue(config.selectedModels.isEmpty())
    }

    @Test
    fun `ProviderEntry - 空白provider name`() {
        val entry = ProviderEntry(
            id = "custom", name = "  ", type = "openai",
            apiHost = "", anthropicApiHost = null,
            models = emptyList(), websites = null
        )
        assertEquals("  ", entry.name)
    }
}
