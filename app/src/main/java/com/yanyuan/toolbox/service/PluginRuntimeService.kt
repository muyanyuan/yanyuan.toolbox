package com.yanyuan.toolbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yanyuan.toolbox.MainActivity
import com.yanyuan.toolbox.R

/**
 * 插件运行时前台服务。
 * 用户启动任何插件后，通知栏会显示一条常驻通知："XXX 正在运行"。
 * 关闭插件（退出 WebPluginActivity）后，通知自动消失。
 */
class PluginRuntimeService : Service() {

    companion object {
        private const val EXTRA_PLUGIN_NAME = "plugin_name"
        private const val CHANNEL_ID = "plugin_runtime"
        private const val CHANNEL_NAME = "插件运行中"
        private const val CHANNEL_DESC = "插件正在运行时的常驻提醒"
        private const val NOTIFICATION_ID = 9901

        /** 启动前台服务，显示"XXX 正在运行"通知 */
        fun start(context: Context, pluginName: String) {
            val intent = Intent(context, PluginRuntimeService::class.java).apply {
                putExtra(EXTRA_PLUGIN_NAME, pluginName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) { }
        }

        /** 停止服务，通知消失 */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PluginRuntimeService::class.java))
            } catch (_: Exception) { }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pluginName = intent?.getStringExtra(EXTRA_PLUGIN_NAME) ?: "插件"

        ensureChannel()

        val notification = buildNotification(pluginName)

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // 通知权限被禁用时会抛异常，此时直接停止服务
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW   // 低优先级，不打扰
                ).apply {
                    description = CHANNEL_DESC
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(pluginName: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getActivity(this, 0, intent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$pluginName 正在运行")
            .setContentText("点击返回琰鸢工具箱")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)                 // 常驻，用户不能划掉
            .setShowWhen(false)
            .setContentIntent(pending)
            .build()
    }
}