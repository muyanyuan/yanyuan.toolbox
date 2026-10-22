package com.yanyuan.toolbox.runtime

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import com.google.gson.JsonParser

/**
 * App-private Toolbox Runtime layout owned by this application.
 * The archive is deliberately installed only after its manifest and every file hash verify.
 */
enum class RuntimeState { MISSING, READY, DAMAGED, ABI_UNSUPPORTED, PACKAGE_MANAGER_UNAVAILABLE }

data class RuntimeStatus(
    val state: RuntimeState,
    val version: String? = null,
    val abi: String? = null,
    val detail: String? = null
)

data class PackageManagerStatus(
    val available: Boolean,
    val repositoryConfigured: Boolean,
    val signedMetadataVerified: Boolean,
    val detail: String? = null
)

data class RuntimeEnvironment(
    val root: File,
    val prefix: File,
    val home: File,
    val shell: File,
    val path: String,
    val version: String,
    val variables: Map<String, String>
)

data class RuntimeFileRecord(
    val path: String,
    val sha256: String,
    val size: Long,
    val mode: Int
)

data class RuntimeManifest(
    val version: String,
    val abi: String,
    val files: List<RuntimeFileRecord>
) {
    companion object {
        fun parse(json: String): RuntimeManifest {
            val objectJson = runCatching { JsonParser.parseString(json).asJsonObject }
                .getOrElse { throw IllegalArgumentException("invalid runtime manifest", it) }
            val filesJson = objectJson.getAsJsonArray("files")
                ?: throw IllegalArgumentException("runtime manifest is missing files")
            val files = filesJson.map { element ->
                val file = element.asJsonObject
                val path = file.get("path").asString
                require(path == ToolboxRuntimeInstaller.normalizeArchivePath(path)) { "unsafe runtime manifest path" }
                require(path != "manifest.json") { "runtime manifest cannot list itself" }
                val hash = file.get("sha256").asString.lowercase()
                val size = file.get("size").asLong
                val mode = file.get("mode")?.asInt
                    ?: throw IllegalArgumentException("runtime manifest is missing file mode")
                require(hash.matches(Regex("[0-9a-f]{64}"))) { "invalid runtime file hash" }
                require(size >= 0) { "invalid runtime file size" }
                require(mode in 0..0b111111111) { "unsafe runtime file mode" }
                RuntimeFileRecord(path = path, sha256 = hash, size = size, mode = mode)
            }
            return RuntimeManifest(
                version = objectJson.get("version").asString,
                abi = objectJson.get("abi").asString,
                files = files
            )
        }
    }
}

object RuntimePaths {
    private const val HOME_NAME = "home"
    private const val PREFIX_NAME = "usr"

    fun root(context: Context): File = File("/data/data/${context.packageName}/files")
    fun active(context: Context): File = prefix(context)
    fun home(context: Context): File = File(root(context), HOME_NAME)
    fun prefix(context: Context): File = File(root(context), PREFIX_NAME)

    fun environment(context: Context): RuntimeEnvironment {
        val runtimeRoot = root(context)
        val runtimePrefix = prefix(context)
        val runtimeHome = home(context)
        val version = ToolboxRuntimeInstaller.installedVersion(context).orEmpty()
        return RuntimeEnvironment(
            root = runtimeRoot,
            prefix = runtimePrefix,
            home = runtimeHome,
            shell = File(runtimePrefix, "bin/bash"),
            path = listOf(
                File(runtimePrefix, "bin").absolutePath,
                File(runtimePrefix, "bin/applets").absolutePath
            ).joinToString(File.pathSeparator),
            version = version,
            variables = mapOf(
                "HOME" to runtimeHome.absolutePath,
                "PREFIX" to runtimePrefix.absolutePath,
                "PATH" to listOf(File(runtimePrefix, "bin").absolutePath, File(runtimePrefix, "bin/applets").absolutePath).joinToString(File.pathSeparator),
                "LD_LIBRARY_PATH" to File(runtimePrefix, "lib").absolutePath,
                "TMPDIR" to File(runtimeHome, ".tmp").absolutePath,
                "LANG" to "C.UTF-8",
                "TERM" to "xterm-256color"
            )
        )
    }
}

