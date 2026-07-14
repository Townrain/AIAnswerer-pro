package com.hwb.aianswerer

import com.hwb.aianswerer.config.AppConfig

class DialogQueue(
    private val showLanguageDialog: () -> Boolean,
    private val setShowLanguageDialog: (Boolean) -> Unit,
    private val showModelSetupDialog: () -> Boolean,
    private val setShowModelSetupDialog: (Boolean) -> Unit,
    private val restartActivity: () -> Unit,
    private val navigateToSettings: () -> Unit
) {
    companion object {
        const val DIALOG_LANGUAGE = "language"
        const val DIALOG_MODEL_SETUP = "model_setup"
    }

    internal val queue = mutableListOf<String>()

    fun checkAndQueueDialogs() {
        when {
            AppConfig.isFirstLaunch() -> queue.add(DIALOG_LANGUAGE)
            !AppConfig.isApiConfigValid() -> queue.add(DIALOG_MODEL_SETUP)
        }
        processDialogQueue()
    }

    fun processDialogQueue() {
        if (queue.isNotEmpty()) {
            when (queue.first()) {
                DIALOG_LANGUAGE -> setShowLanguageDialog(true)
                DIALOG_MODEL_SETUP -> setShowModelSetupDialog(true)
            }
        }
    }

    fun dismissLanguageDialog() {
        setShowLanguageDialog(false)
        queue.remove(DIALOG_LANGUAGE)
        processDialogQueue()
    }

    fun handleLanguageConfirmed() {
        dismissLanguageDialog()
        if (queue.isEmpty() && !AppConfig.isApiConfigValid()) {
            queue.add(DIALOG_MODEL_SETUP)
        }
        restartActivity()
    }

    fun dismissModelSetupDialog() {
        setShowModelSetupDialog(false)
        queue.remove(DIALOG_MODEL_SETUP)
        processDialogQueue()
    }

    fun navigateToModelSettings() {
        dismissModelSetupDialog()
        navigateToSettings()
    }
}
