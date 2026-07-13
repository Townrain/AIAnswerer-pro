package com.hwb.aianswerer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.ui.theme.*

// ═══════════════════════════════════════════════
//  Animated Button — Q-bouncy press with depth
// ═══════════════════════════════════════════════

@Composable
fun AnimatedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    // Detect press-down (not click-up) for immediate bounce feedback
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Cancel -> pressed = false
                is PressInteraction.Release -> { /* auto-release handles bounce-back */ }
            }
        }
    }

    // Scale: squash on press, Q-bouncy spring back
    val animScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
        label = "btn_scale"
    )
    // Shadow elevation: lifts on idle, sinks on press (Tonal buttons have no shadow)
    val elevation by animateFloatAsState(
        targetValue = when {
            variant == ButtonVariant.Tonal -> 0f
            pressed -> 2f
            else -> 8f
        },
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 400f),
        label = "btn_elevation"
    )
    // Subtle Y translation: button physically moves down
    val translationY by animateFloatAsState(
        targetValue = if (pressed) 2f else 0f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
        label = "btn_translate"
    )

    val shape = RoundedCornerShape(BtnRadius)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shadowPx = with(density) { elevation.dp.toPx() }
    val shadowColor = ButtonShadowColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonMinHeight)
            .then(
                when (variant) {
                    ButtonVariant.Primary -> Modifier.darkAccentGradient(shape, BtnRadius, shadowElevation = shadowPx, shadowColor = shadowColor)
                    ButtonVariant.Glass -> if (isDark)
                        Modifier.glassSurfaceDark(shape = shape, cornerRadius = BtnRadius, shadowElevation = shadowPx)
                    else
                        Modifier.glassSurface(shape = shape, cornerRadius = BtnRadius, shadowElevation = shadowPx)
                    ButtonVariant.Tonal -> Modifier
                        .background(
                            if (isDark) PremiumPrimary.copy(alpha = 0.15f)
                            else PremiumPrimary.copy(alpha = 0.08f),
                            shape
                        )
                }
            )
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
                this.translationY = translationY
                this.alpha = if (enabled) 1f else 0.4f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (variant) {
                ButtonVariant.Primary -> LightOnPrimary
                ButtonVariant.Glass -> if (isDark) TextDarkPrimary else TextDark
                ButtonVariant.Tonal -> PremiumPrimary
            }
        )
    }

    // Auto-release for visible bounce-back
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}

enum class ButtonVariant { Primary, Glass, Tonal }
