package com.yanyuan.toolbox.runtime

import android.content.Context
import com.yanyuan.toolbox.util.TerminalRequest
import com.yanyuan.toolbox.util.TerminalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Executes shell commands inside this app's private Toolbox Runtime. */
object ToolboxRuntimeExecutor {
    private const val MAX_COMMAND_CHARS = 8_192
    private val running = ConcurrentHashMap<String, Process>()

    internal fun validateRequest(command: String?, workdir: String?): String? {
        if (command.isNullOrBlank() || command.length > MAX_COMMAND_CHARS || '\u0000' in command) {
            return "Toolbox Runtime 命令为空、包含非法字符或超过 ${MAX_COMMAND_CHARS} 字符"
        }
        if (workdir != null && (workdir.startsWith("/") || workdir.contains("..") || workdir.contains('\\'))) {
            return "Toolbox Runtime 工作目录不在私有 Home 范围内"
        }
        return null
    }

    suspend fun execute(context: Context, request: TerminalRequest): TerminalResult = withContext(Dispatchers.IO) {
        validateRequest(request.shellCommand, request.runtimeWorkdir)?.let {
            return@withContext TerminalResult(request.id, "denied", "", it, null, false, false)
        }
        val environment = ToolboxRuntimeInstaller.environmentIfReady(context)
            ?: return@withContext TerminalResult(request.id, "denied", "", "Toolbox Runtime尚未初始化", null, false, false)
        if (request.runtimeVersion != null && request.runtimeVersion != environment.version) {
            return@withContext TerminalResult(request.id, "denied", "", "Runtime 版本与审批快照不一致", null, false, false)
        }
        val command = request.shellCommand
            ?: return@withContext TerminalResult(request.id, "denied", "", "缺少 shell command", null, false, false)
        val workdir = if (request.runtimeWorkdir == null) {
            environment.home
        } else {
            resolveHomePath(environment.home, request.runtimeWorkdir)
                ?: return@withContext TerminalResult(request.id, "denied", "", "内置 Toolbox Runtime 工作目录越界", null, false, false)
        }
        if (!workdir.isDirectory) {
            return@withContext TerminalResult(request.id, "denied", "", "内置 Toolbox Runtime 工作目录不可用", null, false, false)
        }
        val process = try {
            ProcessBuilder(environment.shell.absolutePath, "-lc", command)
                .directory(workdir)
                .redirectErrorStream(false)
                .apply {
                    environment().clear()
                    environment().putAll(environment.variables)
                }
                .start()
        } catch (e: Exception) {
            return@withContext TerminalResult(request.id, "error", "", "无法启动内置 shell：${e.message.orEmpty()}", null, false, false)
        }
        running[request.id] = process
        val stdout = LimitedOutput(request.maxOutputBytes)
        val stderr = LimitedOutput(request.maxOutputBytes)
        val outThread = Thread { process.inputStream.use { stdout.readFrom(it) } }
        val errThread = Thread { process.errorStream.use { stderr.readFrom(it) } }
        outThread.start()
        errThread.start()
        val completed = withTimeoutOrNull(request.timeoutMs) { process.waitFor(); true } ?: false
        if (!completed) process.destroyForcibly()
        running.remove(request.id)
        outThread.join(750)
        errThread.join(750)
        val code = if (completed) process.exitValue() else null
        TerminalResult(
            id = request.id,
            status = when {
                !completed -> "timeout"
                code == 0 -> "ok"
                else -> "error"
            },
            stdout = stdout.text(),
            stderr = stderr.text(),
            exitCode = code,
            timedOut = !completed,
            truncated = stdout.truncated || stderr.truncated
        )
    }

    suspend fun cancel(id: String) {
        running.remove(id)?.destroyForcibly()
    }

    private fun resolveHomePath(home: File, value: String): File? {
        val normalized = value.removePrefix("~/").removePrefix("./")
        if (value == "~" || value == "." || value.isBlank()) return home
        if (value.startsWith("/") || normalized.contains("..") || normalized.contains('\\')) return null
        val candidate = File(home, normalized).canonicalFile
        return candidate.takeIf { it == home.canonicalFile || it.path.startsWith(home.canonicalPath + File.separator) }
    }

    private class LimitedOutput(private val limit: Int) {
        private val bytes = java.io.ByteArrayOutputStream()
        var truncated: Boolean = false
            private set

        fun readFrom(input: java.io.InputStream) {
            val buffer = ByteArray(4096)
            var remaining = limit.coerceAtLeast(1)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                val retained = minOf(count, remaining)
                if (retained > 0) {
                    bytes.write(buffer, 0, retained)
                    remaining -= retained
                }
                if (retained < count) truncated = true
            }
        }

        fun text(): String = bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
