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
    onToggleExpanded: (Boolean) -> Unit,
    // 折叠功能：收起态标题文本 = "答案:" + 第 1 题纯答案摘要（≤7 字符，超长省略号截断）
    summaryText: String? = null
) {
    val t = sandboxTheme()
    val showCard = showAnswer || statusMessage != null
    // 与 D 窗同构：滚动容器按内容自然高度测量（不受父约束/窗口高度影响），
    // 避免 Box 被窗口高度撑满到上限导致窗口无法收缩（透明区拦截触摸）
    val scrollState = rememberScrollState()

    Box(
        Modifier
            .width(FWDims.cardWidthDp)
            .heightIn(max = FWDims.cardCompactMaxHeight)
            .verticalScroll(scrollState)
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
                // Answer-ready notification：仅一行"答案:摘要"标题，不显示正文
                hasAnswer && showAnswer -> {
                    Card(
                        t = t,
                        answerText = null,
                        titleText = summaryText,
                        hasAnswer = true,
                        statusMessage = null,
                        status = floatingStatus,
                        onCopy = {},
                        onCloseAnswer = onCloseAnswer,
                        onCloseStatus = onCloseStatus,
                        // 折叠功能：C 窗显示展开按钮（→ D 窗完整答案）
                        showExpandBtn = true,
                        onExpand = { onToggleExpanded(!isExpanded) }
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
    cardAlpha: Float = 1.0f,
    // 折叠功能：收起回到紧凑 C 窗
    onCollapse: (() -> Unit)? = null
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
                totalCount = displayAnswers.size,
                onCollapse = onCollapse
            )
        }
    }
}

