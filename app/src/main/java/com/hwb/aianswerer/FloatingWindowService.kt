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
import androidx.compose.ui.platform.ViewCompositionStrategy
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

// ===== 收起态答案摘要（纯函数，可单测） ===== 

/** 收起态摘要答案字符上限：选择题短答案（如 ABCD）直接显示，填空/问答题长答案截断。 */
const val ANSWER_SUMMARY_MAX_CHARS = 7

/** 答案超过上限时的省略后缀（为填空题/问答题准备）。 */
const val ANSWER_SUMMARY_ELLIPSIS = "......"

/** copyText 的"第 X 题："前缀（如 "第 1 题：B. 任天堂" → "B. 任天堂"）。 */
private val COPY_TEXT_PREFIX = Regex("""^第\s*\d+\s*题\s*[:：]\s*""")

/**
 * 构建收起态（C 窗）答案摘要：prefix + 第 1 题纯答案。
 * 答案 ≤ [maxChars] 字符全显；超过则截断并追加 [ellipsis]。
 * 无任何答案时返回 null。
 */
fun buildCollapsedAnswerSummary(
    copyTexts: List<Pair<Int, String>>,
    fallbackAnswer: String?,
    prefix: String,
    maxChars: Int = ANSWER_SUMMARY_MAX_CHARS,
    ellipsis: String = ANSWER_SUMMARY_ELLIPSIS
): String? {
    val raw = copyTexts.firstOrNull()?.second?.let {
        it.replaceFirst(COPY_TEXT_PREFIX, "")
    } ?: fallbackAnswer?.takeIf { it.isNotBlank() } ?: return null
    val trimmed = raw.trim().replace("\n", " ").trim()
    if (trimmed.isEmpty()) return null
    val body = if (trimmed.length > maxChars) trimmed.take(maxChars) + ellipsis else trimmed
    return "$prefix$body"
}

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
    private var isDetailExpanded = mutableStateOf(false)
    /** M16: 动画帧 B/C/D 同步节流时间戳 */
    private var lastAnimSyncMs = 0L
    /**
     * C/D 窗展开/收起过渡动画 Job。动画进行期间 snapshotFlow 跳过窗口增删,
     * 由动画回调统一收尾,避免动画帧与窗口操作竞争。
     */
    private var windowTransitionJob: Job? = null
    /** D 窗 attach 后等待首次测量,测量后播放从紧凑高度伸展到内容高度的展开动画。 */
    private var pendingExpandAnim = false
    /** 收起过渡完成、C 窗以透明状态创建,等待测量收缩后淡入(一次性)。 */
    private var pendingCFadeIn = false

    // ── M14: 旋转适配 ──
    /** 上一次已知屏幕尺寸（用于旋转后按比例映射偏移，保留相对位置） */
    private var lastScreenSize = android.graphics.Point(0, 0)

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
                        // P0-5: 普通答题展示 → 关闭多图结果窗口，旧多图结果到达时被 onResult 守卫丢弃
                        viewModel.isImageResultActive.value = false
                        viewModel.answerText.value = answer
                        // M11: 广播路径必须同步填充 paginatedAnswers，否则 D 窗空白（WindowDContent 只渲染 paginated/recording）
                        viewModel.paginatedAnswers.value = listOf(1 to answer)
                        viewModel.paginatedCopyTexts.value = listOf(1 to answer)
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
                        } else if (viewModel.isImageCollecting.value) {
                            imageCollector.handleCroppedImage(imagePath, cropRect)
                        } else {
                            captureHandler.handleCroppedImage(imagePath, cropRect)
                        }
                    }
                }
                Constants.ACTION_REFRESH_SETTINGS -> {
                    // L2: 真正重读持久化设置到 Compose 状态（原 refreshSettingsFromApp 为 noop）
                    settings.refresh()
                    // 外观设置需重新应用到窗口（大小/透明度等）
                    updateWindowAPosition()
                    viewModel.refreshSettingsFromApp()
                }
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
            override fun onImageText(text: String) { imageCollector.processText(text) }
            override fun onImageBitmap(bitmap: Bitmap) { imageCollector.processBitmap(bitmap) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        showFloatingWindow()

        // M14: 记录初始屏幕尺寸，供旋转后按比例映射偏移
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val initMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(initMetrics)
        lastScreenSize.set(initMetrics.widthPixels, initMetrics.heightPixels)

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
            // API 26-32: 接收方无法指定 NOT_EXPORTED，但所有发送方均已 setPackage(packageName)
            // （ConfirmTextActivity/ImageCropActivity/MainActivity），外部应用无法注入，安全性由发送方保证
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
                    // M1: 重入守卫——服务已运行时再次 start 会带相同 extra，跳过已就绪的 MediaProjection
                    if (screenCaptureManager?.isReady != true) {
                        screenCaptureManager?.initMediaProjection(resultCode, data)
                    } else {
                        AppLog.d("FWS", "onStartCommand: MediaProjection already ready, skip re-init")
                    }
                }
            }
            if (it.hasExtra("cropMode")) {
                viewModel.cropMode = it.getStringExtra("cropMode") ?: AppConfig.CROP_MODE_FULL
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
            // M3: 显式组合策略——detach 时自动释放 composition，避免泄漏
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
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
                                // M5: removeView 移出 Compose 回调，避免 snapshot 系统重入死锁（代码自身注释警告过）
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    removeWindowB()
                                }
                            }
                        },
                        onMove = { deltaX, deltaY -> dragWindowBy(deltaX, deltaY) },
                        onDragEnd = { leftSide ->
                            // M13: snapX 用窗口半宽（含 padding），与 dragWindowBy 的 halfSize 映射一致
                            val halfSize = getAWindowSize() / 2f
                            val snapX = if (leftSide) halfSize else screenW - halfSize
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
                        // 观察答案/状态/折叠三态：展开态显示 D 窗，收起态显示 C 窗（紧凑摘要）
                        // M-EXPAND: 答案生成(show false→true)时自动展开 D 窗显示完整答案，
                        //           避免用户需手动点展开才看到结果；用户手动收起后保持收起
                        var prevShow = false
                        snapshotFlow {
                            Triple(viewModel.showAnswer.value, viewModel.statusMessage.value, isDetailExpanded.value)
                        }.collect { (show, msg, expanded) ->
                            // 答案新到达且当前收起态 → 自动展开
                            if (show && !prevShow && !expanded) {
                                AppLog.d("FWS", "snapshotFlow: answer arrived, auto-expanding");
                                isDetailExpanded.value = true
                                prevShow = true
                                return@collect
                            }
                            prevShow = show
                            // 折叠过渡动画进行中:窗口增删由动画回调统一收尾,
                            // 此处跳过避免与动画帧竞争(收起先收缩再替换 C 窗)
                            if (windowTransitionJob?.isActive == true) {
                                AppLog.d("FWS", "snapshotFlow: transition anim active, skip window ops")
                                return@collect
                            }
                            // M4: 收集器内任何窗口操作异常都不允许杀死 LaunchedEffect(否则状态变化永久失去响应)
                            try {
                                syncWindows(show, msg, expanded)
                            } catch (e: Exception) {
                                AppLog.e("FWS", "snapshotFlow collect failed", e)
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
                                listOfNotNull(windowAView, windowBView, windowCView, windowDView).forEach { view ->
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

        // S6: attachA 失败时不能崩溃服务——catch 并清理部分状态
        try {
            windowMgr.attachA(aComposeView, aParams)
        } catch (e: Exception) {
            AppLog.e("FWS", "attachA failed — floating window unavailable", e)
            windowMgr.detachA()
            return
        }
        windowAView = aComposeView
        updateWindowAPosition()
    }

    /**
     * C/D 窗生命周期同步(快照收集器与过渡动画回调共用):
     * 展开态只留 D(完整答案),收起态只留 C(紧凑摘要),无内容则全部移除。
     */
    private fun syncWindows(show: Boolean, msg: String?, expanded: Boolean) {
        val hasContent = show || msg != null
        AppLog.d("FWS", "syncWindows: showAnswer=$show statusMessage=$msg expanded=$expanded hasContent=$hasContent cView=${windowMgr.cView != null} dView=${windowMgr.dView != null}")
        if (show) {
            // Answer ready: 展开态显示 D(完整答案),收起态显示 C(紧凑摘要)
            if (expanded) {
                if (windowMgr.cView != null) removeWindowC()
                if (windowMgr.dView == null) ensureWindowD()
            } else {
                if (windowMgr.dView != null) removeWindowD()
                if (windowMgr.cView == null) ensureWindowC()
            }
        } else if (msg != null) {
            // Status message: show C, hide D
            if (windowMgr.dView != null) removeWindowD()
            if (windowMgr.cView == null) ensureWindowC()
        } else {
            // Clean up: nothing to show
            AppLog.d("FWS", "syncWindows: cleaning up windows")
            removeWindowD()
            removeWindowC()
        }
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
            // M3
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
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
        var attachBOk = false
        try {
            windowMgr.attachB(bComposeView, bParams)
            attachBOk = windowMgr.bView != null
            AppLog.d("FWS", "ensureWindowB: attachB done")
        } catch (e: Exception) {
            AppLog.e("FWS", "attachB failed", e)
        }

        // P4: attach 失败时不要赋值 windowBView / 不要 syncB，避免对未附加 View 操作
        if (!attachBOk) {
            AppLog.e("FWS", "ensureWindowB: attachB failed, window not created"); return
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
                        // P0-5: 进入多图结果展示期 — onResult 守卫放行；普通答题/录制会复位此标志
                        viewModel.isImageResultActive.value = true
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
                        viewModel.imageCaptureCount.value = 0
                        // P0-5: 新多图会话开始 → 关闭旧结果窗口
                        viewModel.isImageResultActive.value = false
                        viewModel.showAnswer.value = false
                        viewModel.paginatedAnswers.value = emptyList()
                        // Fix A: 立即显示状态消息，让折叠窗马上出现（窗口由 statusMessage 驱动）
                        viewModel.statusMessage.value = getString(R.string.image_collection_start)
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
                            viewModel.isImageResultActive.value = false // P0-5: 切录制 → 关闭多图结果窗口
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
        // 窗口初始高度按内容模式设定，避免窗口大于内容产生透明触摸区（阻挡底层按键）：
        // 答案摘要模式：单行标题，用上次实测或紧凑值（72dp）；
        // 状态消息模式：1-2 行消息，用 96dp——不再用 180dp 上限（真机上 Compose 测量
        // 会把 Box 撑满到上限上报假值，导致窗口永不收缩）。
        val answerMode = viewModel.showAnswer.value
        if (answerMode) {
            if (measuredWindowCHeight == null) {
                measuredWindowCHeight = (FWDims.cardCompactInitHeight.value * density).toFloat()
            }
        } else {
            measuredWindowCHeight = (FWDims.cardStatusInitHeight.value * density).toFloat()
        }

        val cComposeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            // M3
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
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
                            // 诊断日志:确认 C 窗测量回调是否触发及上报值(修好后可移除)
                            AppLog.d("FWS", "WindowC measuredHeight=$h (was=${measuredWindowCHeight})")
                            // 防御:上报值达到上限(180dp)时视为测量异常假值(内容被父约束撑满),
                            // 不采纳,保持模式初始高度(72/96dp)——否则窗口永不收缩且透明区拦截触摸
                            val upper = (FWDims.cardCompactMaxHeight.value * density).toFloat()
                            if (h < upper - 1f) {
                                measuredWindowCHeight = h
                            }
                            syncC()
                            // 收起过渡:透明创建的 C 窗在测量收缩到实际高度后淡入,
                            // 先压回透明再渐入,避免 syncC 重置 alpha 造成的一帧闪白
                            if (pendingCFadeIn) {
                                pendingCFadeIn = false
                                val target = if (settings.stealthMode.value)
                                    Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA
                                windowMgr.setAlpha(windowMgr.cView, 0f)
                                windowMgr.animateWindowAlpha(
                                    scope = serviceScope,
                                    view = windowMgr.cView,
                                    from = 0f,
                                    to = target,
                                    durationMs = 90L
                                )
                            }
                        },
                        onDismissRequest = { removeWindowC() },
                        isExpanded = isDetailExpanded.value,
                        onToggleExpanded = { expanded ->
                            // 状态驱动：snapshotFlow 监听 isDetailExpanded，自动切换 C/D 窗
                            isDetailExpanded.value = expanded
                        },
                        // 收起态答案摘要："答案:" + 第 1 题纯答案，≤7 字符全显，>7 截断加省略号
                        // （选择题短答案如 ABCD 可直接阅读；填空/问答题为长答案预留省略）
                        summaryText = buildCollapsedAnswerSummary(
                            copyTexts = viewModel.paginatedCopyTexts.value.ifEmpty {
                                viewModel.recordingCopyTexts.value
                            },
                            fallbackAnswer = viewModel.answerText.value,
                            prefix = getString(R.string.float_answer_title) + ":"
                        )
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
        var attachCOk = false
        try {
            windowMgr.attachC(cComposeView, cParams)
            attachCOk = windowMgr.cView != null
            AppLog.d("FWS", "ensureWindowC: attachC done, cView after=${windowMgr.cView != null} cParams=${cParams.width}x${cParams.height} x=${cParams.x} y=${cParams.y}")
        } catch (e: Exception) {
            AppLog.e("FWS", "attachC failed", e)
        }

        // P4: attach 失败时不要赋值 windowCView / 不要 syncC
        if (!attachCOk) {
            AppLog.e("FWS", "ensureWindowC: attachC failed, window not created"); return
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
        // 保留上次实测高度：收起/展开过渡与重新创建时窗口初始高度直接贴合内容，
        // 避免窗口大于内容产生透明触摸区（模式切换时由 ensureWindowC 重新设定）
        pendingCFadeIn = false
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
            // M3
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
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
                        recordingProcessedCount = viewModel.recordingProcessedCount.value,
                        recordingCaptureCount = viewModel.recordingCaptureCount.value,
                        // Fix C: 多图模式答案卡 header 总数用进站截图数（录制模式用 recordingCaptureCount）
                        imageCaptureCount = viewModel.imageCaptureCount.value,
                        isImageResult = viewModel.isImageResultActive.value,
                        onCopyRecordingAnswer = { text ->
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, text)
                        },
                        onCloseAnswer = { closeAnswer() },
                        onMeasuredHeight = { h ->
                            AppLog.d("FWS", "WindowD measuredHeight=$h")
                            measuredWindowDHeight = h
                            // 过渡动画进行中:窗口尺寸/透明度由动画帧接管,
                            // 跳过重新定位——否则窗口缩小触发重测→重置全高→再缩小,形成闪烁反馈回路
                            if (windowTransitionJob?.isActive != true) {
                                positionWindowD()
                            }
                        },
                        cardAlpha = settings.floatCardAlpha.value,
                        // 折叠功能：收起 D 窗（渐出动画），恢复紧凑 C 窗
                        onCollapse = { collapseWindowD() }
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
        // S6: ensureWindowD 的 attachD 加异常保护——addView 失败时不崩溃（原代码无 try-catch）
        try {
            windowMgr.attachD(dComposeView, dParams)
        } catch (e: Exception) {
            AppLog.e("FWS", "attachD failed", e)
            windowDView = null
            measuredWindowDHeight = null
            return
        }
        windowDView = dComposeView
        measuredWindowDHeight = null // reset before first measure
        // 展开过渡:首次测量后从紧凑高度(与 C 窗一致)伸展到内容高度并淡入
        pendingExpandAnim = true
        positionWindowD()
        AppLog.d("FWS", "ensureWindowD: COMPLETE")
    }

    /** Detaches and disposes Window D. */
    private fun removeWindowD() {
        windowMgr.detachD()
        windowDView?.disposeComposition()
        windowDView = null
        measuredWindowDHeight = null
        pendingExpandAnim = false
        isDetailExpanded.value = false
    }

    /**
     * 收起 D 窗:先播高度收缩动画(顶部/底部固定,收缩到紧凑摘要高度)并同步淡出,
     * 动画结束后移除 D 窗、恢复紧凑 C 窗——两者位置/尺寸对齐,实现丝滑衔接。
     * 动画被中断(如拖拽、状态变化)时由 onDone 立即完成窗口替换,降级为瞬间切换。
     */
    private fun collapseWindowD() {
        if (windowMgr.dView == null) {
            isDetailExpanded.value = false
            return
        }
        isDetailExpanded.value = false
        val dView = windowMgr.dView ?: return
        val dParams = windowMgr.dParams ?: return
        val fromH = dParams.height
        val fromY = dParams.y
        // 目标高度 = C 窗上次实测高度(收起态窗口形状),未知时用紧凑初始值
        val density = resources.displayMetrics.density
        val toH = (measuredWindowCHeight ?: (FWDims.cardCompactInitHeight.value * density).toFloat())
            .toInt().coerceAtLeast(1)
        // D 在 A 下方:顶部固定、底部上移(从下往上收起);D 在 A 上方:底部固定
        val keepTop = windowMgr.aParams?.let { fromY > it.y } ?: true
        val anchorY = if (keepTop) fromY else fromY + fromH
        windowTransitionJob?.cancel()
        windowTransitionJob = windowMgr.animateWindowHeight(
            scope = serviceScope,
            view = dView,
            fromH = fromH,
            toH = toH,
            keepTop = keepTop,
            anchorY = anchorY,
            fromAlpha = dParams.alpha,
            toAlpha = 0f,
            durationMs = 160L
        ) {
            // 服务销毁中(scope.cancel 触发):跳过窗口操作,避免销毁后创建窗口
            if (destroyed) return@animateWindowHeight
            windowTransitionJob = null
            if (viewModel.showAnswer.value || viewModel.statusMessage.value != null) {
                // 收起完成:移除 D 后以透明状态创建 C 窗,
                // 等 C 窗测量收缩到实际高度后再淡入,避免"出现→收缩"跳变闪烁
                pendingCFadeIn = true
                removeWindowD()
                if (windowMgr.cView == null) ensureWindowC()
                windowMgr.setAlpha(windowMgr.cView, 0f)
            } else {
                syncWindows(viewModel.showAnswer.value, viewModel.statusMessage.value, isDetailExpanded.value)
            }
        }
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
        val gapPx = (8 * density).toInt()
        val spaceBelow = screenH.toInt() - (aParams.y + aParams.height + gapPx)
        val spaceAbove = aParams.y - gapPx
        val maxH = maxOf(spaceBelow, spaceAbove, 1)
        // H1 修复：D 窗口高度优先用内容实测高度（onMeasuredHeight 上报，最高 cardMaxHeight=560dp）；
        //     未测量时用最大可用空间让 Compose 内容完整布局（若给 240dp 小窗，内容会被压缩且上报压缩值，永远无法扩展）
        //     任何情况下不超过上下可用空间，防止窗口盖住 A
        val dH = (measuredWindowDHeight ?: (FWDims.cardMaxHeight.value * density).toFloat())
            .toInt().coerceIn(1, maxH)

        // Center D horizontally relative to A
        val dX = aParams.x + (aParams.width - dW) / 2

        // Place D below A if there's room, otherwise above A
        val dY = if (dH > 0 && spaceBelow >= dH) {
            aParams.y + aParams.height + gapPx
        } else {
            (aParams.y - dH - gapPx).coerceAtLeast(0)
        }

        AppLog.d("FWS", "positionWindowD: dX=$dX dY=$dY dSize=${dW}x${dH} below=${spaceBelow >= dH}")

        // 展开过渡动画:首次测量后从紧凑高度(与 C 窗一致)伸展到内容高度并淡入
        if (pendingExpandAnim && measuredWindowDHeight != null) {
            pendingExpandAnim = false
            val startH = (measuredWindowCHeight ?: (FWDims.cardCompactInitHeight.value * density).toFloat())
                .toInt().coerceIn(1, dH)
            // D 在 A 下方:顶部固定、向下伸展;在上方:底部固定、向上伸展
            val keepTop = dY > aParams.y
            val anchorY = if (keepTop) dY else dY + dH
            windowMgr.updateLayoutD(
                windowX = dX.coerceIn(0, maxOf(0, screenW.toInt() - dW)),
                windowY = (if (keepTop) anchorY else anchorY - startH)
                    .coerceIn(0, maxOf(0, screenH.toInt() - startH)),
                width = dW,
                height = startH,
                alpha = 0f,
                screenW = screenW,
                screenH = screenH
            )
            windowTransitionJob?.cancel()
            windowTransitionJob = windowMgr.animateWindowHeight(
                scope = serviceScope,
                view = windowMgr.dView,
                fromH = startH,
                toH = dH,
                keepTop = keepTop,
                anchorY = anchorY,
                fromAlpha = 0f,
                toAlpha = if (settings.stealthMode.value) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA,
                durationMs = 160L
            ) {
                // 服务销毁中(scope.cancel 触发):跳过窗口操作,避免销毁后创建窗口
                if (destroyed) return@animateWindowHeight
                windowTransitionJob = null
                syncWindows(viewModel.showAnswer.value, viewModel.statusMessage.value, isDetailExpanded.value)
            }
            return
        }

        windowMgr.updateLayoutD(
            windowX = dX.coerceIn(0, maxOf(0, screenW.toInt() - dW)),
            windowY = dY.coerceIn(0, maxOf(0, screenH.toInt() - dH)),
            width = dW,
            // 窗口高度保持 WRAP_CONTENT：由系统按内容自动包裹，避免固定高度产生透明触摸区
            height = WindowManager.LayoutParams.WRAP_CONTENT,
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
        val y = viewModel.floatOffsetY.value.toInt().coerceIn(0, maxOf(0, screenH.toInt() - aSize))

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
        // S9: 统一使用 realMetrics（与 syncC/positionWindowD/updateWindowAPosition 一致），
        //     避免 displayMetrics（不含系统栏）与 A 窗口 realMetrics 坐标基准不一致导致 B 错位
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(realMetrics)
        val screenW = realMetrics.widthPixels.toFloat()
        val screenH = realMetrics.heightPixels.toFloat()
        val density = realMetrics.density
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
        // 测量前窗口保持紧凑高度：ensureWindowC 已按内容模式设定初始高度
        // （答案单行=紧凑值，状态消息=上限），onGloballyPositioned 上报真实高度后收缩
        val defaultH = (FWDims.cardCompactInitHeight.value * density).toInt()
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
            // 窗口高度保持 WRAP_CONTENT：由系统按内容自动包裹，避免固定高度产生透明触摸区
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            alpha = Constants.VISIBLE_ALPHA,
            screenW = screenW,
            screenH = screenH
        )
    }

    /** Handles drag gesture on Window A — updates offset and repositions all windows. */
    private fun dragWindowBy(deltaX: Float, deltaY: Float) {
        // S8: 拖拽开始即取消吸附动画，避免动画 onFrame 与拖拽双写窗口坐标
        viewModel.windowXAnimJob?.cancel()
        viewModel.windowXAnimJob = null
        // 折叠过渡动画同样在拖拽时终止：取消后 onDone 立即完成窗口替换（降级为瞬间切换）
        windowTransitionJob?.cancel()
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(realMetrics)
        val screenW = realMetrics.widthPixels.toFloat()
        val screenH = realMetrics.heightPixels.toFloat()
        val aSize = getAWindowSize().toFloat()
        // M13: 用窗口半宽 aSize/2（含 padding）而非 buttonHalf，统一 floatOffsetX 与窗口中心映射
        val halfSize = aSize / 2f

        viewModel.floatOffsetX.value = (viewModel.floatOffsetX.value + deltaX)
            .coerceIn(halfSize, screenW - halfSize)
        viewModel.floatOffsetY.value = (viewModel.floatOffsetY.value + deltaY)
            .coerceIn(0f, maxOf(0f, screenH - aSize))

        // During drag: position window at actual finger position (continuous, not snapped)
        val dragX = (viewModel.floatOffsetX.value - halfSize).toInt()
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
            // M16: 动画帧节流——B/C/D 跟随同步限频 20Hz（每 50ms 一次），减少 updateViewLayout 压力
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastAnimSyncMs >= 50L) {
                lastAnimSyncMs = now
                syncB()
                syncC()
                positionWindowD()
            }
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
        val recordingActive = viewModel.isRecording.value || viewModel.isProcessingRecording.value
        viewModel.showAnswer.value = false
        viewModel.answerText.value = null
        viewModel.paginatedAnswers.value = emptyList()
        viewModel.paginatedCopyTexts.value = emptyList()
        viewModel.floatingStatus.value = FloatingStatus.Idle
        viewModel.statusMessage.value = null
        // 防呆：录制进行中或结果处理中，只隐藏窗口、保留数据与状态；
        // 录制中删窗 → 结束录制时照常显示；处理中删窗 → 任务完成时自动重新弹出
        if (recordingActive) {
            AppLog.d("FWS", "closeAnswer: recording active/processing, preserving data")
            viewModel.hasContent = false
            return
        }
        viewModel.recordingAnswers.value = emptyList()
        // 补齐录制/图片相关复位，避免关闭后状态残留（L1）
        viewModel.recordingCopyTexts.value = emptyList()
        viewModel.recordingCaptureCount.value = 0
        viewModel.recordingSkippedCount.value = 0
        viewModel.recordingFailedCount.value = 0
        viewModel.recordingProcessedCount.value = 0
        viewModel.isProcessingRecording.value = false
        // P0-5: 关闭答案 → 复位多图计数与结果窗口，避免残留值污染后续普通答案 header
        viewModel.imageCollectCount.value = 0
        viewModel.imageCaptureCount.value = 0
        viewModel.isImageResultActive.value = false
        viewModel.hasContent = false
    }

    private fun closeStatus() {
        viewModel.currentFetchJob?.cancel()
        viewModel.currentFetchJob = null
        val recordingActive = viewModel.isRecording.value || viewModel.isProcessingRecording.value
        // 防呆：录制进行中或结果处理中，不取消录制任务也不清空已收集答案，
        // 避免误触关闭导致全部答案丢失；录制中删窗 → 结束录制时照常显示
        if (recordingActive) {
            AppLog.d("FWS", "closeStatus: recording active/processing, preserving data")
            viewModel.showAnswer.value = false
            viewModel.answerText.value = null
            viewModel.paginatedAnswers.value = emptyList()
            viewModel.paginatedCopyTexts.value = emptyList()
            viewModel.floatingStatus.value = FloatingStatus.Idle
            viewModel.statusMessage.value = null
            viewModel.hasContent = false
            return
        }
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
        // 补齐录制计数复位，避免关闭后残留（L1）
        viewModel.recordingCopyTexts.value = emptyList()
        viewModel.recordingCaptureCount.value = 0
        viewModel.recordingSkippedCount.value = 0
        viewModel.recordingFailedCount.value = 0
        viewModel.recordingProcessedCount.value = 0
        // P0-5: 关闭状态 → 复位多图计数与结果窗口，避免残留值污染后续普通答案 header
        viewModel.imageCollectCount.value = 0
        viewModel.imageCaptureCount.value = 0
        viewModel.isImageResultActive.value = false
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    override fun onDestroy() {
        destroyed = true
        isRunning = false
        // M2: 清理链整体包裹——任何一步异常都不允许中断后续清理
        try {
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
        } catch (e: Exception) {
            AppLog.e("FWS", "onDestroy cleanup failed", e)
        } finally {
            // 无论清理是否成功，ViewModel 与超类都必须释放
            _viewModelStore.clear()
            super.onDestroy()
        }
    }

    /**
     * M14: 横竖屏/配置变更适配。
     * 旋转后屏幕尺寸变化，绝对像素偏移会失效：
     *   - 将 floatOffsetX/Y 按旧屏幕尺寸的比例映射到新屏幕（保留相对位置）；
     *   - 重新定位全部窗口（A/B/C/D）。
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (destroyed || !::windowMgr.isInitialized) return
        val wm2 = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        wm2.defaultDisplay.getRealMetrics(metrics)
        val newW = metrics.widthPixels
        val newH = metrics.heightPixels
        val oldW = lastScreenSize.x
        val oldH = lastScreenSize.y
        if (oldW <= 0 || oldH <= 0 || (oldW == newW && oldH == newH)) {
            lastScreenSize.set(newW, newH)
            return
        }

        // 按比例映射偏移：floatOffsetX 为窗口中心 x（aSize/2 基准），floatOffsetY 为窗口顶部 y
        val aSize = getAWindowSize().toFloat()
        val halfSize = aSize / 2f
        val ratioX = newW.toFloat() / oldW.toFloat()
        val ratioY = newH.toFloat() / oldH.toFloat()
        val newOffsetX = (viewModel.floatOffsetX.value * ratioX).coerceIn(halfSize, newW - halfSize)
        val newOffsetY = (viewModel.floatOffsetY.value * ratioY).coerceIn(0f, maxOf(0f, newH - aSize))
        viewModel.floatOffsetX.value = newOffsetX
        viewModel.floatOffsetY.value = newOffsetY
        lastScreenSize.set(newW, newH)

        AppLog.d("FWS", "onConfigurationChanged: ${oldW}x$oldH -> ${newW}x$newH, offset=$newOffsetX,$newOffsetY")
        // 全窗口重排：A 定位 + B/C/D 跟随
        updateWindowAPosition()
        windowMgr.setAllAlpha(if (settings.stealthMode.value) Constants.STEALTH_ALPHA else Constants.VISIBLE_ALPHA)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
