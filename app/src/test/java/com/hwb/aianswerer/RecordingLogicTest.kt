package com.hwb.aianswerer

import org.junit.Assert.*
import org.junit.Test

/**
 * RecordingSemaphore, ConcurrencyGate, DedupNormalizer, RecordingProgressTracker 单元测试。
 * 纯 Kotlin 逻辑，零 Android 依赖，无需 safelyInvoke。
 */
class RecordingLogicTest {

    // ── RecordingSemaphore ──

    @Test
    fun `RecordingSemaphore - acquire 递增 activeCount`() {
        val sem = RecordingSemaphore()
        assertEquals(1, sem.acquire())
        assertEquals(2, sem.acquire())
        assertEquals(3, sem.acquire())
        assertEquals(3, sem.activeCount)
    }

    @Test
    fun `RecordingSemaphore - release 递减 activeCount`() {
        val sem = RecordingSemaphore()
        sem.acquire()
        sem.acquire()
        sem.acquire()
        assertEquals(3, sem.activeCount)

        assertEquals(2, sem.release())
        assertEquals(1, sem.release())
        assertEquals(0, sem.release())
        assertEquals(0, sem.activeCount)
    }

    @Test
    fun `RecordingSemaphore - acquire 和 release 配对后归零`() {
        val sem = RecordingSemaphore()
        repeat(5) { sem.acquire() }
        assertEquals(5, sem.activeCount)
        repeat(5) { sem.release() }
        assertEquals(0, sem.activeCount)
    }

    @Test
    fun `RecordingSemaphore - release 无对应 acquire 时返回负值`() {
        val sem = RecordingSemaphore()
        assertEquals(-1, sem.release())
        assertEquals(-2, sem.release())
    }

    @Test
    fun `RecordingSemaphore - reset 清零 activeCount`() {
        val sem = RecordingSemaphore()
        sem.acquire()
        sem.acquire()
        assertEquals(2, sem.activeCount)
        sem.reset()
        assertEquals(0, sem.activeCount)
        assertEquals(1, sem.acquire())
    }

    @Test
    fun `RecordingSemaphore - activeCount 初始为 0`() {
        val sem = RecordingSemaphore()
        assertEquals(0, sem.activeCount)
    }

    // ── ConcurrencyGate ──

    @Test
    fun `ConcurrencyGate - activeJobs 小于 max 时 canAcceptMore 为 true`() {
        val gate = ConcurrencyGate(5)
        assertTrue(gate.canAcceptMore(0))
        assertTrue(gate.canAcceptMore(3))
        assertTrue(gate.canAcceptMore(4))
    }

    @Test
    fun `ConcurrencyGate - activeJobs 等于 max 时 canAcceptMore 为 false`() {
        val gate = ConcurrencyGate(5)
        assertFalse(gate.canAcceptMore(5))
    }

    @Test
    fun `ConcurrencyGate - activeJobs 大于 max 时 canAcceptMore 为 false`() {
        val gate = ConcurrencyGate(5)
        assertFalse(gate.canAcceptMore(6))
        assertFalse(gate.canAcceptMore(100))
    }

    @Test
    fun `ConcurrencyGate - maxConcurrency 为 0 时永不接受`() {
        val gate = ConcurrencyGate(0)
        assertFalse(gate.canAcceptMore(0))
        assertFalse(gate.canAcceptMore(1))
    }

    @Test
    fun `ConcurrencyGate - 负数 activeJobs 被视为接受`() {
        val gate = ConcurrencyGate(3)
        assertTrue(gate.canAcceptMore(-1))
        assertTrue(gate.canAcceptMore(-100))
    }

    // ── DedupNormalizer ──

    @Test
    fun `DedupNormalizer - 相同字符串归一化结果相同`() {
        assertEquals(
            DedupNormalizer.normalize("hello world"),
            DedupNormalizer.normalize("hello world")
        )
    }

    @Test
    fun `DedupNormalizer - 空白差异归一化结果相同`() {
        assertEquals(
            DedupNormalizer.normalize("hello world"),
            DedupNormalizer.normalize("  hello   world  ")
        )
    }

    @Test
    fun `DedupNormalizer - 标点差异归一化结果相同`() {
        assertEquals(
            DedupNormalizer.normalize("hello,world!"),
            DedupNormalizer.normalize("hello，world！")
        )
    }

