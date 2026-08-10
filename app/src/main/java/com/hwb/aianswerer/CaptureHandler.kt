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
import com.hwb.aianswerer.ScreenReaderService

// ── Callbacks (called from capture-related coroutines) ────────────────

interface CaptureHandlerCallbacks {
    // ── State reads ────────────────────────────────────────────────────
    fun isRecording(): Boolean
    fun getCropMode(): String?
    fun getSavedCropRect(): CropRect?
    fun getSavedCropRectEach(): CropRect?
    fun isVisionEnabled(): Boolean
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
    fun setWindowAlpha(alpha: Float)
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

    /** Current recording capture count (for status message). */
    fun getRecordingCaptureCount(): Int

    /** Return a reference to the current recording fetch job (for cancel). */
    fun getCurrentFetchJob(): Job?
    fun setCurrentFetchJob(job: Job?)

    /** Clear all answer state for a fresh capture. */
    fun clearAnswers()
    /** Whether image-collection mode is active. */
    fun isImageCollecting(): Boolean
    /**
     * Called to pass recognized screen text to the image collector (multi-image mode, accessibility path).
     * 序号由 ImageCollector 进站时同步生成。
     */
    fun onImageText(text: String)

    /**
     * Called to pass a screenshot bitmap to the image collector (multi-image mode).
     * 与录制模式 onRecordingBitmap 对称；序号由 ImageCollector 进站时同步生成。
     */
    fun onImageBitmap(bitmap: Bitmap)

    /** Current image-collection collected count (for status message). */
    fun getImageCollectCount(): Int

