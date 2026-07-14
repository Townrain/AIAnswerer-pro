package com.hwb.aianswerer

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * 触摸穿透容器 — 直接继承 ViewGroup，管控子 View 布局逻辑。
 *
 * 不使用 FrameLayout 的原因：FrameLayout 的 LayoutParams 体系
 * （MarginLayoutParams）会与 WindowManager.LayoutParams 冲突，
 * 导致 onMeasure 时 ClassCastException。
 *
 * 通过 [setInteractiveRect] 设置可交互区域。触摸落在区域外时，
 * [dispatchTouchEvent] 返回 false，配合 FLAG_NOT_TOUCH_MODAL
 * 让触摸事件穿透到悬浮窗下方的应用。
 */
class InteractiveTouchLayout(context: Context) : ViewGroup(context) {

    @Volatile
    private var interactiveRect: Rect? = null

    /** 设置可交互区域（屏幕坐标）。非法值/空表示清空，整个区域可交互。 */
    fun setInteractiveRect(left: Float, top: Float, right: Float, bottom: Float) {
        if (left < 0f || top < 0f || right <= left || bottom <= top) {
            interactiveRect = null
            return
        }
        interactiveRect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount > 0) {
            val child = getChildAt(0)
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(child.measuredWidth, child.measuredHeight)
        } else {
            setMeasuredDimension(
                View.getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
                View.getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount > 0) {
            val child = getChildAt(0)
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    override fun generateLayoutParams(attrs: android.util.AttributeSet?): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(context, attrs)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        val rect = interactiveRect ?: return super.dispatchTouchEvent(event)
        if (event == null) return false

        if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
            return false  // 区域外 → 穿透给底层 App
        }
        return super.dispatchTouchEvent(event)
    }
}
