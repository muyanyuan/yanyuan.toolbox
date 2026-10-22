package com.yanyuan.toolbox.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.yanyuan.toolbox.util.PermissionHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * 管理运行在 Shizuku 身份下的受控文件 UserService。
 * 不把 Binder 暴露给 UI；调用方只得到经过限制的文本结果。
 */
object ShizukuFileServiceManager {
    private const val SERVICE_VERSION = 1
    private const val SERVICE_TAG = "toolbox-agent-file"
    private const val CONNECT_TIMEOUT_MS = 5_000L
    private const val CALL_TIMEOUT_MS = 15_000L

    private val lock = Any()
    @Volatile
    private var remote: IShizukuFileService? = null
    private var remoteBinder: IBinder? = null
    private var connecting: CompletableDeferred<Boolean>? = null
    private var boundContext: Context? = null

    private val deathRecipient = IBinder.DeathRecipient {
        synchronized(lock) {
            clearRemoteLocked()
            connecting?.complete(false)
            connecting = null
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val proxy = service?.let { IShizukuFileService.Stub.asInterface(it) }
            val alive = service != null && service.isBinderAlive && proxy != null
            if (alive) {
                runCatching { service!!.linkToDeath(deathRecipient, 0) }
                    .onFailure { /* 绑定回调已到达，但 Binder 不能可靠存活 */ }
            }
            synchronized(lock) {
                clearRemoteLocked()
                if (alive) {
                    remote = proxy
                    remoteBinder = service
                }
                connecting?.complete(alive)
                connecting = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                clearRemoteLocked()
                connecting?.complete(false)
                connecting = null
            }
        }
    }

    fun isReady(): Boolean {
        val binder = remote?.asBinder()
        return PermissionHelper.isShizukuAvailable() &&
            PermissionHelper.isShizukuGranted() &&
            binder?.isBinderAlive == true
    }

    suspend fun list(context: Context, path: String): String =
        call(context) { it.list(path, 200) }

    suspend fun read(context: Context, path: String): String =
        call(context) { it.read(path, 64 * 1024) }

    suspend fun write(context: Context, path: String, content: String): String =
        call(context) { it.write(path, content) }

    suspend fun exists(context: Context, path: String): Boolean =
        callBoolean(context) { it.exists(path) }

    suspend fun ensureReady(context: Context): Boolean {
        if (!PermissionHelper.isShizukuAvailable() || !PermissionHelper.isShizukuGranted()) {
            return false
        }
        if (isReady()) return true

        val appContext = context.applicationContext
        val waitFor: CompletableDeferred<Boolean>
        val shouldBind: Boolean
        synchronized(lock) {
            if (isReady()) return true
            val existing = connecting
            if (existing != null) {
                waitFor = existing
                shouldBind = false
            } else {
                waitFor = CompletableDeferred()
                connecting = waitFor
                shouldBind = true
            }
            boundContext = appContext
        }

        if (shouldBind) {
            try {
                withContext(Dispatchers.Main.immediate) {
                    Shizuku.bindUserService(userServiceArgs(appContext), connection)
                }
            } catch (_: Exception) {
                synchronized(lock) {
                    if (connecting === waitFor) connecting = null
                    waitFor.complete(false)
                }
                return false
            }
        }

        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            waitFor.await()
        } ?: run {
            synchronized(lock) {
                if (connecting === waitFor) {
                    connecting = null
                    waitFor.complete(false)
                }
            }
            false
        }
    }

    suspend fun disconnect() {
        val context = synchronized(lock) { boundContext } ?: return
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs(context), connection, true)
            }
        }
        synchronized(lock) {
            clearRemoteLocked()
            connecting?.complete(false)
            connecting = null
            boundContext = null
        }
    }

    private suspend fun callBoolean(
        context: Context,
        block: (IShizukuFileService) -> Boolean
    ): Boolean {
        if (!ensureReady(context)) return false
        return withTimeoutOrNull(CALL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val service = remote ?: return@withContext false
                try {
                    block(service)
                } catch (_: Exception) {
                    synchronized(lock) { clearRemoteIfLocked(service.asBinder()) }
                    false
                }
            }
        } ?: false
    }

    private suspend fun call(
        context: Context,
        block: (IShizukuFileService) -> String
    ): String {
        if (!ensureReady(context)) {
            return "拒绝执行：请先在系统设置中启动并授权 Shizuku"
        }
        return withTimeoutOrNull(CALL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val service = remote ?: return@withContext "拒绝执行：Shizuku 文件后端不可用"
                try {
                    block(service).take(MAX_RESULT_CHARS)
                } catch (_: RemoteException) {
                    synchronized(lock) { clearRemoteIfLocked(service.asBinder()) }
                    "拒绝执行：Shizuku 文件后端已断开"
                } catch (_: Exception) {
                    "拒绝执行：Shizuku 文件后端执行失败"
                }
            }
        } ?: "拒绝执行：Shizuku 文件后端响应超时"
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context, ToolboxShizukuFileService::class.java)
        )
            .version(SERVICE_VERSION)
            .tag(SERVICE_TAG)
            .daemon(false)
            .processNameSuffix("agent-file")

    private fun clearRemoteIfLocked(binder: IBinder) {
        if (remoteBinder === binder || remote?.asBinder() === binder) {
            clearRemoteLocked()
        }
    }

    private fun clearRemoteLocked() {
        remoteBinder?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
        remoteBinder = null
        remote = null
    }

    private const val MAX_RESULT_CHARS = 512 * 1024
}
