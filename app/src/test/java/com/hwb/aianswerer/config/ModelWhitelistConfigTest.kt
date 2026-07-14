package com.hwb.aianswerer.config

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ModelWhitelistConfig 单元测试
 *
 * 所有方法均通过 ConfigStorage.requireMmkv() 存取 MMKV，在纯 JVM 环境中
 * 不可用。所有测试使用 safelyInvoke 包裹，在 Android 运行时缺失时跳过。
 */
class ModelWhitelistConfigTest {

    @Test
    fun `ModelWhitelistConfig动态视觉模型保存和读取应一致`() {
        safelyInvoke {
            val models = listOf("deepseek-vision", "gpt-4-vision-preview", "claude-3-vision")
            ModelWhitelistConfig.saveDynamicVisionModels(models)
            val retrieved = ModelWhitelistConfig.getDynamicVisionModels()
            assertEquals(models, retrieved)
        }
    }

    @Test
    fun `ModelWhitelistConfig动态视觉模型空列表保存后应返回空`() {
        safelyInvoke {
            ModelWhitelistConfig.saveDynamicVisionModels(emptyList())
            val retrieved = ModelWhitelistConfig.getDynamicVisionModels()
            assertTrue(retrieved.isEmpty())
        }
    }

    @Test
    fun `ModelWhitelistConfig动态视觉排除列表保存和读取应一致`() {
        safelyInvoke {
            val excluded = listOf("gpt-4-base", "legacy-model")
            ModelWhitelistConfig.saveDynamicVisionExcluded(excluded)
            val retrieved = ModelWhitelistConfig.getDynamicVisionExcluded()
            assertEquals(excluded, retrieved)
        }
    }

    @Test
    fun `ModelWhitelistConfig动态厂商模型Map保存和读取应一致`() {
        safelyInvoke {
            val providerModels = mapOf(
                "deepseek" to listOf("deepseek-chat", "deepseek-coder"),
                "openai" to listOf("gpt-4", "gpt-3.5-turbo")
            )
            ModelWhitelistConfig.saveDynamicProviderModels(providerModels)
            val retrieved = ModelWhitelistConfig.getDynamicProviderModels()
            assertEquals(providerModels, retrieved)
        }
    }

    @Test
    fun `ModelWhitelistConfig动态厂商模型空Map保存后应返回空`() {
        safelyInvoke {
            ModelWhitelistConfig.saveDynamicProviderModels(emptyMap())
            val retrieved = ModelWhitelistConfig.getDynamicProviderModels()
            assertTrue(retrieved.isEmpty())
        }
    }

    @Test
    fun `ModelWhitelistConfig单个Provider模型应正确读写`() {
        safelyInvoke {
            val single = mapOf("test-provider" to listOf("test-model-v1"))
            ModelWhitelistConfig.saveDynamicProviderModels(single)
            val byId = ModelWhitelistConfig.getDynamicProviderModels("test-provider")
            assertEquals(listOf("test-model-v1"), byId)
        }
    }

    @Test
    fun `ModelWhitelistConfig不存在的providerId应返回空列表`() {
        safelyInvoke {
            // 明确保存一个已知值，再查不存在的 key
            ModelWhitelistConfig.saveDynamicProviderModels(mapOf("existing" to listOf("m1")))
            val result = ModelWhitelistConfig.getDynamicProviderModels("non-existent-provider")
            assertTrue(result.isEmpty())
        }
    }
}
