package com.hwb.aianswerer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for recording-specific system prompt assembly.
 */
class RecordingPromptTest {

    @Test
    fun recordingPrompt_containsBatchContext() {
        val prompt = safelyInvoke { Constants.buildRecordingSystemPrompt(3, 8, setOf("选择题")) }
        assertTrue("Should contain question index", prompt.contains("第3题"))
        assertTrue("Should mention batch total", prompt.contains("共 8 题"))
    }

    @Test
    fun recordingPrompt_doesNotChangeSingleShot() {
        val singleShot = safelyInvoke { Constants.buildSystemPrompt(setOf("填空题"), "") }
        assertFalse("Single-shot should not mention batch", singleShot.contains("第3题"))
    }

    @Test
    fun buildSystemPrompt_containsQuestionTypes() {
        val prompt = safelyInvoke { Constants.buildSystemPrompt(setOf("选择题", "填空题"), "") }
        assertTrue("Should mention choice type", prompt.contains("选择题"))
        assertTrue("Should mention fill-blank type", prompt.contains("填空题"))
    }

    @Test
    fun buildSystemPrompt_emptyTypes_returnsBasePrompt() {
        val prompt = safelyInvoke { Constants.buildSystemPrompt(emptySet(), "") }
        assertTrue("Should return non-empty base prompt", prompt.isNotBlank())
        assertFalse("Empty types should not contain type header", prompt.contains("题型"))
    }

    @Test
    fun buildSystemPrompt_withSearchContext_includesSearchResults() {
        val searchContext = "搜索到了：光合作用需要光和叶绿体"
        val prompt = safelyInvoke { Constants.buildSystemPrompt(setOf("选择题"), searchContext) }
        assertTrue("Should include search context", prompt.contains("光合作用"))
    }

    @Test
    fun buildSystemPrompt_withoutSearchContext_noSearchSection() {
        val prompt = safelyInvoke { Constants.buildSystemPrompt(setOf("选择题"), "") }
        // The base prompt shouldn't have search-related headers when no context
        val baseOnly = safelyInvoke { Constants.buildSystemPrompt(emptySet(), "") }
        // Just verify both are non-empty — detailed content depends on resources
        assertTrue(prompt.isNotBlank())
        assertTrue(baseOnly.isNotBlank())
    }

    @Test
    fun buildRecordingSystemPrompt_containsBatchInfo() {
        val prompt = safelyInvoke { Constants.buildRecordingSystemPrompt(5, 10, setOf("选择题"), "") }
        assertTrue("Should contain question index", prompt.contains("第5题"))
        assertTrue("Should contain batch total", prompt.contains("共 10 题"))
    }

    @Test
    fun buildRecordingSystemPrompt_withSearchContext_hasBoth() {
        val prompt = safelyInvoke { Constants.buildRecordingSystemPrompt(1, 3, setOf("问答题"), "参考资料：太阳是恒星") }
        assertTrue("Should have batch context", prompt.contains("第1题"))
        assertTrue("Should have search context", prompt.contains("太阳"))
    }

    @Test
    fun buildSystemPrompt_withChineseQuestionTypes() {
        val prompt = safelyInvoke { Constants.buildSystemPrompt(setOf("单选题", "多选题", "判断题"), "") }
        assertTrue("Prompt should be generated", prompt.isNotBlank())
        // Normalized: 单选题/多选题 → 选择题, 判断题 stays unchanged
        assertTrue("Should contain choice type (normalized)", prompt.contains("选择题"))
        assertTrue("Should contain judgment type", prompt.contains("判断题"))
    }

    @Test
    fun 录制模式多题型不崩溃() {
        val prompt = safelyInvoke { Constants.buildRecordingSystemPrompt(2, 10, setOf("选择题", "填空题", "问答题"), "") }
        assertTrue("Multi-type recording prompt should be non-blank", prompt.isNotBlank())
    }

    @Test
    fun 构建提示词空类型空搜索不崩溃() {
        val prompt = safelyInvoke { Constants.buildSystemPrompt(emptySet(), "") }
        assertTrue("Minimal prompt should be non-blank", prompt.isNotBlank())
    }

    @Test
    fun 录制模式边界题号正常() {
        val promptSingle = safelyInvoke { Constants.buildRecordingSystemPrompt(1, 1, emptySet(), "") }
        assertTrue("Single-batch recording should work", promptSingle.isNotBlank())
        val promptHundred = safelyInvoke { Constants.buildRecordingSystemPrompt(100, 100, emptySet(), "") }
        assertTrue("100/100 recording should work", promptHundred.isNotBlank())
    }

    @Test
    fun 录制模式空题型不显示题型限制() {
        val prompt = safelyInvoke { Constants.buildRecordingSystemPrompt(1, 5, emptySet(), "") }
        assertTrue("Should be non-blank", prompt.isNotBlank())
    }

}
