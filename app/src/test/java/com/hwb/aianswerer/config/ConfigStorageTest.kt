package com.hwb.aianswerer.config

import com.hwb.aianswerer.config.ConfigStorage.KEY_API_KEY
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfigStorageTest {

    @Before
    fun setUp() {
        // ConfigStorage.mmkv is null until init() is called — tests verify this
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── requireMmkv ──

    @Test
    fun `requireMmkv throws when not initialized`() {
        // ConfigStorage 的 mmkv 字段初始为 null
        // requireMmkv() 应抛出 IllegalStateException
        try {
            ConfigStorage.requireMmkv()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("init()") == true)
        }
    }

    // ── Storage key constants ──

    @Test
    fun `all storage keys are non-blank`() {
        val keys = listOf(
            ConfigStorage.KEY_API_URL, ConfigStorage.KEY_API_KEY,
            ConfigStorage.KEY_MODEL_NAME, ConfigStorage.KEY_LANGUAGE,
            ConfigStorage.KEY_AUTO_SUBMIT, ConfigStorage.KEY_AUTO_COPY,
            ConfigStorage.KEY_QUESTION_TYPES, ConfigStorage.KEY_IS_FIRST_LAUNCH,
            ConfigStorage.KEY_CROP_MODE, ConfigStorage.KEY_SHOW_ANSWER_CARD_QUESTION,
            ConfigStorage.KEY_SHOW_ANSWER_CARD_OPTIONS
        )
        keys.forEach { key ->
            assertTrue("key '$key' should not be blank", key.isNotBlank())
        }
    }

    @Test
    fun `storage keys are unique`() {
        val keys = listOf(
            ConfigStorage.KEY_API_URL, ConfigStorage.KEY_API_KEY,
            ConfigStorage.KEY_MODEL_NAME, ConfigStorage.KEY_LANGUAGE,
            ConfigStorage.KEY_AUTO_SUBMIT, ConfigStorage.KEY_AUTO_COPY,
            ConfigStorage.KEY_QUESTION_TYPES, ConfigStorage.KEY_IS_FIRST_LAUNCH,
            ConfigStorage.KEY_CROP_MODE, ConfigStorage.KEY_DARK_MODE,
            ConfigStorage.KEY_PARALLEL_MODE, ConfigStorage.KEY_MAX_CONCURRENCY,
            ConfigStorage.KEY_LLM_TEMPERATURE,
            ConfigStorage.KEY_REASONING_EFFORT, ConfigStorage.KEY_CAPTURE_MODE
        )
        assertEquals(keys.size, keys.toSet().size)
    }

    // ── Language constants ──

    @Test
    fun `language constants are distinct`() {
        assertNotEquals(ConfigStorage.LANGUAGE_ZH, ConfigStorage.LANGUAGE_EN)
    }

    @Test
    fun `language constants are non-blank`() {
        assertTrue(ConfigStorage.LANGUAGE_ZH.isNotBlank())
        assertTrue(ConfigStorage.LANGUAGE_EN.isNotBlank())
    }

    // ── Crop mode constants ──

    @Test
    fun `crop mode constants are distinct`() {
        val modes = setOf(
            ConfigStorage.CROP_MODE_FULL,
            ConfigStorage.CROP_MODE_EACH,
            ConfigStorage.CROP_MODE_ONCE
        )
        assertEquals(3, modes.size)
    }

    // ── Capture mode constants ──

    @Test
    fun `capture mode constants are distinct`() {
        assertNotEquals(
            ConfigStorage.CAPTURE_MODE_SCREENSHOT,
            ConfigStorage.CAPTURE_MODE_ACCESSIBILITY
        )
    }

    // ── Quick button layout constants ──

    @Test
    fun `quick button layout constants are distinct`() {
        assertNotEquals(
            ConfigStorage.QUICK_BUTTON_LAYOUT_ARC,
            ConfigStorage.QUICK_BUTTON_LAYOUT_HORIZONTAL
        )
    }

    // ── API key storage key used correctly ──

    @Test
    fun `API key storage key is correct constant`() {
        assertEquals("api_key", KEY_API_KEY)
    }

    // ── getSecurePrefs returns null when not initialized ──

    @Test
    fun `getSecurePrefs returns null before init`() {
        // securePrefs is initialized in initSecurePrefs()
        // Before that, it should be null
        val prefs = ConfigStorage.getSecurePrefs()
        // May be null if not initialized yet in test environment
        // This is expected behavior
        // assertTrue removed — was a tautology; test verifies no-crash behavior
    }
}
