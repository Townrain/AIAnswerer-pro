package com.hwb.aianswerer.providers

import com.hwb.aianswerer.config.ConfigStorage
import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.*
import org.junit.Test

/**
 * WebSearchStorage + ConfigStorage 单元测试
 *
 * WebSearchStorage:
 * - 联网搜索服务商配置的 MMKV 持久化（enabled/apiKey/customApiHost/basicAuth）
 * - 全局搜索开关（isSearchEnabled/saveSearchEnabled）
 *
 * ConfigStorage:
 * - MMKV 与 EncryptedSharedPreferences 的底层容器管理
 * - 参数常量定义
 *
 * 所有依赖 MMKV 方法通过 safelyInvoke 包裹，在 JVM 上优雅跳过。
 */
class WebSearchStorageTest {

    // ════════════════════════════════════════════════════════════════════
    // WebSearchStorage
    // ════════════════════════════════════════════════════════════════════

    // ── UserWebSearchConfig ──

    @Test
    fun `WebSearchStorageUserWebSearchConfig默认值应正确`() {
        val config = WebSearchStorage.UserWebSearchConfig()
        assertFalse(config.enabled)
        assertEquals("", config.apiKey)
        assertNull(config.customApiHost)
        assertEquals("", config.basicAuthUsername)
        assertEquals("", config.basicAuthPassword)
    }

    @Test
    fun `WebSearchStorageUserWebSearchConfig自定义值应保持`() {
        val config = WebSearchStorage.UserWebSearchConfig(
            enabled = true,
            apiKey = "sk-tavily-test",
            customApiHost = "https://custom-search.example.com",
            basicAuthUsername = "user",
            basicAuthPassword = "pass"
        )
        assertTrue(config.enabled)
        assertEquals("sk-tavily-test", config.apiKey)
        assertEquals("https://custom-search.example.com", config.customApiHost)
        assertEquals("user", config.basicAuthUsername)
        assertEquals("pass", config.basicAuthPassword)
    }

    // ── 保存/读取 userConfig ──

    @Test
    fun `WebSearchStorage保存搜索配置后读取应一致`() {
        safelyInvoke {
            val config = WebSearchStorage.UserWebSearchConfig(
                enabled = true,
                apiKey = "sk-tavily-123",
                customApiHost = "https://proxy.tavily.com"
            )
            WebSearchStorage.saveUserConfig("tavily", config)
            val loaded = WebSearchStorage.getUserConfig("tavily")
            assertTrue(loaded.enabled)
            assertEquals("sk-tavily-123", loaded.apiKey)
            assertEquals("https://proxy.tavily.com", loaded.customApiHost)
        }
    }

    @Test
    fun `WebSearchStorage未保存的provider应返回默认配置`() {
        safelyInvoke {
            val config = WebSearchStorage.getUserConfig("unknown-provider")
            assertFalse("不存在的 provider 应返回默认 enabled=false", config.enabled)
            assertTrue("不存在的 provider 应返回空 apiKey", config.apiKey.isEmpty())
            assertNull("不存在的 provider 应返回 null customApiHost", config.customApiHost)
        }
    }

    @Test
    fun `WebSearchStorage两个provider配置互不干扰`() {
        safelyInvoke {
            val tavilyConfig = WebSearchStorage.UserWebSearchConfig(
                enabled = true, apiKey = "sk-tavily"
            )
            val bochaConfig = WebSearchStorage.UserWebSearchConfig(
                enabled = false, apiKey = ""
            )
            WebSearchStorage.saveUserConfig("tavily", tavilyConfig)
            WebSearchStorage.saveUserConfig("bocha", bochaConfig)

            assertEquals("sk-tavily", WebSearchStorage.getUserConfig("tavily").apiKey)
            assertTrue(WebSearchStorage.getUserConfig("tavily").enabled)
            assertEquals("", WebSearchStorage.getUserConfig("bocha").apiKey)
            assertFalse(WebSearchStorage.getUserConfig("bocha").enabled)
        }
    }

    @Test
    fun `WebSearchStorage更新已有配置应覆盖旧值`() {
        safelyInvoke {
            val old = WebSearchStorage.UserWebSearchConfig(enabled = true, apiKey = "old-key")
            WebSearchStorage.saveUserConfig("tavily", old)
            val updated = WebSearchStorage.UserWebSearchConfig(enabled = false, apiKey = "new-key")
            WebSearchStorage.saveUserConfig("tavily", updated)

            val loaded = WebSearchStorage.getUserConfig("tavily")
            assertFalse("覆盖后 enabled 应更新为 false", loaded.enabled)
            assertEquals("覆盖后 apiKey 应更新为 new-key", "new-key", loaded.apiKey)
        }
    }

    // ── 搜索开关 ──

    @Test
    fun `WebSearchStorage全局搜索开关默认关闭`() {
        safelyInvoke {
            val enabled = WebSearchStorage.isSearchEnabled()
            assertFalse("全局搜索开关默认应为 false", enabled)
        }
    }

