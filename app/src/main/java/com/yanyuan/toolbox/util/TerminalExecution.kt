package com.yanyuan.toolbox.util

import android.content.Context
import com.yanyuan.toolbox.shizuku.ShizukuTerminalServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.UUID
import com.yanyuan.toolbox.runtime.ToolboxRuntimeExecutor

private const val TERMINAL_TIMEOUT_MS = 15_000L
private const val TERMINAL_OUTPUT_BYTES = 64 * 1024
private const val MAX_ARG_LENGTH = 256
private const val MAX_ARGS = 16
private const val MAX_TOTAL_ARGS = 2_048

interface TerminalExecutor {
    suspend fun execute(request: TerminalRequest): TerminalResult
    suspend fun cancel(id: String) {}
}

data class TerminalRequest(
    val id: String = UUID.randomUUID().toString(),
    val program: String,
    val args: List<String>,
    val cwd: File,
    val timeoutMs: Long = TERMINAL_TIMEOUT_MS,
    val maxOutputBytes: Int = TERMINAL_OUTPUT_BYTES,
    val runtimeWorkdir: String? = null,
    val shellCommand: String? = null,
    val runtimeVersion: String? = null
)

data class TerminalResult(
    val id: String,
    val status: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val truncated: Boolean
) {
    fun safeText(): String = buildString {
        append("状态：$status")
        exitCode?.let { append("，退出码：$it") }
        if (timedOut) append("，已超时")
        if (truncated) append("，输出已截断")
        if (stdout.isNotBlank()) append("\n标准输出：\n${redactTerminalText(stdout)}")
        if (stderr.isNotBlank()) append("\n标准错误：\n${redactTerminalText(stderr)}")
    }.take(128 * 1024)

    private fun redactTerminalText(text: String): String {
        val assignment = Regex(
            "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|bearer|client[_-]?secret|password|secret[_-]?key)\\s*[:=]\\s*([\\\"']?)[^\\s,;\\\"']+"
        )
        val knownKey = Regex("(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{12,}|AIza[0-9A-Za-z_-]{20,})\\b")
        return text.lineSequence().joinToString("\\n") { line ->
            knownKey.replace(assignment.replace(line) { m -> "${m.groupValues[1]}=[已隐藏]" }, "[已隐藏密钥]")
        }
    }
}

object TerminalPolicy {
    // 只保留不会启动子进程或修改文件的基础诊断程序；find/sed 的参数语义过于宽泛。
    private val allowedPrograms = setOf("pwd", "ls", "grep", "cat", "head", "tail", "wc", "file", "stat", "printf")
    private val forbiddenChars = Regex("[;|&><$()\\n\\r]")

    fun validate(program: String, args: List<String>, cwd: File): String? {
        if (program !in allowedPrograms) return "终端不允许执行该程序"
        if (program.contains(File.separator) || program.contains('\\')) return "终端程序必须使用白名单名称"
        if (args.size > MAX_ARGS || args.sumOf { it.length } > MAX_TOTAL_ARGS) return "终端参数过多"
        if (args.any { arg ->
                    arg.length > MAX_ARG_LENGTH || forbiddenChars.containsMatchIn(arg) ||
                    File(arg).isAbsolute || arg.startsWith("/") || arg.startsWith("\\") || arg.contains("..")
            }) {
            return "终端参数包含禁止的 shell 语法或越界路径"
        }
        if (!cwd.isDirectory) return "终端工作目录不可用"
        return null
    }

    fun isAllowed(program: String) = program in allowedPrograms
}

class AppTerminalExecutor : TerminalExecutor {
    private val running = java.util.concurrent.ConcurrentHashMap<String, Process>()

    override suspend fun execute(request: TerminalRequest): TerminalResult = withContext(Dispatchers.IO) {
        TerminalPolicy.validate(request.program, request.args, request.cwd)?.let {
            return@withContext TerminalResult(request.id, "denied", "", it, null, false, false)
        }
        val process = try {
            ProcessBuilder(listOf(request.program) + request.args)
                .directory(request.cwd)
                .redirectErrorStream(false)
                .start()
        } catch (_: Exception) {
            return@withContext TerminalResult(request.id, "error", "", "无法启动受控终端程序", null, false, false)
        }
        running[request.id] = process
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread { readLimited(process.inputStream, stdout, request.maxOutputBytes) }
        val errThread = Thread { readLimited(process.errorStream, stderr, request.maxOutputBytes) }
        outThread.start(); errThread.start()
        val completed = withTimeoutOrNull(request.timeoutMs) { process.waitFor(); true } ?: false
        if (!completed) process.destroyForcibly()
        running.remove(request.id)
        outThread.join(500); errThread.join(500)
        TerminalResult(request.id, if (completed && process.exitValue() == 0) "ok" else if (!completed) "timeout" else "error",
            stdout.toString(), stderr.toString(), if (completed) process.exitValue() else null,
            !completed, stdout.length + stderr.length >= request.maxOutputBytes)
    }

    override suspend fun cancel(id: String) {
        running.remove(id)?.destroyForcibly()
    }

    private fun readLimited(input: java.io.InputStream, output: StringBuilder, limit: Int) {
        input.use { stream ->
            val buffer = ByteArray(4096)
            var total = 0
            while (total < limit) {
                val count = runCatching { stream.read(buffer, 0, minOf(buffer.size, limit - total)) }.getOrDefault(-1)
                if (count <= 0) break
                output.append(String(buffer, 0, count, Charsets.UTF_8))
                total += count
            }
        }
    }
}

class ShizukuTerminalExecutor(private val context: Context) : TerminalExecutor {
    override suspend fun execute(request: TerminalRequest): TerminalResult =
        ShizukuTerminalServiceManager.execute(context, request)

    override suspend fun cancel(id: String) {
        ShizukuTerminalServiceManager.cancel(context, id)
    }
}

suspend fun executeControlledTerminal(context: Context, request: TerminalRequest): TerminalResult {
    if (!AgentTerminalStore.isEnabled(context)) {
        return TerminalResult(request.id, "denied", "", "受控终端未启用", null, false, false)
    }
    return when (AgentTerminalStore.backend(context)) {
        TerminalBackend.APP_SANDBOX -> AppTerminalExecutor().execute(request)
        TerminalBackend.SHIZUKU -> ShizukuTerminalExecutor(context).execute(request)
        TerminalBackend.BUILTIN_RUNTIME -> ToolboxRuntimeExecutor.execute(context, request)
    }
}
