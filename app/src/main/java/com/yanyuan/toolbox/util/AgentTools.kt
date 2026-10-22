package com.yanyuan.toolbox.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.mutableIntStateOf
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.shizuku.ShizukuFileServiceManager
import com.yanyuan.toolbox.runtime.ToolboxRuntimeInstaller
import com.yanyuan.toolbox.ui.FileChangeState
import com.yanyuan.toolbox.ui.FileChangeUi
import java.io.File
import java.util.UUID

/**
 * Agent 的运行环境。环境资格只返回状态，不暴露 API Key 或授权存储内容。
 * 只有导入 API 平台满足启用、模型、地址和平台授权条件时才可用。
 */
class AgentEnvironment private constructor(
    val context: Context,
    private val availability: () -> Boolean
) {
    fun isAvailable(): Boolean = runCatching { availability() }.getOrDefault(false)

    companion object {
        fun importedApi(
            context: Context,
            platformId: () -> String?,
            selectedModelAvailable: (String) -> Boolean,
            apiAuthorized: (String) -> Boolean
        ): AgentEnvironment {
            val appContext = context.applicationContext
            return AgentEnvironment(appContext) {
                val id = platformId()?.trim().orEmpty()
                if (id.isBlank() || !apiAuthorized(id) || !selectedModelAvailable(id)) return@AgentEnvironment false
                val settings = AiModelStore.get(appContext)
                val cfg = settings.configs.firstOrNull { it.platformId == id } ?: return@AgentEnvironment false
                if (!cfg.enabled || (!cfg.platform.keyOptional && cfg.apiKey.isBlank())) return@AgentEnvironment false
                val endpoint = runCatching { java.net.URI(cfg.effectiveBaseUrl) }.getOrNull()
                    ?: return@AgentEnvironment false
                val scheme = endpoint.scheme?.lowercase() ?: return@AgentEnvironment false
                val host = endpoint.host?.lowercase() ?: return@AgentEnvironment false
                val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
                scheme == "https" || (scheme == "http" && localHost)
            }
        }
    }
}

// =======================================================
//  Agent 权限：仅可查看 / 工作区内修改 / 完全权限
// =======================================================
enum class AgentPermission(val id: String, val label: String, val desc: String) {
    VIEW("view", "仅可查看", "AI 可读取文件，但不能修改任何内容"),
    WORKSPACE("workspace", "工作区内修改", "AI 只能在所选工作区内创建或修改文件，每次写入都需确认"),
    FULL("full", "完全访问手机所有内容", "AI 可访问宿主应用实际有权限访问的文件范围；每次修改文件都需确认");

    companion object {
        fun fromId(id: String?): AgentPermission =
            entries.firstOrNull { it.id == id } ?: VIEW
    }
}

object AgentFeatureStore {
    private const val PREF = "agent_feature"
    private const val KEY_ENABLED = "agent_enabled"

    fun get(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun set(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        AgentPermissionState.notifyChanged()
    }
}

object AgentPermissionStore {
    private const val PREF = "agent_permission"
    private const val KEY = "permission"

    fun get(context: Context): AgentPermission =
        AgentPermission.fromId(
            context.applicationContext
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, null)
        )

    fun set(context: Context, permission: AgentPermission) {
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, permission.id).apply()
        AgentPermissionState.notifyChanged()
    }
}

object AgentPermissionState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue
    fun notifyChanged() { _version.intValue = _version.intValue + 1 }
}

// =======================================================
//  Agent 工具执行器：list / read / write（按权限放行）
// =======================================================
object AgentTools {

    /** 未选择外部目录时使用的应用专属工作区。 */
    fun workspaceDir(context: Context): File =
        (AgentWorkspaceStore.resolveFile(context)
            ?: File(context.filesDir, "agent_workspace")).apply { mkdirs() }

    data class WriteApprovalRequest(
        val id: String = UUID.randomUUID().toString(),
        val path: String,
        val content: String,
        val existed: Boolean,
        val byteSize: Int,
        val preview: String
    )

    /** 模型输出的协议块：```agent 后跟一行 JSON（流结束后解析）。 */
    data class AgentAction(
        val action: String,   // list / read / write / exec
        val path: String = "",
        val content: String? = null,
        val program: String = "",
        val args: List<String> = emptyList(),
        val cwd: String = ".",
        val command: String? = null
    )

