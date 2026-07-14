package com.hwb.aianswerer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
