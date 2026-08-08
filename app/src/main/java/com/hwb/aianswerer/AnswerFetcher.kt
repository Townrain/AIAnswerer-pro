package com.hwb.aianswerer

import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.vision.SeparatedQuestion
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.ui.components.FloatingStatus
import com.hwb.aianswerer.utils.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

// ── Result types ──────────────────────────────────────────────────────

/** Outcome of a single-question fetch. */
sealed class AnswerResult {
    data class Success(val answers: List<AIAnswer>) : AnswerResult()
    data class Error(val message: String) : AnswerResult()
}

// ── UI callbacks (all run on Main) ─────────────────────────────────────

interface AnswerFetcherCallbacks {
    /** Update floating status + optional status message. */
    fun onStatus(status: FloatingStatus, message: String?)
    /** Show a temporary toast-sized message. */
    fun onToast(message: String)
    /** Show a dismissible error message. */
    fun onError(message: String)
    /** Check whether web-search is currently toggled on. */
    fun isSearchEnabled(): Boolean
}

// ── Fetcher ───────────────────────────────────────────────────────────

/**
 * Owns answer-fetching logic: single/multi-question, sequential/parallel,
 * search-context building, and the [fetchMutex] that serialises top-level
 * requests.
 */
class AnswerFetcher(
    private val pipeline: CapturePipeline,
    private val scope: CoroutineScope,
    private val callbacks: AnswerFetcherCallbacks
) {
    private val fetchMutex = Mutex()

    // ── Public API ─────────────────────────────────────────────────────

    /** Cancellable. Reports result through [callback]. */
    fun fetchAnswer(
        text: String,
        visionResult: VisionFilterResult? = null,
        callback: (AnswerResult) -> Unit
    ) {
        scope.launch {
            fetchMutex.withLock {
                try {
                    if (!OpenAIClient.isNetworkAvailable()) {
                        callback(AnswerResult.Error("网络不可用"))
                        return@withLock
                    }

                    val questionTypes = AppConfig.getQuestionTypes()

                    // Multi-question VLM path
                    if (visionResult != null && visionResult.questions.size > 1) {
                        val result = fetchMultiQuestion(visionResult, questionTypes)
                        callback(result)
                        return@withLock
                    }

                    // Single question — build search context（仅预搜索模式；工具模式下由模型自主调用）
                    var searchContext = ""
                    if (!pipeline.isSearchToolModeActive()) {
                        if (visionResult != null) {
                            if (visionResult.searchKeywords.isNotBlank() && callbacks.isSearchEnabled()) {
                                callbacks.onStatus(FloatingStatus.Searching, "搜索中…")
                                searchContext = pipeline.searchWeb(visionResult.searchKeywords)
                            }
                        } else if (callbacks.isSearchEnabled()) {
                            callbacks.onStatus(FloatingStatus.Searching, "搜索中…")
                            val lines = text.lines()
                            val questionLine = lines.firstOrNull { it.contains("?") || it.contains("？") }?.trim()
                            val optionLines = lines
                                .filter { it.trim().matches(Regex("""^[A-Da-d][.、．)\s].*""")) }
                                .map { it.trim() }
                            val query = if (!questionLine.isNullOrBlank()) {
                                (listOf(questionLine) + optionLines).joinToString(" ")
                            } else text
                            searchContext = pipeline.searchWeb(query)
                        }
                    }

                    callbacks.onStatus(FloatingStatus.GettingAnswer, "获取答案中…")

                    val result = withTimeout(60_000L) {
                        pipeline.askLlm(text, questionTypes, searchContext)
                    }

                    result
                        .onSuccess { answers -> callback(AnswerResult.Success(answers)) }
                        .onFailure { error ->
                            AppLog.e("AnswerFetcher", "LLM request failed: ${error.message}", error)
                            callback(AnswerResult.Error(
                                "AI分析失败: ${error.message ?: ""}"
                            ))
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("AnswerFetcher", "fetchAnswer unexpected: ${e.message}", e)
                    callback(AnswerResult.Error("获取答案失败: ${e.message ?: ""}"))
                }
            }
        }
    }

    // ── Multi-question dispatch ────────────────────────────────────────

    private suspend fun fetchMultiQuestion(
        visionResult: VisionFilterResult,
        questionTypes: Set<String>
    ): AnswerResult {
        val questions = visionResult.questions.filter { it.text.isNotBlank() }
        val totalQuestions = questions.size

        return if (AppConfig.isParallelModeEnabled()) {
            fetchParallel(questions, questionTypes, totalQuestions)
        } else {
            fetchSequential(questions, questionTypes, totalQuestions)
        }
    }

    private suspend fun fetchSequential(
        questions: List<SeparatedQuestion>,
        questionTypes: Set<String>,
        totalQuestions: Int
    ): AnswerResult {
        val allAnswers = mutableListOf<AIAnswer>()

        for ((idx, question) in questions.withIndex()) {
            var searchContext = ""
            if (!pipeline.isSearchToolModeActive() && question.searchKeywords.isNotBlank() && callbacks.isSearchEnabled()) {
                callbacks.onStatus(FloatingStatus.Searching,
                    "搜索中 (${idx + 1}/$totalQuestions)")
                searchContext = pipeline.searchWeb(question.searchKeywords, 2)
            }

            callbacks.onStatus(FloatingStatus.GettingAnswer,
                "获取答案中 (${idx + 1}/$totalQuestions)")
            val result = pipeline.askLlm(question.text, questionTypes, searchContext)

            result.onSuccess { answers -> allAnswers.addAll(answers) }
                .onFailure { error ->
                    AppLog.e("AnswerFetcher", "题目${idx + 1}答题失败: ${error.message}")
                }
        }

        return if (allAnswers.isNotEmpty()) AnswerResult.Success(allAnswers)
        else AnswerResult.Error("所有题目答题失败")
    }

    private suspend fun fetchParallel(
        questions: List<SeparatedQuestion>,
        questionTypes: Set<String>,
        totalQuestions: Int
    ): AnswerResult {
        val maxConcurrency = AppConfig.getMaxConcurrency()
        val completedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val allAnswers = arrayOfNulls<List<AIAnswer>>(questions.size)
        val semaphore = Semaphore(maxConcurrency)

        kotlinx.coroutines.coroutineScope {
            val jobs = questions.mapIndexed { idx, question ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            var searchContext = ""
                            if (!pipeline.isSearchToolModeActive() && question.searchKeywords.isNotBlank() && callbacks.isSearchEnabled()) {
                                searchContext = pipeline.searchWeb(question.searchKeywords, 2)
                            }
                            val result = pipeline.askLlm(question.text, questionTypes, searchContext)
                            result.onSuccess { answers ->
                                allAnswers[idx] = answers
                            }.onFailure { error ->
                                AppLog.e("AnswerFetcher", "题目${idx + 1}答题失败: ${error.message}")
                                failedCount.incrementAndGet()
                            }
                            val completed = completedCount.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                callbacks.onStatus(FloatingStatus.GettingAnswer,
                                    "答题中 ($completed/$totalQuestions)")
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            // M12: 超时也计入失败，否则部分超时时不提示"部分题目获取失败"
                            failedCount.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                callbacks.onError("超时")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.e("AnswerFetcher", "题目${idx + 1}处理异常: ${e.message}")
                            failedCount.incrementAndGet()
                        }
                    }
                }
            }
            jobs.forEach { job -> job.join() }
        }

        val ordered = allAnswers.filterNotNull().flatten()
        if (ordered.isNotEmpty()) {
            if (failedCount.get() > 0) {
                callbacks.onToast("部分题目获取失败")
            }
            return AnswerResult.Success(ordered)
        }
        return AnswerResult.Error("所有题目答题失败")
    }
}
