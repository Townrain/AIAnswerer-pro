package com.hwb.aianswerer.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hwb.aianswerer.R
import com.hwb.aianswerer.ui.components.PremiumDialog

@Composable
fun ModelSetupReminderDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    PremiumDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dialog_model_setup_title),
        message = stringResource(R.string.dialog_model_setup_message),
        confirmText = stringResource(R.string.dialog_model_setup_confirm),
        onConfirm = onGoToSettings,
        dismissText = stringResource(R.string.dialog_model_setup_cancel),
        onDismissAction = onDismiss
    )
}
