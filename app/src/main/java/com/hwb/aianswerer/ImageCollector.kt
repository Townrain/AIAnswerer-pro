package com.hwb.aianswerer

import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.utils.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
/**
 * 多图文本收集器 — 收集每次截图/屏幕读取提取的文字，去重后合并发送给 LLM。
 *
 * 与 RecordingCoordinator 的区别：
 *   - Recording: 每次截图独立 VLM/OCR → 独立 LLM 答题 → 逐题展示
 *   - ImageCollector: 每次截图独立识别文字 → 去重累积 → stop 时合并所有文本 → 单次 LLM 答题
 *
 * 适用场景：长文阅读题目，一张图切不下，需要多次截图收集完整题干。
 *
 * 流程：
 *   1. start() → 开始采集
 *   2. 每次点击主按钮 → CaptureHandler 识别文字 → addText() 去重收集
 *   3. stop() → 合并所有文本 → LLM 答题
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
        fun onProgressUpdate(collected: Int)
    }

    companion object {
        private const val MAX_COLLECT_COUNT = 10
    }

    @Volatile var isActive = false
        private set
    @Volatile var isProcessing = false
        private set

    private val collectedTexts = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val textHashes = mutableSetOf<String>()
    private val stateLock = Any()
    private val collectCount = AtomicInteger(0)
    private var processingJob: Job? = null

    fun getCollectedCount(): Int = collectCount.get()
    fun getActiveJobCount(): Int = if (isProcessing) 1 else 0

    /** 开始采集 */
    fun start() {
        isActive = true
        collectCount.set(0)
        collectedTexts.clear()
        textHashes.clear()
        isProcessing = false
        AppLog.i("IMG", "ImageCollector started")
    }

    /** 收集一段已识别的文本（去重） */
    fun addText(text: String) {
        if (!isActive) return
        if (text.isBlank()) return

        if (collectCount.get() >= MAX_COLLECT_COUNT) {
            callbacks.onToast("已达到最大收集数量 ($MAX_COLLECT_COUNT)")
            return
        }

        val normalized = RecordingCoordinator.normalizeForDedupe(text)
        synchronized(stateLock) {
            if (textHashes.contains(normalized)) {
                AppLog.d("IMG", "去重: 与已收集内容重复，跳过")
                return
            }
            textHashes.add(normalized)
        }
        val idx = collectCount.incrementAndGet()
        collectedTexts.add(text)
        callbacks.onProgressUpdate(idx)
        AppLog.d("IMG", "collected text #$idx (${text.length} chars)")
    }

    /** 停止采集并开始合并分析 */
    fun stop() {
        isActive = false
        if (collectedTexts.isEmpty()) {
            callbacks.onToast("未收集到内容")
            return
        }
        isProcessing = true
        processingJob = scope.launch {
            try {
                callbacks.onProgressUpdate(-1) // -1 = 分析中
                val combinedText = collectedTexts.joinToString("\n\n---\n\n")
                AppLog.i("IMG", "analysing combined text, ${collectedTexts.size} segments, ${combinedText.length} chars")

                val questionTypes = AppConfig.getQuestionTypes()
                val result = withContext(Dispatchers.IO) {
                    pipeline.askLlm(combinedText, questionTypes, searchContext = "")
                }
                result.onSuccess { answers ->
                    callbacks.onResult(answers)
                }.onFailure { e ->
                    AppLog.e("IMG", "LLM analysis failed", e)
                    callbacks.onError("AI分析失败: ${e.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                callbacks.onError("分析失败: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    /** 取消并清理 */
    fun cancel() {
        isActive = false
        isProcessing = false
        processingJob?.cancel()
        processingJob = null
        collectedTexts.clear()
        textHashes.clear()
        collectCount.set(0)
}

}
