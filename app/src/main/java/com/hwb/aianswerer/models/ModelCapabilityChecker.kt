package com.hwb.aianswerer.models

import com.hwb.aianswerer.config.AppConfig

/**
 * 模型能力判断工具类
 * 通过模型 ID 匹配来判断是纯语言模型还是多模态模型（支持视觉）
 *
 * ⚠️ 此文件由脚本自动生成，请勿手动修改
 * 更新命令: python scripts/update_model_whitelist.py
 * 数据来源: https://github.com/CherryHQ/cherry-studio
 */
object ModelCapabilityChecker {

    // ── 视觉模型白名单（多模态模型）──────────────────────────────────────
    // Auto-generated from Cherry Studio GitHub

    private val VISION_ALLOWED_MODELS = listOf(
        "llava",
        "moondream",
        "minicpm",
        "gemini-1\\.5",
        "gemini-2\\.0",
        "gemini-2\\.5",
        "gemini-3(?:\\.\\d)?-(?:flash|pro)(?:-preview)?",
        "gemini-(flash|pro|flash-lite)-latest",
        "gemini-exp",
        "claude-3",
        "claude-haiku-4",
        "claude-sonnet-4",
        "claude-opus-4",
        "claude-fable",
        "vision",
        "glm-4(?:\\.\\d+)?v(?:-[\\w-]+)?",
        "qwen-vl",
        "qwen2-vl",
        "qwen2.5-vl",
        "qwen3-vl",
        "qwen3\\.[5-9](?!-max)(?:-[\\w-]+)?",
        "qwen2.5-omni",
        "qwen3-omni(?:-[\\w-]+)?",
        "qvq",
        "internvl2",
        "grok-vision-beta",
        "grok-4(?:-[\\w-]+)?",
        "grok-build(?:-[\\w-]+)?",
        "pixtral",
        "gpt-4(?:-[\\w-]+)",
        "gpt-4.1(?:-[\\w-]+)?",
        "gpt-4o(?:-[\\w-]+)?",
        "gpt-4.5(?:-[\\w-]+)",
        "gpt-5(?:-[\\w-]+)?",
        "chatgpt-4o(?:-[\\w-]+)?",
        "o1(?:-[\\w-]+)?",
        "o3(?:-[\\w-]+)?",
        "o4(?:-[\\w-]+)?",
        "deepseek-vl(?:[\\w-]+)?",
        "kimi-k2\\.[5-9]\\d*(?:-[\\w-]+)?",
        "kimi-latest",
        "gemma-?[3-4](?:[-.\\w]+)?",
        "doubao-seed-1[.-][68](?:-[\\w-]+)?",
        "doubao-seed-2[.-]0(?:-[\\w-]+)?",
        "doubao-seed-code(?:-[\\w-]+)?",
        "minimax-m3(?:-[\\w-]+)?",
        "kimi-thinking-preview",
        "gemma3(?:[-:\\w]+)?",
        "kimi-vl-a3b-thinking(?:-[\\w-]+)?",
        "llama-guard-4(?:-[\\w-]+)?",
        "llama-4(?:-[\\w-]+)?",
        "step-1o(?:.*vision)?",
        "step-1v(?:-[\\w-]+)?",
        "qwen-omni(?:-[\\w-]+)?",
        "mistral-large-(2512|latest)",
        "mistral-medium-(2508|latest)",
        "mistral-small",
        "mimo-v2\\.5$",
        "mimo-v2-omni(?:-[\\w-]+)?",
        "glm-5v-turbo"
    )

    private val VISION_EXCLUDED_MODELS = listOf(
        "gpt-4-\\d+-preview",
        "gpt-4-turbo-preview",
        "gpt-4-32k",
        "gpt-4-\\d+",
        "o1-mini",
        "o3-mini",
        "o1-preview",
        "AIDC-AI/Marco-o1"
    )

    // ── 函数调用/工具使用模型（来自 Cherry Studio tooluse.ts）──────────────

