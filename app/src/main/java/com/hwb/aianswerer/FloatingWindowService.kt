package com.hwb.aianswerer

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.Gravity
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.api.TavilyClient
import com.hwb.aianswerer.api.search.WebSearchClientFactory
import com.hwb.aianswerer.api.search.WebSearchResult
import com.hwb.aianswerer.api.vision.VisionProviderFactory
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.models.formatAnswerWithConfig
import com.hwb.aianswerer.ui.components.FloatingWindowContent
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ClipboardUtil
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout


/**
 * 悬浮窗服务 — 答题模式的核心运行时。
 *
 * 生命周期：
 *   1. MainActivity 请求权限后通过 startForegroundService 启动，
 *      在 onStartCommand 中接收 MediaProjection intent 数据和答题设置。
 *   2. onCreate 中创建悬浮窗并注册广播接收器，通过广播与 ConfirmTextActivity、
 *      ImageCropActivity 通信（而非 startActivityForResult，因为这些 Activity
 *      是 NEW_TASK 方式启动的，无法返回 result）。
 *   3. onDestroy 时释放 MediaProjection、取消协程、移除悬浮窗。
 *
 * Compose 集成：
 *   Service 主动实现 LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner，
 *   使 ComposeView 能正常工作在 Service 上下文中（setViewTree* 链）。
 */

