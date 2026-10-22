package com.yanyuan.toolbox.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku
import com.yanyuan.toolbox.util.PermissionHelper
import com.yanyuan.toolbox.shizuku.ShizukuFileServiceManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.yanyuan.toolbox.R
import com.yanyuan.toolbox.data.AppThemeColor
import com.yanyuan.toolbox.data.AppearanceRepository
import com.yanyuan.toolbox.data.AppearanceState
import com.yanyuan.toolbox.data.FeatureRepository
import com.yanyuan.toolbox.data.GlassOpacityState
import com.yanyuan.toolbox.data.GlassOpacityStore
import com.yanyuan.toolbox.data.InstalledPlugin
import com.yanyuan.toolbox.data.NotificationState
import com.yanyuan.toolbox.data.NotificationStore
import com.yanyuan.toolbox.data.ThemeColorState
import com.yanyuan.toolbox.data.ThemeColorStore
import com.yanyuan.toolbox.data.ThemeMode
import com.yanyuan.toolbox.data.ThemeModeState
import com.yanyuan.toolbox.data.ThemeModeStore
import com.yanyuan.toolbox.data.WindowGlassMode
import com.yanyuan.toolbox.data.WindowGlassState
import com.yanyuan.toolbox.data.WindowGlassStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.yanyuan.toolbox.util.LogCenter
import com.yanyuan.toolbox.util.NotificationHelper
import com.yanyuan.toolbox.ui.theme.monetPrimaryFromBackground
import com.yanyuan.toolbox.data.WelcomeAppearanceState
import com.yanyuan.toolbox.data.WelcomeAppearanceStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ★ 版本号统一在这里，改版本只需改这两行
private const val APP_VERSION_NAME = "0.5.0002-bata"
private const val APP_VERSION_DESC = "正在内测中......"

/**
 * 本版更新内容。
 * 只列对外能感知的重点，按本次改动主题分组。
 */
private val UPDATE_NOTES: List<Pair<String, List<String>>> = listOf(
    "悬浮导航栏（液态玻璃）" to listOf(
        "新增液态玻璃样式：背景折射、模糊与按压放大",
        "平板左侧导轨可切换为全高侧栏：连点左上角应用图标 5 次展开/收起，先向上再展开宽度的分阶段动画"
    ),
    "功能页" to listOf(
        "平板端取消导航栏避让，三个页面整体右移",
        "欢迎字样与卡片左对齐；卡片说明文字行距收紧，保证卡内完整显示"
    ),
    "AI 页" to listOf(
        "加入液态玻璃模式"
    ),
    "日志" to listOf(
        "新增 设置 → 日志 → 导出日志：软件 / 权限 / 插件/ 崩溃 分开记录，可选时长，过滤后合成一个 zip"
    )
)

private data class PluginGuideSection(val title: String, val markdown: String)

/** 往期更新 */
private const val HISTORY_NOTES = """往期更新 0.4.2 -ChatGPT
【插件与安全】
· 无障碍服务与自动化接口改为通用插件能力，服务名称显示为琰鸢工具箱
· 规则文件随插件 ZIP 导入；更新时保留已调整的规则
· 规则匹配引擎由插件包内脚本执行，宿主仅提供节点读取与点击接口

【API 密钥保护】
· API Key 使用 Android Keystore 保护的 AES-GCM 加密后保存在本机
· 密钥数据不参与云备份或设备迁移备份；从旧版本升级时自动加密迁移

往期更新 0.4.1 -ChatGPT
【插件与安全】
· 新增插件介绍与开发接入文档二级页面，支持目录跳转、表格展示、背景切换和文档下载
· 启用大模型、开放插件 AI 授权及程序授权前增加风险提示
· 提醒用户插件可能记录 API 内容，程序授权会消耗 Token 生成每日欢迎词
· 明确说明 API 密钥保存在本机且未做额外加密

往期更新 0.4.0 -DeepSeek & ChatGPT
【AI 页】
· 修复流式输出时整个应用卡顿的问题
· 修复输出时被强行拽回思考过程开头，现在可以自由滑动
· 滚动贴底时自动跟随新内容
· 消息支持编辑、复制和长按光标操作

【功能页】
· 长按卡片可重新授权、卸载及拖动排序

【设置】
· 新增「导入大模型」：平台、密钥、API 地址、同步与检测
· 新增 AI 接口授权：分别为 AI、插件、程序授权
· 设置页重构为「个性化 / 功能 / 关于」
· 打开 AI 对话记录时导航栏自动收回

往期更新 0.3.5 -DeepSeek
· 修复了 ai 页导航栏不沉浸的问题
· 添加了导航栏动画
· 平板端添加了卡片动画
· 平板端提升了 ai 页视觉效果
· 平板端重新设计了欢迎页

往期更新 0.3.1 -DeepSeek
· 修复了关于页动画问题

往期更新 0.3.0 -DeepSeek
· 新增支持平板端，大屏观感更舒适
· 平板端底部导航移至左下角竖排
· 手机 / 平板布局自适应

往期更新 0.2.0 -DeepSeek
【AI 对话】
· 流式响应，逐字输出，思考过程可折叠
· 多会话管理：新建、切换、删除、本地持久化
· 消息分支：编辑问题后重发，可在多版本间切换
· 重试模式：重新回答 / 更详细 / 更精简
· 一键复制全文或选择复制
【深度思考】
· 深度思考开关，注入推理指令
【人格面具】
· 新建、编辑、删除，可设名字、职业、年龄、简介、输出格式
· 会话级绑定，不同对话可用不同人格
【欢迎页】
· 全新开场
· 不同的问候
· 逐字打字机效果
【界面与交互】
· 输入栏沉浸式：键盘弹起贴键盘、收起不回落到屏幕底部
· 键盘弹起时底部导航淡出，收起时淡入
· 消息列表自动滚动到最新，手动上滑时暂停跟随
· Markdown 渲染：标题、列表、引用、代码块、粗体、行内码、链接
【设置】
· 主题色、毛玻璃透明度、深色模式、背景图
· 通知开关
【性能】
· 流式输出合并窗口，长文回复不卡顿
· 消息列表滚动节流，避免动画堆积"""

/** 平板断点：宽度 >= 600dp 视为平板 */
private val SETTINGS_TABLET_BREAKPOINT = 600.dp

/**
 * ★ 平板导航栏从"系统导航栏底部"往上占据的总高度（含底部边距 + pill 高度 + 额外冗余）
 * —— 供导航避让的纵向计算使用（卡片收缩已移除，不再据此收窄）。
 */
private val TABLET_NAV_TOTAL_HEIGHT_ABOVE_NAVBAR = 260.dp

