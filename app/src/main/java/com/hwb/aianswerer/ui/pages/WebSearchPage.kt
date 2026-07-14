package com.hwb.aianswerer.ui.pages

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.providers.WebSearchStorage
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// =============================================================================
// Data Models
// =============================================================================
data class SearchProviderDef(
    val id: String, val name: String, val apiHost: String = "", val url: String = "",
    val requiresApiKey: Boolean = true, val requiresHost: Boolean = false,
    val supportsBasicAuth: Boolean = false, val officialUrl: String? = null, val apiKeyUrl: String? = null,
    val testEndpoint: String? = null
)

data class SearchProviderState(
    val def: SearchProviderDef,
    var apiKey: String = "", var enabled: Boolean = false,
    var customApiHost: String = "", var basicAuthUser: String = "", var basicAuthPass: String = ""
)

enum class BadgeType { API, FREE, LOCAL }

// =============================================================================
// Provider Definitions
// =============================================================================
private val WS_PROVIDERS = listOf(
    SearchProviderDef("tavily", "Tavily", "https://api.tavily.com",
        officialUrl = "https://tavily.com", apiKeyUrl = "https://app.tavily.com",
        testEndpoint = "https://api.tavily.com/search"),
    SearchProviderDef("zhipu", "Zhipu", "https://open.bigmodel.cn/api/paas/v4/web_search",
        officialUrl = "https://open.bigmodel.cn", apiKeyUrl = "https://open.bigmodel.cn",
        testEndpoint = "https://open.bigmodel.cn/api/paas/v4/web_search"),
    SearchProviderDef("bocha", "Bocha", "https://api.bochaai.com",
        officialUrl = "https://bochaai.com", apiKeyUrl = "https://bochaai.com",
        testEndpoint = "https://api.bochaai.com/v1/web-search"),
    SearchProviderDef("exa", "Exa", "https://api.exa.ai",
        officialUrl = "https://exa.ai", apiKeyUrl = "https://exa.ai",
        testEndpoint = "https://api.exa.ai/search"),
    SearchProviderDef("querit", "Querit", "https://api.querit.ai",
        officialUrl = "https://querit.ai", apiKeyUrl = "https://querit.ai",
        testEndpoint = "https://api.querit.ai/v1/search"),
    SearchProviderDef("searxng", "Searxng", requiresApiKey = false, requiresHost = true, supportsBasicAuth = true,
        officialUrl = "https://docs.searxng.org"),
    SearchProviderDef("exa-mcp", "ExaMCP", "https://mcp.exa.ai/mcp", requiresApiKey = false,
        officialUrl = "https://exa.ai", testEndpoint = "https://mcp.exa.ai/mcp"),
    SearchProviderDef("local-google", "Google", "", "https://www.google.com/search?q=%s", requiresApiKey = false),
    SearchProviderDef("local-bing", "Bing", "", "https://cn.bing.com/search?q=%s&ensearch=1", requiresApiKey = false),
    SearchProviderDef("local-baidu", "Baidu", "", "https://www.baidu.com/s?wd=%s", requiresApiKey = false)
)

private fun badgeFor(p: SearchProviderDef) = when {
    p.requiresApiKey -> BadgeType.API
    p.url.isNotEmpty() -> BadgeType.LOCAL
    else -> BadgeType.FREE
}

// =============================================================================
// Previews
// =============================================================================
@Preview(showSystemUi = true, showBackground = true, name = "联网搜索 — Light")
@Composable private fun WSPLightPreview() { Themed { WebSearchPage(it, {}) } }

@Preview(showSystemUi = true, showBackground = true, name = "联网搜索 — Dark")
@Composable private fun WSPDarkPreview() { Themed(DH) { WebSearchPage(it, {}) } }

// =============================================================================
// TestState
// =============================================================================
sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Success(val ms: Int) : TestState()
    data class Error(val msg: String) : TestState()
}

