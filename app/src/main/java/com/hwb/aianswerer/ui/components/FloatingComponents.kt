package com.hwb.aianswerer.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset

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

sealed class FloatingStatus {
    data object Idle : FloatingStatus()
    data object Capturing : FloatingStatus()
    data object Recognizing : FloatingStatus()
    data object Searching : FloatingStatus()
    data object GettingAnswer : FloatingStatus()
    data object Success : FloatingStatus()
    data object Error : FloatingStatus()
}

// =============================================================================
// 设计常量
// =============================================================================

internal object FWDims {
    val pillHeight get() = com.hwb.aianswerer.config.AppConfig.getFloatButtonSize().dp
    val pillCornerRadius = 21.dp
    val pillIconSize get() = (com.hwb.aianswerer.config.AppConfig.getFloatButtonSize() * 20 / 36 * com.hwb.aianswerer.config.AppConfig.getFloatIconScale()).dp
    val pillHPadding get() = (com.hwb.aianswerer.config.AppConfig.getFloatButtonSize() * 10 / 36).dp
    val pillVPadding get() = (com.hwb.aianswerer.config.AppConfig.getFloatButtonSize() * 8 / 36).dp
    val pillEdgeMargin = 8.dp

    val cardWidthRatio = 0.88f
    val cardCornerRadius = 20.dp
    val cardMaxHeight = 560.dp
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
    val idleHeightPaddingDp = 16.dp
}

internal object FWAnim {
    val bouncyScale = 0.88f
    val bouncyScaleSpring = spring<Float>(0.7f, 300f)
    val expandSpring = spring<Float>(0.8f, 1200f)
    val collapseSpring = spring<Float>(0.8f, 1200f)
    val snapSpringX = spring<Float>(0.82f, 900f)
    val snapSpringY = spring<Float>(0.85f, 500f)
    val cardEnterSlideSpring = spring<IntOffset>(0.8f, 300f)
    val cardEnterScaleSpring = spring<Float>(0.8f, 300f)
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

internal val IcVision: ImageVector get() = com.hwb.aianswerer.ui.icons.LocalIcons.Vision

internal val IcGlobe: ImageVector by lazy {
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

internal val IcBulb: ImageVector by lazy {
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

internal val IcRecord: ImageVector by lazy {
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

/** 图片图标（相册），用于: 图片功能快捷开关 */
internal val IcImage: ImageVector by lazy {
    ImageVector.Builder("IcImage", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFFA855F7))) {
            moveTo(21f, 19f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            close()
            moveTo(8.5f, 13.5f)
            lineToRelative(2.5f, 3.01f)
            lineTo(14.5f, 12f)
            lineToRelative(4.5f, 6f)
            horizontalLineTo(5f)
            lineToRelative(3.5f, -4.5f)
            close()
        }
    }.build()
}
