package com.hwb.aianswerer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 源码卫生回归：防止明文 API Key 再次被写入 logcat。
 *
 * 背景：b894d36 修复了多处明文 key 日志，WebSearchPage.kt:228 的
 * "onSave: apiKey='...'" 日志漏改（M2），本测试以"源码断言"锁定该不变式。
 */
class WebSearchLogHygieneTest {

    private fun mainSource(path: String): String {
        // Gradle 单测工作目录为模块根（app/），源码位于 src/main/...
        val candidates = listOf("src/main/java/com/hwb/aianswerer/$path", "app/src/main/java/com/hwb/aianswerer/$path")
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("找不到源码: $path (尝试过 $candidates)")
        return file.readText()
    }

    @Test
    fun `no Log call interpolates apiKey in WebSearchPage`() {
        val src = mainSource("ui/pages/WebSearchPage.kt")
        val logWithApiKey = Regex("""Log\.\w+\(\s*"[^"]*"[^)]*apiKey""")
        assertFalse(
            "WebSearchPage 中不允许出现拼接 apiKey 的日志调用: ${logWithApiKey.find(src)?.value}",
            logWithApiKey.containsMatchIn(src)
        )
    }

    @Test
    fun `no plaintext apiKey log anywhere in main search sources`() {
        val candidates = listOf(
            "api/search/WebSearchToolExecutor.kt",
            "ui/pages/WebSearchPage.kt",
            "api/search/WebSearchProviders.kt"
        )
        val leaky = candidates.filter { path ->
            val src = mainSource(path)
            Regex("""Log\.\w+\([^)]*apiKey""").containsMatchIn(src)
        }
        assertTrue("以下文件仍存在明文 key 日志: $leaky", leaky.isEmpty())
    }
}
