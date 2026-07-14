package com.hwb.aianswerer

import com.hwb.aianswerer.ui.components.FWDims
import org.junit.Assert.*
import org.junit.Test

class FloatingWindowManagerTest {

    @Test fun `idle 返回按钮加内边距高度`() {
        val d = 2.0f; val b = 40
        val h = calc(d, b, a = false, p = false, c = false, s = false, r = false, m = 0f)
        assertEquals((b * d + FWDims.idleHeightPaddingDp.value * d).toInt(), h)
    }

    @Test fun `录制中优先于 hasContent`() {
        val h = calc(2f, 40, a = true, p = false, c = true, s = true, r = true, m = 999f)
        assertEquals((40 * 2f + 120 * 2f).toInt(), h)
    }

    @Test fun `录制处理中也优先`() {
        val h = calc(2f, 40, a = false, p = true, c = true, s = true, r = true, m = 999f)
        assertEquals((40 * 2f + 120 * 2f).toInt(), h)
    }

    @Test fun `有实测高度用实测值`() {
        assertEquals(500, calc(2f, 40, a = false, p = false, c = true, s = true, r = false, m = 500f))
    }

    @Test fun `无实测高度默认 700dp`() {
        assertEquals((700 * 1.5).toInt(), calc(1.5f, 56, a = false, p = false, c = true, s = true, r = false, m = 0f))
    }

    @Test fun `hasContent 无 showAnswer 返回进度高度`() {
        val h = calc(2f, 40, a = false, p = false, c = true, s = false, r = false, m = 0f)
        assertEquals((40 * 2f + 120 * 2f).toInt(), h)
    }

    private fun calc(d: Float, b: Int, a: Boolean, p: Boolean, c: Boolean, s: Boolean, r: Boolean, m: Float): Int {
        val bs = b * d; val idle = bs + FWDims.idleHeightPaddingDp.value * d
        val prog = bs + 120 * d; val cont = if (m > 0f) m.toInt() else (700 * d).toInt()
        return when { a || p -> prog.toInt(); c && (s || r) -> cont; c -> prog.toInt(); else -> idle.toInt() }
    }
}
