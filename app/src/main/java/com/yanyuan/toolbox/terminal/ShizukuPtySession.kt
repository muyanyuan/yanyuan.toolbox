package com.yanyuan.toolbox.terminal

import android.content.Context
import com.yanyuan.toolbox.shizuku.ShizukuTerminalServiceManager
import com.yanyuan.toolbox.util.TerminalRequest
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.launch

/** Shizuku session transport. The service side may use a pipe fallback until PTY is available there. */
class ShizukuPtySession private constructor(
    private val context: Context,
    private val sessionId: String
) : Closeable {
    @Volatile private var closed = false

    suspend fun read(maxBytes: Int = 8192): ByteArray? {
        if (closed) return null
        return ShizukuTerminalServiceManager.readSession(context, sessionId, maxBytes).takeIf { it.isNotEmpty() }
    }

    suspend fun write(bytes: ByteArray): Int {
        if (closed) return -1
        return ShizukuTerminalServiceManager.writeSession(context, sessionId, bytes)
    }

    suspend fun resize(rows: Int, cols: Int): Boolean =
        !closed && ShizukuTerminalServiceManager.resizeSession(context, sessionId, rows, cols)

    suspend fun interrupt(): Boolean =
        !closed && ShizukuTerminalServiceManager.signalSession(context, sessionId, 2)

    override fun close() {
        if (!closed) {
            closed = true
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                ShizukuTerminalServiceManager.closeSession(context, sessionId)
            }
        }
    }

    companion object {
        suspend fun open(context: Context, cwd: String): ShizukuPtySession? {
            val id = UUID.randomUUID().toString()
            val request = TerminalRequest(id, "/system/bin/sh", listOf("-i"), java.io.File(cwd))
            return if (ShizukuTerminalServiceManager.openSession(context, id, request, 24, 80)) {
                ShizukuPtySession(context.applicationContext, id)
            } else null
        }
    }
}
