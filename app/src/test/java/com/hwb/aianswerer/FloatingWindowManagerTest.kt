package com.hwb.aianswerer

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.hwb.aianswerer.ui.components.FWDims
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FloatingWindowManager tests for the 4-window architecture (A=Pill, B=Toggles, C=Card, D=Detail).
 *
 * Tests all multi-window APIs: createLayoutParams, attachA/B/C/D, detachA/B/C/D,
 * updateLayoutA/B/C/D, setAllFlagSecure, setAllAlpha, applyToAllWindows,
 * animateWindowX, and backward compat methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = android.app.Application::class)
class FloatingWindowManagerTest {

    private val mockWm = mockk<WindowManager>(relaxed = true)
    private val mockResources = mockk<Resources>(relaxed = true).apply {
        every { displayMetrics } returns DisplayMetrics().apply { density = 2f }
    }
    private val context = mockk<Context>(relaxed = true).apply {
        every { getSystemService(Context.WINDOW_SERVICE) } returns mockWm
        every { resources } returns mockResources
    }
    private val wm = FloatingWindowManager(context)

    // ═════════════════════════════════════════════════════════════════
    // createLayoutParams — WindowId specific dimensions
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `createLayoutParams Window A returns square dimensions with pill margin`() {
        val buttonSizePx = 40
        val density = 2f
        val padding = (FWDims.pillEdgeMargin.value * 2 * density).toInt()
        val expectedSize = buttonSizePx + padding

        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, buttonSizePx, false)

