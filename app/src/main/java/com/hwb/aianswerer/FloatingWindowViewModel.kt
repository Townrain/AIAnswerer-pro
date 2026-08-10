package com.hwb.aianswerer

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.models.formatAnswerWithConfig
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ClipboardUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class FloatingWindowViewModel : ViewModel() {

    // ===== ServiceContext =====
    /** Bridge between ViewModel and Service for operations that require platform/context.
     *
     *  Window operations are now per-window via FloatingWindowManager.
     *  Removed: updateWindowPosition, updateWindowHeight, getCurrentWindowHeightPx,
     *  setCurrentWindowHeightPx, updateFloatingWindowHeight — 3-window arch manages
     *  window geometry independently per window. */
    interface ServiceContext {
        fun showToast(msg: String)
        fun getString(id: Int): String
        fun getString(id: Int, vararg args: Any): String
        fun showErrorToUser(message: String)
        fun copyToClipboard(text: String)
        fun isLeftSide(): Boolean
        fun getDensity(): Float
        fun setFlagSecure(enabled: Boolean)
        fun setWindowAlpha(alpha: Float)
        fun animateWindowX(targetX: Float, animated: Boolean)
        fun setHasContent(has: Boolean)
        fun onRecordingBitmap(bitmap: Bitmap)
        fun onImageText(text: String)
    }

    private var ctx: ServiceContext? = null
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var answerFetcher: AnswerFetcher? = null

    // ===== Generation counter (S3/M8) =====
    // 每次发起新捕获/新录制时递增；旧异步结果回调时校验代次，不匹配则丢弃
    @Volatile private var captureGeneration = 0
    @Volatile private var recordingGeneration = 0

    private fun nextGeneration(): Int = ++captureGeneration
    private fun isCurrentGeneration(gen: Int): Boolean = gen == captureGeneration


    fun initialize(context: ServiceContext) { ctx = context }

    // ===== Compose state =====
    var answerText = mutableStateOf<String?>(null)
    var showAnswer = mutableStateOf(false)
    var statusMessage = mutableStateOf<String?>(null)
    var floatingStatus = mutableStateOf<FloatingStatus>(FloatingStatus.Idle)

    // ===== Window geometry =====
    var isArcExpanded = false
    var hasContent = false
    var currentWindowHeightPx = 0f
    var currentWindowWidthPx = 0f
    var measuredContentHeightPx = 0f
    var captureInProgress = false

    var displayWindowX = mutableFloatStateOf(0f)
    var windowXAnimJob: Job? = null
    var floatOffsetX = mutableFloatStateOf(0f)
    var floatOffsetY = mutableFloatStateOf(200f)

    // ===== Crop state =====
    var cropMode = AppConfig.CROP_MODE_FULL
    @Volatile var savedCropRect: CropRect? = null
    @Volatile var savedCropRectEach: CropRect? = null

    // ===== Network =====
    var currentFetchJob: Job? = null

    // ===== Recording mode state =====
    var isRecording = mutableStateOf(false)
    var recordingCaptureCount = mutableStateOf(0)
    var isProcessingRecording = mutableStateOf(false)
    var recordingProcessedCount = mutableStateOf(0)
    var recordingAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    var recordingCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    var recordingSkippedCount = mutableStateOf(0)
    var recordingFailedCount = mutableStateOf(0)

    // ===== Image collection mode state =====
    var isImageCollecting = mutableStateOf(false)
    var imageCollectCount = mutableStateOf(0)
    var isProcessingImages = mutableStateOf(false)

    // ===== Paginated answers =====
    var paginatedAnswers = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    var paginatedCopyTexts = mutableStateOf<List<Pair<Int, String>>>(emptyList())

    // ===== RecordingCoordinator.Callbacks =====
    val recordingCallbacks = object : RecordingCoordinator.Callbacks {
        override fun onError(message: String) { ctx?.showErrorToUser(message) }
        override fun onToast(message: String) { ctx?.showToast(message) }
        override fun onResultsAvailable(answers: List<Pair<Int, String>>, copyTexts: List<Pair<Int, String>>, total: Int, skipped: Int, failed: Int, isFinal: Boolean) {
            showRecordingResultsFromCoordinator(answers, copyTexts, total, skipped, failed, isFinal)
        }
        override fun onProgressUpdate(processed: Int, total: Int) {
            // P1-2b: 分母钳制——协调器可能先发来一图多题时的 maxOf 分母，避免卡片 header 回落
            recordingCaptureCount.value = maxOf(recordingCaptureCount.value, total)
            statusMessage.value = ctx?.getString(R.string.recording_processing, processed, total)
            recordingProcessedCount.value = processed
        }
        @Suppress("UNCHECKED_CAST")
        override fun getString(resId: Int, vararg args: Any?): String = ctx?.getString(resId, *(args as Array<out Any>)) ?: ""
    }

    // ===== ImageCollector.Callbacks =====
    val imageCallbacks = object : ImageCollector.Callbacks {
        override fun onError(message: String) { ctx?.showErrorToUser(message) }
        override fun onToast(message: String) { ctx?.showToast(message) }
        override fun onResult(answers: List<AIAnswer>) {
            val autoCopy = AppConfig.getAutoCopy()
            // Reuse the existing answer display pipeline
            // NOTE: paginatedAnswers MUST be set BEFORE showAnswer, otherwise
            // the snapshotFlow observer in FloatingWindowService creates
            // Window D with empty data before content arrives.
            paginatedAnswers.value = answers.mapIndexed { i, a ->
                (i + 1) to a.formatAnswerWithConfig(
                    AppConfig.getShowAnswerCardQuestion(),
                    AppConfig.getShowAnswerCardOptions()
                )
            }
            paginatedCopyTexts.value = answers.mapIndexed { i, a ->
                (i + 1) to "第 ${i + 1} 题：${a.answer}"
            }
            showAnswer.value = true
            val copyText = if (answers.size == 1) answers.first().answer
                else answers.mapIndexed { i, a -> "第 ${i + 1} 题：${a.answer}" }.joinToString("\n")
            if (autoCopy) ctx?.copyToClipboard(copyText)
            floatingStatus.value = FloatingStatus.Success
            statusMessage.value = if (autoCopy) "答案已复制" else "答案已生成"
            isProcessingImages.value = false
        }
        override fun onProgressUpdate(collected: Int) {
            imageCollectCount.value = collected
            if (collected >= 0) {
                statusMessage.value = ctx?.getString(R.string.image_collecting, collected)
            } else {
                statusMessage.value = ctx?.getString(R.string.image_analyzing)
            }
        }
    }

    // ===== AnswerFetcherCallbacks =====
    val answerCallbacks = object : AnswerFetcherCallbacks {
        override fun onStatus(status: FloatingStatus, message: String?) {
            floatingStatus.value = status
            statusMessage.value = message
        }
        override fun onToast(message: String) { ctx?.showToast(message) }
        override fun onError(message: String) { ctx?.showErrorToUser(message) }
    }


    // ===== CaptureHandlerCallbacks =====
    val captureCallbacks = object : CaptureHandlerCallbacks {
        override fun isRecording() = isRecording.value
        override fun isImageCollecting() = isImageCollecting.value
        override fun getCropMode() = cropMode
        override fun getSavedCropRect() = savedCropRect
        override fun getSavedCropRectEach() = savedCropRectEach
        override fun isVisionEnabled() = com.hwb.aianswerer.config.AppConfig.isVisionEnabled()
        override fun isStealthModeEnabled(): Boolean {
            return AppConfig.isStealthModeEnabled()
        }
        override fun getFloatButtonSizeDp(): Int {
            return try {
                val mmkv = com.tencent.mmkv.MMKV.defaultMMKV()
                mmkv.decodeInt("float_button_size_dp", 40)
            } catch (_: Exception) { 40 }
        }
        override fun getDensity() = ctx?.getDensity() ?: 3f

        override fun setSavedCropRect(rect: CropRect?) { savedCropRect = rect }
        override fun setSavedCropRectEach(rect: CropRect?) { savedCropRectEach = rect }
        override fun setHasContent(has: Boolean) { hasContent = has; ctx?.setHasContent(has) }
        override fun setCaptureInProgress(enabled: Boolean) { captureInProgress = enabled }
        override fun setShowAnswer(show: Boolean) { showAnswer.value = show }
        override fun getCurrentWindowHeightPx(): Float = currentWindowHeightPx
        override fun setCurrentWindowHeightPx(h: Float) { currentWindowHeightPx = h }

        override fun setFlagSecure(enabled: Boolean) { ctx?.setFlagSecure(enabled) }
        override fun setWindowAlpha(alpha: Float) { ctx?.setWindowAlpha(alpha) }
        // 下两个回调被 CaptureHandler 中 12 处调用，但在 3-window 架构下已 noop：
        // 窗口位置/尺寸由 service.dragWindowBy + ComposeView onMeasuredSize + snapshotFlow
        // 响应式处理。留空实现避免破坏 CaptureHandlerCallbacks 接口；如果某场景下窗口
        // 没被响应式调整读取，优先在 service 里补 snapshotFlow 监听，不要这里偷改几何。
        override fun updateWindowPosition() { /* noop — 3-window architecture: 窗口位置由 service.dragWindowBy + animateWindowX 直接控制 */ }
        override fun updateWindowHeight() { /* noop — 3-window architecture: 每窗口高度由 ComposeView onMeasuredSize 响应式上报 */ }
        override fun showError(message: String) { ctx?.showErrorToUser(message) }
        override fun showToast(message: String) { ctx?.showToast(message) }
        override fun setStatus(status: FloatingStatus) { floatingStatus.value = status }
        override fun setStatusMessage(msg: String?) { statusMessage.value = msg }
        @Suppress("UNCHECKED_CAST")
        override fun getString(resId: Int, vararg args: Any?): String = ctx?.getString(resId, *(args as Array<out Any>)) ?: ""

        override fun onTextRecognized(text: String, visionResult: VisionFilterResult?) {
            onTextRecognized(text, visionResult, answerFetcher)
        }
        override fun onRecordingBitmap(bitmap: Bitmap) { ctx?.onRecordingBitmap(bitmap) }
        override fun onImageText(text: String) { ctx?.onImageText(text) }
        override fun incRecordingCaptureCount(): Int {
            recordingCaptureCount.value++
            return recordingCaptureCount.value
        }
        override fun getRecordingCaptureCount(): Int = recordingCaptureCount.value
        override fun getCurrentFetchJob(): Job? = currentFetchJob
        override fun setCurrentFetchJob(job: Job?) { currentFetchJob = job }
        override fun clearAnswers() {
            // 防呆：录制结果仍在处理/展示中时，跳过清理，避免误触主按钮清空录制答案
            if (isProcessingRecording.value) {
                AppLog.d("VM", "clearAnswers skipped: recording results processing"); return
            }
            // S3: 新捕获开始即递增代次，让仍在途的旧 fetch/录制回调立即失效
            nextGeneration()
            answerText.value = null
            paginatedAnswers.value = emptyList()
            paginatedCopyTexts.value = emptyList()
            recordingAnswers.value = emptyList()
            recordingCopyTexts.value = emptyList()
            floatingStatus.value = FloatingStatus.Idle
            showAnswer.value = false
            statusMessage.value = null
            hasContent = false
        }
    }

    // ===== Business methods =====

    fun onTextRecognized(text: String, visionResult: VisionFilterResult?, answerFetcher: AnswerFetcher?) {
        if (answerFetcher == null) return
        val autoCopy = AppConfig.getAutoCopy()
        // S3: 每次识别捕获当前代次；回调时若代次已过期（期间用户发起新捕获）则丢弃旧结果
        val gen = nextGeneration()
        answerFetcher.fetchAnswer(text, visionResult) { result ->
            if (!isCurrentGeneration(gen)) {
                AppLog.d("VM", "drop stale answer result (gen=$gen, current=$captureGeneration)")
                return@fetchAnswer
            }
            when (result) {
                is AnswerResult.Success -> vmScope.launch { handleAnswerSuccess(result.answers, autoCopy) }
                is AnswerResult.Error -> ctx?.showErrorToUser(result.message)
            }
        }
    }

    private suspend fun handleAnswerSuccess(aiAnswers: List<AIAnswer>, autoCopy: Boolean) {
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
            ctx?.copyToClipboard(copyText)
        }

        answerText.value = formattedAnswer
        showAnswer.value = true
        floatingStatus.value = FloatingStatus.Success
        statusMessage.value = if (autoCopy) "答案已复制" else "答案已生成"
        val msgAfterAnswer = statusMessage.value
        delay(2000)
        // 防呆：仅当消息未被后续流程覆盖时才清除，避免旧协程抹掉新状态
        if (statusMessage.value == msgAfterAnswer) statusMessage.value = null
    }

    fun startRecording(recorder: RecordingCoordinator) {
        currentFetchJob?.cancel()
        currentFetchJob = null
        // M8: 录制开始 = 新代次，使此前的普通 fetch 结果失效；录制结果回调校验此代次
        recordingGeneration = nextGeneration()
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
        statusMessage.value = ctx?.getString(R.string.recording_indicator, 0)
        ctx?.showToast(ctx?.getString(R.string.recording_start) ?: "")
    }

    fun stopRecording(recorder: RecordingCoordinator) {
        isRecording.value = false
        when (val result = recorder.stop()) {
            is RecordingCoordinator.StopResult.NothingToShow -> {
                ctx?.showToast(ctx?.getString(R.string.recording_no_captures) ?: "")
            }
            is RecordingCoordinator.StopResult.Completed -> {
                showRecordingResults()
            }
            is RecordingCoordinator.StopResult.Processing -> {
                isProcessingRecording.value = true
                floatingStatus.value = FloatingStatus.GettingAnswer
                statusMessage.value = ctx?.getString(R.string.recording_processing, recordingProcessedCount.value, result.captureCount)
                ctx?.showToast(ctx?.getString(R.string.recording_stop, result.captureCount) ?: "")
            }
        }
    }

    private fun showRecordingResults() {
        val autoCopy = AppConfig.getAutoCopy()
        val allEntries = recordingAnswers.value.sortedBy { it.first }
        if (allEntries.isEmpty()) {
            ctx?.showErrorToUser(ctx?.getString(R.string.recording_no_valid_answers) ?: "")
            isProcessingRecording.value = false
            return
        }
        showAnswer.value = true
        floatingStatus.value = FloatingStatus.Success
        val total = recordingCaptureCount.value
        val skipped = recordingSkippedCount.value
        val failed = recordingFailedCount.value
        val resultSummary = buildString {
            append(ctx?.getString(R.string.recording_all_done, total))
            if (skipped > 0) append("，去除重复 ${skipped} 题")
            if (failed > 0) append("，${failed} 题获取失败")
        }
        statusMessage.value = resultSummary
        if (autoCopy) {
            val copyText = recordingCopyTexts.value.sortedBy { it.first }
                .joinToString("\n") { it.second }
            ctx?.copyToClipboard(copyText)
        }
        isProcessingRecording.value = false
    }

    private fun showRecordingResultsFromCoordinator(
        answers: List<Pair<Int, String>>,
        copyTexts: List<Pair<Int, String>>,
        total: Int, skipped: Int, failed: Int,
        isFinal: Boolean
    ) {
        // M8: 若期间用户已发起新捕获/新录制（代次已变），丢弃旧录制结果，避免后置弹出
        if (!isCurrentGeneration(recordingGeneration)) {
            AppLog.d("VM", "drop stale recording result (recGen=$recordingGeneration, current=$captureGeneration)")
            return
        }
        if (answers.isEmpty()) {
            ctx?.showErrorToUser(ctx?.getString(R.string.recording_no_valid_answers) ?: "")
            isProcessingRecording.value = false
            return
        }
        recordingAnswers.value = answers
        recordingCopyTexts.value = copyTexts
        // P1-2b: 最终/partial 通知的 total 同样钳制卡片 header 分母
        recordingCaptureCount.value = maxOf(recordingCaptureCount.value, total)
        showAnswer.value = true
        // M11: partial 通知（stop 时已有部分答案）显示"处理中"，全部完成后才显示"全部完成"
        if (isFinal) {
            floatingStatus.value = FloatingStatus.Success
            val resultSummary = buildString {
                append(ctx?.getString(R.string.recording_all_done, total))
                if (skipped > 0) append("，去除重复 ${skipped} 题")
                if (failed > 0) append("，${failed} 题获取失败")
            }
            statusMessage.value = resultSummary
            isProcessingRecording.value = false
        } else {
            floatingStatus.value = FloatingStatus.GettingAnswer
            statusMessage.value = ctx?.getString(R.string.recording_processing, answers.size, total)
        }
        if (AppConfig.getAutoCopy()) {
            ctx?.copyToClipboard(copyTexts.sortedBy { it.first }.joinToString("\n") { it.second })
        }
    }

    fun refreshSettingsFromApp() { AppLog.d("FWS", "settings refreshed") }

    fun showErrorMessage(message: String) {
        floatingStatus.value = FloatingStatus.Error
        vmScope.launch {
            statusMessage.value = message
            delay(5000)
            if (statusMessage.value == message) {
                statusMessage.value = null
                if (floatingStatus.value == FloatingStatus.Error) {
                    floatingStatus.value = FloatingStatus.Idle
                }
            }
        }
        AppLog.e("FWS", message)
    }

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
    }
}
