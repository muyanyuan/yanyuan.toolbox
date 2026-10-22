package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf

object GlassOpacityStore {
    private const val PREF_NAME = "glass_opacity_pref"
    private const val KEY_OPACITY = "glass_opacity"

    const val DEFAULT = 0.92f
    const val MIN = 0.30f
    const val MAX = 1.00f

    fun getCurrent(context: Context): Float {
        return context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_OPACITY, DEFAULT)
            .coerceIn(MIN, MAX)
    }

    fun setCurrent(context: Context, value: Float) {
        val v = value.coerceIn(MIN, MAX)
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_OPACITY, v).apply()
        GlassOpacityState.update(v)
    }
}

/** 全局毛玻璃透明度状态 */
object GlassOpacityState {
    private val _alpha = mutableFloatStateOf(GlassOpacityStore.DEFAULT)
    private val _version = mutableIntStateOf(0)

    val alpha: Float get() = _alpha.floatValue
    val version: Int get() = _version.intValue

    fun update(v: Float) {
        _alpha.floatValue = v.coerceIn(GlassOpacityStore.MIN, GlassOpacityStore.MAX)
        _version.intValue++
    }
}