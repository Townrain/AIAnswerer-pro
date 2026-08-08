package com.hwb.aianswerer.ui.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.vision.OpenAIVisionConfig
import com.hwb.aianswerer.api.vision.OpenAIVisionProvider
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.providers.WebSearchStorage
import com.hwb.aianswerer.ui.components.AppTextField
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// =============================================================================
// Previews
// =============================================================================
@Preview(showSystemUi = true, showBackground = true, name = "设置 — Light")
@Composable private fun SettingsLightPreview() { Themed { SettingsPage(it, {}, {}, {}, {}) } }

@Preview(showSystemUi = true, showBackground = true, name = "设置 — Dark")
@Composable private fun SettingsDarkPreview() { Themed(DH) { SettingsPage(it, {}, {}, {}, {}) } }

// =============================================================================
// Connection test state
// =============================================================================
private sealed class ConnTest {
    data object Idle : ConnTest()
    data object Testing : ConnTest()
    data class Ok(val ms: Int) : ConnTest()
    data class Fail(val msg: String) : ConnTest()
}

// =============================================================================
// Settings Page
// =============================================================================
@Composable
fun SettingsPage(t: Th, onBack: () -> Unit, onWebSearch: () -> Unit = {}, onModels: () -> Unit = {}, onAbout: () -> Unit = {}, onExportLogs: (suspend () -> Boolean)? = null) {
    var autoSubmit by remember { mutableStateOf(AppConfig.getAutoSubmit()) }
    var autoCopy by remember { mutableStateOf(AppConfig.getAutoCopy()) }
    var stealthMode by remember { mutableStateOf(AppConfig.isStealthModeEnabled()) }
    var parallelMode by remember { mutableStateOf(AppConfig.isParallelModeEnabled()) }
    var maxConcurrency by remember { mutableStateOf(AppConfig.getMaxConcurrency().toFloat()) }
    var debugLog by remember { mutableStateOf(AppConfig.isDebugLogEnabled()) }
    var isExporting by remember { mutableStateOf(false) }
    var llmTest by remember { mutableStateOf<ConnTest>(ConnTest.Idle) }
    var vlmTest by remember { mutableStateOf<ConnTest>(ConnTest.Idle) }
    var searchTest by remember { mutableStateOf<ConnTest>(ConnTest.Idle) }
    var llmConcurrencyTest by remember { mutableStateOf<ConnTest>(ConnTest.Idle) }
    var vlmConcurrencyTest by remember { mutableStateOf<ConnTest>(ConnTest.Idle) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showQuestion by remember { mutableStateOf(AppConfig.getShowAnswerCardQuestion()) }
    var showOptions by remember { mutableStateOf(AppConfig.getShowAnswerCardOptions()) }
    var floatButtonSize by remember { mutableStateOf(AppConfig.getFloatButtonSize().toFloat()) }
    var floatButtonAlpha by remember { mutableStateOf(AppConfig.getFloatButtonAlpha()) }
    var floatCardAlpha by remember { mutableStateOf(AppConfig.getFloatCardAlpha()) }
    var floatIconScale by remember { mutableStateOf(AppConfig.getFloatIconScale()) }
    var longPressDuration by remember { mutableStateOf(AppConfig.getLongPressDuration().toFloat()) }
    var customSystemPrompt by remember { mutableStateOf(AppConfig.getCustomSystemPrompt()) }
    var customVLMPrompt by remember { mutableStateOf(AppConfig.getCustomVLMPrompt()) }
    val darkMode = ThemeState.darkMode

    val bgGradient = Brush.linearGradient(
        listOf(t.bg1, t.bg2, t.bg3, t.bg4, t.bg5),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(Modifier.fillMaxSize().background(bgGradient)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 108.dp, bottom = 48.dp)
        ) {

            // Navigation entries
            HighlightEntry(t, "模型厂商", "管理 60+ 厂商的 API 配置，支持云端同步") { onModels() }
            HighlightEntry(t, "联网搜索设置", "配置联网搜索服务商（Tavily、Zhipu、Bocha 等）") { onWebSearch() }

            Spacer(Modifier.height(12.dp))

            // General settings
            SectionTitle(t, "通用设置")
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                SettingSwitch(t, "自动提交", "识别后自动提交答案", autoSubmit) { autoSubmit = it; AppConfig.saveAutoSubmit(it) }
                Sep(t)
                SettingSwitch(t, "自动复制", "结果自动复制到剪贴板", autoCopy) { autoCopy = it; AppConfig.saveAutoCopy(it) }
                Sep(t)
                SettingSwitch(t, "隐身模式", "隐藏悬浮窗和通知内容", stealthMode) { stealthMode = it; AppConfig.saveStealthMode(it) }
            }

            // Parallel mode
            SectionTitle(t, "并发设置")
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                SettingSwitch(t, "并发模式", "同时处理多道题目", parallelMode) { parallelMode = it; AppConfig.saveParallelMode(it) }
                Spacer(Modifier.height(6.dp))
                SettingSlider(t, "最大并发数", maxConcurrency, 1f..50f, "${maxConcurrency.toInt()} 题", enabled = parallelMode) { maxConcurrency = it; AppConfig.saveMaxConcurrency(it.toInt()) }
                if (maxConcurrency > 20) {
                    Text("并发数过高可能导致 API 限流", style = DW.BodySmall.copy(color = t.err), modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Sep(t)
                Text("并发测试（按当前并发数 ${maxConcurrency.toInt()} 同时发请求，验证服务商真实并发能力）", style = DW.LabelLarge.copy(color = t.ob), modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TestChip("LLM", llmConcurrencyTest, t, Modifier.weight(1f)) {
                        if (llmConcurrencyTest is ConnTest.Testing) return@TestChip
                        val key = AppConfig.getApiKey()
                        if (key.isBlank()) { toastMsg = "请先在模型厂商设置中配置 LLM API Key"; return@TestChip }
                        llmConcurrencyTest = ConnTest.Testing
                        val n = maxConcurrency.toInt()
                        scope.launch {
                            llmConcurrencyTest = OpenAIClient.getInstance().testConcurrency(concurrency = n).fold(
                                { ms -> ConnTest.Ok(ms.toInt()) },
                                { e -> ConnTest.Fail(e.message ?: "并发测试失败") }
                            )
                        }
                    }
                    TestChip("VLM", vlmConcurrencyTest, t, Modifier.weight(1f)) {
                        if (vlmConcurrencyTest is ConnTest.Testing) return@TestChip
                        val key = AppConfig.getVisionApiKey()
                        if (key.isBlank()) { toastMsg = "请先在视觉模型设置中配置 API Key"; return@TestChip }
                        vlmConcurrencyTest = ConnTest.Testing
                        val n = maxConcurrency.toInt()
                        scope.launch {
                            vlmConcurrencyTest = OpenAIVisionProvider.testConcurrency(
                                OpenAIVisionConfig.fromAppConfig(), concurrency = n
                            ).fold(
                                { ms -> ConnTest.Ok(ms.toInt()) },
                                { e -> ConnTest.Fail(e.message ?: "并发测试失败") }
                            )
                        }
                    }
                }
                TestResultLine("LLM", llmConcurrencyTest, t)
                TestResultLine("VLM", vlmConcurrencyTest, t)
                Spacer(Modifier.height(4.dp))
                Sep(t)
                Text("连接测试", style = DW.LabelLarge.copy(color = t.ob), modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TestChip("LLM", llmTest, t, Modifier.weight(1f)) {
                        if (llmTest is ConnTest.Testing) return@TestChip
                        val key = AppConfig.getApiKey()
                        val url = AppConfig.getApiUrl()
                        if (key.isBlank()) { toastMsg = "请先在模型厂商设置中配置 LLM API Key"; return@TestChip }
                        llmTest = ConnTest.Testing
                        scope.launch {
                            llmTest = testConnection(url, key, "/models")
                        }
                    }
                    TestChip("VLM", vlmTest, t, Modifier.weight(1f)) {
                        if (vlmTest is ConnTest.Testing) return@TestChip
                        val key = AppConfig.getVisionApiKey()
                        val url = AppConfig.getVisionBaseUrl()
                        if (key.isBlank()) { toastMsg = "请先在视觉模型设置中配置 API Key"; return@TestChip }
                        vlmTest = ConnTest.Testing
                        scope.launch {
                            vlmTest = testConnection(url, key, "/models")
                        }
                    }
                    TestChip("搜索", searchTest, t, Modifier.weight(1f)) {
                        if (searchTest is ConnTest.Testing) return@TestChip
                        val enabled = WebSearchStorage.getEnabledProviders()
                        if (enabled.isEmpty() || enabled.all { it.apiKey.isBlank() }) {
                            toastMsg = "请先在联网搜索设置中配置服务商 API Key"; return@TestChip
                        }
                        val provider = enabled.first { it.apiKey.isNotBlank() }
                        searchTest = ConnTest.Testing
                        scope.launch {
                            searchTest = testConnection(provider.apiHost, provider.apiKey)
                        }
                    }
                }
                TestResultLine("LLM", llmTest, t)
                TestResultLine("VLM", vlmTest, t)
                TestResultLine("搜索", searchTest, t)
            }

            // Display control
            SectionTitle(t, "显示控制")
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                SettingSwitch(t, "显示题目", "在答案卡片中显示题目内容", showQuestion) { showQuestion = it; AppConfig.saveShowAnswerCardQuestion(it) }
                Sep(t)
                SettingSwitch(t, "显示选项", "在答案卡片中显示选项列表", showOptions) { showOptions = it; AppConfig.saveShowAnswerCardOptions(it) }
            }

            // Floating window
            SectionTitle(t, "悬浮窗外观")
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                SettingSlider(t, "悬浮按钮大小", floatButtonSize, 32f..80f, "${floatButtonSize.toInt()}dp") { floatButtonSize = it; AppConfig.saveFloatButtonSize(it.toInt()) }
                SettingSlider(t, "悬浮按钮透明度", floatButtonAlpha, 0.1f..1.0f, "${(floatButtonAlpha * 100).toInt()}%") { floatButtonAlpha = it; AppConfig.saveFloatButtonAlpha(it) }
                SettingSlider(t, "悬浮卡片透明度", floatCardAlpha, 0.1f..1.0f, "${(floatCardAlpha * 100).toInt()}%") { floatCardAlpha = it; AppConfig.saveFloatCardAlpha(it) }
                SettingSlider(t, "图标缩放", floatIconScale, 0.5f..2.0f, "${(floatIconScale * 100).toInt()}%") { floatIconScale = it; AppConfig.saveFloatIconScale(it) }
                SettingSlider(t, "长按触发时长", longPressDuration, 300f..3000f, "${longPressDuration.toInt()}ms") { longPressDuration = it; AppConfig.saveLongPressDuration(it.toInt()) }
            }

            // Custom prompts
            SectionTitle(t, "自定义提示词")
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                AppTextField(
                    value = customSystemPrompt,
                    onValueChange = { customSystemPrompt = it; AppConfig.saveCustomSystemPrompt(it) },
                    label = "LLM 系统提示词",
                    placeholder = "你是答题助手。只返回合法 JSON，格式 {question, questionType, answer, options}。题型含选择题/问答题/填空题。留空使用默认。",
                    singleLine = false,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = customVLMPrompt,
                    onValueChange = { customVLMPrompt = it; AppConfig.saveCustomVLMPrompt(it) },
                    label = "VLM 视觉提示词",
                    placeholder = "你是题目截图分析器。只返回 JSON，含 has_questions, question_count, questions 等字段。留空使用默认。",
                    singleLine = false,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Theme presets
            SectionTitle(t, "主题风格")
            ThemePresetGrid(t)

            // Dark mode
            SectionTitle(t, "外观模式")
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                SettingRadio(t, "跟随系统", darkMode == 0) { ThemeState.update(0) }
                SettingRadio(t, "亮色", darkMode == 1) { ThemeState.update(1) }
                SettingRadio(t, "暗色", darkMode == 2) { ThemeState.update(2) }
            }

            // Custom theme import
            SectionTitle(t, "自定义主题")
            CustomThemeSection(t, toastMsg) { toastMsg = it }

            // Debug tools
            val ctx = LocalContext.current
            SectionTitle(t, ctx.getString(com.hwb.aianswerer.R.string.debug_section_title))
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                SettingSwitch(t,
                    ctx.getString(com.hwb.aianswerer.R.string.debug_log_toggle),
                    ctx.getString(com.hwb.aianswerer.R.string.debug_log_toggle_desc),
                    debugLog
                ) {
                    debugLog = it
                    AppConfig.saveDebugLogEnabled(it)
                    com.hwb.aianswerer.utils.AppLog.refreshDebugState()
                    if (it) {
                        com.hwb.aianswerer.utils.AppLog.i("Debug", "Debug logging enabled by user")
                    }
                }
                Sep(t)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            ctx.getString(com.hwb.aianswerer.R.string.debug_export_logs),
                            style = DW.BodyMedium.copy(color = t.ob)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            ctx.getString(com.hwb.aianswerer.R.string.debug_export_logs_desc),
                            style = DW.BodySmall.copy(color = t.osv)
                        )
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(ChipR))
                            .background(if (isExporting) t.gb.copy(alpha = 0.4f) else t.p.copy(alpha = 0.12f))
                            .clickable(enabled = !isExporting) {
                                if (isExporting) return@clickable
                                isExporting = true
                                scope.launch {
                                    val success = onExportLogs?.invoke() ?: false
                                    isExporting = false
                                    toastMsg = if (success) {
                                        ctx.getString(com.hwb.aianswerer.R.string.debug_export_success)
                                    } else {
                                        ctx.getString(com.hwb.aianswerer.R.string.debug_no_logs)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.p)
                        } else {
                            Text("导出", style = DW.LabelMedium.copy(color = t.p))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(t.bg1, t.bg2)))) {
            SettingsTopBar(t, onBack, onAbout)
        }
        toastMsg?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2000); toastMsg = null }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(t.p.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(msg, style = DW.LabelMedium.copy(color = Color.White))
                }
            }
        }
    }
}