// =============================================================================
// Page
// =============================================================================
@Composable
fun WebSearchPage(t: Th, onBack: () -> Unit) {
    var searchEnabled by remember { mutableStateOf(WebSearchStorage.isSearchEnabled()) }
    var regexFilter by remember { mutableStateOf(AppConfig.isRegexFilterEnabled()) }
    var searchText by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val providers = remember {
        mutableStateListOf(*WS_PROVIDERS.map { def ->
            val saved = WebSearchStorage.getUserConfig(def.id)
            SearchProviderState(def,
                apiKey = saved.apiKey, enabled = saved.enabled,
                customApiHost = saved.customApiHost ?: "",
                basicAuthUser = saved.basicAuthUsername, basicAuthPass = saved.basicAuthPassword
            )
        }.toTypedArray())
    }
    var testStates by remember { mutableStateOf(mapOf<String, TestState>()) }
    var showSaveToast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val bgGradient = Brush.linearGradient(
        listOf(t.bg1, t.bg2, t.bg3, t.bg4, t.bg5),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val filtered = providers.filter {
        searchText.isBlank() || it.def.name.contains(searchText, ignoreCase = true) || it.def.id.contains(searchText, ignoreCase = true)
    }

    Box(Modifier.fillMaxSize().background(bgGradient)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 108.dp, bottom = 32.dp)) {
            Spacer(Modifier.height(4.dp))

            WSGlass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                WSSwitch(t, "启用联网搜索", "识别题目后自动联网检索答案", searchEnabled) { searchEnabled = it; WebSearchStorage.saveSearchEnabled(it) }
                WSSep(t)
                WSSwitch(t, "多题正则过滤", "检测到多题时跳过联网搜索（关闭后始终搜索）", regexFilter) { regexFilter = it; AppConfig.saveRegexFilterEnabled(it) }
            }

            if (searchEnabled) {
            WSSearchBar(t, searchText) { searchText = it }
            Spacer(Modifier.height(8.dp))

            Text("服务商  ${filtered.size}", style = DW.LabelSmall.copy(color = t.osv),
                modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))

            filtered.forEach { ps ->
                WebSearchCard(t = t, ps = ps, expanded = expandedId == ps.def.id,
                    testState = testStates[ps.def.id],
                    onToggleExpand = { expandedId = if (expandedId == ps.def.id) null else ps.def.id },
                    onEnableToggle = {
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx >= 0) {
                            val newEnabled = !ps.enabled
                            providers[idx] = ps.copy(enabled = newEnabled)
                            val p = providers[idx]
                            WebSearchStorage.saveUserConfig(p.def.id, WebSearchStorage.UserWebSearchConfig(
                                enabled = newEnabled, apiKey = p.apiKey,
                                customApiHost = p.customApiHost.ifBlank { null },
                                basicAuthUsername = p.basicAuthUser, basicAuthPassword = p.basicAuthPass
                            ))
                        }
                    },
                    onApiKeyChange = { v ->
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx >= 0) providers[idx] = ps.copy(apiKey = v)
                    },
                    onHostChange = { v ->
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx >= 0) providers[idx] = ps.copy(customApiHost = v)
                    },
                    onAuthUserChange = { v ->
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx >= 0) providers[idx] = ps.copy(basicAuthUser = v)
                    },
                    onAuthPassChange = { v ->
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx >= 0) providers[idx] = ps.copy(basicAuthPass = v)
                    },
                    onTest = {
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx < 0) return@WebSearchCard
                        val p = providers[idx]
                        val endpoint = p.def.testEndpoint ?: p.def.apiHost
                        if (endpoint.isBlank()) {
                            testStates = testStates + (p.def.id to TestState.Success(0))
                            return@WebSearchCard
                        }
                        testStates = testStates + (p.def.id to TestState.Testing)
                        scope.launch {
                            val result = runCatching {
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(10, TimeUnit.SECONDS)
                                    .readTimeout(10, TimeUnit.SECONDS).build()
                                val jsonBody = """{"query":"test","max_results":1}""".toRequestBody("application/json".toMediaType())
                                val reqBuilder = Request.Builder().url(endpoint).post(jsonBody)
                                    .addHeader("Content-Type", "application/json")
                                if (p.apiKey.isNotBlank()) reqBuilder.addHeader("Authorization", "Bearer ${p.apiKey}")
                                val start = System.currentTimeMillis()
                                withContext(Dispatchers.IO) {
                                    client.newCall(reqBuilder.build()).execute().use { resp ->
                                        val elapsed = System.currentTimeMillis() - start
                                        if (resp.isSuccessful) Result.success(elapsed.toInt())
                                        else Result.failure(Exception("HTTP ${resp.code}"))
                                    }
                                }
                            }.getOrElse { Result.failure(it) }
                            val errMsg = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "未知错误"
                            testStates = if (result.isSuccess) testStates + (p.def.id to TestState.Success(result.getOrNull() ?: 0))
                            else testStates + (p.def.id to TestState.Error(errMsg))
                        }
                    },
                    onSave = {
                        val idx = providers.indexOfFirst { it.def.id == ps.def.id }
                        if (idx >= 0) {
                            val p = providers[idx]
                            android.util.Log.d("WebSearchPage", "onSave: apiKey='${p.apiKey}', enabled=${p.enabled}")
                            WebSearchStorage.saveUserConfig(p.def.id, WebSearchStorage.UserWebSearchConfig(
                                enabled = p.enabled, apiKey = p.apiKey,
                                customApiHost = p.customApiHost.ifBlank { null },
                                basicAuthUsername = p.basicAuthUser, basicAuthPassword = p.basicAuthPass
                            ))
                            showSaveToast = p.def.name
                        }
                    }
                )
            }
            }
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(t.bg1, t.bg2)))) {
            WebSearchTopBar(t, onBack)
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
    }
}

