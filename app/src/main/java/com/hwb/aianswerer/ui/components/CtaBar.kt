package com.hwb.aianswerer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.theme.*

// ── Gradient helper ──
private fun g(t: Th) = Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite)

@Composable
fun CtaBar(
    t: Th, m: Modifier,
    onStartClick: () -> Unit,
    isAnswerModeActive: Boolean,
    onStopClick: () -> Unit,
    hasModel: Boolean = true
) {
    val disabled = !hasModel && !isAnswerModeActive
    val inf = rememberInfiniteTransition(label = "cta")
    val pulse by inf.animateFloat(1f, 1.03f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bounceScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.2f, stiffness = 400f),
        label = "ctaBounce"
    )

    val bg = when {
        disabled -> Brush.linearGradient(
            listOf(Color(0xFF9E9E9E), Color(0xFF757575)),
            Offset.Zero, Offset.Infinite
        )
        isAnswerModeActive -> Brush.linearGradient(
            listOf(Color(0xFFFF3B30), Color(0xFFD32F2F)),
            Offset.Zero, Offset.Infinite
        )
        else -> g(t)
    }

    val label = when {
        isAnswerModeActive -> stringResource(R.string.button_stop_mode)
        disabled -> stringResource(R.string.button_start_mode_disabled)
        else -> stringResource(R.string.button_start_mode)
    }

    Surface(
        m.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    scaleX = if (disabled) 1f else pulse * bounceScale
                    scaleY = if (disabled) 1f else pulse * bounceScale
                }
                .clip(RoundedCornerShape(32.dp))
                .background(bg)
                .then(
                    if (disabled) Modifier
                    else Modifier.clickable(interactionSource = interactionSource, indication = null) {
                        if (isAnswerModeActive) onStopClick() else onStartClick()
                    }
                )
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = DW.LabelLarge.copy(color = Color.White)
            )
        }
    }
}
