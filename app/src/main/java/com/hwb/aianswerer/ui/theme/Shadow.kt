package com.hwb.aianswerer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple-inspired shadow system.
 * Uses native canvas shadow with matching corner radius to avoid
 * double-border artifacts in dark mode.
 */

// Shared Paint instance — avoids per-frame allocation
private val sharedShadowPaint = Paint().apply {
    color = Color.Transparent
    isAntiAlias = true
}

internal fun DrawScope.drawNativeShadow(
    cornerRadius: CornerRadius,
    elevationPx: Float,
    shadowColor: Color
) {
    if (elevationPx <= 0f) return
    drawIntoCanvas { canvas ->
        sharedShadowPaint.asFrameworkPaint().apply {
            setShadowLayer(elevationPx, 0f, elevationPx * 0.5f, shadowColor.toArgb())
        }
        canvas.drawRoundRect(
            left = 0f, top = 0f,
            right = size.width, bottom = size.height,
            radiusX = cornerRadius.x, radiusY = cornerRadius.y,
            paint = sharedShadowPaint
        )
    }
}

/** Subtle lift — setting rows, quiet surfaces. */
fun Modifier.shadowSubtle(cornerRadius: Dp = 0.dp): Modifier =
    this.drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        val elevation = 1.dp.toPx()
        val color = Color.Black.copy(alpha = ShadowSubtleAlpha)
        onDrawBehind {
            drawNativeShadow(corner, elevation, color)
        }
    }

/** Standard card — info panels, settings groups. */
fun Modifier.shadowCard(cornerRadius: Dp = 0.dp): Modifier =
    this.drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        val elevation = 4.dp.toPx()
        val color = Color.Black.copy(alpha = ShadowCardAlpha)
        onDrawBehind {
            drawNativeShadow(corner, elevation, color)
        }
    }

/** Elevated — dialogs, dropdowns, floating elements. */
fun Modifier.shadowElevated(cornerRadius: Dp = 0.dp): Modifier =
    this.drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        val elevation = 8.dp.toPx()
        val color = Color.Black.copy(alpha = ShadowElevatedAlpha)
        onDrawBehind {
            drawNativeShadow(corner, elevation, color)
        }
    }

/** Floating — answer cards, top-level overlays. */
fun Modifier.shadowFloating(cornerRadius: Dp = 0.dp): Modifier =
    this.drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        val elevation = 12.dp.toPx()
        val color = Color.Black.copy(alpha = ShadowFloatingAlpha)
        onDrawBehind {
            drawNativeShadow(corner, elevation, color)
        }
    }

/** Dark theme floating — deeper shadow for dark backgrounds. */
fun Modifier.shadowFloatingDark(cornerRadius: Dp = 0.dp): Modifier =
    this.drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        val elevation = 12.dp.toPx()
        val color = Color.Black.copy(alpha = ShadowFloatingDarkAlpha)
        onDrawBehind {
            drawNativeShadow(corner, elevation, color)
        }
    }

/** Primary button — subtle purple glow. */
fun Modifier.shadowButton(cornerRadius: Dp = 0.dp): Modifier =
    this.drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        val elevation = 6.dp.toPx()
        val color = PremiumPrimary.copy(alpha = ShadowButtonAlpha)
        onDrawBehind {
            drawNativeShadow(corner, elevation, color)
        }
    }