// =============================================================================
// Sub-composables
// =============================================================================
@Composable
private fun WebSearchTopBar(t: Th, onBack: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).scale(scale.value).pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.85f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) onBack()
            })
        }, contentAlignment = Alignment.Center) {
            Icon(LocalIcons.ArrowBack, "返回", tint = t.ob, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text("联网搜索设置", style = DW.TitleLarge.copy(color = t.ob))
            Text("配置搜索服务商", style = DW.BodySmall.copy(color = t.osv))
        }
    }
}

@Composable
private fun WSGlass(modifier: Modifier, t: Th, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(CardR)).border(1.dp, t.gb, RoundedCornerShape(CardR))
        .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
        .padding(CardPad)) { Column { content() } }
}

@Composable
private fun WSSwitch(t: Th, title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val switchScale = remember { Animatable(1f) }
    LaunchedEffect(checked) { switchScale.snapTo(0.82f); switchScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = DW.BodyMedium.copy(color = t.ob))
            Spacer(Modifier.height(2.dp))
            Text(desc, style = DW.BodySmall.copy(color = t.osv))
        }
        Box(Modifier.scale(switchScale.value)) {
            Switch(checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = t.w, checkedTrackColor = t.p,
                    uncheckedThumbColor = t.w, uncheckedTrackColor = t.to,
                    checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent))
        }
    }
}

@Composable private fun WSSep(t: Th) { HorizontalDivider(color = t.ac.copy(alpha = 0.12f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp)) }

@Composable
private fun WSSearchBar(t: Th, value: String, onValueChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.08f))
        .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.3f else 0.1f), RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LocalIcons.Search, "搜索", tint = t.osv, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("搜索服务商名称或 ID …", style = DW.BodySmall.copy(color = t.osv.copy(alpha = 0.6f)))
                BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
                    textStyle = DW.BodySmall.copy(color = t.ob), cursorBrush = SolidColor(t.p), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(t.ac.copy(alpha = 0.2f)).clickable { onValueChange("") },
                    contentAlignment = Alignment.Center) { Text("✕", style = DW.LabelSmall.copy(color = t.osv, fontSize = 10.sp)) }
            }
        }
    }
}

