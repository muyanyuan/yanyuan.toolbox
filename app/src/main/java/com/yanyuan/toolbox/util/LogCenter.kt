package com.yanyuan.toolbox.util

import android.app.AppOpsManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 排障导向的日志中心（不是"为了有日志而记"，每类都要能回答一个排障问题）：
 *
 *  - app.log        软件日志：应用自身发生了什么（启动/页面/设置变更/导出/失败）
 *  - permission.log 权限日志：谁、什么时候、【为了干什么】检查/使用了什么权限与结果；
 *                   以及【权限丢失的时间与原因】（快照差分检测）
 *  - plugin.log     插件日志：插件的安装/启动/开关/卸载/导入失败（与软件分开）
 *  - crash.log      崩溃日志：未捕获异常独立成 txt（时间、版本、线程、完整堆栈）
 *
 * 导出：选中的文件按【时长 / 插件】过滤后与报告一起【合成一个 zip】写入下载目录。
 * 行格式（crash.log 为整块）：`时间|类别|标签|内容`
 */
object LogCenter {

    const val CAT_APP = "APP"
    const val CAT_PERM = "PERM"
    const val CAT_PLUGIN = "PLUGIN"

    const val FILE_APP = "app.log"
    const val FILE_PERM = "permission.log"
    const val FILE_PLUGIN = "plugin.log"
    const val FILE_CRASH = "crash.log"