// =============================================================================
// Sub-composables
// =============================================================================

@Composable
private fun SettingsTopBar(t: Th, onBack: () -> Unit, onAbout: () -> Unit = {}) {
    val backInteraction = remember { MutableInteractionSource() }
    val backPressed by backInteraction.collectIsPressedAsState()
    val backScale = remember { Animatable(1f) }
    LaunchedEffect(backPressed) {
        if (backPressed) backScale.snapTo(0.85f)
        else backScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).scale(backScale.value).clickable(interactionSource = backInteraction, indication = null) { onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(LocalIcons.ArrowBack, "返回", tint = t.ob, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(4.dp))
        Text("设置", style = DW.TitleLarge.copy(color = t.ob), modifier = Modifier.weight(1f))

        val infoInteraction = remember { MutableInteractionSource() }
        val infoPressed by infoInteraction.collectIsPressedAsState()
        val infoScale = remember { Animatable(1f) }
        LaunchedEffect(infoPressed) {
            if (infoPressed) infoScale.snapTo(0.85f)
            else infoScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
        }
        Box(
            Modifier.size(44.dp).scale(infoScale.value).clip(CircleShape)
                .clickable(interactionSource = infoInteraction, indication = null) { onAbout() },
            contentAlignment = Alignment.Center
        ) { Icon(LocalIcons.Info, "关于", tint = t.osv, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun HighlightEntry(t: Th, title: String, subtitle: String, onClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) scale.snapTo(0.85f)
        else scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
    }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)
            .scale(scale.value).clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(CardR))
                .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
                .border(1.dp, t.gb, RoundedCornerShape(CardR)).padding(CardPad)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(t.p, t.pe), endY = Float.POSITIVE_INFINITY)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = DW.TitleMedium.copy(color = t.ob))
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = DW.BodySmall.copy(color = t.osv))
                }
                Text("›", style = DW.TitleLarge.copy(color = t.osv))
            }
        }
    }
}

