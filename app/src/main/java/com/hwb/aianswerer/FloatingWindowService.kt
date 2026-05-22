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
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext


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
        const val EXTRA_IMAGE_PATH = "image_path"
        
        // Compose重组等待时间（毫秒）
        // 确保UI状态更新完成后再进行截图
        private const val COMPOSE_RECOMPOSITION_DELAY_MS = 100L
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val textRecognitionManager = TextRecognitionManager.getInstance()

    private var answerText = mutableStateOf<String?>(null)
    private var showAnswer = mutableStateOf(false)
    private var statusMessage = mutableStateOf<String?>(null)
    private var floatingStatus = mutableStateOf(FloatingStatus.Idle)
    private var questionTypes = mutableSetOf<String>()
    private var cropMode = AppConfig.CROP_MODE_FULL
    // savedCropRect: 单次模式(once)首次裁剪后缓存，后续截图直接复用
    // savedCropRectEach: 每次模式(each)缓存上一次坐标，作为裁剪 UI 的初始位置
    private var savedCropRect: CropRect? = null
    private var savedCropRectEach: CropRect? = null

    // 当前进行中的网络请求 Job，用于在 onDestroy 时取消
    private var currentFetchJob: Job? = null
    // 用于防止并发请求的互斥锁
    private val fetchMutex = kotlinx.coroutines.sync.Mutex()

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

                        // 处理裁剪后的图片
                        handleCroppedImage(imagePath, cropRect)
                    }
                }

                else -> {
                    // 忽略未知广播
                }
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(com.hwb.aianswerer.utils.LanguageUtil.attachBaseContext(newBase!!))
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(answerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(answerReceiver, filter)
        }

        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID, createNotification())

        showFloatingWindow()

        // 快速推进生命周期到RESUMED状态，使ComposeView能够正常组合和渲染
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        // START_STICKY: Service被系统杀死后会尝试重建，但intent为null
        return START_STICKY
    }

    // 悬浮窗内部偏移量（按钮中心的屏幕坐标）
    private var floatOffsetX = mutableStateOf(0f)
    private var floatOffsetY = mutableStateOf(200f)

    private fun showFloatingWindow() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val buttonSizePx = AppConfig.getFloatButtonSize() * metrics.density
        val buttonHalf = buttonSizePx / 2f
        val cardWidthPx = 300 * metrics.density

        // 初始位置：按钮中心在屏幕右侧
        floatOffsetX.value = screenW - buttonHalf

        fun isLeftSide() = floatOffsetX.value < screenW / 2f

        fun windowX(): Int {
            return if (isLeftSide()) {
                (floatOffsetX.value - buttonHalf).toInt().coerceAtLeast(0)
            } else {
                (floatOffsetX.value + buttonHalf - cardWidthPx).toInt()
                    .coerceAtMost(screenW.toInt() - cardWidthPx.toInt())
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or if (AppConfig.isStealthModeEnabled()) WindowManager.LayoutParams.FLAG_SECURE else 0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowX()
            y = floatOffsetY.value.toInt().coerceIn(0, screenH.toInt() - buttonSizePx.toInt())
            if (AppConfig.isStealthModeEnabled()) alpha = 0.99f
        }

        floatingView = ComposeView(this).apply {
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
                        buttonAlpha = AppConfig.getFloatButtonAlpha(),
                        cardAlpha = AppConfig.getFloatCardAlpha(),
                        isLeftSide = isLeftSide(),
                        floatingStatus = floatingStatus.value,
                        onCaptureClick = { handleCapture() },
                        onCloseAnswer = {
                            showAnswer.value = false
                            answerText.value = null
                            floatingStatus.value = FloatingStatus.Idle
                        },
                        onCloseStatus = {
                            statusMessage.value = null
                        },
                        onCopyAnswer = {
                            ClipboardUtil.copyToClipboard(this@FloatingWindowService, answerText.value ?: "")
                        },
                        onMove = { deltaX, deltaY ->
                            floatOffsetX.value = (floatOffsetX.value + deltaX)
                                .coerceIn(buttonHalf, screenW - buttonHalf)
                            floatOffsetY.value = (floatOffsetY.value + deltaY)
                                .coerceIn(buttonHalf, screenH - buttonHalf)
                            floatingView?.let { v ->
                                val p = v.layoutParams as WindowManager.LayoutParams
                                p.x = windowX()
                                p.y = floatOffsetY.value.toInt()
                                    .coerceIn(0, screenH.toInt() - buttonSizePx.toInt())
                                windowManager.updateViewLayout(v, p)
                            }
                        },
                        onDragEnd = {
                            val mid = screenW / 2f
                            floatOffsetX.value = if (floatOffsetX.value < mid) {
                                buttonHalf
                            } else {
                                screenW - buttonHalf
                            }
                            floatingView?.let { v ->
                                val p = v.layoutParams as WindowManager.LayoutParams
                                p.x = windowX()
                                windowManager.updateViewLayout(v, p)
                            }
                        }
                    )
                }
            }
        }

        windowManager.addView(floatingView, params)
    }

    private fun handleCapture() {
        // 忙时点击 → 取消当前请求，回到空闲，不开始新截图
        val isBusy = floatingStatus.value != FloatingStatus.Idle &&
                floatingStatus.value != FloatingStatus.Success &&
                floatingStatus.value != FloatingStatus.Error
        if (isBusy) {
            currentFetchJob?.cancel()
            currentFetchJob = null
            showAnswer.value = false
            answerText.value = null
            floatingStatus.value = FloatingStatus.Idle
            statusMessage.value = null
            return
        }

        serviceScope.launch {
            try {
                showAnswer.value = false
                answerText.value = null
                floatingStatus.value = FloatingStatus.Idle

                // 检查是否使用无障碍屏幕读取模式
                if (AppConfig.isAccessibilityCaptureMode()) {
                    handleAccessibilityCapture()
                    return@launch
                }

                // === 截图模式 ===
                floatingStatus.value = FloatingStatus.Capturing
                statusMessage.value = getString(R.string.status_capturing)

                // 等待 Compose 重组完成，确保上一次的答案卡片已从屏幕上移除，
                // 否则 OCR 会识别到旧卡片内容而非新页面
                delay(COMPOSE_RECOMPOSITION_DELAY_MS)

                val bitmap = screenCaptureManager?.captureScreen()
                if (bitmap == null) {
                    showErrorMessage(getString(R.string.status_capture_failed))
                    return@launch
                }

                // 根据裁剪模式决定是否需要裁剪步骤：
                //   full  → 直接 OCR 全屏
                //   each  → 每次都启动裁剪 UI（可复用像素坐标）
                //   once  → 首次启动裁剪 UI，后续复用 savedCropRect（图片坐标）
                when (cropMode) {
                    AppConfig.CROP_MODE_FULL -> {
                        // 全屏模式：直接识别
                        processBitmap(bitmap)
                    }

                    AppConfig.CROP_MODE_EACH -> {
                        // 部分识别（每次）：启动裁剪Activity（传递上次的坐标）
                        launchCropActivity(bitmap, savedCropRectEach)
                    }

                    AppConfig.CROP_MODE_ONCE -> {
                        savedCropRect?.let { rect ->
                            // 已有保存的坐标：直接裁剪
                            val croppedBitmap = ImageCropUtil.cropBitmap(bitmap, rect)
                            bitmap.recycle()
                            processBitmap(croppedBitmap)
                        } ?: run {
                            // 没有坐标：启动裁剪Activity
                            launchCropActivity(bitmap, null)
                        }
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showErrorMessage(getString(R.string.status_operation_failed, e.message ?: ""))
            }
        }
    }

    /**
     * 无障碍模式采集：直接读取屏幕文本，无需截图
     */
    private suspend fun handleAccessibilityCapture() {
        if (!ScreenReaderService.isActive) {
            showErrorMessage(getString(R.string.status_screen_read_failed))
            return
        }

        floatingStatus.value = FloatingStatus.Recognizing
        statusMessage.value = getString(R.string.status_reading_screen)

        // 等待 Compose 重组完成，避免读取到旧答案卡片
        delay(COMPOSE_RECOMPOSITION_DELAY_MS)

        val screenText = ScreenReaderService.readScreenText()
        if (screenText.isNullOrBlank()) {
            showErrorMessage(getString(R.string.status_screen_read_failed))
            return
        }

        statusMessage.value = getString(R.string.status_recognized)

        val autoSubmit = AppConfig.getAutoSubmit()
        if (autoSubmit) {
            fetchAnswer(screenText)
        } else {
            val intent = Intent(
                this@FloatingWindowService,
                ConfirmTextActivity::class.java
            ).apply {
                putExtra(Constants.EXTRA_RECOGNIZED_TEXT, screenText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            floatingStatus.value = FloatingStatus.Idle
            statusMessage.value = null
            startActivity(intent)
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
            startActivity(intent)

            statusMessage.value = getString(R.string.status_select_region)
            delay(2000)
            statusMessage.value = null
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
     * OCR模式处理
     */
    private suspend fun processBitmapWithOcr(bitmap: android.graphics.Bitmap) {
        floatingStatus.value = FloatingStatus.Recognizing
        statusMessage.value = getString(R.string.status_recognizing)

        val result = textRecognitionManager.recognizeText(bitmap)
        bitmap.recycle()

        result.onSuccess { recognizedText ->
            statusMessage.value = getString(R.string.status_recognized)
            val autoSubmit = AppConfig.getAutoSubmit()

            if (autoSubmit) {
                fetchAnswer(recognizedText)
            } else {
                val intent = Intent(
                    this@FloatingWindowService,
                    ConfirmTextActivity::class.java
                ).apply {
                    putExtra(Constants.EXTRA_RECOGNIZED_TEXT, recognizedText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                floatingStatus.value = FloatingStatus.Idle
                startActivity(intent)
            }
        }.onFailure { error ->
            showErrorMessage(getString(R.string.status_recognition_failed, error.message ?: ""))
        }
    }

    /**
     * VLM模式处理：使用视觉模型提取文本和元数据
     */
    private suspend fun processBitmapWithVlm(bitmap: android.graphics.Bitmap) {
        floatingStatus.value = FloatingStatus.Recognizing
        statusMessage.value = getString(R.string.status_vision_analyzing)

        val provider = VisionProviderFactory.create()
        if (provider == null) {
            // Provider未创建，降级为OCR
            AppLog.w("VisionProvider未创建，降级为OCR模式")
            processBitmapWithOcr(bitmap)
            return
        }

        val visionResult = provider.analyze(bitmap)

        visionResult.onSuccess { filter ->
            // VLM成功，现在可以回收bitmap
            bitmap.recycle()

            if (!filter.hasQuestions) {
                showErrorMessage(getString(R.string.status_vision_no_question))
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

            val autoSubmit = AppConfig.getAutoSubmit()
            if (autoSubmit) {
                fetchAnswer(text, filter)
            } else {
                val intent = Intent(
                    this@FloatingWindowService,
                    ConfirmTextActivity::class.java
                ).apply {
                    putExtra(Constants.EXTRA_RECOGNIZED_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                floatingStatus.value = FloatingStatus.Idle
                startActivity(intent)
            }
        }.onFailure { e ->
            // VLM失败，bitmap仍可用于OCR降级
            AppLog.w("VLM分析失败，降级为OCR模式", e)
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
        // 使用Mutex防止并发请求
        if (!fetchMutex.tryLock()) {
            AppLog.d("已有请求正在进行，跳过本次请求")
            return
        }
        
        currentFetchJob?.cancel()
        currentFetchJob = lifecycleScope.launch {
            try {
                // 网络连接预检
                if (!OpenAIClient.isNetworkAvailable()) {
                    showErrorMessage(getString(R.string.error_api_unknown_host))
                    return@launch
                }

                // 从配置读取答题设置
                val questionTypes = AppConfig.getQuestionTypes()
                val autoCopy = AppConfig.getAutoCopy()

                // ========== 多题模式：VLM分离题目 + 单独搜索 ==========
                if (visionResult != null && visionResult.questions.size > 1) {
                    fetchAnswerMultiQuestion(visionResult, questionTypes, autoCopy)
                    return@launch
                }

                // ========== 单题模式 ==========
                var searchContext = ""

                // VLM模式：使用VLM提供的搜索关键词
                if (visionResult != null) {
                    if (visionResult.searchKeywords.isNotBlank() && AppConfig.isTavilyConfigValid()) {
                        floatingStatus.value = FloatingStatus.Searching
                        statusMessage.value = getString(R.string.status_searching)
                        AppLog.d("Tavily搜索(VLM关键词): ${visionResult.searchKeywords}")

                        val tavilyResult = TavilyClient.getInstance().simpleSearch(
                            query = visionResult.searchKeywords,
                            maxResults = 3,
                            includeAnswer = true
                        )
                        tavilyResult.onSuccess { results ->
                            searchContext = results.joinToString("\n") {
                                "【${it.title}】${it.content}"
                            }
                            AppLog.d("Tavily搜索完成: ${results.size}条")
                        }
                    }
                    // VLM模式下不使用正则，直接进入LLM答题
                } else {
                    // OCR模式：使用正则提取搜索关键词
                    val multiQuestionPattern = Regex("""[1-9]\s*[.、．]\s*\S""")
                    val isMultiQuestion = AppConfig.isRegexFilterEnabled() && multiQuestionPattern.containsMatchIn(text)

                    if (!isMultiQuestion && AppConfig.isTavilyConfigValid()) {
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
                        AppLog.d("Tavily搜索(正则提取): $searchQuery")
                        val tavilyResult = TavilyClient.getInstance().simpleSearch(
                            query = searchQuery,
                            maxResults = 3,
                            includeAnswer = true
                        )
                        tavilyResult.onSuccess { results ->
                            searchContext = results.joinToString("\n") {
                                "【${it.title}】${it.content}"
                            }
                            AppLog.d("Tavily搜索结果已注入上下文: ${results.size} 条")
                        }
                    }
                }

                // ========== LLM答题 ==========
                floatingStatus.value = FloatingStatus.GettingAnswer
                statusMessage.value = getString(R.string.status_getting_answer)

                val apiClient = OpenAIClient.getInstance()
                val result = apiClient.analyzeQuestion(text, questionTypes, searchContext)

                result.onSuccess { aiAnswers ->
                    handleAnswerSuccess(aiAnswers, autoCopy)
                }.onFailure { error ->
                    showErrorMessage(getString(R.string.status_ai_analysis_failed, error.message ?: ""))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showErrorMessage(getString(R.string.status_fetch_answer_failed, e.message ?: ""))
            } finally {
                // 释放互斥锁，允许下一次请求
                fetchMutex.unlock()
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
        AppLog.d("多题模式: $totalQuestions 道题目")

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
            if (question.searchKeywords.isNotBlank() && AppConfig.isTavilyConfigValid()) {
                statusMessage.value = getString(R.string.status_searching) + " (${idx + 1}/$totalQuestions)"
                AppLog.d("Tavily搜索(题目${idx + 1}): ${question.searchKeywords}")

                val tavilyResult = TavilyClient.getInstance().simpleSearch(
                    query = question.searchKeywords,
                    maxResults = 2,
                    includeAnswer = true
                )
                tavilyResult.onSuccess { results ->
                    searchContext = results.joinToString("\n") {
                        "【${it.title}】${it.content}"
                    }
                    AppLog.d("题目${idx + 1}搜索完成: ${results.size}条")
                }
            }

            // 调用LLM答题
            statusMessage.value = getString(R.string.status_getting_answer) + " (${idx + 1}/$totalQuestions)"

            val apiClient = OpenAIClient.getInstance()
            val result = apiClient.analyzeQuestion(question.text, questionTypes, searchContext)

            result.onSuccess { answers ->
                allAnswers.addAll(answers)
                AppLog.d("题目${idx + 1}答题完成: ${answers.size}个答案")
            }.onFailure { error ->
                AppLog.e("题目${idx + 1}答题失败: ${error.message}")
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

        AppLog.d("并行模式: $totalQuestions 道题目, 最大并发数: $maxConcurrency")

        // 使用 coroutineScope 确保所有协程完成
        kotlinx.coroutines.coroutineScope {
            // 并发执行所有题目
            val jobs = questions.mapIndexed { idx, question ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            // 搜索参考资料
                            var searchContext = ""
                            if (question.searchKeywords.isNotBlank() && AppConfig.isTavilyConfigValid()) {
                                AppLog.d("Tavily搜索(题目${idx + 1}): ${question.searchKeywords}")

                                val tavilyResult = TavilyClient.getInstance().simpleSearch(
                                    query = question.searchKeywords,
                                    maxResults = 2,
                                    includeAnswer = true
                                )
                                tavilyResult.onSuccess { results ->
                                    searchContext = results.joinToString("\n") { result ->
                                        "【${result.title}】${result.content}"
                                    }
                                    AppLog.d("题目${idx + 1}搜索完成: ${results.size}条")
                                }
                            }

                            // 调用LLM答题
                            val apiClient = OpenAIClient.getInstance()
                            val result = apiClient.analyzeQuestion(question.text, questionTypes, searchContext)

                            result.onSuccess { answers ->
                                allAnswers.addAll(answers)
                                AppLog.d("题目${idx + 1}答题完成: ${answers.size}个答案")
                            }.onFailure { error ->
                                AppLog.e("题目${idx + 1}答题失败: ${error.message}")
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

                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.e("题目${idx + 1}处理异常: ${e.message}")
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

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        // 取消进行中的网络请求
        currentFetchJob?.cancel()
        currentFetchJob = null

        try {
            unregisterReceiver(answerReceiver)
        } catch (e: IllegalArgumentException) {
            AppLog.w("Receiver not registered", e)
        }

        floatingView?.let {
            windowManager.removeView(it)
        }

        screenCaptureManager?.releaseAll()
        serviceScope.cancel()
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
        AppLog.e(message)
    }

    private fun showSuccessMessage(message: String) {
        showStatusMessage(getString(R.string.status_success_prefix, message), 2000)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

