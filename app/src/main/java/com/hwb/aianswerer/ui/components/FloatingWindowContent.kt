package com.hwb.aianswerer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

enum class FloatingStatus { Idle, Capturing, Recognizing, Searching, GettingAnswer, Success, Error }

private val MinCardWidth = 280.dp
private val MaxCardWidth = 360.dp
private val MinCardHeight = 250.dp
private val MaxCardHeight = 500.dp
private val ProgressStrokeWidth = 2.5.dp

// 长按触发时间（毫秒）
private const val LONG_PRESS_DURATION_MS = 2000L

// 小按钮大小
private val QuickButtonSize = 40.dp

// Apple-style spring specs — inline with proper types where used

@Composable
fun FloatingWindowContent(
    answerText: String?, showAnswer: Boolean, statusMessage: String?,
    buttonSize: Int = 56, buttonAlpha: Float = 1.0f, cardAlpha: Float = 1.0f,
    isLeftSide: Boolean = true, floatingStatus: FloatingStatus = FloatingStatus.Idle,
    onCaptureClick: () -> Unit, onCloseAnswer: () -> Unit, onCloseStatus: () -> Unit,
    onCopyAnswer: (() -> Unit)? = null, onMove: (Float, Float) -> Unit, onDragEnd: () -> Unit = {},
    visionEnabled: Boolean = false,
    searchEnabled: Boolean = false,
    reasoningEnabled: Boolean = false,
    onVisionToggle: (() -> Unit)? = null,
    onSearchToggle: (() -> Unit)? = null,
    onReasoningToggle: (() -> Unit)? = null
) {
    val viewConfig = androidx.compose.ui.platform.LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop

    val currentOnCaptureClick by rememberUpdatedState(onCaptureClick)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val isDark = LocalIsDarkMode.current

    val cardWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.82f).dp
        .coerceIn(MinCardWidth, MaxCardWidth)

    val hasContent = statusMessage != null || (showAnswer && answerText != null)

    // 小按钮显示状态
    var showQuickButtons by remember { mutableStateOf(false) }

    // 有内容时自动收起快捷按钮
    LaunchedEffect(hasContent) {
        if (hasContent) showQuickButtons = false
    }

    // 使用Box作为根容器，fillMaxWidth让宽度等于窗口宽度
    Box(modifier = Modifier.fillMaxWidth()) {
        // 主按钮 + 卡片的垂直布局
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isLeftSide) Alignment.Start else Alignment.End
        ) {
            // 主按钮行 - 使用固定大小的Box，避免小按钮撑大布局
            Box(modifier = Modifier.size(buttonSize.dp)) {
                // 主按钮（始终在Box内）
                FloatingButton(
                    buttonSize = buttonSize,
                    buttonAlpha = buttonAlpha,
                    floatingStatus = floatingStatus,
                    onCaptureClick = {
                        // 如果小按钮展开，只收起小按钮，不触发截图
                        if (showQuickButtons) {
                            showQuickButtons = false
                        } else {
                            currentOnCaptureClick()
                        }
                    },
                    onMove = { dx, dy ->
                        if (showQuickButtons) showQuickButtons = false
                        currentOnMove(dx, dy)
                    },
                    onDragEnd = currentOnDragEnd,
                    touchSlop = touchSlop,
                    onLongPress = { showQuickButtons = !showQuickButtons }
                )
            }

            // 卡片内容
            if (hasContent) {
                Spacer(Modifier.height(Spacing.sm))
                Box(modifier = Modifier.width(cardWidth)) {
                    ContentCard(
                        answerText = answerText,
                        showAnswer = showAnswer,
                        statusMessage = statusMessage,
                        floatingStatus = floatingStatus,
                        isDark = isDark,
                        cardAlpha = cardAlpha,
                        onCloseAnswer = onCloseAnswer,
                        onCloseStatus = onCloseStatus,
                        onCopyAnswer = onCopyAnswer
                    )
                }
            }
        }

        // 小按钮展开层
        if (showQuickButtons) {
            Box(
                modifier = Modifier
                    .align(if (isLeftSide) Alignment.TopStart else Alignment.TopEnd)
                    .offset(
                        x = if (isLeftSide) {
                            (buttonSize + 8).dp  // 左侧：主按钮右边
                        } else {
                            -(buttonSize + 8).dp  // 右侧：主按钮左边
                        },
                        y = ((buttonSize - QuickButtonSize.value) / 2).dp  // 垂直居中
                    )
            ) {
                QuickButtonsRow(
                    visionEnabled = visionEnabled,
                    searchEnabled = searchEnabled,
                    reasoningEnabled = reasoningEnabled,
                    onVisionToggle = { onVisionToggle?.invoke() },
                    onSearchToggle = { onSearchToggle?.invoke() },
                    onReasoningToggle = { onReasoningToggle?.invoke() }
                )
            }
        }
    }
}

