package com.hwb.aianswerer.ui.pages

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.BuildConfig
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.*

// =============================================================================
// Previews
// =============================================================================
@Preview(showSystemUi = true, showBackground = true, name = "关于 — Light")
@Composable private fun AboutLightPreview() { Themed { AboutPage(it, {}) } }

@Preview(showSystemUi = true, showBackground = true, name = "关于 — Dark")
@Composable private fun AboutDarkPreview() { Themed(DH) { AboutPage(it, {}) } }

// =============================================================================
// About Page
// =============================================================================
@Composable
fun AboutPage(t: Th, onBack: () -> Unit) {
    val bgGradient = Brush.linearGradient(
        listOf(t.bg1, t.bg2, t.bg3, t.bg4, t.bg5),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(Modifier.fillMaxSize().background(bgGradient)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Top bar
            val backInteraction = remember { MutableInteractionSource() }
            val backPressed by backInteraction.collectIsPressedAsState()
            val backScale = remember { Animatable(1f) }
            LaunchedEffect(backPressed) {
                if (backPressed) backScale.snapTo(0.85f)
                else backScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 28.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).scale(backScale.value)
                        .clickable(interactionSource = backInteraction, indication = null) { onBack() },
                    contentAlignment = Alignment.Center
                ) { Icon(LocalIcons.ArrowBack, "返回", tint = t.ob, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(4.dp))
                Text("关于", style = DW.TitleLarge.copy(color = t.ob))
            }

            Spacer(Modifier.height(40.dp))

            // App icon
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(t.p, t.pe), Offset.Zero, Offset.Infinite)),
                    contentAlignment = Alignment.Center) {
                    Text("AI", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("AI Answerer", style = DW.TitleLarge.copy(color = t.ob, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(36.dp))

            // 应用简介
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                Text("应用简介", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 8.dp))
                Text("AI Answerer 是一款 Android 端智能答题助手，支持截图识别题目、多模型并行作答、联网搜索增强。通过悬浮窗实现一键截屏与答案展示，覆盖单选、多选、填空、问答等题型。",
                    style = DW.BodySmall.copy(color = t.ob, lineHeight = 20.sp))
            }

            // 版本信息
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                Text("版本信息", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 8.dp))
                AboutRow(t, "版本号", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Sep(t)
                AboutRow(t, "构建工具", "Gradle 8.9 · AGP 8.7.3")
                Sep(t)
                AboutRow(t, "Kotlin", "2.0.21")
                Sep(t)
                AboutRow(t, "最低支持", "Android 11 (API 30)")
            }

            // 核心依赖
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                Text("核心第三方库", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 8.dp))
                AboutRow(t, "UI 框架", "Jetpack Compose + Material3")
                Sep(t)
                AboutRow(t, "网络", "OkHttp 4.12")
                Sep(t)
                AboutRow(t, "序列化", "Gson 2.10")
                Sep(t)
                AboutRow(t, "存储", "MMKV 1.3.9")
                Sep(t)
                AboutRow(t, "OCR", "MLKit Text Recognition")
                Sep(t)
                AboutRow(t, "加密", "AndroidX Security-Crypto")
            }

            // GitHub
            Glass(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), t) {
                val ctx = LocalContext.current
                Text("开源地址", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(t.p.copy(alpha = if (t.isLight) 0.08f else 0.1f))
                    .clickable {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/wb-hwang/AIAnswerer-Android")))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GitHub", style = DW.BodyMedium.copy(color = t.ob, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(2.dp))
                        Text("github.com/user/AIAnswerer", style = DW.BodySmall.copy(color = t.p))
                    }
                    Text("↗", style = DW.LabelLarge.copy(color = t.osv))
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AboutRow(t: Th, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DW.BodyMedium.copy(color = t.osv))
        Text(value, style = DW.BodyMedium.copy(color = t.ob))
    }
}
