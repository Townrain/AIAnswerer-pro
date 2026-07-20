package com.hwb.aianswerer

import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.ui.components.FWDims
import com.hwb.aianswerer.ui.components.FWAnim
import com.hwb.aianswerer.ui.components.pillVisual
import com.hwb.aianswerer.ui.components.parseSections
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import androidx.compose.ui.graphics.Color
import com.hwb.aianswerer.ui.theme.Th
import java.io.File

/**
 * 悬浮窗回归测试。
 *
 * 每个测试对应一个曾经出现并被修复的 bug。测试失败 = 回归。
 */
class FloatingWindowRegressionTest {
    private val c = Color(0xFF888888.toInt())

    private fun testTh(light: Boolean = true): Th = Th(
        c, c, c, c, c,  // bg1-5
        c, c, c, c, c,  // p, pe, pd, pc, opc
        c,              // ok
        c, c,           // ob, osv
        c, c, c,        // gt, gb, gdp
        c, c,           // ht, hdp
        c, c, c, c, c, c, // ac, ua, ual, to, err, w
        light           // isLight
    )

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
        assertNull(pillVisual(FloatingStatus.Idle, false, false, testTh()).badge)
    }

    @Test fun `pillVisual 录制中无 badge`() {
        assertNull(pillVisual(FloatingStatus.Idle, true, false, testTh()).badge)
    }

    @Test fun `pillVisual Success badge`() {
        assertEquals("\u2713", pillVisual(FloatingStatus.Success, false, false, testTh()).badge!!.first)
    }

    @Test fun `pillVisual Error badge`() {
        assertEquals("\u2717", pillVisual(FloatingStatus.Error, false, false, testTh()).badge!!.first)
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

    // ════ resolvePillClickAction() ════

    @Test fun `resolvePillClickAction 面板关闭时返回 CaptureOnly`() {
        assertEquals(
            com.hwb.aianswerer.ui.components.PillClickAction.CaptureOnly,
            com.hwb.aianswerer.ui.components.resolvePillClickAction(expandQuickButtons = false, isRecording = false)
        )
    }

    @Test fun `resolvePillClickAction 面板关闭且录制中仍返回 CaptureOnly`() {
        assertEquals(
            com.hwb.aianswerer.ui.components.PillClickAction.CaptureOnly,
            com.hwb.aianswerer.ui.components.resolvePillClickAction(expandQuickButtons = false, isRecording = true)
        )
    }

    @Test fun `resolvePillClickAction 面板展开且非录制返回 QuickToggleOnly`() {
        assertEquals(
            com.hwb.aianswerer.ui.components.PillClickAction.QuickToggleOnly,
            com.hwb.aianswerer.ui.components.resolvePillClickAction(expandQuickButtons = true, isRecording = false)
        )
    }

    @Test fun `resolvePillClickAction 面板展开且录制中返回 QuickToggleAndCapture`() {
        assertEquals(
            com.hwb.aianswerer.ui.components.PillClickAction.QuickToggleAndCapture,
            com.hwb.aianswerer.ui.components.resolvePillClickAction(expandQuickButtons = true, isRecording = true)
        )
    }

    // ════ computeButtonScale() ════

    @Test fun `computeButtonScale 默认比例`() {
        assertEquals(1.0f, com.hwb.aianswerer.ui.components.computeButtonScale(1f, 1f, 1f), 0.001f)
    }

    @Test fun `computeButtonScale 拖拽中缩放`() {
        assertEquals(1.05f, com.hwb.aianswerer.ui.components.computeButtonScale(1f, 1.05f, 1f), 0.001f)
    }

    @Test fun `computeButtonScale 录制脉冲缩小`() {
        assertEquals(0.85f, com.hwb.aianswerer.ui.components.computeButtonScale(1f, 1f, 0.85f), 0.001f)
    }

    @Test fun `computeButtonScale 成功弹跳+录制脉冲`() {
        assertEquals(1.03f * 0.85f, com.hwb.aianswerer.ui.components.computeButtonScale(1.03f, 1f, 0.85f), 0.001f)
    }
}
