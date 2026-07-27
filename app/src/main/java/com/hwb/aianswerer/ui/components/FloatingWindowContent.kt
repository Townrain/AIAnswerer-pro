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

// =============================================================================
// Window A — Pill button (always visible, draggable)
// =============================================================================

/**
 * Window A content: the primary always-visible pill button.
 * Drag gestures are handled internally and reported via [onMove]/[onDragEnd] so
 * the service can reposition the underlying WindowManager window.
 */
@Composable
fun WindowAContent(
    buttonSize: Int,
    buttonAlpha: Float,
    floatingStatus: FloatingStatus,
    isRecording: Boolean,
    isImageCollecting: Boolean,
    isLeftSide: Boolean,
    isDragging: Boolean,
    onCaptureClick: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onMeasuredSize: (Float, Float) -> Unit
) {
    val t = sandboxTheme()
    var dragging by remember { mutableStateOf(false) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentIsLeftSide by rememberUpdatedState(isLeftSide)

    // Drag gesture — coexists with PillButton's tap/long-press via Bouncy
    val dragMod = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { dragging = true },
            onDrag = { change, dragAmount ->
                change.consume()
                currentOnMove(dragAmount.x, dragAmount.y)
            },
            onDragEnd = {
                dragging = false
                currentOnDragEnd(currentIsLeftSide)
            },
            onDragCancel = { dragging = false }
        )
    }

    val effectiveDragging = isDragging || dragging

    Box(
        Modifier
            .wrapContentSize()
            .onGloballyPositioned { coords ->
                onMeasuredSize(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
    ) {
        PillButton(
            t = t,
            status = floatingStatus,
            expandQuickButtons = false, // Window B managed separately
            isRecording = isRecording,
            isImageCollecting = isImageCollecting,
            buttonAlpha = buttonAlpha,
            isLeftSide = isLeftSide,
            isDragging = effectiveDragging,
            dragMod = dragMod,
            onCaptureClick = onCaptureClick,
            onLongPress = onLongPress,
            onQuickToggle = {} // Window B toggled by service
        )
    }
}

// =============================================================================
// Window B — Quick-toggle panel
// =============================================================================

/**
 * Window B content: the quick-toggle action panel.
 * Positioned adjacent to Window A by the service; reports its measured size
 * so the service can update the WindowManager layout.
 */
@Composable
fun WindowBContent(
    t: Th,
    actions: List<QuickAction>,
    scale: Float,
    isLeftSide: Boolean,
    transformOrigin: TransformOrigin,
    onMeasuredSize: (Float, Float) -> Unit
) {
    Box(
        Modifier
            .wrapContentSize()
            .onGloballyPositioned { coords ->
                onMeasuredSize(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
    ) {
        QuickToggles(
            t = t,
            actions = actions,
            scale = scale,
            isLeftSide = isLeftSide,
            transformOrigin = transformOrigin
        )
    }
}

// =============================================================================
// Window C — Answer/Status card (compact)
// =============================================================================

/**
 * Window C content: compact answer/status card.
 *
 * Shows a status message when [statusMessage] is set, or a minimal "Answer
 * ready" header when [hasAnswer] is true. The full answer detail is rendered
 * in Window D, which the service creates when [onToggleExpanded] is called
 * with `true`.
 */
@Composable
fun WindowCContent(
    showAnswer: Boolean,
    hasAnswer: Boolean,
    statusMessage: String?,
    floatingStatus: FloatingStatus,
    cardAlpha: Float,
    recordingCaptureCount: Int,
    isRecording: Boolean,
    isProcessingRecording: Boolean,
    onCloseAnswer: () -> Unit,
    onCloseStatus: () -> Unit,
    onMeasuredHeight: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit
) {
    val t = sandboxTheme()
    val showCard = showAnswer || statusMessage != null

    Box(
        Modifier
            .width(FWDims.cardWidthDp)
            .graphicsLayer { alpha = cardAlpha }
            .onGloballyPositioned { coords ->
                onMeasuredHeight(coords.size.height.toFloat())
            }
    ) {
        if (showCard) {
            when {
                // Status message with recording progress
                recordingCaptureCount > 0 && !showAnswer && statusMessage != null &&
                    (isRecording || isProcessingRecording) -> {
                    Card(
                        t = t,
                        answerText = null,
                        hasAnswer = false,
                        statusMessage = statusMessage,
                        status = floatingStatus,
                        onCopy = {},
                        onCloseAnswer = onCloseAnswer,
                        onCloseStatus = onCloseStatus
                    )
                }
                // Answer-ready notification
                hasAnswer && showAnswer -> {
                    Card(
                        t = t,
                        answerText = null,
                        hasAnswer = true,
                        statusMessage = null,
                        status = floatingStatus,
                        onCopy = {},
                        onCloseAnswer = onCloseAnswer,
                        onCloseStatus = onCloseStatus
                    )
                }
                // Status message
                statusMessage != null -> {
                    Card(
                        t = t,
                        answerText = null,
                        hasAnswer = false,
                        statusMessage = statusMessage,
                        status = floatingStatus,
                        onCopy = {},
                        onCloseAnswer = onCloseAnswer,
                        onCloseStatus = onCloseStatus
                    )
                }
            }
        }
    }
}

// =============================================================================
// Window D — Answer detail (expanded content)
// =============================================================================

/**
 * Window D content: full answer detail for paginated/recording answers.
 *
 * Rendered as a [RecordingResultCard] that fills the window width.
 * The service manages this window's position below Window C.
 */
@Composable
fun WindowDContent(
    hasAnswer: Boolean,
    paginatedAnswers: List<Pair<Int, String>>,
    recordingAnswers: List<Pair<Int, String>>,
    isRecording: Boolean,
    isProcessingRecording: Boolean,
    onCopyRecordingAnswer: (String) -> Unit,
    onCloseAnswer: () -> Unit,
    onMeasuredHeight: (Float) -> Unit,
    cardAlpha: Float = 1.0f
) {
    val t = sandboxTheme()
    val scrollState = rememberScrollState()

    val displayAnswers = when {
        paginatedAnswers.isNotEmpty() -> paginatedAnswers
        recordingAnswers.isNotEmpty() && !isRecording && !isProcessingRecording -> recordingAnswers
        else -> emptyList()
    }

    Box(
        Modifier
            .width(FWDims.cardWidthDp)
            .heightIn(max = FWDims.cardMaxHeight)
            .verticalScroll(scrollState)
            .graphicsLayer { alpha = cardAlpha }
            .onGloballyPositioned { coords ->
                onMeasuredHeight(coords.size.height.toFloat())
            }
    ) {
        if (displayAnswers.isNotEmpty()) {
            RecordingResultCard(
                t = t,
                answers = displayAnswers,
                onClose = onCloseAnswer,
                onCopyAnswer = onCopyRecordingAnswer,
                isProcessing = false,
                processedCount = displayAnswers.size,
                totalCount = displayAnswers.size
            )
        }
    }
}

// =============================================================================

// =============================================================================
// 主 Composable
// =============================================================================

@Composable
fun FloatingWindowContent(
    answerText: String?, showAnswer: Boolean, statusMessage: String?,
    buttonSize: Int = 40, buttonAlpha: Float = 1.0f, cardAlpha: Float = 1.0f,
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
    imageEnabled: Boolean = false,
    onImageToggle: (() -> Unit)? = null,
    isRecording: Boolean = false,
    isProcessingRecording: Boolean = false,
    isImageCollecting: Boolean = false,
    imageCollectCount: Int = 0,
    isProcessingImages: Boolean = false,
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
    var measuredCardH by remember { mutableFloatStateOf(0f) }

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

    val vlmLabel = stringResource(R.string.float_quick_vlm)
    val searchLabel = stringResource(R.string.float_quick_search)
    val reasoningLabel = stringResource(R.string.float_quick_reasoning)
    val recordLabel = stringResource(R.string.float_quick_record)
    val imageLabel = stringResource(R.string.float_quick_image)
    val quickActions = remember(visionEnabled, searchEnabled, reasoningEnabled, isRecording, imageEnabled, vlmLabel, searchLabel, reasoningLabel, recordLabel, imageLabel) {
        listOf(
            QuickAction(IcVision, vlmLabel, visionEnabled) { onVisionToggle?.invoke() },
            QuickAction(IcGlobe, searchLabel, searchEnabled) { onSearchToggle?.invoke() },
            QuickAction(IcBulb, reasoningLabel, reasoningEnabled) { onReasoningToggle?.invoke() },
            QuickAction(IcRecord, recordLabel, isRecording) { onRecordingToggle() },
            QuickAction(IcImage, imageLabel, imageEnabled) { onImageToggle?.invoke() },
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
            rightEdge = sW  // 贴右边
            dragX = rightEdge - with(density) { FWDims.quickBtnSize.toPx() }
            snapY = initialY.coerceIn(0f, (sH - pillHInit).coerceAtLeast(0f))
            dragY = snapY
        }
        val pillH = with(density) { buttonSize.dp.toPx() }
        val pillW = if (measuredPillW > 0f) measuredPillW else with(density) { FWDims.quickBtnSize.toPx() }
        val marginPx = with(density) { FWDims.pillEdgeMargin.toPx() }
        val rightEdgeTarget = sW                       // 右贴边
        val leftEdgeTarget = 0f                       // 左贴边
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
        val dragMod = Modifier.pointerInput(Unit) {  // Unit键确保手势检测器不会因pillW变化而重启
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
                    val curPillW = if (measuredPillW > 0f) measuredPillW else pillW
                    fingerX = (fingerX + d.x).coerceIn(0f, (sW - curPillW).coerceAtLeast(0f))
                    dragX = fingerX
                    val centerX = sW / 2f
                    rightEdge = if (fingerX < centerX) leftEdgeTarget + curPillW else rightEdgeTarget
                    snapY = dragY
                    currentOnMove?.invoke(d.x, d.y)
                }
            )
        }

        val quickOrigin = if (curIsLeftSide) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)

        val pillPlacedX = if (externalPillX >= 0f) externalPillX
                          else if (isDragging || isAnimating) dragX
                          else if (curIsLeftSide) 0f
                          else sW - measuredPillW
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
                                0
                            } else {
                                (sW - boxW).roundToInt()
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
                        isImageCollecting = isImageCollecting,
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
                            else if (curIsLeftSide) 0f
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

        FloatingAnswerCard(
            t = t,
            showCard = showCard,
            showAnswer = showAnswer,
            hasAnswer = hasAnswer,
            statusMessage = statusMessage,
            floatingStatus = floatingStatus,
            cardAlpha = cardAlpha,
            cardOffX = cardOffXClamped,
            cardOffY = cardOffY,
            cardW = cardW,
            density = density,
            answerText = answerText,
            paginatedAnswers = paginatedAnswers,
            recordingAnswers = recordingAnswers,
            isRecording = isRecording,
            isProcessingRecording = isProcessingRecording,
            recordingCaptureCount = recordingCaptureCount,
            onCloseAnswer = onCloseAnswer,
            onCloseStatus = onCloseStatus,
            onCopyAnswer = onCopyAnswer,
            onCopyRecordingAnswer = onCopyRecordingAnswer,
            onMeasuredCardH = { h -> measuredCardH = h }
        )
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
                        showQuickButtons, showCard, cardOffXClamped, cardOffY, cardW, hasAnswer, statusMessage, measuredCardH) {
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
                val cardH = if (measuredCardH > 0f) measuredCardH else with(density) { FWDims.cardMaxHeight.toPx() }
                left = minOf(left, cardOffXClamped.toFloat() - pad)
                right = maxOf(right, cardOffXClamped + cardW + pad)
                bottom = maxOf(bottom, cardOffY + cardH + pad)
            }
            currentOnInteractiveArea?.invoke(left, top, right, bottom)
        }
    }
}
