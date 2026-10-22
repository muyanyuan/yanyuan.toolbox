package com.yanyuan.toolbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanyuan.toolbox.data.AiChatConversation
import com.yanyuan.toolbox.data.AiChatMessage
import com.yanyuan.toolbox.data.AiChatMessageBranch
import com.yanyuan.toolbox.data.AiChatStorage
import com.yanyuan.toolbox.data.AiModelPreference
import com.yanyuan.toolbox.data.AiPersona
import com.yanyuan.toolbox.data.PersonaStorage
import com.yanyuan.toolbox.network.AiChatApi
import com.yanyuan.toolbox.network.OpenAiChatMessage
import com.yanyuan.toolbox.util.AgentTerminalStore
import com.yanyuan.toolbox.util.TerminalBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AiChatViewModel(
    private val chatStorage: AiChatStorage,
    private val personaStorage: PersonaStorage,
    private val modelPreference: AiModelPreference,
    // 按 modelId（= 平台 id）解析「接入AI」里导入的平台 API；内置模型返回 null。
    private val importedProvider: (String) -> com.yanyuan.toolbox.network.AiChatApi.ImportedAiSpec? = { null },
    /**
     * Agent 环境只由导入 API 页面提供；内置 AI 传 null，因而没有文件工具执行路径。
     */
    private val agentEnvironment: com.yanyuan.toolbox.util.AgentEnvironment? = null,
    initialAgentEnabled: Boolean = true,
    /** 导入 API 平台在请求前的授权校验；调用方必须明确处理内置模型和未知选择。 */
    private val importedApiAuthorized: (String) -> Boolean
) : ViewModel() {

    enum class RetryMode { Regenerate, Detailed, Concise }

    enum class AgentPhase {
        Idle,
        Generating,
        ExecutingTools,
        WaitingForUser,
        WaitingForModel,
        Completed,
        Failed
    }

    data class AgentWriteApproval(
        val id: String,
        val path: String,
        val existed: Boolean,
        val byteSize: Int,
        val preview: String
    )

    data class TerminalApproval(
        val id: String,
        val backend: String,
        val program: String,
        val args: List<String>,
        val cwd: String,
        val timeoutMs: Long,
        val maxOutputBytes: Int,
        val shellCommand: String? = null,
        val builtinRuntime: Boolean = false,
        val runtimeVersion: String? = null
    )

    data class AiChatUiState(
        val conversations: List<AiChatConversation> = emptyList(),
        val activeConversationId: String = "",
        val input: String = "",
        val deepThinkingEnabled: Boolean = false,
        val isSending: Boolean = false,
        val agentPhase: AgentPhase = AgentPhase.Idle,
        val agentActionSummary: List<String> = emptyList(),
        val pendingAgentWrite: AgentWriteApproval? = null,
        val pendingTerminal: TerminalApproval? = null,
        val workspacePhase: WorkspaceRunPhase = WorkspaceRunPhase.Idle,
        val toolCalls: List<ToolCallUi> = emptyList(),
        val conversationNodes: List<ConversationNode> = emptyList(),
        val fileChanges: List<FileChangeUi> = emptyList(),
        val selectedChangeId: String? = null,
        val reviewOpen: Boolean = false,
        val applyInProgress: Boolean = false,
        val errorMessage: String? = null,
        val personas: List<AiPersona> = emptyList(),
        val activePersonaId: String? = null,
    ) {
        val activePersona: AiPersona?
            get() = personas.firstOrNull { it.id == activePersonaId }

        val activeConversation: AiChatConversation?
            get() = conversations.firstOrNull { it.id == activeConversationId }
    }

    private val api = AiChatApi()

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState = _uiState.asStateFlow()
    private val conversationNodes = ConversationNodeReducer()
    private var nextNodeSeq = 0L
    private var activeAssistantMessageId: String? = null

    private fun publishConversationNodes() {
        val snapshot = conversationNodes.snapshot()
        _uiState.update { it.copy(conversationNodes = snapshot) }
    }

    private fun updateToolNode(
        id: String,
        state: ToolCallState,
        output: String? = null,
        error: String? = null
    ) {
        val node = conversationNodes.snapshot().firstOrNull { it.id == id } as? ConversationNode.ToolCall
            ?: return
        conversationNodes.upsert(node.copy(
            state = state,
            output = output ?: node.output,
            error = error,
            messageId = activeAssistantMessageId
        ))
        publishConversationNodes()
    }

    private fun sanitizeToolOutput(text: String): String {
        val sensitive = Regex("(?i)(api[_-]?key|access_token|refresh_token|shared_prefs|api_secret_store|password|client[_-]?secret)")
        val path = Regex("(?<![A-Za-z0-9])(?:/(?:[^\\s\\\"'<>|]+)|[A-Za-z]:\\\\(?:[^\\s\\\"'<>|]+))")
        return text.lineSequence().map { line ->
            when {
                sensitive.containsMatchIn(line) -> "已隐藏敏感内容"
                else -> path.replace(line) { match -> displayPath(match.value) }
            }
        }.joinToString("\n").take(8_000)
    }

    /** 保留可定位的末级目录和文件名，不把普通工作区路径全部显示成同一句占位文本。 */
    private fun displayPath(raw: String): String {
        val cleaned = raw.trimEnd(',', '，', '.', '。', ';', '；', ':', '：', ')', '）', ']', '】')
        val parts = cleaned.split(Regex("[/\\\\]+")).filter { it.isNotBlank() }
        if (parts.size <= 3) return cleaned
        return "…/" + parts.takeLast(3).joinToString("/")
    }

    @Volatile
    private var agentEnabled: Boolean = initialAgentEnabled

    private var sendJob: Job? = null
    private var persistJob: Job? = null
    private var pendingWriteDecision: CompletableDeferred<Boolean>? = null
    private var pendingWriteId: String? = null
    private var pendingTerminalDecision: CompletableDeferred<Boolean>? = null
    private var pendingTerminalId: String? = null

    // ★ 流式 delta 缓冲：每 60ms 批量刷新一次 UI，避免卡顿
    private val deltaBuffer = StringBuilder()
    private var deltaFlushJob: Job? = null
    private var lastFlushConvId: String? = null
    private var lastFlushMsgId: String? = null

    init {
        // SharedPreferences/Gson 反序列化可能包含很长的历史消息，不能在 Compose 创建 ViewModel 的主线程执行。
        viewModelScope.launch(Dispatchers.IO) {
            val list = chatStorage.get().ifEmpty { listOf(newConversation()) }
                .sortedByDescending { it.updatedAt }
            chatStorage.set(list)

            val personas = personaStorage.get()
            val activeId = personaStorage.getActiveId()
            val toggles = chatStorage.getModeToggles()
            _uiState.update {
                it.copy(
                    conversations = list,
                    activeConversationId = list.first().id,
                    personas = personas,
                    activePersonaId = activeId,
                    deepThinkingEnabled = toggles.deepThinking
                )
            }
        }

        combine(personaStorage.state, personaStorage.activeIdState) { l, a ->
            (l ?: emptyList()) to a
        }.distinctUntilChanged().onEach { (list, id) ->
            _uiState.update { it.copy(personas = list, activePersonaId = id) }
        }.launchIn(viewModelScope)
    }

    // ==================== 基础操作 ====================
    fun changeInput(v: String) = _uiState.update { it.copy(input = v) }

    fun selectFileChange(id: String) = _uiState.update { it.copy(selectedChangeId = id) }

    fun changeDeepThinkingEnabled(v: Boolean) {
        chatStorage.setDeepThinkingEnabled(v)
        _uiState.update { it.copy(deepThinkingEnabled = v) }
    }

    fun setActivePersona(id: String?) {
        personaStorage.setActiveId(id)
        _uiState.update { it.copy(activePersonaId = id) }
    }

    // ★ 人格 CRUD
    fun savePersona(persona: AiPersona) {
        personaStorage.save(persona)
        _uiState.update { it.copy(personas = personaStorage.get()) }
    }

    fun deletePersona(id: String) {
        personaStorage.delete(id)
        _uiState.update {
            it.copy(
                personas = personaStorage.get(),
                activePersonaId = personaStorage.getActiveId()
            )
        }
    }

    fun bindConversationPersona(conversationId: String, personaId: String?) {
        val list = _uiState.value.conversations.map {
            if (it.id == conversationId) it.copy(personaId = personaId.orEmpty()) else it
        }
        persist(list)
        _uiState.update { it.copy(conversations = list) }
    }

    fun createConversation() {
        val c = newConversation().copy(personaId = _uiState.value.activePersonaId.orEmpty())
        val list = listOf(c) + _uiState.value.conversations
        persist(list)
        _uiState.update {
            it.copy(
                conversations = list,
                activeConversationId = c.id,
                input = "",
                errorMessage = null,
            )
        }
    }

    fun selectConversation(id: String) {
        _uiState.update {
            it.copy(
                activeConversationId = id,
                input = "",
                errorMessage = null,
            )
        }
    }

    fun deleteConversation(id: String) {
        val cur = _uiState.value
        val remaining = cur.conversations.filterNot { it.id == id }
            .ifEmpty { listOf(newConversation()) }
            .sortedByDescending { it.updatedAt }
        val active = if (remaining.any { it.id == cur.activeConversationId }) cur.activeConversationId
        else remaining.first().id
        persist(remaining)
        _uiState.update {
            it.copy(conversations = remaining, activeConversationId = active, errorMessage = null)
        }
    }

    private suspend fun awaitAgentWrite(
        request: com.yanyuan.toolbox.util.AgentTools.WriteApprovalRequest
    ): Boolean {
        val decision = CompletableDeferred<Boolean>()
        val approval = AgentWriteApproval(
            id = request.id,
            path = request.path,
            existed = request.existed,
            byteSize = request.byteSize,
            preview = request.preview
        )
        pendingWriteDecision = decision
        pendingWriteId = request.id
        conversationNodes.upsert(ConversationNode.Approval(
            id = "approval-${request.id}", seq = nextNodeSeq++, time = System.currentTimeMillis(),
            callId = request.id, title = "文件写入审批", detail = sanitizeToolOutput(request.path), messageId = activeAssistantMessageId
        ))
        publishConversationNodes()
        _uiState.update {
                it.copy(
                    pendingAgentWrite = approval,
                    workspacePhase = WorkspaceRunPhase.WaitingApproval,
                reviewOpen = true,
                agentPhase = AgentPhase.WaitingForUser
            )
        }
        var approvedResult: Boolean? = null
        return try {
            withTimeoutOrNull(5 * 60_000L) { decision.await() }?.also { approvedResult = it } ?: false
        } finally {
            val approved = approvedResult ?: false
            val approvalId = "approval-${request.id}"
            val node = conversationNodes.snapshot().firstOrNull { it.id == approvalId } as? ConversationNode.Approval
            if (node != null) {
                conversationNodes.upsert(node.copy(answerable = false, approved = approved, messageId = activeAssistantMessageId))
                publishConversationNodes()
            }
            if (pendingWriteDecision === decision) {
                pendingWriteDecision = null
                pendingWriteId = null
                _uiState.update { it.copy(pendingAgentWrite = null) }
            }
        }
    }

    private suspend fun awaitTerminalApproval(request: com.yanyuan.toolbox.util.AgentTools.TerminalApprovalRequest): Boolean {
        val decision = CompletableDeferred<Boolean>()
        pendingTerminalDecision = decision
        pendingTerminalId = request.id
        conversationNodes.upsert(ConversationNode.Approval(
            id = "approval-${request.id}", seq = nextNodeSeq++, time = System.currentTimeMillis(),
            callId = request.id,
            title = if (request.backend == TerminalBackend.BUILTIN_RUNTIME) "Toolbox Runtime 命令审批" else "终端操作审批",
            detail = sanitizeToolOutput(request.shellCommand ?: "${request.program} ${request.args.joinToString(" ")}\n${request.cwd}"),
            messageId = activeAssistantMessageId
        ))
        publishConversationNodes()
        _uiState.update {
            it.copy(
                pendingTerminal = TerminalApproval(
                    id = request.id,
                    backend = request.backend.label,
                    program = request.program,
                    args = request.args,
                    cwd = request.cwd,
                    timeoutMs = request.timeoutMs,
                    maxOutputBytes = request.maxOutputBytes,
                    shellCommand = request.shellCommand,
                    builtinRuntime = request.backend == TerminalBackend.BUILTIN_RUNTIME,
                    runtimeVersion = request.runtimeVersion
                ),
                workspacePhase = WorkspaceRunPhase.WaitingApproval,
                agentPhase = AgentPhase.WaitingForUser
            )
        }
        var approvedResult: Boolean? = null
        var approvalTimedOut = false
        return try {
            val result = withTimeoutOrNull(5 * 60_000L) { decision.await() }
            approvalTimedOut = result == null
            result?.also { approvedResult = it } ?: false
        } finally {
            val nodeId = "approval-${request.id}"
            val node = conversationNodes.snapshot().firstOrNull { it.id == nodeId } as? ConversationNode.Approval
            if (node != null) {
                conversationNodes.upsert(node.copy(
                    answerable = false,
                    approved = approvedResult ?: false,
                    outcome = when {
                        approvalTimedOut -> "expired"
                        approvedResult == true -> "approved"
                        approvedResult == false -> "rejected"
                        else -> "cancelled"
                    }
                ))
                publishConversationNodes()
            }
            if (pendingTerminalDecision === decision) {
                pendingTerminalDecision = null
                pendingTerminalId = null
                _uiState.update { it.copy(pendingTerminal = null) }
            }
        }
    }

    fun approveTerminal(id: String) {
        if (pendingTerminalId == id && pendingTerminalDecision?.complete(true) == true) {
            _uiState.update { state ->
                if (state.pendingTerminal?.id == id) state.copy(pendingTerminal = null) else state
            }
        }
    }

    fun rejectTerminal(id: String) {
        if (pendingTerminalId == id && pendingTerminalDecision?.complete(false) == true) {
            _uiState.update { state ->
                if (state.pendingTerminal?.id == id) state.copy(pendingTerminal = null) else state
            }
        }
    }

    fun approveAgentWrite(id: String) {
        if (pendingWriteId == id) {
            val path = _uiState.value.pendingAgentWrite?.path
            _uiState.update { current ->
                current.copy(
                    fileChanges = current.fileChanges.map { change ->
                        if (change.path == path) change.copy(state = FileChangeState.APPROVED) else change
                    }
                )
            }
            pendingWriteDecision?.complete(true)
        }
    }

    fun rejectAgentWrite(id: String) {
        if (pendingWriteId == id) {
            val path = _uiState.value.pendingAgentWrite?.path
            _uiState.update { current ->
                current.copy(
                    fileChanges = current.fileChanges.map { change ->
                        if (change.path == path) change.copy(state = FileChangeState.REJECTED) else change
                    }
                )
            }
            pendingWriteDecision?.complete(false)
        }
    }

    private fun clearPendingWrite(approved: Boolean = false) {
        pendingWriteDecision?.complete(approved)
        pendingWriteDecision = null
        pendingWriteId = null
        _uiState.update {
            it.copy(
                pendingAgentWrite = null,
                agentPhase = if (it.isSending && it.agentPhase == AgentPhase.WaitingForUser)
                    AgentPhase.ExecutingTools else it.agentPhase
            )
        }
    }

    fun setAgentEnabled(enabled: Boolean) {
        agentEnabled = enabled
        if (!enabled) stopGenerating()
    }

    fun isAgentEnabled(): Boolean = agentEnabled

    fun stopGenerating() {
        clearPendingWrite(false)
        conversationNodes.snapshot().filterIsInstance<ConversationNode.ToolCall>()
            .filter { it.state == ToolCallState.PREPARING || it.state == ToolCallState.RUNNING }
            .forEach { node -> updateToolNode(node.id, ToolCallState.STOPPED) }
        conversationNodes.snapshot().filterIsInstance<ConversationNode.Approval>()
            .filter { it.answerable }
            .forEach { node ->
                conversationNodes.upsert(node.copy(answerable = false, approved = false))
            }
        publishConversationNodes()
        sendJob?.cancel()
        sendJob = null
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        deltaBuffer.clear()
        _uiState.update {
            it.copy(
                isSending = false,
                agentPhase = AgentPhase.Idle,
                agentActionSummary = emptyList(),
                pendingAgentWrite = null
            )
        }
    }

    // ==================== 发送 ====================
    fun send(attachments: List<com.yanyuan.toolbox.data.ChatAttachment> = emptyList()) {
        val cur = _uiState.value
        val text = cur.input.trim()
        if ((text.isBlank() && attachments.isEmpty()) || cur.isSending) return

        val active = cur.conversations.firstOrNull { it.id == cur.activeConversationId }
            ?: newConversation()
        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(), role = "user", content = text,
            attachments = attachments
        )
        val aiMsg = AiChatMessage(id = UUID.randomUUID().toString(), role = "assistant", content = "")
        val now = System.currentTimeMillis()
        conversationNodes.clear()
        nextNodeSeq = 0L
        activeAssistantMessageId = aiMsg.id
        conversationNodes.upsert(ConversationNode.User(userMsg.id, nextNodeSeq++, now, text, attachments))
        conversationNodes.upsert(ConversationNode.Assistant(aiMsg.id, nextNodeSeq++, now, "", streaming = true, messageId = aiMsg.id))
        publishConversationNodes()

        val nextActive = active.copy(
            title = titleFor(active, text),
            messages = active.messages + userMsg + aiMsg,
            updatedAt = System.currentTimeMillis()
        ).syncBranches()
        val list = upsert(cur.conversations, nextActive)
        _uiState.update {
            it.copy(
                conversations = list,
                activeConversationId = nextActive.id,
                input = "",
                isSending = true,
                errorMessage = null,
            )
        }

        // ★ 以“发送这一刻”的开关状态构造这一次请求。附件文件内容不在点击发送的主线程读取，
        // 否则图片 Base64 和文本 readText 会直接造成键盘卡顿甚至 ANR。
        val latest = _uiState.value
        startAssistant(
            nextActive.id, aiMsg.id, aiMsg.createdAt,
            active.messages + userMsg, text, null,
            latest.deepThinkingEnabled, resolvePersona(nextActive.id), attachments
        )
    }

    fun retry(messageId: String, mode: RetryMode) {
        val cur = _uiState.value
        if (cur.isSending) return
        val active = cur.conversations.firstOrNull { it.id == cur.activeConversationId }
            ?.syncBranches() ?: return
        val idx = active.messages.indexOfFirst { it.id == messageId && it.role == "assistant" }
        if (idx <= 0) return

        val src = active.messages.take(idx)
        val prevUser = src.lastOrNull { it.role == "user" } ?: return
        val aiMsg = AiChatMessage(id = UUID.randomUUID().toString(), role = "assistant", content = "")
        conversationNodes.clear()
        nextNodeSeq = 0L
        activeAssistantMessageId = aiMsg.id
        conversationNodes.upsert(ConversationNode.User(prevUser.id, nextNodeSeq++, prevUser.createdAt, prevUser.content, prevUser.attachments))
        conversationNodes.upsert(ConversationNode.Assistant(aiMsg.id, nextNodeSeq++, System.currentTimeMillis(), "", streaming = true, messageId = aiMsg.id))
        publishConversationNodes()
        val nextActive = active.copy(
            messages = src + aiMsg,
            updatedAt = System.currentTimeMillis()
        ).syncBranches()
        val list = upsert(cur.conversations, nextActive)
        persist(list)
        _uiState.update {
            it.copy(
                conversations = list,
                activeConversationId = nextActive.id,
                isSending = true,
                errorMessage = null,
            )
        }

        startAssistant(
            nextActive.id, aiMsg.id, aiMsg.createdAt, src, prevUser.content,
            retryInstruction(mode), cur.deepThinkingEnabled, resolvePersona(nextActive.id)
        )
    }

    fun editUserMessage(messageId: String, newContent: String) {
        val cur = _uiState.value
        val text = newContent.trim()
        if (text.isBlank() || cur.isSending) return

        val active = cur.conversations.firstOrNull { it.id == cur.activeConversationId }
            ?.syncBranches() ?: return
        val uIdx = active.messages.indexOfFirst { it.id == messageId && it.role == "user" }
        if (uIdx < 0) return

        val userMsg = active.messages[uIdx]
        val following = active.messages.drop(uIdx + 1)
        val existing = userMsg.branches.ifEmpty {
            listOf(AiChatMessageBranch(content = userMsg.content, followingMessages = following))
        }
        val aiMsg = AiChatMessage(id = UUID.randomUUID().toString(), role = "assistant", content = "")
        val newBranchIdx = existing.size
        val newUser = userMsg.copy(
            content = text,
            branches = existing + AiChatMessageBranch(text, listOf(aiMsg)),
            activeBranchIndex = newBranchIdx
        )
        val nextActive = active.copy(
            messages = active.messages.take(uIdx) + newUser + aiMsg,
            updatedAt = System.currentTimeMillis()
        ).syncBranches()
        val list = upsert(cur.conversations, nextActive)
        persist(list)
        _uiState.update {
            it.copy(
                conversations = list,
                activeConversationId = nextActive.id,
                isSending = true,
                errorMessage = null,
            )
        }

        startAssistant(
            nextActive.id, aiMsg.id, aiMsg.createdAt,
            nextActive.messages.take(uIdx) + newUser.copy(
                branches = emptyList(),
                activeBranchIndex = 0
            ),
            text, null, cur.deepThinkingEnabled, resolvePersona(nextActive.id)
        )
    }

    fun switchUserBranch(messageId: String, target: Int) {
        val cur = _uiState.value
        if (cur.isSending) return
        val active = cur.conversations.firstOrNull { it.id == cur.activeConversationId }
            ?.syncBranches() ?: return
        val uIdx = active.messages.indexOfFirst { it.id == messageId && it.role == "user" }
        if (uIdx < 0) return
        val userMsg = active.messages[uIdx]
        val branches = userMsg.branches
        if (branches.isEmpty() || target !in branches.indices) return
        val tb = branches[target]
        val newUser = userMsg.copy(content = tb.content, activeBranchIndex = target, branches = branches)
        val nextActive = active.copy(
            messages = active.messages.take(uIdx) + newUser + tb.followingMessages,
            updatedAt = System.currentTimeMillis()
        ).syncBranches()
        val list = upsert(cur.conversations, nextActive)
        persist(list)
        _uiState.update {
            it.copy(
                conversations = list,
                activeConversationId = nextActive.id,
                errorMessage = null
            )
        }
    }

    // ==================== 内部 ====================
    private fun startAssistant(
        convId: String,
        aiMsgId: String,
        startAt: Long,
        srcMessages: List<AiChatMessage>,
        userQuery: String,
        retryInstr: String?,
        deepEnabled: Boolean,
        persona: AiPersona?,
        attachments: List<com.yanyuan.toolbox.data.ChatAttachment> = emptyList()
    ) {
        // 重置缓冲状态
        deltaBuffer.clear()
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        lastFlushConvId = convId
        lastFlushMsgId = aiMsgId

        sendJob = viewModelScope.launch {
            try {
                val attachMsgs = withContext(Dispatchers.IO) {
                    buildAttachmentMessages(attachments)
                }

                // 配置读取、授权判断和 Agent 环境检查可能触发 Gson/Keystore，不能运行在主线程。
                val modelId = withContext(Dispatchers.IO) { modelPreference.get() }
                val provider = if (modelId.startsWith("imported:")) {
                    withContext(Dispatchers.IO) {
                        importedProvider(modelId)
                    } ?: throw IllegalStateException("当前导入 API 配置不可用")
                } else null
                val authorizedAtStart = if (provider != null) {
                    withContext(Dispatchers.IO) {
                        importedApiAuthorized(modelId)
                    }
                } else true
                if (!authorizedAtStart) {
                    throw IllegalStateException("当前导入 API 平台未获授权，请在“接入AI”的 API 授权中开启")
                }

                // ★ Agent（仅导入 API 环境）：模型需要读/写文件时输出 ```agent 块。
                val agentReady = agentEnabled && withContext(Dispatchers.IO) {
                    val environment = agentEnvironment
                    environment != null &&
                        com.yanyuan.toolbox.util.AgentFeatureStore.get(environment.context) &&
                        environment.isAvailable()
                }
                    _uiState.update {
                        it.copy(
                            workspacePhase = if (agentReady) WorkspaceRunPhase.Thinking else WorkspaceRunPhase.Idle,
                            toolCalls = emptyList(),
                            fileChanges = emptyList(),
                            selectedChangeId = null,
                            reviewOpen = false,
                            agentPhase = if (agentReady) AgentPhase.Generating else AgentPhase.Idle,
                            agentActionSummary = emptyList()
                        )
                    }

                val agentPermission = if (agentReady) {
                    withContext(Dispatchers.IO) {
                        agentEnvironment?.let {
                            com.yanyuan.toolbox.util.AgentPermissionStore.get(it.context).label
                        }
                    } ?: "不可用"
                } else "不可用"
                val terminalBackend = agentEnvironment?.let {
                    withContext(Dispatchers.IO) {
                        if (AgentTerminalStore.isEnabled(it.context)) AgentTerminalStore.backend(it.context) else null
                    }
                }
                val runtimeAvailable = terminalBackend == TerminalBackend.BUILTIN_RUNTIME && agentEnvironment?.let {
                    com.yanyuan.toolbox.runtime.ToolboxRuntimeInstaller.environmentIfReady(it.context) != null
                } == true
                val agentSys = if (agentReady) {
                    val terminalCapability = when {
                        terminalBackend == null -> "受控终端未启用；不要请求 exec，不要声称运行过终端命令。"
                        terminalBackend == TerminalBackend.BUILTIN_RUNTIME && !runtimeAvailable -> "当前Toolbox Runtime 基础环境未初始化或不可用；不要请求 exec，也不要声称运行过 shell/APT 命令。"
                        terminalBackend == TerminalBackend.BUILTIN_RUNTIME -> "受控终端后端为本应用Toolbox Runtime 基础环境。任意命令与 Toolbox 共用本应用 UID（不是 Android root），默认从独立 Home 启动；但显式路径仍可能访问应用私有数据。不得输出、搜索或读取 API 密钥、授权存储或 Agent 工作区内容。每条命令必须用 exec 的 command 字段完整给出，准确命令、工作目录和风险会显示给用户并逐条等待明确批准；未经批准不得执行。"
                        else -> "受控终端后端为 ${terminalBackend.label}。可用 exec 请求只允许 pwd/ls/grep/cat/head/tail/wc/file/stat/printf；禁止 shell 语法。每条 exec 都会先显示完整程序、参数和工作目录并等待用户单次明确批准，未批准绝不执行。"
                    }
                    val execInstruction = if (
                        terminalBackend != null && (terminalBackend != TerminalBackend.BUILTIN_RUNTIME || runtimeAvailable)
                    ) {
                        if (terminalBackend == TerminalBackend.BUILTIN_RUNTIME) {
                            "需要运行内置 Toolbox Runtime 命令时，输出 action=exec 的独立 agent JSON 块，字段为 command（完整 shell 命令）和 cwd；一次只请求一条命令，每条都要单独等待用户批准。默认从独立 Home 启动，但进程与应用共享 UID，显式路径仍可能访问应用私有文件；不要声称存在内核级隔离。当前基础版不含 APT/dpkg。"
                        } else {
                            "需要运行受控诊断命令时，输出 action=exec 的独立 agent JSON 块，字段为 program、args 数组和 cwd；一次只请求一个命令。"
                        }
                    } else {
                        "当前不可请求 exec；如果用户要求终端操作，说明当前终端未启用或不可用，不要声称已执行。"
                    }
                    com.yanyuan.toolbox.network.OpenAiChatMessage(
                        role = "system",
                        content = "你是具备文件工具的助手。需要读取或修改手机文件时，在回复中输出一个独立代码块：" +
                                "```agent\n{\"action\":\"list\",\"path\":\"工作区或绝对路径\"}\n```" +
                                "（action 可为 list/read/write，write 需带 content 字段），" +
                                "之后等待【工具执行结果】再继续回答；没有文件需求就不要输出该块。" +
                                "终端能力：$terminalCapability $execInstruction " +
                                "当前文件权限：$agentPermission。"
                    )
                } else null
                val req = listOfNotNull(agentSys) +
                        buildRequestMessages(srcMessages, retryInstr, deepEnabled, persona) + attachMsgs
                if (!authorizedAtStart) {
                    throw IllegalStateException("当前导入 API 平台未获授权，请在“接入AI”的 API 授权中开启")
                }

                // ★ 关键：callbackFlow 的代码体运行在"收集方"的协程上下文里。
                //   这里处在 viewModelScope.launch 中，也就是 Main。
                //   原来的写法会把「读 SSE 每一行 + 逐行 JsonParser 解析」全部放在主线程上，
                //   思考阶段往往一秒几百上千个小 chunk，主线程被彻底占满，
                //   于是不只是聊天区，整个 App 都卡。
                //   用 flowOn(Dispatchers.Default) 把网络读取与 JSON 解析挪到后台线程，
                //   只有 _uiState 更新（appendDelta）回到主线程。
                //   同时用 UNLIMITED 缓冲，避免 callbackFlow 默认的 64 容量在
                //   trySend 非挂起的情况下直接丢 token。
                // ★ Agent 循环：流式结束若含 ```agent``` 块 → 按权限执行文件工具，
                //   结果可见并回传模型继续（最多 3 轮）。
                var currentReq = req
                var agentRounds = 0
                var lastAuthCheckAt = 0L
                var streamedChars = 0
                while (true) {
                    // 只解析本轮新增的模型输出，避免上一轮已执行的 agent 块被重复执行。
                    val roundStartLength = _uiState.value.conversations
                        .firstOrNull { it.id == convId }?.messages
                        ?.firstOrNull { it.id == aiMsgId }?.content?.length ?: 0

                    // 授权和导入配置可能触发 SharedPreferences/Gson/密钥解密，统一放到后台线程。
                    val authorizedBeforeRound = if (provider != null) {
                        withContext(Dispatchers.IO) {
                            importedApiAuthorized(modelId)
                        }
                    } else true
                    if (!authorizedBeforeRound) {
                        throw IllegalStateException(
                            "当前导入 API 平台未获授权，请在“接入AI”的 API 授权中开启"
                        )
                    }
                    api.streamChat(modelId, currentReq, provider)
                        .buffer(Channel.UNLIMITED)
                        .flowOn(Dispatchers.Default)
                        .collect { delta ->
                            streamedChars += delta.length
                            if (streamedChars > 1_000_000) {
                                throw IllegalStateException("AI 回复过长，已停止生成")
                            }
                            // 保留流式期间的撤权检查，但限频并放到 IO，避免每个 token 阻塞 UI 线程。
                            val now = System.currentTimeMillis()
                            if (provider != null && now - lastAuthCheckAt >= 500L) {
                                lastAuthCheckAt = now
                                val stillAuthorized = withContext(Dispatchers.IO) {
                                    importedApiAuthorized(modelId)
                                }
                                if (!stillAuthorized) {
                                    throw IllegalStateException(
                                        "当前导入 API 平台授权已撤销，请重新授权后再发送"
                                    )
                                }
                            }
                            appendDelta(convId, aiMsgId, delta)
                        }

                    flushDeltaNow()
                    if (!agentEnabled) break
                    val environment = agentEnvironment
                    val agentAvailable = agentEnabled && environment != null && withContext(Dispatchers.IO) {
                        environment.isAvailable()
                    }
                    if (agentRounds >= 3 || !agentAvailable) break
                    val reply = _uiState.value.conversations
                        .firstOrNull { it.id == convId }?.messages
                        ?.firstOrNull { it.id == aiMsgId }?.content ?: break
                    val roundReply = reply.drop(roundStartLength)
                    val actions = withContext(Dispatchers.Default) {
                        com.yanyuan.toolbox.util.AgentTools.extractActions(roundReply)
                    }
                    if (actions.isEmpty() || !agentEnabled) break
                    val toolNodes = actions.take(8).map { action ->
                        val nodeId = UUID.randomUUID().toString()
                        conversationNodes.upsert(ConversationNode.ToolCall(
                            nodeId, nextNodeSeq++, System.currentTimeMillis(), nodeId,
                            action.action, sanitizeToolOutput(action.path), ToolCallState.PREPARING,
                            messageId = aiMsgId
                        ))
                        publishConversationNodes()
                        ToolCallUi(
                            id = nodeId,
                            action = action.action,
                            variant = when (action.action) {
                                "list" -> ToolVariant.LIST
                                "read" -> ToolVariant.READ
                                "write" -> ToolVariant.WRITE
                                else -> ToolVariant.OTHER
                            },
                            path = sanitizeToolOutput(action.path),
                            inputSummary = sanitizeToolOutput(action.path).take(180)
                        )
                    }
                    _uiState.update {
                        it.copy(
                            workspacePhase = WorkspaceRunPhase.RunningTool,
                            toolCalls = it.toolCalls + toolNodes,
                            agentPhase = AgentPhase.ExecutingTools,
                            agentActionSummary = actions.take(8).map { action ->
                                action.action.trim().lowercase(Locale.ROOT)
                            }
                        )
                    }
                    val authorizedBeforeTools = withContext(Dispatchers.IO) {
                        importedApiAuthorized(modelId)
                    }
                    if (!authorizedBeforeTools) {
                        throw IllegalStateException(
                            "当前导入 API 平台授权已撤销，请重新授权后再执行 Agent 工具"
                        )
                    }
                    if (!agentEnabled) break
                    agentRounds++
                    val usableEnvironment = environment
                        ?: throw IllegalStateException("当前 AI 页面未启用 Agent 环境")
                    val results = withContext(Dispatchers.IO) {
                        if (!agentEnabled || !com.yanyuan.toolbox.util.AgentFeatureStore.get(usableEnvironment.context)) {
                            throw CancellationException("Agent 已关闭")
                        }
                        if (!usableEnvironment.isAvailable()) {
                            throw IllegalStateException("当前导入 API 已不可用，已停止 Agent 工具执行")
                        }
                        buildString {
                            actions.take(8).forEachIndexed { index, a ->
                                if (!agentEnabled || !com.yanyuan.toolbox.util.AgentFeatureStore.get(usableEnvironment.context)) {
                                    return@forEachIndexed
                                }
                                val nodeId = toolNodes.getOrNull(index)?.id ?: return@forEachIndexed
                                _uiState.update { current ->
                                    current.copy(toolCalls = current.toolCalls.map { node ->
                                        if (node.id == nodeId) node.copy(state = ToolCallState.RUNNING) else node
                                    })
                                }
                                updateToolNode(nodeId, ToolCallState.RUNNING)
                                val perm = com.yanyuan.toolbox.util.AgentPermissionStore
                                    .get(usableEnvironment.context)
                                if (a.action == "write") {
                                    val preview = com.yanyuan.toolbox.util.AgentTools.previewWrite(
                                        usableEnvironment.context, a, perm
                                    )
                                    if (preview != null) {
                                        _uiState.update { current ->
                                            current.copy(
                                                workspacePhase = WorkspaceRunPhase.WaitingApproval,
                                                fileChanges = current.fileChanges + preview,
                                                selectedChangeId = preview.id,
                                                reviewOpen = true
                                            )
                                        }
                                    }
                                }
                                val result = com.yanyuan.toolbox.util.AgentTools.execute(
                                    environment = usableEnvironment,
                                    action = a,
                                    permission = perm,
                                    approveWrite = ::awaitAgentWrite,
                                    approveTerminal = ::awaitTerminalApproval
                                )
                                val terminalState = when {
                                    result.startsWith("拒绝") || result.startsWith("用户拒绝") ||
                                        result.contains("状态：denied", ignoreCase = true) -> ToolCallState.DENIED
                                    result.contains("状态：timeout", ignoreCase = true) || result.contains("超时") -> ToolCallState.ERROR
                                    result.startsWith("写入失败") || result.startsWith("错误") ||
                                        result.contains("状态：error", ignoreCase = true) -> ToolCallState.ERROR
                                    else -> ToolCallState.OK
                                }
                                _uiState.update { current ->
                                    current.copy(
                                        toolCalls = current.toolCalls.map { node ->
                                            if (node.id == nodeId) node.copy(
                                                state = terminalState,
                                                output = result,
                                                finishedAt = System.currentTimeMillis()
                                            ) else node
                                        },
                                        fileChanges = current.fileChanges.map { change ->
                                            if (change.path == a.path && a.action == "write" && result.startsWith("已写入")) {
                                                change.copy(state = FileChangeState.APPLIED)
                                            } else change
                                        }
                                    )
                                }
                                updateToolNode(nodeId, terminalState, output = sanitizeToolOutput(result),
                                    error = result.takeIf { terminalState == ToolCallState.ERROR }?.let(::sanitizeToolOutput))
                                append("${sanitizeToolOutput(result)}\n")
                            }
                        }.trimEnd()
                    }
                    _uiState.update {
                        it.copy(agentPhase = AgentPhase.WaitingForModel)
                    }
                    currentReq = currentReq + com.yanyuan.toolbox.network.OpenAiChatMessage(
                        role = "user",
                        content = "【工具执行结果】\n$results\n请基于结果继续完成任务；如仍需操作文件，可再输出 agent 块。"
                    )
                }
                markFinished(convId, aiMsgId, startAt)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        workspacePhase = if (agentEnabled && agentEnvironment != null) WorkspaceRunPhase.Completed else WorkspaceRunPhase.Idle,
                        agentPhase = if (agentEnabled && agentEnvironment != null) AgentPhase.Completed else AgentPhase.Idle,
                        agentActionSummary = emptyList()
                    )
                }
            } catch (_: CancellationException) {
                flushDeltaNow()
                markFinished(convId, aiMsgId, startAt)
                _uiState.update {
                    it.copy(
                        isSending = false,
                                agentPhase = AgentPhase.Idle,
                        agentActionSummary = emptyList()
                    )
                }
            } catch (e: Exception) {
                flushDeltaNow()
                markFinished(convId, aiMsgId, startAt)
                conversationNodes.upsert(ConversationNode.Error(
                    id = "error-$aiMsgId", seq = nextNodeSeq++, time = System.currentTimeMillis(),
                    message = if (agentEnvironment != null) safeAgentErrorMessage(e) else sanitizeToolOutput(e.message ?: "AI 请求失败"),
                    retryable = true, messageId = aiMsgId
                ))
                publishConversationNodes()
                _uiState.update {
                    it.copy(
                        isSending = false,
                        workspacePhase = if (agentEnabled && agentEnvironment != null) WorkspaceRunPhase.Failed(e.message ?: "任务失败") else WorkspaceRunPhase.Idle,
                        agentPhase = if (agentEnabled && agentEnvironment != null) AgentPhase.Failed else AgentPhase.Idle,
                        agentActionSummary = emptyList(),
                        errorMessage = if (agentEnvironment != null) {
                            safeAgentErrorMessage(e)
                        } else {
                            e.message ?: "AI 请求失败"
                        }
                    )
                }
            } finally {
                clearPendingWrite(false)
                sendJob = null
                persist(_uiState.value.conversations)
            }
        }
    }

    /**
     * ★ 节流版 delta 追加：
     * 多个 chunk 合并到 100ms 后一次性写入 state。
     * 10 次/秒的刷新率对文字流已经足够顺滑，
     * 相比 60ms（约 16 次/秒）可少 40% 的重组与测量开销。
     */
    private fun updateAssistantProjection(msgId: String, content: String, streaming: Boolean, durationMs: Long? = null) {
        val parsed = parseReasoning(content)
        val assistant = conversationNodes.snapshot().firstOrNull { it.id == msgId } as? ConversationNode.Assistant
        val seq = assistant?.seq ?: nextNodeSeq++
        conversationNodes.upsert(ConversationNode.Assistant(
            id = msgId, seq = seq, time = assistant?.time ?: System.currentTimeMillis(),
            content = parsed.first, streaming = streaming, durationMs = durationMs, messageId = msgId
        ))
        val reasoningId = "reasoning-$msgId"
        val reasoning = conversationNodes.snapshot().firstOrNull { it.id == reasoningId } as? ConversationNode.Reasoning
        if (parsed.second.isNotBlank()) {
            conversationNodes.upsert(ConversationNode.Reasoning(
                id = reasoningId, seq = reasoning?.seq ?: nextNodeSeq++,
                time = reasoning?.time ?: System.currentTimeMillis(), content = parsed.second,
                streaming = parsed.third, messageId = msgId
            ))
        } else if (reasoning != null) conversationNodes.remove(reasoningId)
        publishConversationNodes()
    }

    private fun parseReasoning(content: String): Triple<String, String, Boolean> {
        val open = content.indexOf("<think>")
        if (open < 0) return Triple(content.trim(), "", false)
        val close = content.indexOf("</think>", open + 7)
        if (close < 0) return Triple(content.substring(0, open).trim(), content.substring(open + 7), true)
        return Triple(
            (content.substring(0, open) + content.substring(close + 8)).trim(),
            content.substring(open + 7, close), false
        )
    }

    private fun appendDelta(convId: String, msgId: String, delta: String) {
        deltaBuffer.append(delta)
        lastFlushConvId = convId
        lastFlushMsgId = msgId

        if (deltaFlushJob?.isActive == true) return

        deltaFlushJob = viewModelScope.launch {
            delay(100)
            flushDeltaNow()
        }
    }

    /**
     * 立即把 buffer 内容刷到 state。
     */
    private fun flushDeltaNow() {
        val text = deltaBuffer.toString()
        deltaBuffer.clear()
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        if (text.isEmpty()) return

        val cid = lastFlushConvId ?: return
        val mid = lastFlushMsgId ?: return

        var projectedContent: String? = null
        _uiState.update { state ->
            val idx = state.conversations.indexOfFirst { it.id == cid }
            if (idx < 0) return@update state
            val target = state.conversations[idx]
            val updated = target.copy(
                messages = target.messages.map {
                    if (it.id == mid) it.copy(content = it.content + text) else it
                },
                updatedAt = System.currentTimeMillis()
            )
            projectedContent = updated.messages.firstOrNull { it.id == mid }?.content.orEmpty()
            // ★ 流式过程中只有"当前会话"在变，而它本来就在列表首位。
            //   原实现每次刷新都重建整个列表并 sortedByDescending，
            //   造成列表引用频繁变化、每个会话都要重新比较，白白增加主线程开销。
            //   仅当顺序确实需要变化时才重建 + 排序。
            val list = if (idx == 0) {
                state.conversations.toMutableList().also { it[0] = updated }
            } else {
                val l = state.conversations.toMutableList()
                l[idx] = updated
                l.sortedByDescending { it.updatedAt }
            }
            state.copy(conversations = list)
        }
        if (_uiState.value.activeConversationId == cid) {
            updateAssistantProjection(mid, projectedContent.orEmpty(), streaming = true)
        }
    }

    /**
     * ★ 完成流式：把 buffer 里剩余内容刷入，并标记 durationMs；
     * 一次性 syncBranches，把分支内容同步到最新。
     */
    private fun markFinished(convId: String, msgId: String, startAt: Long) {
        val leftover = deltaBuffer.toString()
        deltaBuffer.clear()
        deltaFlushJob?.cancel()
        deltaFlushJob = null

        val dur = (System.currentTimeMillis() - startAt).coerceAtLeast(0)
        var finishedContent: String? = null
        _uiState.update { state ->
            val list = state.conversations.map { c ->
                if (c.id != convId) c
                else c.copy(
                    messages = c.messages.map { m ->
                        if (m.id == msgId) {
                            m.copy(
                                content = if (leftover.isEmpty()) m.content else m.content + leftover,
                                durationMs = dur
                            )
                        } else m
                    },
                    updatedAt = System.currentTimeMillis()
                ).syncBranches().also { updated ->
                    finishedContent = updated.messages.firstOrNull { it.id == msgId }?.content
                }
            }.sortedByDescending { it.updatedAt }
            state.copy(conversations = list)
        }
        updateAssistantProjection(msgId, finishedContent.orEmpty(), streaming = false, durationMs = dur)
    }

    private fun safeAgentErrorMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("未获授权") || message.contains("授权已撤销") ->
                "Agent 已停止：当前 API 平台未授权或授权已撤销"
            message.contains("不可用") || message.contains("未启用") ->
                "Agent 不可用：请检查 API 启用状态、模型和地址"
            message.contains("路径") || message.contains("目录") || message.contains("文件") ||
                    message.contains("写入") || message.contains("读取") ->
                "Agent 文件操作未完成：路径或权限不满足要求"
            message.contains("过长") -> "Agent 回复过长，已停止生成"
            else -> "Agent 请求失败，请检查 API 配置后重试"
        }
    }

    private fun buildAttachmentMessages(
        attachments: List<com.yanyuan.toolbox.data.ChatAttachment>
    ): List<OpenAiChatMessage> = attachments.map { att ->
        val f = java.io.File(att.path)
        val ext = f.extension.lowercase()
        val isImage = ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        val b64 = if (isImage && att.size <= 3_000_000L) {
            runCatching {
                android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
            }.getOrNull()
        } else null
        if (b64 != null) {
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/png"
            }
            OpenAiChatMessage(
                role = "user",
                content = listOf(
                    mapOf("type" to "text", "text" to "【图片 ${att.name}】"),
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:$mime;base64,$b64")
                    )
                )
            )
        } else {
            val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
            val body = if (raw != null && raw.length <= 200_000) raw
            else "（二进制/超大文件，未内联：${(att.size / 1024).coerceAtLeast(1)}KB）"
            OpenAiChatMessage(role = "user", content = "【附件 ${att.name}】\n$body")
        }
    }

    private fun buildRequestMessages(
        msgs: List<AiChatMessage>,
        retryInstr: String?,
        deep: Boolean,
        persona: AiPersona?
    ): List<OpenAiChatMessage> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sys = buildString {
            append("当前日期是 $today。")
            if (deep) {
                append("请先用 <think> 标签包裹你的深度思考过程（问题拆解、逻辑推导、陷阱评估），然后在标签外输出给用户看的正式回答。")
            } else {
                append("请直接输出正式回答，不要输出内部推理过程或隐藏分析标记。")
            }
            append("涉及新闻、版本、价格、政策、人物职位等时间敏感信息时，只依据模型或服务商实际提供的可靠信息；无法确认时必须明确说明。")
            append("回答使用中文（除非用户用其他语言提问或要求外语回答）。")
        }
        val sysMsgs = mutableListOf(OpenAiChatMessage("system", sys))

        persona?.let { p ->
            val seg = p.toPromptSegment()
            if (seg.isNotBlank()) {
                sysMsgs += OpenAiChatMessage(
                    "system",
                    "以下是用户为你设定的角色人格，请在回答中遵循该设定（若与用户当前问题冲突，以用户问题为准）：\n$seg"
                )
            }
        }

        if (retryInstr != null) {
            sysMsgs += OpenAiChatMessage("system", retryInstr)
        }

        val hist = msgs.filter { it.content.isNotBlank() }
            .map { OpenAiChatMessage(it.role, it.content) }
        return sysMsgs + hist
    }

    private fun retryInstruction(mode: RetryMode) = when (mode) {
        RetryMode.Regenerate -> "请重新回答上一条用户请求。不要复述原回答，不要提及这是重试；直接给出新的正式回答。"
        RetryMode.Detailed -> "请重新回答上一条用户请求，并显著增加细节、步骤、依据和必要上下文。不要提及这是重试。"
        RetryMode.Concise -> "请重新回答上一条用户请求，并压缩为更精简的版本，只保留结论、关键步骤和必要注意事项。不要提及这是重试。"
    }

    private fun resolvePersona(convId: String): AiPersona? {
        val s = _uiState.value
        val c = s.conversations.firstOrNull { it.id == convId } ?: return null
        val pid = c.personaId.ifBlank { s.activePersonaId ?: "" }
        return if (pid.isBlank()) null else s.personas.firstOrNull { it.id == pid }
    }

    private fun newConversation() = AiChatConversation(
        id = UUID.randomUUID().toString(),
        title = "新对话",
        messages = emptyList(),
        updatedAt = System.currentTimeMillis()
    )

    private fun titleFor(c: AiChatConversation, text: String): String =
        if (c.messages.isNotEmpty() && c.title != "新对话") c.title
        else text.take(24).ifBlank { "新对话" }

    private fun upsert(list: List<AiChatConversation>, c: AiChatConversation): List<AiChatConversation> {
        val next = if (list.any { it.id == c.id }) list.map { if (it.id == c.id) c else it }
        else listOf(c) + list
        return next.sortedByDescending { it.updatedAt }
    }

    private fun AiChatConversation.syncBranches(): AiChatConversation {
        if (messages.none { it.branches.isNotEmpty() }) return this
        val synced = messages.mapIndexed { i, m ->
            if (m.role != "user" || m.branches.isEmpty()) m
            else {
                val ai = m.activeBranchIndex.coerceIn(m.branches.indices)
                val bs = m.branches.mapIndexed { bi, b ->
                    if (bi == ai) b.copy(
                        content = m.content,
                        followingMessages = messages.drop(i + 1)
                    ) else b
                }
                m.copy(branches = bs, activeBranchIndex = ai)
            }
        }
        return copy(messages = synced)
    }

    private fun persist(list: List<AiChatConversation>) {
        val snapshot = list.map { it.syncBranches() }.sortedByDescending { it.updatedAt }
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            chatStorage.set(snapshot)
        }
    }
}