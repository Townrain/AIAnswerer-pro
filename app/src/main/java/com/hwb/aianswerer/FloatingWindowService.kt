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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.ui.components.FWDims
import com.hwb.aianswerer.ui.components.QuickAction
import com.hwb.aianswerer.ui.components.WindowAContent
import com.hwb.aianswerer.ui.components.WindowBContent
import com.hwb.aianswerer.ui.components.WindowCContent
import com.hwb.aianswerer.ui.components.WindowDContent
import com.hwb.aianswerer.ui.components.IcGlobe
import com.hwb.aianswerer.ui.components.IcBulb
import com.hwb.aianswerer.ui.components.IcImage
import com.hwb.aianswerer.ui.components.IcRecord
import com.hwb.aianswerer.ui.components.IcVision
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.ui.theme.sandboxTheme
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ClipboardUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

/**
 * Floating window service — the runtime core of answer mode.
 *
 * Manages 3 independent WindowManager windows:
 *   Window A (Pill) — always visible, draggable pill button
 *   Window B (Toggles) — quick-toggle panel, shown/hidden on long-press
 *   Window C (Card) — answer/status card, shown when content is available
 *
 * Lifecycle:
 *   1. MainActivity requests permissions then starts via startForegroundService,
 *      passing MediaProjection intent data and answer settings in onStartCommand.
 *   2. onCreate creates Window A and registers BroadcastReceiver.
 *   3. B and C are created dynamically as needed.
 *   4. onDestroy releases everything.
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

    // Window A, B, C ComposeViews (owned by service, managed through windowMgr)
    private var windowAView: ComposeView? = null
    private var windowBView: ComposeView? = null
    private var windowCView: ComposeView? = null
    private var windowDView: ComposeView? = null

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

    // ── Arc / toggle state ──────────────────────────────────────────────

    /** Whether quick-toggle window (B) is currently shown. */
    private var isArcExpanded = false

    /** Measured size of Window A content (width, height) in px. */
    private var measuredSizeA: Pair<Float, Float>? = null

    /** Measured height of Window C content in px. */
    private var measuredWindowCHeight: Float? = null

    /** Measured height of Window D content in px. */
    private var measuredWindowDHeight: Float? = null

    /** Whether Window D (detail content) is currently expanded. */
    private var isDetailExpanded = mutableStateOf(false)

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
            override fun setFlagSecure(enabled: Boolean) { windowMgr.setAllFlagSecure(enabled) }
            override fun setWindowAlpha(alpha: Float) { windowMgr.setAllAlpha(alpha) }
            override fun animateWindowX(targetX: Float, animated: Boolean) { this@FloatingWindowService.animateWindowX(targetX, animated) }
            override fun setHasContent(has: Boolean) { viewModel.hasContent = has }
            override fun onRecordingBitmap(bitmap: Bitmap) { recorder.processBitmap(bitmap) }
            override fun onImageText(text: String) { imageCollector.addText(text) }
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

        // ── Stealth mode observer (D3: dynamic FLAG_SECURE + notification) ─
        // Monitored from within Window A's composable via LaunchedEffect + snapshotFlow
        // (see showFloatingWindow setContent block)

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

    // ── Floating window UI (3-window architecture) ──────────────────────

    /**
     * Creates Window A (Pill) — the primary always-visible window.
     * Windows B and C are created lazily when needed.
     */
    private fun showFloatingWindow() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val density = metrics.density
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val buttonHalf = buttonSizePx / 2f
        val isStealth = settings.stealthMode.value

        // Initial position: right edge, 30% down
        viewModel.floatOffsetX.value = screenW - buttonHalf
        viewModel.floatOffsetY.value = screenH * 0.30f

        val aComposeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            if (isStealth) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            setContent {
                AIAnswererTheme {
                    // Window A — always visible pill button
                    WindowAContent(
                        buttonSize = settings.floatButtonSizeDp.value,
                        buttonAlpha = settings.floatButtonAlpha.value,
                        floatingStatus = viewModel.floatingStatus.value,
                        isRecording = viewModel.isRecording.value,
                        isImageCollecting = viewModel.isImageCollecting.value,
                        isLeftSide = viewModel.floatOffsetX.value < screenW / 2f,
                        isDragging = false,
                        onCaptureClick = { captureHandler.handleCapture() },
                        onLongPress = {
                            AppLog.d("FWS", "onLongPress triggered, isArcExpanded=$isArcExpanded")
                            isArcExpanded = !isArcExpanded
                            if (isArcExpanded) {
                                try {
                                    AppLog.d("FWS", "ensureWindowB calling...")
                                    ensureWindowB()
                                    AppLog.d("FWS", "ensureWindowB done")
                                } catch (e: Exception) {
                                    AppLog.e("FWS", "ensureWindowB failed", e)
                                }
                            } else {
                                removeWindowB()
                            }
                        },
                        onMove = { deltaX, deltaY -> dragWindowBy(deltaX, deltaY) },
                        onDragEnd = { leftSide ->
                            val snapX = if (leftSide) buttonHalf else screenW - buttonHalf
                            viewModel.floatOffsetX.value = snapX
                            val windowX = if (leftSide) 0f else (screenW - getAWindowSize()).coerceAtLeast(0f)
                            animateWindowX(windowX, animated = true)
                        },
                        onMeasuredSize = { w, h -> measuredSizeA = w to h }
                    )

                    // Window C/D lifecycle observer — separate windows for status (C)
                    // and answer content (D). When answer arrives, C is hidden and D
                    // appears directly below (or above) Window A.
                    LaunchedEffect(Unit) {
                        snapshotFlow { viewModel.showAnswer.value to viewModel.statusMessage.value }
                            .collect { (show, msg) ->
                                val hasContent = show || msg != null
                                AppLog.d("FWS", "snapshotFlow: showAnswer=$show statusMessage=$msg hasContent=$hasContent cView=${windowMgr.cView != null} dView=${windowMgr.dView != null}")
                                if (show) {
                                    // Answer ready: show D, hide C
                                    if (windowMgr.cView != null) removeWindowC()
                                    if (windowMgr.dView == null) ensureWindowD()
                                } else if (msg != null) {
                                    // Status message: show C, hide D
                                    if (windowMgr.dView != null) removeWindowD()
                                    if (windowMgr.cView == null) ensureWindowC()
                                } else {
                                    // Clean up: nothing to show
                                    AppLog.d("FWS", "snapshotFlow: cleaning up windows")
                                    removeWindowD()
                                    removeWindowC()
                                }
                            }
                    }

                    // Stealth mode toggle observer (D3) — reactively update FLAG_SECURE
                    // on all 3 windows when the user toggles stealth in settings.
                    LaunchedEffect(Unit) {
                        snapshotFlow { settings.stealthMode.value }
                            .distinctUntilChanged()
                            .collect { isStealth ->
                                windowMgr.setAllFlagSecure(isStealth)
                                windowMgr.setAllAlpha(if (isStealth) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)
                                listOfNotNull(windowAView, windowBView, windowCView).forEach { view ->
                                    view.importantForAccessibility = if (isStealth)
                                        View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                    else View.IMPORTANT_FOR_ACCESSIBILITY_YES
                                }
                                updateNotification(isStealth)
                            }
                    }
                }
            }
        }

        val aParams = windowMgr.createLayoutParams(
            windowId = FloatingWindowManager.WindowId.A,
            buttonSizePx = buttonSizePx.toInt(),
            isStealth = isStealth
        )

        windowMgr.attachA(aComposeView, aParams)
        windowAView = aComposeView
        updateWindowAPosition()
    }

    // ── Window B (Quick Toggles) ────────────────────────────────────────

    /** Creates and attaches Window B, positioned adjacent to A. */
    private fun ensureWindowB() {
        AppLog.d("FWS", "ensureWindowB: start")
        if (windowMgr.bView != null) { AppLog.d("FWS", "ensureWindowB: already exists, return"); return }

        val metrics = resources.displayMetrics
        val density = metrics.density
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val isStealth = settings.stealthMode.value
        val isLeft = viewModel.floatOffsetX.value < resources.displayMetrics.widthPixels.toFloat() / 2f
        AppLog.d("FWS", "ensureWindowB: isLeft=$isLeft density=$density buttonSizePx=$buttonSizePx")

        // 1. Build quick actions FIRST (before creating ComposeView) to catch any resource exceptions early
        AppLog.d("FWS", "ensureWindowB: building quick actions...")
        // NOT calling composable buildQuickActions here - will use inside setContent

        AppLog.d("FWS", "ensureWindowB: creating ComposeView...")
        val bComposeView = ComposeView(this).apply {
            AppLog.d("FWS", "ensureWindowB: ComposeView.apply start")
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            if (isStealth) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            AppLog.d("FWS", "ensureWindowB: calling setContent...")
            setContent {
                AIAnswererTheme {
                    WindowBContent(
                        t = sandboxTheme(),
                        actions = buildQuickActions(),
                        scale = 1f,
                        isLeftSide = isLeft,
                        transformOrigin = if (isLeft) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f),
                        onMeasuredSize = { w, h ->
                            AppLog.d("FWS", "WindowB onMeasuredSize: $w x $h")
                            if (w > 0 && h > 0) {
                                val bParams = windowMgr.bParams ?: return@WindowBContent
                                windowMgr.updateLayoutB(
                                    windowX = bParams.x,
                                    windowY = bParams.y,
                                    width = w.toInt(),
                                    height = h.toInt(),
                                    alpha = Constants.VISIBLE_ALPHA,
                                    screenW = resources.displayMetrics.widthPixels.toFloat(),
                                    screenH = resources.displayMetrics.heightPixels.toFloat()
                                )
                                syncB()
                            }
                        }
                    )
                }
            }
            AppLog.d("FWS", "ensureWindowB: setContent done")
        }

        AppLog.d("FWS", "ensureWindowB: creating layout params...")
        val bParams = windowMgr.createLayoutParams(
            windowId = FloatingWindowManager.WindowId.B,
            buttonSizePx = buttonSizePx.toInt(),
            isStealth = isStealth
        )
        AppLog.d("FWS", "ensureWindowB: bParams size=${bParams.width}x${bParams.height}")

        // IMPORTANT: Do NOT remove/re-add Window A for Z-order here.
        // Doing so while inside a Compose callback (onLongPress) causes a
        // reentrancy deadlock in Compose's snapshot system (removeView triggers
        // onDetachedFromWindow → composition disposal while still in the callback).
        // B will appear on top of A — acceptable since they are side-by-side.

        AppLog.d("FWS", "ensureWindowB: calling attachB...")
        try {
            windowMgr.attachB(bComposeView, bParams)
            AppLog.d("FWS", "ensureWindowB: attachB done")
        } catch (e: Exception) {
            AppLog.e("FWS", "attachB failed", e)
        }

        windowBView = bComposeView
        AppLog.d("FWS", "ensureWindowB: calling syncB...")
        syncB()
        AppLog.d("FWS", "ensureWindowB: COMPLETE")
    }

    /** Detaches and disposes Window B. */
    private fun removeWindowB() {
        windowMgr.detachB()
        windowBView?.disposeComposition()
        windowBView = null
    }

    /** Builds the quick-toggle action list from current settings state. */
    private fun buildQuickActions(): List<QuickAction> {
        val vlmLabel = getString(R.string.float_quick_vlm)
        val searchLabel = getString(R.string.float_quick_search)
        val reasoningLabel = getString(R.string.float_quick_reasoning)
        val recordLabel = getString(R.string.float_quick_record)
        val imageLabel = getString(R.string.float_quick_image)
        return listOf(
            QuickAction(
                icon = IcVision,
                label = vlmLabel,
                enabled = settings.visionEnabled.value,
                onClick = {
                    settings.visionEnabled.value = !settings.visionEnabled.value
                    AppConfig.saveVisionEnabled(settings.visionEnabled.value)
                    Toast.makeText(this@FloatingWindowService,
                        if (settings.visionEnabled.value) getString(R.string.float_toggle_vlm_on) else getString(R.string.float_toggle_vlm_off),
                        Toast.LENGTH_SHORT).show()
                }
            ),
            QuickAction(
                icon = IcGlobe,
                label = searchLabel,
                enabled = settings.searchEnabled.value,
                onClick = {
                    val hasProviders = com.hwb.aianswerer.providers.WebSearchStorage.getEnabledProviders().isNotEmpty()
                    if (!hasProviders && !settings.searchEnabled.value) {
                        Toast.makeText(this@FloatingWindowService,
                            getString(R.string.float_toggle_search_no_provider),
                            Toast.LENGTH_SHORT).show()
                        return@QuickAction
                    }
                    settings.searchEnabled.value = !settings.searchEnabled.value
                    com.hwb.aianswerer.providers.WebSearchStorage.saveSearchEnabled(settings.searchEnabled.value)
                    Toast.makeText(this@FloatingWindowService,
                        if (settings.searchEnabled.value) getString(R.string.float_toggle_search_on) else getString(R.string.float_toggle_search_off),
                        Toast.LENGTH_SHORT).show()
                }
            ),
            QuickAction(
                icon = IcBulb,
                label = reasoningLabel,
                enabled = settings.reasoningEnabled.value,
                onClick = {
                    settings.reasoningEnabled.value = !settings.reasoningEnabled.value
                    AppConfig.saveReasoningEffort(settings.reasoningEnabled.value)
                    Toast.makeText(this@FloatingWindowService,
                        if (settings.reasoningEnabled.value) getString(R.string.float_toggle_reasoning_on) else getString(R.string.float_toggle_reasoning_off),
                        Toast.LENGTH_SHORT).show()
                }
            ),
            QuickAction(
                icon = IcImage,
                label = imageLabel,
                enabled = settings.imageEnabled.value,
                onClick = {
                    if (viewModel.isImageCollecting.value) {
                        imageCollector.stop()
                        viewModel.isImageCollecting.value = false
                        viewModel.isProcessingImages.value = imageCollector.isProcessing
                        settings.imageEnabled.value = false
                        Toast.makeText(this@FloatingWindowService,
                            getString(R.string.image_analyzing),
                            Toast.LENGTH_SHORT).show()
                    } else {
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
                }
            ),
            QuickAction(
                icon = IcRecord,
                label = recordLabel,
                enabled = viewModel.isRecording.value,
                onClick = {
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
                }
            )
        )
    }

    // ── Window C (Answer/Status Card) ──────────────────────────────────

    /** Creates and attaches Window C, positioned below A. */
    private fun ensureWindowC() {
        AppLog.d("FWS", "ensureWindowC: start")
        if (windowMgr.cView != null) { AppLog.d("FWS", "ensureWindowC: already exists, return"); return }

        val metrics = resources.displayMetrics
        val density = metrics.density
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val isStealth = settings.stealthMode.value
        AppLog.d("FWS", "ensureWindowC: density=$density isStealth=$isStealth")

        val cComposeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            if (isStealth) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            setContent {
                AIAnswererTheme {
                    WindowCContent(
                        showAnswer = viewModel.showAnswer.value,
                        hasAnswer = hasCardContent(),
                        statusMessage = viewModel.statusMessage.value,
                        floatingStatus = viewModel.floatingStatus.value,
                        cardAlpha = settings.floatCardAlpha.value,
                        recordingCaptureCount = viewModel.recordingCaptureCount.value,
                        isRecording = viewModel.isRecording.value,
                        isProcessingRecording = viewModel.isProcessingRecording.value,
                        onCloseAnswer = { closeAnswer() },
                        onCloseStatus = { closeStatus() },
                        onMeasuredHeight = { h ->
                            measuredWindowCHeight = h
                            syncC()
                        },
                        onDismissRequest = { removeWindowC() },
                        isExpanded = isDetailExpanded.value,
                        onToggleExpanded = { expanded ->
                            isDetailExpanded.value = expanded
                            if (expanded) ensureWindowD() else removeWindowD()
                        }
                    )
                }
            }
        }

        AppLog.d("FWS", "ensureWindowC: creating layout params...")
        val cParams = windowMgr.createLayoutParams(
            windowId = FloatingWindowManager.WindowId.C,
            buttonSizePx = buttonSizePx.toInt(),
            isStealth = isStealth
        )
        AppLog.d("FWS", "ensureWindowC: params size=${cParams.width}x${cParams.height}")

        AppLog.d("FWS", "ensureWindowC: calling attachC... cView before=${windowMgr.cView != null}")
        try {
            windowMgr.attachC(cComposeView, cParams)
            AppLog.d("FWS", "ensureWindowC: attachC done, cView after=${windowMgr.cView != null} cParams=${cParams.width}x${cParams.height} x=${cParams.x} y=${cParams.y}")
        } catch (e: Exception) {
            AppLog.e("FWS", "attachC failed", e)
        }

        windowCView = cComposeView
        AppLog.d("FWS", "ensureWindowC: calling syncC... measuredCHeight=$measuredWindowCHeight")
        syncC()
        AppLog.d("FWS", "ensureWindowC: COMPLETE. final cParams=${windowMgr.cParams?.width}x${windowMgr.cParams?.height}")
    }

    /** Detaches and disposes Window C. */
    private fun removeWindowC() {
        AppLog.d("FWS", "removeWindowC called! cView=${windowMgr.cView != null}")
        windowMgr.detachC()
        windowCView?.disposeComposition()
        windowCView = null
        measuredWindowCHeight = null
    }

    // ── Window D (Answer Detail) ─────────────────────────────────────────

    /** Creates and attaches Window D (full answer content) below Window C. */
    private fun ensureWindowD() {
        if (windowMgr.dView != null) return
        AppLog.d("FWS", "ensureWindowD: start")

        val density = resources.displayMetrics.density

        val dComposeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            if (settings.stealthMode.value) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            setContent {
                AIAnswererTheme {
                    WindowDContent(
                        hasAnswer = hasCardContent(),
                        paginatedAnswers = viewModel.paginatedAnswers.value,
                        recordingAnswers = viewModel.recordingAnswers.value,
                        isRecording = viewModel.isRecording.value,
                        isProcessingRecording = viewModel.isProcessingRecording.value,
                        onCopyRecordingAnswer = { text ->
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, text)
                        },
                        onCloseAnswer = { closeAnswer() },
                        onMeasuredHeight = { h ->
                            AppLog.d("FWS", "WindowD measuredHeight=$h")
                            measuredWindowDHeight = h
                            positionWindowD()
                        },
                        cardAlpha = settings.floatCardAlpha.value
                    )
                }
            }
        }

        val dParams = windowMgr.createLayoutParams(
            windowId = FloatingWindowManager.WindowId.D,
            buttonSizePx = (settings.floatButtonSizeDp.value * density).toInt(),
            isStealth = settings.stealthMode.value
        )
        AppLog.d("FWS", "ensureWindowD: dParams size=${dParams.width}x${dParams.height}")

        windowMgr.attachD(dComposeView, dParams)
        windowDView = dComposeView
        measuredWindowDHeight = null // reset before first measure
        positionWindowD()
        AppLog.d("FWS", "ensureWindowD: COMPLETE")
    }

    /** Detaches and disposes Window D. */
    private fun removeWindowD() {
        windowMgr.detachD()
        windowDView?.disposeComposition()
        windowDView = null
        measuredWindowDHeight = null
        isDetailExpanded.value = false
    }

    /**
     * Positions Window D directly below (or above) Window A.
     * Used when answer is ready: Window C is hidden, D attaches to A.
     * Falls back to a reasonable default if C was never measured.
     */
    private fun positionWindowD() {
        if (windowMgr.dView == null) return
        val aParams = windowMgr.aParams ?: return
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(realMetrics)
        val screenW = realMetrics.widthPixels.toFloat()
        val screenH = realMetrics.heightPixels.toFloat()
        val density = realMetrics.density
        AppLog.d("FWS", "positionWindowD: aPos=${aParams.x},${aParams.y} aSize=${aParams.width}x${aParams.height}")

        val dW = (FWDims.cardWidthDp.value * density).toInt()
        val cH = measuredWindowCHeight ?: (60 * density).toFloat()
        val dH = (cH * 4).toInt() // 400% of C height
        val gapPx = (8 * density).toInt()

        // Center D horizontally relative to A
        val dX = aParams.x + (aParams.width - dW) / 2

        // Place D below A if there's room, otherwise above A
        val spaceBelow = screenH.toInt() - (aParams.y + aParams.height + gapPx)
        val dY = if (dH > 0 && spaceBelow >= dH) {
            aParams.y + aParams.height + gapPx
        } else {
            (aParams.y - dH - gapPx).coerceAtLeast(0)
        }

        AppLog.d("FWS", "positionWindowD: dX=$dX dY=$dY dSize=${dW}x${dH} below=${spaceBelow >= dH}")
        windowMgr.updateLayoutD(
            windowX = dX.coerceIn(0, maxOf(0, screenW.toInt() - dW)),
            windowY = dY.coerceIn(0, maxOf(0, screenH.toInt() - dH)),
            width = dW,
            height = dH,
            alpha = Constants.VISIBLE_ALPHA,
            screenW = screenW,
            screenH = screenH
        )
    }

    /** Whether there is card-worthy content to display. */
    private fun hasCardContent(): Boolean {
        return viewModel.showAnswer.value && (
            viewModel.answerText.value != null ||
            viewModel.paginatedAnswers.value.isNotEmpty() ||
            viewModel.recordingAnswers.value.isNotEmpty()
        )
    }

    // ── Window positioning ──────────────────────────────────────────────

    /** Size of Window A (square) in px, based on button size + margins. */
    private fun getAWindowSize(): Int {
        val density = resources.displayMetrics.density
        val buttonSizePx = settings.floatButtonSizeDp.value * density
        val padding = (FWDims.pillEdgeMargin.value * 2 * density).toInt()
        return buttonSizePx.toInt() + padding
    }

    /**
     * Updates Window A position from floatOffsetX / floatOffsetY.
     * displayWindowX tracks the actual window X coordinate (0 or screenW-aSize).
     */
    private fun updateWindowAPosition() {
        if (destroyed) return
        val aParams = windowMgr.aParams ?: return
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(realMetrics)
        val screenW = realMetrics.widthPixels.toFloat()
        val screenH = realMetrics.heightPixels.toFloat()
        val aSize = getAWindowSize()
        val isLeft = viewModel.floatOffsetX.value < screenW / 2f

        val x = if (isLeft) 0 else (screenW - aSize).toInt().coerceAtLeast(0)
        val y = viewModel.floatOffsetY.value.toInt().coerceIn(0, screenH.toInt() - aSize)

        viewModel.displayWindowX.floatValue = x.toFloat()
        windowMgr.updateLayoutA(
            windowX = x,
            windowY = y,
            width = aSize,
            height = aSize,
            alpha = Constants.VISIBLE_ALPHA,
            screenW = screenW,
            screenH = screenH
        )

        // Sync companion windows
        syncB()
        syncC()
        positionWindowD()
    }

    /** Positions Window B adjacent to Window A. */
    private fun syncB() {
        val aParams = windowMgr.aParams ?: return
        if (windowMgr.bView == null) return
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val density = metrics.density
        val isLeft = viewModel.floatOffsetX.value < screenW / 2f
        val gapPx = (FWDims.quickPanelGap.value * density).toInt()

        val bP = windowMgr.bParams ?: return
        val bW = bP.width.coerceAtLeast(1)
        val bH = bP.height.coerceAtLeast(1)

        val bX = if (isLeft) {
            aParams.x + aParams.width + gapPx
        } else {
            aParams.x - bW - gapPx
        }
        val bY = aParams.y + (aParams.height - bH) / 2

        windowMgr.updateLayoutB(
            windowX = bX,
            windowY = bY,
            width = bW,
            height = bH,
            alpha = Constants.VISIBLE_ALPHA,
            screenW = screenW,
            screenH = screenH
        )
    }

    /** Positions Window C below (or above) Window A, using real display metrics. */
    private fun syncC() {
        val aParams = windowMgr.aParams ?: return
        if (windowMgr.cView == null) return
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(realMetrics)
        val screenW = realMetrics.widthPixels.toFloat()
        val screenH = realMetrics.heightPixels.toFloat()
        val density = realMetrics.density
        AppLog.d("FWS", "syncC: realScreen=${screenW.toInt()}x${screenH.toInt()} aPos=${aParams.x},${aParams.y} aSize=${aParams.width}x${aParams.height}")

        val cW = (FWDims.cardWidthDp.value * density).toInt()
        // Compact card header is ~52dp; use 60dp as initial before measurement
        val defaultH = (60 * density).toInt()
        val cH = (measuredWindowCHeight ?: defaultH.toFloat()).toInt()

        val gapPx = (8 * density).toInt()
        val cX = aParams.x + (aParams.width - cW) / 2

        val spaceBelow = screenH.toInt() - (aParams.y + aParams.height + gapPx)
        val cY = if (cH > 0 && spaceBelow >= cH) {
            aParams.y + aParams.height + gapPx
        } else if (cH > 0) {
            (aParams.y - cH - gapPx).coerceAtLeast(0)
        } else {
            aParams.y + aParams.height + gapPx
        }

        val clampedX = cX.coerceIn(0, maxOf(0, screenW.toInt() - cW))
        val clampedY = cY.coerceIn(0, maxOf(0, screenH.toInt() - cH))
        AppLog.d("FWS", "syncC: computed cX=$cX cY=$cY clamped=$clampedX,$clampedY cSize=${cW}x${cH}")
        windowMgr.updateLayoutC(
            windowX = clampedX,
            windowY = clampedY,
            width = cW,
            height = cH,
            alpha = Constants.VISIBLE_ALPHA,
            screenW = screenW,
            screenH = screenH
        )
    }

    /** Handles drag gesture on Window A — updates offset and repositions all windows. */
    private fun dragWindowBy(deltaX: Float, deltaY: Float) {
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(realMetrics)
        val screenW = realMetrics.widthPixels.toFloat()
        val screenH = realMetrics.heightPixels.toFloat()
        val aSize = getAWindowSize().toFloat()
        val buttonHalf = (settings.floatButtonSizeDp.value * realMetrics.density) / 2f

        viewModel.floatOffsetX.value = (viewModel.floatOffsetX.value + deltaX)
            .coerceIn(buttonHalf, screenW - buttonHalf)
        viewModel.floatOffsetY.value = (viewModel.floatOffsetY.value + deltaY)
            .coerceIn(0f, screenH - aSize)

        // During drag: position window at actual finger position (continuous, not snapped)
        val dragX = (viewModel.floatOffsetX.value - buttonHalf).toInt()
            .coerceIn(0, maxOf(0, screenW.toInt() - aSize.toInt()))
        val dragY = viewModel.floatOffsetY.value.toInt()
            .coerceIn(0, maxOf(0, screenH.toInt() - aSize.toInt()))
        viewModel.displayWindowX.floatValue = dragX.toFloat()
        windowMgr.updateLayoutA(
            windowX = dragX,
            windowY = dragY,
            width = aSize.toInt(),
            height = aSize.toInt(),
            alpha = Constants.VISIBLE_ALPHA,
            screenW = screenW,
            screenH = screenH
        )
        syncB()
        syncC()
        positionWindowD()
    }

    /**
     * Animates Window A to target X position (edge snap).
     * Uses displayWindowX as the animated window coordinate.
     * floatOffsetX is not changed during animation (side stays constant during snap).
     */
    private fun animateWindowX(targetX: Float, animated: Boolean) {
        viewModel.windowXAnimJob?.cancel()
        if (!animated) {
            viewModel.displayWindowX.floatValue = targetX
            // Apply final position directly
            val aParams = windowMgr.aParams ?: return
            val metrics = resources.displayMetrics
            windowMgr.updateLayoutA(
                windowX = targetX.toInt(),
                windowY = aParams.y,
                width = aParams.width,
                height = aParams.height,
                alpha = Constants.VISIBLE_ALPHA,
                screenW = metrics.widthPixels.toFloat(),
                screenH = metrics.heightPixels.toFloat()
            )
            syncB()
            syncC()
            positionWindowD()
            return
        }
        viewModel.windowXAnimJob = windowMgr.animateWindowX(
            scope = serviceScope,
            from = viewModel.displayWindowX.floatValue,
            to = targetX
        ) anim@{ currentX ->
            viewModel.displayWindowX.floatValue = currentX
            // Directly update Window A position using animated X
            val aParams = windowMgr.aParams ?: return@anim
            val metrics = resources.displayMetrics
            windowMgr.updateLayoutA(
                windowX = currentX.toInt(),
                windowY = aParams.y,
                width = aParams.width,
                height = aParams.height,
                alpha = Constants.VISIBLE_ALPHA,
                screenW = metrics.widthPixels.toFloat(),
                screenH = metrics.heightPixels.toFloat()
            )
            syncB()
            syncC()
            positionWindowD()
        }
    }

    // ── Stealth notification update ──────────────────────────────────────

    /** Rebuilds and re-posts the foreground notification with stealth-aware content. */
    private fun updateNotification(isStealth: Boolean) {
        val notification = NotificationHelper.buildNotification(this, isStealth)
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(Constants.NOTIFICATION_ID, notification)
    }

    // ── Card action handlers ────────────────────────────────────────────

    private fun closeAnswer() {
        AppLog.d("FWS", "closeAnswer called")
        viewModel.currentFetchJob?.cancel()
        viewModel.currentFetchJob = null
        viewModel.showAnswer.value = false
        viewModel.answerText.value = null
        viewModel.recordingAnswers.value = emptyList()
        viewModel.paginatedAnswers.value = emptyList()
        viewModel.paginatedCopyTexts.value = emptyList()
        viewModel.floatingStatus.value = FloatingStatus.Idle
        viewModel.statusMessage.value = null
    }

    private fun closeStatus() {
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
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

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

        // Remove all windows
        windowMgr.detachD()
        windowMgr.detachC()
        windowMgr.detachB()
        windowMgr.detachA()

        // Dispose composition for each
        windowDView?.disposeComposition()
        windowCView?.disposeComposition()
        windowBView?.disposeComposition()
        windowAView?.disposeComposition()

        windowDView = null
        windowCView = null
        windowBView = null
        windowAView = null

        screenCaptureManager?.releaseAll()
        _viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
