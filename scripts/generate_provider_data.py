"""
Generate provider_data.json from API-Key-Manager's provider definitions.
Replaces Cherry Studio data source with OpenCode API-Key-Manager data.

Usage:
    python scripts/generate_provider_data.py [--api-key-manager-dir PATH]
"""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path


# ── Type mapping: API-Key-Manager provider → Cherry Studio type ──
# Derived from provider behavior: auth headers, endpoint patterns
PROVIDER_TYPE_MAP = {
    "openai": "openai",
    "deepseek": "openai",
    "groq": "openai",
    "grok": "openai",
    "perplexity": "openai",
    "together": "openai",
    "mistral": "openai",
    "cohere": "openai",
    "replicate": "openai",
    "huggingface": "openai",
    "fireworks": "openai",
    "openrouter": "openai",
    "cerebras": "openai",
    "nvidia": "openai",
    "hyperbolic": "openai",
    "poe": "openai",
    "longcat": "openai",
    "dashscope": "openai",
    "dashscope-coding": "openai",
    "modelscope": "openai",
    "zhipu": "openai",
    "zhipu-coding": "openai",
    "kimi": "openai",
    "kimi-coding": "openai",
    "minimax": "openai",
    "minimax-plan": "openai",
    "siliconflow": "openai",
    "baichuan": "openai",
    "yi": "openai",
    "mimo": "openai",
    "mimo-plan": "openai",
    "stepfun": "openai",
    "doubao": "openai",
    "infini": "openai",
    "infini-coding": "openai",
    "zai": "openai",
    "ai302": "openai",
    "ppio": "openai",
    "dmxapi": "openai",
    "ocoolai": "openai",
    "tencent-hunyuan": "openai",
    "cstcloud": "openai",
    "opencode-go": "openai",
    "opencode-zen": "openai",
    "anthropic": "anthropic",
    "google": "gemini",
}

# ── API host construction ──
# Some providers need explicit apiHost overrides for the Cherry format
API_HOST_OVERRIDES = {
    # Providers where check_endpoint doesn't reveal the full path
    "deepseek": "https://api.deepseek.com/v1",
    "dashscope": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "dashscope-coding": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "zhipu": "https://open.bigmodel.cn/api/paas/v4",
    "zhipu-coding": "https://open.bigmodel.cn/api/paas/v4",
    "kimi": "https://api.moonshot.cn/v1",
    "kimi-coding": "https://api.moonshot.cn/v1",
    "minimax": "https://api.minimax.chat/v1",
    "minimax-plan": "https://api.minimax.chat/v1",
    "siliconflow": "https://api.siliconflow.cn/v1",
    "baichuan": "https://api.baichuan-ai.com/v1",
    "yi": "https://api.lingyiwanwu.com/v1",
    "mimo": "https://api.xiaomi.com/v1",
    "mimo-plan": "https://api.xiaomi.com/v1",
    "stepfun": "https://api.stepfun.com/v1",
    "doubao": "https://ark.cn-beijing.volces.com/api/v3",
    "infini": "https://cloud.infini-ai.com/maas/v1",
    "infini-coding": "https://cloud.infini-ai.com/maas/v1",
    "zai": "https://api.z.ai/api/paas/v4",
    "ai302": "https://api.302.ai/v1",
    "ppio": "https://api.ppio.ai/v1",
    "dmxapi": "https://www.dmxapi.cn/v1",
    "ocoolai": "https://api.ocoolai.com/v1",
    "tencent-hunyuan": "https://api.hunyuan.cloud.tencent.com/v1",
    "cstcloud": "https://api.cstcloud.cn/v1",
    "longcat": "https://api.longcat.chat/v1",
    "openrouter": "https://openrouter.ai/api/v1",
    "groq": "https://api.groq.com/openai/v1",
    "modelscope": "https://api-inference.modelscope.cn/v1",
    "grok": "https://api.x.ai/v1",
    "cerebras": "https://api.cerebras.ai/v1",
    "nvidia": "https://integrate.api.nvidia.com/v1",
    "hyperbolic": "https://api.hyperbolic.xyz/v1",
    "poe": "https://api.poe.com/v1",
    "opencode-go": "https://opencode.ai/zen/go/v1",
    "opencode-zen": "https://opencode.ai/zen/v1",
}

