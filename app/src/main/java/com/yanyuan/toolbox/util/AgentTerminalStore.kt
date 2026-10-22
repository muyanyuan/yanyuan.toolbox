package com.yanyuan.toolbox.util

import android.content.Context

/** 受控诊断终端配置；默认关闭，不能因升级自动扩大权限。 */
enum class TerminalBackend(val id: String, val label: String) {
    APP_SANDBOX("app", "应用沙箱"),
    SHIZUKU("shizuku", "Shizuku"),
    BUILTIN_RUNTIME("builtin_runtime", "Toolbox Runtime")
}

object AgentTerminalStore {
    private const val PREF = "agent_terminal"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BACKEND = "backend"

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        AgentPermissionState.notifyChanged()
    }

    fun backend(context: Context): TerminalBackend {
        val preferences = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_BACKEND, TerminalBackend.APP_SANDBOX.id)
        if (stored == "runtime" || stored == "termux") {
            preferences.edit().putString(KEY_BACKEND, TerminalBackend.BUILTIN_RUNTIME.id).apply()
            return TerminalBackend.BUILTIN_RUNTIME
        }
        return TerminalBackend.entries.firstOrNull { it.id == stored } ?: TerminalBackend.APP_SANDBOX
    }

    fun setBackend(context: Context, backend: TerminalBackend) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, backend.id).apply()
        AgentPermissionState.notifyChanged()
    }
}
