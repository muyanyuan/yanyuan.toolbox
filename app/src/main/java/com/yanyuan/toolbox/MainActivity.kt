package com.yanyuan.toolbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanyuan.toolbox.data.AiChatModel
import com.yanyuan.toolbox.data.AiChatStorage
import com.yanyuan.toolbox.data.ChatAttachment
import com.yanyuan.toolbox.data.FeatureRepository
import com.yanyuan.toolbox.data.AiModelPreference
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AiModelState
import com.yanyuan.toolbox.data.AppearanceRepository
import com.yanyuan.toolbox.data.AppearanceState
import com.yanyuan.toolbox.data.AuthState
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.GlassOpacityState
import com.yanyuan.toolbox.data.NicknameStore
import com.yanyuan.toolbox.data.PersonaStorage
import com.yanyuan.toolbox.ui.AppGlassBackdrop
import com.yanyuan.toolbox.ui.AiChatProStorage
import com.yanyuan.toolbox.ui.AiChatScreen
import com.yanyuan.toolbox.ui.AiChatViewModel
import com.yanyuan.toolbox.ui.AppIconBadge
import com.yanyuan.toolbox.ui.FeatureScreen
import com.yanyuan.toolbox.ui.SettingsScreen
import com.yanyuan.toolbox.ui.ShareHandoffScreen
import com.yanyuan.toolbox.ui.WelcomeScreen
import com.yanyuan.toolbox.ui.welcomeText
import com.yanyuan.toolbox.ui.theme.YanyuanTheme
import com.yanyuan.toolbox.ui.theme.isAppInDarkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var shareHandoff by mutableStateOf<com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff?>(null)
    private var shareLoading by mutableStateOf(false)
    private var shareError by mutableStateOf<String?>(null)
    private var aiShareHandoff by mutableStateOf<com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT
            )
        )

        super.onCreate(savedInstanceState)
        handleShareIntent(intent)

        // ★ 崩溃日志独立成文件（crash.log）：未捕获异常先落盘，再交给系统默认处理。
        com.yanyuan.toolbox.util.LogCenter.installCrashHandler(applicationContext)

        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            YanyuanTheme {
                var showWelcome by remember { mutableStateOf(shareHandoff == null && !shareLoading) }
                LaunchedEffect(shareHandoff, shareLoading) {
                    if (shareHandoff != null || shareLoading) showWelcome = false
                }
                when {
                    shareLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    shareHandoff != null -> MainScreen(
                        shareHandoff = shareHandoff,
                        aiShareHandoff = aiShareHandoff,
                        onShareToAi = { handoff ->
                            aiShareHandoff = handoff
                            shareHandoff = null
                        },
                        onAiShareConsumed = {
                            aiShareHandoff?.token?.let { com.yanyuan.toolbox.util.SharedFileHandoffStore.release(it) }
                            aiShareHandoff = null
                        },
                        onShareToPlugin = { plugin ->
                            val handoff = shareHandoff ?: return@MainScreen
                            val i = Intent(this@MainActivity, com.yanyuan.toolbox.ui.plugin.WebPluginActivity::class.java)
                                .putExtra(com.yanyuan.toolbox.ui.plugin.WebPluginActivity.EXTRA_PLUGIN_ID, plugin.manifest.id)
                                .putExtra(com.yanyuan.toolbox.ui.plugin.WebPluginActivity.EXTRA_SHARE_TOKEN, handoff.token)
                            shareHandoff = null
                            startActivity(i)
                        },
                        onCancelShare = {
                            com.yanyuan.toolbox.util.SharedFileHandoffStore.release(shareHandoff?.token)
                            shareHandoff = null
                            shareError = null
                        }
                    )
                    showWelcome -> WelcomeScreen(onContinue = { showWelcome = false })
                    else -> MainScreen(
                        shareHandoff = null,
                        aiShareHandoff = aiShareHandoff,
                        onShareToAi = { aiShareHandoff = it },
                        onShareToPlugin = {},
                        onCancelShare = {}
                    )
                }
                shareError?.let { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    shareError = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleShareIntent(intent)
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        shareLoading = true
        shareError = null
        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.yanyuan.toolbox.util.SharedFileHandoffStore.ingest(applicationContext, intent)
            withContext(Dispatchers.Main) {
                shareLoading = false
                result.onSuccess { handoff ->
                    shareHandoff?.token?.let { com.yanyuan.toolbox.util.SharedFileHandoffStore.release(it) }
                    shareHandoff = handoff
                }.onFailure { error ->
                    shareError = error.message ?: "无法读取分享文件"
                }
            }
        }
    }
}

/** 平板断点：宽度 >= 600dp 视为平板/大屏 */
private val TABLET_WIDTH_BREAKPOINT = 600.dp

/** 导入平台在全局模型偏好中的命名空间，避免与内置模型 ID 冲突。 */
private const val IMPORTED_MODEL_SELECTION_PREFIX = "imported:"

private fun importedSelectionId(platformId: String): String =
    "$IMPORTED_MODEL_SELECTION_PREFIX$platformId"

/** 平板导航栏高度 = 屏幕高度 × 此比例（随屏幕高度拉长）。 */
private const val TABLET_RAIL_HEIGHT_FRACTION = 0.4f

/**
 * 平板导航栏高度上限（"设定最长"）。
 * 不另设数字下限：下限由"最短时滑块为圆形"推出 = 轨宽 × 3 ——
 * 收缩态滑块尺寸为 (轨宽-12) × (高/3-12)，当 高 = 3×轨宽 时两轴相等 → 正好 60dp 正圆。
 */
private val TABLET_RAIL_HEIGHT_MAX = 360.dp

/** 平板侧栏宽度（导航栏切换成侧栏后展开的宽度） */
private val TABLET_SIDEBAR_WIDTH = 224.dp

/** 侧栏切换分阶段动画：先纵向（向上展开/落下），再横向（扩宽/缩窄） */
private const val SIDEBAR_HEIGHT_MS = 260
private const val SIDEBAR_WIDTH_MS = 240

/** 导航栏滑入滑出动画时长 */
private const val NAV_SLIDE_MS = 280

// 导航栏样式单独持久化，重启后仍保留用户选择。
private const val NAV_STYLE_PREFS = "yanyuan_navigation_style"
private const val NAV_STYLE_KEY_LIQUID_GLASS = "liquid_glass_enabled"

/**
 * AI 页内容为左侧导航栏预留的宽度（与 AiChatScreen 内部的 AI_NAV_LEFT_INSET 保持一致）。
 * 侧栏打开、导航栏收回时这个值会传给 AI 页变成 0。
 */
private val AI_NAV_LEFT_INSET = 96.dp

