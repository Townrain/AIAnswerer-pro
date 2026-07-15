package com.hwb.aianswerer.providers

import com.hwb.aianswerer.api.vision.VisionProviderFactory
import com.hwb.aianswerer.config.ConfigStorage
import com.tencent.mmkv.MMKV
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderConfigResolverTest {

    private lateinit var mmkvMock: MMKV

    @Before
    fun setUp() {
        mmkvMock = mockk(relaxed = true)
        mockkObject(ConfigStorage)
        mockkObject(ProviderStorage)
        every { ConfigStorage.requireMmkv() } returns mmkvMock
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun createTestProvider(
        id: String = "test-provider",
        name: String = "Test Provider",
        apiHost: String = "https://api.test.com",
        apiKey: String = "sk-test-key",
        enabled: Boolean = true,
        selectedModels: List<String> = listOf("gpt-4"),
        models: List<ModelEntry> = listOf(ModelEntry("gpt-4", "GPT-4", "chat"))
    ): LocalProviderConfig = LocalProviderConfig(
        id = id, name = name, type = "openai",
        apiHost = apiHost, anthropicApiHost = null,
        models = models, websites = null,
        apiKey = apiKey, enabled = enabled,
        selectedModels = selectedModels
    )


    // ═══════════════════════════════════════════════════════════════════
    //  resolveApiProvider()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveApiProvider - no enabled providers returns null`() {
        every { ProviderStorage.getMergedProviders() } returns emptyList()

        val result = ProviderConfigResolver.resolveApiProvider()
        assertNull(result)
    }

    @Test
    fun `resolveApiProvider - exact model match returns matching provider`() {
        val providerA = createTestProvider(id = "p-a", name = "Provider A", selectedModels = listOf("gpt-4"))
        val providerB = createTestProvider(id = "p-b", name = "Provider B", selectedModels = listOf("claude-3"))
        every { ProviderStorage.getMergedProviders() } returns listOf(providerA, providerB)
        // UserConfig selectedModels contain "gpt-4" → targetId = "p-a"
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns listOf<LocalProviderConfig>(
            createTestProvider(id = "p-a", selectedModels = listOf("gpt-4")),
            createTestProvider(id = "p-b", selectedModels = listOf("claude-3"))
        )
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns "gpt-4"

        val result = ProviderConfigResolver.resolveApiProvider()
        assertNotNull(result)
        assertEquals("p-a", result!!.id)
    }

    @Test
    fun `resolveApiProvider - model not matched falls back to first enabled`() {
        val providerA = createTestProvider(id = "p-a", name = "Provider A", selectedModels = listOf("gpt-4"))
        val providerB = createTestProvider(id = "p-b", name = "Provider B", selectedModels = listOf("claude-3"))
        every { ProviderStorage.getMergedProviders() } returns listOf(providerA, providerB)
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns listOf<LocalProviderConfig>(
            createTestProvider(id = "p-a", selectedModels = listOf("gpt-4")),
            createTestProvider(id = "p-b", selectedModels = listOf("claude-3"))
        )
        // Model "gemini-pro" not in any user config → fallback to first
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns "gemini-pro"

        val result = ProviderConfigResolver.resolveApiProvider()
        assertNotNull(result)
        assertEquals("p-a", result!!.id)
    }

    @Test
    fun `resolveApiProvider - empty modelName returns first enabled`() {
        val providerA = createTestProvider(id = "p-a", name = "First")
        val providerB = createTestProvider(id = "p-b", name = "Second")
        every { ProviderStorage.getMergedProviders() } returns listOf(providerA, providerB)
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiProvider()
        assertNotNull(result)
        assertEquals("p-a", result!!.id)
    }

    @Test
    fun `resolveApiProvider - exception returns null`() {
        every { ProviderStorage.getMergedProviders() } throws RuntimeException("Test exception")

        val result = ProviderConfigResolver.resolveApiProvider()
        assertNull(result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveApiUrl()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveApiUrl - no provider returns null`() {
        every { ProviderStorage.getMergedProviders() } returns emptyList()
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiUrl()
        assertNull(result)
    }

    @Test
    fun `resolveApiUrl - host ends with v1 appends chat completions`() {
        val provider = createTestProvider(apiHost = "https://api.openai.com/v1")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiUrl()
        assertEquals("https://api.openai.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveApiUrl - host contains v1 slash appends chat completions`() {
        val provider = createTestProvider(apiHost = "https://api.openai.com/v1/")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiUrl()
        assertEquals("https://api.openai.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveApiUrl - host without v1 appends full path`() {
        val provider = createTestProvider(apiHost = "https://api.openai.com")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiUrl()
        assertEquals("https://api.openai.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveApiUrl - host with trailing slash and v1 in path`() {
        val provider = createTestProvider(apiHost = "https://api.example.com/v1/chat")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiUrl()
        assertEquals("https://api.example.com/v1/chatchat/completions", result)
    }

    @Test
    fun `resolveApiUrl - exception returns null`() {
        every { ProviderStorage.getMergedProviders() } throws RuntimeException("Test exception")

        val result = ProviderConfigResolver.resolveApiUrl()
        assertNull(result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveApiKey()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveApiKey - provider with key returns key`() {
        val provider = createTestProvider(apiKey = "sk-my-secret-key")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiKey()
        assertEquals("sk-my-secret-key", result)
    }

    @Test
    fun `resolveApiKey - no enabled providers returns empty`() {
        every { ProviderStorage.getMergedProviders() } returns emptyList()
        every { mmkvMock.decodeString(ConfigStorage.KEY_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveApiKey()
        assertEquals("", result)
    }

    @Test
    fun `resolveApiKey - exception returns empty`() {
        every { ProviderStorage.getMergedProviders() } throws RuntimeException("Test exception")

        val result = ProviderConfigResolver.resolveApiKey()
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveModelName()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveModelName - provider with selected models returns first name`() {
        val provider = createTestProvider(selectedModels = listOf("gpt-4-turbo", "gpt-4"))
        every { ProviderStorage.getEnabledProviders() } returns listOf(provider)

        val result = ProviderConfigResolver.resolveModelName()
        assertEquals("gpt-4-turbo", result)
    }

    @Test
    fun `resolveModelName - no enabled providers returns empty`() {
        every { ProviderStorage.getEnabledProviders() } returns emptyList()

        val result = ProviderConfigResolver.resolveModelName()
        assertEquals("", result)
    }

    @Test
    fun `resolveModelName - exception returns empty`() {
        every { ProviderStorage.getEnabledProviders() } throws RuntimeException("Test exception")

        val result = ProviderConfigResolver.resolveModelName()
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  isAnyProviderConfigured()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `isAnyProviderConfigured - true when any enabled`() {
        every { ProviderStorage.isAnyProviderConfigured() } returns true

        assertTrue(ProviderConfigResolver.isAnyProviderConfigured())
    }

    @Test
    fun `isAnyProviderConfigured - false when none enabled`() {
        every { ProviderStorage.isAnyProviderConfigured() } returns false

        assertFalse(ProviderConfigResolver.isAnyProviderConfigured())
    }

    @Test
    fun `isAnyProviderConfigured - exception returns false`() {
        every { ProviderStorage.isAnyProviderConfigured() } throws RuntimeException("Test exception")

        assertFalse(ProviderConfigResolver.isAnyProviderConfigured())
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveVisionProvider()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveVisionProvider - exact vision model match returns provider`() {
        val providerA = createTestProvider(id = "v-a", name = "Vision A", selectedModels = listOf("gpt-4-vision"))
        val providerB = createTestProvider(id = "v-b", name = "Vision B", selectedModels = listOf("claude-vision"))
        every { ProviderStorage.getMergedProviders() } returns listOf(providerA, providerB)
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns listOf<LocalProviderConfig>(
            createTestProvider(id = "v-a", selectedModels = listOf("gpt-4-vision")),
            createTestProvider(id = "v-b", selectedModels = listOf("claude-vision"))
        )
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns "gpt-4-vision"

        val result = ProviderConfigResolver.resolveVisionProvider()
        assertNotNull(result)
        assertEquals("v-a", result!!.id)
    }

    @Test
    fun `resolveVisionProvider - no providers returns null`() {
        every { ProviderStorage.getMergedProviders() } returns emptyList()
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns emptyList()
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveVisionProvider()
        assertNull(result)
    }

    @Test
    fun `resolveVisionProvider - exception returns null`() {
        every { ProviderStorage.getMergedProviders() } throws RuntimeException("Test exception")

        val result = ProviderConfigResolver.resolveVisionProvider()
        assertNull(result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveVisionBaseUrl()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveVisionBaseUrl - from provider host`() {
        val provider = createTestProvider(apiHost = "https://vision.api.com/v1")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns listOf<LocalProviderConfig>(
            createTestProvider(selectedModels = listOf("gpt-4-vision"))
        )
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns "gpt-4-vision"

        val result = ProviderConfigResolver.resolveVisionBaseUrl()
        assertEquals("https://vision.api.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveVisionBaseUrl - from provider host without v1`() {
        val provider = createTestProvider(apiHost = "https://custom.vision.com")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns listOf<LocalProviderConfig>(
            createTestProvider(selectedModels = listOf("custom-model"))
        )
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns "custom-model"

        val result = ProviderConfigResolver.resolveVisionBaseUrl()
        assertEquals("https://custom.vision.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveVisionBaseUrl - fallback to VisionProviderFactory default`() {
        // No providers → resolveVisionProvider returns null
        every { ProviderStorage.getMergedProviders() } returns emptyList()
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns emptyList()
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns ""
        // readVisionProviderId() → "openai_compat" via default
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "openai_compat"

        val result = ProviderConfigResolver.resolveVisionBaseUrl()
        assertEquals("https://api.deepseek.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveVisionBaseUrl - fallback when REGISTERED_PROVIDERS missing entry`() {
        every { ProviderStorage.getMergedProviders() } returns emptyList()
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns emptyList()
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns ""
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "unknown_provider"

        val result = ProviderConfigResolver.resolveVisionBaseUrl()
        // Fallback to hardcoded default
        assertEquals("https://api.deepseek.com/v1/chat/completions", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveVisionApiKey()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveVisionApiKey - from provider api key`() {
        val provider = createTestProvider(apiKey = "sk-vision-key")
        every { ProviderStorage.getMergedProviders() } returns listOf(provider)
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns listOf<LocalProviderConfig>(
            createTestProvider(selectedModels = listOf("gpt-4-vision"))
        )
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns "gpt-4-vision"

        val result = ProviderConfigResolver.resolveVisionApiKey()
        assertEquals("sk-vision-key", result)
    }

    @Test
    fun `resolveVisionApiKey - no provider returns empty`() {
        every { ProviderStorage.getMergedProviders() } returns emptyList()
        every { ProviderStorage.getEnabledProvidersFromUserConfigs() } returns emptyList()
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_MODEL_NAME, any()) } returns ""

        val result = ProviderConfigResolver.resolveVisionApiKey()
        assertEquals("", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveVisionModelName()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveVisionModelName - from openai_compat default model`() {
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "openai_compat"

        val result = ProviderConfigResolver.resolveVisionModelName()
        assertEquals("deepseek-chat", result)
    }

    @Test
    fun `resolveVisionModelName - default fallback when provider not registered`() {
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "unknown"

        val result = ProviderConfigResolver.resolveVisionModelName()
        assertEquals("deepseek-chat", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveVisionDefaultBaseUrl()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveVisionDefaultBaseUrl - from openai_compat default`() {
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "openai_compat"

        val result = ProviderConfigResolver.resolveVisionDefaultBaseUrl()
        assertEquals("https://api.deepseek.com/v1/chat/completions", result)
    }

    @Test
    fun `resolveVisionDefaultBaseUrl - fallback when provider not registered`() {
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "unknown"

        val result = ProviderConfigResolver.resolveVisionDefaultBaseUrl()
        assertEquals("https://api.deepseek.com/v1/chat/completions", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resolveVisionDefaultModel()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveVisionDefaultModel - from openai_compat default`() {
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "openai_compat"

        val result = ProviderConfigResolver.resolveVisionDefaultModel()
        assertEquals("deepseek-chat", result)
    }

    @Test
    fun `resolveVisionDefaultModel - fallback when provider not registered`() {
        every { mmkvMock.decodeString(ConfigStorage.KEY_VISION_PROVIDER_ID, any()) } returns "unknown"

        val result = ProviderConfigResolver.resolveVisionDefaultModel()
        assertEquals("deepseek-chat", result)
    }
}