@Composable
fun SettingsScreen(
    performanceMode: Boolean,
    onPerformanceModeChange: (Boolean) -> Unit,
    navigationGlassEnabled: Boolean = true,
    onNavigationGlassChange: (Boolean) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp.dp >= SETTINGS_TABLET_BREAKPOINT

    // 通知开关状态
    val notifVersion = NotificationState.version
    // 欢迎页透明度状态
    val welcomeStore = remember { WelcomeAppearanceStore(context) }
    val welcomeVersion = WelcomeAppearanceState.version
    var welcomeTransparent by remember(welcomeVersion) {
        mutableStateOf(WelcomeAppearanceState.transparent)
    }

    // 启动时把存储值加载到 State（只执行一次）
    LaunchedEffect(Unit) {
        welcomeStore.loadToState()
    }

    var notificationsEnabled by remember(notifVersion) {
        mutableStateOf(NotificationStore.isEnabled(context))
    }

    var shizukuStateVersion by remember { mutableIntStateOf(0) }
    val shizukuScope = rememberCoroutineScope()
    fun refreshShizukuState() {
        shizukuStateVersion++
    }
    val shizukuAvailable = remember(shizukuStateVersion) {
        PermissionHelper.isShizukuAvailable()
    }
    val shizukuGranted = remember(shizukuStateVersion) {
        PermissionHelper.isShizukuGranted()
    }
    val shizukuFileBackendReady = remember(shizukuStateVersion) {
        ShizukuFileServiceManager.isReady()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val binderReceived = Shizuku.OnBinderReceivedListener {
            refreshShizukuState()
        }
        val binderDead = Shizuku.OnBinderDeadListener {
            refreshShizukuState()
        }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PermissionHelper.SHIZUKU_REQUEST_CODE) {
                shizukuScope.launch {
                    if (PermissionHelper.isShizukuGranted()) {
                        ShizukuFileServiceManager.ensureReady(context)
                    }
                    refreshShizukuState()
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshShizukuState()
            }
        }
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    var showAbout by remember { mutableStateOf(false) }
    var showPluginGuide by remember { mutableStateOf(false) }
    var showImportModel by remember { mutableStateOf(false) }
    var showThemeColor by remember { mutableStateOf(false) }
    var showThemeMode by remember { mutableStateOf(false) }
    var showTransparency by remember { mutableStateOf(false) }
    var showWindowGlass by remember { mutableStateOf(false) }
    var showExportLog by remember { mutableStateOf(false) }

    // 子页面按层级返回：插件指南 → 关于 → 设置。
    BackHandler(enabled = showAbout || showPluginGuide || showImportModel) {
        when {
            showPluginGuide -> showPluginGuide = false
            showImportModel -> showImportModel = false
            else -> showAbout = false
        }
    }

    // ★ 把"是否处于全屏子页"上报给 MainActivity
    LaunchedEffect(showAbout, showPluginGuide, showImportModel) {
        onFullscreenChange(showAbout || showPluginGuide || showImportModel)
    }
    DisposableEffect(Unit) {
        onDispose { onFullscreenChange(false) }
    }

    // 通知权限请求
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationStore.setEnabled(context, true)
            notificationsEnabled = true
        } else {
            NotificationStore.setEnabled(context, false)
            notificationsEnabled = false
        }
    }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use {
                AppearanceRepository.saveBackground(context, it)
            }
        } catch (_: Exception) { }
    }

    // ★ 设置页 ↔ 子页（关于 / 导入大模型）带滑动切换动画
    val subPage = when {
        showImportModel -> 2
        showPluginGuide -> 3
        showAbout -> 1
        else -> 0
    }
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState > initialState) {
                (
                        slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { it }
                        ) + fadeIn(tween(220))
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it / 4 }
                        ) + fadeOut(tween(200))
                        )
            } else {
                (
                        slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { -it / 4 }
                        ) + fadeIn(tween(220))
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            targetOffsetX = { it }
                        ) + fadeOut(tween(200))
                        )
            }
        },
        label = "settings-sub"
    ) { page ->
        when (page) {
            1 -> AboutScreen(onBack = { showAbout = false })
            2 -> ImportModelScreen(onBack = { showImportModel = false })
            3 -> PluginGuideScreen(onBack = { showPluginGuide = false })
            else -> SettingsMainContent(
                isTablet = isTablet,
                notificationsEnabled = notificationsEnabled,
                shizukuAvailable = shizukuAvailable,
                shizukuGranted = shizukuGranted,
                shizukuFileBackendReady = shizukuFileBackendReady,
                onShizukuClick = {
                    PermissionHelper.requestShizukuPermission(context)
                    shizukuScope.launch {
                        if (PermissionHelper.isShizukuGranted()) {
                            ShizukuFileServiceManager.ensureReady(context)
                        }
                        refreshShizukuState()
                    }
                },
                onNotificationsChange = { wanted ->
                    if (wanted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = androidx.core.content.ContextCompat
                                .checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                NotificationStore.setEnabled(context, true)
                                notificationsEnabled = true
                            } else {
                                notifPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        } else {
                            NotificationStore.setEnabled(context, true)
                            notificationsEnabled = true
                        }
                    } else {
                        NotificationStore.setEnabled(context, false)
                        notificationsEnabled = false
                        NotificationHelper.cancelAll(context)
                    }
                },
                onThemeColorClick = { showThemeColor = true },
                onThemeModeClick = { showThemeMode = true },
                onTransparencyClick = { showTransparency = true },
                onWindowGlassClick = { showWindowGlass = true },
                onExportLogClick = { showExportLog = true },
                navigationGlassEnabled = navigationGlassEnabled,
                onNavigationGlassChange = onNavigationGlassChange,
                onBackgroundPick = { pickBackground.launch("image/*") },
                onBackgroundClear = { AppearanceRepository.clearBackground(context) },
                onAboutClick = { showAbout = true },
                onPluginGuideClick = { showPluginGuide = true },
                onImportModelClick = { showImportModel = true }
            )
        }
    }

    // 三个对话框保持在最外层
    if (showThemeColor) {
        ThemeColorDialog(onDismiss = { showThemeColor = false })
    }
    if (showThemeMode) {
        ThemeModeDialog(onDismiss = { showThemeMode = false })
    }
    if (showTransparency) {
        TransparencyDialog(
            welcomeTransparent = welcomeTransparent,
            onWelcomeTransparentChange = { wanted ->
                welcomeTransparent = wanted
                welcomeStore.transparent = wanted
            },
            onDismiss = { showTransparency = false }
        )
    }
    if (showWindowGlass) {
        WindowGlassDialog(onDismiss = { showWindowGlass = false })
    }
    if (showExportLog) {
        ExportLogDialog(onDismiss = { showExportLog = false })
    }
}

