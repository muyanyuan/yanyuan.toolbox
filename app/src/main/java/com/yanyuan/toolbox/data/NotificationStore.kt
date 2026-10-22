package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

object NotificationStore {

    private const val PREF_NAME = "notification_pref"
    private const val KEY_ENABLED = "notification_enabled"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        NotificationState.update(on)
    }
}

object NotificationState {
    private val _enabled = mutableStateOf(true)
    val enabled: Boolean get() = _enabled.value

    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue

    fun update(on: Boolean) {
        _enabled.value = on
        _version.intValue++
    }

    fun initFromStore(context: Context) {
        _enabled.value = NotificationStore.isEnabled(context)
        _version.intValue++
    }
}