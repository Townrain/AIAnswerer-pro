package com.hwb.aianswerer.api.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI 兼容格式的视觉模型 Provider
 *
 * 适用后端（只要符合 OpenAI Chat Completions 多模态格式）：
 *   - DeepSeek V4 (vision)
 *   - OpenAI GPT-4o / GPT-4.1-mini / GPT-5
 *   - 阿里百炼 DashScope (Qwen-VL-Max, OpenAI兼容模式)
 *   - 硅基流动 SiliconFlow
 *   - 智谱 GLM-4V
 *   - 任何自部署 vLLM / Ollama 兼容服务
 *
 * API 格式：
 *   POST {baseUrl}
 *   Body: {
 *     "model": "...",
 *     "messages": [{
 *       "role": "user",
 *       "content": [
 *         {"type": "text", "text": "..."},
 *         {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
 *       ]
 *     }],
 *     "temperature": 0.0,
 *     "max_tokens": 1024,
 *     "response_format": {"type": "json_object"}
 *   }
 */
class OpenAIVisionProvider(
    private val config: OpenAIVisionConfig
) : VisionProvider {

    override val providerId: String = "openai_compat"
    override val displayName: String = "OpenAI 兼容"

    private val gson = JsonUtil.gson
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    override suspend fun analyze(bitmap: Bitmap): Result<VisionFilterResult> = analyzeImages(listOf(bitmap), false)

    override suspend fun analyzeMultiple(bitmaps: List<Bitmap>): Result<VisionFilterResult> = analyzeImages(bitmaps, true)

    private suspend fun analyzeImages(bitmaps: List<Bitmap>, multiPage: Boolean): Result<VisionFilterResult> =
        withContext(Dispatchers.IO) {
            val _start = System.currentTimeMillis()
            try {
                AppLog.enter("VLM", "analyze ${bitmaps.size} images multiPage=$multiPage")
                val imageParts = bitmaps.map { bitmap ->
                    val b64 = encodeBitmap(bitmap)
                    ContentPart(
                        type = "image_url",
                        imageUrl = ImageUrlObj(url = "data:image/jpeg;base64,$b64")
                    )
                }

                val userContent = mutableListOf<ContentPart>()
                userContent.add(ContentPart(type = "text", text = if (multiPage) buildMultiPagePrompt() else buildSystemPrompt()))
                userContent.addAll(imageParts)

                AppLog.d("VLM", "encoded ${bitmaps.size} images, total chars=${imageParts.joinToString { it.imageUrl?.url?.length?.toString() ?: "0" }}")

                val requestBody = OpenAIVisionRequest(
                    model = config.modelName,
                    messages = listOf(
                        OpenAIMessage(role = "user", content = userContent)
                    ),
                    temperature = config.temperature,
                    maxTokens = if (multiPage) 8192 else config.maxTokens,
                    responseFormat = if (config.useJsonMode) {
                        ResponseFormat(type = "json_object")
                    } else null
                )

                val httpRequest = Request.Builder()
                    .url(config.baseUrl)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        config.extraHeaders.forEach { (k, v) ->
                            addHeader(k, v)
                        }
                    }
                    .post(gson.toJson(requestBody).toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                AppLog.net("VLM", "request to ${config.baseUrl} model=${config.modelName} images=${bitmaps.size}")
                val response = withTimeout(WITH_TIMEOUT_MS) {
                    val call = client.newCall(httpRequest)
                    suspendCancellableCoroutine { cont ->
                        cont.invokeOnCancellation {
                            AppLog.w("VLM", "call cancelled by timeout")
                            call.cancel()
                        }
                        call.enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                AppLog.e("VLM", "onFailure: ${e.message}", e)
                                if (!cont.isCancelled) cont.resumeWithException(e)
                            }
                            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                AppLog.net("VLM", "onResponse code=${response.code}")
                                if (!cont.isCancelled) cont.resume(response)
                                else response.close()
                            }
                        })
                    }
                }

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: ""
                        return@withContext Result.failure(
                            Exception("HTTP ${resp.code}: $errorBody")
                        )
                    }

                    val body = resp.body?.string() ?: ""
                    val chatResp = gson.fromJson(body, OpenAIVisionResponse::class.java)
                    val rawContent = chatResp.choices.firstOrNull()?.message?.contentRaw
                        ?: return@withContext Result.failure(Exception("空响应"))

                    val jsonStr = when (rawContent) {
                        is String -> rawContent
                        else -> gson.toJson(rawContent)
                    }

                    val parsed = parseResponse(jsonStr)
                    AppLog.d("VLM", "${parsed.questionCount}题 | ${parsed.searchKeywords}")
                    AppLog.leave("VLM", "analyze", _start)
                    Result.success(parsed.copy(rawResponse = body))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("VLM", "analyze failed", e)
                Result.failure(e)
            }
        }
    override fun validateConfig(): ConfigValidationResult {
        val errors = mutableListOf<String>()
        if (config.baseUrl.isBlank()) errors.add("API 地址不能为空")
        if (config.apiKey.isBlank()) errors.add("API Key 不能为空")
        if (config.modelName.isBlank()) errors.add("模型名称不能为空")
        return ConfigValidationResult(errors.isEmpty(), errors)
    }

    override fun getConfigDescriptor(): ProviderConfigDescriptor {
        return ProviderConfigDescriptor(
            fields = listOf(
                ConfigField.TextField("baseUrl", "API 地址", "https://api.deepseek.com/v1/chat/completions"),
                ConfigField.TextField("apiKey", "API Key", isPassword = true),
                ConfigField.TextField("modelName", "模型名称", "deepseek-chat"),
                ConfigField.TextField("temperature", "Temperature", "0.0"),
                ConfigField.TextField("maxTokens", "Max Tokens", "1024"),
                ConfigField.SwitchField("useJsonMode", "JSON 模式", "要求模型返回 JSON 格式", true),
            )
        )
    }

    /**
     * 测试API连接
     * 发送一个简单的请求验证配置是否正确
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 验证配置
            val validation = validateConfig()
            if (!validation.isValid) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_incomplete))
                )
            }

            // 构建最简单的测试请求（使用数组格式，兼容视觉API）
            val messages = listOf(
                OpenAIMessage(role = "user", content = listOf(ContentPart(type = "text", text = "hello")))
            )

            val request = OpenAIVisionRequest(
                model = config.modelName,
                messages = messages,
                temperature = 0.0,
                maxTokens = 10
            )

            val httpRequest = Request.Builder()
                .url(config.baseUrl)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(request).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            // 发送请求
            val response = client.newCall(httpRequest).execute()

            response.use { resp ->
                // 检查响应状态
                if (!resp.isSuccessful) {
                    val errorMessage = when (resp.code) {
                        401 -> R.string.error_api_key_invalid
                        403 -> R.string.error_api_forbidden
                        404 -> R.string.error_api_not_found
                        429 -> R.string.error_api_rate_limited
                        500, 502, 503 -> R.string.error_api_server_error
                        else -> null
                    }?.let { MyApplication.getString(it) }
                        ?: MyApplication.getString(
                            R.string.error_http_status_generic,
                            resp.code,
                            resp.message
                        )
                    return@withContext Result.failure(Exception(errorMessage))
                }

                // 验证响应体存在
                val responseBody = resp.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(
                        Exception(MyApplication.getString(R.string.error_api_empty_response))
                    )
                }

                // 尝试解析响应以验证格式正确
                try {
                    val chatResp = gson.fromJson(responseBody, OpenAIVisionResponse::class.java)
                    if (chatResp.choices.isEmpty()) {
                        return@withContext Result.failure(
                            Exception(MyApplication.getString(R.string.error_api_response_invalid))
                        )
                    }
                } catch (e: JsonSyntaxException) {
                    return@withContext Result.failure(
                        Exception(MyApplication.getString(R.string.error_api_response_error))
                    )
                }

                // 测试成功
                Result.success(MyApplication.getString(R.string.toast_connection_success))
            }

        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_unknown_host)))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_timeout)))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_ssl)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val unknownError = MyApplication.getString(R.string.error_unknown)
            Result.failure(
                Exception(
                    MyApplication.getString(
                        R.string.error_connection_test_failed,
                        e.message ?: unknownError
                    )
                )
            )
        }
    }

    // ==================== 私有方法 ====================

    private fun encodeBitmap(bitmap: Bitmap): String {
        val maxSize = config.maxImageWidth  // 最大尺寸限制（宽高都不超过此值）
        var scaled = bitmap

        // 如果宽或高超过最大尺寸，等比缩放
        if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val ratio = minOf(
                maxSize.toFloat() / bitmap.width,
                maxSize.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, config.imageQuality, baos)

        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildSystemPrompt(): String {
        val custom = com.hwb.aianswerer.config.AppConfig.getCustomVLMPrompt()
        if (custom.isNotBlank()) return custom
        return """
你是题目截图分析器。只返回JSON，不要解释：
{
  "has_questions": true或false,
  "question_count": 题数,
  "question_types": ["选择题"|"填空题"|"问答题"],
  "search_keywords": "核心搜索关键词(简短)",
  "extracted_text": "所有题目完整文本",
  "questions": [{"index": 1, "text": "题目文本", "search_keywords": "该题关键词"}]
}
规则：忽略UI噪声和广告。多题时必须分离到questions数组。无题目时has_questions=false。
""".trimIndent()
    }

    /**
     * 多图模式专用 prompt — 告知模型这是长文分页截图，需合并阅读
     */
    private fun buildMultiPagePrompt(): String {
        val custom = com.hwb.aianswerer.config.AppConfig.getCustomVLMPrompt()
        if (custom.isNotBlank()) return custom
        return """
你是长文分页截图分析器。以下多张截图是同一篇文章的多页连续截图（从上到下）。
请按顺序合并所有截图的内容，提取完整题目文本。只返回JSON，不要解释：
{
  "has_questions": true或false,
  "question_count": 题数,
  "question_types": ["选择题"|"填空题"|"问答题"],
  "search_keywords": "核心搜索关键词(简短)",
  "extracted_text": "合并后的所有题目完整文本",
  "questions": [{"index": 1, "text": "题目文本", "search_keywords": "该题关键词"}]
}
规则：忽略UI噪声和广告。跨页内容要拼接完整。多题时必须分离到questions数组。无题目时has_questions=false。
""".trimIndent()
    }

    private fun parseResponse(jsonStr: String): VisionFilterResult {
        return try {
            gson.fromJson(jsonStr, VisionFilterResult::class.java)
        } catch (e: Exception) {
            // 降级：尝试从非标准JSON中提取
            AppLog.w("VLM", "JSON解析失败，使用降级策略: ${e.message}")
            // 使用简单的方式提取JSON：找到第一个{和最后一个}
            val startIndex = jsonStr.indexOf('{')
            val endIndex = jsonStr.lastIndexOf('}')
            if (startIndex >= 0 && endIndex > startIndex) {
                try {
                    val extracted = jsonStr.substring(startIndex, endIndex + 1)
                    gson.fromJson(extracted, VisionFilterResult::class.java)
                } catch (e2: Exception) {
                    AppLog.w("VLM", "JSON二次解析失败: ${e2.message}")
                    // 解析失败时返回hasQuestions=false，避免垃圾数据被当作有效答题处理
                    VisionFilterResult(
                        hasQuestions = false,
                        questionCount = 0,
                        searchKeywords = ""
                    )
                }
            } else {
                // 无法提取JSON时返回hasQuestions=false
                VisionFilterResult(
                    hasQuestions = false,
                    questionCount = 0,
                    searchKeywords = ""
                )
            }
        }
    }

    companion object {
        const val READ_TIMEOUT_SEC = 120L
        const val CALL_TIMEOUT_SEC = 130L
        const val WITH_TIMEOUT_MS = 120_000L
        const val CONNECT_TIMEOUT_SEC = 15L
        const val WRITE_TIMEOUT_SEC = 15L
        const val TEST_TIMEOUT_SEC = 30L

        @Volatile
        private var instance: OpenAIVisionProvider? = null

        @Volatile
        private var currentConfig: OpenAIVisionConfig? = null

        private val testClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        /**
         * 获取单例实例，当config变化时自动重建实例
         */
        fun getInstance(config: OpenAIVisionConfig): OpenAIVisionProvider {
            // 检查是否需要重建实例：首次创建或配置变化
            val existing = instance
            if (existing != null && currentConfig == config) {
                return existing
            }
            return synchronized(this) {
                // 双重检查：再次比较配置
                val existingInSync = instance
                if (existingInSync != null && currentConfig == config) {
                    existingInSync
                } else {
                    OpenAIVisionProvider(config).also {
                        instance = it
                        currentConfig = config
                    }
                }
            }
        }

        fun clearInstance() {
            instance = null
            currentConfig = null
        }

        /**
         * 测试视觉模型API并发性能，返回响应时间（毫秒）
         * 使用当前AppConfig中的配置进行测试
         */
        suspend fun testConcurrency(): Result<Long> {
            val config = OpenAIVisionConfig.fromAppConfig()
            return testConcurrency(config)
        }

        /**
         * 测试视觉模型API并发性能，返回响应时间（毫秒）
         */
        suspend fun testConcurrency(config: OpenAIVisionConfig): Result<Long> {
            AppLog.d("VLM", "开始测试并发性能, baseUrl: ${config.baseUrl}, model: ${config.modelName}")
            return withContext(Dispatchers.IO) {
                try {
                    if (config.apiKey.isBlank()) {
                        AppLog.e("VLM", "API Key 未配置")
                        return@withContext Result.failure(Exception("视觉模型 API Key 未配置"))
                    }

                    val startTime = System.currentTimeMillis()

                    // 创建一个简单的测试图片
                    val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(testBitmap)
                    canvas.drawColor(Color.WHITE)

                    val baos = java.io.ByteArrayOutputStream()
                    testBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    testBitmap.recycle()
                    val base64 = android.util.Base64.encodeToString(
                        baos.toByteArray(),
                        android.util.Base64.NO_WRAP
                    )

                    // 构建请求
                    val imageContent = ContentPart(
                        type = "image_url",
                        imageUrl = ImageUrlObj(url = "data:image/jpeg;base64,$base64")
                    )
                    val textContent = ContentPart(type = "text", text = "test")
                    val message = OpenAIMessage(
                        role = "user",
                        content = listOf(imageContent, textContent)
                    )
                    val request = OpenAIVisionRequest(
                        model = config.modelName,
                        messages = listOf(message),
                        temperature = config.temperature,
                        maxTokens = 64
                    )
                    val requestBody = JsonUtil.gson.toJson(request)
                        .toRequestBody("application/json; charset=utf-8".toMediaType())

                    val requestBuilder = okhttp3.Request.Builder()
                        .url(config.baseUrl)
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)

                    // 添加额外headers
                    config.extraHeaders.forEach { (key, value) ->
                        requestBuilder.addHeader(key, value)
                    }

                    AppLog.d("VLM", "发送测试请求")
                    val response = testClient.newCall(requestBuilder.build()).execute()
                    response.use { resp ->
                        val elapsed = System.currentTimeMillis() - startTime

                        if (resp.isSuccessful) {
                            AppLog.d("VLM", "测试成功，耗时: ${elapsed}ms")
                            Result.success(elapsed)
                        } else {
                            AppLog.e("VLM", "测试失败: HTTP ${resp.code}")
                            Result.failure(Exception("HTTP ${resp.code}"))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("VLM", "测试异常: ${e.message}")
                    Result.failure(e)
                }
            }
        }
    }
}

