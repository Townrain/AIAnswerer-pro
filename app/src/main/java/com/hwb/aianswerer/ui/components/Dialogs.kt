package com.hwb.aianswerer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hwb.aianswerer.ui.theme.*

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
