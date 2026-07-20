package com.hwb.aianswerer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.ui.theme.Th

/**
 * 悬浮窗答案卡片区。
 * 从 [FloatingWindowContent] 提取，处理三种显示模式：
 * 1. 翻页卡片（paginatedAnswers / recordingAnswers）
 * 2. 录制进度卡片
 * 3. 单题答案卡片
 */
@Composable
internal fun FloatingAnswerCard(
    t: Th,
    showCard: Boolean,
    showAnswer: Boolean,
    hasAnswer: Boolean,
    statusMessage: String?,
    floatingStatus: FloatingStatus,
    cardAlpha: Float,
    cardOffX: Int,
    cardOffY: Int,
    cardW: Float,
    density: Density,
    answerText: String?,
    paginatedAnswers: List<Pair<Int, String>>,
    recordingAnswers: List<Pair<Int, String>>,
    isRecording: Boolean,
    isProcessingRecording: Boolean,
    recordingCaptureCount: Int,
    onCloseAnswer: () -> Unit,
    onCloseStatus: () -> Unit,
    onCopyAnswer: (() -> Unit)?,
    onCopyRecordingAnswer: ((String) -> Unit)?,
    onMeasuredCardH: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = showCard && (hasAnswer || statusMessage != null),
        modifier = Modifier
            .offset { IntOffset(cardOffX, cardOffY) }
            .graphicsLayer { alpha = cardAlpha },
        enter = slideInVertically(FWAnim.cardEnterSlideSpring) { -it / 4 } +
                fadeIn(tween(200)) +
                scaleIn(FWAnim.cardEnterScaleSpring, initialScale = 0.9f),
        exit = slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
    ) {
        val cardDp = with(density) { cardW.toDp() }
        val displayAnswers = if (paginatedAnswers.isNotEmpty()) paginatedAnswers
            else if (recordingAnswers.isNotEmpty() && !isRecording && !isProcessingRecording) recordingAnswers
            else emptyList()

        if (displayAnswers.isNotEmpty()) {
            Box(Modifier.width(cardDp).onGloballyPositioned { coords ->
                onMeasuredCardH(coords.size.height.toFloat())
            }) {
                RecordingResultCard(
                    t, displayAnswers, onCloseAnswer,
                    onCopyAnswer = onCopyRecordingAnswer ?: {},
                    isProcessing = false,
                    processedCount = displayAnswers.size,
                    totalCount = displayAnswers.size
                )
            }
        } else if (recordingCaptureCount > 0 && !showAnswer && statusMessage != null &&
            (isRecording || isProcessingRecording)
        ) {
            Box(Modifier.width(cardDp).onGloballyPositioned { coords ->
                onMeasuredCardH(coords.size.height.toFloat())
            }) {
                Card(t, null, false, statusMessage, floatingStatus, {}, onCloseStatus, onCloseStatus)
            }
        } else {
            Box(Modifier.width(cardDp).onGloballyPositioned { coords ->
                onMeasuredCardH(coords.size.height.toFloat())
            }) {
                Card(t, answerText, hasAnswer, statusMessage, floatingStatus,
                    onCopyAnswer ?: {}, onCloseAnswer, onCloseStatus)
            }
        }
    }
}
