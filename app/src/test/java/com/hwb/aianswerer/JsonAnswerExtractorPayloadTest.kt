package com.hwb.aianswerer

import com.hwb.aianswerer.api.JsonAnswerExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JsonAnswerExtractor payload extraction unit tests.
 * Tests extractJsonPayload method with various input formats.
 */
class JsonAnswerExtractorPayloadTest {

    private val extractor = JsonAnswerExtractor()

    @Test
    fun `extractJsonPayload - 纯JSON对象`() {
        val input = """{"question":"test","answer":"A"}"""
        assertEquals(input, extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - 纯JSON数组`() {
        val input = """[{"question":"test","answer":"A"}]"""
        assertEquals(input, extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - Markdown代码块 json`() {
        val input = """```json
{"question":"test","answer":"A"}
```"""
        assertEquals("""{"question":"test","answer":"A"}""", extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - Markdown代码块 无语言标记`() {
        val input = """```
{"question":"test","answer":"A"}
```"""
        assertEquals("""{"question":"test","answer":"A"}""", extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - JSON混在文字中`() {
        val input = "Here is the answer: {\"question\":\"test\"} end"
        assertEquals("""{"question":"test"}""", extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - 嵌套JSON对象`() {
        val input = """{"outer":{"inner":"value"},"list":[1,2,3]}"""
        assertEquals(input, extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - 包含字符串内的括号`() {
        val input = """{"text":"hello {world}","answer":"A"}"""
        assertEquals(input, extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - 包含转义引号`() {
        val input = """{"text":"hello \"world\"","answer":"A"}"""
        assertEquals(input, extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - 空白填充`() {
        val input = "  {\"question\":\"test\"}  "
        assertEquals("""{"question":"test"}""", extractor.extractJsonPayload(input))
    }

    @Test
    fun `extractJsonPayload - 无JSON返回原文`() {
        val input = "This is just plain text"
        assertEquals(input, extractor.extractJsonPayload(input))
    }
}
