package com.hwb.aianswerer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.AnimatedButton
import com.hwb.aianswerer.ui.components.AppTextField
import com.hwb.aianswerer.ui.components.ButtonVariant
import com.hwb.aianswerer.ui.components.CardRadius
import com.hwb.aianswerer.ui.theme.*


/**
 * 透明确认 Activity — 显示 OCR 识别文本供用户编辑，确认后通过本地广播
 * 将文本传回 FloatingWindowService 调用 AI 接口。
 */
class ConfirmTextActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recognizedText = intent.getStringExtra(Constants.EXTRA_RECOGNIZED_TEXT) ?: ""

        setContent {
            AIAnswererTheme {
                ConfirmTextScreen(
                    recognizedText = recognizedText,
                    onConfirm = { editedText ->
                        handleConfirm(editedText)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun handleConfirm(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_text_empty), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.toast_getting_answer), Toast.LENGTH_SHORT).show()

        val intent = Intent(Constants.ACTION_REQUEST_ANSWER).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_QUESTION_TEXT, text)
        }
        sendBroadcast(intent)
        finish()
    }
}

private const val CONFIRM_CARD_WIDTH = 0.92f
private const val CONFIRM_CARD_HEIGHT = 0.80f

@Composable
fun ConfirmTextScreen(
    recognizedText: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(recognizedText) }

    val questionTypes = AppConfig.getQuestionTypes()
    val settingsText = buildString {
        append(questionTypes.joinToString("、"))
    }
    val isDark = LocalIsDarkMode.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBgDark.copy(alpha = 0.50f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        // Apple-style glass overlay card
        val cardModifier = if (isDark) Modifier
            .glassSurfaceDark(alpha = 0.07f, shape = RoundedCornerShape(CardRadius), cornerRadius = CardRadius)
        else Modifier.glassOverlay(shape = RoundedCornerShape(CardRadius))
        Box(
            modifier = Modifier
                .fillMaxWidth(CONFIRM_CARD_WIDTH)
                .fillMaxHeight(CONFIRM_CARD_HEIGHT)
                .then(cardModifier)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl)
            ) {
                // 标题
                Text(
                    text = stringResource(R.string.confirm_text_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isDark) TextDarkPrimary else TextDark,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )

                // 当前设置
                Text(
                    text = stringResource(R.string.current_settings, settingsText),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextDarkTertiary else TextTertiary,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )

                // 文本输入框 — 使用统一的 AppTextField
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.confirm_text_label),
                    placeholder = stringResource(R.string.confirm_text_placeholder),
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    AnimatedButton(
                        text = stringResource(R.string.button_cancel),
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Glass
                    )

                    AnimatedButton(
                        text = stringResource(R.string.button_confirm_and_answer),
                        onClick = { onConfirm(text) },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Primary
                    )
                }
            }
        }
    }
}
