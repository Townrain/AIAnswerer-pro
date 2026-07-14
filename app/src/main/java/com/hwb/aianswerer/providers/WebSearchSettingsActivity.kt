package com.hwb.aianswerer.providers

import android.os.Bundle
import androidx.activity.compose.setContent
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.ui.pages.WebSearchPage
import com.hwb.aianswerer.ui.theme.sandboxTheme

class WebSearchSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val t = sandboxTheme()
            WebSearchPage(t = t, onBack = { finish() })
        }
    }
}