    /** Current image-collection capture count (total captured, for status message x/n). */
    fun getImageCaptureCount(): Int
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
            callbacks.setCaptureInProgress(true)
            callbacks.setCaptureInProgress(true)
            callbacks.setShowAnswer(false)
            // Accessibility text mode: read screen directly when capture mode is
            // '屏幕读取' and VLM is OFF. Screen reading is same level as OCR —
            // both produce text for LLM. VLM needs screenshots (half level above).
            val useAccessibilityText = AppConfig.isAccessibilityCaptureMode() && !callbacks.isVisionEnabled()
            serviceScope.launch {
                delay(COMPOSE_DELAY_MS)
                val idleH = callbacks.getFloatButtonSizeDp() * callbacks.getDensity() +
                        com.hwb.aianswerer.ui.components.FWDims.idleHeightPaddingDp.value * callbacks.getDensity()
                callbacks.setCurrentWindowHeightPx(idleH)
                callbacks.updateWindowPosition()
                if (useAccessibilityText) {
                    // Screen reading path — read text directly, no screenshot needed
                    val screenText = readScreenWithRetry()
                    callbacks.setCaptureInProgress(false)
                    if (screenText.isNullOrBlank()) {
                        callbacks.showError("屏幕读取失败")
                        return@launch
                    }
                    if (!pipeline.looksLikeQuestion(screenText)) {
                        callbacks.showError("未识别到题目")
                        return@launch
                    }
                    // P1-4: 文本路径对称守卫——stop 后丢弃迟到文本，避免计数分叉
                    if (!callbacks.isRecording()) return@launch
                    callbacks.setHasContent(true)
                    callbacks.updateWindowHeight()
                    // M10: 成功读取后才计数（与 RecordingCoordinator 的 _captureCount 对齐）
                    callbacks.incRecordingCaptureCount()
                    recorder.processText(screenText)
                } else {
                    // Screenshot path: non-accessibility mode, or accessibility+VLM
                    val wasStealth = callbacks.isStealthModeEnabled()
                    try {
                        callbacks.setWindowAlpha(0f)           // hide window
                        callbacks.setFlagSecure(enabled = false) // remove FLAG_SECURE for clean screenshot
                        delay(FLAG_SECURE_DELAY_MS)
                        var bitmap = screenCaptureManager?.captureScreen()
                        if (bitmap == null) {
                            delay(300)
                            bitmap = screenCaptureManager?.captureScreen()
                        }
                        callbacks.setCaptureInProgress(false)
                        if (bitmap == null) {
                            // Accessibility+VLM mode: fall back to screen text (same as normal mode)
                            if (AppConfig.isAccessibilityCaptureMode()) {
                                // Accessibility+VLM fallback: read screen text
                                val screenText = readScreenWithRetry()
                                if (screenText.isNullOrBlank()) {
                                    callbacks.showError("截图失败且屏幕读取失败")
                                    return@launch
                                }
                                if (!pipeline.looksLikeQuestion(screenText)) {
                                    callbacks.showError("未识别到题目")
                                    return@launch
                                }
                                // P1-4: 回退文本路径对称守卫
                                if (!callbacks.isRecording()) return@launch
                                callbacks.setHasContent(true)
                                callbacks.updateWindowHeight()
                                callbacks.showToast("视觉模型截图失败，已使用屏幕文字")
                                // M10: 成功读取后才计数
                                callbacks.incRecordingCaptureCount()
                                recorder.processText(screenText)
                            } else {
                                callbacks.showError("截图失败")
                                return@launch
                            }
                        } else {
                            // B7: 截图落地时若已停止录制，丢弃迟到截图（recorder.processBitmap 也有 isActive 守卫，此处防止计数虚增）
                            if (!callbacks.isRecording()) {
                                if (!bitmap.isRecycled) bitmap.recycle()
                                return@launch
                            }
                            callbacks.setHasContent(true)
                            callbacks.updateWindowHeight() // P1-3: 恢复 B7 守卫误删的调用
                            // M10: 截图成功后才计数（原在捕获前 inc，截图失败会虚增）
                            callbacks.incRecordingCaptureCount()
                            dispatchCropForRecording(bitmap)
                        }
                    } finally {
                        callbacks.setWindowAlpha(if (wasStealth) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)  // restore alpha
                        if (wasStealth) { callbacks.setFlagSecure(enabled = true) } // restore FLAG_SECURE
                        callbacks.setCaptureInProgress(false)
                    }
                }
                callbacks.setStatus(FloatingStatus.Idle)
                delay(50)
                callbacks.setStatusMessage(
                    context.getString(R.string.recording_indicator, callbacks.getRecordingCaptureCount())
                )
            }
            return
        }

        // ── Image collection mode ─────────────────────────────────────
        // 与录制分支同构：截图/读屏 → dispatchCropForImageCollecting → onImageBitmap
        // 识别与序号全部交给 ImageCollector 内部处理（进站同步生成序号）
        if (callbacks.isImageCollecting()) {
            AppLog.enter("CaptureHandler", "handleCapture imageMode #$captureCounter")

            callbacks.setCaptureInProgress(true)
            callbacks.setShowAnswer(false)
            serviceScope.launch {
                delay(COMPOSE_DELAY_MS)
                val idleH = callbacks.getFloatButtonSizeDp() * callbacks.getDensity() +
                        com.hwb.aianswerer.ui.components.FWDims.idleHeightPaddingDp.value * callbacks.getDensity()
                callbacks.setCurrentWindowHeightPx(idleH)
                callbacks.updateWindowPosition()

                // Accessibility text mode: read screen directly when capture mode is
                // '屏幕读取' and VLM is OFF（与录制分支同级）
                val useAccessibilityText = AppConfig.isAccessibilityCaptureMode() && !callbacks.isVisionEnabled()
                if (useAccessibilityText) {
                    val screenText = readScreenWithRetry()
                    callbacks.setCaptureInProgress(false)
                    if (screenText.isNullOrBlank()) {
                        callbacks.showError("屏幕读取失败")
                        return@launch
                    }
                    // stop 后丢弃迟到文本
                    if (!callbacks.isImageCollecting()) return@launch
                    callbacks.setHasContent(true)
                    callbacks.updateWindowHeight()
                    callbacks.onImageText(screenText)
                    callbacks.setStatus(FloatingStatus.Idle)
                    return@launch
                }

                // Screenshot path
                val wasStealth = callbacks.isStealthModeEnabled()
                try {
                    callbacks.setWindowAlpha(0f)           // hide window
                    callbacks.setFlagSecure(enabled = false) // remove FLAG_SECURE for clean screenshot
                    delay(FLAG_SECURE_DELAY_MS)
                    var bitmap = screenCaptureManager?.captureScreen()
                    if (bitmap == null) {
                        delay(300)
                        bitmap = screenCaptureManager?.captureScreen()
                    }
                    callbacks.setCaptureInProgress(false)
                    if (bitmap == null) {
                        // Accessibility+VLM fallback: read screen text
                        if (AppConfig.isAccessibilityCaptureMode()) {
                            val screenText = readScreenWithRetry()
                            if (screenText.isNullOrBlank()) {
                                callbacks.showError("截图失败且屏幕读取失败")
                                return@launch
                            }
                            if (!callbacks.isImageCollecting()) return@launch
                            callbacks.setHasContent(true)
                            callbacks.updateWindowHeight()
                            callbacks.showToast("视觉模型截图失败，已使用屏幕文字")
                            callbacks.onImageText(screenText)
                        } else {
                            callbacks.showError("截图失败")
                            return@launch
                        }
                    } else {
                        // stop 后丢弃迟到截图（ImageCollector.processBitmap 也有 isActive 守卫）
                        if (!callbacks.isImageCollecting()) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            return@launch
                        }
                        callbacks.setHasContent(true)
                        callbacks.updateWindowHeight()
                        callbacks.setStatus(FloatingStatus.Recognizing)
                        callbacks.setStatusMessage("识别中…")
                        dispatchCropForImageCollecting(bitmap)
                    }
                } finally {
                    callbacks.setWindowAlpha(if (wasStealth) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)
                    if (wasStealth) { callbacks.setFlagSecure(enabled = true) }
                    callbacks.setCaptureInProgress(false)
                }
                callbacks.setStatus(FloatingStatus.Idle)
                // Fix B: 不再覆盖 statusMessage——保留"识别中…"，由 ImageCollector.onProgressUpdate
                //        实时更新为"图片收集中 (x/n)"。若此处读 ViewModel 缓存会滞后
                //        （识别异步，缓存未更新时显示旧值如 0/0、1/2）
                delay(50)
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
        callbacks.clearAnswers()
        // Actually set it — use the service's own Job holder
        val job = serviceScope.launch {
            val wasStealth = callbacks.isStealthModeEnabled()
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

                callbacks.setWindowAlpha(0f)
                callbacks.setFlagSecure(enabled = false)
                delay(FLAG_SECURE_DELAY_MS)
                val bitmap = withTimeout(8_000L) {
                    screenCaptureManager?.captureScreen()
                }
                callbacks.setWindowAlpha(if (wasStealth) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)
                if (wasStealth) { callbacks.setFlagSecure(enabled = true) }
                callbacks.setCaptureInProgress(false)

                AppLog.d("CaptureHandler", "captureScreen done, bitmap=${bitmap != null}")

                if (bitmap == null) {
                    callbacks.showError("截图失败")
                    return@launch
                }
                callbacks.setHasContent(true)
                callbacks.updateWindowHeight()

                dispatchCropThenRecognize(bitmap)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                callbacks.setWindowAlpha(if (wasStealth) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)
                if (wasStealth) { callbacks.setFlagSecure(enabled = true) }
                callbacks.setCaptureInProgress(false)
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
        val screenText = readScreenWithRetry()

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
        // Text-only mode: validate screen text before proceeding.
        // VLM mode: always try — VLM can see questions that text extraction misses.
        val hasQuestionText = pipeline.looksLikeQuestion(screenText)

        // VLM mode: screenshot + vision analysis
        if (callbacks.isVisionEnabled() && screenCaptureManager?.isReady == true) {
            callbacks.setCaptureInProgress(true)
            callbacks.setStatus(FloatingStatus.Capturing)
            delay(COMPOSE_DELAY_MS)
            val wasStealth = callbacks.isStealthModeEnabled()
            callbacks.setWindowAlpha(0f)
            callbacks.setFlagSecure(enabled = false)
            delay(FLAG_SECURE_DELAY_MS)
            val bitmap = screenCaptureManager?.captureScreen()
                callbacks.setWindowAlpha(if (wasStealth) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)
                if (wasStealth) { callbacks.setFlagSecure(enabled = true) }
            callbacks.setCaptureInProgress(false)
            if (bitmap != null) {
                processBitmapWithVlm(bitmap)
            } else if (hasQuestionText) {
                callbacks.setStatusMessage("识别完成")
                callbacks.onTextRecognized(screenText, null)
            } else {
                callbacks.showError("截图失败，且未识别到题目文本")
            }
        } else {
            if (!hasQuestionText) {
                callbacks.showError("未识别到题目")
                return
            }
            callbacks.setStatusMessage("识别完成")
            callbacks.onTextRecognized(screenText, null)
        }
    }

    /** Read screen text with retry — shared by accessibility capture paths. */
    private suspend fun readScreenWithRetry(): String? {
        delay(100)
        var text = ScreenReaderService.readScreenText()
        if (text.isNullOrBlank()) {
            delay(500)
            text = ScreenReaderService.readScreenText()
        }
        return text
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

    /** Dispatch cropped or full bitmap to image collector for multi-image mode（与录制同构）. */
    private fun dispatchCropForImageCollecting(bitmap: Bitmap) {
        when (callbacks.getCropMode()) {
            AppConfig.CROP_MODE_FULL -> callbacks.onImageBitmap(bitmap)
            AppConfig.CROP_MODE_EACH -> {
                callbacks.getSavedCropRectEach()?.let { rect ->
                    val cropped = try { ImageCropUtil.cropBitmap(bitmap, rect) }
                    catch (e: Exception) { bitmap.recycle(); throw e }
                    bitmap.recycle()
                    callbacks.onImageBitmap(cropped)
                } ?: launchCropActivity(bitmap, null)
            }
            AppConfig.CROP_MODE_ONCE -> {
                callbacks.getSavedCropRect()?.let { rect ->
                    val cropped = try { ImageCropUtil.cropBitmap(bitmap, rect) }
                    catch (e: Exception) { bitmap.recycle(); throw e }
                    bitmap.recycle()
                    callbacks.onImageBitmap(cropped)
                } ?: launchCropActivity(bitmap, null)
            }
            else -> callbacks.onImageBitmap(bitmap)
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
