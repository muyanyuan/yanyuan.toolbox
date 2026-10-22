package com.yanyuan.toolbox.ui.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.yanyuan.toolbox.data.AiModelPlatform
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.PermissionRequirement
import com.yanyuan.toolbox.data.PluginSwitchStore
import com.yanyuan.toolbox.data.PluginCapability
import com.yanyuan.toolbox.data.PluginRulesStore
import com.yanyuan.toolbox.data.SpecialType
import com.yanyuan.toolbox.data.ThemeMode
import com.yanyuan.toolbox.data.ThemeModeStore
import com.yanyuan.toolbox.util.PermissionHelper
import com.yanyuan.toolbox.util.PluginSwitchManager
import com.yanyuan.toolbox.util.SharedFileHandoffStore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PluginBridge(
    private val activity: WebPluginActivity,
    private val pluginId: String,
    private val pluginName: String,
    private val capabilities: Set<String>,
    private val shareToken: String? = null
) {
    private val ctx: Context = activity.applicationContext

    // AI 查询专用客户端：只用于读取宿主已配置模型的平台信息、余额/账户状态，
    // 不把 API Key 暴露给 WebView，也不发送聊天请求。
    private val aiClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ============ 系统返回拦截 ============
    /** 插件注册的返回处理器函数名（形如 __pluginBackHandler）。为空表示插件不拦返回。 */
    @Volatile
    private var backHandlerName: String? = null

    /**
     * 插件注册返回处理器。传空字符串表示取消注册。
     *
     * 宿主在收到系统返回手势时会先调用这个处理器：
     *   返回 true  → 插件已处理（例如关闭自己弹出的浮层），宿主不退出
     *   返回 false → 插件不处理，宿主按原逻辑 goBack 或退出
     */
    @JavascriptInterface
    fun setBackHandler(name: String?) {
        backHandlerName = name?.takeIf { it.isNotBlank() }
    }

    /** 宿主调用：触发插件注册的返回处理器 */
    fun invokeBackHandler(wv: android.webkit.WebView) {
        val name = backHandlerName
        if (name == null) {
            // 插件没注册处理器 → 直接告知宿主「不处理」
            WebPluginActivity.deliverBackResult(false)
            return
        }
        // Call the registered handler directly, then return its result through the bridge.
        val encodedName = org.json.JSONObject.quote(name)
        wv.evaluateJavascript(
            "(function(){try{var h=window[$encodedName];" +
                "window.Yanyuan.onBackResult(!!(typeof h==='function'&&h()));" +
                "}catch(e){window.Yanyuan.onBackResult(false);}})();",
            null
        )
    }

    /** 供插件 JS 调用：回传「本次返回是否已被插件处理」 */
    @JavascriptInterface
    fun onBackResult(handled: Boolean) {
        WebPluginActivity.deliverBackResult(handled)
    }

    companion object {
        /** 当前存活的桥接实例，供 Compose 侧调用（同一时间只有一个插件页） */
        @Volatile
        var current: PluginBridge? = null
            private set

        fun attach(bridge: PluginBridge) { current = bridge }

        fun detach() { current = null }
    }

    init {
        attach(this)
    }

    // ============ 本次分享文件（只读） ============
    @JavascriptInterface
    fun getSharedFileCount(): Int = SharedFileHandoffStore.get(shareToken)?.files?.size ?: 0

    @JavascriptInterface
    fun getSharedFileInfo(index: Int): String {
        val file = SharedFileHandoffStore.get(shareToken)?.files?.getOrNull(index)
            ?: return ""
        return JSONObject().apply {
            put("name", file.name)
            put("mimeType", file.mimeType)
            put("size", file.size)
        }.toString()
    }

    @JavascriptInterface
    fun readSharedFileText(index: Int, maxBytes: Int): String =
        SharedFileHandoffStore.readText(shareToken, index, maxBytes).orEmpty()

    @JavascriptInterface
    fun readSharedFileBase64(index: Int, maxBytes: Int): String =
        SharedFileHandoffStore.readBase64(shareToken, index, maxBytes).orEmpty()

    // ============ 通用 ============
    @JavascriptInterface fun getPluginId(): String = pluginId

    @JavascriptInterface fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface fun vibrate(ms: Long) {
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    @JavascriptInterface fun setKeepScreenOn(on: Boolean) {
        activity.runOnUiThread { activity.setKeepScreenOnFlag(on) }
    }

    @JavascriptInterface fun setOrientation(mode: String) {
        activity.runOnUiThread { activity.setOrientationMode(mode) }
    }

    /**
     * 设备与屏幕信息，供插件自己做响应式适配。
     *
     * 返回 JSON 字符串：
     *   isTablet       最小宽度 ≥ 600dp（与 App 内其它地方一致）
     *   smallestWidthDp 最小宽度（dp）
     *   screenWidthDp / screenHeightDp 当前屏幕尺寸（dp，随旋转变化）
     *   screenWidthPx / screenHeightPx 当前屏幕尺寸（px）
     *   orientation    "portrait" / "landscape"
     *   densityDpi     屏幕密度
     */
    @JavascriptInterface
    fun getDeviceInfo(): String {
        val res = activity.resources
        val cfg = res.configuration
        val dm = res.displayMetrics

        val widthDp = if (cfg.screenWidthDp > 0) cfg.screenWidthDp
        else (dm.widthPixels / dm.density).toInt()
        val heightDp = if (cfg.screenHeightDp > 0) cfg.screenHeightDp
        else (dm.heightPixels / dm.density).toInt()
        val smallestDp = if (cfg.smallestScreenWidthDp > 0) cfg.smallestScreenWidthDp
        else minOf(widthDp, heightDp)

        val widthPx: Int
        val heightPx: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = activity.windowManager.currentWindowMetrics.bounds
            widthPx = b.width()
            heightPx = b.height()
        } else {
            @Suppress("DEPRECATION")
            val d = activity.windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            widthPx = d.width
            @Suppress("DEPRECATION")
            heightPx = d.height
        }

        return JSONObject().apply {
            put("isTablet", smallestDp >= 600)
            put("smallestWidthDp", smallestDp)
            put("screenWidthDp", widthDp)
            put("screenHeightDp", heightDp)
            put("screenWidthPx", widthPx)
            put("screenHeightPx", heightPx)
            put("orientation", if (widthDp > heightDp) "landscape" else "portrait")
            put("densityDpi", dm.densityDpi)
        }.toString()
    }

    /**
     * 按设备类型设置方向：手机锁竖屏，平板跟随重力感应。
     * 插件在启动时调一次即可，配合 getDeviceInfo() 使用。
     */
    @JavascriptInterface
    fun applyDeviceOrientation() {
        val smallestDp = activity.resources.configuration.smallestScreenWidthDp
        val isTablet = smallestDp >= 600
        val mode = if (isTablet) "sensor" else "portrait"
        activity.runOnUiThread { activity.setOrientationMode(mode) }
    }

    @JavascriptInterface fun close() {
        activity.runOnUiThread { activity.finish() }
    }

    @JavascriptInterface fun openAppSettings() {
        activity.runOnUiThread {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // ============ 独立存储 ============
    private val prefs by lazy {
        ctx.getSharedPreferences("plugin_storage_$pluginId", Context.MODE_PRIVATE)
    }

    @JavascriptInterface fun storageGet(key: String): String? = prefs.getString(key, null)
    @JavascriptInterface fun storageSet(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    @JavascriptInterface fun storageRemove(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ============ 插件开关 ============
    @JavascriptInterface fun getSwitch(): Boolean =
        PluginSwitchStore.isEnabled(ctx, pluginId)

    @JavascriptInterface fun setSwitch(on: Boolean) {
        PluginSwitchManager.setSwitch(ctx, pluginId, pluginName, on)
    }

    // ============ 应用列表（带 isSystemApp 标记）============
    @JavascriptInterface
    fun getInstalledApps(): String {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        val arr = JSONArray()
        val seen = mutableSetOf<String>()
        for (info in apps) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == ctx.packageName) continue
            if (!seen.add(pkg)) continue

            val label = try {
                info.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkg
            }

            // 判断是否系统应用
            val isSystem = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) {
                false
            }

            val obj = JSONObject()
            obj.put("packageName", pkg)
            obj.put("appName", label)
            obj.put("isSystemApp", isSystem)
            arr.put(obj)
        }
        return arr.toString()
    }


    // ============ AI 模型与账户信息（正式插件接口） ============
    /**
     * 读取宿主「导入大模型」中的全部平台和模型。
     * API Key 永远不会返回给 WebView，仅返回是否已配置。
     */
    @JavascriptInterface
    fun getAiCatalog(): String {
        if (!AuthStore.get(ctx).isPluginAuthorized(pluginId)) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "此插件尚未获得 AI 接口授权")
            }.toString()
        }
        return try {
            val settings = AiModelStore.get(ctx)
            val platforms = JSONArray()
            val configMap = settings.configs.associateBy { it.platformId }

            for (platform in AiModelPlatform.entries) {
                val cfg = configMap[platform.id]
                val models = JSONArray()
                settings.modelsOf(platform.id).forEach { model ->
                    if (model.cleanModelId.isNotBlank()) {
                        models.put(JSONObject().apply {
                            put("id", model.cleanModelId)
                            put("name", model.displayName)
                        })
                    }
                }
                // 只把已有配置或已有模型的平台展示给插件，避免塞满未使用的平台。
                val shouldInclude = cfg != null || models.length() > 0
                if (!shouldInclude) continue

                platforms.put(JSONObject().apply {
                    put("id", platform.id)
                    put("name", platform.displayName)
                    put("baseUrl", cfg?.effectiveBaseUrl ?: platform.baseUrl)
                    put("enabled", cfg?.enabled ?: false)
                    put("keyConfigured", !cfg?.apiKey.isNullOrBlank())
                    put("keyOptional", platform.keyOptional)
                    put("models", models)
                })
            }

            JSONObject().apply {
                put("ok", true)
                put("platforms", platforms)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", "读取模型配置失败：${e.message ?: "未知错误"}")
            }.toString()
        }
    }

    /**
     * 查询所选平台的账户信息。
     * 已适配：DeepSeek 余额、OpenRouter 余额/今日 API key 用量；
     * 其他平台采用无计费请求的 /models 探活，因此全部已导入平台都可显示 API 状态。
     */
    @JavascriptInterface
    fun getAiSnapshot(platformId: String): String {
        if (!AuthStore.get(ctx).isPluginAuthorized(pluginId)) {
            return aiSnapshotError("此插件尚未获得 AI 接口授权", "未授权")
        }
        val settings = AiModelStore.get(ctx)
        val cfg = settings.configs.firstOrNull { it.platformId == platformId }
            ?: return aiSnapshotError("尚未配置该平台")
        val platform = AiModelPlatform.fromId(platformId)

        if (!cfg.enabled) return aiSnapshotError("该平台未启用", "未启用")
        if (cfg.apiKey.isBlank() && !platform.keyOptional) {
            return aiSnapshotError("尚未配置 API 密钥", "缺少密钥")
        }

        return try {
            when (platform) {
                AiModelPlatform.DEEPSEEK -> snapshotDeepSeek(cfg)
                AiModelPlatform.OPENROUTER -> snapshotOpenRouter(cfg)
                else -> snapshotGeneric(cfg)
            }
        } catch (e: Exception) {
            aiSnapshotError(e.message ?: "请求失败", "检测失败")
        }
    }

    private fun snapshotDeepSeek(cfg: com.yanyuan.toolbox.data.AiPlatformConfig): String {
        val base = cfg.effectiveBaseUrl.trimEnd('/').removeSuffix("/v1")
        val url = "$base/user/balance"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (cfg.apiKey.isNotBlank()) header("Authorization", "Bearer ${cfg.apiKey}") }
            .get()
            .build()
        return aiClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return aiSnapshotError("HTTP ${resp.code}", "接口错误")
            val root = JSONObject(body)
            val infos = root.optJSONArray("balance_infos") ?: JSONArray()
            var chosen: JSONObject? = null
            for (i in 0 until infos.length()) {
                val item = infos.optJSONObject(i) ?: continue
                if (item.optString("currency") == "CNY" && item.optDouble("total_balance", 0.0) != 0.0) { chosen = item; break }
                if (chosen == null) chosen = item
            }
            val obj = JSONObject().apply {
                put("ok", true)
                put("statusLabel", if (root.optBoolean("is_available", true)) "正常" else "余额不足")
                put("detail", if (root.optBoolean("is_available", true)) "DeepSeek 账户余额接口可用" else "账户余额不足以继续 API 调用")
                put("balanceSource", "DeepSeek 官方账户余额")
            }
            if (chosen != null) {
                putNumber(obj, "balance", chosen!!.optString("total_balance", ""))
                obj.put("currency", chosen!!.optString("currency", "CNY"))
            } else {
                obj.put("balanceMessage", "接口未返回余额数据")
            }
            obj.toString()
        }
    }

    private fun snapshotOpenRouter(cfg: com.yanyuan.toolbox.data.AiPlatformConfig): String {
        val base = cfg.effectiveBaseUrl.trimEnd('/')
        val result = JSONObject().apply {
            put("ok", false)
            put("statusLabel", "接口可用")
        }

        var creditsOk = false
        runCatching {
            val req = Request.Builder()
                .url("$base/credits")
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .get().build()
            aiClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = JSONObject(resp.body?.string().orEmpty())
                    val data = body.optJSONObject("data")
                    if (data != null) {
                        val total = data.optDouble("total_credits", Double.NaN)
                        val usage = data.optDouble("total_usage", Double.NaN)
                        if (!total.isNaN()) {
                            val balance = if (!usage.isNaN()) total - usage else total
                            result.put("balance", balance)
                            result.put("currency", "USD")
                            result.put("balanceSource", "OpenRouter Credits")
                            creditsOk = true
                        }
                    }
                }
            }
        }

        var dailyOk = false
        runCatching {
            val req = Request.Builder()
                .url("$base/key")
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .get().build()
            aiClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = JSONObject(resp.body?.string().orEmpty())
                    val data = body.optJSONObject("data")
                    if (data != null && data.has("usage_daily")) {
                        result.put("dailyUsage", data.optDouble("usage_daily", 0.0))
                        result.put("dailyCurrency", "USD")
                        result.put("dailyUsageSource", "OpenRouter 当前 API Key 今日用量")
                        dailyOk = true
                    }
                }
            }
        }

        if (!creditsOk && !dailyOk) {
            return snapshotGeneric(cfg)
        }
        result.put("ok", true)
        result.put("detail", "OpenRouter 账户接口可用")
        result.put("apiOnly", false)
        return result.toString()
    }

    private fun snapshotGeneric(cfg: com.yanyuan.toolbox.data.AiPlatformConfig): String {
        val base = cfg.effectiveBaseUrl.trimEnd('/')
        val url = "$base/models"
        val request = Request.Builder()
            .url(url)
            .apply { if (cfg.apiKey.isNotBlank()) header("Authorization", "Bearer ${cfg.apiKey}") }
            .get().build()
        return aiClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return aiSnapshotError("HTTP ${resp.code}", "接口错误")
            var count: Int? = null
            runCatching { count = JSONObject(body).optJSONArray("data")?.length() }
            return JSONObject().apply {
                put("ok", true)
                put("statusLabel", "正常")
                put("detail", count?.let { "模型接口可用 · 服务端返回 $it 个模型" } ?: "模型接口可用")
                put("balanceMessage", "该平台未提供统一余额接口")
            }.toString()
        }
    }

    private fun putNumber(obj: JSONObject, key: String, value: String) {
        value.toDoubleOrNull()?.let { obj.put(key, it) }
    }

    private fun aiSnapshotError(message: String, label: String = "不可用"): String =
        JSONObject().apply {
            put("ok", false)
            put("statusLabel", label)
            put("error", message)
        }.toString()

    // ============ 规则接口 ============
    private val pluginRules by lazy { PluginRulesStore(ctx, pluginId) }

    private fun requireCap(cap: String) {
        if (cap !in capabilities) throw SecurityException("插件未声明能力：$cap")
    }

    @JavascriptInterface
    fun getPluginRules(): String {
        requireCap(PluginCapability.ACCESSIBILITY_AUTOMATION)
        return pluginRules.rulesJson
    }

    @JavascriptInterface
    fun setPluginRules(json: String) {
        requireCap(PluginCapability.ACCESSIBILITY_AUTOMATION)
        try {
            JSONObject(json)
            pluginRules.setRules(json)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun isAccessibilityServiceEnabled(): Boolean {
        requireCap(PluginCapability.ACCESSIBILITY_AUTOMATION)
        return PermissionHelper.check(
            ctx,
            PermissionRequirement.Special(SpecialType.ACCESSIBILITY, "", ""),
            usedBy = pluginId,
            purpose = "插件查询无障碍服务状态"
        )
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        requireCap(PluginCapability.ACCESSIBILITY_AUTOMATION)
        activity.runOnUiThread {
            PermissionHelper.openSpecialSettings(
                ctx, SpecialType.ACCESSIBILITY,
                usedBy = pluginId, purpose = "插件打开无障碍设置"
            )
        }
    }

    @JavascriptInterface
    fun openBatteryOptimizationSettings() {
        requireCap(PluginCapability.ACCESSIBILITY_AUTOMATION)
        activity.runOnUiThread {
            PermissionHelper.openSpecialSettings(
                ctx, SpecialType.BATTERY_OPTIMIZATION,
                usedBy = pluginId, purpose = "插件打开电池优化白名单"
            )
        }
    }

    /**
     * 软件端提供的深色模式状态 = 应用主题的深色模式（深色 / 浅色 / 跟随系统），
     * 插件读取后可自行切换配色：`App.isDarkMode()`
     */
    @JavascriptInterface
    fun isDarkMode(): Boolean {
        val mode = ThemeModeStore.getCurrent(ctx)
        val systemDark = (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        return when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            else -> systemDark
        }
    }
}
