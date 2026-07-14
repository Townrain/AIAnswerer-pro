package com.hwb.aianswerer.providers

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.*
import org.junit.Test

/**
 * ProviderStorage 单元测试
 *
 * ProviderStorage 使用 MMKV 存储云端厂商数据、同步元数据、用户配置。
 * 所有方法依赖 Android MMKV 运行时，通过 safelyInvoke 包裹使其在 JVM 上优雅跳过。
 */
class ProviderStorageTest {

    // ── ProviderDataJson 保存/读取 ──

    @Test
    fun `ProviderStorage保存Provider后读取应一致`() {
        safelyInvoke {
            val data = ProviderDataJson(
                version = 1,
                generatedAt = "2025-07-14T00:00:00Z",
                providerCount = 2,
                modelCount = 3,
                providers = listOf(
                    ProviderEntry(
                        id = "openai", name = "OpenAI", type = "openai",
                        apiHost = "https://api.openai.com", anthropicApiHost = null,
                        models = listOf(ModelEntry("gpt-4", "GPT-4", "chat")),
                        websites = WebsiteInfo("https://openai.com", null, null, null)
                    ),
                    ProviderEntry(
                        id = "deepseek", name = "DeepSeek", type = "openai",
                        apiHost = "https://api.deepseek.com", anthropicApiHost = null,
                        models = listOf(ModelEntry("deepseek-chat", "DeepSeek Chat", "chat")),
                        websites = WebsiteInfo("https://deepseek.com", null, null, null)
                    )
                )
            )
            ProviderStorage.saveProviderData(data)
            val loaded = ProviderStorage.getProviderData()
            assertNotNull(loaded)
            assertEquals(1, loaded?.version)
            assertEquals(2, loaded?.providerCount)
            assertEquals("openai", loaded?.providers?.get(0)?.id)
            assertEquals("deepseek", loaded?.providers?.get(1)?.id)
        }
    }

