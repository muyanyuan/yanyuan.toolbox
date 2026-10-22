package com.yanyuan.toolbox.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Slider
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AuthState
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.ChatAttachment

/**
 * 导入模型专用 AI 页（与旧 AI 页互不影响）：
 * 独立会话、深度思考、附件上传（文本内联给模型）、流式输出。
 * 第 3 轮将把消息渲染升级为 Markdown + KaTeX（式句解码）。
 */
@Composable
fun AiChatProScreen(
    viewModel: AiChatProViewModel,
    statusBarTop: Dp = 0.dp,
    navLeftInset: Dp = 0.dp,
    modelId: String = ""
) {
    val state by viewModel.state.collectAsState()
    // 切换 AI 大模型 → 切换到该模型自己的对话记录与内容。
    LaunchedEffect(modelId) { viewModel.reloadForCurrentModel() }
    val active = state.conversations.firstOrNull { it.id == state.activeId }
    val messages = active?.messages ?: emptyList()
    val keyboard = LocalSoftwareKeyboardController.current

    // ===== 附件 =====
    val appContext = LocalContext.current
    var pendingAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = appContext.contentResolver
        val name = runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        }.getOrNull() ?: "file_${System.currentTimeMillis()}"
        val dir = java.io.File(appContext.cacheDir, "chat_files").apply { mkdirs() }
        val dest = java.io.File(dir, "${System.currentTimeMillis()}_$name")
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
        }
        if (dest.exists()) {
            pendingAttachments = pendingAttachments +
                    ChatAttachment(name = name, path = dest.absolutePath, size = dest.length())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = statusBarTop)
            .padding(start = navLeftInset)
    ) {
        // ===== 顶栏 =====
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Psychology, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("AI 对话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // 独立对话记录列表
            var historyOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { historyOpen = true }) {
                Icon(Icons.Default.Menu, "对话记录", tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = historyOpen, onDismissRequest = { historyOpen = false }) {
                state.conversations.forEach { conv ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                conv.title.ifBlank { "新对话" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (conv.id == state.activeId) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            viewModel.switchTo(conv.id)
                            historyOpen = false
                        }
                    )
                }
            }
            IconButton(onClick = { viewModel.newConversation() }) {
                Icon(Icons.Default.Add, "新对话", tint = MaterialTheme.colorScheme.onSurface)
            }
            // 选择 API 下的模型 + 强度
            val proStorage = remember { AiChatProStorage(appContext) }
            val aiSettings = remember { AiModelStore.get(appContext) }
            var temperature by remember { mutableFloatStateOf(proStorage.getTemperature()) }
            var modelMenuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { modelMenuOpen = true }) {
                Icon(Icons.Default.Settings, "模型与强度", tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                val authVersion = AuthState.version
                val auth = remember(authVersion) { AuthStore.get(appContext) }
                val authorizedModels = if (auth.isImportedApiAuthorized(modelId)) {
                    val visible = aiSettings.modelsOf(modelId).filter {
                        !it.hidden && it.cleanModelId.isNotBlank()
                    }
                    visible
                } else emptyList()
                authorizedModels.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                m.displayName,
                                fontWeight = if (proStorage.getSelectedModel(modelId) == m.cleanModelId)
                                    FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            proStorage.setSelectedModel(modelId, m.cleanModelId)
                            modelMenuOpen = false
                            viewModel.reloadForCurrentModel()
                        }
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "强度（温度）：%.1f".format(temperature),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        onValueChangeFinished = { proStorage.setTemperature(temperature) },
                        valueRange = 0f..2f
                    )
                }
            }
        }

        // ===== 消息列表 =====
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // 流式输出时自动跟随最新内容（滚到底部）。
        LaunchedEffect(messages.size, active?.messages?.lastOrNull()?.content?.length) {
            if (messages.isNotEmpty()) {
                try {
                    listState.animateScrollToItem(messages.size - 1)
                } catch (_: Exception) {
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                ProEmptyState(Modifier.fillMaxSize())
            } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    // 附件条
                    if (msg.attachments.isNotEmpty()) {
                        msg.attachments.forEach { att ->
                            Row(
                                Modifier
                                    .padding(bottom = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AttachFile, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(att.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Download, "下载文件",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { saveAttachmentToDownloads(appContext, att) }
                                )
                            }
                        }
                    }
                    if (msg.content.isNotBlank()) {
                        Box(
                            Modifier
                                .widthIn(max = 340.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (isUser) {
                                Text(msg.content, fontSize = 15.sp, lineHeight = 22.sp)
                            } else {
                                // 式句解码：AI 回复用 Markdown + KaTeX 渲染（公式直接显示）。
                                MathMarkdownText(msg.content)
                            }
                        }
                    }
                    if (msg.role == "assistant" && state.isSending && msg.content.isBlank()) {
                        Text("思考中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            }
        }
        }

        state.errorMessage?.let {
            Text(
                it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ===== 待发送附件 =====
        pendingAttachments.forEach { att ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AttachFile, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(att.name, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    Icons.Default.Close, "移除",
                    modifier = Modifier.size(16.dp).clickable {
                        pendingAttachments = pendingAttachments.filterNot { it.path == att.path }
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ===== 输入区 =====
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 深度思考开关
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (state.deepThinking) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .clickable { viewModel.changeDeepThinking(!state.deepThinking) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Psychology, null,
                    tint = if (state.deepThinking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "深度思考",
                    fontSize = 12.sp,
                    color = if (state.deepThinking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (state.input.isEmpty()) {
                    Text("输入消息…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = state.input,
                    onValueChange = { viewModel.changeInput(it) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 4
                )
            }
            IconButton(
                onClick = { attachLauncher.launch("*/*") },
                // 与发送按钮同风格：52dp 圆形。
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.AttachFile, "添加文件", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = {
                    if (!state.isSending) {
                        viewModel.send(pendingAttachments)
                        pendingAttachments = emptyList()
                        keyboard?.hide()
                    }
                },
                enabled = state.isSending || state.input.isNotBlank() || pendingAttachments.isNotEmpty()
            ) {
                Icon(Icons.Default.Send, "发送", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 新 AI 页欢迎页：与旧 AI 页统一风格 —— 左对齐、大图标、大标题 + 一行说明。
 */
@Composable
private fun ProEmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Icon(
            Icons.Default.AutoAwesome, null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("开始一段 AI 对话", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "支持深度思考、公式渲染、上传文件",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 附件下载：保存到 下载/YanyuanToolbox/files/（任意格式）。 */
private fun saveAttachmentToDownloads(context: Context, att: ChatAttachment): Boolean = try {
    val src = java.io.File(att.path)
    if (!src.exists()) false else {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, att.name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/YanyuanToolbox/files")
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        )
        if (uri == null) false else {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(src).use { it.copyTo(out) }
            }
            true
        }
    }
} catch (_: Exception) {
    false
}
