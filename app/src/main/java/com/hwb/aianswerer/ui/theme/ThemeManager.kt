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
 * - 导入自定义主题：`ThemeManager.importCustomTheme(json)` 返回 [ImportResult]
 * - 导出为 JSON：`ThemeManager.exportTheme(id)` 返回 JSON 字符串
 */
object ThemeManager {

    /** 导入结果 */
    sealed class ImportResult {
        /** 导入成功，携带新主题 ID */
        data class Success(val themeId: String) : ImportResult()
        /** 导入失败，携带错误信息 */
        data class Error(val message: String) : ImportResult()
    }

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
            // 只取低 32 位 ARGB，避免颜色空间编码干扰预览渲染
            val bg1 = light.bg1.value.toLong() and 0xFFFFFFFFL
            val p = light.p.value.toLong() and 0xFFFFFFFFL
            return bg1 to p
        }
        customThemes[presetId]?.let { def ->
            // 同样只暴露 ARGB 位，兼容手动构造的 JSON 数值
            return (def.light.bg1 and 0xFFFFFFFFL) to (def.light.primary and 0xFFFFFFFFL)
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
     * 自动去除 JSON 中的注释（// 和 /* */），防止 Gson 解析崩溃。
     * 检查 ID 和 Name 是否与已有主题冲突。
     */
    fun importCustomTheme(jsonStr: String): ImportResult {
        // 1. 去除 JSON 注释（JSON 标准不支持注释，Gson 会直接抛异常）
        val cleaned = stripJsonComments(jsonStr)

        // 2. 解析 JSON
        val def: CustomThemeDefinition
        try {
            def = gson.fromJson(cleaned, CustomThemeDefinition::class.java)
        } catch (e: Exception) {
            return ImportResult.Error("JSON 格式错误：${e.message ?: "无法解析"}。请检查是否有 // 或 /* */ 注释未去除")
        }

        // 3. 基础校验 — Gson 会跳过 Kotlin non-null 构造器，
        // 因此 JSON 中缺失 light/dark 字段时这里会变成 null。
        if (def.id.isBlank()) return ImportResult.Error("主题 ID 不能为空")
        if (def.name.isBlank()) return ImportResult.Error("主题名称不能为空")
        @Suppress("SENSELESS_COMPARISON")
        if (def.light == null) return ImportResult.Error("缺少 light 颜色定义")
        @Suppress("SENSELESS_COMPARISON")
        if (def.dark == null) return ImportResult.Error("缺少 dark 颜色定义")

        // 3.1 完整校验 24 个颜色字段：非零 + ARGB 范围 [1, 0xFFFFFFFF]
        validateAllColors(def.light)?.let { return ImportResult.Error("亮色: $it") }
        validateAllColors(def.dark)?.let { return ImportResult.Error("暗色: $it") }

        // 4. 检查 ID 冲突（内置主题）— 自动加 custom_ 前缀避免覆盖内置主题
        val (finalId, finalDef) = if (def.id in ThemePresets.BUILT_IN) {
            val newId = "custom_${def.id}"
            newId to def.copy(id = newId, name = "${def.name} (自定义)")
        } else {
            def.id to def
        }

        // 5. 检查 ID 冲突（已有自定义主题）
        if (finalId in customThemes) {
            return ImportResult.Error("主题 ID「$finalId」已存在，请先删除旧主题或修改 ID")
        }

        // 6. 检查 Name 冲突（内置主题）
        val builtInNames = ThemePresets.BUILT_IN.values.map { it.first }
        if (finalDef.name in builtInNames) {
            return ImportResult.Error("主题名称「${finalDef.name}」与内置主题重名，请修改 name 字段")
        }

        // 7. 检查 Name 冲突（已有自定义主题）
        val customNames = customThemes.values.map { it.name }
        if (finalDef.name in customNames) {
            return ImportResult.Error("主题名称「${finalDef.name}」已被其他自定义主题使用，请修改 name 字段")
        }

        // 8. 先存后改 — 序列化 + 写盘成功后才 mutate 内存 map，避免半应用状态造成内存与持久化不一致
        val updatedList = customThemes.values.toList() + finalDef
        val type = object : TypeToken<List<CustomThemeDefinition>>() {}.type
        val json = try { gson.toJson(updatedList, type) }
            catch (e: Exception) { return ImportResult.Error("主题序列化失败：${e.message}") }
        try {
            AppConfig.saveCustomThemes(json)
        } catch (e: Exception) {
            return ImportResult.Error("主题持久化失败，请检查存储空间：${e.message}")
        }
        // 写盘成功，才应用内存变更
        customThemes[finalId] = finalDef
        return ImportResult.Success(finalId)
    }

    /**
     * 校验 [SerializableThemeColors] 中全部 24 个颜色字段：
     * - 不能为 0（会导致透明/不可见颜色）
     * - 必须在 ARGB 范围 [1, 0xFFFFFFFF] 内
     * @return 返回第一个不合法字段名 + 原因，全部合法则返回 null
     */
    private fun validateAllColors(c: SerializableThemeColors): String? {
        val fields = listOf(
            "bg1" to c.bg1, "bg2" to c.bg2, "bg3" to c.bg3, "bg4" to c.bg4, "bg5" to c.bg5,
            "primary" to c.primary, "primaryEnd" to c.primaryEnd, "primaryDim" to c.primaryDim,
            "primaryContainer" to c.primaryContainer, "onPrimaryContainer" to c.onPrimaryContainer,
            "success" to c.success, "onBg" to c.onBg, "onBgVariant" to c.onBgVariant,
            "glassTop" to c.glassTop, "glassBorder" to c.glassBorder,
            "glassDarkPrimary" to c.glassDarkPrimary,
            "headerTop" to c.headerTop, "headerDarkPrimary" to c.headerDarkPrimary,
            "accent" to c.accent, "uiAccent" to c.uiAccent, "uiAccentLight" to c.uiAccentLight,
            "trackOff" to c.trackOff, "error" to c.error, "white" to c.white,
        )
        for ((name, value) in fields) {
            if (value == 0L) return "$name 不能为 0（会导致透明色）"
            if (value < 0L || value > 0xFFFFFFFFL) return "$name 超出 ARGB 范围: $value"
        }
        return null
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
                author = "FloatyAnswer",
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

    /**
     * 去除 JSON 中的 // 和斜杠星号注释。
     * 注意：会破坏字符串值中的 // 或斜杠星号，但主题 JSON 中无此需求。
     */
    private fun stripJsonComments(json: String): String {
        // 去除多行注释 /* ... */
        val noBlock = json.replace(Regex("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/"), " ")
        // 去除单行注释 // ...（保留换行符以保证行号不偏移太多）
        return noBlock.replace(Regex("//[^\n]*"), " ")
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
            val list: List<CustomThemeDefinition> = gson.fromJson(json, type) ?: emptyList()
            list.forEach { def ->
                // 跳过老化主题记录（深层颜色字段为 0 或超 ARGB 范围）
                if (def.light != null && def.dark != null &&
                    validateAllColors(def.light) == null && validateAllColors(def.dark) == null) {
                    customThemes[def.id] = def
                } else {
                    android.util.Log.w("ThemeManager", "跳过无效存储自定义主题: ${def.id}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ThemeManager", "加载自定义主题失败: ${e.message}")
        }
    }
}
