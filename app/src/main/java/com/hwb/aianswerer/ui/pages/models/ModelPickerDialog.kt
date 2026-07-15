package com.hwb.aianswerer.ui.pages.models

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.providers.DynamicApiClient
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// Model Picker Dialog
// =============================================================================
@Composable
internal fun ModelPickerDialog(
    t: Th, providerName: String, providerType: String, apiHost: String, apiKey: String,
    availableModels: List<String>, initiallySelected: Set<String>,
    onDismiss: () -> Unit, onConfirm: (selected: List<String>, fetchedModels: List<String>) -> Unit
) {
    var loading by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(availableModels) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var hasFetched by remember { mutableStateOf(availableModels.isNotEmpty()) }
    val selected = remember { mutableStateListOf(*initiallySelected.toTypedArray()) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnConfirm by rememberUpdatedState(onConfirm)

    fun fetchModels() {
        if (apiHost.isBlank() || apiKey.isBlank()) {
            loadError = "请先填写 API Host 和 Key"; return
        }
        loading = true; loadError = null
        scope.launch {
            val result = DynamicApiClient.fetchModelList(apiHost, apiKey, providerType)
            if (result.isSuccess) {
                val fetched = result.getOrNull() ?: emptyList()
                if (fetched.isNotEmpty()) {
                    models = fetched
                    fetchedModels = fetched
                    hasFetched = true
                } else loadError = "返回列表为空"
            } else {
                loadError = result.exceptionOrNull()?.message ?: "获取失败"
            }
            loading = false
        }
    }

    // No auto-fetch; user clicks refresh to load models
    LaunchedEffect(Unit) { /* no-op: models loaded on demand */ }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss
    )) {
        Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp))
                    .background(
                        if (t.isLight) Brush.verticalGradient(listOf(Color(0xFFF8F4F0), Color(0xFFF0EAE4)), endY = Float.POSITIVE_INFINITY)
                        else Brush.verticalGradient(listOf(Color(0xFF2A2030), Color(0xFF221A26)), endY = Float.POSITIVE_INFINITY)
                    )
                    .border(1.dp, if (t.isLight) Color(0xFFE0D8D0) else Color(0x40FFFFFF), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("获取模型列表", style = DW.TitleMedium.copy(color = t.ob))
                        Spacer(Modifier.height(2.dp))
                        Text(providerName, style = DW.BodySmall.copy(color = t.osv))
                    }
                    val refreshScale = remember { Animatable(1f) }
                    val refreshScope = rememberCoroutineScope()
                    val refreshRot by animateFloatAsState(
                        if (loading) 360f else 0f,
                        if (loading) infiniteRepeatable(tween(1000, easing = LinearEasing)) else tween(300), label = "rr"
                    )
                    Box(Modifier.size(40.dp).scale(refreshScale.value).clip(CircleShape)
                        .background(t.p.copy(alpha = 0.1f))
                        .pointerInput(loading) {
                            detectTapGestures(onPress = {
                                refreshScale.snapTo(0.85f); val released = tryAwaitRelease()
                                refreshScope.launch { refreshScale.animateTo(1f, spring(0.15f, 500f)) }
                                if (released && !loading) fetchModels()
                            })
                        }, contentAlignment = Alignment.Center) {
                        Text("\u21BB", style = TextStyle(fontSize = 20.sp, color = t.p),
                            modifier = Modifier.graphicsLayer { rotationZ = refreshRot })
                    }
                }

                Spacer(Modifier.height(16.dp))

                when {
                    loading -> {
                        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp, color = t.p)
                                Spacer(Modifier.height(12.dp))
                                Text("正在获取模型列表 …", style = DW.BodySmall.copy(color = t.osv))
                            }
                        }
                    }
                    loadError != null -> {
                        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(loadError!!, style = DW.BodySmall.copy(color = t.err))
                                Spacer(Modifier.height(12.dp))
                                Text("点击 \u21BB 重试", style = DW.LabelSmall.copy(color = t.osv))
                            }
                        }
                    }
                    !hasFetched -> {
                        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                            Text("点击 \u21BB 获取模型列表", style = DW.BodySmall.copy(color = t.osv))
                        }
                    }
                    else -> {
                        // 按厂商/系列分组
                        val groups = remember(models) { groupModels(models) }
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            groups.forEach { (groupName, groupModels) ->
                                var groupExpanded by remember(groupName) { mutableStateOf(true) }
                                val chevronRot by animateFloatAsState(if (groupExpanded) 0f else -90f, tween(200), label = "gcr")
                                // Group header
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(t.ac.copy(alpha = 0.06f))
                                    .clickable { groupExpanded = !groupExpanded }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("\u25BE", style = DW.LabelSmall.copy(color = t.osv),
                                        modifier = Modifier.graphicsLayer { rotationZ = chevronRot })
                                    Spacer(Modifier.width(6.dp))
                                    Text(groupName, style = DW.LabelSmall.copy(color = t.osv, fontWeight = FontWeight.Medium))
                                    Spacer(Modifier.weight(1f))
                                    Text("${groupModels.size}", style = DW.LabelSmall.copy(color = t.osv, fontSize = 10.sp))
                                }
                                Spacer(Modifier.height(2.dp))
                                // Group models
                                AnimatedVisibility(visible = groupExpanded, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(150))) {
                                    Column {
                                        groupModels.forEach { model ->
                                            val isSelected = model in selected
                                            val bg by animateColorAsState(
                                                if (isSelected) t.p.copy(alpha = 0.12f) else Color.Transparent, tween(200), label = "ibg"
                                            )
                                            val checkScale by animateFloatAsState(
                                                if (isSelected) 1f else 0.6f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "cs"
                                            )
                                            val checkBg by animateColorAsState(
                                                if (isSelected) t.p else Color.Transparent, tween(200), label = "cbg"
                                            )
                                            val checkBorder by animateColorAsState(
                                                if (isSelected) t.p else t.osv.copy(alpha = 0.4f), tween(200), label = "cbr"
                                            )
                                            val checkAlpha by animateFloatAsState(
                                                if (isSelected) 1f else 0f, tween(200), label = "ca"
                                            )
                                            val textColor by animateColorAsState(
                                                if (isSelected) t.ob else t.osv, tween(200), label = "itc"
                                            )

                                            Row(
                                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                                                    .clickable { if (isSelected) selected.remove(model) else selected.add(model) }
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(Modifier.size(22.dp).scale(checkScale).clip(RoundedCornerShape(6.dp))
                                                    .background(checkBg).border(1.5.dp, checkBorder, RoundedCornerShape(6.dp)),
                                                    contentAlignment = Alignment.Center) {
                                                    Text("\u2713", style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = checkAlpha), fontWeight = FontWeight.Bold))
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Text(model, style = DW.BodyMedium.copy(color = textColor), modifier = Modifier.weight(1f))
                                                // 模型能力标签
                                                ModelCapabilityTags(model, t)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val cancelScale = remember { Animatable(1f) }
                    val cancelScope = rememberCoroutineScope()
                    Box(Modifier.weight(1f).scale(cancelScale.value).clip(RoundedCornerShape(14.dp))
                        .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.1f))
                        .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.25f else 0.1f), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                cancelScale.snapTo(0.92f); val released = tryAwaitRelease()
                                cancelScope.launch { cancelScale.animateTo(1f, spring(0.15f, 500f)) }
                                if (released) currentOnDismiss()
                            })
                        }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Text("取消", style = DW.LabelLarge.copy(color = t.osv))
                    }
                    val confirmScale = remember { Animatable(1f) }
                    val confirmScope = rememberCoroutineScope()
                    Box(Modifier.weight(1f).scale(confirmScale.value).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                confirmScale.snapTo(0.92f); val released = tryAwaitRelease()
                                confirmScope.launch { confirmScale.animateTo(1f, spring(0.15f, 500f)) }
                                if (released) currentOnConfirm(selected.toList(), fetchedModels)
                            })
                        }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Text("确认 (${selected.size})", style = DW.LabelLarge.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

/**
 * 将模型列表按厂商/系列分组。
 * 如 "deepseek-ai/DeepSeek-V4-Flash" → "DeepSeek"
 *    "Qwen/Qwen3-235B" → "Qwen"
 *    无前缀的直接模型名 → "其他"
 */
private fun groupModels(models: List<String>): List<Pair<String, List<String>>> {
    val groups = linkedMapOf<String, MutableList<String>>()
    for (model in models) {
        val key = when {
            model.contains("deepseek", ignoreCase = true) -> "DeepSeek"
            model.contains("qwen", ignoreCase = true) -> "Qwen"
            model.contains("gpt", ignoreCase = true) || model.contains("openai", ignoreCase = true) || model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") -> "OpenAI"
            model.contains("claude", ignoreCase = true) || model.contains("anthropic", ignoreCase = true) -> "Anthropic"
            model.contains("gemini", ignoreCase = true) || model.contains("gemma", ignoreCase = true) -> "Google"
            model.contains("glm", ignoreCase = true) || model.startsWith("chatglm") || model.contains("cogview") -> "智谱"
            model.contains("kimi", ignoreCase = true) || model.contains("moonshot", ignoreCase = true) -> "Kimi"
            model.contains("doubao", ignoreCase = true) || model.contains("skylark", ignoreCase = true) -> "豆包"
            model.contains("hunyuan", ignoreCase = true) -> "混元"
            model.contains("yi-", ignoreCase = true) || model.contains("yi-", ignoreCase = true) -> "零一"
            model.contains("llama", ignoreCase = true) -> "Llama"
            model.contains("mistral", ignoreCase = true) || model.contains("mixtral", ignoreCase = true) -> "Mistral"
            model.contains("grok", ignoreCase = true) -> "Grok"
            model.contains("minimax", ignoreCase = true) -> "MiniMax"
            model.contains("step-", ignoreCase = true) -> "阶跃"
            model.contains("baichuan", ignoreCase = true) -> "百川"
            model.contains("internvl", ignoreCase = true) || model.contains("internlm", ignoreCase = true) -> "书生"
            model.contains("mimo", ignoreCase = true) -> "MiMo"
            else -> "其他"
        }
        groups.getOrPut(key) { mutableListOf() }.add(model)
    }
    // 把"其他"组放到最后
    val result = groups.toList().toMutableList()
    val otherIdx = result.indexOfFirst { it.first == "其他" }
    if (otherIdx >= 0) {
        val other = result.removeAt(otherIdx)
        result.add(other)
    }
    return result
}