@Composable private fun SectionTitle(t: Th, title: String) {
    Text(title, style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun SettingSwitch(t: Th, title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val switchScale = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        switchScale.snapTo(0.82f)
        switchScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = DW.BodyMedium.copy(color = t.ob))
            Spacer(Modifier.height(2.dp))
            Text(description, style = DW.BodySmall.copy(color = t.osv))
        }
        Box(Modifier.scale(switchScale.value)) {
            Switch(checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = t.w, checkedTrackColor = t.p,
                    uncheckedThumbColor = t.w, uncheckedTrackColor = t.to,
                    checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent
                ))
        }
    }
}

@Composable
private fun SettingRadio(t: Th, title: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) scale.snapTo(0.90f)
        else scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
    }
    val dotScale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "rdDot")
    val ringColor by animateColorAsState(if (selected) t.p else t.osv.copy(alpha = 0.4f), tween(200), label = "rdRing")
    val rowBg by animateColorAsState(if (selected) t.p.copy(alpha = 0.08f) else Color.Transparent, tween(250), label = "rdBg")
    val textColor by animateColorAsState(if (selected) t.p else t.ob, tween(200), label = "rdTxt")

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(rowBg)
            .scale(scale.value).clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).border(2.dp, ringColor, CircleShape))
            if (dotScale > 0f) { Box(Modifier.fillMaxSize(fraction = 0.55f).scale(dotScale).clip(CircleShape).background(t.p)) }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = DW.BodyLarge.copy(color = textColor))
    }
}

