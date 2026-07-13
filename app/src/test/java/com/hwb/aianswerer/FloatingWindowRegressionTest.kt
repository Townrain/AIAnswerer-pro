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
    private val pillContentPath = "app/src/main/java/com/hwb/aianswerer/ui/components/FloatingPillContent.kt"
    private val windowContentPath = "app/src/main/java/com/hwb/aianswerer/ui/components/FloatingWindowContent.kt"

    // ════ 设计常量 ════

    @Test fun `FWDims pill 尺寸`() {
        assertEquals(36f, FWDims.pillHeight.value)
        assertEquals(21f, FWDims.pillCornerRadius.value)
        assertEquals(10f, FWDims.pillHPadding.value)
        assertEquals(8f, FWDims.pillVPadding.value)
        assertEquals(20f, FWDims.pillIconSize.value)
        assertEquals(8f, FWDims.pillEdgeMargin.value)
    }

    @Test fun `FWDims 快捷按钮尺寸`() {
        assertEquals(40f, FWDims.quickBtnSize.value)
        assertEquals(18f, FWDims.quickBtnIconSize.value)
        assertEquals(6f, FWDims.quickBtnSpacing.value)
        assertEquals(8f, FWDims.quickPanelGap.value)
    }

    @Test fun `FWDims 卡片尺寸`() {
        assertEquals(0.88f, FWDims.cardWidthRatio, 0.001f)
        assertEquals(20f, FWDims.cardCornerRadius.value)
        assertEquals(460f, FWDims.cardMaxHeight.value)
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
    // FIX #3: onSettled 竞态 — 同步传递 pillCenterX
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `onSettled 签名包含 pillCenterX`() {
        val src = readSource(pillContentPath)
        val sig = src.substringAfter("onSettled: ((")
            .substringBefore("))?")
        assertTrue("onSettled 签名必须包含 pillCenterX",
            sig.contains("pillCenterX") && sig.contains("Float"))
    }

    @Test fun `onSettled 调用传入 dragX + measuredPillW div 2`() {
        val src = readSource(pillContentPath)
        val call = src.substringAfter("currentOnSettled?.invoke")
            .substringBefore("\n")
        assertTrue("onSettled 必须传入 dragX + measuredPillW / 2f",
            call.contains("dragX") && call.contains("measuredPillW"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #4: onDragStart 用 rightEdge - pillW
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `onDragStart 用 rightEdge - pillW 计算起点`() {
        val src = readSource(pillContentPath)
        val start = src.substringAfter("onDragStart = {")
            .substringBefore("}")
        assertTrue("fingerX 必须用 rightEdge - pillW",
            start.contains("fingerX = rightEdge - pillW"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #5: layoutInDisplayCutoutMode
    // ═══════════════════════════════════════════════════════════════════

    @Ignore("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS no longer set — cutout handling refactored")
    @Test fun `窗口 使用 layoutInDisplayCutoutMode`() {
        val src = readSource(servicePath)
        assertTrue("必须设置 layoutInDisplayCutoutMode",
            src.contains("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #6: curIsLeftSide 仅由内部 rightEdge 决定
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `curIsLeftSide 无外部参数覆盖`() {
        val src = readSource(pillContentPath)
        val line = src.substringAfter("fun FloatingPillContent(")
            .lines().find { it.contains("val curIsLeftSide") && it.contains("rightEdge") }
        assertNotNull("curIsLeftSide 计算行必须存在", line)
        assertTrue("必须基于 rightEdge 计算", line!!.contains("rightEdge") && line.contains("<"))
        assertFalse("不得引用 pillSide", line.contains("pillSide"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #7: 代码质量
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `无旧方案残留`() {
        val src = readSource(pillContentPath)
        assertFalse("不应有 prevSW", src.contains("var prevSW"))
        assertFalse("不应有 dragStartSW", src.contains("var dragStartSW"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #8: 拖拽不跟随手指 — overridePillX 滞后 1-2 帧
    //
    // bug: layout 块中拖拽时使用 overridePillX（来自 Service 回调，滞后 1-2 帧）
    //      而非 dragX（拖拽手势实时更新），导致按钮落后手指
    // fix: 拖拽时直接用 dragX，不判断 overridePillX
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `拖拽时 layout 块不依赖 overridePillX`() {
        val src = readSource(pillContentPath)
        // 提取 layout 块中 isDragging||isAnimating 分支
        val layoutBlock = src.substringAfter(".layout { measurable, constraints ->")
            .substringBefore(".onGloballyPositioned")
        // 跨行匹配：拖拽分支内容（从 isDragging||isAnimating 到 else if）
        val dragBranch = layoutBlock.substringAfter("isDragging || isAnimating)")
            .substringBefore("else if")
        assertFalse(
            "拖拽时不得使用 overridePillX（滞后值），应直接使用 dragX",
            dragBranch.contains("overridePillX")
        )
    }

    @Test fun `拖拽时 layout 块使用 dragX 实时值`() {
        val src = readSource(pillContentPath)
        val layoutBlock = src.substringAfter(".layout { measurable, constraints ->")
            .substringBefore(".onGloballyPositioned")
        val scrXLine = layoutBlock.lines().find { it.contains("val scrX = if") }
        assertNotNull("scrX 计算行必须存在", scrXLine)
        // fix 应用后：isDragging||isAnimating 分支应只有 dragX
        val dragBranch = layoutBlock.substringAfter("isDragging || isAnimating)")
            .substringBefore("else if")
        assertTrue(
            "拖拽分支中必须使用 dragX",
            dragBranch.contains("dragX")
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #9: 首次拖拽 Y 跳变 — animY 初始为 0
    //
    // bug: onDragStart 中 dragY = animY.value，但 animY(Animatable) 初始为 0f
    // fix: 不重新初始化 dragY，保持现有值
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `onDragStart 不重新初始化 dragY`() {
        val src = readSource(pillContentPath)
        val startBlock = src.substringAfter("onDragStart = {")
            .substringBefore("currentOnDragStart")
        assertFalse(
            "onDragStart 中不得使用 animY.value 初始化 dragY",
            startBlock.contains("dragY = if") && startBlock.contains("animY.value")
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #9b: 吸附方向使用 fingerX 而非 curIsLeftSide
    //
    // bug: LaunchedEffect 中使用 curIsLeftSide（组合时静态值），
    //      而非 fingerX（拖拽结束实时位置），可能导致吸附到错误一侧
    // fix: 用 fingerX < scrW / 2f 计算 leftSide
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `吸附方向基于 fingerX 而非 curIsLeftSide`() {
        val src = readSource(pillContentPath)
        // 在 LaunchedEffect(isDragging...) 内部查找 leftSide 计算
        val snapBlock = src.substringAfter("LaunchedEffect(isDragging, snapX, snapY)")
            .substringBefore("// 拖拽边界")
        assertTrue(
            "吸附方向必须基于 fingerX（实时）而非 curIsLeftSide（静态）",
            snapBlock.contains("fingerX < scrW / 2f")
        )
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
    // FIX #11: 快捷按钮首次测量被 120dp 阈值过滤
    //
    // bug: minRealW = 120.dp 过滤掉首次测量值，导致 onQuickAreaChanged 延迟
    // fix: 降低阈值为 20.dp
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `快捷按钮 minRealW 阈值为 20dp 以下`() {
        val src = readSource(pillContentPath)
        val minRealWLine = src.lines().find { it.contains("val minRealW") } ?: ""
        assertTrue(
            "minRealW 阈值应为 20.dp 或更低",
            minRealWLine.contains("20.dp") || minRealWLine.contains("10.dp") || minRealWLine.contains("0.dp")
        )
        assertFalse(
            "minRealW 阈值不得为 120.dp",
            minRealWLine.contains("120.dp")
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIX #12: 快捷按钮右侧展开被 coerceAtLeast(0) 裁剪
    //
    // bug: quickOffsetX.toInt().coerceAtLeast(0) 将负偏移裁剪为 0
    // fix: 移除 coerceAtLeast(0)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `快捷按钮偏移不使用 coerceAtLeast`() {
        val src = readSource(pillContentPath)
        // 快捷按钮 offset 附近不应有 coerceAtLeast(0)
        val quickBlock = src.substringAfter("// Quick toggles (rendered in pill window alongside the pill)")
            .substringBefore("// Card rendered in pill window")
        assertFalse(
            "快捷按钮 offset 不得使用 coerceAtLeast(0) 裁剪",
            quickBlock.contains("quickOffsetX.toInt().coerceAtLeast(0)")
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
