package com.hwb.aianswerer

import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OpenAIClient.dedupeText（专职去重 LLM，非流式轻量调用）HTTP 层行为验证。
 *
 * 场景：200 有内容（裁剪空白）/ 200 空 content 失败 / 200 缺 content 失败 /
 *       HTTP 400 失败（带状态码）/ HTTP 500 失败 / 响应 JSON 结构异常失败 /
 *       API 配置无效失败 / choices 为空失败。
 */
class DedupeTextApiTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAIClient

    private var configValid = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/v1/chat/completions").toString()

        mockkObject(AppConfig)
        every { AppConfig.getApiUrl() } returns baseUrl
        every { AppConfig.getApiKey() } returns "test-api-key"
        every { AppConfig.getModelName() } returns "test-model"
        every { AppConfig.isApiConfigValid() } answers { configValid }
        every { AppConfig.isApiConfigValid(any(), any(), any()) } answers { configValid }

        // Constants.getPromptResources() 提供 system_prompt_dedupe 资源
        val mockRes = mockk<android.content.res.Resources>(relaxed = true) {
            every { getString(any<Int>()) } returns "mocked_message"
            every { getString(any<Int>(), any<Any>()) } returns "mocked_formatted"
        }
        mockkObject(Constants)
        every { Constants.getPromptResources() } returns mockRes

        // 错误路径使用 MyApplication.getString 生成用户消息
        mockkObject(MyApplication.Companion)
        every { MyApplication.getString(any<Int>()) } returns "mocked_message"

        client = OpenAIClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkObject(Constants)
        unmockkAll()
    }

    private fun chatResponse(content: String?): String =
        """{"choices":[{"message":{"role":"assistant","content":${if (content == null) "null" else "\"$content\""}}}]}"""

    @Test
    fun `dedupeText_200_with_content_returns_trimmed_text`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatResponse("  去重后的干净题干  ")))

        val result = client.dedupeText("原始拼接文本")

        assertTrue(result.isSuccess)
        assertEquals("去重后的干净题干", result.getOrNull())
    }

    @Test
    fun `dedupeText_200_empty_content_returns_failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatResponse("")))

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
    }

    @Test
    fun `dedupeText_200_missing_content_returns_failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatResponse(null)))

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
    }

    @Test
    fun `dedupeText_200_empty_choices_returns_failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
    }

    @Test
    fun `dedupeText_http_400_returns_failure_with_status_code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request body"))

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 400") == true)
    }

    @Test
    fun `dedupeText_http_500_returns_failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 500") == true)
    }

    @Test
    fun `dedupeText_malformed_json_returns_failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json-at-all{{{"))

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
    }

    @Test
    fun `dedupeText_config_invalid_returns_failure_without_request`() = runBlocking {
        configValid = false

        val result = client.dedupeText("原始文本")

        assertTrue(result.isFailure)
        // 配置无效时不发请求
        assertEquals(0, server.requestCount)
    }
}
