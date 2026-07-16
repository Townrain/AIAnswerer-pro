package com.hwb.aianswerer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hwb.aianswerer.config.AppConfig

/**
 * 全局主题管理器 — 单例，管理主题选择、自定义主题导入/导出。
 *
 * 使用方式：
 * - 选择主题：`ThemeManager.selectPreset("premium_indigo")`
 * - 获取当前 Th：`val t = ThemeManager.getCurrentTheme()` (在 Composable 中使用)
 * - 导入自定义主题：`ThemeManager.importCustomTheme(json)` 返回成功/失败
 * - 导出为 JSON：`ThemeManager.exportTheme(id)` 返回 JSON 字符串
 */
object ThemeManager {

    private val gson = Gson()

    /** 当前选中的主题预设 ID */
    var currentPresetId by mutableStateOf(AppConfig.getThemePresetId())

    /** 自定义主题映射 (id -> CustomThemeDefinition) */
    val customThemes = mutableStateMapOf<String, CustomThemeDefinition>()

    /** 标记初始化是否完成 */
    private var initialized = false

    /** 初始化 — 加载自定义主题 */
    fun init() {
        if (initialized) return
        initialized = true
        loadCustomThemes()
    }

    /**
     * 选择内置主题预设
     * @param presetId 主题 ID (ThemePresets.WARM_AUTUMN 等)
     */
    fun selectPreset(presetId: String) {
        currentPresetId = presetId
        AppConfig.saveThemePresetId(presetId)
    }

    /**
     * 获取当前主题的显示名称
     */
    fun getCurrentThemeName(): String {
        ThemePresets.BUILT_IN[currentPresetId]?.let { return it.first }
        customThemes[currentPresetId]?.let { return it.name }
        return "暖秋" // fallback
    }

    /**
     * 获取所有可用主题的列表：内置 + 自定义
     * 返回 Pair<主题ID, 显示名>
     */
    fun getAllThemes(): List<Pair<String, String>> {
        val themes = mutableListOf<Pair<String, String>>()
        ThemePresets.BUILT_IN.forEach { (id, data) ->
            themes.add(id to data.first)
        }
        customThemes.forEach { (id, def) ->
            themes.add(id to def.name)
        }
        return themes
    }

    /**
     * 判断指定主题是否为内置主题
     */
    fun isBuiltIn(presetId: String): Boolean = presetId in ThemePresets.BUILT_IN

    /**
     * 获取主题预览所需的背景色 (用于设置页主题选择器)
     */
    fun getPreviewColors(presetId: String): Pair<Long, Long>? {
        ThemePresets.BUILT_IN[presetId]?.let { (_, light, _) ->
            return light.bg1.value.toLong() to light.p.value.toLong()
        }
        customThemes[presetId]?.let { def ->
            return def.light.bg1 to def.light.primary
        }
        return null
    }

    // ═══════════════════════════════════════════════
    //  Theme Resolution (核心)
    // ═══════════════════════════════════════════════

    /**
     * 获取当前主题的 Th 对象 — 根据选中的预设 + 暗色模式自动选择亮/暗色
     * 在 Composable 中调用即可自动响应状态变化
     */
    @Composable
    fun getCurrentTheme(): Th {
        val isDark = when (ThemeState.darkMode) {
            1 -> false
            2 -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }

        // 检查内置主题
        ThemePresets.BUILT_IN[currentPresetId]?.let { (_, light, dark) ->
            return if (isDark) dark else light
        }

        // 检查自定义主题
        customThemes[currentPresetId]?.let { def ->
            val (light, dark) = def.toThPair()
            return if (isDark) dark else light
        }

        // Fallback: 返回暖秋主题
        return if (isDark) ThemePresets.WarmAutumnDark else ThemePresets.WarmAutumnLight
    }

    // ═══════════════════════════════════════════════
    //  自定义主题导入/导出
    // ═══════════════════════════════════════════════

    /**
     * 导入自定义主题 (从 JSON 字符串)
     * @return 成功返回主题 ID，失败返回 null
     */
    fun importCustomTheme(jsonStr: String): String? {
        return try {
            val def = gson.fromJson(jsonStr, CustomThemeDefinition::class.java)
            if (!validateThemeDefinition(def)) return null

            // 检查 ID 是否冲突 (内置主题 ID 不可覆盖)
            if (def.id in ThemePresets.BUILT_IN) {
                // 自动重命名
                val newId = "custom_${def.id}"
                val renamed = def.copy(id = newId, name = "${def.name} (自定义)")
                customThemes[newId] = renamed
                saveCustomThemes()
                newId
            } else {
                customThemes[def.id] = def
                saveCustomThemes()
                def.id
            }
        } catch (e: Exception) {
            android.util.Log.w("ThemeManager", "导入主题失败: ${e.message}")
            null
        }
    }

    /**
     * 导出指定主题为 JSON 字符串
     */
    fun exportTheme(presetId: String): String? {
        // 内置主题
        ThemePresets.BUILT_IN[presetId]?.let { (name, light, dark) ->
            val def = CustomThemeDefinition(
                id = presetId,
                name = name,
                author = "AI Answerer",
                description = "内置主题：$name",
                light = SerializableThemeColors.fromTh(light),
                dark = SerializableThemeColors.fromTh(dark),
            )
            return try { gson.toJson(def) } catch (_: Exception) { null }
        }
        // 自定义主题
        customThemes[presetId]?.let { def ->
            return try { gson.toJson(def) } catch (_: Exception) { null }
        }
        return null
    }

    /**
     * 删除自定义主题
     */
    fun removeCustomTheme(presetId: String): Boolean {
        if (presetId in ThemePresets.BUILT_IN) return false
        val removed = customThemes.remove(presetId) != null
        if (removed) {
            // 如果正在使用被删除的主题，回退到默认
            if (currentPresetId == presetId) {
                selectPreset(ThemePresets.WARM_AUTUMN)
            }
            saveCustomThemes()
        }
        return removed
    }

    // ═══════════════════════════════════════════════
    //  Private
    // ═══════════════════════════════════════════════

    private fun validateThemeDefinition(def: CustomThemeDefinition): Boolean {
        if (def.id.isBlank() || def.name.isBlank()) return false
        // 基本颜色合法性检查：确保不是全0
        val allColors = listOf(
            def.light.bg1, def.light.bg2, def.dark.bg1, def.dark.bg2
        )
        return allColors.none { it == 0L }
    }

    private fun saveCustomThemes() {
        if (customThemes.isEmpty()) {
            AppConfig.saveCustomThemes("")
            return
        }
        val type = object : TypeToken<List<CustomThemeDefinition>>() {}.type
        val json = try { gson.toJson(customThemes.values.toList(), type) } catch (_: Exception) { "" }
        AppConfig.saveCustomThemes(json)
    }

    private fun loadCustomThemes() {
        val json = AppConfig.getCustomThemes()
        if (json.isBlank()) return
        try {
            val type = object : TypeToken<List<CustomThemeDefinition>>() {}.type
            val list: List<CustomThemeDefinition> = gson.fromJson(json, type)
            list.forEach { def ->
                customThemes[def.id] = def
            }
        } catch (e: Exception) {
            android.util.Log.w("ThemeManager", "加载自定义主题失败: ${e.message}")
        }
    }
}
