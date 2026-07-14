package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.providers.LocalWebSearchConfig
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val gson = JsonUtil.gson
private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
private val client = OkHttpClient.Builder()
    .callTimeout(20, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private suspend fun httpGetJson(url: String, vararg headers: Pair<String, String>): com.google.gson.JsonObject? =
    withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).get()
            for ((k, v) in headers) builder.addHeader(k, v)
            val resp = client.newCall(builder.build()).execute()
            resp.use { r ->
                if (!r.isSuccessful) { AppLog.w("WebSearch: HTTP ${r.code}"); return@withContext null }
                gson.fromJson(r.body?.string(), com.google.gson.JsonObject::class.java)
            }
        } catch (e: Exception) { AppLog.e("WebSearch: GET failed", e); null }
    }

private suspend fun httpPostJson(url: String, body: String, vararg headers: Pair<String, String>): com.google.gson.JsonObject? =
    withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType))
            for ((k, v) in headers) builder.addHeader(k, v)
            val resp = client.newCall(builder.build()).execute()
            resp.use { r ->
                if (!r.isSuccessful) { AppLog.w("WebSearch: HTTP ${r.code}"); return@withContext null }
                gson.fromJson(r.body?.string(), com.google.gson.JsonObject::class.java)
            }
        } catch (e: Exception) { AppLog.e("WebSearch: POST failed", e); null }
    }

private suspend fun httpGetString(url: String, vararg headers: Pair<String, String>): String? =
    withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).get()
            for ((k, v) in headers) builder.addHeader(k, v)
            val resp = client.newCall(builder.build()).execute()
            resp.use { r ->
                if (!r.isSuccessful) { AppLog.w("WebSearch: HTTP ${r.code}"); return@withContext null }
                r.body?.string()
            }
        } catch (e: Exception) { AppLog.e("WebSearch: GET html failed", e); null }
    }

// ══════════════════════════════════════════════════════════════════════════
class TavilySearchProvider(config: LocalWebSearchConfig) : BaseWebSearchProvider(config) {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        if (apiKey.isBlank()) return emptyList()
        val body = gson.toJson(mapOf("query" to query, "max_results" to maxResults))
        val json = httpPostJson("$apiHost/search", body, "Authorization" to "Bearer $apiKey", "Content-Type" to "application/json") ?: return emptyList()
        val arr = json.getAsJsonArray("results") ?: return emptyList()
        return arr.map { r -> val o = r.asJsonObject; WebSearchResult(o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(), o.get("content")?.asString.orEmpty()) }
    }
}

class ZhipuSearchProvider(config: LocalWebSearchConfig) : BaseWebSearchProvider(config) {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        if (apiKey.isBlank()) return emptyList()
        val body = gson.toJson(mapOf("search_query" to query, "search_engine" to "search_std", "search_intent" to false))
        val json = httpPostJson(apiHost, body, "Authorization" to "Bearer $apiKey", "Content-Type" to "application/json") ?: return emptyList()
        val arr = json.getAsJsonArray("search_result") ?: return emptyList()
        return arr.take(maxResults).map { r -> val o = r.asJsonObject; WebSearchResult(o.get("title")?.asString.orEmpty(), o.get("link")?.asString.orEmpty(), o.get("content")?.asString.orEmpty()) }
    }
}

class BochaSearchProvider(config: LocalWebSearchConfig) : BaseWebSearchProvider(config) {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        if (apiKey.isBlank()) return emptyList()
        val body = gson.toJson(mapOf("query" to query, "count" to maxResults, "summary" to true, "page" to 1))
        val json = httpPostJson("$apiHost/v1/web-search", body, "Authorization" to "Bearer $apiKey", "Content-Type" to "application/json") ?: return emptyList()
        if (json.get("code")?.asInt != 200) return emptyList()
        val pages = json.getAsJsonObject("data")?.getAsJsonArray("value") ?: return emptyList()
        return pages.map { p -> val o = p.asJsonObject; WebSearchResult(o.get("name")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(), o.get("summary")?.asString ?: o.get("snippet")?.asString.orEmpty()) }
    }
}

class ExaSearchProvider(config: LocalWebSearchConfig) : BaseWebSearchProvider(config) {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        if (apiKey.isBlank()) return emptyList()
        val body = gson.toJson(mapOf("query" to query, "numResults" to maxResults, "contents" to mapOf("text" to true)))
        val json = httpPostJson("$apiHost/search", body, "x-api-key" to apiKey, "Content-Type" to "application/json") ?: return emptyList()
        val arr = json.getAsJsonArray("results") ?: return emptyList()
        return arr.map { r -> val o = r.asJsonObject; WebSearchResult(o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(), o.get("text")?.asString.orEmpty()) }
    }
}

