package com.hwb.aianswerer.providers

import com.google.gson.annotations.SerializedName

/**
 * OpenCode provider-data.json 的数据结构
 * 兼容 Cherry Studio JSON Schema + OpenCode source 字段
 */
data class ProviderDataJson(
    val version: Int,
    val generatedAt: String,
    val providerCount: Int,
    val modelCount: Int,
    val source: String? = null,
    val providers: List<ProviderEntry>
)

data class ProviderEntry(
    val id: String,
    val name: String,
    val type: String,           // "openai", "anthropic", "gemini", "azure-openai", etc.
    val apiHost: String,
    val anthropicApiHost: String?,
    val models: List<ModelEntry>,
    val websites: WebsiteInfo?
)

data class ModelEntry(
    val id: String,
    val name: String,
    val group: String
)

data class WebsiteInfo(
    val official: String?,
    val apiKey: String?,
    val docs: String?,
    val models: String?
)

/**
 * 本地持久化的厂商配置（合并了云端数据 + 用户配置）
 */
data class LocalProviderConfig(
    val id: String,
    val name: String,
    val type: String,
    val apiHost: String,
    val anthropicApiHost: String?,
    val models: List<ModelEntry>,
    val websites: WebsiteInfo?,
    // 用户配置（同步时保留）
    val apiKey: String = "",
    val enabled: Boolean = false,
    val isSystem: Boolean = true,
    val dataVersion: Int = 0,
    val selectedModels: List<String> = listOf()
)
