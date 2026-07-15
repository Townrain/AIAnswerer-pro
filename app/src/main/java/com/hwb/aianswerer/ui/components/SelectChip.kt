package com.hwb.aianswerer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.ui.theme.*

@Composable
fun SelectChip(label: String, selected: Boolean, t: Th, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) (if (t.isLight) t.p.copy(alpha = 0.40f) else t.ua.copy(alpha = 0.4f))
        else t.gb.copy(alpha = if (t.isLight) 0.65f else 0.06f),
        tween(250, easing = FastOutSlowInEasing), label = "scBg"
    )
    val fg by animateColorAsState(
        if (selected) (if (t.isLight) t.p else t.ual) else t.osv,
        tween(250, easing = FastOutSlowInEasing), label = "scFg"
    )
    val border by animateColorAsState(
        if (selected) (if (t.isLight) t.p.copy(alpha = 0.7f) else t.ua.copy(alpha = 0.65f))
        else t.ac.copy(alpha = if (t.isLight) 0.48f else 0.08f),
        tween(250, easing = FastOutSlowInEasing), label = "scBord"
    )
    val selScale by animateFloatAsState(
        if (selected) 1.03f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "scSel"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed) 0.88f else 1f,
        spring(dampingRatio = 0.2f, stiffness = 500f),
        label = "scPress"
    )

    Box(
        Modifier.fillMaxWidth().scale(selScale * pressScale).clip(RoundedCornerShape(ChipR)).background(bg)
            .border(1.dp, border, RoundedCornerShape(ChipR))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = DW.LabelLarge.copy(color = fg, textAlign = TextAlign.Center))
    }
}
