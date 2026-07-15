package com.hwb.aianswerer.providers

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.api.vision.OpenAIVisionConfig
import com.hwb.aianswerer.api.vision.OpenAIVisionProvider
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.AnimatedButton
import com.hwb.aianswerer.ui.components.AppTextField
import com.hwb.aianswerer.ui.components.ButtonVariant
import com.hwb.aianswerer.ui.components.PasswordTextField
import com.hwb.aianswerer.ui.components.PremiumToggle
import com.hwb.aianswerer.ui.components.SectionLabel
import com.hwb.aianswerer.ui.components.TopBarWithBack
import com.hwb.aianswerer.ui.pages.TestState
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 视觉模型厂商设置 — 复用 Cherry Studio 厂商列表，保存到 AppConfig 视觉配置
 */
class VisionProviderSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProviderStorage.init(applicationContext)
        ProviderStorage.initSecurePrefs(applicationContext)

        setContent {
            AIAnswererTheme {
                VisionProviderSettingsScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisionProviderSettingsScreen(onBackClick: () -> Unit) {
    var providers by remember { mutableStateOf(loadVisionProviders()) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var visionEnabled by remember { mutableStateOf(AppConfig.isVisionEnabled()) }
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalIsDarkMode.current

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val result = ProviderSyncManager.sync(MyApplication.getAppContext())
            if (result is ProviderSyncManager.SyncResult.Updated) {
                providers = loadVisionProviders()
            }
        }
    }

    val filtered = if (searchQuery.isBlank()) providers
    else providers.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopBarWithBack(
                title = stringResource(R.string.vision_provider_settings_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) PremiumBgDark else PremiumBgLight)
                .padding(paddingValues)
        ) {
            // 全局启用开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxl, vertical = Spacing.md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vision_enable_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                    Text(
                        text = stringResource(R.string.vision_provider_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextDarkTertiary else TextTertiary
                    )
                }
                PremiumToggle(
                    checked = visionEnabled,
                    onCheckedChange = {
                        visionEnabled = it
                        AppConfig.saveVisionEnabled(it)
                    }
                )
            }

            AnimatedVisibility(
                visible = visionEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    // 搜索框
                    VisionSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xxl, vertical = Spacing.sm)
                    )

                    // 统计
                    Text(
                        text = "${providers.size} 厂商 · ${providers.sumOf { it.models.size }} 模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) TextDarkTertiary else TextTertiary,
                        modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.xxl, bottom = Spacing.md)
                    )

                    // 列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(filtered, key = { it.id }) { provider ->
                            VisionProviderCard(
                                provider = provider,
                                isDark = isDark,
                                expanded = expandedId == provider.id,
                                onToggleExpand = {
                                    expandedId = if (expandedId == provider.id) null else provider.id
                                }
                            )
                        }
                        item { Spacer(Modifier.height(Spacing.xxxl)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisionSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkMode.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(stringResource(R.string.provider_search_hint), color = if (isDark) TextDarkTertiary else TextTertiary)
        },
        leadingIcon = {
            Text("🔍", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.xs))
        },
        singleLine = true,
        modifier = modifier.clip(RoundedCornerShape(Spacing.lg)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = if (isDark) PremiumSurfaceDark else PremiumCardLight,
            unfocusedContainerColor = if (isDark) PremiumSurfaceDark else PremiumCardLight,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun VisionProviderCard(
    provider: LocalProviderConfig,
    isDark: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val cardBg = if (isDark) PremiumSurfaceDark else PremiumCardLight
    val cornerRadius = Spacing.lg
    val isCurrentProvider = AppConfig.getVisionBaseUrl().contains(provider.id, ignoreCase = true) ||
            AppConfig.getVisionBaseUrl().contains(provider.apiHost, ignoreCase = true)

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
        label = "arrowRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .shadowCard(cornerRadius)
            .clip(RoundedCornerShape(cornerRadius))
            .background(cardBg)
            .padding(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpand
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumPrimary,
                        modifier = Modifier.rotate(arrowRotation).padding(end = Spacing.sm)
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    VisionTypeBadge(provider.type, isDark)
                    if (isCurrentProvider) {
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SuccessGreen.copy(alpha = 0.15f))
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        ) {
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                val currentModel = AppConfig.getVisionModelName()
                if (currentModel.isNotBlank() && isCurrentProvider) {
                    Text(
                        text = currentModel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                    )
                } else {
                    Text(
                        text = "${provider.models.size} 个模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) TextDarkTertiary else TextTertiary,
                        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = 280f)) + fadeIn(tween(200)),
            exit = shrinkVertically(spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeOut(tween(150))
        ) {
            VisionExpandedContent(provider = provider, isDark = isDark)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisionExpandedContent(
    provider: LocalProviderConfig,
    isDark: Boolean
) {
    var apiKey by remember(provider.id) { mutableStateOf(AppConfig.getVisionApiKey()) }
    var apiHost by remember(provider.id) { mutableStateOf(if (AppConfig.getVisionBaseUrl().isNotBlank()) AppConfig.getVisionBaseUrl() else provider.apiHost) }
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    var selectedModel by remember(provider.id) { mutableStateOf(AppConfig.getVisionModelName().ifBlank { null }) }
    val coroutineScope = rememberCoroutineScope()

    val allModels = provider.models.map { it.id }

    Column(modifier = Modifier.padding(top = Spacing.lg)) {
        HorizontalDivider(
            color = if (isDark) PremiumSurfaceDarkBorder else PremiumSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.lg)
        )

        SectionLabel(stringResource(R.string.label_api_url))
        Spacer(Modifier.height(Spacing.xs))
        AppTextField(
            value = apiHost,
            onValueChange = { apiHost = it },
            label = "",
            placeholder = provider.apiHost,
            isPassword = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.md))

        SectionLabel(stringResource(R.string.label_api_key))
        Spacer(Modifier.height(Spacing.xs))
        PasswordTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "",
            placeholder = stringResource(R.string.hint_api_key),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.lg))

        // 测试连接
        AnimatedButton(
            text = if (testState is TestState.Testing) "测试中…" else stringResource(R.string.button_test_connection),
            onClick = {
                coroutineScope.launch {
                    testState = TestState.Testing
                    try {
                        val config = OpenAIVisionConfig(
                            baseUrl = apiHost,
                            apiKey = apiKey,
                            modelName = selectedModel ?: allModels.firstOrNull() ?: "gpt-4o"
                        )
                        val pv = OpenAIVisionProvider.getInstance(config)
                        val result = pv.testConnection()
                        testState = result.fold(
                            onSuccess = { TestState.Success(0) },
                            onFailure = { TestState.Error(it.message ?: "Unknown") }
                        )
                    } catch (e: Exception) {
                        testState = TestState.Error(e.message ?: "Unknown")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Tonal,
            enabled = testState !is TestState.Testing && apiKey.isNotBlank()
        )

        when (val s = testState) {
            is TestState.Success -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(SuccessGreen))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = if (s.ms > 0) "连接成功 · ${s.ms}ms" else "连接成功",
                        style = MaterialTheme.typography.bodyMedium, color = SuccessGreen, fontWeight = FontWeight.Medium
                    )
                }
            }
            is TestState.Error -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(ErrorRed))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(text = s.msg, style = MaterialTheme.typography.bodyMedium, color = ErrorRed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(Spacing.lg))

        // 模型列表
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.provider_section_models))
            if (selectedModel != null) {
                Spacer(Modifier.width(Spacing.sm))
                Text("当前: $selectedModel", style = MaterialTheme.typography.labelLarge, color = PremiumPrimary, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(Spacing.sm))

        if (allModels.isEmpty()) {
            Text(
                text = if (provider.type == "anthropic") "Anthropic 请手动输入模型名称"
                else "点击上方输入 API Key 后选择模型",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) TextDarkTertiary else TextTertiary
            )
        } else {
            val grouped = provider.models.groupBy { it.group }.mapValues { (_, v) -> v.map { it.id } }.toSortedMap()
            grouped.forEach { (group, ids) ->
                if (group.isNotBlank()) {
                    Text(
                        text = group, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = PremiumPrimary, modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ids.forEach { id ->
                        VisionModelChip(
                            id = id, selected = id == selectedModel, isDark = isDark,
                            onClick = {
                                selectedModel = id
                                // 保存到 AppConfig 视觉配置
                                AppConfig.saveVisionBaseUrl(apiHost)
                                AppConfig.saveVisionApiKey(apiKey)
                                AppConfig.saveVisionModelName(id)
                                AppConfig.saveVisionEnabled(true)
                            }
                        )
                    }
                }
            }
        }

        // 手动输入
        if (allModels.isEmpty() || provider.type == "anthropic") {
            Spacer(Modifier.height(Spacing.lg))
            var manualModel by remember(provider.id) { mutableStateOf(selectedModel ?: "") }
            AppTextField(
                value = manualModel, onValueChange = { manualModel = it },
                label = "手动输入模型名称", placeholder = "例如 claude-sonnet-4-20250514",
                isPassword = false, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            AnimatedButton(
                text = "确认",
                onClick = {
                    selectedModel = manualModel
                    AppConfig.saveVisionBaseUrl(apiHost)
                    AppConfig.saveVisionApiKey(apiKey)
                    AppConfig.saveVisionModelName(manualModel)
                    AppConfig.saveVisionEnabled(true)
                },
                modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Tonal, enabled = manualModel.isNotBlank()
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        // 保存
        AnimatedButton(
            text = stringResource(R.string.button_save),
            onClick = {
                AppConfig.saveVisionBaseUrl(apiHost)
                AppConfig.saveVisionApiKey(apiKey)
                if (selectedModel != null) AppConfig.saveVisionModelName(selectedModel!!)
                AppConfig.saveVisionEnabled(true)
                Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Primary
        )

        // 链接
        provider.websites?.let { websites ->
            Spacer(Modifier.height(Spacing.xl))
            SectionLabel(stringResource(R.string.provider_section_links))
            Spacer(Modifier.height(Spacing.sm))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Spacing.md))
                    .background(if (isDark) PremiumSurfaceDark.copy(alpha = 0.5f) else PremiumSurfaceVariant.copy(alpha = 0.5f))
                    .padding(Spacing.md)
            ) {
                websites.official?.let { VisionLinkRow("官网", it, isDark) }
                if (websites.official != null && websites.apiKey != null) {
                    HorizontalDivider(color = if (isDark) PremiumSurfaceDarkBorder else PremiumSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs))
                }
                websites.apiKey?.let { VisionLinkRow("获取 API Key", it, isDark) }
                websites.docs?.let {
                    HorizontalDivider(color = if (isDark) PremiumSurfaceDarkBorder else PremiumSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs))
                    VisionLinkRow("文档", it, isDark)
                }
                websites.models?.let {
                    HorizontalDivider(color = if (isDark) PremiumSurfaceDarkBorder else PremiumSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs))
                    VisionLinkRow("模型列表", it, isDark)
                }
            }
        }
    }
}

