package com.yanyuan.toolbox.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yanyuan.toolbox.util.AgentTools
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AgentWorkspacePanel(
    phase: WorkspaceRunPhase,
    toolCalls: List<ToolCallUi>,
    changes: List<FileChangeUi>,
    selectedChangeId: String?,
    onSelectChange: (String) -> Unit,
    pendingApproval: AiChatViewModel.AgentWriteApproval?,
    pendingTerminal: AiChatViewModel.TerminalApproval? = null,
    onReject: (() -> Unit)? = null,
    onTerminalReject: (() -> Unit)? = null,
    onTerminalApprove: (() -> Unit)? = null,
    onApprove: (() -> Unit)? = null
) {
    val inlineTerminal = pendingTerminal?.takeUnless { it.builtinRuntime }
    if (toolCalls.isEmpty() && changes.isEmpty() && pendingApproval == null && inlineTerminal == null && phase == WorkspaceRunPhase.Idle) return
    var expanded by remember(toolCalls.size, changes.size, pendingApproval?.id, pendingTerminal?.id) {
        mutableStateOf(pendingApproval != null || pendingTerminal != null)
    }
    val detailCount = toolCalls.size + changes.size
    val animatedCorner by animateDpAsState(
        targetValue = if (expanded) 14.dp else 50.dp,
        animationSpec = tween(220),
        label = "workspace-corner"
    )
    val capsuleLabel = when {
        pendingApproval != null || inlineTerminal != null -> "等待审批"
        phase == WorkspaceRunPhase.RunningTool -> "执行中"
        phase == WorkspaceRunPhase.Thinking -> "思考中"
        phase == WorkspaceRunPhase.Completed -> "已完成"
        phase is WorkspaceRunPhase.Failed -> "失败"
        else -> "工作区"
    }
    Card(
        Modifier.widthIn(min = 132.dp, max = if (expanded) 360.dp else 220.dp)
            .animateContentSize(animationSpec = tween(220))
            .padding(start = 12.dp, bottom = 6.dp),
        shape = RoundedCornerShape(animatedCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            Modifier.fillMaxWidth().border(
                1.dp,
                if (pendingApproval != null || pendingTerminal != null) MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(animatedCorner)
            ).padding(horizontal = 12.dp, vertical = if (expanded) 10.dp else 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(capsuleLabel, fontSize = 12.sp, maxLines = 1)
                if (detailCount > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text("$detailCount", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "收起工作区" else "展开工作区", Modifier.size(16.dp))
            }
            if (expanded) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (toolCalls.isNotEmpty()) {
                            Text("执行步骤 · ${toolCalls.size}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            toolCalls.forEach { ToolCallRow(it) }
                        }
                        if (changes.isNotEmpty()) {
                            Text("文件修改 · ${changes.size}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            changes.forEach { change ->
                                FileChangeRow(change, selectedChangeId == change.id) {
                                    onSelectChange(change.id)
                                }
                            }
                            selectedChangeId?.let { id ->
                                changes.firstOrNull { it.id == id }?.let { FileDiffPreview(it) }
                            }
                        }
                        if (pendingApproval != null && onApprove != null && onReject != null) {
                            ApprovalPanel(pendingApproval, onReject, onApprove)
                        }
                        if (inlineTerminal != null && onTerminalApprove != null && onTerminalReject != null) {
                            TerminalApprovalPanel(inlineTerminal, onTerminalReject, onTerminalApprove)
                        }
                        NativeTerminalPanel(AgentTools.workspaceDir(LocalContext.current).absolutePath)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallRow(node: ToolCallUi) {
    val (icon, tint) = when (node.state) {
        ToolCallState.OK -> Icons.Default.CheckCircle to Color(0xFF2E9B61)
        ToolCallState.ERROR, ToolCallState.DENIED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        ToolCallState.PREPARING, ToolCallState.RUNNING -> Icons.Default.Pending to MaterialTheme.colorScheme.primary
        ToolCallState.STOPPED -> Icons.Default.Error to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(node.action.uppercase(), fontSize = 11.sp)
            if (node.path.orEmpty().isNotBlank()) Text(node.path.orEmpty(), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (node.output.isNotBlank()) Text(node.output, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text(toolStateLabel(node.state), fontSize = 10.sp, color = tint)
    }
}

@Composable
private fun FileChangeRow(change: FileChangeUi, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Code, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(change.path.substringAfterLast('/'), fontSize = 11.sp)
            Text(change.path, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
        Text("+${change.addedLines} -${change.removedLines}", fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FileDiffPreview(change: FileChangeUi) {
    var expanded by remember(change.id) { mutableStateOf(false) }
    val oldLines = change.oldText?.lines().orEmpty()
    val newLines = change.newText.lines()
    val lines = if (expanded) newLines else newLines.take(9)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (change.existed) "修改预览" else "新建预览", fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "收起" else "展开", fontSize = 10.sp,
                modifier = Modifier.clickable { expanded = !expanded })
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, Modifier.size(16.dp))
        }
        Spacer(Modifier.height(5.dp))
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            if (change.existed && oldLines.isNotEmpty()) {
                oldLines.take(3).forEach { line ->
                    Text("- $line", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            lines.forEach { line ->
                Text("+ $line", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = Color(0xFF2E9B61))
            }
        }
    }
}

@Composable
private fun TerminalApprovalPanel(
    request: AiChatViewModel.TerminalApproval,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("等待终端命令确认", fontSize = 13.sp)
        Text(
            request.shellCommand ?: ("${request.program} " + request.args.joinToString(" ")),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        if (request.shellCommand != null) {
            Text(
                "Toolbox Runtime Shell 执行由用户逐条审批；这是完整命令，不是 Android root 授权。",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("工作目录：${request.cwd} · 超时 ${request.timeoutMs / 1000}s · 输出上限 ${request.maxOutputBytes / 1024}KB", fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onReject) { Text("拒绝") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApprove) { Text("仅允许一次") }
        }
    }
}

@Composable
private fun ApprovalPanel(
    request: AiChatViewModel.AgentWriteApproval,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("等待确认文件修改", fontSize = 13.sp)
        Text(request.path, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("${request.byteSize} 字节 · ${if (request.existed) "覆盖" else "创建"}", fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onReject) { Text("拒绝") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApprove) { Text("仅允许一次") }
        }
    }
}

private fun workspacePhaseLabel(phase: WorkspaceRunPhase): String = when (phase) {
    WorkspaceRunPhase.Idle -> "空闲"
    WorkspaceRunPhase.Thinking -> "思考中"
    WorkspaceRunPhase.RunningTool -> "执行工具"
    WorkspaceRunPhase.WaitingApproval -> "等待审批"
    WorkspaceRunPhase.ReviewingChanges -> "审查修改"
    WorkspaceRunPhase.ApplyingChanges -> "应用修改"
    WorkspaceRunPhase.Completed -> "已完成"
    is WorkspaceRunPhase.Failed -> "失败"
}

private fun toolStateLabel(state: ToolCallState): String = when (state) {
    ToolCallState.PREPARING -> "准备中"
    ToolCallState.RUNNING -> "执行中"
    ToolCallState.OK -> "完成"
    ToolCallState.ERROR -> "错误"
    ToolCallState.STOPPED -> "已停止"
    ToolCallState.DENIED -> "已拒绝"
}
