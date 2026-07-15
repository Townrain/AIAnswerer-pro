package com.hwb.aianswerer.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.*
import com.hwb.aianswerer.ui.theme.*

// =============================================================================
// Previews
// =============================================================================
@Preview(showSystemUi = true, showBackground = true, name = "主页 — Light")
@Composable private fun HomeLightPreview() = Themed { t -> HomePage(t, {}, {}) }

@Preview(showSystemUi = true, showBackground = true, name = "主页 — Dark")
@Composable private fun HomeDarkPreview() = Themed(DH) { t -> HomePage(t, {}, {}) }

// =============================================================================
// Home Page
// =============================================================================
@Composable
fun HomePage(t: Th, onSettingsClick: () -> Unit, onStartClick: () -> Unit, isAnswerModeActive: Boolean = false, onStopClick: () -> Unit = {}) {
    val bgGradient = Brush.linearGradient(
        listOf(t.bg1, t.bg2, t.bg3, t.bg4, t.bg5),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val scrollState = rememberScrollState()
    val expandedMenu = remember { mutableStateOf<String?>(null) }

    // Shared capture mode state — SettingsCard and CaptureModeCard both observe this
    val captureMode = remember { mutableStateOf(if (AppConfig.isAccessibilityCaptureMode()) "屏幕读取" else "截图模式") }

    // 从设置页返回时强制刷新模型菜单数据
    var resumeVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(bgGradient)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(top = 145.dp)
        ) {
            Box(Modifier.zIndex(12f)) { key(resumeVersion) { MergedCard(t, expandedMenu) } }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.zIndex(1f)) { SettingsCard(t, captureMode) }
            Spacer(Modifier.height(16.dp))
            CaptureModeCard(t, scrollState, captureMode)
            // CTA 按钮高度预留空间
            Spacer(Modifier.height(80.dp))
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(t.bg1, t.bg2)))) {
            TitleSection(t, onSettingsClick = onSettingsClick)
        }
        CtaBar(t, Modifier.align(Alignment.BottomCenter), onStartClick, isAnswerModeActive, onStopClick)
    }
}