@Composable
private fun VisionModelChip(id: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val bgColor = animateColorAsState(
        targetValue = if (selected) PremiumPrimary else if (isDark) PremiumSurfaceDark else PremiumSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f), label = "chipBg"
    )
    val textColor = animateColorAsState(
        targetValue = if (selected) Color.White else if (isDark) TextDarkPrimary else TextDark,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f), label = "chipText"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "chipScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .shadowSubtle(Spacing.sm)
            .clip(RoundedCornerShape(Spacing.sm))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        if (selected) {
            Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = id, style = MaterialTheme.typography.bodyMedium, color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VisionTypeBadge(type: String, isDark: Boolean) {
    val (bg, fg) = when (type) {
        "anthropic" -> Color(0xFFD4A574) to Color.White
        "gemini" -> Color(0xFF4285F4) to Color.White
        "openai" -> Color(0xFF10A37F) to Color.White
        "openai-response" -> Color(0xFF10A37F) to Color.White
        "ollama" -> (if (isDark) Color(0xFF3A3A4A) else Color(0xFFF0F0F0)) to (if (isDark) TextDarkSecondary else Color(0xFF555555))
        "azure-openai" -> Color(0xFF0078D4) to Color.White
        "mistral" -> Color(0xFFFF7000) to Color.White
        else -> PremiumPrimary.copy(alpha = 0.15f) to PremiumPrimary
    }
    Text(
        text = type, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = Spacing.sm, vertical = 2.dp)
    )
}

@Composable
private fun VisionLinkRow(label: String, url: String, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.xs))
            .clickable {
                try {
                    MyApplication.getAppContext().startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isDark) TextDarkSecondary else TextSecondary, modifier = Modifier.width(100.dp))
        Text(url, style = MaterialTheme.typography.bodyMedium, color = PremiumPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun animateColorAsState(targetValue: Color, animationSpec: androidx.compose.animation.core.AnimationSpec<Color>, label: String): Color {
    return androidx.compose.animation.animateColorAsState(targetValue = targetValue, animationSpec = animationSpec, label = label).value
}


private fun loadVisionProviders(): List<LocalProviderConfig> {
    return ProviderStorage.getMergedProviders().ifEmpty {
        try {
            val ctx = MyApplication.getAppContext()
            val json = ctx.assets.open("provider_data.json").bufferedReader().use { it.readText() }
            val data = com.hwb.aianswerer.utils.JsonUtil.gson.fromJson(json, ProviderDataJson::class.java)
            if (data != null) {
                ProviderStorage.saveProviderData(data)
                ProviderStorage.getMergedProviders()
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
