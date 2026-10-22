package com.yanyuan.toolbox.ui

import com.yanyuan.toolbox.data.ChatAttachment

/** Android projection of Harness conversation nodes. Nodes are UI/event state, not an authorization boundary. */
sealed interface ConversationNode {
    val id: String
    val seq: Long
    val time: Long

    data class User(
        override val id: String,
        override val seq: Long,
        override val time: Long,
        val content: String,
        val attachments: List<ChatAttachment> = emptyList(),
        val submissionId: String? = null
    ) : ConversationNode

    data class Assistant(
        override val id: String,
        override val seq: Long,
        override val time: Long,
        val content: String,
        val streaming: Boolean = false,
        val durationMs: Long? = null,
        val messageId: String? = null
    ) : ConversationNode

    data class Reasoning(
        override val id: String,
        override val seq: Long,
        override val time: Long,
        val content: String,
        val streaming: Boolean = false,
        val expanded: Boolean = false,
        val messageId: String? = null
    ) : ConversationNode

    data class ToolCall(
        override val id: String,
        override val seq: Long,
        override val time: Long,
        val callId: String,
        val action: String,
        val args: String,
        val state: ToolCallState,
        val output: String = "",
        val error: String? = null,
        val parentCallId: String? = null,
        val messageId: String? = null
    ) : ConversationNode

    data class Approval(
        override val id: String,
        override val seq: Long,
        override val time: Long,
        val callId: String,
        val title: String,
        val detail: String,
        val answerable: Boolean = true,
        val approved: Boolean? = null,
        val outcome: String? = null,
        val messageId: String? = null
    ) : ConversationNode

    data class Error(
        override val id: String,
        override val seq: Long,
        override val time: Long,
        val message: String,
        val retryable: Boolean = true,
        val messageId: String? = null
    ) : ConversationNode
}

data class SubmissionSnapshot(
    val id: String,
    val timestamp: Long,
    val source: SubmissionSource,
    val mode: SubmissionMode,
    val modelId: String,
    val running: Boolean
)

enum class SubmissionSource { CLICK, ENTER, EXTERNAL_SHARE }
enum class SubmissionMode { PLAIN, QUEUE, STEER }

/** Deterministic append/replace reducer used by the UI projection. */
class ConversationNodeReducer {
    private val nodes = LinkedHashMap<String, ConversationNode>()

    @Synchronized
    fun upsert(node: ConversationNode): List<ConversationNode> {
        nodes[node.id] = node
        return nodes.values.sortedBy { it.seq }
    }

    @Synchronized
    fun remove(id: String): List<ConversationNode> {
        nodes.remove(id)
        return nodes.values.sortedBy { it.seq }
    }

    @Synchronized
    fun snapshot(): List<ConversationNode> = nodes.values.sortedBy { it.seq }

    @Synchronized
    fun clear() = nodes.clear()
}
