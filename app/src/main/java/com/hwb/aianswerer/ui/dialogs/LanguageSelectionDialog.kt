package com.hwb.aianswerer.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.hwb.aianswerer.R
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.PremiumDialog
import com.hwb.aianswerer.ui.components.PremiumRadioOption

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onLanguageConfirmed: () -> Unit
) {
    val currentLanguage = AppConfig.getLanguage()
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    PremiumDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dialog_language_title),
        confirmText = stringResource(R.string.dialog_language_confirm),
        onConfirm = {
            AppConfig.saveLanguage(selectedLanguage)
            AppConfig.setFirstLaunchComplete()
            onLanguageConfirmed()
        },
        dismissText = stringResource(R.string.dialog_language_cancel),
        onDismissAction = {
            AppConfig.saveLanguage(AppConfig.LANGUAGE_ZH)
            AppConfig.setFirstLaunchComplete()
            onDismiss()
        },
        content = {
            PremiumRadioOption(
                text = stringResource(R.string.language_zh_label),
                selected = selectedLanguage == AppConfig.LANGUAGE_ZH,
                onClick = { selectedLanguage = AppConfig.LANGUAGE_ZH }
            )
            PremiumRadioOption(
                text = stringResource(R.string.language_en_label),
                selected = selectedLanguage == AppConfig.LANGUAGE_EN,
                onClick = { selectedLanguage = AppConfig.LANGUAGE_EN }
            )
        }
    )
}
