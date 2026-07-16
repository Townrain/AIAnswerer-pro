package com.hwb.aianswerer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Light Colors ──
object Lc {
    val Bg1 = Color(0xFFC0AEE0); val Bg2 = Color(0xFFD8C8EC); val Bg3 = Color(0xFFECD8C0)
    val Primary = Color(0xFFD97757); val PrimaryEnd = Color(0xFFE8A850); val PrimaryDim = Color(0xFFC56A4A)
    val PrimaryContainer = Color(0xFFFBE8E0); val OnPrimaryContainer = Color(0xFF5C2D1A)
    val Success = Color(0xFF7DA38B)
    val OnBg = Color(0xFF3C2A1F); val OnBgVariant = Color(0xFF8B7E74)
    val GlassTop = Color(0x0DFFFFFF); val GlassBot = Color(0x08FFFFFF)
    val GlassBorder = Color(0xF4FFFFFF)
    val HdrTop = Color(0xB0FFF8F0); val HdrBot = Color(0x78FFF0E0)
    val Accent = Color(0xFFE8A850)
    val TrackOff = Color(0x28C49A6C); val Error = Color(0xFFD95757)
}

// ── Dark Colors ──
object Dc {
    val Bg1 = Color(0xFF0E0A12); val Bg2 = Color(0xFF1C141A); val Bg3 = Color(0xFF3A2A24)
    val Bg4 = Color(0xFF3A281A); val Bg5 = Color(0xFF5A3C14)
    val Primary = Color(0xFFD97757); val PrimaryEnd = Color(0xFFF0C878); val PrimaryDim = Color(0xFFC56A4A)
    val PrimaryContainer = Color(0xFF5C2D1A); val OnPrimaryContainer = Color(0xFFFBE8E0)
    val Success = Color(0xFF34C759)
    val OnBg = Color(0xFFF5F3F8); val OnBgVariant = Color(0xFFD0C8C0)
    val GlassTop = Color(0x30FFFFFF); val GlassBot = Color(0x22FFFFFF)
    val GlassBorder = Color(0x3DFFFFFF)
    val HdrTop = Color(0x38FFFFFF); val HdrBot = Color(0x2CFFFFFF)
    val Accent = Color(0xFFB87A24)
    val UiAccent = Color(0xFF9B8FF8); val UiAccentLight = Color(0xFFCBC4FF)
    val TrackOff = Color(0x1AD0C8C0); val Error = Color(0xFFFF6961)
    // 标题文字用的亮色版背景渐变（保留背景色调，提高亮度）
    val TitleBg1 = Color(0xFF604878); val TitleBg2 = Color(0xFF7A5A72); val TitleBg3 = Color(0xFF9A7A68)
    val TitleBg4 = Color(0xFFAA7E50); val TitleBg5 = Color(0xFFCC9A40)
}

// ── Typography ──
object DW {
    val DisplayLarge = TextStyle(fontSize = 36.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
    val HeadlineMedium = TextStyle(fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp)
    val TitleLarge = TextStyle(fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, lineHeight = 30.sp)
    val TitleMedium = TextStyle(fontSize = 18.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, lineHeight = 26.sp)
    val BodyLarge = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, lineHeight = 28.sp)
    val BodyMedium = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val BodySmall = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, lineHeight = 19.sp)
    val LabelLarge = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, lineHeight = 22.sp)
    val LabelMedium = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
    val LabelSmall = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, lineHeight = 15.sp, letterSpacing = 0.5.sp)
}

// ── Theme Data ──
data class Th(
    val bg1: Color, val bg2: Color, val bg3: Color, val bg4: Color, val bg5: Color,
    val p: Color, val pe: Color, val pd: Color, val pc: Color, val opc: Color,
    val ok: Color,
    val ob: Color, val osv: Color,
    val gt: Color, val gb: Color, val gdp: Color,
    val ht: Color, val hdp: Color,
    val ac: Color, val ua: Color, val ual: Color, val to: Color, val err: Color, val w: Color,
    val isLight: Boolean
)

val LH by lazy {
    Th(Lc.Bg1, Lc.Bg2, Lc.Bg3, Lc.Bg2, Lc.Bg1,
        Lc.Primary, Lc.PrimaryEnd, Lc.PrimaryDim, Lc.PrimaryContainer, Lc.OnPrimaryContainer,
        Lc.Success, Lc.OnBg, Lc.OnBgVariant,
        Lc.GlassTop, Lc.GlassBot, Lc.GlassBorder,
        Lc.HdrTop, Lc.HdrBot,
        Lc.Accent, Lc.Accent, Lc.Accent,
        Lc.TrackOff, Lc.Error, Color.White, true)
}

val DH by lazy {
    Th(Dc.Bg1, Dc.Bg2, Dc.Bg3, Dc.Bg4, Dc.Bg5,
        Dc.UiAccent, Dc.UiAccentLight, Dc.PrimaryDim, Dc.PrimaryContainer, Dc.OnPrimaryContainer,
        Dc.Success, Dc.OnBg, Dc.OnBgVariant,
        Dc.GlassTop, Dc.GlassBot, Dc.GlassBorder,
        Dc.HdrTop, Dc.HdrBot,
        Dc.Accent, Dc.UiAccent, Dc.UiAccentLight,
        Dc.TrackOff, Dc.Error, Color.White, false)
}

// ── Constants ──
val BtnR = 32.dp; val CardR = 24.dp; val ChipR = 20.dp; val CardPad = 24.dp

// ── Theme Accessor ──
@Composable
fun sandboxTheme(): Th {
    return ThemeManager.getCurrentTheme()
}

// ── Preview Helper ──
@Composable
fun Themed(t: Th = LH, content: @Composable (Th) -> Unit) {
    androidx.compose.material3.MaterialTheme { content(t) }
}

// ── Glass Surface ──
@Composable
fun Glass(modifier: Modifier, t: Th, p: Dp = CardPad, r: Dp = CardR, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(r)).border(1.dp, t.gb, RoundedCornerShape(r))
        .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(r))
        .padding(p)) {
        androidx.compose.foundation.layout.Column { content() }
    }
}