    private val FUNCTION_CALLING_MODELS = listOf(
        "gpt-4o", "gpt-4o-mini", "gpt-4", "gpt-4.5",
        "gpt-oss(?:-[\\w-]+)", "gpt-5(?:[0-9-]+)?",
        "o(1|3|4)(?:-[\\w-]+)?",
        "claude", "qwen", "qwen3", "hunyuan", "deepseek",
        "glm-4(?:-[\\w-]+)?", "glm-4.5(?:-[\\w-]+)?", "glm-4.7(?:-[\\w-]+)?",
        "glm-5(?:-[\\w-]+)?", "learnlm(?:-[\\w-]+)?",
        "gemini(?:-[\\w-]+)?", "gemma-?4(?:[-.\\w]+)?",
        "grok-3(?:-[\\w-]+)?", "grok-4(?:-[\\w-]+)?", "grok-build(?:-[\\w-]+)?",
        "doubao-seed-1[.-][68](?:-[\\w-]+)?", "doubao-seed-2[.-]0(?:-[\\w-]+)?",
        "doubao-seed-code(?:-[\\w-]+)?", "kimi-k2(?:-[\\w-]+)?",
        "ling-\\w+(?:-[\\w-]+)?", "ring-\\w+(?:-[\\w-]+)?",
        "minimax-m[23](?:\\.\\d+)?(?:-[\\w-]+)?",
        "mimo-v2\\.5(?:-pro)?(?!-)", "mimo-v2-flash", "mimo-v2-pro", "mimo-v2-omni",
        "glm-5v-turbo"
    )

    private val FUNCTION_CALLING_EXCLUDED = listOf(
        "aqa(?:-[\\w-]+)?", "imagen(?:-[\\w-]+)?",
        "o1-mini", "o1-preview", "AIDC-AI/Marco-o1",
        "gemini-1(?:\\.[\\w-]+)?", "qwen-mt(?:-[\\w-]+)?",
        "gpt-5-chat(?:-[\\w-]+)?", "glm-4\\.5v",
        "gemini-2.5-flash-image(?:-[\\w-]+)?",
        "gemini-2.0-flash-preview-image-generation",
        "gemini-3(?:\\.\\d+)?-pro-image(?:-[\\w-]+)?",
        "deepseek-v3.2-speciale", "deepseek-r1(?:[-:][\\w.-]+)?"
    )

    // ── 推理模型（来自 Cherry Studio reasoning.ts）─────────────────────────

    private val REASONING_REGEX = Regex(
        "^(?!.*-non-reasoning\\b)(o\\d+(?:-[\\w-]+)?" +
        "|.*\\b(?:reasoning|reasoner|thinking|think)\\b.*" +
        "|.*-[rR]\\d+.*" +
        "|.*\\bqwq(?:-[\\w-]+)?\\b.*" +
        "|.*\\bhunyuan-t1(?:-[\\w-]+)?\\b.*" +
        "|.*\\bglm-zero-preview\\b.*" +
        "|.*\\bgrok-(?:3-mini|4|4-fast|build)(?:-[\\w-]+)?\\b.*)$",
        RegexOption.IGNORE_CASE
    )

    // ── 嵌入模型（来自 Cherry Studio embedding.ts）──────────────────────────

    private val EMBEDDING_REGEX = Regex(
        "(?:^text-|embed|bge-|e5-|LLM2Vec|retrieval|uae-|gte-|jina-clip|jina-embeddings|voyage-)",
        RegexOption.IGNORE_CASE
    )

    private val RERANK_REGEX = Regex(
        "(?:rerank|re-rank|re-ranker|re-ranking|retrieval|retriever)",
        RegexOption.IGNORE_CASE
    )

    // 缓存动态白名单，避免每次都从 AppConfig 读取
    private var cachedDynamicModels: List<String>? = null
    private var cachedDynamicExcluded: List<String>? = null

