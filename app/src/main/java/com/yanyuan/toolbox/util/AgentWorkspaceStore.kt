package com.yanyuan.toolbox.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Agent 工作区配置。只保存 SAF URI 和显示名称，不保存文件内容或凭据。
 */
object AgentWorkspaceStore {
    private const val PREF = "agent_workspace"
    private const val URI_KEY = "tree_uri"
    private const val NAME_KEY = "display_name"

    data class Selection(val uri: String, val displayName: String)

    fun get(context: Context): Selection? {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val uri = prefs.getString(URI_KEY, null)?.trim().orEmpty()
        return uri.takeIf { it.isNotBlank() }?.let {
            Selection(it, prefs.getString(NAME_KEY, "已选择目录") ?: "已选择目录")
        }
    }

    fun set(context: Context, uri: Uri, displayName: String) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(URI_KEY, uri.toString())
            .putString(NAME_KEY, displayName.take(120))
            .apply()
        AgentPermissionState.notifyChanged()
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        val old = get(app)
        if (old != null) {
            runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old.uri), 3) }
        }
        app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        AgentPermissionState.notifyChanged()
    }

    /**
     * 对 primary external storage 的 SAF 目录返回真实路径；其他 provider 返回 null。
     * 当前 Agent 文件工具基于 File API，因此不支持的 provider 必须在选择时拒绝，
     * 不能让界面显示外部目录、实际却静默写入应用私有工作区。
     */
    fun resolveFile(context: Context): File? = get(context)?.let { selection ->
        resolveFile(context, runCatching { Uri.parse(selection.uri) }.getOrNull())
    }

    fun resolveFile(context: Context, uri: Uri?): File? {
        if (uri == null || uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val split = docId.split(':', limit = 2)
        if (split.size != 2 || !split[0].equals("primary", ignoreCase = true)) return null
        val relative = split[1].trim('/')
        val root = runCatching { File("/storage/emulated/0").canonicalFile }.getOrNull()
            ?: return null
        val candidate = runCatching {
            (if (relative.isBlank()) root else File(root, relative)).canonicalFile
        }.getOrNull() ?: return null
        val insideRoot = candidate == root ||
                candidate.path.startsWith(root.path + File.separator)
        return candidate.takeIf { insideRoot && it.exists() && it.isDirectory }
    }
}
