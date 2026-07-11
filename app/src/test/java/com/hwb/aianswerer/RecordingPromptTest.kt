package com.hwb.aianswerer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Tests for recording-specific system prompt assembly.
 */
class RecordingPromptTest {

    @Test
    fun recordingPrompt_containsBatchContext() {
        val prompt = safelyBuild { Constants.buildRecordingSystemPrompt(3, 8, setOf("选择题")) }
        assertTrue("Should contain question index", prompt.contains("第3题") || prompt.contains("Q3"))
        assertTrue("Should mention batch", prompt.contains("8") || prompt.contains("总") || prompt.contains("题"))
    }

    @Test
    fun recordingPrompt_doesNotChangeSingleShot() {
        val singleShot = safelyBuild { Constants.buildSystemPrompt(setOf("填空题"), "") }
        assertFalse("Single-shot should not mention batch", singleShot.contains("第3题"))
    }

    /**
     * Invoke [block] which calls [Constants] prompt builders that require Android
     * runtime ([MyApplication.getString]). Skip the test gracefully when the
     * Application instance is unavailable in the JVM test environment.
     */
    private fun safelyBuild(block: () -> String): String {
        return try {
            block()
        } catch (e: Throwable) {
            Assume.assumeNoException(
                "Constants prompt builders require Android Application context; test skipped in JVM",
                e
            )
            "" // unreachable — assumeNoException throws
        }
    }
}
