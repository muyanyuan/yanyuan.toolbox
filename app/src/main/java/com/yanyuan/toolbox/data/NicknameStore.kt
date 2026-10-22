package com.yanyuan.toolbox.data

import android.content.Context

class NicknameStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("nickname_pref", Context.MODE_PRIVATE)

    var nickname: String
        get() = prefs.getString("nickname", "") ?: ""
        set(v) = prefs.edit().putString("nickname", v).apply()
}