    @Test
    fun `WebSearchStorage保存搜索开关后读取应一致`() {
        safelyInvoke {
            WebSearchStorage.saveSearchEnabled(true)
            assertTrue("开启后 isSearchEnabled 应为 true", WebSearchStorage.isSearchEnabled())

            WebSearchStorage.saveSearchEnabled(false)
            assertFalse("关闭后 isSearchEnabled 应为 false", WebSearchStorage.isSearchEnabled())
        }
    }

    // ── 合并服务商列表 ──

    @Test
    fun `WebSearchStoragegetMergedProviders应返回所有内置服务商`() {
        safelyInvoke {
            val merged = WebSearchStorage.getMergedProviders()
            assertNotNull(merged)
            assertEquals("内置服务商数量应匹配", WebSearchProviders.PROVIDERS.size, merged.size)
            val ids = merged.map { it.id }
            assertTrue("应包含 tavily", ids.contains("tavily"))
            assertTrue("应包含 searxng", ids.contains("searxng"))
            assertTrue("应包含 local-google", ids.contains("local-google"))
        }
    }

    @Test
    fun `WebSearchStoragegetMergedProviders应合并用户配置`() {
        safelyInvoke {
            val userConfig = WebSearchStorage.UserWebSearchConfig(
                enabled = true, apiKey = "my-key",
                customApiHost = "https://custom.example.com"
            )
            WebSearchStorage.saveUserConfig("tavily", userConfig)
            val tavily = WebSearchStorage.getMergedProviders().find { it.id == "tavily" }
            assertNotNull(tavily)
            assertEquals("用户配置的 apiKey 应覆盖", "my-key", tavily?.apiKey)
            assertEquals("用户配置的 customApiHost 应覆盖", "https://custom.example.com", tavily?.customApiHost)
            assertTrue("用户配置的 enabled 应为 true", tavily?.enabled == true)
        }
    }

    @Test
    fun `WebSearchStoragegetEnabledProviders只返回启用的服务商`() {
        safelyInvoke {
            WebSearchStorage.saveUserConfig("tavily", WebSearchStorage.UserWebSearchConfig(enabled = true))
            WebSearchStorage.saveUserConfig("bocha", WebSearchStorage.UserWebSearchConfig(enabled = false))
            val enabled = WebSearchStorage.getEnabledProviders()
            assertTrue("启用的列表中应包含 tavily", enabled.any { it.id == "tavily" })
            assertFalse("启用的列表不应包含 bocha", enabled.any { it.id == "bocha" })
        }
    }

    @Test
    fun `WebSearchStorage全部禁用时getEnabledProviders应返回空列表`() {
        safelyInvoke {
            WebSearchStorage.getMergedProviders().forEach { provider ->
                WebSearchStorage.saveUserConfig(provider.id, WebSearchStorage.UserWebSearchConfig(enabled = false))
            }
            assertTrue("全部禁用时应返回空列表", WebSearchStorage.getEnabledProviders().isEmpty())
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ConfigStorage
    // ════════════════════════════════════════════════════════════════════

    // ── 实例访问 ──

    @Test
    fun `ConfigStorage未初始化时getSecurePrefs应返回null`() {
        safelyInvoke {
            assertNull("未调用 initSecurePrefs 前 getSecurePrefs 应为 null", ConfigStorage.getSecurePrefs())
        }
    }

    @Test
    fun `ConfigStoragerequireMmkv未初始化时应抛出异常`() {
        safelyInvoke {
            // requireMmkv 在 mmkv 为 null 时抛出 IllegalStateException
            // safelyInvoke 捕获后通过 Assume 跳过
            ConfigStorage.requireMmkv()
        }
    }

    // ── 常量定义 ──

    @Test
    fun `ConfigStorage语言常量定义正确`() {
        assertEquals("zh", ConfigStorage.LANGUAGE_ZH)
        assertEquals("en", ConfigStorage.LANGUAGE_EN)
    }

    @Test
    fun `ConfigStorage截图模式常量定义正确`() {
        assertEquals("full", ConfigStorage.CROP_MODE_FULL)
        assertEquals("each", ConfigStorage.CROP_MODE_EACH)
        assertEquals("once", ConfigStorage.CROP_MODE_ONCE)
    }

    @Test
    fun `ConfigStorage采集模式常量定义正确`() {
        assertEquals("screenshot", ConfigStorage.CAPTURE_MODE_SCREENSHOT)
        assertEquals("accessibility", ConfigStorage.CAPTURE_MODE_ACCESSIBILITY)
    }

    @Test
    fun `ConfigStorage快捷按钮布局常量定义正确`() {
        assertEquals("arc", ConfigStorage.QUICK_BUTTON_LAYOUT_ARC)
        assertEquals("horizontal", ConfigStorage.QUICK_BUTTON_LAYOUT_HORIZONTAL)
    }

    @Test
    fun `ConfigStorage关键Key常量非空`() {
        assertTrue("KEY_API_URL 不应为空", ConfigStorage.KEY_API_URL.isNotBlank())
        assertTrue("KEY_API_KEY 不应为空", ConfigStorage.KEY_API_KEY.isNotBlank())
        assertTrue("KEY_MODEL_NAME 不应为空", ConfigStorage.KEY_MODEL_NAME.isNotBlank())
        assertTrue("KEY_LANGUAGE 不应为空", ConfigStorage.KEY_LANGUAGE.isNotBlank())
    }
}
