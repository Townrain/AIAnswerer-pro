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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.ui.theme.DW
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===== extracted from FloatingWindowContent.kt =====

internal enum class BouncyState { Idle, Pressed, LongPressed, Released }

@Composable
internal fun Bouncy(
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    longPressDurationMs: Long = FWAnim.longPressDurationMs,
    modifier: Modifier = Modifier,
    progressContent: (@Composable BoxScope.(Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scaleAnim = remember { Animatable(1f) }
    val progressAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier
            .wrapContentSize()
            .scale(scaleAnim.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scaleAnim.snapTo(FWAnim.bouncyScale)
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
    val badge: Pair<String, Color>?
)

internal fun pillVisual(status: FloatingStatus, isRecording: Boolean, isLight: Boolean): PillVisual {
    return when {
        isRecording -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(Color(0xFFFF3B30), Color(0xFFD32F2F)),
                Offset.Zero, Offset.Infinite
            ),
            border = Color(0xFFFF6961).copy(alpha = 0.45f),
            iconTint = Color.White.copy(alpha = 0.95f),
            badge = null
        )
        status in listOf(
            FloatingStatus.Capturing, FloatingStatus.Recognizing,
            FloatingStatus.Searching, FloatingStatus.GettingAnswer
        ) -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(Color(0xFF1A1A2E), Color(0xFF2D2B55)),
                Offset.Zero, Offset.Infinite
            ),
            border = Color.White.copy(alpha = 0.12f),
            iconTint = Color.White.copy(alpha = 0.95f),
            badge = null
        )
        status == FloatingStatus.Success -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(Color(0xFF2D2B55), Color(0xFF4C4889)),
                Offset.Zero, Offset.Infinite
            ),
            border = Color.White.copy(alpha = 0.12f),
            iconTint = Color.White.copy(alpha = 0.95f),
            badge = "✓" to Color(0xFF34C759)
        )
        status == FloatingStatus.Error -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(Color(0xFF2D2B55), Color(0xFF4C4889)),
                Offset.Zero, Offset.Infinite
            ),
            border = Color.White.copy(alpha = 0.12f),
            iconTint = Color.White.copy(alpha = 0.95f),
            badge = "✗" to Color(0xFFFF3B30)
        )
        else -> PillVisual(
            gradient = Brush.linearGradient(
                listOf(Color(0xFF2D2B55), Color(0xFF4C4889)),
                Offset.Zero, Offset.Infinite
            ),
            border = Color.White.copy(alpha = 0.12f),
            iconTint = Color.White.copy(alpha = 0.95f),
            badge = null
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
    buttonAlpha: Float,
    isLeftSide: Boolean,
    isDragging: Boolean,
    dragMod: Modifier,
    onCaptureClick: () -> Unit,
    onLongPress: () -> Unit,
    onQuickToggle: () -> Unit
) {
    val density = LocalDensity.current
    val visual = remember(status, isRecording, t.isLight) { pillVisual(status, isRecording, t.isLight) }
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
        FloatingStatus.Success -> Color(0xFF34C759).copy(alpha = 0.5f)
        FloatingStatus.Error -> Color(0xFFFF3B30).copy(alpha = 0.5f)
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
            if (expandQuickButtons) {
                onQuickToggle()
                if (isRecording) onCaptureClick()
            } else onCaptureClick()
        },
        onLongPress = onLongPress,
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
                        brush = Brush.sweepGradient(listOf(Color.White, Color(0xFFB8B0FF), Color.White)),
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
        Row(
            Modifier
                .widthIn(max = with(density) { 120.dp })
                .graphicsLayer {
                    alpha = buttonAlpha
                    scaleX = successScale * dragScale * recordingPulse.value
                    scaleY = successScale * dragScale * recordingPulse.value
                    transformOrigin = if (isLeftSide) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                }
                .shadow(dragShadow.dp, pillShape, spotColor = Color(0xFF4C4889).copy(alpha = if (isDragging) 0.5f else 0.3f))
                .then(
                    if (successGlow.value > 0f) Modifier.drawBehind {
                        val glowRadius = size.maxDimension * 0.6f
                        drawCircle(
                            color = Color(0xFF34C759).copy(alpha = successGlow.value * 0.3f),
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
                    if (isRecording) Color(0xFFFF3B30).copy(alpha = recordingBorderAlpha.value)
                    else if (glowColor != Color.Transparent) glowColor
                    else visual.border,
                    pillShape
                )
                .padding(horizontal = FWDims.pillHPadding, vertical = FWDims.pillVPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visual.badge?.let { (label, color) ->
                if (!isLeftSide) {
                    Badge2(label, color)
                    Spacer(Modifier.width(8.dp))
                }
            }
            Icon(
                IcCapture, "screenshot",
                tint = visual.iconTint,
                modifier = Modifier.size(FWDims.pillIconSize)
            )
            visual.badge?.let { (label, color) ->
                if (isLeftSide) {
                    Spacer(Modifier.width(8.dp))
                    Badge2(label, color)
                }
            }
        }
    }
}

// =============================================================================
// 图标：截图按钮 (magnifying glass)
// =============================================================================

internal val IcCapture: ImageVector by lazy {
    ImageVector.Builder("IcCapture", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
            curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
            lineToRelative(0.27f, 0.28f)
            verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f)
            lineTo(20.49f, 19f)
            lineToRelative(-4.99f, -5f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
            reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
            reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
            close()
        }
    }.build()
}

// =============================================================================
// Badge2 (synced from sandbox)
// =============================================================================

@Composable
private fun Badge2(label: String, color: Color, alpha: Float = 1f, modifier: Modifier = Modifier) {
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, style = DW.LabelSmall.copy(color = color, fontSize = 10.sp))
    }
}

// =============================================================================
// StatusDot (synced from sandbox)
// =============================================================================

@Composable
internal fun StatusDot(status: FloatingStatus) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(
        when (status) {
            FloatingStatus.Success -> Color(0xFF34C759)
            FloatingStatus.Error -> Color(0xFFFF3B30)
            else -> Color(0xFF6C5CE7)
        }
    ))
}
