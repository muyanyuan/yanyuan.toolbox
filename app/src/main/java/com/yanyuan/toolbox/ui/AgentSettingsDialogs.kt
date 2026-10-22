package com.yanyuan.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanyuan.toolbox.util.AgentPermission
import com.yanyuan.toolbox.util.AgentPermissionState
import com.yanyuan.toolbox.util.AgentPermissionStore
import com.yanyuan.toolbox.util.AgentWorkspaceStore
import com.yanyuan.toolbox.util.AgentTerminalStore
import com.yanyuan.toolbox.util.TerminalBackend
import com.yanyuan.toolbox.runtime.RuntimeInstallResult
import com.yanyuan.toolbox.runtime.ToolboxRuntimeInstaller
@Composable
fun AgentSettingsDialog(
    agentEnvironmentAvailable: Boolean,
    shizukuFileBackendReady: Boolean,
    agentEnabled: Boolean,
    onAgentEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onChooseWorkspace: () -> Unit,
    onClearWorkspace: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var runtimeMessage by remember { mutableStateOf<String?>(null) }
    val permissionVersion = AgentPermissionState.version
    val currentPermission = remember(permissionVersion) { AgentPermissionStore.get(context) }
    val workspace = remember(permissionVersion) { AgentWorkspaceStore.get(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (agentEnabled) {
                        if (agentEnvironmentAvailable) "Agent 环境可用"
                        else "Agent 环境不可用，请检查导入 API 授权、模型和地址"
                    } else {
                        "Agent 已关闭，仅保留普通聊天，不会调用文件工具"
                    },
                    fontSize = 12.sp,
                    color = if (agentEnabled && agentEnvironmentAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else if (!agentEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                TextButton(
                    onClick = { onAgentEnabledChange(!agentEnabled) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (agentEnabled) "关闭 Agent" else "开启 Agent")
                }
                Text("文件访问权限", fontSize = 13.sp)
                AgentPermission.entries.forEach { permission ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(
                                if (currentPermission == permission) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { AgentPermissionStore.set(context, permission) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(permission.label, fontSize = 14.sp)
                            Text(permission.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (currentPermission == permission) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Divider()
                Text("工作区范围", fontSize = 13.sp)
                Text(
                    workspace?.displayName ?: "应用专属工作区",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "工作区模式只能在此范围内修改文件；每次写入都会单独询问。选择外部目录需要授予持久访问权限。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onChooseWorkspace) { Text("选择手机目录") }
                    if (workspace != null) TextButton(onClick = onClearWorkspace) { Text("恢复默认") }
                }
                var terminalEnabled by remember(permissionVersion) { mutableStateOf(AgentTerminalStore.isEnabled(context)) }
                var terminalBackend by remember(permissionVersion) { mutableStateOf(AgentTerminalStore.backend(context)) }
                Divider()
                Text("受控终端", fontSize = 13.sp)
                Text(
                    if (terminalBackend == TerminalBackend.BUILTIN_RUNTIME) {
                        "Toolbox Runtime 提供 Bash + BusyBox 基础 shell。它与应用共享 UID（不是 Android root）；每条 Agent 命令都会显示完整内容并单独询问批准。此阶段不提供 APT/dpkg。"
                    } else {
                        "本地受控终端仅允许内置只读诊断程序，不支持 shell 语法或脚本；每条命令都要单独确认。"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用终端", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = terminalEnabled, onCheckedChange = {
                        terminalEnabled = it
                        AgentTerminalStore.setEnabled(context, it)
                    })
                }
                if (terminalEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalBackend.entries.forEach { backend ->
                            TextButton(onClick = {
                                terminalBackend = backend
                                AgentTerminalStore.setBackend(context, backend)
                            }) { Text(if (terminalBackend == backend) "✓ ${backend.label}" else backend.label) }
                        }
                    }
                    Text(
                        when (terminalBackend) {
                            TerminalBackend.SHIZUKU -> "Shizuku 只改变实际执行身份，不保证 root；adb/shell 仍受 Android 权限、SELinux 和 ROM 限制。"
                            TerminalBackend.APP_SANDBOX -> "应用沙箱后端只使用本应用 UID 的工作区权限。"
                            TerminalBackend.BUILTIN_RUNTIME -> {
                                val environment = ToolboxRuntimeInstaller.environmentIfReady(context)
                                if (environment == null) {
                                    "Toolbox Runtime 基础环境尚未初始化或已损坏；请先安装/修复 bootstrap。它运行在本应用 UID 下，不是 Android root。"
                                } else {
                                    "Toolbox Runtime 基础环境已就绪：Bash + BusyBox（${environment.version}）。此阶段不含 APT/dpkg；每条 Agent 命令仍需单独审批，不会扩大 Android 或 Shizuku 权限。"
                                }
                            }
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (terminalBackend == TerminalBackend.BUILTIN_RUNTIME) {
                        TextButton(onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { ToolboxRuntimeInstaller.installBundled(context) }
                                runtimeMessage = when (result) {
                                    is RuntimeInstallResult.Installed -> "内置 Toolbox Runtime 已安装：${result.version}"
                                    is RuntimeInstallResult.Rejected -> "安装失败：${result.reason}"
                                }
                                AgentPermissionState.notifyChanged()
                            }
                        }) {
                            Text("安装 / 修复内置 Toolbox Runtime")
                        }
                        runtimeMessage?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Text(
                    if (shizukuFileBackendReady) {
                        "完全访问后端：Shizuku 文件服务已连接。实际范围仍受 Shizuku 的 adb/root 身份、Android 权限、SELinux 和敏感目录过滤限制。"
                    } else {
                        "完全访问需要 Shizuku 文件服务连接；当前未连接时不会回退到普通应用权限。即使连接成功，仍受 adb/root 身份、Android 权限、SELinux 和敏感目录过滤限制。"
                    },
                    fontSize = 11.sp,
                    color = if (shizukuFileBackendReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
fun AgentWriteApprovalDialog(
    request: AiChatViewModel.AgentWriteApproval,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(if (request.existed) "确认覆盖文件" else "确认创建文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标：${request.path}", fontSize = 13.sp)
                Text("大小：${request.byteSize} 字节", fontSize = 12.sp)
                Text("内容预览：", fontSize = 12.sp)
                Text(
                    request.preview.ifBlank { "（空文件）" },
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
                Text("仅本次允许；Agent 后续再次修改文件仍会重新询问。", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onApprove) { Text("允许本次") } },
        dismissButton = { TextButton(onClick = onReject) { Text("拒绝") } }
    )
}
