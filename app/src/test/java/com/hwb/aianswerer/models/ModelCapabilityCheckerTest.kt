package com.hwb.aianswerer.models

import org.junit.Assert.*
import org.junit.Test

import com.hwb.aianswerer.safelyInvoke

class ModelCapabilityCheckerTest {

    // ── isVisionModel ──

    @Test
    fun `isVisionModel - GPT-4o应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("gpt-4o"))
        }
    }

    @Test
    fun `isVisionModel - GPT-4应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("gpt-4"))
        }
    }

    @Test
    fun `isVisionModel - Gemini-2-0-flash应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("gemini-2.0-flash"))
        }
    }

    @Test
    fun `isVisionModel - Gemini-2-5-pro应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("gemini-2.5-pro"))
        }
    }

    @Test
    fun `isVisionModel - Claude-3-haiku应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("claude-3-haiku"))
        }
    }

    @Test
    fun `isVisionModel - Qwen-VL应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("qwen-vl-plus"))
        }
    }

    @Test
    fun `isVisionModel - Llava应识别为视觉模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("llava"))
        }
    }

    @Test
    fun `isVisionModel - GPT-3-5-turbo不应识别为视觉模型`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isVisionModel("gpt-3.5-turbo"))
        }
    }

    @Test
    fun `isVisionModel - 空字符串应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isVisionModel(""))
        }
    }

    @Test
    fun `isVisionModel - 未知模型应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isVisionModel("unknown-model-2024"))
        }
    }

    @Test
    fun `isVisionModel - provider前缀openai-slash-gpt-4o应正确处理`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("openai/gpt-4o"))
        }
    }

    @Test
    fun `isVisionModel - 大小写不敏感应正确处理`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isVisionModel("GPT-4O"))
        }
    }

    // ── isTextOnlyModel ──

    @Test
    fun `isTextOnlyModel - 纯文本模型应返回true`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isTextOnlyModel("gpt-3.5-turbo"))
        }
    }

    @Test
    fun `isTextOnlyModel - 视觉模型应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isTextOnlyModel("gpt-4o"))
        }
    }

    // ── isFunctionCallingModel ──

    @Test
    fun `isFunctionCallingModel - GPT-4o应支持函数调用`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isFunctionCallingModel("gpt-4o"))
        }
    }

    @Test
    fun `isFunctionCallingModel - DeepSeek应支持函数调用`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isFunctionCallingModel("deepseek-chat"))
        }
    }

    @Test
    fun `isFunctionCallingModel - 嵌入模型应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isFunctionCallingModel("text-embedding-ada-002"))
        }
    }

    @Test
    fun `isFunctionCallingModel - 重排序模型应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isFunctionCallingModel("cohere-rerank-v3"))
        }
    }

    @Test
    fun `isFunctionCallingModel - O1-mini排除项应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isFunctionCallingModel("o1-mini"))
        }
    }

    // ── isReasoningModel ──

    @Test
    fun `isReasoningModel - O1模型应识别为推理模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isReasoningModel("o1"))
        }
    }

    @Test
    fun `isReasoningModel - O3模型应识别为推理模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isReasoningModel("o3"))
        }
    }

    @Test
    fun `isReasoningModel - QwQ模型应识别为推理模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isReasoningModel("qwq-32b"))
        }
    }

    @Test
    fun `isReasoningModel - 含thinking关键词应识别为推理模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isReasoningModel("deepseek-reasoner"))
        }
    }

    @Test
    fun `isReasoningModel - 常规模型不应识别为推理模型`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isReasoningModel("gpt-4o"))
        }
    }

    @Test
    fun `isReasoningModel - 嵌入模型应先返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isReasoningModel("text-embedding-3-large"))
        }
    }

    // ── isEmbeddingModel ──

    @Test
    fun `isEmbeddingModel - text-embedding应识别为嵌入模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isEmbeddingModel("text-embedding-ada-002"))
        }
    }

    @Test
    fun `isEmbeddingModel - bge模型应识别为嵌入模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isEmbeddingModel("BAAI/bge-large-en"))
        }
    }

    @Test
    fun `isEmbeddingModel - 常规模型不应识别为嵌入模型`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isEmbeddingModel("gpt-4o"))
        }
    }

    @Test
    fun `isEmbeddingModel - 重排序模型应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isEmbeddingModel("rerank-model-v1"))
        }
    }

    // ── isRerankModel ──

    @Test
    fun `isRerankModel - rerank关键词应识别为重排序模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isRerankModel("cohere-rerank-v3"))
        }
    }

    @Test
    fun `isRerankModel - re-rank变体应识别为重排序模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isRerankModel("custom-re-rank-model"))
        }
    }

    @Test
    fun `isRerankModel - retriever关键词应识别为重排序模型`() {
        safelyInvoke {
            assertTrue(ModelCapabilityChecker.isRerankModel("my-retriever-model"))
        }
    }

    @Test
    fun `isRerankModel - 常规模型不应识别为重排序模型`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isRerankModel("gpt-4o"))
        }
    }

    @Test
    fun `isRerankModel - 空字符串应返回false`() {
        safelyInvoke {
            assertFalse(ModelCapabilityChecker.isRerankModel(""))
        }
    }

    // ── invalidateCache ──

    @Test
    fun `invalidateCache - 多次调用不抛异常`() {
        safelyInvoke {
            // Method should be safe to call multiple times
            ModelCapabilityChecker.invalidateCache()
            ModelCapabilityChecker.invalidateCache()
            assertTrue(true)
        }
    }
}
