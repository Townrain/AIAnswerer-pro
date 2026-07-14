package com.hwb.aianswerer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.providers.WebSearchStorage
import com.hwb.aianswerer.ScreenReaderService
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

// =============================================================================
// Previews
// =============================================================================
@Preview(showSystemUi = true, showBackground = true, name = "主页 — Light")
@Composable private fun HomeLightPreview() = Themed { t -> HomePage(t, {}, {}) }

@Preview(showSystemUi = true, showBackground = true, name = "主页 — Dark")
@Composable private fun HomeDarkPreview() = Themed(DH) { t -> HomePage(t, {}, {}) }

// =============================================================================
// Animation helpers
// =============================================================================
private val A400: TweenSpec<Float> = tween(400, easing = FastOutSlowInEasing)
private val C400: TweenSpec<Color> = tween(400, easing = FastOutSlowInEasing)

// ── Settings icon (gear) ──
private val SettingsIcon: ImageVector by lazy {
    ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19.14f, 12.94f); curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
            curveToRelative(0f, -0.33f, -0.02f, -0.64f, -0.06f, -0.94f); lineToRelative(2.02f, -1.58f)
            curveToRelative(0.18f, -0.14f, 0.23f, -0.38f, 0.12f, -0.56f); lineToRelative(-1.89f, -3.28f)
            curveToRelative(-0.12f, -0.19f, -0.36f, -0.26f, -0.56f, -0.18f); lineToRelative(-2.38f, 0.96f)
            curveToRelative(-0.5f, -0.38f, -1.06f, -0.68f, -1.66f, -0.88f); lineTo(14.45f, 3.5f)
            curveToRelative(-0.04f, -0.2f, -0.2f, -0.34f, -0.4f, -0.34f); horizontalLineToRelative(-3.78f)
            curveToRelative(-0.2f, 0f, -0.36f, 0.14f, -0.4f, 0.34f); lineToRelative(-0.3f, 2.52f)
            curveToRelative(-0.6f, 0.2f, -1.16f, 0.5f, -1.66f, 0.88f); lineToRelative(-2.38f, -0.96f)
            curveToRelative(-0.2f, -0.08f, -0.44f, -0.01f, -0.56f, 0.18f); lineToRelative(-1.89f, 3.28f)
            curveToRelative(-0.12f, 0.19f, -0.07f, 0.42f, 0.12f, 0.56f); lineToRelative(2.02f, 1.58f)
            curveToRelative(-0.04f, 0.3f, -0.06f, 0.61f, -0.06f, 0.94f); curveToRelative(0f, 0.33f, 0.02f, 0.64f, 0.06f, 0.94f)
            lineToRelative(-2.02f, 1.58f); curveToRelative(-0.18f, 0.14f, -0.23f, 0.38f, -0.12f, 0.56f)
            lineToRelative(1.89f, 3.28f); curveToRelative(0.12f, 0.19f, 0.36f, 0.26f, 0.56f, 0.18f)
            lineToRelative(2.38f, -0.96f); curveToRelative(0.5f, 0.38f, 1.06f, 0.68f, 1.66f, 0.88f)
            lineToRelative(0.3f, 2.52f); curveToRelative(0.04f, 0.2f, 0.2f, 0.34f, 0.4f, 0.34f)
            horizontalLineToRelative(3.78f); curveToRelative(0.2f, 0f, 0.36f, -0.14f, 0.4f, -0.34f)
            lineToRelative(0.3f, -2.52f); curveToRelative(0.6f, -0.2f, 1.16f, -0.5f, 1.66f, -0.88f)
            lineToRelative(2.38f, 0.96f); curveToRelative(0.2f, 0.08f, 0.44f, 0.01f, 0.56f, -0.18f)
            lineToRelative(1.89f, -3.28f); curveToRelative(0.12f, -0.19f, 0.07f, -0.42f, -0.12f, -0.56f)
            lineToRelative(-2.02f, -1.58f); close(); moveTo(12f, 15f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f); close()
        }
    }.build()
}

