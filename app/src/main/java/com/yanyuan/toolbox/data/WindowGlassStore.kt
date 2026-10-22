package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

// =======================================================
//  窗口玻璃样式（对话框/弹窗背景）：普通 / 毛玻璃 / 液态玻璃
//  毛玻璃与液态玻璃在 Android 12+ 通过窗口背景模糊（blur behind）实现，
//  低版本自动回退普通样式。
// =======================================================
enum class WindowGlassMode(val id: String, val displayName: String, val description: String) {
    NORMAL("normal", "普通", "实色窗口背景，与之前一致"),
    FROST("frost", "毛玻璃", "模糊窗口身后的应用内容，表面柔和"),
    LIQUID("liquid", "液态玻璃", "更通透的模糊，边缘更亮");

    companion object {
        fun fromId(id: String?): WindowGlassMode =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

object WindowGlassStore {
    private const val PREF_NAME = "window_glass_pref"
    private const val KEY_MODE = "window_glass_mode"

    fun getCurrent(context: Context): WindowGlassMode {
        val id = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return WindowGlassMode.fromId(id)
    }

    fun setCurrent(context: Context, mode: WindowGlassMode) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.id).apply()
        WindowGlassState.notifyChanged()
    }
}

object WindowGlassState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue
    fun notifyChanged() { _version.intValue = _version.intValue + 1 }
}
