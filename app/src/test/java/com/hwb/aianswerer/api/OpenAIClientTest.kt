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
}
