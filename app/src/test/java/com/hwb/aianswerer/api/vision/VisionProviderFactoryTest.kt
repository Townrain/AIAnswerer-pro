package com.hwb.aianswerer.api.vision

import org.junit.Assert.*
import org.junit.Test

class VisionProviderFactoryTest {

    // ═══ REGISTERED_PROVIDERS ═══

    @Test
    fun `REGISTERED_PROVIDERS - 有两个内置Provider`() {
        assertEquals(2, VisionProviderFactory.REGISTERED_PROVIDERS.size)
    }

    @Test
    fun `REGISTERED_PROVIDERS - 包含openai_compat和custom`() {
        assertTrue(VisionProviderFactory.REGISTERED_PROVIDERS.containsKey("openai_compat"))
        assertTrue(VisionProviderFactory.REGISTERED_PROVIDERS.containsKey("custom"))
    }

    @Test
    fun `REGISTERED_PROVIDERS - openai_compat元数据`() {
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS["openai_compat"]
        assertNotNull(meta)
        assertEquals("OpenAI 兼容接口", meta!!.displayName)
        assertEquals("https://api.deepseek.com/v1/chat/completions", meta.defaultBaseUrl)
        assertEquals("deepseek-chat", meta.defaultModel)
        assertTrue(meta.description.contains("DeepSeek"))
    }

    @Test
    fun `REGISTERED_PROVIDERS - custom元数据`() {
        val meta = VisionProviderFactory.REGISTERED_PROVIDERS["custom"]
        assertNotNull(meta)
        assertEquals("自定义接口", meta!!.displayName)
        assertEquals("", meta.defaultBaseUrl)
        assertEquals("", meta.defaultModel)
    }

    // ═══ ProviderMeta ═══

    @Test
    fun `ProviderMeta - 数据类构造`() {
        val meta = ProviderMeta(
            displayName = "测试",
            description = "描述信息",
            defaultBaseUrl = "https://example.com",
            defaultModel = "gpt-4"
        )
        assertEquals("测试", meta.displayName)
        assertEquals("描述信息", meta.description)
        assertEquals("https://example.com", meta.defaultBaseUrl)
        assertEquals("gpt-4", meta.defaultModel)
    }

    @Test
    fun `ProviderMeta - 空字符串默认值`() {
        val meta = ProviderMeta(
            displayName = "空默认",
            description = "",
            defaultBaseUrl = "",
            defaultModel = ""
        )
        assertEquals("", meta.defaultBaseUrl)
        assertEquals("", meta.defaultModel)
    }

    // ═══ invalidateCache ═══

    @Test
    fun `invalidateCache - 调用不抛异常`() {
        // invalidateCache 只是清空单例，不会触发 Android 依赖
        VisionProviderFactory.invalidateCache()
        // 多次调用也不应抛异常
        VisionProviderFactory.invalidateCache()
    }
}
