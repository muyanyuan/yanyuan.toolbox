package com.yanyuan.toolbox.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

// ========================================================
//  会话存储
// ========================================================
class AiChatStorage(context: Context, prefsName: String = PREF) {
    companion object {
        private const val PREF = "ai_chat_store"
        private const val KEY_CONV = "conversations"
        private const val KEY_DEEP_THINKING = "deep_thinking_enabled"
    }

    private val gson = Gson()
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<List<AiChatConversation>?>(null)
    val state = _state.asStateFlow()

    fun get(): List<AiChatConversation> {
        if (_state.value == null) {
            _state.update {
                runCatching {
                    val json = prefs.getString(KEY_CONV, null) ?: return@runCatching emptyList()
                    val t = object : TypeToken<List<AiChatConversation>>() {}.type
                    gson.fromJson<List<AiChatConversation>>(json, t)
                }.getOrNull() ?: emptyList()
            }
        }
        return _state.value.orEmpty()
    }

    fun set(list: List<AiChatConversation>) {
        _state.update { list }
        prefs.edit().putString(KEY_CONV, gson.toJson(list)).apply()
    }

    // ==================== 模式开关（深度思考） ====================
    data class ModeToggles(val deepThinking: Boolean = false)

    fun getModeToggles(): ModeToggles = ModeToggles(
        deepThinking = prefs.getBoolean(KEY_DEEP_THINKING, false)
    )

    fun setDeepThinkingEnabled(v: Boolean) {
        prefs.edit().putBoolean(KEY_DEEP_THINKING, v).apply()
    }
}

// ========================================================
//  人格存储
// ========================================================
class PersonaStorage(context: Context) {
    companion object {
        private const val PREF = "persona_store"
        private const val KEY_LIST = "personas"
        private const val KEY_ACTIVE = "active_id"
    }

    private val gson = Gson()
    private val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<List<AiPersona>?>(null)
    val state = _state.asStateFlow()
    private val _activeId = MutableStateFlow<String?>(null)
    val activeIdState = _activeId.asStateFlow()

    fun get(): List<AiPersona> {
        if (_state.value == null) {
            _state.update {
                runCatching {
                    val json = prefs.getString(KEY_LIST, null) ?: return@runCatching emptyList()
                    val t = object : TypeToken<List<AiPersona>>() {}.type
                    gson.fromJson<List<AiPersona>>(json, t)
                }.getOrNull() ?: emptyList()
            }
        }
        return _state.value.orEmpty()
    }

    fun getActiveId(): String? {
        if (_activeId.value == null) {
            _activeId.value = prefs.getString(KEY_ACTIVE, null)
        }
        return _activeId.value
    }

    fun setActiveId(id: String?) {
        _activeId.value = id
        prefs.edit().apply {
            if (id.isNullOrBlank()) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
        }.apply()
    }

    fun save(persona: AiPersona) {
        val list = get().toMutableList()
        val i = list.indexOfFirst { it.id == persona.id }
        if (i >= 0) list[i] = persona.copy(updatedAt = System.currentTimeMillis())
        else list.add(persona)
        setList(list)
    }

    fun delete(id: String) {
        val list = get().filterNot { it.id == id }
        setList(list)
        if (getActiveId() == id) setActiveId(null)
    }

    private fun setList(list: List<AiPersona>) {
        _state.update { list }
        prefs.edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }
}

// ========================================================
//  全局 AI 模型选择
// ========================================================
class AiModelPreference(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai_model_pref", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<String?>(null)
    val state = _state.asStateFlow()

    fun get(): String {
        // 每次回读 prefs：切换对话框在另一个实例里写入，内存缓存会过期（读到旧模型）。
        val fromPrefs = prefs.getString("model_id", null)
        if (fromPrefs != null) {
            val normalized = if (fromPrefs == "notrack-ai") {
                AiChatModel.UnlimitedAi.id
            } else {
                fromPrefs
            }
            if (normalized != fromPrefs) {
                prefs.edit().putString("model_id", normalized).apply()
            }
            _state.value = normalized
            return normalized
        }
        _state.value = AiChatModel.UnlimitedAi.id
        return AiChatModel.UnlimitedAi.id
    }

    fun set(id: String) {
        _state.value = id
        prefs.edit().putString("model_id", id).apply()
        notifyChanged()
    }

    companion object {
        // 跨实例通知：切换 AI 后主界面（另一个实例）也要感知变化。
        private val _version = androidx.compose.runtime.mutableIntStateOf(0)
        val version: Int get() = _version.intValue
        fun notifyChanged() { _version.intValue = _version.intValue + 1 }
    }
}