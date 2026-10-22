package com.yanyuan.toolbox.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.google.gson.Gson
import com.yanyuan.toolbox.util.TerminalPolicy
import com.yanyuan.toolbox.util.TerminalRequest
import com.yanyuan.toolbox.util.TerminalResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

object ShizukuTerminalServiceManager {
    private const val SERVICE_VERSION = 1
    private const val SERVICE_TAG = "toolbox-agent-terminal"
    private const val CONNECT_TIMEOUT_MS = 5_000L
    private const val CALL_TIMEOUT_MS = 20_000L
    private val lock = Any()
    @Volatile private var remote: IShizukuTerminalService? = null
    private var connecting: CompletableDeferred<Boolean>? = null
    private var binder: IBinder? = null

    private val death = IBinder.DeathRecipient {
        synchronized(lock) {
            remote = null; binder = null; connecting?.complete(false); connecting = null
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val proxy = service?.let { IShizukuTerminalService.Stub.asInterface(it) }
            val alive = service != null && service.isBinderAlive && proxy != null
            if (alive) runCatching { service!!.linkToDeath(death, 0) }
            synchronized(lock) {
                remote = if (alive) proxy else null
                binder = if (alive) service else null
                connecting?.complete(alive); connecting = null
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) { remote = null; binder = null; connecting?.complete(false); connecting = null }
        }
    }

    suspend fun cancel(context: Context, id: String) {
        if (!ensureReady(context)) return
        withContext(Dispatchers.IO) { runCatching { remote?.cancel(id) } }
    }

    suspend fun openSession(context: Context, id: String, request: TerminalRequest, rows: Int, cols: Int): Boolean {
        if (!ensureReady(context)) return false
        return withContext(Dispatchers.IO) {
            runCatching { remote?.openSession(id, request.program, request.args.toTypedArray(), request.cwd.absolutePath, rows, cols) == "ok" }.getOrDefault(false)
        }
    }

    suspend fun readSession(context: Context, id: String, maxBytes: Int = 8192): ByteArray {
        if (!ensureReady(context)) return ByteArray(0)
        return withContext(Dispatchers.IO) { runCatching { remote?.readSession(id, maxBytes) ?: ByteArray(0) }.getOrDefault(ByteArray(0)) }
    }

    suspend fun writeSession(context: Context, id: String, data: ByteArray): Int {
        if (!ensureReady(context)) return -1
        return withContext(Dispatchers.IO) { runCatching { remote?.writeSession(id, data) ?: -1 }.getOrDefault(-1) }
    }

    suspend fun resizeSession(context: Context, id: String, rows: Int, cols: Int): Boolean {
        if (!ensureReady(context)) return false
        return withContext(Dispatchers.IO) { runCatching { remote?.resizeSession(id, rows, cols) == 0 }.getOrDefault(false) }
    }

    suspend fun signalSession(context: Context, id: String, signal: Int): Boolean {
        if (!ensureReady(context)) return false
        return withContext(Dispatchers.IO) { runCatching { remote?.signalSession(id, signal) == 0 }.getOrDefault(false) }
    }

    suspend fun closeSession(context: Context, id: String) {
        if (!ensureReady(context)) return
        withContext(Dispatchers.IO) { runCatching { remote?.closeSession(id) } }
    }

    suspend fun execute(context: Context, request: TerminalRequest): TerminalResult {
        if (!ensureReady(context)) return TerminalResult(request.id, "denied", "", "Shizuku 终端后端不可用", null, false, false)
        return withTimeoutOrNull(CALL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val service = remote ?: return@withContext TerminalResult(request.id, "denied", "", "Shizuku 终端后端不可用", null, false, false)
                try {
                    val raw = service.execute(request.id, request.program, request.args.toTypedArray(), request.cwd.absolutePath,
                        request.timeoutMs.toInt(), request.maxOutputBytes)
                    parse(raw, request.id)
                } catch (_: Exception) {
                    synchronized(lock) { remote = null; binder = null }
                    TerminalResult(request.id, "error", "", "Shizuku 终端服务已断开", null, false, false)
                }
            }
        } ?: TerminalResult(request.id, "timeout", "", "Shizuku 终端响应超时", null, true, false)
    }

    private fun parse(raw: String, fallbackId: String): TerminalResult = runCatching {
        val o = Gson().fromJson(raw, com.google.gson.JsonObject::class.java)
        TerminalResult(o.get("id")?.asString ?: fallbackId, o.get("status")?.asString ?: "error",
            o.get("stdout")?.asString.orEmpty(), o.get("stderr")?.asString.orEmpty(),
            o.get("exitCode")?.asInt, o.get("timedOut")?.asBoolean ?: false, o.get("truncated")?.asBoolean ?: false)
    }.getOrElse { TerminalResult(fallbackId, "error", "", "Shizuku 终端响应无法解析", null, false, false) }

    private suspend fun ensureReady(context: Context): Boolean {
        if (!com.yanyuan.toolbox.util.PermissionHelper.isShizukuAvailable() ||
            !com.yanyuan.toolbox.util.PermissionHelper.isShizukuGranted()) return false
        if (remote?.asBinder()?.isBinderAlive == true) return true
        val wait: CompletableDeferred<Boolean>
        val bind: Boolean
        synchronized(lock) {
            val existing = connecting
            if (existing != null) { wait = existing; bind = false }
            else { wait = CompletableDeferred(); connecting = wait; bind = true }
        }
        if (bind) runCatching {
            withContext(Dispatchers.Main.immediate) { Shizuku.bindUserService(args(context), connection) }
        }.onFailure { synchronized(lock) { connecting = null; wait.complete(false) } }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { wait.await() } ?: run {
            synchronized(lock) { if (connecting === wait) { connecting = null; wait.complete(false) } }; false
        }
    }

    private fun args(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.applicationContext, ToolboxShizukuTerminalService::class.java)
    ).version(SERVICE_VERSION).tag(SERVICE_TAG).daemon(false).processNameSuffix("agent-terminal")
}
