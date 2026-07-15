package com.hwb.aianswerer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// QuickToggles — expandable row of floating quick-action buttons
// =============================================================================

@Composable
internal fun QuickToggles(
    t: com.hwb.aianswerer.ui.theme.Th,
    actions: List<QuickAction>,
    scale: Float,
    isLeftSide: Boolean,
    transformOrigin: TransformOrigin
) {
    val itemAnimatables = remember(actions.size) { List(actions.size) { Animatable(0f) } }
    val isExpanding = scale > 0.5f

    LaunchedEffect(isExpanding) {
        if (isExpanding) {
            val order = if (isLeftSide) itemAnimatables.indices else itemAnimatables.indices.reversed()
            order.forEachIndexed { i, index ->
                if (i > 0) delay(FWAnim.staggerDelayMs.toLong())
                launch { itemAnimatables[index].animateTo(1f, FWAnim.expandSpring) }
            }
        } else {
            val order = if (isLeftSide) itemAnimatables.indices.reversed() else itemAnimatables.indices
            order.forEachIndexed { i, index ->
                if (i > 0) delay(FWAnim.staggerDelayMs.toLong())
                launch { itemAnimatables[index].animateTo(0f, FWAnim.collapseSpring) }
            }
        }
    }

    val orderedActions = if (isLeftSide) actions else actions.asReversed()

    val slideOffset by animateFloatAsState(
        if (isExpanding) 0f else if (isLeftSide) -20f else 20f,
        spring(0.85f, 1000f), label = "so"
    )

    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale; alpha = scale
                this.transformOrigin = transformOrigin
                translationX = slideOffset
            }
            .padding(
                start = if (isLeftSide) FWDims.quickPanelGap else 0.dp,
                end = if (isLeftSide) 0.dp else FWDims.quickPanelGap
            ),
        horizontalArrangement = Arrangement.spacedBy(FWDims.quickBtnSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        orderedActions.forEachIndexed { index, action ->
            val s = if (index < itemAnimatables.size) itemAnimatables[index].value else 0f
            QuickBtn(t, action, s)
        }
    }
}

// =============================================================================
// QuickBtn — individual circular quick-action toggle button
// =============================================================================

@Composable
private fun QuickBtn(t: com.hwb.aianswerer.ui.theme.Th, action: QuickAction, itemScale: Float) {
    val s by animateFloatAsState(
        if (action.enabled) 1.08f else 1f,
        FWAnim.expandSpring, label = "q"
    )

    val elasticScale = remember { Animatable(0f) }
    LaunchedEffect(itemScale) {
        if (itemScale > 0.5f && elasticScale.value < 0.5f) {
            elasticScale.snapTo(0.8f)
            elasticScale.animateTo(1.05f, spring(0.7f, 1200f))
            elasticScale.animateTo(1f, spring(0.85f, 1000f))
        } else if (itemScale < 0.5f) {
            elasticScale.snapTo(0f)
        }
    }

    Bouncy(onClick = action.onClick) {
        Box(
            Modifier
                .size(FWDims.quickBtnSize)
                .graphicsLayer { scaleX = s * itemScale * elasticScale.value; scaleY = s * itemScale * elasticScale.value }
                .clip(CircleShape)
                .background(
                    if (action.enabled) Brush.linearGradient(
                        listOf(Color(0xFF2D2B55), Color(0xFF4C4889)),
                        Offset.Zero, Offset.Infinite
                    )
                    else if (t.isLight) SolidColor(t.ac.copy(alpha = 0.08f))
                    else SolidColor(Color.White.copy(alpha = 0.12f))
                )
                .then(
                    if (action.enabled) Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    else if (t.isLight) Modifier.border(0.5.dp, t.ac.copy(alpha = 0.12f), CircleShape)
                    else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                action.icon, action.label,
                tint = if (action.enabled) Color.White else if (t.isLight) t.osv else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(FWDims.quickBtnIconSize)
            )
        }
    }
}
