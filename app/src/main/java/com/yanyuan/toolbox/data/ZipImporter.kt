package com.yanyuan.toolbox.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

object ZipImporter {

    private const val MANIFEST_NAME = "manifest.json"
    private const val MAX_SINGLE_FILE = 10 * 1024 * 1024
    private const val MAX_TOTAL = 30 * 1024 * 1024

    sealed class ImportResult {
        data class Success(val plugin: InstalledPlugin) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    fun import(context: Context, uri: Uri): ImportResult {
        val tmpDir = File(context.filesDir, "plugins_tmp").apply {
            deleteRecursively(); mkdirs()
        }
        var manifestJson: String? = null

        try {
            var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }
                        val name = entry.name
                        if (name.contains("..") || name.startsWith("/")) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }

                        val bytes = zip.readLimited(MAX_SINGLE_FILE)
                        if (bytes == null) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }

                        total += bytes.size
                        if (total > MAX_TOTAL) {
                            tmpDir.deleteRecursively()
                            return ImportResult.Failure("插件包过大（上限 30MB）")
                        }

                        val out = File(tmpDir, name)
                        out.parentFile?.mkdirs()
                        out.writeBytes(bytes)

                        if (name.equals(MANIFEST_NAME, true) ||
                            name.lowercase().endsWith("/$MANIFEST_NAME")) {
                            manifestJson = String(bytes, Charsets.UTF_8)
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return ImportResult.Failure("无法读取文件")
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            return ImportResult.Failure("解析 zip 失败：${e.message}")
        }

        val json = manifestJson ?: run {
            tmpDir.deleteRecursively()
            return ImportResult.Failure("压缩包内缺少 manifest.json")
        }

        val manifest = try {
            PluginManifest.fromJson(JSONObject(json))
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            return ImportResult.Failure("manifest.json 格式错误：${e.message}")
        }

        if (manifest.id.isBlank()) {
            tmpDir.deleteRecursively()
            return ImportResult.Failure("manifest.json 缺少 id")
        }
        if (manifest.name.isBlank()) {
            tmpDir.deleteRecursively()
            return ImportResult.Failure("manifest.json 缺少 name")
        }

        val rulesPath = JSONObject(json).optString("rules", "").takeIf { it.isNotBlank() }
        val initialRules = if (rulesPath != null) {
            if (rulesPath.contains("..") || rulesPath.startsWith("/")) {
                tmpDir.deleteRecursively()
                return ImportResult.Failure("manifest.json 的 rules 路径无效")
            }
            val rulesFile = File(tmpDir, rulesPath)
            if (!rulesFile.isFile) {
                tmpDir.deleteRecursively()
                return ImportResult.Failure("插件包缺少规则文件：$rulesPath")
            }
            try {
                rulesFile.readText(Charsets.UTF_8).also {
                    require(JSONObject(it).optJSONArray("rules") != null) { "规则文件缺少 rules 数组" }
                }
            } catch (e: Exception) {
                tmpDir.deleteRecursively()
                return ImportResult.Failure("规则文件格式错误：${e.message}")
            }
        } else null

        val finalDir = File(context.filesDir, "plugins/${manifest.id}")
        try {
            if (finalDir.exists()) finalDir.deleteRecursively()
            finalDir.parentFile?.mkdirs()
            if (!tmpDir.renameTo(finalDir)) {
                tmpDir.copyRecursively(finalDir, overwrite = true)
                tmpDir.deleteRecursively()
            }
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            return ImportResult.Failure("保存插件失败：${e.message}")
        }

        if (initialRules != null) {
            PluginRulesStore(context, manifest.id).importInitialRules(initialRules)
        }

        val iconFile: File? = manifest.iconEntryName?.let { n ->
            File(finalDir, n).takeIf { it.exists() }
        }

        return ImportResult.Success(InstalledPlugin(manifest, iconFile))
    }

    private fun ZipInputStream.readLimited(max: Int): ByteArray? {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(chunk)
            if (n <= 0) break
            total += n
            if (total > max) return null
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }
}
