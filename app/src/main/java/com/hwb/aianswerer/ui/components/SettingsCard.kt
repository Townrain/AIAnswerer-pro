package com.hwb.aianswerer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.theme.*

@Composable
internal fun SettingsCard(t: Th, captureMode: MutableState<String>) {
    val questionTypes = remember { AppConfig.getQuestionTypes().toMutableStateList() }
    val cropMode = remember { mutableStateOf(AppConfig.getCropMode()) }

    val allTypes = listOf(
        MyApplication.getString(R.string.question_type_single),
        MyApplication.getString(R.string.question_type_multiple),
        MyApplication.getString(R.string.question_type_uncertain),
        MyApplication.getString(R.string.question_type_blank),
        MyApplication.getString(R.string.question_type_essay)
    )
    // (code, label) — saves English code to match service constants
    val cropModes = listOf(
        AppConfig.CROP_MODE_FULL to "全屏识别",
        AppConfig.CROP_MODE_EACH to "部分识别（每次）",
        AppConfig.CROP_MODE_ONCE to "部分识别（单次）"
    )

    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            .background(Brush.verticalGradient(listOf(t.gt, t.gdp), endY = Float.POSITIVE_INFINITY), RoundedCornerShape(CardR))
            .border(1.dp, t.gb, RoundedCornerShape(CardR))
            .padding(CardPad)
    ) {
        Column {
            // ── Upper: Question types ──
            Text(MyApplication.getString(R.string.question_type_label), style = DW.TitleMedium.copy(color = t.ob))
            Spacer(Modifier.height(12.dp))
            // 3 + 2 layout, chips fill each row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTypes.take(3).forEach { type ->
                    key(type) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(type, selected = type in questionTypes, t) {
                                if (type in questionTypes) {
                                    if (questionTypes.size > 1) questionTypes.remove(type)
                                } else {
                                    questionTypes.add(type)
                                }
                                AppConfig.saveQuestionTypes(questionTypes.toSet())
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTypes.drop(3).forEach { type ->
                    key(type) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(type, selected = type in questionTypes, t) {
                                if (type in questionTypes) {
                                    if (questionTypes.size > 1) questionTypes.remove(type)
                                } else {
                                    questionTypes.add(type)
                                }
                                AppConfig.saveQuestionTypes(questionTypes.toSet())
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = t.ac.copy(alpha = 0.15f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 16.dp))

            // ── Lower: Screenshot recognition mode ──
            val isAccessMode = captureMode.value == "屏幕读取"
            Text("截图识别模式", style = DW.TitleMedium.copy(color = if (isAccessMode) t.ac.copy(alpha = 0.4f) else t.ob))
            Spacer(Modifier.height(12.dp))
            if (isAccessMode) {
                Text("屏幕读取模式下不可用", style = DW.BodySmall.copy(color = t.ac.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cropModes.forEach { (code, label) ->
                    key(code) {
                        Box(Modifier.weight(1f)) {
                            SelectChip(label, selected = cropMode.value == code && !isAccessMode, t) {
                                if (!isAccessMode) { cropMode.value = code; AppConfig.saveCropMode(code) }
                            }
                        }
                    }
                }
            }
        }
    }
}
