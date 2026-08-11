package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ImageCollector（多图采集 → 专职去重 LLM → 答题 LLM）的行为验证。
 *
 * 生命周期与 RecordingCoordinator 同构：processBitmap/processText 进站（同步生成序号）、
 * stop 只关入口并等待在途识别完成后幂等提交一次（双 stop 安全）。
 *
 * 覆盖：去重成功/失败/空串、乱序完成合并顺序、序号进站同步生成、MAX_COLLECT_COUNT 边界、
 * 连续两次 stop 仅提交一次、cancel 后进站丢弃、空采集 stop、进度回调序列、
 * askLlm 失败 onError、异常路径 onError、start 重置、stop 等待在途识别。
 *
 * 依赖注入：CapturePipeline 用 mockk relaxed mock（仓库既有模式），
 * AppConfig.getQuestionTypes() 用 mockkObject 拦截，
 * Dispatchers.setMain(UnconfinedTestDispatcher) 使 launch(Main) 同步执行，
 * 随后轮询 isProcessing 等待异步收尾。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageCollectorTest {

    private val pipeline = mockk<CapturePipeline>(relaxed = true)
    private val callbacks = mockk<ImageCollector.Callbacks>(relaxed = true)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private lateinit var collector: ImageCollector

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(AppConfig)
        every { AppConfig.getQuestionTypes() } returns setOf("选择题")
        every { AppConfig.getMaxConcurrency() } returns 3
        collector = ImageCollector(pipeline, scope, callbacks)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun answer() = AIAnswer("测试题目？", "选择题", "A", listOf("A. 甲", "B. 乙"))
    private fun bitmap() = mockk<Bitmap>(relaxed = true)

    /** 等待 stop() 启动的处理协程结束（isProcessing 回到 false） */
    private fun awaitProcessingDone() {
        val deadline = System.currentTimeMillis() + 5000
        while (collector.isProcessing && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertFalse("processing should finish within timeout", collector.isProcessing)
    }

    // ===========================================================
    //  State Machine / 进站收集行为
    // ===========================================================

    @Test
    fun `start_initializes_state`() {
        collector.start()
        assertTrue(collector.isActive)
        assertEquals(0, collector.getCollectedCount())
        assertFalse(collector.isProcessing)
    }

    @Test
    fun `processText_before_start_is_noop`() {
        collector.processText("内容")
        assertEquals(0, collector.getCollectedCount())
        assertFalse(collector.isActive)
    }

    @Test
    fun `processBitmap_collects_recognized_text`() {
        collector.start()
        coEvery { pipeline.recognizeToText(any()) } returns Result.success("第一页内容")
        collector.processBitmap(bitmap())
        awaitProcessingDone()
        // 收集发生在识别完成回调里，等 jobs 清空后断言
        val deadline = System.currentTimeMillis() + 5000
        while (collector.getCollectedCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertEquals(1, collector.getCollectedCount())
        coVerify(exactly = 1) { callbacks.onProgressUpdate(1, any()) }
    }

    @Test
    fun `processText_collects_in_order`() {
        collector.start()
        collector.processText("第一页")
        collector.processText("第二页")
        assertEquals(2, collector.getCollectedCount())
        coVerify(exactly = 2) { callbacks.onProgressUpdate(any(), any()) }
    }

    @Test
    fun `processText_same_content_different_index_is_deduped`() {
        collector.start()
        collector.processText("同一屏幕内容")
        collector.processText("同一屏幕内容")
        assertEquals(1, collector.getCollectedCount())
    }

    @Test
    fun `same_photo_captured_twice_is_deduped`() {
        // 同一张照片拍两次：识别文本相同 → normalize 后相同 → 第二次去重跳过
        collector.start()
        coEvery { pipeline.recognizeToText(any()) } returns Result.success("同一道题目的识别文本")
        collector.processBitmap(bitmap())
        collector.processBitmap(bitmap())
        val deadline = System.currentTimeMillis() + 5000
        while (collector.getCollectedCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        Thread.sleep(100) // 等第二次识别完成（应被去重）
        assertEquals(1, collector.getCollectedCount())
        coVerify(exactly = 2) { pipeline.recognizeToText(any()) } // 两次都识别了，但只收集 1 段
    }

    @Test
    fun `processBitmap_after_stop_is_dropped`() {
        collector.start()
        collector.stop() // 空采集 stop → toast
        coEvery { pipeline.recognizeToText(any()) } returns Result.success("迟到内容")
        collector.processBitmap(bitmap())
        // isActive=false，进站即丢弃，不启动识别
        coVerify(exactly = 0) { pipeline.recognizeToText(any()) }
        assertEquals(0, collector.getCollectedCount())
    }

    @Test
    fun `processBitmap_recognize_failure_is_logged_and_skipped`() {
        collector.start()
        coEvery { pipeline.recognizeToText(any()) } returns Result.failure(Exception("VLM 失败"))
        collector.processBitmap(bitmap())
        val deadline = System.currentTimeMillis() + 5000
        while (collector.getActiveJobCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertEquals(0, collector.getCollectedCount())
    }

    @Test
    fun `processText_reaches_max_collect_count_then_rejects`() {
        collector.start()
        repeat(10) { i -> collector.processText("内容 $i") }
        assertEquals(10, collector.getCollectedCount())
        collector.processText("第 11 段")
        assertEquals(10, collector.getCollectedCount())
        coVerify { callbacks.onToast("已达到最大收集数量 (10)") }
    }

    // ===========================================================
    //  stop() 合并分析（去重 LLM → 答题 LLM）
    // ===========================================================

    @Test
    fun `stop_with_empty_collection_toasts_and_skips_llm`() {
        collector.start()
        collector.stop()
        coVerify { callbacks.onToast("未收集到内容") }
        coVerify(exactly = 0) { pipeline.dedupeText(any()) }
        coVerify(exactly = 0) { pipeline.askLlm(any(), any(), any(), any()) }
    }

    @Test
    fun `stop_dedupe_success_passes_clean_text_to_ask_llm`() {
        collector.start()
        collector.processText("第一页")
        collector.processText("第二页")
        coEvery { pipeline.dedupeText(any()) } returns Result.success("干净合并文本")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer()))

        collector.stop()
        awaitProcessingDone()

        val askSlot = slot<String>()
        coVerify { pipeline.askLlm(capture(askSlot), any(), any(), any()) }
        assertEquals("干净合并文本", askSlot.captured)
        coVerify(exactly = 1) { callbacks.onResult(any()) }
    }

    @Test
    fun `stop_dedupe_failure_falls_back_to_raw_combined_text`() {
        collector.start()
        collector.processText("第一页内容")
        coEvery { pipeline.dedupeText(any()) } returns Result.failure(Exception("去重失败"))
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer()))

        collector.stop()
        awaitProcessingDone()

        val askSlot = slot<String>()
        coVerify { pipeline.askLlm(capture(askSlot), any(), any(), any()) }
        assertTrue("降级应使用原始拼接文本，实际: ${askSlot.captured}", askSlot.captured.contains("第一页内容"))
        coVerify(exactly = 1) { callbacks.onResult(any()) }
    }

    @Test
    fun `stop_dedupe_blank_success_passes_through_to_ask_llm`() {
        // 防御观察：ImageCollector 层不拦截空串（生产路径 OpenAIClient.dedupeText 空 content 转 failure，实际不可达）
        collector.start()
        collector.processText("第一页")
        coEvery { pipeline.dedupeText(any()) } returns Result.success("")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer()))

        collector.stop()
        awaitProcessingDone()

        val askSlot = slot<String>()
        coVerify { pipeline.askLlm(capture(askSlot), any(), any(), any()) }
        assertEquals("", askSlot.captured)
    }

    @Test
    fun `stop_twice_triggers_only_one_analysis`() {
        // B2 修复验证：双 stop 仅提交一次（幂等锁），不再重复去重+答题
        collector.start()
        collector.processText("第一页")
        coEvery { pipeline.dedupeText(any()) } returns Result.success("干净文本")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer()))

        collector.stop()
        collector.stop() // 第二次 stop：isProcessing=true → 忽略
        awaitProcessingDone()

        coVerify(exactly = 1) { pipeline.dedupeText(any()) }
        coVerify(exactly = 1) { pipeline.askLlm(any(), any(), any(), any()) }
        coVerify(exactly = 1) { callbacks.onResult(any()) }
    }

    @Test
    fun `stop_waits_for_in_flight_recognition_before_merging`() {
        // stop 时仍有识别在途：等待其完成后再合并（文本不缺失）
        collector.start()
        collector.processText("第一页")
        coEvery { pipeline.recognizeToText(any()) } returns Result.success("第二页(慢)")

        // 第二个 job 仍在途时调用 stop
        val bmp = bitmap()
        collector.processBitmap(bmp)
        collector.stop()
        awaitProcessingDone()

        val dedupeSlot = slot<String>()
        coVerify { pipeline.dedupeText(capture(dedupeSlot)) }
        val combined = dedupeSlot.captured
        assertTrue("合并文本应包含在途识别结果，实际: $combined", combined.contains("第二页(慢)"))
        assertTrue(combined.contains("第一页"))
    }

    @Test
    fun `stop_merges_out_of_order_recognition_by_screenshot_index`() {
        // B1 验证：乱序完成（先进站的页后返回）仍按进站序号排序合并
        collector.start()
        val bmp1 = bitmap()
        val bmp2 = bitmap()
        // 进站顺序 1,2；完成顺序 2,1（bmp1 慢 50ms）
        coEvery { pipeline.recognizeToText(bmp2) } returns Result.success("第二页内容")
        coEvery { pipeline.recognizeToText(bmp1) } coAnswers {
            delay(50)
            Result.success("第一页内容")
        }
        collector.processBitmap(bmp1)
        collector.processBitmap(bmp2)
        collector.stop()
        awaitProcessingDone()

        val dedupeSlot = slot<String>()
        coVerify { pipeline.dedupeText(capture(dedupeSlot)) }
        val combined = dedupeSlot.captured
        val idx1 = combined.indexOf("第一页内容")
        val idx2 = combined.indexOf("第二页内容")
        assertTrue("第一页应排在第二页前，combined=$combined", idx1 in 0 until idx2)
    }

    @Test
    fun `stop_ask_llm_failure_calls_on_error`() {
        collector.start()
        collector.processText("第一页")
        coEvery { pipeline.dedupeText(any()) } returns Result.success("干净文本")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.failure(Exception("API down"))

        collector.stop()
        awaitProcessingDone()

        coVerify { callbacks.onError(match { it.contains("AI分析失败") }) }
    }

    @Test
    fun `progress_callbacks_follow_add_then_analyzing_sequence`() {
        collector.start()
        val progress = mutableListOf<Int>()
        coEvery { callbacks.onProgressUpdate(capture(progress), any()) } returns Unit
        collector.processText("第一页")
        collector.processText("第二页")
        collector.processText("第三页")
        coEvery { pipeline.dedupeText(any()) } returns Result.success("干净文本")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer()))
        collector.stop()
        awaitProcessingDone()
        // 1,2,3 后跟 -1（分析中）
        assertTrue("进度序列应包含 -1，实际: $progress", progress.contains(-1))
        assertEquals(4, progress.size)
        assertEquals(listOf(1, 2, 3, -1), progress)
    }

    // ===========================================================
    //  cancel / start 重置
    // ===========================================================

    @Test
    fun `cancel_clears_state_and_late_captures_are_noop`() {
        collector.start()
        collector.processText("第一页")
        collector.cancel()
        assertFalse(collector.isActive)
        assertEquals(0, collector.getCollectedCount())
        collector.processText("迟到内容")
        assertEquals(0, collector.getCollectedCount())
    }

    @Test
    fun `start_after_stop_resets_state`() {
        collector.start()
        collector.processText("旧内容")
        collector.stop()
        awaitProcessingDone()
        collector.start()
        assertEquals(0, collector.getCollectedCount())
        assertFalse(collector.isProcessing)
        assertTrue(collector.isActive)
    }

    @Test
    fun `restart_during_submit_drops_stale_result`() {
        // P0-3: stop 提交在途（去重/答题慢）时快速重启，旧会话结果不得打进新会话
        // 旧场景：提交协程不被 start() 追踪/取消，onResult 无守卫 → 旧答案覆盖新会话
        collector.start()
        collector.processText("第一页")

        // 提交阶段慢（模拟 240s 窗口内的在途提交）
        coEvery { pipeline.dedupeText(any()) } coAnswers {
            delay(300)
            Result.success("干净文本")
        }
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } coAnswers {
            delay(300)
            Result.success(listOf(answer()))
        }

        val onResults = mutableListOf<List<AIAnswer>>()
        coEvery { callbacks.onResult(capture(onResults)) } returns Unit

        collector.stop() // 提交协程在途（约 600ms 后 onResult）
        // 立即重启新会话（不等旧提交完成）
        collector.start()

        // 新会话立即完成一次采集并提交
        collector.processText("新会话内容")
        coEvery { pipeline.dedupeText(any()) } returns Result.success("新干净文本")
        coEvery { pipeline.askLlm(any(), any(), any(), any()) } returns Result.success(listOf(answer()))
        collector.stop()
        awaitProcessingDone()

        // 等旧提交（600ms）也走完 — 若未隔离其 onResult 会污染新会话
        Thread.sleep(800)

        // 新会话 stop 后 isProcessing 复位，onResult 守卫（isImageResultActive 由 ViewModel 层控制），
        // 此处验证 collector 层面：旧提交的 onResult 数量被代次守卫丢弃后总数为 1
        // （旧提交 onResult 被丢弃；新提交正常返回）
        // 注：callbacks.onResult 是 mock，collector 内部校验 gen 后丢弃旧提交，仅新提交触发
        assertTrue("只有新会话提交应触发 onResult，实际: ${onResults.size}", onResults.size == 1)
    }
}
