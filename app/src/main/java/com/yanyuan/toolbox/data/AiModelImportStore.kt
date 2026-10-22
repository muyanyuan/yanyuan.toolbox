package com.yanyuan.toolbox.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// =======================================================
//  大模型平台
// =======================================================
/**
 * 支持的模型平台。
 * baseUrl 是「选中平台后直接带出来」的默认 API 地址，用户可以再改。
 */
enum class AiModelPlatform(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    /** 获取密钥的页面，用于「获取密钥」按钮 */
    val consoleUrl: String
) {
    DEEPSEEK(
        "deepseek", "DeepSeek",
        "https://api.deepseek.com",
        "https://platform.deepseek.com/api_keys"
    ),
    MIMO(
        "mimo", "小米 MiMo",
        "https://api.xiaomimimo.com/v1",
        "https://platform.xiaomimimo.com/"
    ),
    OPENAI(
        "openai", "OpenAI",
        "https://api.openai.com/v1",
        "https://platform.openai.com/api-keys"
    ),
    MOONSHOT(
        "moonshot", "Moonshot 月之暗面",
        "https://api.moonshot.cn/v1",
        "https://platform.moonshot.cn/console/api-keys"
    ),
    ZHIPU(
        "zhipu", "智谱 GLM",
        "https://open.bigmodel.cn/api/paas/v4",
        "https://open.bigmodel.cn/usercenter/apikeys"
    ),
    DASHSCOPE(
        "dashscope", "通义千问",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "https://bailian.console.aliyun.com/?apiKey=1"
    ),
    SILICONFLOW(
        "siliconflow", "SiliconFlow 硅基流动",
        "https://api.siliconflow.cn/v1",
        "https://cloud.siliconflow.cn/account/ak"
    ),
    GEMINI(
        "gemini", "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://aistudio.google.com/app/apikey"
    ),
    OPENROUTER(
        "openrouter", "OpenRouter",
        "https://openrouter.ai/api/v1",
        "https://openrouter.ai/keys"
    ),
    OLLAMA(
        "ollama", "Ollama（本地）",
        "http://127.0.0.1:11434/v1",
        "https://ollama.com/download"
    ),
    CUSTOM(
        "custom", "自定义",
        "",
        ""
    );

    /** 本地部署的平台不需要密钥 */
    val keyOptional: Boolean get() = this == OLLAMA

    companion object {
        fun fromId(id: String?): AiModelPlatform =
            entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}

// =======================================================
//  用户接入的模型
// =======================================================
data class AiModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val platformId: String = AiModelPlatform.DEEPSEEK.id,
    /** 用户填写的 API 地址；为空时用平台的默认地址 */
    val baseUrl: String = "",
    val apiKey: String = "",
    /** 带 `-` 前缀表示被用户隐藏（对应图里的「—」按钮） */
    val modelId: String = ""
) {
    val platform: AiModelPlatform get() = AiModelPlatform.fromId(platformId)

    /** 实际请求用的地址 */
    val effectiveBaseUrl: String
        get() = baseUrl.ifBlank { platform.baseUrl }

    val hidden: Boolean get() = modelId.startsWith("-")
    val cleanModelId: String get() = modelId.removePrefix("-")
    val displayName: String get() = cleanModelId.ifBlank { "（未命名模型）" }
}

/** 每个平台的接入配置（密钥 / 地址 / 是否启用） */
data class AiPlatformConfig(
    val platformId: String = AiModelPlatform.DEEPSEEK.id,
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    /** 由 API 服务商/模型自行联网；普通兼容接口默认关闭。 */
    val nativeWebSearch: Boolean = false
) {
    val platform: AiModelPlatform get() = AiModelPlatform.fromId(platformId)
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { platform.baseUrl }
}

data class AiModelSettings(
    val configs: List<AiPlatformConfig> = emptyList(),
    val models: List<AiModelEntry> = emptyList()
) {
    fun configFor(platformId: String): AiPlatformConfig =
        configs.firstOrNull { it.platformId == platformId }
            ?: AiPlatformConfig(platformId = platformId)

    fun modelsOf(platformId: String): List<AiModelEntry> =
        models.filter { it.platformId == platformId }
}

// =======================================================
//  存储
// =======================================================
object AiModelStore {
    private const val PREF = "ai_model_import"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_SECRET_REVISION = "api_secret_revision"
    private const val SECRET_PREF = "api_secret_store"

