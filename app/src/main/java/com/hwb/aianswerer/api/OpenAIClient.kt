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
import com.hwb.aianswerer.models.ResponseFormat
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

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .callTimeout(190, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
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
                    content = MyApplication.getString(
                        R.string.system_prompt_user_message,
                        recognizedText
                    )
                )
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = AppConfig.getLlmTemperature(),
                maxTokens = 1024,
                reasoningEffort = AppConfig.getReasoningEffort(),
                stream = true
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 流式请求，120s Kotlin 层超时兜底（LLM 长响应）
            val answerContent = withTimeout(180_000L) {
                client.newCall(request).awaitStreamContent()
            }

            AppLog.d("API", "AI原始响应长度: ${answerContent.length}")

            // 解析AI返回的JSON答案
            // 策略：先直接解析原文（AI通常返回干净JSON），失败再提取+修复
            val result = Result.success(parseJsonAnswers(answerContent))

            AppLog.leave("API", "analyzeQuestion", _start)
            result

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_timeout)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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
                    content = "以下是OCR识别的题目文本，请判断其中包含多少道独立的题目。只回复一个阿拉伯数字，不要任何解释。\n\n$ocrText"
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
     * 解析 AI 返回的 JSON 答案
     * 策略：直接解析原文 → 提取+修复 → 正则提取 → 文本降级
     */
    private fun parseJsonAnswers(content: String): List<AIAnswer> {
        val trimmed = content.trim()

        // 策略1：直接解析原文（AI 通常返回干净 JSON）
        tryParseAsAnswers(trimmed)?.let {
            AppLog.d("API", "JSON解析: 直接解析成功, size=${it.size}")
            return it
        }

        // 策略2：提取 JSON 负载 + 修复
        val extracted = extractJsonPayload(trimmed)
        val fixed = fixMalformedJson(extracted)
        tryParseAsAnswers(fixed)?.let {
            AppLog.d("API", "JSON解析: 提取+修复成功, size=${it.size}")
            return it
        }

        // 策略3：正则提取 JSON 数组
        val arrayRegex = Regex("""\[[\s\S]*]""")
        arrayRegex.find(trimmed)?.let { match ->
            val candidate = fixMalformedJson(match.value)
            tryParseAsAnswers(candidate)?.let {
                AppLog.d("API", "JSON解析: 正则提取成功, size=${it.size}")
                return it
            }
        }

        // 策略4：正则提取单个 JSON 对象（非贪婪匹配）
        val objRegex = Regex("""\{[^\{\}]*\}""")
        objRegex.find(trimmed)?.let { match ->
            val candidate = fixMalformedJson(match.value)
            try {
                val single = gson.fromJson(candidate, AIAnswer::class.java)
                if (single.question.isNotBlank()) {
                    AppLog.d("API", "JSON解析: 单对象正则提取成功")
                    return listOf(single)
                }
            } catch (_: Exception) {}
        }

        // 策略5：文本降级提取
        AppLog.d("API", "JSON解析: 全部失败, 降级文本提取")
        return listOf(parseAnswerFromText(content))
    }

    /**
     * 尝试将字符串解析为 AIAnswer 列表
     * 支持数组和单对象格式
     */
    private fun tryParseAsAnswers(json: String): List<AIAnswer>? {
        return try {
            // 先尝试数组
            val arrayType = com.google.gson.reflect.TypeToken.getParameterized(
                java.util.List::class.java, AIAnswer::class.java
            ).type
            val list: List<AIAnswer> = gson.fromJson(json, arrayType)
            list.takeIf { it.isNotEmpty() && it.first().question.isNotBlank() }
        } catch (_: Exception) {
            try {
                // 再尝试单对象
                val single = gson.fromJson(json, AIAnswer::class.java)
                listOf(single).takeIf { single.question.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 从 AI 回复文本中提取 JSON 负载。
     *
     * LLM 返回格式多样（裸 JSON、Markdown 代码块、JSON 混在文字中等）。
     * 按优先级处理：
     *   1. 正则匹配 ```json {...} ``` 或 ``` {...} ``` 代码块
     *   2. 括号配对算法：从第一个 { 或 [ 开始，逐字符跟踪字符串内转义
     *      和嵌套深度，找到配对的闭合括号
     *   3. 兜底：原文直接返回，由 Gson 尝试解析
     */
    fun extractJsonPayload(content: String): String {
        val s = content.trim()

        // 优先匹配 Markdown 代码块 ```json {...}```
        val fenceRegex = Regex("(?s)```\\s*([a-zA-Z0-9_-]+)?\\s*(\\{.*?\\}|\\[.*?\\])\\s*```")
        fenceRegex.find(s)?.let { m ->
            return sanitizeJson(m.groupValues[2].trim())
        }

        // 无代码块时：找到首个 '{' 或 '['，通过括号配对提取完整JSON
        val start = sequenceOf(s.indexOf('{'), s.indexOf('['))
            .filter { it >= 0 }
            .minOrNull() ?: return s

        val openChar = s[start]
        val closeChar = if (openChar == '{') '}' else ']'

        // 括号配对状态机：处理嵌套和字符串内的括号
        var depth = 0
        var inString = false
        var escape = false
        var end = -1

        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else {
                    if (c == '\\') escape = true
                    else if (c == '"') inString = false
                }
            } else {
                if (c == '"') inString = true
                else if (c == openChar) depth++
                else if (c == closeChar) {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end != -1) {
            return sanitizeJson(s.substring(start, end + 1).trim())
        }

        // 兜底：返回原文（可能已是纯JSON）
        return sanitizeJson(s)
    }

    /**
     * 清理 JSON 字符串中的非法字符
     * LLM 返回的 JSON 可能在字符串值中包含换行符、中文标点等，导致解析失败
     *
     * 重要：中文标点只在 JSON 字符串外部替换，避免破坏字符串值内的中文内容
     */
    private fun sanitizeJson(json: String): String {
        val result = StringBuilder()
        var inString = false
        var escape = false

        for (c in json) {
            when {
                escape -> {
                    result.append(c)
                    escape = false
                }
                c == '\\' && inString -> {
                    result.append(c)
                    escape = true
                }
                c == '"' -> {
                    result.append(c)
                    inString = !inString
                }
                inString && (c == '\n' || c == '\r') -> {
                    // 字符串内的换行符替换为 \\n
                    result.append("\\n")
                }
                inString && c == '\t' -> {
                    // 字符串内的制表符替换为 \\t
                    result.append("\\t")
                }
                inString -> {
                    // 字符串内的内容原样保留，不替换中文标点
                    result.append(c)
                }
                else -> {
                    // 字符串外的中文标点替换为 ASCII
                    when (c) {
                        '\u201C', '\u201D' -> result.append('"')  // 中文双引号
                        '\u2018', '\u2019' -> result.append('\'') // 中文单引号
                        '\uFF0C' -> result.append(',')             // 全角逗号
                        '\u3001' -> result.append(',')             // 顿号
                        '\uFF1A' -> result.append(':')             // 全角冒号
                        '\uFF1B' -> result.append(';')             // 全角分号
                        '\u3002' -> result.append('.')             // 句号
                        '\uFF08' -> result.append('(')             // 全角左括号
                        '\uFF09' -> result.append(')')             // 全角右括号
                        '\u300A' -> result.append('<')             // 左书名号
                        '\u300B' -> result.append('>')             // 右书名号
                        '\uFF01' -> result.append('!')             // 全角感叹号
                        else -> result.append(c)
                    }
                }
            }
        }
        return result.toString()
    }

    /**
     * 修复常见的 JSON 格式问题
     * - 缺少花括号
     * - 空值 (answer:)
     * - 尾部逗号
     * - 中文标点（仅替换字符串外的标点）
     */
    private fun fixMalformedJson(json: String): String {
        var s = json.trim()

        // 如果不是以 { 或 [ 开头，添加 {
        if (!s.startsWith("{") && !s.startsWith("[")) {
            s = "{$s"
        }
        // 如果不是以 } 或 ] 结尾，添加 }
        if (!s.endsWith("}") && !s.endsWith("]")) {
            // 移除尾部逗号后添加 }
            s = s.trimEnd().trimEnd(',') + "}"
        }

        // 仅替换字符串值外面的中文标点（不替换字符串内的内容）
        val result = StringBuilder()
        var inString = false
        var escape = false
        var i = 0

        while (i < s.length) {
            val c = s[i]

            when {
                escape -> {
                    result.append(c)
                    escape = false
                }
                c == '\\' && inString -> {
                    result.append(c)
                    escape = true
                }
                c == '"' -> {
                    result.append(c)
                    inString = !inString
                }
                inString -> {
                    // 字符串内的内容原样保留，不替换中文标点
                    result.append(c)
                }
                else -> {
                    // 字符串外的中文标点替换为 ASCII
                    when (c) {
                        '\u201C', '\u201D' -> result.append('"')  // 中文双引号
                        '\uFF0C' -> result.append(',')             // 全角逗号
                        '\u3001' -> result.append(',')             // 顿号
                        '\uFF1A' -> result.append(':')             // 全角冒号
                        '\uFF08' -> result.append('(')             // 全角左括号
                        '\uFF09' -> result.append(')')             // 全角右括号
                        else -> result.append(c)
                    }
                }
            }
            i++
        }
        s = result.toString()

        // 修复空值 answer: 或 answer: , 或 answer:"",
        s = s.replace("\"answer\":,", "\"answer\":\"\"," )
        s = s.replace("\"answer\": ,", "\"answer\":\"\"," )
        s = s.replace("\"answer\":  ,", "\"answer\":\"\"," )

        // 移除尾部逗号 (如 "options":[...] ,)
        var changed = true
        while (changed) {
            val newS = s.replace(",}", "}").replace(",]", "]")
            changed = newS != s
            s = newS
        }

        return s
    }

    /**
     * 从原始文本中解析答案（JSON 解析失败时的降级方案）
     * 不使用正则，改用字符串查找
     */
    private fun parseAnswerFromText(text: String): AIAnswer {
        val question = extractJsonValue(text, "question")
            ?: MyApplication.getString(R.string.error_parse_question_failed)
        val rawAnswer = extractJsonValue(text, "answer")
        val answer = rawAnswer?.takeIf { it.isNotBlank() }
            ?: inferAnswerFromOptions(text)
            ?: MyApplication.getString(R.string.error_parse_question_failed)
        val questionType = extractJsonValue(text, "questionType")
            ?: MyApplication.getString(R.string.question_type_essay)

        // 提取 options 数组
        val options = extractJsonArray(text, "options")

        return AIAnswer(
            question = question,
            questionType = questionType,
            answer = answer,
            options = options
        )
    }

    /**
     * 当 answer 为空时，尝试从选项中推断答案
     * 如果只有一个选项看起来像答案，就返回它
     */
    private fun inferAnswerFromOptions(text: String): String? {
        // 尝试找到 "answer": 后面的内容
        val answerIndex = text.indexOf("\"answer\"")
        if (answerIndex == -1) return null

        val colonIndex = text.indexOf(':', answerIndex)
        if (colonIndex == -1) return null

        // 检查 answer 后面是否直接是逗号或结束
        var i = colonIndex + 1
        while (i < text.length && text[i].isWhitespace()) i++

        // 如果是引号，提取引号内容
        if (i < text.length && text[i] == '"') {
            i++
            val start = i
            while (i < text.length && text[i] != '"') i++
            val value = text.substring(start, i)
            if (value.isNotBlank()) return value
        }

        return null
    }

    /**
     * 从 JSON 文本中提取指定 key 的字符串值
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val searchKey = "\"$key\""
        val keyIndex = json.indexOf(searchKey)
        if (keyIndex == -1) return null

        // 找到 key 后面的冒号
        val colonIndex = json.indexOf(':', keyIndex + searchKey.length)
        if (colonIndex == -1) return null

        // 跳过空白
        var i = colonIndex + 1
        while (i < json.length && json[i].isWhitespace()) i++

        if (i >= json.length) return null

        // 如果是引号开头，提取引号内的内容
        if (json[i] == '"') {
            i++ // 跳过开始引号
            val start = i
            while (i < json.length && json[i] != '"') {
                if (json[i] == '\\') i++ // 跳过转义字符
                i++
            }
            return json.substring(start, i)
        }

        // 如果不是引号，提取到逗号或结尾
        val start = i
        while (i < json.length && json[i] != ',' && json[i] != '}' && json[i] != ']') {
            i++
        }
        return json.substring(start, i).trim()
    }

    /**
     * 从 JSON 文本中提取指定 key 的数组值
     */
    private fun extractJsonArray(json: String, key: String): List<String>? {
        val searchKey = "\"$key\""
        val keyIndex = json.indexOf(searchKey)
        if (keyIndex == -1) return null

        // 找到 key 后面的 [
        val bracketIndex = json.indexOf('[', keyIndex + searchKey.length)
        if (bracketIndex == -1) return null

        // 找到匹配的 ]（括号配对，支持嵌套）
        var depth = 0
        var endIndex = -1
        var inString = false
        var escape = false
        for (i in bracketIndex until json.length) {
            val c = json[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '[') depth++
            else if (c == ']') { depth--; if (depth == 0) { endIndex = i; break } }
        }
        if (endIndex == -1) return null

        // 提取数组内容
        val arrayContent = json.substring(bracketIndex + 1, endIndex)

        // 解析数组元素（用引号分割）
        val items = mutableListOf<String>()
        var i = 0
        while (i < arrayContent.length) {
            // 跳过空白和逗号
            while (i < arrayContent.length && (arrayContent[i].isWhitespace() || arrayContent[i] == ',')) i++

            if (i >= arrayContent.length) break

            // 如果是引号开头，提取引号内的内容
            if (arrayContent[i] == '"') {
                i++ // 跳过开始引号
                val start = i
                while (i < arrayContent.length && arrayContent[i] != '"') {
                    if (arrayContent[i] == '\\') i++ // 跳过转义字符
                    i++
                }
                items.add(arrayContent.substring(start, i))
                i++ // 跳过结束引号
            } else {
                // 非引号值
                val start = i
                while (i < arrayContent.length && arrayContent[i] != ',' && arrayContent[i] != ']') i++
                val value = arrayContent.substring(start, i).trim()
                if (value.isNotEmpty()) {
                    items.add(value)
                }
            }
        }

        return items.takeIf { it.isNotEmpty() }
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
            val response = withTimeout(30_000L) {
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

            val response = withTimeout(30_000L) {
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
        val content: String?
    )
}

/**
 * 流式读取 SSE 响应，累积所有 delta.content 后返回完整文本。
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
                        val contentBuilder = StringBuilder()
                        reader.useLines { lines ->
                            for (line in lines) {
                                if (!line.startsWith("data: ")) continue
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break
                                try {
                                    val chunk = JsonUtil.gson.fromJson(data, ChatStreamChunk::class.java)
                                    chunk.choices?.firstOrNull()?.delta?.content?.let {
                                        contentBuilder.append(it)
                                    }
                                } catch (_: Exception) {
                                    // 跳过无法解析的 chunk（如注释行或空白 data）
                                }
                            }
                        }
                        val content = contentBuilder.toString()
                        if (content.isBlank()) {
                            cont.resumeWithException(IOException("stream returned empty content"))
                        } else {
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