class QueritSearchProvider(config: LocalWebSearchConfig) : BaseWebSearchProvider(config) {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        if (apiKey.isBlank()) return emptyList()
        val body = gson.toJson(mapOf("query" to query, "count" to maxResults))
        val json = httpPostJson("$apiHost/v1/search", body, "Authorization" to "Bearer $apiKey", "Content-Type" to "application/json") ?: return emptyList()
        if (json.get("error_code")?.asInt != 200) return emptyList()
        val arr = json.getAsJsonObject("results")?.getAsJsonArray("result") ?: return emptyList()
        return arr.map { r -> val o = r.asJsonObject; WebSearchResult(o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(), o.get("snippet")?.asString.orEmpty()) }
    }
}

class SearXNGSProvider(config: LocalWebSearchConfig) : BaseWebSearchProvider(config) {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        val url = "$apiHost/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&language=auto"
        val headers = mutableListOf<Pair<String, String>>()
        if (config.basicAuthUsername.isNotBlank()) {
            val cred = android.util.Base64.encodeToString("${config.basicAuthUsername}:${config.basicAuthPassword}".toByteArray(), android.util.Base64.NO_WRAP)
            headers.add("Authorization" to "Basic $cred")
        }
        val json = httpGetJson(url, *headers.toTypedArray()) ?: return emptyList()
        val arr = json.getAsJsonArray("results") ?: return emptyList()
        return arr.take(maxResults).map { r -> val o = r.asJsonObject; WebSearchResult(o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(), o.get("content")?.asString ?: o.get("snippet")?.asString.orEmpty()) }
    }
}

abstract class LocalSearchProvider(private val cfg: LocalWebSearchConfig) : BaseWebSearchProvider(cfg) {
    protected abstract fun parseResults(html: String, maxResults: Int): List<WebSearchResult>
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        val url = cfg.url.replace("%s", java.net.URLEncoder.encode(query, "UTF-8"))
        val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val html = httpGetString(url, "User-Agent" to ua) ?: return emptyList()
        return parseResults(html, maxResults)
    }
}

class LocalGoogleSearchProvider(config: LocalWebSearchConfig) : LocalSearchProvider(config) {
    override fun parseResults(html: String, maxResults: Int): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        val blockRegex = Regex("""<a\s+href="(https?://[^"]+)"[^>]*>\s*<h3[^>]*>([^<]+)</h3>""", RegexOption.IGNORE_CASE)
        blockRegex.findAll(html).take(maxResults).forEach { m -> results.add(WebSearchResult(m.groupValues[2].trim(), m.groupValues[1], "")) }
        if (results.isEmpty()) {
            val simpleRegex = Regex("""<a\s+href="(https?://[^"]+)"[^>]*>([^<]{10,200})</a>""")
            simpleRegex.findAll(html).take(maxResults).forEach { m ->
                if (!m.groupValues[1].contains("google") && m.groupValues[1].startsWith("http"))
                    results.add(WebSearchResult(m.groupValues[2].trim(), m.groupValues[1], ""))
            }
        }
        return results
    }
}

class LocalBingSearchProvider(config: LocalWebSearchConfig) : LocalSearchProvider(config) {
    override fun parseResults(html: String, maxResults: Int): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        val regex = Regex("""<li\s+class="b_algo"[^>]*>.*?<a\s+href="(https?://[^"]+)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
        regex.findAll(html).take(maxResults).forEach { m -> results.add(WebSearchResult(m.groupValues[2].replace(Regex("<[^>]+>"), "").trim(), m.groupValues[1], "")) }
        return results
    }
}

class LocalBaiduSearchProvider(config: LocalWebSearchConfig) : LocalSearchProvider(config) {
    override fun parseResults(html: String, maxResults: Int): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        val regex = Regex("""<h3[^>]*>\s*<a\s+href="(https?://[^"]+)"[^>]*>(.+?)</a>""", RegexOption.IGNORE_CASE)
        regex.findAll(html).take(maxResults).forEach { m ->
            var title = m.groupValues[2].replace(Regex("<[^>]+>"), "").replace(Regex("</?em>"), "").trim()
            val url = m.groupValues[1]
            if (!url.contains("baidu.com/cb.php") && !url.contains("baidu.com/link")) results.add(WebSearchResult(title, url, ""))
        }
        return results
    }
}
