package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.vision.SeparatedQuestion
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingCoordinatorTest {

    private val pipeline = mockk<CapturePipeline>(relaxed = true)
    private val callbacks = mockk<RecordingCoordinator.Callbacks>(relaxed = true)
    private lateinit var coordinator: RecordingCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(AppConfig)
        mockkObject(OpenAIClient)
        mockkObject(Constants)
        mockkObject(MyApplication.Companion)
        every { AppConfig.getMaxConcurrency() } returns 3
        every { AppConfig.isVisionEnabled() } returns false
        every { AppConfig.getQuestionTypes() } returns setOf("选择题")
        coEvery { OpenAIClient.isNetworkAvailable() } returns true
        every { Constants.buildRecordingSystemPrompt(any(), any(), any(), any()) } returns "recording_system_prompt"
        every { MyApplication.getString(any<Int>()) } returns "MOCK_LABEL"
        every { MyApplication.getString(any<Int>(), *anyVararg()) } returns "MOCK_LABEL"
        every { callbacks.getString(any(), *anyVararg()) } returns "mock_string"
        every { callbacks.getString(any()) } returns "mock_string"
        coordinator = RecordingCoordinator(pipeline, CoroutineScope(Dispatchers.Unconfined), callbacks)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockBitmap(): Bitmap = mockk<Bitmap>(relaxed = true).also {
        every { it.isRecycled } returns false
    }

    private fun mockAnswer() = AIAnswer("test question?", "选择题", "A", listOf("A. opt1", "B. opt2"))

    // ===========================================================
    //  State Machine Tests
    // ===========================================================

    @Test
    fun start_resets_all_counters_and_creates_semaphores() {
        coordinator.start()

        assertTrue(coordinator.isActive)
        assertEquals(0, coordinator.captureCount)
        assertEquals(0, coordinator.processedCount)
        assertEquals(0, coordinator.skippedCount)
        assertEquals(0, coordinator.failedCount)
        assertEquals(0, coordinator.totalQuestions)
        assertEquals(0, coordinator.getActiveJobCount())
    }

    @Test
    fun stop_with_zero_captures_returns_NothingToShow() {
        coordinator.start()
        val result = coordinator.stop()

        assertTrue(result is RecordingCoordinator.StopResult.NothingToShow)
        assertFalse(coordinator.isActive)
    }

    @Test
    fun stop_with_captures_no_pending_jobs_returns_Completed_and_notifies() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("test question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        val latch = CountDownLatch(1)
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers { latch.countDown() }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        val result = coordinator.stop()
        assertTrue(result is RecordingCoordinator.StopResult.Completed)
        assertFalse(coordinator.isActive)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun second_recording_still_notifies_results() = runBlocking {
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("test question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        val notifyCount = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = CountDownLatch(2)
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            notifyCount.incrementAndGet()
            latch.countDown()
        }

        // 第一次录制
        coordinator.start()
        coordinator.processBitmap(mockBitmap())
        delay(10)
        val first = coordinator.stop()
        assertTrue(first is RecordingCoordinator.StopResult.Completed)

        // 第二次录制：start() 必须复位 resultsNotified，否则第二次 stop 永不通知
        coordinator.start()
        coordinator.processBitmap(mockBitmap())
        delay(10)
        val second = coordinator.stop()
        assertTrue(second is RecordingCoordinator.StopResult.Completed)

        assertEquals(2, notifyCount.get())
    }

    @Test
    fun processBitmap_after_stop_is_dropped() = runBlocking {
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("test question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.start()
        coordinator.stop() // captureCount == 0 → NothingToShow，isActive=false

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals("stop 后迟到截图不应计数", 0, coordinator.captureCount)
        coVerify(exactly = 0) { pipeline.recognizeOcr(any()) }
    }

    @Test
    fun stop_with_pending_jobs_returns_Processing() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } coAnswers {
            delay(50)
            Result.success("test")
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        val result = coordinator.stop()
        assertTrue(result is RecordingCoordinator.StopResult.Processing)
        val proc = result as RecordingCoordinator.StopResult.Processing
        assertEquals(1, proc.captureCount)
        assertEquals(0, proc.processedCount)
        assertTrue(coordinator.isProcessing)
    }

    @Test
    fun cancel_cancels_all_jobs_and_clears_state() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } coAnswers {
            delay(Long.MAX_VALUE)
            Result.success("test")
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)
        assertTrue(coordinator.isActive)

        coordinator.cancel()
        delay(10)

        assertFalse(coordinator.isActive)
        assertFalse(coordinator.isProcessing)
        assertEquals(0, coordinator.getActiveJobCount())
    }

    // ===========================================================
    //  Dedup Tests
    // ===========================================================

    @Test
    fun normalizeForDedupe_delegates_to_DedupNormalizer() {
        val input = "  Hello, World!  "
        val expected = DedupNormalizer.normalize(input)
        assertEquals(expected, RecordingCoordinator.normalizeForDedupe(input))
    }

    @Test
    fun dedupeAndTrack_new_text_accepted_and_processed() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("unique question text")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals(1, coordinator.totalQuestions)
        coVerify(exactly = 1) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    @Test
    fun dedupeAndTrack_duplicate_text_prevents_second_fetchAnswer() = runBlocking {
        coordinator.start()
        val text = "same question text"
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success(text)
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals(1, coordinator.totalQuestions)
        coVerify(exactly = 1) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    // ===========================================================
    //  VLM Path Tests
    // ===========================================================

    @Test
    fun processWithVlm_success_single_question_dedupes_and_fetches() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            extractedText = "VLM extracted text",
            searchKeywords = "search keywords"
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coVerify { pipeline.recognizeVlm(any()) }
        coVerify { pipeline.askLlm(any(), any(), any(), any()) }
        assertEquals(1, coordinator.totalQuestions)
    }

    @Test
    fun processWithVlm_success_multi_question_each_fetched() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            questions = listOf(
                SeparatedQuestion(index = 1, text = "Q1 text", searchKeywords = "kw1"),
                SeparatedQuestion(index = 2, text = "Q2 text", searchKeywords = "kw2"),
                SeparatedQuestion(index = 3, text = "Q3 text", searchKeywords = "kw3")
            )
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals(3, coordinator.totalQuestions)
        coVerify(exactly = 3) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    @Test
    fun stop_partial_progress_denominator_uses_max_capture_and_questions() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            questions = listOf(
                SeparatedQuestion(index = 1, text = "Q1 text", searchKeywords = "kw1"),
                SeparatedQuestion(index = 2, text = "Q2 text", searchKeywords = "kw2")
            )
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)
        // 第一题答案快速返回，第二题延迟（保证 stop 时只有 1 条答案在卡上、1 个 job 在途）
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            if (callCount.incrementAndGet() == 1) Result.success(listOf(mockAnswer())) else {
                delay(100)
                Result.success(listOf(mockAnswer()))
            }
        }
        val progressTotals = mutableListOf<Int>()
        every { callbacks.onProgressUpdate(any(), capture(progressTotals)) } returns Unit

        coordinator.processBitmap(mockBitmap())
        delay(10)
        coordinator.stop()
        delay(20)

        // 一图多题：分母必须是 maxOf(captureCount=1, totalQuestions=2) = 2，绝不出现 "1" 或 "0"
        assertTrue("partial 进度分母必须为 2: $progressTotals", progressTotals.isNotEmpty() && progressTotals.all { it == 2 })
    }

    @Test
    fun processWithVlm_multi_question_skips_duplicate_within_batch() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            questions = listOf(
                SeparatedQuestion(index = 1, text = "duplicate text", searchKeywords = "kw1"),
                SeparatedQuestion(index = 2, text = "duplicate text", searchKeywords = "kw2"),
                SeparatedQuestion(index = 3, text = "unique text", searchKeywords = "kw3")
            )
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        // 全量并行测试下 IO 线程池竞争，固定 delay 不可靠：轮询等待 VLM 回调完成
        while (coordinator.totalQuestions < 2) { delay(10) }

        assertEquals(2, coordinator.totalQuestions)
        assertEquals(1, coordinator.skippedCount)
    }

    @Test
    fun processWithVlm_hasQuestions_false_returns_without_processing() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(hasQuestions = false, questionCount = 0)
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals(0, coordinator.totalQuestions)
        coVerify(exactly = 0) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    @Test
    fun processWithVlm_extractedText_blank_returns_without_processing() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            extractedText = "   ",
            questions = emptyList()
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals(0, coordinator.totalQuestions)
        coVerify(exactly = 0) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    @Test
    fun processWithVlm_failure_degrades_to_OCR() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()

        coEvery { pipeline.recognizeVlm(any()) } returns Result.failure(RuntimeException("VLM fail"))
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("OCR fallback text")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coVerify { pipeline.recognizeVlm(any()) }
        coVerify { pipeline.recognizeOcr(any()) }
        assertEquals(1, coordinator.totalQuestions)
    }

    // ===========================================================
    //  OCR Path Tests
    // ===========================================================

    @Test
    fun processWithOcr_success_dedupes_and_fetches_answer() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("OCR recognized text")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coVerify { pipeline.recognizeOcr(any()) }
        coVerify { pipeline.askLlm(any(), any(), any(), any()) }
        assertEquals(1, coordinator.totalQuestions)
    }

    @Test
    fun processWithOcr_failure_does_not_fetch_answer() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.failure(RuntimeException("OCR fail"))

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coVerify(exactly = 0) { pipeline.askLlm(any(), any(), any(), any()) }
        assertEquals(0, coordinator.totalQuestions)
    }

    // ===========================================================
    //  fetchAnswer Tests
    // ===========================================================
    // ===========================================================
    //  fetchAnswer Tests
    // ===========================================================

    @Test
    fun fetchAnswer_network_unavailable_increments_failedCount() = runBlocking {
        coEvery { OpenAIClient.isNetworkAvailable() } returns false
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")

        coordinator.processBitmap(mockBitmap())
        delay(10)

        assertEquals(1, coordinator.failedCount)
        coVerify(exactly = 0) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    @Test
    fun fetchAnswer_success_stores_answer_and_notifies_on_stop() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question text")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(AIAnswer("What is X?", "选择题", "B", listOf("A. a", "B. b", "C. c")))
        )

        val latch = CountDownLatch(1)
        val receivedAnswers = mutableListOf<List<Pair<Int, String>>>()
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        val result = coordinator.stop()
        assertTrue(result is RecordingCoordinator.StopResult.Completed)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, receivedAnswers.size)
        assertTrue(receivedAnswers[0].isNotEmpty())
    }

    @Test
    fun fetchAnswer_failure_increments_failedCount_and_is_reported() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("LLM error"))

        val latch = CountDownLatch(1)
        val receivedFailed = mutableListOf<Int>()
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            receivedFailed.add(invocation.args[4] as Int)
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coordinator.stop()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, receivedFailed[0])
    }

    // ===========================================================
    //  storeAnswer Tests
    // ===========================================================

    @Test
    fun storeAnswer_single_answer_formats_with_question_and_options() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question")
        val answer = AIAnswer("What is X?", "选择题", "C", listOf("A. a", "B. b", "C. c"))
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer))

        val latch = CountDownLatch(1)
        val receivedAnswers = mutableListOf<List<Pair<Int, String>>>()
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coordinator.stop()
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        val displayText = receivedAnswers[0].first().second
        assertTrue("Should contain answer letter C", displayText.contains("C"))
        assertEquals(1, receivedAnswers[0].size)
    }

    @Test
    fun storeAnswer_multiple_answers_formats_each_with_sub_index() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question")
        val answers = listOf(
            AIAnswer("Q1", "选择题", "A"),
            AIAnswer("Q2", "填空题", "hello")
        )
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(answers)

        val latch = CountDownLatch(1)
        val receivedAnswers = mutableListOf<List<Pair<Int, String>>>()
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coordinator.stop()
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        val displayText = receivedAnswers[0].first().second
        assertTrue("Should contain sub-index -1", displayText.contains("-1"))
        assertTrue("Should contain sub-index -2", displayText.contains("-2"))
    }

    @Test
    fun storeAnswer_null_or_empty_list_does_not_add_to_results() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(emptyList())

        val latch = CountDownLatch(1)
        val receivedAnswers = mutableListOf<List<Pair<Int, String>>>()
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)

        coordinator.stop()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(receivedAnswers[0].isEmpty())
    }

    // ===========================================================
    //  防呆回归: 结束录制时部分答案立即展示 + 误触保护
    @Test
    fun stop_with_pending_jobs_and_partial_answers_notifies_immediately() = runBlocking {
        // 场景: 第1题已完成, 第2题仍在途 — stop 时应立即通知已有答案, 不等全部 job
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } answers {
            Result.success(listOf(mockAnswer()))
        }

        val latch = CountDownLatch(1)
        val receivedAnswers = mutableListOf<List<Pair<Int, String>>>()
        val receivedIsFinal = mutableListOf<Boolean>()
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            receivedIsFinal.add(invocation.args[5] as Boolean)
            latch.countDown()
        }

        // 第一题快速完成
        coordinator.processBitmap(mockBitmap())
        delay(50)
        // 第二题模拟在途（慢 LLM）— 用不同题目文本避免被去重跳过
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question two")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(500)
            Result.success(listOf(mockAnswer()))
        }
        coordinator.processBitmap(mockBitmap())
        delay(50)

        assertTrue("still has in-flight job, activeJobs=${coordinator.getActiveJobCount()}", coordinator.getActiveJobCount() >= 1)
        val result = coordinator.stop()
        assertTrue(result is RecordingCoordinator.StopResult.Processing)

        // 防呆: 已有部分答案时应立即收到至少一次通知（不阻塞等待全部完成）
        assertTrue("partial notified, received=${receivedAnswers.size}", latch.await(1, TimeUnit.SECONDS))
        assertTrue(receivedAnswers.isNotEmpty())
        assertTrue(receivedAnswers[0].isNotEmpty())
        // M11: partial 通知必须标记 isFinal=false（UI 显示"处理中"而非"全部完成"）
        assertFalse("partial notification must be isFinal=false", receivedIsFinal[0])
        // 最终通知（等待在途 job 完成后的 ensureResultsNotified）— 必须 isFinal=true
        delay(800)
        assertTrue("final notification must arrive", receivedIsFinal.any { it })
    }

    @Test
    fun cancel_after_stop_keeps_partial_answers_visible_via_notify() = runBlocking {
        // 场景: 用户结束录制后误触关闭 → Service 层防呆已跳过 recorder.cancel();
        // 此处验证 Coordinator 自身: stop 的部分结果已送达后再 cancel 不丢已展示数据
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        val latch = CountDownLatch(1)
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers { latch.countDown() }

        coordinator.processBitmap(mockBitmap())
        delay(50)

        // 制造在途 job — 用不同题目文本避免被去重跳过
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question two")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(Long.MAX_VALUE)
            Result.success(listOf(mockAnswer()))
        }
        coordinator.processBitmap(mockBitmap())
        delay(50)

        coordinator.stop()
        // 部分结果已立即通知
        assertTrue("partial notified", latch.await(1, TimeUnit.SECONDS))

        // 误触保护模拟: cancel 清状态但 answers 已通过通知送达 UI
        coordinator.cancel()
        assertFalse(coordinator.isActive)
    }

    @Test
    fun fetchAnswer_empty_answers_counts_as_failed() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("empty answer question?")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(emptyList())

        coordinator.processBitmap(mockBitmap())
        coordinator.stop()
        // 等待在途 job 完成（IO 线程异步）后再断言
        while (coordinator.getActiveJobCount() > 0) { delay(10) }
        delay(50)

        assertEquals(1, coordinator.failedCount)
        assertEquals(1, coordinator.captureCount)
    }

    @Test
    fun stop_waits_for_late_fetch_jobs_before_final_notify() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()
        // VLM 识别耗时（stop 时仍在途），完成后才启动 fetchAnswer
        coEvery { pipeline.recognizeVlm(any()) } coAnswers {
            delay(100)
            Result.success(VisionFilterResult(hasQuestions = true, extractedText = "late question?"))
        }
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(100)
            Result.success(listOf(mockAnswer()))
        }

        coordinator.processBitmap(mockBitmap())
        delay(20) // VLM job 仍在途

        val result = coordinator.stop()
        assertTrue(result is RecordingCoordinator.StopResult.Processing)

        delay(500) // 等待兜底协程动态 join 全部 job（VLM + 后加入的 fetchAnswer）
        // 最终通知必须发生在答案完整之后（回归：旧实现会提前通知空/部分答案且不再刷新）
        coVerify(exactly = 1) {
            callbacks.onResultsAvailable(
                match { answers -> answers.isNotEmpty() },
                any(), any(), any(), any(), any()
            )
        }
        assertEquals(0, coordinator.failedCount)
    }

    @Test
    fun fetch_answer_completion_updates_progress_after_stop() = runBlocking {
        coordinator.start()
        // OCR 识别立即完成，但答案生成延迟（stop 后才完成）
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(200)
            Result.success(listOf(mockAnswer()))
        }
        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        every { callbacks.onProgressUpdate(any(), any()) } answers {
            progressUpdates.add(invocation.args[0] as Int to invocation.args[1] as Int)
        }

        coordinator.processBitmap(mockBitmap())
        delay(50) // OCR 完成、fetch 还在跑
        coordinator.stop() // isProcessing=true，answers=0 → 无 partial 通知
        delay(400) // fetch 完成后 M12 触发 checkAndNotifyProgress

        // 答案完成后进度必须更新（回归：M12 代码行曾丢失导致 (x/N) 不更新）
        assertTrue("progress should update after answer completes", progressUpdates.any { it.first == 1 })
        assertEquals(0, coordinator.failedCount)
    }

    @Test
    fun stop_processing_then_quick_start_still_notifies() = runBlocking {
        // P0-2: stop 返回 Processing 后快速 start，旧兜底协程不得吞掉新会话通知
        coEvery { pipeline.recognizeOcr(any()) } coAnswers {
            delay(200)
            Result.success("test question")
        }
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        // 第一次录制：stop 时 OCR 仍在跑 → Processing
        coordinator.start()
        coordinator.processBitmap(mockBitmap())
        delay(10)
        val first = coordinator.stop()
        assertTrue(first is RecordingCoordinator.StopResult.Processing)
        assertTrue(coordinator.isProcessing)

        // 快速重启（不 cancel）：旧兜底协程此时 join 的是已清空的 jobs
        coordinator.start()
        assertFalse("start() 必须复位 isProcessing", coordinator.isProcessing)
        delay(20)

        // 第二次录制：快速成功并 stop
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("test question 2")
        val notifyCount = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = CountDownLatch(1)
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any(), any()) } answers {
            notifyCount.incrementAndGet()
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(10)
        val second = coordinator.stop()
        assertTrue(second is RecordingCoordinator.StopResult.Completed)
        assertTrue("第二次录制必须收到通知", latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, notifyCount.get())
    }

    @Test
    fun fetchAnswer_timeout_counts_as_failed() = runBlocking {
        // P1-1: 录制路径外层超时 → 计失败
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("test question")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(10_000)
            Result.success(listOf(mockAnswer()))
        }
        val oldTimeout = RecordingCoordinator.recordingAnswerTimeoutMs
        try {
            RecordingCoordinator.recordingAnswerTimeoutMs = 50
            coordinator.start()
            coordinator.processBitmap(mockBitmap())
            // 先等 fetchAnswer 启动（activeJobCount 0→1），再等其超时完成
            while (coordinator.getActiveJobCount() == 0) { delay(10) }
            while (coordinator.getActiveJobCount() > 0) { delay(10) }
            assertEquals(1, coordinator.failedCount)
        } finally {
            RecordingCoordinator.recordingAnswerTimeoutMs = oldTimeout
        }
    }

    @Test
    fun final_notify_total_uses_max_capture_and_questions() = runBlocking {
        // P1-2a: 一图两题最终通知 total 用 maxOf(captureCount=1, totalQuestions=2) = 2
        every { AppConfig.isVisionEnabled() } returns true
        coordinator.start()
        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            questions = listOf(
                SeparatedQuestion(index = 1, text = "Q1", searchKeywords = "kw1"),
                SeparatedQuestion(index = 2, text = "Q2", searchKeywords = "kw2")
            )
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )
        val totals = mutableListOf<Int>()
        every { callbacks.onResultsAvailable(any(), any(), capture(totals), any(), any(), any()) } returns Unit

        coordinator.processBitmap(mockBitmap())
        delay(10)
        coordinator.stop()
        delay(20)

        assertTrue("final total 必须为 maxOf(1,2)=2: $totals", totals.isNotEmpty() && totals.all { it == 2 })
    }
}