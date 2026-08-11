package com.hwb.aianswerer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.ui.theme.ErrorRedLight
import com.hwb.aianswerer.ui.theme.ImageCollectingPurple
import com.hwb.aianswerer.ui.theme.ImageCollectingPurpleDark
import com.hwb.aianswerer.ui.theme.ImageCollectingPurpleLight
import com.hwb.aianswerer.ui.theme.PremiumPrimary
import com.hwb.aianswerer.ui.theme.RecordingRed
import com.hwb.aianswerer.ui.theme.RecordingRedDark
import com.hwb.aianswerer.ui.theme.SuccessGreen
import com.hwb.aianswerer.ui.theme.SuccessGreenLight
import com.hwb.aianswerer.ui.theme.Th
import com.hwb.aianswerer.ui.icons.LocalIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===== extracted from FloatingWindowContent.kt =====

// ===== 点击逻辑解析（纯函数，可测试）=====

internal enum class PillClickAction {
    CaptureOnly,
    QuickToggleOnly,
    QuickToggleAndCapture
}

internal fun resolvePillClickAction(
    expandQuickButtons: Boolean,
    isRecording: Boolean
) = when {
    expandQuickButtons && isRecording -> PillClickAction.QuickToggleAndCapture
    expandQuickButtons -> PillClickAction.QuickToggleOnly
    else -> PillClickAction.CaptureOnly
}

/** 按钮缩放合成: success弹跳 × 拖拽反馈 × 录制脉冲 */
internal fun computeButtonScale(successScale: Float, dragScale: Float, recordingPulse: Float): Float {
    return successScale * dragScale * recordingPulse
}

internal enum class BouncyState { Idle, Pressed, LongPressed, Released }

@Composable
internal fun Bouncy(
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    longPressDurationMs: Long = FWAnim.longPressDurationMs,
    suppressPress: Boolean = false,
    modifier: Modifier = Modifier,
    progressContent: (@Composable BoxScope.(Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scaleAnim = remember { Animatable(1f) }
    val progressAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    // M15: 拖动时抑制按压反馈——拖动已开始时按下不缩放，避免"想拖先被按"的干扰
    val currentSuppressPress by rememberUpdatedState(suppressPress)

    Box(
        modifier
            .wrapContentSize()
            .scale(scaleAnim.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!currentSuppressPress) {
                            scaleAnim.snapTo(FWAnim.bouncyScale)
                        }
                        val progressJob = scope.launch {
                            delay(150)
                            progressAnim.animateTo(1f, tween((longPressDurationMs - 150).toInt().coerceAtLeast(1)))
                        }
                        val released = tryAwaitRelease()
                        progressJob.cancel()
                        scope.launch {
                            scaleAnim.animateTo(1f, FWAnim.bouncyScaleSpring)
                        }
                        if (released) {
                            if (progressAnim.value >= 0.99f) {
                                currentOnLongPress?.invoke()
                                progressAnim.animateTo(0f, tween(FWAnim.progressDrainMs))
                            } else {
                                progressAnim.snapTo(0f)
                                currentOnClick()
                            }
                        } else {
                            progressAnim.snapTo(0f)
                        }
                    }
                )
            }
    ) {
        content()
        progressContent?.invoke(this, progressAnim.value)
    }
}

// PillVisual — 渐变背景 + 边框 + 图标色 (synced from sandbox)
// =============================================================================

internal data class PillVisual(
    val gradient: Brush,
    val border: Color,
    val iconTint: Color,
)

