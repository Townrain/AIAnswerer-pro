package com.hwb.aianswerer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

// ── Apple-style constants ──
val CardRadius = 20.dp
val InputRadius = 16.dp
val BtnRadius = 16.dp
val ChipRadius = 12.dp
val IconBtnRadius = 12.dp
val ToggleWidth = 51.dp  // Apple switch width
val ToggleHeight = 31.dp // Apple switch height
val TouchMin = 48.dp
val BackButtonSize = 36.dp  // Apple HIG minimum touch target
val KnobDiameter = 27.dp    // Premium toggle knob
val KnobShadowRadius = 15.5.dp  // = KnobDiameter/2 + 2.dp spread
val ButtonMinHeight = 52.dp    // Apple HIG: 52dp minimum tap target

// Apple-style spring specs — inline with proper types where used

// ── Card usage rules ──
//  GlassInfoCard: 状态指示、实时信息（运行状态、进度）
//  InfoCard: 静态配置、表单输入
//  HighlightCard: 导航入口（跳转到子页面）

// ═══════════════════════════════════════════════
//  Top Bars — Apple frosted glass
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithBack(
    title: String,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isDark = LocalIsDarkMode.current
    val contentColor = if (isDark) TextDarkPrimary else TextDark
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Box(
                    modifier = Modifier
                        .size(BackButtonSize)
                        .clip(RoundedCornerShape(IconBtnRadius))
                        .background(if (isDark) GlassDark else CardBorderLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LocalIcons.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_button),
                        tint = contentColor
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = contentColor
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithMenu(
    title: String,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkMode.current
    val contentColor = if (isDark) TextDarkPrimary else TextDark
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        },
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = LocalIcons.MoreVert,
                        contentDescription = stringResource(R.string.cd_menu_button),
                        tint = contentColor
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = Spacing.xxxl)
                ) {
                    PremiumDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) { menuContent() }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = contentColor
        )
    )
}

// ═══════════════════════════════════════════════
//  Section Label — Apple-style subtle label
// ═══════════════════════════════════════════════

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkMode.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isDark) TextDarkSecondary else TextTertiary,
        modifier = modifier.padding(start = Spacing.xs, bottom = Spacing.md)
    )
}

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
//  Info Card — Apple-style clean white card
// ═══════════════════════════════════════════════

@Composable
fun InfoCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val cardShape = RoundedCornerShape(CardRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val corner = CornerRadius(CardRadius.toPx())
                val elev = if (isDark) 12f * density else 4f * density
                val shadowAlpha = if (isDark) 0.20f else 0.06f
                onDrawBehind {
                    if (elev > 0f) drawGlassShadow(this, corner, elev, Color.Black.copy(alpha = shadowAlpha))
                }
            }
            .clip(cardShape)
            .drawBehind {
                val corner = CornerRadius(CardRadius.toPx())
                val bg = if (isDark) PremiumSurfaceDark else PremiumCardLight
                val border = if (isDark) PremiumSurfaceDarkBorder else CardBorderLight
                drawRoundRect(color = bg, cornerRadius = corner)
                drawRoundRect(color = border, cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
            }
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                }
            }
            content()
        }
    }
}

/** Glass variant — Apple frosted glass. */
@Composable
fun GlassInfoCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) Modifier.glassSurfaceDark(alpha = 0.07f, shadowElevation = 12f * density)
                else Modifier.glassSurface(alpha = 0.82f, shadowElevation = 4f * density)
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                }
            }
            content()
        }
    }
}

