package com.hwb.aianswerer.ui.pages.models

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.models.ModelCapabilityChecker
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.pages.TestState
import com.hwb.aianswerer.ui.theme.*

// =============================================================================
// Small reusable UI components for ModelsPage
// =============================================================================

@Composable
internal fun ModelsTopBar(t: Th, onBack: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    Row(Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 28.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).scale(scale.value).pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.85f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) currentOnBack()
            })
        }, contentAlignment = Alignment.Center) {
            Icon(LocalIcons.ArrowBack, "返回", tint = t.ob, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text("模型厂商", style = DW.TitleLarge.copy(color = t.ob))
            Text("配置各厂商 API Key 并选择模型", style = DW.BodySmall.copy(color = t.osv))
        }
    }
}

@Composable
internal fun ModelsSearchBar(t: Th, value: String, onValueChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(20.dp))
        .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.08f))
        .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.3f else 0.1f), RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LocalIcons.Search, "搜索", tint = t.osv, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("搜索厂商名称 …", style = DW.BodySmall.copy(color = t.osv.copy(alpha = 0.6f)))
                BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
                    textStyle = DW.BodySmall.copy(color = t.ob), cursorBrush = SolidColor(t.p), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(t.ac.copy(alpha = 0.2f)).clickable { onValueChange("") },
                    contentAlignment = Alignment.Center) { Text("\u2715", style = DW.LabelSmall.copy(color = t.osv, fontSize = 10.sp)) }
            }
        }
    }
}

@Composable
internal fun ModelConfigField(t: Th, label: String, hint: String, value: String, onValueChange: (String) -> Unit, isPassword: Boolean = false) {
    var showPassword by remember { mutableStateOf(false) }
    Column {
        Text(label, style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(t.gb.copy(alpha = if (t.isLight) 0.5f else 0.06f))
            .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.25f else 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, style = DW.BodySmall.copy(color = t.osv.copy(alpha = 0.5f)))
                    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
                        textStyle = DW.BodySmall.copy(color = t.ob), cursorBrush = SolidColor(t.p),
                        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth())
                }
                if (isPassword && value.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape).background(t.ac.copy(alpha = 0.12f)).clickable { showPassword = !showPassword },
                        contentAlignment = Alignment.Center) { Text(if (showPassword) "\uD83D\uDE48" else "\uD83D\uDC41", style = TextStyle(fontSize = 13.sp)) }
                }
            }
        }
    }
}

@Composable
internal fun ModelTestButton(t: Th, state: TestState?, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val current = state ?: TestState.Idle
    val currentOnClick by rememberUpdatedState(onClick)
    Column {
        Box(Modifier.fillMaxWidth().scale(scale.value).clip(RoundedCornerShape(14.dp))
            .background(t.gb.copy(alpha = if (t.isLight) 0.55f else 0.1f))
            .border(1.dp, t.ac.copy(alpha = if (t.isLight) 0.3f else 0.1f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scale.snapTo(0.92f); val released = tryAwaitRelease()
                    scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                    if (released) currentOnClick()
                })
            }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current is TestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.p)
                    Spacer(Modifier.width(8.dp))
                }
                Text(when (current) { TestState.Idle -> "测试连接"
                    TestState.Testing -> "测试中 …"
                    is TestState.Success -> "测试连接"
                    is TestState.Error -> "重新测试" },
                    style = DW.LabelMedium.copy(color = when (current) {
                        is TestState.Success -> t.ok
                        is TestState.Error -> t.err; else -> t.ob }))
            }
        }
        when (current) {
            is TestState.Success -> Text("连接成功 (${current.ms}ms)", style = DW.BodySmall.copy(color = t.ok, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            is TestState.Error -> Text("连接失败: ${current.msg}", style = DW.BodySmall.copy(color = t.err, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            else -> {}
        }
    }
}

@Composable
internal fun ModelSaveButton(t: Th, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    Box(Modifier.fillMaxWidth().scale(scale.value).clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(t.p, t.pe), Offset.Infinite, Offset.Zero))
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                scale.snapTo(0.92f); val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
                if (released) currentOnClick()
            })
        }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text("保存配置", style = DW.LabelLarge.copy(color = t.w))
    }
}

@Composable
internal fun ModelLinkButton(t: Th, label: String, url: String) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { scale.snapTo(0.92f); pressed = true }
                is PressInteraction.Cancel -> { scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)); pressed = false }
                is PressInteraction.Release -> { /* handled below */ }
            }
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            scale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f))
            pressed = false
        }
    }

    Box(Modifier.scale(scale.value).clip(RoundedCornerShape(12.dp))
        .background(t.p.copy(alpha = if (t.isLight) 0.08f else 0.12f))
        .border(1.dp, t.p.copy(alpha = if (t.isLight) 0.2f else 0.15f), RoundedCornerShape(12.dp))
        .clickable(interactionSource = interactionSource, indication = null) {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LocalIcons.Link, null, tint = t.p, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = DW.LabelSmall.copy(color = t.p))
        }
    }
}

@Composable
internal fun ModelTypeBadge(type: ModelProviderType, t: Th) {
    val bg = type.color.copy(alpha = 0.15f)
    val fg = if (t.isLight) type.color.copy(alpha = 0.9f) else type.color.copy(alpha = 0.8f)
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(type.label, style = DW.LabelSmall.copy(color = fg, fontSize = 10.sp, letterSpacing = 0.3.sp))
    }
}

/**
 * 模型能力标签组件
 * 纯语言模型显示"语言"标签，多模态模型显示"视觉"+"语言"两个标签
 */
@Composable
internal fun ModelCapabilityTags(modelId: String, t: Th) {
    val isVision = remember(modelId) { ModelCapabilityChecker.isVisionModel(modelId) }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // 视觉标签（多模态模型才有）
        if (isVision) {
            val visionColor = Color(0xFF4A90D9)
            val visionBg = visionColor.copy(alpha = 0.15f)
            val visionFg = if (t.isLight) visionColor.copy(alpha = 0.9f) else visionColor.copy(alpha = 0.8f)
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(visionBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("视觉", style = DW.LabelSmall.copy(color = visionFg, fontSize = 9.sp, letterSpacing = 0.2.sp))
            }
        }
        // 语言标签（所有模型都有）
        val textColor = Color(0xFF34C759)
        val textBg = textColor.copy(alpha = 0.15f)
        val textFg = if (t.isLight) textColor.copy(alpha = 0.9f) else textColor.copy(alpha = 0.8f)
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(textBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("语言", style = DW.LabelSmall.copy(color = textFg, fontSize = 9.sp, letterSpacing = 0.2.sp))
        }
    }
}
