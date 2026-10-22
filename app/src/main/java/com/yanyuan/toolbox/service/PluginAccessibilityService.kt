package com.yanyuan.toolbox.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yanyuan.toolbox.data.FeatureRepository
import com.yanyuan.toolbox.data.PluginCapability
import com.yanyuan.toolbox.data.PluginSwitchStore
import com.yanyuan.toolbox.data.PluginRulesStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class PluginAccessibilityService : AccessibilityService() {

    companion object {
        private const val COOLDOWN_MS = 1500L
        private const val MAX_SCAN_NODES = 500
        private val snapshotSequence = AtomicLong()
        private val SYSTEM_PACKAGES = setOf(
            "android", "com.android.settings", "com.android.systemui",
            "com.android.providers.settings", "com.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.android.keychain", "com.android.providers.downloads",
            "com.android.providers.downloads.ui", "com.android.documentsui",
            "com.miui.securitycenter", "com.miui.packageinstaller", "com.miui.systemui",
            "com.miui.home", "com.miui.permcenter", "com.miui.guardprovider",
            "com.huawei.systemmanager", "com.huawei.android.launcher",
            "com.huawei.packageinstaller", "com.huawei.systemserver",
            "com.coloros.safecenter", "com.coloros.oppoguardelf", "com.oppo.usercenter",
            "com.coloros.phonemanager", "com.oplus.safecenter", "com.oplus.securitykeyboard",
            "com.vivo.permissionmanager", "com.vivo.abe", "com.vivo.pem", "com.iqoo.secure",
            "com.samsung.android.lool", "com.samsung.android.packageinstaller",
            "com.samsung.android.sm_cn", "com.oneplus.security", "com.oneplus.opbackup",
            "com.google.android.settings.intelligence"
        )
    }

    private var engineView: WebView? = null
    @Volatile
    private var enginePluginId: String? = null
    @Volatile
    private var engineReady = false
    private var lastEngineStamp: String? = null
    @Volatile
    private var lastClickTime = 0L
    private val nodeLock = Any()
    private val actionNodes = mutableMapOf<String, MutableMap<Int, AccessibilityNodeInfo>>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        reloadEngineIfNeeded()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || isSystemPackage(pkg)) return

        val root = rootInActiveWindow ?: return
        try {
            val rootPkg = root.packageName?.toString() ?: return
            if (rootPkg == packageName || isSystemPackage(rootPkg)) return

            val active = activePlugin() ?: return
            if (SystemClock.uptimeMillis() - lastClickTime < COOLDOWN_MS) return

            reloadEngineIfNeeded(active)
            if (!engineReady) return

            val token = snapshotSequence.incrementAndGet().toString()
            val snapshot = buildSnapshot(root, pkg, token)
            val json = JSONObject.quote(snapshot.toString())
            val view = engineView
            if (view == null) releaseActionNodes(token)
            else view.evaluateJavascript("window.runAutomationEvent && window.runAutomationEvent($json)") {
                releaseActionNodes(token)
            }
        } finally {
            runCatching { root.recycle() }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        releaseActionNodes()
        engineView?.apply {
            removeJavascriptInterface("PluginAutomationHost")
            stopLoading()
            destroy()
        }
        engineView = null
        super.onDestroy()
    }

    private fun activePlugin(): com.yanyuan.toolbox.data.InstalledPlugin? {
        val plugins = try {
            FeatureRepository(this).listInstalled().filter {
                PluginCapability.ACCESSIBILITY_AUTOMATION in it.manifest.capabilities
            }
        } catch (_: Exception) { emptyList() }
        val plugin = plugins.firstOrNull { PluginSwitchStore.isEnabled(this, it.manifest.id) }
            ?: return null
        return plugin
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun reloadEngineIfNeeded(selectedPlugin: com.yanyuan.toolbox.data.InstalledPlugin? = null) {
        val active = selectedPlugin ?: activePlugin() ?: return
        if (!PluginSwitchStore.isEnabled(this, active.manifest.id)) return

        val engineEntry = active.manifest.automationEngineEntry ?: return
        if (engineEntry.contains("..") || engineEntry.startsWith("/")) return
        val engineFile = File(filesDir, "plugins/${active.manifest.id}/$engineEntry")
        if (!engineFile.isFile) {
            engineReady = false
            return
        }
        val scriptFile = File(engineFile.parentFile, "rule-engine.js")
        val stamp = listOf(engineFile, scriptFile).joinToString(":") {
            "${it.absolutePath}:${it.lastModified()}:${it.length()}"
        }
        if (enginePluginId == active.manifest.id && lastEngineStamp == stamp && engineView != null) return

        engineReady = false
        enginePluginId = active.manifest.id
        lastEngineStamp = stamp
        engineView?.destroy()
        engineView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            addJavascriptInterface(PluginAutomationHost(), "PluginAutomationHost")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (view === engineView && enginePluginId == active.manifest.id) {
                        engineReady = true
                    }
                }
            }
            loadUrl(engineFile.toURI().toString())
        }
    }

    private fun buildSnapshot(root: AccessibilityNodeInfo, packageName: String, token: String): JSONObject {
        val nodes = JSONArray()
        var nextId = 1
        val tokenNodes = mutableMapOf<Int, AccessibilityNodeInfo>()

        fun visit(node: AccessibilityNodeInfo, parentId: Int, depth: Int) {
            if (nodes.length() >= MAX_SCAN_NODES || depth > 80) return
            val id = nextId++
            tokenNodes[id] = AccessibilityNodeInfo.obtain(node)
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            nodes.put(JSONObject().apply {
                put("id", id)
                put("parentId", parentId)
                put("text", node.text?.toString().orEmpty())
                put("description", node.contentDescription?.toString().orEmpty())
                put("hint", node.hintText?.toString().orEmpty())
                put("visible", node.isVisibleToUser)
                put("enabled", node.isEnabled)
                put("clickable", node.isClickable)
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
            })
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { visit(child, id, depth + 1) } finally { runCatching { child.recycle() } }
            }
        }

        visit(root, 0, 0)
        synchronized(nodeLock) { actionNodes[token] = tokenNodes }
        return JSONObject().apply {
            put("token", token)
            put("packageName", packageName)
            put("screenWidth", resources.displayMetrics.widthPixels)
            put("screenHeight", resources.displayMetrics.heightPixels)
            put("nodes", nodes)
        }
    }

    private fun releaseActionNodes() {
        synchronized(nodeLock) {
            actionNodes.values.flatMap { it.values }.forEach { runCatching { it.recycle() } }
            actionNodes.clear()
        }
    }

    private fun releaseActionNodes(token: String) {
        synchronized(nodeLock) {
            actionNodes.remove(token)?.values?.forEach { runCatching { it.recycle() } }
        }
    }

    private inner class PluginAutomationHost {
        @JavascriptInterface
        fun getPluginRules(): String = enginePluginId?.let {
            PluginRulesStore(this@PluginAccessibilityService, it).rulesJson
        } ?: "{}"

        @JavascriptInterface
        fun clickNode(token: String, id: Int): Boolean {
            val now = SystemClock.uptimeMillis()
            if (now - lastClickTime < COOLDOWN_MS) return false
            val node = synchronized(nodeLock) { actionNodes[token]?.get(id)?.let { AccessibilityNodeInfo.obtain(it) } }
                ?: return false
            try {
                var current: AccessibilityNodeInfo? = node
                var depth = 0
                var clicked = false
                while (current != null && depth < 5) {
                    val currentNode = current
                    if (currentNode.isClickable && currentNode.isEnabled &&
                        currentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        lastClickTime = now
                        clicked = true
                        break
                    }
                    val parent = currentNode.parent
                    if (currentNode !== node) runCatching { currentNode.recycle() }
                    current = parent
                    depth++
                }
                current?.let { if (it !== node) runCatching { it.recycle() } }
                return clicked
            } finally {
                runCatching { node.recycle() }
            }
        }
    }

    private fun isSystemPackage(pkg: String): Boolean =
        pkg in SYSTEM_PACKAGES || listOf(
            "com.android.", "com.miui.", "com.huawei.", "com.coloros.", "com.oplus.",
            "com.vivo.", "com.iqoo.", "com.samsung.", "com.oneplus.", "com.google.android."
        ).any(pkg::startsWith)
}
