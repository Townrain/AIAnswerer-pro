package com.hwb.aianswerer

import android.os.Bundle
import androidx.activity.compose.setContent
import com.hwb.aianswerer.ui.pages.AboutPage
import com.hwb.aianswerer.ui.theme.sandboxTheme

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val t = sandboxTheme()
            AboutPage(t = t, onBack = { finish() })
        }
    }
}
