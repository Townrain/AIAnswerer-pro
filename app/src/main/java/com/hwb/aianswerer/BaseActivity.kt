package com.hwb.aianswerer

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.LanguageUtil

/**
 * 基础 Activity
 * 统一处理语言配置和主题，所有 Activity 应继承此类
 */
abstract class BaseActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStatusBarStyle()
    }

    /**
     * 应用状态栏样式
     * 根据当前主题模式设置状态栏颜色和图标颜色
     */
    private fun applyStatusBarStyle() {
        val darkMode = AppConfig.getDarkMode()
        val isDark = when (darkMode) {
            2 -> true   // 强制暗色
            else -> {
                // 0=跟随系统, 1=亮色 - 都使用亮色状态栏
                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (darkMode == 0) nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                else false
            }
        }

        if (isDark) {
            // 暗色模式：深色状态栏，浅色图标
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        } else {
            // 亮色模式：浅色状态栏，深色图标
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }
    }
}
