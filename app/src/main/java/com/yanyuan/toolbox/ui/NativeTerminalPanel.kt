package com.yanyuan.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import com.yanyuan.toolbox.terminal.NativePtySession
import com.yanyuan.toolbox.terminal.TerminalUiSession
import com.yanyuan.toolbox.terminal.NativeUiSession
import com.yanyuan.toolbox.terminal.ShizukuUiSession
import com.yanyuan.toolbox.util.AgentTerminalStore
import com.yanyuan.toolbox.util.TerminalBackend
import com.yanyuan.toolbox.terminal.ShizukuPtySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_INPUT_CHARS = 2_048
private const val MAX_SCROLLBACK_CHARS = 128_000

@Composable
internal fun NativeTerminalPanel(cwd: String) {
    val context = LocalContext.current
    if (!AgentTerminalStore.isEnabled(context)) {
        Text("受控 PTY 终端未启用，请在 Agent 设置中打开。", Modifier.fillMaxWidth().padding(8.dp), fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var opened by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    val screen = remember { com.yanyuan.toolbox.terminal.TerminalScreenBuffer() }
    var input by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<TerminalUiSession?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row {
            Text("终端", fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (!opened) {
                Button(enabled = !opening, onClick = {
                    if (!opening) scope.launch {
                        opening = true
                        screen.clear()
                        output = ""
                        val attempt = runCatching {
                            withContext(Dispatchers.IO) {
                                when (AgentTerminalStore.backend(context)) {
                                    TerminalBackend.APP_SANDBOX -> NativePtySession.open("/system/bin/sh", listOf("-i"), cwd)?.let(::NativeUiSession)
                                    TerminalBackend.SHIZUKU -> ShizukuPtySession.open(context, cwd)?.let(::ShizukuUiSession)
                                    TerminalBackend.BUILTIN_RUNTIME -> {
                                        val environment = com.yanyuan.toolbox.runtime.ToolboxRuntimeInstaller.environmentIfReady(context)
                                        environment?.let {
                                            File(it.home, ".tmp").mkdirs()
                                            NativePtySession.open(
                                                it.shell.absolutePath,
                                                listOf("-i"),
                                                it.home.absolutePath,
                                                environment = it.variables
                                            )?.let(::NativeUiSession)
                                        }
                                    }
                                }
                            }
                        }
                        val next = attempt.getOrNull()
                        session = next
                        opened = next != null
                        opening = false
                        if (next == null) {
                            output = "无法打开 PTY 终端\\n${attempt.exceptionOrNull()?.let { ": ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }.orEmpty()}"
                        }
                    }
                }) { Text(if (opening) "正在打开…" else "打开") }
            } else {
                OutlinedButton(onClick = {
                    val current = session
                    session = null
                    opened = false
                    output += "\n[终端已关闭]\n"
                    scope.launch(Dispatchers.IO) { current?.close() }
                }) { Text("关闭") }
            }
        }
        if (opened) {
            Text("${AgentTerminalStore.backend(context).label} PTY · 工作目录：$cwd", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                output.ifBlank { "[终端已连接，等待输出]" },
                Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp)
                    .background(Color.Black, RoundedCornerShape(10.dp))
                    .verticalScroll(scroll).padding(10.dp),
                color = Color(0xFFB8F5C0), fontFamily = FontFamily.Monospace, fontSize = 11.sp
            )
            Row {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(MAX_INPUT_CHARS) },
                    Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(9.dp),
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    val text = input
                    if (text.length <= MAX_INPUT_CHARS && '\u0000' !in text && '\n' !in text && '\r' !in text) {
                        scope.launch { session?.writeText(text + "\n") }
                        input = ""
                    } else {
                        output += "\n[输入包含无效控制字符]\n"
                        input = ""
                    }
                }) { Text("执行") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { scope.launch { session?.interrupt() } }) { Text("中断") }
            }
        }
    }

    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            while (isActive && session === current) {
                val chunk = current.read(8192)?.toString(Charsets.UTF_8)
                if (!chunk.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        screen.append(chunk)
                        output = screen.text().takeLast(MAX_SCROLLBACK_CHARS)
                    }
                } else {
                    delay(30)
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val current = session
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { current?.close() }
        }
    }
}

