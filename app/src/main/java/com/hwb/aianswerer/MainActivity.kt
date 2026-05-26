package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.AnimatedButton
import com.hwb.aianswerer.ui.components.ButtonVariant
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.ui.components.GlassInfoCard
import com.hwb.aianswerer.ui.components.BtnRadius
import com.hwb.aianswerer.ui.components.ButtonMinHeight
import com.hwb.aianswerer.ui.components.IconBtnRadius
import com.hwb.aianswerer.ui.components.InfoCard
import com.hwb.aianswerer.ui.components.PremiumChip
import com.hwb.aianswerer.ui.components.PremiumDialog
import com.hwb.aianswerer.ui.components.SectionLabel
import com.hwb.aianswerer.ui.dialogs.LanguageSelectionDialog
import com.hwb.aianswerer.ui.dialogs.ModelSetupReminderDialog
import com.hwb.aianswerer.ui.theme.*
import com.hwb.aianswerer.utils.LanguageUtil

/**
 * 主界面 — 权限管理、答题设置、启动/停止悬浮窗服务。
 *
 * 权限获取顺序：
 *   1. 先检查 API 配置是否完整（未配置则提示去设置）
 *   2. 再请求 SYSTEM_ALERT_WINDOW（悬浮窗权限）
 *   3. 最后请求 MediaProjection（屏幕截图权限）
 *   悬浮窗权限必须先于截图权限，因为用户可能先同意截图再拒绝悬浮窗，
 *   导致拿到了截图 data 但无法启动服务。
 *
 * Dialog 队列：
 *   首次启动和 API 未配置时可能同时触发语言选择和模型设置提醒。
 *   使用 FIFO 队列确保同一时间只有一个 Dialog 可见。
 */
class MainActivity : BaseActivity() {

    private var isAnswerModeActive by mutableStateOf(false)
    private var showStopConfirmDialog by mutableStateOf(false)
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private lateinit var defaultQuestionType: String
    private var selectedQuestionTypes by mutableStateOf<Set<String>>(emptySet())
    private var cropMode by mutableStateOf(AppConfig.CROP_MODE_FULL)

    // Dialog状态管理
    private var showLanguageDialog by mutableStateOf(false)
    private var showModelSetupDialog by mutableStateOf(false)
    // dialogQueue: 用于顺序显示多个Dialog，避免同时弹出
    private var dialogQueue = mutableStateListOf<String>()

    companion object {
        const val DIALOG_LANGUAGE = "language"
        const val DIALOG_MODEL_SETUP = "model_setup"
    }

    // 截图权限请求
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            screenCaptureResultCode = result.resultCode
            screenCaptureData = result.data

