package com.hwb.aianswerer

import android.app.Application
import android.content.Context
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.providers.TavilyMigration
import com.hwb.aianswerer.providers.ProviderStorage
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.LanguageUtil

class MyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        // 保存Application实例（必须在AppConfig.init之前，避免lateinit访问崩溃）
        instance = this

        // 在attachBaseContext中初始化MMKV（必须在使用AppConfig之前）
        AppConfig.init(base)
        ProviderStorage.init(base)

        // 应用语言配置并获取新的Context
        val context = LanguageUtil.attachBaseContext(base)

        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()

        // EncryptedSharedPreferences 需要可用的 Application context，不能在 attachBaseContext 中初始化
        AppConfig.initSecurePrefs(this)
        ProviderStorage.initSecurePrefs(this)

        // 初始化文件日志（写入 cacheDir，系统低存储时会自动清理）
        AppLog.init(cacheDir)
        AppLog.i("=== AIAnswerer started ===")
        AppLog.i("Version: ${BuildConfig.VERSION_NAME}, Debug: ${BuildConfig.DEBUG}")

        // 清理残留的临时裁剪文件
        cleanupTempFiles()
        // 迁移旧 Tavily 配置到新的多供应商 WebSearchStorage
        TavilyMigration.run()

    }

    /**
     * 清理缓存目录中的临时裁剪文件
     */
    private fun cleanupTempFiles() {
        try {
            val cacheDir = cacheDir
            val tempFiles = cacheDir.listFiles { file -> file.name.startsWith("temp_crop_") }
            tempFiles?.forEach { file ->
                if (file.exists()) {
                    file.delete()
                    android.util.Log.d("MyApplication", "清理临时文件: ${file.name}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MyApplication", "清理临时文件失败", e)
        }
    }

    companion object {
        private lateinit var instance: MyApplication

        /**
         * 获取Application实例
         */
        fun getInstance(): MyApplication = instance

        /**
         * 获取Application Context
         * 使用 applicationContext 确保获取到最新的配置（包括语言切换后的）
         */
        fun getAppContext(): Context = instance.applicationContext

        /**
         * 便捷方法：获取字符串资源
         * @param resId 字符串资源ID
         * @return 字符串
         */
        fun getString(resId: Int): String {
            return getAppContext().getString(resId)
        }

        /**
         * 便捷方法：获取带格式化参数的字符串资源
         * @param resId 字符串资源ID
         * @param formatArgs 格式化参数
         * @return 格式化后的字符串
         */
        fun getString(resId: Int, vararg formatArgs: Any): String {
            return getAppContext().getString(resId, *formatArgs)
        }
    }
}