    @Test
    fun `ProviderStorage保存后版本号应正确`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            val data = ProviderDataJson(
                version = 3, generatedAt = "", providerCount = 0, modelCount = 0,
                providers = emptyList()
            )
            ProviderStorage.saveProviderData(data)
            assertEquals(3, ProviderStorage.getDataVersion())
        }
    }

    @Test
    fun `ProviderStorage清除后getProviderData应返回null`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            assertNull(ProviderStorage.getProviderData())
            assertEquals(0, ProviderStorage.getDataVersion())
        }
    }

    // ── 同步元数据 ──

    @Test
    fun `ProviderStorage保存同步元数据后读取应一致`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            ProviderStorage.saveSyncMeta("etag-abc", "2025-07-14T10:00:00Z")
            assertEquals("etag-abc", ProviderStorage.getSyncEtag())
            assertEquals("2025-07-14T10:00:00Z", ProviderStorage.getSyncLastModified())
            assertTrue("时间戳应大于 0", ProviderStorage.getSyncTimestamp() > 0)
        }
    }

    @Test
    fun `ProviderStorage清除后同步元数据应丢失`() {
        safelyInvoke {
            ProviderStorage.saveSyncMeta("etag-keep", "lastmod-keep")
            ProviderStorage.clearAll()
            assertNull("清除后 etag 应为空", ProviderStorage.getSyncEtag())
            assertEquals("清除后时间戳应为 0", 0L, ProviderStorage.getSyncTimestamp())
        }
    }

    @Test
    fun `ProviderStorage未保存etag时getSyncEtag应返回null`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            assertNull(ProviderStorage.getSyncEtag())
            assertNull(ProviderStorage.getSyncLastModified())
            assertEquals(0L, ProviderStorage.getSyncTimestamp())
        }
    }

    // ── UserProviderConfig ──

    @Test
    fun `ProviderStorageUserProviderConfig默认值应正确`() {
        val config = ProviderStorage.UserProviderConfig()
        assertFalse(config.enabled)
        assertNull(config.customApiHost)
        assertNull(config.customAnthropicApiHost)
        assertTrue(config.selectedModels.isEmpty())
        assertTrue(config.availableModels.isEmpty())
    }

    @Test
    fun `ProviderStorageUserProviderConfig自定义值应保持`() {
        val config = ProviderStorage.UserProviderConfig(
            enabled = true,
            customApiHost = "https://custom.api.com",
            customAnthropicApiHost = "https://custom.anthropic.com",
            selectedModels = listOf("gpt-4", "claude-3"),
            availableModels = listOf("gpt-4", "gpt-5")
        )
        assertTrue(config.enabled)
        assertEquals("https://custom.api.com", config.customApiHost)
        assertEquals("https://custom.anthropic.com", config.customAnthropicApiHost)
        assertEquals(listOf("gpt-4", "claude-3"), config.selectedModels)
        assertEquals(2, config.availableModels.size)
    }

    @Test
    fun `ProviderStorage保存用户配置后读取应一致`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            val config = ProviderStorage.UserProviderConfig(
                enabled = true,
                customApiHost = "https://my-proxy.com",
                selectedModels = listOf("deepseek-chat", "deepseek-reasoner")
            )
            ProviderStorage.saveUserConfig("deepseek", config)
            val loaded = ProviderStorage.getUserConfig("deepseek")
            assertTrue(loaded.enabled)
            assertEquals("https://my-proxy.com", loaded.customApiHost)
            assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), loaded.selectedModels)
        }
    }

    @Test
    fun `ProviderStorage未保存的provider应返回默认配置`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            val config = ProviderStorage.getUserConfig("nonexistent-provider")
            assertFalse("不存在的 provider 应返回默认 enabled=false", config.enabled)
            assertNull("不存在的 provider 应返回默认 customApiHost=null", config.customApiHost)
            assertTrue("不存在的 provider 应返回空 selectedModels", config.selectedModels.isEmpty())
        }
    }

    @Test
    fun `ProviderStorage两个provider的用户配置互不干扰`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            val configA = ProviderStorage.UserProviderConfig(enabled = true, selectedModels = listOf("gpt-4"))
            val configB = ProviderStorage.UserProviderConfig(enabled = false, selectedModels = listOf("claude-3"))
            ProviderStorage.saveUserConfig("openai", configA)
            ProviderStorage.saveUserConfig("anthropic", configB)

            val loadedA = ProviderStorage.getUserConfig("openai")
            val loadedB = ProviderStorage.getUserConfig("anthropic")
            assertTrue(loadedA.enabled)
            assertFalse(loadedB.enabled)
            assertEquals(listOf("gpt-4"), loadedA.selectedModels)
            assertEquals(listOf("claude-3"), loadedB.selectedModels)
        }
    }

    // ── 合并列表 ──

    @Test
    fun `ProviderStorage无数据时getMergedProviders应返回空列表`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            val merged = ProviderStorage.getMergedProviders()
            assertNotNull(merged)
            assertTrue("无数据时应返回空列表", merged.isEmpty())
        }
    }

    @Test
    fun `ProviderStorage无数据时getEnabledProviders应返回空列表`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            assertTrue(ProviderStorage.getEnabledProviders().isEmpty())
        }
    }

    @Test
    fun `ProviderStorage无数据时isAnyProviderConfigured应返回false`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            assertFalse(ProviderStorage.isAnyProviderConfigured())
        }
    }

    @Test
    fun `ProviderStorage无数据时getProvider应返回null`() {
        safelyInvoke {
            ProviderStorage.clearAll()
            assertNull(ProviderStorage.getProvider("openai"))
        }
    }

    // ── 清除 ──

    @Test
    fun `ProviderStorageclearAll后ProviderData和用户配置应全部清除`() {
        safelyInvoke {
            val data = ProviderDataJson(
                version = 1, generatedAt = "2025-01-01T00:00:00Z",
                providerCount = 1, modelCount = 1,
                providers = listOf(ProviderEntry(
                    id = "test", name = "Test", type = "openai",
                    apiHost = "", anthropicApiHost = null,
                    models = emptyList(), websites = null
                ))
            )
            ProviderStorage.saveProviderData(data)
            ProviderStorage.saveUserConfig("test", ProviderStorage.UserProviderConfig(enabled = true))
            ProviderStorage.saveSyncMeta("etag", "lastmod")

            ProviderStorage.clearAll()

            assertNull("clearAll 后 providerData 应为 null", ProviderStorage.getProviderData())
            assertEquals("clearAll 后 version 应为 0", 0, ProviderStorage.getDataVersion())
            assertFalse("clearAll 后 userConfig 的 enabled 应为 false",
                ProviderStorage.getUserConfig("test").enabled)
            assertNull("clearAll 后 syncEtag 应为 null", ProviderStorage.getSyncEtag())
        }
    }
}