    private val gson = Gson()
    private val settingsType = object : TypeToken<AiModelSettings>() {}.type

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun secretPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(SECRET_PREF, Context.MODE_PRIVATE)

    fun get(context: Context): AiModelSettings {
        val preferences = prefs(context)
        val json = preferences.getString(KEY_SETTINGS, null) ?: return AiModelSettings()
        val stored = runCatching {
            gson.fromJson<AiModelSettings>(json, settingsType) ?: AiModelSettings()
        }.getOrElse { return AiModelSettings() }

        val revision = preferences.getString(KEY_SECRET_REVISION, null)
        if (revision == null) {
            val normalized = normalizeLegacySettings(stored)
            if (hasPlaintextKeys(normalized)) runCatching { save(context, normalized) }
            return normalized
        }

        val secrets = secretPrefs(context)
        val configs = stored.configs.map { config ->
                config.copy(apiKey = readSecret(secrets.getString(secretKey(revision, "config", config.platformId), null)))
            }
        val keysByPlatform = configs.associate { it.platformId to it.apiKey }
        return stored.copy(
            configs = configs,
            models = stored.models.map { model ->
                model.copy(apiKey = keysByPlatform[model.platformId].orEmpty())
            }
        )
    }

    fun set(context: Context, settings: AiModelSettings) {
        if (runCatching { save(context, settings) }.getOrDefault(false)) {
            AiModelState.notifyChanged()
        }
    }

    private fun save(context: Context, settings: AiModelSettings): Boolean {
        val normalized = normalizeLegacySettings(settings)
        val revision = UUID.randomUUID().toString()
        val secretValues = mutableMapOf<String, String>()
        normalized.configs.forEach { config ->
            if (config.apiKey.isNotBlank()) {
                secretValues[secretKey(revision, "config", config.platformId)] = ApiKeyCipher.encrypt(config.apiKey)
            }
        }

        val secretPreferences = secretPrefs(context)
        val previousRevision = prefs(context).getString(KEY_SECRET_REVISION, null)
        val secretEditor = secretPreferences.edit()
        secretValues.forEach { (key, value) -> secretEditor.putString(key, value) }
        if (!secretEditor.commit()) return false

        val settingsWithoutKeys = normalized.copy(
            configs = normalized.configs.map { it.copy(apiKey = "") },
            models = normalized.models.map { it.copy(apiKey = "") }
        )
        val stored = prefs(context).edit()
            .putString(KEY_SETTINGS, gson.toJson(settingsWithoutKeys, settingsType))
            .putString(KEY_SECRET_REVISION, revision)
        if (!stored.commit()) return false

        if (previousRevision != null) {
            val prefix = "$previousRevision|"
            val cleanup = secretPreferences.edit()
            secretPreferences.all.keys.filter { it.startsWith(prefix) }.forEach { cleanup.remove(it) }
            cleanup.apply()
        }
        return true
    }

    private fun hasPlaintextKeys(settings: AiModelSettings): Boolean =
        settings.configs.any { it.apiKey.isNotBlank() && !ApiKeyCipher.isEncrypted(it.apiKey) } ||
                settings.models.any { it.apiKey.isNotBlank() && !ApiKeyCipher.isEncrypted(it.apiKey) }

    private fun normalizeLegacySettings(settings: AiModelSettings): AiModelSettings {
        val configs = settings.configs.toMutableList()
        settings.models.forEach { model ->
            if (model.apiKey.isBlank()) return@forEach
            val index = configs.indexOfFirst { it.platformId == model.platformId }
            if (index < 0) {
                configs.add(AiPlatformConfig(platformId = model.platformId, apiKey = model.apiKey))
            } else if (configs[index].apiKey.isBlank()) {
                configs[index] = configs[index].copy(apiKey = model.apiKey)
            }
        }
        val keysByPlatform = configs.associate { it.platformId to it.apiKey }
        return settings.copy(
            configs = configs,
            models = settings.models.map { model ->
                model.copy(apiKey = keysByPlatform[model.platformId].orEmpty())
            }
        )
    }

    private fun readSecret(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return if (ApiKeyCipher.isEncrypted(value)) {
            runCatching { ApiKeyCipher.decrypt(value) }.getOrDefault("")
        } else value
    }

    private fun secretKey(revision: String, kind: String, id: String): String =
        "$revision|$kind|$id"
}

object AiModelState {
    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue
    fun notifyChanged() { _version.intValue = _version.intValue + 1 }
}
