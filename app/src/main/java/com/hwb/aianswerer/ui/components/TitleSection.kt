package com.hwb.aianswerer.ui.components

import com.hwb.aianswerer.R
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.ui.theme.*

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
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "设置",
                tint = t.osv,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
