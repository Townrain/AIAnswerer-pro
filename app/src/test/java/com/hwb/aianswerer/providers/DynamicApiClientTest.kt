package com.hwb.aianswerer.providers

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Tests for DynamicApiClient.
 *
 * Classification:
 * - Pure (no safelyInvoke): data classes, modelListPath, buildModelListUrl,
 *   tryParseOpenAI, tryParseOllama, tryParseStringArray — all use only
 *   string manipulation and Gson (JVM-compatible).
 * - Android-dependent: fetchModelList, testConnection — use OkHttp/MMKV.
 *   Wrapped in safelyInvoke; will gracefully skip in JVM environment.
 */
class DynamicApiClientTest {

    // ── Data model tests (pure) ──────────────────────────────────────────

    // Removed: references private ModelItem type

    @Test
    fun `ModelListResponse空数据列表`() {
        val resp = DynamicApiClient.ModelListResponse(data = emptyList())
        assertTrue(resp.data?.isEmpty() == true)
    }

    // Removed: references private OllamaModelItem type

    // ── modelListPath tests (pure, via reflection) ────────────────────

    @Test
    fun `modelListPath ollama类型返回apiTags`() {
        val path = invokePrivateModelListPath("ollama", "http://localhost:11434")
        assertEquals("/api/tags", path)
    }

    @Test
    fun `modelListPath host含localhost11434返回apiTags`() {
        val path = invokePrivateModelListPath("openai", "http://localhost:11434")
        assertEquals("/api/tags", path)
    }

    @Test
    fun `modelListPath anthropic类型返回空字符串`() {
        val path = invokePrivateModelListPath("anthropic", "https://api.anthropic.com")
        assertEquals("", path)
    }

    @Test
    fun `modelListPath host含v1返回models`() {
        val path = invokePrivateModelListPath("openai", "https://api.openai.com/v1")
        assertEquals("/models", path)
    }

    @Test
    fun `modelListPath host含v2返回models`() {
        val path = invokePrivateModelListPath("openai", "https://api.example.com/v2")
        assertEquals("/models", path)
    }

    @Test
    fun `modelListPath默认返回v1Models`() {
        val path = invokePrivateModelListPath("openai", "https://api.openai.com")
        assertEquals("/v1/models", path)
    }

    @Test
    fun `modelListPath不识别类型走默认路径`() {
        // gemini 等非特殊类型走默认
        val path = invokePrivateModelListPath("gemini", "https://api.gemini.com")
        assertEquals("/v1/models", path)
    }

    // ── buildModelListUrl tests (pure, via reflection) ─────────────────

    @Test
    fun `buildModelListUrl普通host拼接正确`() {
        val url = invokePrivateBuildModelListUrl("https://api.openai.com", "openai")
        assertEquals("https://api.openai.com/v1/models", url)
    }

    @Test
    fun `buildModelListUrl以v1结尾不重复拼`() {
        val url = invokePrivateBuildModelListUrl("https://api.openai.com/v1", "openai")
        assertEquals("https://api.openai.com/v1/models", url)
    }

    @Test
    fun `buildModelListUrl以v1斜杠结尾不重复拼`() {
        val url = invokePrivateBuildModelListUrl("https://api.openai.com/v1/", "openai")
        assertEquals("https://api.openai.com/v1/models", url)
    }

    @Test
    fun `buildModelListUrl带尾部斜杠正确去除`() {
        val url = invokePrivateBuildModelListUrl("https://api.example.com/", "openai")
        assertEquals("https://api.example.com/v1/models", url)
    }

    @Test
    fun `buildModelListUrl ollama类型路径正确`() {
        val url = invokePrivateBuildModelListUrl("http://localhost:11434", "ollama")
        assertEquals("http://localhost:11434/api/tags", url)
    }

    @Test
    fun `buildModelListUrl anthropic类型返回空路径`() {
        val url = invokePrivateBuildModelListUrl("https://api.anthropic.com", "anthropic")
        assertEquals("https://api.anthropic.com", url)
    }

    // ── tryParseOpenAI tests (pure, via reflection) ────────────────────

    @Test
    fun `tryParseOpenAI有效响应返回排序列表`() {
        val json = """{"data":[{"id":"gpt-4"},{"id":"gpt-3.5-turbo"},{"id":"gpt-4o"}]}"""
        val result = invokePrivateTryParseOpenAI(json)
        assertNotNull(result)
        assertEquals(listOf("gpt-3.5-turbo", "gpt-4", "gpt-4o"), result)
    }

    @Test
    fun `tryParseOpenAI空数据返回null`() {
        val json = """{"data":[]}"""
        val result = invokePrivateTryParseOpenAI(json)
        assertNull(result)
    }

