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
    var isActive = false
        private set
    var isProcessing = false
        private set
    var captureCount = 0
        private set
    var processedCount = 0
        private set
    var skippedCount = 0
        private set
    var failedCount = 0
        private set
    var totalQuestions = 0
        private set

    private val answers = mutableListOf<Pair<Int, String>>()
    private val copyTexts = mutableListOf<Pair<Int, String>>()
    private val textHashes = mutableSetOf<String>()
    private val jobs = CopyOnWriteArrayList<Job>()
    val activeJobCount = AtomicInteger(0)
    private var llmSemaphore: Semaphore? = null
    private var vlmSemaphore: Semaphore? = null

    /** 开始录题 */
    fun start() {
        isActive = true
        captureCount = 0
        processedCount = 0
        skippedCount = 0
        failedCount = 0
        totalQuestions = 0
        answers.clear()
        copyTexts.clear()
        textHashes.clear()
        jobs.clear()
        activeJobCount.set(0)
        val maxConcurrency = AppConfig.getMaxConcurrency()
        llmSemaphore = Semaphore(maxConcurrency)
        vlmSemaphore = Semaphore(maxConcurrency)
        AppLog.i("REC", "startRecording maxConcurrency=$maxConcurrency")
    }

    /** 停止录题 — 返回是否需要等待处理完成的标志 */
    fun stop(): StopResult {
        isActive = false
        AppLog.i("REC", "stopRecording captured=$captureCount processed=$processedCount answers=${answers.size}")
        if (captureCount == 0) return StopResult.NothingToShow
        if (jobs.isEmpty()) {
            notifyResults()
            return StopResult.Completed
        }
        isProcessing = true
        return StopResult.Processing(captureCount, processedCount)
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
        val captureIndex = captureCount
        captureCount++
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
                    processedCount++
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
                totalQuestions++
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
                        totalQuestions++
                    }
                    if (skipped > 0) skippedCount += skipped
                } else {
                    val text = filter.extractedText
                    if (text.isBlank()) return
                    if (!dedupeAndTrack(text, captureIndex)) { skippedCount++; return }
                    fetchAnswer(text, captureIndex, filter)
                    totalQuestions++
                }
            }
            .onFailure {
                AppLog.w("REC", "VLM分析失败，降级为OCR")
                callbacks.onError(callbacks.getString(R.string.status_vision_fallback))
                processWithOcr(bitmap, captureIndex)
            }
    }

    /** 去重检查。返回 true 表示新题，false 表示重复 */
    private fun dedupeAndTrack(text: String, captureIndex: Int): Boolean {
        val normalized = normalizeForDedupe(text)
        val alreadyExists = textHashes.contains(normalized)
        if (!alreadyExists) textHashes.add(normalized)
        else AppLog.d("REC", "去重: 第$captureIndex 题与之前重复，跳过")
        return !alreadyExists
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
                        failedCount++
                        return@withPermit
                    }
                    val questionTypes = AppConfig.getQuestionTypes()
                    var searchContext = ""
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
                    val result = pipeline.askLlm(
                        text, questionTypes, searchContext,
                        systemPrompt = Constants.buildRecordingSystemPrompt(
                            captureIndex, captureCount, questionTypes, searchContext
                        )
                    )
                    result.onSuccess { aiAnswers -> storeAnswer(aiAnswers, captureIndex) }
                        .onFailure { error ->
                            failedCount++
                            AppLog.e("REC", "answer failed for Q$captureIndex", error)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failedCount++
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
                scope.launch(Dispatchers.Main) { notifyResults() }
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
        answers += captureIndex to displayEntry
        copyTexts += captureIndex to copyEntry
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
            notifyResults()
        } else {
            callbacks.onProgressUpdate(answersSoFar, totalQuestions)
        }
    }

    fun cancel() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        isActive = false
        isProcessing = false
    }

    companion object {
        fun normalizeForDedupe(text: String): String = DedupNormalizer.normalize(text)
    }
}
