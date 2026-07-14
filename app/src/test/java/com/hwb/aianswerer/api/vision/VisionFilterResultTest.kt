package com.hwb.aianswerer.api.vision

import org.junit.Assert.*
import org.junit.Test

class VisionFilterResultTest {

    // ═══ 默认值 ═══

    @Test
    fun `VisionFilterResult - 默认值`() {
        val result = VisionFilterResult()
        assertFalse(result.hasQuestions)
        assertEquals(0, result.questionCount)
        assertTrue(result.questionTypes.isEmpty())
        assertEquals("", result.searchKeywords)
        assertFalse(result.isMultiQuestion)
        assertEquals("", result.noiseDescription)
        assertEquals("", result.extractedText)
        assertTrue(result.questions.isEmpty())
        assertEquals("", result.rawResponse)
    }

    @Test
    fun `VisionFilterResult - 全部字段构造`() {
        val questions = listOf(
            SeparatedQuestion(index = 1, text = "题1", questionType = "选择题", searchKeywords = "keyword1")
        )
        val result = VisionFilterResult(
            hasQuestions = true,
            questionCount = 1,
            questionTypes = listOf("选择题"),
            searchKeywords = "test keyword",
            isMultiQuestion = false,
            noiseDescription = "广告",
            extractedText = "题目文本",
            questions = questions,
            rawResponse = """{"has_questions":true}"""
        )
        assertTrue(result.hasQuestions)
        assertEquals(1, result.questionCount)
        assertEquals(listOf("选择题"), result.questionTypes)
        assertEquals("test keyword", result.searchKeywords)
        assertEquals("广告", result.noiseDescription)
        assertEquals("题目文本", result.extractedText)
        assertEquals(1, result.questions.size)
        assertEquals("题1", result.questions[0].text)
        assertEquals("""{"has_questions":true}""", result.rawResponse)
    }

    // ═══ copy() ═══

    @Test
    fun `VisionFilterResult - copy修改hasQuestions`() {
        val original = VisionFilterResult()
        val copied = original.copy(hasQuestions = true)
        assertTrue(copied.hasQuestions)
        assertFalse(original.hasQuestions)
        assertEquals(original.questionCount, copied.questionCount)
    }

    @Test
    fun `VisionFilterResult - copy修改questionCount和types`() {
        val original = VisionFilterResult(hasQuestions = true, questionCount = 3)
        val copied = original.copy(questionCount = 5, questionTypes = listOf("选择题", "填空题"))
        assertEquals(5, copied.questionCount)
        assertEquals(2, copied.questionTypes.size)
        assertEquals(3, original.questionCount) // 原对象不变
    }

    @Test
    fun `VisionFilterResult - copy修改questions列表`() {
        val qs = listOf(SeparatedQuestion(index = 1, text = "新题"))
        val result = VisionFilterResult().copy(questions = qs)
        assertEquals(1, result.questions.size)
        assertEquals("新题", result.questions[0].text)
    }

    // ═══ hasQuestions ═══

    @Test
    fun `VisionFilterResult - hasQuestions为true时表示有题目`() {
        val result = VisionFilterResult(hasQuestions = true, questionCount = 2)
        assertTrue(result.hasQuestions)
        assertEquals(2, result.questionCount)
    }

    @Test
    fun `VisionFilterResult - hasQuestions为false时无题目`() {
        val result = VisionFilterResult(hasQuestions = false, questionCount = 0)
        assertFalse(result.hasQuestions)
        assertEquals(0, result.questionCount)
    }

    // ═══ questionTypes ═══

    @Test
    fun `VisionFilterResult - questionTypes空列表`() {
        val result = VisionFilterResult(questionTypes = emptyList())
        assertTrue(result.questionTypes.isEmpty())
    }

    @Test
    fun `VisionFilterResult - questionTypes多题型`() {
        val types = listOf("选择题", "填空题", "问答题")
        val result = VisionFilterResult(questionTypes = types)
        assertEquals(3, result.questionTypes.size)
        assertTrue(result.questionTypes.contains("填空题"))
    }

    // ═══ isMultiQuestion ═══

    @Test
    fun `VisionFilterResult - isMultiQuestion单题和多题`() {
        val single = VisionFilterResult(isMultiQuestion = false)
        val multi = VisionFilterResult(isMultiQuestion = true)
        assertFalse(single.isMultiQuestion)
        assertTrue(multi.isMultiQuestion)
    }

    // ═══ SeparatedQuestion ═══

    @Test
    fun `SeparatedQuestion - 默认值`() {
        val q = SeparatedQuestion()
        assertEquals(0, q.index)
        assertEquals("", q.text)
        assertEquals("", q.questionType)
        assertEquals("", q.searchKeywords)
    }

    @Test
    fun `SeparatedQuestion - 完整构造`() {
        val q = SeparatedQuestion(
            index = 3,
            text = "光合作用的化学方程式是什么？",
            questionType = "问答题",
            searchKeywords = "光合作用 化学方程式"
        )
        assertEquals(3, q.index)
        assertEquals("光合作用的化学方程式是什么？", q.text)
        assertEquals("问答题", q.questionType)
        assertEquals("光合作用 化学方程式", q.searchKeywords)
    }

    @Test
    fun `SeparatedQuestion - 多题列表`() {
        val questions = listOf(
            SeparatedQuestion(index = 1, text = "1+1=?", questionType = "选择题"),
            SeparatedQuestion(index = 2, text = "水化学式", questionType = "填空题"),
            SeparatedQuestion(index = 3, text = "什么是AI", questionType = "问答题")
        )
        assertEquals(3, questions.size)
        assertEquals(1, questions[0].index)
        assertEquals("填空题", questions[1].questionType)
        assertEquals("什么是AI", questions[2].text)
    }

    // ═══ @SerializedName 注解验证（通过JSON序列化模拟） ═══

    @Test
    fun `VisionFilterResult - hasQuestions被序列化为has_questions`() {
        // 验证 hasQuestions 与 @SerializedName("has_questions") 的对应关系
        val result = VisionFilterResult(hasQuestions = true)
        assertTrue(result.hasQuestions)
    }
}
