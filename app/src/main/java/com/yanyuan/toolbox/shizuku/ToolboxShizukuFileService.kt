package com.yanyuan.toolbox.shizuku

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Shizuku UserService 中运行的受控文件后端。
 *
 * 这里只提供结构化文件操作，不接受 shell 命令。应用侧会先做一次策略检查，
 * 这里在实际使用较高权限执行前再次检查路径和大小。
 */
class ToolboxShizukuFileService : IShizukuFileService.Stub() {

    override fun list(path: String?, maxEntries: Int): String {
        val dir = resolve(path)
            ?: return "拒绝读取：路径不在 Agent 可访问目录内"
        if (!dir.exists()) return "目录不存在"
        if (!dir.isDirectory) return "读取失败：目标不是目录"

        val limit = maxEntries.coerceIn(1, MAX_LIST_ENTRIES)
        val files = try {
            dir.listFiles()
                ?.filterNot { isSensitive(it) || isBlockedRootPath(it) }
                ?.sortedBy { it.name }
                ?: return "无法读取目录"
        } catch (_: SecurityException) {
            return "无法读取目录"
        }
        if (files.isEmpty()) return "目录为空"

        return buildString {
            appendLine("目录内容：")
            files.take(limit).forEach { file ->
                val kind = if (file.isDirectory) "[目录]" else "[文件]"
                val size = runCatching { file.length() }.getOrDefault(0L)
                appendLine("$kind ${safeName(file.name)}（${size}B）")
            }
            if (files.size > limit) appendLine("…共 ${files.size} 项")
        }
    }

    override fun read(path: String?, maxBytes: Int): String {
        val file = resolve(path)
            ?: return "拒绝读取：路径不在 Agent 可访问目录内"
        if (!file.exists()) return "文件不存在"
        if (file.isDirectory) return list(file.path, MAX_LIST_ENTRIES)
        if (isSensitive(file)) return "拒绝读取：路径包含敏感数据"

        val limit = maxBytes.coerceIn(1, MAX_READ_BYTES)
        val size = runCatching { file.length() }.getOrDefault(0L)
        return try {
            FileInputStream(file).use { input ->
                val bytes = ByteArray(limit)
                val count = input.read(bytes).coerceAtLeast(0)
                val text = String(bytes, 0, count, StandardCharsets.UTF_8)
                val suffix = if (size > limit) {
                    "\n文件过大，仅返回前 ${limit / 1024}KB"
                } else {
                    ""
                }
                redact(text).take(MAX_RESPONSE_CHARS) + suffix
            }
        } catch (_: Exception) {
            "读取失败（可能是二进制或无权访问）"
        }
    }

    override fun exists(path: String?): Boolean {
        val target = resolve(path) ?: return false
        return runCatching { target.exists() }.getOrDefault(false)
    }

    override fun write(path: String?, content: String?): String {
        if (path.isNullOrBlank()) return "写入失败：缺少 path"
        if (content == null) return "写入失败：缺少 content"
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_WRITE_BYTES) return "写入失败：内容超过 512KB 限制"

        val target = resolve(path)
            ?: return "拒绝写入：目标不在 Agent 可访问范围内"
        if (target.path == File.separator || target.isDirectory || isSensitive(target)) {
            return "拒绝写入：目标不在 Agent 可访问范围内"
        }

        return try {
            val parent = target.parentFile
                ?: return "写入失败：目标目录不可用"
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                return "写入失败：目标目录不可用"
            }
            if (isSensitive(parent)) return "拒绝写入：目标目录包含敏感数据"

            // 先写入同目录临时文件，再替换目标，避免把半截内容留给后续读取。
            val temp = File(parent, ".agent-write-${System.nanoTime()}.tmp")
            try {
                FileOutputStream(temp).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                if (isSensitive(temp)) return "拒绝写入：目标文件名不可用"
                if (!temp.renameTo(target)) {
                    return "写入失败：无法提交目标文件"
                }
                "已写入（Shizuku）：${safeName(target.name)}（${bytes.size}B）"
            } finally {
                if (temp.exists()) runCatching { temp.delete() }
            }
        } catch (_: Exception) {
            "写入失败：无法写入目标文件"
        }
    }

    private fun resolve(rawPath: String?): File? {
        if (rawPath.isNullOrBlank()) return File(File.separator)
        val raw = File(rawPath)
        if (!raw.isAbsolute) return null
        val target = runCatching { raw.canonicalFile }.getOrNull() ?: return null
        if (target.path != File.separator && firstSegment(target) in BLOCKED_ROOTS) {
            return null
        }
        if (isSensitive(target)) return null
        return target
    }

    private fun firstSegment(file: File): String {
        return file.path.removePrefix(File.separator)
            .substringBefore(File.separator)
            .lowercase()
    }

    private fun isBlockedRootPath(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return true
        if (path == File.separator) return false
        return firstSegment(File(path)) in BLOCKED_ROOTS
    }

    private fun isSensitive(file: File): Boolean {
        val segments = file.path.split(File.separatorChar)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        if (segments.any { it in BLOCKED_SEGMENTS }) return true
        val name = file.name.lowercase()
        return SENSITIVE_NAME_PARTS.any { it in name }
    }

    private fun redact(text: String): String {
        val assignment = Regex(
            "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|bearer|client[_-]?secret|password|secret[_-]?key)\\s*[:=]\\s*([\\\"']?)[^\\s,;\\\"']+"
        )
        val knownKey = Regex(
            "(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{12,}|AIza[0-9A-Za-z_-]{20,})\\b"
        )
        return text.lineSequence().joinToString("\n") { line ->
            knownKey.replace(
                assignment.replace(line) { match ->
                    "${match.groupValues[1]}=[已隐藏]"
                },
                "[已隐藏密钥]"
            )
        }
    }

    private fun safeName(name: String): String = name.take(160)

    companion object {
        private const val MAX_LIST_ENTRIES = 200
        private const val MAX_READ_BYTES = 64 * 1024
        private const val MAX_WRITE_BYTES = 512 * 1024
        private const val MAX_RESPONSE_CHARS = 512 * 1024
        private val BLOCKED_ROOTS = setOf("proc", "sys", "dev", "data", "apex", "vendor")
        private val BLOCKED_SEGMENTS = setOf(
            "shared_prefs", "databases", "api_secret_store", "api_model_import",
            ".ssh", ".gnupg"
        )
        private val SENSITIVE_NAME_PARTS = listOf(
            "api_key", "apikey", "secret", "credential", "access_token", "refresh_token"
        )
    }
}
