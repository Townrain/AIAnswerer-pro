package com.hwb.aianswerer.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.hwb.aianswerer.config.AppConfig

/**
 * 全局主题状态
 * 使用 Compose 的 mutableStateOf 实现主题实时切换
 */
object ThemeState {
    // 0=跟随系统, 1=亮色, 2=暗色
    var darkMode by mutableIntStateOf(AppConfig.getDarkMode())

    fun update(mode: Int) {
        darkMode = mode
        AppConfig.saveDarkMode(mode)
    }
}
