package com.hwb.aianswerer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pill composable for the touchable pill window.
 * Handles pill drag/click/long-press + quick toggle buttons.
 * Reuses PillButton and QuickToggles to match FloatingWindowContent exactly.
 */
@Composable
fun FloatingPillContent(
    floatingStatus: FloatingStatus = FloatingStatus.Idle,
    isRecording: Boolean = false,
    buttonAlpha: Float = 1.0f,
    visionEnabled: Boolean = false,
    searchEnabled: Boolean = false,
    reasoningEnabled: Boolean = false,
    onCaptureClick: () -> Unit,
    onLongPress: () -> Unit,
    onVisionToggle: (() -> Unit)? = null,
    onSearchToggle: (() -> Unit)? = null,
    onReasoningToggle: (() -> Unit)? = null,
    onRecordingToggle: () -> Unit = {},
    onSettled: ((leftSide: Boolean, pillCenterX: Float) -> Unit)? = null,
    onDragEnd: ((leftSide: Boolean) -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onDragging: ((screenX: Float, screenY: Float) -> Unit)? = null,
    initialY: Float = 0f,
    onPillPositionChanged: ((screenX: Float, screenY: Float, width: Float, height: Float) -> Unit)? = null,
    onQuickAreaChanged: ((left: Float, top: Float, right: Float, bottom: Float) -> Unit)? = null,
    onCardAreaChanged: ((left: Float, top: Float, right: Float, bottom: Float) -> Unit)? = null,
    showCard: Boolean = false,
    // Card rendering params (card rendered in pill window so buttons receive touches)
    answerText: String? = null,
    hasAnswer: Boolean = false,
    statusMessage: String? = null,
    cardAlpha: Float = 1f,
    onCopyAnswer: (() -> Unit)? = null,
    onCloseAnswer: (() -> Unit)? = null,
    onCloseStatus: (() -> Unit)? = null,
    recordingAnswers: List<Pair<Int, String>> = emptyList(),
    onCopyRecordingAnswer: ((String) -> Unit)? = null,
    isProcessingRecording: Boolean = false,
    recordingCaptureCount: Int = 0,
    recordingProcessedCount: Int = 0,
    overridePillX: Float = -1f,
    screenWidthPx: Float = 0f,
    windowScreenX: Float = 0f,
    lastQuickW: Float = 0f,
    onQuickWidthChanged: ((Float) -> Unit)? = null
) {
    val currentOnCaptureClick by rememberUpdatedState(onCaptureClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragging by rememberUpdatedState(onDragging)
    val currentOnPillPosition by rememberUpdatedState(onPillPositionChanged)
    val currentOnQuickWidthChanged by rememberUpdatedState(onQuickWidthChanged)
    val currentOnQuickAreaChanged by rememberUpdatedState(onQuickAreaChanged)
    val currentOnCardAreaChanged by rememberUpdatedState(onCardAreaChanged)
    val isDark = LocalIsDarkMode.current
    val t = if (isDark) DH else LH

    val density = LocalDensity.current

    var measuredPillW by remember { mutableFloatStateOf(0f) }
    var measuredPillH by remember { mutableFloatStateOf(0f) }
    var measuredQuickW by remember { mutableFloatStateOf(0f) }
    var measuredQuickH by remember { mutableFloatStateOf(0f) }

    var showQuickButtons by remember { mutableStateOf(false) }

    val quickActions = remember(visionEnabled, searchEnabled, reasoningEnabled, isRecording) {
        listOf(
            QuickAction(IcVision, "VLM", visionEnabled) { onVisionToggle?.invoke() },
            QuickAction(IcGlobe, "联网", searchEnabled) { onSearchToggle?.invoke() },
            QuickAction(IcBulb, "深度", reasoningEnabled) { onReasoningToggle?.invoke() },
            QuickAction(IcRecord, "录制", isRecording) { onRecordingToggle() }
        )
    }

    val quickScale by animateFloatAsState(
        if (showQuickButtons) 1f else 0f,
        FWAnim.expandSpring, label = "qts"
    )

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
        val scrW = if (screenWidthPx > 0f) screenWidthPx else sW

        val marginPxInit = with(density) { FWDims.pillEdgeMargin.toPx() }
        val pillHInit = with(density) { FWDims.pillHeight.toPx() }
        if (rightEdge < 0f) {
            rightEdge = scrW - marginPxInit
            dragX = rightEdge - with(density) { FWDims.quickBtnSize.toPx() }
            snapY = initialY.coerceIn(0f, (sH - pillHInit).coerceAtLeast(0f))
            dragY = snapY
        }
        val pillH = with(density) { FWDims.pillHeight.toPx() }
        val pillW = if (measuredPillW > 0f) measuredPillW else with(density) { FWDims.quickBtnSize.toPx() }
        val marginPx = with(density) { FWDims.pillEdgeMargin.toPx() }
        val gapPx = with(density) { FWDims.quickPanelGap.toPx() }
        val rightEdgeTarget = scrW - marginPx
        val leftEdgeTarget = marginPx
        val curIsLeftSide = rightEdge < scrW / 2f

        val clampedY = (if (isDragging || isAnimating) dragY else snapY)
            .coerceIn(0f, (sH - pillH).coerceAtLeast(0f))

        val snapX = rightEdge - pillW

        // 吸附动画：仅在拖拽结束时触发
        LaunchedEffect(isDragging, snapX, snapY) {
            if (!isDragging) {
                val leftSide = fingerX < scrW / 2f
                rightEdge = if (leftSide) leftEdgeTarget + pillW else rightEdgeTarget
                val targetX = rightEdge - pillW
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
                        currentOnSettled?.invoke(leftSide, dragX + measuredPillW / 2f)
                    }
                }
            }
        }

        // 拖拽边界 — 使用稳定的 sW
        val dragMaxX by rememberUpdatedState((scrW - marginPx).coerceAtLeast(marginPx + 1f))
        val dragCenterX by rememberUpdatedState(scrW / 2f)
        val dragMaxY by rememberUpdatedState((sH - pillH).coerceAtLeast(1f))
        val dragRightEdgeTarget by rememberUpdatedState(rightEdgeTarget)
        val dragLeftEdgeTarget by rememberUpdatedState(leftEdgeTarget)

        val dragMod = Modifier.pointerInput(pillW) {
            detectDragGestures(
                onDragStart = {
                    // 直接用 Compose 内部计算的位置，避免跨组件状态传递的不一致
                    fingerX = rightEdge - pillW
                    dragX = fingerX
                    dragY = dragY
                    currentOnDragStart?.invoke()
                    isDragging = true; isAnimating = true
                },
                onDragEnd = {
                    isDragging = false
                    val leftSide = fingerX < dragCenterX
                    rightEdge = if (leftSide) dragLeftEdgeTarget + pillW else dragRightEdgeTarget
                    snapY = dragY.coerceIn(0f, dragMaxY)
                    currentOnDragEnd.value?.invoke(leftSide)
                },
                onDragCancel = { isDragging = false },
                onDrag = { _: PointerInputChange, d: Offset ->
                    fingerX = (fingerX + d.x).coerceIn(marginPx, dragMaxX)
                    dragX = fingerX
                    rightEdge = if (fingerX < dragCenterX) dragLeftEdgeTarget + pillW else dragRightEdgeTarget
                    dragY = (dragY + d.y).coerceIn(0f, dragMaxY)
                    snapY = dragY
                    currentOnDragging?.invoke(fingerX, dragY)
                }
            )
        }

        val pillX = if (isDragging || isAnimating) dragX
                    else if (curIsLeftSide) marginPx
                    else rightEdge - pillW
        val pillPlacedX = pillX - windowScreenX

        // 位置报告 — 屏幕坐标
        LaunchedEffect(pillX, clampedY, measuredPillW, measuredPillH) {
            if (measuredPillW > 0f) {
                currentOnPillPosition?.invoke(pillX, clampedY, measuredPillW, measuredPillH)
            }
        }

        // 快捷/卡片区域报告 — 本地坐标（dispatchTouchEvent 使用）
        LaunchedEffect(showQuickButtons, pillPlacedX, clampedY, measuredPillW, measuredPillH, measuredQuickW) {
            if (showQuickButtons && measuredQuickW > 0f) {
                val pad = with(density) { 16.dp.toPx() }
                val left: Float
                val right: Float
                if (curIsLeftSide) {
                    left = pillPlacedX - pad
                    right = pillPlacedX + measuredPillW + gapPx + measuredQuickW + pad
                } else {
                    left = pillPlacedX - gapPx - measuredQuickW - pad
                    right = pillPlacedX + measuredPillW + pad
                }
                val top = clampedY - pad
                val bottom = clampedY + measuredPillH + pad
                currentOnQuickAreaChanged?.invoke(left, top, right, bottom)
            } else {
                currentOnQuickAreaChanged?.invoke(-1f, -1f, -1f, -1f)
            }
        }

        LaunchedEffect(showCard, pillPlacedX, clampedY, measuredPillW, measuredPillH, sW) {
            if (showCard && measuredPillW > 0f) {
                val pad = with(density) { 16.dp.toPx() }
                val cardW = sW * FWDims.cardWidthRatio
                val cardMaxH = with(density) { 260.dp.toPx() }
                val cardGap = with(density) { 8.dp.toPx() }
                val cardTop = clampedY + measuredPillH + cardGap
                val cardBottom = cardTop + cardMaxH
                val cardLeft = if (curIsLeftSide) pillPlacedX else (pillPlacedX + measuredPillW / 2f - cardW / 2f)
                val cardRight = cardLeft + cardW
                currentOnCardAreaChanged?.invoke(
                    (cardLeft - pad).coerceAtLeast(0f),
                    (cardTop - pad).coerceAtLeast(0f),
                    cardRight + pad,
                    cardBottom + pad
                )
            } else {
                currentOnCardAreaChanged?.invoke(-1f, -1f, -1f, -1f)
            }
        }

        val quickOrigin = if (curIsLeftSide) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)

        Box(Modifier.fillMaxSize()) {
            val maxPillW = with(density) { 120.dp }
            Box(
                Modifier
                    .widthIn(max = maxPillW)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val boxW = placeable.width
                        // 屏幕坐标 → 窗口本地坐标
                        val scrX = if (isDragging || isAnimating) {
                            dragX
                        } else if (curIsLeftSide) {
                            marginPx
                        } else {
                            rightEdge - boxW
                        }
                        val x = (scrX - windowScreenX).toInt()
                        val y = clampedY.toInt()
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
                        showQuickButtons = !showQuickButtons
                        currentOnLongPress()
                    },
                    onQuickToggle = {
                        showQuickButtons = false
                    }
                )
            }

            // Quick toggles (rendered in pill window alongside the pill)
            // quickScrX 是屏幕坐标(pillX为屏幕坐标)，但 Modifier.offset 需要窗口本地坐标
            val quickScrX = if (curIsLeftSide) pillX + measuredPillW + gapPx
                               else pillX - measuredQuickW - gapPx
            val quickOffsetX = quickScrX - windowScreenX  // 屏幕→窗口本地坐标
            val quickOffsetY = clampedY + (measuredPillH - measuredQuickH) / 2f
            com.hwb.aianswerer.utils.AppLog.d("Pill | QUICK_POS | pillX=${pillX.toInt()} qScrX=${quickScrX.toInt()} winSX=${windowScreenX.toInt()} localX=${quickOffsetX.toInt()} leftSide=$curIsLeftSide qW=${measuredQuickW.toInt()}")

            Box(
                Modifier
                    .wrapContentSize()
                    .offset { IntOffset(quickOffsetX.toInt(), quickOffsetY.toInt()) }
                    .onGloballyPositioned {
                        // 快捷按钮真实宽度 ~186dp(489px@2.625x)，60dp 小窗约束测量仅 157px
                        // 过滤掉明显被窗口约束的错误值
                        val minRealW = with(density) { 20.dp.toPx() }
                        val w = it.size.width.toFloat()
                        if (w > minRealW) {
                            measuredQuickW = w
                            measuredQuickH = it.size.height.toFloat()
                            currentOnQuickWidthChanged?.invoke(w)
                        }
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

            // Card rendered in pill window (so buttons receive touches)
            val cardW = scrW * FWDims.cardWidthRatio
            val maxCardX = (scrW - cardW).toInt().coerceAtLeast(0)
            val pillCenterX = pillPlacedX + measuredPillW / 2f
            val cardScrX = if (curIsLeftSide) pillPlacedX.toInt().coerceIn(0, maxCardX)
                           else (pillCenterX - cardW / 2f).toInt().coerceIn(0, maxCardX)
            val cardOffX = cardScrX.toInt()
            val cardOffY = (clampedY + measuredPillH + gapPx).toInt()
            val currentOnCloseAnswer by rememberUpdatedState(onCloseAnswer)
            val currentOnCloseStatus by rememberUpdatedState(onCloseStatus)
            val currentOnCopyAnswer by rememberUpdatedState(onCopyAnswer)

            AnimatedVisibility(
                visible = showCard && (hasAnswer || statusMessage != null),
                modifier = Modifier.offset { IntOffset(cardOffX, cardOffY) }.graphicsLayer { alpha = cardAlpha },
                enter = slideInVertically(FWAnim.cardEnterSlideSpring) { -it / 4 } + fadeIn(tween(200)) + scaleIn(FWAnim.cardEnterScaleSpring, initialScale = 0.9f),
                exit = slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
            ) {
                val cardDp = with(density) { cardW.toDp() }
                if (recordingAnswers.isNotEmpty() && !isRecording && !isProcessingRecording) {
                    Box(Modifier.width(cardDp)) {
                        RecordingResultCard(
                            t, recordingAnswers, currentOnCloseAnswer ?: {}, onCopyRecordingAnswer ?: {},
                            isProcessing = isRecording && isProcessingRecording,
                            processedCount = recordingProcessedCount,
                            totalCount = recordingAnswers.size
                        )
                    }
                } else {
                    Box(Modifier.width(cardDp)) {
                        Card(t, answerText, hasAnswer, statusMessage, floatingStatus, currentOnCopyAnswer ?: {}, currentOnCloseAnswer ?: {}, currentOnCloseStatus ?: {})
                    }
                }
            }
        }
    }
}
