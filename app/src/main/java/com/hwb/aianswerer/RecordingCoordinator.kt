package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.models.formatAnswerWithConfig
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 录题模式协调器 — 管理录题状态和流程编排。
 *
 * 将录题相关的 ~300 行逻辑和 14 个状态字段从 FloatingWindowService 提取出来。
 * 通过 [RecordingCoordinator.Callbacks] 与 Service 通信，不直接操作 Compose 状态。
 */
class RecordingCoordinator(
    private val pipeline: CapturePipeline,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks
) {
    /** 回调接口 — 所有 UI/Toast/结果展示通过此接口通知 Service */
    interface Callbacks {
        fun onError(message: String)
        fun onToast(message: String)
        fun onResultsAvailable(
            answers: List<Pair<Int, String>>,
            copyTexts: List<Pair<Int, String>>,
            total: Int, skipped: Int, failed: Int
        )
        fun onProgressUpdate(processed: Int, total: Int)
        fun getString(resId: Int, vararg args: Any?): String
        fun isSearchEnabled(): Boolean
    }

    // ── 录题状态 ──
    @Volatile var isActive = false
        private set
    @Volatile var isProcessing = false
        private set
    private val _captureCount = AtomicInteger(0)
    val captureCount: Int get() = _captureCount.get()
    private val _processedCount = AtomicInteger(0)
    val processedCount: Int get() = _processedCount.get()
    private val _skippedCount = AtomicInteger(0)
    val skippedCount: Int get() = _skippedCount.get()
    private val _failedCount = AtomicInteger(0)
    val failedCount: Int get() = _failedCount.get()
    private val _totalQuestions = AtomicInteger(0)
    val totalQuestions: Int get() = _totalQuestions.get()

    private val answers = CopyOnWriteArrayList<Pair<Int, String>>()
    private val copyTexts = CopyOnWriteArrayList<Pair<Int, String>>()
    private val textHashes = mutableSetOf<String>()
    private val stateLock = Any()  // guards textHashes writes
    private val jobs = CopyOnWriteArrayList<Job>()
    private val activeJobCount = AtomicInteger(0)
    // M9: 结果通知幂等标志（防兜底协程与正常路径重复 notifyResults）
    private val resultsNotified = java.util.concurrent.atomic.AtomicBoolean(false)
    fun getActiveJobCount(): Int = activeJobCount.get()
    private var llmSemaphore: Semaphore? = null
    private var vlmSemaphore: Semaphore? = null
    /** 开始录题 */
    fun start() {
        isActive = true
        _captureCount.set(0)
        _processedCount.set(0)
        _skippedCount.set(0)
        _failedCount.set(0)
        _totalQuestions.set(0)
        answers.clear()
        copyTexts.clear()
        textHashes.clear()
        jobs.clear()
        activeJobCount.set(0)
        val maxConcurrency = AppConfig.getMaxConcurrency()
        llmSemaphore = Semaphore(maxConcurrency)
        // VLM 与 LLM 共用用户配置的并发数；若服务商并发能力不足，
        // 应由设置页「并发测试」提前暴露限流，而非在录制时硬编码限制
        vlmSemaphore = Semaphore(maxConcurrency)
        AppLog.i("REC", "startRecording maxConcurrency=$maxConcurrency")
    }

    /** 停止录题 — 返回是否需要等待处理完成的标志 */
    fun stop(): StopResult {
        isActive = false
        AppLog.i("REC", "stopRecording captured=$captureCount processed=$processedCount answers=${answers.size}")
        if (captureCount == 0) return StopResult.NothingToShow
        if (jobs.isEmpty()) {
            ensureResultsNotified()
            return StopResult.Completed
        }
        isProcessing = true
        // 防呆：立即先展示已收集到的部分答案，避免等待全部在途 job 期间误触导致答案丢失；
        // 全部完成后 ensureResultsNotified 会再补一次最终通知（幂等，不重复）
        if (answers.isNotEmpty()) {
            scope.launch(Dispatchers.Main) {
                AppLog.d("REC", "stop: partial results (${answers.size}) shown immediately"); notifyResults()
            }
        }
        // M9: 兜底——所有 job 去重/失败时 notifyResults 可能永不触发（isProcessing && jobs.isEmpty() 条件不满足），
        //     启动兜底协程：jobs 清空后强制 notifyResults（幂等，防止与正常路径重复触发）
        scope.launch {
            jobs.forEach { it.join() }
            ensureResultsNotified()
        }
        return StopResult.Processing(captureCount, processedCount)
    }

    /** M9: 幂等通知——防止正常路径与兜底路径重复调用 notifyResults */
    private fun ensureResultsNotified() {
        if (resultsNotified.compareAndSet(false, true)) {
            scope.launch(Dispatchers.Main) { notifyResults() }
        }
    }

    sealed class StopResult {
        data object NothingToShow : StopResult()
        data object Completed : StopResult()
        data class Processing(val captureCount: Int, val processedCount: Int) : StopResult()
    }

    /** 处理裁剪后的录题截图 */
    fun handleCroppedImage(imagePath: String, cropRect: CropRect) {
        scope.launch {
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
                callbacks.onError(callbacks.getString(R.string.status_crop_failed, e.message ?: ""))
            } finally {
                ImageCropUtil.deleteTempFile(imagePath)
            }
        }
    }

    /** 处理录题截图 — 入口 */
    fun processBitmap(bitmap: Bitmap) {
        val captureIndex = _captureCount.incrementAndGet()
        AppLog.enter("REC", "recordingProcessBitmap Q$captureIndex")
        val job = scope.launch(Dispatchers.IO) {
            try {
                if (AppConfig.isVisionEnabled()) {
                    processWithVlm(bitmap, captureIndex)
                } else {
                    processWithOcr(bitmap, captureIndex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("REC", "process failed for Q$captureIndex", e)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        jobs.add(job)
        job.invokeOnCompletion { cause ->
            jobs.remove(job)
            if (cause != null && cause is CancellationException) return@invokeOnCompletion
            if (isProcessing) {
                scope.launch(Dispatchers.Main) {
                    _processedCount.incrementAndGet()
                    checkAndNotifyProgress()
                }
            }
        }
    }

    /** 处理录题文本输入（屏幕读取模式 — 与 OCR 同级，文本已就绪） */
    fun processText(text: String) {
        val captureIndex = _captureCount.incrementAndGet()
        AppLog.enter("REC", "recordingProcessText Q$captureIndex")
        val wasValid = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = scope.launch(Dispatchers.IO) {
            try {
                if (!dedupeAndTrack(text, captureIndex)) {
                    return@launch
                }
                wasValid.set(true)
                fetchAnswer(text, captureIndex)
                _totalQuestions.incrementAndGet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("REC", "process failed for Q$captureIndex", e)
            }
        }
        jobs.add(job)
        job.invokeOnCompletion { cause ->
            jobs.remove(job)
            if (cause != null && cause is CancellationException) return@invokeOnCompletion
            if (isProcessing && wasValid.get()) {
                scope.launch(Dispatchers.Main) {
                    _processedCount.incrementAndGet()
                    checkAndNotifyProgress()
                }
            }
        }
    }

    private suspend fun processWithOcr(bitmap: Bitmap, captureIndex: Int) {
        pipeline.recognizeOcr(bitmap)
            .onSuccess { recognizedText ->
                bitmap.recycle()
                if (!dedupeAndTrack(recognizedText, captureIndex)) return
                fetchAnswer(recognizedText, captureIndex)
                _totalQuestions.incrementAndGet()
            }
            .onFailure {
                bitmap.recycle()
                AppLog.e("REC", "OCR failed for Q$captureIndex", it)
            }
    }

    private suspend fun processWithVlm(bitmap: Bitmap, captureIndex: Int) {
        AppLog.enter("REC", "recordingProcessWithVlm Q$captureIndex")
        val vlmResult = vlmSemaphore?.withPermit { pipeline.recognizeVlm(bitmap) }
        if (vlmResult == null) return
        vlmResult
            .onSuccess { filter ->
                bitmap.recycle()
                if (!filter.hasQuestions) return
                if (filter.questions.size > 1) {
                    AppLog.i("REC", "VLM found ${filter.questions.size} questions")
                    var skipped = 0
                    filter.questions.forEach { separatedQuestion ->
                        if (!dedupeAndTrack(separatedQuestion.text, captureIndex)) { skipped++; return@forEach }
                        fetchAnswer(separatedQuestion.text, captureIndex, filter)
                        _totalQuestions.incrementAndGet()
                    }
                    if (skipped > 0) _skippedCount.addAndGet(skipped)
                } else {
                    val text = filter.extractedText
                    if (text.isBlank()) return
                    if (!dedupeAndTrack(text, captureIndex)) { _skippedCount.incrementAndGet(); return }
                    fetchAnswer(text, captureIndex, filter)
                _totalQuestions.incrementAndGet()
                }
            }
            .onFailure {
                AppLog.w("REC", "VLM分析失败，降级为OCR")
                withContext(Dispatchers.Main) {
                    callbacks.onError(callbacks.getString(R.string.status_vision_fallback))
                }
                processWithOcr(bitmap, captureIndex)
            }
    }

    /** 去重检查。返回 true 表示新题，false 表示重复 */
    private fun dedupeAndTrack(text: String, captureIndex: Int): Boolean {
        val normalized = normalizeForDedupe(text)
        synchronized(stateLock) {
            val alreadyExists = textHashes.contains(normalized)
            if (!alreadyExists) textHashes.add(normalized)
            else AppLog.d("REC", "去重: 第$captureIndex 题与之前重复，跳过")
            return !alreadyExists
        }
    }

    private fun fetchAnswer(
        text: String, captureIndex: Int,
        visionResult: com.hwb.aianswerer.api.vision.VisionFilterResult? = null
    ) {
        activeJobCount.incrementAndGet()
        AppLog.enter("REC", "recordingFetchAnswer Q$captureIndex")
        val job = scope.launch(Dispatchers.IO) {
            llmSemaphore?.withPermit {
                try {
                    if (!OpenAIClient.isNetworkAvailable()) {
                        _failedCount.incrementAndGet()
                        return@withPermit
                    }
                    val questionTypes = AppConfig.getQuestionTypes()
                    var searchContext = ""
                    // 预搜索模式才预搜索；工具模式下由 LLM 自主调用搜索工具
                    if (!pipeline.isSearchToolModeActive()) {
                        if (visionResult != null && visionResult.searchKeywords.isNotBlank()
                            && callbacks.isSearchEnabled()) {
                            searchContext = pipeline.searchWeb(visionResult.searchKeywords)
                        } else if (visionResult == null && callbacks.isSearchEnabled()) {
                            // OCR模式：从文本中提取搜索关键词
                            val lines = text.lines()
                            val multiQuestionPattern = Regex("""[1-9]\s*[.、．]\s*\S""")
                            val isMultiQuestion = AppConfig.isRegexFilterEnabled() && multiQuestionPattern.containsMatchIn(text)
                            if (!isMultiQuestion) {
                                val questionLine = lines.firstOrNull { it.contains("?") || it.contains("？") }?.trim()
                                val optionLines = lines.filter { it.trim().matches(Regex("""^[A-Da-d][.、．)\s].*""")) }.map { it.trim() }
                                val searchQuery = if (!questionLine.isNullOrBlank()) {
                                    (listOf(questionLine) + optionLines).joinToString(" ")
                                } else {
                                    text
                                }
                                AppLog.d("REC", "Web搜索(OCR): $searchQuery")
                                searchContext = pipeline.searchWeb(searchQuery)
                            } else {
                                AppLog.d("REC", "多题正则过滤: 跳过OCR搜索")
                            }
                        }
                    }
                    val result = pipeline.askLlm(
                        text, questionTypes, searchContext,
                        systemPrompt = Constants.buildRecordingSystemPrompt(
                            captureIndex, captureCount, questionTypes, searchContext
                        )
                    )
                    result.onSuccess { aiAnswers -> storeAnswer(aiAnswers, captureIndex) }
                        .onFailure { error ->
                            _failedCount.incrementAndGet()
                            AppLog.e("REC", "answer failed for Q$captureIndex", error)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _failedCount.incrementAndGet()
                    AppLog.e("REC", "fetch failed", e)
                }
            }
        }
        jobs.add(job)
        job.invokeOnCompletion {
            jobs.remove(job)
            activeJobCount.decrementAndGet()
            AppLog.d("REC", "计数器-1(API完成): activeJobs=${activeJobCount.get()}")
            if (isProcessing && jobs.isEmpty()) {
                ensureResultsNotified()
            }
        }
    }

    private fun storeAnswer(aiAnswers: List<AIAnswer>?, captureIndex: Int) {
        if (aiAnswers == null || aiAnswers.isEmpty()) return
        val displayEntry = buildString {
            append("━━━ 第 $captureIndex 题 ━━━\n")
            if (aiAnswers.size == 1) {
                append(aiAnswers.first().formatAnswerWithConfig(showQuestion = true, showOptions = true))
            } else {
                aiAnswers.forEachIndexed { i, a ->
                    if (i > 0) append("\n\n")
                    append("━━━ 第 ${captureIndex}-${i + 1} 题 ━━━\n")
                    append(a.formatAnswerWithConfig(showQuestion = true, showOptions = true))
                }
            }
        }
        val copyEntry = if (aiAnswers.size == 1) {
            "第${captureIndex}题：${aiAnswers.first().answer}"
        } else {
            aiAnswers.mapIndexed { i, a -> "第${captureIndex}-${i + 1}题：${a.answer}" }.joinToString("\n")
        }
        answers.add(captureIndex to displayEntry)
        copyTexts.add(captureIndex to copyEntry)
    }

    private fun notifyResults() {
        callbacks.onResultsAvailable(
            answers = answers.sortedBy { it.first },
            copyTexts = copyTexts.sortedBy { it.first },
            total = captureCount,
            skipped = skippedCount,
            failed = failedCount
        )
    }

    private fun checkAndNotifyProgress() {
        val done = processedCount
        val total = captureCount
        val answersSoFar = answers.size
        if (done >= total && jobs.isEmpty()) {
            ensureResultsNotified()
        } else {
            callbacks.onProgressUpdate(answersSoFar, totalQuestions)
        }
    }

    fun cancel() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        isActive = false
        isProcessing = false
        // Clean up state to prevent stale data on next start
        textHashes.clear()
        answers.clear()
        copyTexts.clear()
        _captureCount.set(0)
        _processedCount.set(0)
        _skippedCount.set(0)
        _failedCount.set(0)
        _totalQuestions.set(0)
        resultsNotified.set(false) // M9: 复位幂等标志，避免下次录制不通知
    }

    companion object {
        fun normalizeForDedupe(text: String): String = DedupNormalizer.normalize(text)
    }
}