/** Elevated card for model config entry — subtle purple accent. */
@Composable
fun HighlightCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val corner = CornerRadius(CardRadius.toPx())
                val elev = if (isDark) 12f * density else 4f * density
                val shadowAlpha = if (isDark) 0.20f else 0.06f
                onDrawBehind {
                    if (elev > 0f) drawGlassShadow(this, corner, elev, Color.Black.copy(alpha = shadowAlpha))
                }
            }
            .clip(RoundedCornerShape(CardRadius))
            .drawBehind {
                val corner = CornerRadius(CardRadius.toPx())
                val bg = if (isDark) PremiumPrimary.copy(alpha = 0.12f) else PremiumPrimary.copy(alpha = 0.07f)
                val border = if (isDark) PremiumPrimary.copy(alpha = 0.18f) else PremiumPrimary.copy(alpha = 0.12f)
                drawRoundRect(color = bg, cornerRadius = corner)
                drawRoundRect(color = border, cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
            }
            .clickable { onClick() }
            .padding(Spacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadowButton(14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(AccentBronze, AccentGold))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LocalIcons.Search,
                        contentDescription = null,
                        tint = PremiumCardLight,
                        modifier = Modifier.size(Spacing.xxl)
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextDarkSecondary else TextTertiary
                    )
                }
            }
            Icon(
                imageVector = LocalIcons.ArrowBack,
                contentDescription = null,
                tint = PremiumPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
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
                    .heightIn(min = Spacing.xxl)
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
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
                        .heightIn(min = Spacing.xxl)
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

// ═══════════════════════════════════════════════
//  Info Items
// ═══════════════════════════════════════════════

@Composable
fun InfoItem(
    title: String,
    content: String,
    onClick: (() -> Unit)? = null
) {
    val isDark = LocalIsDarkMode.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = Spacing.sm)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) TextDarkSecondary else TextTertiary
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) TextDarkPrimary else TextDark
        )
    }
}

@Composable
fun FeatureItem(text: String, modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkMode.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .size(Spacing.sm)
                .clip(CircleShape)
                .background(PremiumPrimary)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) TextDarkPrimary else TextDark
        )
    }
}

@Composable
fun LibraryItem(name: String, description: String) {
    val isDark = LocalIsDarkMode.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) TextDarkPrimary else TextDark
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) TextDarkSecondary else TextTertiary
        )
    }
}

// ═══════════════════════════════════════════════
//  Premium Dialog — Apple-style glass dialog
// ═══════════════════════════════════════════════

@Composable
fun PremiumDialog(
    onDismiss: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismissAction: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val isDark = LocalIsDarkMode.current
    val density = LocalDensity.current.density

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumBgDark.copy(alpha = 0.50f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Card with enter animation
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .then(
                        if (isDark) Modifier
                            .clip(RoundedCornerShape(CardRadius))
                            .background(GlassDarkBorder)
                            .border(Spacing.xs / 8, GlassDarkBorder, RoundedCornerShape(CardRadius))
                        else Modifier.glassOverlay(shape = RoundedCornerShape(CardRadius))
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume click */ }
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                    if (message != null) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) TextDarkSecondary else TextSecondary
                        )
                    }
                    if (content != null) {
                        Spacer(Modifier.height(Spacing.md))
                        content()
                    }
                    Spacer(Modifier.height(Spacing.xl))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        if (dismissText != null) {
                            AnimatedButton(
                                text = dismissText,
                                onClick = { onDismissAction?.invoke(); onDismiss() },
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Glass
                            )
                        }
                        AnimatedButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = if (dismissText != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Primary
                        )
                    }
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

// ═══════════════════════════════════════════════
//  Premium Dropdown Menu — Apple-style dropdown
// ═══════════════════════════════════════════════

@Composable
fun PremiumDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkMode.current

    AnimatedVisibility(
        visible = expanded,
        enter = scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f)
        ) + fadeIn(animationSpec = tween(150)),
        exit = scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(80)
        ) + fadeOut(animationSpec = tween(80))
    ) {
        Box(
            modifier = modifier
                .shadowElevated(CardRadius)
                .then(
                    if (isDark) Modifier.glassSurfaceDark(alpha = 0.07f, shadowElevation = 0f)
                    else Modifier.glassOverlay()
                )
        ) {
            Column(
                modifier = Modifier
                    .width(Spacing.xxxl * 5) // 160dp
                    .padding(Spacing.xs)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PremiumDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    val isDark = LocalIsDarkMode.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
        label = "menu_item_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(ChipRadius))
            .clickable {
                pressed = true
                onClick()
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) TextDarkSecondary else TextSecondary,
                    modifier = Modifier.size(Spacing.lg)
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) TextDarkPrimary else TextDark
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}
