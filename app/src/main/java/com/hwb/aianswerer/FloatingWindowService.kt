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

    // ── Extracted helpers ───────────────────────────────────────────────

    private lateinit var settings: SettingsService
    private lateinit var captureHandler: CaptureHandler
    private lateinit var answerFetcher: AnswerFetcher

    // ── Compose state ───────────────────────────────────────────────────

    private var answerText = mutableStateOf<String?>(null)
    private var showAnswer = mutableStateOf(false)
    private var statusMessage = mutableStateOf<String?>(null)
    private var floatingStatus = mutableStateOf<FloatingStatus>(FloatingStatus.Idle)

    // ── Window geometry ─────────────────────────────────────────────────

    private var isArcExpanded = false
    private var hasContent = false
    private var currentWindowHeightPx = 0f
    private var measuredContentHeightPx = 0f
    private var captureInProgress = false

    private var displayWindowX = mutableFloatStateOf(0f)
    private var windowXAnimJob: Job? = null

    private var floatOffsetX = mutableFloatStateOf(0f)
    private var floatOffsetY = mutableFloatStateOf(200f)

    // ── Crop state ──────────────────────────────────────────────────────

    private var cropMode = AppConfig.CROP_MODE_FULL
    @Volatile private var savedCropRect: CropRect? = null
    @Volatile private var savedCropRectEach: CropRect? = null

    // ── Network ─────────────────────────────────────────────────────────

    private var currentFetchJob: Job? = null

    // ── Recording mode state ────────────────────────────────────────────

    private var isRecording = mutableStateOf(false)
    private var recordingCaptureCount = mutableStateOf(0)
    private var isProcessingRecording = mutableStateOf(false)
    private var recordingProcessedCount = mutableStateOf(0)
    private var recordingAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private var recordingCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private var recordingSkippedCount = mutableStateOf(0)
    private var recordingFailedCount = mutableStateOf(0)

    // ── Paginated answers (normal mode) ─────────────────────────────────

    private var paginatedAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private var paginatedCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())

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
                        answerText.value = answer
                        showAnswer.value = true
                    }
                }
                Constants.ACTION_REQUEST_ANSWER -> {
                    val questionText = intent.getStringExtra(Constants.EXTRA_QUESTION_TEXT)
                    if (!questionText.isNullOrBlank()) {
                        onTextRecognized(questionText, null)
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
                        when (cropMode) {
                            AppConfig.CROP_MODE_ONCE -> { savedCropRect = cropRect }
                            AppConfig.CROP_MODE_EACH -> { savedCropRectEach = cropRect }
                        }
                        if (isRecording.value) {
                            recorder.handleCroppedImage(imagePath, cropRect)
                        } else {
                            captureHandler.handleCroppedImage(imagePath, cropRect)
                        }
                    }
                }
                Constants.ACTION_REFRESH_SETTINGS -> refreshSettingsFromApp()
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

        recorder = RecordingCoordinator(pipeline, serviceScope, object : RecordingCoordinator.Callbacks {
            override fun onError(message: String) { showErrorMessage(message) }
            override fun onToast(message: String) { Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show() }
            override fun onResultsAvailable(answers: List<Pair<Int, String>>, copyTexts: List<Pair<Int, String>>, total: Int, skipped: Int, failed: Int) {
                showRecordingResultsFromCoordinator(answers, copyTexts, total, skipped, failed)
            }
            override fun onProgressUpdate(processed: Int, total: Int) {
                statusMessage.value = getString(R.string.recording_processing, processed, total)
                recordingProcessedCount.value = processed
            }
            override fun getString(resId: Int, vararg args: Any?): String =
                this@FloatingWindowService.getString(resId, *args)
            override fun isSearchEnabled(): Boolean = settings.searchEnabled.value
        })

        answerFetcher = AnswerFetcher(pipeline, serviceScope, object : AnswerFetcherCallbacks {
            override fun onStatus(status: FloatingStatus, message: String?) {
                floatingStatus.value = status
                statusMessage.value = message
            }
            override fun onToast(message: String) {
                Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show()
            }
            override fun onError(message: String) { showErrorMessage(message) }
            override fun isSearchEnabled(): Boolean = settings.searchEnabled.value
        })

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
            if (it.hasExtra("cropMode")) {
                cropMode = it.getStringExtra("cropMode") ?: AppConfig.CROP_MODE_FULL
            }
            savedCropRect = null
            savedCropRectEach = null
        }
        return START_NOT_STICKY
    }

    // ── CaptureHandlerCallbacks implementation ──────────────────────────

    private val captureCallbacks = object : CaptureHandlerCallbacks {
        override fun isRecording() = isRecording.value
        override fun getCropMode() = cropMode
        override fun getSavedCropRect() = savedCropRect
        override fun getSavedCropRectEach() = savedCropRectEach
        override fun isVisionEnabled() = settings.visionEnabled.value
        override fun isSearchEnabled() = settings.searchEnabled.value
        override fun isStealthModeEnabled() = settings.stealthMode.value
        override fun getFloatButtonSizeDp() = settings.floatButtonSizeDp.value
        override fun getDensity() = resources.displayMetrics.density

        override fun setSavedCropRect(rect: CropRect?) { savedCropRect = rect }
        override fun setSavedCropRectEach(rect: CropRect?) { savedCropRectEach = rect }
        override fun setHasContent(has: Boolean) { hasContent = has }
        override fun setCaptureInProgress(enabled: Boolean) { captureInProgress = enabled }
        override fun setShowAnswer(show: Boolean) { showAnswer.value = show }
        override fun getCurrentWindowHeightPx() = currentWindowHeightPx
        override fun setCurrentWindowHeightPx(h: Float) { currentWindowHeightPx = h }

        override fun setFlagSecure(enabled: Boolean) {
            windowMgr.setFlagSecure(touchLayout, enabled)
        }
        override fun updateWindowPosition() { this@FloatingWindowService.updateWindowPosition() }
        override fun updateWindowHeight() { updateFloatingWindowHeight() }

        override fun showError(message: String) { showErrorMessage(message) }
        override fun showToast(message: String) {
            Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show()
        }
        override fun setStatus(status: FloatingStatus) { floatingStatus.value = status }
        override fun setStatusMessage(msg: String?) { statusMessage.value = msg }
        override fun getString(resId: Int, vararg args: Any?) =
            this@FloatingWindowService.getString(resId, *args)

        override fun onTextRecognized(text: String, visionResult: com.hwb.aianswerer.api.vision.VisionFilterResult?) {
            this@FloatingWindowService.onTextRecognized(text, visionResult)
        }
        override fun onRecordingBitmap(bitmap: Bitmap) {
            recorder.processBitmap(bitmap)
        }
        override fun incRecordingCaptureCount(): Int {
            recordingCaptureCount.value++
            return recordingCaptureCount.value
        }
        override fun getCurrentFetchJob() = currentFetchJob
        override fun setCurrentFetchJob(job: Job?) { currentFetchJob = job }
    }

    // ── Recognized text → answer dispatch ──────────────────────────────

    /**
     * Central handler called when [CaptureHandler] finishes recognition
     * (or when a question-text broadcast arrives).
     */
    private fun onTextRecognized(text: String, visionResult: com.hwb.aianswerer.api.vision.VisionFilterResult?) {
        val autoCopy = AppConfig.getAutoCopy()
        answerFetcher.fetchAnswer(text, visionResult) { result ->
            when (result) {
                is AnswerResult.Success -> serviceScope.launch { handleAnswerSuccess(result.answers, autoCopy) }
                is AnswerResult.Error -> showErrorMessage(result.message)
            }
        }
    }

    // ── Floating window UI ──────────────────────────────────────────────

    private fun showFloatingWindow() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val density = metrics.density
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val buttonHalf = buttonSizePx / 2f

        currentWindowHeightPx = 200 * density
        floatOffsetX.value = screenW - buttonHalf
        floatOffsetY.value = screenH * 0.30f

        fun isLeftSide() = floatOffsetX.value < screenW / 2f

        val windowWidthPx = 360 * density

        fun windowX(): Int {
            return if (isLeftSide()) 0
            else (screenW - windowWidthPx).toInt().coerceAtLeast(0)
        }

        val params = windowMgr.createLayoutParams(
            windowWidthPx = windowWidthPx.toInt(),
            windowHeightPx = currentWindowHeightPx.toInt(),
            isLeftSide = isLeftSide(),
            offsetY = floatOffsetY.value,
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
                        answerText = answerText.value,
                        showAnswer = showAnswer.value,
                        statusMessage = statusMessage.value,
                        buttonSize = settings.floatButtonSizeDp.value,
                        buttonAlpha = settings.floatButtonAlpha.value,
                        cardAlpha = settings.floatCardAlpha.value,
                        isLeftSide = isLeftSide(),
                        floatingStatus = floatingStatus.value,
                        onCaptureClick = { captureHandler.handleCapture() },
                        onCloseAnswer = {
                            currentFetchJob?.cancel()
                            currentFetchJob = null
                            showAnswer.value = false
                            answerText.value = null
                            recordingAnswers.value = emptyList()
                            paginatedAnswers.value = emptyList()
                            paginatedCopyTexts.value = emptyList()
                            floatingStatus.value = FloatingStatus.Idle
                            statusMessage.value = null
                        },
                        onCloseStatus = {
                            currentFetchJob?.cancel()
                            currentFetchJob = null
                            recorder.cancel()
                            isProcessingRecording.value = false
                            showAnswer.value = false
                            answerText.value = null
                            recordingAnswers.value = emptyList()
                            paginatedAnswers.value = emptyList()
                            paginatedCopyTexts.value = emptyList()
                            floatingStatus.value = FloatingStatus.Idle
                            statusMessage.value = null
                        },
                        onCopyAnswer = {
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, answerText.value ?: "")
                        },
                        onMove = { deltaX, deltaY ->
                            val prevX = floatOffsetX.value
                            val prevY = floatOffsetY.value
                            floatOffsetX.value = (floatOffsetX.value + deltaX)
                                .coerceIn(buttonHalf, screenW - buttonHalf)
                            floatOffsetY.value = (floatOffsetY.value + deltaY)
                                .coerceIn(0f, screenH - currentWindowHeightPx)
                            animateWindowX(windowX().toFloat(), false)
                            updateWindowPosition()
                        },
                        onDragEnd = { leftSide ->
                            floatOffsetX.value = if (leftSide) buttonHalf else screenW - buttonHalf
                            animateWindowX(windowX().toFloat(), true)
                        },
                        visionEnabled = settings.visionEnabled.value,
                        searchEnabled = settings.searchEnabled.value,
                        reasoningEnabled = settings.reasoningEnabled.value,
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
                        isRecording = isRecording.value,
                        isProcessingRecording = isProcessingRecording.value,
                        recordingCaptureCount = recordingCaptureCount.value,
                        recordingProcessedCount = recordingProcessedCount.value,
                        recordingAnswers = recordingAnswers.value,
                        paginatedAnswers = paginatedAnswers.value,
                        paginatedCopyTexts = paginatedCopyTexts.value,
                        onCopyRecordingAnswer = { text ->
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, text)
                        },
                        onRecordingToggle = {
                            if (isRecording.value) stopRecording() else startRecording()
                        },
                        onArcExpandChanged = { expanded ->
                            isArcExpanded = expanded
                            updateFloatingWindowHeight()
                        },
                        onContentVisibilityChanged = { visible ->
                            hasContent = visible
                            updateFloatingWindowHeight()
                        },
                        onInteractiveAreaChanged = { left, top, right, bottom ->
                            val contentH = (bottom - top).toInt()
                            if (contentH > 0 && showAnswer.value) {
                                measuredContentHeightPx = contentH.toFloat()
                                updateFloatingWindowHeight()
                            }
                        }
                    )
                }
            }
        }

        displayWindowX.floatValue = windowX().toFloat()
        windowMgr.attach(touchLayout!!, params)
        touchLayout!!.addView(floatingView)
    }

    // ── Window animation & position ─────────────────────────────────────

    private fun animateWindowX(targetX: Float, animated: Boolean) {
        windowXAnimJob?.cancel()
        if (!animated) {
            displayWindowX.floatValue = targetX
            return
        }
        windowXAnimJob = windowMgr.animateWindowX(
            scope = serviceScope, from = displayWindowX.floatValue, to = targetX
        ) { currentX ->
            displayWindowX.floatValue = currentX
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
            windowX = displayWindowX.floatValue.toInt(),
            windowY = floatOffsetY.value.toInt(),
            windowHeight = currentWindowHeightPx.toInt(),
            screenW = screenW,
            screenH = screenH
        )
    }

    private fun updateFloatingWindowHeight() {
        if (captureInProgress || destroyed) return
        val density = resources.displayMetrics.density
        val newHeight = windowMgr.calculateHeight(
            density = density,
            screenHeightPx = resources.displayMetrics.heightPixels,
            buttonSizeDp = settings.floatButtonSizeDp.value,
            isRecording = isRecording.value,
            isProcessingRecording = isProcessingRecording.value,
            hasContent = hasContent,
            showAnswer = showAnswer.value,
            hasAnswers = recordingAnswers.value.isNotEmpty() || paginatedAnswers.value.isNotEmpty(),
            measuredCardHeightPx = measuredContentHeightPx
        )
        if (newHeight.toFloat() != currentWindowHeightPx) {
            currentWindowHeightPx = newHeight.toFloat()
            updateWindowPosition()
        }
    }

    private fun setWindowVisible(visible: Boolean) {
        windowMgr.setVisible(touchLayout, visible, settings.stealthMode.value)
    }

    // ── Settings refresh ────────────────────────────────────────────────

    private fun refreshSettingsFromApp() {
        settings.refresh()
        AppLog.d("FWS", "settings refreshed")
    }

    // ── Recording mode orchestration ────────────────────────────────────

    private fun startRecording() {
        currentFetchJob?.cancel()
        currentFetchJob = null
        recorder.start()
        isRecording.value = true
        recordingCaptureCount.value = 0
        recordingSkippedCount.value = 0
        recordingFailedCount.value = 0
        recordingAnswers.value = emptyList()
        recordingCopyTexts.value = emptyList()
        paginatedAnswers.value = emptyList()
        paginatedCopyTexts.value = emptyList()
        showAnswer.value = false
        answerText.value = null
        floatingStatus.value = FloatingStatus.Idle
        statusMessage.value = getString(R.string.recording_indicator, 0)
        Toast.makeText(this, getString(R.string.recording_start), Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording.value = false
        when (val result = recorder.stop()) {
            is RecordingCoordinator.StopResult.NothingToShow -> {
                Toast.makeText(this, getString(R.string.recording_no_captures), Toast.LENGTH_SHORT).show()
            }
            is RecordingCoordinator.StopResult.Completed -> {
                showRecordingResults()
            }
            is RecordingCoordinator.StopResult.Processing -> {
                isProcessingRecording.value = true
                floatingStatus.value = FloatingStatus.GettingAnswer
                statusMessage.value = getString(R.string.recording_processing, recordingProcessedCount.value, result.captureCount)
                Toast.makeText(this, getString(R.string.recording_stop, result.captureCount), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRecordingResults() {
        val autoCopy = AppConfig.getAutoCopy()
        val allEntries = recordingAnswers.value.sortedBy { it.first }
        if (allEntries.isEmpty()) {
            showErrorMessage(getString(R.string.recording_no_valid_answers))
            isProcessingRecording.value = false
            return
        }
        showAnswer.value = true
        floatingStatus.value = FloatingStatus.Success
        val total = recordingCaptureCount.value
        val skipped = recordingSkippedCount.value
        val failed = recordingFailedCount.value
        val resultSummary = buildString {
            append(getString(R.string.recording_all_done, total))
            if (skipped > 0) append("，去除重复 ${skipped} 题")
            if (failed > 0) append("，${failed} 题获取失败")
        }
        statusMessage.value = resultSummary
        if (autoCopy) {
            val copyText = recordingCopyTexts.value.sortedBy { it.first }
                .joinToString("\n") { it.second }
            ClipboardUtil.copyToClipboard(this@FloatingWindowService, copyText)
        }
        isProcessingRecording.value = false
    }

    private fun showRecordingResultsFromCoordinator(
        answers: List<Pair<Int, String>>,
        copyTexts: List<Pair<Int, String>>,
        total: Int, skipped: Int, failed: Int
    ) {
        if (answers.isEmpty()) {
            showErrorMessage(getString(R.string.recording_no_valid_answers))
            isProcessingRecording.value = false
            return
        }
        recordingAnswers.value = answers
        recordingCopyTexts.value = copyTexts
        showAnswer.value = true
        floatingStatus.value = FloatingStatus.Success
        val resultSummary = buildString {
            append(getString(R.string.recording_all_done, total))
            if (skipped > 0) append("，去除重复 ${skipped} 题")
            if (failed > 0) append("，${failed} 题获取失败")
        }
        statusMessage.value = resultSummary
        if (AppConfig.getAutoCopy()) {
            ClipboardUtil.copyToClipboard(this, copyTexts.sortedBy { it.first }.joinToString("\n") { it.second })
        }
        isProcessingRecording.value = false
    }

    // ── Answer display ──────────────────────────────────────────────────

    private suspend fun handleAnswerSuccess(
        aiAnswers: List<com.hwb.aianswerer.models.AIAnswer>,
        autoCopy: Boolean
    ) {
        val showQuestion = AppConfig.getShowAnswerCardQuestion()
        val showOptions = AppConfig.getShowAnswerCardOptions()

        paginatedAnswers.value = aiAnswers.mapIndexed { index, answer ->
            (index + 1) to answer.formatAnswerWithConfig(showQuestion, showOptions)
        }
        paginatedCopyTexts.value = aiAnswers.mapIndexed { index, ans ->
            (index + 1) to "第 ${index + 1} 题：${ans.answer}"
        }

        val formattedAnswer = if (aiAnswers.size == 1) {
            aiAnswers.first().formatAnswerWithConfig(showQuestion, showOptions)
        } else {
            aiAnswers.mapIndexed { index, answer ->
                val header = "━━━ 第 ${index + 1} 题 ━━━\n"
                header + answer.formatAnswerWithConfig(showQuestion, showOptions)
            }.joinToString("\n\n")
        }

        if (autoCopy) {
            val copyText = if (aiAnswers.size == 1) {
                aiAnswers.first().answer
            } else {
                aiAnswers.mapIndexed { index, ans ->
                    "第 ${index + 1} 题：${ans.answer}"
                }.joinToString("\n")
            }
            ClipboardUtil.copyToClipboard(this@FloatingWindowService, copyText)
        }

        answerText.value = formattedAnswer
        showAnswer.value = true
        floatingStatus.value = FloatingStatus.Success
        statusMessage.value = if (autoCopy) "答案已复制" else "答案已生成"
        delay(2000)
        statusMessage.value = null
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        destroyed = true

        recorder.cancel()
        isRecording.value = false
        isProcessingRecording.value = false

        currentFetchJob?.cancel()
        currentFetchJob = null
        serviceScope.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        try {
            unregisterReceiver(answerReceiver)
        } catch (e: IllegalArgumentException) {
            AppLog.w("FWS", "Receiver not registered", e)
        }

        floatingView?.disposeComposition()
        touchLayout?.let { windowMgr.detach(it) }
        touchLayout = null
        floatingView = null

        screenCaptureManager?.releaseAll()
        _viewModelStore.clear()
    }

    // ── Status helpers ──────────────────────────────────────────────────

    private fun showStatusMessage(message: String, durationMs: Long = 2000) {
        serviceScope.launch {
            statusMessage.value = message
            delay(durationMs)
            if (statusMessage.value == message) {
                statusMessage.value = null
                if (floatingStatus.value == FloatingStatus.Error) {
                    floatingStatus.value = FloatingStatus.Idle
                }
            }
        }
    }

    private fun showErrorMessage(message: String) {
        floatingStatus.value = FloatingStatus.Error
        showStatusMessage(message, 5000)
        AppLog.e("FWS", message)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
