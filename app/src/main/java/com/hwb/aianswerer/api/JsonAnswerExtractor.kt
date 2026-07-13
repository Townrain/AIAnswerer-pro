package com.hwb.aianswerer.api

import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.JsonUtil

/**
 * JSON 答案解析器。
 *
 * 负责从 AI 返回的原始文本中提取和解析 JSON 格式的答案。
 * 从 OpenAIClient 中抽取出来，保持 HTTP/流式处理和 JSON 解析的关注点分离。
 */
class JsonAnswerExtractor(
    private val gson: com.google.gson.Gson = JsonUtil.gson
) {

    /**
     * 解析 AI 返回的 JSON 答案
     * 策略：直接解析原文 → 提取+修复 → 正则提取 → 文本降级
     */
    fun parseJsonAnswers(content: String): List<AIAnswer> {
        val trimmed = content.trim()

        // 策略1：直接解析原文（AI 通常返回干净 JSON）
        tryParseAsAnswers(trimmed)?.let {
            AppLog.d("API", "JSON解析: 直接解析成功, size=${it.size}")
            return it
        }

        // 策略2：提取 JSON 负载 + 修复
        val extracted = extractJsonPayload(trimmed)
        val fixed = fixMalformedJson(extracted)
        tryParseAsAnswers(fixed)?.let {
            AppLog.d("API", "JSON解析: 提取+修复成功, size=${it.size}")
            return it
        }

        // 策略3：正则提取 JSON 数组
        val arrayRegex = Regex("""\[[\s\S]*]""")
        arrayRegex.find(trimmed)?.let { match ->
            val candidate = fixMalformedJson(match.value)
            tryParseAsAnswers(candidate)?.let {
                AppLog.d("API", "JSON解析: 正则提取成功, size=${it.size}")
                return it
            }
        }

        // 策略4：正则提取单个 JSON 对象（非贪婪匹配）
        val objRegex = Regex("""\{[^\{\}]*\}""")
        objRegex.find(trimmed)?.let { match ->
            val candidate = fixMalformedJson(match.value)
            try {
                val single = gson.fromJson(candidate, AIAnswer::class.java)
                if (single.question.isNotBlank()) {
                    AppLog.d("API", "JSON解析: 单对象正则提取成功")
                    return listOf(single)
                }
            } catch (_: Exception) {}
        }

        // 策略5：文本降级提取
        AppLog.d("API", "JSON解析: 全部失败, 降级文本提取")
        return listOf(parseAnswerFromText(content))
    }

    /**
     * 尝试将字符串解析为 AIAnswer 列表
     * 支持数组和单对象格式
     */
    private fun tryParseAsAnswers(json: String): List<AIAnswer>? {
        return try {
            // 先尝试数组
            val arrayType = com.google.gson.reflect.TypeToken.getParameterized(
                java.util.List::class.java, AIAnswer::class.java
            ).type
            val list: List<AIAnswer> = gson.fromJson(json, arrayType)
            list.takeIf { it.isNotEmpty() && it.first().question.isNotBlank() }
        } catch (_: Exception) {
            try {
                // 再尝试单对象
                val single = gson.fromJson(json, AIAnswer::class.java)
                listOf(single).takeIf { single.question.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 从 AI 回复文本中提取 JSON 负载。
     *
     * LLM 返回格式多样（裸 JSON、Markdown 代码块、JSON 混在文字中等）。
     * 按优先级处理：
     *   1. 正则匹配 ```json {...} ``` 或 ``` {...} ``` 代码块
     *   2. 括号配对算法：从第一个 { 或 [ 开始，逐字符跟踪字符串内转义
     *      和嵌套深度，找到配对的闭合括号
     *   3. 兜底：原文直接返回，由 Gson 尝试解析
     */
    fun extractJsonPayload(content: String): String {
        val s = content.trim()

        // 优先匹配 Markdown 代码块 ```json {...}```
        val fenceRegex = Regex("(?s)```\\s*([a-zA-Z0-9_-]+)?\\s*(\\{.*?\\}|\\[.*?\\])\\s*```")
        fenceRegex.find(s)?.let { m ->
            return sanitizeJson(m.groupValues[2].trim())
        }

        // 无代码块时：找到首个 '{' 或 '['，通过括号配对提取完整JSON
        val start = sequenceOf(s.indexOf('{'), s.indexOf('['))
            .filter { it >= 0 }
            .minOrNull() ?: return s

        val openChar = s[start]
        val closeChar = if (openChar == '{') '}' else ']'

        // 括号配对状态机：处理嵌套和字符串内的括号
        var depth = 0
        var inString = false
        var escape = false
        var end = -1

        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else {
                    if (c == '\\') escape = true
                    else if (c == '"') inString = false
                }
            } else {
                if (c == '"') inString = true
                else if (c == openChar) depth++
                else if (c == closeChar) {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end != -1) {
            return sanitizeJson(s.substring(start, end + 1).trim())
        }

        // 兜底：返回原文（可能已是纯JSON）
        return sanitizeJson(s)
    }

    /**
     * 清理 JSON 字符串中的非法字符
     * LLM 返回的 JSON 可能在字符串值中包含换行符、中文标点等，导致解析失败
     *
     * 重要：中文标点只在 JSON 字符串外部替换，避免破坏字符串值内的中文内容
     */
    private fun sanitizeJson(json: String): String {
        val result = StringBuilder()
        var inString = false
        var escape = false

        for (c in json) {
            when {
                escape -> {
                    result.append(c)
                    escape = false
                }
                c == '\\' && inString -> {
                    result.append(c)
                    escape = true
                }
                c == '"' -> {
                    result.append(c)
                    inString = !inString
                }
                inString && (c == '\n' || c == '\r') -> {
                    // 字符串内的换行符替换为 \\n
                    result.append("\\n")
                }
                inString && c == '\t' -> {
                    // 字符串内的制表符替换为 \\t
                    result.append("\\t")
                }
                inString -> {
                    // 字符串内的内容原样保留，不替换中文标点
                    result.append(c)
                }
                else -> {
                    // 字符串外的中文标点替换为 ASCII
                    when (c) {
                        '\u201C', '\u201D' -> result.append('"')  // 中文双引号
                        '\u2018', '\u2019' -> result.append('\'') // 中文单引号
                        '\uFF0C' -> result.append(',')             // 全角逗号
                        '\u3001' -> result.append(',')             // 顿号
                        '\uFF1A' -> result.append(':')             // 全角冒号
                        '\uFF1B' -> result.append(';')             // 全角分号
                        '\u3002' -> result.append('.')             // 句号
                        '\uFF08' -> result.append('(')             // 全角左括号
                        '\uFF09' -> result.append(')')             // 全角右括号
                        '\u300A' -> result.append('<')             // 左书名号
                        '\u300B' -> result.append('>')             // 右书名号
                        '\uFF01' -> result.append('!')             // 全角感叹号
                        else -> result.append(c)
                    }
                }
            }
        }
        return result.toString()
    }

    /**
     * 修复常见的 JSON 格式问题
     * - 缺少花括号
     * - 空值 (answer:)
     * - 尾部逗号
     * - 中文标点（仅替换字符串外的标点）
     */
    private fun fixMalformedJson(json: String): String {
        var s = json.trim()

        // 如果不是以 { 或 [ 开头，添加 {
        if (!s.startsWith("{") && !s.startsWith("[")) {
            s = "{$s"
        }
        // 如果不是以 } 或 ] 结尾，添加 }
        if (!s.endsWith("}") && !s.endsWith("]")) {
            // 移除尾部逗号后添加 }
            s = s.trimEnd().trimEnd(',') + "}"
        }

        // 仅替换字符串值外面的中文标点（不替换字符串内的内容）
        val result = StringBuilder()
        var inString = false
        var escape = false
        var i = 0

        while (i < s.length) {
            val c = s[i]

            when {
                escape -> {
                    result.append(c)
                    escape = false
                }
                c == '\\' && inString -> {
                    result.append(c)
                    escape = true
                }
                c == '"' -> {
                    result.append(c)
                    inString = !inString
                }
                inString -> {
                    // 字符串内的内容原样保留，不替换中文标点
                    result.append(c)
                }
                else -> {
                    // 字符串外的中文标点替换为 ASCII
                    when (c) {
                        '\u201C', '\u201D' -> result.append('"')  // 中文双引号
                        '\uFF0C' -> result.append(',')             // 全角逗号
                        '\u3001' -> result.append(',')             // 顿号
                        '\uFF1A' -> result.append(':')             // 全角冒号
                        '\uFF08' -> result.append('(')             // 全角左括号
                        '\uFF09' -> result.append(')')             // 全角右括号
                        else -> result.append(c)
                    }
                }
            }
            i++
        }
        s = result.toString()

        // 修复空值 answer: 或 answer: , 或 answer:"",
        s = s.replace("\"answer\":,", "\"answer\":\"\"," )
        s = s.replace("\"answer\": ,", "\"answer\":\"\"," )
        s = s.replace("\"answer\":  ,", "\"answer\":\"\"," )

        // 移除尾部逗号 (如 "options":[...] ,)
        var changed = true
        while (changed) {
            val newS = s.replace(",}", "}").replace(",]", "]")
            changed = newS != s
            s = newS
        }

        return s
    }

    /**
     * 从原始文本中解析答案（JSON 解析失败时的降级方案）
     * 不使用正则，改用字符串查找
     */
    private fun parseAnswerFromText(text: String): AIAnswer {
        val question = extractJsonValue(text, "question")
            ?: MyApplication.getString(R.string.error_parse_question_failed)
        val rawAnswer = extractJsonValue(text, "answer")
        val answer = rawAnswer?.takeIf { it.isNotBlank() }
            ?: inferAnswerFromOptions(text)
            ?: MyApplication.getString(R.string.error_parse_question_failed)
        val questionType = extractJsonValue(text, "questionType")
            ?: MyApplication.getString(R.string.question_type_essay)

        // 提取 options 数组
        val options = extractJsonArray(text, "options")

        return AIAnswer(
            question = question,
            questionType = questionType,
            answer = answer,
            options = options
        )
    }

    /**
     * 当 answer 为空时，尝试从选项中推断答案
     * 如果只有一个选项看起来像答案，就返回它
     */
    private fun inferAnswerFromOptions(text: String): String? {
        // 尝试找到 "answer": 后面的内容
        val answerIndex = text.indexOf("\"answer\"")
        if (answerIndex == -1) return null

        val colonIndex = text.indexOf(':', answerIndex)
        if (colonIndex == -1) return null

        // 检查 answer 后面是否直接是逗号或结束
        var i = colonIndex + 1
        while (i < text.length && text[i].isWhitespace()) i++

        // 如果是引号，提取引号内容
        if (i < text.length && text[i] == '"') {
            i++
            val start = i
            while (i < text.length && text[i] != '"') i++
            val value = text.substring(start, i)
            if (value.isNotBlank()) return value
        }

        return null
    }

    /**
     * 从 JSON 文本中提取指定 key 的字符串值
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val searchKey = "\"$key\""
        val keyIndex = json.indexOf(searchKey)
        if (keyIndex == -1) return null

        // 找到 key 后面的冒号
        val colonIndex = json.indexOf(':', keyIndex + searchKey.length)
        if (colonIndex == -1) return null

        // 跳过空白
        var i = colonIndex + 1
        while (i < json.length && json[i].isWhitespace()) i++

        if (i >= json.length) return null

        // 如果是引号开头，提取引号内的内容
        if (json[i] == '"') {
            i++ // 跳过开始引号
            val start = i
            while (i < json.length && json[i] != '"') {
                if (json[i] == '\\') i++ // 跳过转义字符
                i++
            }
            return json.substring(start, i)
        }

        // 如果不是引号，提取到逗号或结尾
        val start = i
        while (i < json.length && json[i] != ',' && json[i] != '}' && json[i] != ']') {
            i++
        }
        return json.substring(start, i).trim()
    }

    /**
     * 从 JSON 文本中提取指定 key 的数组值
     */
    private fun extractJsonArray(json: String, key: String): List<String>? {
        val searchKey = "\"$key\""
        val keyIndex = json.indexOf(searchKey)
        if (keyIndex == -1) return null

        // 找到 key 后面的 [
        val bracketIndex = json.indexOf('[', keyIndex + searchKey.length)
        if (bracketIndex == -1) return null

        // 找到匹配的 ]（括号配对，支持嵌套）
        var depth = 0
        var endIndex = -1
        var inString = false
        var escape = false
        for (i in bracketIndex until json.length) {
            val c = json[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '[') depth++
            else if (c == ']') { depth--; if (depth == 0) { endIndex = i; break } }
        }
        if (endIndex == -1) return null

        // 提取数组内容
        val arrayContent = json.substring(bracketIndex + 1, endIndex)

        // 解析数组元素（用引号分割）
        val items = mutableListOf<String>()
        var i = 0
        while (i < arrayContent.length) {
            // 跳过空白和逗号
            while (i < arrayContent.length && (arrayContent[i].isWhitespace() || arrayContent[i] == ',')) i++

            if (i >= arrayContent.length) break

            // 如果是引号开头，提取引号内的内容
            if (arrayContent[i] == '"') {
                i++ // 跳过开始引号
                val start = i
                while (i < arrayContent.length && arrayContent[i] != '"') {
                    if (arrayContent[i] == '\\') i++ // 跳过转义字符
                    i++
                }
                items.add(arrayContent.substring(start, i))
                i++ // 跳过结束引号
            } else {
                // 非引号值
                val start = i
                while (i < arrayContent.length && arrayContent[i] != ',' && arrayContent[i] != ']') i++
                val value = arrayContent.substring(start, i).trim()
                if (value.isNotEmpty()) {
                    items.add(value)
                }
            }
        }

        return items.takeIf { it.isNotEmpty() }
    }
}