sealed interface RuntimeInstallResult {
    data class Installed(val version: String, val abi: String) : RuntimeInstallResult
    data class Rejected(val reason: String) : RuntimeInstallResult
}

object ToolboxRuntimeInstaller {
    private const val PREFIX_NAME = "usr"
    private const val MANIFEST_NAME = "manifest.json"
    private const val MARKER_NAME = ".runtime-installed"
    private const val MAX_ENTRIES = 30_000
    private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    private const val MAX_PATH_LENGTH = 512

    /** ABI names match Android's supported ABI directory names. */
    fun currentAbi(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64-v8a"
        "armeabi-v7a" -> "armeabi-v7a"
        "x86_64" -> "x86_64"
        else -> Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    }

    fun status(context: Context): RuntimeStatus {
        val supported = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        val abi = currentAbi()
        if (abi !in supported) return RuntimeStatus(RuntimeState.ABI_UNSUPPORTED, abi = abi)
        val active = RuntimePaths.active(context)
        val marker = File(active, MARKER_NAME)
        val env = RuntimePaths.environment(context)
        if (!marker.isFile || !env.prefix.isDirectory) return RuntimeStatus(RuntimeState.MISSING, abi = abi)
        if (!env.shell.isFile || !File(env.prefix, "bin/busybox").isFile) {
            return RuntimeStatus(RuntimeState.DAMAGED, installedVersion(context), abi, "缺少 bash 或 BusyBox")
        }
        return RuntimeStatus(RuntimeState.READY, installedVersion(context), abi)
    }

    fun storageBytes(context: Context): Long = RuntimePaths.prefix(context).walkTopDown().sumOf { it.length() }

    fun packageManagerStatus(context: Context): PackageManagerStatus {
        val env = environmentIfReady(context) ?: return PackageManagerStatus(false, false, false, "Toolbox Runtime 未安装")
        val apt = File(env.prefix, "bin/apt")
        val dpkg = File(env.prefix, "bin/dpkg")
        val configured = File(env.prefix, "etc/apt/sources.list.d/toolbox.list").isFile
        val available = apt.isFile && dpkg.isFile
        return PackageManagerStatus(
            available = available,
            repositoryConfigured = configured,
            signedMetadataVerified = false,
            detail = when {
                !available -> "基础 Bash + BusyBox 版本不包含 APT/dpkg"
                !configured -> "仓库尚未配置"
                else -> "尚未验证已签名的 Toolbox 仓库元数据"
            }
        )
    }

    fun environmentIfReady(context: Context): RuntimeEnvironment? {
        val state = status(context)
        return if (state.state == RuntimeState.READY) RuntimePaths.environment(context) else null
    }

    fun installedVersion(context: Context): String? = runCatching {
        val marker = File(RuntimePaths.active(context), MARKER_NAME)
        if (!marker.isFile) return null
        marker.readText(Charsets.UTF_8).trim().ifBlank { null }
    }.getOrNull()

    /** Installs the ABI-specific archive bundled in app assets, when a verified asset is present. */
    fun installBundled(context: Context): RuntimeInstallResult {
        val abi = currentAbi()
        val assetName = "toolbox-runtime/$abi/bootstrap.zip"
        return runCatching {
            context.assets.open(assetName).use { install(context, it, abi) }
        }.getOrElse { RuntimeInstallResult.Rejected("未找到内置 $abi bootstrap：${it.message.orEmpty()}") }
    }

