package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// ── Callbacks (called from capture-related coroutines) ────────────────

interface CaptureHandlerCallbacks {
    // ── State reads ────────────────────────────────────────────────────
    fun isRecording(): Boolean
    fun getCropMode(): String?
    fun getSavedCropRect(): CropRect?
    fun getSavedCropRectEach(): CropRect?
    fun isVisionEnabled(): Boolean
    fun isSearchEnabled(): Boolean
    fun isStealthModeEnabled(): Boolean
    fun getFloatButtonSizeDp(): Int
    fun getDensity(): Float

    // ── State writes ───────────────────────────────────────────────────
    fun setSavedCropRect(rect: CropRect?)
    fun setSavedCropRectEach(rect: CropRect?)
    fun setHasContent(has: Boolean)
    fun setCaptureInProgress(enabled: Boolean)
    fun setShowAnswer(show: Boolean)
    fun getCurrentWindowHeightPx(): Float
    fun setCurrentWindowHeightPx(h: Float)

    // ── Window operations ──────────────────────────────────────────────
    fun setFlagSecure(enabled: Boolean)
    fun updateWindowPosition()
    fun updateWindowHeight()

    // ── UI feedback ────────────────────────────────────────────────────
    fun showError(message: String)
    fun showToast(message: String)
    fun setStatus(status: FloatingStatus)
    fun setStatusMessage(msg: String?)
    fun getString(resId: Int, vararg args: Any?): String

    // ── Flow control ───────────────────────────────────────────────────
    /** Called when recognition succeeds — text + optional vision result. */
    fun onTextRecognized(text: String, visionResult: VisionFilterResult?)

    /** Called for recording captures — delegates directly to coordinator. */
    fun onRecordingBitmap(bitmap: Bitmap)

    /** Increment recording capture count & return current value. */
    fun incRecordingCaptureCount(): Int

    /** Return a reference to the current recording fetch job (for cancel). */
    fun getCurrentFetchJob(): Job?
    fun setCurrentFetchJob(job: Job?)
}

// ── Handler ───────────────────────────────────────────────────────────

/**
 * Owns capture → crop → recognition pipeline for the floating window.
 *
 * All UI-side effects are pushed through [callbacks] so the service remains
 * the single owner of Compose state and window management.
 *
 * Recording‑mode captures are forwarded to [recorder] while normal captures
 * feed the recognized text back via [CaptureHandlerCallbacks.onTextRecognized].
 */