            // 检查悬浮窗权限
            if (checkOverlayPermission()) {
                startAnswerMode()
            } else {
                requestOverlayPermission()
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_permission_capture_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            // 如果已经有截图权限，直接启动
            if (screenCaptureResultCode != null) {
                startAnswerMode()
            } else {
                requestScreenCapturePermission()
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_permission_overlay_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 加载答题设置
        selectedQuestionTypes = AppConfig.getQuestionTypes()
        cropMode = AppConfig.getCropMode()

        // 检查并添加Dialog到队列
        checkAndQueueDialogs()

        setContent {
            AIAnswererTheme {
                MainScreen(
                    isAnswerModeActive = isAnswerModeActive,
                    selectedQuestionTypes = selectedQuestionTypes,
                    cropMode = cropMode,
                    showLanguageDialog = showLanguageDialog,
                    showModelSetupDialog = showModelSetupDialog,
                    onToggleAnswerMode = {
                        if (isAnswerModeActive) {
                            showStopConfirmDialog = true
                        } else {
                            checkAndRequestPermissions()
                        }
                    },
                    onQuestionTypesChanged = { types ->
                        selectedQuestionTypes = types
                        AppConfig.saveQuestionTypes(types)
                    },
                    onCropModeChanged = { mode ->
                        cropMode = mode
                        AppConfig.saveCropMode(mode)
                    },
                    onLanguageDialogDismiss = { dismissLanguageDialog() },
                    onLanguageConfirmed = { handleLanguageConfirmed() },
                    onModelSetupDismiss = { dismissModelSetupDialog() },
                    onGoToSettings = { navigateToModelSettings() },
                    onMenuItemClick = { menuItem ->
                        when (menuItem) {
                            MenuItem.SETTINGS -> {
                                startActivity(Intent(this, SettingsActivity::class.java))
                            }

                            MenuItem.ABOUT -> {
                                startActivity(Intent(this, AboutActivity::class.java))
                            }
                        }
                    }
                )

                // 停止答题确认对话框
                if (showStopConfirmDialog) {
                    PremiumDialog(
                        onDismiss = { showStopConfirmDialog = false },
                        title = stringResource(R.string.stop_confirm_title),
                        message = stringResource(R.string.stop_confirm_message),
                        confirmText = stringResource(R.string.button_stop_mode),
                        onConfirm = {
                            showStopConfirmDialog = false
                            stopAnswerMode()
                        },
                        dismissText = stringResource(R.string.button_cancel),
                        onDismissAction = { showStopConfirmDialog = false }
                    )
                }
            }
        }
    }

    /**
     * 检查并请求所需权限
     */
    private fun checkAndRequestPermissions() {
        // 首先检查模型是否已配置
        if (!AppConfig.isApiConfigValid()) {
            Toast.makeText(
                this,
                getString(R.string.toast_model_not_configured),
                Toast.LENGTH_LONG
            ).show()
            // 显示模型设置提醒Dialog
            if (!dialogQueue.contains(DIALOG_MODEL_SETUP)) {
                dialogQueue.add(DIALOG_MODEL_SETUP)
                processDialogQueue()
            }
            return
        }

        // 先检查悬浮窗权限
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        // 再检查截图权限
        requestScreenCapturePermission()
    }

    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    /**
     * 请求截图权限
     */
    private fun requestScreenCapturePermission() {
        val screenCaptureManager = ScreenCaptureManager(this)
        val intent = screenCaptureManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    /**
     * 启动答题模式
     */
    private fun startAnswerMode() {
        // 屏幕读取模式下，无障碍服务未启用时禁止进入答题
        if (AppConfig.isAccessibilityCaptureMode() && !ScreenReaderService.isActive) {
            Toast.makeText(this, getString(R.string.accessibility_service_not_enabled), Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, FloatingWindowService::class.java).apply {
            val resultCode = screenCaptureResultCode
            val data = screenCaptureData
            if (resultCode != null && data != null) {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            // 传递答题设置
            putStringArrayListExtra("questionTypes", ArrayList(selectedQuestionTypes))
            putExtra("cropMode", cropMode)
        }

        // Android 8.0+ 使用 startForegroundService，否则使用 startService
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_mode_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            return
        }

        isAnswerModeActive = true
        Toast.makeText(this, getString(R.string.toast_mode_started), Toast.LENGTH_SHORT).show()

        // 将应用移至后台
        moveTaskToBack(true)
    }

    /**
     * 停止答题模式
     */
    private fun stopAnswerMode() {
        stopService(Intent(this, FloatingWindowService::class.java))
        isAnswerModeActive = false
        screenCaptureResultCode = null
        screenCaptureData = null
        Toast.makeText(this, getString(R.string.toast_mode_stopped), Toast.LENGTH_SHORT).show()
    }

    // ========== Dialog管理方法 ==========

    /**
     * 检查并添加Dialog到队列
     */
    private fun checkAndQueueDialogs() {
        when {
            AppConfig.isFirstLaunch() -> {
                dialogQueue.add(DIALOG_LANGUAGE)
            }

            !AppConfig.isApiConfigValid() -> {
                dialogQueue.add(DIALOG_MODEL_SETUP)
            }
        }
        processDialogQueue()
    }

    /**
     * 处理Dialog队列，确保一个Dialog显示完成后才显示下一个
     */
    private fun processDialogQueue() {
        if (dialogQueue.isNotEmpty()) {
            val nextDialog = dialogQueue.first()
            when (nextDialog) {
                DIALOG_LANGUAGE -> showLanguageDialog = true
                DIALOG_MODEL_SETUP -> showModelSetupDialog = true
            }
        }
    }

    /**
     * 关闭语言选择Dialog
     */
    private fun dismissLanguageDialog() {
        showLanguageDialog = false
        dialogQueue.remove(DIALOG_LANGUAGE)
        processDialogQueue()
    }

    /**
     * 处理语言选择确认后的操作
     */
    private fun handleLanguageConfirmed() {
        dismissLanguageDialog()

        // 如果是首次启动，语言选择完成后添加模型设置提醒
        if (dialogQueue.isEmpty() && !AppConfig.isApiConfigValid()) {
            dialogQueue.add(DIALOG_MODEL_SETUP)
        }

        // 重启Activity以应用语言设置
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * 关闭模型设置提醒Dialog
     */
    private fun dismissModelSetupDialog() {
        showModelSetupDialog = false
        dialogQueue.remove(DIALOG_MODEL_SETUP)
        processDialogQueue()
    }

    /**
     * 跳转到模型设置页面
     */
    private fun navigateToModelSettings() {
        dismissModelSetupDialog()
        startActivity(Intent(this, ModelSettingsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        // 双向同步：Activity 重建或 Service 被系统杀死后，UI 状态与实际 Service 状态保持一致
        if (isAnswerModeActive != FloatingWindowService.isRunning) {
            isAnswerModeActive = FloatingWindowService.isRunning
            if (!FloatingWindowService.isRunning) {
                screenCaptureResultCode = null
                screenCaptureData = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注意：不在这里停止服务
        // 用户按返回键退出主界面时，悬浮窗服务应该继续运行
        // 只有用户主动点击"停止"按钮才停止服务
    }
}

/**
 * 菜单项枚举
 */
enum class MenuItem {
    SETTINGS,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun MainScreen(
    isAnswerModeActive: Boolean = false,
    selectedQuestionTypes: Set<String> = setOf("单选题"),
    cropMode: String = AppConfig.CROP_MODE_FULL,
    showLanguageDialog: Boolean = false,
    showModelSetupDialog: Boolean = false,
    onToggleAnswerMode: () -> Unit = {},
    onQuestionTypesChanged: (Set<String>) -> Unit = {},
    onCropModeChanged: (String) -> Unit = {},
    onLanguageDialogDismiss: () -> Unit = {},
    onLanguageConfirmed: () -> Unit = {},
    onModelSetupDismiss: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
    onMenuItemClick: (MenuItem) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current

    // Q-bouncy spring: elastic overshoot, snappy settle
    val bouncySpring = spring<Float>(dampingRatio = 0.35f, stiffness = 450f)

    // Menu state — hoisted to top level so Popup isn't clipped
    var menuExpanded by remember { mutableStateOf(false) }

    // Page-level fade-in with Q-bounce
    var pageAlpha by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { pageAlpha = 1f }
    val pageFade by animateFloatAsState(
        targetValue = pageAlpha,
        animationSpec = bouncySpring,
        label = "page_fade"
    )

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pageFade }) {
        // ── Apple-style Background ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) listOf(PremiumBgDark, PremiumBgDark) else listOf(PremiumBgLight, PremiumBgLightEnd)
                    )
                )
        ) {
            // Ambient glow orbs — responsive sizing
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            Box(
                modifier = Modifier
                    .size(screenWidth * 0.5f)
                    .align(Alignment.TopEnd)
                    .offset(x = (-30).dp, y = 80.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(WarmGlow.copy(alpha = if (isDark) 0.08f else 0.05f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(screenWidth * 0.4f)
                    .align(Alignment.CenterStart)
                    .offset(x = (-10).dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(IndigoGlow.copy(alpha = if (isDark) 0.10f else 0.05f), Color.Transparent)
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header: Apple-style generous spacing ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xxxl + Spacing.xxl, bottom = Spacing.xxl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.main_title),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (isDark) TextDarkPrimary else TextDark
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.main_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDark) TextDarkSecondary else TextSecondary
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                var menuPressed by remember { mutableStateOf(false) }
                val menuScale by animateFloatAsState(
                    targetValue = if (menuPressed) 0.92f else 1f,
                    animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
                    label = "menu_scale"
                )
                Box(
                    modifier = Modifier
                        .size(Spacing.xxxl + Spacing.md)
                        .graphicsLayer {
                            scaleX = menuScale
                            scaleY = menuScale
                        }
                        .clip(RoundedCornerShape(IconBtnRadius))
                        .background(if (isDark) GlassDark else InputBackground)
                        .clickable {
                            menuPressed = true
                            menuExpanded = !menuExpanded
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LocalIcons.MoreVert,
                        contentDescription = stringResource(R.string.cd_menu_button),
                        tint = if (isDark) TextDarkSecondary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Auto-release menu press animation
                LaunchedEffect(menuPressed) {
                    if (menuPressed) {
                        kotlinx.coroutines.delay(120)
                        menuPressed = false
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xxl)
            ) {
                Spacer(Modifier.height(Spacing.sm))

                // ── Glass Status Card ──
                val pulseAlpha by rememberInfiniteTransition().animateFloat(
                    initialValue = 0.6f, targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(animation = tween(1200, easing = EaseInOutCubic), repeatMode = RepeatMode.Reverse),
                    label = "pulse"
                )
                GlassInfoCard(modifier = Modifier.padding(bottom = Spacing.xl)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer { alpha = if (isAnswerModeActive) pulseAlpha else 1f }
                                    .then(
                                        if (isAnswerModeActive) {
                                            val d = androidx.compose.ui.platform.LocalDensity.current
                                            Modifier.primaryGradient(RoundedCornerShape(14.dp), 14.dp, shadowElevation = with(d) { 6.dp.toPx() }, shadowColor = PremiumPrimary.copy(alpha = 0.08f))
                                        } else Modifier.background(
                                            if (isDark) Color(0xFFFCA731).copy(alpha = 0.2f) else AccentGold.copy(alpha = 0.15f),
                                            RoundedCornerShape(14.dp)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAnswerModeActive) LocalIcons.Search else LocalIcons.Search,
                                    contentDescription = null,
                                    tint = if (isAnswerModeActive) Color.White
                                           else if (isDark) Color(0xFFFCA731) else AccentGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(Spacing.lg))
                            Column {
                                Text(
                                    text = if (isAnswerModeActive)
                                        stringResource(R.string.status_running)
                                    else stringResource(R.string.status_stopped),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) TextDarkPrimary else TextDark
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = if (isAnswerModeActive)
                                        stringResource(R.string.status_running_desc)
                                    else stringResource(R.string.status_stopped_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextDarkSecondary else TextTertiary
                                )
                            }
                        }
                    }

                // ── Usage Guide ──
                Spacer(Modifier.height(Spacing.sm))
                Box(modifier = Modifier.padding(bottom = Spacing.xl)) {
                    UsageGuideCard(context = context)
                }

                // ── Session Settings ──
                SessionSettingsCard(
                    selectedQuestionTypes = selectedQuestionTypes,
                    cropMode = cropMode,
                    onQuestionTypesChanged = onQuestionTypesChanged,
                    onCropModeChanged = onCropModeChanged,
                    enabled = !isAnswerModeActive
                )

                Spacer(Modifier.height(Spacing.xxxl))
            }

            // ── CTA Button ──
            val btnInteractionSource = remember { MutableInteractionSource() }
            var btnPressed by remember { mutableStateOf(false) }
            LaunchedEffect(btnInteractionSource) {
                btnInteractionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> btnPressed = true
                        is PressInteraction.Cancel -> btnPressed = false
                        is PressInteraction.Release -> { /* auto-release */ }
                    }
                }
            }
            val btnScale by animateFloatAsState(
                targetValue = if (btnPressed) 0.94f else 1f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
                label = "cta_scale"
            )
            val btnTranslateY by animateFloatAsState(
                targetValue = if (btnPressed) 2f else 0f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
                label = "cta_translate"
            )
            val ctaShadowPx = with(LocalDensity.current) {
                (if (btnPressed) 3.dp else if (isAnswerModeActive) 4.dp else 10.dp).toPx()
            }
            val ctaShape = RoundedCornerShape(BtnRadius)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxl, vertical = Spacing.lg)
                    .then(
                        if (isAnswerModeActive) {
                            if (isDark) Modifier
                                .drawWithCache {
                                    val corner = CornerRadius(BtnRadius.toPx())
                                    val elev = ctaShadowPx
                                    val shadowAlpha = 0.15f
                                    onDrawBehind {
                                        if (elev > 0f) drawGlassShadow(this, corner, elev, ErrorRed.copy(alpha = shadowAlpha))
                                    }
                                }
                                .clip(ctaShape)
                                .drawBehind {
                                    val corner = CornerRadius(BtnRadius.toPx())
                                    drawRoundRect(color = ErrorRed.copy(alpha = 0.12f), cornerRadius = corner)
                                    drawRoundRect(color = ErrorRed.copy(alpha = 0.18f), cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
                                }
                            else Modifier
                                .drawWithCache {
                                    val corner = CornerRadius(BtnRadius.toPx())
                                    val elev = ctaShadowPx
                                    val shadowAlpha = 0.08f
                                    onDrawBehind {
                                        if (elev > 0f) drawGlassShadow(this, corner, elev, ErrorRed.copy(alpha = shadowAlpha))
                                    }
                                }
                                .clip(ctaShape)
                                .drawBehind {
                                    val corner = CornerRadius(BtnRadius.toPx())
                                    drawRoundRect(color = ErrorRed.copy(alpha = 0.06f), cornerRadius = corner)
                                    drawRoundRect(color = ErrorRed.copy(alpha = 0.12f), cornerRadius = corner, style = Stroke(0.5.dp.toPx()))
                                }
                        } else {
                            Modifier
                                .drawWithCache {
                                    val corner = CornerRadius(BtnRadius.toPx())
                                    val glowAlpha = if (isDark) 0.40f else 0.25f
                                    onDrawBehind {
                                        drawGlassShadow(this, corner, ctaShadowPx, PremiumPrimary.copy(alpha = glowAlpha))
                                    }
                                }
                                .primaryGlowGradient(ctaShape, BtnRadius)
                        }
                    )
                    .graphicsLayer {
                        scaleX = btnScale
                        scaleY = btnScale
                        translationY = btnTranslateY
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                    }
                    .clickable(
                        interactionSource = btnInteractionSource,
                        indication = LocalIndication.current
                    ) { onToggleAnswerMode() }
                    .heightIn(min = ButtonMinHeight),
                contentAlignment = Alignment.Center
            ) {
                    Text(
                        text = if (isAnswerModeActive)
                            stringResource(R.string.button_stop_mode)
                        else stringResource(R.string.button_start_mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAnswerModeActive) ErrorRed else Color.White
                    )
            }

            // Auto-release for CTA bounce-back
            LaunchedEffect(btnPressed) {
                if (btnPressed) {
                    kotlinx.coroutines.delay(120)
                    btnPressed = false
                }
            }
        }

        // ── Dropdown overlay — rendered last so it's on top ──
        AnimatedVisibility(
            visible = menuExpanded,
            enter = scaleIn(
                initialScale = 0.85f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f)
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.40f, stiffness = 400f)),
            exit = scaleOut(
                targetScale = 0.85f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = 0.50f, stiffness = 500f)
            ) + fadeOut(animationSpec = tween(80))
        ) {
            // Full-screen transparent backdrop — tap anywhere to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { menuExpanded = false }
            ) {
                // The menu itself — positioned top-end, offset below the button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-16).dp, y = 100.dp)
                        .width(Spacing.xxxl * 5)
                        .shadowElevated(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isDark)
                                Modifier
                                    .background(GlassDark.copy(alpha = 0.14f))
                                    .border(0.5.dp, GlassDarkBorder.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                            else
                                Modifier
                                    .background(GlassWhiteBorder)
                                    .border(0.5.dp, GlassWhiteBorder, RoundedCornerShape(16.dp))
                        )
                        // Prevent tap on menu from propagating to backdrop
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { }
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { menuExpanded = false; onMenuItemClick(MenuItem.SETTINGS) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.menu_settings),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDark) TextDarkPrimary else TextDark
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(0.5.dp)
                                .background(if (isDark) GlassDarkBorder else InputBorder)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { menuExpanded = false; onMenuItemClick(MenuItem.ABOUT) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.menu_about),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDark) TextDarkPrimary else TextDark
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = onLanguageDialogDismiss,
            onLanguageConfirmed = onLanguageConfirmed
        )
    }
    if (showModelSetupDialog) {
        ModelSetupReminderDialog(
            onDismiss = onModelSetupDismiss,
            onGoToSettings = onGoToSettings
        )
    }
}

