package com.yanyuan.toolbox.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.yanyuan.toolbox.data.AppThemeColor
import com.yanyuan.toolbox.data.AppearanceRepository
import com.yanyuan.toolbox.data.AppearanceState
import com.yanyuan.toolbox.data.GlassOpacityState
import com.yanyuan.toolbox.data.GlassOpacityStore
import com.yanyuan.toolbox.data.ThemeColorState
import com.yanyuan.toolbox.data.ThemeColorStore
import com.yanyuan.toolbox.data.ThemeMode
import com.yanyuan.toolbox.data.ThemeModeState
import com.yanyuan.toolbox.data.ThemeModeStore

// =========================================================
//  颜色工具
// =========================================================
private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha
)

private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha
)

// =========================================================
//  亮色配色
// =========================================================
private fun lightSchemeFrom(seed: Color): ColorScheme {
    val primary = seed
    val primaryContainer = seed.lighten(0.72f)
    val onPrimaryContainer = seed.darken(0.55f)

    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,

        secondary = seed.darken(0.08f),
        onSecondary = Color.White,
        secondaryContainer = seed.lighten(0.80f),
        onSecondaryContainer = seed.darken(0.60f),

        tertiary = seed.lighten(0.10f),
        onTertiary = Color.White,

        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),

        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1E293B),

        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),

        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0),

        error = Color(0xFFEF4444),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D)
    )
}

// =========================================================
//  暗色配色
// =========================================================
private fun darkSchemeFrom(seed: Color): ColorScheme {
    val primary = seed.lighten(0.15f)
    val primaryContainer = seed.darken(0.55f)
    val onPrimaryContainer = seed.lighten(0.75f)

    return darkColorScheme(
        primary = primary,
        onPrimary = seed.darken(0.75f),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,

        secondary = seed.lighten(0.05f),
        onSecondary = seed.darken(0.70f),
        secondaryContainer = seed.darken(0.60f),
        onSecondaryContainer = seed.lighten(0.70f),

        tertiary = seed.lighten(0.25f),
        onTertiary = seed.darken(0.75f),

        background = Color(0xFF0F0F12),
        onBackground = Color(0xFFE5E7EB),

        surface = Color(0xFF1A1A1F),
        onSurface = Color(0xFFE5E7EB),

        surfaceVariant = Color(0xFF26262D),
        onSurfaceVariant = Color(0xFFB0B4BB),

        outline = Color(0xFF3F3F46),
        outlineVariant = Color(0xFF2A2A32),

        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2)
    )
}

// =========================================================
//  判断当前实际是不是深色模式
// =========================================================
@Composable
fun isAppInDarkMode(): Boolean {
    val context = LocalContext.current
    val modeVersion = ThemeModeState.version
    val mode = remember(modeVersion) { ThemeModeStore.getCurrent(context) }
    val systemDark = isSystemInDarkTheme()
    return when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

// =========================================================
//  让 ColorScheme 的每个颜色平滑过渡
// =========================================================
@Composable
private fun animateColorScheme(target: ColorScheme, durationMs: Int = 500): ColorScheme {
    val spec = tween<Color>(durationMillis = durationMs)

    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer")

    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer")

    val tertiary by animateColorAsState(target.tertiary, spec, label = "tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, spec, label = "onTertiary")

    val background by animateColorAsState(target.background, spec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "onBackground")

    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")

    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant")

    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "outlineVariant")

    val error by animateColorAsState(target.error, spec, label = "error")
    val onError by animateColorAsState(target.onError, spec, label = "onError")
    val errorContainer by animateColorAsState(target.errorContainer, spec, label = "errorContainer")
    val onErrorContainer by animateColorAsState(target.onErrorContainer, spec, label = "onErrorContainer")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,

        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,

        tertiary = tertiary,
        onTertiary = onTertiary,

        background = background,
        onBackground = onBackground,

        surface = surface,
        onSurface = onSurface,

        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,

        outline = outline,
        outlineVariant = outlineVariant,

        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer
    )
}

// =========================================================
//  莫奈取色：从【上传的应用背景图】提取与图片实际观感相符的主色
// =========================================================

/**
 * 提取背景图主色：**dominant（图片主体色）优先**，其次 vibrant / muted / lightVibrant ——
 * 保证主题色与图片实际观感一致（vibrant 优先会选到图里一小块高饱和区域）。
 * 无背景图或失败返回 null。
 */
fun monetPrimaryFromBackground(context: Context): Color? {
    return try {
        val file = AppearanceRepository.backgroundFile(context)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        // 取样到最长边 128px，避免大图在主题重组时卡顿。
        val scale = (128f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val sample = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val swatch = Palette.from(sample).generate()
            .let {
                it.dominantSwatch ?: it.vibrantSwatch ?: it.mutedSwatch ?: it.lightVibrantSwatch
            }
        swatch?.let { Color(it.rgb) }
    } catch (_: Exception) {
        null
    }
}

private fun monetSchemeFrom(context: Context, dark: Boolean): ColorScheme {
    val primary = monetPrimaryFromBackground(context) ?: Color(0xFF6750A4)
    return if (dark) darkSchemeFrom(primary.lighten(0.25f)) else lightSchemeFrom(primary)
}

// =========================================================
//  主题
// =========================================================
@Composable
fun YanyuanTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 初始化全局状态（毛玻璃透明度）
    LaunchedEffect(Unit) {
        GlassOpacityState.update(GlassOpacityStore.getCurrent(context))
    }

    val colorVersion = ThemeColorState.version
    val modeVersion = ThemeModeState.version

    val themeColor: AppThemeColor = remember(colorVersion) {
        ThemeColorStore.getCurrent(context)
    }
    val themeMode: ThemeMode = remember(modeVersion) {
        ThemeModeStore.getCurrent(context)
    }
    val systemDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 莫奈取色的取色来源：① 从【上传的应用背景图】提取主色（Palette）；
    // ② 未上传背景图时回退到系统壁纸动态取色（Android 12+）；③ 再回退占位配色。
    val bgVersion = AppearanceState.backgroundVersion
    val wantMonet = dynamicColor || themeColor == AppThemeColor.MONET
    val targetScheme = remember(themeColor, darkTheme, bgVersion, dynamicColor, colorVersion) {
        when {
            themeColor == AppThemeColor.MONET &&
                    AppearanceRepository.backgroundFile(context).exists() ->
                monetSchemeFrom(context, darkTheme)
            // 自定义色：长按调色盘保存的色值，亮/暗整套由它衍生。
            themeColor == AppThemeColor.CUSTOM -> {
                val custom = ThemeColorStore.getCustom(context) ?: themeColor.lightPrimary
                if (darkTheme) darkSchemeFrom(custom) else lightSchemeFrom(custom)
            }
            wantMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            }
            darkTheme -> darkSchemeFrom(themeColor.darkPrimary)
            else -> lightSchemeFrom(themeColor.lightPrimary)
        }
    }

    val animatedScheme = animateColorScheme(targetScheme)

    MaterialTheme(
        colorScheme = animatedScheme,
        content = content
    )
}