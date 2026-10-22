package com.yanyuan.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanyuan.toolbox.data.InstalledPlugin
import com.yanyuan.toolbox.util.SharedFileHandoffStore

@Composable
fun ShareHandoffScreen(
    handoff: SharedFileHandoffStore.Handoff,
    plugins: List<InstalledPlugin>,
    aiAvailable: Boolean,
    onUseAi: () -> Unit,
    onUsePlugin: (InstalledPlugin) -> Unit,
    onCancel: () -> Unit
) {
    var selectedPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("打开分享文件", style = MaterialTheme.typography.headlineSmall)
        Text(
            "请选择一个目标读取本次分享的文件。文件只会在本次操作中提供只读访问。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(handoff.files, key = { it.index }) { file ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.InsertDriveFile, null, Modifier.size(28.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(file.name, maxLines = 1)
                            Text("${file.mimeType} · ${file.size / 1024} KB", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!handoff.sharedText.isNullOrBlank()) {
                item {
                    Text("分享文字", fontSize = 13.sp)
                    Text(
                        handoff.sharedText,
                        Modifier.fillMaxWidth().background(
                            MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                        ).padding(10.dp),
                        maxLines = 8,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (aiAvailable) {
            Button(onClick = onUseAi, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.size(8.dp))
                Text("交给 AI")
            }
        } else {
            Text("当前没有可用的已授权导入 API，暂不能交给 AI。", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(
            onClick = { if (plugins.isNotEmpty()) selectedPlugin = plugins.firstOrNull() },
            Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Extension, null)
            Spacer(Modifier.size(8.dp))
            Text("选择插件（${plugins.size}）")
        }
        TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("取消") }
    }

    if (selectedPlugin != null) {
        AlertDialog(
            onDismissRequest = { selectedPlugin = null },
            title = { Text("选择插件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plugins.forEach { plugin ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedPlugin = plugin }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Extension, null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(plugin.manifest.name)
                                Text(plugin.manifest.description, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedPlugin?.let(onUsePlugin)
                    selectedPlugin = null
                }) { Text("允许只读") }
            },
            dismissButton = { TextButton(onClick = { selectedPlugin = null }) { Text("取消") } }
        )
    }
}
