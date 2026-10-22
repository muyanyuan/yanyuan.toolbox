package com.yanyuan.toolbox.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FeatureRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yanyuan_plugins", Context.MODE_PRIVATE)

    fun listInstalled(): List<InstalledPlugin> {
        val raw = prefs.getString(KEY_PLUGINS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val manifest = PluginManifest.fromJson(obj.getJSONObject("manifest"))
                val iconPath = obj.optString("iconPath", "").takeIf { it.isNotEmpty() }
                val iconFile = iconPath?.let { File(it) }?.takeIf { it.exists() }
                InstalledPlugin(manifest, iconFile)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun isInstalled(id: String): Boolean = listInstalled().any { it.manifest.id == id }

    /**
     * ★ 按给定顺序持久化已装插件，用于功能页卡片的拖动排序。
     *   只接受已知插件，未列出的排在后面，避免因为一次排序把插件弄丢。
     */
    fun reorder(orderedIds: List<String>) {
        val cur = listInstalled()
        if (cur.isEmpty()) return
        val byId = cur.associateBy { it.manifest.id }
        val ordered = orderedIds.mapNotNull { byId[it] }
        val rest = cur.filterNot { p -> orderedIds.contains(p.manifest.id) }
        val next = ordered + rest
        // 顺序没变就不写盘，避免无意义的重组与 I/O
        if (next.map { it.manifest.id } == cur.map { it.manifest.id }) return
        save(next)
    }

    fun install(plugin: InstalledPlugin) {
        val cur = listInstalled().toMutableList()
        cur.removeAll { it.manifest.id == plugin.manifest.id }
        cur.add(plugin)
        save(cur)
    }

    /**
     * 卸载插件：删除插件目录、图标、以及所有关联的 SharedPreferences。
     * 这样插件的运行数据、配置全部清干净，服务也会因为插件列表变化而停止。
     */
    fun uninstall(id: String) {
        val cur = listInstalled().toMutableList()
        cur.find { it.manifest.id == id }?.iconFile?.delete()
        cur.removeAll { it.manifest.id == id }
        save(cur)

        // 删除插件资源目录
        try {
            File(appContext.filesDir, "plugins/$id").deleteRecursively()
        } catch (_: Exception) {}

        // 清理该插件的所有 SharedPreferences
        try {
            PluginRulesStore(appContext, id).clear()
            appContext.getSharedPreferences("plugin_storage_$id", Context.MODE_PRIVATE)
                .edit().clear().apply()
            appContext.getSharedPreferences("plugin_first_run", Context.MODE_PRIVATE)
                .edit().remove("guided_$id").apply()
        } catch (_: Exception) {}
    }

    private fun save(list: List<InstalledPlugin>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("manifest", p.manifest.toJson())
            p.iconFile?.let { obj.put("iconPath", it.absolutePath) }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PLUGINS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_PLUGINS = "installed_plugins"
    }
}
