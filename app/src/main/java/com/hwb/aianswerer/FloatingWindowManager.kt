package com.hwb.aianswerer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.hwb.aianswerer.ui.components.FWDims
import com.hwb.aianswerer.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 悬浮窗管理 — 三窗口（A=Pill, B=Toggles, C=Card）的创建、位置、动画操作。
 *
 * 不管 Compose 内容是什么，不管答题业务逻辑。
 * 只负责把 ComposeView 贴到屏幕上，并响应位置/大小变化。
 */
class FloatingWindowManager(private val context: Context) {
    val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var animJob: Job? = null

    enum class WindowId { A, B, C, D }

    var aParams: WindowManager.LayoutParams? = null
    var bParams: WindowManager.LayoutParams? = null
    var cParams: WindowManager.LayoutParams? = null
    var dParams: WindowManager.LayoutParams? = null

    var aView: View? = null
    var bView: View? = null
    var cView: View? = null
    var dView: View? = null

    // ── Layout params factory ────────────────────────────────────────

    /** 创建指定窗口的布局参数 */
    fun createLayoutParams(
        windowId: WindowId,
        buttonSizePx: Int,
        isStealth: Boolean
    ): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (isStealth) WindowManager.LayoutParams.FLAG_SECURE else 0)

        val (width, height) = when (windowId) {
            WindowId.A -> {
                val padding = (FWDims.pillEdgeMargin.value * 2 * density).toInt()
                val size = buttonSizePx + padding
                size to size
            }
            WindowId.B -> {
                // Estimated size for 5 toggles (40dp each + spacing). Actual size from windowBContent onMeasuredSize.
                val estW = ((FWDims.quickBtnSize.value * 5 + FWDims.quickBtnSpacing.value * 4 + FWDims.quickPanelHPadding.value * 2) * density).toInt()
                val estH = ((FWDims.quickBtnSize.value + FWDims.quickPanelVPadding.value * 2) * density).toInt()
                estW.coerceAtLeast(50) to estH.coerceAtLeast(40)
            }
            WindowId.C -> {
                // 窗口 C: 宽度固定，高度 WRAP_CONTENT —— 窗口自动包裹内容
                // （固定高度会导致 Compose 根被 EXACTLY 约束撑满，onGloballyPositioned 上报窗口高度
                //  而非内容高度，窗口永不收缩、透明区拦截触摸）
                (FWDims.cardWidthDp.value * density).toInt() to WindowManager.LayoutParams.WRAP_CONTENT
            }
            WindowId.D -> {
                // Window D: 宽度同 C，高度 WRAP_CONTENT（同上，自动包裹答案内容）
                (FWDims.cardWidthDp.value * density).toInt() to WindowManager.LayoutParams.WRAP_CONTENT
            }
        }

        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (isStealth) alpha = Constants.STEALTH_ALPHA
        }
    }

    // ── Attach ───────────────────────────────────────────────────────

    fun attachA(view: View, params: WindowManager.LayoutParams) {
        // 先 addView 成功后赋值，避免 addView 失败后字段残留导致状态不一致（S4）
        windowManager.addView(view, params)
        aView = view
        aParams = params
    }

    fun attachB(view: View, params: WindowManager.LayoutParams) {
        windowManager.addView(view, params)
        bView = view
        bParams = params
    }

    fun attachC(view: View, params: WindowManager.LayoutParams) {
        windowManager.addView(view, params)
        cView = view
        cParams = params
    }

    fun attachD(view: View, params: WindowManager.LayoutParams) {
        windowManager.addView(view, params)
        dView = view
        dParams = params
    }

    // ── Detach ──────────────────────────────────────────────────────

    fun detachA() {
        animJob?.cancel()
        aView?.let { view ->
            try { windowManager.removeView(view) } catch (e: Exception) { AppLog.e("FWM", "detachA failed", e) }
        }
        aView = null
        aParams = null
    }

    fun detachB() {
        bView?.let { view ->
            try { windowManager.removeView(view) } catch (e: Exception) { AppLog.e("FWM", "detachB failed", e) }
        }
        bView = null
        bParams = null
    }

    fun detachC() {
        cView?.let { view ->
            try { windowManager.removeView(view) } catch (e: Exception) { AppLog.e("FWM", "detachC failed", e) }
        }
        cView = null
        cParams = null
    }

    fun detachD() {
        dView?.let { view ->
            try { windowManager.removeView(view) } catch (e: Exception) { AppLog.e("FWM", "detachD failed", e) }
        }
        dView = null
        dParams = null
    }

    // ── Per-window layout update ─────────────────────────────────────

    fun updateLayoutA(
        windowX: Int, windowY: Int, width: Int, height: Int,
        alpha: Float, screenW: Float, screenH: Float
    ) {
        val view = aView ?: return
        val p = aParams ?: return
        p.width = width
        p.height = height
        p.x = windowX.coerceIn(0, maxOf(0, screenW.toInt() - p.width))
        p.y = windowY.coerceIn(0, maxOf(0, screenH.toInt() - p.height))
        p.alpha = alpha
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "updateLayoutA failed", e)
        }
    }

    fun updateLayoutB(
        windowX: Int, windowY: Int, width: Int, height: Int,
        alpha: Float, screenW: Float, screenH: Float
    ) {
        val view = bView ?: return
        val p = bParams ?: return
        p.width = width
        p.height = height
        p.x = windowX.coerceIn(0, maxOf(0, screenW.toInt() - p.width))
        p.y = windowY.coerceIn(0, maxOf(0, screenH.toInt() - p.height))
        p.alpha = alpha
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "updateLayoutB failed", e)
        }
    }

    fun updateLayoutC(
        windowX: Int, windowY: Int, width: Int, height: Int,
        alpha: Float, screenW: Float, screenH: Float
    ) {
        val view = cView ?: return
        val p = cParams ?: return
        p.width = width
        p.height = height
        p.x = windowX.coerceIn(0, maxOf(0, screenW.toInt() - p.width))
        p.y = windowY.coerceIn(0, maxOf(0, screenH.toInt() - p.height))
        p.alpha = alpha
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "updateLayoutC failed", e)
        }
    }

    fun updateLayoutD(
        windowX: Int, windowY: Int, width: Int, height: Int,
        alpha: Float, screenW: Float, screenH: Float
    ) {
        val view = dView ?: return
        val p = dParams ?: return
        p.width = width
        p.height = height
        p.x = windowX.coerceIn(0, maxOf(0, screenW.toInt() - p.width))
        p.y = windowY.coerceIn(0, maxOf(0, screenH.toInt() - p.height))
        p.alpha = alpha
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "updateLayoutD failed", e)
        }
    }

    // ── Batch operations ────────────────────────────────────────────

    /** Apply individual actions to matched windows by view identity. */
    fun applyToAllWindows(vararg views: Pair<View?, (WindowManager.LayoutParams) -> Unit>) {
        for ((view, action) in views) {
            val p = when (view) {
                aView -> aParams
                bView -> bParams
                cView -> cParams
                dView -> dParams
                else -> null
            }
            if (view != null && p != null) {
                action(p)
                try {
                    windowManager.updateViewLayout(view, p)
                } catch (e: Exception) {
                    AppLog.e("FWM", "applyToAllWindows failed", e)
                }
            }
        }
    }

    /** Add or remove FLAG_SECURE on all windows. */
    fun setAllFlagSecure(enabled: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        listOfNotNull(
            aView to aParams,
            bView to bParams,
            cView to cParams,
            dView to dParams
        ).forEach { (view, p) ->
            val np = p ?: return@forEach
            np.flags = if (enabled) np.flags or flag else np.flags and flag.inv()
            try {
                windowManager.updateViewLayout(view, np)
            } catch (e: Exception) {
                AppLog.e("FWM", "setAllFlagSecure failed", e)
            }
        }
    }

    /** Set alpha on all windows. */
    fun setAllAlpha(alpha: Float) {
        listOfNotNull(
            aView to aParams,
            bView to bParams,
            cView to cParams,
            dView to dParams
        ).forEach { (view, p) ->
            val np = p ?: return@forEach
            np.alpha = alpha
            try {
                windowManager.updateViewLayout(view, np)
            } catch (e: Exception) {
                AppLog.e("FWM", "setAllAlpha failed", e)
            }
        }
    }

    // ── Animation ───────────────────────────────────────────────────

    /** 平滑动画窗口 X 位置（ease-out cubic），用于 Window A 边缘吸附 */
    fun animateWindowX(
        scope: CoroutineScope,
        from: Float,
        to: Float,
        onFrame: (currentX: Float) -> Unit
    ): Job {
        animJob?.cancel()
        val job = scope.launch {
            val duration = 250L
            val start = System.currentTimeMillis()
            try {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - start
                    val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                    val eased = 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
                    onFrame(from + (to - from) * eased)
                    if (fraction >= 1f) break
                    delay(16)
                }
            } finally {
                onFrame(to)
            }
        }
        animJob = job
        return job
    }

    /** 渐入/渐出单个窗口的透明度（用于折叠动画）。完成后调用 [onDone]。 */
    fun animateWindowAlpha(
        scope: CoroutineScope,
        view: View?,
        from: Float,
        to: Float,
        durationMs: Long = 200L,
        onDone: () -> Unit = {}
    ): Job {
        val job = scope.launch {
            val start = System.currentTimeMillis()
            try {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - start
                    val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    setAlpha(view, from + (to - from) * fraction)
                    if (fraction >= 1f) break
                    delay(16)
                }
            } finally {
                onDone()
            }
        }
        return job
    }

    /**
     * 窗口高度过渡动画:顶部或底部固定,高度从 [fromH] 渐变到 [toH],
     * 可同步渐变透明度。用于 D 窗展开/收起过渡——
     * 收起 = 收缩到紧凑高度 + 淡出,展开 = 从紧凑高度伸展 + 淡入。
     * 无论正常完成还是被取消都会调用 [onDone],由调用方在 onDone 中完成窗口增删收尾。
     */
    fun animateWindowHeight(
        scope: CoroutineScope,
        view: View?,
        fromH: Int,
        toH: Int,
        keepTop: Boolean,
        anchorY: Int,
        fromAlpha: Float? = null,
        toAlpha: Float? = null,
        durationMs: Long = 160L,
        onDone: () -> Unit = {}
    ): Job {
        val job = scope.launch {
            val start = System.currentTimeMillis()
            try {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - start
                    val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    // 轻微弹性：back ease-out overshoot 约 3%（用户反馈过强的 10% 已调低），
                    // 避免窗口冲过头造成“抖出”观感
                    val s = 0.5f
                    val t1 = fraction - 1f
                    val eased = t1 * t1 * ((s + 1f) * t1 + s) + 1f
                    val h = (fromH + (toH - fromH) * eased).toInt().coerceAtLeast(0)
                    // keepTop: 顶部固定、底部随高度移动(从下往上收起/展开)
                    // 否则:底部固定(anchorY = 底部 y)、顶部随高度移动
                    val y = if (keepTop) anchorY else anchorY - h
                    val p = when (view) {
                        dView -> dParams
                        cView -> cParams
                        else -> null
                    }
                    if (p != null) {
                        p.height = h
                        p.y = y
                        if (fromAlpha != null && toAlpha != null) {
                            p.alpha = fromAlpha + (toAlpha - fromAlpha) * fraction
                        }
                        try { windowManager.updateViewLayout(view!!, p) }
                        catch (e: Exception) { AppLog.e("FWM", "animateWindowHeight failed", e) }
                    }
                    if (fraction >= 1f) break
                    // 20ms 帧间隔(约 50fps)：降低动画期间 Compose 重排与 updateViewLayout 的叠加压力
                    delay(20)
                }
            } finally {
                onDone()
            }
        }
        return job
    }
    // ── Backward compat delegates ───────────────────────────────────

    fun attach(view: View, params: WindowManager.LayoutParams) {
        attachA(view, params)
    }

    fun setAlpha(view: View?, alpha: Float) {
        val p = when (view) {
            aView -> aParams
            bView -> bParams
            cView -> cParams
            dView -> dParams
            else -> null
        } ?: return
        p.alpha = alpha
        try {
            windowManager.updateViewLayout(view!!, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "setAlpha failed", e)
        }
    }

    fun detach(view: View?) {
        when (view) {
            aView -> detachA()
            bView -> detachB()
            cView -> detachC()
            dView -> detachD()
            else -> {}
        }
    }
}
