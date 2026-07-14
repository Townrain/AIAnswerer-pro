package com.hwb.aianswerer.utils

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.*
import org.junit.Test

/**
 * ModelWhitelistUpdater 单元测试
 *
 * 覆盖：
 * - CheckResult 和 ProviderConfig 数据类（纯 Kotlin，无 Android 依赖）
 * - checkUpdate() 通过 safelyInvoke 包装（需要 AppConfig/MMKV 运行时，JVM 测试会自动跳过）
 */
class ModelWhitelistUpdaterTest {

    // ── CheckResult 数据类 ──

    @Test
    fun `CheckResult - 默认成功结果构建`() {
        val result = ModelWhitelistUpdater.CheckResult(
            success = true,
            message = "更新成功"
        )
        assertTrue(result.success)
        assertEquals("更新成功", result.message)
        assertEquals(0, result.visionCount)
        assertEquals(0, result.excludedCount)
        assertEquals(0, result.totalCount)
        assertEquals(0, result.languageCount)
        assertEquals(0, result.providerCount)
        assertEquals(0, result.newProviderCount)
        assertEquals(0, result.newModelCount)
    }

    @Test
    fun `CheckResult - 完整字段构建`() {
        val result = ModelWhitelistUpdater.CheckResult(
            success = true,
            message = "更新成功",
            visionCount = 5,
            excludedCount = 2,
            totalCount = 50,
            languageCount = 45,
            providerCount = 52,
            newProviderCount = 3,
            newModelCount = 10
        )
        assertTrue(result.success)
        assertEquals(5, result.visionCount)
        assertEquals(2, result.excludedCount)
        assertEquals(50, result.totalCount)
        assertEquals(45, result.languageCount)
        assertEquals(52, result.providerCount)
        assertEquals(3, result.newProviderCount)
        assertEquals(10, result.newModelCount)
    }

    @Test
    fun `CheckResult - 失败结果构建`() {
        val result = ModelWhitelistUpdater.CheckResult(
            success = false,
            message = "网络连接失败，请检查网络"
        )
        assertFalse(result.success)
        assertEquals("网络连接失败，请检查网络", result.message)
    }

    @Test
    fun `CheckResult - copy() 修改部分字段`() {
        val original = ModelWhitelistUpdater.CheckResult(
            success = true, message = "成功",
            visionCount = 10
        )
        val modified = original.copy(success = false, message = "失败")
        assertTrue(original.success)
        assertFalse(modified.success)
        assertEquals("失败", modified.message)
        assertEquals(10, modified.visionCount)  // 未修改
    }

    @Test
    fun `CheckResult - equals和hashCode`() {
        val a = ModelWhitelistUpdater.CheckResult(true, "msg", visionCount = 3)
        val b = ModelWhitelistUpdater.CheckResult(true, "msg", visionCount = 3)
        val c = ModelWhitelistUpdater.CheckResult(false, "msg")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ── ProviderConfig 数据类 ──

    @Test
    fun `ProviderConfig - 完整构建`() {
        val config = ModelWhitelistUpdater.ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            type = "openai",
            apiHost = "https://api.deepseek.com"
        )
        assertEquals("deepseek", config.id)
        assertEquals("DeepSeek", config.name)
        assertEquals("openai", config.type)
        assertEquals("https://api.deepseek.com", config.apiHost)
    }

    @Test
    fun `ProviderConfig - 最小字段构建`() {
        val config = ModelWhitelistUpdater.ProviderConfig(
            id = "custom",
            name = "Custom",
            type = "openai",
            apiHost = ""
        )
        assertEquals("custom", config.id)
        assertEquals("", config.apiHost)
    }

    @Test
    fun `ProviderConfig - copy() 修改字段`() {
        val original = ModelWhitelistUpdater.ProviderConfig(
            id = "openai", name = "OpenAI", type = "openai",
            apiHost = "https://api.openai.com"
        )
        val modified = original.copy(
            name = "Custom OpenAI",
            apiHost = "https://custom.openai.com"
        )
        assertEquals("openai", modified.id)       // 未修改
        assertEquals("Custom OpenAI", modified.name)
        assertEquals("https://custom.openai.com", modified.apiHost)
    }

    // ── checkUpdate Android 依赖测试 ──

    @Test
    fun `checkUpdate - 通过safelyInvoke调用`() {
        // checkUpdate 是 suspend 函数，需要 AppConfig/MMKV Android 运行时
        // 在 JVM 环境下通过 safelyInvoke 自动跳过
        val result = safelyInvoke {
            kotlinx.coroutines.runBlocking {
                ModelWhitelistUpdater.checkUpdate()
            }
        }
        // 如果执行到这里，说明有 Android 运行时
        assertNotNull(result)
    }
}
