package com.hwb.aianswerer.config

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.*
import org.junit.Test

/**
 * 组合测试 —— 覆盖 VisionConfig / SearchConfig / UIConfig / CaptureConfig
 *
 * 这四个 config 均为 internal object，方法全部通过 ConfigStorage.requireMmkv()
 * 存取数据，在纯 JVM 中不可用。所有测试使用 safelyInvoke 包裹，
 * 在 Android 运行时缺失时跳过。
 *
 * 每个 config 至少包含 2 个测试（1 个默认值校验 + 1 个边界/保存-读取验证）。
 */
class FeatureConfigsTest {

    // ─────────────────────────────────────────────
    // VisionConfig
    // ─────────────────────────────────────────────

    @Test
    fun `VisionConfig默认ProviderId为openai_compat`() {
        safelyInvoke {
            assertEquals("openai_compat", VisionConfig.getVisionProviderId())
        }
    }

    @Test
    fun `VisionConfig默认Temperature为0_0`() {
        safelyInvoke {
            assertEquals(0.0, VisionConfig.getVisionTemperature(), 0.01)
        }
    }

    @Test
    fun `VisionConfig默认MaxTokens为4096`() {
        safelyInvoke {
            assertEquals(4096, VisionConfig.getVisionMaxTokens())
        }
    }

    @Test
    fun `VisionConfig默认JsonMode为true`() {
        safelyInvoke {
            assertTrue(VisionConfig.getVisionJsonMode())
        }
    }

    @Test
    fun `VisionConfig保存ProviderId后读取应一致`() {
        safelyInvoke {
            VisionConfig.saveVisionProviderId("azure_openai")
            assertEquals("azure_openai", VisionConfig.getVisionProviderId())
            // 恢复默认
            VisionConfig.saveVisionProviderId("openai_compat")
        }
    }

    @Test
    fun `VisionConfig保存Temperature后读取应一致`() {
        safelyInvoke {
            VisionConfig.saveVisionTemperature(0.7)
            assertEquals(0.7, VisionConfig.getVisionTemperature(), 0.01)
            VisionConfig.saveVisionTemperature(1.5)
            assertEquals(1.5, VisionConfig.getVisionTemperature(), 0.01)
            VisionConfig.saveVisionTemperature(0.0)
            assertEquals(0.0, VisionConfig.getVisionTemperature(), 0.01)
        }
    }

    @Test
    fun `VisionConfig保存MaxTokens后读取应一致`() {
        safelyInvoke {
            VisionConfig.saveVisionMaxTokens(2048)
            assertEquals(2048, VisionConfig.getVisionMaxTokens())
            VisionConfig.saveVisionMaxTokens(8192)
            assertEquals(8192, VisionConfig.getVisionMaxTokens())
            VisionConfig.saveVisionMaxTokens(4096)
            assertEquals(4096, VisionConfig.getVisionMaxTokens())
        }
    }

    @Test
    fun `VisionConfig保存JsonMode后读取应一致`() {
        safelyInvoke {
            VisionConfig.saveVisionJsonMode(false)
            assertFalse(VisionConfig.getVisionJsonMode())
            VisionConfig.saveVisionJsonMode(true)
            assertTrue(VisionConfig.getVisionJsonMode())
        }
    }

    // ─────────────────────────────────────────────
    // SearchConfig
    // ─────────────────────────────────────────────


    @Test
    fun `SearchConfig默认正则过滤已启用`() {
        safelyInvoke {
            assertTrue(SearchConfig.isRegexFilterEnabled())
        }
    }

    @Test
    fun `SearchConfig默认Web搜索Provider为空字符串`() {
        safelyInvoke {
            assertEquals("", SearchConfig.getWebSearchProvider())
        }
    }

    @Test
    fun `SearchConfig保存Web搜索Provider后读取应一致`() {
        safelyInvoke {
            SearchConfig.saveWebSearchProvider("tavily")
            assertEquals("tavily", SearchConfig.getWebSearchProvider())
            SearchConfig.saveWebSearchProvider("")
            assertEquals("", SearchConfig.getWebSearchProvider())
        }
    }

    @Test
    fun `SearchConfig保存正则过滤状态后读取应一致`() {
        safelyInvoke {
            SearchConfig.saveRegexFilterEnabled(false)
            assertFalse(SearchConfig.isRegexFilterEnabled())
            SearchConfig.saveRegexFilterEnabled(true)
            assertTrue(SearchConfig.isRegexFilterEnabled())
        }
    }

    // ─────────────────────────────────────────────
    // UIConfig
    // ─────────────────────────────────────────────

    @Test
    fun `UIConfig默认语言为中文`() {
        safelyInvoke {
            assertEquals("zh", UIConfig.getLanguage())
        }
    }

