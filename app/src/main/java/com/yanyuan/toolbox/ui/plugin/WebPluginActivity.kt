package com.yanyuan.toolbox.ui.plugin

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.yanyuan.toolbox.ui.theme.YanyuanTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yanyuan.toolbox.data.FeatureRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

class WebPluginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: run { finish(); return }
        val plugin = FeatureRepository(this).listInstalled()
            .find { it.manifest.id == pluginId } ?: run { finish(); return }
        val shareToken = intent.getStringExtra(EXTRA_SHARE_TOKEN)
        val manifest = plugin.manifest

        setOrientationMode(manifest.orientation)
        if (manifest.fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        if (manifest.keepScreenOn) setKeepScreenOnFlag(true)

        val pluginDir = File(filesDir, "plugins/$pluginId")
        val entryFile = File(pluginDir, manifest.entry.ifBlank { "index.html" })

        setContent {
            // 跟随应用主题（YanyuanTheme）：应用处于深色模式时插件容器即深色背景 —— 软件端为插件提供深色模式。
            YanyuanTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!entryFile.exists()) {
                        MissingEntry(manifest.entry, pluginDir.absolutePath)
                    } else {
                        PluginContent(
                            activity = this,
                            entryFile = entryFile,
                            pluginId = pluginId,
                            pluginName = manifest.name,
                            capabilities = manifest.capabilities,
                            shareToken = shareToken
                        )
                    }
                }
            }
        }
    }

    fun setKeepScreenOnFlag(on: Boolean) {
        runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun setOrientationMode(mode: String) {
        // 平板断点：最小宽度 >= 600dp（与 App 内其它地方一致）
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600

        requestedOrientation = when (mode) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "reverseLandscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            "sensorLandscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "sensorPortrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

            /**
             * ★ 按设备类型自适应：
             *   手机 → 锁定竖屏（不跟随旋转）
             *   平板 → 跟随重力感应
             *
             * 只在 onCreate 里设置一次，插件运行期间不再变更，
             * 避免中途改 requestedOrientation 导致窗口重建 → WebView 渲染表面
             * 被销毁重建 → 黑屏闪烁。
             */
            "autoTablet" -> if (isTablet) ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    companion object {
        const val EXTRA_PLUGIN_ID = "plugin_id"
        const val EXTRA_SHARE_TOKEN = "share_token"

        /**
         * 等待插件返回处理器结果的中转。
         * JS 侧算完后调 window.Yanyuan.onBackResult(bool)，经此回调唤醒协程。
         */
        @Volatile
        internal var backResultCallback: ((Boolean) -> Unit)? = null

        internal fun deliverBackResult(handled: Boolean) {
            val cb = backResultCallback
            backResultCallback = null
            cb?.invoke(handled)
        }
    }
}

/**
 * 问插件「这次返回你处理吗」。
 *
 * 插件通过 Y.setBackHandler('函数名') 注册；没注册则视为不处理。
 * 超时 600ms 兜底为不处理，避免插件异常时返回手势彻底失灵。
 */
private suspend fun awaitPluginBack(wv: WebView): Boolean =
    withTimeoutOrNull(600L) {
        suspendCancellableCoroutine { cont ->
            WebPluginActivity.backResultCallback = { handled ->
                if (cont.isActive) cont.resume(handled)
            }
            val bridge = PluginBridge.current
            if (bridge != null) {
                bridge.invokeBackHandler(wv)
            } else {
                WebPluginActivity.backResultCallback = null
                if (cont.isActive) cont.resume(false)
            }
        }
    } ?: false

@SuppressLint("SetJavaScriptEnabled")
@Composable
    private fun PluginContent(
    activity: WebPluginActivity,
    entryFile: File,
    pluginId: String,
    pluginName: String,
    capabilities: Set<String>,
    shareToken: String?
) {

    var webView by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()
    var lastBackAt by remember { mutableLongStateOf(0L) }
    val pendingFileChooser = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileChooser.value ?: return@rememberLauncherForActivityResult
        pendingFileChooser.value = null
        val data = result.data
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback.onReceiveValue(uris)
    }

    // 桥接实例：注册给 WebView 的 JS，同时供返回拦截查询插件注册的处理器
    val bridge = remember {
            PluginBridge(activity, pluginId, pluginName, capabilities, shareToken)

    }

    /**
     * 系统返回手势。
     *
     * 顺序：先问插件「这次返回你处理吗」——
     *   插件返回 true（关掉了自己的浮层）→ 宿主什么都不做
     *   插件返回 false / 没注册处理器 → 再走 WebView 历史，最后才退出
     *
     * 以前是直接 canGoBack() ?: finish()，导致插件内部弹的浮层（比如关于页）
     * 完全拦不住返回手势，一返回就退出到功能页。
     */
    BackHandler {
        val wv = webView
        if (wv == null) { activity.finish(); return@BackHandler }

        // 400ms 节流：避免返回手势重复触发导致 evaluateJavascript 结果错位
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 400) return@BackHandler
        lastBackAt = now

        scope.launch {
            val handled = awaitPluginBack(wv)
            if (!handled) {
                if (wv.canGoBack()) wv.goBack() else activity.finish()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // ★ 软件端为插件提供深色模式：夜间模式下允许 WebView 算法暗化网页内容。
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        setAlgorithmicDarkeningAllowed(true)
                    } else {
                        @Suppress("DEPRECATION")
                        setForceDark(WebSettings.FORCE_DARK_AUTO)
                    }
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {}
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (filePathCallback == null || fileChooserParams == null) return false
                        pendingFileChooser.value?.onReceiveValue(null)
                        pendingFileChooser.value = filePathCallback
                        val mimeTypes = fileChooserParams.acceptTypes
                            .flatMap { it.split(',') }
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
                            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
                            if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                        }
                        return try {
                            filePicker.launch(Intent.createChooser(intent, "选择文件"))
                            true
                        } catch (_: Exception) {
                            pendingFileChooser.value = null
                            filePathCallback.onReceiveValue(null)
                            false
                        }
                    }
                }
                addJavascriptInterface(
                    bridge,
                    "Yanyuan"
                )
                loadUrl("file://${entryFile.absolutePath}")
                webView = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // 离开插件页时注销桥接，避免下次进入时残留旧实例
    DisposableEffect(Unit) {
        onDispose {
            pendingFileChooser.value?.onReceiveValue(null)
            pendingFileChooser.value = null
            PluginBridge.detach()
            com.yanyuan.toolbox.util.SharedFileHandoffStore.release(shareToken)
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun MissingEntry(entry: String, pluginDir: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "插件缺少入口文件",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "找不到 $entry\n目录：$pluginDir",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
