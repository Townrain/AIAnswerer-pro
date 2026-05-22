package com.hwb.aianswerer.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** CompositionLocal for Premium Fusion effective dark mode. Respects ThemeState override. */
val LocalIsDarkMode = staticCompositionLocalOf { false }

/**
 * 深色模式发光轮廓 Indication — 点击时显示柔和紫色光晕边框。
 * 使用 Animatable 实现平滑的按下/释放过渡动画。
 */
private class GlowOutlineIndication(
    private val glowColor: Color,
    private val cornerRadiusPx: Float
) : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        launch {
                            alpha.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        launch {
                            alpha.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f))
                        }
                    }
                }
            }
        }

        return remember {
            object : IndicationInstance {
                override fun ContentDrawScope.drawIndication() {
                    drawContent()
                    val a = alpha.value
                    if (a > 0f) {
                        // 内层光晕边框
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.25f * a),
                            cornerRadius = CornerRadius(cornerRadiusPx),
                            style = Stroke(2.5f)
                        )
                        // 外层扩散光晕
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.10f * a),
                            cornerRadius = CornerRadius(cornerRadiusPx + 6f),
                            topLeft = Offset(-3f, -3f),
                            size = androidx.compose.ui.geometry.Size(size.width + 6f, size.height + 6f),
                            style = Stroke(4f)
                        )
                    }
                }
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer, onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary, onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer, onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError, onError = LightOnError,
    errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer,
    background = LightBackground, onBackground = LightOnBackground,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline, outlineVariant = LightOutlineVariant,
    scrim = LightScrim, inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface, inversePrimary = LightInversePrimary,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary, onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer, onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary, onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer, onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError, onError = DarkOnError,
    errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground, onBackground = DarkOnBackground,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline, outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim, inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface, inversePrimary = DarkInversePrimary,
)

@Composable
fun AIAnswererTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (ThemeState.darkMode) {
        1 -> false; 2 -> true; else -> darkTheme
    }
    val context = LocalContext.current
    DisposableEffect(isDark) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        onDispose {}
    }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> DarkColors
        else -> LightColors
    }
    val density = LocalContext.current.resources.displayMetrics.density
    val indication: Indication = if (isDark) {
        GlowOutlineIndication(
            glowColor = Color(0xFFB8B0F8),
            cornerRadiusPx = 12f * density
        )
    } else {
        rememberRipple(
            color = Color(0xFF6C5CE7).copy(alpha = 0.08f),
            bounded = true
        )
    }
    CompositionLocalProvider(
        LocalIsDarkMode provides isDark,
        LocalIndication provides indication
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