# ── Anthropic API host overrides ──
ANTHROPIC_API_HOST_MAP = {
    "anthropic": "https://api.anthropic.com",
}

# ── Model group mapping ──
# Heuristic: extract group name from model prefix
def infer_group(model_id: str) -> str:
    """Infer a display group from a model ID."""
    # Common patterns
    patterns = [
        ("gpt-", "GPT"),
        ("claude-", "Claude"),
        ("gemini-", "Gemini"),
        ("deepseek-", "DeepSeek"),
        ("qwen", "Qwen"),
        ("glm-", "GLM"),
        ("moonshot-", "Moonshot"),
        ("doubao-", "Doubao"),
        ("minimax", "MiniMax"),
        ("mimo-", "MiMo"),
        ("llama", "Llama"),
        ("mistral", "Mistral"),
        ("mixtral", "Mixtral"),
        ("yi-", "Yi"),
        ("baichuan", "Baichuan"),
        ("grok-", "Grok"),
        ("o1", "OpenAI o-series"),
        ("o3", "OpenAI o-series"),
        ("o4", "OpenAI o-series"),
    ]
    model_lower = model_id.lower()
    for prefix, group in patterns:
        if model_lower.startswith(prefix) or prefix in model_lower:
            return group
    # Use first segment as group
    parts = model_id.replace("/", "-").split("-")
    if parts:
        return parts[0].capitalize()
    return "Other"


def compute_api_host(provider_name: str, base_url: str, check_endpoint: str) -> str:
    """Compute the Cherry-format apiHost from API-Key-Manager provider info."""
    # Check overrides first
    if provider_name in API_HOST_OVERRIDES:
        return API_HOST_OVERRIDES[provider_name]
    
    # Standard computation: base_url + version from check_endpoint
    import re
    version_match = re.match(r'(/v\d+)', check_endpoint or '')
    if version_match:
        return f"{base_url.rstrip('/')}{version_match.group(1)}"
    # Default: just base_url
    return base_url.rstrip('/')