@Composable
fun UsageGuideCard(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkMode.current
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = 300f),
        label = "expand_icon"
    )

    InfoCard(modifier = Modifier.padding(bottom = 0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(bottom = if (isExpanded) Spacing.lg else 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.usage_guide_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) TextDarkPrimary else TextDark
            )
            Icon(
                imageVector = LocalIcons.ExpandMore,
                contentDescription = stringResource(if (isExpanded) R.string.cd_collapse else R.string.cd_expand),
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer { rotationZ = rotationAngle },
                tint = if (isDark) TextDarkSecondary else TextTertiary
            )
        }

        FeatureItem(stringResource(R.string.usage_step_0), context)
        FeatureItem(stringResource(R.string.usage_step_1), context)
        FeatureItem(stringResource(R.string.usage_step_2), context)
        FeatureItem(stringResource(R.string.usage_step_3), context)
        FeatureItem(stringResource(R.string.usage_step_4), context)
        FeatureItem(stringResource(R.string.usage_step_5), context)

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.25f, stiffness = 180f)
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.35f, stiffness = 250f)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f)
            ) + fadeOut(animationSpec = tween(80))
        ) {
            Column {
                FeatureItem(
                    context = context,
                    text = stringResource(R.string.usage_step_6_text),
                    urlText = stringResource(R.string.link_close_screen_protection),
                    url = stringResource(R.string.usage_step_6_url)
                )
                FeatureItem(
                    context = context,
                    text = stringResource(R.string.usage_step_7_text),
                    urlText = stringResource(R.string.usage_step_7_link),
                    url = stringResource(R.string.usage_step_7_url)
                )
                FeatureItem(context = context, text = stringResource(R.string.usage_step_8))
            }
        }
    }
}