@Composable
private fun WebSearchCard(
    t: Th, ps: SearchProviderState, expanded: Boolean, testState: TestState?,
    onToggleExpand: () -> Unit, onEnableToggle: () -> Unit,
    onApiKeyChange: (String) -> Unit, onHostChange: (String) -> Unit,
    onAuthUserChange: (String) -> Unit, onAuthPassChange: (String) -> Unit,
    onTest: () -> Unit, onSave: () -> Unit
) {
    val badge = badgeFor(ps.def)
    val borderColor by animateColorAsState(
        if (expanded) t.p.copy(alpha = if (t.isLight) 0.5f else 0.35f) else t.ac.copy(alpha = if (t.isLight) 0.2f else 0.06f),
        tween(300), label = "cb")
    val cardBgAlpha by animateFloatAsState(
        if (expanded) (if (t.isLight) 0.95f else 0.18f) else (if (t.isLight) 0.85f else 0.06f),
        tween(300), label = "cba")
    val chevronRot by animateFloatAsState(if (expanded) 180f else 0f, spring(0.6f, 400f), label = "cv")

    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(20.dp)).background(t.gb.copy(alpha = cardBgAlpha))
        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onToggleExpand() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ps.def.name, style = DW.BodyLarge.copy(color = t.ob, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.width(8.dp))
                    BadgeChip(badge, t)
                }
                Spacer(Modifier.height(3.dp))
                Text(ps.def.apiHost.ifEmpty { ps.def.url }.ifEmpty { "已配置" },
                    style = DW.BodySmall.copy(color = t.osv, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("▾", style = DW.LabelMedium.copy(color = t.osv),
                modifier = Modifier.graphicsLayer { rotationZ = chevronRot }.padding(horizontal = 6.dp))
            Spacer(Modifier.width(4.dp))
            val switchScale = remember { Animatable(1f) }
            LaunchedEffect(ps.enabled) { switchScale.snapTo(0.82f); switchScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
            Box(Modifier.scale(switchScale.value).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEnableToggle
            )) {
                Switch(checked = ps.enabled, onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedThumbColor = t.w, checkedTrackColor = t.p,
                        uncheckedThumbColor = t.w, uncheckedTrackColor = t.to,
                        checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent),
                    modifier = Modifier.height(28.dp))
            }
        }

        AnimatedVisibility(visible = expanded,
            enter = expandVertically(spring(0.7f, 300f)) + fadeIn(tween(250)),
            exit = shrinkVertically(spring(0.7f, 400f)) + fadeOut(tween(200))) {
            Column(Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = t.ac.copy(alpha = 0.1f), thickness = 0.5.dp)
                Spacer(Modifier.height(16.dp))
                if (ps.def.requiresHost || ps.def.apiHost.isNotEmpty()) {
                    WSConfigField(t, "API Host", hint = ps.def.apiHost.ifEmpty { "输入服务地址" }, value = ps.customApiHost, onValueChange = onHostChange)
                    Spacer(Modifier.height(12.dp))
                }
                if (ps.def.requiresApiKey) {
                    WSConfigField(t, "API Key", hint = "输入 API Key", value = ps.apiKey, onValueChange = onApiKeyChange, isPassword = true)
                    Spacer(Modifier.height(12.dp))
                }
                if (ps.def.supportsBasicAuth) {
                    WSConfigField(t, "用户名", hint = "Basic Auth 用户名（可选）", value = ps.basicAuthUser, onValueChange = onAuthUserChange)
                    Spacer(Modifier.height(10.dp))
                    WSConfigField(t, "密码", hint = "Basic Auth 密码（可选）", value = ps.basicAuthPass, onValueChange = onAuthPassChange, isPassword = true)
                    Spacer(Modifier.height(12.dp))
                }
                WSTestButton(t, testState, onTest)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ps.def.officialUrl?.let { WSLinkButton(t, "官方网站") }
                    ps.def.apiKeyUrl?.let { WSLinkButton(t, "获取 Key") }
                }
                Spacer(Modifier.height(12.dp))
                WSSaveButton(t, onSave)
            }
        }
    }
}

