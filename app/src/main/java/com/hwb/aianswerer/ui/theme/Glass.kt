package com.hwb.aianswerer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple-inspired frosted glass effects.
 *
 * Shadow is drawn in a drawWithCache BEFORE .clip() so it extends
 * beyond the clipped shape. Background/border are drawn in drawBehind
 * AFTER .clip() so they respect the shape boundary.
 */

private val DefaultGlassRadius = 20.dp
private val DefaultGradientRadius = 14.dp

private val sharedGlassShadowPaint = Paint().apply {
    color = Color.Transparent
    isAntiAlias = true
}

internal fun drawGlassShadow(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    cornerRadius: CornerRadius,
    elevation: Float,
    shadowColor: Color
) {
    if (elevation <= 0f) return
    drawScope.drawIntoCanvas { canvas ->
        sharedGlassShadowPaint.asFrameworkPaint().apply {
            setShadowLayer(elevation, 0f, elevation * 0.5f, shadowColor.toArgb())
        }
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = drawScope.size.width,
            bottom = drawScope.size.height,
            radiusX = cornerRadius.x,
            radiusY = cornerRadius.y,
            paint = sharedGlassShadowPaint
        )
    }
}

/** Light glass surface — Apple-style frosted white. */
fun Modifier.glassSurface(
    alpha: Float = GlassWhite.alpha,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGlassRadius),
    cornerRadius: Dp = DefaultGlassRadius,
    shadowElevation: Float = 0f
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) {
                drawGlassShadow(this, corner, shadowElevation, Color.Black.copy(alpha = ShadowSubtleAlpha))
            }
        }
    }
    .clip(shape)
    .drawBehind {
        val corner = CornerRadius(cornerRadius.toPx())
        drawRoundRect(color = Color.White.copy(alpha = alpha), cornerRadius = corner)
        drawRoundRect(color = GlassWhiteBorder, cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
    }

/** Dark glass surface — Apple dark mode subtle glass. */
fun Modifier.glassSurfaceDark(
    alpha: Float = GlassDark.alpha,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGlassRadius),
    cornerRadius: Dp = DefaultGlassRadius,
    shadowElevation: Float = 0f
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) {
                drawGlassShadow(this, corner, shadowElevation, Color.Black.copy(alpha = 0.20f))
            }
        }
    }
    .clip(shape)
    .drawBehind {
        val corner = CornerRadius(cornerRadius.toPx())
        drawRoundRect(color = Color.White.copy(alpha = alpha), cornerRadius = corner)
        drawRoundRect(color = GlassDarkBorder, cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
    }

/** Stronger glass for overlay cards. */
fun Modifier.glassOverlay(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGlassRadius),
    cornerRadius: Dp = DefaultGlassRadius
): Modifier = this
    .clip(shape)
    .drawBehind {
        val corner = CornerRadius(cornerRadius.toPx())
        drawRoundRect(color = GlassWhiteStrong, cornerRadius = corner)
        drawRoundRect(color = GlassWhiteBorder, cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
    }

/** Dark accent gradient — Apple-style charcoal to deep charcoal. */
fun Modifier.darkAccentGradient(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGradientRadius),
    cornerRadius: Dp = DefaultGradientRadius,
    shadowElevation: Float = 0f,
    shadowColor: Color = ButtonShadowColor
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) drawGlassShadow(this, corner, shadowElevation, shadowColor)
        }
    }
    .clip(shape)
    .drawBehind {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(DarkAccent, DarkAccentGradientEnd),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }

/** Primary gradient — muted purple. */
fun Modifier.primaryGradient(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGradientRadius),
    cornerRadius: Dp = DefaultGradientRadius,
    shadowElevation: Float = 0f,
    shadowColor: Color = Color.Black.copy(alpha = 0.15f)
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) drawGlassShadow(this, corner, shadowElevation, shadowColor)
        }
    }
    .clip(shape)
    .drawBehind {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(PremiumPrimary, PremiumPrimaryVariant),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }

/** Success gradient. */
fun Modifier.successGradient(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGradientRadius),
    cornerRadius: Dp = DefaultGradientRadius,
    shadowElevation: Float = 0f,
    shadowColor: Color = Color.Black.copy(alpha = 0.15f)
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) drawGlassShadow(this, corner, shadowElevation, shadowColor)
        }
    }
    .clip(shape)
    .drawBehind {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(SuccessGreen, SuccessGreenLight),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }

/** Primary Glow gradient — luminous purple, brighter than primaryGradient. */
fun Modifier.primaryGlowGradient(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGradientRadius),
    cornerRadius: Dp = DefaultGradientRadius,
    shadowElevation: Float = 0f,
    shadowColor: Color = PremiumPrimary.copy(alpha = 0.25f)
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) drawGlassShadow(this, corner, shadowElevation, shadowColor)
        }
    }
    .clip(shape)
    .drawBehind {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(PrimaryGlow, PrimaryGlowEnd),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }

/** Recording gradient — red. */
fun Modifier.recordingGradient(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGradientRadius),
    cornerRadius: Dp = DefaultGradientRadius,
    shadowElevation: Float = 0f,
    shadowColor: Color = Color.Black.copy(alpha = 0.15f)
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) drawGlassShadow(this, corner, shadowElevation, shadowColor)
        }
    }
    .clip(shape)
    .drawBehind {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(RecordingRed, RecordingRedDark),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }

/** Error gradient. */
fun Modifier.errorGradient(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(DefaultGradientRadius),
    cornerRadius: Dp = DefaultGradientRadius,
    shadowElevation: Float = 0f,
    shadowColor: Color = Color.Black.copy(alpha = 0.15f)
): Modifier = this
    .drawWithCache {
        val corner = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            if (shadowElevation > 0f) drawGlassShadow(this, corner, shadowElevation, shadowColor)
        }
    }
    .clip(shape)
    .drawBehind {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(ErrorRed, ErrorRedLight),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }
