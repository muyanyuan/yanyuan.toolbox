package com.yanyuan.toolbox.data

import android.content.Context
import java.io.File
import java.io.InputStream

object AppearanceRepository {

    private const val ICON_FILE = "user_icon.png"
    private const val BG_FILE = "user_background.png"

    fun iconFile(context: Context): File = File(context.filesDir, ICON_FILE)
    fun backgroundFile(context: Context): File = File(context.filesDir, BG_FILE)

    fun hasIcon(context: Context) = iconFile(context).exists()
    fun hasBackground(context: Context) = backgroundFile(context).exists()

    fun saveIcon(context: Context, input: InputStream) {
        iconFile(context).outputStream().use { input.copyTo(it) }
        AppearanceState.notifyIconChanged()
    }

    fun saveBackground(context: Context, input: InputStream) {
        backgroundFile(context).outputStream().use { input.copyTo(it) }
        AppearanceState.notifyBackgroundChanged()
    }

    fun clearIcon(context: Context) {
        iconFile(context).delete()
        AppearanceState.notifyIconChanged()
    }

    fun clearBackground(context: Context) {
        backgroundFile(context).delete()
        AppearanceState.notifyBackgroundChanged()
    }
}