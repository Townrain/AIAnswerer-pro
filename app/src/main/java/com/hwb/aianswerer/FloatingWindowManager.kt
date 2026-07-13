package com.hwb.aianswerer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 悬浮窗管理 — 纯粹的窗口创建、位置、动画、高度操作。
 *
 * 不管 Compose 内容是什么，不管答题业务逻辑。
 * 只负责把 ComposeView 贴到屏幕上，并响应位置/高度变化。
 */
class FloatingWindowManager(private val context: Context) {
    val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var animJob: Job? = null

    /** 创建悬浮窗布局参数 */
    fun createLayoutParams(
        windowWidthPx: Int,
        windowHeightPx: Int,
        isLeftSide: Boolean,
        offsetY: Float,
        screenW: Float,
        screenH: Float,
        isStealth: Boolean
    ): WindowManager.LayoutParams {
        val x = if (isLeftSide) 0 else (screenW - windowWidthPx).toInt().coerceAtLeast(0)
        val y = offsetY.toInt().coerceIn(0, screenH.toInt() - windowHeightPx)

        return WindowManager.LayoutParams(
            windowWidthPx,
            windowHeightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or if (isStealth) WindowManager.LayoutParams.FLAG_SECURE else 0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            if (isStealth) alpha = 0.99f
        }
    }

    /** 把 ComposeView 添加到窗口 */
    fun attach(view: ComposeView, params: WindowManager.LayoutParams) {
        windowManager.addView(view, params)
    }

    /** 更新窗口位置和大小 */
    fun updateLayout(
        view: ComposeView?,
        windowX: Int,
        windowY: Int,
        windowHeight: Int,
        alpha: Float = 1f,
        screenW: Float,
        screenH: Float
    ) {
        view ?: return
        val p = view.layoutParams as WindowManager.LayoutParams
        p.x = windowX.coerceIn(0, maxOf(0, screenW.toInt() - p.width))
        p.y = windowY.coerceIn(0, maxOf(0, screenH.toInt() - p.height))
        p.height = windowHeight
        p.alpha = alpha
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "updateLayout failed", e)
        }
    }

    /** 设置窗口可见性（用于截图期间隐藏） */
    fun setVisible(view: ComposeView?, visible: Boolean, isStealth: Boolean) {
        view ?: return
        val p = view.layoutParams as WindowManager.LayoutParams
        p.alpha = if (visible) (if (isStealth) 0.99f else 1f) else 0f
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "setVisible failed", e)
        }
    }

    /** 添加/移除 FLAG_SECURE，用于截图期间隐藏窗口内容 */
    fun setFlagSecure(view: ComposeView?, enabled: Boolean) {
        view ?: return
        val p = view.layoutParams as WindowManager.LayoutParams
        p.flags = if (enabled) p.flags or WindowManager.LayoutParams.FLAG_SECURE
                  else p.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        try {
            windowManager.updateViewLayout(view, p)
        } catch (e: Exception) {
            AppLog.e("FWM", "setFlagSecure failed", e)
        }
    }

    /** 平滑动画窗口 X 位置（ease-out cubic） */
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

    /** 计算悬浮窗高度 */
    fun calculateHeight(
        density: Float,
        screenHeightPx: Int,
        buttonSizeDp: Int,
        isRecording: Boolean,
        isProcessingRecording: Boolean,
        hasContent: Boolean,
        showAnswer: Boolean,
        hasAnswers: Boolean
    ): Int {
        val buttonSizePx = buttonSizeDp * density
        val idleHeight = buttonSizePx + 16 * density
        val progressHeight = buttonSizePx + 120 * density
        val contentHeight = 700 * density

        return when {
            isRecording || isProcessingRecording -> progressHeight
            hasContent && (showAnswer || hasAnswers) -> contentHeight
            hasContent -> progressHeight
            else -> idleHeight
        }.toInt()
    }

    /** 移除窗口 */
    fun detach(view: ComposeView?) {
        animJob?.cancel()
        view?.let { windowManager.removeView(it) }
    }
}
