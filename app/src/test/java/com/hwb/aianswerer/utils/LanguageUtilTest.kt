package com.hwb.aianswerer.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import com.hwb.aianswerer.config.AppConfig
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

class LanguageUtilTest {

    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var config: Configuration
    private var originalLocale: Locale? = null

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        mockkObject(AppConfig)
        config = Configuration()
        mockResources = mockk(relaxed = true)
        every { mockResources.configuration } returns config
        every { mockResources.displayMetrics } returns DisplayMetrics()
        every { mockResources.updateConfiguration(any(), any()) } just Runs
        mockContext = mockk(relaxed = true)
        every { mockContext.resources } returns mockResources
    }

    @After
    fun tearDown() {
        unmockkObject(AppConfig)
        originalLocale?.let { Locale.setDefault(it) }
    }

    // ── applyLanguage ──

    @Test
    fun `applyLanguage zh - calls AppConfig saveLanguage with zh`() {
        every { AppConfig.saveLanguage(any()) } just Runs

        LanguageUtil.applyLanguage(mockContext, "zh")

        verify(exactly = 1) { AppConfig.saveLanguage("zh") }
    }

    @Test
    fun `applyLanguage en - calls AppConfig saveLanguage with en`() {
        every { AppConfig.saveLanguage(any()) } just Runs

        LanguageUtil.applyLanguage(mockContext, "en")

        verify(exactly = 1) { AppConfig.saveLanguage("en") }
    }

    // ── getCurrentLanguage ──

    @Test
    fun `getCurrentLanguage - delegates to AppConfig getLanguage`() {
        every { AppConfig.getLanguage() } returns "en"

        assertEquals("en", LanguageUtil.getCurrentLanguage())
    }

    // ── updateConfigurationContext (via reflection) ──

    @Test
    fun `updateConfigurationContext zh - returns context with Chinese locale`() {
        val configSlot = slot<Configuration>()
        every { mockResources.updateConfiguration(capture(configSlot), any()) } just Runs

        val result = invokeUpdateConfiguration(mockContext, "zh")

        assertSame(mockContext, result)
        assertEquals(Locale.SIMPLIFIED_CHINESE, configSlot.captured.locale)
    }

    @Test
    fun `updateConfigurationContext en - returns context with English locale`() {
        val configSlot = slot<Configuration>()
        every { mockResources.updateConfiguration(capture(configSlot), any()) } just Runs

        val result = invokeUpdateConfiguration(mockContext, "en")

        assertSame(mockContext, result)
        assertEquals(Locale.ENGLISH, configSlot.captured.locale)
    }

    @Test
    fun `updateConfigurationContext unknown code - falls back to English locale`() {
        val configSlot = slot<Configuration>()
        every { mockResources.updateConfiguration(capture(configSlot), any()) } just Runs

        val result = invokeUpdateConfiguration(mockContext, "xx")

        assertSame(mockContext, result)
        assertEquals(Locale.ENGLISH, configSlot.captured.locale)
    }

    // ── restartActivity ──

    @Test
    fun `restartActivity - finishes and starts activity with fade animation`() {
        val activity = mockk<Activity>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { activity.intent } returns intent

        LanguageUtil.restartActivity(activity)

        verify(exactly = 1) { activity.finish() }
        verify(exactly = 1) { activity.startActivity(intent) }
        verify(exactly = 1) {
            activity.overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }
    }

    // ── language constants ──

    @Test
    fun `language constants - ZH equals zh`() {
        assertEquals("zh", AppConfig.LANGUAGE_ZH)
    }

    @Test
    fun `language constants - EN equals en`() {
        assertEquals("en", AppConfig.LANGUAGE_EN)
    }

    // ── helper ──

    private fun invokeUpdateConfiguration(context: Context, languageCode: String): Context {
        val method = LanguageUtil::class.java.getDeclaredMethod(
            "updateConfigurationContext",
            Context::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(LanguageUtil, context, languageCode) as Context
    }
}