    /**
     * 获取合并后的视觉模型白名单（硬编码 + 动态更新）
     */
    private fun getAllowedModels(): List<String> {
        val dynamic = getDynamicModels()
        return if (dynamic.isEmpty()) {
            VISION_ALLOWED_MODELS
        } else {
            VISION_ALLOWED_MODELS + dynamic
        }
    }

    /**
     * 获取合并后的排除列表（硬编码 + 动态更新）
     */
    private fun getExcludedModels(): List<String> {
        val dynamic = getDynamicExcluded()
        return if (dynamic.isEmpty()) {
            VISION_EXCLUDED_MODELS
        } else {
            VISION_EXCLUDED_MODELS + dynamic
        }
    }

    private fun getDynamicModels(): List<String> {
        if (cachedDynamicModels == null) {
            cachedDynamicModels = AppConfig.getDynamicVisionModels()
        }
        return cachedDynamicModels ?: emptyList()
    }

    private fun getDynamicExcluded(): List<String> {
        if (cachedDynamicExcluded == null) {
            cachedDynamicExcluded = AppConfig.getDynamicVisionExcluded()
        }
        return cachedDynamicExcluded ?: emptyList()
    }

    /**
     * 清除缓存，当动态白名单更新后调用
     */
    fun invalidateCache() {
        cachedDynamicModels = null
        cachedDynamicExcluded = null
    }

    /**
     * 判断是否为纯语言模型（不支持视觉）
     */
    fun isTextOnlyModel(modelId: String): Boolean {
        return !isVisionModel(modelId)
    }

    /**
     * 判断是否支持视觉（多模态模型，同时支持语言和视觉）
     */
    fun isVisionModel(modelId: String): Boolean {
        val normalizedId = getLowerBaseModelName(modelId)
        val allowed = getAllowedModels()
        val excluded = getExcludedModels()
        val regex = Regex(
            "\\b(?!(?:${excluded.joinToString("|")})\\b)(${allowed.joinToString("|")})\\b",
            RegexOption.IGNORE_CASE
        )
        return regex.containsMatchIn(normalizedId)
    }

    /**
     * 判断是否支持函数调用/工具使用（来自 Cherry Studio tooluse.ts）
     */
    fun isFunctionCallingModel(modelId: String): Boolean {
        if (isEmbeddingModel(modelId) || isRerankModel(modelId)) return false
        val normalizedId = getLowerBaseModelName(modelId)
        val regex = Regex(
            "\\b(?!(?:${FUNCTION_CALLING_EXCLUDED.joinToString("|")})\\b)(?:${FUNCTION_CALLING_MODELS.joinToString("|")})\\b",
            RegexOption.IGNORE_CASE
        )
        return regex.containsMatchIn(normalizedId)
    }

    /**
     * 判断是否为推理模型（来自 Cherry Studio reasoning.ts）
     */
    fun isReasoningModel(modelId: String): Boolean {
        if (isEmbeddingModel(modelId) || isRerankModel(modelId)) return false
        val normalizedId = getLowerBaseModelName(modelId)
        return REASONING_REGEX.containsMatchIn(normalizedId)
    }

    /**
     * 判断是否为嵌入/向量模型（来自 Cherry Studio embedding.ts）
     */
    fun isEmbeddingModel(modelId: String): Boolean {
        if (isRerankModel(modelId)) return false
        val normalizedId = getLowerBaseModelName(modelId)
        return EMBEDDING_REGEX.containsMatchIn(normalizedId)
    }

    /**
     * 判断是否为重排序模型（来自 Cherry Studio embedding.ts）
     */
    fun isRerankModel(modelId: String): Boolean {
        val normalizedId = getLowerBaseModelName(modelId)
        return RERANK_REGEX.containsMatchIn(normalizedId)
    }

    /**
     * 获取模型 ID 的基础名称（小写）
     * 处理 provider 前缀，如 "openai/gpt-4o" -> "gpt-4o"
     */
    private fun getLowerBaseModelName(modelId: String): String {
        return modelId.lowercase().split("/").last()
    }
}
