package com.yanyuan.toolbox.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext

// content 支持两种形态：纯文本 String；多模态 List<Map>（图片附件的 image_url 分段）。
data class OpenAiChatMessage(val role: String, val content: Any)

class AiChatApi {

    companion object {
        private const val TARGET_API = "https://app.unlimitedai.chat/api/chat"
        private const val UA = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Mobile Safari/537.36"
        const val THINK_OPEN = "<think>"
        const val THINK_CLOSE = "</think>"
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // SSE 允许持续响应，但不能无限期等待服务端完全无数据。
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    /** 导入的平台 API（接入AI 页导入，OpenAI 兼容接口）。 */
    data class ImportedAiSpec(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val displayName: String,
        // 强度（创造性/温度）：0~2，默认 0.7。
        val temperature: Float = 0.7f,
        val nativeWebSearch: Boolean = false
    )

    fun streamChat(
        modelId: String,
        messages: List<OpenAiChatMessage>,
        imported: ImportedAiSpec? = null
    ): Flow<String> = channelFlow {
        val callRef = AtomicReference<Call?>(null)
        val worker = launch(Dispatchers.IO) {
            try {
                if (imported != null) {
                    streamImported(imported, messages, { callRef.set(it) }) { send(it) }
                } else {
                    if (modelId.startsWith("imported:")) {
                        throw IllegalStateException("当前导入 API 平台未获授权，请在“接入AI”的 API 授权中开启")
                    }
                    val target = com.yanyuan.toolbox.data.AiChatModel.entries
                        .firstOrNull { it.id == modelId }
                        ?: throw IllegalStateException("当前 AI 模型选择无效，请重新选择模型")
                    if (target == com.yanyuan.toolbox.data.AiChatModel.UnlimitedAi) {
                        streamUnlimited(messages, { callRef.set(it) }) { send(it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(e)
            } finally {
                callRef.getAndSet(null)?.cancel()
                close()
            }
        }
        awaitClose {
            callRef.getAndSet(null)?.cancel()
            worker.cancel()
        }
    }

    // ==================== 导入的平台 API（OpenAI 兼容） ====================
    private suspend fun streamImported(
        spec: ImportedAiSpec,
        messages: List<OpenAiChatMessage>,
        onCall: (Call) -> Unit,
        emit: suspend (String) -> Unit
    ) {
        val baseUrl = spec.baseUrl.trimEnd('/')
        val endpoint = runCatching { URI(baseUrl) }.getOrNull()
        val scheme = endpoint?.scheme?.lowercase(Locale.ROOT)
        val host = endpoint?.host?.lowercase(Locale.ROOT)
        val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (endpoint == null || endpoint.host.isNullOrBlank() ||
            scheme != "https" && !(scheme == "http" && localHost)
        ) {
            throw IllegalStateException("API 地址必须使用 HTTPS；仅允许本机回环地址使用 HTTP")
        }
        val url = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }
        val payload = buildMap<String, Any?> {
            put("model", spec.model)
            put("stream", true)
            put("temperature", spec.temperature)
            put("messages", messages.map {
                mapOf("role" to it.role, "content" to it.content)
            })
            if (spec.nativeWebSearch) put("web_search_options", emptyMap<String, Any>())
        }
        val body = gson.toJson(payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .header("authorization", "Bearer ${spec.apiKey}")
            .post(body)
            .build()

        val call = client.newCall(req)
        onCall(call)
        val response = withContext(Dispatchers.IO) { call.execute() }
        response.use { resp ->
            val rb = resp.body ?: throw IllegalStateException("AI 服务返回空响应")
            if (!resp.isSuccessful) {
                throw IllegalStateException("AI 服务请求失败（HTTP ${resp.code}）")
            }
            val src: BufferedSource = rb.source()
            var inReasoning = false
            while (currentCoroutineContext().isActive && !src.exhausted()) {
                val line = withContext(Dispatchers.IO) { src.readUtf8Line() } ?: break
                val t = line.trim()
                if (!t.startsWith("data:")) continue
                val data = t.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                if (json.has("error")) {
                    throw IllegalStateException("AI 服务返回错误")
                }
                val parts = runCatching {
                    val choices = json.getAsJsonArray("choices")
                    val choice = choices?.firstOrNull()?.asJsonObject
                    val deltaObject = choice?.getAsJsonObject("delta")
                    val reasoning = deltaObject?.stringValue("reasoning_content", "reasoning")
                        ?: choice?.stringValue("reasoning_content", "reasoning")
                        ?: json.stringValue("reasoning_content", "reasoning")
                    val content = deltaObject?.stringValue("content")
                        ?: choice?.stringValue("content")
                        ?: json.stringValue("content", "text")
                    reasoning to content
                }.getOrNull() ?: continue

                val reasoning = parts.first
                val content = parts.second
                if (!reasoning.isNullOrEmpty()) {
                    if (!inReasoning) {
                        emit(THINK_OPEN)
                        inReasoning = true
                    }
                    emit(reasoning)
                }
                if (!content.isNullOrEmpty()) {
                    if (inReasoning) {
                        emit(THINK_CLOSE)
                        inReasoning = false
                    }
                    emit(content)
                }
            }
            if (inReasoning) emit(THINK_CLOSE)
        }
    }

    // ==================== Unlimited ====================
    private suspend fun streamUnlimited(
        messages: List<OpenAiChatMessage>,
        onCall: (Call) -> Unit,
        emit: suspend (String) -> Unit
    ) {
        val body = gson.toJson(toUpstreamPayload(messages))
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(TARGET_API)
            .header("accept", "*/*")
            .header("content-type", "application/json")
            .header("origin", "https://app.unlimitedai.chat")
            .header("referer", "https://app.unlimitedai.chat/zh")
            .header("user-agent", UA)
            .header("x-next-intl-locale", "zh")
            .header("sec-fetch-dest", "empty")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-origin")
            .post(body)
            .build()

        for (attempt in 0 until 3) {
            val call = client.newCall(req)
            onCall(call)
            val response = withContext(Dispatchers.IO) { call.execute() }
            if (response.code == 429) {
                response.close()
                if (attempt < 2) { delay(1_000L shl attempt); continue }
                throw RateLimitedException()
            }
            response.use { resp ->
                val rb = resp.body ?: throw IllegalStateException("AI 服务返回空响应")
                if (!resp.isSuccessful) {
                    throw IllegalStateException("AI 服务请求失败（HTTP ${resp.code}）")
                }
                val src: BufferedSource = rb.source()
                var inReasoning = false
                while (currentCoroutineContext().isActive && !src.exhausted()) {
                    val line = withContext(Dispatchers.IO) { src.readUtf8Line() } ?: break
                    val t = line.trim()
                    if (t.isBlank()) continue
                    if (t.startsWith("<!DOCTYPE") || t.startsWith("<html")) {
                        throw IllegalStateException("AI 服务返回 HTML，可能被拦截")
                    }
                    val json = runCatching { JsonParser.parseString(t).asJsonObject }.getOrNull() ?: continue
                    if (json.has("error")) {
                        throw IllegalStateException("AI 服务返回错误")
                    }
                    val type = json.get("type")?.asString.orEmpty()
                    val delta = json.stringValue("delta", "text", "content").orEmpty()
                    val isReasoning = type.contains("reason", ignoreCase = true)
                    if (isReasoning && !inReasoning) { emit(THINK_OPEN); inReasoning = true }
                    else if (!isReasoning && inReasoning) { emit(THINK_CLOSE); inReasoning = false }
                    if (delta.isNotEmpty()) emit(delta)
                }
                if (inReasoning) emit(THINK_CLOSE)
            }
            return
        }
    }

    private fun toUpstreamPayload(messages: List<OpenAiChatMessage>): Map<String, Any?> {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        val upstream = messages.map { m ->
            mapOf(
                "id" to UUID.randomUUID().toString(),
                "role" to m.role,
                "content" to m.content,
                "parts" to listOf(mapOf("type" to "text", "text" to (m.content as? String ?: "[图片附件]"))),
                "createdAt" to now
            )
        }
        return mapOf(
            "chatId" to UUID.randomUUID().toString(),
            "messages" to upstream,
            "selectedChatModel" to "chat-model",
            "selectedCharacter" to null,
            "selectedStory" to null,
            "deviceId" to "",
            "locale" to "zh"
        )
    }

    private fun JsonObject.stringValue(vararg names: String): String? {
        for (n in names) {
            if (!has(n)) continue
            val e = get(n) ?: continue
            if (e.isJsonNull) continue
            if (e.isJsonPrimitive && e.asJsonPrimitive.isString) return e.asString
        }
        return null
    }

    private class RateLimitedException : IllegalStateException("Unlimited AI 触发限流")
}
