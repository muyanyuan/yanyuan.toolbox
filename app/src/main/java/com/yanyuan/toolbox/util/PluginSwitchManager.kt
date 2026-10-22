package com.yanyuan.toolbox.util

import android.content.Context
import com.yanyuan.toolbox.data.PluginSwitchStore

/**
 * 插件开关的统一入口。
 * 功能页的 Switch 和插件内部的 JS 调用都会经过这里，
 * 保证"一处改动，两处同步"。
 */
object PluginSwitchManager {

    fun setSwitch(context: Context, pluginId: String, pluginName: String, on: Boolean) {
        PluginSwitchStore.setEnabled(context, pluginId, on)
        // 插件日志：开关记录（供"导出日志 → 插件"按插件筛选）。
        LogCenter.log(context, LogCenter.CAT_PLUGIN, pluginId, "$pluginName 开关：" + if (on) "开" else "关")

        val notifId = NotificationHelper.notificationIdForPlugin(pluginId)
        if (on) {
            NotificationHelper.notifyOngoing(
                context,
                notifId,
                "「$pluginName」已启用",
                "点击返回琰鸢工具箱"
            )
        } else {
            NotificationHelper.cancel(context, notifId)
        }
    }
}