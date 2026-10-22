package com.yanyuan.toolbox.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yanyuan.toolbox.MainActivity
import com.yanyuan.toolbox.R
import com.yanyuan.toolbox.data.NotificationState

object NotificationHelper {

    private const val CHANNEL_ID = "yanyuan_default"
    private const val CHANNEL_NAME = "琰鸢工具箱"
    private const val CHANNEL_DESC = "插件安装、服务运行等状态提醒"

    /**
     * 创建通知渠道（Android 8.0+ 必须）
     * 幂等：重复调用不会报错，也不会重复创建
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 检查是否具备发送通知的条件：
     *  1. 用户开关为开
     *  2. Android 13+ 已授予 POST_NOTIFICATIONS 权限
     */
    fun canNotify(context: Context): Boolean {
        if (!NotificationState.enabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 发送一条普通通知
     * @param id 通知 id，同一个 id 会覆盖旧通知
     */
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val pending = buildPendingIntent(context, id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // 权限被取消，忽略
        }
    }

    /**
     * 发送一条常驻通知（用户不能划掉，必须由 App 主动取消）
     * 用于"插件已启用"这类状态提醒
     */
    fun notifyOngoing(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val pending = buildPendingIntent(context, id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // 权限被取消，忽略
        }
    }

    /** 取消指定 id 的通知 */
    fun cancel(context: Context, id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (_: Exception) {}
    }

    /** 取消所有通知 */
    fun cancelAll(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (_: Exception) {}
    }

    /** 根据插件 id 生成稳定的通知 id（10000 ~ 99999） */
    fun notificationIdForPlugin(pluginId: String): Int {
        val h = pluginId.hashCode()
        return 10000 + (h and 0x7FFFFFFF) % 90000
    }

    // =======================================================
    //  内部工具
    // =======================================================
    private fun buildPendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, id, intent, flags)
    }

    // =======================================================
    //  预定义的通知 id
    // =======================================================
    object Ids {
        const val PLUGIN_INSTALLED = 1001
        const val PLUGIN_UNINSTALLED = 1002
        const val SKIP_AD_ENABLED = 2001
        const val SKIP_AD_DISABLED = 2002
    }
}