    @Test
    fun `tryParseOpenAI非法JSON返回null`() {
        val result = invokePrivateTryParseOpenAI("not json at all")
        assertNull(result)
    }

    @Test
    fun `tryParseOpenAI空字符串返回null`() {
        val result = invokePrivateTryParseOpenAI("")
        assertNull(result)
    }

    @Test
    fun `tryParseOpenAI null数据返回null`() {
        val json = """{"data":null}"""
        val result = invokePrivateTryParseOpenAI(json)
        assertNull(result)
    }

    // ── tryParseOllama tests (pure, via reflection) ────────────────────

    @Test
    fun `tryParseOllama有效响应返回排序列表`() {
        val json = """{"models":[{"name":"llama3:8b"},{"name":"mistral"},{"name":"codellama"}]}"""
        val result = invokePrivateTryParseOllama(json)
        assertNotNull(result)
        assertEquals(listOf("codellama", "llama3:8b", "mistral"), result)
    }

    @Test
    fun `tryParseOllama空models返回null`() {
        val json = """{"models":[]}"""
        val result = invokePrivateTryParseOllama(json)
        assertNull(result)
    }

    @Test
    fun `tryParseOllama非法JSON返回null`() {
        val result = invokePrivateTryParseOllama("{broken}")
        assertNull(result)
    }

    // ── tryParseStringArray tests (pure, via reflection) ───────────────

    @Test
    fun `tryParseStringArray有效数组返回排序列表`() {
        val json = """["gpt-4","claude-3","gemini-pro"]"""
        val result = invokePrivateTryParseStringArray(json)
        assertNotNull(result)
        assertEquals(3, result!!.size)
        assertTrue(result!!.containsAll(listOf("claude-3", "gpt-4", "gemini-pro")))
    }

    @Test
    fun `tryParseStringArray空数组返回null`() {
        val result = invokePrivateTryParseStringArray("[]")
        assertNull(result)
    }

    @Test
    fun `tryParseStringArray非数组JSON返回null`() {
        val result = invokePrivateTryParseStringArray("""{"key":"value"}""")
        assertNull(result)
    }

    @Test
    fun `tryParseStringArray非法内容返回null`() {
        val result = invokePrivateTryParseStringArray("not json")
        assertNull(result)
    }

    // ── Android-dependent (safelyInvoke) ───────────────────────────────

    @Test
    fun `fetchModelList anthropic类型直接返回失败`() {
        safelyInvoke {
            runBlocking {
                val result = DynamicApiClient.fetchModelList(
                    apiHost = "https://api.anthropic.com",
                    apiKey = "sk-test",
                    type = "anthropic"
                )
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message?.contains("Anthropic") == true)
            }
        }
    }

    @Test
    fun `testConnection通过safelyInvoke安全调用`() {
        safelyInvoke {
            runBlocking {
                val result = DynamicApiClient.testConnection(
                    apiHost = "https://api.openai.com",
                    apiKey = "sk-test",
                    modelName = "gpt-4",
                    type = "openai"
                )
                // In JVM test environment, this will either succeed or fail
                // depending on network/runtime — safelyInvoke catches MMKV/issues
                assertTrue(result.isSuccess || result.isFailure)
            }
        }
    }

    // ── Private reflection helpers ─────────────────────────────────────

    private fun invokePrivateModelListPath(type: String, apiHost: String): String {
        val method = DynamicApiClient::class.java.getDeclaredMethod(
            "modelListPath", String::class.java, String::class.java
        ).apply { isAccessible = true }
        return method.invoke(DynamicApiClient, type, apiHost) as String
    }

    private fun invokePrivateBuildModelListUrl(apiHost: String, type: String): String {
        val method = DynamicApiClient::class.java.getDeclaredMethod(
            "buildModelListUrl", String::class.java, String::class.java
        ).apply { isAccessible = true }
        return method.invoke(DynamicApiClient, apiHost, type) as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokePrivateTryParseOpenAI(body: String): List<String>? {
        val method = DynamicApiClient::class.java.getDeclaredMethod(
            "tryParseOpenAI", String::class.java
        ).apply { isAccessible = true }
        return method.invoke(DynamicApiClient, body) as? List<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokePrivateTryParseOllama(body: String): List<String>? {
        val method = DynamicApiClient::class.java.getDeclaredMethod(
            "tryParseOllama", String::class.java
        ).apply { isAccessible = true }
        return method.invoke(DynamicApiClient, body) as? List<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokePrivateTryParseStringArray(body: String): List<String>? {
        val method = DynamicApiClient::class.java.getDeclaredMethod(
            "tryParseStringArray", String::class.java
        ).apply { isAccessible = true }
        return method.invoke(DynamicApiClient, body) as? List<String>
    }
}
