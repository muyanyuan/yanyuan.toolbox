package com.yanyuan.toolbox.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yanyuan.toolbox.data.AiChatConversation
import com.yanyuan.toolbox.data.AiChatMessage
import com.yanyuan.toolbox.data.AiModelPreference
import com.yanyuan.toolbox.data.ChatAttachment
import com.yanyuan.toolbox.network.AiChatApi
import com.yanyuan.toolbox.network.OpenAiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

// ========================================================
//  导入模型专用会话存储（独立 prefs，与旧 AI 页互不影响）
// ========================================================
class AiChatProStorage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ai_chat_pro", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<AiChatConversation>>() {}.type

    // ★ 按所选模型分库：切换 AI 大模型 → 切换到该模型自己的对话记录（与原 AI 页互不相通）。
    fun loadConversations(scope: String): MutableList<AiChatConversation> =
        runCatching {
            gson.fromJson<MutableList<AiChatConversation>>(prefs.getString("list_$scope", null), type)
        }.getOrNull() ?: mutableListOf()

    fun saveConversations(scope: String, list: List<AiChatConversation>) {
        prefs.edit().putString("list_$scope", gson.toJson(list)).apply()
    }

    fun loadActiveId(scope: String): String = prefs.getString("active_$scope", "") ?: ""
    fun saveActiveId(scope: String, id: String) { prefs.edit().putString("active_$scope", id).apply() }

    // 每个平台下选中的具体模型与强度（温度）。
    fun getSelectedModel(platformId: String): String? =
        prefs.getString("model_$platformId", null)

    fun setSelectedModel(platformId: String, modelId: String) {
        prefs.edit().putString("model_$platformId", modelId).apply()
    }

    fun clearSelectedModel(platformId: String, modelId: String? = null) {
        val key = "model_$platformId"
        if (modelId == null || prefs.getString(key, null) == modelId) {
            prefs.edit().remove(key).apply()
        }
    }

    fun getTemperature(): Float = prefs.getFloat("temperature", 0.7f)

    fun setTemperature(value: Float) {
        prefs.edit().putFloat("temperature", value.coerceIn(0f, 2f)).apply()
    }
}