// ==================== 配置类 ====================

data class OpenAIVisionConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val temperature: Double = 0.0,
    val maxTokens: Int = 4096,  // 多题模式需要更多token
    val useJsonMode: Boolean = true,
    val maxImageWidth: Int = 1024,
    val imageQuality: Int = 75,
    val extraHeaders: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * 从AppConfig创建配置实例
         */
        fun fromAppConfig(): OpenAIVisionConfig {
            return OpenAIVisionConfig(
                baseUrl = com.hwb.aianswerer.config.AppConfig.getVisionBaseUrl(),
                apiKey = com.hwb.aianswerer.config.AppConfig.getVisionApiKey(),
                modelName = com.hwb.aianswerer.config.AppConfig.getVisionModelName(),
                temperature = com.hwb.aianswerer.config.AppConfig.getVisionTemperature(),
                maxTokens = com.hwb.aianswerer.config.AppConfig.getVisionMaxTokens(),
                useJsonMode = com.hwb.aianswerer.config.AppConfig.getVisionJsonMode()
            )
        }
    }
}

// ==================== OpenAI 格式序列化模型 ====================

data class OpenAIVisionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<OpenAIMessage>,
    @SerializedName("temperature") val temperature: Double = 0.0,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
)

data class OpenAIMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any  // String 或 List<ContentPart>
)

data class ContentPart(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrlObj? = null
)

data class ImageUrlObj(
    @SerializedName("url") val url: String
)

data class ResponseFormat(
    @SerializedName("type") val type: String
)

data class OpenAIVisionResponse(
    @SerializedName("choices") val choices: List<Choice>
) {
    data class Choice(
        @SerializedName("message") val message: ResponseMessage
    )
    data class ResponseMessage(
        @SerializedName("content") val contentRaw: Any?  // String or Any
    )
}
