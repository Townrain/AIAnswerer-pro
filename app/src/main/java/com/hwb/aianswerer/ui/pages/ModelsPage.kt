package com.hwb.aianswerer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.providers.DynamicApiClient
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*
import com.hwb.aianswerer.utils.ModelWhitelistUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// Data Models
// =============================================================================
enum class ModelProviderType(val label: String, val color: Color) {
    OPENAI("OpenAI 兼容", Color(0xFF34C759)),
    ANTHROPIC("Anthropic", Color(0xFFD4A853)),
    GEMINI("Gemini", Color(0xFF4A90D9)),
    OLLAMA("Ollama", Color(0xFF9E9E9E)),
    AZURE("Azure", Color(0xFF0078D4)),
    CUSTOM("自定义", Color(0xFF9B8FF8))
}

data class ProviderDef(
    val id: String, val name: String, val type: ModelProviderType,
    val apiHost: String, val availableModels: List<String> = listOf(),
    val officialUrl: String? = null, val apiKeyUrl: String? = null
)

data class ProviderState(
    val def: ProviderDef,
    var apiKey: String = "", var customHost: String = "",
    var enabled: Boolean = false, var selectedModels: List<String> = listOf()
)

// Whitelist update state
private sealed class WhitelistUpdateState {
    data object Idle : WhitelistUpdateState()
    data object Checking : WhitelistUpdateState()
    data class Success(
        val visionCount: Int,
        val excludedCount: Int,
        val totalCount: Int,
        val languageCount: Int,
        val providerCount: Int,
        val newProviderCount: Int,
        val newModelCount: Int
    ) : WhitelistUpdateState()
    data class Fail(val msg: String) : WhitelistUpdateState()
}