// ── 快捷按钮行 ──

@Composable
private fun QuickButtonsRow(
    visionEnabled: Boolean,
    searchEnabled: Boolean,
    reasoningEnabled: Boolean,
    onVisionToggle: (() -> Unit)?,
    onSearchToggle: (() -> Unit)?,
    onReasoningToggle: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickToggleButton(
            icon = LocalIcons.Vision,
            enabled = visionEnabled,
            contentDescription = stringResource(R.string.quick_toggle_vlm),
            onClick = { onVisionToggle?.invoke() }  // 不再自动收起
        )
        QuickToggleButton(
            icon = LocalIcons.Globe,
            enabled = searchEnabled,
            contentDescription = stringResource(R.string.quick_toggle_search),
            onClick = { onSearchToggle?.invoke() }  // 不再自动收起
        )
        QuickToggleButton(
            icon = LocalIcons.Lightbulb,
            enabled = reasoningEnabled,
            contentDescription = stringResource(R.string.quick_toggle_reasoning),
            onClick = { onReasoningToggle?.invoke() }  // 不再自动收起
        )
    }
}

// ── 独立悬浮按钮 ──

@Composable
private fun FloatingButton(
    buttonSize: Int,
    buttonAlpha: Float,
    floatingStatus: FloatingStatus,
    onCaptureClick: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    touchSlop: Float,
    onLongPress: () -> Unit = {}
) {
    var pressed by remember { mutableStateOf(false) }
    var longPressProgress by remember { mutableStateOf(0f) }
    val fabScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = 500f),
        label = "fab_scale"
    )

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }

    val shape = RoundedCornerShape(CardRadius)

    Box(
        modifier = Modifier
            .size(buttonSize.dp)
            .graphicsLayer {
                this.alpha = buttonAlpha
                scaleX = fabScale
                scaleY = fabScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var totalDx = 0f; var totalDy = 0f; var isDragging = false
                        var longPressTriggered = false
                        val startTime = System.currentTimeMillis()
                        longPressProgress = 0f

                        while (true) {
                            val event = withTimeoutOrNull(16L) {
                                awaitPointerEvent()
                            }
                            
                            if (event != null) {
                                val change = event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    change?.consume()
                                    break
                                }
                                val dx = change.positionChange().x; val dy = change.positionChange().y
                                if (dx != 0f || dy != 0f) {
                                    totalDx += dx; totalDy += dy
                                    if (!isDragging && totalDx * totalDx + totalDy * totalDy > touchSlop * touchSlop) {
                                        isDragging = true
                                        longPressProgress = 0f
                                    }
                                    if (isDragging) {
                                        onMove(dx, dy)
                                        change.consume()
                                    }
                                }
                            }

                            val elapsed = System.currentTimeMillis() - startTime
                            if (!isDragging) {
                                longPressProgress = (elapsed.toFloat() / LONG_PRESS_DURATION_MS).coerceIn(0f, 1f)
                            }
                            
                            if (!isDragging && !longPressTriggered && elapsed >= LONG_PRESS_DURATION_MS) {
                                longPressTriggered = true
                                longPressProgress = 0f
                                onLongPress()
                            }
                        }

                        longPressProgress = 0f

                        if (isDragging) {
                            onDragEnd()
                        } else if (!longPressTriggered) {
                            pressed = true
                            onCaptureClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        FloatingBtnBackground(floatingStatus = floatingStatus, shape = shape)
        
        // 长按进度条
        if (longPressProgress > 0f) {
            val progressColor = PremiumPrimary.copy(alpha = 0.6f)
            Box(
                modifier = Modifier
                    .size(buttonSize.dp)
                    .drawBehind {
                        val strokeWidth = 3.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = radius,
                            center = center,
                            style = Stroke(strokeWidth)
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * longPressProgress,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }
            )
        }
        
        Icon(
            imageVector = LocalIcons.Search,
            contentDescription = stringResource(R.string.cd_capture_button),
            modifier = Modifier.size(Spacing.xxl),
            tint = PremiumCardLight
        )
    }
}

// ── 快捷切换按钮 ──

