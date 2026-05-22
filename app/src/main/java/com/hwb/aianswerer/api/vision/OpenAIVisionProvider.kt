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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // VLM分析需要更长时间
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun analyze(bitmap: Bitmap): Result<VisionFilterResult> =
        withContext(Dispatchers.IO) {
            try {
                val base64Image = encodeBitmap(bitmap)

                val userContent = listOf(
                    ContentPart(type = "text", text = buildSystemPrompt()),
                    ContentPart(
                        type = "image_url",
                        imageUrl = ImageUrlObj(url = "data:image/jpeg;base64,$base64Image")
                    )
                )

                val requestBody = OpenAIVisionRequest(
                    model = config.modelName,
                    messages = listOf(
                        OpenAIMessage(role = "user", content = userContent)
                    ),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
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

                val response = client.newCall(httpRequest).execute()

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

                    // content 可能是 String 或 Any（取决于 Gson 解析）
                    val jsonStr = when (rawContent) {
                        is String -> rawContent
                        else -> gson.toJson(rawContent)
                    }

                    val parsed = parseResponse(jsonStr)
                    AppLog.d("OpenAIVision: ${parsed.questionCount}题 | ${parsed.searchKeywords}")
                    Result.success(parsed.copy(rawResponse = body))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("OpenAIVision analyze failed", e)
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

    private fun buildSystemPrompt(): String = """
你是一个题目截图分析器。严格返回 JSON：
{
  "has_questions": true或false,
  "question_count": 题目数量(整数),
  "question_types": ["选择题"|"填空题"|"问答题"...],
  "search_keywords": "提炼后的核心搜索关键词",
  "noise_description": "干扰内容描述",
  "is_multi_question": true或false,
  "extracted_text": "提取的所有题目完整文本",
  "questions": [
    {
      "index": 1,
      "text": "第一道题的完整文本",
      "question_type": "选择题|填空题|问答题",
      "search_keywords": "第一道题的核心搜索关键词"
    },
    {
      "index": 2,
      "text": "第二道题的完整文本",
      "question_type": "选择题|填空题|问答题",
      "search_keywords": "第二道题的核心搜索关键词"
    }
  ]
}
规则：
- 忽略状态栏、导航栏、广告、按钮等UI噪声
- 当截图包含多道题时，必须将每道题分离到questions数组中
- 每道题的search_keywords提炼该题的核心知识点，适合搜索引擎查询
- extracted_text 提取所有题目的完整文本
- 单题时questions数组也包含一个元素
- 无题目时 has_questions=false，questions为空数组
仅返回JSON，不要解释。
""".trimIndent()

    private fun parseResponse(jsonStr: String): VisionFilterResult {
        return try {
            gson.fromJson(jsonStr, VisionFilterResult::class.java)
        } catch (e: Exception) {
            // 降级：尝试从非标准JSON中提取
            AppLog.w("Vision JSON解析失败，使用降级策略: ${e.message}")
            // 使用简单的方式提取JSON：找到第一个{和最后一个}
            val startIndex = jsonStr.indexOf('{')
            val endIndex = jsonStr.lastIndexOf('}')
            if (startIndex >= 0 && endIndex > startIndex) {
                try {
                    val extracted = jsonStr.substring(startIndex, endIndex + 1)
                    gson.fromJson(extracted, VisionFilterResult::class.java)
                } catch (e2: Exception) {
                    AppLog.w("Vision JSON二次解析失败: ${e2.message}")
                    VisionFilterResult(
                        hasQuestions = true,
                        questionCount = 1,
                        searchKeywords = jsonStr.take(200)
                    )
                }
            } else {
                VisionFilterResult(
                    hasQuestions = true,
                    questionCount = 1,
                    searchKeywords = jsonStr.take(200)
                )
            }
        }
    }

    companion object {
        @Volatile
        private var instance: OpenAIVisionProvider? = null

        private val testClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        fun getInstance(config: OpenAIVisionConfig): OpenAIVisionProvider {
            return instance ?: synchronized(this) {
                instance ?: OpenAIVisionProvider(config).also { instance = it }
            }
        }

        fun clearInstance() { instance = null }

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
            AppLog.d("开始测试VLM API并发性能, baseUrl: ${config.baseUrl}, model: ${config.modelName}")
            return withContext(Dispatchers.IO) {
                try {
                    if (config.apiKey.isBlank()) {
                        AppLog.e("视觉模型 API Key 未配置")
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

                    AppLog.d("发送VLM测试请求")
                    val response = testClient.newCall(requestBuilder.build()).execute()
                    response.use { resp ->
                        val elapsed = System.currentTimeMillis() - startTime

                        if (resp.isSuccessful) {
                            AppLog.d("VLM测试成功，耗时: ${elapsed}ms")
                            Result.success(elapsed)
                        } else {
                            AppLog.e("VLM测试失败: HTTP ${resp.code}")
                            Result.failure(Exception("HTTP ${resp.code}"))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("VLM测试异常: ${e.message}")
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