    /**
     * Installs an archive into a staging directory and swaps it into place only after verification.
     * The caller owns the InputStream and should close it.
     */
    fun install(
        context: Context,
        input: InputStream,
        expectedAbi: String = currentAbi()
    ): RuntimeInstallResult {
        val root = RuntimePaths.root(context)
        val staging = File(root, ".staging-${UUID.randomUUID()}")
        val active = RuntimePaths.active(context)
        val backup = File(root, ".backup-${UUID.randomUUID()}")
        return try {
            require(expectedAbi.isNotBlank()) { "runtime ABI is empty" }
            if (!root.exists() && !root.mkdirs()) throw IllegalStateException("cannot create runtime root")
            if (!root.isDirectory) throw IllegalStateException("runtime root is not a directory")
            if (!staging.mkdirs()) throw IllegalStateException("cannot create staging directory")

            val manifest = extractAndVerify(input, staging, expectedAbi)
            val stagedPrefix = staging
            val shell = File(stagedPrefix, "bin/bash")
            if (!shell.isFile || !File(stagedPrefix, "bin/busybox").isFile) {
                throw IllegalArgumentException("runtime archive must contain bash and busybox")
            }
            if (!RuntimePaths.home(context).exists() && !RuntimePaths.home(context).mkdirs()) {
                throw IllegalStateException("cannot create runtime home")
            }
            File(stagedPrefix, MARKER_NAME).writeText(manifest.version, Charsets.UTF_8)

            if (active.exists() && !active.renameTo(backup)) {
                throw IllegalStateException("cannot preserve active runtime")
            }
            if (!stagedPrefix.renameTo(active)) {
                if (backup.exists()) backup.renameTo(active)
                throw IllegalStateException("cannot activate staged runtime")
            }
            deleteRecursively(backup)
            RuntimeInstallResult.Installed(manifest.version, manifest.abi)
        } catch (e: Exception) {
            deleteRecursively(staging)
            if (!active.exists() && backup.exists()) backup.renameTo(active)
            RuntimeInstallResult.Rejected(e.message ?: "runtime installation failed")
        }
    }