internal fun pillVisual(status: FloatingStatus, isRecording: Boolean, isImageCollecting: Boolean, t: Th): PillVisual {
    return when {
        isImageCollecting -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(ImageCollectingPurple, ImageCollectingPurpleDark),
                Offset.Zero, Offset.Infinite
            ),
            border = ImageCollectingPurpleLight.copy(alpha = 0.45f),
            iconTint = Color.White.copy(alpha = 0.95f),
        )
        isRecording -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(RecordingRed, RecordingRedDark),
                Offset.Zero, Offset.Infinite
            ),
            border = ErrorRedLight.copy(alpha = 0.45f),
            iconTint = Color.White.copy(alpha = 0.95f),
        )
        status in listOf(
            FloatingStatus.Capturing, FloatingStatus.Recognizing,
            FloatingStatus.Searching, FloatingStatus.GettingAnswer
        ) -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(t.pd, t.p),
                Offset.Zero, Offset.Infinite
            ),
            border = t.w.copy(alpha = 0.12f),
            iconTint = t.w.copy(alpha = 0.95f),
        )
        status == FloatingStatus.Success -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(t.ok, SuccessGreenLight),
                Offset.Zero, Offset.Infinite
            ),
            border = t.ok.copy(alpha = 0.3f),
            iconTint = t.w.copy(alpha = 0.95f),
        )
        status == FloatingStatus.Error -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(t.err, ErrorRedLight),
                Offset.Zero, Offset.Infinite
            ),
            border = t.err.copy(alpha = 0.3f),
            iconTint = t.w.copy(alpha = 0.95f),
        )
        else -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(t.p, t.pe),
                Offset.Zero, Offset.Infinite
            ),
            border = t.w.copy(alpha = 0.25f),
            iconTint = t.w.copy(alpha = 0.95f),
        )
    }
}


// =============================================================================
// rememberShimmerBrush (synced from sandbox)
// =============================================================================

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(FWAnim.shimmerDurationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "so"
    )
    return Brush.linearGradient(
        listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
        start = Offset(offset * 300f, 0f),
        end = Offset(offset * 300f + 150f, 0f)
    )
}

// ── pillShape constant ──
private val pillShape = RoundedCornerShape(FWDims.pillCornerRadius)


// =============================================================================
// 子组件 (synced from sandbox)
// =============================================================================