// =============================================================================
// Provider Definitions
// 国内常用提供者放在前面，方便用户选择
// =============================================================================
private val MODEL_PROVIDERS = listOf(
    // 自定义提供者（置顶）
    ProviderDef("custom", "自定义 OpenAI 兼容", ModelProviderType.CUSTOM, "", listOf(), null, null),
    // 国内常用提供者
    ProviderDef("deepseek", "DeepSeek", ModelProviderType.OPENAI, "https://api.deepseek.com/v1",
        listOf("deepseek-chat", "deepseek-reasoner"),
        "https://deepseek.com", "https://platform.deepseek.com/api_keys"),
    ProviderDef("dashscope", "阿里百炼", ModelProviderType.OPENAI, "https://dashscope.aliyuncs.com/compatible-mode/v1",
        listOf("qwen-max", "qwen-plus", "qwen-turbo", "qwen-vl-max", "qwen-vl-plus"),
        "https://tongyi.aliyun.com", "https://dashscope.console.aliyun.com/apiKey"),
    ProviderDef("zhipu", "智谱清言", ModelProviderType.OPENAI, "https://open.bigmodel.cn/api/paas/v4",
        listOf("glm-4-plus", "glm-4-flash", "glm-4-long", "glm-4v"),
        "https://open.bigmodel.cn", "https://open.bigmodel.cn/usercenter/apikeys"),
    ProviderDef("moonshot", "Kimi", ModelProviderType.OPENAI, "https://api.moonshot.cn/v1",
        listOf("moonshot-v1-128k", "moonshot-v1-32k", "moonshot-v1-8k"),
        "https://moonshot.cn", "https://platform.moonshot.cn/console/api-keys"),
    ProviderDef("mimo", "MiMo", ModelProviderType.OPENAI, "https://api.xiaomi.com/v1",
        listOf("mimo-v2-flash", "mimo-v2-pro", "mimo-v2.5"),
        "https://mimo.xiaomi.com", "https://mimo.xiaomi.com/api-keys"),
    ProviderDef("silicon", "硅基流动", ModelProviderType.OPENAI, "https://api.siliconflow.cn/v1",
        listOf("Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3", "THUDM/glm-4-9b-chat"),
        "https://siliconflow.cn", "https://cloud.siliconflow.cn/account/ak"),
    // 国际提供者
    ProviderDef("openai", "OpenAI", ModelProviderType.OPENAI, "https://api.openai.com/v1",
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o1", "o3-mini"),
        "https://openai.com", "https://platform.openai.com/api-keys"),
    ProviderDef("anthropic", "Anthropic", ModelProviderType.ANTHROPIC, "https://api.anthropic.com/v1",
        listOf("claude-sonnet-4-20250514", "claude-opus-4-20250514", "claude-haiku-4-5-20251001", "claude-3-5-sonnet-20241022"),
        "https://anthropic.com", "https://console.anthropic.com/settings/keys"),
    ProviderDef("gemini", "Google Gemini", ModelProviderType.GEMINI, "https://generativelanguage.googleapis.com/v1beta",
        listOf("gemini-2.0-flash", "gemini-2.5-pro", "gemini-1.5-pro"),
        "https://gemini.google.com", "https://aistudio.google.com/apikey"),
    ProviderDef("openrouter", "OpenRouter", ModelProviderType.OPENAI, "https://openrouter.ai/api/v1",
        listOf("anthropic/claude-sonnet-4", "google/gemini-2.5-pro", "deepseek/deepseek-chat-v3-0324"),
        "https://openrouter.ai", "https://openrouter.ai/settings/keys"),
    ProviderDef("ollama", "Ollama (本地)", ModelProviderType.OLLAMA, "http://localhost:11434/v1",
        listOf("llama3.1", "qwen2.5", "deepseek-r1", "llava", "minicpm-v"),
        "https://ollama.com"),
    ProviderDef("azure-openai", "Azure OpenAI", ModelProviderType.AZURE,
        "https://{resource}.openai.azure.com/openai/deployments/{deployment}",
        listOf("gpt-4o", "gpt-4", "gpt-35-turbo"),
        "https://azure.microsoft.com/products/ai-services/openai-service"),
    // 更多国内常用厂商
    ProviderDef("minimax", "MiniMax (海螺)", ModelProviderType.OPENAI,
        "https://api.minimaxi.com/v1",
        listOf("MiniMax-M2.7", "MiniMax-M2.5", "MiniMax-M2.1"),
        "https://www.minimaxi.com", "https://platform.minimaxi.com/user-center/basic-information/interface-key"),
    ProviderDef("doubao", "豆包 (字节)", ModelProviderType.OPENAI,
        "https://ark.cn-beijing.volces.com/api/v3",
        listOf("doubao-seed-1-8-251228", "doubao-1-5-pro-32k-250115", "doubao-1-5-vision-pro-32k-250115"),
        "https://www.volcengine.com/product/doubao", "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey"),
    ProviderDef("ai302", "302.AI", ModelProviderType.OPENAI,
        "https://api.302.ai/v1",
        listOf("deepseek-chat", "deepseek-reasoner", "gpt-4o", "claude-sonnet-4-20250514", "gemini-2.5-pro"),
        "https://302.ai", "https://dash.302.ai/apis"),
    ProviderDef("aihubmix", "AiHubMix", ModelProviderType.OPENAI,
        "https://aihubmix.com/v1",
        listOf("gpt-5", "gpt-4o", "claude-sonnet-4-20250514", "gemini-2.5-pro"),
        "https://aihubmix.com", "https://aihubmix.com/token"),
    ProviderDef("burncloud", "BurnCloud", ModelProviderType.OPENAI,
        "https://api.burncloud.com/v1",
        listOf("claude-opus-4-5-20251101", "claude-sonnet-4-5-20250929", "gemini-2.5-flash", "deepseek-chat"),
        "https://burncloud.com", "https://burncloud.com/panel"),
    ProviderDef("ppio", "PPIO", ModelProviderType.OPENAI,
        "https://api.ppio.ai/v1",
        listOf("deepseek/deepseek-v3.2", "minimax/minimax-m2", "qwen/qwen3-235b-a22b-instruct-2507"),
        "https://ppio.ai", "https://ppio.ai/console"),
    // ── 更多 OpenAI 兼容厂商 ──
    ProviderDef("cherryin", "CherryIN", ModelProviderType.OPENAI,
        "https://open.cherryin.cc",
        listOf(), "https://open.cherryin.ai", "https://open.cherryin.ai/console/token"),
    ProviderDef("ocoolai", "ocoolAI", ModelProviderType.OPENAI,
        "https://api.ocoolai.com",
        listOf("deepseek-chat", "deepseek-reasoner", "gpt-4o"),
        "https://one.ocoolai.com", "https://one.ocoolai.com/token"),
    ProviderDef("zai", "Z.ai", ModelProviderType.OPENAI,
        "https://api.z.ai/api/paas/v4",
        listOf("glm-5", "glm-4.7", "glm-4.6", "glm-4.6v"),
        "https://z.ai", "https://z.ai/manage-apikey/apikey-list"),
    ProviderDef("alayanew", "AlayaNew", ModelProviderType.OPENAI,
        "https://deepseek.alayanew.com",
        listOf(), "https://www.alayanew.com", "https://www.alayanew.com/backend/register"),
    ProviderDef("dmxapi", "DMXAPI", ModelProviderType.OPENAI,
        "https://www.dmxapi.cn",
        listOf("Qwen/Qwen2.5-7B-Instruct", "ERNIE-Speed-128K", "gpt-4o"),
        "https://www.dmxapi.cn", "https://www.dmxapi.cn/register"),
    ProviderDef("aionly", "AIOnly", ModelProviderType.OPENAI,
        "https://api.aiionly.com",
        listOf("claude-opus-4-6", "claude-sonnet-4-6", "gpt-5.4", "gemini-3.1-pro-preview"),
        "https://www.aiionly.com", "https://maas.aiionly.com/keyApi"),
    ProviderDef("tokenflux", "TokenFlux", ModelProviderType.OPENAI,
        "https://api.tokenflux.ai/openai/v1",
        listOf("gpt-4.1", "claude-sonnet-4", "gemini-2.5-pro", "deepseek-v3"),
        "https://tokenflux.ai"),
    ProviderDef("cephalon", "Cephalon", ModelProviderType.OPENAI,
        "https://cephalon.cloud/user-center/v1/model",
        listOf("DeepSeek-R1", "DeepSeek-V3", "Qwen3-235B-A22B-Instruct-2507", "kimi-k2-0711-preview"),
        "https://cephalon.cloud", "https://cephalon.cloud/api"),
    ProviderDef("lanyun", "LANYUN (蓝云)", ModelProviderType.OPENAI,
        "https://maas-api.lanyun.net",
        listOf("deepseek-ai/DeepSeek-R1", "deepseek-ai/DeepSeek-V3", "Qwen2.5-72B-Instruct"),
        "https://maas.lanyun.net", "https://maas.lanyun.net/#/system/apiKey"),
    ProviderDef("ph8", "PH8", ModelProviderType.OPENAI,
        "https://ph8.co",
        listOf("deepseek-v3-241226", "deepseek-r1-250120"),
        "https://ph8.co", "https://ph8.co/apiKey"),
    ProviderDef("sophnet", "SophNet", ModelProviderType.OPENAI,
        "https://www.sophnet.com/api/open-apis/v1",
        listOf(), "https://sophnet.com"),
    ProviderDef("minimax-global", "MiniMax Global", ModelProviderType.OPENAI,
        "https://api.minimax.io/v1",
        listOf("MiniMax-M2.7", "MiniMax-M2.5"),
        "https://platform.minimax.io", "https://platform.minimax.io/user-center/basic-information/interface-key"),
    ProviderDef("qiniu", "七牛云", ModelProviderType.OPENAI,
        "https://api.qnaigc.com",
        listOf("deepseek-r1", "deepseek-v3", "qwq-32b", "qwen2.5-72b-instruct"),
        "https://qiniu.com", "https://portal.qiniu.com/ai-inference/api-key"),
    // ── 国内大厂 ──
    ProviderDef("baichuan", "百川智能", ModelProviderType.OPENAI,
        "https://api.baichuan-ai.com",
        listOf(), "https://www.baichuan-ai.com", "https://platform.baichuan-ai.com/console/apikey"),
    ProviderDef("stepfun", "阶跃星辰", ModelProviderType.OPENAI,
        "https://api.stepfun.com",
        listOf(), "https://platform.stepfun.com", "https://platform.stepfun.com/interface-key"),
    ProviderDef("yi", "零一万物", ModelProviderType.OPENAI,
        "https://api.lingyiwanwu.com",
        listOf(), "https://platform.lingyiwanwu.com", "https://platform.lingyiwanwu.com/apikeys"),
    ProviderDef("hunyuan", "腾讯混元", ModelProviderType.OPENAI,
        "https://api.hunyuan.cloud.tencent.com",
        listOf(), "https://cloud.tencent.com/product/hunyuan", "https://console.cloud.tencent.com/hunyuan/api-key"),
    ProviderDef("tencent-cloud-ti", "腾讯云 TI", ModelProviderType.OPENAI,
        "https://api.lkeap.cloud.tencent.com",
        listOf(), "https://cloud.tencent.com/product/ti", "https://console.cloud.tencent.com/lkeap/api"),
    ProviderDef("baidu-cloud", "百度千帆", ModelProviderType.OPENAI,
        "https://qianfan.baidubce.com/v2",
        listOf(), "https://cloud.baidu.com", "https://console.bce.baidu.com/iam/#/iam/apikey/list"),
    ProviderDef("xirang", "天翼云 (息壤)", ModelProviderType.OPENAI,
        "https://wishub-x1.ctyun.cn",
        listOf(), "https://www.ctyun.cn"),
    ProviderDef("modelscope", "ModelScope", ModelProviderType.OPENAI,
        "https://api-inference.modelscope.cn/v1",
        listOf(), "https://modelscope.cn", "https://modelscope.cn/my/myaccesstoken"),
    // ── 国际厂商 ──
    ProviderDef("groq", "Groq", ModelProviderType.OPENAI,
        "https://api.groq.com/openai",
        listOf(), "https://groq.com", "https://console.groq.com/keys"),
    ProviderDef("together", "Together AI", ModelProviderType.OPENAI,
        "https://api.together.xyz",
        listOf(), "https://www.together.ai", "https://api.together.ai/settings/api-keys"),
    ProviderDef("fireworks", "Fireworks AI", ModelProviderType.OPENAI,
        "https://api.fireworks.ai/inference",
        listOf(), "https://fireworks.ai", "https://fireworks.ai/account/api-keys"),
    ProviderDef("nvidia", "NVIDIA NIM", ModelProviderType.OPENAI,
        "https://integrate.api.nvidia.com",
        listOf(), "https://build.nvidia.com"),
    ProviderDef("grok", "Grok (xAI)", ModelProviderType.OPENAI,
        "https://api.x.ai",
        listOf(), "https://x.ai"),
    ProviderDef("hyperbolic", "Hyperbolic", ModelProviderType.OPENAI,
        "https://api.hyperbolic.xyz",
        listOf(), "https://app.hyperbolic.xyz", "https://app.hyperbolic.xyz/settings"),
    ProviderDef("mistral", "Mistral AI", ModelProviderType.OPENAI,
        "https://api.mistral.ai",
        listOf(), "https://mistral.ai", "https://console.mistral.ai/api-keys"),
    ProviderDef("jina", "Jina AI", ModelProviderType.OPENAI,
        "https://api.jina.ai",
        listOf(), "https://jina.ai"),
    ProviderDef("perplexity", "Perplexity", ModelProviderType.OPENAI,
        "https://api.perplexity.ai",
        listOf(), "https://perplexity.ai", "https://www.perplexity.ai/settings/api"),
    ProviderDef("infini", "Infini", ModelProviderType.OPENAI,
        "https://cloud.infini-ai.com/maas",
        listOf(), "https://cloud.infini-ai.com", "https://cloud.infini-ai.com/iam/secret/key"),
    ProviderDef("poe", "Poe", ModelProviderType.OPENAI,
        "https://api.poe.com/v1",
        listOf(), "https://poe.com", "https://poe.com/api/keys"),
    ProviderDef("longcat", "LongCat", ModelProviderType.OPENAI,
        "https://api.longcat.chat/openai",
        listOf(), "https://longcat.chat", "https://longcat.chat/platform/api_keys"),
    ProviderDef("huggingface", "Hugging Face", ModelProviderType.OPENAI,
        "https://router.huggingface.co/v1",
        listOf(), "https://huggingface.co", "https://huggingface.co/settings/tokens"),
    ProviderDef("cerebras", "Cerebras AI", ModelProviderType.OPENAI,
        "https://api.cerebras.ai/v1",
        listOf(), "https://www.cerebras.ai", "https://cloud.cerebras.ai"),
    ProviderDef("voyageai", "Voyage AI", ModelProviderType.OPENAI,
        "https://api.voyageai.com",
        listOf(), "https://www.voyageai.com", "https://dashboard.voyageai.com/organization/api-keys"),
    ProviderDef("github", "GitHub Models", ModelProviderType.OPENAI,
        "https://models.github.ai/inference",
        listOf("gpt-4o"), "https://github.com/marketplace/models", "https://github.com/settings/tokens"),
    ProviderDef("copilot", "GitHub Copilot", ModelProviderType.OPENAI,
        "https://api.githubcopilot.com",
        listOf())
)

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
    var testStates by remember { mutableStateOf(mapOf<String, com.hwb.aianswerer.ui.pages.TestState>()) }
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
                                testStates = testStates + (ps.def.id to com.hwb.aianswerer.ui.pages.TestState.Error("请先填写 API Host 和 Key"))
                                return@ModelProviderCard
                            }
                            val model = ps.selectedModels.firstOrNull() ?: ps.def.availableModels.firstOrNull()
                            if (model.isNullOrBlank()) {
                                testStates = testStates + (ps.def.id to com.hwb.aianswerer.ui.pages.TestState.Error("请先添加并选择一个模型"))
                                return@ModelProviderCard
                            }
                            testStates = testStates + (ps.def.id to com.hwb.aianswerer.ui.pages.TestState.Testing)
                            scope.launch {
                                val typeStr = when (ps.def.type) {
                                    ModelProviderType.ANTHROPIC -> "anthropic"
                                    ModelProviderType.OLLAMA -> "ollama"
                                    else -> "openai"
                                }
                                val result = DynamicApiClient.testConnection(host, key, model, typeStr)
                                testStates = if (result.isSuccess) testStates + (ps.def.id to com.hwb.aianswerer.ui.pages.TestState.Success((result.getOrNull() ?: 0).toInt()))
                                else testStates + (ps.def.id to com.hwb.aianswerer.ui.pages.TestState.Error(result.exceptionOrNull()?.message ?: "未知错误"))
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

// =============================================================================
// Model Picker Dialog
// =============================================================================
@Composable
private fun ModelPickerDialog(
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
                        Text("↻", style = TextStyle(fontSize = 20.sp, color = t.p),
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
                                Text("点击 ↻ 重试", style = DW.LabelSmall.copy(color = t.osv))
                            }
                        }
                    }
                    !hasFetched -> {
                        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                            Text("点击 ↻ 获取模型列表", style = DW.BodySmall.copy(color = t.osv))
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
                                    Text("▾", style = DW.LabelSmall.copy(color = t.osv),
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
                                        Text("✓", style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = checkAlpha), fontWeight = FontWeight.Bold))
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

