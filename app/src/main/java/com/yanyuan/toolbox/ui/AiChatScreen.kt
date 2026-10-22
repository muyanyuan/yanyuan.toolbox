@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.yanyuan.toolbox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yanyuan.toolbox.data.AiChatConversation
import com.kyant.backdrop.Backdrop
import com.yanyuan.toolbox.data.AiChatMessage
import com.yanyuan.toolbox.data.AiPersona
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AiModelState
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.AuthState
import com.yanyuan.toolbox.data.ChatAttachment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.yanyuan.toolbox.data.GlassOpacityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yanyuan.toolbox.liquidGlassSurface
import kotlin.math.roundToInt

/** 平板断点 */
private val AI_TABLET_BREAKPOINT = 600.dp

/** 平板左侧导航栏占用的总宽度（与 MainActivity 保持一致） */
private val AI_NAV_LEFT_INSET = 96.dp

@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    statusBarTop: Dp = 0.dp,
    softwareNavHeight: Dp = 0.dp,
    visible: Boolean = true,
    navLeftInset: Dp = AI_NAV_LEFT_INSET,
    // 液态玻璃模式下的玻璃采样（导航层记录）；非 null 时输入框与四个按钮走玻璃样式。
    glassBackdrop: Backdrop? = null,
    onDrawerOpenChange: (Boolean) -> Unit = {},
    apiMode: Boolean = false,
    showAgentSettings: Boolean = false,
    agentEnvironmentAvailable: Boolean = false,
    agentEnabled: Boolean = true,
    onAgentEnabledChange: (Boolean) -> Unit = {},
    aiShareHandoff: com.yanyuan.toolbox.util.SharedFileHandoffStore.Handoff? = null,
    onAiShareConsumed: () -> Unit = {},
    showAttach: Boolean = false,
    showModelMenu: Boolean = false,
    proModelId: String = "",
    agentOnly: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val messages = state.activeConversation?.messages ?: emptyList()

    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var drawerOpen by remember { mutableStateOf(false) }
    var personaSwitchOpen by remember { mutableStateOf(false) }
    var agentSettingsOpen by remember { mutableStateOf(false) }
    var currentAgentEnabled by remember(agentEnabled) { mutableStateOf(agentEnabled) }

    // ===== 附件（仅复刻页 showAttach 打开；发送时文本类内容内联给模型） =====
    val appContext = LocalContext.current
    var shizukuFileBackendReady by remember {
        mutableStateOf(com.yanyuan.toolbox.shizuku.ShizukuFileServiceManager.isReady())
    }
    LaunchedEffect(agentSettingsOpen) {
        if (agentSettingsOpen) {
            com.yanyuan.toolbox.shizuku.ShizukuFileServiceManager.ensureReady(appContext)
            shizukuFileBackendReady =
                com.yanyuan.toolbox.shizuku.ShizukuFileServiceManager.isReady()
        }
    }
    var pendingAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    LaunchedEffect(aiShareHandoff) {
        val handoff = aiShareHandoff ?: return@LaunchedEffect
        val copied = withContext(Dispatchers.IO) {
            com.yanyuan.toolbox.util.SharedFileHandoffStore.copyToChatFiles(appContext, handoff.token)
        }
        if (copied.isNotEmpty()) {
            pendingAttachments = (pendingAttachments + copied).distinctBy { it.path }
        }
        handoff.sharedText?.let { shared ->
            if (shared.isNotBlank()) viewModel.changeInput(shared)
        }
        onAiShareConsumed()
    }
    val workspaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { appContext.contentResolver.takePersistableUriPermission(uri, flags) }
        val name = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        }.getOrNull() ?: "已选择目录"
        val resolved = com.yanyuan.toolbox.util.AgentWorkspaceStore.resolveFile(appContext, uri)
        if (resolved != null) {
            com.yanyuan.toolbox.util.AgentWorkspaceStore.set(appContext, uri, name)
        } else {
            android.widget.Toast.makeText(
                appContext,
                "当前存储位置暂不支持，请选择手机内部共享存储目录",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
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

    val dismissComposerFocus: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    var followOutput by remember { mutableStateOf(true) }

    // ★ 离开 AI 页时释放输入框焦点与键盘
    //   刻意「不再」监听 WindowInsets.ime 来收焦点：只要键盘一收起就 clearFocus()，
    //   而平板长按选择文字时 IME 会短暂重新布局、imeVisible 抖动一次，
    //   于是触发 clearFocus → 键盘闪一下。
    //   现在收焦点只由明确的用户操作驱动：切页、点聊天区。
    LaunchedEffect(visible) {
        if (!visible) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // ★ 是否"贴在底部"：决定要不要自动跟随新内容。
    //   只要用户往上滑离开底部，就立刻停止自动滚动，不会再把他拽回去；
    //   滑回底部附近则恢复跟随。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    followOutput = total == 0 || lastVisible >= total - 2
                }
            }
    }

    // ★ 流式自动跟随（不打扰用户手动滚动）：
    //   之所以不用 scrollToItem(lastIndex)：那会把长气泡的顶部对齐到屏幕顶部，
    //   而新内容还在往下长，视觉上就像被强行拽回思考过程开头、怎么滑都被拉回去。
    //   这里改成"按内容增长量滚动"：算出底部内容长了多少像素，就滚多少像素，
    //   效果是新内容从下往上顶、平滑跟随，和 ChatGPT 的手感一致。
    //   用户一旦上滑离开底部，followOutput 变 false，这里完全不介入。
    //   dispatchRawDelta 是同步的原始滚动，不抢手势、不会和用户互相打架。
    val followOutputState = rememberUpdatedState(followOutput)
    LaunchedEffect(listState) {
        var lastContentHeight = -1
        var lastFollowAt = 0L
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null) -1 else last.offset + last.size + info.afterContentPadding
        }.collect { contentHeight ->
            if (contentHeight < 0) return@collect
            val delta = if (lastContentHeight < 0) 0 else contentHeight - lastContentHeight
            lastContentHeight = contentHeight
            if (delta <= 0) return@collect
            if (!followOutputState.value) return@collect
            val now = System.currentTimeMillis()
            if (now - lastFollowAt < 100L) return@collect
            lastFollowAt = now
            // dispatchRawDelta：同步的原始滚动，用户上手一拖就自然接管。
            try { listState.dispatchRawDelta(delta.toFloat()) } catch (_: Exception) {}
        }
    }

    // ★ 切换会话 / 新增消息时回到最新
    LaunchedEffect(messages.size, state.activeConversationId) {
        if (messages.isEmpty()) return@LaunchedEffect
        followOutput = true
        try { listState.scrollToItem(messages.lastIndex) } catch (_: Exception) {}
    }

    // ★ 打开/关闭对话记录栏时通知外层：
    //   平板端据此把左侧导航栏向左收回，避免导航栏压在侧栏上面
    LaunchedEffect(drawerOpen) {
        onDrawerOpenChange(drawerOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onDrawerOpenChange(false) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= AI_TABLET_BREAKPOINT
        // 平板端避让左侧导航栏；传入 0（例如侧栏打开、导航栏已收回）时不再避让
        val contentLeftInset = if (isTablet) navLeftInset else 0.dp

        // ★ 内容区底部避让量：用 maxOf 保证键盘收起动画平滑
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val navBarBottom = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()

        // 手机：键盘 / （软件导航栏 + 系统导航栏）取大
        // 平板：键盘 / 系统导航栏 取大
        val contentBottomInset = if (softwareNavHeight > 0.dp) {
            maxOf(imeBottom, softwareNavHeight + navBarBottom)
        } else {
            maxOf(imeBottom, navBarBottom)
        }

        Box(Modifier.fillMaxSize()) {

            // ==================== 主内容（避让键盘 + 导航栏） ====================
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = contentBottomInset)
            ) {
                Spacer(Modifier.height(statusBarTop))

                // ===== 顶部栏（贴左；平板随导航/侧栏让位 contentLeftInset，菜单按钮与内容对齐）=====
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 4.dp + contentLeftInset, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { dismissComposerFocus(); drawerOpen = true }) {
                        Icon(Icons.Default.Menu, "菜单", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        state.activeConversation?.title ?: "任务工作台",
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (!agentOnly) {
                        IconButton(onClick = { personaSwitchOpen = true }, modifier = Modifier.size(44.dp)) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person, "人格",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.createConversation() }) {
                        Icon(Icons.Default.Add, "新任务", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // ===== 剩余内容整体避让左侧导航栏 =====
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = contentLeftInset)
                ) {
                    // 消息列表
                    Box(Modifier.weight(1f)) {
                        if (messages.isEmpty()) {
                            if (agentOnly) {
                                AgentEmptyState(
                                    modifier = Modifier.fillMaxSize(),
                                    available = agentEnvironmentAvailable
                                )
                            } else {
                                // 左对齐欢迎页（大图标大字号）。
                                EmptyState(Modifier.fillMaxSize())
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    if (msg.attachments.isNotEmpty()) {
                                        // 对话中显示附件条（图片/文件名 + 下载）。
                                        msg.attachments.forEach { att ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = if (msg.role == "user") 0.dp else 56.dp,
                                                        bottom = 4.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (msg.role == "user") MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.AttachFile, null,
                                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(att.name, fontSize = 12.sp, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Icon(Icons.Default.Download, "下载文件",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp).clickable {
                                                        saveAttachmentToDownloads(appContext, att)
                                                    })
                                            }
                                        }
                                    }
                                    when {
                                        apiMode -> TaskTimelineBubble(
                                            msg = msg,
                                            nodes = state.conversationNodes.filter { node ->
                                                when (node) {
                                                    is ConversationNode.User -> node.id == msg.id
                                                    is ConversationNode.Assistant -> node.messageId == msg.id
                                                    is ConversationNode.Reasoning -> node.messageId == msg.id
                                                    is ConversationNode.ToolCall -> node.messageId == msg.id
                                                    is ConversationNode.Approval -> node.messageId == msg.id
                                                    is ConversationNode.Error -> node.messageId == msg.id
                                                }
                                            },
                                            isSending = state.isSending
                                        )
                                        else -> ChatBubble(msg, state.isSending, viewModel)
                                    }
                                }
                            }
                        }

                        // ★ 用户上滑查看历史时，给一个「回到最新」入口（生成中同样可用）
                        //   注意：AnimatedVisibility 只有 RowScope / ColumnScope 重载，
                        //   没有 BoxScope 版本，所以这里用 Column 承载淡入淡出。
                        if (messages.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.End
                            ) {
                                AnimatedVisibility(
                                    visible = !followOutput,
                                    enter = fadeIn(), exit = fadeOut()
                                ) {
                                    FloatingActionButton(
                                        onClick = { followOutput = true },
                                        modifier = Modifier.padding(16.dp).size(40.dp),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Icon(Icons.Default.ExpandMore, "回到最新",
                                            modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        if (apiMode && agentEnabled) {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                AgentWorkspacePanel(
                                    phase = state.workspacePhase,
                                    toolCalls = state.toolCalls,
                                    changes = state.fileChanges,
                                    selectedChangeId = state.selectedChangeId,
                                    onSelectChange = { viewModel.selectFileChange(it) },
                                    pendingApproval = state.pendingAgentWrite,
                                    pendingTerminal = state.pendingTerminal?.takeUnless { it.builtinRuntime },
                                    onReject = state.pendingAgentWrite?.let { { viewModel.rejectAgentWrite(it.id) } },
                                    onApprove = state.pendingAgentWrite?.let { { viewModel.approveAgentWrite(it.id) } },
                                    onTerminalReject = state.pendingTerminal?.takeUnless { it.builtinRuntime }?.let { { viewModel.rejectTerminal(it.id) } },
                                    onTerminalApprove = state.pendingTerminal?.takeUnless { it.builtinRuntime }?.let { { viewModel.approveTerminal(it.id) } }
                                )
                            }
                        }
                    }

                    state.errorMessage?.let { err ->
                        Text(
                            err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    TraditionalChatComposer(
                        state = state,
                        showAgentSettings = showAgentSettings,
                        currentAgentEnabled = currentAgentEnabled,
                        onAgentClick = { agentSettingsOpen = true },
                        showModelMenu = showModelMenu,
                        proModelId = proModelId,
                        appContext = appContext,
                        glassBackdrop = glassBackdrop,
                        deepThinkingEnabled = state.deepThinkingEnabled,
                        onDeepThinkingClick = { viewModel.changeDeepThinkingEnabled(!state.deepThinkingEnabled) },
                        showAttach = showAttach,
                        pendingAttachments = pendingAttachments,
                        onRemoveAttachment = { att ->
                            pendingAttachments = pendingAttachments.filterNot { it.path == att.path }
                        },
                        onAddAttachment = { attachLauncher.launch("*/*") },
                        onInputChange = viewModel::changeInput,
                        onSubmit = {
                            viewModel.send(if (showAttach) pendingAttachments else emptyList())
                            pendingAttachments = emptyList()
                        },
                        onStop = viewModel::stopGenerating
                    )
                }
            }

            // ==================== 遮罩（全屏，含系统导航栏） ====================
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200)),
                modifier = Modifier.zIndex(50f)
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { drawerOpen = false }
                )
            }

            // ==================== 侧栏（从屏幕最左侧滑出，覆盖系统导航栏） ====================
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(
                    tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it }
                ) + fadeIn(tween(200)),
                exit = slideOutHorizontally(
                    tween(240, easing = FastOutSlowInEasing),
                    targetOffsetX = { -it }
                ) + fadeOut(tween(200)),
                modifier = Modifier.zIndex(51f)
            ) {
                SessionDrawer(
                    sessions = state.conversations,
                    currentId = state.activeConversationId,
                    topPadding = statusBarTop,
                    onSelect = { viewModel.selectConversation(it); drawerOpen = false },
                    onDelete = { viewModel.deleteConversation(it) },
                    onNew = { viewModel.createConversation(); drawerOpen = false }
                )
            }
        }
    }

    // ==================== 对话框 ====================
    if (personaSwitchOpen) {
        PersonaSwitchDialog(
            personas = state.personas,
            activeId = state.activePersonaId,
            onDismiss = { personaSwitchOpen = false },
            onSelect = { id ->
                if (state.activeConversationId.isNotBlank()) {
                    viewModel.bindConversationPersona(state.activeConversationId, id)
                }
                viewModel.setActivePersona(id)
                personaSwitchOpen = false
            },
            onSave = { viewModel.savePersona(it) },
            onDelete = { viewModel.deletePersona(it) }
        )
    }

    if (agentSettingsOpen && showAgentSettings) {
        AgentSettingsDialog(
            agentEnvironmentAvailable = agentEnvironmentAvailable,
            shizukuFileBackendReady = shizukuFileBackendReady,
            agentEnabled = currentAgentEnabled,
            onAgentEnabledChange = { enabled ->
                currentAgentEnabled = enabled
                onAgentEnabledChange(enabled)
            },
            onDismiss = { agentSettingsOpen = false },
            onChooseWorkspace = { workspaceLauncher.launch(null) },
            onClearWorkspace = {
                com.yanyuan.toolbox.util.AgentWorkspaceStore.clear(appContext)
            }
        )
    }
    if (!apiMode) state.pendingAgentWrite?.let { request ->
        AgentWriteApprovalDialog(
            request = request,
            onReject = { viewModel.rejectAgentWrite(request.id) },
            onApprove = { viewModel.approveAgentWrite(request.id) }
        )
    }
    if (apiMode) state.pendingTerminal?.takeIf { it.builtinRuntime }?.let { request ->
        RuntimeCommandApprovalDialog(
            request = request,
            onReject = { viewModel.rejectTerminal(request.id) },
            onApprove = { viewModel.approveTerminal(request.id) }
        )
    }
}

