package com.hwb.aianswerer

import androidx.compose.runtime.mutableStateOf
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.AppLog

/**
 * Centralised settings reader for the floating window service.
 *
 * Holds one [androidx.compose.runtime.State] per setting so that Compose
 * recomposition picks up changes, and exposes a single [refresh] entry-point.
 */
class SettingsService {
    val visionEnabled = mutableStateOf(AppConfig.isVisionEnabled())
    val searchEnabled = mutableStateOf(
        com.hwb.aianswerer.providers.WebSearchStorage.isSearchEnabled()
    )
    val reasoningEnabled = mutableStateOf(AppConfig.getReasoningEffort() != null)
    val imageEnabled = mutableStateOf(false)  // TODO: 接入图片功能时改为从 AppConfig 读取

    // Floating-window appearance
    val floatButtonAlpha = mutableStateOf(AppConfig.getFloatButtonAlpha())
    val stealthMode = mutableStateOf(AppConfig.isStealthModeEnabled())
    val floatButtonSizeDp = mutableStateOf(AppConfig.getFloatButtonSize())
    val floatCardAlpha = mutableStateOf(AppConfig.getFloatCardAlpha())

    /** Re-read every setting from persistent storage and push to state holders. */
    fun refresh() {
        visionEnabled.value = AppConfig.isVisionEnabled()
        searchEnabled.value =
            com.hwb.aianswerer.providers.WebSearchStorage.isSearchEnabled()
        reasoningEnabled.value = AppConfig.getReasoningEffort() != null
        stealthMode.value = AppConfig.isStealthModeEnabled()
        floatButtonAlpha.value = AppConfig.getFloatButtonAlpha()
        floatButtonSizeDp.value = AppConfig.getFloatButtonSize()
        floatCardAlpha.value = AppConfig.getFloatCardAlpha()
        AppLog.d("SettingsService", "settings refreshed from app")
    }
}
