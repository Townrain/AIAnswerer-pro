package com.hwb.aianswerer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * API 超时配置回归测试（BF-001, BF-002）。
 *
 * 验证 OkHttpClient 的 readTimeout 与 Kotlin 层的 withTimeout 对齐，
 * 防止 readTimeout 先于 withTimeout 触发导致 SocketTimeoutException。
 * 每个测试对应一条曾被修复的超时不匹配问题。
 */
class ApiTimeoutRegressionTest {

    private fun readSource(relativePath: String): String {
        val userDir = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(userDir, relativePath),
            File(File(userDir).parentFile ?: File("."), relativePath),
            File(relativePath)
        )
        for (candidate in candidates) {
            if (candidate.exists()) return candidate.readText()
        }
        fail("源文件不存在: $relativePath (尝试: ${candidates.joinToString { it.absolutePath}})")
        return ""
    }

    private val openAiClientPath = "app/src/main/java/com/hwb/aianswerer/api/OpenAIClient.kt"
    private val visionProviderPath = "app/src/main/java/com/hwb/aianswerer/api/vision/OpenAIVisionProvider.kt"

    // ═══════════════════════════════════════════════════════════════════
    // BF-001: OpenAIClient readTimeout 与 withTimeout 对齐
    // bug: readTimeout(60s) < withTimeout(120s) → HTTP/2 60s 先炸
    // fix: readTimeout→120s, callTimeout→130s, stream=true + awaitStreamContent
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `BF-001 OpenAIClient readTimeout 180s`() {
        val src = readSource(openAiClientPath)
        val clientBlock = src.substringAfter("private val client: OkHttpClient by lazy {")
            .substringBefore(".build()")
        assertTrue("readTimeout 必须为 180", clientBlock.contains("readTimeout(180"))
    }

    @Test fun `BF-001 OpenAIClient callTimeout 190s`() {
        val src = readSource(openAiClientPath)
        val clientBlock = src.substringAfter("private val client: OkHttpClient by lazy {")
            .substringBefore(".build()")
        assertTrue("callTimeout 必须为 190", clientBlock.contains("callTimeout(190"))
    }

    @Test fun `BF-001 OpenAIClient stream 为 true`() {
        val src = readSource(openAiClientPath)
        val analyzeBlock = src.substringAfter("fun analyzeQuestion(")
            .substringBefore("fun countQuestions(")
        assertTrue("stream 必须为 true", analyzeBlock.contains("stream = true"))
    }

    @Test fun `BF-001 OpenAIClient awaitStreamContent 存在`() {
        val src = readSource(openAiClientPath)
        assertTrue("awaitStreamContent 方法必须存在",
            src.contains("private suspend fun Call.awaitStreamContent():"))
    }

    @Test fun `BF-001 OpenAIClient analyzeQuestion 调用 awaitStreamContent`() {
        val src = readSource(openAiClientPath)
        val analyzeBlock = src.substringAfter("fun analyzeQuestion(")
            .substringBefore("fun countQuestions(")
        assertTrue("analyzeQuestion 必须调用 awaitStreamContent",
            analyzeBlock.contains("awaitStreamContent()"))
    }

    @Test fun `BF-001 OpenAIClient withTimeout 为 180s`() {
        val src = readSource(openAiClientPath)
        val analyzeBlock = src.substringAfter("fun analyzeQuestion(")
            .substringBefore("fun countQuestions(")
        assertTrue("withTimeout 必须为 180_000L", analyzeBlock.contains("withTimeout(180_000L)"))
    }

    @Test fun `BF-001 OpenAIClient readTimeout 与 withTimeout 一致`() {
        val src = readSource(openAiClientPath)
        val clientBlock = src.substringAfter("private val client: OkHttpClient by lazy {")
            .substringBefore(".build()")
        val analyzeBlock = src.substringAfter("fun analyzeQuestion(")
            .substringBefore("fun countQuestions(")
        assertTrue("readTimeout 与 withTimeout 必须一致为 180s",
            clientBlock.contains("readTimeout(180") && analyzeBlock.contains("withTimeout(180_000L)"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // BF-002: OpenAIVisionProvider readTimeout 与 withTimeout 对齐
    // bug: readTimeout(30s) < withTimeout(60s), execute() 不可取消
    // fix: readTimeout→120s, callTimeout→130s, withTimeout→120s, enqueue替代execute
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `BF-002 VisionProvider readTimeout 120s`() {
        val src = readSource(visionProviderPath)
        val clientBlock = src.substringAfter("private val client: OkHttpClient by lazy {")
            .substringBefore(".build()")
        assertTrue("readTimeout 必须为 120", clientBlock.contains("readTimeout(120"))
    }

    @Test fun `BF-002 VisionProvider callTimeout 130s`() {
        val src = readSource(visionProviderPath)
        val clientBlock = src.substringAfter("private val client: OkHttpClient by lazy {")
            .substringBefore(".build()")
        assertTrue("callTimeout 必须为 130", clientBlock.contains("callTimeout(130"))
    }

    @Test fun `BF-002 VisionProvider withTimeout 120s`() {
        val src = readSource(visionProviderPath)
        val analyzeBlock = src.substringAfter("override suspend fun analyze(")
            .substringBefore("override fun validateConfig(")
        assertTrue("withTimeout 必须为 120_000L", analyzeBlock.contains("withTimeout(120_000L)"))
    }

    @Test fun `BF-002 VisionProvider 使用 enqueue 而非 execute`() {
        val src = readSource(visionProviderPath)
        val analyzeBlock = src.substringAfter("override suspend fun analyze(")
            .substringBefore("override fun validateConfig(")
        assertTrue("必须使用 call.enqueue（可取消）", analyzeBlock.contains("call.enqueue("))
        assertFalse("不得使用 call.execute()（不可取消）", analyzeBlock.contains(".execute()"))
    }

    @Test fun `BF-002 VisionProvider suspendCancellableCoroutine 存在`() {
        val src = readSource(visionProviderPath)
        val analyzeBlock = src.substringAfter("override suspend fun analyze(")
            .substringBefore("override fun validateConfig(")
        assertTrue("必须有 suspendCancellableCoroutine",
            analyzeBlock.contains("suspendCancellableCoroutine"))
    }

    @Test fun `BF-002 VisionProvider invokeOnCancellation 调用 cancel`() {
        val src = readSource(visionProviderPath)
        val analyzeBlock = src.substringAfter("override suspend fun analyze(")
            .substringBefore("override fun validateConfig(")
        assertTrue("invokeOnCancellation 必须调用 call.cancel()",
            analyzeBlock.contains("invokeOnCancellation") && analyzeBlock.contains("call.cancel()"))
    }
}
