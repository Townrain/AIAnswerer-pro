package com.hwb.aianswerer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.DW
import kotlinx.coroutines.delay

// ===== extracted from FloatingWindowContent.kt =====



// =============================================================================
// Card (synced from sandbox Card2)
// =============================================================================

@Composable
internal fun Card(
    t: com.hwb.aianswerer.ui.theme.Th,
    answerText: String?,
    hasAnswer: Boolean,
    statusMessage: String?,
    status: FloatingStatus,
    onCopy: () -> Unit,
    onCloseAnswer: () -> Unit,
    onCloseStatus: () -> Unit,
    showExpandBtn: Boolean = false,
    onExpand: () -> Unit = {},
    // 收起态（C 窗）答案摘要的最大高度，超出可滚动；null 表示不限制（D 窗/单卡）
    bodyMaxHeight: androidx.compose.ui.unit.Dp? = null,
    // 标题文本覆盖：默认"答案"；收起态传"答案:摘要"单行提示
    titleText: String? = null
) {
    val shape = RoundedCornerShape(FWDims.cardCornerRadius)
    val isBusy = status in listOf(FloatingStatus.Capturing, FloatingStatus.Recognizing,
                                  FloatingStatus.Searching, FloatingStatus.GettingAnswer)

    Box(
        Modifier
            .shadow(12.dp, shape, spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(
                if (t.isLight) Brush.verticalGradient(listOf(Color(0xFFF8F4F0), Color(0xFFF0EAE4)), endY = Float.POSITIVE_INFINITY)
                else Brush.verticalGradient(listOf(Color(0xFF28202A), Color(0xFF201822)), endY = Float.POSITIVE_INFINITY)
            )
            .border(0.5.dp, t.ac.copy(alpha = if (t.isLight) 0.10f else 0.06f), shape)
    ) {
        Column {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = FWDims.cardPaddingH, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!hasAnswer && statusMessage != null) {
                        StatusDot(status)
                        Spacer(Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = statusMessage,
                            transitionSpec = {
                                (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.8f)) togetherWith
                                fadeOut(tween(150))
                            },
                            label = "status"
                        ) { msg ->
                            Text(msg, style = DW.LabelMedium.copy(color = t.osv))
                        }
                    } else if (hasAnswer) {
                        Text(titleText ?: stringResource(R.string.float_answer_title), style = DW.BodyMedium.copy(fontWeight = FontWeight.SemiBold, color = t.ob))
                    }
                }
                Row {
                    if (hasAnswer && showExpandBtn) {
                        // 折叠功能：展开查看完整答案（C 窗 → D 窗）
                        Bouncy(onClick = onExpand) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(t.ac.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center) {
                                Icon(LocalIcons.ExpandMore, "展开", tint = t.osv, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (hasAnswer) CopyBtn(t, onCopy)
                    CloseBtn(t, if (hasAnswer) onCloseAnswer else onCloseStatus, isBusy = isBusy)
                }
            }
            // Body
            AnimatedVisibility(visible = hasAnswer, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                answerText?.let {
                    if (bodyMaxHeight != null) {
                        Box(Modifier.heightIn(max = bodyMaxHeight)) { Body(t, it) }
                    } else {
                        Body(t, it)
                    }
                }
            }
        }
    }
}

// =============================================================================
// CopyBtn (synced from sandbox CopyBtn2)
// =============================================================================

@Composable
internal fun CopyBtn(t: com.hwb.aianswerer.ui.theme.Th, onCopy: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    val checkScale by animateFloatAsState(if (done) 1.15f else 1f, spring(0.6f, 350f), label = "cs")
    val bgColor by animateColorAsState(if (done) Color(0xFF34C759).copy(alpha = 0.12f) else t.ac.copy(alpha = 0.06f), tween(200), label = "cb")
    Bouncy(onClick = { onCopy(); done = true }) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(bgColor), contentAlignment = Alignment.Center) {
            Icon(
                if (done) LocalIcons.CheckCircle else LocalIcons.ContentCopy, "复制",
                tint = if (done) Color(0xFF34C759) else t.osv,
                modifier = Modifier.size(14.dp).graphicsLayer {
                    if (done) { scaleX = checkScale; scaleY = checkScale }
                }
            )
        }
    }
    if (done) LaunchedEffect(Unit) { delay(FWAnim.copyResetMs); done = false }
}

