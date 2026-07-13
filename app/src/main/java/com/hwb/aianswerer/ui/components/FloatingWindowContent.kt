package com.hwb.aianswerer.ui.components

import com.hwb.aianswerer.ui.icons.LocalIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

// Shared components now in FloatingComponents.kt

// =============================================================================
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
// Bouncy — state-machine press helper (synced from sandbox)
// =============================================================================

private enum class BouncyState { Idle, Pressed, LongPressed, Released }

@Composable
private fun Bouncy(
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

// =============================================================================
// Section parser (synced from sandbox)
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
// 主 Composable
// =============================================================================

@Composable
fun FloatingWindowContent(
    answerText: String?, showAnswer: Boolean, statusMessage: String?,
    buttonSize: Int = 56, buttonAlpha: Float = 1.0f, cardAlpha: Float = 1.0f,
    isLeftSide: Boolean = true, floatingStatus: FloatingStatus = FloatingStatus.Idle,
    onCaptureClick: () -> Unit, onCloseAnswer: () -> Unit, onCloseStatus: () -> Unit,
    onCopyAnswer: (() -> Unit)? = null,
    onSettled: ((leftSide: Boolean, pillCenterX: Float) -> Unit)? = null,
    visionEnabled: Boolean = false,
    searchEnabled: Boolean = false,
    reasoningEnabled: Boolean = false,
    onVisionToggle: (() -> Unit)? = null,
    onSearchToggle: (() -> Unit)? = null,
    onReasoningToggle: (() -> Unit)? = null,
    isRecording: Boolean = false,
    isProcessingRecording: Boolean = false,
    recordingCaptureCount: Int = 0,
    recordingProcessedCount: Int = 0,
    recordingAnswers: List<Pair<Int, String>> = emptyList(),
    paginatedAnswers: List<Pair<Int, String>> = emptyList(),
    paginatedCopyTexts: List<Pair<Int, String>> = emptyList(),
    onCopyRecordingAnswer: ((String) -> Unit)? = null,
    onRecordingToggle: () -> Unit = {},
    onArcExpandChanged: ((Boolean) -> Unit)? = null,
    externalPillX: Float = -1f,
    externalPillY: Float = -1f,
    externalPillW: Float = -1f,
    externalPillH: Float = -1f,
    renderPill: Boolean = true,
    renderCard: Boolean = true,
    windowScreenX: Float = 0f,
    windowScreenY: Float = 0f,
    windowWidthPx: Float = 0f,
    onDragEnd: ((leftSide: Boolean) -> Unit)? = null,
    onMove: ((Float, Float) -> Unit)? = null,
    onContentVisibilityChanged: ((Boolean) -> Unit)? = null,
    initialY: Float = 0f,
    onWindowBoundsChanged: ((screenX: Int, screenY: Int, width: Int, height: Int) -> Unit)? = null,
    onInteractiveAreaChanged: ((left: Float, top: Float, right: Float, bottom: Float) -> Unit)? = null
) {
    val currentOnCaptureClick by rememberUpdatedState(onCaptureClick)
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnContentVisibility by rememberUpdatedState(onContentVisibilityChanged)
    val isDark = LocalIsDarkMode.current
    val t = if (isDark) DH else LH

    val hasAnswer = answerText != null || recordingAnswers.isNotEmpty() || paginatedAnswers.isNotEmpty()
    var showCard by remember { mutableStateOf(false) }

    LaunchedEffect(floatingStatus, statusMessage, recordingAnswers, paginatedAnswers) {
        showCard = when (floatingStatus) {
            FloatingStatus.Idle -> statusMessage != null || recordingAnswers.isNotEmpty() || paginatedAnswers.isNotEmpty()
            FloatingStatus.Success, FloatingStatus.Error -> true
            FloatingStatus.Capturing, FloatingStatus.Recognizing,
            FloatingStatus.Searching, FloatingStatus.GettingAnswer -> true
        }
    }

    // Report content visibility for window height management
    LaunchedEffect(showCard, hasAnswer, statusMessage) {
        currentOnContentVisibility?.invoke(showCard && (hasAnswer || statusMessage != null))
    }

    var showQuickButtons by remember { mutableStateOf(false) }

    val quickActions = remember(visionEnabled, searchEnabled, reasoningEnabled, isRecording) {
        listOf(
            QuickAction(IcVision, "VLM", visionEnabled) { onVisionToggle?.invoke() },
            QuickAction(IcGlobe, "联网", searchEnabled) { onSearchToggle?.invoke() },
            QuickAction(IcBulb, "深度", reasoningEnabled) { onReasoningToggle?.invoke() },
            QuickAction(IcRecord, "录制", isRecording) { onRecordingToggle() }
        )
    }

    LaunchedEffect(hasAnswer) {
        if (hasAnswer && !isRecording && showQuickButtons) {
            onArcExpandChanged?.invoke(false)
            showQuickButtons = false
        }
    }

    val density = LocalDensity.current
    val gapPx = with(density) { FWDims.quickPanelGap.toPx() }

    var measuredPillW by remember { mutableFloatStateOf(0f) }
    var measuredPillH by remember { mutableFloatStateOf(0f) }
    var measuredQuickW by remember { mutableFloatStateOf(0f) }
    var measuredQuickH by remember { mutableFloatStateOf(0f) }

    val quickScale by animateFloatAsState(
        if (showQuickButtons) 1f else 0f,
        FWAnim.expandSpring, label = "qts"
    )

    // ── Drag state ──
    var isDragging by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }
    var snapY by remember { mutableFloatStateOf(initialY) }
    var dragY by remember { mutableFloatStateOf(initialY) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var rightEdge by remember { mutableFloatStateOf(-1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sW = constraints.maxWidth.toFloat()
        val sH = constraints.maxHeight.toFloat()

        val marginPxInit = with(density) { FWDims.pillEdgeMargin.toPx() }
        val pillHInit = with(density) { buttonSize.dp.toPx() }
        if (rightEdge < 0f) {
            rightEdge = sW - marginPxInit
            dragX = rightEdge - with(density) { 40.dp.toPx() }
            snapY = initialY.coerceIn(0f, (sH - pillHInit).coerceAtLeast(0f))
            dragY = snapY
        }
        val pillH = with(density) { buttonSize.dp.toPx() }
        val pillW = if (measuredPillW > 0f) measuredPillW else with(density) { 40.dp.toPx() }
        val marginPx = with(density) { FWDims.pillEdgeMargin.toPx() }
        val rightEdgeTarget = sW - marginPx
        val leftEdgeTarget = marginPx
        val curIsLeftSide = rightEdge < sW / 2f

        val posX = if (isDragging || isAnimating) dragX else rightEdge - pillW
        val posY = if (isDragging || isAnimating) dragY else snapY
        val clampedY = posY.coerceIn(0f, (sH - pillH).coerceAtLeast(0f))

        // ── Dual-axis spring animation ──
        val snapX = rightEdge - pillW
        LaunchedEffect(isDragging, snapX, snapY, sW, pillW) {
            if (!isDragging) {
                rightEdge = if (curIsLeftSide) leftEdgeTarget + pillW else rightEdgeTarget
                val targetX = rightEdge - pillW
                dragX = targetX
                try {
                    coroutineScope {
                        launch {
                            animX.snapTo(dragX)
                            animX.animateTo(targetX, FWAnim.snapSpringX) { dragX = value }
                        }
                        launch {
                            animY.snapTo(dragY)
                            animY.animateTo(snapY, FWAnim.snapSpringY) { dragY = value }
                        }
                    }
                } finally {
                    isAnimating = false
                    if (!isDragging) {
                        dragX = targetX; dragY = snapY
                        currentOnSettled?.invoke(curIsLeftSide, dragX + measuredPillW / 2f)
                    }
                }
            }
        }

        // ── Drag gesture ──
        val dragMod = Modifier.pointerInput(pillW) {
            detectDragGestures(
                onDragStart = {
                    fingerX = rightEdge - pillW
                    dragX = fingerX
                    dragY = if (isAnimating) dragY else animY.value
                    isDragging = true; isAnimating = true
                },
                onDragEnd = {
                    isDragging = false
                    val centerX = sW / 2f
                    val leftSide = fingerX < centerX
                    rightEdge = if (leftSide) leftEdgeTarget + pillW else rightEdgeTarget
                    snapY = dragY.coerceIn(0f, (sH - pillH).coerceAtLeast(0f))
                    currentOnDragEnd.value?.invoke(leftSide)
                },
                onDragCancel = { isDragging = false },
                onDrag = { _: PointerInputChange, d: Offset ->
                    fingerX = (fingerX + d.x).coerceIn(marginPx, sW - marginPx)
                    dragX = fingerX
                    val centerX = sW / 2f
                    rightEdge = if (fingerX < centerX) leftEdgeTarget + pillW else rightEdgeTarget
                    dragY = (dragY + d.y).coerceIn(0f, (sH - pillH).coerceAtLeast(0f))
                    snapY = dragY
                    currentOnMove?.invoke(d.x, d.y)
                }
            )
        }

        val quickOrigin = if (curIsLeftSide) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)

        val pillPlacedX = if (externalPillX >= 0f) externalPillX
                          else if (isDragging || isAnimating) dragX
                          else if (curIsLeftSide) marginPx
                          else sW - marginPx - measuredPillW
        val effectivePillY = if (externalPillY >= 0f) externalPillY else clampedY
        val effectivePillW = if (externalPillW >= 0f) externalPillW else measuredPillW
        val effectivePillH = if (externalPillH >= 0f) externalPillH else measuredPillH
        val effectiveIsLeftSide = if (externalPillX >= 0f) externalPillX < sW / 2f else curIsLeftSide

        Box(Modifier.fillMaxSize()) {
            // Pill button (only when renderPill=true)
            if (renderPill) {
                val maxPillW = with(density) { 120.dp }
                Box(
                    Modifier
                        .widthIn(max = maxPillW)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val boxW = placeable.width
                            val x = if (isDragging || isAnimating) {
                                if (externalPillX >= 0f) externalPillX.roundToInt() else dragX.roundToInt()
                            } else if (curIsLeftSide) {
                                marginPx.roundToInt()
                            } else {
                                (sW - marginPx - boxW).roundToInt()
                            }
                            val y = clampedY.roundToInt()
                            layout(boxW, placeable.height) {
                                placeable.placeRelative(x, y)
                            }
                        }
                        .onGloballyPositioned {
                            measuredPillW = it.size.width.toFloat()
                            measuredPillH = it.size.height.toFloat()
                        }
                ) {
                    PillButton(
                        t = t,
                        status = floatingStatus,
                        expandQuickButtons = showQuickButtons,
                        isRecording = isRecording,
                        buttonAlpha = buttonAlpha,
                        isLeftSide = curIsLeftSide,
                        isDragging = isDragging,
                        dragMod = dragMod,
                        onCaptureClick = { currentOnCaptureClick() },
                        onLongPress = {
                            onArcExpandChanged?.invoke(!showQuickButtons)
                            showQuickButtons = !showQuickButtons
                        },
                        onQuickToggle = {
                            onArcExpandChanged?.invoke(false)
                            showQuickButtons = false
                        }
                    )
                }

                // Quick toggles
                val pillX = if (isDragging || isAnimating) dragX
                            else if (curIsLeftSide) marginPx
                            else rightEdge - measuredPillW
                val quickOffsetX = if (curIsLeftSide) pillX + measuredPillW + gapPx
                                   else pillX - measuredQuickW - gapPx
                val quickOffsetY = clampedY + (measuredPillH - measuredQuickH) / 2f

                Box(
                    Modifier
                        .wrapContentSize()
                        .offset { IntOffset(quickOffsetX.roundToInt().coerceAtLeast(0), quickOffsetY.roundToInt()) }
                        .onGloballyPositioned {
                            measuredQuickW = it.size.width.toFloat()
                            measuredQuickH = it.size.height.toFloat()
                        }
                ) {
                    QuickToggles(
                        t = t,
                        actions = quickActions,
                        scale = quickScale,
                        isLeftSide = curIsLeftSide,
                        transformOrigin = quickOrigin
                    )
                }
            }
        }

        // ── Answer card ──
        val cardW = sW * FWDims.cardWidthRatio
        val maxCardX = (sW - cardW).roundToInt().coerceAtLeast(0)
        val pillCenterX = pillPlacedX + effectivePillW / 2f
        val cardOffX = if (effectiveIsLeftSide) pillPlacedX.roundToInt()
                       else (pillCenterX - cardW / 2f).roundToInt()
        val cardOffXClamped = cardOffX.coerceIn(0, maxCardX)
        val cardOffY = (effectivePillY + effectivePillH + gapPx).roundToInt()
        if (renderCard) {

        AnimatedVisibility(
            visible = showCard && (hasAnswer || statusMessage != null),
            modifier = Modifier
                .offset { IntOffset(cardOffXClamped, cardOffY) }
                .graphicsLayer { alpha = cardAlpha },
            enter = slideInVertically(spring(0.8f, 300f)) { -it / 4 } + fadeIn(tween(200)) + scaleIn(spring(0.8f, 300f), initialScale = 0.9f),
            exit = slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
        ) {
            val cardDp = with(density) { cardW.toDp() }
                // 统一翻页卡片：普通模式答题结果（paginatedAnswers）或录制完成结果（recordingAnswers）
                val displayAnswers = if (paginatedAnswers.isNotEmpty()) paginatedAnswers
                    else if (recordingAnswers.isNotEmpty() && !isRecording && !isProcessingRecording) recordingAnswers
                    else emptyList()
                if (displayAnswers.isNotEmpty()) {
                    Box(Modifier.width(cardDp)) {
                        RecordingResultCard(
                            t, displayAnswers, onCloseAnswer,
                            onCopyAnswer = onCopyRecordingAnswer ?: {},
                            isProcessing = false,
                            processedCount = displayAnswers.size,
                            totalCount = displayAnswers.size
                        )
                    }
            } else if (recordingCaptureCount > 0 && !showAnswer && statusMessage != null && (isRecording || isProcessingRecording)) {
                // 录制中：展示进度
                Box(Modifier.width(cardDp)) {
                    Card(t, null, false, statusMessage, floatingStatus, {}, onCloseStatus, onCloseStatus)
                }
            } else {
                Box(Modifier.width(cardDp)) {
                    Card(t, answerText, hasAnswer, statusMessage, floatingStatus, onCopyAnswer ?: {}, onCloseAnswer, onCloseStatus)
                }
            }
        }
        } // renderCard

        // ── Window bounds notification ──
        val currentOnBoundsChanged by rememberUpdatedState(onWindowBoundsChanged)
        LaunchedEffect(pillPlacedX, clampedY, measuredPillW, measuredPillH, showCard, cardOffXClamped, cardOffY, hasAnswer, statusMessage) {
            if (measuredPillW <= 0f) return@LaunchedEffect
            val padding = with(density) { 24.dp.toPx() }
            var left = pillPlacedX - padding
            var top = clampedY - padding
            var right = pillPlacedX + measuredPillW + padding
            var bottom = clampedY + measuredPillH + padding
            if (showCard && (hasAnswer || statusMessage != null)) {
                val cardBottom = cardOffY + with(density) { 260.dp.toPx() }
                left = minOf(left, cardOffXClamped.toFloat() - padding)
                right = maxOf(right, cardOffXClamped + cardW + padding)
                bottom = maxOf(bottom, cardBottom + padding)
            }
            val screenLeft = (left + windowScreenX).roundToInt().coerceAtLeast(0)
            val screenTop = (top + windowScreenY).roundToInt().coerceAtLeast(0)
            val screenRight = (right + windowScreenX).roundToInt()
            val screenBottom = (bottom + windowScreenY).roundToInt()
            val pillAreaSize = (measuredPillH + 2 * padding).roundToInt().coerceAtLeast(1)
            val windowW = (screenRight - screenLeft).coerceAtLeast(pillAreaSize)
            val windowH = (screenBottom - screenTop).coerceAtLeast(pillAreaSize)
            currentOnBoundsChanged?.invoke(screenLeft, screenTop, windowW, windowH)
        }

        // ── Touch passthrough area notification ──
        val currentOnInteractiveArea by rememberUpdatedState(onInteractiveAreaChanged)
        LaunchedEffect(pillPlacedX, clampedY, measuredPillW, measuredPillH,
                        showQuickButtons, showCard, cardOffXClamped, cardOffY, cardW, hasAnswer, statusMessage) {
            if (measuredPillW <= 0f) return@LaunchedEffect
            val pad = with(density) { 16.dp.toPx() }
            var left = pillPlacedX - pad
            var top = clampedY - pad
            var right = pillPlacedX + measuredPillW + pad
            var bottom = clampedY + measuredPillH + pad
            if (showQuickButtons) {
                val quickW = with(density) { (4 * 40 + 3 * 6 + 8).dp.toPx() }
                if (curIsLeftSide) right = maxOf(right, pillPlacedX + measuredPillW + gapPx + quickW + pad)
                else left = minOf(left, pillPlacedX - gapPx - quickW - pad)
            }
            if (showCard && (hasAnswer || statusMessage != null)) {
                val cardH = with(density) { FWDims.cardMaxHeight.toPx() }
                left = minOf(left, cardOffXClamped.toFloat() - pad)
                right = maxOf(right, cardOffXClamped + cardW + pad)
                bottom = maxOf(bottom, cardOffY + cardH + pad)
            }
            currentOnInteractiveArea?.invoke(left, top, right, bottom)
        }
    }
}


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

