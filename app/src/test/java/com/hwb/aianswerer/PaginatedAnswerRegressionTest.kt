package com.hwb.aianswerer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 翻页答案回归测试（BF-004）。
 *
 * 验证 paginatedAnswers 状态在 handleAnswerSuccess 中被正确填充，
 * 以及在关闭/重置时被正确清空。防止多题结果挤在一页或翻页状态残留。
 */
class PaginatedAnswerRegressionTest {

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

    private val servicePath = "app/src/main/java/com/hwb/aianswerer/FloatingWindowService.kt"
    private val windowContentPath = "app/src/main/java/com/hwb/aianswerer/ui/components/FloatingWindowContent.kt"

    // ═══════════════════════════════════════════════════════════════════
    // BF-004: paginatedAnswers 状态管理
    // bug: 普通模式多题挤在 Card 组件全文本滚动，无翻页
    // fix: 新增 paginatedAnswers，handleAnswerSuccess 始终填充
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `BF-004 paginatedAnswers 状态声明存在`() {
        val src = readSource(servicePath)
        assertTrue("paginatedAnswers 必须声明",
            src.contains("private var paginatedAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())"))
    }

    @Test fun `BF-004 paginatedCopyTexts 状态声明存在`() {
        val src = readSource(servicePath)
        assertTrue("paginatedCopyTexts 必须声明",
            src.contains("private var paginatedCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())"))
    }

    @Test fun `BF-004 handleAnswerSuccess 填充 paginatedAnswers`() {
        val src = readSource(servicePath)
        val handleMethod = src.substringAfter("private suspend fun handleAnswerSuccess(")
            .substringBefore("private fun createNotificationChannel()")
        assertTrue("handleAnswerSuccess 必须填充 paginatedAnswers.value",
            handleMethod.contains("paginatedAnswers.value = aiAnswers.mapIndexed"))
    }

    @Test fun `BF-004 handleAnswerSuccess 填充 paginatedCopyTexts`() {
        val src = readSource(servicePath)
        val handleMethod = src.substringAfter("private suspend fun handleAnswerSuccess(")
            .substringBefore("private fun createNotificationChannel()")
        assertTrue("handleAnswerSuccess 必须填充 paginatedCopyTexts.value",
            handleMethod.contains("paginatedCopyTexts.value = aiAnswers.mapIndexed"))
    }

    @Test fun `BF-004 onCloseAnswer 清空 paginatedAnswers`() {
        val src = readSource(servicePath)
        val closeBlock = src.substringAfter("onCloseAnswer = {")
            .substringBefore("onCloseStatus = {")
        assertTrue("onCloseAnswer 必须清空 paginatedAnswers",
            closeBlock.contains("paginatedAnswers.value = emptyList()"))
    }

    @Test fun `BF-004 onCloseStatus 清空 paginatedAnswers`() {
        val src = readSource(servicePath)
        val closeBlock = src.substringAfter("onCloseStatus = {")
            .substringBefore("onCopyAnswer = {")
        assertTrue("onCloseStatus 必须清空 paginatedAnswers",
            closeBlock.contains("paginatedAnswers.value = emptyList()"))
    }

    @Test fun `BF-004 startRecording 清空 paginatedAnswers`() {
        val src = readSource(servicePath)
        val recordingBlock = src.substringAfter("private fun startRecording()")
            .substringBefore("private fun stopRecording()")
        assertTrue("startRecording 必须清空 paginatedAnswers",
            recordingBlock.contains("paginatedAnswers.value = emptyList()"))
    }

    @Test fun `BF-004 FloatingWindowContent 接受 paginatedAnswers 参数`() {
        val src = readSource(windowContentPath)
        val params = src.substringAfter("fun FloatingWindowContent(")
            .substringBefore(") {")
        assertTrue("FloatingWindowContent 必须有 paginatedAnswers 参数",
            params.contains("paginatedAnswers:"))
    }

    @Test fun `BF-004 UI 使用 paginatedAnswers 选择翻页卡片`() {
        val src = readSource(windowContentPath)
        val cardBlock = src.substringAfter("val displayAnswers = if")
            .substringBefore("if (displayAnswers.isNotEmpty())")
        assertTrue("必须检查 paginatedAnswers.isNotEmpty()",
            cardBlock.contains("paginatedAnswers.isNotEmpty()"))
    }

    @Test fun `BF-004 hasAnswer 包含 paginatedAnswers`() {
        val src = readSource(windowContentPath)
        assertTrue("hasAnswer 必须包含 paginatedAnswers",
            src.contains("val hasAnswer = answerText != null || recordingAnswers.isNotEmpty() || paginatedAnswers.isNotEmpty()"))
    }

    @Test fun `BF-004 updateFloatingWindowHeight 包含 paginatedAnswers`() {
        val src = readSource(servicePath)
        val heightBlock = src.substringAfter("private fun updateFloatingWindowHeight()")
            .substringBefore("private suspend fun performWebSearch(")
        assertTrue("窗口高度计算必须包含 paginatedAnswers",
            heightBlock.contains("paginatedAnswers.value.isNotEmpty()"))
    }
}
