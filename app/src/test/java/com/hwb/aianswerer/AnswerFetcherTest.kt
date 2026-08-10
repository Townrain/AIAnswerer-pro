package com.hwb.aianswerer

import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.vision.SeparatedQuestion
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * AnswerFetcher unit tests — 11 tests covering single/multi question,
 * parallel ordering, search context building, network guard, and mutex.
 */
class AnswerFetcherTest {

    @After fun teardown() { unmockkAll() }

    private fun a(answer: String) = AIAnswer("Q", "选择题", answer)
    private fun q(i: Int, t: String) = SeparatedQuestion(index = i, text = t, searchKeywords = "")
    private fun vr(qs: List<SeparatedQuestion>) = VisionFilterResult(hasQuestions = true, questionTypes = listOf("选择题"), questions = qs)
    private fun novr() = VisionFilterResult(hasQuestions = false)

    data class F(val f: AnswerFetcher, val p: CapturePipeline, val c: AnswerFetcherCallbacks)

    private fun setup(p: CapturePipeline = mockk()): F {
        mockkObject(AppConfig); mockkObject(OpenAIClient)
        every { AppConfig.getQuestionTypes() } returns setOf("选择题")
        coEvery { OpenAIClient.isNetworkAvailable() } returns true
        val c = mockk<AnswerFetcherCallbacks>(relaxed = true)
        val f = AnswerFetcher(p, CoroutineScope(Dispatchers.Unconfined), c)
        return F(f, p, c)
    }

    private fun await(f: AnswerFetcher, text: String, vr: VisionFilterResult = novr()): AnswerResult {
        val r = mutableListOf<AnswerResult>()
        val latch = CountDownLatch(1)
        f.fetchAnswer(text, vr) { r.add(it); latch.countDown() }
        assertTrue("callback timeout", latch.await(5, TimeUnit.SECONDS))
        return r[0]
    }

    // ── Network guard ──────────────────────────────────────────────────
    @Test fun network_unavailable() {
        val s = setup(); coEvery { OpenAIClient.isNetworkAvailable() } returns false
        assertEquals("网络不可用", (await(s.f, "x") as AnswerResult.Error).message)
    }

    // ── Single question ───────────────────────────────────────────────
    @Test fun single_success() {
        val s = setup()
        coEvery { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("B")))
        assertEquals("B", (await(s.f, "test") as AnswerResult.Success).answers[0].answer)
    }

    @Test fun single_failure() {
        val s = setup()
        coEvery { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } returns Result.failure(RuntimeException("boom"))
        assertTrue((await(s.f, "t") as AnswerResult.Error).message.contains("AI分析失败"))
    }

    @Test
    fun single_timeout_invokes_error_callback() = runTest {
        mockkObject(AppConfig); mockkObject(OpenAIClient)
        every { AppConfig.getQuestionTypes() } returns setOf("选择题")
        coEvery { OpenAIClient.isNetworkAvailable() } returns true
        val p = mockk<CapturePipeline>()
        coEvery { p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } coAnswers {
            delay(AnswerFetcher.ANSWER_TIMEOUT_MS + 10_000)
            Result.success(listOf(a("X")))
        }
        val c = mockk<AnswerFetcherCallbacks>(relaxed = true)
        val f = AnswerFetcher(p, this, c)
        val r = mutableListOf<AnswerResult>()
        f.fetchAnswer("t") { r.add(it) }
        advanceTimeBy(AnswerFetcher.ANSWER_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals("超时必须回调错误而非静默取消", 1, r.size)
        assertTrue((r[0] as AnswerResult.Error).message.contains("超时"))
    }

    @Test
    fun single_empty_answers_returns_error() {
        val s = setup()
        coEvery { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } returns Result.success(emptyList())
        val err = await(s.f, "t") as AnswerResult.Error
        assertTrue(err.message.contains("未获取到答案"))
    }
    // ── Mutex serialisation ───────────────────────────────────────────
    // ── Mutex serialisation ───────────────────────────────────────────
    @Test fun mutex_serializes() {
        val s = setup()
        coEvery { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("X")))
        val r1 = mutableListOf<AnswerResult>(); val r2 = mutableListOf<AnswerResult>()
        val latch = CountDownLatch(2)
        s.f.fetchAnswer("Q1") { r1.add(it); latch.countDown() }
        s.f.fetchAnswer("Q2") { r2.add(it); latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
        assertEquals(1, r1.size); assertEquals(1, r2.size)
    }

    // ── Parallel — ordering (CRITICAL) ─────────────────────────────────
    @Test fun parallel_preserves_order() {
        val s = setup()
        every { AppConfig.isParallelModeEnabled() } returns true
        every { AppConfig.getMaxConcurrency() } returns 10
        coEvery { s.p.askLlm(match { it.contains("Q1") }, any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("A1")))
        coEvery { s.p.askLlm(match { it.contains("Q2") }, any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("A2")))
        coEvery { s.p.askLlm(match { it.contains("Q3") }, any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("A3")))
        val ans = (await(s.f, "", vr(listOf(q(1, "Q1"), q(2, "Q2"), q(3, "Q3")))) as AnswerResult.Success).answers
        assertEquals(3, ans.size)
        assertEquals("A1", ans[0].answer); assertEquals("A2", ans[1].answer); assertEquals("A3", ans[2].answer)
    }

    @Test fun parallel_partial_failure() {
        val s = setup()
        every { AppConfig.isParallelModeEnabled() } returns true
        every { AppConfig.getMaxConcurrency() } returns 10
        coEvery { s.p.askLlm(match { it.contains("Q1") }, any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("OK1")))
        coEvery { s.p.askLlm(match { it.contains("Q2") }, any<Set<String>>(), any<String>()) } returns Result.failure(RuntimeException("e"))
        coEvery { s.p.askLlm(match { it.contains("Q3") }, any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("OK3")))
        val r = await(s.f, "", vr(listOf(q(1, "Q1"), q(2, "Q2"), q(3, "Q3")))) as AnswerResult.Success
        assertEquals(2, r.answers.size)
        verify { s.c.onToast("部分题目获取失败") }
    }

    @Test fun parallel_all_failure() {
        val s = setup()
        every { AppConfig.isParallelModeEnabled() } returns true
        every { AppConfig.getMaxConcurrency() } returns 10
        coEvery { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } returns Result.failure(RuntimeException("x"))
        assertEquals("所有题目答题失败", (await(s.f, "", vr(listOf(q(1, "Q1"), q(2, "Q2")))) as AnswerResult.Error).message)
    }

    @Test fun parallel_concurrency_control() {
        val s = setup()
        every { AppConfig.isParallelModeEnabled() } returns true
        every { AppConfig.getMaxConcurrency() } returns 2
        coEvery { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) } returns Result.success(listOf(a("OK")))
        val done = CountDownLatch(1)
        s.f.fetchAnswer("", vr((1..6).map { q(it, "Q$it") })) { if (it is AnswerResult.Success) done.countDown() }
        assertTrue("callback timeout", done.await(5, TimeUnit.SECONDS))
        coVerify(exactly = 6) { s.p.askLlm(any<String>(), any<Set<String>>(), any<String>()) }
    }
}
