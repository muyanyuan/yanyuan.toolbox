package com.yanyuan.toolbox.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import rikka.shizuku.Shizuku
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.yanyuan.toolbox.data.PermissionRequirement
import com.yanyuan.toolbox.data.SpecialType

object PermissionHelper {

    const val SHIZUKU_REQUEST_CODE = 0x5348

    /** Whether the Shizuku binder is currently connected and usable. */
    fun isShizukuAvailable(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.pingBinder()
    }.getOrDefault(false)

    /** Whether this app has the Shizuku permission, without requesting it. */
    fun isShizukuGranted(): Boolean = runCatching {
        isShizukuAvailable() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Request Shizuku permission only after an explicit user action. */
    fun requestShizukuPermission(context: Context): Boolean {
        if (!isShizukuAvailable()) {
            LogCenter.log(
                context,
                LogCenter.CAT_PERM,
                "app",
                "请求 Shizuku 权限失败：服务未连接"
            )
            return false
        }
        return runCatching {
            if (!isShizukuGranted()) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
            true
        }.onFailure {
            LogCenter.log(
                context,
                LogCenter.CAT_PERM,
                "app",
                "请求 Shizuku 权限失败：${it.javaClass.simpleName}"
            )
        }.getOrDefault(false)
    }

    /**
     * 检查某个权限需求当前是否已授权。
     * @param usedBy 谁在用它（"app" 或插件 id）
     * @param purpose 用它干什么 —— 写进 permission.log，排障时能看到权限的真实用途
     */
    fun check(
        context: Context,
        requirement: PermissionRequirement,
        usedBy: String = "app",
        purpose: String? = null
    ): Boolean {
        val granted = when (requirement) {
            is PermissionRequirement.Runtime -> isRuntimeGranted(context, requirement.permission)
            is PermissionRequirement.Special -> when (requirement.type) {
                SpecialType.ACCESSIBILITY -> isAccessibilityEnabled(context)
                SpecialType.OVERLAY -> isOverlayGranted(context)
                SpecialType.BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored(context)
                SpecialType.USAGE_STATS -> hasUsageStatsPermission(context)
            }
        }
        // 权限使用日志：记录每次权限检查与结果（供"导出日志 → 权限使用"筛选）。
        val label = when (requirement) {
            is PermissionRequirement.Runtime ->
                "Runtime:" + requirement.permission.substringAfterLast('.')
            is PermissionRequirement.Special -> "Special:${requirement.type}"
        }
        val what = if (purpose.isNullOrBlank()) "检查权限" else "用途：$purpose"
        LogCenter.log(
            context, LogCenter.CAT_PERM, usedBy,
            "$what，$label → " + (if (granted) "已授权" else "未授权")
        )
        return granted
    }

    /** 打开特殊权限对应的系统设置页 */
    @SuppressLint("BatteryLife")
    fun openSpecialSettings(
        context: Context,
        type: SpecialType,
        usedBy: String = "app",
        purpose: String? = null
    ) {
        // 权限使用日志：谁、为了什么，跳转到哪个系统授权页。
        val what = if (purpose.isNullOrBlank()) "打开系统授权页" else "用途：$purpose，打开系统授权页"
        LogCenter.log(context, LogCenter.CAT_PERM, usedBy, "$what：$type")
        val intent = when (type) {
            SpecialType.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            SpecialType.OVERLAY ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                } else return

            SpecialType.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }

            SpecialType.USAGE_STATS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // 兜底：跳到应用详情页
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    // ---------- 具体检查实现 ----------

    private fun isRuntimeGranted(context: Context, permission: String): Boolean {
        // Android 13 以下 POST_NOTIFICATIONS 默认有
        if (permission == Manifest.permission.POST_NOTIFICATIONS &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) return true

        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun isOverlayGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context.packageName,
            "com.yanyuan.toolbox.service.PluginAccessibilityService"
        )
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
