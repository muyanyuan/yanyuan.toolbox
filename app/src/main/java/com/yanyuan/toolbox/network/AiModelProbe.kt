package com.yanyuan.toolbox.network

import com.google.gson.JsonParser
import com.yanyuan.toolbox.data.AiModelPlatform
import com.yanyuan.toolbox.data.AiPlatformConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 大模型平台连通性检测 / 模型列表拉取。
 *
 * 走 OpenAI 兼容的 `GET {baseUrl}/models`：
 *  - 返回 200 且解析出 data[].id → 认为密钥和地址都可用
 *  - 401 / 403 → 密钥无效
 *  - 其它 → 原样报出状态码，便于用户判断地址是否写错
 */
object AiModelProbe {

    data class Result(
        val ok: Boolean,
        val message: String,
        val models: List<String> = emptyList()
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun endpoint(baseUrl: String): String {
        val b = baseUrl.trim().trimEnd('/')
        return if (b.isEmpty()) "" else "$b/models"
    }

    private fun request(cfg: AiPlatformConfig): Request? {
        val url = endpoint(cfg.effectiveBaseUrl)
        if (url.isBlank()) return null
        val b = Request.Builder()
            .url(url)
            .header("accept", "application/json")
            .get()
        if (cfg.apiKey.isNotBlank()) {
            b.header("Authorization", "Bearer ${cfg.apiKey.trim()}")
        }
        return b.build()
    }

    suspend fun testConnection(
        platform: AiModelPlatform,
        cfg: AiPlatformConfig
    ): Result = withContext(Dispatchers.IO) {
        val req = request(cfg)
            ?: return@withContext Result(false, "请先填写 API 地址")
        if (cfg.apiKey.isBlank() && !platform.keyOptional) {
            return@withContext Result(false, "请先填写 API 密钥")
        }
        runCatching {
            client.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> Result(true, "连接成功（HTTP ${r.code}）")
                    r.code == 401 || r.code == 403 ->
                        Result(false, "密钥无效或没有权限（HTTP ${r.code}）")
                    r.code == 404 ->
                        Result(false, "地址不对：该地址下没有 /models（HTTP 404）")
                    else -> Result(false, "请求失败：HTTP ${r.code}")
                }
            }
        }.getOrElse { e ->
            Result(false, "网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun fetchModels(
        platform: AiModelPlatform,
        cfg: AiPlatformConfig
    ): Result = withContext(Dispatchers.IO) {
        val req = request(cfg)
            ?: return@withContext Result(false, "请先填写 API 地址")
        if (cfg.apiKey.isBlank() && !platform.keyOptional) {
            return@withContext Result(false, "请先填写 API 密钥")
        }
        runCatching {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    val hint = when (r.code) {
                        401, 403 -> "密钥无效或没有权限"
                        404 -> "地址不对：该地址下没有 /models"
                        else -> "请求失败"
                    }
                    return@use Result(false, "$hint（HTTP ${r.code}）")
                }
                val text = r.body?.string().orEmpty()
                val ids = parseModelIds(text)
                if (ids.isEmpty()) {
                    Result(false, "已连通，但没有解析到模型列表")
                } else {
                    Result(true, "同步成功，共 ${ids.size} 个模型", ids)
                }
            }
        }.getOrElse { e ->
            Result(false, "网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** 兼容 OpenAI `{data:[{id:…}]}` 和少量其它形状 */
    private fun parseModelIds(json: String): List<String> = runCatching {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) return emptyList()
        val obj = root.asJsonObject
        val arr = obj.getAsJsonArray("data")
            ?: obj.getAsJsonArray("models")
            ?: return emptyList()
        arr.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val o = e.asJsonObject
            (o.get("id") ?: o.get("name"))?.asString?.takeIf { it.isNotBlank() }
        }.distinct().sorted()
    }.getOrElse { emptyList() }
}
