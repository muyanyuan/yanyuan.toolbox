package com.yanyuan.toolbox.shizuku

import android.os.IBinder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.yanyuan.toolbox.terminal.NativePtySession
import com.yanyuan.toolbox.util.TerminalPolicy
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Shizuku 身份下的受控诊断终端：只接收白名单程序和参数数组，不启动 shell。 */
class ToolboxShizukuTerminalService : IShizukuTerminalService.Stub() {
    private val running = ConcurrentHashMap<String, Process>()
    private val sessions = ConcurrentHashMap<String, NativePtySession>()
    private val gson = Gson()

    override fun execute(
        executionId: String?,
        program: String?,
        args: Array<out String>?,
        cwd: String?,
        timeoutMs: Int,
        maxOutputBytes: Int
    ): String {
        val id = executionId?.takeIf { it.isNotBlank() } ?: return result("", "denied", "", "缺少执行 ID", null, false, false)
        val name = program.orEmpty()
        val argv = args?.toList().orEmpty()
        val dir = cwd?.let { runCatching { File(it).canonicalFile }.getOrNull() }
        if (dir == null || !dir.isDirectory || TerminalPolicy.validate(name, argv, dir) != null || isBlocked(dir)) {
            return result(id, "denied", "", "终端请求不符合安全策略", null, false, false)
        }
        val process = try {
            ProcessBuilder(listOf(name) + argv)
                .directory(dir)
                .redirectErrorStream(false)
                .start()
        } catch (_: Exception) {
            return result(id, "error", "", "无法启动受控终端程序", null, false, false)
        }
        running[id] = process
        val out = StringBuilder()
        val err = StringBuilder()
        val outThread = Thread { readLimited(process.inputStream, out, maxOutputBytes.coerceIn(1, MAX_OUTPUT_BYTES)) }
        val errThread = Thread { readLimited(process.errorStream, err, maxOutputBytes.coerceIn(1, MAX_OUTPUT_BYTES)) }
        outThread.start(); errThread.start()
        val timeout = timeoutMs.toLong().coerceIn(1_000L, MAX_TIMEOUT_MS)
        val completed = try { process.waitFor(timeout, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
        if (!completed) process.destroyForcibly()
        running.remove(id)
        outThread.join(500); errThread.join(500)
        val exit = if (completed) runCatching { process.exitValue() }.getOrNull() else null
        return result(id, if (!completed) "timeout" else if (exit == 0) "ok" else "error",
            out.toString(), err.toString(), exit, !completed,
            out.length + err.length >= maxOutputBytes.coerceIn(1, MAX_OUTPUT_BYTES))
    }

    override fun openSession(sessionId: String?, program: String?, args: Array<out String>?, cwd: String?, rows: Int, cols: Int): String {
        val id = sessionId?.takeIf { it.isNotBlank() } ?: return "denied"
        val name = program.orEmpty()
        val argv = args?.toList().orEmpty()
        val dir = cwd?.let { runCatching { File(it).canonicalFile }.getOrNull() }
        val interactiveShell = name == "/system/bin/sh" && argv == listOf("-i")
        if (dir == null || !dir.isDirectory || (!interactiveShell && TerminalPolicy.validate(name, argv, dir) != null) || isBlocked(dir)) return "denied"
        val pty = runCatching {
            NativePtySession.open(name, argv, dir.absolutePath, rows, cols)
        }.getOrNull() ?: return "error"
        sessions[id] = pty
        return "ok"
    }

    override fun readSession(sessionId: String?, maxBytes: Int): ByteArray {
        val pty = sessions[sessionId] ?: return ByteArray(0)
        return runCatching { pty.read(maxBytes.coerceIn(1, MAX_OUTPUT_BYTES)) ?: ByteArray(0) }
            .getOrDefault(ByteArray(0))
    }

    override fun writeSession(sessionId: String?, data: ByteArray?): Int {
        val pty = sessions[sessionId] ?: return -1
        return runCatching { pty.write(data ?: ByteArray(0)) }.getOrDefault(-1)
    }

    override fun resizeSession(sessionId: String?, rows: Int, cols: Int): Int =
        sessions[sessionId]?.let { if (it.resize(rows, cols)) 0 else -1 } ?: -1

    override fun signalSession(sessionId: String?, signal: Int): Int =
        sessions[sessionId]?.let { if (it.signal(signal)) 0 else -1 } ?: -1

    override fun closeSession(sessionId: String?) {
        sessions.remove(sessionId)?.close()
    }

    override fun cancel(executionId: String?) {
        executionId?.let { running.remove(it)?.destroyForcibly() }
    }

    private fun isBlocked(dir: File): Boolean {
        val first = dir.path.removePrefix(File.separator).substringBefore(File.separator).lowercase()
        return first in setOf("proc", "sys", "dev", "data", "apex", "vendor")
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

    private fun result(id: String, status: String, stdout: String, stderr: String, exit: Int?, timedOut: Boolean, truncated: Boolean): String =
        gson.toJson(JsonObject().apply {
            addProperty("id", id); addProperty("status", status); addProperty("stdout", stdout.take(MAX_OUTPUT_BYTES))
            addProperty("stderr", stderr.take(MAX_OUTPUT_BYTES)); exit?.let { addProperty("exitCode", it) }
            addProperty("timedOut", timedOut); addProperty("truncated", truncated)
        })

    companion object {
        private const val MAX_TIMEOUT_MS = 15_000L
        private const val MAX_OUTPUT_BYTES = 64 * 1024
    }
}
