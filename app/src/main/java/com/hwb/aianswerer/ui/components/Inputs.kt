package com.hwb.aianswerer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

// ═══════════════════════════════════════════════
//  Premium Toggle — Apple-style 51x31dp switch
// ═══════════════════════════════════════════════

@Composable
fun PremiumToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current
    val offColor = if (isDark) Color.White.copy(alpha = 0.12f) else ToggleOff
    val bgColor by animateColorAsState(
        targetValue = if (checked) PremiumPrimary else offColor,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
        label = "toggle_bg"
    )
    val knobOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = 500f),
        label = "toggle_knob"
    )
    val alpha = if (enabled) 1f else 0.4f
    val knobSize = KnobDiameter
    val margin = Spacing.xs
    val trackWidth = ToggleWidth - knobSize - margin * 2

    Box(
        modifier = Modifier
            .width(ToggleWidth)
            .height(ToggleHeight)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(bgColor)
            .toggleable(
                value = checked,
                onValueChange = { onCheckedChange(!checked) },
                enabled = enabled
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Q-bouncy knob with squash & stretch
        Box(
            modifier = Modifier
                .offset(x = margin + trackWidth * knobOffset)
                .size(knobSize)
                .graphicsLayer {
                    // Squash & stretch during transition
                    scaleX = 1f + 0.15f * knobOffset * (1f - knobOffset) * 4f
                    scaleY = 1f - 0.20f * knobOffset * (1f - knobOffset) * 4f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                .shadowSubtle(KnobShadowRadius)
                .clip(CircleShape)
                .background(SurfaceKnob)
        )
    }
}

// ═══════════════════════════════════════════════
//  Setting Item — clean Apple-style row
// ═══════════════════════════════════════════════

@Composable
fun SettingItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> if (isDark) TextDarkTertiary else TextTertiary
                    isDark -> TextDarkPrimary
                    else -> TextDark
                }
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary
                )
            }
        }
        Spacer(Modifier.width(Spacing.lg))
        PremiumToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ═══════════════════════════════════════════════
//  Premium Chip — Apple segmented control feel
// ═══════════════════════════════════════════════

@Composable
fun PremiumChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) PremiumPrimary.copy(alpha = 0.20f) else ChipSelected
        } else {
            if (isDark) Color.White.copy(alpha = 0.06f) else ChipUnselected
        },
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 400f),
        label = "chip_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) PremiumPrimary else (if (isDark) TextDarkSecondary else TextSecondary),
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 400f),
        label = "chip_text"
    )
    val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
        label = "chip_scale"
    )
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
                this.alpha = if (enabled) 1f else 0.4f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .clip(RoundedCornerShape(ChipRadius))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = fontWeight,
            color = textColor
        )
    }
}

// ═══════════════════════════════════════════════
//  Text Fields — Apple-style clean inputs
// ═══════════════════════════════════════════════

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val textColor = if (isDark) TextDarkPrimary else TextDark
    val placeholderColor = if (isDark) TextDarkSecondary else TextTertiary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchMin)
            .clip(RoundedCornerShape(InputRadius))
            .background(if (isDark) GlassDark else InputBackground)
            .then(if (isDark) Modifier.border(0.5.dp, GlassDarkBorder, RoundedCornerShape(InputRadius)) else Modifier.border(0.5.dp, InputBorder, RoundedCornerShape(InputRadius)))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) TextDarkTertiary else TextTertiary,
                maxLines = 1
            )
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) Spacing.lg else Spacing.xxl)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = placeholderColor,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = singleLine,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    cursorBrush = SolidColor(PremiumPrimary)
                )
            }
        }
    }
}

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    var passwordVisible by remember { mutableStateOf(false) }
    val textColor = if (isDark) TextDarkPrimary else TextDark
    val placeholderColor = if (isDark) TextDarkSecondary else TextTertiary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchMin)
            .clip(RoundedCornerShape(InputRadius))
            .background(if (isDark) GlassDark else InputBackground)
            .then(if (isDark) Modifier.border(0.5.dp, GlassDarkBorder, RoundedCornerShape(InputRadius)) else Modifier.border(0.5.dp, InputBorder, RoundedCornerShape(InputRadius)))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) TextDarkTertiary else TextTertiary,
                maxLines = 1
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Spacing.lg)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = placeholderColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = singleLine,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                        cursorBrush = SolidColor(PremiumPrimary),
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation()
                    )
                }
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) LocalIcons.Visibility else LocalIcons.VisibilityOff,
                        contentDescription = if (passwordVisible)
                            stringResource(R.string.cd_hide_password)
                        else stringResource(R.string.cd_show_password),
                        tint = if (isDark) TextDarkTertiary else TextTertiary,
                        modifier = Modifier.size(Spacing.lg)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  Premium Slider — Apple-style custom slider
