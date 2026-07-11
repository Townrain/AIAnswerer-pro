package com.hwb.aianswerer.api.vision

import android.graphics.Bitmap

/**
 * 视觉模型抽象接口
 *
 * 所有视觉模型 Provider 必须实现此接口。
 * 上层调用者不关心底层是 OpenAI / 自定义 API，
 * 只通过此接口获取统一的过滤结果。
 *
 * 设计原则：
 *   - 接口即契约：输入 Bitmap，输出 VisionFilterResult
 *   - 失败不抛异常，通过 Result 返回（与项目风格一致）
 *   - 支持取消（协程协作式取消）
 */
interface VisionProvider {

    /** Provider 唯一标识，用于配置序列化 */
    val providerId: String

    /** 显示名称，用于 UI */
    val displayName: String

    /**
     * 分析截图，返回题目标注信息
     *
     * @param bitmap 原始截图（Provider 自行决定压缩策略）
     * @return Result<VisionFilterResult>
     */
    suspend fun analyze(bitmap: Bitmap): Result<VisionFilterResult>

    /**
     * 验证当前配置是否可用（API Key 格式、URL 可达性等）
     * 轻量检查，不应发起网络请求
     */
    fun validateConfig(): ConfigValidationResult

    /**
     * 获取此 Provider 的配置描述（供 UI 展示）
     */
    fun getConfigDescriptor(): ProviderConfigDescriptor
}

/**
 * Provider 配置描述符 — 告诉 UI 需要展示哪些配置项
 */
data class ProviderConfigDescriptor(
    val fields: List<ConfigField>
)

sealed class ConfigField {
    data class TextField(
        val key: String,
        val label: String,
        val hint: String = "",
        val defaultValue: String = "",
        val isPassword: Boolean = false
    ) : ConfigField()

    data class SelectField(
        val key: String,
        val label: String,
        val options: List<Pair<String, String>>,  // value -> display
        val defaultValue: String = ""
    ) : ConfigField()

    data class SwitchField(
        val key: String,
        val label: String,
        val description: String = "",
        val defaultValue: Boolean = false
    ) : ConfigField()
}

data class ConfigValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
