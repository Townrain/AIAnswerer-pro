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
import kotlinx.coroutines.withTimeoutOrNull
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
            total: Int, skipped: Int, failed: Int,
            isFinal: Boolean
        )
        fun onProgressUpdate(processed: Int, total: Int)
        fun getString(resId: Int, vararg args: Any?): String
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
    // P0-2: 保存 stop 兜底通知协程引用，start()/cancel() 时取消，防止快速重启时旧协程吞掉新会话通知
    @Volatile private var notifyJob: Job? = null
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
        resultsNotified.set(false) // M9: 复位幂等标志，避免连续两次录制时第二次不通知
        notifyJob?.cancel() // P0-2: 取消上一会话遗留的兜底通知协程，防止其 CAS 抢占新会话通知
        notifyJob = null
        isProcessing = false // P0-2: 复位处理中标志，避免快速重启时沿用旧会话的 isProcessing
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
                // M11: partial 通知 — 先同步进度计数（状态消息/卡片 header 显示 x/N），再展示答案卡
                // B3: 分母与完成路径一致（maxOf），避免一图多题时 answers.size > captureCount 出现 "2/1"
                callbacks.onProgressUpdate(answers.size, maxOf(captureCount, totalQuestions))
                AppLog.d("REC", "stop: partial results (${answers.size}) shown immediately"); notifyResults(isFinal = false)
            }
        }
        // M9: 兜底——所有 job 去重/失败时 notifyResults 可能永不触发（isProcessing && jobs.isEmpty() 条件不满足），
        //     启动兜底协程：jobs 清空后强制 notifyResults（幂等，防止与正常路径重复触发）
        notifyJob = scope.launch {
            // M10: 动态 join — stop 后 VLM/OCR job 完成时仍会通过 fetchAnswer 创建新的答案 job，
            //     必须等待全部 job（含后加入的答案 job）完成后才通知，避免"全部完成"提前弹出、
            //     结果卡停留在部分答案
            while (jobs.isNotEmpty()) {
                jobs.toList().forEach { it.join() }
            }
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
        // B7: stop 后丢弃迟到截图，避免答案静默丢失与计数虚增
        if (!isActive) {
            AppLog.d("REC", "drop late capture (recording already stopped)")
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
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
        // B7: stop 后丢弃迟到文本，避免计数虚增
        if (!isActive) {
            AppLog.d("REC", "drop late text (recording already stopped)")
            return
        }
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
                        fetchAnswer(separatedQuestion.text, captureIndex)
                        _totalQuestions.incrementAndGet()
                    }
                    if (skipped > 0) _skippedCount.addAndGet(skipped)
                } else {
                    val text = filter.extractedText
                    if (text.isBlank()) return
                    if (!dedupeAndTrack(text, captureIndex)) { _skippedCount.incrementAndGet(); return }
                    fetchAnswer(text, captureIndex)
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

    private fun fetchAnswer(text: String, captureIndex: Int) {
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
                    // 仅工具模式：联网搜索由 LLM 自主调用工具完成（预搜索注入已移除）
                    // P1-1: 录制路径外层超时兜底（放宽至工具循环上限 ~240s，不掐断合法多轮搜索），超时计失败
                    val result = withTimeoutOrNull(recordingAnswerTimeoutMs) {
                        pipeline.askLlm(
                            text, questionTypes, "",
                            systemPrompt = Constants.buildRecordingSystemPrompt(
                                captureIndex, captureCount, questionTypes, ""
                            )
                        )
                    }
                    if (result == null) {
                        _failedCount.incrementAndGet()
                        AppLog.e("REC", "recording answer timed out after ${recordingAnswerTimeoutMs}ms for Q$captureIndex")
                        return@withPermit
                    }
                    result.onSuccess { aiAnswers ->
                        if (aiAnswers.isEmpty()) {
                            // 空答案（如工具伪文本解析失败）计入失败，避免静默丢失
                            _failedCount.incrementAndGet()
                            AppLog.w("REC", "empty answers for Q$captureIndex, counted as failed")
                        } else {
                            storeAnswer(aiAnswers, captureIndex)
                        }
                    }
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
            if (isProcessing) {
                // M12: 每个答案完成也走进度检查，保证 (x/N) 进度随答案增长更新，
                //     而不是从 partial 直接跳到"全部完成"
                scope.launch(Dispatchers.Main) { checkAndNotifyProgress() }
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

    private fun notifyResults(isFinal: Boolean = true) {
        callbacks.onResultsAvailable(
            answers = answers.sortedBy { it.first },
            copyTexts = copyTexts.sortedBy { it.first },
            total = maxOf(captureCount, totalQuestions), // P1-2a: 与进度分母一致，一图多题时 total 用已识别题数
            skipped = skippedCount,
            failed = failedCount,
            isFinal = isFinal
        )
    }

    private fun checkAndNotifyProgress() {
        val done = processedCount
        val total = captureCount
        val answersSoFar = answers.size
        if (done >= total && jobs.isEmpty()) {
            ensureResultsNotified()
        } else {
            // 进度分母 = max(截图数, 已识别题数)，恒定不变（避免分母随识别结果动态增长造成"识别不全"错觉），
            //     且一图多题场景不会出现已完成数超过分母的荒谬显示
            callbacks.onProgressUpdate(answersSoFar, maxOf(captureCount, totalQuestions))
        }
    }

    fun cancel() {
        notifyJob?.cancel() // P0-2: 取消兜底通知协程
        notifyJob = null
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
        // P1-1: 录制路径答题外层超时（放宽至工具循环上限，默认 (MAX_TOOL_ROUNDS+2) × 60s ≈ 240s）
        @Volatile internal var recordingAnswerTimeoutMs: Long =
            (OpenAIClient.MAX_TOOL_ROUNDS + 2) * OpenAIClient.WITH_TIMEOUT_MS
    }
}
