package com.hwb.aianswerer

import com.hwb.aianswerer.api.JsonAnswerExtractor
import org.junit.Assert.*
import org.junit.Test

class JsonAnswerExtractorTest {

    private val extractor = JsonAnswerExtractor()

    // ═══ 策略1：直接解析（有效JSON） ═══

    @Test
    fun `parseJsonAnswers - 有效JSON数组返回多个答案`() {
        val json = """[{"question":"1+1=?","questionType":"选择题","answer":"B","options":["A.1","B.2","C.3","D.4"]},{"question":"水的化学式","questionType":"填空题","answer":"H2O"}]"""
        val result = extractor.parseJsonAnswers(json)
        assertEquals(2, result.size)
        assertEquals("1+1=?", result[0].question)
        assertEquals("B", result[0].answer)
        assertEquals("H2O", result[1].answer)
    }

    @Test
    fun `parseJsonAnswers - 单个JSON对象`() {
        val json = """{"question":"什么是AI?","questionType":"问答题","answer":"人工智能是..."}"""
        val result = extractor.parseJsonAnswers(json)
        assertEquals(1, result.size)
        assertEquals("什么是AI?", result[0].question)
        assertEquals("人工智能是...", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - options字段正确解析`() {
        val json = """[{"question":"选择","questionType":"选择题","answer":"D","options":["A.a","B.b","C.c","D.d"]}]"""
        val result = extractor.parseJsonAnswers(json)
        assertEquals(1, result.size)
        assertNotNull(result[0].options)
        assertEquals(4, result[0].options!!.size)
        assertEquals("A.a", result[0].options!![0])
    }

    @Test
    fun `parseJsonAnswers - 多题分离后各自有答案`() {
        val json = """[{"question":"题1","questionType":"选择题","answer":"A"},{"question":"题2","questionType":"填空题","answer":"42"},{"question":"题3","questionType":"问答题","answer":"解释"}]"""
        val result = extractor.parseJsonAnswers(json)
        assertEquals(3, result.size)
        assertEquals("题1", result[0].question)
        assertEquals("题2", result[1].question)
        assertEquals("题3", result[2].question)
    }

    @Test
    fun `parseJsonAnswers - 空answer字段正常处理`() {
        val input = """{"question":"test","questionType":"选择题","answer":""}"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("test", result[0].question)
        assertEquals("", result[0].answer)
    }

    // ═══ 策略2：extractJsonPayload + fixMalformedJson ═══

    @Test
    fun `parseJsonAnswers - Markdown代码块包裹的JSON`() {
        val json = """```json
[{"question":"test","questionType":"选择题","answer":"A"}]
```"""
        val result = extractor.parseJsonAnswers(json)
        assertEquals(1, result.size)
        assertEquals("test", result[0].question)
        assertEquals("A", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - JSON混在文字中提取成功`() {
        val input = """好的，根据题目分析：{"question":"光合作用的条件","questionType":"填空题","answer":"光和叶绿体"}这是答案。"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("光合作用的条件", result[0].question)
        assertEquals("光和叶绿体", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - 末尾逗号被修复后解析`() {
        val input = """[{"question":"test","questionType":"选择题","answer":"A",}]"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("A", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - 缺少开头花括号被修复`() {
        val input = """"question":"test","questionType":"选择题","answer":"A"}"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("test", result[0].question)
        assertEquals("A", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - 缺少闭花括号被修复`() {
        val input = """{"question":"test","questionType":"选择题","answer":"A","""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("test", result[0].question)
        assertEquals("A", result[0].answer)
    }

    // ═══ 中文标点处理（sanitizeJson + fixMalformedJson） ═══

    @Test
    fun `parseJsonAnswers - 中文引号和全角标点被修复后解析`() {
        // ASCII 花括号 + JSON key 用 ASCII 双引号，值区域混入中文引号(U+201C/U+201D)、全角冒号(U+FF1A)、全角逗号(U+FF0C)
        val input = "{\"question\"\uFF1A\u201C\u6D4B\u8BD5\u9898\u76EE\u201D\uFF0C\"questionType\"\uFF1A\u201C\u9009\u62E9\u9898\u201D\uFF0C\"answer\"\uFF1A\u201CC\u201D}"
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("\u6D4B\u8BD5\u9898\u76EE", result[0].question)
        assertEquals("C", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - Markdown代码块内中文标点JSON`() {
        // ```json 代码块内包含中文引号和全角标点，应被 sanitizeJson 修复
        val input = "```json\n{\"question\"\uFF1A\u201C\u6C34\u7684\u5316\u5B66\u5F0F\u201D\uFF0C\"questionType\"\uFF1A\u201C\u586B\u7A7A\u9898\u201D\uFF0C\"answer\"\uFF1A\u201CH2O\u201D}\n```"
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("\u6C34\u7684\u5316\u5B66\u5F0F", result[0].question)
        assertEquals("H2O", result[0].answer)
    }

    @Test
    fun `parseJsonAnswers - 全角括号被修复`() {
        // (\uFF08) 和 )(\uFF09) 应被 fixMalformedJson 转为 ASCII 括号
        val input = "{\"question\"\uFF1A\u201C\u82F1\u8BED\u201D\uFF0C\"questionType\"\uFF1A\u201C\u586B\u7A7A\u9898\u201D\uFF0C\"answer\"\uFF1A\u201C\uFF08en\uFF09\u201D}"
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertTrue(result[0].question.isNotBlank())
    }

    // ═══ answer 字段修复（fixMalformedJson） ═══

    @Test
    fun `parseJsonAnswers - answer字段缺失值被修复`() {
        // "answer":, 应被 fixMalformedJson 替换为 "answer":"",
        val input = """{"question":"test","questionType":"选择题","answer":,}"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("test", result[0].question)
    }

    @Test
    fun `parseJsonAnswers - answer字段空格后逗号被修复`() {
        // "answer": , 变体
        val input = """{"question":"test","questionType":"选择题","answer": ,}"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("test", result[0].question)
    }
    @Test
    fun `parseJsonAnswers - JS字面量带内嵌ASCII引号 提取成功`() {
        // 回归: 真实日志第2/3题响应（键无引号 + question 值内含 \"3A大作\" ASCII引号）
        // 此前 quoteJsonKeys 未转义内嵌引号 → 全部策略失败 → 降级返回“无法解析题目”
        val input = """{question:以下那款游戏被称为"3A大作" A 绝区零 B 崩坏三 C 崩坏-星穹铁道 D 原神,questionType:选择题,answer:D,options:[A. 绝区零,B. 崩坏三,C. 崩坏-星穹铁道,D. 原神]}"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("D", result[0].answer)
        assertEquals("选择题", result[0].questionType)
        assertEquals(4, result[0].options!!.size)
    }

    @Test
    fun `parseJsonAnswers - 无引号键JS字面量 降级路径也能解析`() {
        // 回归: 键完全无引号（{key:value}），extractJsonValue 需兼容 bare key
        val input = """{question:蚊子的牙齿有多少颗,questionType:选择题,answer:B,options:[A. 20颗,B. 22颗,C. 24颗,D. 26颗]}"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(1, result.size)
        assertEquals("B", result[0].answer)
        assertEquals("蚊子的牙齿有多少颗", result[0].question)
    }

    @Test
    fun `parseJsonAnswers - 对象内多个字段带尾部逗号`() {
        // 多个字段都有尾部逗号，fixMalformedJson 逐一移除
        val input = """[{"question":"q","questionType":"选择题","answer":"A",},{"question":"q2","questionType":"填空题","answer":"42",}]"""
        val result = extractor.parseJsonAnswers(input)
        assertEquals(2, result.size)
        assertEquals("q", result[0].question)
        assertEquals("q2", result[1].question)
    }
}