// =============================================================================
// CloseBtn (synced from sandbox CloseBtn2)
// =============================================================================

@Composable
internal fun CloseBtn(t: com.hwb.aianswerer.ui.theme.Th, onClose: () -> Unit, isBusy: Boolean) {
    Bouncy(onClick = onClose) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            .background(if (isBusy) Color(0xFFFF3B30).copy(alpha = 0.1f) else t.ac.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center) {
            Icon(
                if (isBusy) LocalIcons.Stop else LocalIcons.Close, "关闭",
                tint = if (isBusy) Color(0xFFFF3B30) else t.osv,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// =============================================================================
// Body (synced from sandbox Body2 — section parsing with color bars)
// =============================================================================

@Composable
private fun Body(t: com.hwb.aianswerer.ui.theme.Th, text: String) {
    val sections = remember(text) { parseSections(text) }
    val bodyShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = FWDims.cardCornerRadius, bottomEnd = FWDims.cardCornerRadius)
    val bg = if (t.isLight) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.04f)
    val border = if (t.isLight) t.ac.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f)

    Box(
        Modifier.fillMaxWidth().clip(bodyShape).drawBehind {
            val r = FWDims.cardCornerRadius.toPx()
            val p = Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - r)
                quadraticBezierTo(size.width, size.height, size.width - r, size.height)
                lineTo(r, size.height)
                quadraticBezierTo(0f, size.height, 0f, size.height - r)
                close()
            }
            drawPath(p, bg)
            drawPath(p, border, style = Stroke(0.5.dp.toPx()))
        }.padding(horizontal = FWDims.cardPaddingH, vertical = FWDims.cardPaddingV)
    ) {
        Column(Modifier.heightIn(max = FWDims.cardMaxHeight).verticalScroll(rememberScrollState())) {
            sections.forEach { sec ->
                Spacer(Modifier.height(if (sec.isAnswer) FWDims.cardSectionSpacing else FWDims.cardItemSpacing))
                if (sec.label.isNotBlank()) {
                    Text(sec.label, style = DW.LabelSmall.copy(color = t.osv, fontSize = 11.sp))
                    Spacer(Modifier.height(3.dp))
                }
                when {
                    sec.isAnswer -> {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF34C759).copy(alpha = 0.04f))
                            .padding(start = 0.dp)) {
                            Box(Modifier.width(4.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF34C759)))
                            Spacer(Modifier.width(10.dp))
                            Text(sec.content, style = DW.BodyMedium.copy(fontWeight = FontWeight.SemiBold, color = t.ob), modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                        }
                    }
                    sec.isExplanation -> {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (t.isLight) Color(0xFF6C5CE7).copy(alpha = 0.05f) else Color(0xFF6C5CE7).copy(alpha = 0.08f))
                            .padding(10.dp)) {
                            Box(Modifier.width(3.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF6C5CE7)))
                            Spacer(Modifier.width(8.dp))
                            Text(sec.content, style = DW.LabelLarge.copy(color = t.osv), modifier = Modifier.weight(1f))
                        }
                    }
                    else -> Column {
                        sec.content.split("\n").filter { it.isNotBlank() }.forEach { line ->
                            val ok = line.contains("✓") || line.contains("√")
                            Box(Modifier.fillMaxWidth().padding(vertical = 1.5.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (ok) Color(0xFF34C759).copy(alpha = 0.07f) else if (t.isLight) Color(0xFFF0EDF5) else Color.White.copy(alpha = 0.03f))
                                .then(if (ok) Modifier.border(0.5.dp, Color(0xFF34C759).copy(alpha = 0.12f), RoundedCornerShape(10.dp)) else Modifier)
                                .padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(line.trim(), style = DW.LabelLarge.copy(fontWeight = if (ok) FontWeight.SemiBold else FontWeight.Normal, color = if (ok) t.ob else t.osv))
                            }
                        }
                    }
                }
            }
        }
    }
}
