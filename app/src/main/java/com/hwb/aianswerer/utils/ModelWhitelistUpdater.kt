package com.hwb.aianswerer.utils

import com.hwb.aianswerer.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 从 GitHub 在线检查模型白名单更新，并存储到本地
 * 同步 Cherry Studio 的所有模型分类列表和厂商配置
 */
object ModelWhitelistUpdater {

    // Cherry Studio GitHub 基础 URL
    private const val GITHUB_BASE_URL = "https://raw.githubusercontent.com/CherryHQ/cherry-studio/main/src/renderer/config/models/"
    private const val GITHUB_PROVIDERS_URL = "https://raw.githubusercontent.com/CherryHQ/cherry-studio/main/src/renderer/config/providers.ts"
    private const val GITHUB_DEFAULT_MODELS_URL = "https://raw.githubusercontent.com/CherryHQ/cherry-studio/main/src/renderer/config/models/default.ts"

    // 要同步的模型分类列表文件
    private val MODEL_FILES = listOf(
        "vision.ts",
        "reasoning.ts",
        "tooluse.ts",
        "embedding.ts",
        "websearch.ts"
    )

    data class CheckResult(
        val success: Boolean,
        val message: String,
        val visionCount: Int = 0,
        val excludedCount: Int = 0,
        val totalCount: Int = 0,
        val languageCount: Int = 0,
        val providerCount: Int = 0,
        val newProviderCount: Int = 0,
        val newModelCount: Int = 0
    )

    /**
     * 从 GitHub 检查最新的白名单并更新本地存储
     */
    suspend fun checkUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            // 获取 vision.ts 用于视觉模型分类
            val visionContent = fetchFromGitHub("${GITHUB_BASE_URL}vision.ts")
            val visionModels = extractRegexList(visionContent, "visionAllowedModels")
            val visionExcluded = extractRegexList(visionContent, "visionExcludedModels")

            if (visionModels.isEmpty()) {
                return@withContext CheckResult(false, "解析白名单失败")
            }

            // 获取所有模型分类列表的总数
            var totalCount = visionModels.size
            for (file in MODEL_FILES) {
                if (file == "vision.ts") continue
                try {
                    val content = fetchFromGitHub("$GITHUB_BASE_URL$file")
                    val models = extractAllRegexLists(content)
                    totalCount += models.size
                } catch (e: Exception) {
                    // 忽略单个文件的错误，继续同步其他文件
                }
            }

            // 获取厂商模型列表
            val defaultModelsContent = fetchFromGitHub(GITHUB_DEFAULT_MODELS_URL)
            val providerModels = extractProviderModels(defaultModelsContent)

            // 获取厂商配置列表
            val providersContent = fetchFromGitHub(GITHUB_PROVIDERS_URL)
            val providerConfigs = extractProviderConfigs(providersContent)

            // 计算更新前的数量
            val existingProviderConfigs = AppConfig.getDynamicProviderConfigs()
            val existingProviderModels = AppConfig.getDynamicProviderModels()
            val existingProviderIds = existingProviderConfigs.map { it.id }.toSet()
            val existingModelCount = existingProviderModels.values.sumOf { it.size }

            // 计算新增的厂商数量
            val newProviderCount = providerConfigs.count { it.id !in existingProviderIds }

            // 计算新增的模型数量
            var newModelCount = 0
            for ((providerId, models) in providerModels) {
                val existingModels = existingProviderModels[providerId] ?: emptyList()
                val newModels = models.filter { it !in existingModels }
                newModelCount += newModels.size
            }

            // 保存视觉模型白名单到本地存储
            AppConfig.saveDynamicVisionModels(visionModels)
            AppConfig.saveDynamicVisionExcluded(visionExcluded)

            // 保存厂商模型列表到本地存储（增量更新）
            AppConfig.saveDynamicProviderModels(providerModels)

            // 保存厂商配置列表到本地存储（增量更新）
            AppConfig.saveDynamicProviderConfigs(providerConfigs)

            // 语言模型 = 总数 - 视觉模型
            val languageCount = totalCount - visionModels.size