// ========================================================
//  导入模型专用 ViewModel：输入 / 深度思考 / 附件内联 / 流式输出
// ========================================================
class AiChatProViewModel(
    private val storage: AiChatProStorage,
    private val modelPreference: AiModelPreference,
    private val importedProvider: (String) -> AiChatApi.ImportedAiSpec?,
    /** 导入 API 平台授权检查；调用方必须显式提供，避免绕过授权。 */
    private val importedApiAuthorized: (String) -> Boolean
) : ViewModel() {

    data class UiState(
        val conversations: List<com.yanyuan.toolbox.data.AiChatConversation> = emptyList(),
        val activeId: String = "",
        val input: String = "",
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val deepThinking: Boolean = false
    )

    private val api = AiChatApi()

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    // 会话分库作用域 = 当前所选模型。
    private fun scope(): String = modelPreference.get()

    init { reloadForCurrentModel() }

    /** 切换 AI 大模型后：加载该模型自己的对话记录与内容。 */
    fun reloadForCurrentModel() {
        val scope = scope()
        val list = storage.loadConversations(scope).sortedByDescending { it.updatedAt }
        if (list.none { it.id == storage.loadActiveId(scope) }) {
            val conv = com.yanyuan.toolbox.data.AiChatConversation(
                id = UUID.randomUUID().toString(), title = "新对话"
            )
            _state.update { it.copy(conversations = listOf(conv), activeId = conv.id, errorMessage = null) }
            storage.saveActiveId(scope, conv.id)
            storage.saveConversations(scope, listOf(conv))
        } else {
            _state.update { it.copy(conversations = list, activeId = storage.loadActiveId(scope), errorMessage = null) }
        }
    }

    private fun persist(list: List<com.yanyuan.toolbox.data.AiChatConversation>) {
        storage.saveConversations(scope(), list)
    }

    fun changeInput(v: String) { _state.update { it.copy(input = v) } }
    fun changeDeepThinking(v: Boolean) { _state.update { it.copy(deepThinking = v) } }

    fun newConversation() {
        val scope = scope()
        val conv = com.yanyuan.toolbox.data.AiChatConversation(
            id = UUID.randomUUID().toString(), title = "新对话"
        )
        _state.update {
            it.copy(conversations = listOf(conv) + it.conversations, activeId = conv.id, input = "", errorMessage = null)
        }
        persist(_state.value.conversations)
        storage.saveActiveId(scope, conv.id)
    }

    fun switchTo(id: String) {
        _state.update { it.copy(activeId = id) }
        storage.saveActiveId(scope(), id)
    }

    fun send(attachments: List<ChatAttachment> = emptyList()) {
        val cur = _state.value
        val text = cur.input.trim()
        if ((text.isBlank() && attachments.isEmpty()) || cur.isSending) return

        val active = cur.conversations.firstOrNull { it.id == cur.activeId } ?: return
        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(), role = "user",
            content = text, attachments = attachments
        )
        val aiMsg = AiChatMessage(id = UUID.randomUUID().toString(), role = "assistant", content = "")

        // 深度思考：附加系统提示（导入模型通用做法）。
        val sysMsg = if (cur.deepThinking) {
            AiChatMessage(id = "sys-thinking", role = "system",
                content = "请先进行深入、分步骤的思考，再给出最终答案；思考过程用「思考：」开头。")
        } else null

        val nextActive = active.copy(
            title = if (active.title == "新对话" && text.isNotBlank()) text.take(16) else active.title,
            messages = active.messages + userMsg + aiMsg,
            updatedAt = System.currentTimeMillis()
        )
        val list = listOf(nextActive) + cur.conversations.filterNot { it.id == nextActive.id }
        _state.update {
            it.copy(conversations = list, activeId = nextActive.id, input = "", isSending = true, errorMessage = null)
        }
        persist(list)

        // 附件内联：文本类内容直接给模型；非文本给说明。
        val attachInlined = attachments.mapIndexed { i, att ->
            val raw = runCatching { File(att.path).readText(Charsets.UTF_8) }.getOrNull()
            val body = if (raw != null && raw.length <= 200_000) raw
            else "（二进制/超大文件，未内联：${(att.size / 1024).coerceAtLeast(1)}KB）"
            OpenAiChatMessage(role = "user", content = "【附件 ${att.name}】\n$body")
        }

        val history = (listOfNotNull(sysMsg) + active.messages + userMsg)
            .filter { it.content.isNotBlank() }
            .map { OpenAiChatMessage(role = it.role, content = it.content) } + attachInlined

        val modelId = modelPreference.get()
        val imported = importedProvider(modelId)

        viewModelScope.launch {
            try {
                if (imported != null && !importedApiAuthorized(modelId)) {
                    throw IllegalStateException("当前导入 API 平台未获授权，请在“接入AI”的 API 授权中开启")
                }
                api.streamChat(modelId, history, imported)
                    .buffer(Channel.UNLIMITED)
                    .flowOn(Dispatchers.Default)
                    .collect { delta ->
                        if (imported != null && !importedApiAuthorized(modelId)) {
                            throw IllegalStateException("当前导入 API 平台授权已撤销，请重新授权后再发送")
                        }
                        appendDelta(nextActive.id, aiMsg.id, delta)
                    }
                _state.update { it.copy(isSending = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, errorMessage = e.message ?: "请求失败") }
            }
        }
    }

    private fun appendDelta(convId: String, msgId: String, delta: String) {
        _state.update { st ->
            val list = st.conversations.map { conv ->
                if (conv.id != convId) conv
                else conv.copy(messages = conv.messages.map { m ->
                    if (m.id == msgId) m.copy(content = m.content + delta) else m
                })
            }
            st.copy(conversations = list)
        }
    }
}
