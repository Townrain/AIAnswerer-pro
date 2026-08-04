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
            WindowId.C -> (FWDims.cardWidthDp.value * density).toInt() to (60 * density).toInt()
            WindowId.D -> {
                // Window D: 宽度同 C，初始高度 = 内容最大高度（cardMaxHeight）
                // 让 Compose 内容先完整布局，onMeasuredHeight 上报真实高度后由 positionWindowD 收缩
                (FWDims.cardWidthDp.value * density).toInt() to (FWDims.cardMaxHeight.value * density).toInt()
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
            while (isActive) {
                val elapsed = System.currentTimeMillis() - start
                val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val eased = 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
                onFrame(from + (to - from) * eased)
                if (fraction >= 1f) break
                delay(16)
            }
        }
        animJob = job
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
