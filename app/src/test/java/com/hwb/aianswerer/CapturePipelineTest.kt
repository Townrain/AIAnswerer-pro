package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.search.BaseWebSearchProvider
import com.hwb.aianswerer.api.search.WebSearchClientFactory
import com.hwb.aianswerer.api.search.WebSearchResult
import com.hwb.aianswerer.api.search.WebSearchToolExecutor
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.api.vision.VisionProvider
import com.hwb.aianswerer.api.vision.SeparatedQuestion
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.providers.LocalWebSearchConfig
import com.hwb.aianswerer.providers.WebSearchStorage
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CapturePipelineTest {

    private lateinit var pipeline: CapturePipeline
    private lateinit var mockTextRecognition: TextRecognitionManager
    private lateinit var mockOpenAiClient: OpenAIClient
    private lateinit var mockVisionProvider: VisionProvider
    private lateinit var mockSearchProvider: BaseWebSearchProvider
    private lateinit var mockBitmap: Bitmap

    @Before
    fun setUp() {
        mockTextRecognition = mockk(relaxed = true)
        mockOpenAiClient = mockk(relaxed = true)
        mockVisionProvider = mockk(relaxed = true)
        mockSearchProvider = mockk(relaxed = true)
        mockBitmap = mockk(relaxed = true)

        mockkObject(AppConfig)
        mockkObject(WebSearchStorage)
        mockkObject(WebSearchClientFactory)

        pipeline = CapturePipeline(
            textRecognitionManager = mockTextRecognition,
            openAiClient = mockOpenAiClient,
            createVisionProvider = { mockVisionProvider }
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── recognizeOcr ──

    @Test
    fun `recognizeOcr delegates to TextRecognitionManager`() = runBlocking {
        val expectedText = "这是一道选择题"
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.success(expectedText)

        val result = pipeline.recognizeOcr(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals(expectedText, result.getOrNull())
    }

    @Test
    fun `recognizeOcr returns failure when TextRecognitionManager fails`() = runBlocking {
        val error = Exception("ML Kit not available")
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.failure(error)

        val result = pipeline.recognizeOcr(mockBitmap)

        assertTrue(result.isFailure)
    }

    // ── recognizeVlm ──

    @Test
    fun `recognizeVlm succeeds when provider available`() = runBlocking {
        val filterResult = VisionFilterResult(hasQuestions = true, questions = listOf(SeparatedQuestion(text = "题目1")))
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.success(filterResult)

        val result = pipeline.recognizeVlm(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals(filterResult, result.getOrNull())
    }

    @Test
    fun `recognizeVlm fails when factory returns null`() = runBlocking {
        pipeline = CapturePipeline(
            textRecognitionManager = mockTextRecognition,
            openAiClient = mockOpenAiClient,
            createVisionProvider = { null }
        )

        val result = pipeline.recognizeVlm(mockBitmap)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not available") == true)
    }

    // ── askLlm ──

    @Test
    fun `askLlm delegates to OpenAIClient`() = runBlocking {
        val answers = listOf(AIAnswer(question = "测试题目", questionType = "单选题", answer = "A"))
        coEvery {
            mockOpenAiClient.analyzeQuestion("测试题目", any(), any(), any())
        } returns Result.success(answers)

        val result = pipeline.askLlm("测试题目", setOf("单选题"), "")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("A", result.getOrNull()?.first()?.answer)
    }

    @Test
    fun `askLlm returns failure when OpenAIClient fails`() = runBlocking {
        coEvery {
            mockOpenAiClient.analyzeQuestion(any(), any(), any(), any())
        } returns Result.failure(Exception("API error"))

        val result = pipeline.askLlm("题目", emptySet(), "")

        assertTrue(result.isFailure)
    }

    @Test
    fun `askLlm passes search context to OpenAIClient`() = runBlocking {
        var capturedSearchContext = ""
        coEvery {
            mockOpenAiClient.analyzeQuestion(any(), any(), any<String>(), any())
        } answers {
            capturedSearchContext = arg<String>(2)
            Result.success(emptyList())
        }

        pipeline.askLlm("题目", emptySet(), "搜索结果内容")

        assertEquals("搜索结果内容", capturedSearchContext)
    }

    // ── looksLikeQuestion ──

    @Test
    fun `looksLikeQuestion returns false for short text`() {
        assertFalse(pipeline.looksLikeQuestion("ab"))
        assertFalse(pipeline.looksLikeQuestion(""))
        assertFalse(pipeline.looksLikeQuestion("   "))
    }

    @Test
    fun `looksLikeQuestion returns true for question marks`() {
        assertTrue(pipeline.looksLikeQuestion("这是什么东西？"))
        assertTrue(pipeline.looksLikeQuestion("What is this?"))
    }

    @Test
    fun `looksLikeQuestion returns true for option markers`() {
        assertTrue(pipeline.looksLikeQuestion("A. 第一选项 B. 第二选项 C. 第三选项"))
        assertTrue(pipeline.looksLikeQuestion("a) 选项一 b) 选项二"))
    }

    @Test
    fun `looksLikeQuestion returns true for Chinese keywords`() {
        assertTrue(pipeline.looksLikeQuestion("下列关于人工智能的说法"))
        assertTrue(pipeline.looksLikeQuestion("以下哪个是正确答案"))
        assertTrue(pipeline.looksLikeQuestion("属于操作系统的是"))
        assertTrue(pipeline.looksLikeQuestion("不属于哺乳动物的是"))
        assertTrue(pipeline.looksLikeQuestion("正确的说法是什么"))
        assertTrue(pipeline.looksLikeQuestion("错误的是哪个选项"))
    }

    @Test
    fun `looksLikeQuestion returns true for English keywords`() {
        assertTrue(pipeline.looksLikeQuestion("Which of the following is correct?"))
        assertTrue(pipeline.looksLikeQuestion("What is the capital of France?"))
        assertTrue(pipeline.looksLikeQuestion("How does this algorithm work?"))
        assertTrue(pipeline.looksLikeQuestion("Why is the sky blue?"))
    }

    @Test
    fun `looksLikeQuestion returns false for non-question text`() {
        assertFalse(pipeline.looksLikeQuestion("这是一个普通的句子。"))
        assertFalse(pipeline.looksLikeQuestion("Hello world"))
        assertFalse(pipeline.looksLikeQuestion("12345678"))
    }

    @Test
    fun `looksLikeQuestion case insensitive for keywords`() {
        assertTrue(pipeline.looksLikeQuestion("WHICH option is CORRECT"))
        assertTrue(pipeline.looksLikeQuestion("what is this"))
    }
}
