package com.hwb.aianswerer

import android.content.Context
import android.view.WindowManager
import com.hwb.aianswerer.ui.components.FWDims
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class FloatingWindowManagerTest {

    private val mockWm = mockk<WindowManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true).apply {
        every { getSystemService(Context.WINDOW_SERVICE) } returns mockWm
    }
    private val wm = FloatingWindowManager(context)

    @Test fun `idle 返回按钮加内边距高度`() {
        val d = 2.0f; val b = 40
        val h = wm.calculateHeight(density = d, screenHeightPx = Int.MAX_VALUE, buttonSizeDp = b, isRecording = false, isProcessingRecording = false, hasContent = false, showAnswer = false, hasAnswers = false, measuredCardHeightPx = 0f)
        assertEquals((b * d + FWDims.idleHeightPaddingDp.value * d).toInt(), h)
    }

    @Test fun `录制中优先于 hasContent`() {
        val h = wm.calculateHeight(density = 2f, screenHeightPx = Int.MAX_VALUE, buttonSizeDp = 40, isRecording = true, isProcessingRecording = false, hasContent = true, showAnswer = true, hasAnswers = true, measuredCardHeightPx = 999f)
        assertEquals((40 * 2f + 120 * 2f).toInt(), h)
    }

    @Test fun `录制处理中也优先`() {
        val h = wm.calculateHeight(density = 2f, screenHeightPx = Int.MAX_VALUE, buttonSizeDp = 40, isRecording = false, isProcessingRecording = true, hasContent = true, showAnswer = true, hasAnswers = true, measuredCardHeightPx = 999f)
        assertEquals((40 * 2f + 120 * 2f).toInt(), h)
    }

    @Test fun `有实测高度用实测值`() {
        assertEquals(500, wm.calculateHeight(density = 2f, screenHeightPx = Int.MAX_VALUE, buttonSizeDp = 40, isRecording = false, isProcessingRecording = false, hasContent = true, showAnswer = true, hasAnswers = false, measuredCardHeightPx = 500f))
    }

    @Test fun `无实测高度默认 700dp`() {
        assertEquals((700 * 1.5).toInt(), wm.calculateHeight(density = 1.5f, screenHeightPx = Int.MAX_VALUE, buttonSizeDp = 56, isRecording = false, isProcessingRecording = false, hasContent = true, showAnswer = true, hasAnswers = false, measuredCardHeightPx = 0f))
    }

    @Test fun `hasContent 无 showAnswer 返回进度高度`() {
        val h = wm.calculateHeight(density = 2f, screenHeightPx = Int.MAX_VALUE, buttonSizeDp = 40, isRecording = false, isProcessingRecording = false, hasContent = true, showAnswer = false, hasAnswers = false, measuredCardHeightPx = 0f)
        assertEquals((40 * 2f + 120 * 2f).toInt(), h)
    }
}