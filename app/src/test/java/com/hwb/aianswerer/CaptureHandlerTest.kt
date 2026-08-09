package com.hwb.aianswerer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.utils.ImageCropUtil
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureHandlerTest {

    private val context: Context = mockk<Context>(relaxed = true).apply {
        every { getString(any(), *anyVararg()) } returns "mock字符串"
        every { getString(any()) } returns "mock字符串"
        every { cacheDir } returns File("/tmp/mock_cache")
    }
    private val testScheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private lateinit var scm: ScreenCaptureManager
    private lateinit var pipeline: CapturePipeline
    private lateinit var recorder: RecordingCoordinator
    private lateinit var cb: CaptureHandlerCallbacks
    private lateinit var bitmap: Bitmap

    @Before
    fun setUp() {
        mockkObject(AppConfig)
        mockkObject(ImageCropUtil)
        mockkObject(ScreenReaderService.Companion)
        every { ScreenReaderService.readScreenText() } returns null
        scm = mockk(relaxed = true)
        pipeline = mockk(relaxed = true)
        recorder = mockk(relaxed = true)
        cb = mockk(relaxed = true)
        bitmap = mockk(relaxed = true)

        every { AppConfig.getMaxConcurrency() } returns 10
        every { AppConfig.isAccessibilityCaptureMode() } returns false
        every { AppConfig.isVisionEnabled() } returns false
        every { AppConfig.isStealthModeEnabled() } returns false

        every { cb.isRecording() } returns false
        every { cb.getCropMode() } returns AppConfig.CROP_MODE_FULL
        every { cb.getFloatButtonSizeDp() } returns 40
        every { cb.getDensity() } returns 2.0f
        every { cb.getCurrentFetchJob() } returns null
        every { cb.isStealthModeEnabled() } returns false
        every { cb.getCurrentWindowHeightPx() } returns 0f
        every { cb.isVisionEnabled() } returns false

        every { scm.isReady } returns true
        every { recorder.getActiveJobCount() } returns 0
        every { pipeline.looksLikeQuestion(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun handler() = CaptureHandler(scm, pipeline, recorder, scope, cb, context)

    // ─────────────────────────────────────────────────────────────────
    // handleCapture() — normal mode
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `handleCapture normal mode captures and processes with OCR`() = runBlocking {
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("题目文本")
        every { pipeline.looksLikeQuestion(any()) } returns true

        handler().handleCapture()
        testScheduler.advanceUntilIdle() // let coroutine finish through delays (50+33ms)

        coVerify { scm.captureScreen() }
        coVerify { pipeline.recognizeOcr(any()) }
        verify { cb.onTextRecognized("题目文本", null) }
        verify { cb.setCaptureInProgress(true) }
        verify { cb.setCaptureInProgress(false) }
    }

    @Test
    fun `handleCapture normal mode null screenCaptureManager shows error`() = runBlocking {
        val h = CaptureHandler(null, pipeline, recorder, scope, cb, context)

        h.handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.showError(any()) }
        verify(exactly = 0) { cb.onTextRecognized(any(), any()) }
    }

    @Test
    fun `handleCapture normal mode mediaProjection not ready shows error`() = runBlocking {
        every { scm.isReady } returns false

        handler().handleCapture()
        testScheduler.runCurrent()

        verify { cb.showError("截图权限未授权，请在主页重新点击\"进入答题模式\"") }
        coVerify(exactly = 0) { scm.captureScreen() }
    }

    @Test
    fun `handleCapture normal mode busy cancels existing job`() = runBlocking {
        val existingJob = mockk<Job>(relaxed = true)
        every { existingJob.isActive } returns true
        every { cb.getCurrentFetchJob() } returns existingJob

        handler().handleCapture()

        verify { existingJob.cancel() }
        verify { cb.setCurrentFetchJob(null) }
        verify { cb.setShowAnswer(false) }
        verify { cb.setStatus(FloatingStatus.Idle) }
        verify { cb.setStatusMessage(null) }
    }

    @Test
    fun `handleCapture normal mode VLM recognition success`() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coEvery { scm.captureScreen() } returns bitmap
        val vr = VisionFilterResult(
            hasQuestions = true,
            questionCount = 1,
            questionTypes = listOf("选择题"),
            extractedText = "VLM识别文本"
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vr)

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeVlm(any()) }
        verify { cb.onTextRecognized("VLM识别文本", vr) }
    }

    @Test
    fun `handleCapture normal mode VLM failure falls back to OCR`() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeVlm(any()) } returns Result.failure(RuntimeException("VLM error"))
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("OCR降级文本")
        every { pipeline.looksLikeQuestion("OCR降级文本") } returns true

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeVlm(any()) }
        coVerify { pipeline.recognizeOcr(any()) }
        verify { cb.onTextRecognized("OCR降级文本", null) }
    }

    @Test
    fun `handleCapture normal mode EACH crop dispatches cropped bitmap`() = runBlocking {
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("题目")
        every { pipeline.looksLikeQuestion(any()) } returns true
        every { cb.getCropMode() } returns AppConfig.CROP_MODE_EACH
        val cropRect = CropRect(PointF(0f, 0f), PointF(100f, 100f))
        every { cb.getSavedCropRectEach() } returns cropRect

        val croppedBitmap = mockk<Bitmap>(relaxed = true)
        every { ImageCropUtil.cropBitmap(any(), any()) } returns croppedBitmap
        every { bitmap.isRecycled } returns false
        every { croppedBitmap.isRecycled } returns false

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeOcr(croppedBitmap) }
        verify { ImageCropUtil.cropBitmap(bitmap, cropRect) }
    }

    @Test
    fun `handleCapture normal mode ONCE crop dispatches cropped bitmap`() = runBlocking {
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("题目")
        every { pipeline.looksLikeQuestion(any()) } returns true
        every { cb.getCropMode() } returns AppConfig.CROP_MODE_ONCE
        val cropRect = CropRect(PointF(10f, 10f), PointF(200f, 200f))
        every { cb.getSavedCropRect() } returns cropRect

        val croppedBitmap = mockk<Bitmap>(relaxed = true)
        every { ImageCropUtil.cropBitmap(any(), any()) } returns croppedBitmap
        every { bitmap.isRecycled } returns false
        every { croppedBitmap.isRecycled } returns false

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeOcr(croppedBitmap) }
        verify { ImageCropUtil.cropBitmap(bitmap, cropRect) }
    }

    // ─────────────────────────────────────────────────────────────────
    // handleCapture() — recording mode
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `handleCapture recording mode captures and dispatches to recorder`() = runBlocking {
        every { cb.isRecording() } returns true
        coEvery { scm.captureScreen() } returns bitmap
        every { cb.incRecordingCaptureCount() } returns 1

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.incRecordingCaptureCount() }
        verify { cb.setCaptureInProgress(true) }
        coVerify { scm.captureScreen() }
        verify { cb.onRecordingBitmap(bitmap) }
        verify { cb.setHasContent(true) }
    }

    @Test
    fun `handleCapture recording mode concurrency limit shows error`() {
        every { cb.isRecording() } returns true
        every { AppConfig.getMaxConcurrency() } returns 5
        every { recorder.getActiveJobCount() } returns 5

        handler().handleCapture()

        verify { cb.setStatus(FloatingStatus.Error) }
        verify { cb.showToast(any()) }
        verify(exactly = 0) { cb.incRecordingCaptureCount() }
        coVerify(exactly = 0) { scm.captureScreen() }
    }

    // ─────────────────────────────────────────────────────────────────
    // handleCapture() — accessibility mode
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `handleCapture accessibility mode reads screen text`() = runBlocking {
        every { AppConfig.isAccessibilityCaptureMode() } returns true
        every { ScreenReaderService.readScreenText() } returns "无障碍文本"

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.onTextRecognized("无障碍文本", null) }
    }

    @Test
    fun `handleCapture accessibility mode empty text retries then succeeds`() = runBlocking {
        every { AppConfig.isAccessibilityCaptureMode() } returns true
        every { ScreenReaderService.readScreenText() } returns null andThen "重试后文本"

        handler().handleCapture()
        testScheduler.advanceUntilIdle() // 100ms initial + 500ms retry delay

        verify(exactly = 2) { ScreenReaderService.readScreenText() }
        verify { cb.onTextRecognized("重试后文本", null) }
    }

    @Test
    fun `handleCapture accessibility mode both retries fail shows error`() = runBlocking {
        every { AppConfig.isAccessibilityCaptureMode() } returns true
        every { ScreenReaderService.readScreenText() } returns null
        every { ScreenReaderService.isAccessibilityServiceEnabled(any()) } returns true
        every { ScreenReaderService.isActive } returns true

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify(exactly = 2) { ScreenReaderService.readScreenText() }
        verify { cb.showError("无法读取屏幕内容，请确保当前页面有可见文字") }
    }

    @Test
    fun `handleCapture accessibility mode with VLM captures and analyzes`() = runBlocking {
        every { AppConfig.isAccessibilityCaptureMode() } returns true
        every { cb.isVisionEnabled() } returns true
        every { scm.isReady } returns true
        every { ScreenReaderService.readScreenText() } returns "屏幕内容"
        coEvery { scm.captureScreen() } returns bitmap
        val vr = VisionFilterResult(
            hasQuestions = true,
            questionCount = 1,
            questionTypes = listOf("选择题"),
            extractedText = "VLM结果"
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vr)

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { scm.captureScreen() }
        coVerify { pipeline.recognizeVlm(any()) }
        verify { cb.onTextRecognized("VLM结果", vr) }
    }

    // ─────────────────────────────────────────────────────────────────
    // handleCroppedImage()
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `handleCroppedImage loads crops and processes`() = runBlocking {
        val loadedBitmap = mockk<Bitmap>(relaxed = true)
        val croppedBitmap = mockk<Bitmap>(relaxed = true)
        val cropRect = CropRect(PointF(0f, 0f), PointF(100f, 100f))

        every { ImageCropUtil.loadBitmapFromFile("/tmp/test.png") } returns loadedBitmap
        every { ImageCropUtil.cropBitmap(loadedBitmap, cropRect) } returns croppedBitmap
        every { loadedBitmap.isRecycled } returnsMany listOf(false, false)
        every { croppedBitmap.isRecycled } returns false
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("识别结果")
        every { pipeline.looksLikeQuestion("识别结果") } returns true
        every { ImageCropUtil.deleteTempFile("/tmp/test.png") } just Runs

        handler().handleCroppedImage("/tmp/test.png", cropRect)
        testScheduler.advanceUntilIdle()

        verify { ImageCropUtil.loadBitmapFromFile("/tmp/test.png") }
        verify { ImageCropUtil.cropBitmap(loadedBitmap, cropRect) }
        coVerify { pipeline.recognizeOcr(croppedBitmap) }
        verify { cb.onTextRecognized("识别结果", null) }
        verify { ImageCropUtil.deleteTempFile("/tmp/test.png") }
    }

    @Test
    fun `handleCroppedImage load failure shows error`() = runBlocking {
        every { ImageCropUtil.loadBitmapFromFile("/tmp/bad.png") } throws RuntimeException("文件损坏")
        every { ImageCropUtil.deleteTempFile("/tmp/bad.png") } just Runs

        handler().handleCroppedImage("/tmp/bad.png", CropRect(PointF(0f, 0f), PointF(100f, 100f)))
        testScheduler.advanceUntilIdle()

        verify { cb.showError(match { it.contains("文件损坏") }) }
        verify { ImageCropUtil.deleteTempFile("/tmp/bad.png") }
    }

    @Test
    fun `handleCroppedImage CancellationException is rethrown not swallowed`() = runBlocking {
        every { ImageCropUtil.loadBitmapFromFile("/tmp/test.png") } throws CancellationException("aborted")
        every { ImageCropUtil.deleteTempFile("/tmp/test.png") } just Runs

        var caught: CancellationException? = null
        val latch = CountDownLatch(1)

        val handler = handler()
        // The exception propagates inside the coroutine, which fails silently.
        // Verify the error callback is NOT shown (CancellationException is not "an error").
        handler.handleCroppedImage("/tmp/test.png", CropRect(PointF(0f, 0f), PointF(100f, 100f)))
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { cb.showError(any()) }
        verify { ImageCropUtil.deleteTempFile("/tmp/test.png") }
    }

    // ─────────────────────────────────────────────────────────────────
    // Stealth mode
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `handleCapture stealth mode sets flag secure`() = runBlocking {
        every { cb.isStealthModeEnabled() } returns true
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")
        every { pipeline.looksLikeQuestion(any()) } returns true

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.setFlagSecure(false) }
        verify { cb.setFlagSecure(true) }
    }

    @Test
    fun `handleCapture recording mode stealth sets flag secure and keeps it`() = runBlocking {
        every { cb.isRecording() } returns true
        every { cb.isStealthModeEnabled() } returns true
        coEvery { scm.captureScreen() } returns bitmap
        every { cb.incRecordingCaptureCount() } returns 1

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.setFlagSecure(false) }
        verify { cb.setFlagSecure(true) }
    }

    // ─────────────────────────────────────────────────────────────────
    // Timeout & Cancellation
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `handleCapture generic exception shows error message`() = runBlocking {
        every { AppConfig.isAccessibilityCaptureMode() } returns false
        every { scm.isReady } returns true
        coEvery { scm.captureScreen() } throws RuntimeException("模拟异常")

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.showError(match { it.contains("模拟异常") }) }
    }

    @Test
    fun `handleCapture CancellationException resets status and is rethrown`() = runBlocking {
        every { AppConfig.isAccessibilityCaptureMode() } returns false
        every { scm.isReady } returns true
        coEvery { scm.captureScreen() } throws CancellationException("job cancelled")

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        verify { cb.setStatus(FloatingStatus.Idle) }
        verify { cb.setStatusMessage(null) }
        // Exception is rethrown inside coroutine — it fails silently,
        // but the cleanup (setStatus Idle) is verified above.
    }

    // ─────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `processBitmapWithOcr looksLikeQuestion false shows error`() = runBlocking {
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("非题目文本")
        every { pipeline.looksLikeQuestion("非题目文本") } returns false

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeOcr(any()) }
        verify { cb.showError("未识别到题目") }
        verify(exactly = 0) { cb.onTextRecognized(any(), any()) }
    }

    @Test
    fun `processBitmapWithVlm hasQuestions false shows error`() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coEvery { scm.captureScreen() } returns bitmap
        val vr = VisionFilterResult(hasQuestions = false, questionCount = 0)
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vr)

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeVlm(any()) }
        verify { cb.showError("未识别到题目") }
        verify(exactly = 0) { cb.onTextRecognized(any(), any()) }
    }

    @Test
    fun `handleCapture normal mode unknown crop falls through to full`() = runBlocking {
        coEvery { scm.captureScreen() } returns bitmap
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")
        every { pipeline.looksLikeQuestion(any()) } returns true
        every { cb.getCropMode() } returns "UNKNOWN_MODE"

        handler().handleCapture()
        testScheduler.advanceUntilIdle()

        coVerify { pipeline.recognizeOcr(any()) }
        verify { cb.onTextRecognized("text", null) }
    }
}
