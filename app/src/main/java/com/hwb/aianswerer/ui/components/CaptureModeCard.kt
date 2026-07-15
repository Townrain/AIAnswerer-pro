package com.hwb.aianswerer.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hwb.aianswerer.ScreenReaderService
import androidx.compose.ui.graphics.Brush
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.theme.*

@Composable
internal fun CaptureModeCard(t: Th, scrollState: androidx.compose.foundation.ScrollState, captureMode: MutableState<String>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityEnabled by remember { mutableStateOf(ScreenReaderService.isAccessibilityServiceEnabled(context)) }
    // Re-check accessibility status when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = ScreenReaderService.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
            .border(1.dp, t.gb, RoundedCornerShape(CardR))
            .padding(CardPad)
    ) {
        Column {
            Text("采集模式", style = DW.TitleMedium.copy(color = t.ob))
            Spacer(Modifier.height(12.dp))
            Text("题目识别方式", style = DW.BodySmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("截图模式", "屏幕读取").forEach { mode ->
                    key(mode) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(mode, selected = captureMode.value == mode, t) {
                                captureMode.value = mode
                                AppConfig.saveCaptureMode(
                                    if (mode == "屏幕读取") AppConfig.CAPTURE_MODE_ACCESSIBILITY
                                    else AppConfig.CAPTURE_MODE_SCREENSHOT
                                )
                                isAccessibilityEnabled = ScreenReaderService.isAccessibilityServiceEnabled(context)
                            }
                        }
                    }
                }
            }
            if (captureMode.value == "屏幕读取") {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isAccessibilityEnabled) t.ok else t.err))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isAccessibilityEnabled) "无障碍服务: 已启用" else "无障碍服务: 未启用",
                        style = DW.BodySmall.copy(color = if (isAccessibilityEnabled) t.ok else t.err)
                    )
                }
                if (!isAccessibilityEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请在系统设置中开启 AI答题助手 的无障碍服务 →",
                        style = DW.BodySmall.copy(color = t.p),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(captureMode.value) {
        if (captureMode.value == "屏幕读取") {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}
