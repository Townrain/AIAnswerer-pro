package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hwb.aianswerer.ui.components.BtnRadius
import com.hwb.aianswerer.ui.components.HighlightCard
import com.hwb.aianswerer.ui.components.InfoCard
import com.hwb.aianswerer.ui.components.LibraryItem
import com.hwb.aianswerer.ui.components.TopBarWithBack
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.ui.theme.*

private data class LibraryInfo(val name: String, val description: String)

private val libraries = listOf(
    LibraryInfo("Jetpack Compose", "Modern UI toolkit for Android"),
    LibraryInfo("ML Kit Text Recognition", "Google's OCR technology"),
    LibraryInfo("MMKV", "High-performance key-value storage"),
    LibraryInfo("OkHttp", "HTTP & HTTP/2 client"),
    LibraryInfo("Gson", "JSON serialization library")
)

/**
 * 关于页面Activity
 */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AIAnswererTheme {
                AboutScreen(
                    context = this,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/**
 * 关于页面界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun AboutScreen(
    context: Context? = null,
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopBarWithBack(
                title = stringResource(R.string.about_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        val isDark = LocalIsDarkMode.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) PremiumBgDark else PremiumBgLight)
                .padding(paddingValues)
                .padding(horizontal = Spacing.xxl)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.xl))

            // Logo 区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                Box(
                    modifier = Modifier
                        .size(Spacing.xxxl + Spacing.xxl)
                        .shadowButton(BtnRadius)
                        .clip(RoundedCornerShape(BtnRadius))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(PremiumPrimary, PremiumPrimaryVariant)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LocalIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.xxl),
                        tint = PremiumCardLight
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextDarkPrimary else TextDark
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary
                )
            }

            // 应用简介
            InfoCard(
                title = stringResource(R.string.about_app_intro_title),
                modifier = Modifier.padding(bottom = Spacing.xl)
            ) {
                Text(
                    text = stringResource(R.string.about_app_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) TextDarkPrimary else TextDark
                )
            }

            // 核心第三方库
            InfoCard(
                title = stringResource(R.string.about_libraries_title),
                modifier = Modifier.padding(bottom = Spacing.xl)
            ) {
                libraries.forEachIndexed { index, lib ->
                    LibraryItem(name = lib.name, description = lib.description)
                    if (index < libraries.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    }
                }
            }

            // GitHub 链接 — HighlightCard 样式
            HighlightCard(
                title = stringResource(R.string.about_github_title),
                subtitle = stringResource(R.string.about_github_subtitle),
                onClick = {
                    context?.let {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            it.getString(R.string.about_github_link).toUri()
                        )
                        it.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(bottom = Spacing.xxl)
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