@Composable
private fun QuickToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 400f),
        label = "quick_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(QuickButtonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                color = if (enabled) {
                    // 启用状态：使用主按钮同款深灰色
                    DarkAccent
                } else {
                    // 未启用状态：半透明
                    if (isDark) GlassDark else GlassWhiteStrong
                },
                shape = CircleShape
            )
            .then(
                if (enabled) {
                    // 启用状态：白色边框
                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                } else {
                    // 未启用状态
                    Modifier.border(
                        0.5.dp,
                        if (isDark) GlassDarkBorder else GlassWhiteBorder,
                        CircleShape
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(Spacing.lg),
            tint = if (enabled) Color.White  // 启用时白色图标
                   else if (isDark) TextDarkSecondary else TextSecondary
        )
    }
}

// ── 内容卡片 ──

@Composable
private fun ContentCard(
    answerText: String?,
    showAnswer: Boolean,
    statusMessage: String?,
    floatingStatus: FloatingStatus,
    isDark: Boolean,
    cardAlpha: Float,
    onCloseAnswer: () -> Unit,
    onCloseStatus: () -> Unit,
    onCopyAnswer: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = cardAlpha }
            .drawBehind {
                val corner = CornerRadius(CardRadius.toPx())
                drawGlassShadow(this, corner, Spacing.lg.toPx(), Color.Black.copy(alpha = ShadowFloatingDarkAlpha))
            }
    ) {
        Column {
            // ── Header ──
            val headerShape = RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius, bottomStart = 0.dp, bottomEnd = 0.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isDark) Modifier.darkAccentGradient(headerShape)
                        else Modifier
                            .background(PremiumSurfaceVariant, headerShape)
                            .border(0.5.dp, InputBorder, headerShape)
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：标题 + 状态消息
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.floating_answer_title),
                            color = if (isDark) TextDarkPrimary else TextDark,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // 状态消息显示在标题右边
                        if (statusMessage != null && !(showAnswer && answerText != null)) {
                            Spacer(Modifier.width(Spacing.sm))
                            StatusDot(status = floatingStatus)
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) TextDarkSecondary else TextTertiary
                            )
                        }
                    }

                    // 右侧：操作按钮
                    Row {
                        if (showAnswer && answerText != null && onCopyAnswer != null) {
                            CopyButton(onCopy = onCopyAnswer, isDark = isDark)
                        }
                        CloseButton(onClose = if (showAnswer && answerText != null) onCloseAnswer else onCloseStatus, isDark = isDark)
                    }
                }
            }

            // ── 答案内容 ──
            AnimatedVisibility(
                visible = showAnswer && answerText != null,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                answerText?.let {
                    AnswerBody(text = it, isDark = isDark)
                }
            }
        }
    }
}

// ── 状态圆点 ──

@Composable
private fun StatusDot(status: FloatingStatus) {
    val dotColor = when (status) {
        FloatingStatus.Success -> SuccessGreen
        FloatingStatus.Error -> ErrorRed
        else -> PremiumPrimary
    }
    Box(
        modifier = Modifier
            .size(Spacing.xs)
            .clip(CircleShape)
            .background(dotColor)
    )
}

// ── 复制按钮 ──

@Composable
private fun CopyButton(onCopy: () -> Unit, isDark: Boolean) {
    var showCopied by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            onCopy()
            showCopied = true
        },
        modifier = Modifier.size(TouchMin)
    ) {
        Icon(
            imageVector = if (showCopied) LocalIcons.CheckCircle else LocalIcons.ContentCopy,
            contentDescription = if (showCopied) stringResource(R.string.cd_copied) else stringResource(R.string.cd_copy),
            modifier = Modifier.size(Spacing.lg),
            tint = if (showCopied) SuccessGreen else (if (isDark) TextDarkSecondary else TextDark)
        )
    }
    if (showCopied) {
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); showCopied = false }
    }
}

// ── 关闭按钮 ──

@Composable
private fun CloseButton(onClose: () -> Unit, isDark: Boolean) {
    IconButton(onClick = onClose, modifier = Modifier.size(TouchMin)) {
        Icon(
            imageVector = LocalIcons.Close,
            contentDescription = stringResource(R.string.cd_close_button),
            modifier = Modifier.size(Spacing.lg),
            tint = if (isDark) TextDarkSecondary else TextTertiary
        )
    }
}

// ── 答案内容区域 ──

private data class AnswerSection(val label: String, val content: String, val isAnswer: Boolean, val isExplanation: Boolean = false)