val IcCapture: ImageVector by lazy {
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
private fun StatusDot(status: FloatingStatus) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(
        when (status) {
            FloatingStatus.Success -> Color(0xFF34C759)
            FloatingStatus.Error -> Color(0xFFFF3B30)
            else -> Color(0xFF6C5CE7)
        }
    ))
}


// =============================================================================
// QuickToggles (synced from sandbox)
// =============================================================================

@Composable
internal fun QuickToggles(
    t: com.hwb.aianswerer.ui.theme.Th,
    actions: List<QuickAction>,
    scale: Float,
    isLeftSide: Boolean,
    transformOrigin: TransformOrigin
) {
    val itemAnimatables = remember(actions.size) { List(actions.size) { Animatable(0f) } }
    val isExpanding = scale > 0.5f

    LaunchedEffect(isExpanding) {
        if (isExpanding) {
            val order = if (isLeftSide) itemAnimatables.indices else itemAnimatables.indices.reversed()
            order.forEachIndexed { i, index ->
                if (i > 0) delay(FWAnim.staggerDelayMs.toLong())
                launch { itemAnimatables[index].animateTo(1f, FWAnim.expandSpring) }
            }
        } else {
            val order = if (isLeftSide) itemAnimatables.indices.reversed() else itemAnimatables.indices
            order.forEachIndexed { i, index ->
                if (i > 0) delay(FWAnim.staggerDelayMs.toLong())
                launch { itemAnimatables[index].animateTo(0f, FWAnim.collapseSpring) }
            }
        }
    }

    val orderedActions = if (isLeftSide) actions else actions.asReversed()

    val slideOffset by animateFloatAsState(
        if (isExpanding) 0f else if (isLeftSide) -20f else 20f,
        spring(0.85f, 1000f), label = "so"
    )

    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale; alpha = scale
                this.transformOrigin = transformOrigin
                translationX = slideOffset
            }
            .padding(
                start = if (isLeftSide) FWDims.quickPanelGap else 0.dp,
                end = if (isLeftSide) 0.dp else FWDims.quickPanelGap
            ),
        horizontalArrangement = Arrangement.spacedBy(FWDims.quickBtnSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        orderedActions.forEachIndexed { index, action ->
            val s = if (index < itemAnimatables.size) itemAnimatables[index].value else 0f
            QuickBtn(t, action, s)
        }
    }
}


