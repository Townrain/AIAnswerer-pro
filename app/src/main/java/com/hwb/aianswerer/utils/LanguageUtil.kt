package com.hwb.aianswerer.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.hwb.aianswerer.config.AppConfig
import java.util.Locale

/**
 * 语言管理工具类
 *
 * 负责应用语言的切换和配置更新
 * 支持中文和英文两种语言
 */
object LanguageUtil {

    /**
     * 应用语言设置
     *
     * 更新系统Configuration并保存到配置中
     * 不会立即生效，需要重启Activity或应用
     *
     * @param context 上下文
     * @param languageCode 语言代码 ("zh" 或 "en")
     */
    fun applyLanguage(context: Context, languageCode: String) {
        // 保存语言设置
        AppConfig.saveLanguage(languageCode)
    }

    /**
     * 在attachBaseContext中应用语言配置
     *
     * 这是Android官方推荐的方式，特别是对于Android N及以上版本
     * 应该在Application和Activity的attachBaseContext方法中调用
     *
     * @param context 基础上下文
     * @return 应用了语言配置的新Context
     */
    fun attachBaseContext(context: Context): Context {
        val languageCode = AppConfig.getLanguage()
        return updateConfigurationContext(context, languageCode)
    }

    /**
     * 更新Configuration并返回新的Context
     *
     * @param context 原始上下文
     * @param languageCode 语言代码
     * @return 应用了新配置的Context
     */
    private fun updateConfigurationContext(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            AppConfig.LANGUAGE_EN -> Locale.ENGLISH
            AppConfig.LANGUAGE_ZH -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.SIMPLIFIED_CHINESE
        }

        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android N及以上版本使用LocaleList
            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)

            // 返回新的Context
            return context.createConfigurationContext(configuration)
        } else {
            // Android N以下版本使用旧的方式
            @Suppress("DEPRECATION")
            configuration.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            return context
        }
    }

    /**
     * 从保存的配置中加载语言设置
     *
     * 已废弃：请使用attachBaseContext方法代替
     * 保留此方法仅为向后兼容
     *
     * @param context 上下文
     */
    @Deprecated("使用attachBaseContext方法代替", ReplaceWith("attachBaseContext(context)"))
    fun loadLanguageConfig(context: Context) {
        val languageCode = AppConfig.getLanguage()
        updateConfigurationContext(context, languageCode)
    }

    /**
     * 获取当前语言代码
     *
     * @return 语言代码 ("zh" 或 "en")
     */
    fun getCurrentLanguage(): String {
        return AppConfig.getLanguage()
    }

    /**
     * 重启Activity以应用新的语言设置
     *
     * @param activity 要重启的Activity
     */
    fun restartActivity(activity: Activity) {
        val intent = activity.intent
        activity.finish()
        activity.startActivity(intent)
        // 添加淡入淡出动画
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /**
     * 重启应用以应用新的语言设置
     *
     * 这种方式会完全重启应用，返回到启动Activity
     *
     * @param context 上下文
     */
    fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)

            // 如果是Activity，结束当前Activity
            if (context is Activity) {
                context.finish()
            }

            // 先停止悬浮窗服务，避免幽灵通知
            try {
                context.stopService(android.content.Intent(context, com.hwb.aianswerer.FloatingWindowService::class.java))
            } catch (_: Exception) {
                // 忽略停止服务失败
            }

            // 延迟300ms确保Service.onDestroy执行后再杀进程
            // 解决stopService异步与killProcess同步的竞态问题
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 300)
        }
    }
}

