package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

object PluginSwitchStore {

    private const val PREF_NAME = "plugin_switch_pref"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, pluginId: String): Boolean =
        prefs(context).getBoolean("enabled_$pluginId", false)

    fun setEnabled(context: Context, pluginId: String, on: Boolean) {
        prefs(context).edit().putBoolean("enabled_$pluginId", on).apply()
        PluginSwitchState.notifyChanged()
    }

    /** 清理已卸载插件的开关状态 */
    fun clear(context: Context, pluginId: String) {
        prefs(context).edit().remove("enabled_$pluginId").apply()
        PluginSwitchState.notifyChanged()
    }
}

object PluginSwitchState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue

    fun notifyChanged() { _version.intValue++ }
}