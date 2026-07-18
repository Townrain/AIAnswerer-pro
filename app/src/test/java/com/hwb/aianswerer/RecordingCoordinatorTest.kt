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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        every { AppConfig.isRegexFilterEnabled() } returns false
        coEvery { OpenAIClient.isNetworkAvailable() } returns true
        every { Constants.buildRecordingSystemPrompt(any(), any(), any(), any()) } returns "recording_system_prompt"
        every { MyApplication.getString(any<Int>()) } returns "MOCK_LABEL"
        every { MyApplication.getString(any<Int>(), *anyVararg()) } returns "MOCK_LABEL"
        every { callbacks.isSearchEnabled() } returns false
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
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers { latch.countDown() }

        coordinator.processBitmap(mockBitmap())
        delay(500)

        val result = coordinator.stop()
        assertTrue(result is RecordingCoordinator.StopResult.Completed)
        assertFalse(coordinator.isActive)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun stop_with_pending_jobs_returns_Processing() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } coAnswers {
            delay(5000)
            Result.success("test")
        }

        coordinator.processBitmap(mockBitmap())
        delay(100)

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
        delay(100)
        assertTrue(coordinator.isActive)

        coordinator.cancel()
        delay(100)

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
        delay(500)

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
        delay(500)

        coordinator.processBitmap(mockBitmap())
        delay(500)

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
        delay(500)

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
        delay(500)

        assertEquals(3, coordinator.totalQuestions)
        coVerify(exactly = 3) { pipeline.askLlm(any(), any(), any(), any()) }
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
        delay(500)

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
        delay(300)

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
        delay(300)

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
        delay(500)

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
        delay(500)

        coVerify { pipeline.recognizeOcr(any()) }
        coVerify { pipeline.askLlm(any(), any(), any(), any()) }
        assertEquals(1, coordinator.totalQuestions)
    }

    @Test
    fun processWithOcr_failure_does_not_fetch_answer() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.failure(RuntimeException("OCR fail"))

        coordinator.processBitmap(mockBitmap())
        delay(300)

        coVerify(exactly = 0) { pipeline.askLlm(any(), any(), any(), any()) }
        assertEquals(0, coordinator.totalQuestions)
    }

    // ===========================================================
    //  fetchAnswer Tests
    // ===========================================================

    @Test
    fun fetchAnswer_with_VLM_search_keywords_calls_searchWeb() = runBlocking {
        every { AppConfig.isVisionEnabled() } returns true
        every { callbacks.isSearchEnabled() } returns true
        coordinator.start()

        val vlmResult = VisionFilterResult(
            hasQuestions = true,
            extractedText = "test",
            searchKeywords = "custom search keywords"
        )
        coEvery { pipeline.recognizeVlm(any()) } returns Result.success(vlmResult)
        coEvery { pipeline.searchWeb(any(), any()) } returns "search results"
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(500)

        coVerify { pipeline.searchWeb("custom search keywords", any()) }
    }

    @Test
    fun fetchAnswer_without_VLM_search_enabled_builds_text_based_query() = runBlocking {
        every { callbacks.isSearchEnabled() } returns true
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("What is the answer?")
        coEvery { pipeline.searchWeb(any(), any()) } returns "search results"
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(500)

        coVerify { pipeline.searchWeb(any(), any()) }
    }

    @Test
    fun fetchAnswer_multi_question_pattern_skips_OCR_web_search_when_regex_enabled() = runBlocking {
        every { callbacks.isSearchEnabled() } returns true
        every { AppConfig.isRegexFilterEnabled() } returns true
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success(
            "1. 第一题\nA. 选项A\nB. 选项B\n2. 第二题\nA. 选项A\nB. 选项B"
        )
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        coordinator.processBitmap(mockBitmap())
        delay(500)

        coVerify(exactly = 0) { pipeline.searchWeb(any(), any()) }
    }

    @Test
    fun fetchAnswer_network_unavailable_increments_failedCount() = runBlocking {
        coEvery { OpenAIClient.isNetworkAvailable() } returns false
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")

        coordinator.processBitmap(mockBitmap())
        delay(500)

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
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(500)

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
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers {
            receivedFailed.add(invocation.args[4] as Int)
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(500)

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
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(500)

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
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(500)

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
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers {
            receivedAnswers.add(firstArg())
            latch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(500)

        coordinator.stop()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(receivedAnswers[0].isEmpty())
    }

    // ===========================================================
    //  Progress Tests
    // ===========================================================

    @Test
    fun checkAndNotifyProgress_all_done_calls_notifyResults() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(
            listOf(mockAnswer())
        )

        val latch = CountDownLatch(1)
        every { callbacks.onResultsAvailable(any(), any(), any(), any(), any()) } answers { latch.countDown() }

        coordinator.processBitmap(mockBitmap())
        delay(500)
        coordinator.stop()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun checkAndNotifyProgress_in_progress_calls_onProgressUpdate() = runBlocking {
        coordinator.start()
        coEvery { pipeline.recognizeOcr(any()) } returns Result.success("text")
        val holdFetch = CountDownLatch(1)

        val progressLatch = CountDownLatch(1)
        every { callbacks.onProgressUpdate(any(), any()) } answers { progressLatch.countDown() }

        coordinator.processBitmap(mockBitmap())
        delay(50)
        coordinator.stop()
        delay(50)

        assertTrue(progressLatch.await(5, TimeUnit.SECONDS))
        holdFetch.countDown()
    }

    @Test
    fun checkAndNotifyProgress_reports_correct_counts() = runBlocking {
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

        val progressLatch = CountDownLatch(1)
        val receivedTotal = mutableListOf<Int>()
        every { callbacks.onProgressUpdate(any(), any()) } answers {
            val total: Int = invocation.args[1] as Int
            receivedTotal.add(total)
            progressLatch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        delay(50)
        coordinator.stop()
        delay(50)

        assertTrue(progressLatch.await(5, TimeUnit.SECONDS))
        assertEquals(2, receivedTotal[0])
    }
}