@Composable
private fun SettingSlider(t: Th, title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, enabled: Boolean = true, onValueChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = DW.BodyMedium.copy(color = if (enabled) t.ob else t.osv))
            Text(valueText, style = DW.BodySmall.copy(color = if (enabled) t.osv else t.osv.copy(alpha = 0.5f)))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) t.w else t.w.copy(alpha = 0.5f),
                activeTrackColor = if (enabled) t.p else t.p.copy(alpha = 0.3f),
                inactiveTrackColor = t.to,
                disabledThumbColor = t.w.copy(alpha = 0.4f),
                disabledActiveTrackColor = t.p.copy(alpha = 0.2f),
                disabledInactiveTrackColor = t.to.copy(alpha = 0.3f)
            ))
    }
}

@Composable
private fun TestChip(label: String, state: ConnTest, t: Th, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) scale.snapTo(0.85f)
        else scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
    }
    val bg = when (state) {
        is ConnTest.Ok -> t.ok.copy(alpha = 0.15f)
        is ConnTest.Fail -> t.err.copy(alpha = 0.15f)
        else -> t.gb.copy(alpha = if (t.isLight) 0.65f else 0.12f)
    }
    Box(
        modifier = modifier.scale(scale.value).clip(RoundedCornerShape(ChipR)).background(bg)
            .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.45f else 0.2f), RoundedCornerShape(ChipR))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state is ConnTest.Testing) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.p)
        } else {
            Text(label, style = DW.LabelSmall.copy(color = t.ob, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun TestResultLine(name: String, state: ConnTest, t: Th) {
    when (state) {
        is ConnTest.Ok -> Text("$name 连接成功 (${state.ms}ms)",
            style = DW.BodySmall.copy(color = t.ok), modifier = Modifier.padding(top = 4.dp))
        is ConnTest.Fail -> Text("$name 连接失败: ${state.msg}",
            style = DW.BodySmall.copy(color = t.err), modifier = Modifier.padding(top = 4.dp))
        else -> {}
    }
}

private suspend fun testConnection(apiUrl: String, apiKey: String, path: String = ""): ConnTest {
    return withContext(Dispatchers.IO) {
        try {
            val url = if (path.isNotEmpty()) {
                val base = apiUrl.trimEnd('/')
                // Strip endpoint paths to get the API base URL, handling any /vN version
                val apiBase = if (base.matches(Regex(".*/v\\d+\\w*/.*"))) {
                    val match = Regex("(.*/v\\d+\\w*)/.*").find(base)
                    match?.groupValues?.get(1) ?: base
                } else if (base.matches(Regex(".*/v\\d+\\w*$"))) {
                    // URL ends with version path like /v4 — use as-is
                    base
                } else {
                    base.substringBeforeLast("/")
                }
                "$apiBase$path"
            } else apiUrl
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS).build()
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $apiKey").get().build()
            val start = System.currentTimeMillis()
            client.newCall(req).execute().use { resp ->
                val ms = (System.currentTimeMillis() - start).toInt()
                if (resp.isSuccessful) ConnTest.Ok(ms)
                else ConnTest.Fail("HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            ConnTest.Fail(e.message ?: "未知错误")
        }
    }
}

@Composable internal fun Sep(t: Th) { HorizontalDivider(color = t.ac.copy(alpha = 0.12f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp)) }

@Composable
private fun Chip(label: String, selected: Boolean, t: Th, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) (if (t.isLight) t.p.copy(alpha = 0.40f) else t.ua.copy(alpha = 0.4f))
        else t.gb.copy(alpha = if (t.isLight) 0.65f else 0.06f),
        tween(250, easing = FastOutSlowInEasing), label = "chipBg"
    )
    val fg by animateColorAsState(
        if (selected) (if (t.isLight) t.p else t.ual) else t.osv,
        tween(250, easing = FastOutSlowInEasing), label = "chipFg"
    )
    val bord by animateColorAsState(
        if (selected) (if (t.isLight) t.p.copy(alpha = 0.7f) else t.ua.copy(alpha = 0.65f))
        else t.ac.copy(alpha = if (t.isLight) 0.48f else 0.08f),
        tween(250, easing = FastOutSlowInEasing), label = "chipBord"
    )
    val selScale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "chipScale")

    val pressAnim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxWidth().scale(pressAnim.value).pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressAnim.snapTo(0.85f); val released = tryAwaitRelease()
                scope.launch { pressAnim.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) onClick()
            })
        }
    ) {
        Box(
            Modifier.fillMaxWidth().scale(selScale).clip(RoundedCornerShape(ChipR)).background(bg)
                .border(1.dp, bord, RoundedCornerShape(ChipR)).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) { Text(label, style = DW.LabelLarge.copy(color = fg, textAlign = TextAlign.Center)) }
    }
}

