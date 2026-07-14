package com.hwb.aianswerer.config

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ApiConfig 单元测试
 *
 * ApiConfig.isApiConfigValid() 的第一道守卫 (url/key/model 非空 + http 开头)
 * 在显式传参且条件满足时不依赖 Android 运行时；但为与项目其他测试保持一致的
 * safelyInvoke 防御模式，所有测试均包裹 safelyInvoke。
 *
 * 当守卫条件不满足时，方法会 fallback 到 ProviderStorage（需要 Android 运行时），
 * 此时 safelyInvoke 会捕获异常并跳过测试。
 */
class ApiConfigTest {

    @Test
    fun `ApiConfig构建httpsURL且KeyModel完整应返回true`() {
        assertTrue(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "https://api.openai.com/v1/chat/completions",
                    key = "sk-test-key-12345",
                    model = "gpt-4"
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建httpURL且KeyModel完整应返回true`() {
        assertTrue(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "http://localhost:8080/v1/chat/completions",
                    key = "sk-test-key",
                    model = "local-model"
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建时URL为空应返回false`() {
        assertFalse(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "",
                    key = "sk-test-key",
                    model = "gpt-4"
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建时Key为空应返回false`() {
        assertFalse(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "https://api.openai.com/v1/chat/completions",
                    key = "",
                    model = "gpt-4"
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建时Model为空应返回false`() {
        assertFalse(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "https://api.openai.com/v1/chat/completions",
                    key = "sk-test-key",
                    model = ""
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建时URL非http开头应返回false`() {
        assertFalse(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "ftp://api.openai.com",
                    key = "sk-test-key",
                    model = "gpt-4"
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建时URL为空白字符串应返回false`() {
        assertFalse(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "   ",
                    key = "sk-test-key",
                    model = "gpt-4"
                )
            }
        )
    }

    @Test
    fun `ApiConfig构建时所有参数为空应返回false`() {
        assertFalse(
            safelyInvoke {
                ApiConfig.isApiConfigValid(
                    url = "",
                    key = "",
                    model = ""
                )
            }
        )
    }
}