class CaptureHandler(
    private val screenCaptureManager: ScreenCaptureManager?,
    private val pipeline: CapturePipeline,
    private val recorder: RecordingCoordinator,
    private val serviceScope: CoroutineScope,
    private val callbacks: CaptureHandlerCallbacks,
    private val context: Context
) {
    private var captureCounter = 0

    // ── Delays (companion for visibility to inline code) ───────────────
    companion object {
        /** Wait for Compose recomposition before screenshotting. */
        const val COMPOSE_DELAY_MS = 50L
        /** Wait for FLAG_SECURE to take effect (2 frames @ 60 fps). */
        const val FLAG_SECURE_DELAY_MS = 33L
    }

    // ── Main entry ─────────────────────────────────────────────────────

    fun handleCapture() {
        captureCounter++
        AppLog.enter("CaptureHandler", "handleCapture recording=${callbacks.isRecording()}")

        // ── Recording branch (must be before isBusy check) ─────────────
        if (callbacks.isRecording()) {
            val maxConcurrency = AppConfig.getMaxConcurrency()
        val activeJobs = recorder.getActiveJobCount()
            if (activeJobs >= maxConcurrency) {
                val msg = context.getString(
                    R.string.recording_concurrency_limit, activeJobs, maxConcurrency
                )
                callbacks.setStatus(FloatingStatus.Error)
                callbacks.setStatusMessage(msg)
                callbacks.showToast(msg)
                return
            }
            val captureIdx = callbacks.incRecordingCaptureCount()
            callbacks.setCaptureInProgress(true)
            callbacks.setShowAnswer(false)
            serviceScope.launch {
                delay(COMPOSE_DELAY_MS)
                val idleH = callbacks.getFloatButtonSizeDp() * callbacks.getDensity() +
                        com.hwb.aianswerer.ui.components.FWDims.idleHeightPaddingDp.value * callbacks.getDensity()
                callbacks.setCurrentWindowHeightPx(idleH)
                callbacks.updateWindowPosition()
                val wasStealth = callbacks.isStealthModeEnabled()
                callbacks.setFlagSecure(enabled = true)
                delay(FLAG_SECURE_DELAY_MS)
                val bitmap = screenCaptureManager?.captureScreen()
                if (!wasStealth) {
                    callbacks.setFlagSecure(enabled = false)
                }
                callbacks.setCaptureInProgress(false)
                if (bitmap == null) {
                    callbacks.showError("截图失败")
                    return@launch
                }
                callbacks.setHasContent(true)
                callbacks.updateWindowHeight()
                dispatchCropForRecording(bitmap)
                callbacks.setStatus(FloatingStatus.Idle)
                delay(50)
                callbacks.setStatusMessage(
                    context.getString(R.string.recording_indicator, captureIdx)
                )
            }
            return
        }

        // ── Normal mode ────────────────────────────────────────────────
        val isBusy = callbacks.getCurrentFetchJob()?.isActive == true
        if (isBusy) {
            callbacks.getCurrentFetchJob()?.cancel()
            callbacks.setCurrentFetchJob(null)
            callbacks.setShowAnswer(false)
            callbacks.setStatus(FloatingStatus.Idle)
            callbacks.setStatusMessage(null)
            return
        }

        callbacks.getCurrentFetchJob()?.cancel()
        // Actually set it — use the service’s own Job holder
        val job = serviceScope.launch {
            try {
                callbacks.setShowAnswer(false)
                callbacks.setStatus(FloatingStatus.Idle)

                // Accessibility mode
                if (AppConfig.isAccessibilityCaptureMode()) {
                    handleAccessibilityCapture()
                    return@launch
                }

                // MediaProjection guard
                if (screenCaptureManager?.isReady != true) {
                    callbacks.showError("截图权限未授权，请在主页重新点击\"进入答题模式\"")
                    return@launch
                }

                AppLog.d("CaptureHandler", "starting captureScreen…")
                callbacks.setCaptureInProgress(true)
                delay(COMPOSE_DELAY_MS)

                val idleH = callbacks.getFloatButtonSizeDp() * callbacks.getDensity() +
                        com.hwb.aianswerer.ui.components.FWDims.idleHeightPaddingDp.value * callbacks.getDensity()
                callbacks.setCurrentWindowHeightPx(idleH)
                callbacks.updateWindowPosition()

                val wasStealth = callbacks.isStealthModeEnabled()
                callbacks.setFlagSecure(enabled = true)
                delay(FLAG_SECURE_DELAY_MS)

                val bitmap = withTimeout(8_000L) {
                    screenCaptureManager?.captureScreen()
                }
                AppLog.d("CaptureHandler", "captureScreen done, bitmap=${bitmap != null}")

                if (!wasStealth) {
                    callbacks.setFlagSecure(enabled = false)
                }
                callbacks.setCaptureInProgress(false)

                if (bitmap == null) {
                    callbacks.showError("截图失败")
                    return@launch
                }
                callbacks.setHasContent(true)
                callbacks.updateWindowHeight()

                dispatchCropThenRecognize(bitmap)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                callbacks.setStatus(FloatingStatus.Idle)
                callbacks.setStatusMessage(null)
                callbacks.showToast("截图超时，请重试")
            } catch (e: CancellationException) {
                callbacks.setStatus(FloatingStatus.Idle)
                callbacks.setStatusMessage(null)
                throw e
            } catch (e: Exception) {
                callbacks.showError("操作失败: ${e.message ?: ""}")
            }
        }
        callbacks.setCurrentFetchJob(job)
    }

    // ── Accessibility ──────────────────────────────────────────────────

    private suspend fun handleAccessibilityCapture() {
        callbacks.setStatus(FloatingStatus.Recognizing)
        callbacks.setStatusMessage("正在读取屏幕…")
        delay(COMPOSE_DELAY_MS)

        // Hide floating window from a11y
        // (floatingView ref lives in service; we signal via callback)
        delay(100)

        var screenText = ScreenReaderService.readScreenText()
        if (screenText.isNullOrBlank()) {
            delay(500)
            screenText = ScreenReaderService.readScreenText()
        }

        if (screenText.isNullOrBlank()) {
            val enabled = ScreenReaderService.isAccessibilityServiceEnabled(context)
            val msg = when {
                !enabled -> "无障碍服务未启用"
                !ScreenReaderService.isActive -> "无障碍服务已启用但未连接，请在系统设置中关闭后重新开启"
                else -> "无法读取屏幕内容，请确保当前页面有可见文字"
            }
            callbacks.showError(msg)
            return
        }

        // VLM mode: screenshot + vision analysis
        if (callbacks.isVisionEnabled() && screenCaptureManager?.isReady == true) {
            callbacks.setStatus(FloatingStatus.Capturing)
            delay(COMPOSE_DELAY_MS)
            val bitmap = screenCaptureManager?.captureScreen()
            if (bitmap != null) {
                processBitmapWithVlm(bitmap)
            } else {
                callbacks.setStatusMessage("识别完成")
                callbacks.onTextRecognized(screenText, null)
            }
        } else {
            callbacks.setStatusMessage("识别完成")
            callbacks.onTextRecognized(screenText, null)
        }
    }

    // ── Crop dispatch ─────────────────────────────────────────────────

    /** Dispatch cropped or full bitmap to OCR/VLM for normal mode. */
    private suspend fun dispatchCropThenRecognize(bitmap: Bitmap) {
        try {
            when (callbacks.getCropMode()) {
                AppConfig.CROP_MODE_FULL -> processBitmap(bitmap)
                AppConfig.CROP_MODE_EACH -> {
                    callbacks.getSavedCropRectEach()?.let { rect ->
                        val cropped = try {
                            ImageCropUtil.cropBitmap(bitmap, rect)
                        } catch (e: Exception) { bitmap.recycle(); throw e }
                        try { processBitmap(cropped) }
                        finally { if (!cropped.isRecycled) cropped.recycle() }
                    } ?: launchCropActivity(bitmap, null)
                }
                AppConfig.CROP_MODE_ONCE -> {
                    callbacks.getSavedCropRect()?.let { rect ->
                        val cropped = try {
                            ImageCropUtil.cropBitmap(bitmap, rect)
                        } catch (e: Exception) { bitmap.recycle(); throw e }
                        try { processBitmap(cropped) }
                        finally { if (!cropped.isRecycled) cropped.recycle() }
                    } ?: launchCropActivity(bitmap, null)
                }
                else -> {
                    AppLog.d("CaptureHandler", "unknown cropMode, fallback to full")
                    processBitmap(bitmap)
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Dispatch cropped or full bitmap to recorder for recording mode. */
    private fun dispatchCropForRecording(bitmap: Bitmap) {
        when (callbacks.getCropMode()) {
            AppConfig.CROP_MODE_FULL -> callbacks.onRecordingBitmap(bitmap)
            AppConfig.CROP_MODE_EACH -> {
                callbacks.getSavedCropRectEach()?.let { rect ->
                    val cropped = try { ImageCropUtil.cropBitmap(bitmap, rect) }
                    catch (e: Exception) { bitmap.recycle(); throw e }
                    bitmap.recycle()
                    callbacks.onRecordingBitmap(cropped)
                } ?: launchCropActivity(bitmap, null)
            }
            AppConfig.CROP_MODE_ONCE -> {
                callbacks.getSavedCropRect()?.let { rect ->
                    val cropped = try { ImageCropUtil.cropBitmap(bitmap, rect) }
                    catch (e: Exception) { bitmap.recycle(); throw e }
                    bitmap.recycle()
                    callbacks.onRecordingBitmap(cropped)
                } ?: launchCropActivity(bitmap, null)
            }
            else -> callbacks.onRecordingBitmap(bitmap)
        }
    }

    // ── Cropped-image handling (from BroadcastReceiver) ───────────────

    /** Called when ImageCropActivity result arrives (normal mode). */
    fun handleCroppedImage(imagePath: String, cropRect: CropRect) {
        serviceScope.launch {
            try {
                val bitmap = ImageCropUtil.loadBitmapFromFile(imagePath)
                try {
                    val croppedBitmap = ImageCropUtil.cropBitmap(bitmap, cropRect)
                    processBitmap(croppedBitmap)
                } finally {
                    bitmap.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                callbacks.showError("裁剪失败: ${e.message ?: ""}")
            } finally {
                ImageCropUtil.deleteTempFile(imagePath)
            }
        }
    }

    // ── Crop activity ─────────────────────────────────────────────────

    private fun launchCropActivity(bitmap: Bitmap, previousCropRect: CropRect?) {
        try {
            val imagePath = ImageCropUtil.saveBitmapToTempFile(bitmap, context.cacheDir)
            bitmap.recycle()
            val intent = Intent(context, ImageCropActivity::class.java).apply {
                putExtra(ImageCropActivity.EXTRA_IMAGE_PATH, imagePath)
                previousCropRect?.let {
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_TOP_LEFT_X, it.topLeft.x)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_TOP_LEFT_Y, it.topLeft.y)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_BOTTOM_RIGHT_X, it.bottomRight.x)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_BOTTOM_RIGHT_Y, it.bottomRight.y)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            callbacks.setHasContent(false)
            callbacks.updateWindowHeight()
            context.startActivity(intent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callbacks.showError("启动裁剪失败: ${e.message ?: ""}")
        }
    }

    // ── Recognition dispatch ───────────────────────────────────────────

    private suspend fun processBitmap(bitmap: Bitmap) {
        try {
            if (AppConfig.isVisionEnabled()) {
                processBitmapWithVlm(bitmap)
            } else {
                processBitmapWithOcr(bitmap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callbacks.showError("识别失败: ${e.message ?: ""}")
        }
    }

    private suspend fun processBitmapWithOcr(bitmap: Bitmap) {
        callbacks.setStatus(FloatingStatus.Recognizing)
        callbacks.setStatusMessage("识别中…")

        pipeline.recognizeOcr(bitmap)
            .onSuccess { recognizedText ->
                bitmap.recycle()
                callbacks.setStatusMessage("识别完成")
                if (!pipeline.looksLikeQuestion(recognizedText)) {
                    callbacks.showError("未识别到题目")
                    return
                }
                callbacks.onTextRecognized(recognizedText, null)
            }
            .onFailure { error ->
                bitmap.recycle()
                callbacks.showError("识别失败: ${error.message ?: ""}")
            }
    }

    private suspend fun processBitmapWithVlm(bitmap: Bitmap) {
        callbacks.setStatus(FloatingStatus.Recognizing)
        callbacks.setStatusMessage("视觉模型分析中…")

        pipeline.recognizeVlm(bitmap)
            .onSuccess { filter ->
                bitmap.recycle()
                if (!filter.hasQuestions) {
                    callbacks.showError("未识别到题目")
                    return
                }
                callbacks.setStatusMessage(
                    if (filter.questionCount > 1) "检测到 ${filter.questionCount} 道题目"
                    else "检测到题目"
                )
                if (filter.extractedText.isBlank()) {
                    callbacks.showError("视觉模型未提取到文本")
                    return
                }
                callbacks.onTextRecognized(filter.extractedText, filter)
            }
            .onFailure { e ->
                AppLog.w("CaptureHandler", "VLM分析失败，降级为OCR模式", e)
                callbacks.setStatusMessage("视觉模型失败，降级为OCR…")
                processBitmapWithOcr(bitmap)
            }
    }
}
