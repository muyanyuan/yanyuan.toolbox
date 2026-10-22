package com.yanyuan.toolbox.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 全局外观状态。任何地方调用 notifyXxxChanged()，所有监听者会自动刷新。
 */
object AppearanceState {

    var iconVersion by mutableIntStateOf(0)
        private set

    var backgroundVersion by mutableIntStateOf(0)
        private set

    fun notifyIconChanged() { iconVersion++ }
    fun notifyBackgroundChanged() { backgroundVersion++ }
}