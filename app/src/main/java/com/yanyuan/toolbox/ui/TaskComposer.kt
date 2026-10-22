package com.yanyuan.toolbox.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.yanyuan.toolbox.data.AiModelState
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AuthState
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.ChatAttachment
import com.yanyuan.toolbox.liquidGlassSurface

@Composable
internal fun TaskComposerCard(
    state: AiChatViewModel.AiChatUiState,
    isTablet: Boolean,
    showAttach: Boolean,
    showAgentSettings: Boolean,
    currentAgentEnabled: Boolean,
    onAgentClick: () -> Unit,
    onDeepThinkingClick: () -> Unit,
    showModelMenu: Boolean,
    proModelId: String,
    appContext: Context,
    glassBackdrop: Backdrop?,
    pendingAttachments: List<ChatAttachment>,
    onRemoveAttachment: (ChatAttachment) -> Unit,
    onAddAttachment: () -> Unit,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val proStorage = remember { AiChatProStorage(appContext) }
    val aiSettings = remember(AiModelState.version) { AiModelStore.get(appContext) }
    val auth = remember(AuthState.version) { AuthStore.get(appContext) }
    var temperature by remember { mutableFloatStateOf(proStorage.getTemperature()) }
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    val shape = RoundedCornerShape(if (isTablet) 24.dp else 20.dp)
    val canSubmit = state.isSending || state.input.isNotBlank() || pendingAttachments.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 24.dp else 12.dp, vertical = 8.dp)
            .clip(shape)
            .then(
                if (glassBackdrop != null) {
                    Modifier.liquidGlassSurface(
                        backdrop = glassBackdrop,
                        shape = shape,
                        pressProgress = 0f,
                        filmAlpha = 0.55f,
                        filmColor = MaterialTheme.colorScheme.surface
                    )
                } else Modifier.background(surface)
            )
            .border(
                1.dp,
                if (focused || state.input.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (pendingAttachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pendingAttachments.forEach { attachment ->
                    Row(
                        Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            attachment.name,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveAttachment(attachment) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.Close, "移除", Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        BasicTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp, max = 180.dp)
                .focusRequester(focusRequester)
                .then(Modifier.padding(horizontal = 4.dp))
                .onFocusChanged { focused = it.isFocused },
            textStyle = LocalTextStyle.current.copy(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            minLines = 3,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit && !state.isSending) onSubmit() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (state.input.isBlank()) {
                        Text(
                            if (showAgentSettings && currentAgentEnabled) "描述要完成的任务…"
                            else "输入任务或问题…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        )
                    }
                    inner()
                }
            }
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ComposerChip(
                label = "深度思考",
                icon = Icons.Default.Psychology,
                selected = state.deepThinkingEnabled,
                onClick = onDeepThinkingClick
            )
            if (showAgentSettings) {
                ComposerChip(
                    label = "Agent",
                    icon = Icons.Default.Settings,
                    selected = currentAgentEnabled,
                    onClick = onAgentClick
                )
            }
            if (showModelMenu) {
                IconButton(onClick = { modelMenuOpen = true }, Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, "模型", Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = modelMenuOpen,
                    onDismissRequest = { modelMenuOpen = false }
                ) {
                    val models = if (auth.isImportedApiAuthorized(proModelId)) {
                        aiSettings.modelsOf(proModelId).filter { !it.hidden && it.cleanModelId.isNotBlank() }
                    } else emptyList()
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName, fontWeight = if (proStorage.getSelectedModel(proModelId) == model.cleanModelId) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = {
                                proStorage.setSelectedModel(proModelId, model.cleanModelId)
                                modelMenuOpen = false
                            }
                        )
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("温度 %.1f".format(temperature), fontSize = 12.sp)
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            onValueChangeFinished = { proStorage.setTemperature(temperature) },
                            valueRange = 0f..2f
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (showAttach) {
                IconButton(
                    onClick = onAddAttachment,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.AttachFile, "添加附件", Modifier.size(18.dp))
                }
            }
            IconButton(
                onClick = if (state.isSending) onStop else onSubmit,
                enabled = canSubmit,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(
                    if (canSubmit) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    if (state.isSending) Icons.Default.Stop else Icons.Default.Send,
                    if (state.isSending) "停止任务" else "运行任务",
                    tint = if (canSubmit) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ComposerChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (selected) 0.45f else 0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = color, maxLines = 1)
    }
}
