package com.hwb.aianswerer.api

import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.api.search.WebSearchToolExecutor
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.ChatMessage
import com.hwb.aianswerer.models.ChatRequest
import com.hwb.aianswerer.models.ChatResponse
import com.hwb.aianswerer.models.ResponseFormat
import com.hwb.aianswerer.models.ToolCall
import com.hwb.aianswerer.models.ToolCallFunction
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
     * 长读超时客户端 — 专供 dedupeText（长文合并去重）：DeepSeek 等服务商对长输出
     * 采用 200 响应头先返、body 流式生成的方式，共享 client 的 readTimeout(60s) 会在
     * body 读取阶段超时（实测 SocketTimeoutException）。readTimeout 与 DEDUPE_TIMEOUT_MS 对齐。
     */
    private val longReadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .callTimeout(DEDUPE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(DEDUPE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .apply {
                if (com.hwb.aianswerer.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
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
            val baseUserMessage = com.hwb.aianswerer.Constants.getPromptResources().getString(
                R.string.system_prompt_user_message,
                recognizedText
            )
            var messages = mutableListOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = baseUserMessage)
            )

            // function calling 工具模式：携带 web_search 工具定义，由模型自主决定是否搜索
            var tools = if (WebSearchToolExecutor.isToolModeActive()) {
                val spec = Constants.buildWebSearchToolSpec()
                AppLog.d("TOOL", "analyzeQuestion: tool mode active, attaching tool '${spec.function.name}' with messages=${messages.size}")
                listOf(spec)
            } else {
                AppLog.d("TOOL", "analyzeQuestion: tool mode inactive, answering without web search")
                null
            }


            // 多轮工具循环：模型请求工具 → 执行搜索 → role:tool 回填 → 重发，直到模型直接给出答案
            var answerContent = ""
            var rounds = 0
            // M-TOOL: 搜索无有效信息时强制直接作答；已收集的搜索上下文用于重建干净对话
            val searchContextBuilder = StringBuilder()
            var forceDirectAnswer = false
            // M-TOOL: 重建"直接作答"对话 — 丢弃全部工具历史，避免模型惯性输出 <tool_calls> 伪文本
            fun buildDirectAnswerMessages(): MutableList<ChatMessage> {
                val forcePrompt = systemPrompt + "\n\n" +
                    MyApplication.getString(R.string.system_prompt_force_direct_answer)
                val userContent = baseUserMessage + if (searchContextBuilder.isNotEmpty()) {
                    "\n\n【已获取的联网搜索结果（仅供参考，可能不足以作答）】\n" + searchContextBuilder
                } else ""
                return mutableListOf(
                    ChatMessage(role = "system", content = forcePrompt),
                    ChatMessage(role = "user", content = userContent)
                )
            }
            while (true) {
                rounds++
                // M-TOOL: 搜索无有效信息 → 去掉工具并用干净对话强制直接作答（从源头掐断死循环）
                if (forceDirectAnswer && tools != null) {
                    AppLog.w("API", "search yielded no useful info, rebuilding clean conversation without tools")
                    messages = buildDirectAnswerMessages()
                    tools = null
                }
                AppLog.d("TOOL", "round $rounds: sending chat request (messages=${messages.size}, tools=${tools != null})")
                val chatRequest = ChatRequest(
                    model = modelName,
                    messages = messages,
                    temperature = AppConfig.getLlmTemperature(),
                    // 多图模式合并后材料可达数万字，4096 会截断答案 JSON（多题/长材料时）；提高到 16384
                    maxTokens = 16384,
                    reasoningEffort = AppConfig.getReasoningEffort(),
                    stream = true,
                    tools = tools,
                    // 显式 tool_choice:auto，提示部分保守模型主动调用工具（null 时该字段不序列化）
                    toolChoice = if (tools != null) "auto" else null,
                    // P1: 无工具模式强制 JSON 输出（从源头约束，减少解析漂移）；工具模式不传（与 tools 兼容性未验证）
                    responseFormat = if (tools == null) ResponseFormat(type = "json_object") else null
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

                // 流式请求，60s Kotlin 层超时兜底（每轮独立超时）
                val streamResult = withTimeout(WITH_TIMEOUT_MS) {
                    client.newCall(request).awaitStreamContent()
                }

                // 模型请求调用工具：回传 assistant 消息 + 执行搜索 + tool 消息回填，进入下一轮
                if (streamResult.toolCalls.isNotEmpty() && rounds <= MAX_TOOL_ROUNDS && tools != null) {
                    AppLog.i("API", "tool_calls received (round=$rounds): ${streamResult.toolCalls.size} calls")
                    streamResult.toolCalls.forEach {
                        AppLog.d("TOOL", "round $rounds: model requested tool call id=${it.id}, name=${it.name}, argsLen=${it.arguments.length}")
                    }
                    messages.add(
                        ChatMessage(
                            role = "assistant",
                            content = streamResult.content.ifBlank { null },
                            toolCalls = streamResult.toolCalls.map {
                                ToolCall(
                                    id = it.id,
                                    function = ToolCallFunction(name = it.name, arguments = it.arguments)
                                )
                            }
                        )
                    )
                    for (tc in streamResult.toolCalls) {
                        // LLM 输出不可信：query 做长度/控制字符防护后进入搜索请求
                        val parsed = parseToolQuery(tc.arguments)
                        val query = sanitizeToolQuery(parsed ?: recognizedText)
                        AppLog.d("TOOL", "round $rounds: executing ${tc.name}: argsParsed=${parsed != null}, fallbackToQuestion=${parsed == null}, queryLen=${query.length}")
                        val searchResult = WebSearchToolExecutor.execute(query, 2)
                        AppLog.d("TOOL", "round $rounds: tool result: chars=${searchResult.length}, blank=${searchResult.isBlank()}")
                        // M-TOOL: 搜索无有效信息时标记强制直接作答；有效结果收集进上下文供重建对话使用
                        if (searchResult.isBlank()) {
                            forceDirectAnswer = true
                        } else {
                            searchContextBuilder.append(searchResult).append('\n')
                        }
                        messages.add(
                            ChatMessage(
                                role = "tool",
                                content = searchResult.ifBlank { "（无搜索结果）" },
                                toolCallId = tc.id
                            )
                        )
                    }
                    continue
                }

                // 工具循环封顶：模型仍在请求工具但已超出轮数上限
                if (streamResult.toolCalls.isNotEmpty() && rounds > MAX_TOOL_ROUNDS) {
                    AppLog.w("API", "tool loop capped after $MAX_TOOL_ROUNDS rounds, forcing answer from remaining content (len=${streamResult.content.length})")
                    // M-TOOL: 封顶且 content 为空（推理模型仍在输出 reasoning、未给出答案）时，
                    //     重建干净对话（丢弃工具历史）再发最后一轮，避免模型惯性输出 <tool_calls> 伪文本
                    if (streamResult.content.isBlank() && tools != null) {
                        AppLog.w("API", "tool loop capped with empty content, rebuilding clean conversation without tools")
                        messages = buildDirectAnswerMessages()
                        tools = null
                        continue
                    }
                }

                answerContent = streamResult.content
                break
            }
            AppLog.d("TOOL", "tool loop finished: rounds=$rounds, answerLen=${answerContent.length}")

            // M-TOOL: 伪工具文本防护 — 模型在无 tools 请求中仍可能输出 <tool_calls> 伪文本，
            //         先剥离工具调用标记再交给解析器，避免伪文本被降级提取成垃圾答案
            val sanitizedAnswer = sanitizeToolCallText(answerContent)
            AppLog.d("API", "AI原始响应长度: ${sanitizedAnswer.length}")
            // 完整响应含题目与答案内容，降级为 debug 级别（受调试日志开关控制），避免无条件写入日志文件
            AppLog.d("API", "AI原始完整响应: $sanitizedAnswer")
            // 解析AI返回的JSON答案
            // 策略：先直接解析原文（AI通常返回干净JSON），失败再提取+修复
            val result: Result<List<AIAnswer>> = if (containsToolCallMarkers(answerContent) && sanitizedAnswer.isBlank()) {
                // 工具伪文本剥离后无任何可用内容 → 按空答案处理，避免被降级文本提取成垃圾答案条目；
                // 若剥离后仍有文本（如块后的 Markdown 答案），走正常解析（解析器自带空内容/伪文本防护）
                AppLog.w("API", "sanitized tool-call pseudo text is blank, treating as empty answer")
                Result.success(emptyList())
            } else {
                val parsed = answerExtractor.parseJsonAnswers(sanitizedAnswer).toMutableList()
                // 降级文本提取的条目可能缺题目（question 为占位文案）：用原始题目文本补全，
                // 避免结果卡显示"无法解析题目"
                if (parsed.size == 1) {
                    val ans = parsed[0]
                    if (ans.question == MyApplication.getString(R.string.error_parse_question_failed) &&
                        ans.answer.isNotBlank()
                    ) {
                        parsed[0] = ans.copy(question = recognizedText)
                    }
                }
                Result.success(parsed)
            }

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
     * 专职去重 LLM — 将多页识别文本（OCR/VLM 分页截图识别结果）合并去重，
     * 去除相邻页重叠内容与识别噪声，输出完整干净的题目材料文本。
     * 非流式轻量调用（与 countQuestions 同款模式），失败时返回 Result.failure 由调用方降级。
     */
    suspend fun dedupeText(rawText: String): Result<String> = withContext(Dispatchers.IO) {
        val _start = System.currentTimeMillis()
        AppLog.enter("API", "dedupeText textLen=${rawText.length}")
        try {
            val apiUrl = AppConfig.getApiUrl()
            val apiKey = AppConfig.getApiKey()
            val modelName = AppConfig.getModelName()
            if (!AppConfig.isApiConfigValid()) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_invalid))
                )
            }

            val systemPrompt = com.hwb.aianswerer.Constants.getPromptResources().getString(
                R.string.system_prompt_dedupe
            )
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = rawText)
            )
            val request = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.0,
                // 多图模式合并文本可达数万字（10 段 × 长文材料），8192 token 会截断整理结果，
                // 提高到 16384 覆盖绝大多数长文场景；若服务商不支持该上限会失败，由调用方降级原始拼接
                maxTokens = 16384
            )
            val body = gson.toJson(request)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val httpRequest = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            // 长文去重输出（16384 tokens）在 60s 内可能生成不完，放宽到 240s
            // 必须用 longReadClient：共享 client 的 readTimeout=60s 会在 body 流式读取阶段超时（实测 SocketTimeoutException）
            val response = withTimeout(DEDUPE_TIMEOUT_MS) {
                longReadClient.newCall(httpRequest).execute()
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    return@withContext Result.failure(
                        Exception("HTTP ${resp.code}: $errorBody")
                    )
                }
                val responseBody = resp.body?.string()
                    ?: return@withContext Result.failure(
                        Exception(MyApplication.getString(R.string.error_api_empty_response))
                    )
                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
                if (content.isNullOrBlank()) {
                    return@withContext Result.failure(
                        Exception(MyApplication.getString(R.string.error_api_empty_stream))
                    )
                }
                AppLog.i("API", "dedupeText ok: in=${rawText.length} out=${content.length}")
                Result.success(content)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AppLog.e("API", "dedupeText timeout after ${DEDUPE_TIMEOUT_MS}ms")
            Result.failure(java.io.IOException(MyApplication.getString(R.string.error_llm_timeout)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("API", "dedupeText failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            AppLog.leave("API", "dedupeText", _start)
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
        modelName: String = AppConfig.getModelName(),
        concurrency: Int = 1
    ): Result<Long> = withContext(Dispatchers.IO) {
        val _start = System.currentTimeMillis()
        AppLog.enter("API", "testConcurrency concurrency=$concurrency")
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
            // 并发测试：同时发出 N 个请求，验证服务商真实并发能力（限流/超时会在这里暴露）
            val n = concurrency.coerceIn(1, 20)
            val results = (1..n).map {
                async {
                    try {
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
                        withTimeout(TEST_TIMEOUT_MS) {
                            client.newCall(request).awaitCancellable().use { it.code }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        -1
                    }
                }
            }.awaitAll()

            val ok = results.count { it == 200 }
            val elapsed = System.currentTimeMillis() - startTime
            AppLog.i("API", "testConcurrency: $ok/$n ok, elapsed=${elapsed}ms")
            AppLog.leave("API", "testConcurrency", _start)
            return@withContext if (ok == n) {
                Result.success(elapsed)
            } else {
                Result.failure(
                    Exception("并发测试 $ok/$n 成功（配置并发数 $n），请降低最大并发数或检查 API 限流")
                )
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
        /** 去重 LLM（长文合并）外层超时 — 输出上限 16384 tokens，长文材料生成慢，放宽至 240s */
        const val DEDUPE_TIMEOUT_MS = 240_000L
        const val CONNECT_TIMEOUT_SEC = 15L
        const val WRITE_TIMEOUT_SEC = 15L
        const val TEST_TIMEOUT_MS = 30_000L

        // function calling 工具循环：最多执行 2 轮工具调用（加首轮共最多 3 次请求；封顶重建后最多 4 次），防止死循环
        const val MAX_TOOL_ROUNDS = 2

        /**
         * 防伪工具文本：模型在无 tools 参数请求中仍可能输出 <tool_calls> 格式伪文本（受历史工具消息影响）。
         * 剥离工具调用标记块（XML/JSON 两种风格），保留其余文本；无可用内容则返回空串（交由解析层按失败处理）。
         */
        fun sanitizeToolCallText(content: String): String {
            val trimmed = content.trim()
            if (!containsToolCallMarkers(trimmed)) return content
            var cleaned = trimmed
            // XML 风格：<tool_calls>…</tool_calls> / <invoke …>…</invoke> / <parameter …>…</parameter>
            TOOL_CALL_BLOCK_REGEXES.forEach { cleaned = cleaned.replace(it, "") }
            // JSON 风格："tool_calls":[…] / "tool_call_id":"…"
            cleaned = cleaned
                .replace(Regex(""""tool_calls"\s*:\s*\[[^\]]*]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex(""""tool_call_id"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE), "")
            val result = cleaned.trim()
            // 剥离后为空（纯伪文本）→ 返回空串由上层按失败处理
            return result
        }

        /** 工具调用伪文本块（XML 风格，大小写不敏感，跨行匹配） */
        private val TOOL_CALL_BLOCK_REGEXES = listOf(
            Regex("""<tool_calls>\s*</tool_calls>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("""<tool_calls>.*?</tool_calls>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("""<tool_call>.*?</tool_call>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("""<invoke[^>]*>.*?</invoke>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("""<parameter[^>]*>.*?</parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        )

        /**
         * 判断内容是否含工具调用标记（伪 <tool_calls> 或 JSON 风格 \"tool_calls\" 字段）。
         */
        fun containsToolCallMarkers(content: String): Boolean {
            val trimmed = content.trim()
            return trimmed.contains("<tool_calls", ignoreCase = true) || trimmed.contains("</tool_calls>", ignoreCase = true) ||
                trimmed.contains("<invoke", ignoreCase = true) || trimmed.contains("</invoke>", ignoreCase = true) ||
                trimmed.contains("<tool_call>", ignoreCase = true) || trimmed.contains("<parameter", ignoreCase = true) ||
                trimmed.contains("tool_call_id", ignoreCase = true) || trimmed.contains("\"tool_calls\"", ignoreCase = true)
        }

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
        val reasoning_content: String? = null,
        val tool_calls: List<StreamToolCallDelta>? = null
    )
    data class StreamToolCallDelta(
        val index: Int,
        val id: String? = null,
        val function: StreamToolCallFunctionDelta? = null
    )
    data class StreamToolCallFunctionDelta(
        val name: String? = null,
        val arguments: String? = null
    )
}

/**
 * 流式读取结果：累积的文本内容 + 模型请求的工具调用列表。
 */
private data class StreamResult(
    val content: String,
    val toolCalls: List<ParsedToolCall>
)

/**
 * 已解析的工具调用（跨 chunk 按 index 累积合并后的完整形态）。
 */
private data class ParsedToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * 流式读取 SSE 响应，累积所有 delta.content 与 delta.reasoning_content 后返回完整文本。
 *
 * 协程取消时调用 call.cancel() 中断 HTTP 连接。
 * 与 awaitCancellable 不同，本函数在返回前完成整个 SSE 流的读取，
 * 因此 withTimeout 能正确覆盖从建连到最后一个 token 的全过程。
 */
private suspend fun Call.awaitStreamContent(): StreamResult =
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
                        // 流式 tool_calls 按 index 累积：id/name 首现写入，arguments 增量拼接
                        val toolCallAccumulators = LinkedHashMap<Int, StreamToolCallAccumulator>()
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
                                        delta.tool_calls?.forEach { tc ->
                                            val acc = toolCallAccumulators.getOrPut(tc.index) { StreamToolCallAccumulator() }
                                            if (acc.id == null && !tc.id.isNullOrBlank()) acc.id = tc.id
                                            val name = tc.function?.name
                                            if (acc.name == null && !name.isNullOrBlank()) acc.name = name
                                            val args = tc.function?.arguments
                                            if (!args.isNullOrBlank()) acc.arguments.append(args)
                                        }
                                    }
                                    chunkCount++
                                } catch (_: Exception) {
                                    // 跳过无法解析的 chunk（如注释行或空白 data）
                                }
                            }
                        }
                        // 优先用 answer（content），为空时检查 reasoning_content 是否含 JSON
                        // 注意：存在 tool_calls 时不回退 reasoning_content——OpenAI 协议要求带 tool_calls 的 assistant 消息 content 必须为 null
                        var content = answerBuilder.toString()
                        if (content.isBlank() && reasonBuilder.isNotEmpty() && toolCallAccumulators.isEmpty()) {
                            val reason = reasonBuilder.toString()
                            // 仅当 reasoning_content 包含 JSON 结构时才回退，避免使用纯思考文本
                            if (reason.contains('{') || reason.contains('[')) {
                                AppLog.d("API", "content empty, falling back to reasoning_content (${reason.length} chars, contains JSON)")
                                content = reason
                            } else {
                                AppLog.w("API", "content empty, reasoning_content is non-JSON text (${reason.length} chars) — skipping fallback")
                            }
                        }
                        // 组装完整工具调用（仅保留 id/name/arguments 齐全的）
                        val toolCalls = toolCallAccumulators.values
                            .filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() && it.arguments.isNotEmpty() }
                            .map { ParsedToolCall(id = it.id!!, name = it.name!!, arguments = it.arguments.toString()) }
                        if (content.isBlank() && toolCalls.isEmpty()) {
                            AppLog.w("API", "stream returned empty content (parsed $contentChunks content + $reasonChunks reasoning from $chunkCount total chunks)")
                            cont.resumeWithException(IOException("stream returned empty content"))
                        } else {
                            AppLog.d("API", "stream completed: ${content.length} chars (content=$contentChunks chunks, reasoning=$reasonChunks chunks, tools=${toolCalls.size}, total=$chunkCount)")
                            cont.resume(StreamResult(content = content, toolCalls = toolCalls))
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
 * 流式 tool_calls 增量累积器（按 index 一一对应）。
 */
private class StreamToolCallAccumulator(
    var id: String? = null,
    var name: String? = null,
    val arguments: StringBuilder = StringBuilder()
)

/**
 * 解析工具调用的 arguments（JSON 字符串），提取 query 参数。
 * 解析失败返回 null，调用方降级使用题目原文。
 */
private fun parseToolQuery(arguments: String): String? {
    return try {
        val obj = JsonUtil.gson.fromJson(arguments, JsonObject::class.java)
        obj.get("query")?.takeIf { it.isJsonPrimitive }?.asString
    } catch (_: Exception) {
        null
    }
}

/**
 * 工具查询参数防护：剔除控制字符并限制长度（LLM 输出不可信，防止超长/异常字符注入搜索请求）。
 */
private fun sanitizeToolQuery(query: String): String =
    query.replace(Regex("[\\x00-\\x1f\\x7f]"), "").take(256)

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

