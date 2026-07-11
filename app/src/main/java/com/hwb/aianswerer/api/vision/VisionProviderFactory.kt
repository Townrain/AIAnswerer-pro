package com.hwb.aianswerer.api.vision

import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.AppLog

/**
 * 视觉模型 Provider 工厂
 *
 * 职责：
 *   1. 注册内置 Provider 类型
 *   2. 根据用户配置创建对应的 Provider 实例
 *   3. 提供可用的 Provider 列表供 UI 展示
 */
object VisionProviderFactory {

    /**
     * 内置 Provider 注册表
     * key = providerId, value = ProviderMeta
     */
    val REGISTERED_PROVIDERS = mapOf(
        "openai_compat" to ProviderMeta(
            displayName = "OpenAI 兼容接口",
            description = "支持 DeepSeek、OpenAI、硅基流动、Ollama 等\nAPI 格式: /v1/chat/completions",
            defaultBaseUrl = "https://api.deepseek.com/v1/chat/completions",
            defaultModel = "deepseek-chat"
        ),
        "custom" to ProviderMeta(
            displayName = "自定义接口",
            description = "任意 OpenAI 兼容的自定义 API 端点\n格式: /v1/chat/completions",
            defaultBaseUrl = "",
            defaultModel = ""
        )
    )

    /**
     * 根据当前 AppConfig 创建 VisionProvider
     * @return VisionProvider 或 null（如果视觉过滤未启用）
     */
    fun create(): VisionProvider? {
        if (!AppConfig.isVisionEnabled()) return null

        val providerId = AppConfig.getVisionProviderId()

        return when (providerId) {
            "openai_compat", "custom" -> {
                val config = OpenAIVisionConfig(
                    baseUrl = AppConfig.getVisionBaseUrl(),
                    apiKey = AppConfig.getVisionApiKey(),
                    modelName = AppConfig.getVisionModelName(),
                    temperature = AppConfig.getVisionTemperature(),
                    maxTokens = AppConfig.getVisionMaxTokens(),
                    useJsonMode = AppConfig.getVisionJsonMode(),
                )
                OpenAIVisionProvider.getInstance(config)
            }
            else -> {
                AppLog.w("未知 VisionProvider: $providerId，降级为 OpenAI 兼容")
                val config = OpenAIVisionConfig(
                    baseUrl = AppConfig.getVisionBaseUrl(),
                    apiKey = AppConfig.getVisionApiKey(),
                    modelName = AppConfig.getVisionModelName(),
                )
                OpenAIVisionProvider.getInstance(config)
            }
        }
    }

    /**
     * 切换 Provider 时清除旧实例
     */
    fun invalidateCache() {
        OpenAIVisionProvider.clearInstance()
    }

    /**
     * 获取当前 Provider 的配置验证结果
     */
    fun validateCurrentConfig(): ConfigValidationResult {
        val provider = create()
        return provider?.validateConfig() ?: ConfigValidationResult(
            isValid = false,
            errors = listOf("视觉过滤未启用")
        )
    }
}

/**
 * Provider 元数据
 */
data class ProviderMeta(
    val displayName: String,
    val description: String,
    val defaultBaseUrl: String,
    val defaultModel: String
)