@Composable
private fun AnswerBody(text: String, isDark: Boolean) {
    val sections = remember(text) { parseSections(text) }
    // 只在底部有圆角，顶部与header无缝连接
    val bodyShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = CardRadius, bottomEnd = CardRadius)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cornerRadiusPx = with(density) { CardRadius.toPx() }
    val bgColor = if (isDark) GlassDark else GlassWhiteStrong
    val borderColor = if (isDark) GlassDarkBorder else GlassWhiteBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(bodyShape)
            .drawBehind {
                // 绘制只有底部圆角的背景
                val path = androidx.compose.ui.graphics.Path().apply {
                    val w = size.width
                    val h = size.height
                    val r = cornerRadiusPx
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h - r)
                    quadraticBezierTo(w, h, w - r, h)
                    lineTo(r, h)
                    quadraticBezierTo(0f, h, 0f, h - r)
                    close()
                }
                drawPath(path, color = bgColor.copy(alpha = bgColor.alpha))
                drawPath(path, color = borderColor, style = Stroke(0.5.dp.toPx()))
            }
            .padding(Spacing.lg)
    ) {
        val maxCardHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.4f).dp
            .coerceIn(MinCardHeight, MaxCardHeight)
        Column(
            modifier = Modifier
                .heightIn(max = maxCardHeight)
                .verticalScroll(rememberScrollState())
        ) {
            sections.forEach { section ->
                Spacer(Modifier.height(if (section.isAnswer) Spacing.sm else Spacing.xs))

                // Section label
                if (section.label.isNotBlank()) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) TextDarkSecondary else TextTertiary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                }

                // Section content
                if (section.isAnswer) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ChipRadius))
                            .background(AnswerHighlightBg)
                            .border(0.5.dp, AnswerHighlightBorder, RoundedCornerShape(ChipRadius))
                            .padding(Spacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = section.content,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextDarkPrimary else TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Icon(
                                imageVector = LocalIcons.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(Spacing.lg),
                                tint = SuccessGreen
                            )
                        }
                    }
                } else if (section.isExplanation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ChipRadius))
                            .background(if (isDark) ExplanationBgDark else ExplanationBgLight)
                            .padding(Spacing.md)
                    ) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .width(Spacing.xs)
                                    .heightIn(min = Spacing.xxxl + Spacing.md)
                                    .clip(RoundedCornerShape(Spacing.xs))
                                    .background(PremiumPrimary)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = section.content,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isDark) TextDarkSecondary else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column {
                        section.content.split("\n").filter { it.isNotBlank() }.forEach { line ->
                            val isCorrect = line.contains("✓") || line.contains("√")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs)
                                    .clip(RoundedCornerShape(ChipRadius))
                                    .background(
                                        if (isCorrect) CorrectOptionBg
                                        else if (isDark) GlassDark
                                        else ChipUnselected
                                    )
                                    .then(
                                        if (isCorrect) Modifier.border(
                                            0.5.dp, CorrectOptionBorder,
                                            RoundedCornerShape(ChipRadius)
                                        )
                                        else Modifier
                                    )
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            ) {
                                Text(
                                    text = line.trim(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isCorrect) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCorrect) (if (isDark) TextDarkPrimary else TextDark)
                                            else (if (isDark) TextDarkSecondary else TextSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Floating Button Background ──

@Composable
private fun FloatingBtnBackground(floatingStatus: FloatingStatus, shape: RoundedCornerShape, cornerRadius: androidx.compose.ui.unit.Dp = CardRadius) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shadowElev = with(density) { Spacing.lg.toPx() }
    val shadowColor = Color.Black.copy(alpha = ShadowFloatingAlpha)
    // 按钮全程使用深灰色调，不变色
    Box(modifier = Modifier.fillMaxSize().darkAccentGradient(shape, cornerRadius, shadowElev, shadowColor))
}

// ── Section Parser ──

private fun parseSections(raw: String): List<AnswerSection> {
    // Support both Chinese brackets 【...】 and markdown bold **...**
    val pattern = Regex("""(?:【([^】]+)】|\*\*([^*]+)\*\*)""")
    val matches = pattern.findAll(raw).toList()
    if (matches.isEmpty()) return listOf(AnswerSection("", raw.trim(), isAnswer = false))
    return matches.mapIndexed { i, m ->
        val start = m.range.last + 1
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else raw.length
        val sectionLabel = m.groupValues[1].ifBlank { m.groupValues[2] }
        val label = if (m.groupValues[1].isNotBlank()) "【$sectionLabel】" else "**$sectionLabel**"
        AnswerSection(
            label = label,
            content = raw.substring(start, end).trim(),
            isAnswer = sectionLabel.contains("答案") || sectionLabel.contains("answer", ignoreCase = true),
            isExplanation = sectionLabel.contains("解析") || sectionLabel.contains("analysis", ignoreCase = true) || sectionLabel.contains("explanation", ignoreCase = true)
        )
    }
}
