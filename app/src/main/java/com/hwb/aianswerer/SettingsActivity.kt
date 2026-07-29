package com.hwb.aianswerer

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.hwb.aianswerer.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.setContent
import com.hwb.aianswerer.ui.pages.SettingsPage
import com.hwb.aianswerer.ui.theme.sandboxTheme

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            val t = sandboxTheme()
            SettingsPage(
                t = t,
                onBack = { finish() },
                onWebSearch = { startActivity(Intent(activity, com.hwb.aianswerer.providers.WebSearchSettingsActivity::class.java)) },
                onModels = { startActivity(Intent(activity, com.hwb.aianswerer.providers.ProviderSettingsActivity::class.java)) },
                onAbout = { startActivity(Intent(activity, AboutActivity::class.java)) },
                onExportLogs = {
                    withContext(Dispatchers.IO) {
                        val zipFile = AppLog.exportLogsZip()
                        if (zipFile == null || !zipFile.exists() || zipFile.length() == 0L) {
                            false
                        } else {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    activity,
                                    "${activity.packageName}.fileprovider",
                                    zipFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                withContext(Dispatchers.Main) {
                                    activity.startActivity(Intent.createChooser(shareIntent, getString(R.string.debug_log_share_title)))
                                }
                                true
                            } catch (e: Exception) {
                                AppLog.e("Settings", "share logs failed", e)
                                false
                            }
                        }
                    }
                }
            )
        }
    }
}