// =============================================================================
// Sub-composables
// =============================================================================
@Composable
private fun ModelsTopBar(t: Th, onBack: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    Row(Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 28.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).scale(scale.value).pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.85f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) currentOnBack()
            })
        }, contentAlignment = Alignment.Center) {
            Icon(LocalIcons.ArrowBack, "返回", tint = t.ob, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text("模型厂商", style = DW.TitleLarge.copy(color = t.ob))
            Text("配置各厂商 API Key 并选择模型", style = DW.BodySmall.copy(color = t.osv))
        }
    }
}

@Composable
private fun ModelsSearchBar(t: Th, value: String, onValueChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(20.dp))
        .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.08f))
        .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.3f else 0.1f), RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LocalIcons.Search, "搜索", tint = t.osv, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("搜索厂商名称 …", style = DW.BodySmall.copy(color = t.osv.copy(alpha = 0.6f)))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelProviderCard(
    t: Th, ps: ProviderState, expanded: Boolean, testState: com.hwb.aianswerer.ui.pages.TestState?,
    bringIntoViewRequester: BringIntoViewRequester,
    onToggleExpand: () -> Unit, onEnableToggle: () -> Unit,
    onApiKeyChange: (String) -> Unit, onHostChange: (String) -> Unit,
    onTest: () -> Unit, onSave: () -> Unit,
    onOpenPicker: () -> Unit, onRemoveModel: (String) -> Unit
) {
    val borderColor by animateColorAsState(
        if (expanded) t.p.copy(alpha = if (t.isLight) 0.5f else 0.35f) else t.ac.copy(alpha = if (t.isLight) 0.2f else 0.06f),
        tween(300), label = "mcb")
    val cardBgAlpha by animateFloatAsState(
        if (expanded) (if (t.isLight) 0.95f else 0.18f) else (if (t.isLight) 0.85f else 0.06f),
        tween(300), label = "mcba")
    val chevronRot by animateFloatAsState(if (expanded) 180f else 0f, spring(0.6f, 400f), label = "mcv")

    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        .bringIntoViewRequester(bringIntoViewRequester)
        .clip(RoundedCornerShape(20.dp)).background(t.gb.copy(alpha = cardBgAlpha))
        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onToggleExpand() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ps.def.name, style = DW.BodyLarge.copy(color = t.ob, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.width(8.dp))
                    ModelTypeBadge(ps.def.type, t)
                }
                Spacer(Modifier.height(3.dp))
                Text(ps.def.apiHost.ifEmpty { "自定义" }, style = DW.BodySmall.copy(color = t.osv, fontSize = 11.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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

                // API Host
                ModelConfigField(t, "API Host", ps.def.apiHost.ifEmpty { "输入 API 地址" }, ps.customHost, onHostChange)
                Spacer(Modifier.height(12.dp))

                // API Key
                ModelConfigField(t, "API Key", "输入 API Key", ps.apiKey, onApiKeyChange, isPassword = true)
                Spacer(Modifier.height(12.dp))

                // Test connection
                ModelTestButton(t, testState, onTest)
                Spacer(Modifier.height(12.dp))

                // Selected models
                if (ps.selectedModels.isNotEmpty()) {
                    Text("已选模型", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 8.dp))
                    ps.selectedModels.forEach { model ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(t.p.copy(alpha = if (t.isLight) 0.06f else 0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(model, style = DW.BodySmall.copy(color = t.ob), modifier = Modifier.weight(1f))
                            Box(Modifier.size(22.dp).clip(CircleShape).background(t.err.copy(alpha = 0.12f))
                                .clickable { onRemoveModel(model) }, contentAlignment = Alignment.Center) {
                                Text("−", style = TextStyle(fontSize = 16.sp, color = t.err, fontWeight = FontWeight.Bold))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Add model button
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, t.p.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .background(t.p.copy(alpha = if (t.isLight) 0.05f else 0.08f))
                    .clickable { onOpenPicker() }
                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text("+ 添加模型", style = DW.LabelLarge.copy(color = t.p))
                }
                Spacer(Modifier.height(12.dp))

                // Links
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ps.def.officialUrl?.let { url -> ModelLinkButton(t, "官方网站", url) }
                    ps.def.apiKeyUrl?.let { url -> ModelLinkButton(t, "获取 Key", url) }
                }
                Spacer(Modifier.height(12.dp))

                // Save
                ModelSaveButton(t, onSave)
            }
        }
    }
}