            CheckResult(
                success = true,
                message = "更新成功",
                visionCount = visionModels.size,
                excludedCount = visionExcluded.size,
                totalCount = totalCount,
                languageCount = languageCount,
                providerCount = providerConfigs.size,
                newProviderCount = newProviderCount,
                newModelCount = newModelCount
            )
        } catch (e: java.net.UnknownHostException) {
            CheckResult(false, "网络连接失败，请检查网络")
        } catch (e: java.net.ConnectException) {
            CheckResult(false, "无法连接到 GitHub")
        } catch (e: java.net.SocketTimeoutException) {
            CheckResult(false, "连接超时，请稍后重试")
        } catch (e: Exception) {
            CheckResult(false, "检查失败: ${e.message}")
        }
    }

    private fun fetchFromGitHub(urlString: String): String {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "AIAnswerer-Android")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        return if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val code = connection.responseCode
            val msg = if (code == 404) "同步源暂时不可用（资源未找到），已使用本地数据"
                      else "HTTP $code"
            throw Exception(msg)
        }
    }

    private fun extractRegexList(content: String, arrayName: String): List<String> {
        val startPattern = Regex("""const\s+$arrayName\s*=\s*\[""")
        val startMatch = startPattern.find(content) ?: return emptyList()

        val start = startMatch.range.last + 1
        var bracketCount = 0
        var i = start

        while (i < content.length) {
            when (content[i]) {
                '[' -> bracketCount++
                ']' -> {
                    if (bracketCount == 0) break
                    bracketCount--
                }
            }
            i++
        }

        val arrayContent = content.substring(start, i)
        val stringPattern = Regex("""['"`]([^'"`]+)['"`]""")
        return stringPattern.findAll(arrayContent).map { it.groupValues[1] }.toList()
    }

    /**
     * 从文件内容中提取所有正则表达式列表
     */
    private fun extractAllRegexLists(content: String): List<String> {
        val allModels = mutableListOf<String>()
        val arrayPattern = Regex("""const\s+(\w+)\s*=\s*\[""")
        val matches = arrayPattern.findAll(content)

        for (match in matches) {
            val arrayName = match.groupValues[1]
            val models = extractRegexList(content, arrayName)
            allModels.addAll(models)
        }

        return allModels.distinct()
    }

    /**
     * 从 default.ts 中提取厂商模型列表
     * 返回 Map<providerId, List<modelName>>
     */
    private fun extractProviderModels(content: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()

        // 匹配 SYSTEM_MODELS 对象中的各个 provider
        val providerPattern = Regex("""(\w+):\s*\[""")
        val matches = providerPattern.findAll(content)

        for (match in matches) {
            val providerId = match.groupValues[1]
            if (providerId == "defaultModel") continue

            val start = match.range.last
            val models = extractModelsFromProvider(content, start)
            if (models.isNotEmpty()) {
                result[providerId] = models
            }
        }

        return result
    }

    /**
     * 从指定位置提取 provider 的模型列表
     */
    private fun extractModelsFromProvider(content: String, startIndex: Int): List<String> {
        val models = mutableListOf<String>()
        var bracketCount = 0
        var i = startIndex
        val sb = StringBuilder()

        while (i < content.length) {
            when (content[i]) {
                '[' -> {
                    bracketCount++
                    sb.append(content[i])
                }
                ']' -> {
                    bracketCount--
                    sb.append(content[i])
                    if (bracketCount == 0) break
                }
                else -> sb.append(content[i])
            }
            i++
        }

        val arrayContent = sb.toString()

        // 提取 id 字段
        val idPattern = Regex("""id:\s*['"`]([^'"`]+)['"`]""")
        val idMatches = idPattern.findAll(arrayContent)
        for (idMatch in idMatches) {
            models.add(idMatch.groupValues[1])
        }

        return models
    }

    /**
     * 从 providers.ts 中提取厂商配置
     * 返回 List<ProviderConfig>
     */
    private fun extractProviderConfigs(content: String): List<ProviderConfig> {
        val providers = mutableListOf<ProviderConfig>()

        // 匹配 SYSTEM_PROVIDERS_CONFIG 对象中的各个 provider
        val providerPattern = Regex("""(\w+):\s*\{""")
        val matches = providerPattern.findAll(content)

        for (match in matches) {
            val providerId = match.groupValues[1]
            if (providerId == "SYSTEM_PROVIDERS_CONFIG" || providerId == "id") continue

            val start = match.range.last
            val config = extractSingleProviderConfig(content, start, providerId)
            if (config != null) {
                providers.add(config)
            }
        }

        return providers
    }

    /**
     * 提取单个厂商配置
     */
    private fun extractSingleProviderConfig(content: String, startIndex: Int, providerId: String): ProviderConfig? {
        var braceCount = 0
        var i = startIndex
        val sb = StringBuilder()

        while (i < content.length) {
            when (content[i]) {
                '{' -> {
                    braceCount++
                    sb.append(content[i])
                }
                '}' -> {
                    braceCount--
                    sb.append(content[i])
                    if (braceCount == 0) break
                }
                else -> sb.append(content[i])
            }
            i++
        }

        val configContent = sb.toString()

        // 提取 name
        val namePattern = Regex("""name:\s*['"`]([^'"`]+)['"`]""")
        val nameMatch = namePattern.find(configContent)
        val name = nameMatch?.groupValues?.get(1) ?: providerId

        // 提取 type
        val typePattern = Regex("""type:\s*['"`]([^'"`]+)['"`]""")
        val typeMatch = typePattern.find(configContent)
        val type = typeMatch?.groupValues?.get(1) ?: "openai"

        // 提取 apiHost
        val apiHostPattern = Regex("""apiHost:\s*['"`]([^'"`]+)['"`]""")
        val apiHostMatch = apiHostPattern.find(configContent)
        val apiHost = apiHostMatch?.groupValues?.get(1) ?: ""

        return ProviderConfig(
            id = providerId,
            name = name,
            type = type,
            apiHost = apiHost
        )
    }

    /**
     * 厂商配置数据类
     */
    data class ProviderConfig(
        val id: String,
        val name: String,
        val type: String,
        val apiHost: String
    )
}
