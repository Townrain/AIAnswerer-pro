package com.hwb.aianswerer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 收起态(C 窗)答案摘要构建逻辑测试。
 * 预期:标题 = "答案:" + 第 1 题纯答案,≤7 字符全显,>7 截断追加"......"。
 */
class FloatingWindowSummaryTest {

    private val prefix = "答案:"

    // ── 选择题短答案(≤7 字符) ──

    @Test
    fun `choice answer ABCD shows as-is`() {
        val r = buildCollapsedAnswerSummary(listOf(1 to "第 1 题：ABCD"), null, prefix)
        assertEquals("答案:ABCD", r)
    }

    @Test
    fun `choice answer with option letter shows as-is`() {
        val r = buildCollapsedAnswerSummary(listOf(1 to "第 1 题：B. 任天堂"), null, prefix)
        assertEquals("答案:B. 任天堂", r)
    }

    @Test
    fun `answer of exactly 7 chars is not truncated`() {
        val r = buildCollapsedAnswerSummary(listOf(1 to "第 1 题：1234567"), null, prefix)
        assertEquals("答案:1234567", r)
    }

    // ── 填空/问答题长答案(>7 字符) ──

    @Test
    fun `long answer is truncated to 7 chars with ellipsis`() {
        val r = buildCollapsedAnswerSummary(listOf(1 to "第 1 题：唐朝诗人李白是杜甫"), null, prefix)
        // "唐朝诗人李白是杜甫" = 9 字符 → 前 7 字符 + "......"
        assertEquals("答案:唐朝诗人李白是......", r)
    }

    @Test
    fun `long answer with spaces and newlines is normalized`() {
        val r = buildCollapsedAnswerSummary(listOf(1 to "第 1 题：李白\n杜甫\n王维"), null, prefix)
        // 换行归一化为空格后 "李白 杜甫 王维" = 8 字符 → 前 7 字符 + "......"
        assertEquals("答案:李白 杜甫 王......", r)
        // 换行归一化为空格后 "李白 杜甫 王维" = 8 字符 → 前 7 字符 + "......"
        assertEquals("答案:李白 杜甫 王......", r)
    }

    // ── 多题取第 1 题 ──

    @Test
    fun `multi-question summary takes first answer only`() {
        val r = buildCollapsedAnswerSummary(
            listOf(1 to "第 1 题：A", 2 to "第 2 题：B", 3 to "第 3 题：C"),
            null, prefix
        )
        assertEquals("答案:A", r)
    }

    // ── 前缀剥离兼容性 ──

    @Test
    fun `copyText prefix variants are stripped`() {
        assertEquals("答案:B", buildCollapsedAnswerSummary(listOf(1 to "第1题:B"), null, prefix))
        assertEquals("答案:B", buildCollapsedAnswerSummary(listOf(1 to "第 1 题：B"), null, prefix))
        assertEquals("答案:B", buildCollapsedAnswerSummary(listOf(1 to "第  2 题 :  B"), null, prefix))
    }

    @Test
    fun `answer that starts with 第 but no question prefix is kept`() {
        val r = buildCollapsedAnswerSummary(listOf(1 to "第 1 题：第7号"), null, prefix)
        assertEquals("答案:第7号", r)
    }

    // ── fallback 路径 ──

    @Test
    fun `falls back to answerText when copyTexts empty`() {
        val r = buildCollapsedAnswerSummary(emptyList(), "光和叶绿体", prefix)
        assertEquals("答案:光和叶绿体", r)
    }

    @Test
    fun `fallback long answer is truncated`() {
        val r = buildCollapsedAnswerSummary(emptyList(), "这是一段非常长的问答题答案内容", prefix)
        // 前 7 字符 = "这是一段非常长"
        assertEquals("答案:这是一段非常长......", r)
    }

    // ── 空值 ──

    @Test
    fun `returns null when nothing available`() {
        assertNull(buildCollapsedAnswerSummary(emptyList(), null, prefix))
        assertNull(buildCollapsedAnswerSummary(emptyList(), "   ", prefix))
        assertNull(buildCollapsedAnswerSummary(listOf(1 to "第 1 题：   "), null, prefix))
    }
    // ── 自定义参数 ──

    @Test
    fun `custom maxChars and ellipsis are honored`() {
        val r = buildCollapsedAnswerSummary(
            listOf(1 to "第 1 题：ABCDEFGH"),
            null, prefix, maxChars = 4, ellipsis = "..."
        )
        assertEquals("答案:ABCD...", r)
    }
}