@Composable
private fun ModelConfigField(t: Th, label: String, hint: String, value: String, onValueChange: (String) -> Unit, isPassword: Boolean = false) {
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
private fun ModelTestButton(t: Th, state: com.hwb.aianswerer.ui.pages.TestState?, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val current = state ?: com.hwb.aianswerer.ui.pages.TestState.Idle
    val currentOnClick by rememberUpdatedState(onClick)
    Column {
        Box(Modifier.fillMaxWidth().scale(scale.value).clip(RoundedCornerShape(14.dp))
            .background(t.gb.copy(alpha = if (t.isLight) 0.55f else 0.1f))
            .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.3f else 0.1f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scale.snapTo(0.92f); val released = tryAwaitRelease()
                    scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                    if (released) currentOnClick()
                })
            }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current is com.hwb.aianswerer.ui.pages.TestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.p)
                    Spacer(Modifier.width(8.dp))
                }
                Text(when (current) { com.hwb.aianswerer.ui.pages.TestState.Idle -> "测试连接"
                    com.hwb.aianswerer.ui.pages.TestState.Testing -> "测试中 …"
                    is com.hwb.aianswerer.ui.pages.TestState.Success -> "测试连接"
                    is com.hwb.aianswerer.ui.pages.TestState.Error -> "重新测试" },
                    style = DW.LabelMedium.copy(color = when (current) {
                        is com.hwb.aianswerer.ui.pages.TestState.Success -> t.ok
                        is com.hwb.aianswerer.ui.pages.TestState.Error -> t.err; else -> t.ob }))
            }
        }
        when (current) {
            is com.hwb.aianswerer.ui.pages.TestState.Success -> Text("连接成功 (${current.ms}ms)", style = DW.BodySmall.copy(color = t.ok, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            is com.hwb.aianswerer.ui.pages.TestState.Error -> Text("连接失败: ${current.msg}", style = DW.BodySmall.copy(color = t.err, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            else -> {}
        }
    }
}

@Composable
private fun ModelSaveButton(t: Th, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    Box(Modifier.fillMaxWidth().scale(scale.value).clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite))
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.92f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) currentOnClick()
            })
        }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text("保存配置", style = DW.LabelLarge.copy(color = t.w))
    }
}

