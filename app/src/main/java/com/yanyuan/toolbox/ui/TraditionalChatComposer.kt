package com.yanyuan.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.yanyuan.toolbox.liquidGlassSurface
import com.yanyuan.toolbox.data.ChatAttachment
import com.yanyuan.toolbox.data.AiModelState
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AuthState
import com.yanyuan.toolbox.data.AuthStore

@Composable
private fun SmallComposerChip(
    label: String,
    selected: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val surfaceModifier = if (backdrop != null) {
        Modifier.liquidGlassSurface(
            backdrop = backdrop,
            shape = shape,
            pressProgress = 0f,
            filmAlpha = if (selected) 0.48f else 0.64f,
            filmColor = background
        )
    } else Modifier.background(background, shape)
    Text(
        label,
        Modifier.clip(shape)
            .then(surfaceModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 6.dp),
        fontSize = 13.sp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun TraditionalChatComposer(
    state: AiChatViewModel.AiChatUiState,
    showAgentSettings: Boolean = false,
    currentAgentEnabled: Boolean = false,
    onAgentClick: () -> Unit = {},
    showModelMenu: Boolean = false,
    proModelId: String = "",
    appContext: android.content.Context? = null,
    glassBackdrop: Backdrop? = null,
    deepThinkingEnabled: Boolean,
    onDeepThinkingClick: () -> Unit,
    showAttach: Boolean,
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
    val proStorage = remember(appContext) { appContext?.let { AiChatProStorage(it) } }
    val aiSettings = remember(AiModelState.version, appContext) { appContext?.let { AiModelStore.get(it) } }
    val auth = remember(AuthState.version, appContext) { appContext?.let { AuthStore.get(it) } }
    var temperature by remember { mutableFloatStateOf(proStorage?.getTemperature() ?: 0.7f) }
    val canSubmit = state.isSending || state.input.isNotBlank() || pendingAttachments.isNotEmpty()
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            SmallComposerChip("深度思考", deepThinkingEnabled, glassBackdrop, onDeepThinkingClick)
            if (showAgentSettings) SmallComposerChip("Agent", currentAgentEnabled, glassBackdrop, onAgentClick)
            if (showModelMenu && appContext != null) {
                IconButton(onClick = { modelMenuOpen = true }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.Settings, "模型", Modifier.size(17.dp))
                }
                DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                    val models = if (auth?.isImportedApiAuthorized(proModelId) == true) {
                        aiSettings?.modelsOf(proModelId)?.filter { !it.hidden && it.cleanModelId.isNotBlank() }.orEmpty()
                    } else emptyList()
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                proStorage?.setSelectedModel(proModelId, model.cleanModelId)
                                modelMenuOpen = false
                            }
                        )
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("温度 %.1f".format(temperature), fontSize = 12.sp)
                        Slider(value = temperature, onValueChange = { temperature = it },
                            onValueChangeFinished = { proStorage?.setTemperature(temperature) }, valueRange = 0f..2f)
                    }
                }
            }
        }
        pendingAttachments.forEach { attachment ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AttachFile, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(attachment.name, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text("×", fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp).clickable { onRemoveAttachment(attachment) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val inputShape = RoundedCornerShape(26.dp)
            val inputBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
            val inputSurface = if (glassBackdrop != null) {
                Modifier.liquidGlassSurface(
                    backdrop = glassBackdrop,
                    shape = inputShape,
                    pressProgress = if (focused) 0.12f else 0f,
                    filmAlpha = if (focused) 0.62f else 0.78f,
                    filmColor = inputBackground
                )
            } else Modifier.background(inputBackground, inputShape)
            BasicTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 120.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(inputShape)
                    .then(inputSurface)
                    .border(1.dp, if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), inputShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSubmit && !state.isSending) onSubmit() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (state.input.isBlank()) Text("输入消息…", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            if (showAttach) {
                IconButton(onClick = onAddAttachment, Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                    Icon(Icons.Default.AttachFile, "添加附件", Modifier.size(19.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(
                onClick = if (state.isSending) onStop else onSubmit,
                enabled = canSubmit,
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(if (state.isSending) Icons.Default.Stop else Icons.Default.Send,
                    if (state.isSending) "停止" else "发送", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
