package com.hwb.aianswerer.ui.components

import org.junit.Assert.*
import org.junit.Test

class SectionParserTest {

    // ── 空输入 / 无标记 ──

    @Test
    fun `empty string returns single untagged section`() {
        val result = parseSections("")
        assertEquals(1, result.size)
        assertEquals("", result[0].label)
        assertEquals("", result[0].content)
        assertFalse(result[0].isAnswer)
        assertFalse(result[0].isExplanation)
    }

    @Test
    fun `plain text without markers returns single section`() {
        val result = parseSections("这是一段没有标记的普通文字。")
        assertEquals(1, result.size)
        assertEquals("", result[0].label)
        assertEquals("这是一段没有标记的普通文字。", result[0].content)
    }

    @Test
    fun `whitespace only returns single section`() {
        val result = parseSections("   \n  ")
        assertEquals(1, result.size)
        assertEquals("", result[0].label)
        assertEquals("", result[0].content)
    }

    // ── 中文方括号标记 【】 ──

    @Test
    fun `single chinese bracket marker produces one section`() {
        val result = parseSections("【答案】A. 这是答案内容")
        assertEquals(1, result.size)
        assertEquals("【答案】", result[0].label)
        assertTrue(result[0].content.startsWith("A."))
        assertTrue(result[0].isAnswer)
        assertFalse(result[0].isExplanation)
    }

    @Test
    fun `chinese bracket with explanation detected`() {
        val result = parseSections("【解析】这是因为选项A最符合题意。")
        assertEquals(1, result.size)
        assertEquals("【解析】", result[0].label)
        assertTrue(result[0].content.contains("符合题意"))
        assertFalse(result[0].isAnswer)
        assertTrue(result[0].isExplanation)
    }

    @Test
    fun `chinese brackets with answer and explanation split correctly`() {
        val result = parseSections("【答案】B\n【解析】选项B是唯一正确的描述。")
        assertEquals(2, result.size)

        assertEquals("【答案】", result[0].label)
        assertEquals("B", result[0].content)
        assertTrue(result[0].isAnswer)

        assertEquals("【解析】", result[1].label)
        assertTrue(result[1].content.contains("选项B"))
        assertTrue(result[1].isExplanation)
    }

    @Test
    fun `chinese bracket label without answer or explanation keywords`() {
        val result = parseSections("【题目分析】这是一道关于内存管理的题目。")
        assertEquals(1, result.size)
        assertEquals("【题目分析】", result[0].label)
        assertFalse(result[0].isAnswer)
        assertFalse(result[0].isExplanation)
    }

    // ── Markdown 粗体标记 ** ──

    @Test
    fun `markdown bold markers detected`() {
        val result = parseSections("**答案** A")
        assertEquals(1, result.size)
        assertEquals("**答案**", result[0].label)
        assertEquals("A", result[0].content)
        assertTrue(result[0].isAnswer)
    }

    @Test
    fun `markdown bold with explanation`() {
        val result = parseSections("**解析** 这是一个异步操作。")
        assertEquals(1, result.size)
        assertEquals("**解析**", result[0].label)
        assertTrue(result[0].isExplanation)
    }

    @Test
    fun `markdown bold with mixed answer and other sections`() {
        val result = parseSections("**题目** 什么是闭包？\n**答案** 函数内部定义的函数。\n**解析** 闭包可以访问外部变量。")
        assertEquals(3, result.size)

        assertEquals("**题目**", result[0].label)
        assertFalse(result[0].isAnswer)

        assertEquals("**答案**", result[1].label)
        assertTrue(result[1].isAnswer)

        assertEquals("**解析**", result[2].label)
        assertTrue(result[2].isExplanation)
    }

    // ── 混合标记 ──

    @Test
    fun `mixed chinese bracket and markdown bold markers`() {
        val result = parseSections("【答案】C\n**解析** 因为C选项符合规范。")
        assertEquals(2, result.size)
        assertEquals("【答案】", result[0].label)
        assertEquals("**解析**", result[1].label)
        assertTrue(result[0].isAnswer)
        assertTrue(result[1].isExplanation)
    }

    // ── 边界情况 ──

    @Test
    fun `empty content after marker`() {
        val result = parseSections("【答案】")
        assertEquals(1, result.size)
        assertEquals("【答案】", result[0].label)
        assertEquals("", result[0].content)
        assertTrue(result[0].isAnswer)
    }

    @Test
    fun `marker at end of string with no content`() {
        val result = parseSections("前面有内容【答案】")
        assertEquals(1, result.size)
        assertEquals("【答案】", result[0].label)
        assertEquals("", result[0].content)
        assertTrue(result[0].isAnswer)
    }

    @Test
    fun `text before first marker is dropped`() {
        val result = parseSections("题目描述区域\n【答案】D\n【解析】详见分析。")
        assertEquals(2, result.size)
        assertEquals("【答案】", result[0].label)
        assertEquals("D", result[0].content)
    }

    @Test
    fun `consecutive markers with no text between`() {
        val result = parseSections("【答案】\n【解析】解释文字。")
        assertEquals(2, result.size)
        assertEquals("【答案】", result[0].label)
        assertEquals("", result[0].content)
        assertEquals("【解析】", result[1].label)
        assertEquals("解释文字。", result[1].content)
    }

    @Test
    fun `multiple answer-like labels all flagged`() {
        val result = parseSections("【答案】A\n**正确答案** B\n【参考答案】C")
        assertEquals(3, result.size)
        // "正确答案" and "参考答案" both contain "答案"
        assertTrue(result[0].isAnswer)
        assertTrue(result[1].isAnswer)
        assertTrue(result[2].isAnswer)
    }

    @Test
    fun `english answer keyword detection case insensitive`() {
        val result = parseSections("**Answer** B")
        assertEquals(1, result.size)
        assertTrue(result[0].isAnswer)
    }

    @Test
    fun `english analysis keyword detection`() {
        val result = parseSections("**Analysis** 详细分析如下。")
        assertEquals(1, result.size)
        assertTrue(result[0].isExplanation)
    }

    // ── 长文本 / 真实场景 ──

    @Test
    fun `realistic multi-section AI output`() {
        val input = """
            以下是根据题目内容生成的解析：
            
            【答案】A
            
            【解析】
            选项A是正确的，因为根据题目描述，该函数的功能是进行数据转换，
            而其他选项都不符合题目要求。
            
            **注意事项**
            该解析仅供参考，实际使用时请核对答案。
        """.trimIndent()

        val result = parseSections(input)
        assertEquals(3, result.size)

        assertEquals("【答案】", result[0].label)
        assertTrue(result[0].content.contains("A"))

        assertEquals("【解析】", result[1].label)
        assertTrue(result[1].content.contains("数据转换"))

        assertEquals("**注意事项**", result[2].label)
        assertFalse(result[2].isAnswer)
        assertFalse(result[2].isExplanation)
    }

    @Test
    fun `section content preserves whitespace within`() {
        val result = parseSections("【答案】A\n  缩进的内容\n  多行文字")
        assertEquals(1, result.size)
        assertTrue(result[0].content.contains("缩进的内容"))
        assertTrue(result[0].content.contains("多行文字"))
    }
}