    @Test
    fun `DedupNormalizer - 大小写差异归一化结果相同`() {
        assertEquals(
            DedupNormalizer.normalize("Hello World"),
            DedupNormalizer.normalize("hello world")
        )
    }

    @Test
    fun `DedupNormalizer - 中英文混合标点全部移除`() {
        val result = DedupNormalizer.normalize("a，b。c、d；e：f！g？h'i\"j()（）【】[]")
        assertEquals("abcdefghij", result)
    }

    @Test
    fun `DedupNormalizer - 空字符串返回空字符串`() {
        assertEquals("", DedupNormalizer.normalize(""))
    }

    @Test
    fun `DedupNormalizer - 纯空白返回空字符串`() {
        assertEquals("", DedupNormalizer.normalize("   \t\n  "))
    }

    @Test
    fun `DedupNormalizer - 与 RecordingCoordinator 原始实现行为一致`() {
        // 验证 DedupNormalizer.normalize 与原始 normalizeForDedupe 等价
        val cases = listOf(
            "hello world",
            "  hello   world  ",
            "Hello, World!",
            "a，b。c、d；e：f！g？h'i\"j",
            "",
            "   "
        )
        for (text in cases) {
            assertEquals(
                "Mismatch for: [$text]",
                RecordingCoordinator.normalizeForDedupe(text),
                DedupNormalizer.normalize(text)
            )
        }
    }

    // ── RecordingProgressTracker ──

    @Test
    fun `RecordingProgressTracker - 初始状态全部为零`() {
        val tracker = RecordingProgressTracker()
        assertEquals(0, tracker.captured)
        assertEquals(0, tracker.processed)
        assertEquals(0, tracker.skipped)
    }

    @Test
    fun `RecordingProgressTracker - onCaptured 递增 captured`() {
        val tracker = RecordingProgressTracker()
        tracker.onCaptured()
        assertEquals(1, tracker.captured)
        tracker.onCaptured()
        assertEquals(2, tracker.captured)
    }

    @Test
    fun `RecordingProgressTracker - onProcessed 递增 processed`() {
        val tracker = RecordingProgressTracker()
        tracker.onProcessed()
        assertEquals(1, tracker.processed)
        tracker.onProcessed()
        assertEquals(2, tracker.processed)
    }

    @Test
    fun `RecordingProgressTracker - onSkipped 递增 skipped`() {
        val tracker = RecordingProgressTracker()
        tracker.onSkipped()
        assertEquals(1, tracker.skipped)
        tracker.onSkipped()
        assertEquals(2, tracker.skipped)
    }

    @Test
    fun `RecordingProgressTracker - isComplete 在 captured 为零时返回 false`() {
        val tracker = RecordingProgressTracker()
        assertFalse(tracker.isComplete())
        // 没有 captured 即使 processed>0 也返回 false
        tracker.onProcessed()
        assertFalse(tracker.isComplete())
    }

    @Test
    fun `RecordingProgressTracker - isComplete 在 processed 小于 captured 时返回 false`() {
        val tracker = RecordingProgressTracker()
        tracker.onCaptured()
        tracker.onCaptured()
        tracker.onProcessed()
        assertFalse(tracker.isComplete())
    }

    @Test
    fun `RecordingProgressTracker - isComplete 在 processed 等于 captured 且 captured 大于 0 时返回 true`() {
        val tracker = RecordingProgressTracker()
        tracker.onCaptured()
        tracker.onCaptured()
        tracker.onCaptured()
        tracker.onProcessed()
        tracker.onProcessed()
        tracker.onProcessed()
        assertTrue(tracker.isComplete())
    }

    @Test
    fun `RecordingProgressTracker - isComplete 在 processed 超过 captured 时返回 true`() {
        val tracker = RecordingProgressTracker()
        tracker.onCaptured()
        tracker.onProcessed()
        tracker.onProcessed()
        assertTrue(tracker.isComplete())
    }

    @Test
    fun `RecordingProgressTracker - reset 全部归零`() {
        val tracker = RecordingProgressTracker()
        tracker.onCaptured()
        tracker.onProcessed()
        tracker.onSkipped()
        tracker.reset()
        assertEquals(0, tracker.captured)
        assertEquals(0, tracker.processed)
        assertEquals(0, tracker.skipped)
        assertFalse(tracker.isComplete())
    }
}
