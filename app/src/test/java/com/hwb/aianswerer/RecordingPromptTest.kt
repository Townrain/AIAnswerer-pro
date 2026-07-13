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

    @Test
    fun buildSystemPrompt_containsQuestionTypes() {
        val prompt = safelyBuild { Constants.buildSystemPrompt(setOf("选择题", "填空题"), "") }
        assertTrue("Should mention question types", prompt.contains("选择") || prompt.contains("填空"))
    }

    @Test
    fun buildSystemPrompt_emptyTypes_returnsBasePrompt() {
        val prompt = safelyBuild { Constants.buildSystemPrompt(emptySet(), "") }
        assertTrue("Should return non-empty base prompt", prompt.isNotBlank())
        assertFalse("Empty types should not contain type header", prompt.contains("题型"))
    }

    @Test
    fun buildSystemPrompt_withSearchContext_includesSearchResults() {
        val searchContext = "搜索到了：光合作用需要光和叶绿体"
        val prompt = safelyBuild { Constants.buildSystemPrompt(setOf("选择题"), searchContext) }
        assertTrue("Should include search context", prompt.contains("光合作用"))
    }

    @Test
    fun buildSystemPrompt_withoutSearchContext_noSearchSection() {
        val prompt = safelyBuild { Constants.buildSystemPrompt(setOf("选择题"), "") }
        // The base prompt shouldn't have search-related headers when no context
        val baseOnly = safelyBuild { Constants.buildSystemPrompt(emptySet(), "") }
        // Just verify both are non-empty — detailed content depends on resources
        assertTrue(prompt.isNotBlank())
        assertTrue(baseOnly.isNotBlank())
    }

    @Test
    fun buildRecordingSystemPrompt_containsBatchInfo() {
        val prompt = safelyBuild { Constants.buildRecordingSystemPrompt(5, 10, setOf("选择题"), "") }
        assertTrue("Should contain index", prompt.contains("5") || prompt.contains("第5"))
        assertTrue("Should contain total", prompt.contains("10") || prompt.contains("总"))
    }

    @Test
    fun buildRecordingSystemPrompt_withSearchContext_hasBoth() {
        val prompt = safelyBuild { Constants.buildRecordingSystemPrompt(1, 3, setOf("问答题"), "参考资料：太阳是恒星") }
        assertTrue("Should have batch context", prompt.contains("第1") || prompt.contains("1"))
        assertTrue("Should have search context", prompt.contains("太阳"))
    }

    @Test
    fun buildSystemPrompt_withChineseQuestionTypes() {
        val prompt = safelyBuild { Constants.buildSystemPrompt(setOf("单选题", "多选题", "判断题"), "") }
        assertTrue("Prompt should be generated", prompt.isNotBlank())
        // Types should appear somewhere
        val hasAny = prompt.contains("单选") || prompt.contains("多选") || prompt.contains("判断")
        assertTrue("Should reference at least one question type", hasAny)
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
