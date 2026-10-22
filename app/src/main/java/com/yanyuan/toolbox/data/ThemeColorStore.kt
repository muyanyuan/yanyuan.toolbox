package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// =======================================================
//  主题色
// =======================================================
enum class AppThemeColor(
    val id: String,
    val displayName: String,
    val lightPrimary: Color,
    val darkPrimary: Color
) {
    INDIGO("indigo", "靛蓝", Color(0xFF6366F1), Color(0xFF818CF8)),
    BLUE("blue", "天蓝", Color(0xFF3B82F6), Color(0xFF60A5FA)),
    PURPLE("purple", "紫罗兰", Color(0xFF8B5CF6), Color(0xFFA78BFA)),
    PINK("pink", "樱花粉", Color(0xFFEC4899), Color(0xFFF472B6)),
    RED("red", "珊瑚红", Color(0xFFEF4444), Color(0xFFF87171)),
    ORANGE("orange", "落日橙", Color(0xFFF97316), Color(0xFFFB923C)),
    GREEN("green", "翡翠绿", Color(0xFF10B981), Color(0xFF34D399)),
    CYAN("cyan", "青碧", Color(0xFF06B6D4), Color(0xFF22D3EE)),
    // 莫奈取色 = Material You 动态配色（Android 12+ 从壁纸提取）。
    // 选中后由 YanyuanTheme 走 dynamicColorScheme，这里的主色仅作低版本回退与色板占位。
    MONET("monet", "莫奈取色", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    // 自定义：短按使用已保存的自定义色；长按进入调色盘重新选择（色值存 ThemeColorStore.custom）。
    CUSTOM("custom", "自定义", Color(0xFF6366F1), Color(0xFF818CF8));

    companion object {
        fun fromId(id: String?): AppThemeColor =
            values().firstOrNull { it.id == id } ?: INDIGO
    }
}

object ThemeColorStore {
    private const val PREF_NAME = "theme_color_pref"
    private const val KEY_COLOR = "theme_color_id"
    private const val KEY_CUSTOM = "custom_theme_color"
    private const val NO_CUSTOM = Int.MIN_VALUE

    fun getCurrent(context: Context): AppThemeColor {
        val id = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COLOR, null)
        return AppThemeColor.fromId(id)
    }

    fun setCurrent(context: Context, color: AppThemeColor) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COLOR, color.id).apply()
        ThemeColorState.notifyChanged()
    }

    /** 自定义主题色（长按调色盘保存），未设置过返回 null。 */
    fun getCustom(context: Context): Color? {
        val raw = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_CUSTOM, NO_CUSTOM)
        return if (raw == NO_CUSTOM) null else Color(raw)
    }

    fun setCustom(context: Context, color: Color) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CUSTOM, color.toArgb()).apply()
        ThemeColorState.notifyChanged()
    }
}

object ThemeColorState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue
    fun notifyChanged() { _version.intValue = _version.intValue + 1 }
}

// =======================================================
//  深色模式
// =======================================================
enum class ThemeMode(val id: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromId(id: String?): ThemeMode =
            values().firstOrNull { it.id == id } ?: SYSTEM
    }
}

object ThemeModeStore {
    private const val PREF_NAME = "theme_mode_pref"
    private const val KEY_MODE = "theme_mode"

    fun getCurrent(context: Context): ThemeMode {
        val id = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return ThemeMode.fromId(id)
    }

    fun setCurrent(context: Context, mode: ThemeMode) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.id).apply()
        ThemeModeState.notifyChanged()
    }
}

object ThemeModeState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue
    fun notifyChanged() { _version.intValue = _version.intValue + 1 }
}