    data class TerminalApprovalRequest(
        val id: String = UUID.randomUUID().toString(),
        val backend: TerminalBackend,
        val program: String,
        val args: List<String>,
        val cwd: String,
        val timeoutMs: Long = 15_000L,
        val maxOutputBytes: Int = 64 * 1024,
        val shellCommand: String? = null,
        val workdir: String = cwd,
        val runtimeVersion: String? = null
    )

    /** 从回复中提取所有 ```agent JSON``` 协议块。 */
    fun extractActions(reply: String): List<AgentAction> {
        if (reply.length > MAX_AGENT_REPLY_CHARS) return emptyList()
        val regex = Regex("```agent\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val gson = com.google.gson.Gson()
        return regex.findAll(reply).mapNotNull { m ->
            runCatching {
                val obj = gson.fromJson(m.groupValues[1].trim(), com.google.gson.JsonObject::class.java)
                val action = obj.get("action")?.asString?.trim()?.lowercase()
                    ?: return@runCatching null
                if (action !in setOf("list", "read", "write", "exec")) return@runCatching null
                val args = obj.getAsJsonArray("args")?.map { it.asString }.orEmpty()
                AgentAction(
                    action = action,
                    path = obj.get("path")?.asString ?: "",
                    content = obj.get("content")?.asString,
                    program = obj.get("program")?.asString ?: "",
                    args = args,
                    cwd = obj.get("cwd")?.asString ?: ".",
                    command = obj.get("command")?.asString
                )
            }.getOrNull()
        }.take(MAX_ACTIONS_PER_REPLY).toList()
    }

    private const val MAX_ACTIONS_PER_REPLY = 8
    private const val MAX_AGENT_REPLY_CHARS = 1_000_000
    private const val MAX_WRITE_BYTES = 512 * 1024
    private val blockedPhoneRootDirectories = setOf(
        "proc", "sys", "dev", "data", "apex", "vendor"
    )

    suspend fun execute(
        environment: AgentEnvironment,
        action: AgentAction,
        permission: AgentPermission,
        approveWrite: suspend (WriteApprovalRequest) -> Boolean = { false },
        approveTerminal: suspend (TerminalApprovalRequest) -> Boolean = { false }
    ): String {
        val context = environment.context
        if (!AgentFeatureStore.get(context)) {
            return "拒绝执行：Agent 已关闭，仅保留普通聊天。"
        }
        if (!environment.isAvailable()) return "拒绝执行：当前仅导入 API 页面可用，且 API 平台必须保持已授权并可用。"
        if (permission == AgentPermission.WORKSPACE &&
            AgentWorkspaceStore.get(context) != null &&
            AgentWorkspaceStore.resolveFile(context) == null
        ) {
            return "拒绝执行：所选工作区已不可访问，请在 Agent 设置中重新选择目录"
        }
        if (action.action != "exec" && permission == AgentPermission.FULL &&
            !ShizukuFileServiceManager.ensureReady(context)
        ) {
            return "拒绝执行：请先在设置中启动并授权 Shizuku，再使用完全访问模式。"
        }
        return when (action.action) {
            "list" -> listDir(
                context,
                action.path,
                phoneRootOnly = permission == AgentPermission.FULL
            )
            "read" -> readFile(
                context,
                action.path,
                phoneRootOnly = permission == AgentPermission.FULL
            )
            "write" -> writeFile(context, environment, action, permission, approveWrite)
            "exec" -> executeTerminal(context, environment, action, permission, approveTerminal)
            else -> "未知操作：${action.action}"
        }
    }

    suspend fun previewWrite(
        context: Context,
        action: AgentAction,
        permission: AgentPermission
    ): FileChangeUi? {
        val content = action.content ?: return null
        val raw = File(action.path)
        val target = if (permission == AgentPermission.FULL) {
            if (!raw.isAbsolute) return null
            runCatching { raw.canonicalFile }.getOrNull()
        } else {
            val base = workspaceDir(context).canonicalFile
            runCatching { (if (raw.isAbsolute) raw else File(base, action.path)).canonicalFile }.getOrNull()
        } ?: return null
        val allowed = if (permission == AgentPermission.FULL) {
            phoneRootPath(target) != null && !isSensitivePath(context, target)
        } else {
            allowedPath(context, target) != null
        }
        if (!allowed || target.path == File.separator || target.isDirectory) return null
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_WRITE_BYTES) return null
        val old = if (target.isFile && target.length() <= MAX_WRITE_BYTES) {
            runCatching { redactSensitiveContent(target.readText(Charsets.UTF_8)) }.getOrNull()
        } else null
        val safeNew = redactSensitiveContent(content)
        val oldLines = old?.lines().orEmpty()
        val newLines = safeNew.lines()
        val common = oldLines.zip(newLines).count { it.first == it.second }
        val removed = (oldLines.size - common).coerceAtLeast(0)
        val added = (newLines.size - common).coerceAtLeast(0)
        return FileChangeUi(
            id = UUID.randomUUID().toString(),
            path = target.path,
            existed = target.exists(),
            oldText = old,
            newText = safeNew,
            addedLines = added,
            removedLines = removed,
            byteSize = bytes.size
        )
    }

