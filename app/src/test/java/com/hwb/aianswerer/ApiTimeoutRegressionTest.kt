package com.hwb.aianswerer

import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.vision.OpenAIVisionProvider
import org.junit.Assert.*
import org.junit.Test

/**
 * API 超时配置回归测试（BF-001, BF-002）。
 *
 * 验证 OkHttpClient 的 readTimeout 与 Kotlin 层的 withTimeout 对齐，
 * 防止 readTimeout 先于 withTimeout 触发导致 SocketTimeoutException。
 *
 * 超时常量已提取到对应类的 companion object 中，
 * 测试直接引用编译时常量，不再扫描源码文本。
 */
class ApiTimeoutRegressionTest {

    // ═══════════════════════════════════════════════════════════════════
    // BF-001: OpenAIClient readTimeout 与 withTimeout 对齐
    // readTimeout(60s) = withTimeout(60s)，避免 OkHttp 先于协程炸
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `BF-001 OpenAIClient readTimeout equals withTimeout`() {
        assertEquals(
            "readTimeout 必须与 withTimeout 对齐（秒→毫秒）",
            OpenAIClient.READ_TIMEOUT_SEC * 1_000,
            OpenAIClient.WITH_TIMEOUT_MS
        )
    }

    @Test
    fun `BF-001 OpenAIClient callTimeout greater than readTimeout`() {
        assertTrue(
            "callTimeout 必须 >= readTimeout，确保总调用时间足够",
            OpenAIClient.CALL_TIMEOUT_SEC >= OpenAIClient.READ_TIMEOUT_SEC
        )
    }

    @Test
    fun `BF-001 OpenAIClient readTimeout is positive`() {
        assertTrue("readTimeout 必须为正数", OpenAIClient.READ_TIMEOUT_SEC > 0)
    }

    @Test
    fun `BF-001 OpenAIClient withTimeout is positive`() {
        assertTrue("withTimeout 必须为正数", OpenAIClient.WITH_TIMEOUT_MS > 0)
    }

    @Test
    fun `BF-001 OpenAIClient connect timeout is less than read timeout`() {
        assertTrue(
            "connectTimeout 应小于 readTimeout",
            OpenAIClient.CONNECT_TIMEOUT_SEC < OpenAIClient.READ_TIMEOUT_SEC
        )
    }

    @Test
    fun `BF-001 OpenAIClient test timeout is less than main timeout`() {
        assertTrue(
            "testConnection 超时应小于 analyzeQuestion 超时",
            OpenAIClient.TEST_TIMEOUT_MS < OpenAIClient.WITH_TIMEOUT_MS
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // BF-002: OpenAIVisionProvider readTimeout 与 withTimeout 对齐
    // bug: readTimeout(30s) < withTimeout(60s), execute() 不可取消
    // fix: readTimeout→120s, callTimeout→130s, withTimeout→120s, enqueue替代execute
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `BF-002 VisionProvider readTimeout equals withTimeout`() {
        assertEquals(
            "readTimeout 必须与 withTimeout 对齐（秒→毫秒）",
            OpenAIVisionProvider.READ_TIMEOUT_SEC * 1_000,
            OpenAIVisionProvider.WITH_TIMEOUT_MS
        )
    }

    @Test
    fun `BF-002 VisionProvider callTimeout greater than readTimeout`() {
        assertTrue(
            "callTimeout 必须 >= readTimeout",
            OpenAIVisionProvider.CALL_TIMEOUT_SEC >= OpenAIVisionProvider.READ_TIMEOUT_SEC
        )
    }

    @Test
    fun `BF-002 VisionProvider readTimeout is 120s`() {
        assertEquals(
            "视觉模型需要更长超时：120 秒",
            120L,
            OpenAIVisionProvider.READ_TIMEOUT_SEC
        )
    }

    @Test
    fun `BF-002 VisionProvider withTimeout is 120s`() {
        assertEquals(
            "视觉模型 withTimeout 应为 120 秒",
            120_000L,
            OpenAIVisionProvider.WITH_TIMEOUT_MS
        )
    }

    @Test
    fun `BF-002 VisionProvider callTimeout is 130s`() {
        assertEquals(
            "视觉模型 callTimeout 应为 130 秒",
            130L,
            OpenAIVisionProvider.CALL_TIMEOUT_SEC
        )
    }

    @Test
    fun `BF-002 VisionProvider timeout alignment ensures cancel works`() {
        // 核心断言：withTimeout == readTimeout，确保协程先于 OkHttp 超时
        // 这样协程取消时可以通过 call.cancel() 中断 HTTP 连接
        assertEquals(
            "withTimeout 必须等于 readTimeout，保证协程取消先于 OkHttp 超时",
            OpenAIVisionProvider.WITH_TIMEOUT_MS,
            OpenAIVisionProvider.READ_TIMEOUT_SEC * 1_000
        )
    }
}
