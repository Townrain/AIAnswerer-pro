package com.hwb.aianswerer.api

import com.google.gson.JsonSyntaxException
import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.ChatMessage
import com.hwb.aianswerer.models.ChatRequest
import com.hwb.aianswerer.models.ChatResponse
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ConnectionPool
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI 兼容 API 客户端。
 *
 * 每次 API 调用都从 AppConfig 实时读取 URL/Key/Model，
 * 确保用户在设置中修改后下次调用立即生效，无需重启 Service。
 */
class OpenAIClient {

    private val answerExtractor = JsonAnswerExtractor(gson)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .apply {
                if (com.hwb.aianswerer.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        // 使用HEADERS级别，避免泄露请求体中的敏感信息（如API Key）
                        level = HttpLoggingInterceptor.Level.HEADERS
                        redactHeader("Authorization")
                    })
                }
            }
            .build()
    }

    /**
     * 分析题目并获取答案
     *
     * 动态从AppConfig读取最新的API配置
     *
     * @param recognizedText OCR识别的文本
     * @param questionTypes 题型集合（如：单选题、多选题等）
     * @return AI解析的答案列表，包装在Result中
     */
    suspend fun analyzeQuestion(
        recognizedText: String,
        questionTypes: Set<String> = emptySet(),
        searchContext: String = "",
        systemPrompt: String? = null
    ): Result<List<AIAnswer>> = withContext(Dispatchers.IO) {
        val _start = System.currentTimeMillis()
        AppLog.enter("API", "analyzeQuestion textLen=${recognizedText.length}")
        try {
            // 从配置中读取最新的API设置
            val apiUrl = AppConfig.getApiUrl()
            val apiKey = AppConfig.getApiKey()
            val modelName = AppConfig.getModelName()

            // 验证配置有效性
            if (!AppConfig.isApiConfigValid()) {
                AppLog.e("API", "config invalid")
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_invalid))
                )
            }

            // 构建请求，使用动态系统提示词（可被调用方覆盖）
            val systemPrompt = systemPrompt ?: Constants.buildSystemPrompt(questionTypes, searchContext)
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(
                    role = "user",
                    content = com.hwb.aianswerer.Constants.getPromptResources().getString(
                        R.string.system_prompt_user_message,
                        recognizedText
                    )
                )
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = AppConfig.getLlmTemperature(),
                maxTokens = 4096,
                reasoningEffort = AppConfig.getReasoningEffort(),
                stream = true
            )

            val requestJson = gson.toJson(chatRequest)
                // 移除以 null 值序列化的字段，避免 API 误解（如 reasoning_effort:null 被当作启用推理）
                .replace(Regex(""",\\s*\"[^\"]+\":\\s*null"""), "")
            val requestBody = requestJson
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 流式请求，60s Kotlin 层超时兜底
            val answerContent = withTimeout(WITH_TIMEOUT_MS) {
                client.newCall(request).awaitStreamContent()
            }

            AppLog.d("API", "AI原始响应长度: ${answerContent.length}")
            AppLog.i("API", "AI原始完整响应: $answerContent")
            // 解析AI返回的JSON答案
            // 策略：先直接解析原文（AI通常返回干净JSON），失败再提取+修复
            val result = Result.success(answerExtractor.parseJsonAnswers(answerContent))

            AppLog.leave("API", "analyzeQuestion", _start)
            result

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AppLog.e("API", "analyzeQuestion timeout after ${WITH_TIMEOUT_MS}ms")
            Result.failure(java.io.IOException(MyApplication.getString(R.string.error_llm_timeout)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            val msg = e.message ?: ""
            AppLog.e("API", "analyzeQuestion IOException: $msg")
            val userMsg = when {
                msg.contains("stream returned empty content") ->
                    MyApplication.getString(R.string.error_api_empty_stream)
                msg.startsWith("HTTP") -> {
                    val code = msg.removePrefix("HTTP ").split(" ").firstOrNull()?.toIntOrNull() ?: 0
                    MyApplication.getString(R.string.error_api_http_status, code)
                }
                msg.contains("Unable to resolve host") || msg.contains("UnknownHost") ->
                    MyApplication.getString(R.string.error_api_unknown_host)
                msg.contains("timeout") || msg.contains("timed out") ->
                    MyApplication.getString(R.string.error_api_timeout)
                msg.contains("SSL") || msg.contains("certificate") ->
                    MyApplication.getString(R.string.error_api_ssl)
                else -> MyApplication.getString(R.string.error_llm_unknown, msg)
            }
            Result.failure(java.io.IOException(userMsg))
        } catch (e: Exception) {
            AppLog.e("API", "analyzeQuestion unexpected: ${e.message}", e)
            Result.failure(Exception(MyApplication.getString(R.string.error_llm_unknown, e.message ?: "未知错误")))
        }
    }

    /**
     * 轻量级调用：判断 OCR 文本中包含多少道题目。
     * 返回题目数量，失败时返回 -1（调用方应视为多题，跳过搜索）。
     */
    suspend fun countQuestions(ocrText: String): Int = withContext(Dispatchers.IO) {
        val _start = System.currentTimeMillis()
        AppLog.enter("API", "countQuestions")
        try {
            val apiUrl = AppConfig.getApiUrl()
            val apiKey = AppConfig.getApiKey()
            val modelName = AppConfig.getModelName()
            if (!AppConfig.isApiConfigValid()) return@withContext -1

            val messages = listOf(
                ChatMessage(
                    role = "user",
                    content = com.hwb.aianswerer.Constants.getPromptResources().getString(R.string.system_prompt_count_questions, ocrText)
                )
            )
            val request = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.0,
                maxTokens = 64
            )
            val body = gson.toJson(request)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val httpRequest = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext -1

                val responseBody = resp.body?.string() ?: return@withContext -1
                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                val content = chatResponse.choices.firstOrNull()?.message?.content?.trim() ?: return@withContext -1

                // 提取数字
                val number = Regex("""\d+""").find(content)?.value?.toIntOrNull()
                AppLog.leave("API", "countQuestions", _start)
                number ?: -1
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("API", "判断题目数量失败", e)
            AppLog.leave("API", "countQuestions", _start)
            -1
        }
    }

    /**
     * 测试API连接，支持传入未保存的配置参数
     */
    suspend fun testConnection(
        apiUrl: String,
        apiKey: String,
        modelName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val _start = System.currentTimeMillis()
        android.util.Log.e("AIAnswerer", "[API] ====== testConnection ENTERED ======")
        AppLog.enter("API", "testConnection")
        try {
            // 验证配置有效性
            if (!AppConfig.isApiConfigValid(apiUrl, apiKey, modelName)) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_incomplete))
                )
            }

            // 构建最简单的测试请求
            val messages = listOf(
                ChatMessage(role = "user", content = "hello")
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = AppConfig.getLlmTemperature()
                // 不使用 response_format，兼容更多 API 提供方
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 异步请求 + 30s 超时兜底
            val response = withTimeout(TEST_TIMEOUT_MS) {
                client.newCall(request).awaitCancellable()
            }

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
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    if (chatResponse.choices.isEmpty()) {
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
            AppLog.leave("API", "testConnection", _start)
            Result.success(MyApplication.getString(R.string.toast_connection_success))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AppLog.leave("API", "testConnection", _start)
            Result.failure(Exception(MyApplication.getString(R.string.error_api_timeout)))
        } catch (e: java.net.UnknownHostException) {
            AppLog.leave("API", "testConnection", _start)
            Result.failure(Exception(MyApplication.getString(R.string.error_api_unknown_host)))
        } catch (e: java.net.SocketTimeoutException) {
            AppLog.leave("API", "testConnection", _start)
            Result.failure(Exception(MyApplication.getString(R.string.error_api_timeout)))
        } catch (e: javax.net.ssl.SSLException) {
            AppLog.leave("API", "testConnection", _start)
            Result.failure(Exception(MyApplication.getString(R.string.error_api_ssl)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.leave("API", "testConnection", _start)
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

    /**
     * 测试API并发性能，返回响应时间（毫秒）
     * 用于让用户测试当前API配置下的并发性能
     */
    suspend fun testConcurrency(
        apiUrl: String = AppConfig.getApiUrl(),
        apiKey: String = AppConfig.getApiKey(),
        modelName: String = AppConfig.getModelName()
    ): Result<Long> = withContext(Dispatchers.IO) {
        val _start = System.currentTimeMillis()
        AppLog.enter("API", "testConcurrency")
        try {
            if (!AppConfig.isApiConfigValid(apiUrl, apiKey, modelName)) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_incomplete))
                )
            }

            val startTime = System.currentTimeMillis()

            val messages = listOf(
                ChatMessage(role = "user", content = "hello")
            )
            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.3
            )
            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = withTimeout(TEST_TIMEOUT_MS) {
                client.newCall(request).awaitCancellable()
            }
            response.use { resp ->
                val elapsed = System.currentTimeMillis() - startTime

                if (resp.isSuccessful) {
                    AppLog.leave("API", "testConcurrency", _start)
                    Result.success(elapsed)
                } else {
                    AppLog.leave("API", "testConcurrency", _start)
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.leave("API", "testConcurrency", _start)
            Result.failure(e)
        }
    }

    companion object {
        // 超时常量：readTimeout 与 Kotlin 层 withTimeout 必须对齐，
        // 避免 OkHttp 先于协程超时导致不可取消的 SocketTimeoutException。
        const val READ_TIMEOUT_SEC = 60L
        const val CALL_TIMEOUT_SEC = 65L
        const val WITH_TIMEOUT_MS = 60_000L
        const val CONNECT_TIMEOUT_SEC = 15L
        const val WRITE_TIMEOUT_SEC = 15L
        const val TEST_TIMEOUT_MS = 30_000L

        // 使用全局共享的Gson实例
        private val gson = JsonUtil.gson

        // 双重检查锁定（DCL）单例：volatile保证可见性，synchronized保证原子性
        @Volatile
        private var instance: OpenAIClient? = null

        fun getInstance(): OpenAIClient {
            return instance ?: synchronized(this) {
                instance ?: OpenAIClient().also { instance = it }
            }
        }

        /**
         * 检查网络连接是否可用
         */
        suspend fun isNetworkAvailable(): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val context = MyApplication.getAppContext()
                    val connectivityManager = context.getSystemService(
                        android.content.Context.CONNECTIVITY_SERVICE
                    ) as android.net.ConnectivityManager
                    val network = connectivityManager.activeNetwork ?: return@withContext false
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                        ?: return@withContext false
                    capabilities.hasCapability(
                        android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                } catch (e: Exception) {
                    false
                }
            }
        }

        /**
         * 带指数退避的重试机制
         * @param maxRetries 最大重试次数
         * @param initialDelayMs 初始延迟（毫秒）
         * @param block 要重试的操作
         */
        suspend fun <T> retryWithBackoff(
            maxRetries: Int = 2,
            initialDelayMs: Long = 1000,
            block: suspend () -> T
        ): T {
            var currentDelay = initialDelayMs
            repeat(maxRetries) {
                try {
                    return block()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (it == maxRetries - 1) throw e
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                }
            }
            throw IllegalStateException("Unreachable")
        }
    }
}