@Composable
fun MainScreen(
    shareHandoff: com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff? = null,
    aiShareHandoff: com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff? = null,
    onShareToAi: (com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff) -> Unit = {},
    onAiShareConsumed: () -> Unit = {},
    onShareToPlugin: (com.yanyuan.toolbox.data.InstalledPlugin) -> Unit = {},
    onCancelShare: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val navigationPrefs = remember(context) {
        context.getSharedPreferences(NAV_STYLE_PREFS, Activity.MODE_PRIVATE)
    }
    var useLiquidGlassNavigation by remember(navigationPrefs) {
        mutableStateOf(navigationPrefs.getBoolean(NAV_STYLE_KEY_LIQUID_GLASS, true))
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var performanceMode by remember { mutableStateOf(true) }
    var settingsFullscreen by remember { mutableStateOf(false) }

// ★ 平板 FAB 点击时，通过 Channel 通知 FeatureScreen 打开导入对话框
//    用 Channel 而不是 Int，避免 FeatureScreen 重建时误触发
    val importEventChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    // ===== AI ViewModel =====
    val aiStorage = remember { AiChatStorage(context) }
    val personaStorage = remember { PersonaStorage(context) }
    val modelPref = remember { AiModelPreference(context) }
    val authVersion = AuthState.version
    val agentFeatureVersion = com.yanyuan.toolbox.util.AgentPermissionState.version
    var agentEnabled by remember(agentFeatureVersion) {
        mutableStateOf(com.yanyuan.toolbox.util.AgentFeatureStore.get(context))
    }
    val proStorage = remember { AiChatProStorage(context) }

    fun selectedImportedModel(platformId: String, settings: com.yanyuan.toolbox.data.AiModelSettings) =
        settings.modelsOf(platformId).filterNot { it.hidden }.let { visibleModels ->
            val picked = proStorage.getSelectedModel(platformId)
            if (picked.isNullOrBlank()) {
                visibleModels.firstOrNull()
            } else {
                visibleModels.firstOrNull {
                    it.cleanModelId == picked || it.id == picked
                }
            }
        }

    fun importedPlatformId(selectionId: String): String? =
        selectionId.removePrefix(IMPORTED_MODEL_SELECTION_PREFIX)
            .takeIf { selectionId.startsWith(IMPORTED_MODEL_SELECTION_PREFIX) && it.isNotBlank() }

    fun importedApiIsAuthorized(platformId: String): Boolean {
        return AuthStore.get(context).isImportedApiAuthorized(platformId)
    }

    fun modelSelectionIsAuthorized(selectionId: String): Boolean {
        val platformId = importedPlatformId(selectionId)
        return if (platformId != null) {
            importedApiIsAuthorized(platformId)
        } else {
            // 内置模型沿用原有授权语义；未知选择 ID 不得静默回落到 Unlimited AI。
            AuthStore.get(context).isBuiltinAiModelAuthorized(selectionId)
        }
    }

    // 「接入AI」导入的平台 API：选择键 → OpenAI 兼容参数（新旧 AI 页共用）。
    val importedAiProvider: (String) -> com.yanyuan.toolbox.network.AiChatApi.ImportedAiSpec? = { selectionId ->
        val platformId = importedPlatformId(selectionId)
        if (platformId == null) {
            null
        } else {
            val settings = AiModelStore.get(context)
            val cfg = settings.configFor(platformId)
            val model = selectedImportedModel(platformId, settings)
            if ((!cfg.platform.keyOptional && cfg.apiKey.isBlank()) || !cfg.enabled || model == null ||
                model.cleanModelId.isBlank() || !importedApiIsAuthorized(platformId)) {
                null
            } else {
                com.yanyuan.toolbox.network.AiChatApi.ImportedAiSpec(
                    baseUrl = cfg.effectiveBaseUrl,
                    apiKey = cfg.apiKey,
                    model = model.cleanModelId,
                    displayName = cfg.platform.displayName,
                    temperature = proStorage.getTemperature(),
                    nativeWebSearch = cfg.nativeWebSearch
                )
            }
        }
    }
    val aiViewModel: AiChatViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiChatViewModel(
                    aiStorage, personaStorage, modelPref,
                    importedProvider = importedAiProvider,
                    importedApiAuthorized = { selectionId ->
                        modelSelectionIsAuthorized(selectionId)
                    }
                ) as T
            }
        }
    )
    // 当前是否选中了「导入且启用」的平台 → 用新 AI 页。
    // ★ 用 AiModelPreference.version（跨实例通知）+ 每次全新 modelPref.get()：
    //   切换对话框是另一个实例，其 state 流不会通知这里，旧写法会一直读到旧模型。
    val aiSettingsVersion = AiModelState.version
    val prefVersion = AiModelPreference.version
    val proModelId = remember(prefVersion) { modelPref.get() }
    val useAiPro = remember(prefVersion, aiSettingsVersion, authVersion, proModelId) {
        val platformId = importedPlatformId(proModelId)
        val currentSettings = AiModelStore.get(context)
        val cfg = platformId?.let { currentSettings.configs.firstOrNull { c -> c.platformId == it } }
        val model = platformId?.let { selectedImportedModel(it, currentSettings) }
        platformId != null && cfg != null && cfg.enabled &&
                (cfg.platform.keyOptional || cfg.apiKey.isNotBlank()) && model != null &&
                model.cleanModelId.isNotBlank() && importedApiIsAuthorized(platformId)
    }
    LaunchedEffect(proModelId, aiSettingsVersion, authVersion) {
        val legacyPlatform = AiModelStore.get(context).configs.firstOrNull {
            it.platformId == proModelId
        }?.platformId
        val isBuiltinId = AiChatModel.entries.any { it.id == proModelId }
        if (importedPlatformId(proModelId) == null && legacyPlatform != null && !isBuiltinId) {
            modelPref.set(importedSelectionId(legacyPlatform))
        } else if (importedPlatformId(proModelId) != null && !useAiPro) {
            modelPref.set(AiChatModel.UnlimitedAi.id)
        }
    }
    val importedAgentEnvironment = remember(proModelId, aiSettingsVersion, authVersion) {
        val boundPlatformId = importedPlatformId(proModelId)
        com.yanyuan.toolbox.util.AgentEnvironment.importedApi(
            context = context,
            // 固定到创建该环境时的平台，避免切换模型后旧请求读到新平台。
            platformId = { boundPlatformId },
            selectedModelAvailable = { platformId ->
                selectedImportedModel(platformId, AiModelStore.get(context))
                    ?.cleanModelId?.isNotBlank() == true
            },
            apiAuthorized = ::importedApiIsAuthorized
        )
    }
    // 资格检查会读取配置、SharedPreferences 和加密密钥，禁止在键盘重组期间同步执行。
    var agentEnvironmentAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(
        importedAgentEnvironment,
        proModelId,
        aiSettingsVersion,
        authVersion,
        com.yanyuan.toolbox.util.AgentPermissionState.version
    ) {
        agentEnvironmentAvailable = withContext(Dispatchers.IO) {
            importedAgentEnvironment.isAvailable()
        }
    }

    // 接入 API：复刻原页 VM + 按平台/模型分库（切换模型即切换对话记录，与原 AI 不互通）。
    val proVmId = proModelId
    val aiProViewModel: AiChatViewModel = remember(proVmId, aiSettingsVersion) {
        AiChatViewModel(
            AiChatStorage(context, "ai_chat_pro_$proVmId"),
            personaStorage, modelPref, importedAiProvider,
            agentEnvironment = importedAgentEnvironment,
            initialAgentEnabled = agentEnabled,
            importedApiAuthorized = { selectionId ->
                modelSelectionIsAuthorized(selectionId)
            }
        )
    }
    LaunchedEffect(aiProViewModel, agentEnabled) {
        aiProViewModel.setAgentEnabled(agentEnabled)
    }

    val darkTheme = isAppInDarkMode()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val ctrl = WindowInsetsControllerCompat(window, view)
        ctrl.isAppearanceLightStatusBars = !darkTheme
        ctrl.isAppearanceLightNavigationBars = !darkTheme
    }

    BackHandler(enabled = selectedTab == 1) {
        (context as? Activity)?.finish()
    }

    val bgVersion = AppearanceState.backgroundVersion
    val bgFile = remember(bgVersion) { AppearanceRepository.backgroundFile(context) }
    val bgBitmap = remember(bgVersion, bgFile.exists()) {
        if (bgFile.exists()) BitmapFactory.decodeFile(bgFile.absolutePath) else null
    }

    val glassVersion = GlassOpacityState.version
    val glassAlpha = remember(glassVersion) { GlassOpacityState.alpha }

    // Backdrop 2.x 的 rememberLayerBackdrop() 不接收绘制 lambda。
    // 由下面承载背景图、页面与 AI 内容的 Box 通过 layerBackdrop(navBackdrop) 采集内容，
    // 导航栏作为它的同级覆盖层绘制，避免把导航栏自身再次采集进背景。
    val navBackdrop = rememberLayerBackdrop()
    // 全局玻璃采样源：对话框窗口的「毛玻璃/液态玻璃」按 kyant glass-bottom-sheet 方式
    // 采样主界面记录层（SettingsScreen.AppGlassBackdrop）。
    AppGlassBackdrop.backdrop = navBackdrop
    // ★ AI 页玻璃的专用采样层：只记录【背景 + 内容页】，AI 层本身在该包装之外。
    //   AI 玻璃节点位于 navBackdrop 的记录子树内，若让它采样 navBackdrop 就会形成
    //   navLayer → AI玻璃 → navLayer 的 RenderNode 自引用环，HWUI prepareTree 无限
    //   递归 → RenderThread 栈溢出（2026-10-11 23:27 的原生崩溃，512 帧纯递归）。
    val aiBackdrop = rememberLayerBackdrop()

    val inAiMode = (selectedTab == 1)

    // 键盘
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val imeVisible = imeBottom > 0.dp

    val statusBarTop = WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    // 动画 State 保留在父层，但不在 MainScreen 中用 by 读取。
    // 这样每帧只会使导航栏子组件重组，不会带着 AI 聊天层和设置页一起重组。
    // 常规态保持原始尺寸 24 / 72 / 36（导航栏本体不改；AI 态 60dp 与聊天页内容预留绑定，不动）。
    val navHorizontalPadding = animateDpAsState(
        targetValue = if (inAiMode) 0.dp else 24.dp,
        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "navH"
    )
    val navHeight = animateDpAsState(
        targetValue = if (inAiMode) 60.dp else 72.dp,
        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "navHeight"
    )
    val navCorner = animateDpAsState(
        targetValue = if (inAiMode) 0.dp else 36.dp,
        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "navCorner"
    )

    // 不要把一个动画值作为另一个动画的 target；否则 target 每帧变化，形成嵌套动画。
    val phoneNavBlockHeight = animateDpAsState(
        targetValue = if (inAiMode) 60.dp + navBarBottom else 72.dp,
        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "phoneNavBlockHeight"
    )
    val phoneNavBlockBottom = animateDpAsState(
        targetValue = if (inAiMode) 0.dp else 24.dp + navBarBottom,
        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "phoneNavBlockBottom"
    )

    // aiSlide 保留为 State，并仅在 AiLayer 的 graphicsLayer 绘制阶段读取。
    // 动画帧不会触发 MainScreen 或 AiChatScreen 的逐帧组合。
    val aiSlide = animateFloatAsState(
        targetValue = if (selectedTab == 1) 0f else 1f,
        // 与导航动画统一时长，缩短离开 AI 页时与设置页初始化的重叠时间。
        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "aiSlide"
    )

    // ★ AI 页的对话记录栏是否打开（用来让左侧导航栏向左收回）
    var aiDrawerOpen by remember { mutableStateOf(false) }
    val sharePlugins = remember { FeatureRepository(context).listInstalled() }
    val shareAiAvailable = useAiPro

    // ★ 平板端：导航栏 ↔ 侧栏 切换（连点左上角应用图标 5 次切换）
    var sidebarOpen by remember { mutableStateOf(false) }
    // ★ 双击导航栏 AI 图标：弹出「切换 AI」窗口
    var showAiSwitcher by remember { mutableStateOf(false) }
    // ★ 切换 AI 后的欢迎层；只有存在对应品牌图片资源时才显示。
    var welcomeAi by remember { mutableStateOf<AiWelcomeSpec?>(null) }

    // ★ 前 3 次打开软件时提示侧栏手势（连点应用图标 5 次）。
    val sidebarHintCount = remember {
        val sp = context.getSharedPreferences("sidebar_hint", Context.MODE_PRIVATE)
        val count = sp.getInt("launch_count", 0)
        if (count < 3) sp.edit().putInt("launch_count", count + 1).apply()
        count
    }
    LaunchedEffect(sidebarHintCount) {
        // 软件日志：应用启动（供"导出日志 → 软件"筛选）。
        com.yanyuan.toolbox.util.LogCenter.log(
            context, com.yanyuan.toolbox.util.LogCenter.CAT_APP, "app", "应用启动"
        )
        // 权限日志：启动时做一次权限快照差分 —— 检测"权限丢失"并记录时间与原因。
        com.yanyuan.toolbox.util.LogCenter.trackPermissionChanges(context, "启动时")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= TABLET_WIDTH_BREAKPOINT

            // ★ 侧栏手势提示：只在能用侧栏的设备（平板 / 横屏）上前 3 次启动弹出。
            //   竖屏手机没有侧栏，不提示。
            LaunchedEffect(sidebarHintCount, isTablet) {
                if (sidebarHintCount < 3 && isTablet) {
                    Toast.makeText(
                        context,
                        "提示：连续点击左上角应用图标 5 次，可展开 / 收起侧栏",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // ★ 可见性控制
            //   aiDrawerOpen：打开 AI 页的对话记录栏时，导航栏向左收回。
            //   —— 平板端也要收回（左侧竖栏让位给侧栏），手机端底部导航栏同样收回。
            val navVisibleByAbout =
                !(selectedTab == 2 && settingsFullscreen) && !aiDrawerOpen
            val navVisibleByKeyboard = !imeVisible

            // 平板左侧导航栏占用宽度（含间距）
            val sideNavWidth = 72.dp
            val sideNavStartMargin = 12.dp

            // ★ 平板端三个页面统一右移：固定 inset 替代"逐卡片收缩"；
            //   侧栏展开时让位到侧栏全宽；导航隐藏（全屏设置）时归零。
            val contentStartInset by animateDpAsState(
                targetValue = when {
                    !navVisibleByAbout -> 0.dp
                    // 侧栏仅平板（含横屏）可用；竖屏手机不启用侧栏，也不给内容让位。
                    sidebarOpen && isTablet -> TABLET_SIDEBAR_WIDTH + sideNavStartMargin + 12.dp
                    isTablet -> AI_NAV_LEFT_INSET
                    else -> 0.dp
                },
                animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                label = "contentStartInset"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    // The full-screen backdrop recording is unnecessary in classic navigation mode.
                    // Keeping it attached in that mode adds drawing/compositing work during page transitions.
                    .then(
                        if (useLiquidGlassNavigation) Modifier.layerBackdrop(navBackdrop) else Modifier
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {

                // AI 玻璃采样层：只包【背景 + 内容页】（AI 层在包装之外，杜绝采样自引用环）。
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (useLiquidGlassNavigation) Modifier.layerBackdrop(aiBackdrop) else Modifier
                        )
                ) {

                // ============ 背景图 ============
                if (bgBitmap != null) {
                    Image(
                        bitmap = bgBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                            )
                    )
                }

                // ============ 内容层（功能 / 设置）============
                if (selectedTab != 1) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            // Keep the original full page transition. The short glass-lens
                            // optimization below handles the extra GPU cost during phone settings
                            // transitions; do not remove or replace this animation.
                            val forward = targetState > initialState
                            val dir = if (forward) 1 else -1
                            (
                                    slideInHorizontally(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> fullWidth * dir }
                                    ) + fadeIn(tween(220))
                                    ) togetherWith (
                                    slideOutHorizontally(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> -fullWidth * dir }
                                    ) + fadeOut(tween(180))
                                    )
                        },
                        label = "cards",
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .zIndex(1f)
                    ) { tab ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(top = statusBarTop)
                                // 平板三页整体固定右移，给左侧导航/侧栏让位（替代逐卡片收缩）。
                                .padding(start = contentStartInset)
                        ) {
                            when (tab) {
                                0 -> FeatureScreen(
                                    importEventChannel = importEventChannel,
                                    showWelcome = !sidebarOpen,
                                    onToggleSidebar = { sidebarOpen = !sidebarOpen }
                                )
                                2 -> SettingsScreen(
                                    performanceMode = performanceMode,
                                    onPerformanceModeChange = { mode ->
                                        performanceMode = mode
                                        // 软件日志：设置项修改。
                                        com.yanyuan.toolbox.util.LogCenter.log(
                                            context, com.yanyuan.toolbox.util.LogCenter.CAT_APP,
                                            "setting", "性能模式变更：$mode"
                                        )
                                    },
                                    navigationGlassEnabled = useLiquidGlassNavigation,
                                    onNavigationGlassChange = { enabled ->
                                        useLiquidGlassNavigation = enabled
                                        navigationPrefs.edit()
                                            .putBoolean(NAV_STYLE_KEY_LIQUID_GLASS, enabled)
                                            .apply()
                                        com.yanyuan.toolbox.util.LogCenter.log(
                                            context, com.yanyuan.toolbox.util.LogCenter.CAT_APP,
                                            "nav_style",
                                            "悬浮导航栏样式：" + if (enabled) "液态玻璃" else "毛玻璃"
                                        )
                                    },
                                    onFullscreenChange = { full ->
                                        settingsFullscreen = full
                                        com.yanyuan.toolbox.util.LogCenter.log(
                                            context, com.yanyuan.toolbox.util.LogCenter.CAT_APP,
                                            "setting", "设置页全屏：$full"
                                        )
                                    }
                                )
                                else -> {}
                            }
                        }
                    }
                }

                    // 关闭 AI 采样层包装（背景 + 内容）。
                }

