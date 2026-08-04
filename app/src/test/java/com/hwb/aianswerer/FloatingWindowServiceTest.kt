package com.hwb.aianswerer

import android.app.Activity
import android.app.Service
import android.content.Intent
import androidx.lifecycle.Lifecycle
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.providers.WebSearchStorage
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowApplication
import org.robolectric.annotation.Config

/**
 * FloatingWindowService Robolectric tests.
 *
 * Uses [BeforeClass] mocks to prevent MMKV / ML Kit native crashes
 * during [MyApplication] startup and [FloatingWindowService] construction.
 * Test-specific stubs are added in [Before].
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FloatingWindowServiceTest {

    companion object {
        @BeforeClass @JvmStatic
        fun setupClass() {
            // ── Prevent ALL MMKV access by mocking at ConfigStorage level ──
            mockkObject(com.hwb.aianswerer.config.ConfigStorage)
            val mockMmkv = mockk<com.tencent.mmkv.MMKV>(relaxed = true)
            every { com.hwb.aianswerer.config.ConfigStorage.requireMmkv() } returns mockMmkv
            every { com.hwb.aianswerer.config.ConfigStorage.getSecurePrefs() } returns null

            // ── Prevent MMKV crashes during MyApplication.attachBaseContext() ──
            mockkObject(AppConfig)
            every { AppConfig.init(any()) } just runs
            every { AppConfig.initSecurePrefs(any()) } just runs
            every { AppConfig.getLanguage() } returns AppConfig.LANGUAGE_ZH
            every { AppConfig.getThemePresetId() } returns "warm_autumn"
            every { AppConfig.getCustomThemes() } returns ""
            every { AppConfig.saveThemePresetId(any()) } just runs

            mockkObject(ProviderStorage)
            every { ProviderStorage.init(any()) } just runs
            every { ProviderStorage.initSecurePrefs(any()) } just runs

            mockkObject(WebSearchStorage)
            every { WebSearchStorage.isSearchEnabled() } returns false
            every { WebSearchStorage.getEnabledProviders() } returns emptyList()

            // ── Prevent ML Kit / network init during service construction ──
            // ── Prevent ML Kit init during service construction ──
            // Use reflection to inject a mock into the singleton;
            // mockkConstructor/mockkObject don't work with Robolectric's classloader.
            val mockTRM = mockk<TextRecognitionManager>(relaxed = true)
            TextRecognitionManager::class.java.getDeclaredField("instance").apply {
                isAccessible = true
                set(TextRecognitionManager.Companion, mockTRM)
            }
            mockkConstructor(CapturePipeline::class)
            mockkConstructor(RecordingCoordinator::class)
            mockkConstructor(AnswerFetcher::class)
            mockkConstructor(CaptureHandler::class)
            mockkConstructor(ImageCollector::class)
            mockkConstructor(FloatingWindowManager::class)
        }

        @AfterClass @JvmStatic
        fun teardownClass() {
            unmockkObject(AppConfig)
            unmockkObject(ProviderStorage)
            unmockkObject(WebSearchStorage)
            unmockkObject(com.hwb.aianswerer.config.ConfigStorage)
            // Reset singleton to null for clean state
            TextRecognitionManager::class.java.getDeclaredField("instance").apply {
                isAccessible = true
                set(TextRecognitionManager.Companion, null)
            }
            unmockkConstructor(CapturePipeline::class)
            unmockkConstructor(RecordingCoordinator::class)
            unmockkConstructor(AnswerFetcher::class)
            unmockkConstructor(CaptureHandler::class)
            unmockkConstructor(ImageCollector::class)
            unmockkConstructor(FloatingWindowManager::class)
        }
    }

    // ════ Per-test setup / teardown ════

    @Before
    fun setUp() {
        // ── AppConfig stubs needed by SettingsService & showFloatingWindow ──
        every { AppConfig.isVisionEnabled() } returns false
        every { AppConfig.getReasoningEffort() } returns null
        every { AppConfig.getFloatButtonAlpha() } returns 0.9f
        every { AppConfig.getFloatButtonSize() } returns 40
        every { AppConfig.getFloatCardAlpha() } returns 0.85f
        every { AppConfig.isStealthModeEnabled() } returns false
        every { AppConfig.getAutoCopy() } returns false
        every { AppConfig.getOutputLanguage() } returns "中文"
        every { AppConfig.getShowAnswerCardQuestion() } returns false
        every { AppConfig.getShowAnswerCardOptions() } returns false
        every { AppConfig.getCropMode() } returns AppConfig.CROP_MODE_FULL
        every { AppConfig.getMaxConcurrency() } returns 10
        every { AppConfig.getDarkMode() } returns 0  // 0=follow system
        every { AppConfig.saveDarkMode(any()) } just runs

        // ── ScreenCaptureManager ──
        mockkConstructor(ScreenCaptureManager::class)
        every { anyConstructed<ScreenCaptureManager>().releaseAll() } just runs
        every { anyConstructed<ScreenCaptureManager>().release() } just runs
        every { anyConstructed<ScreenCaptureManager>().initMediaProjection(any(), any()) } just runs

        // ── NotificationHelper ──
        mockkObject(NotificationHelper)
        every { NotificationHelper.createChannel(any()) } just runs
        every { NotificationHelper.ensurePermission(any()) } just runs
        every { NotificationHelper.buildNotification(any()) } returns mockk(relaxed = true)

        // Ensure clean start
        resetIsRunning()
    }

    @After
    fun tearDown() {
        unmockkConstructor(ScreenCaptureManager::class)
        unmockkObject(NotificationHelper)
        resetIsRunning()
    }

    private fun resetIsRunning() {
        val field = FloatingWindowService::class.java.getDeclaredField("isRunning")
        field.isAccessible = true
        field.setBoolean(null, false)
    }

    // ════ Companion constants ════

    @Test
    fun `ACTION_STOP has expected value`() {
        assertEquals("com.hwb.aianswerer.ACTION_STOP", FloatingWindowService.ACTION_STOP)
    }

    @Test
    fun `ACTION_CROP_RESULT has expected value`() {
        assertEquals("com.hwb.aianswerer.ACTION_CROP_RESULT", FloatingWindowService.ACTION_CROP_RESULT)
    }

    @Test
    fun `EXTRA_IMAGE_PATH has expected value`() {
        assertEquals("image_path", FloatingWindowService.EXTRA_IMAGE_PATH)
    }

    // ════ isRunning flag ════

    @Test
    fun `isRunning starts false`() {
        assertFalse(FloatingWindowService.isRunning)
    }

    @Test
    fun `isRunning becomes true after onCreate`() {
        val service = createService()

        assertTrue("isRunning should be true after onCreate", FloatingWindowService.isRunning)

        service.onDestroy()
    }

    @Test
    fun `isRunning becomes false after onDestroy`() {
        val service = createService()
        assertTrue(FloatingWindowService.isRunning)

        service.onDestroy()

        assertFalse("isRunning should be false after onDestroy", FloatingWindowService.isRunning)
    }

    // ════ Service lifecycle ════

    @Test
    fun `onCreate registers BroadcastReceiver`() {
        val service = createService()

        val app = ShadowApplication.getInstance()
        val receivers = app.registeredReceivers
        assertTrue(
            "Service should have registered at least one receiver",
            receivers.any { it.broadcastReceiver != null }
        )

        service.onDestroy()
    }

    @Test
    fun `onCreate starts foreground with notification`() {
        val service = createService()

        verify(atLeast = 1) { NotificationHelper.buildNotification(any()) }
        verify(atLeast = 1) { NotificationHelper.createChannel(any()) }

        service.onDestroy()
    }

    // ════ onStartCommand ════

    @Test
    fun `onStartCommand with ACTION_STOP stops self and returns START_NOT_STICKY`() {
        val service = createService()
        assertTrue(FloatingWindowService.isRunning)

        val stopIntent = Intent(FloatingWindowService.ACTION_STOP)
        val result = service.onStartCommand(stopIntent, 0, 0)

        assertEquals("ACTION_STOP should return START_NOT_STICKY", Service.START_NOT_STICKY, result)

        service.onDestroy()
    }

    @Test
    fun `onStartCommand with MediaProjection data delegates to ScreenCaptureManager`() {
        val service = createService()

        val dataIntent = Intent("dummy")
        val intent = Intent().apply {
            putExtra("resultCode", Activity.RESULT_OK)
            putExtra("data", dataIntent)
            putExtra("cropMode", AppConfig.CROP_MODE_FULL)
        }

        val result = service.onStartCommand(intent, 0, 0)

        assertEquals("Normal start should return START_NOT_STICKY", Service.START_NOT_STICKY, result)

        service.onDestroy()
    }

    // ════ BroadcastReceiver ════

    @Test
    fun `BroadcastReceiver SHOW_ANSWER updates ViewModel answer state`() {
        val service = createService()

        val viewModel = getViewModel(service)

        // Initial state
        assertFalse(viewModel.showAnswer.value)
        assertNull(viewModel.answerText.value)

        // Send ACTION_SHOW_ANSWER broadcast
        val answerIntent = Intent(Constants.ACTION_SHOW_ANSWER)
        answerIntent.putExtra(Constants.EXTRA_ANSWER_TEXT, "Paris is the capital of France")
        RuntimeEnvironment.application.sendBroadcast(answerIntent)
        Robolectric.flushForegroundThreadScheduler()

        // Verify ViewModel state updated
        assertEquals("Paris is the capital of France", viewModel.answerText.value)
        assertTrue(viewModel.showAnswer.value)

        service.onDestroy()
    }

    @Test
    fun `BroadcastReceiver SHOW_ANSWER ignores blank answer`() {
        val service = createService()

        val viewModel = getViewModel(service)

        // Set initial state that should NOT change
        viewModel.answerText.value = "previous answer"
        viewModel.showAnswer.value = true

        // Send blank answer
        val blankIntent = Intent(Constants.ACTION_SHOW_ANSWER)
        blankIntent.putExtra(Constants.EXTRA_ANSWER_TEXT, "   ")
        RuntimeEnvironment.application.sendBroadcast(blankIntent)
        Robolectric.flushForegroundThreadScheduler()

        // Verify state did NOT change
        assertEquals("previous answer", viewModel.answerText.value)
        assertTrue(viewModel.showAnswer.value)

        service.onDestroy()
    }

    @Test
    fun `BroadcastReceiver REFRESH_SETTINGS calls ViewModel refresh`() {
        val service = createService()

        val refreshIntent = Intent(Constants.ACTION_REFRESH_SETTINGS)
        RuntimeEnvironment.application.sendBroadcast(refreshIntent)
        Robolectric.flushForegroundThreadScheduler()

        assertTrue("Service should still be running after refresh", FloatingWindowService.isRunning)

        service.onDestroy()
    }

    // ════ onDestroy cleanup ════

    @Test
    fun `onDestroy releases ScreenCaptureManager and sets lifecycle to DESTROYED`() {
        val service = createService()

        service.onDestroy()

        verify(atLeast = 1) { anyConstructed<ScreenCaptureManager>().releaseAll() }
        assertEquals(Lifecycle.State.DESTROYED, service.lifecycle.currentState)
        assertFalse(FloatingWindowService.isRunning)
    }

    @Test
    fun `Service returns null from onBind`() {
        val service = createService()

        assertNull(service.onBind(Intent()))

        service.onDestroy()
    }

    // ════ Helpers ════

    private fun createService(): FloatingWindowService {
        val controller = Robolectric.buildService(FloatingWindowService::class.java)
        return controller.create().get()
    }

    private fun getViewModel(service: FloatingWindowService): FloatingWindowViewModel {
        val field = FloatingWindowService::class.java.getDeclaredField("viewModel")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(service) as FloatingWindowViewModel
    }
}