/**
 * SSE 流式响应的单个 chunk 数据类。
 * 用于解析 OpenAI 兼容 API 的 stream 模式下返回的 data: 行。
 */
private data class ChatStreamChunk(
    val choices: List<StreamChoice>?
) {
    data class StreamChoice(
        val delta: StreamDelta?,
        val finish_reason: String?
    )
    data class StreamDelta(
        val content: String?,
        val reasoning_content: String? = null
    )
}

/**
 * 流式读取 SSE 响应，累积所有 delta.content 与 delta.reasoning_content 后返回完整文本。
 *
 * 协程取消时调用 call.cancel() 中断 HTTP 连接。
 * 与 awaitCancellable 不同，本函数在返回前完成整个 SSE 流的读取，
 * 因此 withTimeout 能正确覆盖从建连到最后一个 token 的全过程。
 */
private suspend fun Call.awaitStreamContent(): String =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            AppLog.w("API", "stream cancelled by timeout")
            cancel()
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AppLog.e("API", "stream onFailure: ${e.message}", e)
                if (!cont.isCancelled) {
                    cont.resumeWithException(e)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                AppLog.net("API", "stream onResponse code=${response.code}")
                if (cont.isCancelled) {
                    response.close()
                    return
                }
                try {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            cont.resumeWithException(
                                IOException("HTTP ${resp.code} ${resp.message}")
                            )
                            return
                        }
                        val body = resp.body
                            ?: run { cont.resumeWithException(IOException("empty body")); return }
                        val reader = body.charStream().buffered()
                        val answerBuilder = StringBuilder()
                        val reasonBuilder = StringBuilder()
                        var chunkCount = 0
                        var contentChunks = 0
                        var reasonChunks = 0
                        reader.useLines { lines ->
                            for (line in lines) {
                                if (!line.startsWith("data: ")) continue
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break
                                try {
                                    val chunk = JsonUtil.gson.fromJson(data, ChatStreamChunk::class.java)
                                    chunk.choices?.firstOrNull()?.delta?.let { delta ->
                                        delta.content?.let { answerBuilder.append(it); contentChunks++ }
                                        delta.reasoning_content?.let { reasonBuilder.append(it); reasonChunks++ }
                                    }
                                    chunkCount++
                                } catch (_: Exception) {
                                    // 跳过无法解析的 chunk（如注释行或空白 data）
                                }
                            }
                        }
                        // 优先用 answer（content），为空时检查 reasoning_content 是否含 JSON
                        var content = answerBuilder.toString()
                        if (content.isBlank() && reasonBuilder.isNotEmpty()) {
                            val reason = reasonBuilder.toString()
                            // 仅当 reasoning_content 包含 JSON 结构时才回退，避免使用纯思考文本
                            if (reason.contains('{') || reason.contains('[')) {
                                AppLog.d("API", "content empty, falling back to reasoning_content (${reason.length} chars, contains JSON)")
                                content = reason
                            } else {
                                AppLog.w("API", "content empty, reasoning_content is non-JSON text (${reason.length} chars) — skipping fallback")
                            }
                        }
                        if (content.isBlank()) {
                            AppLog.w("API", "stream returned empty content (parsed $contentChunks content + $reasonChunks reasoning from $chunkCount total chunks)")
                            cont.resumeWithException(IOException("stream returned empty content"))
                            AppLog.d("API", "stream completed: ${content.length} chars (content=$contentChunks chunks, reasoning=$reasonChunks chunks, total=$chunkCount)")
                            cont.resume(content)
                        }
                    }
                } catch (e: Exception) {
                    if (!cont.isCancelled) {
                        cont.resumeWithException(e)
                    }
                }
            }
        })
        AppLog.d("API", "stream enqueue sent")
    }

/**
 * 将 OkHttp 异步 Call 转换为可取消的挂起函数（非流式）。
 * 协程取消时调用 call.cancel() 中断 HTTP 连接。
 *
 * 注意：analyzeQuestion() 已改用流式读取，本函数保留用于向后兼容
 * （countQuestions、testConnection、testConcurrency）。
 */
private suspend fun Call.awaitCancellable(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            AppLog.w("API", "call cancelled by timeout")
            cancel()
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AppLog.e("API", "onFailure: ${e.message}", e)
                if (!cont.isCancelled) {
                    cont.resumeWithException(e)
                } else {
                    AppLog.w("API", "onFailure but cont already cancelled, ignoring")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                AppLog.net("API", "onResponse code=${response.code}")
                if (!cont.isCancelled) {
                    cont.resume(response)
                } else {
                    AppLog.w("API", "onResponse but cont already cancelled")
                    response.close()
                }
            }
        })
        AppLog.d("API", "enqueue sent")
    }

