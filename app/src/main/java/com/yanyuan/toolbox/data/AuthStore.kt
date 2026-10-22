package com.yanyuan.toolbox.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf

// =======================================================
//  授权（AI 接口的开放范围）
// =======================================================

private const val IMPORTED_MODEL_AUTH_PREFIX = "v2:"
private const val IMPORTED_API_AUTH_PREFIX = "v3:"

private fun encodeAuthPart(value: String): String =
    Base64.encodeToString(value.trim().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

/** 导入 API 的平台级授权键，与内置模型 ID 完全隔离。 */
private fun importedApiKey(platformId: String): String =
    "$IMPORTED_API_AUTH_PREFIX${encodeAuthPart(platformId)}"

/**
 * 授权的是「导入大模型」里配置的 AI 接口可以被谁使用。
 *
 *  - pluginAuthorizedIds：按插件 ID 单独授权，插件名称由已安装插件列表动态读取
 *  - programEnabled：程序级联网授权开关
 *  - programApiId/programModelId：唯一的程序级 API/模型绑定
 *  - aiAuthorizedModels：内置模型授权；导入 API 则按平台授权
 */
data class AuthSettings(
    val pluginAuthorizedIds: Set<String> = emptySet(),
    val programEnabled: Boolean = false,
    val programApiId: String? = null,
    val programModelId: String? = null,
    val aiAuthorizedModels: Set<String> = emptySet(),
    val aiAuthorizedApis: Set<String> = emptySet()
) {
    /** 旧版兼容：插件总开关语义已废弃。 */
    @Deprecated("插件授权已改为按插件 ID 单独管理")
    val plugin: Boolean
        get() = pluginAuthorizedIds.isNotEmpty()

    /** 旧版兼容：程序授权现在由独立开关和精确绑定共同决定。 */
    @Deprecated("请使用 isProgramUsable")
    val program: Boolean
        get() = isProgramUsable

    @Deprecated("请使用 isProgramUsable")
    val newAiPage: Boolean
        get() = isProgramUsable

    val hasProgramBinding: Boolean
        get() = !programApiId.isNullOrBlank() && !programModelId.isNullOrBlank()

    val isProgramUsable: Boolean
        get() = programEnabled && hasProgramBinding

    fun isProgramBinding(apiId: String, modelId: String): Boolean =
        hasProgramBinding && programApiId == apiId && programModelId == modelId

    fun withProgramBinding(apiId: String, modelId: String): AuthSettings =
        copy(programApiId = apiId.trim(), programModelId = modelId.trim())

    fun withProgramEnabled(enabled: Boolean): AuthSettings =
        if (enabled && !hasProgramBinding) copy(programEnabled = false)
        else copy(programEnabled = enabled)

    fun withoutProgramBinding(): AuthSettings =
        copy(programEnabled = false, programApiId = null, programModelId = null)

    fun isPluginAuthorized(pluginId: String): Boolean =
        pluginAuthorizedIds.contains(pluginId)

    /** 内置模型仍使用其原有的单字符串授权键。 */
    fun isBuiltinAiModelAuthorized(modelId: String): Boolean =
        aiAuthorizedModels.contains(modelId)

    /** 导入 API 按平台授权；平台下所有模型共用此状态。 */
    fun isImportedApiAuthorized(platformId: String): Boolean =
        aiAuthorizedApis.contains(importedApiKey(platformId))

    fun withImportedApiAuthorization(platformId: String, authorized: Boolean): AuthSettings {
        val next = aiAuthorizedApis.toMutableSet()
        val key = importedApiKey(platformId)
        if (authorized) next.add(key) else next.remove(key)
        return copy(aiAuthorizedApis = next)
    }

    fun withBuiltinAiModelAuthorization(modelId: String, authorized: Boolean): AuthSettings =
        withAiAuthorization(modelId, authorized)

    /** 旧版本模型级 API，仅保留读取兼容，不再用于业务授权。 */
    @Deprecated("导入 API 已改为按平台授权")
    fun isImportedAiModelAuthorized(platformId: String, modelId: String): Boolean =
        isImportedApiAuthorized(platformId)

    @Deprecated("导入 API 已改为按平台授权")
    fun withImportedAiModelAuthorization(
        platformId: String,
        modelId: String,
        authorized: Boolean
    ): AuthSettings = withImportedApiAuthorization(platformId, authorized)

    /** 保留旧调用的兼容入口；新代码应明确使用内置或导入模型方法。 */
    @Deprecated("请明确使用内置或导入模型授权方法")
    fun isAiModelAuthorized(modelKey: String): Boolean =
        aiAuthorizedModels.contains(modelKey)

    @Deprecated("请明确使用内置或导入模型授权方法")
    fun withAiModelAuthorization(modelKey: String, authorized: Boolean): AuthSettings =
        withAiAuthorization(modelKey, authorized)

    private fun withAiAuthorization(key: String, authorized: Boolean): AuthSettings {
        val next = aiAuthorizedModels.toMutableSet()
        if (authorized) next.add(key) else next.remove(key)
        return copy(aiAuthorizedModels = next)
    }

    fun withPluginAuthorization(
        pluginId: String,
        authorized: Boolean
    ): AuthSettings {
        val next = pluginAuthorizedIds.toMutableSet()
        if (authorized) next.add(pluginId) else next.remove(pluginId)
        return copy(pluginAuthorizedIds = next)
    }
}

object AuthStore {
    private const val PREF = "auth_pref"

    private const val K_PLUGIN = "auth_plugin" // 历史总开关，已不再参与授权
    private const val K_PLUGIN_IDS = "auth_plugin_ids"
    private const val K_PROGRAM = "auth_program" // 历史布尔程序授权，已不再单独生效
    private const val K_NEW_AI_PAGE = "auth_new_ai_page" // 历史程序授权，已不再单独生效
    private const val K_PROGRAM_ENABLED = "auth_program_enabled"
    private const val K_PROGRAM_API = "auth_program_api"
    private const val K_PROGRAM_MODEL = "auth_program_model"
    private const val K_AI_MODELS = "auth_ai_models" // 内置模型授权及待迁移的旧导入授权
    private const val K_AI_APIS = "auth_ai_apis" // 导入 API 平台授权

    fun get(context: Context): AuthSettings {
        val appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val ids = p.getStringSet(K_PLUGIN_IDS, emptySet())
            ?.toSet()
            ?: emptySet()

        val programApiId = p.getString(K_PROGRAM_API, null)?.trim()?.takeIf { it.isNotBlank() }
        val programModelId = p.getString(K_PROGRAM_MODEL, null)?.trim()?.takeIf { it.isNotBlank() }
        val normalizedProgramApi = if (programApiId != null && programModelId != null) {
            programApiId
        } else {
            null
        }
        val normalizedProgramModel = if (normalizedProgramApi != null) programModelId else null
        val programEnabled = p.getBoolean(K_PROGRAM_ENABLED, false) &&
                normalizedProgramApi != null && normalizedProgramModel != null

        val modelKeys = p.getStringSet(K_AI_MODELS, emptySet())?.toSet() ?: emptySet()
        val apiKeys = p.getStringSet(K_AI_APIS, emptySet())?.toSet() ?: emptySet()
        val migrated = migrateImportedApiAuthorizations(appContext, modelKeys, apiKeys)
        if (migrated.first != modelKeys || migrated.second != apiKeys) {
            p.edit()
                .putStringSet(K_AI_MODELS, migrated.first)
                .putStringSet(K_AI_APIS, migrated.second)
                .apply()
        }

        return AuthSettings(
            pluginAuthorizedIds = ids,
            programEnabled = programEnabled,
            programApiId = normalizedProgramApi,
            programModelId = normalizedProgramModel,
            aiAuthorizedModels = migrated.first,
            aiAuthorizedApis = migrated.second
        )
    }

    private fun migrateImportedApiAuthorizations(
        context: Context,
        modelKeys: Set<String>,
        apiKeys: Set<String>
    ): Pair<Set<String>, Set<String>> {
        val knownPlatformIds = AiModelPlatform.entries.map { it.id }.toSet()
        val importedPlatformIds = AiModelStore.get(context).configs
            .map { it.platformId }
            .filter { it in knownPlatformIds }
            .toSet()
        val remainingModels = modelKeys.toMutableSet()
        val migratedApis = apiKeys.toMutableSet()

        modelKeys.forEach { key ->
            when {
                key.startsWith(IMPORTED_MODEL_AUTH_PREFIX) -> {
                    val parts = key.removePrefix(IMPORTED_MODEL_AUTH_PREFIX).split(':', limit = 2)
                    if (parts.size == 2) {
                        val platformId = runCatching {
                            String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                        }.getOrNull()
                        platformId
                            ?.takeIf { it in knownPlatformIds }
                            ?.let { knownPlatformId ->
                                migratedApis.add(importedApiKey(knownPlatformId))
                                remainingModels.remove(key)
                            }
                    }
                }
                key in importedPlatformIds -> {
                    // 只有当前确实配置过导入平台时，才把旧裸平台 ID 迁移为 API 授权；
                    // 同名的内置模型授权记录继续留在 aiAuthorizedModels 中。
                    migratedApis.add(importedApiKey(key))
                }
            }
        }
        return remainingModels to migratedApis
    }

    fun set(context: Context, s: AuthSettings) {
        val editor = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(K_PLUGIN_IDS, s.pluginAuthorizedIds)
            .putStringSet(K_AI_MODELS, s.aiAuthorizedModels)
            .putStringSet(K_AI_APIS, s.aiAuthorizedApis)
            .putBoolean(K_PROGRAM_ENABLED, s.isProgramUsable)
        if (s.hasProgramBinding) {
            editor.putString(K_PROGRAM_API, s.programApiId)
                .putString(K_PROGRAM_MODEL, s.programModelId)
        } else {
            editor.remove(K_PROGRAM_API).remove(K_PROGRAM_MODEL)
        }
        editor.apply()

        AuthState.notifyChanged()
    }
}

object AuthState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue

    fun notifyChanged() {
        _version.intValue = _version.intValue + 1
    }
}
