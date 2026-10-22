package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 欢迎页外观状态（全局可观察）
 */
object WelcomeAppearanceState {
    var version by mutableIntStateOf(0)
        private set

    var transparent by mutableStateOf(false)
        private set

    fun update(v: Boolean) {
        transparent = v
        version++
    }
}

/**
 * 欢迎页外观持久化
 */
class WelcomeAppearanceStore(context: Context) {

    companion object {
        private const val PREF = "welcome_appearance"
        private const val KEY_TRANSPARENT = "transparent"
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF, Context.MODE_PRIVATE)

    var transparent: Boolean
        get() = prefs.getBoolean(KEY_TRANSPARENT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TRANSPARENT, value).apply()
            WelcomeAppearanceState.update(value)
        }

    /** 启动时调用一次，把存储值加载到内存状态 */
    fun loadToState() {
        WelcomeAppearanceState.update(prefs.getBoolean(KEY_TRANSPARENT, false))
    }
}