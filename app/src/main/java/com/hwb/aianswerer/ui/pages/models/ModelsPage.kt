package com.hwb.aianswerer.ui.pages.models

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.providers.DynamicApiClient
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.ui.pages.TestState
import com.hwb.aianswerer.ui.theme.*
import com.hwb.aianswerer.utils.ModelWhitelistUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// Previews
// =============================================================================
@Preview(showSystemUi = true, showBackground = true, name = "模型厂商 — Light")
@Composable private fun ModelsLightPreview() { Themed { ModelsPage(it, {}) } }

@Preview(showSystemUi = true, showBackground = true, name = "模型厂商 — Dark")
@Composable private fun ModelsDarkPreview() { Themed(DH) { ModelsPage(it, {}) } }

// =============================================================================
// Page
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelsPage(t: Th, onBack: () -> Unit) {
    var searchText by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var whitelistUpdate by remember { mutableStateOf<WhitelistUpdateState>(WhitelistUpdateState.Idle) }
    val scope = rememberCoroutineScope()

    // 初始化厂商列表
    val providers = remember {
        val dynamicProviderModels = AppConfig.getDynamicProviderModels()
        val dynamicProviderConfigs = AppConfig.getDynamicProviderConfigs()

        // 合并静态厂商和动态厂商，自定义厂商置顶
        val allProviders = mutableListOf<ProviderDef>()
        val addedIds = mutableSetOf<String>()

        // 自定义厂商置顶
        val customProvider = MODEL_PROVIDERS.find { it.id == "custom" }
        if (customProvider != null) {
            allProviders.add(customProvider)
            addedIds.add(customProvider.id)
        }

        // 添加其他静态厂商（排除自定义，去重）
        for (def in MODEL_PROVIDERS) {
            if (def.id != "custom" && def.id !in addedIds) {
                allProviders.add(def)
                addedIds.add(def.id)
            }
        }

        // 添加动态厂商（去重，放在现有厂商后面）
        for (config in dynamicProviderConfigs) {
            if (config.id !in addedIds) {
                val dynamicModels = dynamicProviderModels[config.id] ?: emptyList()
                val type = when (config.type) {
                    "anthropic" -> ModelProviderType.ANTHROPIC
                    "ollama" -> ModelProviderType.OLLAMA
                    "gemini" -> ModelProviderType.GEMINI
                    else -> ModelProviderType.OPENAI
                }
                allProviders.add(ProviderDef(
                    id = config.id,
                    name = config.name,
                    type = type,
                    apiHost = config.apiHost,
                    availableModels = dynamicModels
                ))
                addedIds.add(config.id)
            }
        }

        mutableStateListOf(*allProviders.map { def ->
            val saved = ProviderStorage.getUserConfig(def.id)
            val apiKey = ProviderStorage.getUserApiKey(def.id)
            val baseModels = saved.availableModels.ifEmpty { def.availableModels }
            val dynamicModels = dynamicProviderModels[def.id] ?: emptyList()
            val allModels = (baseModels + dynamicModels).distinct()
            ProviderState(def.copy(availableModels = allModels), apiKey = apiKey, customHost = saved.customApiHost ?: "",
                enabled = saved.enabled, selectedModels = saved.selectedModels)
        }.toTypedArray())
    }
    var testStates by remember { mutableStateOf(mapOf<String, TestState>()) }
    var showSaveToast by remember { mutableStateOf<String?>(null) }
    var pickerProviderId by remember { mutableStateOf<String?>(null) }

    val filtered = providers.filter {
        searchText.isBlank() || it.def.name.contains(searchText, ignoreCase = true) || it.def.id.contains(searchText, ignoreCase = true)
    }

    // 用于自动滚动到展开的提供者
    val bringIntoViewRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }
    LaunchedEffect(expandedId) {
        expandedId?.let { id ->
            kotlinx.coroutines.delay(100)
            bringIntoViewRequesters[id]?.bringIntoView()
        }
    }

    Box(Modifier.fillMaxSize().background(
        Brush.linearGradient(listOf(t.bg1, t.bg2, t.bg3, t.bg4, t.bg5),
            start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
    )) {
        Column(Modifier.fillMaxSize()) {
            ModelsTopBar(t, onBack)
            Text("${providers.size} 厂商", style = DW.LabelSmall.copy(color = t.osv),
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 6.dp))
            ModelsSearchBar(t, searchText) { searchText = it }
            Spacer(Modifier.height(6.dp))

            // Whitelist update section
            Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.08f))
                        .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.2f else 0.06f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("获取模型更新", style = DW.LabelMedium.copy(color = t.ob))
                        Spacer(Modifier.height(2.dp))
                        Text("从 GitHub 同步最新的模型厂商和模型列表", style = DW.BodySmall.copy(color = t.osv, fontSize = 11.sp))
                    }
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale = remember { Animatable(1f) }
                    LaunchedEffect(pressed) {
                        if (pressed) scale.snapTo(0.85f)
                        else scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
                    }
                    val isChecking = whitelistUpdate is WhitelistUpdateState.Checking
                    Box(
                        Modifier.scale(scale.value).clip(RoundedCornerShape(12.dp))
                            .background(if (isChecking) t.p.copy(alpha = 0.15f) else t.p.copy(alpha = if (t.isLight) 0.1f else 0.15f))
                            .border(1.dp, t.p.copy(alpha = if (t.isLight) 0.3f else 0.2f), RoundedCornerShape(12.dp))
                            .clickable(interactionSource = interaction, indication = null) {
                                if (!isChecking) {
                                    whitelistUpdate = WhitelistUpdateState.Checking
                                    scope.launch {
                                        val result = ModelWhitelistUpdater.checkUpdate()
                                        if (result.success) {
                                            ModelCapabilityChecker.invalidateCache()
                                            // 更新厂商列表中的可用模型
                                            val dynamicProviderModels = AppConfig.getDynamicProviderModels()
                                            val dynamicProviderConfigs = AppConfig.getDynamicProviderConfigs()

                                            // 更新现有厂商的模型列表
                                            for (i in 0 until providers.size) {
                                                val ps = providers[i]
                                                val dynamicModels = dynamicProviderModels[ps.def.id] ?: emptyList()
                                                val allModels = (ps.def.availableModels + dynamicModels).distinct()
                                                providers[i] = ps.copy(def = ps.def.copy(availableModels = allModels))
                                            }

                                            // 添加新厂商（放在现有厂商后面，去重）
                                            val existingIds = providers.map { it.def.id }.toSet()

                                            for (config in dynamicProviderConfigs) {
                                                if (config.id !in existingIds) {
                                                    val dynamicModels = dynamicProviderModels[config.id] ?: emptyList()
                                                    val type = when (config.type) {
                                                        "anthropic" -> ModelProviderType.ANTHROPIC
                                                        "ollama" -> ModelProviderType.OLLAMA
                                                        "gemini" -> ModelProviderType.GEMINI
                                                        else -> ModelProviderType.OPENAI
                                                    }
                                                    val newDef = ProviderDef(
                                                        id = config.id,
                                                        name = config.name,
                                                        type = type,
                                                        apiHost = config.apiHost,
                                                        availableModels = dynamicModels
                                                    )
                                                    providers.add(ProviderState(newDef))
                                                }
                                            }

                                            whitelistUpdate = WhitelistUpdateState.Success(
                                                result.visionCount,
                                                result.excludedCount,
                                                result.totalCount,
                                                result.languageCount,
                                                result.providerCount,
                                                result.newProviderCount,
                                                result.newModelCount
                                            )
                                        } else {
                                            whitelistUpdate = WhitelistUpdateState.Fail(result.message)
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.p)
                        } else {
                            Text("检查更新", style = DW.LabelSmall.copy(color = t.p))
                        }
                    }
                }
            }

            // Whitelist update result
            when (val state = whitelistUpdate) {
                is WhitelistUpdateState.Success -> {
                    val providerInfo = if (state.newProviderCount > 0) "新增 ${state.newProviderCount} 个厂商" else "无新增厂商"
                    val modelInfo = if (state.newModelCount > 0) "新增 ${state.newModelCount} 个模型" else "无新增模型"
                    Text("更新完成：$providerInfo，$modelInfo",
                        style = DW.BodySmall.copy(color = t.ok, fontSize = 11.sp),
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp))
                }
                is WhitelistUpdateState.Fail -> {
                    Text("更新失败：${state.msg}",
                        style = DW.BodySmall.copy(color = t.err, fontSize = 11.sp),
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp))
                }
                else -> {}
            }

            Spacer(Modifier.height(4.dp))

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                filtered.forEach { ps ->
                    // 获取或创建该提供者的 bringIntoViewRequester
                    val requester = bringIntoViewRequesters.getOrPut(ps.def.id) { BringIntoViewRequester() }
                    ModelProviderCard(t = t, ps = ps, expanded = expandedId == ps.def.id,
                        testState = testStates[ps.def.id],
                        bringIntoViewRequester = requester,
                        onToggleExpand = { expandedId = if (expandedId == ps.def.id) null else ps.def.id },
                        onEnableToggle = {
                            val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                            if (idx >= 0) {
                                val c = providers[idx]
                                val newEnabled = !c.enabled
                                providers[idx] = c.copy(enabled = newEnabled)
                                ProviderStorage.saveUserConfig(ps.def.id, ProviderStorage.UserProviderConfig(
                                    enabled = newEnabled, customApiHost = ps.customHost.ifBlank { null },
                                    selectedModels = ps.selectedModels
                                ))
                            }
                        },
                        onApiKeyChange = { v ->
                            val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                            if (idx >= 0) providers[idx] = providers[idx].copy(apiKey = v)
                        },
                        onHostChange = { v ->
                            val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                            if (idx >= 0) providers[idx] = providers[idx].copy(customHost = v)
                        },
                        onTest = {
                            val host = ps.customHost.ifBlank { ps.def.apiHost }
                            val key = ps.apiKey
                            if (host.isBlank() || key.isBlank()) {
                                testStates = testStates + (ps.def.id to TestState.Error("请先填写 API Host 和 Key"))
                                return@ModelProviderCard
                            }
                            val model = ps.selectedModels.firstOrNull() ?: ps.def.availableModels.firstOrNull()
                            if (model.isNullOrBlank()) {
                                testStates = testStates + (ps.def.id to TestState.Error("请先添加并选择一个模型"))
                                return@ModelProviderCard
                            }
                            testStates = testStates + (ps.def.id to TestState.Testing)
                            scope.launch {
                                val typeStr = when (ps.def.type) {
                                    ModelProviderType.ANTHROPIC -> "anthropic"
                                    ModelProviderType.OLLAMA -> "ollama"
                                    else -> "openai"
                                }
                                val result = DynamicApiClient.testConnection(host, key, model, typeStr)
                                testStates = if (result.isSuccess) testStates + (ps.def.id to TestState.Success((result.getOrNull() ?: 0L)))
                                else testStates + (ps.def.id to TestState.Error(result.exceptionOrNull()?.message ?: "未知错误"))
                            }
                        },
                        onSave = {
                            val current = providers.firstOrNull { it.def.id == ps.def.id } ?: ps
                            val saved = ProviderStorage.getUserConfig(current.def.id)
                            ProviderStorage.saveUserApiKey(current.def.id, current.apiKey)
                            ProviderStorage.saveUserConfig(current.def.id, ProviderStorage.UserProviderConfig(
                                enabled = current.enabled, customApiHost = current.customHost.ifBlank { null },
                                selectedModels = current.selectedModels,
                                availableModels = current.def.availableModels.ifEmpty { saved.availableModels }
                            ))
                            showSaveToast = current.def.name
                        },
                        onOpenPicker = { pickerProviderId = ps.def.id },
                        onRemoveModel = { model ->
                            val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                            if (idx >= 0) {
                                val current = providers[idx]
                                val newModels = current.selectedModels - model
                                providers[idx] = current.copy(selectedModels = newModels)
                                val saved = ProviderStorage.getUserConfig(ps.def.id)
                                ProviderStorage.saveUserApiKey(ps.def.id, current.apiKey)
                                ProviderStorage.saveUserConfig(ps.def.id, ProviderStorage.UserProviderConfig(
                                    enabled = current.enabled, customApiHost = current.customHost.ifBlank { null },
                                    selectedModels = newModels,
                                    availableModels = current.def.availableModels.ifEmpty { saved.availableModels }
                                ))
                            }
                        }
                    )
                }
            }
        }

        showSaveToast?.let { name ->
            LaunchedEffect(name) { delay(1800); showSaveToast = null }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(t.p.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("$name 配置已保存", style = DW.LabelMedium.copy(color = Color.White))
                }
            }
        }

        pickerProviderId?.let { pid ->
            val ps = providers.find { it.def.id == pid }
            if (ps != null) {
                val typeStr = when (ps.def.type) {
                    ModelProviderType.ANTHROPIC -> "anthropic"
                    ModelProviderType.OLLAMA -> "ollama"
                    else -> "openai"
                }
                ModelPickerDialog(t = t, providerName = ps.def.name, providerType = typeStr,
                    apiHost = ps.customHost.ifBlank { ps.def.apiHost }, apiKey = ps.apiKey,
                    availableModels = ps.def.availableModels, initiallySelected = ps.selectedModels.toSet(),
                    onDismiss = { pickerProviderId = null },
                    onConfirm = { selected, fetchedModels ->
                        val idx = providers.indexOfFirst { it.def.id == pid }
                        if (idx >= 0) {
                            val current = providers[idx]
                            providers[idx] = current.copy(
                                selectedModels = selected,
                                def = current.def.copy(availableModels = fetchedModels)
                            )
                            ProviderStorage.saveUserApiKey(pid, current.apiKey)
                            ProviderStorage.saveUserConfig(pid, ProviderStorage.UserProviderConfig(
                                enabled = current.enabled, customApiHost = current.customHost.ifBlank { null },
                                selectedModels = selected,
                                availableModels = fetchedModels
                            ))
                        }
                        pickerProviderId = null
                    }
                )
            }
        }
    }
}