    private suspend fun executeTerminal(
        context: Context,
        environment: AgentEnvironment,
        action: AgentAction,
        permission: AgentPermission,
        approve: suspend (TerminalApprovalRequest) -> Boolean
    ): String {
        if (!AgentTerminalStore.isEnabled(context)) return "拒绝执行：受控终端未启用"
        val workspace = workspaceDir(context).canonicalFile
        val backend = AgentTerminalStore.backend(context)
        val cwd = if (backend == TerminalBackend.BUILTIN_RUNTIME) {
            ToolboxRuntimeInstaller.environmentIfReady(context)?.home
                ?: return "拒绝执行：Toolbox Runtime 尚未就绪"
        } else if (action.cwd.isBlank() || action.cwd == ".") workspace
        else runCatching { File(workspace, action.cwd).canonicalFile }.getOrNull()
            ?: return "拒绝执行：终端工作目录不可用"
        if (backend != TerminalBackend.BUILTIN_RUNTIME && cwd != workspace && !cwd.path.startsWith(workspace.path + File.separator)) {
            return "拒绝执行：终端工作目录必须位于 Agent 工作区内"
        }
        if (permission == AgentPermission.VIEW) return "拒绝执行：当前权限不允许使用终端"
        val shellCommand = if (backend == TerminalBackend.BUILTIN_RUNTIME) {
            action.command?.takeIf { it.isNotBlank() }
                ?: return "拒绝执行：Toolbox Runtime exec 必须提供 command 字段"
        } else null
        if (backend == TerminalBackend.BUILTIN_RUNTIME && (shellCommand!!.length > 8_192 || '\u0000' in shellCommand)) {
            return "拒绝执行：Toolbox Runtime 命令为空或超出 8192 字符限制"
        }
        if (backend != TerminalBackend.BUILTIN_RUNTIME) {
            TerminalPolicy.validate(action.program, action.args, cwd)?.let { return "拒绝执行：$it" }
        }
        if (backend == TerminalBackend.BUILTIN_RUNTIME && ToolboxRuntimeInstaller.environmentIfReady(context) == null) {
            return "拒绝执行：Toolbox Runtime尚未初始化或已损坏"
        }
        val runtimeWorkdir = if (backend == TerminalBackend.BUILTIN_RUNTIME) {
            val requested = action.cwd.trim()
            if (requested.isBlank() || requested == ".") "~"
            else {
                val normalized = requested.removePrefix("./")
                if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("\\")) {
                    return "拒绝执行：Toolbox Runtime 工作目录必须是 Home 下的相对目录"
                }
                "~/$normalized"
            }
        } else null
        val displayCwd = if (backend == TerminalBackend.BUILTIN_RUNTIME) "Toolbox Runtime $runtimeWorkdir" else cwd.path
        val runtimeVersion = if (backend == TerminalBackend.BUILTIN_RUNTIME) ToolboxRuntimeInstaller.installedVersion(context) else null
        val request = TerminalApprovalRequest(
            backend = backend,
            program = if (backend == TerminalBackend.BUILTIN_RUNTIME) "bash" else action.program,
            args = if (backend == TerminalBackend.BUILTIN_RUNTIME) listOf("-lc", shellCommand.orEmpty()) else action.args,
            cwd = displayCwd,
            shellCommand = shellCommand,
            workdir = runtimeWorkdir ?: cwd.path,
            runtimeVersion = runtimeVersion
        )
        if (!approve(request)) return "用户拒绝终端命令：${request.program}"
        if (!AgentFeatureStore.get(context) || !AgentTerminalStore.isEnabled(context) ||
            AgentTerminalStore.backend(context) != request.backend ||
            AgentPermissionStore.get(context) != permission ||
            !environment.isAvailable() || permission == AgentPermission.VIEW
        ) return "拒绝执行：审批后 Agent、API 授权、权限或终端后端状态已改变"
        if (request.backend == TerminalBackend.BUILTIN_RUNTIME) {
            val currentRuntimeVersion = ToolboxRuntimeInstaller.installedVersion(context)
            if (ToolboxRuntimeInstaller.environmentIfReady(context) == null ||
                currentRuntimeVersion == null || currentRuntimeVersion != request.runtimeVersion
            ) return "拒绝执行：审批后 Toolbox Runtime 版本或状态已改变"
        }
        if (request.backend != TerminalBackend.BUILTIN_RUNTIME &&
            TerminalPolicy.validate(request.program, request.args, workspace) != null
        ) return "拒绝执行：审批后终端请求不再符合当前策略"
        if (request.backend != TerminalBackend.BUILTIN_RUNTIME) {
            val currentWorkspace = workspaceDir(context).canonicalFile
            val currentCwd = runCatching { File(request.cwd).canonicalFile }.getOrNull()
                ?: return "拒绝执行：审批后工作目录不可用"
            if (currentWorkspace != workspace || currentCwd != cwd ||
                (currentCwd != currentWorkspace && !currentCwd.path.startsWith(currentWorkspace.path + File.separator))
            ) return "拒绝执行：审批后工作区状态已改变"
        }
        val result = executeControlledTerminal(
            context,
            TerminalRequest(
                id = request.id,
                program = request.program,
                args = request.args,
                cwd = cwd,
                runtimeWorkdir = runtimeWorkdir,
                shellCommand = request.shellCommand,
                runtimeVersion = request.runtimeVersion
            )
        )
        return result.safeText()
    }

    private fun resolveBase(context: Context): List<File> = listOfNotNull(
        runCatching { workspaceDir(context).canonicalFile }.getOrNull()
    )

    private fun allowedPath(context: Context, candidate: File): File? {
        val target = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        val allowed = resolveBase(context).any { base ->
            target == base || target.path.startsWith(base.path + File.separator)
        }
        if (!allowed || isSensitivePath(context, target)) return null
        return target
    }

    /** 不让 Agent 接触可能包含密钥、授权状态或认证材料的路径。 */
    private fun isSensitivePath(context: Context, target: File): Boolean {
        val blockedSegments = setOf(
            "shared_prefs", "databases", "api_secret_store", "api_model_import",
            ".ssh", ".gnupg"
        )
        val segments = target.path.split(File.separatorChar)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        if (segments.any { it in blockedSegments }) return true
        val name = target.name.lowercase()
        return listOf("api_key", "apikey", "secret", "credential", "access_token", "refresh_token")
            .any { it in name }
    }

    private suspend fun listDir(
        context: Context,
        path: String,
        phoneRootOnly: Boolean
    ): String {
        val requested = if (path.isBlank()) {
            if (phoneRootOnly) File(File.separator) else workspaceDir(context)
        } else {
            val raw = File(path)
            if (raw.isAbsolute) raw else File(if (phoneRootOnly) File(File.separator) else workspaceDir(context), path)
        }
        val dir = if (phoneRootOnly) {
            phoneRootPath(requested)
        } else {
            allowedPath(context, requested)
        } ?: return "拒绝读取：路径不在 Agent 可访问目录内"
        if (phoneRootOnly) {
            return redactSensitiveContent(ShizukuFileServiceManager.list(context, dir.path))
        }
        if (!dir.exists()) return "目录不存在"
        if (!dir.isDirectory) return "读取失败：目标不是目录"
        val files = dir.listFiles()
            ?.filterNot {
                isSensitivePath(context, it) || (phoneRootOnly && isBlockedPhonePath(it))
            }
            ?.sortedBy { it.name }
            ?: return "无法读取目录"
        if (files.isEmpty()) return "目录为空"
        return buildString {
            appendLine("目录内容：")
            files.take(200).forEach {
                appendLine("${if (it.isDirectory) "[目录]" else "[文件]"} ${it.name}（${it.length()}B）")
            }
            if (files.size > 200) appendLine("…共 ${files.size} 项")
        }
    }

    private suspend fun readFile(
        context: Context,
        path: String,
        phoneRootOnly: Boolean
    ): String {
        if (path.isBlank()) {
            if (!phoneRootOnly) return "读取失败：缺少 path"
            return listDir(context, File.separator, phoneRootOnly = true)
        }
        val raw = File(path)
        val requested = if (raw.isAbsolute) raw else File(if (phoneRootOnly) File(File.separator) else workspaceDir(context), path)
        val f = if (phoneRootOnly) {
            phoneRootPath(requested)
        } else {
            allowedPath(context, requested)
        } ?: return "拒绝读取：路径不在 Agent 可访问目录内"
        if (isSensitivePath(context, f)) {
            return "拒绝读取：路径包含敏感数据"
        }
        if (phoneRootOnly) {
            return redactSensitiveContent(ShizukuFileServiceManager.read(context, f.path))
        }
        if (!f.exists()) return "文件不存在"
        return if (f.isDirectory) {
            listDir(context = context, path = f.absolutePath, phoneRootOnly = false)
        } else {
            readExact(f)
        }
    }

    private fun isBlockedPhonePath(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return true
        if (path == File.separator) return false
        val firstSegment = path.removePrefix(File.separator)
            .substringBefore(File.separator)
            .lowercase()
        return firstSegment in blockedPhoneRootDirectories
    }

    private fun phoneRootPath(candidate: File): File? {
        val target = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (target.path == File.separator) return target
        val firstSegment = target.path.removePrefix(File.separator)
            .substringBefore(File.separator)
            .lowercase()
        return target.takeIf { firstSegment.isNotBlank() && firstSegment !in blockedPhoneRootDirectories }
    }

    private fun readExact(f: File): String {
        if (f.isDirectory) return "读取失败：目录必须通过受限目录列表访问"
        val size = runCatching { f.length() }.getOrDefault(0L)
        if (size > 512 * 1024) {
            val head = runCatching {
                f.inputStream().use { ins ->
                    val buf = ByteArray(65536)
                    val n = ins.read(buf)
                    String(buf, 0, n.coerceAtLeast(0), Charsets.UTF_8)
                }
            }.getOrDefault("（读取失败）")
            return "文件过大（${size / 1024}KB > 512KB），仅返回前 64KB：\n${redactSensitiveContent(head)}"
        }
        return runCatching { redactSensitiveContent(f.readText(Charsets.UTF_8)) }
            .getOrElse { "读取失败（可能是二进制或无权访问）" }
    }

    private fun redactSensitiveContent(text: String): String {
        val assignment = Regex(
            "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|bearer|client[_-]?secret|password|secret[_-]?key)\\s*[:=]\\s*([\\\"']?)[^\\s,;\\\"']+"
        )
        val knownKey = Regex("(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{12,}|AIza[0-9A-Za-z_-]{20,})\\b")
        return text.lineSequence().joinToString("\n") { line ->
            knownKey.replace(assignment.replace(line) { m -> "${m.groupValues[1]}=[已隐藏]" }, "[已隐藏密钥]")
        }.take(512 * 1024)
    }

    private suspend fun writeFile(
        context: Context,
        environment: AgentEnvironment,
        action: AgentAction,
        permission: AgentPermission,
        approveWrite: suspend (WriteApprovalRequest) -> Boolean
    ): String {
        val path = action.path
        val content = action.content ?: return "写入失败：缺少 content"
        if (path.isBlank()) return "写入失败：缺少 path"
        val byteSize = content.toByteArray(Charsets.UTF_8).size
        if (byteSize > MAX_WRITE_BYTES) {
            return "写入失败：内容超过 ${MAX_WRITE_BYTES / 1024}KB 限制"
        }
        if (permission == AgentPermission.VIEW) {
            return "拒绝写入：当前为「仅可查看」模式，请在 Agent 设置中调整权限。"
        }

        val ws = workspaceDir(context).canonicalFile
        val raw = File(path)
        val target = if (permission == AgentPermission.FULL) {
            if (!raw.isAbsolute) return "拒绝写入：完全访问模式要求使用绝对路径"
            runCatching { raw.canonicalFile }.getOrElse {
                return "拒绝写入：目标路径不可用"
            }
        } else {
            runCatching { (if (raw.isAbsolute) raw else File(ws, path)).canonicalFile }.getOrElse {
                return "拒绝写入：目标路径不可用"
            }
        }
        val allowed = if (permission == AgentPermission.FULL) {
            phoneRootPath(target) != null && !isSensitivePath(context, target)
        } else {
            target != ws && target.path.startsWith(ws.path + File.separator) &&
                    !isSensitivePath(context, target)
        }
        if (!allowed) {
            return if (permission == AgentPermission.FULL) {
                "拒绝写入：目标不在宿主应用可访问的手机内容范围内"
            } else {
                "拒绝写入：只能写入所选工作区内的文件"
            }
        }
        val displayPath = if (permission == AgentPermission.FULL) {
            target.path.substringAfterLast(File.separator).ifBlank { "手机内容" }
        } else target.path.replace(ws.path, "工作区")
        val existed = if (permission == AgentPermission.FULL) {
            ShizukuFileServiceManager.exists(context, target.path)
        } else {
            target.exists()
        }
        val request = WriteApprovalRequest(
            path = displayPath,
            content = content,
            existed = existed,
            byteSize = byteSize,
            preview = redactSensitiveContent(content.take(2_000))
        )
        if (!approveWrite(request)) return "用户拒绝写入：${request.path}"

        // 用户等待确认期间权限或 Agent 环境可能已经变化，写入前再次校验。
        if (!environment.isAvailable()) return "拒绝写入：Agent 环境已不可用"
        val currentPermission = AgentPermissionStore.get(context)
        if (currentPermission == AgentPermission.VIEW) {
            return "拒绝写入：当前 Agent 权限不允许修改文件"
        }
        val verifiedWorkspace = workspaceDir(context).canonicalFile
        val verifiedRaw = File(path)
        val verifiedTarget = if (currentPermission == AgentPermission.FULL) {
            if (!verifiedRaw.isAbsolute) return "拒绝写入：确认后目标必须是绝对路径"
            runCatching { verifiedRaw.canonicalFile }.getOrElse {
                return "拒绝写入：确认后目标路径不可用"
            }
        } else {
            runCatching {
                (if (verifiedRaw.isAbsolute) verifiedRaw else File(verifiedWorkspace, path)).canonicalFile
            }.getOrElse {
                return "拒绝写入：确认后目标路径不可用"
            }
        }
        val verifiedAllowed = if (currentPermission == AgentPermission.FULL) {
            phoneRootPath(verifiedTarget) != null && !isSensitivePath(context, verifiedTarget)
        } else {
            verifiedTarget != verifiedWorkspace &&
                    verifiedTarget.path.startsWith(verifiedWorkspace.path + File.separator) &&
                    !isSensitivePath(context, verifiedTarget)
        }
        if (!verifiedAllowed || content.toByteArray(Charsets.UTF_8).size > MAX_WRITE_BYTES) {
            return "拒绝写入：确认后目标或权限已改变"
        }

        if (currentPermission == AgentPermission.FULL) {
            if (!ShizukuFileServiceManager.ensureReady(context)) {
                return "拒绝写入：Shizuku 文件后端已不可用"
            }
            return ShizukuFileServiceManager.write(context, verifiedTarget.path, content)
        }

        return try {
            verifiedTarget.parentFile?.mkdirs()
            verifiedTarget.writeText(content, Charsets.UTF_8)
            "已写入（工作区内）：工作区/${verifiedTarget.relativeTo(verifiedWorkspace).path}（${verifiedTarget.length()}B）"
        } catch (_: Exception) {
            "写入失败：无法写入目标文件"
        }
    }
}
