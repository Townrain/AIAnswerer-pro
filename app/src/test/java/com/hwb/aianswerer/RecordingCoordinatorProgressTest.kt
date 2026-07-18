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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Progress callback tests for RecordingCoordinator.
 *
 * Separated from [RecordingCoordinatorTest] to avoid mockk coEvery
 * isolation issues when OCR and VLM progress tests share the same
 * pipeline mock instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingCoordinatorProgressTest {

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
        coEvery { pipeline.recognizeOcr(any()) } coAnswers {
            delay(500)
            Result.success("text")
        }
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(300)
            Result.success(listOf(mockAnswer()))
        }

        val progressLatch = CountDownLatch(1)
        every { callbacks.onProgressUpdate(any(), any()) } answers { progressLatch.countDown() }

        coordinator.processBitmap(mockBitmap())
        coordinator.stop()  // sets isProcessing=true before OCR completes

        assertTrue(progressLatch.await(10, TimeUnit.SECONDS))
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
        coEvery { pipeline.recognizeVlm(any()) } coAnswers {
            delay(500)
            Result.success(vlmResult)
        }
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(300)  // ensure fetchAnswer completes AFTER VLM's invokeOnCompletion
            Result.success(listOf(mockAnswer()))
        }

        val progressLatch = CountDownLatch(1)
        val receivedTotal = mutableListOf<Int>()
        every { callbacks.onProgressUpdate(any(), any()) } answers {
            val total: Int = invocation.args[1] as Int
            receivedTotal.add(total)
            progressLatch.countDown()
        }

        coordinator.processBitmap(mockBitmap())
        coordinator.stop()  // sets isProcessing=true before VLM completes

        assertTrue(progressLatch.await(10, TimeUnit.SECONDS))
        assertEquals(2, receivedTotal[0])
    }
}
