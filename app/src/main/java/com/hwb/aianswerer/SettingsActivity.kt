package com.hwb.aianswerer

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.TavilyClient
import com.hwb.aianswerer.api.vision.OpenAIVisionProvider
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.*
import com.hwb.aianswerer.ui.theme.*
import com.hwb.aianswerer.utils.LanguageUtil
import kotlinx.coroutines.launch

/**
 * 设置页面 — 自动提交/自动复制/显示控制/语言切换。
 *
 * 语言切换通过 killProcess 重启整个应用进程，而非仅重启 Activity，
 * 因为 Application 和所有已启动的 Service 也需要重新应用语言配置。
 */
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AIAnswererTheme {
                SettingsScreen(
                    onBackClick = { finish() },
                    onModelSettingsClick = {
                        startActivity(Intent(this, ModelSettingsActivity::class.java))
                    },
                    onLanguageChange = { languageCode ->
                        LanguageUtil.applyLanguage(this, languageCode)
                        LanguageUtil.restartApp(this)
                    }
                )
            }
        }
    }
}

/**
 * 设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onModelSettingsClick: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    // 从配置中加载当前值
    var autoSubmit by remember { mutableStateOf(AppConfig.getAutoSubmit()) }
    var autoCopy by remember { mutableStateOf(AppConfig.getAutoCopy()) }
    var showQuestion by remember { mutableStateOf(AppConfig.getShowAnswerCardQuestion()) }
    var showOptions by remember { mutableStateOf(AppConfig.getShowAnswerCardOptions()) }

    // 采集模式设置
    var captureMode by remember { mutableStateOf(AppConfig.getCaptureMode()) }
    var isAccessibilityEnabled by remember { mutableStateOf(ScreenReaderService.isActive) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从无障碍设置页返回时自动刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Lifecycle.Event.ON_RESUME == event) {
                isAccessibilityEnabled = ScreenReaderService.isActive
                // 无障碍服务未启用时，自动回退到截图模式
                if (!isAccessibilityEnabled && captureMode == AppConfig.CAPTURE_MODE_ACCESSIBILITY) {
                    captureMode = AppConfig.CAPTURE_MODE_SCREENSHOT
                    AppConfig.saveCaptureMode(AppConfig.CAPTURE_MODE_SCREENSHOT)
                    Toast.makeText(context, context.getString(R.string.accessibility_fallback_to_screenshot), Toast.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 并发设置
    var parallelMode by remember { mutableStateOf(AppConfig.isParallelModeEnabled()) }
    var maxConcurrency by remember { mutableStateOf(AppConfig.getMaxConcurrency().toFloat()) }

    // 测试状态
    var llmTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var vlmTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var searchTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    val coroutineScope = rememberCoroutineScope()

    // 悬浮窗外观设置
    var floatButtonSize by remember { mutableStateOf(AppConfig.getFloatButtonSize().toFloat()) }
    var floatButtonAlpha by remember { mutableStateOf(AppConfig.getFloatButtonAlpha()) }
    var floatCardAlpha by remember { mutableStateOf(AppConfig.getFloatCardAlpha()) }

    // 快捷按钮布局模式
    var quickButtonLayout by remember { mutableStateOf(AppConfig.getQuickButtonLayout()) }

    // 隐身模式设置
    var stealthMode by remember { mutableStateOf(AppConfig.isStealthModeEnabled()) }

    // 暗色模式设置：0=跟随系统, 1=亮色, 2=暗色
    var darkMode by remember { mutableStateOf(ThemeState.darkMode) }

    // 语言设置状态
    var showRestartDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    val currentLanguage = LanguageUtil.getCurrentLanguage()
    val isDark = LocalIsDarkMode.current

    Scaffold(
        topBar = {
            TopBarWithBack(
                title = stringResource(R.string.settings_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) PremiumBgDark else PremiumBgLight)
                .padding(paddingValues)
                .padding(horizontal = Spacing.xxl)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.lg))

            // Model settings entry — HighlightCard
            HighlightCard(
                title = stringResource(R.string.model_settings_card_title),
                subtitle = stringResource(R.string.model_settings_card_desc),
                onClick = onModelSettingsClick,
                modifier = Modifier.padding(bottom = Spacing.xxl)
            )

            // ── General Settings ──
            SectionLabel(stringResource(R.string.settings_title))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SettingItem(
                    title = stringResource(R.string.setting_auto_submit),
                    description = stringResource(R.string.setting_auto_submit_desc),
                    checked = autoSubmit,
                    onCheckedChange = { autoSubmit = it; AppConfig.saveAutoSubmit(it) }
                )
                SettingItem(
                    title = stringResource(R.string.setting_auto_copy),
                    description = stringResource(R.string.setting_auto_copy_desc),
                    checked = autoCopy,
                    onCheckedChange = { autoCopy = it; AppConfig.saveAutoCopy(it) }
                )
            }

            // ── Capture Mode ──
            SectionLabel(stringResource(R.string.setting_capture_mode))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                Text(
                    text = stringResource(R.string.setting_capture_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PremiumChip(
                        text = stringResource(R.string.capture_mode_screenshot),
                        selected = captureMode == AppConfig.CAPTURE_MODE_SCREENSHOT,
                        onClick = {
                            captureMode = AppConfig.CAPTURE_MODE_SCREENSHOT
                            AppConfig.saveCaptureMode(AppConfig.CAPTURE_MODE_SCREENSHOT)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PremiumChip(
                        text = stringResource(R.string.capture_mode_accessibility),
                        selected = captureMode == AppConfig.CAPTURE_MODE_ACCESSIBILITY,
                        onClick = {
                            captureMode = AppConfig.CAPTURE_MODE_ACCESSIBILITY
                            AppConfig.saveCaptureMode(AppConfig.CAPTURE_MODE_ACCESSIBILITY)
                            // 如果无障碍服务未开启，自动跳转到系统设置
                            if (!isAccessibilityEnabled) {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 无障碍模式提示
                if (captureMode == AppConfig.CAPTURE_MODE_ACCESSIBILITY) {
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(Spacing.sm)
                                .clip(CircleShape)
                                .background(if (isAccessibilityEnabled) SuccessGreen else ErrorRed)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = if (isAccessibilityEnabled)
                                stringResource(R.string.accessibility_status_enabled)
                            else
                                stringResource(R.string.accessibility_status_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAccessibilityEnabled) SuccessGreen else ErrorRed
                        )
                    }
                    if (!isAccessibilityEnabled) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.accessibility_enable_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = PremiumPrimary,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // ── Parallel Mode + Connection Test ──
            SectionLabel(stringResource(R.string.setting_parallel_title))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SettingItem(
                    title = stringResource(R.string.setting_parallel_mode),
                    description = stringResource(R.string.setting_parallel_mode_desc),
                    checked = parallelMode,
                    onCheckedChange = {
                        parallelMode = it
                        AppConfig.saveParallelMode(it)
                    }
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.setting_max_concurrency, maxConcurrency.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextDarkPrimary else TextDark
                )
                PremiumSlider(
                    value = maxConcurrency,
                    onValueChange = { maxConcurrency = it; AppConfig.saveMaxConcurrency(it.toInt()) },
                    valueRange = 1f..50f,
                    steps = 48,
                    valueFormatter = { "${it.toInt()} 题" },
                    enabled = parallelMode
                )
                if (maxConcurrency > 20) {
                    Text(
                        text = stringResource(R.string.setting_concurrency_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }

                // 分隔线
                Spacer(Modifier.height(Spacing.lg))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(if (isDark) GlassDarkBorder else InputBorder)
                )
                Spacer(Modifier.height(Spacing.lg))

                // 连接测试
                Text(
                    text = stringResource(R.string.button_test_connection),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextDarkPrimary else TextDark
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    AnimatedButton(
                        text = if (llmTestState is TestConnectionState.Testing) "..." else stringResource(R.string.setting_test_llm),
                        onClick = {
                            llmTestState = TestConnectionState.Testing
                            coroutineScope.launch {
                                val result = OpenAIClient.getInstance().testConcurrency()
                                llmTestState = result.fold(
                                    onSuccess = { TestConnectionState.Success(it) },
                                    onFailure = { TestConnectionState.Error(it.message ?: context.getString(R.string.error_unknown)) }
                                )
                            }
                        },
                        enabled = llmTestState !is TestConnectionState.Testing,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Tonal
                    )
                    AnimatedButton(
                        text = if (vlmTestState is TestConnectionState.Testing) "..." else stringResource(R.string.setting_test_vlm),
                        onClick = {
                            vlmTestState = TestConnectionState.Testing
                            coroutineScope.launch {
                                val result = OpenAIVisionProvider.testConcurrency()
                                vlmTestState = result.fold(
                                    onSuccess = { TestConnectionState.Success(it) },
                                    onFailure = { TestConnectionState.Error(it.message ?: context.getString(R.string.error_unknown)) }
                                )
                            }
                        },
                        enabled = vlmTestState !is TestConnectionState.Testing,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Tonal
                    )
                    AnimatedButton(
                        text = if (searchTestState is TestConnectionState.Testing) "..." else stringResource(R.string.setting_test_search),
                        onClick = {
                            searchTestState = TestConnectionState.Testing
                            coroutineScope.launch {
                                val result = TavilyClient.getInstance().testConcurrency()
                                searchTestState = result.fold(
                                    onSuccess = { TestConnectionState.Success(it) },
                                    onFailure = { TestConnectionState.Error(it.message ?: context.getString(R.string.error_unknown)) }
                                )
                            }
                        },
                        enabled = searchTestState !is TestConnectionState.Testing,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Tonal
                    )
                }

                // 显示测试结果
                Spacer(Modifier.height(Spacing.sm))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    when (val state = llmTestState) {
                        is TestConnectionState.Success -> Text(
                            text = stringResource(R.string.test_result_success, stringResource(R.string.setting_test_llm), state.latencyMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                        is TestConnectionState.Error -> Text(
                            text = stringResource(R.string.test_result_error, stringResource(R.string.setting_test_llm), state.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                        else -> {}
                    }
                    when (val state = vlmTestState) {
                        is TestConnectionState.Success -> Text(
                            text = stringResource(R.string.test_result_success, stringResource(R.string.setting_test_vlm), state.latencyMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                        is TestConnectionState.Error -> Text(
                            text = stringResource(R.string.test_result_error, stringResource(R.string.setting_test_vlm), state.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                        else -> {}
                    }
                    when (val state = searchTestState) {
                        is TestConnectionState.Success -> Text(
                            text = stringResource(R.string.test_result_success, stringResource(R.string.setting_test_search), state.latencyMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                        is TestConnectionState.Error -> Text(
                            text = stringResource(R.string.test_result_error, stringResource(R.string.setting_test_search), state.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                        else -> {}
                    }
                }
            }

            // ── Stealth Mode ──
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SettingItem(
                    title = stringResource(R.string.setting_stealth_mode),
                    checked = stealthMode,
                    onCheckedChange = {
                        stealthMode = it
                        AppConfig.saveStealthMode(it)
                    }
                )
            }

            // ── Display Control ──
            SectionLabel(stringResource(R.string.setting_display_control_title))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SettingItem(
                    title = stringResource(R.string.setting_show_question),
                    description = stringResource(R.string.setting_show_question_desc),
                    checked = showQuestion,
                    onCheckedChange = { showQuestion = it; AppConfig.saveShowAnswerCardQuestion(it) }
                )
                SettingItem(
                    title = stringResource(R.string.setting_show_options),
                    description = stringResource(R.string.setting_show_options_desc),
                    checked = showOptions,
                    onCheckedChange = { showOptions = it; AppConfig.saveShowAnswerCardOptions(it) }
                )
            }

            // ── Floating Window Appearance ──
            SectionLabel(stringResource(R.string.setting_float_window_title))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                // 快捷按钮布局模式
                Text(
                    text = stringResource(R.string.setting_quick_button_layout),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextDarkPrimary else TextDark
                )
                Text(
                    text = stringResource(R.string.setting_quick_button_layout_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PremiumChip(
                        text = stringResource(R.string.quick_button_layout_arc),
                        selected = quickButtonLayout == AppConfig.QUICK_BUTTON_LAYOUT_ARC,
                        onClick = {
                            quickButtonLayout = AppConfig.QUICK_BUTTON_LAYOUT_ARC
                            AppConfig.saveQuickButtonLayout(AppConfig.QUICK_BUTTON_LAYOUT_ARC)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PremiumChip(
                        text = stringResource(R.string.quick_button_layout_horizontal),
                        selected = quickButtonLayout == AppConfig.QUICK_BUTTON_LAYOUT_HORIZONTAL,
                        onClick = {
                            quickButtonLayout = AppConfig.QUICK_BUTTON_LAYOUT_HORIZONTAL
                            AppConfig.saveQuickButtonLayout(AppConfig.QUICK_BUTTON_LAYOUT_HORIZONTAL)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.setting_float_button_size, floatButtonSize.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = if (isDark) TextDarkPrimary else TextDark
                )
                PremiumSlider(
                    value = floatButtonSize,
                    onValueChange = { floatButtonSize = it; AppConfig.saveFloatButtonSize(it.toInt()) },
                    valueRange = 32f..80f,
                    steps = 11,
                    valueFormatter = { "${it.toInt()}dp" }
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.setting_float_button_alpha, (floatButtonAlpha * 100).toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = if (isDark) TextDarkPrimary else TextDark
                )
                PremiumSlider(
                    value = floatButtonAlpha,
                    onValueChange = { floatButtonAlpha = it; AppConfig.saveFloatButtonAlpha(it) },
                    valueRange = 0.1f..1.0f,
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.setting_float_card_alpha, (floatCardAlpha * 100).toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = if (isDark) TextDarkPrimary else TextDark
                )
                PremiumSlider(
                    value = floatCardAlpha,
                    onValueChange = { floatCardAlpha = it; AppConfig.saveFloatCardAlpha(it) },
                    valueRange = 0.1f..1.0f,
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )
            }

            // ── Theme ──
            SectionLabel(stringResource(R.string.setting_theme_title))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                PremiumRadioOption(
                    text = stringResource(R.string.setting_theme_system),
                    selected = darkMode == 0,
                    onClick = { if (darkMode != 0) { darkMode = 0; ThemeState.update(0) } }
                )
                PremiumRadioOption(
                    text = stringResource(R.string.setting_theme_light),
                    selected = darkMode == 1,
                    onClick = { if (darkMode != 1) { darkMode = 1; ThemeState.update(1) } }
                )
                PremiumRadioOption(
                    text = stringResource(R.string.setting_theme_dark),
                    selected = darkMode == 2,
                    onClick = { if (darkMode != 2) { darkMode = 2; ThemeState.update(2) } }
                )
            }

            // ── Language ──
            SectionLabel(stringResource(R.string.about_language_title))
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xxl)) {
                PremiumRadioOption(
                    text = stringResource(R.string.about_language_chinese),
                    selected = currentLanguage == AppConfig.LANGUAGE_ZH,
                    onClick = {
                        if (currentLanguage != AppConfig.LANGUAGE_ZH) {
                            selectedLanguage = AppConfig.LANGUAGE_ZH
                            showRestartDialog = true
                        }
                    }
                )
                PremiumRadioOption(
                    text = stringResource(R.string.about_language_english),
                    selected = currentLanguage == AppConfig.LANGUAGE_EN,
                    onClick = {
                        if (currentLanguage != AppConfig.LANGUAGE_EN) {
                            selectedLanguage = AppConfig.LANGUAGE_EN
                            showRestartDialog = true
                        }
                    }
                )
            }

            Spacer(Modifier.height(Spacing.xxxl))
        }
    }

    // Restart dialog
    if (showRestartDialog && selectedLanguage != null) {
        PremiumDialog(
            onDismiss = { showRestartDialog = false; selectedLanguage = null },
            title = stringResource(R.string.about_restart_dialog_title),
            message = stringResource(R.string.about_restart_dialog_message),
            confirmText = stringResource(R.string.button_confirm),
            onConfirm = { selectedLanguage?.let { onLanguageChange(it) } },
            dismissText = stringResource(R.string.button_cancel),
            onDismissAction = { showRestartDialog = false; selectedLanguage = null }
        )
    }
}
