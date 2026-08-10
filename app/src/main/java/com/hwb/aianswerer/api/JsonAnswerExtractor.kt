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
        // 空内容（如伪工具文本被剥离后）直接返回空列表，由上层按失败处理，
        // 避免降级文本提取产出垃圾答案条目
        if (trimmed.isEmpty()) {
            AppLog.d("API", "JSON解析: 空内容, 返回空列表")
            return emptyList()
        }

        // 统一出口：剥离 answer 值中的题号前缀（模型按录制 prompt 标注题号但常放错位置，如 answer:"第2题：C"）
        fun List<AIAnswer>.sanitized(): List<AIAnswer> = map { ai ->
            val clean = stripQuestionNumberPrefix(ai.answer)
            // 不用 copy()：gson 缺失字段时 questionType 可能为 null，copy 会 NPE
            if (clean == ai.answer) ai
            else AIAnswer(ai.question, ai.questionType, clean, ai.options)
        }

        // 策略1：直接解析原文（AI 通常返回干净 JSON）
        tryParseAsAnswers(trimmed)?.let {
            AppLog.d("API", "JSON解析: 直接解析成功, size=${it.size}")
            return it.sanitized()
        }

        // 策略2：提取 JSON 负载 + 修复
        val extracted = extractJsonPayload(trimmed)
        val fixed = fixMalformedJson(extracted)
        tryParseAsAnswers(fixed)?.let {
            AppLog.d("API", "JSON解析: 提取+修复成功, size=${it.size}")
            return it.sanitized()
        }

        // 策略3：正则提取 JSON 数组
        val arrayRegex = Regex("""\[[\s\S]*]""")
        arrayRegex.find(trimmed)?.let { match ->
            val candidate = fixMalformedJson(match.value)
            tryParseAsAnswers(candidate)?.let {
                AppLog.d("API", "JSON解析: 正则提取成功, size=${it.size}")
                return it.sanitized()
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
                    return listOf(single).sanitized()
                }
            } catch (_: Exception) {}
        }

        // 策略5：文本降级提取 — 工具调用伪文本直接放弃（模型在无 tools 请求中输出 <tool_calls> 伪代码时，
        //     降级提取只会产出垃圾答案条目，返回空列表由上层按失败处理）
        if (containsToolCallSyntax(trimmed)) {
            AppLog.d("API", "JSON解析: 检测到工具调用伪文本, 放弃降级提取")
            return emptyList()
        }
        AppLog.d("API", "JSON解析: 全部失败, 降级文本提取")
        return listOf(parseAnswerFromText(content)).sanitized()
    }

    /**
     * 剥离 answer 值开头的题号前缀（如 "第2题：C" → "C"、"第5题：B" → "B"）。
     * 模型按录制 prompt 要求标注题号，但经常把题号写进 answer 字段而非正文前缀，
     * 导致结果卡/复制文本出现 "第N题：第M题：C" 的重复题号。
     */
    private fun stripQuestionNumberPrefix(answer: String): String =
        answer.replace(Regex("""^\s*第\s*\d+\s*题\s*[：:]\s*"""), "").trim()

    /**
     * 判断文本是否包含工具调用伪语法（<tool_calls>/<invoke> 等）。
     */
    private fun containsToolCallSyntax(text: String): Boolean =
        text.contains("<tool_calls") || text.contains("</tool_calls>") ||
            text.contains("<invoke") || text.contains("</invoke>") ||
            text.contains("tool_call_id") || text.contains("\"tool_calls\"")

    /**
     * 尝试将字符串解析为 AIAnswer 列表
     * 支持数组和单对象格式
     */
    private fun tryParseAsAnswers(json: String): List<AIAnswer>? {
        return try {
            val arrayType = com.google.gson.reflect.TypeToken.getParameterized(
                java.util.List::class.java, AIAnswer::class.java
            ).type
            val list: List<AIAnswer> = gson.fromJson(json, arrayType)
            list.takeIf { it.isNotEmpty() && it.first().question.isNotBlank() }
        } catch (_: Exception) {
            try {
                val single = gson.fromJson(json, AIAnswer::class.java)
                listOf(single).takeIf { single.question.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 从 AI 回复文本中提取 JSON 负载。
     */
    fun extractJsonPayload(content: String): String {
        val s = content.trim()

        val fenceRegex = Regex("(?s)```\\s*([a-zA-Z0-9_-]+)?\\s*(\\{.*?\\}|\\[.*?\\])\\s*```")
        fenceRegex.find(s)?.let { m ->
            return sanitizeJson(m.groupValues[2].trim())
        }

        val start = sequenceOf(s.indexOf('{'), s.indexOf('['))
            .filter { it >= 0 }
            .minOrNull() ?: return s

        val openChar = s[start]
        val closeChar = if (openChar == '{') '}' else ']'

        var depth = 0
        var inString = false
        var escape = false
        var end = -1

        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
            } else {
                if (c == '"') inString = true
                else if (c == openChar) depth++
                else if (c == closeChar) {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        if (end != -1) {
            return sanitizeJson(s.substring(start, end + 1).trim())
        }
        return sanitizeJson(s)
    }

    private fun sanitizeJson(json: String): String {
        val result = StringBuilder()
        var inString = false
        var escape = false
        for (c in json) {
            when {
                escape -> { result.append(c); escape = false }
                c == '\\' && inString -> { result.append(c); escape = true }
                c == '"' -> { result.append(c); inString = !inString }
                inString && (c == '\n' || c == '\r') -> result.append("\\n")
                inString && c == '\t' -> result.append("\\t")
                inString -> result.append(c)
                else -> {
                    when (c) {
                        '\u201C', '\u201D' -> result.append('"')
                        '\u2018', '\u2019' -> result.append('\'')
                        '\uFF0C' -> result.append(',')
                        '\u3001' -> result.append(',')
                        '\uFF1A' -> result.append(':')
                        '\uFF1B' -> result.append(';')
                        '\u3002' -> result.append('.')
                        '\uFF08' -> result.append('(')
                        '\uFF09' -> result.append(')')
                        '\u300A' -> result.append('<')
                        '\u300B' -> result.append('>')
                        '\uFF01' -> result.append('!')
                        else -> result.append(c)
                    }
                }
            }
        }
        return result.toString()
    }

    /**
     * 将 JavaScript 对象字面量格式转换为标准 JSON：
     * - 键名加引号: {key: → {"key":
     * - 字符串值加引号: :中文 → :"中文", [A. xx → ["A. xx"
     */
    private fun quoteJsonKeys(json: String): String {
        var s = json

        // 步骤1: 引用未加引号的键名 ({或, 后跟 word:)
        s = Regex("""([\{,])\s*([a-zA-Z_]\w*)\s*:""").replace(s) { mr ->
            "${mr.groupValues[1]}\"${mr.groupValues[2]}\":"
        }

        // 步骤2: 引用未加引号的字符串值（状态机）
        val sb = StringBuilder()
        var inStr = false
        var esc = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                esc -> { sb.append(c); esc = false; i++; continue }
                c == '\\' && inStr -> { sb.append(c); esc = true; i++; continue }
                c == '"' -> { sb.append(c); inStr = !inStr; i++; continue }
                inStr -> { sb.append(c); i++; continue }
                // :  [  , 后面可能跟未引号字符串值
                c == ':' || c == '[' || c == ',' -> {
                    sb.append(c); i++
                    while (i < s.length && s[i].isWhitespace()) { sb.append(s[i]); i++ }
                    if (i >= s.length) continue
                    val nc = s[i]
                    // 已引号 / { [ ] } / 数字 / - / true / false / null → 跳过
                    if (nc == '"' || nc == '{' || nc == '[' || nc == ']' || nc == '}' ||
                        nc.isDigit() || nc == '-') continue
                    if (nc == 't' && s.regionMatches(i, "true", 0, 4, true)) continue
                    if (nc == 'f' && s.regionMatches(i, "false", 0, 5, true)) continue
                    if (nc == 'n' && s.regionMatches(i, "null", 0, 4, true)) continue
                    // 未引号字符串值：提取到 , } ]，内嵌 ASCII 引号需转义（如“3A大作”）
                    val valStart = i
                    while (i < s.length && s[i] != ',' && s[i] != '}' && s[i] != ']') i++
                    val raw = s.substring(valStart, i).trim()
                    if (raw.isNotEmpty()) {
                        val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
                        sb.append('"').append(escaped).append('"')
                    }
                    continue
                }
                else -> { sb.append(c); i++; continue }
            }
        }
        return sb.toString()
    }

    /**
     * 修复常见的 JSON 格式问题
     */
    private fun fixMalformedJson(json: String): String {
        var s = json.trim()

        // 步骤0：JavaScript 对象格式 → 标准 JSON
        s = quoteJsonKeys(s)

        if (!s.startsWith("{") && !s.startsWith("[")) s = "{$s"
        if (!s.endsWith("}") && !s.endsWith("]")) s = s.trimEnd().trimEnd(',') + "}"

        // 替换字符串外的中文标点
        val result = StringBuilder()
        var inString = false
        var escape = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                escape -> { result.append(c); escape = false }
                c == '\\' && inString -> { result.append(c); escape = true }
                c == '"' -> { result.append(c); inString = !inString }
                inString -> result.append(c)
                else -> {
                    when (c) {
                        '\u201C', '\u201D' -> result.append('"')
                        '\uFF0C' -> result.append(',')
                        '\u3001' -> result.append(',')
                        '\uFF1A' -> result.append(':')
                        '\uFF08' -> result.append('(')
                        '\uFF09' -> result.append(')')
                        else -> result.append(c)
                    }
                }
            }
            i++
        }
        s = result.toString()

        // 修复空值
        s = s.replace("\"answer\":,", "\"answer\":\"\",")
        s = s.replace("\"answer\": ,", "\"answer\":\"\",")
        s = s.replace("\"answer\":  ,", "\"answer\":\"\",")

        // 移除尾部逗号
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
     */
    private fun parseAnswerFromText(text: String): AIAnswer {
        val question = extractJsonValue(text, "question")
            ?: MyApplication.getString(R.string.error_parse_question_failed)
        val rawAnswer = extractJsonValue(text, "answer")
        val answer = rawAnswer?.takeIf { it.isNotBlank() }
            ?: inferAnswerFromMarkdown(text)
            ?: inferAnswerFromOptions(text)
            ?: MyApplication.getString(R.string.error_parse_question_failed)
        val questionType = extractJsonValue(text, "questionType")
            ?: MyApplication.getString(R.string.question_type_essay)
        val options = extractJsonArray(text, "options")
        return AIAnswer(question, questionType, answer, options)
    }

    /**
     * 提取中文 Markdown 风格的答案标注（模型输出非 JSON 文本时的常见格式）：
     * **答案：D 原神**、答案：B、answer: C 等。
     */
    private fun inferAnswerFromMarkdown(text: String): String? {
        val regex = Regex(
            """(?:答案|answer)\s*[:：]\s*(.+?)(?:\*\*|\n|\r|。|；|;|，|,|$)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(text) ?: return null
        val raw = match.groupValues[1].trim().trimEnd('*', '。', '，', ',', '；', ';', ' ').trim()
        return raw.takeIf { it.isNotBlank() }
    }

    private fun inferAnswerFromOptions(text: String): String? {
        val colonIndex = findBareKeyColon(text, "answer") ?: return null
        var i = colonIndex + 1
        while (i < text.length && text[i].isWhitespace()) i++
        if (i < text.length && text[i] == '\"') {
            i++
            val start = i
            while (i < text.length && text[i] != '\"') i++
            val value = text.substring(start, i)
            if (value.isNotBlank()) return value
        }
        return null
    }

    /**
     * 查找键后的冒号位置：兼容带引号键（"key"）与无引号键（key:）两种 JS 字面量格式。
     */
    private fun findBareKeyColon(json: String, key: String): Int? {
        val searchKey = "\"$key\""
        val keyIndex = json.indexOf(searchKey)
        if (keyIndex != -1) {
            val colonIndex = json.indexOf(':', keyIndex + searchKey.length)
            if (colonIndex != -1) return colonIndex
        }
        // 无引号键：匹配 {key: 或 ,key: 或 行首 key:，后面紧跟冒号
        val bareRegex = Regex("(?:[\\{,}]|^)\\s*$key\\s*:")
        val match = bareRegex.find(json) ?: return null
        val colonIndex = json.indexOf(':', match.range.last)
        return if (colonIndex != -1) colonIndex else null
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val colonIndex = findBareKeyColon(json, key) ?: return null
        var i = colonIndex + 1
        if (i >= json.length) return null
        if (json[i] == '"') {
            i++
            val start = i
            while (i < json.length && json[i] != '"') {
                if (json[i] == '\\') i++
                i++
            }
            return json.substring(start, i)
        }
        val start = i
        while (i < json.length && json[i] != ',' && json[i] != '}' && json[i] != ']') i++
        return json.substring(start, i).trim()
    }

    private fun extractJsonArray(json: String, key: String): List<String>? {
        val colonIndex = findBareKeyColon(json, key) ?: return null
        val bracketIndex = json.indexOf('[', colonIndex + 1)
        if (bracketIndex == -1) return null
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
        val arrayContent = json.substring(bracketIndex + 1, endIndex)
        val items = mutableListOf<String>()
        var i = 0
        while (i < arrayContent.length) {
            while (i < arrayContent.length && (arrayContent[i].isWhitespace() || arrayContent[i] == ',')) i++
            if (i >= arrayContent.length) break
            if (arrayContent[i] == '"') {
                i++
                val start = i
                while (i < arrayContent.length && arrayContent[i] != '"') {
                    if (arrayContent[i] == '\\') i++
                    i++
                }
                items.add(arrayContent.substring(start, i))
                i++
            } else {
                val start = i
                while (i < arrayContent.length && arrayContent[i] != ',' && arrayContent[i] != ']') i++
                val value = arrayContent.substring(start, i).trim()
                if (value.isNotEmpty()) items.add(value)
            }
        }
        return items.takeIf { it.isNotEmpty() }
    }
}