@Composable
private fun ModelLinkButton(t: Th, label: String, url: String) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { scale.snapTo(0.92f); pressed = true }
                is PressInteraction.Cancel -> { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)); pressed = false }
                is PressInteraction.Release -> { /* handled below */ }
            }
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
            pressed = false
        }
    }

    Box(Modifier.scale(scale.value).clip(RoundedCornerShape(12.dp))
        .background(t.p.copy(alpha = if (t.isLight) 0.08f else 0.12f))
        .border(1.dp, t.p.copy(alpha = if (t.isLight) 0.2f else 0.15f), RoundedCornerShape(12.dp))
        .clickable(interactionSource = interactionSource, indication = null) {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LocalIcons.Link, null, tint = t.p, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = DW.LabelSmall.copy(color = t.p))
        }
    }
}

@Composable
private fun ModelTypeBadge(type: ModelProviderType, t: Th) {
    val bg = type.color.copy(alpha = 0.15f)
    val fg = if (t.isLight) type.color.copy(alpha = 0.9f) else type.color.copy(alpha = 0.8f)
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(type.label, style = DW.LabelSmall.copy(color = fg, fontSize = 10.sp, letterSpacing = 0.3.sp))
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

/**
 * 模型能力标签组件
 * 纯语言模型显示"语言"标签，多模态模型显示"视觉"+"语言"两个标签
 */
@Composable
private fun ModelCapabilityTags(modelId: String, t: Th) {
    val isVision = remember(modelId) { ModelCapabilityChecker.isVisionModel(modelId) }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // 视觉标签（多模态模型才有）
        if (isVision) {
            val visionColor = Color(0xFF4A90D9)
            val visionBg = visionColor.copy(alpha = 0.15f)
            val visionFg = if (t.isLight) visionColor.copy(alpha = 0.9f) else visionColor.copy(alpha = 0.8f)
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(visionBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("视觉", style = DW.LabelSmall.copy(color = visionFg, fontSize = 9.sp, letterSpacing = 0.2.sp))
            }
        }
        // 语言标签（所有模型都有）
        val textColor = Color(0xFF34C759)
        val textBg = textColor.copy(alpha = 0.15f)
        val textFg = if (t.isLight) textColor.copy(alpha = 0.9f) else textColor.copy(alpha = 0.8f)
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(textBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("语言", style = DW.LabelSmall.copy(color = textFg, fontSize = 9.sp, letterSpacing = 0.2.sp))
        }
    }
}
