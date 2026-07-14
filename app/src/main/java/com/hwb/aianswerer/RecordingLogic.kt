package com.hwb.aianswerer

import java.util.concurrent.atomic.AtomicInteger

/**
 * Encapsulates AtomicInteger acquire/release for recording job counting.
 * Every increment MUST have exactly one decrement.
 */
class RecordingSemaphore {
    private val count = AtomicInteger(0)

    fun acquire(): Int = count.incrementAndGet()
    fun release(): Int = count.decrementAndGet()
    val activeCount: Int get() = count.get()
    fun reset() = count.set(0)
}

/**
 * Pure predicate for admission control.
 */
class ConcurrencyGate(private val maxConcurrency: Int) {
    fun canAcceptMore(activeJobs: Int): Boolean = activeJobs < maxConcurrency
}

/**
 * Normalizes text for deduplication: trims, collapses whitespace,
 * removes punctuation, lowercases.
 */
object DedupNormalizer {
    fun normalize(text: String): String = text.trim()
        .replace(Regex("\\s+"), "")
        .replace(Regex("[,，。.、；;：:！!？?\"'`()（）\\[\\]【】]"), "")
        .lowercase()
}

/**
 * Tracks recording progress.
 */
class RecordingProgressTracker {
    var captured: Int = 0
        private set
    var processed: Int = 0
        private set
    var skipped: Int = 0
        private set

    fun onCaptured() { captured++ }
    fun onProcessed() { processed++ }
    fun onSkipped() { skipped++ }
    fun isComplete(): Boolean = processed >= captured && captured > 0
    fun reset() { captured = 0; processed = 0; skipped = 0 }
}
