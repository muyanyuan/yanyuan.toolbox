package com.yanyuan.toolbox.terminal

interface TerminalUiSession {
    suspend fun read(maxBytes: Int): ByteArray?
    suspend fun writeText(text: String): Int
    suspend fun interrupt(): Boolean
    fun close()
}

class NativeUiSession(private val native: NativePtySession) : TerminalUiSession {
    override suspend fun read(maxBytes: Int): ByteArray? = native.read(maxBytes)
    override suspend fun writeText(text: String): Int = native.writeText(text)
    override suspend fun interrupt(): Boolean = native.interrupt()
    override fun close() = native.close()
}

class ShizukuUiSession(private val remote: ShizukuPtySession) : TerminalUiSession {
    override suspend fun read(maxBytes: Int): ByteArray? = remote.read(maxBytes)
    override suspend fun writeText(text: String): Int = remote.write(text.toByteArray(Charsets.UTF_8))
    override suspend fun interrupt(): Boolean = remote.interrupt()
    override fun close() = remote.close()
}
