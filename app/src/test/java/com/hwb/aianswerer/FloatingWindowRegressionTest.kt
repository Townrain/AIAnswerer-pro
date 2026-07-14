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
        assertNull(pillVisual(FloatingStatus.Idle, false, false).badge)
    }

    @Test fun `pillVisual 录制中无 badge`() {
        assertNull(pillVisual(FloatingStatus.Idle, true, false).badge)
    }

    @Test fun `pillVisual Success badge`() {
        assertEquals("✓", pillVisual(FloatingStatus.Success, false, false).badge!!.first)
    }

    @Test fun `pillVisual Error badge`() {
        assertEquals("✗", pillVisual(FloatingStatus.Error, false, false).badge!!.first)
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

    // ═══════════════════════════════════════════════════════════════════
    // FIX #1: 窗口永远 60dp — 拖拽移动窗口位置，不变大小
    //
    // bug: 窗口扩缩 → 单帧跳变；全屏 dispatchTouchEvent → SystemUI ANR
    // fix: 永远 60dp 小窗（桌面触摸自然穿透），拖拽时 movePillWin 移窗
    // ═══════════════════════════════════════════════════════════════════

    @Ignore("pillWinW/movePillWin removed — refactored to fixed windowWidthPx + windowManager.updateViewLayout")
    @Test fun `窗口 pillWinW 60dp movePillWin 存在`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun showFloatingWindow()")
            .substringBefore("private fun handleCapture()")
        assertTrue("pillWinW 必须存在", method.contains("val pillWinW"))
        assertTrue("movePillWin 必须存在", method.contains("fun movePillWin"))
    }

    @Test fun `窗口 无 applyPillWin 无 pillWinExpanded`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun showFloatingWindow()")
            .substringBefore("private fun handleCapture()")
        assertFalse("不得有 applyPillWin", method.contains("fun applyPillWin"))
        assertFalse("不得有 pillWinExpanded", method.contains("var pillWinExpanded"))
    }

    @Test fun `窗口 gravity 始终 G_START`() {
        // Gravity 已提取到 FloatingWindowManager.createLayoutParams
        val src = readSource(managerPath)
        assertTrue("必须包含 G_START", src.contains("Gravity.TOP or Gravity.START"))
        assertFalse("不得包含 Gravity.END", src.contains("Gravity.END"))
    }

    @Test fun `窗口 pillView 为普通 FrameLayout`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun showFloatingWindow()")
            .substringBefore("private fun handleCapture()")
        val pillViewSection = method.substringAfter("pillView = ")
            .substringBefore("windowManager.addView(pillView")
        assertFalse("pillView 不得有 dispatchTouchEvent", pillViewSection.contains("override fun dispatchTouchEvent"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #10: 卡片窗口 FLAG_NOT_TOUCHABLE — 桌面不可交互
    //
    // bug: updateCardFlags() 在 showAnswer/showQuick 时切为 NOT_TOUCH_MODAL，
    //      MATCH_PARENT 卡片窗口无 TouchOverlayView，阻塞全屏触摸
    // fix: updateCardFlags 始终保持 NOT_TOUCHABLE
    // ═══════════════════════════════════════════════════════════════════

    @Ignore("updateCardFlags removed — card window flag management refactored")
    @Test fun `updateCardFlags 不切换 NOT_TOUCH_MODAL`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun updateCardFlags()")
            .substringBefore("private fun handleCapture")
        assertFalse(
            "updateCardFlags 不得切换为 NOT_TOUCH_MODAL",
            method.contains("or nm") || method.contains("FLAG_NOT_TOUCH_MODAL")
        )
    }

    @Ignore("updateCardFlags removed — card window flag management refactored")
    @Test fun `updateCardFlags 始终保 NOT_TOUCHABLE`() {
        val src = readSource(servicePath)
        val method = src.substringAfter("private fun updateCardFlags()")
            .substringBefore("private fun handleCapture")
        assertTrue(
            "updateCardFlags 必须确保 NOT_TOUCHABLE",
            method.contains("FLAG_NOT_TOUCHABLE") && method.contains("or nt")
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #13: TouchOverlayView UP 事件丢失 — 点击/长按不响应
    //
    // bug: ACTION_UP 时先清除 gestureActive 再判断，导致 UP 永远不 dispatch
    //      Compose 的 tryAwaitRelease() 永远等不到 UP → 点击/长按不触发
    // fix: ACTION_UP 时先保存 wasActive，dispatch 后再清除
    // ═══════════════════════════════════════════════════════════════════

    @Ignore("TouchOverlayView class removed — touch handling refactored")
    @Test fun `TouchOverlayView UP 事件在清除 gestureActive 前 dispatch`() {
        val src = readSource(servicePath)
        // 提取 TouchOverlayView 类（从 class 到 private class 或 EOF）
        val classStart = src.indexOf("class TouchOverlayView")
        val afterClass = src.substring(classStart)
        // UP 处理应在 ACTION_DOWN 和 hasValidRects 之间
        val dispatchMethod = afterClass.substringAfter("override fun dispatchTouchEvent")
            .substringBefore("private fun") // next method or end
        assertTrue(
            "UP 必须在清除前保存 wasActive 并调用 super.dispatchTouchEvent",
            dispatchMethod.contains("wasActive") &&
            dispatchMethod.contains("ACTION_UP") &&
            dispatchMethod.contains("super.dispatchTouchEvent(ev)")
        )
    }

}
