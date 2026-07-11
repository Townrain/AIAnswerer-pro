package com.hwb.aianswerer

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import com.hwb.aianswerer.ui.pages.SettingsPage
import com.hwb.aianswerer.ui.theme.sandboxTheme

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val t = sandboxTheme()
            SettingsPage(
                t = t,
                onBack = { finish() },
                onWebSearch = { startActivity(Intent(this, com.hwb.aianswerer.providers.WebSearchSettingsActivity::class.java)) },
                onModels = { startActivity(Intent(this, com.hwb.aianswerer.providers.ProviderSettingsActivity::class.java)) },
                onAbout = { startActivity(Intent(this, AboutActivity::class.java)) }
            )
        }
    }
}
