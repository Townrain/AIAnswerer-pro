package com.hwb.aianswerer.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// 数据类
// =============================================================================

data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

// =============================================================================
// 状态枚举
// =============================================================================

enum class FloatingStatus {
    Idle, Capturing, Recognizing, Searching, GettingAnswer, Success, Error
}

// =============================================================================
// 设计常量
// =============================================================================

internal object FWDims {
    val pillHeight = 36.dp
    val pillCornerRadius = 21.dp
    val pillIconSize = 20.dp
    val pillHPadding = 10.dp
    val pillVPadding = 8.dp
    val pillEdgeMargin = 8.dp

    val cardWidthRatio = 0.88f
    val cardCornerRadius = 20.dp
    val cardMaxHeight = 460.dp
    val cardSectionSpacing = 10.dp
    val cardItemSpacing = 6.dp
    val cardPaddingH = 14.dp
    val cardPaddingV = 12.dp

    val quickBtnSize = 40.dp
    val quickBtnIconSize = 18.dp
    val quickBtnSpacing = 6.dp
    val quickPanelGap = 8.dp
    val quickPanelHPadding = 4.dp
    val quickPanelVPadding = 4.dp

    val progressStroke = 3.dp
    val progressInset = 1.dp
}

internal object FWAnim {
    val bouncyScale = 0.88f
    val bouncyScaleSpring = spring<Float>(0.7f, 300f)
    val expandSpring = spring<Float>(0.8f, 1200f)
    val collapseSpring = spring<Float>(0.8f, 1200f)
    val snapSpringX = spring<Float>(0.82f, 900f)
    val snapSpringY = spring<Float>(0.85f, 500f)
    val fadeInSpec = tween<Float>(300)
    val fadeOutSpec = tween<Float>(200)
    const val shimmerDurationMs = 1200
    const val successBounceDurationMs = 250
    const val longPressDurationMs = 1000L
    const val staggerDelayMs = 30
    const val progressDrainMs = 200
    const val copyResetMs = 1500L
    const val pillTransitionMs = 200
}

// =============================================================================
// 图标定义
// =============================================================================

val IcVision: ImageVector by lazy {
    ImageVector.Builder("IcVision", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFF22C55E))) {
            moveTo(12f, 4.5f)
            curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            close()
            moveTo(12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
            close()
            moveTo(12f, 9f)
            curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
            close()
        }
    }.build()
}

val IcGlobe: ImageVector by lazy {
    ImageVector.Builder("IcGlobe", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFF3B82F6))) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(11f, 19.93f)
            curveTo(7.05f, 19.44f, 4f, 16.08f, 4f, 12f)
            curveTo(4f, 11.38f, 4.08f, 10.79f, 4.21f, 10.21f)
            lineTo(9f, 15f)
            verticalLineTo(16f)
            curveTo(9f, 17.1f, 9.9f, 18f, 11f, 18f)
            verticalLineTo(19.93f)
            close()
            moveTo(17.9f, 17.39f)
            curveTo(17.64f, 16.58f, 16.9f, 16f, 16f, 16f)
            horizontalLineTo(15f)
            verticalLineTo(13f)
            curveTo(15f, 12.45f, 14.55f, 12f, 14f, 12f)
            horizontalLineTo(8f)
            verticalLineTo(10f)
            horizontalLineTo(10f)
            curveTo(10.55f, 10f, 11f, 9.55f, 11f, 9f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            curveTo(13.55f, 7f, 14f, 6.55f, 14f, 6f)
            verticalLineTo(5.5f)
            curveTo(17.94f, 7.16f, 20f, 10.95f, 20f, 12f)
            curveTo(20f, 14.08f, 19.2f, 15.97f, 17.9f, 17.39f)
            close()
        }
    }.build()
}

val IcBulb: ImageVector by lazy {
    ImageVector.Builder("IcBulb", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFFF59E0B))) {
            moveTo(9f, 21f)
            curveTo(9f, 21.55f, 9.45f, 22f, 10f, 22f)
            horizontalLineTo(14f)
            curveTo(14.55f, 22f, 15f, 21.55f, 15f, 21f)
            verticalLineTo(20f)
            horizontalLineTo(9f)
            verticalLineTo(21f)
            close()
            moveTo(12f, 2f)
            curveTo(8.14f, 2f, 5f, 5.14f, 5f, 9f)
            curveTo(5f, 11.38f, 6.19f, 13.47f, 8f, 14.74f)
            verticalLineTo(17f)
            curveTo(8f, 17.55f, 8.45f, 18f, 9f, 18f)
            horizontalLineTo(15f)
            curveTo(15.55f, 18f, 16f, 17.55f, 16f, 17f)
            verticalLineTo(14.74f)
            curveTo(17.81f, 13.47f, 19f, 11.38f, 19f, 9f)
            curveTo(19f, 5.14f, 15.86f, 2f, 12f, 2f)
            close()
        }
    }.build()
}

val IcRecord: ImageVector by lazy {
    ImageVector.Builder("IcRecord", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFFEF4444))) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
            close()
        }
    }.build()
}
