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
        override fun onResultsAvailable(answers: List<Pair<Int, String>>, copyTexts: List<Pair<Int, String>>, total: Int, skipped: Int, failed: Int) {
            showRecordingResultsFromCoordinator(answers, copyTexts, total, skipped, failed)
        }
        override fun onProgressUpdate(processed: Int, total: Int) {
            statusMessage.value = ctx?.getString(R.string.recording_processing, processed, total)
            recordingProcessedCount.value = processed
        }
        @Suppress("UNCHECKED_CAST")
        override fun getString(resId: Int, vararg args: Any?): String = ctx?.getString(resId, *(args as Array<out Any>)) ?: ""
        override fun isSearchEnabled(): Boolean = com.hwb.aianswerer.providers.WebSearchStorage.isSearchEnabled()
    }

    // ===== ImageCollector.Callbacks =====
    val imageCallbacks = object : ImageCollector.Callbacks {
        override fun onError(message: String) { ctx?.showErrorToUser(message) }
        override fun onToast(message: String) { ctx?.showToast(message) }
        override fun onResult(answers: List<AIAnswer>) {
            val autoCopy = AppConfig.getAutoCopy()
            // Reuse the existing answer display pipeline
            showAnswer.value = true
            paginatedAnswers.value = answers.mapIndexed { i, a ->
                (i + 1) to a.formatAnswerWithConfig(
                    AppConfig.getShowAnswerCardQuestion(),
                    AppConfig.getShowAnswerCardOptions()
                )
            }
            paginatedCopyTexts.value = answers.mapIndexed { i, a ->
                (i + 1) to "第 ${i + 1} 题：${a.answer}"
            }
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
        override fun isSearchEnabled(): Boolean = com.hwb.aianswerer.providers.WebSearchStorage.isSearchEnabled()
    }

    // ===== CaptureHandlerCallbacks =====
    val captureCallbacks = object : CaptureHandlerCallbacks {
        override fun isRecording() = isRecording.value
        override fun isImageCollecting() = isImageCollecting.value
        override fun getCropMode() = cropMode
        override fun getSavedCropRect() = savedCropRect
        override fun getSavedCropRectEach() = savedCropRectEach
        override fun isVisionEnabled() = com.hwb.aianswerer.config.AppConfig.isVisionEnabled()
        override fun isSearchEnabled() = com.hwb.aianswerer.providers.WebSearchStorage.isSearchEnabled()
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
        override fun updateWindowPosition() { /* noop — 3-window architecture */ }
        override fun updateWindowHeight() { /* noop — each window-sized independently */ }
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
        override fun getCurrentFetchJob(): Job? = currentFetchJob
        override fun setCurrentFetchJob(job: Job?) { currentFetchJob = job }
    }

    // ===== Business methods =====

    fun onTextRecognized(text: String, visionResult: VisionFilterResult?, answerFetcher: AnswerFetcher?) {
        if (answerFetcher == null) return
        val autoCopy = AppConfig.getAutoCopy()
        answerFetcher.fetchAnswer(text, visionResult) { result ->
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
        delay(2000)
        statusMessage.value = null
    }

    fun startRecording(recorder: RecordingCoordinator) {
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
        total: Int, skipped: Int, failed: Int
    ) {
        if (answers.isEmpty()) {
            ctx?.showErrorToUser(ctx?.getString(R.string.recording_no_valid_answers) ?: "")
            isProcessingRecording.value = false
            return
        }
        recordingAnswers.value = answers
        recordingCopyTexts.value = copyTexts
        showAnswer.value = true
        floatingStatus.value = FloatingStatus.Success
        val resultSummary = buildString {
            append(ctx?.getString(R.string.recording_all_done, total))
            if (skipped > 0) append("，去除重复 ${skipped} 题")
            if (failed > 0) append("，${failed} 题获取失败")
        }
        statusMessage.value = resultSummary
        if (AppConfig.getAutoCopy()) {
            ctx?.copyToClipboard(copyTexts.sortedBy { it.first }.joinToString("\n") { it.second })
        }
        isProcessingRecording.value = false
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
