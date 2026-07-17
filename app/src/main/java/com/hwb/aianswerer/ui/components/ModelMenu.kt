package com.hwb.aianswerer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.hwb.aianswerer.ui.theme.*

private val C400: TweenSpec<Color> = tween(400, easing = FastOutSlowInEasing)
@Composable
internal fun ModelMenu(label: String, value: MutableState<String>, options: List<String>, expanded: State<Boolean>, t: Th, modifier: Modifier, hint: String? = null, onToggle: () -> Unit) {
    val chevronRot by animateFloatAsState(if (expanded.value) 180f else 0f, spring(dampingRatio = 0.55f, stiffness = 400f), label = "cr")
    val btnBg by animateColorAsState(
        if (expanded.value) t.gb.copy(alpha = if (t.isLight) 0.82f else 0.22f)
        else t.gb.copy(alpha = if (t.isLight) 0.65f else 0.08f),
        C400, label = "bbg"
    )
    val btnBorder by animateColorAsState(
        if (expanded.value) t.gb.copy(alpha = if (t.isLight) 0.92f else 0.5f)
        else t.ac.copy(alpha = if (t.isLight) 0.55f else 0.08f),
        C400, label = "bbr"
    )

    // Track button position in window for popup placement
    var btnBounds by remember { mutableStateOf(Rect.Zero) }

    // Popup lifecycle: stays alive during close animation
    var showPopup by remember { mutableStateOf(false) }
    val dropAnim = remember { Animatable(0f) }
    LaunchedEffect(expanded.value) {
        if (expanded.value) {
            showPopup = true
            dropAnim.snapTo(0f)
            dropAnim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 350f))
        } else if (showPopup) {
            dropAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
            showPopup = false
        }
    }

    // Solid frosted glass — fully opaque, nothing shows through
    val dropBg = Brush.verticalGradient(
        listOf(
            if (t.isLight) Color(0xFFFDF8F0) else Color(0xFF3A2E3E),
            if (t.isLight) Color(0xFFF6EEE4) else Color(0xFF302636)
        ),
        endY = Float.POSITIVE_INFINITY
    )
    val dropBorder = Brush.verticalGradient(
        listOf(
            if (t.isLight) Color(0xFFFFFFFF) else Color(0x66FFFFFF),
            if (t.isLight) Color(0xFFC8BEB4) else Color(0x30FFFFFF)
        ),
        endY = Float.POSITIVE_INFINITY
    )

    Box(modifier = modifier) {
        // Button — same visual, added onGloballyPositioned for window coords
        Box(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { btnBounds = it.boundsInWindow() }
                .clip(RoundedCornerShape(16.dp)).background(btnBg)
                .border(1.dp, btnBorder, RoundedCornerShape(16.dp))
                .bouncyClick { onToggle() }.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = DW.LabelSmall.copy(color = t.osv))
                    Spacer(Modifier.height(1.dp))
                    Text(value.value, style = DW.BodyMedium.copy(color = t.ob))
                }
                Text("▾", style = DW.LabelMedium.copy(color = t.osv), modifier = Modifier.graphicsLayer { rotationZ = chevronRot })
            }
        }

        // Dropdown via Popup — independent window, immune to parent zIndex issues
        if (showPopup && btnBounds.width > 0f) {
            val density = LocalDensity.current
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset = IntOffset(btnBounds.left.toInt(), btnBounds.bottom.toInt())
                },
                onDismissRequest = onToggle,
                properties = PopupProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Box(
                    Modifier.width(with(density) { btnBounds.width.toDp() })
                        .graphicsLayer {
                            alpha = dropAnim.value
                            scaleY = dropAnim.value
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(dropBg).border(1.dp, dropBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            Modifier.padding(vertical = 4.dp)
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (hint != null) {
                                Text(hint, style = DW.LabelSmall.copy(color = t.osv, fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            options.forEach { opt ->
                                val sel = opt == value.value
                                val interactionSource = remember(opt) { MutableInteractionSource() }
                                val isHovered by interactionSource.collectIsHoveredAsState()
                                val isPressed by interactionSource.collectIsPressedAsState()

                                val itemScale by animateFloatAsState(
                                    when {
                                        isPressed -> 0.92f
                                        isHovered && !sel -> 1.03f
                                        else -> 1f
                                    },
                                    spring(dampingRatio = 0.2f, stiffness = 400f),
                                    label = "itemSc"
                                )
                                val itemBg by animateColorAsState(
                                    when {
                                        sel -> t.p.copy(alpha = 0.14f)
                                        isPressed -> t.p.copy(alpha = 0.08f)
                                        isHovered -> if (t.isLight) t.gb.copy(alpha = 0.45f) else t.ac.copy(alpha = 0.10f)
                                        else -> Color.Transparent
                                    },
                                    tween(250),
                                    label = "itemBg"
                                )
                                val itemFg by animateColorAsState(
                                    when {
                                        sel -> t.p
                                        isHovered -> if (t.isLight) t.p.copy(alpha = 0.8f) else t.ual.copy(alpha = 0.9f)
                                        else -> t.ob
                                    },
                                    tween(250),
                                    label = "itemFg"
                                )

                                Text(opt,
                                    style = (if (sel) DW.LabelMedium else DW.BodySmall).copy(color = itemFg),
                                    modifier = Modifier.fillMaxWidth()
                                        .scale(itemScale)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBg)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = { value.value = opt; onToggle() }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
