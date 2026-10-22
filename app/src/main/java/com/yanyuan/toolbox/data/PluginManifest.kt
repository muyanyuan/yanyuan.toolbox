package com.yanyuan.toolbox.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PluginManifest(
    val id: String,
    val name: String,
    val description: String,
    /**
     * 插件作者。manifest 里没有 `author` 字段时为空串，
     * 卡片按「缺失则不显示」处理，保证对老插件兼容。
     */
    val author: String,
    val version: String,
    val type: String,
    val entry: String,
    val automationEngineEntry: String?,
    val orientation: String,
    val fullscreen: Boolean,
    val keepScreenOn: Boolean,
    val capabilities: Set<String>,
    /** 插件是否内置开关。true → 功能页卡片显示 Switch；false → 不显示，点击卡片直接进 */
    val hasSwitch: Boolean,
    val iconEntryName: String?,
    val permissions: List<PermissionRequirement>
) {
    /** 是否有作者信息可展示 */
    val hasAuthor: Boolean get() = author.isNotBlank()

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("description", description)
        if (author.isNotBlank()) obj.put("author", author)
        obj.put("version", version)
        obj.put("type", type)
        obj.put("entry", entry)
        automationEngineEntry?.let { obj.put("automationEngine", it) }
        obj.put("orientation", orientation)
        obj.put("fullscreen", fullscreen)
        obj.put("keepScreenOn", keepScreenOn)
        obj.put("capabilities", JSONArray(capabilities.toList()))
        obj.put("hasSwitch", hasSwitch)
        iconEntryName?.let { obj.put("icon", it) }

        val arr = JSONArray()
        permissions.forEach { p ->
            val po = JSONObject()
            when (p) {
                is PermissionRequirement.Runtime -> {
                    po.put("kind", "runtime")
                    po.put("permission", p.permission)
                    po.put("label", p.label)
                    po.put("description", p.description)
                }
                is PermissionRequirement.Special -> {
                    po.put("kind", "special")
                    po.put("special", p.type.name)
                    po.put("label", p.label)
                    po.put("description", p.description)
                }
            }
            arr.put(po)
        }
        obj.put("permissions", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): PluginManifest {
            val perms = mutableListOf<PermissionRequirement>()
            val arr = obj.optJSONArray("permissions") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val po = arr.getJSONObject(i)
                val kind = po.optString("kind")
                val label = po.optString("label", "")
                val desc = po.optString("description", "")
                when (kind) {
                    "runtime" -> perms.add(
                        PermissionRequirement.Runtime(po.optString("permission", ""), label, desc)
                    )
                    "special" -> {
                        val t = try {
                            SpecialType.valueOf(po.optString("special", ""))
                        } catch (_: Exception) { null }
                        if (t != null) perms.add(PermissionRequirement.Special(t, label, desc))
                    }
                }
            }

            val caps = mutableSetOf<String>()
            val capsArr = obj.optJSONArray("capabilities") ?: JSONArray()
            for (i in 0 until capsArr.length()) caps.add(capsArr.getString(i))

            return PluginManifest(
                id = obj.optString("id", ""),
                name = obj.optString("name", ""),
                description = obj.optString("description", ""),
                // 老插件没有 author 字段 → 空串 → 卡片不显示作者行
                author = obj.optString("author", ""),
                version = obj.optString("version", "1.0"),
                type = obj.optString("type", "web"),
                entry = obj.optString("entry", "index.html"),
                automationEngineEntry = obj.optString("automationEngine", "")
                    .takeIf { it.isNotBlank() },
                orientation = obj.optString("orientation", "auto"),
                fullscreen = obj.optBoolean("fullscreen", false),
                keepScreenOn = obj.optBoolean("keepScreenOn", false),
                capabilities = caps,
                hasSwitch = obj.optBoolean("hasSwitch", false),
                iconEntryName = obj.optString("icon", "").takeIf { it.isNotEmpty() },
                permissions = perms
            )
        }
    }
}

data class InstalledPlugin(
    val manifest: PluginManifest,
    val iconFile: File?
)