// ── Gradient helper ──
private fun g(t: Th) = Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite)

// ── Bouncy click modifier ──
@Composable
private fun Modifier.bouncyClick(onClick: () -> Unit): Modifier {
    val scale = remember { Animatable(1f) }
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scale.snapTo(0.88f)
                    tryAwaitRelease()
                    scale.animateTo(1f, spring(dampingRatio = 0.2f, stiffness = 400f))
                },
                onTap = { onClick() }
            )
        }
}

// =============================================================================
// Home Page
// =============================================================================
@Composable
fun HomePage(t: Th, onSettingsClick: () -> Unit, onStartClick: () -> Unit, isAnswerModeActive: Boolean = false, onStopClick: () -> Unit = {}) {
    val bgGradient = Brush.linearGradient(
        listOf(t.bg1, t.bg2, t.bg3, t.bg4, t.bg5),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val scrollState = rememberScrollState()
    val expandedMenu = remember { mutableStateOf<String?>(null) }

    // Shared capture mode state — SettingsCard and CaptureModeCard both observe this
    val captureMode = remember { mutableStateOf(if (AppConfig.isAccessibilityCaptureMode()) "屏幕读取" else "截图模式") }

    // 从设置页返回时强制刷新模型菜单数据
    var resumeVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(bgGradient)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(top = 145.dp)
        ) {
            Box(Modifier.zIndex(12f)) { key(resumeVersion) { MergedCard(t, expandedMenu) } }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.zIndex(1f)) { SettingsCard(t, captureMode) }
            Spacer(Modifier.height(16.dp))
            CaptureModeCard(t, scrollState, captureMode)
            // CTA 按钮高度预留空间
            Spacer(Modifier.height(80.dp))
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(t.bg1, t.bg2)))) {
            TitleSection(t, onSettingsClick = onSettingsClick)
        }
        CtaBar(t, Modifier.align(Alignment.BottomCenter), onStartClick, isAnswerModeActive, onStopClick)
    }
}

// =============================================================================
// Title section — "AI Answer" horizontal (gradient), settings gear right
// =============================================================================
@Composable
private fun TitleSection(t: Th, onSettingsClick: () -> Unit) {
    val titleBrush = if (t.isLight) Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite)
        else Brush.linearGradient(listOf(Dc.TitleBg1, Dc.TitleBg2, Dc.TitleBg3, Dc.TitleBg4, Dc.TitleBg5), start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
    Row(
        Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 12.dp, start = 28.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f)) {
            Text("AI", style = DW.DisplayLarge.copy(brush = titleBrush, fontSize = 56.sp, lineHeight = 62.sp), modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(10.dp))
            Text("Answer", style = DW.HeadlineMedium.copy(brush = titleBrush, fontSize = 34.sp, lineHeight = 40.sp), modifier = Modifier.alignByBaseline())
        }
        Box(
            Modifier.size(56.dp).bouncyClick(onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(SettingsIcon, "设置", tint = t.osv, modifier = Modifier.size(32.dp))
        }
    }
}

// =============================================================================
// Merged Card — upper: service status, lower: 2×2 model menus
// =============================================================================
@Composable
private fun MergedCard(t: Th, expandedMenu: MutableState<String?>) {
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
    LaunchedEffect(outputLang.value) {
        AppConfig.saveOutputLanguage(outputLang.value)
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
                ModelMenu("输出语言", outputLang, listOf("中文", "English", "日本語", "自动识别"), outEx, t, Modifier.weight(1f)) { expandedMenu.value = if (expandedMenu.value == "输出语言") null else "输出语言" }
            }
        }
    }
}