// =============================================================================
// Theme Preset Grid — card-based selector
// =============================================================================

@Composable
private fun ThemePresetGrid(t: Th) {
    val currentId = ThemeManager.currentPresetId
    // 不缓存列表，确保删除自定义主题后 UI 即时刷新
    val themes = ThemeManager.getAllThemes()

    Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t, p = 12.dp) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(themes.size) { index ->
                val (id, name) = themes[index]
                ThemePresetCard(t, id, name, isSelected = id == currentId,
                    isBuiltIn = ThemeManager.isBuiltIn(id),
                    onSelect = { ThemeManager.selectPreset(id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemePresetCard(
    t: Th, id: String, name: String, isSelected: Boolean,
    isBuiltIn: Boolean, onSelect: () -> Unit,
) {
    val bgColor: androidx.compose.ui.graphics.Color
    val accentColor: androidx.compose.ui.graphics.Color
    if (isBuiltIn) {
        // 内置主题直接从 BUILT_IN 取 Th 对象，颜色天然有效
        val entry = ThemePresets.BUILT_IN[id]
        if (entry != null) {
            val light = entry.second
            bgColor = light.bg1
            accentColor = light.p
        } else {
            bgColor = t.bg2; accentColor = t.p
        }
    } else {
        val pair = ThemeManager.getPreviewColors(id)
        if (pair != null) {
            bgColor = Color(pair.first.toULong().toInt())
            accentColor = Color(pair.second.toULong().toInt())
        } else {
            bgColor = t.bg2; accentColor = t.p
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) scale.snapTo(0.90f)
        else scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 400f))
    }

    var showMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            Modifier.width(100.dp).scale(scale.value)
                .clip(RoundedCornerShape(CardR))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onSelect,
                    onLongClick = { showMenu = true }
                )
        ) {
            // Color preview area
            Box(
                Modifier.fillMaxWidth().height(72.dp)
                    .background(Brush.verticalGradient(
                        listOf(bgColor, bgColor.copy(alpha = 0.7f)),
                        endY = Float.POSITIVE_INFINITY
                    ))
                    .then(
                        if (isSelected) Modifier.border(2.5.dp, accentColor,
                            RoundedCornerShape(topStart = CardR, topEnd = CardR))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(accentColor))
                if (isSelected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                            .clip(CircleShape).background(accentColor),
                        contentAlignment = Alignment.Center
                    ) { Text("✓", style = DW.LabelSmall.copy(color = Color.White)) }
                }
            }
            // Name
            Box(
                Modifier.fillMaxWidth().background(t.gt)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text(name, style = DW.LabelSmall.copy(color = t.ob), maxLines = 1, textAlign = TextAlign.Center) }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("导出 JSON") },
                onClick = {
                    showMenu = false
                        val json = ThemeManager.exportTheme(id)
                        if (json != null) {
                            com.hwb.aianswerer.utils.ClipboardUtil.copyToClipboard(
                                MyApplication.getAppContext(), json
                            )
                        }
                },
            )
            if (!isBuiltIn) {
                DropdownMenuItem(
                    text = { Text("删除", color = t.err) },
                    onClick = { showMenu = false; ThemeManager.removeCustomTheme(id) },
                )
            }
        }
    }
}

