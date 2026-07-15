package com.hwb.aianswerer.utils

import com.hwb.aianswerer.safelyInvoke
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * AppLog 全链路日志系统单元测试
 *
 * 覆盖：init / d / i / w / e / net / enter / leave / getLogFile / getLogDir / getLogFiles
 * 以及 7 天日志清理、废弃 API、append-only 模式。
 *
 * AppLog.init(dir: File) 不要求 Android Context 参数，但内部使用了
 * android.os.Build / android.util.Log（isReturnDefaultValues=true 可安全返回默认值），
 * 所有测试包裹 safelyInvoke 以与项目其他测试保持一致。
 */
class AppLogTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("applog_test_")
        safelyInvoke { AppLog.init(tempDir) }
    }

    @After
    fun tearDown() {
        if (::tempDir.isInitialized && tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `init创建日志文件和目录`() {
        val logFile = AppLog.getLogFile()
        assertNotNull("init 后 getLogFile() 不应为 null", logFile)
        assertTrue("日志文件应存在于磁盘", logFile!!.exists())
        assertTrue("日志文件名应以 aianswerer_ 开头", logFile.name.startsWith("aianswerer_"))
        assertTrue("日志文件名应以 .log 结尾", logFile.name.endsWith(".log"))
    }

    @Test
    fun `init设置日志目录`() {
        val logDir = AppLog.getLogDir()
        assertNotNull("init 后 getLogDir() 不应为 null", logDir)
        assertEquals("日志目录应等于传入的 dir", tempDir.absolutePath, logDir!!.absolutePath)
    }

    @Test
    fun `init写入起始标记到日志文件`() {
        safelyInvoke {
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 Log Start 标记", content.contains("AIAnswerer Log Start"))
            assertTrue("应包含版本信息", content.contains("Version:"))
            assertTrue("应包含设备信息", content.contains("Device:"))
        }
    }

    @Test
    fun `d写入DEBUG级别日志`() {
        safelyInvoke {
            AppLog.d("TestTag", "debug message")
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 DEBUG 级别标识", content.contains("[DEBUG]"))
            assertTrue("应包含 tag", content.contains("[TestTag]"))
            assertTrue("应包含消息正文", content.contains("debug message"))
        }
    }

    @Test
    fun `i写入INFO级别日志`() {
        safelyInvoke {
            AppLog.i("TestTag", "info message")
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 INFO 级别标识", content.contains("[INFO]"))
            assertTrue("应包含 tag", content.contains("[TestTag]"))
            assertTrue("应包含消息正文", content.contains("info message"))
        }
    }

    @Test
    fun `w写入WARN级别日志并可附带异常栈`() {
        safelyInvoke {
            val throwable = RuntimeException("test warn exception")
            AppLog.w("TestTag", "warn message", throwable)
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 WARN 级别标识", content.contains("[WARN]"))
            assertTrue("应包含异常类型", content.contains("RuntimeException"))
            assertTrue("应包含异常消息", content.contains("test warn exception"))
        }
    }

    @Test
    fun `w无异常参数写入WARN级别日志`() {
        safelyInvoke {
            AppLog.w("TestTag", "warn without throwable")
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 WARN 级别标识", content.contains("[WARN]"))
            assertTrue("应包含消息正文", content.contains("warn without throwable"))
        }
    }

    @Test
    fun `e写入ERROR级别日志并可附带异常栈`() {
        safelyInvoke {
            val throwable = IllegalStateException("test error exception")
            AppLog.e("TestTag", "error message", throwable)
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 ERROR 级别标识", content.contains("[ERROR]"))
            assertTrue("应包含异常类型", content.contains("IllegalStateException"))
            assertTrue("应包含异常消息", content.contains("test error exception"))
        }
    }

    @Test
    fun `e无异常参数写入ERROR级别日志`() {
        safelyInvoke {
            AppLog.e("TestTag", "error without throwable")
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 ERROR 级别标识", content.contains("[ERROR]"))
            assertTrue("应包含消息正文", content.contains("error without throwable"))
        }
    }

    @Test
    fun `net写入NET级别网络追踪日志`() {
        safelyInvoke {
            AppLog.net("HttpTag", "POST https://api.example.com/v1/chat")
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 NET 级别标识", content.contains("[NET]"))
            assertTrue("应包含 tag", content.contains("[HttpTag]"))
            assertTrue("应包含请求信息", content.contains("POST https://api.example.com/v1/chat"))
        }
    }

    @Test
    fun `enter写入TRACE级别函数进入日志带右箭头`() {
        safelyInvoke {
            AppLog.enter("FuncTag", "fetchAnswer")
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 TRACE 级别标识", content.contains("[TRACE]"))
            assertTrue("应包含右箭头 →", content.contains("→"))
            assertTrue("应包含函数名", content.contains("fetchAnswer"))
        }
    }

    @Test
    fun `leave写入TRACE级别函数离开日志含耗时和左箭头`() {
        safelyInvoke {
            val startMs = System.currentTimeMillis()
            Thread.sleep(5)
            AppLog.leave("FuncTag", "fetchAnswer", startMs)
            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 TRACE 级别标识", content.contains("[TRACE]"))
            assertTrue("应包含左箭头 ←", content.contains("←"))
            assertTrue("应包含函数名", content.contains("fetchAnswer"))
            assertTrue("应包含耗时单位 ms", content.contains("ms"))
        }
    }

    @Test
    fun `getLogFiles过滤aianswerer日志并按修改时间降序排列`() {
        safelyInvoke {
            File(tempDir, "aianswerer_1000.log").apply {
                createNewFile(); setLastModified(1000)
            }
            File(tempDir, "aianswerer_3000.log").apply {
                createNewFile(); setLastModified(3000)
            }
            File(tempDir, "aianswerer_2000.log").apply {
                createNewFile(); setLastModified(2000)
            }
            File(tempDir, "other.log").apply { createNewFile() }
            File(tempDir, "aianswerer_test.txt").apply { createNewFile() }

            val files = AppLog.getLogFiles()
            assertTrue("应返回至少 3 个匹配文件", files.size >= 3)

            for (i in 0 until files.size - 1) {
                assertTrue(
                    "文件应按 lastModified 降序排列",
                    files[i].lastModified() >= files[i + 1].lastModified()
                )
            }

            assertFalse("应排除 other.log", files.any { it.name == "other.log" })
            assertFalse("应排除 aianswerer_test.txt", files.any { it.name == "aianswerer_test.txt" })
        }
    }

    @Test
    fun `getLogFile和getLogDir在init后非null`() {
        assertNotNull("getLogFile() 不应为 null", AppLog.getLogFile())
        assertNotNull("getLogDir() 不应为 null", AppLog.getLogDir())
    }

    @Test
    fun `init自动清理7天前的旧日志文件`() {
        safelyInvoke {
            val cleanDir = createTempDir("applog_cleanup_")
            try {
                val eightDaysAgo = System.currentTimeMillis() - 8 * 24 * 3600_000L
                val oneDayAgo = System.currentTimeMillis() - 1 * 24 * 3600_000L

                val oldFile = File(cleanDir, "aianswerer_old.log").apply {
                    createNewFile(); setLastModified(eightDaysAgo)
                }
                val recentFile = File(cleanDir, "aianswerer_recent.log").apply {
                    createNewFile(); setLastModified(oneDayAgo)
                }

                AppLog.init(cleanDir)

                assertFalse("7天前的旧日志文件应被删除", oldFile.exists())
                assertTrue("7天内的日志文件应保留", recentFile.exists())
            } finally {
                cleanDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `init不移除非aianswerer前缀的文件`() {
        safelyInvoke {
            val cleanDir = createTempDir("applog_other_")
            try {
                val eightDaysAgo = System.currentTimeMillis() - 8 * 24 * 3600_000L
                val otherFile = File(cleanDir, "crash.log").apply {
                    createNewFile(); setLastModified(eightDaysAgo)
                }

                AppLog.init(cleanDir)

                assertTrue("非 aianswerer_ 前缀的文件不应被删除", otherFile.exists())
            } finally {
                cleanDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `日志文件是追加写入多次调用内容不覆盖`() {
        safelyInvoke {
            AppLog.d("T", "first")
            AppLog.i("T", "second")
            AppLog.e("T", "third")

            val content = AppLog.getLogFile()!!.readText()
            assertTrue("应包含 'first'", content.contains("first"))
            assertTrue("应包含 'second'", content.contains("second"))
            assertTrue("应包含 'third'", content.contains("third"))

            val idxFirst = content.indexOf("first")
            val idxThird = content.indexOf("third")
            assertTrue("'first' 应在 'third' 之前写入", idxFirst >= 0 && idxThird >= 0 && idxFirst < idxThird)
        }
    }

    @Test
    fun `废弃的d方法委托到带APP标签的版本`() {
        safelyInvoke {
            val tagLessDir = createTempDir("applog_depr_d_")
            try {
                AppLog.init(tagLessDir)
                AppLog.d("deprecated debug call")
                val content = AppLog.getLogFile()!!.readText()
                assertTrue("应使用 APP tag", content.contains("[APP]"))
                assertTrue("应包含 DEBUG 级别", content.contains("[DEBUG]"))
                assertTrue("应包含消息", content.contains("deprecated debug call"))
            } finally {
                tagLessDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `废弃的i方法委托到带APP标签的版本`() {
        safelyInvoke {
            val tagLessDir = createTempDir("applog_depr_i_")
            try {
                AppLog.init(tagLessDir)
                AppLog.i("deprecated info call")
                val content = AppLog.getLogFile()!!.readText()
                assertTrue("应使用 APP tag", content.contains("[APP]"))
                assertTrue("应包含 INFO 级别", content.contains("[INFO]"))
                assertTrue("应包含消息", content.contains("deprecated info call"))
            } finally {
                tagLessDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `废弃的w方法委托到带APP标签的版本`() {
        safelyInvoke {
            val tagLessDir = createTempDir("applog_depr_w_")
            try {
                AppLog.init(tagLessDir)
                val throwable = IllegalArgumentException("deprecated warn")
                AppLog.w("deprecated warn call", throwable)
                val content = AppLog.getLogFile()!!.readText()
                assertTrue("应使用 APP tag", content.contains("[APP]"))
                assertTrue("应包含 WARN 级别", content.contains("[WARN]"))
                assertTrue("应包含消息", content.contains("deprecated warn call"))
                assertTrue("应包含异常信息", content.contains("IllegalArgumentException"))
            } finally {
                tagLessDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `废弃的e方法委托到带APP标签的版本`() {
        safelyInvoke {
            val tagLessDir = createTempDir("applog_depr_e_")
            try {
                AppLog.init(tagLessDir)
                val throwable = IllegalStateException("deprecated error")
                AppLog.e("deprecated error call", throwable)
                val content = AppLog.getLogFile()!!.readText()
                assertTrue("应使用 APP tag", content.contains("[APP]"))
                assertTrue("应包含 ERROR 级别", content.contains("[ERROR]"))
                assertTrue("应包含消息", content.contains("deprecated error call"))
                assertTrue("应包含异常信息", content.contains("IllegalStateException"))
            } finally {
                tagLessDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `未init情况下getLogFiles不抛异常`() {
        safelyInvoke {
            val files = AppLog.getLogFiles()
            assertNotNull("getLogFiles 不应返回 null", files)
        }
    }
}
