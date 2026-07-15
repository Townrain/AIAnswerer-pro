package com.hwb.aianswerer.ui.components

// =============================================================================
// Section parser — splits raw answer text into labeled sections
// =============================================================================

internal data class Section(
    val label: String,
    val content: String,
    val isAnswer: Boolean,
    val isExplanation: Boolean
)

internal fun parseSections(raw: String): List<Section> {
    val pattern = Regex("""(?:【([^】]+)】|\*\*([^*]+)\*\*)""")
    val matches = pattern.findAll(raw).toList()
    if (matches.isEmpty()) return listOf(Section("", raw.trim(), false, false))
    return matches.mapIndexed { i, m ->
        val start = m.range.last + 1
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else raw.length
        val lbl = m.groupValues[1].ifBlank { m.groupValues[2] }
        val label = if (m.groupValues[1].isNotBlank()) "【$lbl】" else "**$lbl**"
        Section(
            label, raw.substring(start, end).trim(),
            lbl.contains("答案") || lbl.contains("answer", ignoreCase = true),
            lbl.contains("解析") || lbl.contains("analysis", ignoreCase = true)
        )
    }
}