// =============================================================================
// QuickBtn (synced from sandbox)
// =============================================================================

@Composable
private fun QuickBtn(t: com.hwb.aianswerer.ui.theme.Th, action: QuickAction, itemScale: Float) {
    val s by animateFloatAsState(
        if (action.enabled) 1.08f else 1f,
        FWAnim.expandSpring, label = "q"
    )

    val elasticScale = remember { Animatable(0f) }
    LaunchedEffect(itemScale) {
        if (itemScale > 0.5f && elasticScale.value < 0.5f) {
            elasticScale.snapTo(0.8f)
            elasticScale.animateTo(1.05f, spring(0.7f, 1200f))
            elasticScale.animateTo(1f, spring(0.85f, 1000f))
        } else if (itemScale < 0.5f) {
            elasticScale.snapTo(0f)
        }
    }

    Bouncy(onClick = action.onClick) {
        Box(
            Modifier
                .size(FWDims.quickBtnSize)
                .graphicsLayer { scaleX = s * itemScale * elasticScale.value; scaleY = s * itemScale * elasticScale.value }
                .clip(CircleShape)
                .background(
                    if (action.enabled) Brush.linearGradient(
                        listOf(Color(0xFF2D2B55), Color(0xFF4C4889)),
                        Offset.Zero, Offset.Infinite
                    )
                    else if (t.isLight) SolidColor(t.ac.copy(alpha = 0.08f))
                    else SolidColor(Color.White.copy(alpha = 0.12f))
                )
                .then(
                    if (action.enabled) Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    else if (t.isLight) Modifier.border(0.5.dp, t.ac.copy(alpha = 0.12f), CircleShape)
                    else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                action.icon, action.label,
                tint = if (action.enabled) Color.White else if (t.isLight) t.osv else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(FWDims.quickBtnIconSize)
            )
        }
    }
}


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
    onCloseStatus: () -> Unit
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
                        Text("答案", style = DW.BodyMedium.copy(fontWeight = FontWeight.SemiBold, color = t.ob))
                    }
                }
                Row {
                    if (hasAnswer) CopyBtn(t, onCopy)
                    CloseBtn(t, if (hasAnswer) onCloseAnswer else onCloseStatus, isBusy = isBusy)
                }
            }
            // Body
            AnimatedVisibility(visible = hasAnswer, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                answerText?.let { Body(t, it) }
            }
        }
    }
}