@Composable
internal fun PillButton(
    t: com.hwb.aianswerer.ui.theme.Th,
    status: FloatingStatus,
    expandQuickButtons: Boolean,
    isRecording: Boolean,
    isImageCollecting: Boolean,
    buttonAlpha: Float,
    isLeftSide: Boolean,
    isDragging: Boolean,
    dragMod: Modifier,
    onCaptureClick: () -> Unit,
    onLongPress: () -> Unit,
    onQuickToggle: () -> Unit,
    longPressDurationMs: Long = com.hwb.aianswerer.config.AppConfig.getLongPressDuration().toLong()
) {
    val density = LocalDensity.current
    val visual = remember(status, isRecording, isImageCollecting, t) { pillVisual(status, isRecording, isImageCollecting, t) }
    val isBusy = status in listOf(FloatingStatus.Capturing, FloatingStatus.Recognizing, FloatingStatus.Searching, FloatingStatus.GettingAnswer)

    val shimmerBrush = if (isBusy) rememberShimmerBrush() else null

    // Success bounce
    val successScaleAnim = remember { Animatable(1f) }
    val successScale = successScaleAnim.value
    LaunchedEffect(status) {
        if (status == FloatingStatus.Success) {
            successScaleAnim.snapTo(1.03f)
            delay(120)
            successScaleAnim.animateTo(1f, spring(1.0f, 800f))
        } else {
            successScaleAnim.snapTo(1f)
        }
    }

    // Drag visual feedback
    val dragScale by animateFloatAsState(if (isDragging) 1.05f else 1f, spring(0.8f, 400f), label = "ds")
    val dragShadow by animateFloatAsState(if (isDragging) 16f else 8f, tween(200), label = "dsh")

    // Recording pulse
    val recordingPulse = remember { Animatable(1f) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                recordingPulse.animateTo(0.85f, tween(600))
                recordingPulse.animateTo(1f, tween(600))
            }
        } else {
            recordingPulse.snapTo(1f)
        }
    }

    // Recording border pulse
    val recordingBorderAlpha = remember { Animatable(0f) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                recordingBorderAlpha.animateTo(0.6f, tween(500))
                recordingBorderAlpha.animateTo(0.2f, tween(500))
            }
        } else {
            recordingBorderAlpha.snapTo(0f)
        }
    }

    // Glow border
    val glowTarget = when (status) {
        FloatingStatus.Success -> t.ok.copy(alpha = 0.5f)
        FloatingStatus.Error -> t.err.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val glowColor by animateColorAsState(glowTarget, tween(350), label = "gc")

    // Success glow pulse
    val successGlow = remember { Animatable(0f) }
    LaunchedEffect(status) {
        if (status == FloatingStatus.Success) {
            successGlow.snapTo(0f)
            successGlow.animateTo(1f, tween(400))
            successGlow.animateTo(0f, tween(600))
        } else {
            successGlow.snapTo(0f)
        }
    }

    // 按沙箱 PillButton2 方式：用 Bouncy 包裹，dragMod 传给 Bouncy
    Bouncy(
        onClick = {
            when (resolvePillClickAction(expandQuickButtons, isRecording)) {
                PillClickAction.CaptureOnly -> onCaptureClick()
                PillClickAction.QuickToggleOnly -> onQuickToggle()
                PillClickAction.QuickToggleAndCapture -> {
                    onQuickToggle()
                    onCaptureClick()
                }
            }
        },
        onLongPress = onLongPress,
        longPressDurationMs = longPressDurationMs,
        // M15: 拖动中抑制按压反馈
        suppressPress = isDragging,
        modifier = dragMod,
        progressContent = { progress ->
            if (progress > 0f) {
                Box(Modifier.matchParentSize().drawBehind {
                    val stroke = FWDims.progressStroke.toPx()
                    val pad = stroke / 2 + FWDims.progressInset.toPx()
                    val arcSize = Size(
                        size.width - stroke - 2 * FWDims.progressInset.toPx(),
                        size.height - stroke - 2 * FWDims.progressInset.toPx()
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(t.w, t.pe, t.w)),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                })
            }
        }
    ) {

    // 单一动画状态推导: success弹跳 × 拖拽反馈 × 录制脉冲
    val buttonScale by remember {
        derivedStateOf { computeButtonScale(successScale, dragScale, recordingPulse.value) }
    }

        Row(
            Modifier
                .widthIn(max = with(density) { 120.dp })
                .graphicsLayer {
                    alpha = buttonAlpha
                    scaleX = buttonScale
                    scaleY = buttonScale
                    transformOrigin = if (isLeftSide) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                }
                .shadow(dragShadow.dp, pillShape, spotColor = t.p.copy(alpha = if (isDragging) 0.35f else 0.15f))
                .then(
                    if (successGlow.value > 0f) Modifier.drawBehind {
                        val glowRadius = size.maxDimension * 0.6f
                        drawCircle(
                            color = t.ok.copy(alpha = successGlow.value * 0.3f),
                            radius = glowRadius,
                            center = center
                        )
                    } else Modifier
                )
                .clip(pillShape)
                .background(visual.gradient)
                .then(if (shimmerBrush != null) Modifier.background(shimmerBrush) else Modifier)
                .border(
                    if (isRecording) 1.5.dp else 0.5.dp,
                    if (isRecording) RecordingRed.copy(alpha = recordingBorderAlpha.value)
                    else if (glowColor != Color.Transparent) glowColor
                    else visual.border,
                    pillShape
                )
                .padding(horizontal = FWDims.pillHPadding, vertical = FWDims.pillVPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                LocalIcons.Search, "screenshot",
                tint = visual.iconTint,
                modifier = Modifier.size(FWDims.pillIconSize)
            )
        }
    }
}



// =============================================================================
// StatusDot (synced from sandbox)
// =============================================================================


@Composable
internal fun StatusDot(status: FloatingStatus) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(
        when (status) {
            FloatingStatus.Success -> SuccessGreen
            FloatingStatus.Error -> RecordingRed
            else -> PremiumPrimary
        }
    ))
}