def load_providers_info(api_key_manager_dir: str) -> dict:
    """Load provider metadata from API-Key-Manager __init__.py and config files."""
    info = {}
    
    # Hard-coded provider metadata extracted from API-Key-Manager
    # This avoids needing to import Python modules at runtime
    
    # From __init__.py: PROVIDERS dict
    provider_metadata = {
        "openai": {"base_url": "https://api.openai.com", "check_endpoint": "/v1/models"},
        "anthropic": {"base_url": "https://api.anthropic.com", "check_endpoint": "/v1/models"},
        "google": {"base_url": "https://generativelanguage.googleapis.com", "check_endpoint": "/v1beta/models"},
        "grok": {"base_url": "https://api.x.ai", "check_endpoint": "/v1/models"},
        "deepseek": {"base_url": "https://api.deepseek.com", "check_endpoint": "/models"},
        "groq": {"base_url": "https://api.groq.com", "check_endpoint": "/openai/v1/models"},
        "perplexity": {"base_url": "https://api.perplexity.ai", "check_endpoint": "/models"},
        "together": {"base_url": "https://api.together.xyz", "check_endpoint": "/v1/models"},
        "mistral": {"base_url": "https://api.mistral.ai", "check_endpoint": "/v1/models"},
        "cohere": {"base_url": "https://api.cohere.ai", "check_endpoint": "/v1/models"},
        "replicate": {"base_url": "https://api.replicate.com", "check_endpoint": "/v1/models"},
        "huggingface": {"base_url": "https://api-inference.huggingface.co", "check_endpoint": "/v1/models"},
        "fireworks": {"base_url": "https://api.fireworks.ai", "check_endpoint": "/inference/v1/models"},
        "openrouter": {"base_url": "https://openrouter.ai", "check_endpoint": "/api/v1/models"},
        "cerebras": {"base_url": "https://api.cerebras.ai", "check_endpoint": "/v1/models"},
        "nvidia": {"base_url": "https://integrate.api.nvidia.com", "check_endpoint": "/v1/models"},
        "hyperbolic": {"base_url": "https://api.hyperbolic.xyz", "check_endpoint": "/v1/models"},
        "poe": {"base_url": "https://api.poe.com", "check_endpoint": "/v1/models"},
        "longcat": {"base_url": "https://api.longcat.chat", "check_endpoint": "/v1/models"},
        # Chinese providers
        "dashscope": {"base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1", "check_endpoint": "/models"},
        "dashscope-coding": {"base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1", "check_endpoint": "/models"},
        "modelscope": {"base_url": "https://api-inference.modelscope.cn", "check_endpoint": "/v1/models"},
        "zhipu": {"base_url": "https://open.bigmodel.cn/api/paas/v4", "check_endpoint": "/models"},
        "zhipu-coding": {"base_url": "https://open.bigmodel.cn/api/paas/v4", "check_endpoint": "/models"},
        "kimi": {"base_url": "https://api.moonshot.cn", "check_endpoint": "/v1/models"},
        "kimi-coding": {"base_url": "https://api.moonshot.cn", "check_endpoint": "/v1/models"},
        "minimax": {"base_url": "https://api.minimax.chat", "check_endpoint": "/v1/models"},
        "minimax-plan": {"base_url": "https://api.minimax.chat", "check_endpoint": "/v1/models"},
        "siliconflow": {"base_url": "https://api.siliconflow.cn", "check_endpoint": "/v1/models"},
        "baichuan": {"base_url": "https://api.baichuan-ai.com", "check_endpoint": "/v1/models"},
        "yi": {"base_url": "https://api.lingyiwanwu.com", "check_endpoint": "/v1/models"},
        "mimo": {"base_url": "https://api.xiaomi.com", "check_endpoint": "/v1/models"},
        "mimo-plan": {"base_url": "https://api.xiaomi.com", "check_endpoint": "/v1/models"},
        "stepfun": {"base_url": "https://api.stepfun.com", "check_endpoint": "/v1/models"},
        "doubao": {"base_url": "https://ark.cn-beijing.volces.com/api/v3", "check_endpoint": "/models"},
        "infini": {"base_url": "https://cloud.infini-ai.com/maas", "check_endpoint": "/v1/models"},
        "infini-coding": {"base_url": "https://cloud.infini-ai.com/maas", "check_endpoint": "/v1/models"},
        "zai": {"base_url": "https://api.z.ai/api/paas/v4", "check_endpoint": "/models"},
        "ai302": {"base_url": "https://api.302.ai", "check_endpoint": "/v1/models"},
        "ppio": {"base_url": "https://api.ppio.ai", "check_endpoint": "/v1/models"},
        "dmxapi": {"base_url": "https://www.dmxapi.cn", "check_endpoint": "/v1/models"},
        "ocoolai": {"base_url": "https://api.ocoolai.com", "check_endpoint": "/v1/models"},
        "tencent-hunyuan": {"base_url": "https://api.hunyuan.cloud.tencent.com", "check_endpoint": "/v1/models"},
        "cstcloud": {"base_url": "https://api.cstcloud.cn", "check_endpoint": "/v1/models"},
        "opencode-go": {"base_url": "https://opencode.ai/zen/go", "check_endpoint": "/v1/models"},
        "opencode-zen": {"base_url": "https://opencode.ai/zen", "check_endpoint": "/v1/models"},
    }
    
    # Display names from API-Key-Manager DISPLAY_NAMES
    display_names = {
        "openai": "OpenAI", "anthropic": "Anthropic", "google": "Google Gemini",
        "deepseek": "DeepSeek", "groq": "Groq", "grok": "Grok (xAI)",
        "perplexity": "Perplexity", "together": "Together AI", "mistral": "Mistral AI",
        "cohere": "Cohere", "replicate": "Replicate", "huggingface": "Hugging Face",
        "fireworks": "Fireworks AI", "openrouter": "OpenRouter",
        "dashscope": "阿里百炼", "dashscope-coding": "阿里百炼编程",
        "modelscope": "魔搭 ModelScope", "zhipu": "智谱 GLM",
        "kimi": "Kimi (月之暗面)", "minimax": "MiniMax", "minimax-plan": "MiniMax 计划版",
        "siliconflow": "硅基流动", "baichuan": "百川智能", "yi": "零一万物",
        "cerebras": "Cerebras AI", "nvidia": "NVIDIA NIM", "hyperbolic": "Hyperbolic",
        "poe": "Poe", "longcat": "LongCat", "mimo": "MiMo", "mimo-plan": "MiMo 计划版",
        "stepfun": "阶跃星辰", "doubao": "豆包 (字节)", "infini": "无问芯穹",
        "zai": "Z.AI", "ai302": "302.AI", "ppio": "PPIO", "dmxapi": "DMXAPI",
        "ocoolai": "OCoolAI", "tencent-hunyuan": "腾讯混元",
        "zhipu-coding": "智谱 GLM 编程版", "kimi-coding": "Kimi 编程版",
        "infini-coding": "无问芯穹 编程版", "cstcloud": "中算云",
        "opencode-go": "OpenCode Go", "opencode-zen": "OpenCode Zen",
    }
    
    # Website info from API-Key-Manager PROVIDER_WEBSITES
    website_info = {
        "openai": {"official": "https://platform.openai.com", "apiKey": "https://platform.openai.com/api-keys", "docs": "https://platform.openai.com/docs"},
        "anthropic": {"official": "https://console.anthropic.com", "apiKey": "https://console.anthropic.com/settings/keys", "docs": "https://docs.anthropic.com"},
        "google": {"official": "https://aistudio.google.com", "apiKey": "https://aistudio.google.com/apikey", "docs": "https://ai.google.dev/docs"},
        "deepseek": {"official": "https://platform.deepseek.com", "apiKey": "https://platform.deepseek.com/api_keys", "docs": "https://platform.deepseek.com/api-docs"},
        "groq": {"official": "https://console.groq.com", "apiKey": "https://console.groq.com/keys", "docs": "https://docs.groq.com"},
        "mistral": {"official": "https://console.mistral.ai", "apiKey": "https://console.mistral.ai/api-keys", "docs": "https://docs.mistral.ai"},
        "cohere": {"official": "https://dashboard.cohere.com", "apiKey": "https://dashboard.cohere.com/api-keys", "docs": "https://docs.cohere.com"},
        "replicate": {"official": "https://replicate.com", "apiKey": "https://replicate.com/account/api-tokens", "docs": "https://replicate.com/docs"},
        "huggingface": {"official": "https://huggingface.co", "apiKey": "https://huggingface.co/settings/tokens", "docs": "https://huggingface.co/docs"},
        "fireworks": {"official": "https://fireworks.ai", "apiKey": "https://fireworks.ai/account/api-keys", "docs": "https://docs.fireworks.ai"},
        "perplexity": {"official": "https://perplexity.ai", "apiKey": "https://www.perplexity.ai/settings/api", "docs": "https://docs.perplexity.ai"},
        "together": {"official": "https://api.together.xyz", "apiKey": "https://api.together.ai/settings/api-keys", "docs": "https://docs.together.ai"},
        "openrouter": {"official": "https://openrouter.ai", "apiKey": "https://openrouter.ai/settings/keys", "docs": "https://openrouter.ai/docs"},
        "dashscope": {"official": "https://dashscope.aliyun.com", "apiKey": "https://dashscope.console.aliyun.com/apiKey", "docs": "https://help.aliyun.com/zh/dashscope/"},
        "zhipu": {"official": "https://open.bigmodel.cn", "apiKey": "https://open.bigmodel.cn/usercenter/apikeys", "docs": "https://open.bigmodel.cn/dev/api"},
        "kimi": {"official": "https://platform.moonshot.cn", "apiKey": "https://platform.moonshot.cn/console/api-keys", "docs": "https://platform.moonshot.cn/docs"},
        "minimax": {"official": "https://platform.minimaxi.com", "apiKey": "https://platform.minimaxi.com/user-center/basic-information/interface-key", "docs": "https://platform.minimaxi.com/document"},
        "siliconflow": {"official": "https://siliconflow.cn", "apiKey": "https://cloud.siliconflow.cn/account/ak", "docs": "https://docs.siliconflow.cn"},
        "baichuan": {"official": "https://platform.baichuan-ai.com", "apiKey": "https://platform.baichuan-ai.com/console/apikey", "docs": "https://platform.baichuan-ai.com/docs"},
        "yi": {"official": "https://platform.lingyiwanwu.com", "apiKey": "https://platform.lingyiwanwu.com/apikeys", "docs": "https://platform.lingyiwanwu.com/docs"},
        "cerebras": {"official": "https://cerebras.ai", "apiKey": "https://cloud.cerebras.ai", "docs": "https://docs.cerebras.ai"},
        "nvidia": {"official": "https://build.nvidia.com", "apiKey": "", "docs": "https://docs.api.nvidia.com"},
        "grok": {"official": "https://console.x.ai", "apiKey": "https://console.x.ai", "docs": "https://docs.x.ai"},
        "poe": {"official": "https://poe.com", "apiKey": "https://poe.com/api/keys", "docs": "https://developer.poe.com"},
        "stepfun": {"official": "https://platform.stepfun.com", "apiKey": "https://platform.stepfun.com/interface-key", "docs": "https://platform.stepfun.com/docs"},
        "doubao": {"official": "https://console.volcengine.com/ark", "apiKey": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey", "docs": "https://www.volcengine.com/docs/82379"},
        "infini": {"official": "https://cloud.infini-ai.com", "apiKey": "https://cloud.infini-ai.com/iam/secret/key", "docs": "https://docs.infini-ai.com"},
        "mimo": {"official": "https://mimo.xiaomi.com", "apiKey": "https://mimo.xiaomi.com/api-keys", "docs": "https://mimo.xiaomi.com/docs"},
        "hyperbolic": {"official": "https://hyperbolic.xyz", "apiKey": "https://app.hyperbolic.xyz/settings", "docs": "https://docs.hyperbolic.xyz"},
        "modelscope": {"official": "https://modelscope.cn", "apiKey": "https://modelscope.cn/my/myaccesstoken", "docs": "https://modelscope.cn/docs"},
        "ppio": {"official": "https://ppinfra.com", "apiKey": "https://ppio.ai/console", "docs": "https://docs.ppinfra.com"},
        "dmxapi": {"official": "https://www.dmxapi.cn", "apiKey": "https://www.dmxapi.cn/register", "docs": "https://www.dmxapi.cn/docs"},
        "ocoolai": {"official": "https://ocoolai.com", "apiKey": "https://ocoolai.com/docs", "docs": "https://ocoolai.com/docs"},
        "ai302": {"official": "https://302.ai", "apiKey": "https://dash.302.ai/apis", "docs": "https://302.ai/docs"},
        "zai": {"official": "https://zai.ai", "apiKey": "https://z.ai/manage-apikey/apikey-list", "docs": "https://zai.ai/docs"},
        "longcat": {"official": "https://longcat.chat", "apiKey": "https://longcat.chat/platform/api_keys", "docs": "https://longcat.chat/docs"},
        "tencent-hunyuan": {"official": "https://cloud.tencent.com/product/hunyuan", "apiKey": "https://console.cloud.tencent.com/hunyuan/api-key", "docs": "https://cloud.tencent.com/document/product/1729"},
        "cstcloud": {"official": "https://www.cstcloud.com", "apiKey": "", "docs": "https://www.cstcloud.com/docs"},
        "opencode-go": {"official": "https://opencode.ai", "apiKey": "https://opencode.ai/dashboard", "docs": "https://opencode.ai/docs/zh-cn/go/"},
        "opencode-zen": {"official": "https://opencode.ai", "apiKey": "https://opencode.ai/dashboard", "docs": "https://opencode.ai/docs/"},
    }
    
    return {
        "metadata": provider_metadata,
        "display_names": display_names,
        "website_info": website_info,
    }


def load_models_registry(api_key_manager_dir: str) -> dict[str, list[str]]:
    """Load PROVIDER_MODELS from models_registry.py by executing it."""
    registry_path = Path(api_key_manager_dir) / "key_manager" / "providers" / "models_registry.py"
    
    namespace = {}
    with open(registry_path, 'r', encoding='utf-8') as f:
        code = f.read()
    exec(code, namespace)
    
    return namespace.get("PROVIDER_MODELS", {})


def main():
    api_key_manager_dir = os.environ.get(
        "API_KEY_MANAGER_DIR",
        str(Path(__file__).parent.parent.parent / "api-key-manager")
    )
    
    # Fallback: use the cloned repo in temp
    if not Path(api_key_manager_dir).exists():
        api_key_manager_dir = os.path.expandvars(
            r"%LOCALAPPDATA%\Temp\opencode\api-key-manager"
        )
    if not Path(api_key_manager_dir).exists():
        api_key_manager_dir = os.path.expandvars(
            r"%TEMP%\opencode\api-key-manager"
        )
    
    if not Path(api_key_manager_dir).exists():
        print(f"ERROR: API-Key-Manager directory not found: {api_key_manager_dir}")
        print("Clone it first: git clone https://github.com/Townrain/API-Key-Manager.git")
        sys.exit(1)
    
    print(f"Using API-Key-Manager at: {api_key_manager_dir}")
    
    # Load provider info
    info = load_providers_info(api_key_manager_dir)
    metadata = info["metadata"]
    display_names = info["display_names"]
    website_info = info["website_info"]
    
    # Load models
    try:
        provider_models = load_models_registry(api_key_manager_dir)
        print(f"Loaded models for {len(provider_models)} providers")
    except Exception as e:
        print(f"Warning: Could not load models_registry.py: {e}")
        provider_models = {}

    # OpenCode Zen/Go models (from models.dev)
    _opencode_models = {
        "opencode-zen": [
            "claude-fable-5",
            "deepseek-v4-flash-free",
            "deepseek-v4-pro",
            "mimo-v2.5-free",
            "mimo-v2.5-pro",
            "qwen3-coder-plus",
        ],
        "opencode-go": [
            "claude-fable-5",
            "deepseek-v4-flash-free",
            "deepseek-v4-pro",
            "gpt-5.2",
            "gemini-3.0-flash",
        ],
    }
    for pid, models in _opencode_models.items():
        if pid not in provider_models or not provider_models[pid]:
            provider_models[pid] = models
            print(f"  Added OpenCode models for {pid}: {len(models)} models")
    # Build provider entries
    providers = []
    total_models = 0
    
    for provider_name, meta in metadata.items():
        if provider_name not in display_names:
            continue
        
        # Determine type
        provider_type = PROVIDER_TYPE_MAP.get(provider_name, "openai")
        
        # Compute apiHost
        api_host = compute_api_host(
            provider_name,
            meta["base_url"],
            meta.get("check_endpoint", "/v1/models")
        )
        
        # Get models
        models_list = provider_models.get(provider_name, [])
        
        # Get websites
        websites = website_info.get(provider_name, {})
        
        # Anthropic API host
        anthropic_api_host = ANTHROPIC_API_HOST_MAP.get(provider_name)
        
        # Build model entries
        model_entries = []
        seen = set()
        for model_id in models_list:
            if model_id in seen:
                continue
            seen.add(model_id)
            model_entries.append({
                "id": model_id,
                "name": model_id,
                "group": infer_group(model_id)
            })
        
        total_models += len(model_entries)
        
        providers.append({
            "id": provider_name,
            "name": display_names[provider_name],
            "type": provider_type,
            "apiHost": api_host,
            "anthropicApiHost": anthropic_api_host,
            "models": model_entries,
            "websites": {
                "official": websites.get("official"),
                "apiKey": websites.get("apiKey"),
                "docs": websites.get("docs"),
                "models": None,
            } if websites else None,
        })
    
    # Sort: put common providers first
    priority_ids = {"openai", "anthropic", "google", "deepseek", "dashscope", "zhipu", "kimi", "siliconflow", "opencode-zen", "opencode-go"}
    providers.sort(key=lambda p: (0 if p["id"] in priority_ids else 1, p["name"]))
    
    # Build output
    output = {
        "version": 2,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "providerCount": len(providers),
        "modelCount": total_models,
        "source": "opencode",
        "providers": providers,
    }
    
    # Write output
    output_path = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "provider_data.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    
    print(f"\nGenerated {output_path}")
    print(f"  Version: {output['version']}")
    print(f"  Providers: {output['providerCount']}")
    print(f"  Models: {output['modelCount']}")
    print(f"  Source: OpenCode (API-Key-Manager)")
    
    # Print summary
    print("\nProviders by type:")
    type_counts = {}
    for p in providers:
        t = p["type"]
        type_counts[t] = type_counts.get(t, 0) + 1
    for t, c in sorted(type_counts.items()):
        print(f"  {t}: {c} providers")


if __name__ == "__main__":
    main()