// ═══════════════════════════════════════════════

@Composable
fun PremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    label: String? = null,
    valueFormatter: ((Float) -> String)? = null,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current

    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 24.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
        label = "slider_thumb"
    )

    val trackHeight = Spacing.xs
    val trackColor = if (isDark) GlassDarkBorder else InputBorder
    val activeColor = PremiumPrimary

    fun updateFromPosition(x: Float, width: Float) {
        val newFraction = (x / width).coerceIn(0f, 1f)
        val range = valueRange.endInclusive - valueRange.start
        var newValue = valueRange.start + newFraction * range
        if (steps > 0) {
            val stepSize = range / (steps + 1)
            newValue = Math.round(newValue / stepSize) * stepSize
        }
        onValueChange(newValue.coerceIn(valueRange))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary
                )
                Text(
                    text = valueFormatter?.invoke(value) ?: value.toInt().toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchMin)
                .pointerInput(valueRange, steps) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            updateFromPosition(offset.x, size.width.toFloat())
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            updateFromPosition(change.position.x, size.width.toFloat())
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxWidth()) {
                val trackWidth = size.width
                val y = size.height / 2
                val corner = CornerRadius(trackHeight.toPx() / 2)
                val thumbPx = thumbSize.toPx()

                // Inactive track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(thumbPx / 2, y - trackHeight.toPx() / 2),
                    size = androidx.compose.ui.geometry.Size(
                        trackWidth - thumbPx,
                        trackHeight.toPx()
                    ),
                    cornerRadius = corner
                )
                // Active track
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(thumbPx / 2, y - trackHeight.toPx() / 2),
                    size = androidx.compose.ui.geometry.Size(
                        (trackWidth - thumbPx) * fraction,
                        trackHeight.toPx()
                    ),
                    cornerRadius = corner
                )
                // Thumb shadow
                drawCircle(
                    color = SurfaceKnobShadow,
                    radius = thumbPx / 2,
                    center = Offset(thumbPx / 2 + (trackWidth - thumbPx) * fraction, y + 1.dp.toPx())
                )
                // Thumb
                drawCircle(
                    color = SurfaceKnob,
                    radius = thumbPx / 2,
                    center = Offset(thumbPx / 2 + (trackWidth - thumbPx) * fraction, y)
                )
                // Thumb border
                drawCircle(
                    color = PremiumPrimary.copy(alpha = 0.15f),
                    radius = thumbPx / 2,
                    center = Offset(thumbPx / 2 + (trackWidth - thumbPx) * fraction, y),
                    style = Stroke(1.dp.toPx())
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  Premium Radio Option — Apple-style radio
// ═══════════════════════════════════════════════

@Composable
fun PremiumRadioOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current

    val indicatorColor by animateColorAsState(
        targetValue = if (selected) PremiumPrimary else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
        label = "radio_color"
    )
    val borderColor = if (selected) PremiumPrimary else {
        if (isDark) GlassDarkBorder else InputBorder
    }
    val textColor = when {
        !enabled -> if (isDark) TextDarkTertiary else TextTertiary
        isDark -> TextDarkPrimary
        else -> TextDark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchMin)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
        Box(
            modifier = Modifier
                .size(Spacing.xxl)
                .border(2.dp, borderColor, CircleShape)
                .clip(CircleShape)
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(Spacing.sm)
                        .clip(CircleShape)
                        .background(SurfaceKnob)
                )
            }
        }
    }
}
