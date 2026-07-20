package com.hwb.aianswerer

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.models.formatAnswerWithConfig
import com.hwb.aianswerer.ui.components.FloatingWindowContent
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ClipboardUtil
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider

/**
 * Floating window service — the runtime core of answer mode.
 *
 * Lifecycle:
 *   1. MainActivity requests permissions then starts via startForegroundService,
 *      passing MediaProjection intent data and answer settings in onStartCommand.
 *   2. onCreate creates the floating window and registers a BroadcastReceiver.
 *   3. onDestroy releases MediaProjection, cancels coroutines, removes the window.
 *
 * Heavy lifting is delegated to:
 *   - [SettingsService]     — settings reads / refresh
 *   - [CaptureHandler]      — capture → crop → recognition
 *   - [AnswerFetcher]       — search + LLM calls + pagination
 *   - [RecordingCoordinator] — recording session orchestration
 */
class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val ACTION_CROP_RESULT = "com.hwb.aianswerer.ACTION_CROP_RESULT"
        const val ACTION_STOP = "com.hwb.aianswerer.ACTION_STOP"
        const val EXTRA_IMAGE_PATH = "image_path"
    }

    // ── Infrastructure ──────────────────────────────────────────────────

    private var floatingView: ComposeView? = null
    private var touchLayout: InteractiveTouchLayout? = null
    @Volatile private var destroyed = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val textRecognitionManager = TextRecognitionManager.getInstance()
    private val pipeline = CapturePipeline(textRecognitionManager)
    private lateinit var windowMgr: FloatingWindowManager
    private lateinit var recorder: RecordingCoordinator
    private lateinit var imageCollector: ImageCollector

    // ── Extracted helpers ───────────────────────────────────────────────

    private lateinit var settings: SettingsService
    private lateinit var captureHandler: CaptureHandler
    private lateinit var answerFetcher: AnswerFetcher
    private lateinit var viewModel: FloatingWindowViewModel

    // ── LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner ──

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ── BroadcastReceiver ───────────────────────────────────────────────

    private val answerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_SHOW_ANSWER -> {
                    val answer = intent.getStringExtra(Constants.EXTRA_ANSWER_TEXT)
                    if (!answer.isNullOrBlank()) {
                        viewModel.answerText.value = answer
                        viewModel.showAnswer.value = true
                    }
                }
                Constants.ACTION_REQUEST_ANSWER -> {
                    val questionText = intent.getStringExtra(Constants.EXTRA_QUESTION_TEXT)
                    if (!questionText.isNullOrBlank()) {
                        viewModel.onTextRecognized(questionText, null, answerFetcher)
                    }
                }
                ACTION_CROP_RESULT -> {
                    val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
                    val tlX = intent.getFloatExtra(ImageCropActivity.EXTRA_TOP_LEFT_X, 0f)
                    val tlY = intent.getFloatExtra(ImageCropActivity.EXTRA_TOP_LEFT_Y, 0f)
                    val brX = intent.getFloatExtra(ImageCropActivity.EXTRA_BOTTOM_RIGHT_X, 0f)
                    val brY = intent.getFloatExtra(ImageCropActivity.EXTRA_BOTTOM_RIGHT_Y, 0f)

                    if (imagePath != null) {
                        val cropRect = CropRect(
                            topLeft = android.graphics.PointF(tlX, tlY),
                            bottomRight = android.graphics.PointF(brX, brY)
                        )
                        when (viewModel.cropMode) {
                            AppConfig.CROP_MODE_ONCE -> { viewModel.savedCropRect = cropRect }
                            AppConfig.CROP_MODE_EACH -> { viewModel.savedCropRectEach = cropRect }
                        }
                        if (viewModel.isRecording.value) {
                            recorder.handleCroppedImage(imagePath, cropRect)
                        } else {
                            captureHandler.handleCroppedImage(imagePath, cropRect)
                        }
                    }
                }
                Constants.ACTION_REFRESH_SETTINGS -> viewModel.refreshSettingsFromApp()
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(
            if (newBase != null) com.hwb.aianswerer.utils.LanguageUtil.attachBaseContext(newBase)
            else newBase
        )
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        screenCaptureManager = ScreenCaptureManager(this)
        windowMgr = FloatingWindowManager(this)
        settings = SettingsService()

        viewModel = ViewModelProvider(this).get(FloatingWindowViewModel::class.java)
        viewModel.initialize(object : FloatingWindowViewModel.ServiceContext {
            override fun showToast(msg: String) { Toast.makeText(this@FloatingWindowService, msg, Toast.LENGTH_SHORT).show() }
            override fun getString(id: Int) = this@FloatingWindowService.getString(id)
            override fun getString(id: Int, vararg args: Any) = this@FloatingWindowService.getString(id, *args)
            override fun showErrorToUser(msg: String) {
                viewModel.floatingStatus.value = FloatingStatus.Error
                serviceScope.launch {
                    viewModel.statusMessage.value = msg
                    delay(5000)
                    if (viewModel.statusMessage.value == msg) { viewModel.statusMessage.value = null }
                }
            }
            override fun copyToClipboard(text: String) { ClipboardUtil.copyToClipboard(this@FloatingWindowService, text) }
            override fun isLeftSide(): Boolean { val w = resources.displayMetrics.widthPixels.toFloat(); return viewModel.floatOffsetX.value < w / 2f }
            override fun getDensity() = resources.displayMetrics.density
            override fun setFlagSecure(enabled: Boolean) { windowMgr.setFlagSecure(touchLayout, enabled) }
            override fun setWindowAlpha(alpha: Float) { windowMgr.setAlpha(touchLayout, alpha) }
            override fun updateWindowPosition() { this@FloatingWindowService.updateWindowPosition() }
            override fun updateWindowHeight() { updateFloatingWindowHeight() }
            override fun animateWindowX(targetX: Float, animated: Boolean) { this@FloatingWindowService.animateWindowX(targetX, animated) }
            override fun getCurrentWindowHeightPx() = viewModel.currentWindowHeightPx
            override fun setCurrentWindowHeightPx(h: Float) { viewModel.currentWindowHeightPx = h }
            override fun setHasContent(has: Boolean) { viewModel.hasContent = has }
            override fun onRecordingBitmap(bitmap: Bitmap) { recorder.processBitmap(bitmap) }
            override fun onImageText(text: String) { imageCollector.addText(text) }
            override fun updateFloatingWindowHeight() { this@FloatingWindowService.updateFloatingWindowHeight() }
        })

        recorder = RecordingCoordinator(pipeline, serviceScope, viewModel.recordingCallbacks)
        imageCollector = ImageCollector(pipeline, serviceScope, viewModel.imageCallbacks)

        answerFetcher = AnswerFetcher(pipeline, serviceScope, viewModel.answerCallbacks)
        viewModel.answerFetcher = answerFetcher

        captureHandler = CaptureHandler(
            screenCaptureManager, pipeline, recorder, serviceScope,
            captureCallbacks, this
        )

        registerReceiver()

        // Foreground notification
        NotificationHelper.createChannel(this)
        NotificationHelper.ensurePermission(this)
        val notification = NotificationHelper.buildNotification(this)
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(
                Constants.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        showFloatingWindow()

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun registerReceiver() {
        val filter = IntentFilter(Constants.ACTION_SHOW_ANSWER)
        filter.addAction(Constants.ACTION_REQUEST_ANSWER)
        filter.addAction(ACTION_CROP_RESULT)
        filter.addAction(Constants.ACTION_REFRESH_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(answerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(answerReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.let {
            if (it.hasExtra("resultCode") && it.hasExtra("data")) {
                val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = it.getParcelableExtra<Intent>("data")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    screenCaptureManager?.initMediaProjection(resultCode, data)
                }
            }
            if (it.hasExtra("viewModel.cropMode")) {
                viewModel.cropMode = it.getStringExtra("viewModel.cropMode") ?: AppConfig.CROP_MODE_FULL
            }
            viewModel.savedCropRect = null
            viewModel.savedCropRectEach = null
        }
        return START_NOT_STICKY
    }

    // ── CaptureHandlerCallbacks implementation ──────────────────────────

    private val captureCallbacks get() = viewModel.captureCallbacks


    // ── Floating window UI ──────────────────────────────────────────────

    private fun showFloatingWindow() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val density = metrics.density
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val buttonHalf = buttonSizePx / 2f

        viewModel.currentWindowHeightPx = 200 * density
        viewModel.floatOffsetX.value = screenW - buttonHalf
        viewModel.floatOffsetY.value = screenH * 0.30f

        fun isLeftSide() = viewModel.floatOffsetX.value < screenW / 2f

        // 动态宽度：空闲时窄（仅包住按钮），展开时自动扩宽
        val narrowW = buttonSizePx + 16 * density
        viewModel.currentWindowWidthPx = narrowW

        fun windowX(): Int {
            val w = viewModel.currentWindowWidthPx
            return if (isLeftSide()) 0
            else (screenW - w).toInt().coerceAtLeast(0)
        }

        val params = windowMgr.createLayoutParams(
            windowWidthPx = viewModel.currentWindowWidthPx.toInt(),
            windowHeightPx = viewModel.currentWindowHeightPx.toInt(),
            isLeftSide = isLeftSide(),
            offsetY = viewModel.floatOffsetY.value,
            screenW = screenW,
            screenH = screenH,
            isStealth = settings.stealthMode.value
        )

        touchLayout = InteractiveTouchLayout(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
        }

        floatingView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            if (settings.stealthMode.value) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            setContent {
                AIAnswererTheme {
                    FloatingWindowContent(
                        answerText = viewModel.answerText.value,
                        showAnswer = viewModel.showAnswer.value,
                        statusMessage = viewModel.statusMessage.value,
                        buttonSize = settings.floatButtonSizeDp.value,
                        buttonAlpha = settings.floatButtonAlpha.value,
                        cardAlpha = settings.floatCardAlpha.value,
                        isLeftSide = isLeftSide(),
                        floatingStatus = viewModel.floatingStatus.value,
                        onCaptureClick = { captureHandler.handleCapture() },
                        onCloseAnswer = {
                            viewModel.currentFetchJob?.cancel()
                            viewModel.currentFetchJob = null
                            viewModel.showAnswer.value = false
                            viewModel.answerText.value = null
                            viewModel.recordingAnswers.value = emptyList()
                            viewModel.paginatedAnswers.value = emptyList()
                            viewModel.paginatedCopyTexts.value = emptyList()
                            viewModel.floatingStatus.value = FloatingStatus.Idle
                            viewModel.statusMessage.value = null
                            updateFloatingWindowWidth()
                        },
                        onCloseStatus = {
                            viewModel.currentFetchJob?.cancel()
                            viewModel.currentFetchJob = null
                            recorder.cancel()
                            imageCollector.cancel()
                            viewModel.isImageCollecting.value = false
                            viewModel.isProcessingImages.value = false
                            viewModel.isProcessingRecording.value = false
                            viewModel.showAnswer.value = false
                            viewModel.answerText.value = null
                            viewModel.recordingAnswers.value = emptyList()
                            viewModel.paginatedAnswers.value = emptyList()
                            viewModel.paginatedCopyTexts.value = emptyList()
                            viewModel.floatingStatus.value = FloatingStatus.Idle
                            viewModel.statusMessage.value = null
                            updateFloatingWindowWidth()
                        },
                        onCopyAnswer = {
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, viewModel.answerText.value ?: "")
                        },
                        onMove = { deltaX, deltaY ->
                            val prevX = viewModel.floatOffsetX.value
                            val prevY = viewModel.floatOffsetY.value
                            viewModel.floatOffsetX.value = (viewModel.floatOffsetX.value + deltaX)
                                .coerceIn(buttonHalf, screenW - buttonHalf)
                            viewModel.floatOffsetY.value = (viewModel.floatOffsetY.value + deltaY)
                                .coerceIn(0f, screenH - viewModel.currentWindowHeightPx)
                            animateWindowX(windowX().toFloat(), false)
                            updateWindowPosition()
                        },
                        onDragEnd = { leftSide ->
                            viewModel.floatOffsetX.value = if (leftSide) buttonHalf else screenW - buttonHalf
                            animateWindowX(windowX().toFloat(), true)
                            updateFloatingWindowWidth()
                        },
                        visionEnabled = settings.visionEnabled.value,
                        searchEnabled = settings.searchEnabled.value,
                        reasoningEnabled = settings.reasoningEnabled.value,
                        imageEnabled = settings.imageEnabled.value,
                        onVisionToggle = {
                            settings.visionEnabled.value = !settings.visionEnabled.value
                            AppConfig.saveVisionEnabled(settings.visionEnabled.value)
                            Toast.makeText(this@FloatingWindowService,
                                if (settings.visionEnabled.value) getString(R.string.float_toggle_vlm_on) else getString(R.string.float_toggle_vlm_off),
                                Toast.LENGTH_SHORT).show()
                        },
                        onSearchToggle = {
                            val hasProviders = com.hwb.aianswerer.providers.WebSearchStorage.getEnabledProviders().isNotEmpty()
                            if (!hasProviders && !settings.searchEnabled.value) {
                                Toast.makeText(this@FloatingWindowService,
                                    getString(R.string.float_toggle_search_no_provider),
                                    Toast.LENGTH_SHORT).show()
                                return@FloatingWindowContent
                            }
                            settings.searchEnabled.value = !settings.searchEnabled.value
                            com.hwb.aianswerer.providers.WebSearchStorage.saveSearchEnabled(settings.searchEnabled.value)
                            Toast.makeText(this@FloatingWindowService,
                                if (settings.searchEnabled.value) getString(R.string.float_toggle_search_on) else getString(R.string.float_toggle_search_off),
                                Toast.LENGTH_SHORT).show()
                        },
                        onReasoningToggle = {
                            settings.reasoningEnabled.value = !settings.reasoningEnabled.value
                            AppConfig.saveReasoningEffort(settings.reasoningEnabled.value)
                            Toast.makeText(this@FloatingWindowService,
                                if (settings.reasoningEnabled.value) getString(R.string.float_toggle_reasoning_on) else getString(R.string.float_toggle_reasoning_off),
                                Toast.LENGTH_SHORT).show()
                        },
                        onImageToggle = {
                            if (viewModel.isImageCollecting.value) {
                                // 停止图片采集，开始分析
                                imageCollector.stop()
                                viewModel.isImageCollecting.value = false
                                viewModel.isProcessingImages.value = imageCollector.isProcessing
                                settings.imageEnabled.value = false
                                Toast.makeText(this@FloatingWindowService,
                                    getString(R.string.image_analyzing),
                                    Toast.LENGTH_SHORT).show()
                            } else {
                                // 开始图片采集：如果录制模式活跃，自动终止
                                if (recorder.isActive) {
                                    viewModel.stopRecording(recorder)
                                }
                                imageCollector.start()
                                viewModel.isImageCollecting.value = true
                                viewModel.imageCollectCount.value = 0
                                viewModel.showAnswer.value = false
                                viewModel.paginatedAnswers.value = emptyList()
                                settings.imageEnabled.value = true
                                Toast.makeText(this@FloatingWindowService,
                                    getString(R.string.image_collection_start),
                                    Toast.LENGTH_SHORT).show()
                            }
                        },
                        isRecording = viewModel.isRecording.value,
                        isProcessingRecording = viewModel.isProcessingRecording.value,
                        isImageCollecting = viewModel.isImageCollecting.value,
                        imageCollectCount = viewModel.imageCollectCount.value,
                        isProcessingImages = viewModel.isProcessingImages.value,
                        recordingCaptureCount = viewModel.recordingCaptureCount.value,
                        recordingProcessedCount = viewModel.recordingProcessedCount.value,
                        recordingAnswers = viewModel.recordingAnswers.value,
                        paginatedAnswers = viewModel.paginatedAnswers.value,
                        paginatedCopyTexts = viewModel.paginatedCopyTexts.value,
                        onCopyRecordingAnswer = { text ->
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, text)
                        },
                        onRecordingToggle = {
                            if (viewModel.isRecording.value) {
                                viewModel.stopRecording(recorder)
                            } else {
                                if (viewModel.isImageCollecting.value) {
                                    imageCollector.cancel()
                                    viewModel.isImageCollecting.value = false
                                    viewModel.isProcessingImages.value = false
                                    settings.imageEnabled.value = false
                                }
                                viewModel.startRecording(recorder)
                            }
                            updateFloatingWindowWidth()
                        },
                        onArcExpandChanged = { expanded ->
                            viewModel.isArcExpanded = expanded
                            updateFloatingWindowHeight()
                            updateFloatingWindowWidth()
                        },
                        onContentVisibilityChanged = { visible ->
                            viewModel.hasContent = visible
                            updateFloatingWindowHeight()
                            updateFloatingWindowWidth()
                        },
                        onInteractiveAreaChanged = { left, top, right, bottom ->
                            val contentH = (bottom - top).toInt()
                            if (contentH > 0 && viewModel.showAnswer.value) {
                                viewModel.measuredContentHeightPx = contentH.toFloat()
                                updateFloatingWindowHeight()
                            }
                        }
                    )
                }
            }
        }

        viewModel.displayWindowX.floatValue = windowX().toFloat()
        windowMgr.attach(touchLayout!!, params)
        touchLayout!!.addView(floatingView)
    }

    // ── Window animation & position ─────────────────────────────────────

    private fun animateWindowX(targetX: Float, animated: Boolean) {
        viewModel.windowXAnimJob?.cancel()
        if (!animated) {
            viewModel.displayWindowX.floatValue = targetX
            return
        }
        viewModel.windowXAnimJob = windowMgr.animateWindowX(
            scope = serviceScope, from = viewModel.displayWindowX.floatValue, to = targetX
        ) { currentX ->
            viewModel.displayWindowX.floatValue = currentX
            updateWindowPosition()
        }
    }

    private fun updateWindowPosition() {
        if (destroyed) return
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        windowMgr.updateLayout(
            view = touchLayout,
            windowX = viewModel.displayWindowX.floatValue.toInt(),
            windowY = viewModel.floatOffsetY.value.toInt(),
            windowWidth = viewModel.currentWindowWidthPx.toInt(),
            windowHeight = viewModel.currentWindowHeightPx.toInt(),
            screenW = screenW,
            screenH = screenH
        )
    }

    private fun updateFloatingWindowHeight() {
        if (viewModel.captureInProgress || destroyed) return
        val density = resources.displayMetrics.density
        val newHeight = windowMgr.calculateHeight(
            density = density,
            screenHeightPx = resources.displayMetrics.heightPixels,
            buttonSizeDp = settings.floatButtonSizeDp.value,
            isRecording = viewModel.isRecording.value,
            isProcessingRecording = viewModel.isProcessingRecording.value,
            hasContent = viewModel.hasContent,
            showAnswer = viewModel.showAnswer.value,
            hasAnswers = viewModel.recordingAnswers.value.isNotEmpty() || viewModel.paginatedAnswers.value.isNotEmpty(),
            measuredCardHeightPx = viewModel.measuredContentHeightPx
        )
        if (newHeight.toFloat() != viewModel.currentWindowHeightPx) {
            viewModel.currentWindowHeightPx = newHeight.toFloat()
            updateWindowPosition()
        }
    }

    /** 动态调整窗口宽度——空闲时窄，有内容时宽，配合 FLAG_NOT_TOUCH_MODAL 实现触摸穿透 */
    private fun updateFloatingWindowWidth() {
        if (viewModel.captureInProgress || destroyed) return
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val marginPx = 8 * density
        val isLeft = viewModel.floatOffsetX.value < screenW / 2f

        val hasCardContent = viewModel.showAnswer.value ||
            viewModel.recordingAnswers.value.isNotEmpty() || viewModel.paginatedAnswers.value.isNotEmpty()

        val narrowW = buttonSizePx + 2 * marginPx

        val newWidth = if (hasCardContent || viewModel.hasContent || viewModel.isRecording.value) {
            360 * density
        } else if (viewModel.isArcExpanded) {
            val gapPx = 8 * density
            val quickRowW = (4 * 40 + 3 * 6 + 8) * density
            buttonSizePx + 2 * marginPx + gapPx + quickRowW
        } else {
            narrowW
        }

        val prevWidth = viewModel.currentWindowWidthPx
        if (kotlin.math.abs(newWidth - prevWidth) < 4) return

        val delta = newWidth - prevWidth
        viewModel.currentWindowWidthPx = newWidth

        if (!isLeft && delta != 0f) {
            viewModel.displayWindowX.floatValue =
                (viewModel.displayWindowX.floatValue - delta).coerceAtLeast(0f)
        }
        updateWindowPosition()
    }
    private fun setWindowVisible(visible: Boolean) {
        windowMgr.setVisible(touchLayout, visible, settings.stealthMode.value)
    }

    override fun onDestroy() {
        destroyed = true
        isRunning = false
        recorder.cancel()
        imageCollector.cancel()
        viewModel.isRecording.value = false
        viewModel.isProcessingRecording.value = false
        viewModel.currentFetchJob?.cancel()
        viewModel.currentFetchJob = null
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        try { unregisterReceiver(answerReceiver) } catch (_: IllegalArgumentException) {}
        floatingView?.disposeComposition()
        touchLayout?.let { windowMgr.detach(it) }
        touchLayout = null
        floatingView = null
        screenCaptureManager?.releaseAll()
        _viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