// =======================================================
//  设置页主内容
// =======================================================
@Composable
private fun SettingsMainContent(
    isTablet: Boolean,
    notificationsEnabled: Boolean,
    shizukuAvailable: Boolean,
    shizukuGranted: Boolean,
    shizukuFileBackendReady: Boolean,
    onShizukuClick: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onImportModelClick: () -> Unit,
    onThemeColorClick: () -> Unit,
    onThemeModeClick: () -> Unit,
    onTransparencyClick: () -> Unit,
    onWindowGlassClick: () -> Unit,
    onExportLogClick: () -> Unit,
    navigationGlassEnabled: Boolean,
    onNavigationGlassChange: (Boolean) -> Unit,
    onBackgroundPick: () -> Unit,
    onBackgroundClear: () -> Unit,
    onAboutClick: () -> Unit,
    onPluginGuideClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val listCoordinatesState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 从设置列表底部到平板侧边导航栏上沿的距离，保留原来的避让范围。
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val navTopFromBottomPx = with(density) {
        (navBarBottom + TABLET_NAV_TOTAL_HEIGHT_ABOVE_NAVBAR).roundToPx()
    }
    // 导航栏避让区的下沿：与 MainActivity 中侧栏的 bottom = navBarBottom + 24.dp 对齐。
    val navBottomFromBottomPx = with(density) { (navBarBottom + 24.dp).roundToPx() }

    // 页面标题仍沿用原本的单独避让；大分组卡片本身不再整体缩进。
    val titleLeftPad = rememberItemLeftPad(listState, "title", isTablet, navTopFromBottomPx)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { listCoordinatesState.value = it },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "title") {
            Text(
                "设置",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = titleLeftPad.value, top = 8.dp, bottom = 8.dp)
            )
        }

        // 大分组卡片保持原来的整体尺寸；每一条内容独立计算避让量。
        item(key = "theme") {
            SettingsGlassGroup(title = "个性化") {
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    ThemeColorRow(onClick = onThemeColorClick)
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    ThemeModeRow(onClick = onThemeModeClick)
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    TransparencyRow(onClick = onTransparencyClick)
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    NavigationStyleRow(
                        liquidGlassEnabled = navigationGlassEnabled,
                        onSelect = onNavigationGlassChange
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    BackgroundPickerRow(onPick = onBackgroundPick, onClear = onBackgroundClear)
                }
            }
        }

        item(key = "feature") {
            SettingsGlassGroup(title = "功能") {
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    ListItem(
                        headlineContent = { Text("接入AI", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("接入自己的模型平台与密钥", fontSize = 12.sp) },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().height(88.dp).clickable { onImportModelClick() }
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    ListItem(
                        headlineContent = { Text("Shizuku 文件权限", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                when {
                                    shizukuFileBackendReady -> "文件后端已连接，可在 Agent 的完全访问模式中访问根目录"
                                    shizukuGranted -> "Shizuku 已授权，但文件后端尚未连接；点击后可重新连接授权"
                                    shizukuAvailable -> "服务已连接，点击向 Shizuku 申请权限"
                                    else -> "请先安装并启动 Shizuku 服务"
                                },
                                fontSize = 12.sp
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = if (shizukuFileBackendReady || shizukuGranted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        trailingContent = {
                            Text(
                                when {
                                    shizukuFileBackendReady -> "已连接"
                                    shizukuGranted -> "已授权"
                                    shizukuAvailable -> "授权"
                                    else -> "未连接"
                                },
                                color = if (shizukuFileBackendReady || shizukuGranted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 12.sp
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clickable(onClick = onShizukuClick)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    SettingsSwitch(
                        Icons.Default.Notifications,
                        "通知提醒",
                        "插件运行状态提示",
                        notificationsEnabled,
                        onNotificationsChange
                    )
                }
            }
        }

        item(key = "pluginGuide") {
            SettingsGlassGroup(title = "插件介绍与制作") {
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    ListItem(
                        headlineContent = { Text("插件介绍与制作", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("权限、安全风险、制作流程与接口接入", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().height(88.dp).clickable(onClick = onPluginGuideClick)
                    )
                }
            }
        }

        item(key = "exportLog") {
            SettingsGlassGroup(title = "日志") {
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    // 格式与「插件介绍与制作」卡片一致（ListItem 单行说明，居中表现相同）。
                    ListItem(
                        headlineContent = { Text("导出日志", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text("软件 / 权限 / 插件/ 崩溃", fontSize = 12.sp)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clickable(onClick = onExportLogClick)
                    )
                }
            }
        }

        item(key = "about") {
            SettingsGlassGroup(title = "关于") {
                SettingsRowNavAvoidance(isTablet, listCoordinatesState, navTopFromBottomPx, navBottomFromBottomPx) {
                    ListItem(
                        headlineContent = { Text("版本 $APP_VERSION_NAME", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(APP_VERSION_DESC, fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().height(88.dp).clickable { onAboutClick() }
                    )
                }
            }
        }
    }
}

/**
 * 单条设置行的平板导航避让：父级大分组卡片保持原宽度，只有当前行缩窄。
 * 行底部进入左侧导航栏的垂直区域时，按重叠量平滑增加 start padding；离开后恢复。
 */
@Composable
private fun SettingsRowNavAvoidance(
    isTablet: Boolean,
    listCoordinatesState: State<LayoutCoordinates?>,
    navTopFromBottomPx: Int,
    navBottomFromBottomPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // ★ 卡片收缩已移除：平板三页改为整体固定右移（MainActivity 的 contentStartInset），
    //   行不再按与导航栏的重叠量做左侧收窄。
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * 分组卡片左侧留白：恒为 0。
 * ★ 卡片收缩已移除 —— 平板三页改为整体固定右移（MainActivity 的 contentStartInset）。
 * 保留签名，调用方无需改动。
 */
@Composable
private fun rememberItemLeftPad(
    listState: LazyListState,
    itemKey: String,
    enabled: Boolean,
    navTopFromBottomPx: Int
): State<Dp> {
    return remember { derivedStateOf { 0.dp } }
}

// =======================================================
//  导航栏样式选择：原版 / 液态玻璃
// =======================================================
@Composable
private fun NavigationStyleRow(
    liquidGlassEnabled: Boolean,
    onSelect: (Boolean) -> Unit
) {
    var showStyleDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .clickable { showStyleDialog = true }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("悬浮导航栏", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                if (liquidGlassEnabled) "当前：液态玻璃" else "当前：毛玻璃",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "打开悬浮导航栏样式选项",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }

    if (showStyleDialog) {
        NavigationStyleDialog(
            liquidGlassEnabled = liquidGlassEnabled,
            onSelect = onSelect,
            onDismiss = { showStyleDialog = false }
        )
    }
}

@Composable
private fun NavigationStyleDialog(
    liquidGlassEnabled: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // 窗口玻璃：与其它窗口一致 —— 玻璃面采样主界面记录层（kyant glass-bottom-sheet 方式）。
    val windowGlassVersion = WindowGlassState.version
    val glassContext = LocalContext.current
    val windowGlassMode = remember(windowGlassVersion) { WindowGlassStore.getCurrent(glassContext) }
    val navGlassBackdrop = AppGlassBackdrop.backdrop
    val navGlassDark = MaterialTheme.colorScheme.onSurface.red > 0.5f
    val navGlassOn = false // 窗口毛玻璃/液态玻璃模式已移除。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                // ★ 限宽 + 限高：必须放在 fillMaxWidth 外层（顺序反了就封不住宽度）。
                //   宽 ≤420dp、高 ≤600dp；内容超高时内部可滚动。
                .widthIn(max = 420.dp)
                .heightIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .then(
                    if (navGlassOn && navGlassBackdrop != null) {
                        // 玻璃面：vibrancy + blur + lens + 表面色（教程同款）。
                        Modifier.drawBackdrop(
                            backdrop = navGlassBackdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx())
                                if (windowGlassMode == WindowGlassMode.LIQUID) {
                                    lens(24.dp.toPx(), 48.dp.toPx(), true)
                                } else {
                                    lens(16.dp.toPx(), 32.dp.toPx())
                                }
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (navGlassDark) {
                                        Color.Black.copy(
                                            alpha = if (windowGlassMode == WindowGlassMode.LIQUID) 0.42f else 0.55f
                                        )
                                    } else {
                                        Color.White.copy(
                                            alpha = if (windowGlassMode == WindowGlassMode.LIQUID) 0.50f else 0.68f
                                        )
                                    }
                                )
                            }
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(28.dp),
            // 玻璃面模式容器透明（表面色已由 onDrawSurface 绘制）；普通=色调提升色。
            color = if (navGlassOn) Color.Transparent else dialogGlassColor(),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 为本窗口开启身后背景模糊（毛玻璃/液态玻璃模式下）。
                ApplyWindowGlassBehind()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 左侧箭头关闭（与主题颜色窗口一致）。
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDismiss() }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "悬浮导航栏",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "选择样式",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                NavigationStyleOption(
                    title = "毛玻璃",
                    subtitle = "模糊背景并透出后方颜色，表面柔和",
                    glassStyle = false,
                    selected = !liquidGlassEnabled,
                    onClick = { onSelect(false) }
                )
                NavigationStyleOption(
                    title = "液态玻璃",
                    subtitle = "透出后方场景，边缘产生镜头折射与高光",
                    glassStyle = true,
                    selected = liquidGlassEnabled,
                    onClick = { onSelect(true) }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun NavigationStyleOption(
    title: String,
    subtitle: String,
    glassStyle: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Both style cards use the same outer height, regardless of subtitle line count.
            .height(164.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "当前选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        NavigationStylePreview(glassStyle = glassStyle)
        Text(
            subtitle,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NavigationStylePreview(glassStyle: Boolean) {
    val previewShape = RoundedCornerShape(22.dp)
    val frameShape = RoundedCornerShape(16.dp)
    val previewBackdrop = rememberLayerBackdrop()
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = if (glassStyle) 0.52f else 0.28f)
    // Resolve theme colors in the composable scope; drawBackdrop callbacks are draw scopes.
    val previewSurface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(frameShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        // Scene behind both previews: every sample has real color/shape detail to show through glass.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(previewBackdrop)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-13).dp)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.94f))
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = 7.dp, y = (-2).dp)
                    .size(width = 60.dp, height = 82.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF35BFC8).copy(alpha = 0.92f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 18.dp, y = 6.dp)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF4A24B).copy(alpha = 0.96f))
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 34.dp, y = (-18).dp)
                    .size(width = 68.dp, height = 38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFAA77E8).copy(alpha = 0.92f))
            )
        }

        // The option preview samples the colorful scene above. Frosted glass uses a wider blur;
        // liquid glass adds a lens so both cards visibly transmit what is behind their surface.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .height(48.dp)
                .drawBackdrop(
                    backdrop = previewBackdrop,
                    shape = { previewShape },
                    effects = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            blur(if (glassStyle) 3.dp.toPx() else 9.dp.toPx())
                        }
                        if (glassStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            vibrancy()
                            lens(17.dp.toPx(), 22.dp.toPx(), chromaticAberration = true)
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (glassStyle) 0.72f else 0.20f)
                    },
                    onDrawSurface = {
                        drawRect(
                            Color.White.copy(alpha = if (glassStyle) 0.075f else 0.26f)
                        )
                        drawRect(
                            previewSurface.copy(alpha = if (glassStyle) 0.09f else 0.24f)
                        )
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icons = listOf(Icons.Default.Extension, Icons.Default.AutoAwesome, Icons.Default.Settings)
                icons.forEachIndexed { index, icon ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (index == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 预览只展示导航栏表面和图标，不绘制类似滑块的选中胶囊。
        }

        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, outline, frameShape)
        )
    }
}

@Composable
private fun TransparencyRow(onClick: () -> Unit) {
    val glassAlpha = GlassOpacityState.alpha

    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = glassAlpha))
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("透明度", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "全局 ${(glassAlpha * 100).toInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, null)
    }
}

/**
 * 全局玻璃采样源 = 主界面记录层（navBackdrop）。
 * 对话框窗口采不到自己身后的主界面内容，故按 kyant glass-bottom-sheet 教程的方式：
 * 玻璃面用 drawBackdrop 直接采样主界面记录层（vibrancy + blur + lens + 表面色）。
 */
object AppGlassBackdrop {
    var backdrop: Backdrop? = null
}

// =======================================================
//  窗口玻璃：普通 / 毛玻璃 / 液态玻璃（玻璃面采样主界面记录层；Android 13 以下回退普通）
// =======================================================

/** 对话框容器背景色：窗口玻璃模式已移除 —— 统一为色调提升色（悬浮导航栏窗口同款）。 */
@Composable
private fun dialogGlassColor(): Color =
    lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, 0.09f)

/** 窗口毛玻璃/液态玻璃模式已移除 —— no-op（调用点保留，无需改动）。 */
@Composable
private fun ApplyWindowGlassBehind() {
    return
    val context = LocalContext.current
    val version = WindowGlassState.version
    val mode = remember(version) { WindowGlassStore.getCurrent(context) }
    if (mode == WindowGlassMode.NORMAL) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) {
        (if (mode == WindowGlassMode.LIQUID) 48.dp else 40.dp).toPx().toInt()
    }
    SideEffect {
        val raw = view.context
        val dialog = (raw as? android.content.ContextWrapper)?.baseContext as? android.app.Dialog
            ?: raw as? android.app.Dialog
        dialog?.window?.let { window ->
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setBackgroundBlurRadius(radiusPx)
        }
    }
}

/**
 * 窗口玻璃对话框外壳：参数与 AlertDialog 对齐（onDismissRequest / title / text / confirmButton / dismissButton）。
 *
 * 玻璃模式（Android 13+）：按 kyant glass-bottom-sheet 教程 —— 玻璃面用 drawBackdrop
 * 采样主界面记录层（vibrancy + blur + lens），表面色画在 onDrawSurface，容器透明。
 * 普通模式 / 低版本：与之前一致的实色窗口。
 */
@Composable
private fun GlassDialogWindow(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit
) {
    val context = LocalContext.current
    val version = WindowGlassState.version
    val mode = remember(version) { WindowGlassStore.getCurrent(context) }
    val backdrop = AppGlassBackdrop.backdrop
    val dark = MaterialTheme.colorScheme.onSurface.red > 0.5f
    val glass = false // 窗口毛玻璃/液态玻璃模式已移除：窗口恢复普通实色背景。
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .heightIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .then(
                    if (glass && backdrop != null) {
                        // kyant glass-bottom-sheet 教程：玻璃面 = vibrancy + blur + lens + 表面色。
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx())
                                if (mode == WindowGlassMode.LIQUID) {
                                    lens(24.dp.toPx(), 48.dp.toPx(), true)
                                } else {
                                    lens(16.dp.toPx(), 32.dp.toPx())
                                }
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (dark) {
                                        Color.Black.copy(
                                            alpha = if (mode == WindowGlassMode.LIQUID) 0.42f else 0.55f
                                        )
                                    } else {
                                        Color.White.copy(
                                            alpha = if (mode == WindowGlassMode.LIQUID) 0.50f else 0.68f
                                        )
                                    }
                                )
                            }
                        )
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(28.dp),
            color = if (glass) Color.Transparent else dialogGlassColor(),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                title()
                text()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

// =======================================================
//  导出日志对话框：软件 / 权限使用 / 插件（列表） + 时长
// =======================================================
@Composable
private fun ExportLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var includeApp by remember { mutableStateOf(true) }
    var includePerm by remember { mutableStateOf(true) }
    var includePlugin by remember { mutableStateOf(false) }
    var includeCrash by remember { mutableStateOf(true) }
    val plugins = remember {
        try {
            FeatureRepository(context).listInstalled()
        } catch (_: Exception) {
            emptyList()
        }
    }
    val selectedIds = remember { mutableStateListOf<String>() }
    // 时长（小时）：0 = 全部
    var durationHours by remember { mutableStateOf(24) }
    var exportedNote by remember { mutableStateOf<String?>(null) }

    val durationOptions = listOf(
        1 to "最近 1 小时",
        6 to "最近 6 小时",
        24 to "最近 24 小时",
        72 to "最近 3 天",
        0 to "全部"
    )

    GlassDialogWindow(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Download, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    ApplyWindowGlassBehind()
                    Text("导出日志", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        "选择内容与时长，打包为一个 zip",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column {
                // ===== 内容 =====
                Text(
                    "内容",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                ExportOptionRow(
                    icon = Icons.Default.PhoneAndroid,
                    label = "软件",
                    note = "启动、页面、设置变更等",
                    checked = includeApp,
                    onChecked = { includeApp = it }
                )
                ExportOptionRow(
                    icon = Icons.Default.Security,
                    label = "权限使用",
                    note = "谁、何时、为了什么使用权限；权限丢失记录",
                    checked = includePerm,
                    onChecked = { includePerm = it }
                )
                ExportOptionRow(
                    icon = Icons.Default.Extension,
                    label = "插件",
                    note = "安装/启动/开关等，可按列表勾选",
                    checked = includePlugin,
                    onChecked = { includePlugin = it }
                )
                if (includePlugin) {
                    if (plugins.isEmpty()) {
                        Text(
                            "  暂无已安装插件",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    plugins.forEach { plugin ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp)
                                .clickable {
                                    if (!selectedIds.remove(plugin.manifest.id)) {
                                        selectedIds.add(plugin.manifest.id)
                                    }
                                }
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(plugin.manifest.id),
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(plugin.manifest.id)
                                    else selectedIds.remove(plugin.manifest.id)
                                }
                            )
                            Text(plugin.manifest.name, fontSize = 14.sp)
                        }
                    }
                }
                ExportOptionRow(
                    icon = Icons.Default.BugReport,
                    label = "崩溃日志",
                    note = "crash.log",
                    checked = includeCrash,
                    onChecked = { includeCrash = it }
                )

                Spacer(Modifier.height(10.dp))
                // ===== 时长 =====
                Text(
                    "时长",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durationOptions.take(3).forEach { (hours, label) ->
                        DurationChip(
                            label = label,
                            selected = durationHours == hours,
                            onClick = { durationHours = hours },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durationOptions.drop(3).forEach { (hours, label) ->
                        DurationChip(
                            label = label,
                            selected = durationHours == hours,
                            onClick = { durationHours = hours },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                exportedNote?.let {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val since = if (durationHours == 0) 0L
                else System.currentTimeMillis() - durationHours * 3600_000L
                val pluginFilter: Set<String>? = if (includePlugin) selectedIds.toSet() else null
                val durationLabel =
                    durationOptions.firstOrNull { it.first == durationHours }?.second ?: ""
                val contentLabel = listOf(
                    if (includeApp) "软件" else null,
                    if (includePerm) "权限使用" else null,
                    if (includePlugin) "插件" else null,
                    if (includeCrash) "崩溃" else null
                ).filterNotNull().joinToString(" + ")
                if (contentLabel.isEmpty()) {
                    exportedNote = "请至少选择一项内容"
                    return@TextButton
                }
                val pluginDesc = if (includePlugin) {
                    if (selectedIds.isEmpty()) "未勾选任何插件"
                    else plugins.filter { selectedIds.contains(it.manifest.id) }
                        .joinToString("、") { it.manifest.name }
                } else "不限"
                // 导出 = 四个分开的文件（软件/权限/插件/崩溃）按时长与插件过滤后，合成一个 zip。
                val zipName = LogCenter.exportZip(
                    context = context,
                    includeApp = includeApp,
                    includePerm = includePerm,
                    includePlugin = includePlugin,
                    includeCrash = includeCrash,
                    pluginIds = pluginFilter,
                    sinceMillis = since,
                    selectionDesc = "内容：$contentLabel；时长：$durationLabel；插件：$pluginDesc"
                )
                // 软件日志：记录一次导出动作（下次导出可查）。
                LogCenter.log(
                    context, LogCenter.CAT_APP, "export",
                    "导出日志 zip（$contentLabel / $durationLabel）"
                )
                exportedNote = if (zipName != null) {
                    "已导出 下载/YanyuanToolbox/$zipName"
                } else {
                    "导出失败，请重试"
                }
            }) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 导出日志对话框：分区标题 */
@Composable
private fun ExportSectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(6.dp))
}

/** 导出日志对话框：带图标与说明的圆角选项行（勾选态高亮） */
@Composable
private fun ExportOptionRow(
    icon: ImageVector,
    label: String,
    note: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
            )
            .clickable { onChecked(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (note != null) {
                Text(
                    note,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
    Spacer(Modifier.height(6.dp))
}

/** 导出日志对话框：时长胶囊 */
@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// =======================================================
//  窗口玻璃行 + 选择对话框（普通 / 毛玻璃 / 液态玻璃）
// =======================================================
@Composable
private fun WindowGlassRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val version = WindowGlassState.version
    val current = remember(version) { WindowGlassStore.getCurrent(context) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when (current) {
                        WindowGlassMode.NORMAL ->
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                        WindowGlassMode.FROST ->
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        WindowGlassMode.LIQUID ->
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Wallpaper, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("窗口玻璃", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                "当前：${current.displayName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WindowGlassDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = WindowGlassState.version
    var current by remember(version) { mutableStateOf(WindowGlassStore.getCurrent(context)) }
    val modes = WindowGlassMode.entries.toList()

    GlassDialogWindow(
        onDismissRequest = onDismiss,
        title = {
            ApplyWindowGlassBehind()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("窗口玻璃", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        "对话框窗口的背景样式",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column {
                modes.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (current == mode) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                            .clickable {
                                WindowGlassStore.setCurrent(context, mode)
                                current = mode
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                mode.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (current == mode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                mode.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (current == mode) {
                            Icon(
                                Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "毛玻璃/液态玻璃需 Android 12+，低版本自动回退普通；切换后重新打开窗口生效。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

// =======================================================
//  透明度对话框（毛玻璃 + 欢迎页透明）
// =======================================================
@Composable
private fun TransparencyDialog(
    welcomeTransparent: Boolean,
    onWelcomeTransparentChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var glassValue by remember { mutableStateOf(GlassOpacityState.alpha) }

    GlassDialogWindow(
        onDismissRequest = onDismiss,
        title = {
            ApplyWindowGlassBehind()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Text("透明度", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {            Column(Modifier.fillMaxWidth()) {
                // ============ 全局透明度 ============
                Text(
                    "全局透明度",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "卡片、导航栏等元素的透明程度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("不透明度", fontSize = 13.sp)
                    Text(
                        "${(glassValue * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))

                Slider(
                    value = glassValue,
                    onValueChange = {
                        glassValue = it
                        GlassOpacityStore.setCurrent(context, it)
                    },
                    valueRange = GlassOpacityStore.MIN..GlassOpacityStore.MAX,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "更透",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "更实",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        val def = GlassOpacityStore.DEFAULT
                        glassValue = def
                        GlassOpacityStore.setCurrent(context, def)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("恢复默认全局（92%）")
                }

                // ============ 分隔 ============
                Spacer(Modifier.height(20.dp))
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(20.dp))

                // ============ 欢迎页透明 ============
                Text(
                    "欢迎页透明",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "开启后欢迎页透出应用背景图",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onWelcomeTransparentChange(!welcomeTransparent) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (welcomeTransparent) "已开启" else "已关闭",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "需要先在“个性化 → 应用背景”上传图片",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = welcomeTransparent,
                        onCheckedChange = onWelcomeTransparentChange
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

// =======================================================
//  主题颜色入口行
// =======================================================
/** 主题色的"实际显示色"：莫奈=背景图提取色；自定义=调色盘色；其余=色板主色。 */
@Composable
private fun effectiveSwatchColor(color: AppThemeColor): Color {
    val context = LocalContext.current
    val colorVersion = ThemeColorState.version
    val bgVersion = AppearanceState.backgroundVersion
    return remember(color, colorVersion, bgVersion) {
        when {
            color == AppThemeColor.MONET ->
                monetPrimaryFromBackground(context) ?: color.lightPrimary
            color == AppThemeColor.CUSTOM ->
                ThemeColorStore.getCustom(context) ?: color.lightPrimary
            else -> color.lightPrimary
        }
    }
}

@Composable
private fun ThemeColorRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val colorVersion = ThemeColorState.version
    val current = remember(colorVersion) { ThemeColorStore.getCurrent(context) }
    // 图标底色 = 实际生效色（莫奈取背景图提取色，不再是占位紫）。
    val swatchColor = effectiveSwatchColor(current)

    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(swatchColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Palette, null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("主题颜色", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "当前：${current.displayName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, null)
    }
}

// =======================================================
//  主题颜色选择对话框
// =======================================================
// =======================================================
//  颜色选取条：渐变色带 + 当前位置指示（点按 / 拖动选取）
// =======================================================
@Composable
private fun ColorPickStrip(
    gradient: List<Color>,
    fraction: Float,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(gradient))
            // 点按取值
            .pointerInput(gradient) {
                detectTapGestures { offset ->
                    onPick((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            // 拖动取值
            .pointerInput(gradient) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onPick((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = fraction.coerceIn(0f, 1f) * size.width
            // 指示线：黑底白线双描边，深浅色带上都看得清。
            drawLine(
                Color.Black.copy(alpha = 0.45f),
                androidx.compose.ui.geometry.Offset(x - 2f, 0f),
                androidx.compose.ui.geometry.Offset(x - 2f, size.height),
                strokeWidth = 6f
            )
            drawLine(
                Color.White,
                androidx.compose.ui.geometry.Offset(x, 0f),
                androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 3f
            )
        }
    }
}

// =======================================================
//  自定义主题色调色盘：色相环点选 + 饱和度/明度颜色带选取 + 预览
// =======================================================
@Composable
private fun CustomColorPickerDialog(
    initial: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // RGB → HSV 初始值
    val initHsv = remember(initial) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initial.toArgb(), arr)
        arr
    }
    var hue by remember(initial) { mutableFloatStateOf(initHsv[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(initHsv[2]) }
    val current = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }
    val hex = remember(current) { String.format("#%06X", 0xFFFFFF and current.toArgb()) }

    GlassDialogWindow(
        onDismissRequest = onDismiss,
        title = {
            ApplyWindowGlassBehind()
            Text("自定义主题色", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            // 支持滑动：内容超高时窗口内可上下滚动。
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(current)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp)
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(hex, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(16.dp))

                // 色相环：点按环带取色相
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier
                            .size(180.dp)
                            .pointerInput(saturation, value) {
                                detectTapGestures { offset ->
                                    val r = minOf(size.width, size.height) / 2f
                                    val dx = offset.x - r
                                    val dy = offset.y - r
                                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                                    if (dist <= r && dist >= r * 0.55) {
                                        var deg =
                                            Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
                                                .toFloat() + 90f
                                        if (deg < 0f) deg += 360f
                                        hue = deg % 360f
                                    }
                                }
                            }
                    ) {
                        val outer = size.minDimension / 2f
                        val ringWidth = outer * 0.45f
                        val inset = ringWidth / 2f
                        val ringSize = androidx.compose.ui.geometry.Size(
                            size.width - inset * 2f,
                            size.height - inset * 2f
                        )
                        for (seg in 0 until 72) {
                            val h = seg * 5f
                            drawArc(
                                color = Color(
                                    android.graphics.Color.HSVToColor(
                                        floatArrayOf(h, saturation, value)
                                    )
                                ),
                                startAngle = h - 90f,
                                sweepAngle = 5.2f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth),
                                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                size = ringSize
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 饱和度 / 明度：改为【选取颜色】渐变色带（点按或拖动取值），比灰阶滑杆直观。
                Text("饱和度 · 点按色带选取", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ColorPickStrip(
                    gradient = listOf(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                    ),
                    fraction = saturation,
                    onPick = { saturation = it }
                )
                Spacer(Modifier.height(14.dp))
                Text("明度 · 点按色带选取", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ColorPickStrip(
                    gradient = listOf(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                    ),
                    fraction = value,
                    onPick = { value = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("使用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// =======================================================
//  主题颜色对话框
// =======================================================
@Composable
private fun ThemeColorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colorVersion = ThemeColorState.version
    var current by remember(colorVersion) {
        mutableStateOf(ThemeColorStore.getCurrent(context))
    }
    val colors = AppThemeColor.values().toList()
    var pickerOpen by remember { mutableStateOf(false) }

    GlassDialogWindow(
        onDismissRequest = onDismiss,
        title = {
            ApplyWindowGlassBehind()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Text("主题颜色", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            // 支持滑动：色板/提示变多时窗口内可上下滚动，不会被裁掉。
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "选择喜欢的主题色",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                colors.chunked(2).forEach { rowColors ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowColors.forEach { color ->
                            ThemeColorItem(
                                color = color,
                                selected = current == color,
                                onClick = {
                                    if (color == AppThemeColor.CUSTOM &&
                                        ThemeColorStore.getCustom(context) == null
                                    ) {
                                        // 还没选过自定义色：短按也先进调色盘选一个。
                                        pickerOpen = true
                                    } else {
                                        ThemeColorStore.setCurrent(context, color)
                                        current = color
                                    }
                                },
                                // 长按自定义色块 = 进入调色盘重新选择。
                                onLongClick = if (color == AppThemeColor.CUSTOM) {
                                    { pickerOpen = true }
                                } else {
                                    null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowColors.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (current == AppThemeColor.CUSTOM) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "自定义：短按使用，长按色块进入调色盘",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )

    // 长按（或首次短按）自定义色块 → 调色盘；确认后保存并立即启用。
    if (pickerOpen) {
        CustomColorPickerDialog(
            initial = ThemeColorStore.getCustom(context) ?: Color(0xFF6366F1),
            onConfirm = { picked ->
                ThemeColorStore.setCustom(context, picked)
                ThemeColorStore.setCurrent(context, AppThemeColor.CUSTOM)
                current = AppThemeColor.CUSTOM
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

@Composable
private fun ThemeColorItem(
    color: AppThemeColor,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorVersion = ThemeColorState.version
    // 色块与实际生效色一致（与主题颜色入口行共用 effectiveSwatchColor）。
    val swatchColor = effectiveSwatchColor(color)
    Column(
        // 长按 = 进调色盘（仅自定义项提供）；短按 = 使用。
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.pointerInput(color) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
            } else {
                Modifier.clickable { onClick() }
            }
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(swatchColor)
                .then(
                    if (selected) Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(14.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            color.displayName,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// =======================================================
//  深色模式入口行
// =======================================================
@Composable
private fun ThemeModeRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val modeVersion = ThemeModeState.version
    val current = remember(modeVersion) { ThemeModeStore.getCurrent(context) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (current) {
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("深色模式", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "当前：${current.displayName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, null)
    }
}

// =======================================================
//  深色模式选择对话框
// =======================================================
@Composable
private fun ThemeModeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val modeVersion = ThemeModeState.version
    var current by remember(modeVersion) {
        mutableStateOf(ThemeModeStore.getCurrent(context))
    }
    val modes = ThemeMode.values().toList()

    GlassDialogWindow(
        onDismissRequest = onDismiss,
        title = {
            ApplyWindowGlassBehind()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Text("深色模式", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "选择 App 的显示模式",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                modes.forEach { mode ->
                    val selected = current == mode
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                ThemeModeStore.setCurrent(context, mode)
                                current = mode
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                mode.displayName,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Normal
                            )
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "跟随手机系统设置"
                                    ThemeMode.LIGHT -> "始终保持浅色界面"
                                    ThemeMode.DARK -> "始终保持深色界面"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (mode != modes.last()) {
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

// =======================================================
//  关于全屏页
// =======================================================
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "关于",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = navBarBottom + 32.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ---------- 应用标识 ----------
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AboutAppIcon(Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(6.dp))
                    // 版本号做成胶囊标签
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "v$APP_VERSION_NAME",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                APP_VERSION_DESC,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(22.dp))

            // ---------- 作者 ----------
            AboutChipRow(
                icon = Icons.Default.Favorite,
                label = "沐琰鸢 & DeepSeek & ChatGPT",
                value = "倾情制作"
            )

            Spacer(Modifier.height(28.dp))

            // ---------- 本次更新 ----------
            AboutSectionHeader(
                title = "本次更新-0.4.3",
                tag = APP_VERSION_NAME
            )
            Spacer(Modifier.height(12.dp))

            UPDATE_NOTES.forEach { (group, items) ->
                AboutGroup(group = group, items = items)
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 往期更新 ----------
            AboutSectionHeader(title = "往期更新", tag = null)
            Spacer(Modifier.height(12.dp))

            Text(
                HISTORY_NOTES,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/*
private fun PluginGuideScreenLegacy(onBack: () -> Unit) {
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    var selectedTopic by remember { mutableStateOf<PluginGuideTopic?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text("插件介绍与制作", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tablet = maxWidth >= SETTINGS_TABLET_BREAKPOINT
            val columns = if (tablet) 2 else 1
            val rows = PLUGIN_GUIDE_TOPICS.chunked(columns)
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (tablet) 24.dp else 16.dp)
                    .padding(top = 18.dp, bottom = navBarBottom + 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "目录 App 插件体系说明。选择一个主题查看详情。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rows.forEach { rowTopics ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTopics.forEach { topic ->
                            PluginGuideTopicButton(
                                topic = topic,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTopic = topic }
                            )
                        }
                        if (tablet && rowTopics.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

    }

    selectedTopic?.let { topic ->
        PluginGuideTopicDialog(topic = topic, tablet = LocalConfiguration.current.screenWidthDp >= 600) {
            selectedTopic = null
        }
    }
}

@Composable
private fun PluginGuideTopicButton(
    topic: PluginGuideTopic,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = GlassOpacityState.alpha))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(topic.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                topic.paragraphs.first(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = "查看 ${topic.title}")
    }
}

@Composable
private fun PluginGuideTopicDialog(
    topic: PluginGuideTopic,
    tablet: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = if (tablet) 24.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = if (tablet) minOf(720.dp, maxWidth) else maxWidth
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = maxHeight - 48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            topic.title,
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        topic.paragraphs.forEach { paragraph ->
                            Text(
                                paragraph,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("完成") }
                    }
                }
            }
        }

        item(key = "pluginGuide") {
            Box(Modifier.padding(start = pluginGuideLeftPad.value)) {
                SettingsGlassGroup(title = "插件介绍与制作") {
                    ListItem(
                        headlineContent = { Text("插件介绍与制作", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("权限、安全风险、制作流程与接口接入", fontSize = 12.sp) },
                        leadingContent = {
                            Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onPluginGuideClick)
                    )
                }
            }
        }
    }
}

*/

@Composable
private fun PluginGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val document = remember(context) {
        runCatching {
            context.assets.open("plugin_guide.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { "文档暂时无法读取。" }
    }
    val sections = remember(document) { splitPluginGuide(document) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showDirectory by remember { mutableStateOf(false) }
    var transparentArticle by remember { mutableStateOf(true) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(document.toByteArray(Charsets.UTF_8)) }
                    ?: error("无法创建文件")
            }.onFailure {
                android.widget.Toast.makeText(context, "文档保存失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("插件介绍与制作", modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { transparentArticle = !transparentArticle }) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = if (transparentArticle) "切换纯色背景" else "切换透明背景",
                    tint = if (transparentArticle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { createDocument.launch("琰鸢工具箱插件介绍与开发接入文档.md") }) {
                Icon(Icons.Default.Download, contentDescription = "下载完整文档")
            }
            IconButton(onClick = { showDirectory = true }) {
                Icon(Icons.Default.List, contentDescription = "打开目录")
            }
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tablet = maxWidth >= SETTINGS_TABLET_BREAKPOINT
            LazyColumn(
                state = listState,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .widthIn(max = if (tablet) 900.dp else maxWidth)
                    .background(if (transparentArticle) Color.Transparent else MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(horizontal = if (tablet) 28.dp else 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sections.isNotEmpty()) {
                    item(key = "guide-intro") { MarkdownText(sections.first().markdown) }
                    itemsIndexed(sections.drop(1), key = { index, section -> "guide-$index-${section.title}" }) { _, section ->
                        MarkdownText(section.markdown)
                    }
                }
            }
        }
    }
    if (showDirectory) {
        PluginGuideDirectoryDrawer(
            sections = sections.drop(1),
            tablet = LocalConfiguration.current.screenWidthDp >= 600,
            onSelect = { index ->
                showDirectory = false
                scope.launch { listState.animateScrollToItem(index + 1) }
            },
            onDismiss = { showDirectory = false }
        )
    }
}

private fun splitPluginGuide(document: String): List<PluginGuideSection> {
    val result = mutableListOf<PluginGuideSection>()
    val content = StringBuilder()
    var title = "文档"
    val headingPattern = Regex("^#{1,3} .+")
    document.lineSequence().forEach { line ->
        if (headingPattern.matches(line)) {
            if (content.isNotEmpty()) result += PluginGuideSection(title, content.toString().trim())
            title = line.trimStart('#', ' ')
            content.clear()
        }
        content.append(line).append('\n')
    }
    if (content.isNotEmpty()) result += PluginGuideSection(title, content.toString().trim())
    return result
}

@Composable
private fun PluginGuideDirectoryDrawer(sections: List<PluginGuideSection>, tablet: Boolean, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = androidx.compose.animation.slideInHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    initialOffsetX = { it }
                ) + fadeIn(tween(200))
            ) {
                Surface(
                    modifier = Modifier.width(if (tablet) minOf(420.dp, maxWidth) else maxWidth * 0.88f)
                        .fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars),
                    shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("文档目录", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
                        }
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            sections.forEachIndexed { index, section ->
                                TextButton(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(section.title, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 关于页顶部的应用图标（取应用自身的 launcher icon） */
@Composable
private fun AboutAppIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember {
        runCatching {
            val d = context.packageManager.getApplicationIcon(context.packageName)
            val w = d.intrinsicWidth.coerceAtLeast(144)
            val h = d.intrinsicHeight.coerceAtLeast(144)
            val bmp = android.graphics.Bitmap.createBitmap(
                w, h, android.graphics.Bitmap.Config.ARGB_8888
            )
            d.setBounds(0, 0, w, h)
            d.draw(android.graphics.Canvas(bmp))
            bmp
        }.getOrNull()
    }
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
            )
        } else {
            Icon(
                Icons.Default.Extension, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** 关于页的一行信息胶囊 */
@Composable
private fun AboutChipRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val alpha = GlassOpacityState.alpha
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 关于页的区块标题（右侧可带版本标签） */
@Composable
private fun AboutSectionHeader(title: String, tag: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 左侧竖条，强化分区
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (tag != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 一组更新条目：小标题 + 带圆点列表 */
@Composable
private fun AboutGroup(group: String, items: List<String>) {
    val alpha = GlassOpacityState.alpha
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            group,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { i, item ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// The settings row only needs a small preview; do file I/O and bitmap decode off the main thread.
private suspend fun decodeSettingsBackgroundPreview(path: String): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

    val targetPixels = 256
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetPixels &&
        bounds.outHeight / (sampleSize * 2) >= targetPixels
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeFile(path, options)
}

// =======================================================
//  背景图选择行
// =======================================================
@Composable
private fun BackgroundPickerRow(onPick: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val version = AppearanceState.backgroundVersion
    val bgFile = remember(version) { AppearanceRepository.backgroundFile(context) }
    // This row only displays a 56dp thumbnail. Decode a downsampled preview on IO instead of
    // decoding the original full-resolution wallpaper synchronously during page composition.
    val bitmap by produceState<Bitmap?>(null, version, bgFile.absolutePath) {
        value = decodeSettingsBackgroundPreview(bgFile.absolutePath)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { onPick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val previewBitmap = bitmap
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Icon(
                    Icons.Default.Wallpaper, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text("应用背景", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                if (bitmap != null) "点击更换背景图" else "点击上传自定义背景图",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (bitmap != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.DeleteOutline, "清除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// =======================================================
//  毛玻璃分组 + 开关行
// =======================================================
@Composable
internal fun SettingsGlassGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val alpha = GlassOpacityState.alpha
    Column(modifier = modifier) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(24.dp)
                ),
            content = content
        )
    }
}

/**
 * 开关行 —— 与 ThemeColorRow / TransparencyRow / BackgroundPickerRow 同高（88dp）
 */
@Composable
internal fun SettingsSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧 56dp 图标块，与其它行保持一致
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
