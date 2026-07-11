package com.hwb.aianswerer.providers

import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.hwb.aianswerer.utils.AppLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object DynamicApiClient {

    data class ModelListResponse(val data: List<ModelItem>?)
    data class ModelItem(val id: String, @SerializedName("owned_by") val ownedBy: String?)

    // Ollama 格式
    data class OllamaModelListResponse(val models: List<OllamaModelItem>?)
    data class OllamaModelItem(val name: String?)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                    redactHeader("Authorization")
                })
            }
            .build()
    }

    private val gson = JsonUtil.gson

    /**
     * 根据厂商类型推断模型列表 API 路径
     */
    private fun modelListPath(type: String, apiHost: String): String {
        val host = apiHost.trimEnd('/')
        return when {
            // Ollama
            type == "ollama" || host.contains("localhost:11434") -> "/api/tags"
            // Anthropic 没有公开的模型列表 API
            type == "anthropic" -> ""
            // host 已经包含 /v1/ 之类的路径，直接拼 /models
            host.contains("/v1") -> "/models"
            host.contains("/v2") -> "/models"
            // 默认 OpenAI 兼容
            else -> "/v1/models"
        }
    }

    /**
     * 拼接完整的模型列表 URL
     */
    private fun buildModelListUrl(apiHost: String, type: String): String {
        val host = apiHost.trimEnd('/')
        val path = modelListPath(type, apiHost)

        // 如果 host 已经以 /v1/ 结尾或包含 /v1/，不再重复拼
        val base = when {
            host.endsWith("/v1") -> host
            host.endsWith("/v1/") -> host.trimEnd('/')
            else -> host
        }
        return "$base$path"
    }

    /**
     * 获取厂商的模型列表
     */
    suspend fun fetchModelList(
        apiHost: String,
        apiKey: String,
        type: String = "openai"
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (type == "anthropic") {
            return@withContext Result.failure(Exception("Anthropic 不支持在线获取模型列表，请手动输入模型名称"))
        }

        try {
            val url = buildModelListUrl(apiHost, type)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = withTimeout(30_000L) {
                client.newCall(request).awaitCancellable()
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string()?.take(300) ?: ""
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: $errorBody"))
                }

                val body = resp.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Empty response"))
                }

                // 尝试 OpenAI 格式 { data: [{ id: "..." }] }
                val openaiResult = tryParseOpenAI(body)
                if (openaiResult != null) return@withContext Result.success(openaiResult)

                // 尝试 Ollama 格式 { models: [{ name: "..." }] }
                val ollamaResult = tryParseOllama(body)
                if (ollamaResult != null) return@withContext Result.success(ollamaResult)

                // 尝试纯数组 ["model1", "model2"]
                val arrayResult = tryParseStringArray(body)
                if (arrayResult != null) return@withContext Result.success(arrayResult)

                Result.failure(Exception("无法解析响应格式"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法解析主机地址，请检查网络"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时"))
        } catch (e: Exception) {
            Result.failure(Exception("获取模型列表失败: ${e.message}"))
        }
    }

    private fun tryParseOpenAI(body: String): List<String>? {
        return try {
            val resp = gson.fromJson(body, ModelListResponse::class.java)
            resp?.data?.mapNotNull { it.id }?.sorted()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseOllama(body: String): List<String>? {
        return try {
            val resp = gson.fromJson(body, OllamaModelListResponse::class.java)
            resp?.models?.mapNotNull { it.name }?.sorted()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseStringArray(body: String): List<String>? {
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, String::class.java).type
            val list: List<String>? = gson.fromJson(body, type)
            list?.sorted()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 测试厂商连接
     */
    suspend fun testConnection(
        apiHost: String,
        apiKey: String,
        modelName: String,
        type: String = "openai"
    ): Result<Long> = withContext(Dispatchers.IO) {
        android.util.Log.e("AIAnswerer", "[DYN] ====== testConnection ENTERED ======")
        AppLog.enter("DYN", "testConnection $type")
        try {
            val chatEndpoint = when (type) {
                "anthropic" -> "/v1/messages"
                else -> {
                    val host = apiHost.trimEnd('/')
                    when {
                        host.endsWith("/v1") -> "/chat/completions"
                        host.endsWith("/v1/") -> "chat/completions"
                        else -> "/v1/chat/completions"
                    }
                }
            }
            val base = apiHost.trimEnd('/').trimEnd('/')
            val url = "$base${chatEndpoint}"

            val requestBody = when (type) {
                "anthropic" -> """{"model":"$modelName","max_tokens":16,"messages":[{"role":"user","content":"Hi"}]}"""
                else -> """{"model":"$modelName","messages":[{"role":"user","content":"Hi"}],"max_tokens":16,"stream":false}"""
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))

            when (type) {
                "anthropic" -> {
                    requestBuilder
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                }
                else -> requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val startTime = System.currentTimeMillis()
            val response = withTimeout(30_000L) {
                client.newCall(requestBuilder.build()).awaitCancellable()
            }

            response.use { resp ->
                val elapsed = System.currentTimeMillis() - startTime
                if (resp.isSuccessful) Result.success(elapsed)
                else {
                    val errorBody = resp.body?.string()?.take(300) ?: ""
                    Result.failure(Exception("HTTP ${resp.code}: $errorBody"))
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("连接超时"))
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("连接超时"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法解析主机地址"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时"))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("SSL 证书错误"))
        } catch (e: Exception) {
            Result.failure(Exception("连接测试失败: ${e.message}"))
        }
    }
}

private suspend fun Call.awaitCancellable(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!cont.isCancelled) cont.resume(response) else response.close()
            }
        })
    }
