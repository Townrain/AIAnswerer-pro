package com.hwb.aianswerer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.api.TavilyClient
import com.hwb.aianswerer.api.vision.OpenAIVisionConfig
import com.hwb.aianswerer.api.vision.OpenAIVisionProvider
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.AnimatedButton
import com.hwb.aianswerer.ui.components.AppTextField
import com.hwb.aianswerer.ui.components.ButtonVariant
import com.hwb.aianswerer.ui.components.GlassInfoCard
import com.hwb.aianswerer.ui.components.InfoCard
import com.hwb.aianswerer.ui.components.PasswordTextField
import com.hwb.aianswerer.ui.components.SectionLabel
import com.hwb.aianswerer.ui.components.SettingItem
import com.hwb.aianswerer.ui.components.TopBarWithBack
import com.hwb.aianswerer.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 模型设置 — API URL / Key / Model 配置 + 连接测试。
 *
 * 配置变更立即写入 MMKV，下次 API 调用自动生效，无需重启 Service。
 */
class ModelSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AIAnswererTheme {
                ModelSettingsScreen(
                    onBackClick = { finish() },
                    onSaveSuccess = {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_settings_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSaveError = {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_settings_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}

/**
 * 测试连接状态
 */
sealed class TestConnectionState {
    object Idle : TestConnectionState()
    object Testing : TestConnectionState()
    data class Success(val latencyMs: Long = 0) : TestConnectionState()
    data class Error(val message: String) : TestConnectionState()
}

/**
 * 模型设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit,
    onSaveError: () -> Unit
) {
    // 从配置中加载当前值
    var apiUrl by remember { mutableStateOf(AppConfig.getApiUrl()) }
    var apiKey by remember { mutableStateOf(AppConfig.getApiKey()) }
    var modelName by remember { mutableStateOf(AppConfig.getModelName()) }
    var thinkingMode by remember { mutableStateOf(AppConfig.getReasoningEffort() != null) }

    // Tavily 配置
    var tavilyEnabled by remember { mutableStateOf(AppConfig.getTavilyEnabled()) }
    var tavilyApiKey by remember { mutableStateOf(AppConfig.getTavilyApiKey()) }
    var regexFilterEnabled by remember { mutableStateOf(AppConfig.isRegexFilterEnabled()) }

    // 视觉模型配置
    var visionEnabled by remember { mutableStateOf(AppConfig.isVisionEnabled()) }
    var visionApiUrl by remember { mutableStateOf(AppConfig.getVisionBaseUrl()) }
    var visionApiKey by remember { mutableStateOf(AppConfig.getVisionApiKey()) }
    var visionModelName by remember { mutableStateOf(AppConfig.getVisionModelName()) }

    // 测试连接状态管理
    var testState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var tavilyTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var visionTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    val coroutineScope = rememberCoroutineScope()

    val isDark = LocalIsDarkMode.current

    Scaffold(
        topBar = {
            TopBarWithBack(
                title = stringResource(R.string.model_settings_title),
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
            // 顶部说明
            GlassInfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                Row {
                    Box(
                        modifier = Modifier
                            .width(Spacing.xs)
                            .heightIn(min = Spacing.xxxl + Spacing.md)
                            .clip(RoundedCornerShape(Spacing.xs))
                            .background(PremiumPrimary)
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = stringResource(R.string.model_settings_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                }
            }

            // LLM 大模型配置
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SectionLabel(stringResource(R.string.model_settings_section_llm))

                Spacer(modifier = Modifier.height(Spacing.lg))

                // API URL输入框
                AppTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    label = stringResource(R.string.label_api_url),
                    placeholder = stringResource(R.string.hint_api_url),
                    isPassword = false,
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // API Key输入框
                PasswordTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = stringResource(R.string.label_api_key),
                    placeholder = stringResource(R.string.hint_api_key),
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 模型名称输入框
                AppTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = stringResource(R.string.label_model_name),
                    placeholder = stringResource(R.string.hint_model_name),
                    isPassword = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 思考模式开关
                SettingItem(
                    title = stringResource(R.string.setting_thinking_mode),
                    description = stringResource(R.string.setting_thinking_mode_desc),
                    checked = thinkingMode,
                    onCheckedChange = {
                        thinkingMode = it
                        AppConfig.saveReasoningEffort(it)
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // 测试连接按钮
                AnimatedButton(
                    text = if (testState is TestConnectionState.Testing) stringResource(R.string.button_testing) else stringResource(R.string.button_test_connection),
                    onClick = {
                        if (testState is TestConnectionState.Testing) return@AnimatedButton
                        coroutineScope.launch {
                            testState = TestConnectionState.Testing
                            val result = OpenAIClient.getInstance().testConnection(apiUrl, apiKey, modelName)
                            result.onSuccess {
                                testState = TestConnectionState.Success()
                            }.onFailure { error ->
                                testState = TestConnectionState.Error(error.message ?: MyApplication.getString(R.string.error_unknown))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Tonal,
                    enabled = testState !is TestConnectionState.Testing
                )

                if (testState is TestConnectionState.Success) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.toast_connection_success),
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                } else if (testState is TestConnectionState.Error) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.toast_connection_failed).format((testState as TestConnectionState.Error).message),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 保存按钮
                AnimatedButton(
                    text = stringResource(R.string.button_save),
                    onClick = {
                        if (apiUrl.isBlank() || apiKey.isBlank() || modelName.isBlank()) {
                            Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_error_empty_fields), Toast.LENGTH_SHORT).show()
                        } else if (!apiUrl.startsWith("http")) {
                            Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_error_invalid_url), Toast.LENGTH_SHORT).show()
                        } else {
                            AppConfig.saveApiUrl(apiUrl)
                            AppConfig.saveApiKey(apiKey)
                            AppConfig.saveModelName(modelName)
                            onSaveSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Primary
                )
            }

            // ========== Tavily 联网搜索配置 ==========
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SectionLabel(stringResource(R.string.tavily_settings_title))
                Text(
                    text = stringResource(R.string.tavily_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )

                // 启用开关
                SettingItem(
                    title = stringResource(R.string.tavily_enable_label),
                    description = stringResource(R.string.tavily_enable_desc),
                    checked = tavilyEnabled,
                    onCheckedChange = {
                        tavilyEnabled = it
                        AppConfig.saveTavilyEnabled(it)
                    }
                )

                // 多题正则过滤开关（启用时显示）
                if (tavilyEnabled) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    SettingItem(
                        title = stringResource(R.string.setting_regex_filter),
                        description = stringResource(R.string.setting_regex_filter_desc),
                        checked = regexFilterEnabled,
                        onCheckedChange = {
                            regexFilterEnabled = it
                            AppConfig.saveRegexFilterEnabled(it)
                        }
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    PasswordTextField(
                        value = tavilyApiKey,
                        onValueChange = { tavilyApiKey = it },
                        label = stringResource(R.string.label_tavily_api_key),
                        placeholder = stringResource(R.string.hint_tavily_api_key),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 测试连接按钮
                AnimatedButton(
                    text = if (tavilyTestState is TestConnectionState.Testing) stringResource(R.string.button_testing) else stringResource(R.string.button_test_connection),
                    onClick = {
                        if (tavilyTestState is TestConnectionState.Testing) return@AnimatedButton
                        coroutineScope.launch {
                            tavilyTestState = TestConnectionState.Testing
                            val result = TavilyClient.getInstance().testConnection(tavilyApiKey)
                            result.onSuccess {
                                tavilyTestState = TestConnectionState.Success()
                            }.onFailure { error ->
                                tavilyTestState = TestConnectionState.Error(error.message ?: MyApplication.getString(R.string.error_unknown))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Tonal,
                    enabled = tavilyEnabled && tavilyTestState !is TestConnectionState.Testing
                )

                if (tavilyTestState is TestConnectionState.Success) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(text = stringResource(R.string.toast_connection_success), style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                } else if (tavilyTestState is TestConnectionState.Error) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(text = stringResource(R.string.toast_connection_failed).format((tavilyTestState as TestConnectionState.Error).message), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 保存按钮
                AnimatedButton(
                    text = stringResource(R.string.button_save),
                    onClick = {
                        AppConfig.saveTavilyApiKey(tavilyApiKey)
                        Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Primary,
                    enabled = tavilyEnabled
                )
            }

            // ========== 视觉模型配置 ==========
            InfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                SectionLabel(stringResource(R.string.vision_settings_title))
                Text(
                    text = stringResource(R.string.vision_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextDarkSecondary else TextTertiary,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )

                // 启用开关
                SettingItem(
                    title = stringResource(R.string.vision_enable_label),
                    description = stringResource(R.string.vision_enable_desc),
                    checked = visionEnabled,
                    onCheckedChange = {
                        visionEnabled = it
                        AppConfig.saveVisionEnabled(it)
                    }
                )

                if (visionEnabled) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    AppTextField(
                        value = visionApiUrl,
                        onValueChange = { visionApiUrl = it },
                        label = stringResource(R.string.label_vision_api_url),
                        placeholder = stringResource(R.string.hint_vision_api_url),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    PasswordTextField(
                        value = visionApiKey,
                        onValueChange = { visionApiKey = it },
                        label = stringResource(R.string.label_vision_api_key),
                        placeholder = stringResource(R.string.hint_vision_api_key),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    AppTextField(
                        value = visionModelName,
                        onValueChange = { visionModelName = it },
                        label = stringResource(R.string.label_vision_model),
                        placeholder = stringResource(R.string.hint_vision_model),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 测试连接按钮
                AnimatedButton(
                    text = if (visionTestState is TestConnectionState.Testing) stringResource(R.string.button_testing) else stringResource(R.string.button_test_connection),
                    onClick = {
                        if (visionTestState is TestConnectionState.Testing) return@AnimatedButton
                        coroutineScope.launch {
                            visionTestState = TestConnectionState.Testing
                            val config = OpenAIVisionConfig(baseUrl = visionApiUrl, apiKey = visionApiKey, modelName = visionModelName)
                            // 使用getInstance复用实例，避免每次创建新的OkHttpClient
                            val provider = OpenAIVisionProvider.getInstance(config)
                            val result = provider.testConnection()
                            result.onSuccess {
                                visionTestState = TestConnectionState.Success()
                            }.onFailure { error ->
                                visionTestState = TestConnectionState.Error(error.message ?: MyApplication.getString(R.string.error_unknown))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Tonal,
                    enabled = visionEnabled && visionTestState !is TestConnectionState.Testing
                )

                if (visionTestState is TestConnectionState.Success) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(text = stringResource(R.string.toast_connection_success), style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                } else if (visionTestState is TestConnectionState.Error) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(text = stringResource(R.string.toast_connection_failed).format((visionTestState as TestConnectionState.Error).message), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // 保存按钮
                AnimatedButton(
                    text = stringResource(R.string.button_save),
                    onClick = {
                        if (visionApiUrl.isBlank() || visionApiKey.isBlank() || visionModelName.isBlank()) {
                            Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_error_empty_fields), Toast.LENGTH_SHORT).show()
                        } else if (!visionApiUrl.startsWith("http")) {
                            Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_error_invalid_url), Toast.LENGTH_SHORT).show()
                        } else {
                            AppConfig.saveVisionBaseUrl(visionApiUrl)
                            AppConfig.saveVisionApiKey(visionApiKey)
                            AppConfig.saveVisionModelName(visionModelName)
                            Toast.makeText(MyApplication.getAppContext(), MyApplication.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Primary,
                    enabled = visionEnabled
                )
            }
        }
    }
}
