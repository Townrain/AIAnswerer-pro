package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.PremiumDialog
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.ui.dialogs.LanguageSelectionDialog
import com.hwb.aianswerer.ui.dialogs.ModelSetupReminderDialog
import com.hwb.aianswerer.ui.pages.HomePage
import com.hwb.aianswerer.ui.theme.sandboxTheme
import com.hwb.aianswerer.ui.theme.*

class MainActivity : BaseActivity() {

    private var isAnswerModeActive by mutableStateOf(false)
    private var showStopConfirmDialog by mutableStateOf(false)
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var selectedQuestionTypes by mutableStateOf<Set<String>>(emptySet())
    private var cropMode by mutableStateOf(AppConfig.CROP_MODE_FULL)

    // Dialog state
    private var showLanguageDialog by mutableStateOf(false)
    private var showModelSetupDialog by mutableStateOf(false)
    private var dialogQueue = mutableStateListOf<String>()

    companion object {
        const val DIALOG_LANGUAGE = "language"
        const val DIALOG_MODEL_SETUP = "model_setup"
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            screenCaptureResultCode = result.resultCode
            screenCaptureData = result.data
            if (checkOverlayPermission()) startAnswerMode()
            else requestOverlayPermission()
        } else {
            Toast.makeText(this, getString(R.string.toast_permission_capture_required), Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            if (screenCaptureResultCode != null) startAnswerMode()
            else requestScreenCapturePermission()
        } else {
            Toast.makeText(this, getString(R.string.toast_permission_overlay_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedQuestionTypes = AppConfig.getQuestionTypes()
        cropMode = AppConfig.getCropMode()
        checkAndQueueDialogs()

        setContent {
            val t = sandboxTheme()
            Box(Modifier.fillMaxSize()) {
                HomePage(
                    t = t,
                    onSettingsClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                    onStartClick = { checkAndRequestPermissions() },
                    isAnswerModeActive = isAnswerModeActive,
                    onStopClick = { showStopConfirmDialog = true }
                )

                // Stop confirmation dialog
                if (showStopConfirmDialog) {
                    PremiumDialog(
                        onDismiss = { showStopConfirmDialog = false },
                        title = stringResource(R.string.stop_confirm_title),
                        message = stringResource(R.string.stop_confirm_message),
                        confirmText = stringResource(R.string.button_stop_mode),
                        onConfirm = { showStopConfirmDialog = false; stopAnswerMode() },
                        dismissText = stringResource(R.string.button_cancel),
                        onDismissAction = { showStopConfirmDialog = false }
                    )
                }

                // Language dialog
                if (showLanguageDialog) {
                    LanguageSelectionDialog(
                        onDismiss = { dismissLanguageDialog() },
                        onLanguageConfirmed = { handleLanguageConfirmed() }
                    )
                }

                // Model setup dialog
                if (showModelSetupDialog) {
                    ModelSetupReminderDialog(
                        onDismiss = { dismissModelSetupDialog() },
                        onGoToSettings = { navigateToModelSettings() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (!AppConfig.isApiConfigValid()) {
            Toast.makeText(this, getString(R.string.toast_model_not_configured), Toast.LENGTH_LONG).show()
            if (!dialogQueue.contains(DIALOG_MODEL_SETUP)) {
                dialogQueue.add(DIALOG_MODEL_SETUP)
                processDialogQueue()
            }
            return
        }
        if (!checkOverlayPermission()) { requestOverlayPermission(); return }
        requestScreenCapturePermission()
    }

    private fun checkOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestScreenCapturePermission() {
        val screenCaptureManager = ScreenCaptureManager(this)
        screenCaptureLauncher.launch(screenCaptureManager.createScreenCaptureIntent())
    }

    private fun startAnswerMode() {
        if (AppConfig.isAccessibilityCaptureMode() && !ScreenReaderService.isActive) {
            Toast.makeText(this, getString(R.string.accessibility_service_not_enabled), Toast.LENGTH_LONG).show()
            return
        }
        // Re-read settings in case user changed them on HomePage
        selectedQuestionTypes = AppConfig.getQuestionTypes()
        cropMode = AppConfig.getCropMode()
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            val resultCode = screenCaptureResultCode
            val data = screenCaptureData
            if (resultCode != null && data != null) {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            putStringArrayListExtra("questionTypes", ArrayList(selectedQuestionTypes))
            putExtra("cropMode", cropMode)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_mode_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            return
        }
        isAnswerModeActive = true
        Toast.makeText(this, getString(R.string.toast_mode_started), Toast.LENGTH_SHORT).show()
        requestBatteryOptimizationExemption()
        moveTaskToBack(true)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                AppLog.w("Cannot open battery optimization directly: ${e.message}")
            }
        }
    }

    private fun stopAnswerMode() {
        stopService(Intent(this, FloatingWindowService::class.java))
        isAnswerModeActive = false
        screenCaptureResultCode = null
        screenCaptureData = null
        Toast.makeText(this, getString(R.string.toast_mode_stopped), Toast.LENGTH_SHORT).show()
    }

    // ── Dialog management ──

    private fun checkAndQueueDialogs() {
        when {
            AppConfig.isFirstLaunch() -> dialogQueue.add(DIALOG_LANGUAGE)
            !AppConfig.isApiConfigValid() -> dialogQueue.add(DIALOG_MODEL_SETUP)
        }
        processDialogQueue()
    }

    private fun processDialogQueue() {
        if (dialogQueue.isNotEmpty()) {
            when (dialogQueue.first()) {
                DIALOG_LANGUAGE -> showLanguageDialog = true
                DIALOG_MODEL_SETUP -> showModelSetupDialog = true
            }
        }
    }

    private fun dismissLanguageDialog() {
        showLanguageDialog = false
        dialogQueue.remove(DIALOG_LANGUAGE)
        processDialogQueue()
    }

    private fun handleLanguageConfirmed() {
        dismissLanguageDialog()
        if (dialogQueue.isEmpty() && !AppConfig.isApiConfigValid()) {
            dialogQueue.add(DIALOG_MODEL_SETUP)
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun dismissModelSetupDialog() {
        showModelSetupDialog = false
        dialogQueue.remove(DIALOG_MODEL_SETUP)
        processDialogQueue()
    }

    private fun navigateToModelSettings() {
        dismissModelSetupDialog()
        startActivity(Intent(this, com.hwb.aianswerer.providers.ProviderSettingsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (isAnswerModeActive != FloatingWindowService.isRunning) {
            isAnswerModeActive = FloatingWindowService.isRunning
            if (!FloatingWindowService.isRunning) {
                screenCaptureResultCode = null
                screenCaptureData = null
            }
        }
        // 通知悬浮窗刷新设置
        if (FloatingWindowService.isRunning) {
            sendBroadcast(Intent(Constants.ACTION_REFRESH_SETTINGS).setPackage(packageName))
        }
    }
}