/**
 * 功能说明列表项
 * @param text 主要文本内容
 * @param context Android上下文，用于打开链接
 * @param urlText 可选的链接文本
 * @param url 可选的链接URL
 */
@Composable
fun FeatureItem(
    text: String,
    context: Context,
    urlText: String? = null,
    url: String? = null
) {
    val isDark = LocalIsDarkMode.current
    val primaryColor = PremiumPrimary
    val textColor = if (isDark) TextDarkPrimary else TextDark
    val bodyMediumStyle = MaterialTheme.typography.bodyMedium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        // 彩色小圆点 bullet
        Box(
            modifier = Modifier
                .padding(top = Spacing.sm, end = Spacing.sm)
                .size(6.dp)
                .background(primaryColor, RoundedCornerShape(3.dp))
        )
        // 如果有链接参数，构建带链接的文本
        if (urlText != null && url != null) {
            val annotatedString = remember(text, urlText, url, primaryColor) {
                buildAnnotatedString {
                    // 添加普通文本
                    append(text)

                    // 添加可点击的链接文本
                    pushStringAnnotation(
                        tag = "URL",
                        annotation = url
                    )
                    withStyle(
                        style = SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(urlText)
                    }
                    pop()
                }
            }

            ClickableText(
                text = annotatedString,
                style = bodyMediumStyle.copy(
                    color = textColor
                ),
                onClick = { offset ->
                    // 检查点击位置是否在URL上
                    annotatedString.getStringAnnotations(
                        tag = "URL",
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let { annotation ->
                        // 打开外部浏览器
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                MyApplication.getString(R.string.toast_unable_to_open_link),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        } else {
            // 没有链接，直接显示文本
            Text(
                text = text,
                style = bodyMediumStyle,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionSettingsCard(
    selectedQuestionTypes: Set<String>,
    cropMode: String,
    onQuestionTypesChanged: (Set<String>) -> Unit,
    onCropModeChanged: (String) -> Unit,
    enabled: Boolean = true
) {
    val allQuestionTypes = listOf(
        stringResource(R.string.question_type_single),
        stringResource(R.string.question_type_multiple),
        stringResource(R.string.question_type_uncertain),
        stringResource(R.string.question_type_blank),
        stringResource(R.string.question_type_essay)
    )

    InfoCard {
        // Section label
        SectionLabel(stringResource(R.string.question_type_label))

        // Question type chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            allQuestionTypes.forEach { type ->
                val isSelected = selectedQuestionTypes.contains(type)
                PremiumChip(
                    text = type,
                    selected = isSelected,
                    onClick = {
                        val newTypes = if (isSelected) {
                            if (selectedQuestionTypes.size > 1) selectedQuestionTypes - type
                            else selectedQuestionTypes
                        } else {
                            selectedQuestionTypes + type
                        }
                        onQuestionTypesChanged(newTypes)
                    },
                    enabled = enabled
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Crop mode
        SectionLabel(stringResource(R.string.crop_mode_label))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            listOf(
                AppConfig.CROP_MODE_FULL to stringResource(R.string.crop_mode_full),
                AppConfig.CROP_MODE_EACH to stringResource(R.string.crop_mode_each),
                AppConfig.CROP_MODE_ONCE to stringResource(R.string.crop_mode_once)
            ).forEach { (mode, label) ->
                PremiumChip(
                    text = label,
                    selected = cropMode == mode,
                    onClick = { onCropModeChanged(mode) },
                    enabled = enabled
                )
            }
        }
    }
}