@Composable
private fun WSConfigField(t: Th, label: String, hint: String, value: String, onValueChange: (String) -> Unit, isPassword: Boolean = false) {
    var showPassword by remember { mutableStateOf(false) }
    Column {
        Text(label, style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.06f))
            .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.25f else 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, style = DW.BodySmall.copy(color = t.osv.copy(alpha = 0.5f)))
                    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
                        textStyle = DW.BodySmall.copy(color = t.ob), cursorBrush = SolidColor(t.p),
                        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth())
                }
                if (isPassword && value.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape).background(t.ac.copy(alpha = 0.12f)).clickable { showPassword = !showPassword },
                        contentAlignment = Alignment.Center) { Text(if (showPassword) "🙈" else "👁", style = TextStyle(fontSize = 13.sp)) }
                }
            }
        }
    }
}

@Composable
private fun WSTestButton(t: Th, state: TestState?, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val current = state ?: TestState.Idle
    Column {
        Box(Modifier.fillMaxWidth().scale(scale.value).clip(RoundedCornerShape(14.dp))
            .background(t.gb.copy(alpha = if (t.isLight) 0.55f else 0.1f))
            .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.3f else 0.1f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scale.snapTo(0.92f); val released = tryAwaitRelease()
                    scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                    if (released) onClick()
                })
            }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current is TestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.p)
                    Spacer(Modifier.width(8.dp))
                }
                Text(when (current) { TestState.Idle -> "测试连接"; TestState.Testing -> "测试中 …"
                    is TestState.Success -> "测试连接"; is TestState.Error -> "重新测试" },
                    style = DW.LabelMedium.copy(color = when (current) { is TestState.Success -> t.ok; is TestState.Error -> t.err; else -> t.ob }))
            }
        }
        when (current) {
            is TestState.Success -> Text("连接成功 (${current.ms}ms)", style = DW.BodySmall.copy(color = t.ok, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            is TestState.Error -> Text("连接失败: ${current.msg}", style = DW.BodySmall.copy(color = t.err, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            else -> {}
        }
    }
}

@Composable
private fun WSLinkButton(t: Th, label: String) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(Modifier.scale(scale.value).clip(RoundedCornerShape(12.dp))
        .background(t.p.copy(alpha = if (t.isLight) 0.08f else 0.12f))
        .border(1.dp, t.p.copy(alpha = if (t.isLight) 0.2f else 0.15f), RoundedCornerShape(12.dp))
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.92f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
            })
        }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LocalIcons.Link, null, tint = t.p, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = DW.LabelSmall.copy(color = t.p))
        }
    }
}

@Composable
private fun WSSaveButton(t: Th, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().scale(scale.value).clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite))
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.92f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) onClick()
            })
        }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text("保存配置", style = DW.LabelLarge.copy(color = t.w))
    }
}

@Composable
private fun BadgeChip(type: BadgeType, t: Th) {
    val (label, bg, fg) = when (type) {
        BadgeType.API -> Triple("API", t.ua.copy(alpha = 0.2f), t.ua)
        BadgeType.FREE -> Triple("免费", t.ok.copy(alpha = 0.15f), t.ok)
        BadgeType.LOCAL -> Triple("本地", t.pe.copy(alpha = 0.2f), t.pd)
    }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, style = DW.LabelSmall.copy(color = fg, fontSize = 10.sp, letterSpacing = 0.3.sp))
    }
}
