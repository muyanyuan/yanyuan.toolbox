package com.yanyuan.toolbox.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.yanyuan.toolbox.data.ChatAttachment
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 临时保存系统分享文件的私有中转层。
 * 外部 URI 只在 ingest 阶段使用；插件和 AI 只拿到应用内部对象或随机 token。
 */
object SharedFileHandoffStore {
    private const val MAX_FILES = 5
    private const val MAX_FILE_BYTES = 10L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 30L * 1024 * 1024
    private const val MAX_TEXT_BYTES = 512 * 1024
    private const val MAX_BASE64_BYTES = 3L * 1024 * 1024
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_TEXT_LENGTH = 4_000

    data class SharedFile(
        val index: Int,
        val name: String,
        val mimeType: String,
        val size: Long,
        internal val file: File
    )

    data class Handoff(
        val token: String,
        val files: List<SharedFile>,
        val sharedText: String?,
        internal val directory: File
    ) {
        fun attachments(): List<ChatAttachment> = files.map {
            ChatAttachment(name = it.name, path = it.file.absolutePath, size = it.size)
        }
    }

    private val active = mutableMapOf<String, Handoff>()

    fun ingest(context: Context, intent: Intent): Result<Handoff?> = runCatching {
        val uris = collectUris(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.take(MAX_TEXT_LENGTH)
            ?.takeIf { it.isNotBlank() }
        if (uris.isEmpty() && text == null) return@runCatching null
        if (uris.size > MAX_FILES) throw IOException("分享文件最多支持 $MAX_FILES 个")

        val token = UUID.randomUUID().toString()
        val dir = File(context.cacheDir, "share_handoff/$token").apply { mkdirs() }
        val files = mutableListOf<SharedFile>()
        var total = 0L
        try {
            uris.distinctBy { it.toString() }.forEachIndexed { index, uri ->
                val meta = metadata(context, uri)
                val name = sanitizeName(meta.name.ifBlank { "shared_file_${index + 1}" })
                val mime = meta.mimeType.ifBlank { "application/octet-stream" }
                val destination = File(dir, "$index.bin")
                val part = File(dir, "$index.part")
                var copied = 0L
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("无法读取分享文件")
                input.use { source ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            total += count
                            if (copied > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                                throw IOException("分享文件超过大小限制")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (!part.renameTo(destination)) throw IOException("无法保存分享文件")
                files += SharedFile(index, name, mime, copied, destination)
            }
            val handoff = Handoff(token, files, text, dir)
            synchronized(active) { active[token] = handoff }
            handoff
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun get(token: String?): Handoff? = synchronized(active) {
        token?.let { active[it] }
    }

    fun copyToChatFiles(context: Context, token: String?): List<ChatAttachment> {
        val handoff = get(token) ?: return emptyList()
        val dir = File(context.cacheDir, "chat_files").apply { mkdirs() }
        return handoff.files.mapIndexedNotNull { index, shared ->
            val safe = sanitizeName(shared.name)
            val destination = File(dir, "share_${System.currentTimeMillis()}_${index}_$safe")
            val part = File(dir, ".${destination.name}.part")
            runCatching {
                shared.file.inputStream().use { input ->
                    part.outputStream().use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                if (!part.renameTo(destination)) error("copy failed")
                ChatAttachment(shared.name, destination.absolutePath, destination.length())
            }.getOrElse {
                runCatching { part.delete() }
                runCatching { destination.delete() }
                null
            }
        }
    }

    fun release(token: String?) {
        if (token.isNullOrBlank()) return
        val handoff = synchronized(active) { active.remove(token) } ?: return
        handoff.directory.deleteRecursively()
    }

    fun readText(token: String?, index: Int, maxBytes: Int): String? {
        val file = getFile(token, index) ?: return null
        val limit = maxBytes.coerceIn(1, MAX_TEXT_BYTES)
        return runCatching {
            FileInputStream(file).use { input ->
                val bytes = ByteArray(limit)
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count < 0) break
                    offset += count
                }
                String(bytes, 0, offset, StandardCharsets.UTF_8)
            }
        }.getOrNull()
    }

    fun readBase64(token: String?, index: Int, maxBytes: Int): String? {
        val file = getFile(token, index) ?: return null
        val limit = maxBytes.toLong().coerceIn(1L, MAX_BASE64_BYTES)
        if (file.length() > limit) return null
        return runCatching { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    private fun getFile(token: String?, index: Int): File? = synchronized(active) {
        active[token]?.files?.getOrNull(index)?.file?.takeIf { it.isFile }
    }

    private data class Meta(val name: String, val mimeType: String)

    private fun metadata(context: Context, uri: Uri): Meta {
        var name = ""
        var mime = context.contentResolver.getType(uri).orEmpty()
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                val mimeIndex = cursor.getColumnIndex("mime_type")
                if (mimeIndex >= 0 && mime.isBlank()) mime = cursor.getString(mimeIndex).orEmpty()
            }
        }
        return Meta(name, mime)
    }

    private fun sanitizeName(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        return leaf.replace(Regex("[\\u0000-\\u001F\\/:*?\"<>|]"), "_")
            .replace("..", "_")
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifBlank { "shared_file" }
    }

    private fun collectUris(intent: Intent): List<Uri> {
        val result = mutableListOf<Uri>()
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) result += clip.getItemAt(i).uri
        }
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { result += it }
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { result += it }
        intent.data?.let { result += it }
        return result.filter { uri ->
            uri.scheme == "content" || (uri.scheme == "file" && runCatching { File(uri.path.orEmpty()).canonicalFile.isFile }.getOrDefault(false))
        }
    }
}