// ============ AI 层 ============
                AiLayer(
                    viewModel = aiViewModel,
                    proViewModel = aiProViewModel,
                    // 接入 API 的模型 → 使用仿原页的新页（对话记录与原 AI 不互通）。
                    proMode = useAiPro,
                    proModelId = importedPlatformId(proModelId).orEmpty(),
                    statusBarTop = statusBarTop,
                    slide = aiSlide,
                    // AI 聊天内容使用稳定的导航区高度。不能传 navHeight.value 的动画值，
                    // 否则切页期间每一帧都会改变 AiChatScreen 的参数并触发重组/重新布局。
                    navHeight = if (isTablet) 0.dp else 60.dp,
                    visible = selectedTab == 1,
                    // ★ 这个避让宽度【只取决于设备】，不跟随侧栏开合。
                    //   之前写成 `isTablet && !aiDrawerOpen`，结果打开对话记录栏时
                    //   内容左边的 96dp 被就地抹掉 → 整块回答框向左扩张、侵占导航栏区域，
                    //   关掉侧栏又缩回来，看起来就是"左移后又右移"。
                    //   导航栏收回是导航栏自己的动画，不应该改变内容的布局宽度。
                    // AI 页同样让位：与三页共用 contentStartInset（侧栏展开时一起右移）。
                    navInset = contentStartInset,
                    // 液态玻璃模式下，AI 页输入框/按钮折射导航层记录（与导航栏同一 backdrop）。
                    glassBackdrop = if (useLiquidGlassNavigation && selectedTab == 1) aiBackdrop else null,
                    aiShareHandoff = aiShareHandoff,
                    onAiShareConsumed = onAiShareConsumed,
                    agentEnvironmentAvailable = agentEnvironmentAvailable,
                    agentEnabled = agentEnabled,
                    onAgentEnabledChange = { enabled ->
                        com.yanyuan.toolbox.util.AgentFeatureStore.set(context, enabled)
                        agentEnabled = enabled
                        aiProViewModel.setAgentEnabled(enabled)
                    },
                    onDrawerOpenChange = { drawerOpen -> aiDrawerOpen = drawerOpen }
                )
            }

            shareHandoff?.let { handoff ->
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .zIndex(30f)
                ) {
                    ShareHandoffScreen(
                        handoff = handoff,
                        plugins = sharePlugins.filter { it.manifest.type.equals("web", ignoreCase = true) },
                        aiAvailable = shareAiAvailable,
                        onUseAi = {
                            selectedTab = 1
                            onShareToAi(handoff)
                        },
                        onUsePlugin = { plugin -> onShareToPlugin(plugin) },
                        onCancel = onCancelShare
                    )
                }
            }

            // ========================================================
            //  导航栏（平板：左侧竖栏 / 手机：底部横栏）
            // ========================================================
            BottomNavLayer(
                visible = navVisibleByAbout,
                visibleByKeyboard = navVisibleByKeyboard,
                isTablet = isTablet,
                inAiMode = inAiMode,
                selectedTab = selectedTab,
                onSelectTab = { tab ->
                    // 软件日志：页面切换。
                    com.yanyuan.toolbox.util.LogCenter.log(
                        context, com.yanyuan.toolbox.util.LogCenter.CAT_APP,
                        "page", "切换到" + navDescriptionFor(tab)
                    )
                    // 权限日志：去设置页时再做一次快照差分（改权限常发生在系统设置后返回）。
                    if (tab == 2) {
                        com.yanyuan.toolbox.util.LogCenter
                            .trackPermissionChanges(context, "切换到设置页时")
                    }
                    selectedTab = tab
                },
                sidebarOpen = sidebarOpen,
                onSidebarOpenChange = { open ->
                    // 软件日志：侧栏开合。
                    com.yanyuan.toolbox.util.LogCenter.log(
                        context, com.yanyuan.toolbox.util.LogCenter.CAT_APP,
                        "sidebar", if (open) "侧栏展开" else "侧栏收起"
                    )
                    sidebarOpen = open
                },
                onOpenAiSwitcher = { showAiSwitcher = true },
                glassAlpha = glassAlpha,
                backdrop = navBackdrop,
                useLiquidGlassNavigation = useLiquidGlassNavigation,
                navBarBottom = navBarBottom,
                sideNavWidth = sideNavWidth,
                sideNavStartMargin = sideNavStartMargin,
                statusBarTop = statusBarTop,
                navHorizontalPaddingState = navHorizontalPadding,
                phoneNavBlockHeightState = phoneNavBlockHeight,
                phoneNavBlockBottomState = phoneNavBlockBottom,
                navCornerState = navCorner,
                navHeightState = navHeight
            )

            // ========================================================
            //  双击导航栏 AI 图标弹出的「切换 AI」窗口
            // ========================================================
            if (showAiSwitcher) {
                AiSwitcherDialog(
                    onDismiss = { showAiSwitcher = false },
                    onAiSwitched = { welcomeSpec ->
                        showAiSwitcher = false
                        // 没有对应品牌图片时，欢迎层整体不显示。
                        welcomeAi = welcomeSpec
                    }
                )
            }

            // ========================================================
            //  选择有品牌图片的 AI 后的欢迎全屏（两行）
            // ========================================================
            welcomeAi?.let { welcomeSpec ->
                AiWelcomeOverlay(
                    spec = welcomeSpec,
                    onFinished = { welcomeAi = null }
                )
            }

            // ========================================================
            //  平板：右下角矩形加号（仅功能页显示）
            //  ★ 从右侧滑入滑出
            //  ★ 主色调 + 透明度跟随 glassAlpha
            // ========================================================
            if (isTablet && navVisibleByAbout && navVisibleByKeyboard) {
                AnimatedVisibility(
                    visible = selectedTab == 0,
                    enter = slideInHorizontally(
                        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                        initialOffsetX = { it * 3 }
                    ) + fadeIn(tween(200)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                        targetOffsetX = { it * 3 }
                    ) + fadeOut(tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 24.dp,
                            bottom = navBarBottom + 24.dp
                        )
                        .zIndex(10f)
                ) {
                    // ★ 与手机端完全一致的 FloatingActionButton
                    FloatingActionButton(
                        onClick = { importEventChannel.trySend(Unit) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "导入功能",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * 将 AI 聊天层独立出来：退出/进入动画只在图层绘制阶段读取进度，
 * 避免动画每一帧重组 MainScreen 和聊天消息内容。
 */
@Composable
private fun AiLayer(
    viewModel: AiChatViewModel,
    proViewModel: AiChatViewModel?,
    proMode: Boolean,
    proModelId: String,
    statusBarTop: Dp,
    slide: State<Float>,
    navHeight: Dp,
    visible: Boolean,
    navInset: Dp,
    glassBackdrop: Backdrop?,
    aiShareHandoff: com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff? = null,
    onAiShareConsumed: () -> Unit = {},
    agentEnvironmentAvailable: Boolean,
    agentEnabled: Boolean,
    onAgentEnabledChange: (Boolean) -> Unit,
    onDrawerOpenChange: (Boolean) -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .clipToBounds()
    ) {
        val layerHeight = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = slide.value * layerHeight
                    alpha = 1f - slide.value
                }
        ) {
            if (proMode && proViewModel != null) {
                // 接入 API 的模型：复刻原 AI 页（同布局同交互）——
                // 导入 API 页面：开启附件与模型/强度选择；会话按模型分库。
                AiChatScreen(
                    viewModel = proViewModel,
                    statusBarTop = statusBarTop,
                    softwareNavHeight = navHeight,
                    visible = visible,
                    navLeftInset = navInset,
                    glassBackdrop = glassBackdrop,
                    aiShareHandoff = aiShareHandoff,
                    onAiShareConsumed = onAiShareConsumed,
                    onDrawerOpenChange = onDrawerOpenChange,
                    apiMode = true,
                    showAgentSettings = true,
                    agentEnvironmentAvailable = agentEnvironmentAvailable,
                    agentEnabled = agentEnabled,
                    onAgentEnabledChange = onAgentEnabledChange,
                    showAttach = true,
                    showModelMenu = true,
                    proModelId = proModelId,
                    agentOnly = agentEnabled
                )
            } else {
                AiChatScreen(
                    viewModel = viewModel,
                    statusBarTop = statusBarTop,
                    softwareNavHeight = navHeight,
                    visible = visible,
                    navLeftInset = navInset,
                    glassBackdrop = glassBackdrop,
                    onDrawerOpenChange = onDrawerOpenChange
                )
            }
        }
    }
}

/**
 * 导航栏（平板：左侧竖栏 / 手机：底部横栏）。
 *
 * ★ 关键：收回方向必须和设备匹配。
 *   原来两层 AnimatedVisibility 的"外层"固定用 slideOutHorizontally(targetOffsetX = -it)，
 *   对平板（左侧竖栏）是对的，但手机是【底部横栏】，向左滑出看起来就像"往右缩走"。
 *   现在统一在这里按 isTablet 选方向：
 *     平板 → 水平（向左滑出/滑入）
 *     手机 → 垂直（向下滑出/滑入）
 *   键盘弹出时永远用"向下滑出"（贴底元素让位给键盘）。
 */
@Composable
private fun BottomNavLayer(
    visible: Boolean,
    visibleByKeyboard: Boolean,
    isTablet: Boolean,
    inAiMode: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    sidebarOpen: Boolean,
    onSidebarOpenChange: (Boolean) -> Unit,
    onOpenAiSwitcher: () -> Unit,
    glassAlpha: Float,
    backdrop: Backdrop,
    useLiquidGlassNavigation: Boolean,
    navBarBottom: Dp,
    sideNavWidth: Dp,
    sideNavStartMargin: Dp,
    statusBarTop: Dp,
    navHorizontalPaddingState: State<Dp>,
    phoneNavBlockHeightState: State<Dp>,
    phoneNavBlockBottomState: State<Dp>,
    navCornerState: State<Dp>,
    navHeightState: State<Dp>
) {
    // Animation states are read only inside the nav layer, so they don't recompose AI chat every frame.
    val navHorizontalPadding by navHorizontalPaddingState
    val phoneNavBlockHeight by phoneNavBlockHeightState
    val phoneNavBlockBottom by phoneNavBlockBottomState
    val navCorner by navCornerState
    val navHeight by navHeightState
    val density = LocalDensity.current
    val selectedTabState = rememberUpdatedState(selectedTab)
    val onSelectTabState = rememberUpdatedState(onSelectTab)

    // 轨道前景拆两层轻量记录（导航面的模糊底仍不进采样）：
    // trackBackdrop = 可读性色膜 + 描边；iconsBackdrop = 图标。
    // 滑块用两个 drawBackdrop 节点分别采样（见 liquidGlassThumbSurface）：
    //   节点A：背景 + 色膜 + 描边 —— 收缩时整体起雾（blur 随 collapse 上升）；
    //   节点B：图标 —— 只做镜头折射、【永不模糊】，并承载淡黑胶囊膜（膜压在图标之上）。
    // 由此"缩小/变黑过程中背景起雾"与"图标始终清晰"同时成立；两层都是轻量前景，
    // 昂贵的模糊底全应用只画一次。
    val trackBackdrop = rememberLayerBackdrop()
    val iconsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)

    // 互动反馈（Backdrop 文档《Interactive Glass Bottom Bar》：按压时放大）。
    // 放大作用在 drawBackdrop 的 layerBlock 里——drawBackdrop 会对背景采样做逆变换，
    // 所以只有前景表面被放大，背景保持固定（教程 "Prevent backdrop from scaling" 的结论）。
    val barInteractionSource = remember { MutableInteractionSource() }
    val barPressed by barInteractionSource.collectIsPressedAsState()
    val barPressProgress by animateFloatAsState(
        targetValue = if (barPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "navBarPressScale"
    )

    var showGlassSlider by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var isCollapsingGlassSlider by remember { mutableStateOf(false) }
    var isFadingGlassSlider by remember { mutableStateOf(false) }
    var useManualSliderPosition by remember { mutableStateOf(false) }
    var sliderDragCenterFraction by remember { mutableFloatStateOf(Float.NaN) }
    var collapseImmediately by remember { mutableStateOf(false) }
    var sliderInteractionToken by remember { mutableIntStateOf(0) }

    // After the finger lifts, animate from the exact release point to the nearest icon center.
    // Keep the dragged fraction visible until Animatable is primed, avoiding a one-frame flash.
    var isSnappingSlider by remember { mutableStateOf(false) }
    var isSnapAnimationReady by remember { mutableStateOf(false) }
    var sliderSnapStartFraction by remember { mutableFloatStateOf(0.5f) }
    var sliderSnapTargetFraction by remember { mutableFloatStateOf(0.5f) }
    var sliderSnapRequestToken by remember { mutableIntStateOf(0) }
    val sliderSnapAnimatedFraction = remember { Animatable(0.5f) }

    // Release -> collapse immediately into the selected slot as a dark translucent pill -> hold -> fade.
    LaunchedEffect(sliderInteractionToken, isDraggingSlider, useLiquidGlassNavigation) {
        if (!useLiquidGlassNavigation) {
            showGlassSlider = false
            isDraggingSlider = false
            isCollapsingGlassSlider = false
            isFadingGlassSlider = false
            useManualSliderPosition = false
            sliderDragCenterFraction = Float.NaN
            isSnappingSlider = false
            isSnapAnimationReady = false
            collapseImmediately = false
            return@LaunchedEffect
        }
        if (isDraggingSlider) {
            showGlassSlider = true
            isCollapsingGlassSlider = false
            isFadingGlassSlider = false
            return@LaunchedEffect
        }
        if (showGlassSlider) {
            isFadingGlassSlider = false
            // Keep the transparent lens visible while the release snap finishes, then collapse it.
            if (collapseImmediately) delay(760L) else delay(820L)
            isCollapsingGlassSlider = true
            delay(460L) // Match the 460ms size-collapse animation.
            delay(1000L) // Clearly hold the charcoal capsule before fading it away.
            isFadingGlassSlider = true
            // Keep the black capsule mounted through the 400ms linear fade plus a small frame buffer.
            delay(450L)
            showGlassSlider = false
            isCollapsingGlassSlider = false
            isFadingGlassSlider = false
            useManualSliderPosition = false
            sliderDragCenterFraction = Float.NaN
            collapseImmediately = false
        }
    }

    LaunchedEffect(sliderSnapRequestToken, useLiquidGlassNavigation) {
        if (!useLiquidGlassNavigation || !isSnappingSlider) return@LaunchedEffect
        isSnapAnimationReady = false
        sliderSnapAnimatedFraction.snapTo(sliderSnapStartFraction.coerceIn(1f / 6f, 5f / 6f))
        isSnapAnimationReady = true
        sliderSnapAnimatedFraction.animateTo(
            targetValue = sliderSnapTargetFraction.coerceIn(1f / 6f, 5f / 6f),
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f)
        )
        // Keep the settled target as the manual position. Releasing manual positioning here
        // makes the thumb fall back to the old centerFraction animation for one frame, then
        // slide forward again. The fade/cleanup effect resets this position after the thumb hides.
        sliderDragCenterFraction = sliderSnapTargetFraction.coerceIn(1f / 6f, 5f / 6f)
        isSnapAnimationReady = false
        isSnappingSlider = false
        useManualSliderPosition = true
    }

    val sliderExpandedTarget = useLiquidGlassNavigation && showGlassSlider && !isCollapsingGlassSlider
    val sliderProgress by animateFloatAsState(
        targetValue = if (sliderExpandedTarget) 1f else 0f,
        // Opening uses a soft spring so the oversized lens visibly blooms out of the nav bar.
        // Closing remains a controlled tween into the dark idle capsule.
        animationSpec = if (sliderExpandedTarget) {
            spring(dampingRatio = 0.58f, stiffness = 360f)
        } else {
            tween(460, easing = FastOutSlowInEasing)
        },
        label = "glassSliderExpansion"
    )
    // 采样切换（progress ≤ 0.12 = 淡黑胶囊）：A、B 两层采样都切到 emptyBackdrop ——
    // 淡黑膜下透出真实的导航栏（自带 4dp 模糊，外观一致）与【清晰】的真实图标。
    // 收缩/变黑过程中：A 层（背景+色膜+描边）随收缩起雾，B 层（图标）只折射、永远清晰。
    val pillMode = sliderProgress <= 0.12f
    val thumbTrackSample = if (pillMode) emptyBackdrop() else combinedBackdrop
    val thumbIconsSample = if (pillMode) emptyBackdrop() else iconsBackdrop

    // ===== 侧栏切换的分阶段时序（与几何块的动画分段一一对应）=====
    // 展开：导航图标【立刻消失】→ 向上长满 → 扩宽 →【显示侧栏图标】；
    // 收起：侧栏图标【立刻消失】→ 缩窄 → 落回 →【显示导航图标】。
    var showNavStrip by remember { mutableStateOf(true) }
    var showSidebarPanel by remember { mutableStateOf(false) }
    var sidebarSequenceFirst by remember { mutableStateOf(true) }
    LaunchedEffect(sidebarOpen) {
        if (sidebarSequenceFirst) {
            sidebarSequenceFirst = false
        } else if (sidebarOpen) {
            showNavStrip = false
            delay((SIDEBAR_HEIGHT_MS + SIDEBAR_WIDTH_MS).toLong())
            showSidebarPanel = true
        } else {
            showSidebarPanel = false
            delay((SIDEBAR_WIDTH_MS + SIDEBAR_HEIGHT_MS).toLong())
            showNavStrip = true
        }
    }

    // ===== AI 图标双击 → 「切换 AI」弹窗（每次点击都照常走 selectNavTab，不影响导航动效/滑块） =====
    var aiTapCount by remember { mutableIntStateOf(0) }
    var lastAiTapUptime by remember { mutableLongStateOf(0L) }
    fun markAiTap(): Boolean {
        val now = SystemClock.uptimeMillis()
        aiTapCount = if (now - lastAiTapUptime > 350L) 1 else aiTapCount + 1
        lastAiTapUptime = now
        if (aiTapCount >= 2) {
            aiTapCount = 0
            onOpenAiSwitcher()
            return true
        }
        return false
    }
    // Drag speed drives the jelly deformation: slow movement barely changes the capsule,
    // while a quick swipe adds only a small stretch/squeeze. The under-damped spring then
    // rebounds past its resting shape when the finger slows or lifts.
    var sliderMotionDeformTarget by remember { mutableFloatStateOf(0f) }
    var lastSliderDragUptimeMillis by remember { mutableLongStateOf(0L) }
    var sliderMotionToken by remember { mutableIntStateOf(0) }

    fun updateSliderMotion(distancePx: Float, uptimeMillis: Long) {
        val previous = lastSliderDragUptimeMillis
        lastSliderDragUptimeMillis = uptimeMillis
        if (previous <= 0L || uptimeMillis <= previous) return
        // Pointer events can arrive only 1–3ms apart; a small minimum window prevents
        // a tiny movement from being interpreted as a violent swipe.
        val elapsedMs = (uptimeMillis - previous).toFloat().coerceAtLeast(8f)
        val speedDpPerSecond = abs(distancePx) * 1000f / elapsedMs / density.density.coerceAtLeast(0.1f)
        val speedRatio = ((speedDpPerSecond - 130f) / 780f).coerceIn(0f, 1f)
        // Smoothstep removes threshold jumps. Cap the response before it reaches the shape,
        // keeping the lens from looking flattened while still reacting to quick swipes.
        val smoothSpeed = speedRatio * speedRatio * (3f - 2f * speedRatio)
        sliderMotionDeformTarget = smoothSpeed * 0.72f
        sliderMotionToken++
    }

    // If the pointer pauses mid-drag, relax the shape instead of leaving it frozen in a squeeze.
    LaunchedEffect(sliderMotionToken, isDraggingSlider) {
        if (isDraggingSlider && sliderMotionDeformTarget > 0f) {
            delay(115L)
            sliderMotionDeformTarget = 0f
        }
    }

    val sliderVelocityDeform by animateFloatAsState(
        targetValue = sliderMotionDeformTarget,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 300f),
        label = "glassSliderVelocityJelly"
    )
    // Keep the slight negative spring overshoot: it gently narrows the capsule and lets it grow
    // taller on settling, instead of stopping dead at the exact resting dimensions.
    val sliderJelly = sliderVelocityDeform.coerceIn(-0.20f, 1f)
    // A gentle engagement lift plays while dragging. This counters the visual impression that
    // the lens is trapped inside the nav height; velocity only adds a small directional stretch.
    val sliderDragEngagement by animateFloatAsState(
        targetValue = if (isDraggingSlider) 1f else 0f,
        animationSpec = if (isDraggingSlider) {
            spring(dampingRatio = 0.72f, stiffness = 420f)
        } else {
            spring(dampingRatio = 0.42f, stiffness = 300f)
        },
        label = "glassSliderDragLift"
    )
    val sliderOpacity by animateFloatAsState(
        targetValue = if (useLiquidGlassNavigation && showGlassSlider && !isFadingGlassSlider) 1f else 0f,
        animationSpec = if (isFadingGlassSlider) {
            // Fade the entire charcoal capsule linearly over 400ms.
            tween(durationMillis = 400, easing = LinearEasing)
        } else {
            tween(180, easing = FastOutSlowInEasing)
        },
        label = "glassSliderOpacity"
    )

    // Icons stay in their normal navigation layer underneath the glass thumb. The nearest icon
    // follows the thumb's live center, so the glass never switches from a refracted icon to a
    // separately drawn foreground icon when it collapses into the dark capsule.

    val selectNavTab: (Int) -> Unit = { tab ->
        // Reset the old drag-space coordinate before changing page/layout (AI page changes nav width/height).
        useManualSliderPosition = false
        sliderDragCenterFraction = Float.NaN
        isSnappingSlider = false
        isSnapAnimationReady = false
        sliderSnapRequestToken++
        sliderMotionDeformTarget = 0f
        lastSliderDragUptimeMillis = 0L
        if (useLiquidGlassNavigation) {
            collapseImmediately = false
            isDraggingSlider = false
            showGlassSlider = true
            isCollapsingGlassSlider = false
            isFadingGlassSlider = false
            sliderInteractionToken++
        }
        onSelectTab(tab)
    }

    val useHorizontal = isTablet && visibleByKeyboard
    val outerVisible = visible && visibleByKeyboard

    AnimatedVisibility(
        visible = outerVisible,
        enter = if (useHorizontal) {
            slideInHorizontally(
                animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { -it }
            ) + fadeIn(tween(200))
        } else {
            slideInVertically(
                animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(200))
        },
        exit = if (useHorizontal) {
            slideOutHorizontally(
                animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { -it }
            ) + fadeOut(tween(200))
        } else {
            slideOutVertically(
                animationSpec = tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(200))
        },
        modifier = Modifier.fillMaxSize().zIndex(10f)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (isTablet) {
                // ===== 滑块几何（提升到分支作用域）=====
                // 滑块必须画在被记录的导航面/图标之外（否则滑块会被自身采样，产生自反馈），
                // 因此尺寸/位置计算从手势节点里提出来。
                // 导轨高度随屏幕高度拉长（maxHeight = 外层全屏 BoxWithConstraints 的屏幕高）：
                //   0.4 × 屏幕高，下限 = 轨宽×3（此时收缩滑块正好 60dp 正圆），上限 360dp。
                // 容器、镜像占位、槽位/收缩/展开尺寸全部由 railHeight 推导，手机端不使用此逻辑。
                // 下限用【收起态轨宽】×3（72×3=216，最短时收缩滑块为正圆）。
                // 不能用动画中的 railWidth：侧栏展开时它在 72~224 之间过渡，×3 后可能
                // 超过上限 360，coerceIn(min > max) 会直接抛异常（21:30 的崩溃即此）。
                val railHeight = (maxHeight * TABLET_RAIL_HEIGHT_FRACTION)
                    .coerceIn(sideNavWidth * 3f, TABLET_RAIL_HEIGHT_MAX)

                // ===== 侧栏切换的分阶段动画 =====
                // 展开：先向上（高度段）→ 后扩宽（宽度段）；收起反序：先缩窄 → 后落下。
                val sidebarHeightSpec: AnimationSpec<Dp> = if (sidebarOpen) {
                    tween(SIDEBAR_HEIGHT_MS, easing = FastOutSlowInEasing)
                } else {
                    tween(SIDEBAR_HEIGHT_MS, delayMillis = SIDEBAR_WIDTH_MS, easing = FastOutSlowInEasing)
                }
                val sidebarWidthSpec: AnimationSpec<Dp> = if (sidebarOpen) {
                    tween(SIDEBAR_WIDTH_MS, delayMillis = SIDEBAR_HEIGHT_MS, easing = FastOutSlowInEasing)
                } else {
                    tween(SIDEBAR_WIDTH_MS, easing = FastOutSlowInEasing)
                }
                // 侧板全高 = 状态栏以下到手势条以上。
                val sidebarFullHeight = maxHeight - statusBarTop - navBarBottom
                val railHeightAnimated by animateDpAsState(
                    targetValue = if (sidebarOpen) sidebarFullHeight else railHeight,
                    animationSpec = sidebarHeightSpec,
                    label = "tabletRailHeight"
                )
                val railWidth by animateDpAsState(
                    targetValue = if (sidebarOpen) TABLET_SIDEBAR_WIDTH else sideNavWidth,
                    animationSpec = sidebarWidthSpec,
                    label = "tabletRailWidth"
                )
                // 定位内边距与阶段同步：贴边随宽度段，顶部/底部随高度段。
                val railPadStart by animateDpAsState(
                    targetValue = if (sidebarOpen) 0.dp else sideNavStartMargin,
                    animationSpec = sidebarWidthSpec,
                    label = "tabletRailPadStart"
                )
                val railPadTop by animateDpAsState(
                    targetValue = if (sidebarOpen) statusBarTop else 0.dp,
                    animationSpec = sidebarHeightSpec,
                    label = "tabletRailPadTop"
                )
                val railPadBottom by animateDpAsState(
                    targetValue = if (sidebarOpen) navBarBottom else navBarBottom + 24.dp,
                    animationSpec = sidebarHeightSpec,
                    label = "tabletRailPadBottom"
                )
                val slotHeight = railHeight / 3f
                val expandedWidth = railWidth + 8.dp
                // Keep the transparent lens only slightly larger than the rail/slot; the dark
                // idle pill later returns to compact in-slot dimensions.
                val collapsedWidth = (railWidth - 12.dp).coerceAtLeast(1.dp)
                val expandedHeight = slotHeight + 8.dp
                val collapsedHeight = (slotHeight - 12.dp).coerceAtLeast(1.dp)
                val baseThumbWidth = collapsedWidth + (expandedWidth - collapsedWidth) * sliderProgress
                val baseThumbHeight = collapsedHeight + (expandedHeight - collapsedHeight) * sliderProgress
                // On a vertical rail the lens lifts slightly while held and stretches along
                // the swipe axis; horizontal compression is deliberately tiny, never flat.
                val thumbWidth = baseThumbWidth * (1f + 0.035f * sliderDragEngagement - 0.025f * sliderJelly)
                val thumbHeight = baseThumbHeight * (1f + 0.022f * sliderDragEngagement + 0.025f * sliderJelly)
                // Always keep a true capsule corner based on the current animated dimensions.
                val thumbCorner = minOf(thumbWidth, thumbHeight) / 2f
                val centerFraction by animateFloatAsState(
                    targetValue = (selectedTab.coerceIn(0, 2) + 0.5f) / 3f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "tabletGlassCenterFraction"
                )
                val trackHeightPx = with(density) { railHeight.toPx() }
                val thumbHeightPx = with(density) { thumbHeight.toPx() }
                val centerY = when {
                    isSnappingSlider && isSnapAnimationReady -> sliderSnapAnimatedFraction.value * trackHeightPx
                    useManualSliderPosition && sliderDragCenterFraction.isFinite() -> sliderDragCenterFraction * trackHeightPx
                    else -> centerFraction * trackHeightPx
                }
                val visualHighlightTab = if (useLiquidGlassNavigation && showGlassSlider) {
                    ((centerY / trackHeightPx.coerceAtLeast(1f) * 3f) - 0.5f).roundToInt().coerceIn(0, 2)
                } else selectedTab
                val offsetY = (centerY - thumbHeightPx / 2f).roundToInt()

                // 软件图标：固定在顶部（不再随导轨高度沉到屏幕中部），水平与导轨中线对齐
                //（start = 12 + (72-56)/2 = 20dp）；zIndex 盖在侧板之上；连点5次切换侧栏。
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .zIndex(2f)
                        .padding(
                            start = sideNavStartMargin + (sideNavWidth - 56.dp) / 2,
                            top = statusBarTop + 16.dp
                        )
                ) {
                    AppIconToggle(
                        modifier = Modifier.size(56.dp),
                        onToggle = { onSidebarOpenChange(!sidebarOpen) }
                    )
                }

                // 导航栏（底部圆角导轨）与侧栏（贴边全高侧板）的共用形状：
                // 侧栏态 = 只圆右缘（贴左、上下铺满），是真正的侧栏而不是圆角小卡片。
                val railShape = if (sidebarOpen) {
                    RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                } else {
                    RoundedCornerShape(36.dp)
                }

                // ===== 导航面（模糊折射底）：不进滑块采样 =====
                // 屏幕表现与原版一致（vibrancy+blur+lens+按压放大）。模糊底一旦进采样，
                // 滑块里就是"把导航面重新渲染一份"，背景被盖住，看不到同时折射。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // 分阶段定位：贴边随宽度段动，顶部/底部随高度段动（见几何块动画分段）。
                        .padding(
                            start = railPadStart,
                            top = railPadTop,
                            bottom = railPadBottom
                        )
                        .width(railWidth)
                        .height(railHeightAnimated)
                        .then(
                            if (useLiquidGlassNavigation) {
                                Modifier.liquidGlassSurface(
                                    backdrop = backdrop,
                                    shape = railShape,
                                    pressProgress = barPressProgress
                                )
                            } else {
                                Modifier
                                    .clip(railShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
                                    .then(
                                        if (sidebarOpen) Modifier else Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            RoundedCornerShape(36.dp)
                                        )
                                    )
                            }
                        )
                        .clickable(interactionSource = barInteractionSource, indication = null) { }
                )

                // ===== 轨道前景容器：色膜层与图标层各自轻量记录（模糊底不进采样）=====
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // 与导航面同一条（分阶段动画的）定位链。
                        .padding(
                            start = railPadStart,
                            top = railPadTop,
                            bottom = railPadBottom
                        )
                        .width(railWidth)
                        .height(railHeightAnimated)
                ) {
                    // 可读性色膜 + 描边（trackBackdrop 记录）：透明基底，按压放大量与导航面同步。
                    if (useLiquidGlassNavigation) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .then(
                                    if (useLiquidGlassNavigation && showGlassSlider) {
                                        Modifier.layerBackdrop(trackBackdrop)
                                    } else Modifier
                                )
                                .graphicsLayer {
                                    val maxScale = (size.width + 16.dp.toPx()) / size.width
                                    val scale = 1f + (maxScale - 1f) * barPressProgress.coerceIn(0f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(railShape)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(
                                        alpha = (0.5f * glassAlpha).coerceIn(0f, 1f)
                                    )
                                )
                                .then(
                                    if (sidebarOpen) Modifier else Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(36.dp)
                                    )
                                )
                        )
                    }
                    // 图标轨道：与导航面同级，由外层容器统一记录进 trackBackdrop。
                    // 展开瞬间先消失、收起落回后才显示（分阶段时序 showNavStrip）。
                    if (showNavStrip)
                    Box(
                        Modifier.fillMaxSize().then(
                            if (useLiquidGlassNavigation) {
                                Modifier.pointerInput(useLiquidGlassNavigation) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val axis = size.height.toFloat().coerceAtLeast(1f)
                                            sliderSnapRequestToken++
                                            isSnappingSlider = false
                                            isSnapAnimationReady = false
                                            isDraggingSlider = true
                                            sliderMotionDeformTarget = 0f
                                            lastSliderDragUptimeMillis = 0L
                                            useManualSliderPosition = true
                                            showGlassSlider = true
                                            isCollapsingGlassSlider = false
                                            isFadingGlassSlider = false
                                            sliderDragCenterFraction = (offset.y / axis).coerceIn(1f / 6f, 5f / 6f)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            updateSliderMotion(dragAmount.y, change.uptimeMillis)
                                            val axis = size.height.toFloat().coerceAtLeast(1f)
                                            sliderDragCenterFraction = (sliderDragCenterFraction + dragAmount.y / axis)
                                                .coerceIn(1f / 6f, 5f / 6f)
                                        },
                                        onDragEnd = {
                                            val releaseFraction = sliderDragCenterFraction.coerceIn(1f / 6f, 5f / 6f)
                                            val target = ((releaseFraction * 3f) - 0.5f).roundToInt().coerceIn(0, 2)
                                            sliderSnapStartFraction = releaseFraction
                                            sliderSnapTargetFraction = (target + 0.5f) / 3f
                                            isSnappingSlider = true
                                            isSnapAnimationReady = false
                                            useManualSliderPosition = true
                                            sliderSnapRequestToken++
                                            onSelectTabState.value(target)
                                            sliderMotionDeformTarget = 0f
                                            lastSliderDragUptimeMillis = 0L
                                            isDraggingSlider = false
                                            showGlassSlider = true
                                            isCollapsingGlassSlider = false
                                            isFadingGlassSlider = false
                                            collapseImmediately = true
                                            sliderInteractionToken++
                                        },
                                        onDragCancel = {
                                            val releaseFraction = sliderDragCenterFraction.coerceIn(1f / 6f, 5f / 6f)
                                            sliderSnapStartFraction = releaseFraction
                                            sliderSnapTargetFraction = (selectedTabState.value.coerceIn(0, 2) + 0.5f) / 3f
                                            isSnappingSlider = true
                                            isSnapAnimationReady = false
                                            useManualSliderPosition = true
                                            sliderSnapRequestToken++
                                            sliderMotionDeformTarget = 0f
                                            lastSliderDragUptimeMillis = 0L
                                            isDraggingSlider = false
                                            showGlassSlider = true
                                            isCollapsingGlassSlider = false
                                            isFadingGlassSlider = false
                                            collapseImmediately = true
                                            sliderInteractionToken++
                                        }
                                    )
                                }
                            } else Modifier
                        )
                    ) {
                        // 图标单独记录（iconsBackdrop）：滑块的图标层只折射、永不模糊。
                        Column(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (useLiquidGlassNavigation && showGlassSlider) {
                                        Modifier.layerBackdrop(iconsBackdrop)
                                    } else Modifier
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            (0..2).forEach { tab ->
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    NavIcon(
                                        icon = navIconFor(tab),
                                        description = navDescriptionFor(tab),
                                        selected = visualHighlightTab == tab,
                                        accentWhenSelected = true,
                                        useLiquidGlassNavigation = useLiquidGlassNavigation,
                                        size = 26.dp,
                                        onClick = {
                                            if (tab == 1) markAiTap()
                                            selectNavTab(tab)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 侧栏内容：宽度到位后才显示（分阶段时序 showSidebarPanel）。
                    if (showSidebarPanel) {
                        SidebarPanel(
                            selectedTab = selectedTab,
                            onSelectTab = onSelectTab,
                            onAiDoubleTap = onOpenAiSwitcher
                        )
                    }
                }

                // ===== 滑块（轨道记录层之外的兄弟节点）=====
                // 镜像与图标轨道同进退（showNavStrip）：收起过程中不先于轨道闪回。
                if (useLiquidGlassNavigation && showGlassSlider && showNavStrip) {
                    // 镜像导轨占位：与轨道容器同位置同尺寸，滑块保持原有的相对坐标。
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = railPadStart, bottom = railPadBottom)
                            .width(railWidth)
                            .height(railHeightAnimated)
                    ) {
                        // 采样 rememberCombinedBackdrop(backdrop, trackBackdrop)：
                        // 页面背景与轨道（导航面 + 图标）同时被折射。
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset { IntOffset(0, offsetY) }
                                .requiredSize(width = thumbWidth, height = thumbHeight)
                                // IMPORTANT: this layer must wrap drawBackdrop, not sit inside it.
                                // drawBackdrop paints the refraction after its child; placing alpha
                                // after drawBackdrop fades only the child, leaving the lens visible
                                // until it is removed (which looks like a sudden disappearance).
                                .graphicsLayer {
                                    alpha = sliderOpacity.coerceIn(0f, 1f)
                                    shape = RoundedCornerShape(thumbCorner)
                                    clip = true
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .liquidGlassThumbSurface(
                                    trackSample = thumbTrackSample,
                                    iconsSample = thumbIconsSample,
                                    shape = RoundedCornerShape(thumbCorner),
                                    progress = sliderProgress,
                                    surfaceTint = MaterialTheme.colorScheme.surface
                                )
                        )
                    }
                }
            } else {
                // The navigation background may expand behind the system bar in AI mode, but the
                // icon track remains its own stable coordinate space. Thumb position is normalized
                // to this track, so changing AI/non-AI insets cannot leave it at an old pixel offset.

                // ===== 滑块几何（提升到分支作用域）=====
                // 滑块必须画在被记录的导航面/图标之外（否则滑块会被自身采样，产生自反馈），
                // 因此尺寸/位置计算从手势节点里提出来。轨道宽度 = 屏幕宽度 - 左右导航边距，
                // 与原 BoxWithConstraints 的 maxWidth 完全等价。
                val trackWidth = maxWidth - navHorizontalPadding * 2
                val slotWidth = trackWidth / 3f
                val expandedWidth = slotWidth + 8.dp
                // Keep the transparent lens only slightly larger than the rail/slot: 6dp
                // extra width overall and 8dp extra height overall. The dark idle pill stays inset.
                // ★ 必须用 slotWidth（轨道宽度的 1/3）：用 trackWidth 会让休眠胶囊被拉满整条轨道。
                val collapsedWidth = (slotWidth - 12.dp).coerceAtLeast(1.dp)
                val expandedHeight = navHeight + 8.dp
                val collapsedHeight = (navHeight - 12.dp).coerceAtLeast(1.dp)
                // Animate the real layout size, not scaleX/scaleY inside drawBackdrop.
                // That keeps the backdrop buffer and the rounded visual bounds identical (no square halo).
                val baseThumbWidth = collapsedWidth + (expandedWidth - collapsedWidth) * sliderProgress
                val baseThumbHeight = collapsedHeight + (expandedHeight - collapsedHeight) * sliderProgress
                // While held, the capsule gently grows in both directions. Speed adds just
                // a little horizontal stretch and at most ~2.5% vertical squeeze, so it never
                // collapses to nav-bar height. On release the spring relaxes the lift with a soft rebound.
                val thumbWidth = baseThumbWidth * (1f + 0.022f * sliderDragEngagement + 0.018f * sliderJelly)
                val thumbHeight = baseThumbHeight * (1f + 0.035f * sliderDragEngagement - 0.035f * sliderJelly)
                // Radius follows the live size so the refracted lens never becomes a square.
                val thumbCorner = minOf(thumbWidth, thumbHeight) / 2f
                val centerFraction by animateFloatAsState(
                    targetValue = (selectedTab.coerceIn(0, 2) + 0.5f) / 3f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "phoneGlassCenterFraction"
                )
                val trackWidthPx = with(density) { trackWidth.toPx() }
                val thumbWidthPx = with(density) { thumbWidth.toPx() }
                val centerX = when {
                    isSnappingSlider && isSnapAnimationReady -> sliderSnapAnimatedFraction.value * trackWidthPx
                    useManualSliderPosition && sliderDragCenterFraction.isFinite() -> sliderDragCenterFraction * trackWidthPx
                    else -> centerFraction * trackWidthPx
                }
                val visualHighlightTab = if (useLiquidGlassNavigation && showGlassSlider) {
                    ((centerX / trackWidthPx.coerceAtLeast(1f) * 3f) - 0.5f).roundToInt().coerceIn(0, 2)
                } else selectedTab
                val offsetX = (centerX - thumbWidthPx / 2f).roundToInt()

                // ===== 导航面（模糊折射底）：不进滑块采样 =====
                // 屏幕表现与原版一致；模糊底一旦进采样，滑块里就是"把导航面重新渲染一份"，
                // 背景被盖住，看不到同时折射。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = navHorizontalPadding, end = navHorizontalPadding, bottom = phoneNavBlockBottom)
                        .fillMaxWidth()
                        .height(phoneNavBlockHeight)
                        .then(
                            if (useLiquidGlassNavigation) {
                                Modifier.liquidGlassSurface(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(navCorner),
                                    pressProgress = barPressProgress
                                ).clickable(interactionSource = barInteractionSource, indication = null) { }
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(navCorner))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
                                    .then(
                                        if (!inAiMode) Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            RoundedCornerShape(navCorner)
                                        ) else Modifier
                                    )
                            }
                        )
                )

                // ===== 轨道前景容器：色膜层与图标层各自轻量记录（模糊底不进采样）=====
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = navHorizontalPadding, end = navHorizontalPadding, bottom = phoneNavBlockBottom)
                        .fillMaxWidth()
                        .height(phoneNavBlockHeight)
                ) {
                    // 可读性色膜 + 描边（trackBackdrop 记录）：透明基底，按压放大量与导航面同步。
                    if (useLiquidGlassNavigation) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (useLiquidGlassNavigation && showGlassSlider) {
                                        Modifier.layerBackdrop(trackBackdrop)
                                    } else Modifier
                                )
                                .graphicsLayer {
                                    val maxScale = (size.width + 16.dp.toPx()) / size.width
                                    val scale = 1f + (maxScale - 1f) * barPressProgress.coerceIn(0f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(navCorner))
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(
                                        alpha = (0.5f * glassAlpha).coerceIn(0f, 1f)
                                    )
                                )
                                .then(
                                    if (!inAiMode) Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(navCorner)
                                    ) else Modifier
                                )
                        )
                    }

                    // 图标轨道：与导航面同级，由外层容器统一记录进 trackBackdrop。
                    // 滑块画在记录层之外的兄弟节点里，超大镜头可以伸出圆角导航面之外而不被裁剪。
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(navHeight).then(
                            if (useLiquidGlassNavigation) {
                                // Don't key this gesture node on navHeight: that value animates when
                                // entering/leaving AI and would restart the gesture/lose its coordinate.
                                Modifier.pointerInput(useLiquidGlassNavigation) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val axis = size.width.toFloat().coerceAtLeast(1f)
                                            sliderSnapRequestToken++
                                            isSnappingSlider = false
                                            isSnapAnimationReady = false
                                            isDraggingSlider = true
                                            sliderMotionDeformTarget = 0f
                                            lastSliderDragUptimeMillis = 0L
                                            useManualSliderPosition = true
                                            showGlassSlider = true
                                            isCollapsingGlassSlider = false
                                            isFadingGlassSlider = false
                                            sliderDragCenterFraction = (offset.x / axis).coerceIn(1f / 6f, 5f / 6f)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            updateSliderMotion(dragAmount.x, change.uptimeMillis)
                                            val axis = size.width.toFloat().coerceAtLeast(1f)
                                            sliderDragCenterFraction = (sliderDragCenterFraction + dragAmount.x / axis)
                                                .coerceIn(1f / 6f, 5f / 6f)
                                        },
                                        onDragEnd = {
                                            val releaseFraction = sliderDragCenterFraction.coerceIn(1f / 6f, 5f / 6f)
                                            val target = ((releaseFraction * 3f) - 0.5f).roundToInt().coerceIn(0, 2)
                                            sliderSnapStartFraction = releaseFraction
                                            sliderSnapTargetFraction = (target + 0.5f) / 3f
                                            isSnappingSlider = true
                                            isSnapAnimationReady = false
                                            useManualSliderPosition = true
                                            sliderSnapRequestToken++
                                            onSelectTabState.value(target)
                                            sliderMotionDeformTarget = 0f
                                            lastSliderDragUptimeMillis = 0L
                                            isDraggingSlider = false
                                            showGlassSlider = true
                                            isCollapsingGlassSlider = false
                                            isFadingGlassSlider = false
                                            collapseImmediately = true
                                            sliderInteractionToken++
                                        },
                                        onDragCancel = {
                                            val releaseFraction = sliderDragCenterFraction.coerceIn(1f / 6f, 5f / 6f)
                                            sliderSnapStartFraction = releaseFraction
                                            sliderSnapTargetFraction = (selectedTabState.value.coerceIn(0, 2) + 0.5f) / 3f
                                            isSnappingSlider = true
                                            isSnapAnimationReady = false
                                            useManualSliderPosition = true
                                            sliderSnapRequestToken++
                                            sliderMotionDeformTarget = 0f
                                            lastSliderDragUptimeMillis = 0L
                                            isDraggingSlider = false
                                            showGlassSlider = true
                                            isCollapsingGlassSlider = false
                                            isFadingGlassSlider = false
                                            collapseImmediately = true
                                            sliderInteractionToken++
                                        }
                                    )
                                }
                            } else Modifier
                        )
                    ) {
                        // 图标单独记录（iconsBackdrop）：滑块的图标层只折射、永不模糊。
                        Row(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (useLiquidGlassNavigation && showGlassSlider) {
                                        Modifier.layerBackdrop(iconsBackdrop)
                                    } else Modifier
                                ),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (0..2).forEach { tab ->
                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    NavIcon(
                                        icon = navIconFor(tab),
                                        description = navDescriptionFor(tab),
                                        selected = visualHighlightTab == tab,
                                        accentWhenSelected = true,
                                        useLiquidGlassNavigation = useLiquidGlassNavigation,
                                        size = if (inAiMode) 24.dp else 28.dp,
                                        onClick = {
                                            if (tab == 1) markAiTap()
                                            selectNavTab(tab)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== 滑块（轨道记录层之外的兄弟节点）=====
                if (useLiquidGlassNavigation && showGlassSlider) {
                    // 镜像导航块 + 镜像图标轨道：与记录层内的轨道同位置同尺寸，
                    // 滑块因此保持原有的相对坐标。
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = navHorizontalPadding, end = navHorizontalPadding, bottom = phoneNavBlockBottom)
                            .fillMaxWidth()
                            .height(phoneNavBlockHeight)
                    ) {
                        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(navHeight)) {
                            // 采样 rememberCombinedBackdrop(backdrop, trackBackdrop)：
                            // 页面背景与轨道（导航面 + 图标）同时被折射。
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset(offsetX, 0) }
                                    .requiredSize(width = thumbWidth, height = thumbHeight)
                                    // IMPORTANT: this layer must wrap drawBackdrop, not sit inside it.
                                    // drawBackdrop paints the refraction after its child; placing alpha
                                    // after drawBackdrop fades only the child, leaving the lens visible
                                    // until it is removed (which looks like a sudden disappearance).
                                    .graphicsLayer {
                                        alpha = sliderOpacity.coerceIn(0f, 1f)
                                        shape = RoundedCornerShape(thumbCorner)
                                        clip = true
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .liquidGlassThumbSurface(
                                        trackSample = thumbTrackSample,
                                        iconsSample = thumbIconsSample,
                                        shape = RoundedCornerShape(thumbCorner),
                                        progress = sliderProgress,
                                        surfaceTint = MaterialTheme.colorScheme.surface
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun navIconFor(index: Int): ImageVector = when (index) {
    0 -> Icons.Default.Extension
    1 -> Icons.Default.AutoAwesome
    else -> Icons.Default.Settings
}

private fun navDescriptionFor(index: Int): String = when (index) {
    0 -> "功能"
    1 -> "AI"
    else -> "设置"
}

private data class AiWelcomeSpec(
    val name: String,
    val lightLogoRes: Int,
    val darkLogoRes: Int
)

private fun welcomeSpecFor(option: AiSwitchOption): AiWelcomeSpec? = when {
    option.platformId == "mimo" || option.id == "mimo" -> {
        AiWelcomeSpec(
            name = option.label,
            lightLogoRes = R.drawable.mimo_black,
            darkLogoRes = R.drawable.mimo_white
        )
    }
    option.platformId == "deepseek" || option.id == "deepseek" -> {
        AiWelcomeSpec(
            name = option.label,
            lightLogoRes = R.drawable.deepseek_logo,
            darkLogoRes = R.drawable.deepseek_logo
        )
    }
    else -> null
}

/**
 * 选择有品牌图片的 AI 后的欢迎全屏：第一行文字、第二行图片。
 * 没有匹配到图片资源时不会调用此 Composable，整个欢迎层完全不显示。
 */
@Composable
private fun AiWelcomeOverlay(
    spec: AiWelcomeSpec,
    onFinished: () -> Unit
) {
    val dark = isAppInDarkMode()
    val background = if (dark) Color.Black else Color.White
    val contentColor = if (dark) Color.White else Color.Black
    val logoRes = if (dark) spec.darkLogoRes else spec.lightLogoRes
    val context = LocalContext.current
    val logoBitmap = remember(logoRes) {
        BitmapFactory.decodeResource(context.resources, logoRes)
            ?: android.graphics.Bitmap.createBitmap(
                1,
                1,
                android.graphics.Bitmap.Config.ARGB_8888
            )
    }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(spec) {
        alpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        delay(1_600)
        alpha.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(99f)
            .graphicsLayer { this.alpha = alpha.value }
            .background(background),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "欢迎使用",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(Modifier.height(28.dp))
            Image(
                bitmap = logoBitmap.asImageBitmap(),
                contentDescription = spec.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class AiSwitchOption(
    val id: String,
    val label: String,
    val subtitle: String,
    val platformId: String? = null,
    val modelId: String? = null
) {
    val imported: Boolean get() = platformId != null && modelId != null
}

/**
 * 双击导航栏 AI 图标弹出的「切换 AI」窗口：
 * 内置模型 +「接入AI」里导入且启用的平台下的具体模型。
 * 选择后写入 AiModelPreference，下一条消息即使用所选 AI。
 */
@Composable
private fun AiSwitcherDialog(
    onDismiss: () -> Unit,
    onAiSwitched: (AiWelcomeSpec?) -> Unit = {}
) {
    val context = LocalContext.current
    val preference = remember { AiModelPreference(context) }
    val aiVersion = AiModelState.version
    val settings = remember(aiVersion) { AiModelStore.get(context) }
    var current by remember { mutableStateOf(preference.get()) }

    // ★ 内置模型沿用单模型授权；导入模型按 API 平台授权，具体模型仅用于选择。
    val authVersion = com.yanyuan.toolbox.data.AuthState.version
    val authSettings = remember(authVersion) { com.yanyuan.toolbox.data.AuthStore.get(context) }
    var pendingAuth by remember { mutableStateOf<AiSwitchOption?>(null) }

    fun commitSelect(option: AiSwitchOption) {
        if (option.imported) {
            val platformId = option.platformId ?: return
            val modelId = option.modelId ?: return
            AiChatProStorage(context).setSelectedModel(platformId, modelId)
            preference.set(importedSelectionId(platformId))
        } else {
            preference.set(option.id)
        }
        current = preference.get()
        onAiSwitched(welcomeSpecFor(option))
    }

    val options = remember(aiVersion) {
        buildList {
            AiChatModel.entries.forEach {
                add(AiSwitchOption(it.id, it.label, it.subtitle))
            }
            settings.configs.filter {
                it.enabled && (it.platform.keyOptional || it.apiKey.isNotBlank())
            }.forEach { cfg ->
                val model = settings.modelsOf(cfg.platformId)
                    .filter { !it.hidden && it.cleanModelId.isNotBlank() }
                    .let { visible ->
                        val picked = AiChatProStorage(context).getSelectedModel(cfg.platformId)
                        if (picked.isNullOrBlank()) visible.firstOrNull()
                        else visible.firstOrNull { it.cleanModelId == picked || it.id == picked }
                    }
                if (model != null) {
                    add(
                        AiSwitchOption(
                            id = importedSelectionId(cfg.platformId),
                            label = cfg.platform.displayName,
                            subtitle = model.displayName,
                            platformId = cfg.platformId,
                            modelId = model.cleanModelId
                        )
                    )
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("切换 AI", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column {
                Text(
                    "选择对话使用的模型或导入的平台 API",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                options.forEach { option ->
                    val selectedModel = if (option.imported) {
                        AiChatProStorage(context).getSelectedModel(option.platformId!!)
                    } else null
                    val isCurrent = current == option.id &&
                            (!option.imported || selectedModel == option.modelId)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                            .clickable {
                                val authorized = if (option.imported) {
                                    authSettings.isImportedApiAuthorized(option.platformId!!)
                                } else {
                                    authSettings.isBuiltinAiModelAuthorized(option.id)
                                }
                                if (!authorized) {
                                    pendingAuth = option
                                } else {
                                    commitSelect(option)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                option.subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "切换后下一条消息即使用所选 AI；在「接入AI」中导入并启用的 API 会显示在这里。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )

    // ===== 选择 AI 后弹出的单独授权选项 =====
    pendingAuth?.let { option ->
        val imported = option.imported
        val modelLabel = if (imported) "${option.label} / ${option.modelId}" else option.label
        val apiLabel = if (imported) option.label else modelLabel
        AlertDialog(
            onDismissRequest = { pendingAuth = null },
            title = {
                Text("授权「$apiLabel」", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    if (imported) {
                        "授权 $apiLabel 后，该 API 平台下的所有可见模型都可以执行对话与 Agent 文件操作；" +
                                "当前选择的模型是“${option.modelId}”。\n\n" +
                                "接口密钥经加密保存在本机，不会交给其它插件；请仅授权你信任的服务商。"
                    } else {
                        "授权 $apiLabel 后即可使用该内置模型。"
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val auth = com.yanyuan.toolbox.data.AuthStore.get(context)
                        val next = if (imported) {
                            auth.withImportedApiAuthorization(option.platformId!!, true)
                        } else {
                            auth.withBuiltinAiModelAuthorization(option.id, true)
                        }
                        com.yanyuan.toolbox.data.AuthStore.set(context, next)
                        commitSelect(option)
                        pendingAuth = null
                    }
                ) { Text("授权并使用") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAuth = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 应用图标 + 连点5次切换侧栏（500ms 计数窗口）。
 * 手机端 = 插件页头部图标，平板端 = 导轨上方图标，两处共用同一个 AppIconBadge。
 */
@Composable
fun AppIconToggle(
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapUptime by remember { mutableLongStateOf(0L) }
    AppIconBadge(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            val now = SystemClock.uptimeMillis()
            tapCount = if (now - lastTapUptime > 500L) 1 else tapCount + 1
            lastTapUptime = now
            if (tapCount >= 5) {
                tapCount = 0
                onToggle()
            }
        }
    )
}

/**
 * 平板侧栏（导航栏切换成侧栏后的内容）：
 * 顶部留出固定软件图标的位置（图标由导航层原地绘制、全程不动）；
 * 欢迎字样与插件页共用 welcomeText，靠左与下方条目（卡片）左对齐；
 * 下方 = 功能 / AI / 设置（图标 + 文字）。插件页的欢迎字样在侧栏打开期间隐藏。
 */
@Composable
private fun SidebarPanel(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onAiDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    val nickname = remember(context) { NicknameStore(context) }.nickname
    var lastAiTapAt by remember { mutableLongStateOf(0L) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 固定软件图标（状态栏+16、56dp 高）在面板内的占位（y=16..72）：
        // 侧栏欢迎字样必须从图标下方开始，否则会被盖住。
        Spacer(Modifier.height(58.dp))
        // 欢迎字样：靠左，与下方条目（卡片）左对齐（面板14 + 内缩10 = 条目左缘24）。
        Text(
            text = welcomeText(nickname),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp)
        )
        Spacer(Modifier.height(6.dp))
        (0..2).forEach { tab ->
            val tint = if (selectedTab == tab) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (tab == 1) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastAiTapAt <= 500L && lastAiTapAt != 0L) {
                                lastAiTapAt = 0L
                                onAiDoubleTap()
                            } else {
                                lastAiTapAt = now
                            }
                        }
                        onSelectTab(tab)
                    }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = navIconFor(tab),
                    contentDescription = navDescriptionFor(tab),
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = navDescriptionFor(tab),
                    fontSize = 15.sp,
                    color = tint
                )
            }
        }
    }
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    accentWhenSelected: Boolean,
    useLiquidGlassNavigation: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = if (!useLiquidGlassNavigation && selected) 1.08f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "iconScale_$description"
    )
    val targetIconTint = if (selected && (!useLiquidGlassNavigation || accentWhenSelected)) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    }
    // Tint changes are immediate. The selected accent icon during the dark-pill phase is
    // rendered as a separate foreground layer by BottomNavLayer, not animated here.
    val iconTint = targetIconTint
    if (useLiquidGlassNavigation) {
        // Liquid-glass mode uses the glass thumb for the press/selection feedback; suppress the
        // default IconButton ripple that looks like a separate black circle. Classic mode below
        // deliberately retains the original IconButton unchanged.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconTint,
                modifier = Modifier.size(size).scale(iconScale)
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconTint,
                modifier = Modifier.size(size).scale(iconScale)
            )
        }
    }
}

/**
 * 滑块 = 两个 drawBackdrop 节点（同形状、同镜头参数）：
 * - 节点A（trackSample）：页面背景 + 可读性色膜 + 描边 —— 收缩时整体起雾
 *   （blur 从 0 随 collapse 上升），lens 12/16 + 色散（文档滑块参数）；
 * - 节点B（iconsSample）：图标 —— 只有 lens、【永远不模糊】，并承载淡黑胶囊膜
 *   （膜画在图标之后，变黑过程中图标被膜压住但保持锐利）。
 * 透明玻璃态两层正常采样、零色膜；淡黑胶囊两层都切 emptyBackdrop（膜下透出真实导航栏与图标）。
 */
private fun Modifier.liquidGlassThumbSurface(
    trackSample: Backdrop,
    iconsSample: Backdrop,
    shape: Shape,
    progress: Float,
    surfaceTint: Color
): Modifier =
    // Match both the drawBackdrop mask and the compositing layer clip to avoid square transparent bounds.
    clip(shape)
        .drawBackdrop(
            backdrop = trackSample,
            shape = { shape },
            effects = {
                // 收缩即起雾：透明玻璃态半径 0；缩小过程中背景/色膜/描边渐进模糊。
                // 胶囊态采样已切到 emptyBackdrop，此处无像素可作用；
                // 胶囊的"与导航栏一样"来自淡黑膜下透出的真实（自带 4dp 模糊）导航栏。
                val collapse = 1f - progress.coerceIn(0f, 1f)
                blur(4.dp.toPx() * collapse)
                lens(12.dp.toPx(), 16.dp.toPx(), chromaticAberration = true)
            }
        )
        .drawBackdrop(
            backdrop = iconsSample,
            shape = { shape },
            effects = {
                // 图标只折射、不模糊 → 整个收缩/变黑过程中图标始终清晰。
                lens(12.dp.toPx(), 16.dp.toPx(), chromaticAberration = true)
            },
            highlight = null,
            shadow = null,
            onDrawSurface = {
                // 淡黑膜挂在图标层节点：画在图标之后（压在图标上），盖住的是下方真实内容。
                val collapsed = 1f - progress.coerceIn(0f, 1f)
                drawRect(surfaceTint.copy(alpha = 0.04f * collapsed))
                drawRect(Color.White.copy(alpha = 0.010f * collapsed))
                drawRect(Color.Black.copy(alpha = 0.22f * collapsed))
            }
        )

/**
 * 导航面的模糊折射底 = 文档《Glass Bottom Bar》最终代码的 effects 部分
 * （vibrancy + blur + lens）+ 《Interactive Glass Bottom Bar》的按压放大 layerBlock。
 *
 * 可读性色膜【不在这里】：它属于轨道前景层（与描边、图标一起进滑块采样，透明基底），
 * 否则滑块采到的会是"含模糊底的整份导航面复制"，背景被盖住、看不到同时折射。
 * 屏幕上两部分叠在一起，与文档最终效果一致。
 */
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    pressProgress: Float,
    // 可读性色膜：覆盖在玻璃之上，保证玻璃上的文字清晰易读（0 = 不加膜）。
    filmAlpha: Float = 0f,
    filmColor: Color = Color.Unspecified
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrancy()
            blur(4.dp.toPx())
            lens(16.dp.toPx(), 32.dp.toPx())
        }
    },
    layerBlock = {
        // 按压时表面向外放大（增量 16dp 宽），松手由弹簧回弹。
        val maxScale = (size.width + 16.dp.toPx()) / size.width
        val scale = 1f + (maxScale - 1f) * pressProgress.coerceIn(0f, 1f)
        scaleX = scale
        scaleY = scale
    },
    onDrawSurface = {
        if (filmAlpha > 0f && filmColor != Color.Unspecified) {
            drawRect(filmColor.copy(alpha = filmAlpha))
        }
    }
)
