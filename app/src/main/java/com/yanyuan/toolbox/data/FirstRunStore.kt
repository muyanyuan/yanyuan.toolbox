package com.yanyuan.toolbox.data

import android.content.Context

class FirstRunStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("plugin_first_run", Context.MODE_PRIVATE)

    fun isGuided(pluginId: String): Boolean =
        prefs.getBoolean("guided_$pluginId", false)

    fun markGuided(pluginId: String) {
        prefs.edit().putBoolean("guided_$pluginId", true).apply()
    }

    fun resetGuided(pluginId: String) {
        prefs.edit().remove("guided_$pluginId").apply()
    }
}