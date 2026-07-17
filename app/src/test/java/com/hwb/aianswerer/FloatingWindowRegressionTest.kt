package com.hwb.aianswerer

import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.ui.components.FWDims
import com.hwb.aianswerer.ui.components.FWAnim
import com.hwb.aianswerer.ui.components.pillVisual
import com.hwb.aianswerer.ui.components.parseSections
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * 悬浮窗回归测试。
 *
 * 每个测试对应一个曾经出现并被修复的 bug。测试失败 = 回归。
 */
class FloatingWindowRegressionTest {

    private fun readSource(relativePath: String): String {
        val userDir = System.getProperty("user.dir") ?: "."
        // Try multiple resolution strategies:
        // 1. user.dir may be the project root (IDE runs, some CI setups)
        // 2. user.dir may be the app/ subproject (Gradle test worker default)
        // 3. parent of user.dir + relativePath (when user.dir is app/ subproject)
        val candidates = listOf(
            File(userDir, relativePath),                          // root + path
            File(File(userDir).parentFile ?: File("."), relativePath), // parent + path (for app/ subproject)
            File(relativePath)                                     // relative to CWD
        )
        for (candidate in candidates) {
            if (candidate.exists()) return candidate.readText()
        }
        fail("源文件不存在: $relativePath (尝试: ${candidates.joinToString { it.absolutePath}})")
        return ""
    }

    private val servicePath = "app/src/main/java/com/hwb/aianswerer/FloatingWindowService.kt"
    private val managerPath = "app/src/main/java/com/hwb/aianswerer/FloatingWindowManager.kt"
    private val windowContentPath = "app/src/main/java/com/hwb/aianswerer/ui/components/FloatingWindowContent.kt"

    // ════ 设计常量 ════


    @Test fun `FWDims 快捷按钮尺寸`() {
        assertEquals(40f, FWDims.quickBtnSize.value)
        assertEquals(18f, FWDims.quickBtnIconSize.value)
        assertEquals(6f, FWDims.quickBtnSpacing.value)
        assertEquals(8f, FWDims.quickPanelGap.value)
    }


    @Test fun `FWAnim 动画参数`() {
        assertEquals(0.88f, FWAnim.bouncyScale, 0.001f)
        assertEquals(1200, FWAnim.shimmerDurationMs)
        assertEquals(1000L, FWAnim.longPressDurationMs)
    }

    // ════ pillVisual() ════

    @Test fun `pillVisual Idle 无 badge`() {
        assertNull(pillVisual(FloatingStatus.Idle, false, false, false).badge)
    }

    @Test fun `pillVisual 录制中无 badge`() {
        assertNull(pillVisual(FloatingStatus.Idle, true, false, false).badge)
    }

    @Test fun `pillVisual Success badge`() {
        assertEquals("✓", pillVisual(FloatingStatus.Success, false, false, false).badge!!.first)
    }

    @Test fun `pillVisual Error badge`() {
        assertEquals("✗", pillVisual(FloatingStatus.Error, false, false, false).badge!!.first)
    }

    // ════ parseSections() ════

    @Test fun `parseSections 空文本`() {
        val r = parseSections("")
        assertEquals(1, r.size)
        assertEquals("", r[0].content)
    }

    @Test fun `parseSections 【答案】`() {
        val r = parseSections("【答案】\nB. 选项")
        assertTrue(r[0].isAnswer)
    }

    @Test fun `parseSections 【解析】`() {
        assertTrue(parseSections("【解析】\n解释内容")[0].isExplanation)
    }

    @Test fun `parseSections 完整格式`() {
        val r = parseSections("**答案**\nB\n\n**解析**\n解释\n\n**选项**\nA\nB\nC")
        assertTrue(r.size >= 3)
        assertNotNull(r.find { it.isAnswer })
        assertNotNull(r.find { it.isExplanation })
    }

}
