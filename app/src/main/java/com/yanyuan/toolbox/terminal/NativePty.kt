package com.yanyuan.toolbox.terminal

import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** Native PTY bridge. It is transport only; command policy remains in AgentTools. */
class NativePtySession private constructor(private var handle: Long) : Closeable {
    private val closed = AtomicBoolean(false)

    fun read(maxBytes: Int = 8192): ByteArray? {
        if (closed.get()) return null
        return nativeRead(handle, maxBytes)
    }

    fun readText(maxBytes: Int = 8192): String? =
        read(maxBytes)?.toString(StandardCharsets.UTF_8)

    fun write(bytes: ByteArray): Int {
        if (closed.get()) return -1
        return nativeWrite(handle, bytes)
    }

    fun writeText(text: String): Int = write(text.toByteArray(StandardCharsets.UTF_8))

    fun resize(rows: Int, cols: Int): Boolean =
        !closed.get() && nativeResize(handle, rows, cols) == 0

    fun interrupt(): Boolean = signal(2)

    fun signal(number: Int): Boolean =
        !closed.get() && nativeSignal(handle, number) == 0

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        init { System.loadLibrary("toolboxpty") }

        fun open(
            program: String,
            args: List<String>,
            cwd: String,
            rows: Int = 24,
            cols: Int = 80,
            environment: Map<String, String> = emptyMap()
        ): NativePtySession? {
            val keys = environment.keys.toTypedArray()
            val values = keys.map { environment.getValue(it) }.toTypedArray()
            val handle = nativeOpen(program, args.toTypedArray(), cwd, rows, cols, keys, values)
            return handle.takeIf { it != 0L }?.let(::NativePtySession)
        }

        @JvmStatic private external fun nativeOpen(
            program: String,
            args: Array<String>,
            cwd: String,
            rows: Int,
            cols: Int,
            environmentKeys: Array<String>,
            environmentValues: Array<String>
        ): Long
        @JvmStatic private external fun nativeRead(handle: Long, maxBytes: Int): ByteArray?
        @JvmStatic private external fun nativeWrite(handle: Long, data: ByteArray): Int
        @JvmStatic private external fun nativeResize(handle: Long, rows: Int, cols: Int): Int
        @JvmStatic private external fun nativeSignal(handle: Long, signal: Int): Int
        @JvmStatic private external fun nativeClose(handle: Long): Int
    }
}
