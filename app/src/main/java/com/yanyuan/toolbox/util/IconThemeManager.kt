package com.yanyuan.toolbox.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconThemeManager {

    enum class IconTheme(
        val id: String,
        val displayName: String,
        val aliasClass: String
    ) {
        DEFAULT("default", "默认", "com.yanyuan.toolbox.alias.Default"),
        BLUE("blue", "蓝色", "com.yanyuan.toolbox.alias.Blue"),
        PURPLE("purple", "紫色", "com.yanyuan.toolbox.alias.Purple"),
        DARK("dark", "深色", "com.yanyuan.toolbox.alias.Dark");

        companion object {
            fun fromId(id: String?): IconTheme =
                values().firstOrNull { it.id == id } ?: DEFAULT
        }
    }

    private const val PREF_NAME = "icon_theme_pref"
    private const val KEY_CURRENT = "current_theme"

    fun getCurrent(context: Context): IconTheme {
        val id = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT, null)
        return IconTheme.fromId(id)
    }

    fun switchTo(context: Context, theme: IconTheme): Boolean {
        val pm = context.packageManager
        val pkg = context.packageName

        IconTheme.values().forEach { t ->
            setComponentEnabled(pm, pkg, t.aliasClass, false)
        }
        setComponentEnabled(pm, pkg, theme.aliasClass, true)

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CURRENT, theme.id).apply()

        return true
    }

    private fun setComponentEnabled(
        pm: PackageManager,
        pkg: String,
        className: String,
        enabled: Boolean
    ) {
        val state = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        try {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, className),
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }
}