package com.hwb.aianswerer.api

import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Tavily 搜索 API 客户端。
 *
 * 每次调用从 AppConfig 实时读取 API Key，确保用户修改后立即生效。
 * 搜索失败时返回 Result.failure，由调用方决定是否降级。
 */
class TavilyClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val gson = JsonUtil.gson
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 执行搜索
     */
    suspend fun search(request: TavilySearchRequest): Result<TavilySearchResponse> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = AppConfig.getTavilyApiKey()
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(
                        Exception("Tavily API Key 未配置")
                    )
                }

                val requestBody = gson.toJson(request)
                    .toRequestBody(jsonMediaType)

                val httpRequest = Request.Builder()
                    .url(BASE_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val response = client.newCall(httpRequest).execute()

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorMsg = when (resp.code) {
                            401 -> "Tavily API Key 无效"
                            403 -> "Tavily API 访问被禁止"
                            429 -> "Tavily 请求过于频繁，请稍后再试"
                            else -> "Tavily 请求失败: ${resp.code} ${resp.message}"
                        }
                        return@withContext Result.failure(Exception(errorMsg))
                    }

                    val body = resp.body?.string()
                        ?: return@withContext Result.failure(
                            Exception("Tavily 返回空响应")
                        )

                    val searchResponse = gson.fromJson(body, TavilySearchResponse::class.java)
                    AppLog.d("Tavily 搜索完成: ${searchResponse.results.size} 条结果")
                    Result.success(searchResponse)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Tavily 搜索异常", e)
                Result.failure(e)
            }
        }

    /**
     * 简化搜索：返回结果列表
     */
    suspend fun simpleSearch(
        query: String,
        maxResults: Int = 3,
        includeAnswer: Boolean = true
    ): Result<List<SearchResult>> {
        val request = TavilySearchRequest(
            query = query,
            max_results = maxResults,
            include_answer = includeAnswer
        )
        return search(request).map { it.results }
    }

    /**
     * 带重试的搜索（指数退避）
     */
    suspend fun searchWithRetry(
        request: TavilySearchRequest,
        maxRetries: Int = 2
    ): Result<TavilySearchResponse> {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val result = search(request)
                if (result.isSuccess) return result
                lastException = result.exceptionOrNull() as? Exception
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
            }
            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1))
            }
        }
        return Result.failure(lastException ?: Exception("Tavily 所有重试均失败"))
    }

    /**
     * 测试 API 连接，支持传入未保存的 Key
     */
    suspend fun testConnection(apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("Tavily API Key 不能为空"))
                }

                val request = TavilySearchRequest(
                    query = "test",
                    max_results = 1,
                    include_answer = false
                )
                val requestBody = gson.toJson(request)
                    .toRequestBody(jsonMediaType)

                val httpRequest = Request.Builder()
                    .url(BASE_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val response = client.newCall(httpRequest).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorMsg = when (resp.code) {
                            401 -> "Tavily API Key 无效"
                            403 -> "Tavily API 访问被禁止"
                            429 -> "Tavily 请求过于频繁，请稍后再试"
                            500, 502, 503 -> "Tavily 服务器错误"
                            else -> "Tavily 请求失败: ${resp.code} ${resp.message}"
                        }
                        return@withContext Result.failure(Exception(errorMsg))
                    }

                    val body = resp.body?.string()
                    if (body.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("Tavily 返回空响应"))
                    }

                    try {
                        gson.fromJson(body, TavilySearchResponse::class.java)
                    } catch (e: Exception) {
                        return@withContext Result.failure(Exception("Tavily 响应格式异常"))
                    }

                    Result.success("连接成功")
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("无法连接 Tavily 服务器，请检查网络"))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("连接 Tavily 超时，请检查网络"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception("Tavily 连接测试失败: ${e.message}"))
            }
        }

    /**
     * 测试Tavily API并发性能，返回响应时间（毫秒）
     */
    suspend fun testConcurrency(apiKey: String = AppConfig.getTavilyApiKey()): Result<Long> {
        AppLog.d("开始测试Tavily API并发性能")
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    AppLog.e("Tavily API Key 未配置")
                    return@withContext Result.failure(Exception("Tavily API Key 未配置"))
                }

                val startTime = System.currentTimeMillis()

                val searchRequest = TavilySearchRequest(
                    query = "test",
                    max_results = 1
                )
                val requestBody = gson.toJson(searchRequest)
                    .toRequestBody(jsonMediaType)

                val httpRequest = Request.Builder()
                    .url(BASE_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                AppLog.d("发送Tavily测试请求")
                val response = client.newCall(httpRequest).execute()
                response.use { resp ->
                    val elapsed = System.currentTimeMillis() - startTime

                    if (resp.isSuccessful) {
                        AppLog.d("Tavily测试成功，耗时: ${elapsed}ms")
                        Result.success(elapsed)
                    } else {
                        AppLog.e("Tavily测试失败: HTTP ${resp.code}")
                        Result.failure(Exception("HTTP ${resp.code}"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Tavily测试异常: ${e.message}")
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.tavily.com/search"

        @Volatile
        private var instance: TavilyClient? = null

        fun getInstance(): TavilyClient {
            return instance ?: synchronized(this) {
                instance ?: TavilyClient().also { instance = it }
            }
        }
    }
}

// ========== 数据模型 ==========

data class TavilySearchRequest(
    val query: String,
    val search_depth: String = "basic",
    val include_answer: Boolean = false,
    val max_results: Int = 5
)

data class TavilySearchResponse(
    val query: String,
    val answer: String?,
    val results: List<SearchResult>,
    val response_time: Double?
)

data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Float
)