// ========================================================
//  侧栏（底部避开系统导航栏）
// ========================================================
@Composable
private fun SessionDrawer(
    sessions: List<AiChatConversation>,
    currentId: String,
    topPadding: Dp,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val width = (screenWidth * 0.78f).coerceAtMost(320.dp)
    val glassAlpha = GlassOpacityState.alpha

    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    Box(
        Modifier.fillMaxHeight().width(width)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
            .padding(top = topPadding)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("对话记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onNew, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, "新对话")
                }
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(
                    top = 6.dp,
                    bottom = navBarBottom + 6.dp
                )
            ) {
                items(sessions, key = { it.id }) { session ->
                    val sel = session.id == currentId
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(session.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.title, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (sessions.size > 1) {
                            IconButton(onClick = { onDelete(session.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========================================================
//  以下辅助组件不变
// ========================================================
/**
 * AI 空白页（新对话引导）：简洁左对齐 —— 大图标 + 大标题 + 一行说明。
 */
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

@Composable
private fun AgentEmptyState(modifier: Modifier, available: Boolean) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("开始一个任务", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (available) "描述需要 AI 完成的任务，可按权限读取或修改文件"
            else "Agent 环境不可用，请检查 API 启用、授权、模型和地址",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text("仅导入 API 页面可用", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text("开始一段 AI 对话", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("可切换人格与深度思考", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TaskTimelineBubble(
    msg: AiChatMessage,
    nodes: List<ConversationNode>,
    isSending: Boolean
) {
    val parsed = remember(msg.content) {
        val think = parseThink(msg.content)
        think.copy(answer = think.answer.replace(
            Regex("```agent\\s*\\n.*?\\n```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), ""
        ).trim())
    }
    val config = LocalConfiguration.current
    val maxBubbleWidth = if (config.screenWidthDp >= 600) 560.dp else 340.dp
    val isUser = msg.role == "user"
    val reasoningNodes = nodes.filterIsInstance<ConversationNode.Reasoning>()
    val errorNodes = nodes.filterIsInstance<ConversationNode.Error>()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth(if (isUser) 0.78f else 1f)
                .widthIn(max = maxBubbleWidth),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        SelectionContainer {
                            Text(msg.content, fontSize = 14.sp, lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

            } else {
                Column(Modifier.fillMaxWidth()) {
                    reasoningNodes.forEach { node ->
                        HarnessReasoningRow(node)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (parsed.answer.isNotBlank()) {
                        SelectionContainer { MarkdownText(parsed.answer) }
                    } else if (msg.content.isBlank() || isSending) {
                        TypingDots()
                    }
                    errorNodes.sortedBy { it.seq }.forEach { node ->
                        Spacer(Modifier.height(6.dp))
                        Text(node.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (isUser) Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun HarnessReasoningRow(node: ConversationNode.Reasoning) {
    var expanded by remember(node.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.streaming) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            else Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.width(8.dp))
            Text(
                node.content.lineSequence().firstOrNull().orEmpty().ifBlank { "Reasoning" },
                Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        if (expanded) SelectionContainer { MarkdownText(node.content) }
    }
}

@Composable
private fun ChatBubble(msg: AiChatMessage, isSending: Boolean, vm: AiChatViewModel) {
    val isUser = msg.role == "user"
    val parsed = remember(msg.content) { parseThink(msg.content) }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editFocusRequester = remember { FocusRequester() }
    // ★ 用户消息直接在气泡内编辑
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(msg.content) }
    var showRetry by remember { mutableStateOf(false) }

    // 内容被外部改动（切换分支、重新回答）时同步草稿
    LaunchedEffect(msg.content) {
        if (!editing) draft = msg.content
    }

    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val maxBubbleWidth = if (screenWidthDp >= 600.dp) {
        minOf(screenWidthDp * 0.6f, 560.dp)
    } else {
        320.dp
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            Modifier.widthIn(max = maxBubbleWidth),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser && parsed.blocks.isNotEmpty()) {
                parsed.blocks.forEachIndexed { i, block ->
                    ThinkBlock(block, i == parsed.blocks.lastIndex && parsed.isThinking)
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (isUser) {
                if (editing) {
                    // ★ 直接在气泡里编辑（不再弹对话框），保留原气泡空间不跳动
                    Column(
                        Modifier
                            // 留出冗余空间：编辑时气泡宽度不缩水，避免文本域过窄
                            .widthIn(min = 160.dp, max = maxBubbleWidth)
                            .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
                            // 留出冗余空间，避免多行编辑时行数变化导致气泡乱跳
                            minLines = 1,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .focusRequester(editFocusRequester)
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    editing = false
                                    editFocusRequester.requestFocus()
                                    keyboard?.hide()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, "取消",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val t = draft.trim()
                                    editing = false
                                    keyboard?.hide()
                                    if (t.isNotBlank() && t != msg.content) {
                                        vm.editUserMessage(msg.id, t)
                                    }
                                },
                                enabled = draft.isNotBlank() && draft.trim() != msg.content,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Check, "重新回答",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(msg.content, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                UserMeta(
                    msg = msg,
                    enabled = !isSending,
                    onEdit = {
                        if (!isSending) {
                            draft = msg.content
                            editing = true
                            editFocusRequester.requestFocus()
                            keyboard?.show()
                        }
                    },
                    onCopy = {
                        if (msg.content.isNotBlank()) {
                            clipboard.setText(AnnotatedString(msg.content))
                        }
                    },
                    onSwitch = { i -> vm.switchUserBranch(msg.id, i) }
                )
            } else {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (msg.content.isEmpty()) TypingDots()
                    else if (parsed.answer.isNotBlank()) {
                        SelectionContainer { MarkdownText(parsed.answer) }
                    } else {
                        TypingDots()
                    }
                }
                AssistantMeta(
                    duration = msg.durationMs, isSending = isSending,
                    onCopy = {
                        if (parsed.answer.isNotBlank()) clipboard.setText(AnnotatedString(parsed.answer))
                    },
                    onRetry = { showRetry = true }
                )
            }
        }
        if (isUser) Spacer(Modifier.width(8.dp))
    }

    if (showRetry) {
        RetryDialog(
            onDismiss = { showRetry = false },
            onPick = { vm.retry(msg.id, it); showRetry = false }
        )
    }
}

@Composable
private fun UserMeta(
    msg: AiChatMessage,
    enabled: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSwitch: (Int) -> Unit
) {
    val count = msg.branches.size
    val cur = msg.activeBranchIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count > 0) {
            TextButton(enabled = enabled && cur > 0, onClick = { onSwitch(cur - 1) }) { Text("<") }
            Text("${cur + 1}/$count", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = enabled && cur < count - 1, onClick = { onSwitch(cur + 1) }) { Text(">") }
            Spacer(Modifier.width(4.dp))
        }
        TextButton(enabled = enabled, onClick = onEdit) {
            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("编辑", fontSize = 12.sp)
        }
        // ★ 发送的消息同样支持一键复制
        TextButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("复制", fontSize = 12.sp)
        }
    }
}

@Composable
private fun AssistantMeta(
    duration: Long?,
    isSending: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            duration?.let {
                "响应 ${if (it < 1000) "${it}ms"
                else String.format(java.util.Locale.getDefault(), "%.1fs", it / 1000.0)}"
            } ?: if (isSending) "正在响应" else "",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onRetry, enabled = !isSending, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Refresh, "重试", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ThinkBlock(content: String, isThinking: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val summary = remember(content) {
        content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(96)
            .orEmpty()
    }
    LaunchedEffect(isThinking) {
        if (!isThinking) expanded = false
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp))
            // ★ 思考中不要做内容尺寸动画：流式内容每次刷新都会改变高度，
            //   animateContentSize 会在每帧启动一次"高度补间动画"，
            //   导致整条气泡持续重新测量，是思考时卡顿的主因之一。
            //   思考结束后内容稳定，此时再启用动画是安全的。
            .then(if (isThinking) Modifier else Modifier.animateContentSize())
            .clickable { if (!isThinking) expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Psychology, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isThinking) "正在思考…" else "THINK",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isThinking && summary.isNotBlank()) {
                    Text(summary, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            if (isThinking) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(visible = expanded || isThinking,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(6.dp))
                // ★ 思考内容流式增长时避免 Markdown 逐行布局：
                //   逐行 Column 会产生 N 个布局节点并整体重新测量/布局，
                //   配合 animateContentSize 会每帧触发一次测量动画，是卡顿主因。
                //   流式期间用单个 Text 展示原文，结束后再交给 MarkdownText 渲染排版。
                if (isThinking) {
                    Text(content, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer { MarkdownText(content) }
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    // ★ 直接对 graphicsLayer.alpha 做动画：
    //   原实现用 rememberInfiniteTransition() + `by` 在组合阶段读取动画值，
    //   会让 TypingDots 每帧重组一次；思考中的气泡内容很长，
    //   于是每帧都要重新执行一遍该气泡的组合逻辑。
    //   改为把 alpha 写进 graphicsLayer 的 lambda（绘制阶段读取），
    //   动画帧只重绘、不重组。
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "typingAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        // 中间点与两侧点反相，保持原来的交替闪烁效果
                        this.alpha = if (i == 1) 1.3f - alpha else alpha
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String, icon: ImageVector, selected: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
    glassBackdrop: Backdrop? = null
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val bc = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val cc = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .height(36.dp)
            .then(
                if (glassBackdrop != null) {
                    // 液态玻璃芯片：折射导航层记录 + 表面色膜（文字保持主题高对比色，便于阅读）。
                    // 选中态仍靠主色描边 + 主色文字表达。
                    Modifier.liquidGlassSurface(
                        backdrop = glassBackdrop,
                        shape = RoundedCornerShape(18.dp),
                        pressProgress = 0f,
                        filmAlpha = 0.52f,
                        filmColor = MaterialTheme.colorScheme.surface
                    )
                } else {
                    Modifier.clip(RoundedCornerShape(18.dp)).background(bg)
                }
            )
            .border(1.dp, bc, RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = cc, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cc, maxLines = 1)
    }
}

@Composable
private fun RuntimeCommandApprovalDialog(
    request: AiChatViewModel.TerminalApproval,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val command = request.shellCommand ?: buildString {
        append(request.program)
        request.args.forEach { append(" ").append(quoteShellDisplayArg(it)) }
    }
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("允许这条 Toolbox Runtime 命令？") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "将在本应用 UID 下执行，不是 Android root。默认工作目录为独立 Toolbox Runtime Home，且不会把 API 密钥或 Agent 工作区内容注入环境；但任意命令与本应用共享 UID，若显式指定路径仍可能访问应用私有数据。命令可修改/删除数据、安装软件或联网。批准仅对此处显示的命令有效。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error
                )
                Text("工作目录：${request.cwd}", fontSize = 12.sp)
                Text("完整命令", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text("单次审批 · 最长等待 ${request.timeoutMs / 1000}s · 输出上限 ${request.maxOutputBytes / 1024}KB",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(command)) }) {
                    Icon(Icons.Default.ContentCopy, "复制命令", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制")
                }
                TextButton(onClick = onReject) { Text("拒绝") }
                TextButton(onClick = onApprove) { Text("仅执行此命令一次") }
            }
        }
    )
}

private fun quoteShellDisplayArg(value: String): String =
    if (value.isNotEmpty() && value.none { it.isWhitespace() || it in "'\\\"$`\\\\" }) value
    else "'${value.replace("'", "'\\\"'\\\"'")}'"

@Composable
private fun RetryDialog(onDismiss: () -> Unit, onPick: (AiChatViewModel.RetryMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重试") },
        text = {
            Column {
                RetryRow("重新回答", "重新生成当前回复") { onPick(AiChatViewModel.RetryMode.Regenerate) }
                RetryRow("更详细", "增加细节、步骤和上下文") { onPick(AiChatViewModel.RetryMode.Detailed) }
                RetryRow("更精简", "压缩为只保留重点的版本") { onPick(AiChatViewModel.RetryMode.Concise) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RetryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonaSwitchDialog(
    personas: List<AiPersona>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onSave: (AiPersona) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<AiPersona?>(null) }
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("人格面具", fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PersonaRow(
                    title = "默认（无人格）",
                    sub = "不注入任何人格设定",
                    selected = activeId.isNullOrBlank(),
                    onSelect = { onSelect(null) },
                    onEdit = null,
                    onDeleteClick = null
                )

                personas.forEach { p ->
                    PersonaRow(
                        title = p.name.ifBlank { "未命名人格" },
                        sub = listOfNotNull(
                            p.profession.takeIf { it.isNotBlank() },
                            p.bio.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        selected = p.id == activeId,
                        onSelect = { onSelect(p.id) },
                        onEdit = { editing = p },
                        onDeleteClick = { onDelete(p.id) }
                    )
                }

                if (personas.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("尚未创建任何人格面具，点右上角「新建」开始。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    if (creating) {
        PersonaEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { onSave(it); creating = false }
        )
    }
    editing?.let { p ->
        PersonaEditDialog(
            initial = p,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null }
        )
    }
}

@Composable
private fun PersonaRow(
    title: String,
    sub: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDeleteClick: (() -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Person, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "编辑",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "删除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PersonaEditDialog(
    initial: AiPersona?,
    onDismiss: () -> Unit,
    onSave: (AiPersona) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var profession by remember { mutableStateOf(initial?.profession ?: "") }
    var age by remember { mutableStateOf(initial?.age ?: "") }
    var bio by remember { mutableStateOf(initial?.bio ?: "") }
    var outputFormat by remember { mutableStateOf(initial?.outputFormat ?: "") }
    var systemPromptExtra by remember { mutableStateOf(initial?.systemPromptExtra ?: "") }

    val canSave = name.isNotBlank() || profession.isNotBlank() ||
            bio.isNotBlank() || systemPromptExtra.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建人格面具" else "编辑人格面具") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名字") }, singleLine = true,
                    placeholder = { Text("如：小明助手") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = profession, onValueChange = { profession = it },
                    label = { Text("职业 / 身份") }, singleLine = true,
                    placeholder = { Text("如：前端工程师") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = age, onValueChange = { age = it },
                    label = { Text("年龄") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio, onValueChange = { bio = it },
                    label = { Text("简介 / 性格") },
                    minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = outputFormat, onValueChange = { outputFormat = it },
                    label = { Text("输出格式偏好") }, singleLine = true,
                    placeholder = { Text("如：Markdown 列表、简短口语") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPromptExtra, onValueChange = { systemPromptExtra = it },
                    label = { Text("追加系统提示词（可选）") },
                    minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        (initial ?: AiPersona()).copy(
                            name = name.trim(),
                            profession = profession.trim(),
                            age = age.trim(),
                            bio = bio.trim(),
                            outputFormat = outputFormat.trim(),
                            systemPromptExtra = systemPromptExtra.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ========================================================
//  <think> 解析
// ========================================================
private const val THINK_OPEN = "<think>"
private const val THINK_CLOSE = "</think>"

private data class ParsedThink(
    val blocks: List<String>,
    val answer: String,
    val isThinking: Boolean
)

private fun parseThink(content: String): ParsedThink {
    val blocks = mutableListOf<String>()
    val answer = StringBuilder()
    var rem = content
    while (true) {
        val open = rem.indexOf(THINK_OPEN)
        if (open < 0) { answer.append(rem); break }
        val start = open + THINK_OPEN.length
        val close = rem.indexOf(THINK_CLOSE, start)
        if (close < 0) {
            answer.append(rem.substring(0, open))
            blocks.add(rem.substring(start))
            return ParsedThink(blocks, answer.toString().trim(), true)
        }
        answer.append(rem.substring(0, open))
        blocks.add(rem.substring(start, close))
        rem = rem.substring(close + THINK_CLOSE.length)
    }
    return ParsedThink(blocks, answer.toString().trim(), false)
}