package com.hwb.aianswerer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

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
