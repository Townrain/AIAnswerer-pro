package com.hwb.aianswerer.providers

import android.os.Bundle
import androidx.activity.compose.setContent
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.ui.pages.ModelsPage
import com.hwb.aianswerer.ui.theme.sandboxTheme

class ProviderSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProviderStorage.init(applicationContext)
        ProviderStorage.initSecurePrefs(applicationContext)
        setContent {
            val t = sandboxTheme()
            ModelsPage(t = t, onBack = { finish() })
        }
    }
}