class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val TAG = "FloatingWindowService"

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val ACTION_CROP_RESULT = "com.hwb.aianswerer.ACTION_CROP_RESULT"
        const val ACTION_STOP = "com.hwb.aianswerer.ACTION_STOP"
        const val EXTRA_IMAGE_PATH = "image_path"
        
        // Compose重组等待时间（毫秒）
        // 确保UI状态更新完成后再进行截图
        private const val COMPOSE_RECOMPOSITION_DELAY_MS = 50L
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    @Volatile private var destroyed = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val textRecognitionManager = TextRecognitionManager.getInstance()

    private var answerText = mutableStateOf<String?>(null)
    private var showAnswer = mutableStateOf(false)
    private var statusMessage = mutableStateOf<String?>(null)
    private var floatingStatus = mutableStateOf(FloatingStatus.Idle)

    // 悬浮按钮透明度状态（响应式，支持长按垂直滑动实时调节）
    private var floatButtonAlpha = mutableStateOf(AppConfig.getFloatButtonAlpha())

    // 悬浮窗高度与状态管理
    private var isArcExpanded = false
    private var hasContent = false
    private var currentWindowHeightPx = 0f
    private var captureInProgress = false  // 截图期间屏蔽 auto-height 回调

    // 窗口位置动画（消除左右切换时的割裂感）
    private var displayWindowX = mutableFloatStateOf(0f)
    private var windowXAnimJob: Job? = null

    // 悬浮窗内部偏移量（按钮中心的屏幕坐标）
    private var floatOffsetX = mutableFloatStateOf(0f)
    private var floatOffsetY = mutableFloatStateOf(200f)

    // 使用线程安全的Set，因为onStartCommand可能在不同线程调用
    private var questionTypes: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var cropMode = AppConfig.CROP_MODE_FULL
    // savedCropRect: 单次模式(once)首次裁剪后缓存，后续截图直接复用
    // savedCropRectEach: 每次模式(each)缓存上一次坐标，作为裁剪 UI 的初始位置
    // 使用@Volatile确保在主线程和广播接收器之间的可见性
    @Volatile
    private var savedCropRect: CropRect? = null
    @Volatile
    private var savedCropRectEach: CropRect? = null

    // 快捷开关状态：有配置模型则默认开，无则关
    private var visionEnabled = mutableStateOf(AppConfig.isVisionEnabled())
    private var searchEnabled = mutableStateOf(
        com.hwb.aianswerer.providers.WebSearchStorage.getEnabledProviders().isNotEmpty()
    )
    private var reasoningEnabled = mutableStateOf(AppConfig.getReasoningEffort() != null)

    // 当前进行中的网络请求 Job，用于在 onDestroy 时取消
    private var currentFetchJob: Job? = null
    // 用于防止并发请求的互斥锁
    private val fetchMutex = kotlinx.coroutines.sync.Mutex()

    // ── 录制模式 ──
    private var isRecording = mutableStateOf(false)
    private var recordingCaptureCount = mutableStateOf(0)
    private var isProcessingRecording = mutableStateOf(false)
    private var recordingProcessedCount = mutableStateOf(0)
    private var recordingAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private var recordingCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private var recordingSkippedCount = mutableStateOf(0)

    // ── 翻页答案（普通模式）──
    // 所有答题结果（单题/多题）统一拆分为逐题列表，UI 层一页一题翻页显示
    private var paginatedAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private var paginatedCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private val recordingJobs = java.util.concurrent.CopyOnWriteArrayList<Job>()
    private val recordingActiveCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var recordingTextHashes = mutableSetOf<String>()
    private var recordingSemaphore: kotlinx.coroutines.sync.Semaphore? = null
    private var vlmSemaphore: kotlinx.coroutines.sync.Semaphore? = null
    private var recordingTotalQuestions = 0  // 实际题目总数（含VLM多题）

    // 单次截图计数器（用于日志追踪）
    private var captureCounter = 0

    // 以下三个组件是ComposeView在Service中运行的必要条件
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // 使用应用本地广播（setPackage）与 ConfirmTextActivity、ImageCropActivity 通信。
    // 不使用 startActivityForResult 是因为这些 Activity 以 NEW_TASK 标志启动，
    // 无法通过常规方式返回 result。
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
                        fetchAnswer(questionText)
                    }
                }

                ACTION_CROP_RESULT -> {
                    val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
                    val topLeftX = intent.getFloatExtra(ImageCropActivity.EXTRA_TOP_LEFT_X, 0f)
                    val topLeftY = intent.getFloatExtra(ImageCropActivity.EXTRA_TOP_LEFT_Y, 0f)
                    val bottomRightX =
                        intent.getFloatExtra(ImageCropActivity.EXTRA_BOTTOM_RIGHT_X, 0f)
                    val bottomRightY =
                        intent.getFloatExtra(ImageCropActivity.EXTRA_BOTTOM_RIGHT_Y, 0f)

                    if (imagePath != null) {
                        val cropRect = CropRect(
                            topLeft = android.graphics.PointF(topLeftX, topLeftY),
                            bottomRight = android.graphics.PointF(bottomRightX, bottomRightY)
                        )

                        // 根据模式保存裁剪坐标
                        when (cropMode) {
                            AppConfig.CROP_MODE_ONCE -> {
                                savedCropRect = cropRect
                            }

                            AppConfig.CROP_MODE_EACH -> {
                                savedCropRectEach = cropRect
                            }
                        }

                        // 录制模式走录制路径
                        if (isRecording.value) {
                            handleRecordingCroppedImage(imagePath, cropRect)
                        } else {
                            handleCroppedImage(imagePath, cropRect)
                        }
                        }
                    }

                    Constants.ACTION_REFRESH_SETTINGS -> {
                        refreshSettingsFromApp()
                    }

                    else -> {
                    // 忽略未知广播
                }
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(
            if (newBase != null) com.hwb.aianswerer.utils.LanguageUtil.attachBaseContext(newBase)
            else newBase
        )
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // 初始化SavedStateRegistry（必须在生命周期状态变更前调用）
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)

        // 注册广播接收器
        val filter = IntentFilter(Constants.ACTION_SHOW_ANSWER)
        filter.addAction(Constants.ACTION_REQUEST_ANSWER)
        filter.addAction(ACTION_CROP_RESULT)
        filter.addAction(Constants.ACTION_REFRESH_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(answerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(answerReceiver, filter)
        }

        createNotificationChannel()
        ensureNotificationPermission()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        showFloatingWindow()

        // 快速推进生命周期到RESUMED状态，使ComposeView能够正常组合和渲染
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏停止按钮
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // intent 包含了 MainActivity 启动时传入的两类数据：
        //   1) MediaProjection 的 resultCode + data（从 onActivityResult 获取）
        //   2) 答题设置（题型、范围、裁剪模式）
        // 新答题会话启动时会清空 savedCropRect，确保旧裁剪坐标不会残留。
        intent?.let {
            if (it.hasExtra("resultCode") && it.hasExtra("data")) {
                val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = it.getParcelableExtra<Intent>("data")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    screenCaptureManager?.initMediaProjection(resultCode, data)
                }
            }

            // 读取答题设置
            if (it.hasExtra("questionTypes")) {
                val typesList = it.getStringArrayListExtra("questionTypes")
                if (typesList != null) {
                    questionTypes = typesList.toMutableSet()
                }
            }

            if (it.hasExtra("cropMode")) {
                cropMode = it.getStringExtra("cropMode")
                    ?: AppConfig.CROP_MODE_FULL
            }

            // 新答题会话开始，清除上次保存的裁剪坐标
            savedCropRect = null
            savedCropRectEach = null
        }
        // START_NOT_STICKY: Service被系统杀死后不自动重建
        // 因为MediaProjection权限数据随进程死亡失效，重启会成为僵尸服务
        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val density = metrics.density
        val buttonSizePx = AppConfig.getFloatButtonSize() * density
        val buttonHalf = buttonSizePx / 2f
        // 初始化窗口高度为内容显示基准高度
        currentWindowHeightPx = 200 * density

        // 初始位置：右侧贴边，30% 高度
        floatOffsetX.value = screenW - buttonHalf
        floatOffsetY.value = screenH * 0.30f

        fun isLeftSide() = floatOffsetX.value < screenW / 2f

        // 窗口宽度固定为卡片宽度，仅覆盖屏幕窄带，其余区域自然穿透
        val windowWidthPx = 360 * density

        fun windowX(): Int {
            return if (isLeftSide()) {
                0
            } else {
                (screenW - windowWidthPx).toInt().coerceAtLeast(0)
            }
        }

        val params = WindowManager.LayoutParams(
            windowWidthPx.toInt(),
            currentWindowHeightPx.toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or if (AppConfig.isStealthModeEnabled()) WindowManager.LayoutParams.FLAG_SECURE else 0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowX()
            y = floatOffsetY.value.toInt().coerceIn(0, screenH.toInt() - currentWindowHeightPx.toInt())
            if (AppConfig.isStealthModeEnabled()) alpha = 0.99f
        }

        floatingView = ComposeView(this).apply {
            // Prevent black flicker during system UI transitions
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            if (AppConfig.isStealthModeEnabled()) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            setContent {
                AIAnswererTheme {
                    FloatingWindowContent(
                        answerText = answerText.value,
                        showAnswer = showAnswer.value,
                        statusMessage = statusMessage.value,
                        buttonSize = AppConfig.getFloatButtonSize(),
                        buttonAlpha = floatButtonAlpha.value,
                        cardAlpha = AppConfig.getFloatCardAlpha(),
                        isLeftSide = isLeftSide(),
                        floatingStatus = floatingStatus.value,
                        onCaptureClick = { handleCapture() },
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
                            recordingJobs.forEach { it.cancel() }
                            recordingJobs.clear()
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
                            AppLog.d("FWS", "MOVE | d=(${deltaX.toInt()},${deltaY.toInt()}) off=(${prevX.toInt()}→${floatOffsetX.value.toInt()}, ${prevY.toInt()}→${floatOffsetY.value.toInt()}) winX=${windowX()} winH=${currentWindowHeightPx.toInt()}")
                            animateWindowX(windowX().toFloat(), false)
                            updateWindowPosition()
                        },
                        onDragEnd = { leftSide ->
                            AppLog.d("FWS", "DRAG_END | leftSide=$leftSide offX=${floatOffsetX.value.toInt()} winX=${windowX()}")
                            floatOffsetX.value = if (leftSide) buttonHalf
                                                 else screenW - buttonHalf
                            animateWindowX(windowX().toFloat(), true)
                        },
                        visionEnabled = visionEnabled.value,
                        searchEnabled = searchEnabled.value,
                        reasoningEnabled = reasoningEnabled.value,
                        onVisionToggle = {
                            visionEnabled.value = !visionEnabled.value
                            AppConfig.saveVisionEnabled(visionEnabled.value)
                            Toast.makeText(this@FloatingWindowService,
                                if (visionEnabled.value) "VLM模式已启用" else "VLM模式已关闭",
                                Toast.LENGTH_SHORT).show()
                        },
                        onSearchToggle = {
                            searchEnabled.value = !searchEnabled.value
                            AppConfig.saveTavilyEnabled(searchEnabled.value)
                            Toast.makeText(this@FloatingWindowService,
                                if (searchEnabled.value) "联网搜索已启用" else "联网搜索已关闭",
                                Toast.LENGTH_SHORT).show()
                        },
                        onReasoningToggle = {
                            reasoningEnabled.value = !reasoningEnabled.value
                            AppConfig.saveReasoningEffort(reasoningEnabled.value)
                            Toast.makeText(this@FloatingWindowService,
                                if (reasoningEnabled.value) "深度思考已启用" else "深度思考已关闭",
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
                            AppLog.d("FWS", "ARC | expanded=$expanded offY=${floatOffsetY.value.toInt()}")
                            // No Y compensation needed: outer FloatingWindowContent places
                            // quick buttons alongside the pill (horizontal), not above it.
                            // Just update height if needed.
                            updateFloatingWindowHeight()
                        },
                        onContentVisibilityChanged = { visible ->
                            AppLog.d("FWS", "CONTENT_VIS | visible=$visible was=$hasContent")
                            hasContent = visible
                            updateFloatingWindowHeight()
                        }
                    )
                }
            }
        }

        displayWindowX.floatValue = windowX().toFloat()
        windowManager.addView(floatingView, params)
    }

    /**
     * 平滑动画窗口 X 位置。
     * @param targetX 目标窗口 X（px）
     * @param animated true=弹簧动画，false=立即到位（拖拽中使用）
     */
    private fun animateWindowX(targetX: Float, animated: Boolean) {
        windowXAnimJob?.cancel()
        if (!animated) {
            displayWindowX.floatValue = targetX
            return
        }
        windowXAnimJob = serviceScope.launch {
            val from = displayWindowX.floatValue
            val duration = 250L
            val start = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - start
                val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                // Ease-out cubic
                val eased = 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
                displayWindowX.floatValue = from + (targetX - from) * eased
                updateWindowPosition()
                if (fraction >= 1f) break
                delay(16)
            }
            windowXAnimJob = null
        }
    }

    /** 刷新窗口 LayoutParams */
    private fun updateWindowPosition() {
        if (destroyed) return
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        floatingView?.let { v ->
            val p = v.layoutParams as WindowManager.LayoutParams
            p.x = displayWindowX.floatValue.toInt().coerceIn(0, maxOf(0, screenW.toInt() - p.width))
            p.y = floatOffsetY.value.toInt().coerceIn(0, maxOf(0, screenH.toInt() - p.height))
            p.height = currentWindowHeightPx.toInt()
            try { windowManager.updateViewLayout(v, p) } catch (e: Exception) { AppLog.e("FWS", "updateLayout failed", e) }
        }
    }

    /**
     * 根据当前状态动态调整浮窗高度。
     *
     * 状态优先级：
     *   1. 弧形快捷按钮展开 → 内容基准高度 + 弧形预留区
     *   2. 有答案内容 → 内容基准高度（520dp 答案卡）
     *   3. 录制/处理中 → 进度高度（按钮 + 160dp，仅进度卡）
     *   4. 空闲状态 → 仅按钮高度（避免多余透明区拦截触摸）
     */
    private fun updateFloatingWindowHeight() {
        // 截图期间不响应 auto-height 回调，由截图流程手动控高
        if (captureInProgress) {
            AppLog.d("FWS", "HEIGHT | blocked by capture, cur=$currentWindowHeightPx")
            return
        }

        val metrics = resources.displayMetrics
        val density = metrics.density
        val buttonSizePx = AppConfig.getFloatButtonSize() * density
        val idleBaseHeightPx = buttonSizePx + 16 * density  // 按钮 + 8dp 上下边距
        val progressHeightPx = buttonSizePx + 400 * density  // 状态卡足够空间
        val contentBaseHeightPx = 520 * density  // 答案卡
        val screenH = metrics.heightPixels.toFloat()

        val branch = when {
            isRecording.value || isProcessingRecording.value -> "rec"
            hasContent && (showAnswer.value || recordingAnswers.value.isNotEmpty() || paginatedAnswers.value.isNotEmpty()) -> "content"
            hasContent -> "progress"
            else -> "idle"
        }
        val newHeight = (when (branch) {
            "rec" -> progressHeightPx
            "content" -> contentBaseHeightPx
            "progress" -> progressHeightPx
            else -> idleBaseHeightPx
        }).toInt()

        AppLog.d("FWS", "HEIGHT_CALC | branch=$branch hasContent=$hasContent btnDp=${(buttonSizePx/density).toInt()} newH=$newHeight curH=${currentWindowHeightPx.toInt()} rec=${isRecording.value}")

        if (newHeight.toFloat() != currentWindowHeightPx) {
            currentWindowHeightPx = newHeight.toFloat()
            AppLog.d("FWS", "HEIGHT | APPLY $newHeight")
            updateWindowPosition()
        }
    }

    /**
     * 使用用户选中的联网搜索源执行搜索，返回格式化后的上下文文本
     */
    private suspend fun performWebSearch(query: String, maxResults: Int = 2): String {
        if (!searchEnabled.value) return ""
        val providers = com.hwb.aianswerer.providers.WebSearchStorage.getEnabledProviders()
        if (providers.isEmpty()) return ""
        val selectedName = AppConfig.getWebSearchProvider()
        val selected = providers.find { it.name == selectedName } ?: providers.first()
        val provider = WebSearchClientFactory.create(selected)
        val results = provider.search(query, maxResults)
        if (results.isEmpty()) return ""
        return results.joinToString("\n") { "【${it.title}】${it.snippet}" }
    }

    /** Hide or show the floating window content during capture */
    private fun setWindowVisible(visible: Boolean) {
        floatingView?.let { v ->
            val p = v.layoutParams as WindowManager.LayoutParams
            p.alpha = if (visible) (if (AppConfig.isStealthModeEnabled()) 0.99f else 1f) else 0f
            try { windowManager.updateViewLayout(v, p) } catch (e: Exception) { AppLog.e("FWS", "updateLayout failed", e) }
        }
    }

    /** Re-read all app settings and apply to local state */
    private fun refreshSettingsFromApp() {
        visionEnabled.value = AppConfig.isVisionEnabled()
        searchEnabled.value = com.hwb.aianswerer.providers.WebSearchStorage.getEnabledProviders().isNotEmpty()
        reasoningEnabled.value = AppConfig.getReasoningEffort() != null
        floatButtonAlpha.value = AppConfig.getFloatButtonAlpha()
        AppLog.d("FWS", "SETTINGS | refreshed from app")
    }

    private fun handleCapture() {
        captureCounter++
        AppLog.enter("FWS", "handleCapture recording=${isRecording.value}")
        AppLog.i("FWS", "Capture #$captureCounter, mode=$cropMode")
        AppLog.d("FWS", "CAPTURE | clicked, status=${floatingStatus.value} ready=${screenCaptureManager?.isReady} rec=${isRecording.value}")
        // ── 录制模式分支（必须在 isBusy 检查之前）──
        if (isRecording.value) {
            // 并发数限制：活跃处理任务数不能超过设置的最大并发数
            val maxConcurrency = AppConfig.getMaxConcurrency()
            val activeJobs = recordingActiveCount.get()
            AppLog.d("REC", "并发检查: activeJobs=$activeJobs, maxConcurrency=$maxConcurrency")
            if (activeJobs >= maxConcurrency) {
                statusMessage.value = getString(R.string.recording_concurrency_limit, activeJobs, maxConcurrency)
                floatingStatus.value = FloatingStatus.Error
                Toast.makeText(this, getString(R.string.recording_concurrency_limit, activeJobs, maxConcurrency), Toast.LENGTH_SHORT).show()
                return
            }
            recordingCaptureCount.value++
            captureInProgress = true
            showAnswer.value = false
            serviceScope.launch {
                // 等待上一次状态清除
                delay(COMPOSE_RECOMPOSITION_DELAY_MS)
                // 缩窗到按钮高度 + FLAG_SECURE：按钮可见，卡被裁剪，截图仅按钮处有小遮罩
                val savedH = currentWindowHeightPx
                val idleH = AppConfig.getFloatButtonSize() * resources.displayMetrics.density + 16 * resources.displayMetrics.density
                currentWindowHeightPx = idleH
                updateWindowPosition()
                val wasStealth = AppConfig.isStealthModeEnabled()
                floatingView?.let { v ->
                    val p = v.layoutParams as WindowManager.LayoutParams
                    p.flags = p.flags or WindowManager.LayoutParams.FLAG_SECURE
                    try { windowManager.updateViewLayout(v, p) } catch (e: Exception) { AppLog.e("FWS", "flag SECURE failed", e) }
                }
                delay(33)  // 2 frames @ 60fps, FLAG_SECURE 生效足够
                val bitmap = screenCaptureManager?.captureScreen()
                // 去 FLAG_SECURE 合并到 updateFloatingWindowHeight 的同一次 updateViewLayout
                if (!wasStealth) {
                    floatingView?.let { v ->
                        (v.layoutParams as WindowManager.LayoutParams).flags =
                            (v.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    }
                }
                captureInProgress = false
                hasContent = true
                updateFloatingWindowHeight()  // 一次 updateViewLayout：去旗 + 改高

                if (bitmap == null) {
                    showErrorMessage(getString(R.string.status_capture_failed))
                    return@launch
                }
                when (cropMode) {
                    AppConfig.CROP_MODE_FULL -> recordingProcessBitmap(bitmap)
                    AppConfig.CROP_MODE_EACH -> {
                        savedCropRectEach?.let { rect ->
                            val cropped = try { ImageCropUtil.cropBitmap(bitmap, rect) }
                            catch (e: Exception) { bitmap.recycle(); throw e }
                            bitmap.recycle()
                            recordingProcessBitmap(cropped)
                        } ?: launchCropActivity(bitmap, null)
                    }
                    AppConfig.CROP_MODE_ONCE -> {
                        savedCropRect?.let { rect ->
                            val cropped = try { ImageCropUtil.cropBitmap(bitmap, rect) }
                            catch (e: Exception) { bitmap.recycle(); throw e }
                            bitmap.recycle()
                            recordingProcessBitmap(cropped)
                        } ?: launchCropActivity(bitmap, null)
                    }
                }
                floatingStatus.value = FloatingStatus.Idle
                delay(50)  // 等窗口高度变化渲染完，再更新录制计数，避免同帧跳动
                statusMessage.value = getString(R.string.recording_indicator, recordingCaptureCount.value)
            }
            return
        }

        // 忙时点击 → 取消当前请求，回到空闲，不开始新截图
        val isBusy = floatingStatus.value != FloatingStatus.Idle &&
                floatingStatus.value != FloatingStatus.Success &&
                floatingStatus.value != FloatingStatus.Error
        if (isBusy) {
            currentFetchJob?.cancel()
            currentFetchJob = null
            showAnswer.value = false
            answerText.value = null
            paginatedAnswers.value = emptyList()
            paginatedCopyTexts.value = emptyList()
            floatingStatus.value = FloatingStatus.Idle
            statusMessage.value = null
            return
        }

        currentFetchJob?.cancel()
        currentFetchJob = serviceScope.launch {
            try {
                showAnswer.value = false
                answerText.value = null
                recordingAnswers.value = emptyList()
                paginatedAnswers.value = emptyList()
                paginatedCopyTexts.value = emptyList()
                floatingStatus.value = FloatingStatus.Idle

                // 检查是否使用无障碍屏幕读取模式
                if (AppConfig.isAccessibilityCaptureMode()) {
                    handleAccessibilityCapture()
                    return@launch
                }

                // 检查 MediaProjection 截图权限是否已授权（在协程内，可用 showErrorMessage）
                if (screenCaptureManager?.isReady != true) {
                    showErrorMessage("截图权限未授权，请在主页重新点击\"进入答题模式\"")
                    return@launch
                }

                // === 截图模式 ===
                AppLog.d("FWS", "CAPTURE | starting captureScreen...")
                captureInProgress = true  // 屏蔽 auto-height，截图流程手动控高

                // 等待 Compose 重组完成，确保上一次的答案卡片已从屏幕上移除
                delay(COMPOSE_RECOMPOSITION_DELAY_MS)

                // 缩窗到按钮高度（卡被裁剪），加 FLAG_SECURE 掩掉按钮区域
                // 截图中不显示状态卡（idleH 太小装不下），截图只需 ~100ms 用户无感
                val idleH = AppConfig.getFloatButtonSize() * resources.displayMetrics.density + 16 * resources.displayMetrics.density
                currentWindowHeightPx = idleH
                updateWindowPosition()

                val wasStealth = AppConfig.isStealthModeEnabled()
                floatingView?.let { v ->
                    val p = v.layoutParams as WindowManager.LayoutParams
                    p.flags = p.flags or WindowManager.LayoutParams.FLAG_SECURE
                    try { windowManager.updateViewLayout(v, p) } catch (e: Exception) { AppLog.e("FWS", "flag SECURE failed", e) }
                }
                delay(33)  // 2 frames @ 60fps, FLAG_SECURE 生效足够

                val bitmap = withTimeout(8_000L) {
                    screenCaptureManager?.captureScreen()
                }
                AppLog.d("FWS", "CAPTURE | captureScreen done, bitmap=${bitmap != null}")

                // 去 FLAG_SECURE（改内存 flags）+ 扩窗，合并为一次 updateViewLayout
                if (!wasStealth) {
                    floatingView?.let { v ->
                        (v.layoutParams as WindowManager.LayoutParams).flags =
                            (v.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    }
                }
                captureInProgress = false
                hasContent = true
                updateFloatingWindowHeight()  // 一次调用：去旗 + x/y/height 全部到位

                if (bitmap == null) {
                    showErrorMessage(getString(R.string.status_capture_failed))
                    return@launch
                }

                // 根据裁剪模式决定是否需要裁剪步骤：
                //   full  → 直接 OCR 全屏
                //   each  → 每次都启动裁剪 UI（可复用像素坐标）
                //   once  → 首次启动裁剪 UI，后续复用 savedCropRect（图片坐标）
                try {
                    when (cropMode) {
                        AppConfig.CROP_MODE_FULL -> {
                            // 全屏模式：直接识别
                            processBitmap(bitmap)
                        }

                        AppConfig.CROP_MODE_EACH -> {
                            // 部分识别：有上次坐标则复用，否则弹出框选
                            savedCropRectEach?.let { rect ->
                                val croppedBitmap = try {
                            ImageCropUtil.cropBitmap(bitmap, rect)
                        } catch (e: Exception) {
                            bitmap.recycle()
                            throw e
                        }
                        bitmap.recycle()
                        try {
                            processBitmap(croppedBitmap)
                        } finally {
                            if (!croppedBitmap.isRecycled) croppedBitmap.recycle()
                        }
                    } ?: launchCropActivity(bitmap, null)
                }

                AppConfig.CROP_MODE_ONCE -> {
                    savedCropRect?.let { rect ->
                        // 已有保存的坐标：直接裁剪
                        val croppedBitmap = try {
                            ImageCropUtil.cropBitmap(bitmap, rect)
                        } catch (e: Exception) {
                            // 裁剪失败时回收bitmap，避免内存泄漏
                            bitmap.recycle()
                            throw e
                        }
                        bitmap.recycle()
                        try {
                            processBitmap(croppedBitmap)
                        } finally {
                            if (!croppedBitmap.isRecycled) croppedBitmap.recycle()
                        }
                            } ?: run {
                                // 没有坐标：启动裁剪Activity
                                launchCropActivity(bitmap, null)
                            }
                        }
                        else -> {
                            AppLog.d("FWS", "CAPTURE | unknown cropMode=$cropMode, fallback to full")
                            processBitmap(bitmap)
                        }
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                floatingStatus.value = FloatingStatus.Idle
                statusMessage.value = null
                Toast.makeText(this@FloatingWindowService, "截图超时，请重试", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                floatingStatus.value = FloatingStatus.Idle
                statusMessage.value = null
                throw e
            } catch (e: Exception) {
                showErrorMessage(getString(R.string.status_operation_failed, e.message ?: ""))
            }
        }
    }

    /**
     * 无障碍模式采集：直接读取屏幕文本，VLM开启时截图分析
     */
    private suspend fun handleAccessibilityCapture() {
        floatingStatus.value = FloatingStatus.Recognizing
        statusMessage.value = getString(R.string.status_reading_screen)

        delay(COMPOSE_RECOMPOSITION_DELAY_MS)

        // Make floating window invisible to accessibility so service reads app beneath
        floatingView?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        delay(100)

        var screenText = ScreenReaderService.readScreenText()
        if (screenText.isNullOrBlank()) {
            delay(500)
            screenText = ScreenReaderService.readScreenText()
        }

        floatingView?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO

        if (screenText.isNullOrBlank()) {
            // Diagnose why reading failed
            val enabled = ScreenReaderService.isAccessibilityServiceEnabled(this)
            val msg = when {
                !enabled -> getString(R.string.status_screen_read_failed)
                !ScreenReaderService.isActive -> "无障碍服务已启用但未连接，请在系统设置中关闭后重新开启"
                else -> "无法读取屏幕内容，请确保当前页面有可见文字"
            }
            showErrorMessage(msg)
            return
        }

        // VLM 模式：截图 + 视觉分析
        if (visionEnabled.value && screenCaptureManager?.isReady == true) {
            floatingStatus.value = FloatingStatus.Capturing
            delay(COMPOSE_RECOMPOSITION_DELAY_MS)
            val bitmap = screenCaptureManager?.captureScreen()
            if (bitmap != null) {
                processBitmapWithVlm(bitmap)
            } else {
                // 截图失败，降级为纯文本
                statusMessage.value = getString(R.string.status_recognized)
                fetchAnswer(screenText)
            }
        } else {
            statusMessage.value = getString(R.string.status_recognized)
            fetchAnswer(screenText)
        }
    }

    /**
     * 启动裁剪Activity
     * @param bitmap 待裁剪的图片
     * @param previousCropRect 上一次的裁剪坐标（如果有的话）
     */
    private suspend fun launchCropActivity(
        bitmap: android.graphics.Bitmap,
        previousCropRect: CropRect?
    ) {
        try {
            // 保存bitmap到临时文件
            val imagePath =
                ImageCropUtil.saveBitmapToTempFile(bitmap, cacheDir)
            bitmap.recycle()

            // 启动裁剪Activity
            val intent = Intent(this, ImageCropActivity::class.java).apply {
                putExtra(ImageCropActivity.EXTRA_IMAGE_PATH, imagePath)
                // 如果有上次的裁剪坐标，则传递过去
                previousCropRect?.let {
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_TOP_LEFT_X, it.topLeft.x)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_TOP_LEFT_Y, it.topLeft.y)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_BOTTOM_RIGHT_X, it.bottomRight.x)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_BOTTOM_RIGHT_Y, it.bottomRight.y)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // 缩小浮窗避免遮挡裁剪页面（裁剪恢复时 expand 回去）
            hasContent = false
            updateFloatingWindowHeight()
            startActivity(intent)
            // 不设置 statusMessage 避免卡片撑开
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showErrorMessage(getString(R.string.status_crop_launch_failed, e.message ?: ""))
        }
    }

    /**
     * 处理裁剪后的图片
     */
    private fun handleCroppedImage(
        imagePath: String,
        cropRect: CropRect
    ) {
        serviceScope.launch {
            try {
                // 加载图片
                val bitmap = ImageCropUtil.loadBitmapFromFile(imagePath)

                // 裁剪图片
                val croppedBitmap =
                    ImageCropUtil.cropBitmap(bitmap, cropRect)
                bitmap.recycle()

                // 处理裁剪后的图片（OCR）
                processBitmap(croppedBitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showErrorMessage(getString(R.string.status_crop_failed, e.message ?: ""))
            } finally {
                ImageCropUtil.deleteTempFile(imagePath)
            }
        }
    }

    // ══════════════════════════════════════════════
    //  录制模式
    // ══════════════════════════════════════════════

    private fun normalizeForDedupe(text: String): String {
        return text.trim()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[,，。.、；;：:！!？?\"'`()（）\\[\\]【】]"), "")
            .lowercase()
    }

    private fun startRecording() {
        isRecording.value = true
        recordingCaptureCount.value = 0
        recordingProcessedCount.value = 0
        recordingSkippedCount.value = 0
        recordingAnswers.value = emptyList()
        recordingCopyTexts.value = emptyList()
        paginatedAnswers.value = emptyList()
        paginatedCopyTexts.value = emptyList()
        recordingTextHashes.clear()
        recordingJobs.clear()
        recordingActiveCount.set(0)
        recordingTotalQuestions = 0
        recordingSemaphore = kotlinx.coroutines.sync.Semaphore(AppConfig.getMaxConcurrency())
        vlmSemaphore = kotlinx.coroutines.sync.Semaphore(AppConfig.getMaxConcurrency())
        showAnswer.value = false
        answerText.value = null
        floatingStatus.value = FloatingStatus.Idle
        statusMessage.value = getString(R.string.recording_indicator, 0)
        Toast.makeText(this, getString(R.string.recording_start), Toast.LENGTH_SHORT).show()
        AppLog.i("REC", "startRecording maxConcurrency=${AppConfig.getMaxConcurrency()}")
    }

    private fun stopRecording() {
        isRecording.value = false
        vlmSemaphore = null
        val total = recordingCaptureCount.value
        val processed = recordingProcessedCount.value
        AppLog.i("REC", "stopRecording captured=$total processed=$processed answers=${recordingAnswers.value.size}")
        if (total == 0) {
            Toast.makeText(this, getString(R.string.recording_no_captures), Toast.LENGTH_SHORT).show()
            return
        }
        if (recordingJobs.isEmpty()) {
            showRecordingResults()
        } else {
            isProcessingRecording.value = true
            floatingStatus.value = FloatingStatus.GettingAnswer
            statusMessage.value = getString(R.string.recording_processing, recordingProcessedCount.value, total)
            Toast.makeText(this, getString(R.string.recording_stop, total), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRecordingCroppedImage(imagePath: String, cropRect: CropRect) {
        serviceScope.launch {
            try {
                val bitmap = ImageCropUtil.loadBitmapFromFile(imagePath)
                val croppedBitmap = ImageCropUtil.cropBitmap(bitmap, cropRect)
                bitmap.recycle()
                recordingProcessBitmap(croppedBitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showErrorMessage(getString(R.string.status_crop_failed, e.message ?: ""))
            } finally {
                ImageCropUtil.deleteTempFile(imagePath)
            }
        }
    }

    private fun recordingProcessBitmap(bitmap: android.graphics.Bitmap) {
        val captureIndex = recordingCaptureCount.value
        AppLog.enter("REC", "recordingProcessBitmap Q$captureIndex")
        val job = serviceScope.launch(Dispatchers.IO) {
            try {
                if (AppConfig.isVisionEnabled()) {
                    recordingProcessWithVlm(bitmap, captureIndex)
                } else {
                    recordingProcessWithOcr(bitmap, captureIndex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("REC", "process failed for Q$captureIndex", e)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        recordingJobs.add(job)
        job.invokeOnCompletion { cause ->
            recordingJobs.remove(job)
            if (cause != null && cause is kotlinx.coroutines.CancellationException) return@invokeOnCompletion
            if (isProcessingRecording.value) {
                serviceScope.launch(Dispatchers.Main) {
                    recordingProcessedCount.value++
                    updateRecordingProgress()
                }
            }
        }
    }

    private suspend fun recordingProcessWithOcr(bitmap: android.graphics.Bitmap, captureIndex: Int) {
        val result = textRecognitionManager.recognizeText(bitmap)
        bitmap.recycle()
        result.onSuccess { recognizedText ->
            val normalized = normalizeForDedupe(recognizedText)
            val alreadyExists = withContext(Dispatchers.Main) {
                if (recordingTextHashes.contains(normalized)) true
                else { recordingTextHashes.add(normalized); false }
            }
            if (alreadyExists) {
                AppLog.d("REC", "去重: 第$captureIndex 题与之前重复，跳过")
                withContext(Dispatchers.Main) { recordingSkippedCount.value++ }
                return
            }
            recordingFetchAnswer(recognizedText, captureIndex)
            withContext(Dispatchers.Main) { recordingTotalQuestions++ }
        }
    }

    private suspend fun recordingProcessWithVlm(bitmap: android.graphics.Bitmap, captureIndex: Int) {
        AppLog.enter("REC", "recordingProcessWithVlm Q$captureIndex")
        val provider = VisionProviderFactory.create()
        if (provider == null) {
            AppLog.w("REC", "VLM Provider创建失败，降级为OCR")
            showErrorMessage(getString(R.string.status_vision_fallback))
            recordingProcessWithOcr(bitmap, captureIndex)
            return
        }
        vlmSemaphore?.withPermit {
            val visionResult = provider.analyze(bitmap)
            visionResult.onSuccess { filter ->
                bitmap.recycle()
                if (!filter.hasQuestions) {
                    return@withPermit
                }

                if (filter.questions.size > 1) {
                    AppLog.i("REC", "VLM found ${filter.questions.size} questions")
                    var skipped = 0
                    filter.questions.forEachIndexed { index, separatedQuestion ->
                        val normalized = normalizeForDedupe(separatedQuestion.text)
                        val alreadyExists = withContext(Dispatchers.Main) {
                            if (recordingTextHashes.contains(normalized)) true
                            else { recordingTextHashes.add(normalized); false }
                        }
                        if (alreadyExists) {
                            skipped++
                            return@forEachIndexed
                        }
                        recordingFetchAnswer(separatedQuestion.text, captureIndex, filter)
                        withContext(Dispatchers.Main) { recordingTotalQuestions++ }
                    }
                    if (skipped > 0) {
                        withContext(Dispatchers.Main) { recordingSkippedCount.value += skipped }
                    }
                } else {
                    val text = filter.extractedText
                    if (text.isBlank()) {
                        return@withPermit
                    }
                    val normalized = normalizeForDedupe(text)
                    val alreadyExists = withContext(Dispatchers.Main) {
                        if (recordingTextHashes.contains(normalized)) true
                        else { recordingTextHashes.add(normalized); false }
                    }
                    if (alreadyExists) {
                        withContext(Dispatchers.Main) { recordingSkippedCount.value++ }
                        return@withPermit
                    }
                    recordingFetchAnswer(text, captureIndex, filter)
                    withContext(Dispatchers.Main) { recordingTotalQuestions++ }
                }
            }.onFailure {
                AppLog.w("REC", "VLM分析失败，降级为OCR")
                showErrorMessage(getString(R.string.status_vision_fallback))
                recordingProcessWithOcr(bitmap, captureIndex)
            }
        }
    }

    private fun recordingFetchAnswer(
        text: String, captureIndex: Int,
        visionResult: com.hwb.aianswerer.api.vision.VisionFilterResult? = null
    ) {
        recordingActiveCount.incrementAndGet()
        AppLog.enter("REC", "recordingFetchAnswer Q$captureIndex")
        val job = serviceScope.launch(Dispatchers.IO) {
            recordingSemaphore?.withPermit {
                try {
                    if (!OpenAIClient.isNetworkAvailable()) { return@withPermit }
                    val questionTypes = AppConfig.getQuestionTypes()

                    var searchContext = ""
                    if (visionResult != null && visionResult.searchKeywords.isNotBlank()
                        && searchEnabled.value) {
                        val results = performWebSearch(visionResult.searchKeywords)
                        searchContext = results
                    }

                    val result = OpenAIClient.getInstance().analyzeQuestion(
                        text, questionTypes, searchContext,
                        systemPrompt = Constants.buildRecordingSystemPrompt(
                            captureIndex,
                            recordingCaptureCount.value,
                            questionTypes,
                            searchContext
                        )
                    )
                    result.onSuccess { aiAnswers ->
                        recordingStoreAnswer(aiAnswers, captureIndex)
                    }.onFailure { error ->
                        AppLog.e("REC", "answer failed for Q$captureIndex", error)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) { AppLog.e("REC", "fetch failed", e) }
            }
        }
        recordingJobs.add(job)
        job.invokeOnCompletion {
            recordingJobs.remove(job)
            recordingActiveCount.decrementAndGet()
            AppLog.d("REC", "计数器-1(API完成): activeJobs=${recordingActiveCount.get()}")
            if (isProcessingRecording.value && recordingJobs.isEmpty()) {
                serviceScope.launch(Dispatchers.Main) {
                    showRecordingResults()
                }
            }
        }
    }

    private fun recordingStoreAnswer(aiAnswers: List<com.hwb.aianswerer.models.AIAnswer>?, captureIndex: Int) {
        if (aiAnswers == null || aiAnswers.isEmpty()) return

        val displayFormatted = if (aiAnswers.size == 1) {
            aiAnswers.first().formatAnswerWithConfig(showQuestion = true, showOptions = true)
        } else {
            aiAnswers.mapIndexed { i, a ->
                "━━━ 第 ${captureIndex}-${i + 1} 题 ━━━\n" +
                    a.formatAnswerWithConfig(showQuestion = true, showOptions = true)
            }.joinToString("\n\n")
        }
        val displayEntry = "━━━ 第 $captureIndex 题 ━━━\n$displayFormatted"

        val copyFormatted = if (aiAnswers.size == 1) {
            "第${captureIndex}题：${aiAnswers.first().answer}"
        } else {
            aiAnswers.mapIndexed { i, a ->
                "第${captureIndex}-${i + 1}题：${a.answer}"
            }.joinToString("\n")
        }

        serviceScope.launch(Dispatchers.Main) {
            recordingAnswers.value = (recordingAnswers.value + (captureIndex to displayEntry))
                .sortedBy { it.first }
            recordingCopyTexts.value = (recordingCopyTexts.value + (captureIndex to copyFormatted))
                .sortedBy { it.first }
        }
    }

    private fun updateRecordingProgress() {
        val total = recordingCaptureCount.value
        val done = recordingProcessedCount.value
        val answersSoFar = recordingAnswers.value.size
        if (done >= total && recordingJobs.isEmpty()) {
            showRecordingResults()
        } else {
            statusMessage.value = getString(
                R.string.recording_processing,
                answersSoFar,
                recordingTotalQuestions
            )
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
        statusMessage.value = if (skipped > 0) {
            getString(R.string.recording_all_done_dedup, total, skipped, total - skipped)
        } else {
            getString(R.string.recording_all_done, total)
        }

        if (autoCopy) {
            val copyText = recordingCopyTexts.value.sortedBy { it.first }
                .joinToString("\n") { it.second }
            ClipboardUtil.copyToClipboard(this@FloatingWindowService, copyText)
        }
        isProcessingRecording.value = false
    }

    /**
     * 处理截图后的图片
     *
     * 根据用户配置选择识别模式：
     *   - OCR模式：使用ML Kit识别文本
     *   - VLM模式：使用视觉模型提取文本和元数据
     */
    private suspend fun processBitmap(bitmap: android.graphics.Bitmap) {
        try {
            val useVlm = AppConfig.isVisionEnabled()
            AppLog.i("FWS", "VLM enabled=$useVlm")

            if (useVlm) {
                // VLM模式：使用视觉模型直接提取
                processBitmapWithVlm(bitmap)
            } else {
                // OCR模式：使用ML Kit识别
                processBitmapWithOcr(bitmap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showErrorMessage(getString(R.string.status_recognition_error, e.message ?: ""))
        }
    }

    /**
     * 判断识别文本是否像一道题目
     * 特征：包含问号、选项标记(A/B/C/D)、常见题目关键词
     */
    private fun looksLikeQuestion(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        // 包含问号
        if (t.contains("?") || t.contains("？")) return true
        // 包含选项标记 A. B. C. D. 或 A、B、C、D
        if (Regex("""[A-Da-d][.、．)\s]""").containsMatchIn(t)) return true
        // 包含常见题目关键词
        val keywords = listOf("下列", "以下", "属于", "不属于", "正确", "错误", "哪个", "哪些",
            "什么", "如何", "为什么", "原因是", "主要", "关于", "说法", "选项", "答案",
            "which", "what", "how", "why", "correct", "incorrect", "true", "false")
        val lower = t.lowercase()
        if (keywords.any { lower.contains(it) }) return true
        return false
    }

    /**
     * OCR模式处理
     */
    private suspend fun processBitmapWithOcr(bitmap: android.graphics.Bitmap) {
        AppLog.enter("OCR", "processBitmapWithOcr")
        floatingStatus.value = FloatingStatus.Recognizing
        statusMessage.value = getString(R.string.status_recognizing)

        val result = textRecognitionManager.recognizeText(bitmap)
        bitmap.recycle()

        result.onSuccess { recognizedText ->
            AppLog.d("OCR", "recognized=${recognizedText.length} chars")
            statusMessage.value = getString(R.string.status_recognized)

            if (!looksLikeQuestion(recognizedText)) {
                showErrorMessage("未识别到题目")
                return@onSuccess
            }

            fetchAnswer(recognizedText)
        }.onFailure { error ->
            showErrorMessage(getString(R.string.status_recognition_failed, error.message ?: ""))
        }
    }

    /**
     * VLM模式处理：使用视觉模型提取文本和元数据
     */
    private suspend fun processBitmapWithVlm(bitmap: android.graphics.Bitmap) {
        AppLog.enter("VLM", "processBitmapWithVlm")
        AppLog.i("FWS", "VLM analysis starting, bitmap=${bitmap.width}x${bitmap.height}")
        floatingStatus.value = FloatingStatus.Recognizing
        statusMessage.value = getString(R.string.status_vision_analyzing)

        val provider = VisionProviderFactory.create()
        if (provider == null) {
            // Provider未创建，降级为OCR
            AppLog.w("FWS", "VisionProvider未创建，降级为OCR模式")
            processBitmapWithOcr(bitmap)
            return
        }

        AppLog.i("FWS", "VLM calling provider.analyze()...")
        val visionResult = provider.analyze(bitmap)
        AppLog.i("FWS", "VLM analyze() returned")

        visionResult.onSuccess { filter ->
            AppLog.i("FWS", "VLM success=${filter.hasQuestions}, questionCount=${filter.questionCount}")
            // VLM成功，现在可以回收bitmap
            bitmap.recycle()

            if (!filter.hasQuestions) {
                showErrorMessage("未识别到题目")
                return
            }

            statusMessage.value = if (filter.questionCount > 1) {
                getString(R.string.status_vision_detected_multi, filter.questionCount)
            } else {
                getString(R.string.status_vision_detected_single)
            }

            // 使用VLM提取的文本
            val text = filter.extractedText
            if (text.isBlank()) {
                showErrorMessage("视觉模型未提取到文本")
                return
            }

            fetchAnswer(text, filter)
        }.onFailure { e ->
            // VLM失败，bitmap仍可用于OCR降级
            AppLog.i("FWS", "VLM failed, falling back to OCR")
            AppLog.w("FWS", "VLM分析失败，降级为OCR模式", e)
            statusMessage.value = getString(R.string.status_vision_fallback)
            // 降级为OCR模式，复用现有的bitmap
            processBitmapWithOcr(bitmap)
        }
    }

    /**
     * 获取问题答案
     * @param text 问题文本
     * @param visionResult 可选的VLM分析结果，用于搜索关键词提取
     */
    private fun fetchAnswer(text: String, visionResult: com.hwb.aianswerer.api.vision.VisionFilterResult? = null) {
        currentFetchJob?.cancel()
        val _start = System.currentTimeMillis()
        AppLog.enter("FWS", "fetchAnswer textLen=${text.length}")
        // 统一使用serviceScope，确保onDestroy时能正确取消所有协程
        currentFetchJob = serviceScope.launch {
            // 使用withLock确保tryLock/unlock在同一协程作用域内
            fetchMutex.withLock {
                try {
                    // 网络连接预检
                    if (!OpenAIClient.isNetworkAvailable()) {
                        showErrorMessage(getString(R.string.error_api_unknown_host))
                        return@withLock
                    }

                    // 从配置读取答题设置
                    val questionTypes = AppConfig.getQuestionTypes()
                    val autoCopy = AppConfig.getAutoCopy()

                    // ========== 多题模式：VLM分离题目 + 单独搜索 ==========
                    if (visionResult != null && visionResult.questions.size > 1) {
                        fetchAnswerMultiQuestion(visionResult, questionTypes, autoCopy)
                        return@withLock
                    }

                    // ========== 单题模式 ==========
                    var searchContext = ""

                    // VLM模式：使用VLM提供的搜索关键词
                    if (visionResult != null) {
                        if (visionResult.searchKeywords.isNotBlank() && searchEnabled.value) {
                            floatingStatus.value = FloatingStatus.Searching
                            statusMessage.value = getString(R.string.status_searching)
                            AppLog.d("FWS", "Web搜索(VLM关键词): ${visionResult.searchKeywords}")
                            searchContext = performWebSearch(visionResult.searchKeywords)
                            AppLog.d("FWS", "Web搜索完成")
                        }
                        // VLM模式下不使用正则，直接进入LLM答题
                    } else {
                        // OCR模式：使用正则提取搜索关键词
                        val multiQuestionPattern = Regex("""[1-9]\s*[.、．]\s*\S""")
                        val isMultiQuestion = AppConfig.isRegexFilterEnabled() && multiQuestionPattern.containsMatchIn(text)

                        if (!isMultiQuestion && searchEnabled.value) {
                            floatingStatus.value = FloatingStatus.Searching
                            statusMessage.value = getString(R.string.status_searching)
                            val lines = text.lines()
                            val questionLine = lines.firstOrNull { it.contains("?") || it.contains("？") }?.trim()
                            val optionLines = lines.filter { it.trim().matches(Regex("""^[A-Da-d][.、．)\s].*""")) }
                                .map { it.trim() }
                            val searchQuery = if (!questionLine.isNullOrBlank()) {
                                (listOf(questionLine) + optionLines).joinToString(" ")
                            } else {
                                text
                            }
                            AppLog.d("FWS", "Web搜索(正则提取): $searchQuery")
                            searchContext = performWebSearch(searchQuery)
                            AppLog.d("FWS", "Web搜索结果已注入上下文")
                        }
                    }

                    // ========== LLM答题 ==========
                    floatingStatus.value = FloatingStatus.GettingAnswer
                    statusMessage.value = getString(R.string.status_getting_answer)

                    AppLog.i("FWS", "LLM calling analyzeQuestion, textLen=${text.length}")
                    val apiClient = OpenAIClient.getInstance()
                    val result = withTimeout(180_000L) { apiClient.analyzeQuestion(text, questionTypes, searchContext) }
                    AppLog.i("FWS", "LLM analyzeQuestion returned, isSuccess=${result.isSuccess}")
                    AppLog.leave("FWS", "fetchAnswer", _start)

                    result.onSuccess { aiAnswers ->
                        handleAnswerSuccess(aiAnswers, autoCopy)
                    }.onFailure { error ->
                        showErrorMessage(getString(R.string.status_ai_analysis_failed, error.message ?: ""))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showErrorMessage(getString(R.string.status_fetch_answer_failed, e.message ?: ""))
                }
            }
        }
    }

    /**
     * 多题模式：对每道题单独搜索，然后逐题调用LLM
     * 支持并行模式（根据配置）
     */
    private suspend fun fetchAnswerMultiQuestion(
        visionResult: com.hwb.aianswerer.api.vision.VisionFilterResult,
        questionTypes: Set<String>,
        autoCopy: Boolean
    ) {
        val questions = visionResult.questions.filter { it.text.isNotBlank() }
        val totalQuestions = questions.size
        AppLog.d("FWS", "多题模式: $totalQuestions 道题目")

        // 检查是否启用并行模式
        if (AppConfig.isParallelModeEnabled()) {
            fetchAnswerMultiQuestionParallel(questions, questionTypes, autoCopy, totalQuestions)
        } else {
            fetchAnswerMultiQuestionSequential(questions, questionTypes, autoCopy, totalQuestions)
        }
    }

    /**
     * 串行模式：逐题处理
     */
    private suspend fun fetchAnswerMultiQuestionSequential(
        questions: List<com.hwb.aianswerer.api.vision.SeparatedQuestion>,
        questionTypes: Set<String>,
        autoCopy: Boolean,
        totalQuestions: Int
    ) {
        val allAnswers = mutableListOf<com.hwb.aianswerer.models.AIAnswer>()

        for ((idx, question) in questions.withIndex()) {
            // 搜索参考资料
            var searchContext = ""
            if (question.searchKeywords.isNotBlank() && searchEnabled.value) {
                statusMessage.value = getString(R.string.status_searching) + " (${idx + 1}/$totalQuestions)"
                AppLog.d("FWS", "Web搜索(题目${idx + 1}): ${question.searchKeywords}")
                searchContext = performWebSearch(question.searchKeywords, 2)
                AppLog.d("FWS", "题目${idx + 1}搜索完成")
            }

            // 调用LLM答题
            statusMessage.value = getString(R.string.status_getting_answer) + " (${idx + 1}/$totalQuestions)"

            val apiClient = OpenAIClient.getInstance()
            val result = apiClient.analyzeQuestion(question.text, questionTypes, searchContext)

            result.onSuccess { answers ->
                allAnswers.addAll(answers)
                AppLog.d("FWS", "题目${idx + 1}答题完成: ${answers.size}个答案")
            }.onFailure { error ->
                AppLog.e("FWS", "题目${idx + 1}答题失败: ${error.message}")
            }
        }

        // 显示所有答案
        if (allAnswers.isNotEmpty()) {
            handleAnswerSuccess(allAnswers, autoCopy)
        } else {
            showErrorMessage(getString(R.string.status_ai_analysis_failed, "所有题目答题失败"))
        }
    }

    /**
     * 并行模式：并发处理多道题目
     */
    private suspend fun fetchAnswerMultiQuestionParallel(
        questions: List<com.hwb.aianswerer.api.vision.SeparatedQuestion>,
        questionTypes: Set<String>,
        autoCopy: Boolean,
        totalQuestions: Int
    ) {
        val maxConcurrency = AppConfig.getMaxConcurrency()
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val allAnswers = java.util.concurrent.ConcurrentLinkedQueue<com.hwb.aianswerer.models.AIAnswer>()

        // 限制并发数
        val semaphore = Semaphore(maxConcurrency)

        AppLog.d("FWS", "并行模式: $totalQuestions 道题目, 最大并发数: $maxConcurrency")

        // 使用 coroutineScope 确保所有协程完成
        kotlinx.coroutines.coroutineScope {
            // 并发执行所有题目
            val jobs = questions.mapIndexed { idx, question ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            // 搜索参考资料
                            var searchContext = ""
                            if (question.searchKeywords.isNotBlank() && searchEnabled.value) {
                                AppLog.d("FWS", "Web搜索(题目${idx + 1}): ${question.searchKeywords}")
                                searchContext = performWebSearch(question.searchKeywords, 2)
                                AppLog.d("FWS", "题目${idx + 1}搜索完成")
                            }

                            // 调用LLM答题
                            val apiClient = OpenAIClient.getInstance()
                            val result = apiClient.analyzeQuestion(question.text, questionTypes, searchContext)

                            result.onSuccess { answers ->
                                allAnswers.addAll(answers)
                                AppLog.d("FWS", "题目${idx + 1}答题完成: ${answers.size}个答案")
                            }.onFailure { error ->
                                AppLog.e("FWS", "题目${idx + 1}答题失败: ${error.message}")
                                failedCount.incrementAndGet()
                            }

                            // 更新进度
                            val completed = completedCount.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                statusMessage.value = getString(
                                    R.string.status_parallel_answering,
                                    completed, totalQuestions
                                )
                            }

                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    showErrorMessage(getString(R.string.error_api_timeout))
                } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.e("FWS", "题目${idx + 1}处理异常: ${e.message}")
                            failedCount.incrementAndGet()
                        }
                        }
                    }
                }

            // 等待所有任务完成
            jobs.forEach { job -> job.join() }
        }

        // 显示结果
        if (allAnswers.isNotEmpty()) {
            // 部分失败时显示提示
            if (failedCount.get() > 0) {
                showStatusMessage(
                    getString(R.string.status_parallel_partial_failed),
                    3000
                )
            }
            handleAnswerSuccess(allAnswers.toList(), autoCopy)
        } else {
            showErrorMessage(getString(R.string.status_ai_analysis_failed, "所有题目答题失败"))
        }
    }

    /**
     * 处理答题成功后的显示逻辑
     */
    private suspend fun handleAnswerSuccess(
        aiAnswers: List<com.hwb.aianswerer.models.AIAnswer>,
        autoCopy: Boolean
    ) {
        val showQuestion = AppConfig.getShowAnswerCardQuestion()
        val showOptions = AppConfig.getShowAnswerCardOptions()

        // 统一拆分为逐题列表，供翻页卡片使用（一页一题）
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

        statusMessage.value = if (autoCopy) getString(R.string.status_answer_copied) else getString(R.string.status_answer_generated)
        delay(2000)
        statusMessage.value = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_name)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** Check notification permission on Android 13+; foreground service may be killed without it */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                AppLog.w("FWS", "Notifications disabled on API 33+; foreground service may be killed")
            }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingWindowService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, getString(R.string.stop_service), stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        destroyed = true

        // 清理录制状态
        recordingJobs.forEach { it.cancel() }
        recordingJobs.clear()
        isRecording.value = false
        isProcessingRecording.value = false

        // 先取消所有协程，再设置DESTROYED状态
        // 避免lifecycleScope与serviceScope取消顺序不一致的问题
        currentFetchJob?.cancel()
        currentFetchJob = null
        serviceScope.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        try {
            unregisterReceiver(answerReceiver)
        } catch (e: IllegalArgumentException) {
            AppLog.w("FWS", "Receiver not registered", e)
        }

        floatingView?.let {
            it.disposeComposition()
            windowManager.removeView(it)
        }
        floatingView = null

        screenCaptureManager?.releaseAll()
        _viewModelStore.clear()
    }

    // ========== 状态消息辅助方法 ==========

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
        showStatusMessage(getString(R.string.status_error_prefix, message), 5000)
        AppLog.e("FWS", message)
    }

    private fun showSuccessMessage(message: String) {
        showStatusMessage(getString(R.string.status_success_prefix, message), 2000)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