// =============================================================================
// Custom Theme Section
// =============================================================================

@Composable
private fun CustomThemeSection(t: Th, toastMsg: String?, setToast: (String?) -> Unit) {
    val showImport = remember { mutableStateOf(false) }
    val importText = remember { mutableStateOf("") }
    val importResult = remember { mutableStateOf<String?>(null) }

    Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("从 JSON 导入", style = DW.BodyMedium.copy(color = t.ob))
                Text("支持自定义配色方案", style = DW.BodySmall.copy(color = t.osv))
            }
            Box(
                Modifier.clip(RoundedCornerShape(ChipR)).background(t.p.copy(alpha = 0.12f))
                    .clickable {
                        importResult.value = null
                        importText.value = ""
                        showImport.value = true
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("导入", style = DW.LabelMedium.copy(color = t.p))
            }
        }

        if (showImport.value) {
            AlertDialog(
                onDismissRequest = { showImport.value = false; importResult.value = null },
                title = { Text("导入自定义主题", style = DW.TitleLarge.copy(color = t.ob)) },
                text = {
                    Column {
                        Text("粘贴主题 JSON（可从内置主题导出后修改）：",
                            style = DW.BodySmall.copy(color = t.osv),
                            modifier = Modifier.padding(bottom = 8.dp))
                        if (importResult.value != null) {
                            val ok = importResult.value!!.contains("成功")
                            Text(importResult.value!!,
                                style = DW.BodySmall.copy(color = if (ok) t.ok else t.err),
                                modifier = Modifier.padding(bottom = 8.dp))
                        }
                        OutlinedTextField(
                            value = importText.value,
                            onValueChange = { importText.value = it },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            placeholder = { Text("粘贴 JSON...", style = DW.BodySmall.copy(color = t.osv)) },
                            textStyle = DW.BodySmall.copy(color = t.ob),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = t.p,
                                unfocusedBorderColor = t.gb.copy(alpha = 0.5f),
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            val json = ThemeManager.exportTheme(ThemeManager.currentPresetId)
                            if (json != null) {
                                importText.value = json
                                importResult.value = "已导出当前主题 JSON，可修改后重新导入"
                            }
                        }) {
                            Text("导出当前主题 JSON", color = t.ac)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        when (val result = ThemeManager.importCustomTheme(importText.value)) {
                            is ThemeManager.ImportResult.Success -> {
                                ThemeManager.selectPreset(result.themeId)
                                importResult.value = "导入成功！主题已自动选中"
                            }
                            is ThemeManager.ImportResult.Error -> {
                                importResult.value = "导入失败：${result.message}"
                            }
                        }
                    }) { Text("导入", color = t.p) }
                },
                dismissButton = {
                    TextButton(onClick = { showImport.value = false; importResult.value = null }) {
                        Text("取消", color = t.osv)
                    }
                },
                containerColor = t.bg1,
            )
        }
    }
}
