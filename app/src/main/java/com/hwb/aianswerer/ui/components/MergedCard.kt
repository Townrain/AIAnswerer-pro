package com.hwb.aianswerer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.providers.WebSearchStorage
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

@Composable
internal fun MergedCard(t: Th, expandedMenu: MutableState<String?>) {
    // 从模型厂商设置读取已启用的 LLM 模型
    val enabledProviders = ProviderStorage.getEnabledProvidersFromUserConfigs()
    val allModels = enabledProviders.flatMap { it.selectedModels }.filter { it.isNotBlank() }

    // 语言模型菜单 = 所有已选模型
    val textMenuModels = allModels
    // VLM 菜单 = 仅白名单判断为支持视觉的模型
    val visionModels = remember(allModels) { allModels.filter { ModelCapabilityChecker.isVisionModel(it) } }
    // 没有视觉模型时默认关闭，并给下拉菜单加提示
    val vlmNoModels = visionModels.isEmpty()
    val vlmOptions = if (vlmNoModels) listOf("关闭") else visionModels
    val vlmDefault = if (vlmNoModels) "关闭"
        else visionModels.firstOrNull { it == AppConfig.getVisionModelName() } ?: visionModels.first()
    val vlmDropdownHint = if (vlmNoModels) "暂无可用的视觉模型，请先在厂商设置中选择支持视觉的模型" else null
    val llmDefault = allModels.firstOrNull { it == AppConfig.getModelName() }
        ?: allModels.firstOrNull()
        ?: "未配置"

    // 从联网搜索设置读取已启用的服务商
    val enabledWebProviders = WebSearchStorage.getEnabledProviders()
    val webOptions = if (enabledWebProviders.isEmpty()) listOf("关闭")
        else listOf("关闭") + enabledWebProviders.map { it.name }
    val webDefault = AppConfig.getWebSearchProvider().takeIf { it.isNotBlank() }
        ?: if (enabledWebProviders.isEmpty()) "关闭" else enabledWebProviders.first().name

    // 使用 key 确保数据变化时重置状态
    val language = remember { mutableStateOf(llmDefault) }
    val vlm = remember { mutableStateOf(vlmDefault) }
    val web = remember { mutableStateOf(webDefault) }
    val outputLang = remember { mutableStateOf(AppConfig.getOutputLanguage()) }

    // ── 模型列表变化时重置选中项 ──
    LaunchedEffect(textMenuModels) {
        if (language.value !in textMenuModels && textMenuModels.isNotEmpty()) {
            language.value = textMenuModels.first()
        }
    }
    LaunchedEffect(vlmOptions) {
        if (vlm.value !in vlmOptions && vlmOptions.isNotEmpty()) {
            vlm.value = vlmOptions.first()
        }
    }
    LaunchedEffect(webOptions) {
        if (web.value !in webOptions && webOptions.isNotEmpty()) {
            web.value = webOptions.first()
        }
    }

    // ── Sync selections to AppConfig ──
    LaunchedEffect(language.value) {
        if (language.value != "未配置" && language.value != "关闭") AppConfig.saveModelName(language.value)
    }
    LaunchedEffect(vlm.value) {
        if (vlm.value != "关闭") AppConfig.saveVisionModelName(vlm.value)
    }
    LaunchedEffect(web.value) {
        AppConfig.saveWebSearchProvider(web.value)
    }
    // 输出语言显示名 → 语言代码映射
    val outputLangToCode = mapOf(
        "中文" to "zh", "English" to "en", "日本語 (VLM)" to "ja",
        "Français" to "fr", "Deutsch" to "de", "Español" to "es",
        "Português" to "pt", "한국어 (VLM)" to "ko",
        "Русский (VLM)" to "ru", "العربية (VLM)" to "ar", "其他语言 (VLM)" to "other"
    )
    LaunchedEffect(outputLang.value) {
        // 保存显示名（去 VLM 后缀）给 prompt 用
        val displayName = outputLang.value.replace(" (VLM)", "")
        AppConfig.saveOutputLanguage(displayName)
        outputLangToCode[outputLang.value]?.let { code ->
            AppConfig.saveLanguage(code)
        }
    }

    Box(
        Modifier.padding(horizontal = 20.dp)
            .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
            .border(1.dp, t.gb, RoundedCornerShape(CardR))
            .padding(CardPad)
    ) {
        Column {
            // ── Upper: service status ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("服务运行中", style = DW.TitleMedium.copy(color = t.ok))
                    Spacer(Modifier.height(2.dp))
                    Text("随时准备帮你答题", style = DW.BodySmall.copy(color = t.osv))
                }
                Icon(LocalIcons.Lightbulb, "status", tint = t.ok, modifier = Modifier.size(28.dp))
            }

            HorizontalDivider(color = t.ac.copy(alpha = 0.15f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 16.dp))

            // ── Lower: 2×2 model selectors ──
            val langEx = derivedStateOf { expandedMenu.value == "语言模型" }
            val webEx = derivedStateOf { expandedMenu.value == "联网搜索" }
            val vlmEx = derivedStateOf { expandedMenu.value == "VLM 模型" }
            val outEx = derivedStateOf { expandedMenu.value == "输出语言" }

            Row(Modifier.fillMaxWidth().zIndex(2f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModelMenu("语言模型", language, textMenuModels.ifEmpty { listOf(llmDefault) }, langEx, t, Modifier.weight(1f)) { expandedMenu.value = if (expandedMenu.value == "语言模型") null else "语言模型" }
                ModelMenu("联网搜索", web, webOptions, webEx, t, Modifier.weight(1f)) { expandedMenu.value = if (expandedMenu.value == "联网搜索") null else "联网搜索" }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().zIndex(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    ModelMenu("VLM 模型", vlm, vlmOptions, vlmEx, t, Modifier.fillMaxWidth(), hint = vlmDropdownHint, onToggle = { expandedMenu.value = if (expandedMenu.value == "VLM 模型") null else "VLM 模型" })
                }
                val outputLangOptions = listOf(
                    "中文", "English", "日本語 (VLM)", "Français", "Deutsch",
                    "Español", "Português", "한국어 (VLM)",
                    "Русский (VLM)", "العربية (VLM)", "其他语言 (VLM)"
                )
                ModelMenu("输出语言", outputLang, outputLangOptions, outEx, t, Modifier.weight(1f)) { expandedMenu.value = if (expandedMenu.value == "输出语言") null else "输出语言" }
            }
        }
    }
}