    @Test
    fun `UIConfig默认暗色模式为跟随系统`() {
        safelyInvoke {
            assertEquals(0, UIConfig.getDarkMode())
        }
    }

    @Test
    fun `UIConfig默认快捷按钮布局为横向排列`() {
        safelyInvoke {
            assertEquals("horizontal", UIConfig.getQuickButtonLayout())
        }
    }

    @Test
    fun `UIConfig默认悬浮按钮大小为40`() {
        safelyInvoke {
            assertEquals(40, UIConfig.getFloatButtonSize())
        }
    }

    @Test
    fun `UIConfig默认悬浮按钮透明度为0_9`() {
        safelyInvoke {
            assertEquals(0.9f, UIConfig.getFloatButtonAlpha(), 0.01f)
        }
    }

    @Test
    fun `UIConfig默认卡片透明度为0_85`() {
        safelyInvoke {
            assertEquals(0.85f, UIConfig.getFloatCardAlpha(), 0.01f)
        }
    }

    @Test
    fun `UIConfig首次启动默认为true`() {
        safelyInvoke {
            assertTrue(UIConfig.isFirstLaunch())
        }
    }

    @Test
    fun `UIConfig默认输出语言为中文`() {
        safelyInvoke {
            assertEquals("中文", UIConfig.getOutputLanguage())
        }
    }

    @Test
    fun `UIConfig悬浮按钮大小超出范围应限制在32至80`() {
        safelyInvoke {
            UIConfig.saveFloatButtonSize(10)   // 过小
            assertEquals(32, UIConfig.getFloatButtonSize())
            UIConfig.saveFloatButtonSize(100)  // 过大
            assertEquals(80, UIConfig.getFloatButtonSize())
            UIConfig.saveFloatButtonSize(50)   // 合法值
            assertEquals(50, UIConfig.getFloatButtonSize())
        }
    }

    @Test
    fun `UIConfig悬浮按钮透明度超出范围应限制在0_1至1_0`() {
        safelyInvoke {
            UIConfig.saveFloatButtonAlpha(0.0f) // 过小
            assertEquals(0.1f, UIConfig.getFloatButtonAlpha(), 0.01f)
            UIConfig.saveFloatButtonAlpha(2.0f) // 过大
            assertEquals(1.0f, UIConfig.getFloatButtonAlpha(), 0.01f)
            UIConfig.saveFloatButtonAlpha(0.5f) // 合法值
            assertEquals(0.5f, UIConfig.getFloatButtonAlpha(), 0.01f)
        }
    }

    @Test
    fun `UIConfig卡片透明度超出范围应限制在0_1至1_0`() {
        safelyInvoke {
            UIConfig.saveFloatCardAlpha(0.0f)
            assertEquals(0.1f, UIConfig.getFloatCardAlpha(), 0.01f)
            UIConfig.saveFloatCardAlpha(1.5f)
            assertEquals(1.0f, UIConfig.getFloatCardAlpha(), 0.01f)
        }
    }

    @Test
    fun `UIConfig语言保存和读取应一致`() {
        safelyInvoke {
            UIConfig.saveLanguage("en")
            assertEquals("en", UIConfig.getLanguage())
            UIConfig.saveLanguage("zh")
            assertEquals("zh", UIConfig.getLanguage())
        }
    }

    @Test
    fun `UIConfig暗色模式保存和读取应一致`() {
        safelyInvoke {
            UIConfig.saveDarkMode(2)
            assertEquals(2, UIConfig.getDarkMode())
            UIConfig.saveDarkMode(1)
            assertEquals(1, UIConfig.getDarkMode())
            UIConfig.saveDarkMode(0)
            assertEquals(0, UIConfig.getDarkMode())
        }
    }

    @Test
    fun `UIConfig快捷按钮布局保存和读取应一致`() {
        safelyInvoke {
            UIConfig.saveQuickButtonLayout("arc")
            assertEquals("arc", UIConfig.getQuickButtonLayout())
            UIConfig.saveQuickButtonLayout("horizontal")
            assertEquals("horizontal", UIConfig.getQuickButtonLayout())
        }
    }

    @Test
    fun `UIConfig首次启动标记完成后应返回false`() {
        safelyInvoke {
            UIConfig.setFirstLaunchComplete()
            assertFalse(UIConfig.isFirstLaunch())
        }
    }

    // ─────────────────────────────────────────────
    // CaptureConfig
    // ─────────────────────────────────────────────

    @Test
    fun `CaptureConfig默认题型为单选题`() {
        safelyInvoke {
            assertEquals(setOf("单选题"), CaptureConfig.getQuestionTypes())
        }
    }

    @Test
    fun `CaptureConfig默认答题卡片显示题目为true`() {
        safelyInvoke {
            assertTrue(CaptureConfig.getShowAnswerCardQuestion())
        }
    }

