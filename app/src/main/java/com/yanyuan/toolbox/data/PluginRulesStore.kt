package com.yanyuan.toolbox.data

import android.content.Context
import org.json.JSONObject

class PluginRulesStore(context: Context, private val pluginId: String) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("plugin_rules_$pluginId", Context.MODE_PRIVATE)

    val rulesJson: String
        get() = prefs.getString(KEY_RULES, null) ?: migratedRules() ?: "{}"

    fun setRules(json: String) {
        prefs.edit().putString(KEY_RULES, json).apply()
    }

    fun importInitialRules(json: String) {
        if (prefs.contains(KEY_RULES)) return
        val oldRules = migratedRules()
        val initial = oldRules ?: json
        prefs.edit().putString(KEY_RULES, initial).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        appContext.getSharedPreferences(LEGACY_PREF_PREFIX + pluginId, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun migratedRules(): String? {
        val old = appContext.getSharedPreferences(LEGACY_PREF_PREFIX + pluginId, Context.MODE_PRIVATE)
            .getString(KEY_RULES, null)
        if (old.isNullOrBlank() || old == "{}") return null
        return runCatching {
            JSONObject(old)
            prefs.edit().putString(KEY_RULES, old).apply()
            old
        }.getOrNull()
    }

    companion object {
        private const val KEY_RULES = "rules_json"
        private const val LEGACY_PREF_PREFIX = "skip_ad_"
    }
}
