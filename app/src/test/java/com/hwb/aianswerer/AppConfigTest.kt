package com.hwb.aianswerer

import com.hwb.aianswerer.config.AppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppConfig 单元测试
 * 测试配置验证逻辑
 *
 * NOTE: isApiConfigValid() falls through to ProviderStorage.isAnyProviderConfigured()
 * when the primary guard (valid http URL + non-blank key/model) fails.
 * ProviderStorage requires Android MMKV runtime which is unavailable in JVM tests.
 * Tests that hit the fallback path use safelyInvokeIsApiConfigValid() to skip
 * gracefully when the Android runtime is absent (via JUnit Assume).
 */
class AppConfigTest {


    @Test
    fun `isApiConfigValid - 完整配置返回true`() {
        assertTrue(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "https://api.openai.com/v1/chat/completions",
                key = "sk-test-key",
                model = "gpt-4"
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - 空URL返回false`() {
        assertFalse(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "",
                key = "sk-test-key",
                model = "gpt-4"
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - 空Key返回false`() {
        assertFalse(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "https://api.openai.com/v1/chat/completions",
                key = "",
                model = "gpt-4"
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - 空Model返回false`() {
        assertFalse(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "https://api.openai.com/v1/chat/completions",
                key = "sk-test-key",
                model = ""
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - 非http开头返回false`() {
        assertFalse(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "ftp://api.openai.com",
                key = "sk-test-key",
                model = "gpt-4"
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - 空白URL返回false`() {
        assertFalse(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "   ",
                key = "sk-test-key",
                model = "gpt-4"
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - https开头返回true`() {
        assertTrue(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "https://api.deepseek.com/v1/chat/completions",
                key = "sk-test-key",
                model = "deepseek-v3"
            ) }
        )
    }

    @Test
    fun `isApiConfigValid - http开头返回true`() {
        assertTrue(
            safelyInvoke { AppConfig.isApiConfigValid(
                url = "http://localhost:8080/v1/chat/completions",
                key = "sk-test-key",
                model = "local-model"
            ) }
        )
    }
}
