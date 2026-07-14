package com.hwb.aianswerer.models

import org.junit.Assert.*
import org.junit.Test

class OpenAIModelsTest {

    // ── ChatRequest ──

    @Test
    fun `ChatRequest构造函数 - 完整参数应正确设置所有字段`() {
        val messages = listOf(ChatMessage("user", "Hello"))
        val request = ChatRequest(
            model = "gpt-4",
            messages = messages,
            temperature = 1.0,
            maxTokens = 2048,
            responseFormat = ResponseFormat("text"),
            reasoningEffort = "high",
            stream = true
        )
        assertEquals("gpt-4", request.model)
        assertSame(messages, request.messages)
        assertEquals(1.0, request.temperature, 0.001)
        assertEquals(2048, request.maxTokens)
        assertEquals("text", request.responseFormat?.type)
        assertEquals("high", request.reasoningEffort)
        assertEquals(true, request.stream)
    }

    @Test
    fun `ChatRequest构造函数 - 默认参数应正确设置默认值`() {
        val messages = listOf(ChatMessage("user", "Hi"))
        val request = ChatRequest(model = "gpt-3.5-turbo", messages = messages)
        assertEquals(0.7, request.temperature, 0.001)
        assertNull(request.maxTokens)
        assertNull(request.responseFormat)
        assertNull(request.reasoningEffort)
        assertNull(request.stream)
    }

    @Test
    fun `ChatRequest构造函数 - messages为空列表应正确处理`() {
        val request = ChatRequest(model = "gpt-4", messages = emptyList())
        assertTrue(request.messages.isEmpty())
    }

    @Test
    fun `ChatRequest copy方法 - 修改部分字段应保持其他字段不变`() {
        val messages = listOf(ChatMessage("user", "Hello"))
        val original = ChatRequest(
            model = "gpt-4", messages = messages, temperature = 0.5,
            maxTokens = 100, stream = true
        )
        val copied = original.copy(model = "gpt-4o", temperature = 0.8)
        assertEquals("gpt-4o", copied.model)
        assertEquals(0.8, copied.temperature, 0.001)
        assertSame(messages, copied.messages)
        assertEquals(100, copied.maxTokens)
        assertEquals(true, copied.stream)
    }

    // ── ChatMessage ──

    @Test
    fun `ChatMessage构造函数 - 应正确设置角色和内容`() {
        val msg = ChatMessage("system", "You are a helpful assistant.")
        assertEquals("system", msg.role)
        assertEquals("You are a helpful assistant.", msg.content)
    }

    @Test
    fun `ChatMessage构造函数 - 空字符串应正确处理`() {
        val msg = ChatMessage("user", "")
        assertEquals("user", msg.role)
        assertEquals("", msg.content)
    }

    // ── ResponseFormat ──

    @Test
    fun `ResponseFormat构造函数 - 默认type应为json_object`() {
        val fmt = ResponseFormat()
        assertEquals("json_object", fmt.type)
    }

    @Test
    fun `ResponseFormat构造函数 - 自定义type应正确设置`() {
        val fmt = ResponseFormat("text")
        assertEquals("text", fmt.type)
    }

    // ── ChatResponse ──

    @Test
    fun `ChatResponse构造函数 - 完整参数应正确设置所有字段`() {
        val choice = Choice(0, ChatMessage("assistant", "Answer"), "stop")
        val usage = Usage(10, 20, 30)
        val response = ChatResponse(
            id = "chatcmpl-123",
            objectType = "chat.completion",
            created = 1234567890L,
            model = "gpt-4",
            choices = listOf(choice),
            usage = usage
        )
        assertEquals("chatcmpl-123", response.id)
        assertEquals("chat.completion", response.objectType)
        assertEquals(1234567890L, response.created)
        assertEquals("gpt-4", response.model)
        assertEquals(1, response.choices.size)
        assertSame(choice, response.choices[0])
        assertSame(usage, response.usage)
    }

    @Test
    fun `ChatResponse构造函数 - usage为null应正确处理`() {
        val response = ChatResponse(
            id = "chatcmpl-456",
            objectType = "chat.completion",
            created = 987654321L,
            model = "gpt-3.5-turbo",
            choices = listOf(Choice(0, ChatMessage("assistant", "42"), "stop"))
        )
        assertNull(response.usage)
    }

    @Test
    fun `ChatResponse构造函数 - choices为空列表应正确处理`() {
        val response = ChatResponse(
            id = "chatcmpl-789",
            objectType = "chat.completion",
            created = 0L,
            model = "gpt-4",
            choices = emptyList()
        )
        assertTrue(response.choices.isEmpty())
    }

    // ── Choice ──

    @Test
    fun `Choice构造函数 - 应正确设置所有字段`() {
        val msg = ChatMessage("assistant", "42")
        val choice = Choice(index = 0, message = msg, finishReason = "stop")
        assertEquals(0, choice.index)
        assertSame(msg, choice.message)
        assertEquals("stop", choice.finishReason)
    }

    // ── Usage ──

    @Test
    fun `Usage构造函数 - 应正确设置token计数`() {
        val usage = Usage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        assertEquals(100, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(150, usage.totalTokens)
    }

    @Test
    fun `Usage构造函数 - 零值token应正确处理`() {
        val usage = Usage(0, 0, 0)
        assertEquals(0, usage.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertEquals(0, usage.totalTokens)
    }

    // ── AIAnswer ──

    @Test
    fun `AIAnswer构造函数 - 不带options应返回null`() {
        val answer = AIAnswer("1+1=?", "选择题", "B")
        assertEquals("1+1=?", answer.question)
        assertEquals("选择题", answer.questionType)
        assertEquals("B", answer.answer)
        assertNull(answer.options)
    }

    @Test
    fun `AIAnswer构造函数 - 带options应正确设置列表`() {
        val options = listOf("A.1", "B.2", "C.3")
        val answer = AIAnswer("1+1=?", "选择题", "B", options)
        assertSame(options, answer.options)
        assertEquals(3, answer.options?.size)
        assertEquals("B.2", answer.options?.get(1))
    }

    @Test
    fun `AIAnswer copy方法 - 修改options后不影响原对象`() {
        val original = AIAnswer("Q", "选择题", "A", listOf("X", "Y"))
        val copied = original.copy(answer = "B", options = listOf("A", "B", "C"))
        assertEquals("B", copied.answer)
        assertEquals(3, copied.options?.size)
        assertEquals("A", original.answer)
        assertEquals(2, original.options?.size)
    }

    @Test
    fun `AIAnswer copy方法 - 不传参数应生成等价对象`() {
        val original = AIAnswer("Q", "问答题", "42", listOf("opt1"))
        val copied = original.copy()
        assertEquals(original.question, copied.question)
        assertEquals(original.questionType, copied.questionType)
        assertEquals(original.answer, copied.answer)
        assertEquals(original.options, copied.options)
    }
}
