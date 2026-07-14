package com.hwb.aianswerer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 重构审查回归测试 — A1 到 A8 的修复验证。
 * 每个测试编码修复后的期望状态。当前 RED = 修复尚未实施。
 */
class PostRefactorBugRegressionTest {

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
        fail("源文件不存在: $relativePath")
        return ""
    }

    private val servicePath = "app/src/main/java/com/hwb/aianswerer/FloatingWindowService.kt"
    private val managerPath = "app/src/main/java/com/hwb/aianswerer/FloatingWindowManager.kt"
    private val captureHandlerPath = "app/src/main/java/com/hwb/aianswerer/CaptureHandler.kt"
    private val recordingCoordinatorPath = "app/src/main/java/com/hwb/aianswerer/RecordingCoordinator.kt"

    // ════ A1: VLM semaphore 内不应调用 OCR 降级 ════

    @Test fun `A1 recordingProcessWithOcr 不在 vlmSemaphore withPermit 内部`() {
        val src = readSource(recordingCoordinatorPath)
        val method = src.substringAfter("private suspend fun processWithVlm")
            .substringBefore("private fun dedupeAndTrack")
        val idx = method.indexOf("withPermit {")
        if (idx < 0) return
        var depth = 0
        var permitEnd = -1
        for (i in idx until method.length) {
            when (method[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { permitEnd = i; break } }
            }
        }
        val insidePermit = method.substring(idx, permitEnd + 1)
        assertFalse("recordingProcessWithOcr 不应在 withPermit 块内部",
            insidePermit.contains("processWithOcr"))
    }

    // ════ A2: stopRecording 应先处理/取消 recordingJobs，再 null semaphore ════

    @Test fun `A2 stopRecording 先取消任务再置空 vlmSemaphore`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun stopRecording()")
            .substringBefore("private fun showRecordingResults")
        val semaphoreNullPos = method.indexOf("vlmSemaphore = null")
        val jobsCheckPos = method.indexOf("recordingJobs")
        if (semaphoreNullPos >= 0 && jobsCheckPos >= 0) {
            assertTrue("recordingJobs 处理应在 vlmSemaphore = null 之前",
                jobsCheckPos < semaphoreNullPos)
        }
    }

    // ════ A3: 移除死代码 questionTypes 字段 ════

    @Test fun `A3 questionTypes 死代码字段已移除`() {
        val src = readSource(servicePath)
        assertFalse("不应有未使用的 questionTypes 字段",
            src.contains("private var questionTypes"))
    }

    // ════ A4: 裁剪回调中 bitmap.recycle 有保护 ════

    @Test fun `A4 handleCroppedImage bitmap recycle present`() {
        val src = readSource(captureHandlerPath)
        val method = src.substringAfter("fun handleCroppedImage")
            .substringBefore("private fun launchCropActivity")
        val hasRecycle = method.contains("bitmap.recycle()")
        assertTrue("handleCroppedImage 应有 bitmap.recycle()", hasRecycle)
    }

    // ════ A5: startRecording 应取消 currentFetchJob ════

    @Test fun `A5 startRecording 取消 currentFetchJob`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun startRecording()")
            .substringBefore("private fun stopRecording()")
        assertTrue("startRecording 应包含 currentFetchJob?.cancel()",
            method.contains("currentFetchJob?.cancel()"))
    }

    // ════ A6: 录题失败应有可见反馈 ════

    @Test fun `A6 recordingFetchAnswer 失败路径有用户反馈`() {
        val src = readSource(recordingCoordinatorPath)
        val method = src.substringAfter("private fun fetchAnswer")
            .substringBefore("private fun storeAnswer")
        val hasFeedback = method.contains("failedCount") ||
                method.contains("onError") ||
                method.contains("statusMessage")
        assertTrue("录制失败路径应有用户可见反馈", hasFeedback)
    }

    // ════ A7: hasContent 应在 bitmap null 检查之后 ════

    @Test fun `A7 hasContent 在 bitmap null 检查之后`() {
        val src = readSource(captureHandlerPath)
        val method = src.substringAfter("fun handleCapture()")
            .substringBefore("// ── Normal mode")
        val hasContentPos = method.indexOf("callbacks.setHasContent(true)")
        val bitmapNullCheckPos = method.indexOf("bitmap == null")
        if (hasContentPos >= 0 && bitmapNullCheckPos >= 0) {
            assertTrue("hasContent = true 应在 bitmap == null 检查之后",
                bitmapNullCheckPos < hasContentPos)
        }
    }

    // ════ A8: updateLayout 无 screenW/screenH 不安全默认值 ════

    @Test fun `A8 updateLayout 无 screenW 和 screenH 不安全默认值`() {
        val src = readSource(managerPath)
        val method = src.substringAfter("fun updateLayout(")
            .substringBefore(") {")
        assertFalse("screenW 不应有 = 0f 默认值", method.contains("screenW: Float = 0f"))
        assertFalse("screenH 不应有 = 0f 默认值", method.contains("screenH: Float = 0f"))
    }
}
