package com.yanyuan.toolbox.data

import androidx.compose.runtime.Immutable
import java.util.UUID

data class AiPersona(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val profession: String = "",
    val age: String = "",
    val bio: String = "",
    val outputFormat: String = "",
    val systemPromptExtra: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isEmpty: Boolean
        get() = name.isBlank() && profession.isBlank() && age.isBlank() &&
                bio.isBlank() && outputFormat.isBlank() && systemPromptExtra.isBlank()

    fun toPromptSegment(): String {
        if (isEmpty) return ""
        val p = mutableListOf<String>()
        if (name.isNotBlank()) p += "你的名字是「$name」"
        if (profession.isNotBlank()) p += "你的职业/身份是：$profession"
        if (age.isNotBlank()) p += "你的年龄设定：$age"
        if (bio.isNotBlank()) p += "你的性格与背景：$bio"
        if (outputFormat.isNotBlank()) p += "输出格式要求：$outputFormat"
        if (systemPromptExtra.isNotBlank()) p += systemPromptExtra.trim()
        return p.joinToString("\n").trim()
    }
}

enum class AiChatModel(val id: String, val label: String, val subtitle: String) {
    UnlimitedAi("unlimitedai", "UnlimitedAI.Chat", "默认模型");

    companion object {
        fun fromId(id: String?): AiChatModel =
            entries.firstOrNull { it.id == id } ?: UnlimitedAi
    }
}

/**
 * ★ @Immutable：告诉 Compose 编译器这些模型是不可变的。
 * 流式输出时每个 chunk 都会替换整个消息列表，
 * 标注后未变化的气泡（内容相同的消息、其它会话）才能被跳过重组，
 * 否则只能逐个重新组合并重新解析 Markdown。
 */
@Immutable
data class ChatAttachment(
    val name: String = "",
    val path: String = "",
    val size: Long = 0L
)

@Immutable
data class AiChatMessage(
    val id: String = "",
    val role: String = "",
    val content: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long? = null,
    val branches: List<AiChatMessageBranch> = emptyList(),
    val activeBranchIndex: Int = 0
)

@Immutable
data class AiChatMessageBranch(
    val content: String = "",
    val followingMessages: List<AiChatMessage> = emptyList()
)

@Immutable
data class AiChatConversation(
    val id: String = "",
    val title: String = "新对话",
    val messages: List<AiChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val personaId: String = ""
)