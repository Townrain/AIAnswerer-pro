package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 多图采集器 — 与 RecordingCoordinator 同构的生命周期：
 * 截图进站（processBitmap，进站同步生成序号）→ 内部识别（VLM→OCR 降级）→ 按序号收集文本；
 * stop() 只关入口并等待在途识别完成，然后合并所有文本 → 专职去重 LLM → 单次 LLM 答题（幂等，双 stop 安全）。
 *
 * 与 RecordingCoordinator 的区别：
 *   - Recording: 每次截图独立 VLM/OCR → 独立 LLM 答题 → 逐题展示
 *   - ImageCollector: 每次截图独立识别文字 → 按序号累积 → stop 时合并所有文本 → 单次 LLM 答题
 *
 * 适用场景：长文阅读题目，一张图切不下，需要多次截图收集完整题干。
 *
 * 流程：
 *   1. start() → 开始采集（同录制）
 *   2. 每次点击主按钮 → CaptureHandler 截图/读屏 → processBitmap()/processText() 进站
 *   3. stop() → 关入口 → 等在途识别完成 → 合并 → 去重 LLM → 答题 LLM（去重失败降级原始拼接）
 */
class ImageCollector(
    private val pipeline: CapturePipeline,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onError(message: String)
        fun onToast(message: String)
        fun onResult(answers: List<AIAnswer>)
        /** @param collected 已收集段数；@param total 已进站截图数（含识别中/失败的） */
        fun onProgressUpdate(collected: Int, total: Int)
    }

    companion object {
        private const val MAX_COLLECT_COUNT = 10
    }

    @Volatile var isActive = false
        private set
    @Volatile var isProcessing = false
        private set

    private val collectedTexts = java.util.concurrent.ConcurrentSkipListMap<Int, String>()
    private val textHashes = mutableSetOf<String>()
    private val stateLock = Any()
    private val collectCount = AtomicInteger(0)
    /** 截图进站序号 — 进站同步生成，识别响应乱序时用于还原页面顺序 */
    private val captureCount = AtomicInteger(0)
    private val jobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    /** 幂等提交锁 — 保证 stop 只触发一次合并分析（双 stop 安全） */
    private val submitScheduled = AtomicBoolean(false)
    private var notifyJob: Job? = null
    /** P0-3: 会话代次 — start()/cancel() 递增；旧会话识别/提交协程校验代次后丢弃结果，
     *  防止快速重启时旧结果（最长 240s）打进新会话 */
    private val sessionGeneration = AtomicInteger(0)

    fun getCollectedCount(): Int = collectCount.get()
    fun getActiveJobCount(): Int = jobs.size

    /** 开始采集 */
    fun start() {
        // P0-3: 新会话代次 — 使上一会话遗留协程（识别/提交）全部失效
        sessionGeneration.incrementAndGet()
        isActive = true
        collectCount.set(0)
        captureCount.set(0)
        collectedTexts.clear()
        textHashes.clear()
        isProcessing = false
        submitScheduled.set(false)
        notifyJob?.cancel() // 取消上一会话遗留的等待协程
        notifyJob = null
        // P0-3: 取消上一会话遗留识别任务，防止其完成回调把旧文本写入新会话 collectedTexts
        jobs.forEach { it.cancel() }
        jobs.clear()
        AppLog.i("IMG", "ImageCollector started")
    }

    /** 截图进站 — 进站同步生成序号；内部识别（VLM→OCR 降级）后按序号收集文本 */
    fun processBitmap(bitmap: Bitmap) {
        // B7 同款：stop 后丢弃迟到截图，避免内容静默丢失与计数虚增
        if (!isActive) {
            AppLog.d("IMG", "drop late capture (image collecting already stopped)")
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        // P2-1: 并发闸门（与录制分支 maxConcurrency 对齐）— 快速连点时不堆叠识别请求
        if (jobs.size >= AppConfig.getMaxConcurrency()) {
            AppLog.d("IMG", "drop capture: concurrency limit reached (${jobs.size})")
            if (!bitmap.isRecycled) bitmap.recycle()
            scope.launch(Dispatchers.Main) {
                callbacks.onToast("识别繁忙，请稍后再试")
            }
            return
        }
        val pageIndex = captureCount.incrementAndGet() // 进站同步取号，杜绝协程内延迟读取的序号竞态
        val gen = sessionGeneration.get() // P0-3: 捕获进站代次，识别完成回调据此丢弃旧会话结果
        val job = scope.launch(Dispatchers.IO) {
            try {
                pipeline.recognizeToText(bitmap)
                    .onSuccess { text ->
                        // P0-3: 识别完成时代次已变（新会话已启动）→ 丢弃，避免旧文本写入新会话
                        if (gen != sessionGeneration.get()) {
                            AppLog.d("IMG", "drop stale recognition result page=$pageIndex")
                        } else {
                            addCollectedText(pageIndex, text)
                        }
                    }
                    .onFailure { e -> AppLog.e("IMG", "recognize failed for page $pageIndex", e) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("IMG", "process failed for page $pageIndex", e)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        jobs.add(job)
        job.invokeOnCompletion { jobs.remove(job) }
    }

    /** 屏幕读取文本进站（无障碍模式 — 与录制 processText 同级） */
    fun processText(text: String) {
        if (!isActive) return
        val pageIndex = captureCount.incrementAndGet()
        addCollectedText(pageIndex, text)
    }

    /** 处理裁剪后的录图截图（ImageCropActivity 结果 — 与录制 handleCroppedImage 同级） */
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
                callbacks.onError("裁剪失败: ${e.message ?: ""}")
            } finally {
                ImageCropUtil.deleteTempFile(imagePath)
            }
        }
    }

    /** 收集一段已识别的文本（去重 + 按截图序号存储，合并时按序号排序保证材料顺序） */
    private fun addCollectedText(index: Int, text: String) {
        if (text.isBlank()) return

        // P2-2: 先去重再判上限 — 已满后重复页静默跳过，不误报"已达到最大收集数量"
        val normalized = RecordingCoordinator.normalizeForDedupe(text)
        var isDuplicate = false
        var isFull = false
        var idx = 0
        synchronized(stateLock) {
            if (textHashes.contains(normalized)) {
                isDuplicate = true
            } else if (collectCount.get() >= MAX_COLLECT_COUNT) {
                isFull = true
            } else {
                textHashes.add(normalized)
                idx = collectCount.incrementAndGet() // m3: 锁内取号，防止并发超 MAX
            }
        }
        if (isDuplicate) {
            AppLog.d("IMG", "去重: 与已收集内容重复，跳过"); return
        }
        if (isFull) {
            scope.launch(Dispatchers.Main) {
                callbacks.onToast("已达到最大收集数量 ($MAX_COLLECT_COUNT)")
            }
            return
        }
        collectedTexts[index] = text
        scope.launch(Dispatchers.Main) {
            callbacks.onProgressUpdate(idx, captureCount.get())
        }
        AppLog.d("IMG", "collected text #$idx (page=$index, ${text.length} chars)")
    }

    /** 停止采集 — 只关入口；等在途识别完成后合并提交一次（幂等，双 stop 安全） */
    fun stop() {
        isActive = false
        if (isProcessing) {
            AppLog.d("IMG", "stop ignored: already processing")
            return
        }
        if (jobs.isEmpty()) {
            if (collectedTexts.isEmpty()) {
                scope.launch(Dispatchers.Main) { callbacks.onToast("未收集到内容") }
            } else {
                ensureSubmit()
            }
            return
        }
        isProcessing = true
        // 兜底：等待全部在途识别（含识别完成后才加入的文本）完成后统一提交
        notifyJob = scope.launch {
            while (jobs.isNotEmpty()) {
                jobs.toList().forEach { it.join() }
            }
            ensureSubmit()
        }
    }

    /** 幂等提交：合并 → 专职去重 LLM → 答题 LLM（去重失败降级原始拼接） */
    private fun ensureSubmit() {
        if (!submitScheduled.compareAndSet(false, true)) {
            AppLog.d("IMG", "submit already scheduled, skip")
            return
        }
        val gen = sessionGeneration.get() // P0-3: 捕获提交发起代次，提交期间重启会话则丢弃结果
        scope.launch(Dispatchers.Main) {
            try {
                // P0-3: 提交执行前代次已变 → 不展示旧会话的任何 UI 回调
                if (gen != sessionGeneration.get()) {
                    AppLog.d("IMG", "drop stale submit (session restarted)"); return@launch
                }
                callbacks.onProgressUpdate(-1, captureCount.get()) // -1 = 分析中
                val segments = collectedTexts.values.toList()
                if (segments.isEmpty()) {
                    callbacks.onToast("未收集到内容")
                    return@launch
                }
                val combinedText = segments.mapIndexed { i, t ->
                    "【第 ${i + 1}/${segments.size} 页】\n$t"
                }.joinToString("\n\n--- 分页分隔 ---\n\n")
                AppLog.i("IMG", "analysing combined text, ${segments.size} segments, ${combinedText.length} chars")

                val questionTypes = AppConfig.getQuestionTypes()
                val result = withContext(Dispatchers.IO) {
                    // 专职去重 LLM：合并重叠、修正识别误差，输出完整干净文本；失败降级为原始拼接
                    val deduped = pipeline.dedupeText(combinedText)
                    if (deduped.isSuccess) {
                        AppLog.i("IMG", "dedupe ok, clean text ${deduped.getOrThrow().length} chars")
                        pipeline.askLlm(deduped.getOrThrow(), questionTypes, searchContext = "")
                    } else {
                        AppLog.w("IMG", "dedupe failed (${deduped.exceptionOrNull()?.message}), fallback to raw combined text")
                        pipeline.askLlm(combinedText, questionTypes, searchContext = "")
                    }
                }
                // P0-3: 提交结果返回时代次已变 → 丢弃，绝不展示旧会话答案
                if (gen != sessionGeneration.get()) {
                    AppLog.d("IMG", "drop stale submit result (session restarted)"); return@launch
                }
                result.onSuccess { answers ->
                    callbacks.onResult(answers)
                }.onFailure { e ->
                    AppLog.e("IMG", "LLM analysis failed", e)
                    // m4: 仅当前会话的失败才触发错误回调
                    if (gen == sessionGeneration.get()) callbacks.onError("AI分析失败: ${e.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == sessionGeneration.get()) callbacks.onError("分析失败: ${e.message}")
            } finally {
                // m4: 仅当前会话才复位 isProcessing，防止旧提交协程覆写新会话状态
                if (gen == sessionGeneration.get()) isProcessing = false
            }
        }
    }

    /** 取消并清理 */
    fun cancel() {
        sessionGeneration.incrementAndGet() // P0-3: 使在途识别/提交协程全部失效
        isActive = false
        isProcessing = false
        notifyJob?.cancel()
        notifyJob = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        collectedTexts.clear()
        textHashes.clear()
        collectCount.set(0)
        captureCount.set(0)
        submitScheduled.set(false)
    }
}