        assertEquals(expectedSize, params.width)
        assertEquals(expectedSize, params.height)
    }

    @Test
    fun `createLayoutParams Window B returns estimated toggle panel dimensions`() {
        val density = 2f
        val expectedW = ((FWDims.quickBtnSize.value * 5 + FWDims.quickBtnSpacing.value * 4 + FWDims.quickPanelHPadding.value * 2) * density).toInt()
        val expectedH = ((FWDims.quickBtnSize.value + FWDims.quickPanelVPadding.value * 2) * density).toInt()

        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.B, 40, false)

        assertEquals(expectedW, params.width)
        assertEquals(expectedH, params.height)
    }

    @Test
    fun `createLayoutParams Window C returns card width and initial height`() {
        val density = 2f
        val expectedWidth = (FWDims.cardWidthDp.value * density).toInt()
        val expectedHeight = (60 * density).toInt() // initial 60dp until measured

        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.C, 40, false)

        assertEquals(expectedWidth, params.width)
        assertEquals(expectedHeight, params.height)
    }

    @Test
    fun `createLayoutParams Window D returns card width and extended height`() {
        val density = 2f
        val expectedWidth = (FWDims.cardWidthDp.value * density).toInt()
        val expectedHeight = (200 * density).toInt() * 4

        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.D, 40, false)

        assertEquals(expectedWidth, params.width)
        assertEquals(expectedHeight, params.height)
    }

    @Test
    fun `createLayoutParams sets gravity to TOP or START`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, false)

        assertEquals(Gravity.TOP or Gravity.START, params.gravity)
    }

    @Test
    fun `createLayoutParams sets initial position to 0 0`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, false)

        assertEquals(0, params.x)
        assertEquals(0, params.y)
    }

    // ═════════════════════════════════════════════════════════════════
    // createLayoutParams — Stealth mode
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `createLayoutParams with stealth sets FLAG_SECURE`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, true)

        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
    }

    @Test
    fun `createLayoutParams with stealth sets alpha to STEALTH_ALPHA`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, true)

        assertEquals(Constants.STEALTH_ALPHA, params.alpha, 0.001f)
    }

    @Test
    fun `createLayoutParams without stealth does not set FLAG_SECURE`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, false)

        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun `createLayoutParams without stealth has default alpha`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, false)

        // Default LayoutParams alpha is 1.0
        assertEquals(1.0f, params.alpha, 0.001f)
    }

    @Test
    fun `createLayoutParams always sets NOT_FOCUSABLE NOT_TOUCH_MODAL and LAYOUT_IN_SCREEN`() {
        val params = wm.createLayoutParams(FloatingWindowManager.WindowId.A, 40, false)

        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0)
    }

    // ═════════════════════════════════════════════════════════════════
    // attachA / B / C
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `attachA stores view and params and adds to WindowManager`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attachA(view, params)

        assertSame(view, wm.aView)
        assertSame(params, wm.aParams)
        verify { mockWm.addView(view, params) }
    }

    @Test
    fun `attachA overwrites previous A window`() {
        val oldView = mockk<View>(relaxed = true)
        val oldParams = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachA(oldView, oldParams)

        val newView = mockk<View>(relaxed = true)
        val newParams = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attachA(newView, newParams)

        assertSame(newView, wm.aView)
        assertSame(newParams, wm.aParams)
        verify { mockWm.addView(newView, newParams) }
    }

    @Test
    fun `attachB stores view and params`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attachB(view, params)

        assertSame(view, wm.bView)
        assertSame(params, wm.bParams)
        verify { mockWm.addView(view, params) }
    }

    @Test
    fun `attachC stores view and params`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attachC(view, params)

        assertSame(view, wm.cView)
        assertSame(params, wm.cParams)
        verify { mockWm.addView(view, params) }
    }

    @Test
    fun `attachD stores view and params`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attachD(view, params)

        assertSame(view, wm.dView)
        assertSame(params, wm.dParams)
        verify { mockWm.addView(view, params) }
    }

    @Test
    fun `all four windows can be attached independently`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = mockk<WindowManager.LayoutParams>(relaxed = true)
        val bView = mockk<View>(relaxed = true)
        val bParams = mockk<WindowManager.LayoutParams>(relaxed = true)
        val cView = mockk<View>(relaxed = true)
        val cParams = mockk<WindowManager.LayoutParams>(relaxed = true)
        val dView = mockk<View>(relaxed = true)
        val dParams = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attachA(aView, aParams)
        wm.attachB(bView, bParams)
        wm.attachC(cView, cParams)
        wm.attachD(dView, dParams)

        assertSame(aView, wm.aView)
        assertSame(bView, wm.bView)
        assertSame(cView, wm.cView)
        assertSame(dView, wm.dView)
        verify { mockWm.addView(aView, aParams) }
        verify { mockWm.addView(bView, bParams) }
        verify { mockWm.addView(cView, cParams) }
        verify { mockWm.addView(dView, dParams) }
    }

    // ═════════════════════════════════════════════════════════════════
    // detachA / B / C
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `detachA removes view and clears state`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachA(view, params)

        wm.detachA()

        verify { mockWm.removeView(view) }
        assertNull(wm.aView)
        assertNull(wm.aParams)
    }

    @Test
    fun `detachA when no view attached does not crash`() {
        wm.detachA() // should not throw
        assertNull(wm.aView)
        assertNull(wm.aParams)
    }

    @Test
    fun `detachB removes view and clears state`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachB(view, params)

        wm.detachB()

        verify { mockWm.removeView(view) }
        assertNull(wm.bView)
        assertNull(wm.bParams)
    }

    @Test
    fun `detachC removes view and clears state`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachC(view, params)

        wm.detachC()

        verify { mockWm.removeView(view) }
        assertNull(wm.cView)
        assertNull(wm.cParams)
    }

    @Test
    fun `detachD removes view and clears state`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachD(view, params)

        wm.detachD()

        verify { mockWm.removeView(view) }
        assertNull(wm.dView)
        assertNull(wm.dParams)
    }

    @Test
    fun `detaching one window does not affect other windows`() {
        val aView = mockk<View>(relaxed = true)
        val bView = mockk<View>(relaxed = true)
        val dView = mockk<View>(relaxed = true)
        wm.attachA(aView, mockk(relaxed = true))
        wm.attachB(bView, mockk(relaxed = true))
        wm.attachD(dView, mockk(relaxed = true))

        wm.detachA()

        assertNull(wm.aView)
        assertNotNull(wm.bView)
        assertNotNull(wm.dView)
        verify(exactly = 0) { mockWm.removeView(bView) }
        verify(exactly = 0) { mockWm.removeView(dView) }
    }

    // ═════════════════════════════════════════════════════════════════
    // updateLayoutA / B / C
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `updateLayoutA updates width height position and alpha`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(100, 200, 0, 0, 0)
        wm.attachA(view, params)

        wm.updateLayoutA(50, 100, 150, 250, 0.5f, 1000f, 2000f)

        assertEquals(150, params.width)
        assertEquals(250, params.height)
        assertEquals(50, params.x)
        assertEquals(100, params.y)
        assertEquals(0.5f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    @Test
    fun `updateLayoutA clamps x to non-negative`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(100, 200, 0, 0, 0)
        wm.attachA(view, params)

        wm.updateLayoutA(-50, 100, 100, 200, 1f, 1000f, 2000f)

        assertEquals(0, params.x)
    }

    @Test
    fun `updateLayoutA clamps y to non-negative`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(100, 200, 0, 0, 0)
        wm.attachA(view, params)

        wm.updateLayoutA(50, -100, 100, 200, 1f, 1000f, 2000f)

        assertEquals(0, params.y)
    }

    @Test
    fun `updateLayoutA clamps x to prevent going off-screen right`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(100, 200, 0, 0, 0)
        wm.attachA(view, params)

        // screenW=500, width=100 → max x = 500 - 100 = 400
        wm.updateLayoutA(600, 100, 100, 200, 1f, 500f, 2000f)

        assertEquals(400, params.x)
    }

    @Test
    fun `updateLayoutA when no view attached does nothing`() {
        // No view attached — should not throw
        wm.updateLayoutA(50, 100, 150, 250, 0.5f, 1000f, 2000f)

        verify(exactly = 0) { mockWm.updateViewLayout(any(), any()) }
    }

    @Test
    fun `updateLayoutB updates width height position and alpha`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(0, 0, 0, 0, 0)
        wm.attachB(view, params)

        wm.updateLayoutB(10, 20, 80, 40, 0.3f, 1000f, 2000f)

        assertEquals(80, params.width)
        assertEquals(40, params.height)
        assertEquals(10, params.x)
        assertEquals(20, params.y)
        assertEquals(0.3f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    @Test
    fun `updateLayoutB clamps position within screen bounds`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(50, 30, 0, 0, 0)
        wm.attachB(view, params)

        // screenW=200, width=50 → max x = 150; screenH=400, height=30 → max y = 370
        wm.updateLayoutB(300, 500, 50, 30, 1f, 200f, 400f)

        assertEquals(150, params.x)
        assertEquals(370, params.y)
    }

    @Test
    fun `updateLayoutC updates width height position and alpha`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(300, 0, 0, 0, 0)
        wm.attachC(view, params)

        wm.updateLayoutC(30, 300, 300, 500, 0.9f, 1000f, 2000f)

        assertEquals(300, params.width)
        assertEquals(500, params.height)
        assertEquals(30, params.x)
        assertEquals(300, params.y)
        assertEquals(0.9f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    @Test
    fun `updateLayoutD updates width height position and alpha`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(360, 0, 0, 0, 0)
        wm.attachD(view, params)

        wm.updateLayoutD(40, 400, 360, 800, 0.6f, 1000f, 2000f)

        assertEquals(360, params.width)
        assertEquals(800, params.height)
        assertEquals(40, params.x)
        assertEquals(400, params.y)
        assertEquals(0.6f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    // ═════════════════════════════════════════════════════════════════
    // setAllFlagSecure
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `setAllFlagSecure adds FLAG_SECURE to all active windows`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        val dView = mockk<View>(relaxed = true)
        val dParams = WindowManager.LayoutParams(360, 0, 0, 0, 0)
        wm.attachA(aView, aParams)
        wm.attachD(dView, dParams)

        wm.setAllFlagSecure(true)

        assertTrue((aParams.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
        assertTrue((dParams.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
        verify { mockWm.updateViewLayout(aView, aParams) }
        verify { mockWm.updateViewLayout(dView, dParams) }
    }

    @Test
    fun `setAllFlagSecure removes FLAG_SECURE from all active windows`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = WindowManager.LayoutParams(100, 100, 0, WindowManager.LayoutParams.FLAG_SECURE, 0)
        wm.attachA(aView, aParams)

        wm.setAllFlagSecure(false)

        assertEquals(0, aParams.flags and WindowManager.LayoutParams.FLAG_SECURE)
        verify { mockWm.updateViewLayout(aView, aParams) }
    }

    @Test
    fun `setAllFlagSecure with no windows attached does not crash`() {
        wm.setAllFlagSecure(true) // no-op, should not throw
    }

    // ═════════════════════════════════════════════════════════════════
    // setAllAlpha
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `setAllAlpha sets alpha on all active windows`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        val dView = mockk<View>(relaxed = true)
        val dParams = WindowManager.LayoutParams(360, 0, 0, 0, 0)
        wm.attachA(aView, aParams)
        wm.attachD(dView, dParams)

        wm.setAllAlpha(0.5f)

        assertEquals(0.5f, aParams.alpha, 0.001f)
        assertEquals(0.5f, dParams.alpha, 0.001f)
        verify { mockWm.updateViewLayout(aView, aParams) }
        verify { mockWm.updateViewLayout(dView, dParams) }
    }

    @Test
    fun `setAllAlpha applies different alpha values consecutively`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        wm.attachA(view, params)

        wm.setAllAlpha(0.3f)
        assertEquals(0.3f, params.alpha, 0.001f)

        wm.setAllAlpha(0.9f)
        assertEquals(0.9f, params.alpha, 0.001f)
    }

    @Test
    fun `setAllAlpha with no windows attached does not crash`() {
        wm.setAllAlpha(0.5f) // no-op, should not throw
    }

    // ═════════════════════════════════════════════════════════════════
    // applyToAllWindows
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `applyToAllWindows applies action to matched windows`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        wm.attachA(aView, aParams)
        val cView = mockk<View>(relaxed = true)
        val cParams = WindowManager.LayoutParams(300, 0, 0, 0, 0)
        wm.attachC(cView, cParams)

        wm.applyToAllWindows(
            aView to { p -> p.alpha = 0.3f },
            cView to { p -> p.alpha = 0.7f }
        )

        assertEquals(0.3f, aParams.alpha, 0.001f)
        assertEquals(0.7f, cParams.alpha, 0.001f)
        verify { mockWm.updateViewLayout(aView, aParams) }
        verify { mockWm.updateViewLayout(cView, cParams) }
    }

    @Test
    fun `applyToAllWindows ignores unmatched views`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        wm.attachA(aView, aParams)
        val unknown = mockk<View>(relaxed = true)

        wm.applyToAllWindows(unknown to { p -> p.alpha = 0.5f })

        assertEquals(1f, aParams.alpha, 0.001f) // unchanged
        verify(exactly = 0) { mockWm.updateViewLayout(any(), any()) }
    }

    @Test
    fun `applyToAllWindows with no windows attached does not crash`() {
        wm.applyToAllWindows(mockk<View>(relaxed = true) to { }) // should not throw
    }

    // ═════════════════════════════════════════════════════════════════
    // animateWindowX
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `animateWindowX returns an active Job`() {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        val job = wm.animateWindowX(scope, 0f, 100f) {}

        assertNotNull(job)
        assertTrue(job.isActive)
        job.cancel()
        scope.cancel()
    }

    @Test
    fun `animateWindowX calls onFrame through animation progression`() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val positions = mutableListOf<Float>()

        val job = wm.animateWindowX(scope, 0f, 100f) { positions.add(it) }
        scheduler.advanceUntilIdle()
        job.join()

        assertTrue("onFrame should have been called at least once", positions.isNotEmpty())
        assertEquals("Final position should reach target", 100f, positions.last(), 1f)
        scope.cancel()
    }

    @Test
    fun `animateWindowX starts from the from value`() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val positions = mutableListOf<Float>()

        val job = wm.animateWindowX(scope, 50f, 200f) { positions.add(it) }
        scheduler.advanceTimeBy(1)
        assertTrue(positions.isNotEmpty())
        assertEquals(50f, positions.first(), 1f)

        scheduler.advanceUntilIdle()
        job.join()
        scope.cancel()
    }

    @Test
    fun `animateWindowX cancels previous animation when called again`() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val positions1 = mutableListOf<Float>()
        val positions2 = mutableListOf<Float>()

        // Start first animation, advance a bit
        val job1 = wm.animateWindowX(scope, 0f, 100f) { positions1.add(it) }
        scheduler.advanceTimeBy(50)
        assertTrue("first animation should have started", positions1.isNotEmpty())

        // Start second animation — should cancel first
        val job2 = wm.animateWindowX(scope, 50f, 200f) { positions2.add(it) }
        scheduler.advanceUntilIdle()
        job2.join()

        assertTrue("first job should be cancelled", job1.isCancelled)
        assertTrue("second animation should have frames", positions2.isNotEmpty())
        assertEquals(200f, positions2.last(), 1f)
        scope.cancel()
    }

    // ═════════════════════════════════════════════════════════════════
    // Backward compat — attach(view, params), detach(view), setAlpha
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `backward compat attach delegates to attachA`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)

        wm.attach(view, params)

        assertSame(view, wm.aView)
        assertSame(params, wm.aParams)
        verify { mockWm.addView(view, params) }
    }

    @Test
    fun `backward compat detach with A view calls detachA`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachA(view, params)

        wm.detach(view)

        verify { mockWm.removeView(view) }
        assertNull(wm.aView)
        assertNull(wm.aParams)
    }

    @Test
    fun `backward compat detach with B view calls detachB`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachB(view, params)

        wm.detach(view)

        verify { mockWm.removeView(view) }
        assertNull(wm.bView)
        assertNull(wm.bParams)
    }

    @Test
    fun `backward compat detach with unknown view does nothing`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachA(view, params)
        val unknown = mockk<View>(relaxed = true)

        wm.detach(unknown)

        assertSame(view, wm.aView) // state unchanged
        verify(exactly = 0) { mockWm.removeView(unknown) }
    }

    @Test
    fun `backward compat detach with null does nothing`() {
        val view = mockk<View>(relaxed = true)
        wm.attachA(view, mockk(relaxed = true))

        wm.detach(null)

        assertNotNull(wm.aView) // state unchanged
    }

    @Test
    fun `backward compat detach with D view calls detachD`() {
        val view = mockk<View>(relaxed = true)
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        wm.attachD(view, params)

        wm.detach(view)

        verify { mockWm.removeView(view) }
        assertNull(wm.dView)
        assertNull(wm.dParams)
    }

    @Test
    fun `backward compat setAlpha updates alpha for matching A view`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        wm.attachA(view, params)

        wm.setAlpha(view, 0.5f)

        assertEquals(0.5f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    @Test
    fun `backward compat setAlpha updates alpha for matching B view`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(0, 0, 0, 0, 0)
        wm.attachB(view, params)

        wm.setAlpha(view, 0.75f)

        assertEquals(0.75f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    @Test
    fun `backward compat setAlpha with non-matching view does nothing`() {
        val aView = mockk<View>(relaxed = true)
        val aParams = WindowManager.LayoutParams(100, 100, 0, 0, 0)
        wm.attachA(aView, aParams)
        val unknown = mockk<View>(relaxed = true)

        wm.setAlpha(unknown, 0.5f)

        assertEquals(1f, aParams.alpha, 0.001f) // unchanged
        verify(exactly = 0) { mockWm.updateViewLayout(any(), any()) }
    }

    @Test
    fun `backward compat setAlpha updates alpha for matching D view`() {
        val view = mockk<View>(relaxed = true)
        val params = WindowManager.LayoutParams(360, 0, 0, 0, 0)
        wm.attachD(view, params)

        wm.setAlpha(view, 0.4f)

        assertEquals(0.4f, params.alpha, 0.001f)
        verify { mockWm.updateViewLayout(view, params) }
    }

    @Test
    fun `backward compat setAlpha with null view does nothing`() {
        wm.setAlpha(null, 0.5f) // should not throw
    }
}