    private const val SNAPSHOT_PREFS = "perm_snapshot"

    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun dir(context: Context): File =
        File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }

    private fun lineFile(context: Context, name: String) = File(dir(context), name)

    private fun append(file: File, text: String) {
        try {
            synchronized(lock) { file.appendText(text, Charsets.UTF_8) }
        } catch (_: Exception) {
        }
    }

    /** 写一条行日志：软件→app.log，权限→permission.log，插件→plugin.log（分开存放）。 */
    fun log(context: Context, category: String, tag: String, message: String) {
        val target = when (category) {
            CAT_PLUGIN -> FILE_PLUGIN
            CAT_PERM -> FILE_PERM
            else -> FILE_APP
        }
        append(lineFile(context, target), "${timeFormat.format(Date())}|$category|$tag|$message\n")
    }

    // ==================== 崩溃日志（独立 txt） ====================

    /** 安装未捕获异常处理器：先落盘 crash.log，再交给系统默认处理（保持原崩溃流程）。 */
    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                appendCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 崩溃块：时间、版本、线程、异常与完整堆栈。 */
    fun appendCrash(context: Context, thread: Thread, throwable: Throwable) {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        val block = buildString {
            appendLine("==== ${timeFormat.format(Date())} 版本:$version 进程:${context.packageName} ====")
            appendLine("线程: ${thread.name}")
            appendLine(throwable.stackTraceToString())
            appendLine()
        }
        append(lineFile(context, FILE_CRASH), block)
    }

    // ==================== 权限快照：丢失/恢复检测（时间 + 原因） ====================

    /**
     * 与上次快照差分：
     *  - 已授权 → 未授权 = 【权限丢失】，记录丢失时间与最可能的原因（排障方向）
     *  - 未授权 → 已授权 = 【权限恢复】
     * @param occasion 检测时机（启动时 / 切换到设置页时……）
     */
    fun trackPermissionChanges(context: Context, occasion: String) {
        try {
            val prefs = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            val current = currentPermissions(context)
            val editor = prefs.edit()
            for ((key, granted) in current) {
                if (!prefs.contains(key)) {
                    // 首次见到该权限：只建基线，不误报"丢失"。
                    editor.putBoolean(key, granted)
                    continue
                }
                val previous = prefs.getBoolean(key, false)
                if (previous && !granted) {
                    log(
                        context, CAT_PERM, key,
                        "权限丢失（检测时机：$occasion）：${lossReason(key)}"
                    )
                } else if (!previous && granted) {
                    log(context, CAT_PERM, key, "权限恢复（检测时机：$occasion）")
                }
                editor.putBoolean(key, granted)
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    /** 当前受关注权限的快照：4 个特殊权限 + 清单声明的全部运行时权限。 */
    private fun currentPermissions(context: Context): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map["SPECIAL:ACCESSIBILITY"] =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains(context.packageName) == true
        map["SPECIAL:OVERLAY"] = Settings.canDrawOverlays(context)
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        map["SPECIAL:BATTERY_OPTIMIZATION"] =
            power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        map["SPECIAL:USAGE_STATS"] = hasUsageStats(context)
        try {
            val info = context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.forEach { permission ->
                map["RUNTIME:$permission"] =
                    ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun hasUsageStats(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    /** 权限丢失的原因（系统不回调"谁撤销的"，按权限类型给出最可能的排障方向）。 */
    private fun lossReason(key: String): String = when {
        key.endsWith("ACCESSIBILITY") ->
            "无障碍服务被关闭（用户手动关闭，或 MIUI/系统策略自动回收）"
        key.endsWith("OVERLAY") ->
            "悬浮窗权限被关闭（用户撤销，或系统省电策略回收）"
        key.endsWith("BATTERY_OPTIMIZATION") ->
            "电池优化白名单失效（系统省电策略回收，或用户关闭）"
        key.endsWith("USAGE_STATS") ->
            "使用情况访问权限被关闭（用户撤销，或系统策略回收）"
        else ->
            "运行时权限被撤销（用户在应用信息-权限中关闭，或系统/应用商店策略回收）"
    }

    // ==================== 读取与导出（合成 zip） ====================

    /** 行日志按【时长 + 标签】过滤（标签含插件 id 时即按插件过滤）。 */
    private fun filterLines(
        lines: List<String>,
        pluginIds: Set<String>?,
        sinceMillis: Long
    ): List<String> = lines.mapNotNull { line ->
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return@mapNotNull null
        val ts = runCatching { timeFormat.parse(parts[0])?.time }.getOrNull() ?: return@mapNotNull null
        if (sinceMillis > 0 && ts < sinceMillis) return@mapNotNull null
        if (pluginIds != null && parts[2] !in pluginIds) return@mapNotNull null
        line
    }

    /**
     * 导出：选中的日志按时长/插件过滤，连同报告一起打进【一个 zip】写入 下载/YanyuanToolbox/。
     * @param selectionDesc 报告里的筛选说明（内容 + 时长）
     * @return zip 文件名（失败 null）
     */
    fun exportZip(
        context: Context,
        includeApp: Boolean,
        includePerm: Boolean,
        includePlugin: Boolean,
        includeCrash: Boolean,
        pluginIds: Set<String>?,
        sinceMillis: Long,
        selectionDesc: String
    ): String? {
        return try {
            val selected = linkedMapOf<String, List<String>>()
            fun addLineFile(name: String, on: Boolean) {
                if (!on) return
                val file = lineFile(context, name)
                selected[name] = if (file.exists()) {
                    filterLines(file.readLines(Charsets.UTF_8), pluginIds, sinceMillis)
                } else {
                    emptyList()
                }
            }
            addLineFile(FILE_APP, includeApp)
            addLineFile(FILE_PERM, includePerm)
            addLineFile(FILE_PLUGIN, includePlugin)
            if (includeCrash) {
                val file = lineFile(context, FILE_CRASH)
                selected[FILE_CRASH] = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
            }

            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (_: Exception) {
                "?"
            }
            val report = buildString {
                appendLine("琰鸢工具箱 排障日志包")
                appendLine("导出时间：${timeFormat.format(Date())}")
                appendLine("应用版本：$version")
                appendLine("筛选：$selectionDesc")
                appendLine("说明：crash.log 不按时长过滤，整文件保留")
                selected.forEach { (name, lines) ->
                    appendLine("$name：${lines.size} 条")
                }
                appendLine("---- 文件在 zip 内 ----")
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipName = "toolbox_logs_$stamp.zip"
            val temp = File(context.cacheDir, zipName)
            ZipOutputStream(FileOutputStream(temp)).use { zip ->
                zip.putNextEntry(ZipEntry("报告.txt"))
                zip.write(report.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                selected.forEach { (name, lines) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zipName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/YanyuanToolbox")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                temp.delete()
                null
            } else {
                var ok = false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(temp).use { it.copyTo(out) }
                    ok = true
                }
                temp.delete()
                if (ok) zipName else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