    @Test
    fun `CaptureConfig默认答题卡片显示选项为true`() {
        safelyInvoke {
            assertTrue(CaptureConfig.getShowAnswerCardOptions())
        }
    }

    @Test
    fun `CaptureConfig默认识别模式为全屏`() {
        safelyInvoke {
            assertEquals("full", CaptureConfig.getCropMode())
        }
    }

    @Test
    fun `CaptureConfig默认采集模式为截图`() {
        safelyInvoke {
            assertEquals("screenshot", CaptureConfig.getCaptureMode())
        }
    }

    @Test
    fun `CaptureConfig默认非无障碍模式`() {
        safelyInvoke {
            assertFalse(CaptureConfig.isAccessibilityCaptureMode())
        }
    }

    @Test
    fun `CaptureConfig默认自动提交为true`() {
        safelyInvoke {
            assertTrue(CaptureConfig.getAutoSubmit())
        }
    }

    @Test
    fun `CaptureConfig默认自动复制为false`() {
        safelyInvoke {
            assertFalse(CaptureConfig.getAutoCopy())
        }
    }

    @Test
    fun `CaptureConfig默认隐身模式为true`() {
        safelyInvoke {
            assertTrue(CaptureConfig.isStealthModeEnabled())
        }
    }

    @Test
    fun `CaptureConfig题型保存和读取应一致`() {
        safelyInvoke {
            val types = setOf("单选题", "多选题", "判断题")
            CaptureConfig.saveQuestionTypes(types)
            assertEquals(types, CaptureConfig.getQuestionTypes())
        }
    }

    @Test
    fun `CaptureConfig空题型保存后应返回默认单选题`() {
        safelyInvoke {
            CaptureConfig.saveQuestionTypes(emptySet())
            // 内部保存空串 → isBlank → 返回 setOf("单选题")
            assertEquals(setOf("单选题"), CaptureConfig.getQuestionTypes())
        }
    }

    @Test
    fun `CaptureConfig答题卡片显示设置保存后应一致`() {
        safelyInvoke {
            CaptureConfig.saveShowAnswerCardQuestion(false)
            assertFalse(CaptureConfig.getShowAnswerCardQuestion())
            CaptureConfig.saveShowAnswerCardQuestion(true)
            assertTrue(CaptureConfig.getShowAnswerCardQuestion())

            CaptureConfig.saveShowAnswerCardOptions(false)
            assertFalse(CaptureConfig.getShowAnswerCardOptions())
            CaptureConfig.saveShowAnswerCardOptions(true)
            assertTrue(CaptureConfig.getShowAnswerCardOptions())
        }
    }

    @Test
    fun `CaptureConfig截图识别模式保存后应一致`() {
        safelyInvoke {
            CaptureConfig.saveCropMode("once")
            assertEquals("once", CaptureConfig.getCropMode())
            CaptureConfig.saveCropMode("full")
            assertEquals("full", CaptureConfig.getCropMode())
        }
    }

    @Test
    fun `CaptureConfig采集模式保存后无障碍判断应正确`() {
        safelyInvoke {
            CaptureConfig.saveCaptureMode("accessibility")
            assertTrue(CaptureConfig.isAccessibilityCaptureMode())
            CaptureConfig.saveCaptureMode("screenshot")
            assertFalse(CaptureConfig.isAccessibilityCaptureMode())
        }
    }

    @Test
    fun `CaptureConfig自动提交和自动复制保存后应一致`() {
        safelyInvoke {
            CaptureConfig.saveAutoSubmit(false)
            assertFalse(CaptureConfig.getAutoSubmit())
            CaptureConfig.saveAutoSubmit(true)
            assertTrue(CaptureConfig.getAutoSubmit())

            CaptureConfig.saveAutoCopy(true)
            assertTrue(CaptureConfig.getAutoCopy())
            CaptureConfig.saveAutoCopy(false)
            assertFalse(CaptureConfig.getAutoCopy())
        }
    }

    @Test
    fun `CaptureConfig隐身模式保存后应一致`() {
        safelyInvoke {
            CaptureConfig.saveStealthMode(false)
            assertFalse(CaptureConfig.isStealthModeEnabled())
            CaptureConfig.saveStealthMode(true)
            assertTrue(CaptureConfig.isStealthModeEnabled())
        }
    }

    // ─────────────────────────────────────────────
    // WebSearch config 集成
    // ─────────────────────────────────────────────

    @Test
    fun `正则过滤开关保存后读取应一致`() {
        safelyInvoke {
            SearchConfig.saveRegexFilterEnabled(false)
            assertFalse(SearchConfig.isRegexFilterEnabled())
            SearchConfig.saveRegexFilterEnabled(true)
            assertTrue(SearchConfig.isRegexFilterEnabled())
        }
    }
}
