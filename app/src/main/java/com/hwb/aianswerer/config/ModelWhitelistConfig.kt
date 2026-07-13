package com.hwb.aianswerer.config

/**
 * Dynamic model whitelist 配置
 */
internal object ModelWhitelistConfig {

    // ========== 动态模型白名单相关 ==========

    /**
     * 保存动态视觉模型白名单
     * @param models 模型正则表达式列表
     */
    fun saveDynamicVisionModels(models: List<String>) {
        val modelsString = models.joinToString("|||")
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_DYNAMIC_VISION_MODELS, modelsString)
    }

    /**
     * 获取动态视觉模型白名单
     * @return 模型正则表达式列表，如果未设置则返回空列表
     */
    fun getDynamicVisionModels(): List<String> {
        val modelsString = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_DYNAMIC_VISION_MODELS, null)
        return if (modelsString.isNullOrEmpty()) {
            emptyList()
        } else {
            modelsString.split("|||").filter { it.isNotEmpty() }
        }
    }

    /**
     * 保存动态视觉模型排除列表
     * @param models 排除模型正则表达式列表
     */
    fun saveDynamicVisionExcluded(models: List<String>) {
        val modelsString = models.joinToString("|||")
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_DYNAMIC_VISION_EXCLUDED, modelsString)
    }

    /**
     * 获取动态视觉模型排除列表
     * @return 排除模型正则表达式列表，如果未设置则返回空列表
     */
    fun getDynamicVisionExcluded(): List<String> {
        val modelsString = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_DYNAMIC_VISION_EXCLUDED, null)
        return if (modelsString.isNullOrEmpty()) {
            emptyList()
        } else {
            modelsString.split("|||").filter { it.isNotEmpty() }
        }
    }

    /**
     * 保存动态厂商模型列表（增量更新，合并新模型到现有列表）
     * @param providerModels 新的厂商模型列表 Map<providerId, List<modelName>>
     */
    fun saveDynamicProviderModels(providerModels: Map<String, List<String>>) {
        // 加载现有模型列表
        val existingModels = getDynamicProviderModels().toMutableMap()

        // 合并新模型（去重）
        for ((providerId, models) in providerModels) {
            val existing = existingModels[providerId] ?: emptyList()
            val merged = (existing + models).distinct()
            existingModels[providerId] = merged
        }

        // 保存合并后的模型列表
        val sb = StringBuilder()
        for ((providerId, models) in existingModels) {
            if (sb.isNotEmpty()) sb.append(";;;")
            sb.append(providerId)
            sb.append(":::")
            sb.append(models.joinToString("|||"))
        }
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_DYNAMIC_PROVIDER_MODELS, sb.toString())
    }

    /**
     * 获取动态厂商模型列表
     * @return 厂商模型列表 Map<providerId, List<modelName>>，如果未设置则返回空Map
     */
    fun getDynamicProviderModels(): Map<String, List<String>> {
        val data = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_DYNAMIC_PROVIDER_MODELS, null)
        if (data.isNullOrEmpty()) return emptyMap()

        val result = mutableMapOf<String, List<String>>()
        val providers = data.split(";;;")
        for (provider in providers) {
            val parts = provider.split(":::")
            if (parts.size == 2) {
                val providerId = parts[0]
                val models = parts[1].split("|||").filter { it.isNotEmpty() }
                result[providerId] = models
            }
        }
        return result
    }

    /**
     * 获取指定厂商的动态模型列表
     * @param providerId 厂商ID
     * @return 模型列表，如果未设置则返回空列表
     */
    fun getDynamicProviderModels(providerId: String): List<String> {
        return getDynamicProviderModels()[providerId] ?: emptyList()
    }

    /**
     * 保存动态厂商配置列表（增量更新，合并新厂商到现有列表）
     * @param providerConfigs 新的厂商配置列表
     */
    fun saveDynamicProviderConfigs(providerConfigs: List<com.hwb.aianswerer.utils.ModelWhitelistUpdater.ProviderConfig>) {
        // 加载现有配置
        val existingConfigs = getDynamicProviderConfigs().toMutableList()
        val existingIds = existingConfigs.map { it.id }.toSet()

        // 添加新配置（去重）
        for (config in providerConfigs) {
            if (config.id !in existingIds) {
                existingConfigs.add(config)
            }
        }

        // 保存合并后的配置
        val sb = StringBuilder()
        for (config in existingConfigs) {
            if (sb.isNotEmpty()) sb.append(";;;")
            sb.append(config.id)
            sb.append(":::")
            sb.append(config.name)
            sb.append(":::")
            sb.append(config.type)
            sb.append(":::")
            sb.append(config.apiHost)
        }
        ConfigStorage.requireMmkv().encode(ConfigStorage.KEY_DYNAMIC_PROVIDER_CONFIGS, sb.toString())
    }

    /**
     * 获取动态厂商配置列表
     * @return 厂商配置列表，如果未设置则返回空列表
     */
    fun getDynamicProviderConfigs(): List<com.hwb.aianswerer.utils.ModelWhitelistUpdater.ProviderConfig> {
        val data = ConfigStorage.requireMmkv().decodeString(ConfigStorage.KEY_DYNAMIC_PROVIDER_CONFIGS, null)
        if (data.isNullOrEmpty()) return emptyList()

        val result = mutableListOf<com.hwb.aianswerer.utils.ModelWhitelistUpdater.ProviderConfig>()
        val providers = data.split(";;;")
        for (provider in providers) {
            val parts = provider.split(":::")
            if (parts.size == 4) {
                result.add(com.hwb.aianswerer.utils.ModelWhitelistUpdater.ProviderConfig(
                    id = parts[0],
                    name = parts[1],
                    type = parts[2],
                    apiHost = parts[3]
                ))
            }
        }
        return result
    }
}