// =============================================================================
// RecordingResultCard — compact summary + expandable detail (from sandbox)
// =============================================================================

/**
 * 从完整答案文本中提取简短摘要（第一行非空内容）
 */
private fun extractShortAnswer(fullText: String): String {
    val lines = fullText.lines().filter { it.isNotBlank() }
    // 尝试找到答案行
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("**答案**") || trimmed.startsWith("【答案】")) continue
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
    totalCount: Int = 0
) {
    var expanded by remember { mutableStateOf(answers.size > 3) }
    val cardR = FWDims.cardCornerRadius
    val cardShape = RoundedCornerShape(cardR)

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
            // Header: title + copy summary + expand toggle + close
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
                    // Copy summary
                    CopyBtn(t, onCopy = {
                        onCopyAnswer(answers.joinToString("\n") { "${it.first}. ${extractShortAnswer(it.second)}" })
                    })
                    Spacer(Modifier.width(4.dp))
                    // Expand/collapse
                    Bouncy(onClick = { expanded = !expanded }) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(t.ac.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center) {
                            Text(if (expanded) "▲" else "▼", style = DW.LabelMedium.copy(color = t.osv, fontSize = 12.sp))
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // Close
                    CloseBtn(t, onClose, isBusy = false)
                }
            }

            // Pagination state (shared between collapsed and expanded)
            val itemsPerPage = 1
            var curPage by remember { mutableStateOf(0) }
            LaunchedEffect(answers.size) { curPage = 0 }
            val totalPages = (answers.size + itemsPerPage - 1) / itemsPerPage
            val pageAnswers = answers.drop(curPage * itemsPerPage).take(itemsPerPage)

            // Collapsed: compact list with scroll for overflow
            if (!expanded) {
                Column(Modifier
                    .padding(horizontal = FWDims.cardPaddingH)
                    .padding(bottom = FWDims.cardPaddingV)
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                    pageAnswers.forEach { (num, fullText) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("$num.", style = DW.LabelMedium.copy(color = t.osv, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.width(24.dp))
                            Text(extractShortAnswer(fullText), style = DW.BodyMedium.copy(color = t.ob))
                        }
                    }
                    if (isProcessing) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.recording_more_coming),
                                style = DW.BodySmall.copy(color = t.osv)
                            )
                        }
                    }
                    // Pagination controls for collapsed mode
                    if (totalPages > 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            if (curPage > 0) {
                                Bouncy(onClick = { curPage-- }) {
                                    Text("◀ 上一页", style = DW.BodySmall.copy(color = t.ob))
                                }
                            } else {
                                Text("◀ 上一页", style = DW.BodySmall.copy(color = t.osv))
                            }
                            Text(" ${curPage + 1} / $totalPages ", style = DW.BodySmall.copy(color = t.ob))
                            if (curPage < totalPages - 1) {
                                Bouncy(onClick = { curPage++ }) {
                                    Text("下一页 ▶", style = DW.BodySmall.copy(color = t.ob))
                                }
                            } else {
                                Text("下一页 ▶", style = DW.BodySmall.copy(color = t.osv))
                            }
                        }
                    }
                }
            }

            // Expanded: paginated questions
            AnimatedVisibility(visible = expanded, enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
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
                                    Text("第${num}题", style = DW.LabelMedium.copy(color = t.osv, fontWeight = FontWeight.SemiBold))
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
                                    Text("◀ 上一页", style = DW.LabelMedium.copy(color = t.p), modifier = Modifier.padding(8.dp))
                                }
                            } else {
                                Text("◀ 上一页", style = DW.LabelMedium.copy(color = t.ac.copy(alpha = 0.3f)), modifier = Modifier.padding(8.dp))
                            }
                            Text(" ${curPage + 1} / $totalPages ", style = DW.LabelSmall.copy(color = t.osv))
                            if (curPage < totalPages - 1) {
                                Bouncy(onClick = { curPage++ }) {
                                    Text("下一页 ▶", style = DW.LabelMedium.copy(color = t.p), modifier = Modifier.padding(8.dp))
                                }
                            } else {
                                Text("下一页 ▶", style = DW.LabelMedium.copy(color = t.ac.copy(alpha = 0.3f)), modifier = Modifier.padding(8.dp))
                            }
                        }
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
private fun CopyBtn(t: com.hwb.aianswerer.ui.theme.Th, onCopy: () -> Unit) {
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
private fun CloseBtn(t: com.hwb.aianswerer.ui.theme.Th, onClose: () -> Unit, isBusy: Boolean) {
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
