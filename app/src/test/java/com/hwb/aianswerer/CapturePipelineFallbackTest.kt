package com.hwb.aianswerer

import android.graphics.Bitmap
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.vision.VisionFilterResult
import com.hwb.aianswerer.api.vision.VisionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CapturePipeline.recognizeToText（多图采集复用录制策略：VLM 优先、失败/无题降级 OCR）行为验证。
 *
 * 场景：VLM 成功返回提取文本 / VLM 无题降级 OCR / VLM 提取文本为空降级 OCR /
 *       VLM provider 不可用降级 OCR / VLM 失败降级 OCR / OCR 也失败返回失败 / VLM 成功但 OCR 失败。
 */
class CapturePipelineFallbackTest {

    private lateinit var pipeline: CapturePipeline
    private val mockTextRecognition = mockk<TextRecognitionManager>(relaxed = true)
    private val mockOpenAiClient = mockk<OpenAIClient>(relaxed = true)
    private val mockVisionProvider = mockk<VisionProvider>(relaxed = true)
    private val mockBitmap = mockk<Bitmap>(relaxed = true)

    @Before
    fun setUp() {
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

    private fun vlmResult(hasQuestions: Boolean, extractedText: String) =
        VisionFilterResult(hasQuestions = hasQuestions, extractedText = extractedText)

    @Test
    fun `recognizeToText_vlm_success_returns_extracted_text_without_ocr`() = runBlocking {
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.success(vlmResult(true, "VLM提取的题干"))
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.success("OCR内容")

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals("VLM提取的题干", result.getOrNull())
        coVerify(exactly = 0) { mockTextRecognition.recognizeText(any()) }
    }

    @Test
    fun `recognizeToText_vlm_no_questions_falls_back_to_ocr`() = runBlocking {
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.success(vlmResult(false, "噪音文本"))
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.success("OCR内容")

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals("OCR内容", result.getOrNull())
    }

    @Test
    fun `recognizeToText_vlm_blank_extracted_text_falls_back_to_ocr`() = runBlocking {
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.success(vlmResult(true, "   "))
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.success("OCR内容")

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals("OCR内容", result.getOrNull())
    }

    @Test
    fun `recognizeToText_vlm_failure_falls_back_to_ocr`() = runBlocking {
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.failure(Exception("VLM API error"))
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.success("OCR内容")

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals("OCR内容", result.getOrNull())
    }

    @Test
    fun `recognizeToText_vlm_provider_unavailable_falls_back_to_ocr`() = runBlocking {
        pipeline = CapturePipeline(
            textRecognitionManager = mockTextRecognition,
            openAiClient = mockOpenAiClient,
            createVisionProvider = { null }
        )
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.success("OCR内容")

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isSuccess)
        assertEquals("OCR内容", result.getOrNull())
    }

    @Test
    fun `recognizeToText_vlm_failure_and_ocr_failure_returns_failure`() = runBlocking {
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.failure(Exception("VLM down"))
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.failure(Exception("ML Kit down"))

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isFailure)
    }

    @Test
    fun `recognizeToText_vlm_no_questions_and_ocr_failure_returns_failure`() = runBlocking {
        coEvery { mockVisionProvider.analyze(mockBitmap) } returns Result.success(vlmResult(false, ""))
        coEvery { mockTextRecognition.recognizeText(mockBitmap) } returns Result.failure(Exception("ML Kit down"))

        val result = pipeline.recognizeToText(mockBitmap)

        assertTrue(result.isFailure)
    }
}
