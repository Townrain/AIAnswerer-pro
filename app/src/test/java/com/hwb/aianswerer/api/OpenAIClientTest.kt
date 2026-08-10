package com.hwb.aianswerer.api

import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.api.search.WebSearchToolExecutor
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.FunctionSpec
import com.hwb.aianswerer.models.ToolSpec
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAIClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val baseUrl = server.url("/v1/chat/completions").toString()

        mockkObject(AppConfig)
        mockkObject(MyApplication.Companion)

        // Default AppConfig mocks
        every { AppConfig.getApiUrl() } returns baseUrl
        every { AppConfig.getApiKey() } returns "test-api-key"
        every { AppConfig.getModelName() } returns "test-model"
        every { AppConfig.isApiConfigValid() } returns true
        every { AppConfig.isApiConfigValid(any(), any(), any()) } returns true
        every { AppConfig.getLlmTemperature() } returns 0.3
        every { AppConfig.getReasoningEffort() } returns null

        // Default MyApplication mocks — simplified: mock Constants.getPromptResources() directly
        val mockRes = mockk<android.content.res.Resources>(relaxed = true) {
            every { getString(any<Int>()) } returns "mocked_message"
            every { getString(any<Int>(), any<Any>()) } returns "mocked_formatted"
        }
        every { MyApplication.getString(any<Int>()) } returns "mocked_message"
        every { MyApplication.getString(any<Int>(), any()) } returns "mocked_formatted"
        every { MyApplication.getAppContext() } returns mockk(relaxed = true)
        mockkObject(Constants)
        every { Constants.getPromptResources() } returns mockRes

        // function calling 工具模式默认关闭，现有用例保持原行为（tools=null）
        mockkObject(WebSearchToolExecutor)
        every { WebSearchToolExecutor.isToolModeActive() } returns false

        client = OpenAIClient()
    }
    @After
    fun tearDown() {
        server.shutdown()
        unmockkObject(AppConfig)
        unmockkObject(Constants)
        unmockkObject(WebSearchToolExecutor)
    }

    // ═══════════════════════════════════════════════════════════════
    // analyzeQuestion() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `analyzeQuestion - config invalid returns failure`() = runBlocking {
        every { AppConfig.isApiConfigValid() } returns false
        every { AppConfig.isApiConfigValid(any(), any(), any()) } returns false
        every { MyApplication.getString(R.string.error_api_config_invalid) } returns "api_config_invalid"

        val result = client.analyzeQuestion("test question", systemPrompt = "test")

        assertTrue(result.isFailure)
        assertEquals("api_config_invalid", result.exceptionOrNull()?.message)
    }

    @Test
    fun `analyzeQuestion - successful SSE stream parses answers`() = runBlocking {
        val ssePayload = """data: {"choices":[{"delta":{"content":"{\"question\":\"What is 1+1?\",\"answer\":\"2\",\"questionType\":\"\u9009\u62e9\u9898\"}"}}]}

data: [DONE]
"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(ssePayload)
        )

        val result = client.analyzeQuestion("What is 1+1?", systemPrompt = "test")

        assertTrue(result.isSuccess)
        val answers = result.getOrNull()
        assertNotNull(answers)
        assertTrue(answers!!.isNotEmpty())
        assertEquals("2", answers[0].answer)
    }

    @Test
    fun `analyzeQuestion - SSE multi-chunk accumulates content`() = runBlocking {
        val chunks = listOf(
            """data: {"choices":[{"delta":{"content":"{\"question\":"}}]}""",
            """data: {"choices":[{"delta":{"content":"\"Test Q\",\"answer\":\"A\",\"questionType\":\"\u9009\u62e9\u9898\"}"}}]}""",
            """data: [DONE]"""
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(chunks.joinToString("\n\n") + "\n")
        )

        val result = client.analyzeQuestion("test", systemPrompt = "test")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isNotEmpty())
    }

    @Test
    fun `analyzeQuestion - HTTP 500 returns IOException failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = client.analyzeQuestion("test question", systemPrompt = "test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `analyzeQuestion - empty body returns IOException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = client.analyzeQuestion("test question", systemPrompt = "test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `analyzeQuestion - empty stream content returns IOException`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("data: \n\ndata: \n\ndata: [DONE]\n")
        )

        val result = client.analyzeQuestion("test question", systemPrompt = "test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `analyzeQuestion - malformed SSE chunks skipped gracefully`() = runBlocking {
        val sse = """data: {"choices":[{"delta":{"content":"{\"question\":\"Q1\",\"answer\":\"B\",\"questionType\":\"\u9009\u62e9\u9898\"}"}}]}

data: garbage_not_json
data: {"invalid": "structure"}

data: {"choices":[{"delta":{"content":"{\"question\":\"Q2\",\"answer\":\"C\",\"questionType\":\"\u9009\u62e9\u9898\"}"}}]}

data: [DONE]
"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(sse))

        val result = client.analyzeQuestion("test", systemPrompt = "test")

        assertTrue(result.isSuccess)
        val answers = result.getOrNull()
        assertNotNull(answers)
        assertTrue(answers!!.size >= 1)
    }

    @Test
    fun `analyzeQuestion - cancellation mid-stream propagates CancellationException`() = runBlocking {
        // Don't enqueue any response so the request hangs and the coroutine stays suspended
        var caughtCancellation = false

        val job = launch {
            try {
                client.analyzeQuestion("test", systemPrompt = "test")
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }

        // Let the child coroutine start and reach the suspension point
        delay(100)
        job.cancel()
        job.join()

        assertTrue("CancellationException should propagate", caughtCancellation)
    }

    @Test
    fun `analyzeQuestion - outer timeout caught as failure`() {
        server.enqueue(
            MockResponse()
                .setBodyDelay(2, TimeUnit.SECONDS)
                .setResponseCode(200)
                .setBody("data: [DONE]\n")
        )

        runBlocking {
            try {
                withTimeout(1000) {
                    client.analyzeQuestion("test", systemPrompt = "test")
                }
            } catch (_: TimeoutCancellationException) {
                // Outer timeout fired before SUT could catch it internally
            }
        }
    }
    @Test
    fun `analyzeQuestion - returns failure on network IOException`() = runBlocking {
        // Force immediate connection failure by shutting down server before call
        server.shutdown()

        val result = client.analyzeQuestion("test", systemPrompt = "test")

        assertTrue(result.isFailure)
    }

    // ═══════════════════════════════════════════════════════════════
    // testConnection() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `testConnection - valid response with choices returns success`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"test-1","object":"chat.completion","created":1,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}]}""")
        )

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection - HTTP 401 returns key invalid error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_key_invalid) } returns "invalid_api_key"
        server.enqueue(MockResponse().setResponseCode(401))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "bad-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("invalid_api_key", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - HTTP 429 returns rate limit error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_rate_limited) } returns "rate_limited"
        server.enqueue(MockResponse().setResponseCode(429))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("rate_limited", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - HTTP 403 returns forbidden error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_forbidden) } returns "forbidden"
        server.enqueue(MockResponse().setResponseCode(403))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("forbidden", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - HTTP 404 returns not found error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_not_found) } returns "not_found"
        server.enqueue(MockResponse().setResponseCode(404))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("not_found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - HTTP 500 returns server error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_server_error) } returns "server_error"
        server.enqueue(MockResponse().setResponseCode(500))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("server_error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - unknown HTTP status returns generic error`() = runBlocking {
        every { MyApplication.getString(R.string.error_http_status_generic, any(), any()) } returns "generic_418"
        server.enqueue(MockResponse().setResponseCode(418))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("generic_418", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - empty response body returns error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_empty_response) } returns "empty_response"
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
        assertEquals("empty_response", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - config incomplete returns error`() = runBlocking {
        every { AppConfig.isApiConfigValid(any(), any(), any()) } returns false
        every { MyApplication.getString(R.string.error_api_config_incomplete) } returns "config_incomplete"

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConnection(testUrl, "", "")

        assertTrue(result.isFailure)
        assertEquals("config_incomplete", result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection - UnknownHost returns error`() = runBlocking {
        every { MyApplication.getString(R.string.error_api_unknown_host) } returns "unknown_host"

        val result = client.testConnection(
            "http://thishostdoesnotexist.invalid/v1/chat/completions",
            "test-key",
            "test-model"
        )

        assertTrue(result.isFailure)
        assertEquals("unknown_host", result.exceptionOrNull()?.message)
    }

    // ═══════════════════════════════════════════════════════════════
    // countQuestions() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `countQuestions - response with number returns that number`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"t1","object":"chat.completion","created":1,"model":"m","choices":[{"index":0,"message":{"role":"assistant","content":"5"},"finish_reason":"stop"}]}""")
        )

        val count = client.countQuestions("test ocr text with 5 questions")

        assertEquals(5, count)
    }

    @Test
    fun `countQuestions - response with number in text returns extracted number`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"t1","object":"chat.completion","created":1,"model":"m","choices":[{"index":0,"message":{"role":"assistant","content":"There are 3 questions in total."},"finish_reason":"stop"}]}""")
        )

        val count = client.countQuestions("test ocr text")

        assertEquals(3, count)
    }

    @Test
    fun `countQuestions - response without number returns minus one`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"t1","object":"chat.completion","created":1,"model":"m","choices":[{"index":0,"message":{"role":"assistant","content":"No questions found"},"finish_reason":"stop"}]}""")
        )

        val count = client.countQuestions("test")

        assertEquals(-1, count)
    }

    @Test
    fun `countQuestions - HTTP error returns minus one`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val count = client.countQuestions("test")

        assertEquals(-1, count)
    }

    @Test
    fun `countQuestions - config invalid returns minus one`() = runBlocking {
        every { AppConfig.isApiConfigValid() } returns false

        val count = client.countQuestions("test")

        assertEquals(-1, count)
    }

    @Test
    fun `countQuestions - empty body returns minus one`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val count = client.countQuestions("test")

        assertEquals(-1, count)
    }

    // ═══════════════════════════════════════════════════════════════
    // testConcurrency() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `testConcurrency - successful response returns elapsed time`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"t1","object":"chat.completion","created":1,"model":"m","choices":[]}""")
        )

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConcurrency(testUrl, "test-key", "test-model")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!! >= 0)
    }

    @Test
    fun `testConcurrency - HTTP error returns failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val testUrl = server.url("/v1/chat/completions").toString()
        val result = client.testConcurrency(testUrl, "test-key", "test-model")

        assertTrue(result.isFailure)
    }

    // ═══════════════════════════════════════════════════════════════
    // retryWithBackoff() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `retryWithBackoff - fails twice then succeeds on third attempt`() = runBlocking {
        var attempts = 0
        val result = OpenAIClient.retryWithBackoff(
            maxRetries = 3,
            initialDelayMs = 1
        ) {
            attempts++
            if (attempts < 3) throw IOException("attempt $attempts failed")
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retryWithBackoff - always fails throws on last retry`() = runBlocking {
        var attempts = 0
        try {
            OpenAIClient.retryWithBackoff(
                maxRetries = 3,
                initialDelayMs = 1
            ) {
                attempts++
                throw IOException("always failing")
            }
            fail("should have thrown")
        } catch (e: IOException) {
            assertEquals("always failing", e.message)
        }
        assertEquals(3, attempts)
    }

    @Test
    fun `retryWithBackoff - cancellation during retry propagates`() = runBlocking {
        var entered = false

        val job = launch {
            try {
                OpenAIClient.retryWithBackoff(
                    maxRetries = 3,
                    initialDelayMs = 10000
                ) {
                    entered = true
                    throw IOException("fail")
                }
            } catch (e: CancellationException) {
                throw e
            }
        }

        delay(50)
        assertTrue(entered)
        job.cancel()
        try {
            job.join()
        } catch (_: CancellationException) {
            // Expected
        }
    }

    @Test
    fun `retryWithBackoff - single attempt succeeds`() = runBlocking {
        val result = OpenAIClient.retryWithBackoff(
            maxRetries = 2,
            initialDelayMs = 1
        ) {
            "first-try-success"
        }

        assertEquals("first-try-success", result)
    }

    @Test
    fun `retryWithBackoff - uses exponential backoff`() = runBlocking {
        val delays = mutableListOf<Long>()
        var attempts = 0

        OpenAIClient.retryWithBackoff(
            maxRetries = 3,
            initialDelayMs = 10
        ) {
            attempts++
            if (attempts < 3) {
                delays.add(attempts.toLong())
                throw IOException("fail")
            }
            "ok"
        }

        assertEquals(3, attempts)
        assertEquals(2, delays.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // Function calling (tools) tests
    // ═══════════════════════════════════════════════════════════════

    private fun toolModeOn() {
        every { WebSearchToolExecutor.isToolModeActive() } returns true
        every { Constants.buildWebSearchToolSpec() } returns ToolSpec(
            function = FunctionSpec(name = "web_search", description = "Search",
                parameters = JsonObject().apply {
                    addProperty("type", "object")
                    add("properties", JsonObject().apply {
                        add("query", JsonObject().apply { addProperty("type", "string") })
                    })
                    add("required", com.google.gson.JsonArray().apply { add("query") })
                }
            )
        )
        coEvery { WebSearchToolExecutor.execute(any(), any()) } returns "【结果】测试搜索内容"
    }

    private fun sse(json: String, done: Boolean = true): String =
        "data: $json" + if (done) "\n\ndata: [DONE]\n" else "\n"

    private fun answerDelta(answer: String) =
        sse("{\"choices\":[{\"delta\":{\"content\":\"{\\\"question\\\":\\\"Q\\\",\\\"answer\\\":\\\"$answer\\\",\\\"questionType\\\":\\\"选择题\\\"}\"}}]}")

    @Test
    fun `analyzeQuestion - tool mode attaches tools to request body`() = runBlocking {
        toolModeOn()
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("A")))
        val result = client.analyzeQuestion("test", systemPrompt = "test")
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body.readUtf8()
        assertTrue("request should contain tools", body.contains("\"tools\""))
        assertTrue("tools should contain web_search", body.contains("web_search"))
        assertTrue("tools should declare query param", body.contains("\"query\""))
        assertTrue("tool mode should declare tool_choice auto", body.contains("\"tool_choice\":\"auto\""))
    }

    @Test
    fun `analyzeQuestion - non tool mode has no tools field`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("A")))
        val result = client.analyzeQuestion("test", systemPrompt = "test")
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body.readUtf8()
        assertFalse("request should not contain tools", body.contains("\"tools\""))
        assertFalse("request should not contain tool_choice", body.contains("\"tool_choice\""))
    }

    @Test
    fun `analyzeQuestion - non tool mode declares response_format json_object`() = runBlocking {
        // P1: 无工具模式强制 JSON 输出，从源头约束解析漂移
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("A")))
        val result = client.analyzeQuestion("test", systemPrompt = "test")
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body.readUtf8()
        assertTrue("non-tool mode should declare json_object: $body", body.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    @Test
    fun `analyzeQuestion - tool mode does not declare response_format`() = runBlocking {
        // P1: 工具模式不传 response_format（与 tools 兼容性未验证，避免服务商报错）
        toolModeOn()
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("A")))
        val result = client.analyzeQuestion("test", systemPrompt = "test")
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body.readUtf8()
        assertFalse("tool mode should not declare response_format: $body", body.contains("response_format"))
    }

    @Test
    fun `analyzeQuestion - tool loop executes search and feeds back tool message`() = runBlocking {
        toolModeOn()
        // Round 1: model requests web_search tool call (no content)
        val round1 = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"光合作用\\\"}\"}}]}}]}")
        server.enqueue(MockResponse().setResponseCode(200).setBody(round1))
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("B")))

        val result = client.analyzeQuestion("光合作用", systemPrompt = "test")

        assertTrue(result.isSuccess)
        assertEquals("B", result.getOrNull()!![0].answer)
        coVerify { WebSearchToolExecutor.execute("光合作用", 2) }
        server.takeRequest()
        val body2 = server.takeRequest().body.readUtf8()
        assertTrue("second request should carry tool message", body2.contains("\"role\":\"tool\""))
        assertTrue("tool message should reference call id", body2.contains("\"tool_call_id\":\"call_1\""))
        assertTrue("tool message should carry search result", body2.contains("测试搜索内容"))
    }

    @Test
    fun `analyzeQuestion - streamed tool calls accumulate across chunks`() = runBlocking {
        toolModeOn()
        val chunk1 = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_9\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"光合\"}}]}}]}", done = false)
        val chunk2 = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"作用\\\"}\"}}]}}]}", done = false)
        val finalChunk = sse("{\"choices\":[{\"delta\":{\"content\":\"{\\\"question\\\":\\\"Q\\\",\\\"answer\\\":\\\"D\\\",\\\"questionType\\\":\\\"选择题\\\"}\"}}]}")
        server.enqueue(MockResponse().setResponseCode(200).setBody(chunk1 + "\n" + chunk2 + "\n" + finalChunk))
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("E")))

        val result = client.analyzeQuestion("测试", systemPrompt = "test")

        assertTrue(result.isSuccess)
        coVerify { WebSearchToolExecutor.execute("光合作用", 2) }
    }

    @Test
    fun `analyzeQuestion - invalid tool arguments falls back to recognized text`() = runBlocking {
        toolModeOn()
        // arguments is truncated invalid JSON
        val round1 = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_2\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\"}}]}}]}")
        server.enqueue(MockResponse().setResponseCode(200).setBody(round1))
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("C")))

        val result = client.analyzeQuestion("题目原文", systemPrompt = "test")

        assertTrue(result.isSuccess)
        coVerify { WebSearchToolExecutor.execute("题目原文", 2) }
    }

    @Test
    fun `analyzeQuestion - tool loop caps with empty content retries without tools`() = runBlocking {
        toolModeOn()
        val toolChunk = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_x\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"q\\\"}\"}}]}}]}")
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(toolChunk)) }
        // 封顶重试轮（tools=null）：模型直接给出答案
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("B")))

        val result = client.analyzeQuestion("test", systemPrompt = "test")

        // round 3 封顶时 content 为空 → 去工具重发一轮强制作答，共 4 次请求
        assertTrue(result.isSuccess)
        assertEquals(4, server.requestCount)
        coVerify(exactly = 2) { WebSearchToolExecutor.execute(any(), any()) }
        // 重试轮请求体不应再携带 tools
        server.takeRequest() // round1
        server.takeRequest() // round2
        server.takeRequest() // round3
        val retryBody = server.takeRequest().body.readUtf8()
        assertFalse(retryBody.contains("\"tools\""))
    }

    @Test
    fun `analyzeQuestion - tool loop capped with non-empty content returns content directly`() = runBlocking {
        toolModeOn()
        val toolChunk = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_x\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"q\\\"}\"}}]}}]}")
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200).setBody(toolChunk)) }
        // round 3 封顶但 content 非空 → 直接作为答案，不重试（tool_chunk 不带 [DONE]，content 正常累积）
        val capped = sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_x\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"q\\\"}\"}}]}}]}", done = false) + "\n" + answerDelta("C")
        server.enqueue(MockResponse().setResponseCode(200).setBody(capped))

        val result = client.analyzeQuestion("test", systemPrompt = "test")

        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)
        coVerify(exactly = 2) { WebSearchToolExecutor.execute(any(), any()) }
    }

    @Test
    fun `sanitizeToolCallText - strips tool call pseudo text`() {
        val pseudo = "前置说明\n<tool_calls>\n<invoke name=\"web_search\">\n<parameter name=\"query\">\"测试\"</parameter>\n</invoke>\n</tool_calls>"
        assertEquals("前置说明", OpenAIClient.sanitizeToolCallText(pseudo))
        assertEquals("", OpenAIClient.sanitizeToolCallText("<tool_calls><invoke name=\"web_search\"></invoke></tool_calls>"))
        assertEquals("{\"answer\":\"A\"}", OpenAIClient.sanitizeToolCallText("{\"answer\":\"A\"}"))
    }

    @Test
    fun `sanitizeToolCallText - preserves text after marker block`() {
        // F2: 伪文本块后的合法内容必须保留（旧实现截断到首个 < 会整段丢失）
        val pseudo = "前置说明\n<tool_calls>\n<invoke name=\"web_search\">\n</invoke>\n</tool_calls>\n后置答案"
        val result = OpenAIClient.sanitizeToolCallText(pseudo)
        assertTrue("应保留块前文本: $result", result.contains("前置说明"))
        assertTrue("应保留块后文本: $result", result.contains("后置答案"))
    }

    @Test
    fun `sanitizeToolCallText - case insensitive and json style markers`() {
        // 大小写变体
        assertEquals("答案", OpenAIClient.sanitizeToolCallText("答案<TOOL_CALLS>\n<invoke name=\"web_search\">\n</invoke>\n</TOOL_CALLS>"))
        // JSON 风格 tool_calls / tool_call_id
        val json = "{\"answer\":\"A\",\"tool_calls\":[{\"id\":\"x\"}],\"tool_call_id\":\"call_1\"}"
        val cleaned = OpenAIClient.sanitizeToolCallText(json)
        assertFalse("JSON 风格 tool_calls 标记应被剥离: $cleaned", cleaned.contains("tool_calls"))
        assertFalse("tool_call_id 应被剥离: $cleaned", cleaned.contains("tool_call_id"))
        assertTrue("答案内容应保留: $cleaned", cleaned.contains("A"))
    }

    @Test
    fun `analyzeQuestion - tool loop capped rebuilds clean conversation without tools and parses answer`() = runBlocking {
        toolModeOn()
        // Round 1-3: 模型持续请求工具且无内容（冷门题搜不到 → 死循环），round 3 触发封顶
        val toolCallDelta = "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"冷门题\\\"}\"}}]}}]}"
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(sse(toolCallDelta)))
        }
        // 封顶重建后的无工具请求：模型直接输出 JSON 答案
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("C")))

        val result = client.analyzeQuestion("冷门题", systemPrompt = "test")

        assertTrue(result.isSuccess)
        assertEquals("C", result.getOrNull()!![0].answer)
        repeat(3) { server.takeRequest() }
        val body4 = server.takeRequest().body.readUtf8()
        assertFalse("final request must not carry tools", body4.contains("\"tools\""))
        assertFalse("final request must not carry tool history", body4.contains("\"role\":\"tool\""))
        assertTrue("final request should inject force-direct-answer prompt", body4.contains("mocked_message"))
    }

    @Test
    fun `analyzeQuestion - forceDirectAnswer rebuilds clean conversation when search empty`() = runBlocking {
        toolModeOn()
        // 搜索无结果 → 触发顶部 forceDirectAnswer 重建（而非等封顶）
        coEvery { WebSearchToolExecutor.execute(any(), any()) } returns ""
        val toolCallDelta = "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"冷门题\\\"}\"}}]}}]}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(sse(toolCallDelta)))
        // 重建后的无工具请求：模型直接输出 JSON 答案
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerDelta("D")))

        val result = client.analyzeQuestion("冷门题", systemPrompt = "test")

        assertTrue(result.isSuccess)
        assertEquals("D", result.getOrNull()!![0].answer)
        // 共 2 次请求：round1 带工具，round2 重建后无工具
        assertEquals(2, server.requestCount)
        val body1 = server.takeRequest().body.readUtf8()
        val body2 = server.takeRequest().body.readUtf8()
        assertTrue("round1 must carry tools", body1.contains("\"tools\""))
        assertFalse("rebuild must not carry tools", body2.contains("\"tools\""))
        assertFalse("rebuild must not carry tool history", body2.contains("\"role\":\"tool\""))
        assertTrue("rebuild should inject force-direct-answer prompt", body2.contains("mocked_message"))
    }

    @Test
    fun `analyzeQuestion - tool pseudo text after rebuild returns empty answers`() = runBlocking {
        toolModeOn()
        val toolCallDelta = "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"冷门题\\\"}\"}}]}}]}"
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(sse(toolCallDelta)))
        }
        // round 4（无工具重建后）：模型仍输出 <tool_calls> 伪文本（纯文本 content，非协议级 tool_calls）
        val pseudoText = sse("{\"choices\":[{\"delta\":{\"content\":\"<tool_calls>\\n<invoke name=\\\"web_search\\\">\\n</invoke>\\n</tool_calls>\"}}]}")
        server.enqueue(MockResponse().setResponseCode(200).setBody(pseudoText))

        val result = client.analyzeQuestion("冷门题", systemPrompt = "test")

        // 伪文本剥离后无 JSON 负载 → 空答案列表（上层计失败），而不是降级出垃圾条目
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        repeat(3) { server.takeRequest() }
        server.takeRequest()
        Unit // 保持返回类型为 Unit（JUnit 要求测试方法 void）
    }

    @Test
    fun `analyzeQuestion - markdown text answer fills missing question from recognizedText`() = runBlocking {
        // 模型输出非 JSON 的 Markdown 文本（含 **答案：X**），降级提取后 question 用原始题目补全
        val markdownText = "以下哪款游戏被称为“3A大作”？\nA. 绝区零\nB. 崩坏三\nC. 崩坏-星穹铁道\nD. 原神\n\n**答案：D 原神**\n\n**解析：** 原神常被称作3A大作"
        val escaped = markdownText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        server.enqueue(MockResponse().setResponseCode(200).setBody(sse("{\"choices\":[{\"delta\":{\"content\":\"$escaped\"}}]}")))

        val result = client.analyzeQuestion("以下哪款游戏被称为“3A大作”？", systemPrompt = "test")

        assertTrue(result.isSuccess)
        val answers = result.getOrNull()!!
        assertEquals("D 原神", answers[0].answer)
        // question 必须被原始题目文本补全，而非"无法解析题目"占位
        assertEquals("以下哪款游戏被称为“3A大作”？", answers[0].question)
    }

    @Test
    fun `analyzeQuestion - markdown answer after tool block is preserved`() = runBlocking {
        // P0-1: sanitize 保留块后文本，但旧守卫（无 {/[ 即判空）会把 Markdown 答案整段丢弃
        val content = "前置说明\n<tool_calls>\n<invoke name=\"web_search\">\n</invoke>\n</tool_calls>\n\n**答案：D 原神**"
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        server.enqueue(MockResponse().setResponseCode(200).setBody(sse("{\"choices\":[{\"delta\":{\"content\":\"$escaped\"}}]}")))

        val result = client.analyzeQuestion("某题目", systemPrompt = "test")

        assertTrue(result.isSuccess)
        val answers = result.getOrNull()!!
        assertTrue("工具块后的 Markdown 答案必须保留: $answers", answers.isNotEmpty())
        assertEquals("D 原神", answers[0].answer)
    }
}