    private fun extractAndVerify(input: InputStream, staging: File, expectedAbi: String): RuntimeManifest {
        val records = LinkedHashMap<String, RuntimeFileRecord>()
        var manifestJson: String? = null
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > MAX_ENTRIES) throw IllegalArgumentException("runtime archive has too many entries")
                val safePath = validateEntryPath(entry)
                if (safePath in records || (safePath == MANIFEST_NAME && manifestJson != null)) {
                    throw IllegalArgumentException("runtime archive contains duplicate paths")
                }
                if (safePath == MANIFEST_NAME) {
                    if (manifestJson != null || entry.isDirectory) {
                        throw IllegalArgumentException("runtime archive has an invalid manifest entry")
                    }
                    manifestJson = zip.readBytes(1024 * 1024).toString(Charsets.UTF_8)
                    zip.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val target = safeResolve(staging, safePath)
                target.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                var fileBytes = 0L
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        fileBytes += read
                        totalBytes += read
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw IllegalArgumentException("runtime archive is too large")
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
                records[safePath] = RuntimeFileRecord(safePath, digest.hex(), fileBytes, 0)
                zip.closeEntry()
            }
        }
        val manifest = RuntimeManifest.parse(manifestJson ?: throw IllegalArgumentException("runtime manifest missing"))
        if (manifest.abi != expectedAbi) throw IllegalArgumentException("runtime ABI mismatch")
        if (manifest.files.size != records.size || manifest.files.map { it.path }.toSet() != records.keys) {
            throw IllegalArgumentException("runtime manifest does not match archive contents")
        }
        manifest.files.forEach { expected ->
            val actual = records[expected.path] ?: error("missing runtime file ${expected.path}")
            if (actual.size != expected.size || actual.sha256 != expected.sha256) {
                throw IllegalArgumentException("runtime checksum mismatch for ${expected.path}")
            }
            val target = safeResolve(staging, expected.path)
            try {
                Os.chmod(target.absolutePath, expected.mode)
            } catch (error: ErrnoException) {
                throw IllegalArgumentException("cannot apply runtime mode for ${expected.path}", error)
            }
        }
        restoreSymlinks(staging)
        return manifest
    }

    private fun makeRuntimeCommandsExecutable(prefix: File) {
        val bin = File(prefix, "bin")
        bin.walkTopDown().filter { it.isFile && !isSymbolicLink(it) }.forEach { file ->
            if (!file.setExecutable(true, false) && !file.canExecute()) {
                throw IllegalStateException("cannot mark runtime command executable")
            }
        }
        val libexec = File(prefix, "libexec")
        if (libexec.isDirectory) {
            libexec.walkTopDown().filter { it.isFile && !isSymbolicLink(it) }.forEach { file ->
                file.setExecutable(true, false)
            }
        }
    }

    private fun restoreSymlinks(staging: File) {
        val metadata = File(staging, "SYMLINKS.txt")
        if (metadata.isFile) {
            metadata.useLines(Charsets.UTF_8) { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    restoreSymlink(staging, line)
                }
            }
            if (!metadata.delete()) throw IllegalStateException("cannot remove runtime symlink metadata")
        }
    }

    internal fun restoreSymlink(staging: File, line: String) {
        val separator = "\u2192"
        val delimiter = line.indexOf(separator)
        if (delimiter <= 0 || delimiter + separator.length >= line.length) {
            throw IllegalArgumentException("invalid runtime symlink metadata")
        }
        val linkTarget = line.substring(0, delimiter)
        if (linkTarget.contains('\\') || linkTarget.startsWith('/')) {
            throw IllegalArgumentException("unsafe runtime symlink target")
        }
        val linkPath = normalizeArchivePath(line.substring(delimiter + separator.length))
        val linkFile = safeResolve(staging, linkPath)
        val parent = linkFile.parentFile ?: throw IllegalArgumentException("invalid symlink parent")
        if (!parent.isDirectory && !parent.mkdirs()) throw IllegalArgumentException("cannot create symlink parent")
        val resolved = File(parent, linkTarget).canonicalFile
        val stagingCanonical = staging.canonicalFile
        if (resolved.path != stagingCanonical.path && !resolved.path.startsWith(stagingCanonical.path + File.separator)) {
            throw IllegalArgumentException("runtime symlink escapes prefix")
        }
        if (linkFile.exists() || isSymbolicLink(linkFile)) {
            throw IllegalArgumentException("runtime symlink path already exists")
        }
        val relativeTarget = parent.toPath().relativize(resolved.toPath()).toString()
        try {
            Os.symlink(relativeTarget, linkFile.absolutePath)
        } catch (error: ErrnoException) {
            throw IllegalStateException("cannot create runtime symlink", error)
        }
    }

    private fun validateEntryPath(entry: ZipEntry): String = normalizeArchivePath(entry.name)

    internal fun normalizeArchivePath(rawPath: String): String {
        val normalized = rawPath.replace('\\', '/').removePrefix("./")
        val name = normalized
        if (name.isBlank() || name.length > MAX_PATH_LENGTH || name.startsWith('/')) {
            throw IllegalArgumentException("unsafe runtime archive path")
        }
        val parts = name.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." } || name.contains(':')) {
            throw IllegalArgumentException("unsafe runtime archive path")
        }
        return parts.joinToString("/")
    }

    private fun safeResolve(root: File, path: String): File {
        val rootCanonical = root.canonicalFile
        val target = File(root, path).canonicalFile
        val prefix = rootCanonical.path + File.separator
        if (target.path != rootCanonical.path && !target.path.startsWith(prefix)) {
            throw IllegalArgumentException("runtime archive path escapes staging")
        }
        return target
    }

    private fun isSymbolicLink(file: File): Boolean = runCatching {
        (Os.lstat(file.absolutePath).st_mode and OsConstants.S_IFMT) == OsConstants.S_IFLNK
    }.getOrDefault(false)

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory && !isSymbolicLink(file)) file.listFiles()?.forEach(::deleteRecursively)
        if (!file.delete() && file.exists()) throw IllegalStateException("cannot remove ${file.path}")
    }

    private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

    private fun ZipInputStream.readBytes(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count <= 0) break
            total += count
            if (total > maxBytes) throw IllegalArgumentException("runtime manifest is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
