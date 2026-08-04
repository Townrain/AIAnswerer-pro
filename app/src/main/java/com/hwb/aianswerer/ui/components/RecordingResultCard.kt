package com.hwb.aianswerer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.DW

// ===== extracted from FloatingWindowContent.kt =====

// =============================================================================
// RecordingResultCard — compact summary + expandable detail (from sandbox)
// =============================================================================

/**
 * 从完整答案文本中提取简短摘要（第一行非空内容）
 */
private fun extractShortAnswer(fullText: String): String {
    val lines = fullText.lines().filter { it.isNotBlank() }
    // 找到答案段 (【答案】或 **答案** 之后的内容)
    for (i in lines.indices) {
        val trimmed = lines[i].trim()
        if ((trimmed.startsWith("**答案") || trimmed.startsWith("【答案】")) && i + 1 < lines.size) {
            val answerLine = lines[i + 1].trim()
            if (answerLine.length in 1..60 && !answerLine.startsWith("**") && !answerLine.startsWith("【")) {
                return answerLine
            }
        }
    }
    // Fallback: 尝试找不含标记的短行作为摘要
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("**答案") || trimmed.startsWith("【答案】")) continue
        if (trimmed.length in 1..60 && !trimmed.startsWith("**") && !trimmed.startsWith("【")) {
            return trimmed
        }
    }
    return lines.firstOrNull()?.take(60) ?: "..."
}

@Composable
internal fun RecordingResultCard(
    t: com.hwb.aianswerer.ui.theme.Th,
    answers: List<Pair<Int, String>>,
    onClose: () -> Unit,
    onCopyAnswer: (String) -> Unit,
    isProcessing: Boolean = false,
    processedCount: Int = 0,
    totalCount: Int = 0,
    // 折叠功能：D 窗提供收起回调时，header 显示收起按钮（D 窗 → C 窗）
    onCollapse: (() -> Unit)? = null
) {
    com.hwb.aianswerer.utils.AppLog.d("RRC", "RecordingResultCard: answers.size=${answers.size}")
    val cardR = FWDims.cardCornerRadius
    val cardShape = RoundedCornerShape(cardR)

    // Pagination state (always show expanded content, no collapse/expand toggle)
    val itemsPerPage = 1
    var curPage by remember { mutableStateOf(0) }
    LaunchedEffect(answers.size) { curPage = 0 }
    val totalPages = (answers.size + itemsPerPage - 1) / itemsPerPage
    val pageAnswers = answers.drop(curPage * itemsPerPage).take(itemsPerPage)

    Box(
        Modifier
            .shadow(12.dp, cardShape, spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(cardShape)
            .background(
                if (t.isLight) Brush.verticalGradient(listOf(Color(0xFFF8F4F0), Color(0xFFF0EAE4)), endY = Float.POSITIVE_INFINITY)
                else Brush.verticalGradient(listOf(Color(0xFF28202A), Color(0xFF201822)), endY = Float.POSITIVE_INFINITY)
            )
            .border(0.5.dp, t.ac.copy(alpha = if (t.isLight) 0.10f else 0.06f), cardShape)
    ) {
        Column {
            // Header: title + copy all + page nav + close
            Row(Modifier.fillMaxWidth().padding(horizontal = FWDims.cardPaddingH, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isProcessing) {
                        stringResource(R.string.recording_processing_header, processedCount, totalCount)
                    } else {
                        stringResource(R.string.recording_done_header, totalCount)
                    },
                    style = DW.BodyMedium.copy(fontWeight = FontWeight.SemiBold, color = t.ob),
                    modifier = Modifier.weight(1f)
                )
                Row {
                    if (onCollapse != null) {
                        // 折叠功能：收起回到紧凑 C 窗
                        Bouncy(onClick = onCollapse) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(t.ac.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center) {
                                Icon(LocalIcons.ExpandLess, "收起", tint = t.osv, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    CopyBtn(t, onCopy = {
                        onCopyAnswer(answers.joinToString("\n") { "${it.first}. ${extractShortAnswer(it.second)}" })
                    })
                    Spacer(Modifier.width(4.dp))
                    CloseBtn(t, onClose, isBusy = false)
                }
            }

            // Full expanded content: paginated questions with detail
            Column(Modifier.padding(horizontal = FWDims.cardPaddingH).padding(bottom = 8.dp)) {
                pageAnswers.forEach { (num, fullText) ->
                    val sections = remember(fullText) { parseSections(fullText) }
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (t.isLight) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.04f))
                        .border(0.5.dp, t.ac.copy(alpha = if (t.isLight) 0.08f else 0.04f), RoundedCornerShape(12.dp))
                        .padding(10.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.float_question_index, num), style = DW.LabelMedium.copy(color = t.osv, fontWeight = FontWeight.SemiBold))
                                CopyBtn(t, onCopy = { onCopyAnswer(fullText) })
                            }
                            Spacer(Modifier.height(6.dp))
                            sections.forEach { sec ->
                                if (sec.label.isNotBlank()) { Text(sec.label, style = DW.LabelSmall.copy(color = t.osv, fontSize = 11.sp)); Spacer(Modifier.height(2.dp)) }
                                when {
                                    sec.isAnswer -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF34C759).copy(alpha = 0.06f)).padding(8.dp)) {
                                        Text(sec.content, style = DW.BodyMedium.copy(fontWeight = FontWeight.SemiBold, color = t.ob))
                                    }
                                    sec.isExplanation -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Box(Modifier.width(3.dp).heightIn(min = 24.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF6C5CE7)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(sec.content, style = DW.LabelLarge.copy(color = t.osv), modifier = Modifier.weight(1f))
                                    }
                                    else -> Column {
                                        sec.content.split("\n").filter { it.isNotBlank() }.forEach { line ->
                                            val ok = line.contains("✓") || line.contains("√")
                                            Text(line.trim(), style = DW.LabelLarge.copy(
                                                fontWeight = if (ok) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (ok) t.ob else t.osv))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pagination controls
                if (totalPages > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (curPage > 0) {
                            Bouncy(onClick = { curPage-- }) {
                                Text("◀ ${stringResource(R.string.float_prev_page)}", style = DW.LabelMedium.copy(color = t.p), modifier = Modifier.padding(8.dp))
                            }
                        } else {
                            Text("◀ ${stringResource(R.string.float_prev_page)}", style = DW.LabelMedium.copy(color = t.ac.copy(alpha = 0.3f)), modifier = Modifier.padding(8.dp))
                        }
                        Text(" ${curPage + 1} / $totalPages ", style = DW.LabelSmall.copy(color = t.osv))
                        if (curPage < totalPages - 1) {
                            Bouncy(onClick = { curPage++ }) {
                                Text("${stringResource(R.string.float_next_page)} ▶", style = DW.LabelMedium.copy(color = t.p), modifier = Modifier.padding(8.dp))
                            }
                        } else {
                            Text("${stringResource(R.string.float_next_page)} ▶", style = DW.LabelMedium.copy(color = t.ac.copy(alpha = 0.3f)), modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}
