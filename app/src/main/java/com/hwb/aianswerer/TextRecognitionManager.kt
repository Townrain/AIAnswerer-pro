package com.hwb.aianswerer

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 文本识别单例。
 * 使用 ChineseTextRecognizerOptions — 该选项同时支持中文和拉丁字符识别，
 * 无需为英文单独配置识别器。
 */
class TextRecognitionManager {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 识别图片中的文本
     *
     * 使用suspendCancellableCoroutine将ML Kit的回调式API转换为挂起函数，
     * 每个回调都需要检查continuation.isCompleted防止重复resume导致崩溃
     */
    suspend fun recognizeText(bitmap: Bitmap): Result<String> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                // 协程取消时不关闭recognizer（它是单例，close后不可用）
                // ML Kit任务取消不需要关闭客户端，忽略结果即可
            }
            try {
                val image = InputImage.fromBitmap(bitmap, 0)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val recognizedText = visionText.text
                        if (recognizedText.isNotBlank()) {
                            if (!continuation.isCompleted) {
                                continuation.resume(Result.success(recognizedText))
                            }
                        } else {
                            if (!continuation.isCompleted) {
                                continuation.resume(
                                    Result.failure(
                                        Exception(
                                            MyApplication.getString(R.string.error_no_text_recognized)
                                        )
                                    )
                                )
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        if (!continuation.isCompleted) {
                            // 返回Result.failure而不是抛出异常，与函数签名一致
                            continuation.resume(Result.failure(e))
                        }
                    }
                    .addOnCanceledListener {
                        if (!continuation.isCompleted) {
                            continuation.resume(
                                Result.failure(
                                    Exception(
                                        MyApplication.getString(R.string.error_recognition_cancelled)
                                    )
                                )
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    // 返回Result.failure而不是抛出异常，与函数签名一致
                    continuation.resume(Result.failure(e))
                }
            }
        }

    /**
     * 关闭识别器
     */
    fun close() {
        recognizer.close()
        instance = null
    }

    companion object {
        @Volatile
        private var instance: TextRecognitionManager? = null

        fun getInstance(): TextRecognitionManager {
            return instance ?: synchronized(this) {
                instance ?: TextRecognitionManager().also { instance = it }
            }
        }
    }
}

