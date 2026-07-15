package com.hwb.aianswerer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.ui.theme.*

// ── Settings icon (gear) ──
private val SettingsIcon: ImageVector by lazy {
    ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19.14f, 12.94f); curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
            curveToRelative(0f, -0.33f, -0.02f, -0.64f, -0.06f, -0.94f); lineToRelative(2.02f, -1.58f)
            curveToRelative(0.18f, -0.14f, 0.23f, -0.38f, 0.12f, -0.56f); lineToRelative(-1.89f, -3.28f)
            curveToRelative(-0.12f, -0.19f, -0.36f, -0.26f, -0.56f, -0.18f); lineToRelative(-2.38f, 0.96f)
            curveToRelative(-0.5f, -0.38f, -1.06f, -0.68f, -1.66f, -0.88f); lineToRelative(-0.3f, 2.52f)
            curveToRelative(-0.04f, -0.2f, -0.2f, -0.34f, -0.4f, -0.34f); horizontalLineToRelative(-3.78f)
            curveToRelative(-0.2f, 0f, -0.36f, 0.14f, -0.4f, 0.34f); lineToRelative(-0.3f, 2.52f)
            curveToRelative(-0.6f, 0.2f, -1.16f, 0.5f, -1.66f, 0.88f); lineToRelative(-2.38f, -0.96f)
            curveToRelative(-0.2f, -0.08f, -0.44f, -0.01f, -0.56f, 0.18f); lineToRelative(-1.89f, 3.28f)
            curveToRelative(-0.12f, 0.19f, -0.07f, 0.42f, 0.12f, 0.56f); lineToRelative(2.02f, 1.58f)
            curveToRelative(-0.04f, 0.3f, -0.06f, 0.61f, -0.06f, 0.94f); curveToRelative(0f, 0.33f, 0.02f, 0.64f, 0.06f, 0.94f)
            lineToRelative(-2.02f, 1.58f); curveToRelative(-0.18f, 0.14f, -0.23f, 0.38f, -0.12f, 0.56f)
            lineToRelative(1.89f, 3.28f); curveToRelative(0.12f, 0.19f, 0.36f, 0.26f, 0.56f, 0.18f)
            lineToRelative(2.38f, -0.96f); curveToRelative(0.5f, 0.38f, 1.06f, 0.68f, 1.66f, 0.88f)
            lineToRelative(0.3f, 2.52f); curveToRelative(0.04f, 0.2f, 0.2f, 0.34f, 0.4f, 0.34f)
            horizontalLineToRelative(3.78f); curveToRelative(0.2f, 0f, 0.36f, -0.14f, 0.4f, -0.34f)
            lineToRelative(0.3f, -2.52f); curveToRelative(0.6f, -0.2f, 1.16f, -0.5f, 1.66f, -0.88f)
            lineToRelative(2.38f, 0.96f); curveToRelative(0.2f, 0.08f, 0.44f, 0.01f, 0.56f, -0.18f)
            lineToRelative(1.89f, -3.28f); curveToRelative(0.12f, -0.19f, 0.07f, -0.42f, -0.12f, -0.56f)
            lineToRelative(-2.02f, -1.58f); close(); moveTo(12f, 15f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f); close()
        }
    }.build()
}

@Composable
fun TitleSection(t: Th, onSettingsClick: () -> Unit) {
    val titleBrush = if (t.isLight) Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite)
        else Brush.linearGradient(listOf(Dc.TitleBg1, Dc.TitleBg2, Dc.TitleBg3, Dc.TitleBg4, Dc.TitleBg5), start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
    Row(
        Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 12.dp, start = 28.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f)) {
            Text("AI", style = DW.DisplayLarge.copy(brush = titleBrush, fontSize = 56.sp, lineHeight = 62.sp), modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(10.dp))
            Text("Answer", style = DW.HeadlineMedium.copy(brush = titleBrush, fontSize = 34.sp, lineHeight = 40.sp), modifier = Modifier.alignByBaseline())
        }
        Box(
            Modifier.size(56.dp).bouncyClick(onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(SettingsIcon, "设置", tint = t.osv, modifier = Modifier.size(32.dp))
        }
    }
}