// =============================================================================
// Settings Card — upper: question types, lower: screenshot recognition mode
// =============================================================================
@Composable
private fun SettingsCard(t: Th, captureMode: MutableState<String>) {
    val questionTypes = remember { AppConfig.getQuestionTypes().toMutableStateList() }
    val cropMode = remember { mutableStateOf(AppConfig.getCropMode()) }

    val allTypes = listOf("单选题", "多选题", "不定项", "填空题", "问答题")
    // (code, label) — saves English code to match service constants
    val cropModes = listOf(
        AppConfig.CROP_MODE_FULL to "全屏识别",
        AppConfig.CROP_MODE_EACH to "部分识别（每次）",
        AppConfig.CROP_MODE_ONCE to "部分识别（单次）"
    )

    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
            .border(1.dp, t.gb, RoundedCornerShape(CardR))
            .padding(CardPad)
    ) {
        Column {
            // ── Upper: Question types ──
            Text("题型选择", style = DW.TitleMedium.copy(color = t.ob))
            Spacer(Modifier.height(12.dp))
            // 3 + 2 layout, chips fill each row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTypes.take(3).forEach { type ->
                    key(type) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(type, selected = type in questionTypes, t) {
                                if (type in questionTypes) {
                                    if (questionTypes.size > 1) questionTypes.remove(type)
                                } else {
                                    questionTypes.add(type)
                                }
                                AppConfig.saveQuestionTypes(questionTypes.toSet())
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTypes.drop(3).forEach { type ->
                    key(type) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(type, selected = type in questionTypes, t) {
                                if (type in questionTypes) {
                                    if (questionTypes.size > 1) questionTypes.remove(type)
                                } else {
                                    questionTypes.add(type)
                                }
                                AppConfig.saveQuestionTypes(questionTypes.toSet())
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = t.ac.copy(alpha = 0.15f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 16.dp))

            // ── Lower: Screenshot recognition mode ──
            val isAccessMode = captureMode.value == "屏幕读取"
            Text("截图识别模式", style = DW.TitleMedium.copy(color = if (isAccessMode) t.ac.copy(alpha = 0.4f) else t.ob))
            Spacer(Modifier.height(12.dp))
            if (isAccessMode) {
                Text("屏幕读取模式下不可用", style = DW.BodySmall.copy(color = t.ac.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cropModes.forEach { (code, label) ->
                    key(code) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(label, selected = cropMode.value == code && !isAccessMode, t) {
                                if (!isAccessMode) { cropMode.value = code; AppConfig.saveCropMode(code) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Capture Mode Card — 采集模式
// =============================================================================
@Composable
private fun CaptureModeCard(t: Th, scrollState: androidx.compose.foundation.ScrollState, captureMode: MutableState<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityEnabled by remember { mutableStateOf(ScreenReaderService.isAccessibilityServiceEnabled(context)) }
    // Re-check accessibility status when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = ScreenReaderService.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
            .border(1.dp, t.gb, RoundedCornerShape(CardR))
            .padding(CardPad)
    ) {
        Column {
            Text("采集模式", style = DW.TitleMedium.copy(color = t.ob))
            Spacer(Modifier.height(12.dp))
            Text("题目识别方式", style = DW.BodySmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("截图模式", "屏幕读取").forEach { mode ->
                    key(mode) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(mode, selected = captureMode.value == mode, t) {
                                captureMode.value = mode
                                AppConfig.saveCaptureMode(
                                    if (mode == "屏幕读取") AppConfig.CAPTURE_MODE_ACCESSIBILITY
                                    else AppConfig.CAPTURE_MODE_SCREENSHOT
                                )
                                isAccessibilityEnabled = ScreenReaderService.isAccessibilityServiceEnabled(context)
                            }
                        }
                    }
                }
            }
            if (captureMode.value == "屏幕读取") {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isAccessibilityEnabled) t.ok else t.err))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isAccessibilityEnabled) "无障碍服务: 已启用" else "无障碍服务: 未启用",
                        style = DW.BodySmall.copy(color = if (isAccessibilityEnabled) t.ok else t.err)
                    )
                }
                if (!isAccessibilityEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请在系统设置中开启 AI答题助手 的无障碍服务 →",
                        style = DW.BodySmall.copy(color = t.p),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(captureMode.value) {
        if (captureMode.value == "屏幕读取") {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}

// =============================================================================
// Select Chip
// =============================================================================
@Composable
fun SelectChip(label: String, selected: Boolean, t: Th, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) (if (t.isLight) t.p.copy(alpha = 0.40f) else t.ua.copy(alpha = 0.4f))
        else t.gb.copy(alpha = if (t.isLight) 0.65f else 0.06f),
        tween(250, easing = FastOutSlowInEasing), label = "scBg"
    )
    val fg by animateColorAsState(
        if (selected) (if (t.isLight) t.p else t.ual) else t.osv,
        tween(250, easing = FastOutSlowInEasing), label = "scFg"
    )
    val border by animateColorAsState(
        if (selected) (if (t.isLight) t.p.copy(alpha = 0.7f) else t.ua.copy(alpha = 0.65f))
        else t.ac.copy(alpha = if (t.isLight) 0.48f else 0.08f),
        tween(250, easing = FastOutSlowInEasing), label = "scBord"
    )
    val selScale by animateFloatAsState(
        if (selected) 1.03f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "scSel"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed) 0.88f else 1f,
        spring(dampingRatio = 0.2f, stiffness = 500f),
        label = "scPress"
    )

    Box(
        Modifier.fillMaxWidth().scale(selScale * pressScale).clip(RoundedCornerShape(ChipR)).background(bg)
            .border(1.dp, border, RoundedCornerShape(ChipR))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = DW.LabelLarge.copy(color = fg, textAlign = TextAlign.Center))
    }
}

// =============================================================================
// Model menu button + Popup dropdown
// Button tracks its window position; Popup renders in separate window above everything
// =============================================================================
@Composable
private fun ModelMenu(label: String, value: MutableState<String>, options: List<String>, expanded: State<Boolean>, t: Th, modifier: Modifier, hint: String? = null, onToggle: () -> Unit) {
    val chevronRot by animateFloatAsState(if (expanded.value) 180f else 0f, spring(dampingRatio = 0.55f, stiffness = 400f), label = "cr")
    val btnBg by animateColorAsState(
        if (expanded.value) t.gb.copy(alpha = if (t.isLight) 0.82f else 0.22f)
        else t.gb.copy(alpha = if (t.isLight) 0.65f else 0.08f),
        C400, label = "bbg"
    )
    val btnBorder by animateColorAsState(
        if (expanded.value) t.gb.copy(alpha = if (t.isLight) 0.92f else 0.5f)
        else t.ac.copy(alpha = if (t.isLight) 0.55f else 0.08f),
        C400, label = "bbr"
    )

    // Track button position in window for popup placement
    var btnBounds by remember { mutableStateOf(Rect.Zero) }

    // Popup lifecycle: stays alive during close animation
    var showPopup by remember { mutableStateOf(false) }
    val dropAnim = remember { Animatable(0f) }
    LaunchedEffect(expanded.value) {
        if (expanded.value) {
            showPopup = true
            dropAnim.snapTo(0f)
            dropAnim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 350f))
        } else if (showPopup) {
            dropAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
            showPopup = false
        }
    }

    // Solid frosted glass — fully opaque, nothing shows through
    val dropBg = Brush.verticalGradient(
        listOf(
            if (t.isLight) Color(0xFFFDF8F0) else Color(0xFF3A2E3E),
            if (t.isLight) Color(0xFFF6EEE4) else Color(0xFF302636)
        ),
        endY = Float.POSITIVE_INFINITY
    )
    val dropBorder = Brush.verticalGradient(
        listOf(
            if (t.isLight) Color(0xFFFFFFFF) else Color(0x66FFFFFF),
            if (t.isLight) Color(0xFFC8BEB4) else Color(0x30FFFFFF)
        ),
        endY = Float.POSITIVE_INFINITY
    )

    Box(modifier = modifier) {
        // Button — same visual, added onGloballyPositioned for window coords
        Box(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { btnBounds = it.boundsInWindow() }
                .clip(RoundedCornerShape(16.dp)).background(btnBg)
                .border(1.dp, btnBorder, RoundedCornerShape(16.dp))
                .bouncyClick { onToggle() }.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = DW.LabelSmall.copy(color = t.osv))
                    Spacer(Modifier.height(1.dp))
                    Text(value.value, style = DW.BodyMedium.copy(color = t.ob))
                }
                Text("▾", style = DW.LabelMedium.copy(color = t.osv), modifier = Modifier.graphicsLayer { rotationZ = chevronRot })
            }
        }

        // Dropdown via Popup — independent window, immune to parent zIndex issues
        if (showPopup && btnBounds.width > 0f) {
            val density = LocalDensity.current
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset = IntOffset(btnBounds.left.toInt(), btnBounds.bottom.toInt())
                },
                onDismissRequest = onToggle,
                properties = PopupProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    Modifier.width(with(density) { btnBounds.width.toDp() })
                        .graphicsLayer {
                            alpha = dropAnim.value
                            scaleY = dropAnim.value
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(dropBg).border(1.dp, dropBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            Modifier.padding(vertical = 4.dp)
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (hint != null) {
                                Text(hint, style = DW.LabelSmall.copy(color = t.osv, fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            options.forEach { opt ->
                                val sel = opt == value.value
                                val interactionSource = remember(opt) { MutableInteractionSource() }
                                val isHovered by interactionSource.collectIsHoveredAsState()
                                val isPressed by interactionSource.collectIsPressedAsState()

                                val itemScale by animateFloatAsState(
                                    when {
                                        isPressed -> 0.92f
                                        isHovered && !sel -> 1.03f
                                        else -> 1f
                                    },
                                    spring(dampingRatio = 0.2f, stiffness = 400f),
                                    label = "itemSc"
                                )
                                val itemBg by animateColorAsState(
                                    when {
                                        sel -> t.p.copy(alpha = 0.14f)
                                        isPressed -> t.p.copy(alpha = 0.08f)
                                        isHovered -> if (t.isLight) t.gb.copy(alpha = 0.45f) else t.ac.copy(alpha = 0.10f)
                                        else -> Color.Transparent
                                    },
                                    tween(250),
                                    label = "itemBg"
                                )
                                val itemFg by animateColorAsState(
                                    when {
                                        sel -> t.p
                                        isHovered -> if (t.isLight) t.p.copy(alpha = 0.8f) else t.ual.copy(alpha = 0.9f)
                                        else -> t.ob
                                    },
                                    tween(250),
                                    label = "itemFg"
                                )

                                Text(opt,
                                    style = (if (sel) DW.LabelMedium else DW.BodySmall).copy(color = itemFg),
                                    modifier = Modifier.fillMaxWidth()
                                        .scale(itemScale)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBg)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = { value.value = opt; onToggle() }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// CTA Bar — gradient "进入答题模式" replacing bottom nav
// =============================================================================
@Composable
private fun CtaBar(t: Th, m: Modifier, onStartClick: () -> Unit, isAnswerModeActive: Boolean, onStopClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "cta")
    val pulse by inf.animateFloat(1f, 1.03f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bounceScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.2f, stiffness = 400f),
        label = "ctaBounce"
    )

    Surface(
        m.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    scaleX = pulse * bounceScale
                    scaleY = pulse * bounceScale
                }
                .clip(RoundedCornerShape(32.dp))
                .background(
                    if (isAnswerModeActive) Brush.linearGradient(listOf(Color(0xFFFF3B30), Color(0xFFD32F2F)), Offset.Zero, Offset.Infinite)
                    else if (t.isLight) Brush.linearGradient(listOf(Color(0xFFC4A8D0), Color(0xFFD4B898)), Offset.Zero, Offset.Infinite)
                    else g(t)
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (isAnswerModeActive) onStopClick() else onStartClick()
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isAnswerModeActive) "退出答题模式" else "进入答题模式",
                style = DW.LabelLarge.copy(color = Color.White)
            )
        }
    }
}
