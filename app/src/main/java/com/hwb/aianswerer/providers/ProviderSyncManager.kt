package com.hwb.aianswerer.providers

import android.content.Context
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 厂商数据同步管理
 *
 * 同步流程：
 * 1. 首次启动 → 从 assets/provider_data.json 加载到 MMKV
 * 2. 后续启动 → fetch 远程 JSON（带 ETag/If-Modified-Since）
 *    - 304 Not Modified → 跳过
 *    - 200 OK → 解析 JSON → 比较版本号 → upsert 到 MMKV
 *    - 网络失败 → 使用本地数据
 *
 * 数据来源：OpenCode（API-Key-Manager provider definitions + models.dev registry）
 * 兼容 Cherry Studio 格式的 provider_data.json 和 API-Key-Manager REST API
 *
 * 同步只覆盖厂商 metadata，保留用户 apiKey/enabled/sortOrder
 */
object ProviderSyncManager {

    private const val ASSETS_FILE = "provider_data.json"

    // 默认远程 URL — OpenCode provider-data.json
    // 可通过 AppConfig 覆盖为自行部署的 API-Key-Manager 地址
    const val DEFAULT_SYNC_URL =
        "https://github.com/Townrain/AIAnswerer-pro/releases/latest/download/provider_data.json"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    sealed class SyncResult {
        data class Updated(
            val version: Int, val providerCount: Int,
            val modelCount: Int, val source: String? = null
        ) : SyncResult()
        object UpToDate : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    /**
     * 执行同步（应该在后台线程调用）
     *
     * @param context 用于读取 assets
     * @param syncUrl 远程 JSON URL，默认使用 OpenCode provider_data.json
     * @param force 强制同步，忽略 ETag 缓存
     */
    suspend fun sync(
        context: Context,
        syncUrl: String = DEFAULT_SYNC_URL,
        force: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        // 确保本地有兜底数据
        ensureLocalData(context)

        try {
            fetchRemoteData(syncUrl, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("ProviderSyncManager: Remote sync failed, using local data: ${e.message}")
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 确保本地有数据（首次启动时从 assets 加载）
     */
    private fun ensureLocalData(context: Context) {
        if (ProviderStorage.getDataVersion() > 0) return

        AppLog.i("ProviderSyncManager: First launch: loading provider data from assets")
        try {
            val json = context.assets.open(ASSETS_FILE).bufferedReader().use { it.readText() }
            val data = JsonUtil.gson.fromJson(json, ProviderDataJson::class.java)
            if (data != null) {
                ProviderStorage.saveProviderData(data)
                AppLog.i("ProviderSyncManager: Loaded from assets: v${data.version}, ${data.providerCount} providers")
            }
        } catch (e: Exception) {
            AppLog.e("ProviderSyncManager: Failed to load assets/$ASSETS_FILE", e)
        }
    }

    /**
     * 从远程拉取数据，带 ETag/Last-Modified 缓存
     */
    private suspend fun fetchRemoteData(url: String, force: Boolean): SyncResult {
        val requestBuilder = Request.Builder().url(url).get()

        if (!force) {
            val etag = ProviderStorage.getSyncEtag()
            val lastModified = ProviderStorage.getSyncLastModified()
            if (etag != null) requestBuilder.addHeader("If-None-Match", etag)
            if (lastModified != null) requestBuilder.addHeader("If-Modified-Since", lastModified)
        } else {
            requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
        }

        val request = requestBuilder.build()
        val response = httpClient.newCall(request).awaitCancellable()

        return response.use { resp ->
            when (resp.code) {
                304 -> {
                    AppLog.d("ProviderSyncManager: Remote data not modified (304)")
                    SyncResult.UpToDate
                }
                200 -> {
                    val body = resp.body?.string() ?: return@use SyncResult.Error("Empty response body")
                    val remoteData = try {
                        JsonUtil.gson.fromJson(body, ProviderDataJson::class.java)
                    } catch (e: Exception) {
                        return@use SyncResult.Error("JSON parse error: ${e.message}")
                    }

                    if (remoteData == null) {
                        return@use SyncResult.Error("Null parsed data")
                    }

                    val localVersion = ProviderStorage.getDataVersion()
                    if (remoteData.version <= localVersion && !force) {
                        AppLog.d("ProviderSyncManager: Local v${localVersion} >= remote v${remoteData.version}, skipping")
                        return@use SyncResult.UpToDate
                    }

                    // 合并：保留用户配置
                    mergeAndSave(remoteData)

                    // 保存同步元数据
                    ProviderStorage.saveSyncMeta(
                        etag = resp.header("ETag"),
                        lastModified = resp.header("Last-Modified")
                    )

                    AppLog.i("ProviderSyncManager: Synced: v${remoteData.version}, ${remoteData.providerCount} providers, ${remoteData.modelCount} models, source=${remoteData.source ?: "unknown"}")
                    SyncResult.Updated(remoteData.version, remoteData.providerCount, remoteData.modelCount, remoteData.source)
                }
                else -> SyncResult.Error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    /**
     * 合并远程数据到本地，保留用户配置
     */
    private fun mergeAndSave(remoteData: ProviderDataJson) {
        ProviderStorage.saveProviderData(remoteData)
        // 用户的 apiKey/enabled/customApiHost 存在单独的 key 中，不会被覆盖
    }
}

/**
 * OkHttp Call → 可取消的挂起函数
 * 复用 OpenAIClient 的模式
 */
private suspend fun Call.awaitCancellable(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) {
                    cont.resumeWithException(e)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (!cont.isCancelled) {
                    cont.resume(response)
                }
            }
        })
    }
