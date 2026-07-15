package com.hwb.aianswerer.ui.pages.models

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.ui.pages.TestState
import com.hwb.aianswerer.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModelProviderCard(
    t: Th, ps: ProviderState, expanded: Boolean, testState: TestState?,
    bringIntoViewRequester: BringIntoViewRequester,
    onToggleExpand: () -> Unit, onEnableToggle: () -> Unit,
    onApiKeyChange: (String) -> Unit, onHostChange: (String) -> Unit,
    onTest: () -> Unit, onSave: () -> Unit,
    onOpenPicker: () -> Unit, onRemoveModel: (String) -> Unit
) {
    val borderColor by animateColorAsState(
        if (expanded) t.p.copy(alpha = if (t.isLight) 0.5f else 0.35f) else t.ac.copy(alpha = if (t.isLight) 0.2f else 0.06f),
        tween(300), label = "mcb")
    val cardBgAlpha by animateFloatAsState(
        if (expanded) (if (t.isLight) 0.95f else 0.18f) else (if (t.isLight) 0.85f else 0.06f),
        tween(300), label = "mcba")
    val chevronRot by animateFloatAsState(if (expanded) 180f else 0f, spring(0.6f, 400f), label = "mcv")

    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        .bringIntoViewRequester(bringIntoViewRequester)
        .clip(RoundedCornerShape(20.dp)).background(t.gb.copy(alpha = cardBgAlpha))
        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onToggleExpand() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ps.def.name, style = DW.BodyLarge.copy(color = t.ob, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.width(8.dp))
                    ModelTypeBadge(ps.def.type, t)
                }
                Spacer(Modifier.height(3.dp))
                Text(ps.def.apiHost.ifEmpty { "自定义" }, style = DW.BodySmall.copy(color = t.osv, fontSize = 11.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("\u25BE", style = DW.LabelMedium.copy(color = t.osv),
                modifier = Modifier.graphicsLayer { rotationZ = chevronRot }.padding(horizontal = 6.dp))
            Spacer(Modifier.width(4.dp))
            val switchScale = remember { Animatable(1f) }
            LaunchedEffect(ps.enabled) { switchScale.snapTo(0.82f); switchScale.animateTo(1f, spring(dampingRatio = 0.15f, stiffness = 500f)) }
            Box(Modifier.scale(switchScale.value).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEnableToggle
            )) {
                Switch(checked = ps.enabled, onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedThumbColor = t.w, checkedTrackColor = t.p,
                        uncheckedThumbColor = t.w, uncheckedTrackColor = t.to,
                        checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent),
                    modifier = Modifier.height(28.dp))
            }
        }

        AnimatedVisibility(visible = expanded,
            enter = expandVertically(spring(0.7f, 300f)) + fadeIn(tween(250)),
            exit = shrinkVertically(spring(0.7f, 400f)) + fadeOut(tween(200))) {
            Column(Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = t.ac.copy(alpha = 0.1f), thickness = 0.5.dp)
                Spacer(Modifier.height(16.dp))

                // API Host
                ModelConfigField(t, "API Host", ps.def.apiHost.ifEmpty { "输入 API 地址" }, ps.customHost, onHostChange)
                Spacer(Modifier.height(12.dp))

                // API Key
                ModelConfigField(t, "API Key", "输入 API Key", ps.apiKey, onApiKeyChange, isPassword = true)
                Spacer(Modifier.height(12.dp))

                // Test connection
                ModelTestButton(t, testState, onTest)
                Spacer(Modifier.height(12.dp))

                // Selected models
                if (ps.selectedModels.isNotEmpty()) {
                    Text("已选模型", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(bottom = 8.dp))
                    ps.selectedModels.forEach { model ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(t.p.copy(alpha = if (t.isLight) 0.06f else 0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(model, style = DW.BodySmall.copy(color = t.ob), modifier = Modifier.weight(1f))
                            Box(Modifier.size(22.dp).clip(CircleShape).background(t.err.copy(alpha = 0.12f))
                                .clickable { onRemoveModel(model) }, contentAlignment = Alignment.Center) {
                                Text("\u2212", style = TextStyle(fontSize = 16.sp, color = t.err, fontWeight = FontWeight.Bold))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Add model button
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, t.p.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .background(t.p.copy(alpha = if (t.isLight) 0.05f else 0.08f))
                    .clickable { onOpenPicker() }
                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text("+ 添加模型", style = DW.LabelLarge.copy(color = t.p))
                }
                Spacer(Modifier.height(12.dp))

                // Links
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ps.def.officialUrl?.let { url -> ModelLinkButton(t, "官方网站", url) }
                    ps.def.apiKeyUrl?.let { url -> ModelLinkButton(t, "获取 Key", url) }
                }
                Spacer(Modifier.height(12.dp))

                // Save
                ModelSaveButton(t, onSave)
            }
        }
    }
}
