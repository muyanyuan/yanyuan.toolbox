@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.yanyuan.toolbox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AppearanceRepository
import com.yanyuan.toolbox.data.AppearanceState
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.WelcomeAppearanceState
import com.yanyuan.toolbox.data.WelcomeAppearanceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 平板断点：宽度 >= 600dp 视为平板 */
private val WELCOME_TABLET_BREAKPOINT = 600.dp

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var fullGreeting by remember { mutableStateOf("") }
    var typedGreeting by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var typingDone by remember { mutableStateOf(false) }

    val appIconBitmap = remember { loadAppIcon(context) }
    val greetingManager = remember(context) { WelcomeGreetingManager(context) }

    // ============ 透明度开关 + 应用背景图 ============
    remember {
        WelcomeAppearanceStore(context).loadToState()
        true
    }

    val welcomeVersion = WelcomeAppearanceState.version
    val transparent = remember(welcomeVersion) { WelcomeAppearanceState.transparent }

    val bgVersion = AppearanceState.backgroundVersion
    val bgFile = remember(bgVersion) { AppearanceRepository.backgroundFile(context) }
    val bgBitmap = remember(bgVersion, bgFile.exists()) {
        if (bgFile.exists()) BitmapFactory.decodeFile(bgFile.absolutePath) else null
    }

    // ============ AI / 本地欢迎词 ============
    LaunchedEffect(Unit) {
        val startAt = System.currentTimeMillis()
        val period = currentGreetingPeriod()
        fullGreeting = greetingManager.getGreeting(period)
        val elapsed = System.currentTimeMillis() - startAt
        if (elapsed < 300) delay(300 - elapsed)
        loading = false
    }

    // ============ 逐字打字机 ============
    LaunchedEffect(fullGreeting) {
        if (fullGreeting.isEmpty()) return@LaunchedEffect
        typedGreeting = ""
        typingDone = false
        for (i in fullGreeting.indices) {
            typedGreeting = fullGreeting.substring(0, i + 1)
            delay(60)
        }
        typingDone = true
    }

    // ============ 平板 / 手机 尺寸自适应 ============
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val isTablet = screenWidthDp >= WELCOME_TABLET_BREAKPOINT

    Box(Modifier.fillMaxSize()) {
        // ===== 背景层 =====
        if (transparent && bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        if (isTablet) {
            TabletWelcomeContent(
                appIconBitmap = appIconBitmap,
                typedGreeting = typedGreeting,
                loading = loading,
                typingDone = typingDone,
                onContinue = onContinue
            )
        } else {
            PhoneWelcomeContent(
                appIconBitmap = appIconBitmap,
                typedGreeting = typedGreeting,
                loading = loading,
                typingDone = typingDone,
                onContinue = onContinue
            )
        }
    }
}

// ========================================================
//  平板内容：图标+名字在左上角，继续按钮在右下角
// ========================================================
@Composable
private fun TabletWelcomeContent(
    appIconBitmap: ImageBitmap?,
    typedGreeting: String,
    loading: Boolean,
    typingDone: Boolean,
    onContinue: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "琰鸢工具箱",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Text(
                "琰鸢工具箱",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Serif,
                letterSpacing = 4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                WelcomeDots(isTablet = true)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = typedGreeting,
                        fontSize = 26.sp,
                        lineHeight = 46.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 2.sp,
                        maxLines = 4,
                        softWrap = true
                    )
                    if (!typingDone) {
                        Spacer(Modifier.height(8.dp))
                        BlinkingCursor(isTablet = true)
                    }
                }
            }
        }

        Button(
            onClick = onContinue,
            enabled = typingDone,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 40.dp)
                .widthIn(min = 140.dp)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                if (typingDone) "继续" else "请稍候",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

// ========================================================
//  手机内容
// ========================================================
@Composable
private fun PhoneWelcomeContent(
    appIconBitmap: ImageBitmap?,
    typedGreeting: String,
    loading: Boolean,
    typingDone: Boolean,
    onContinue: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(110.dp))

            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "琰鸢工具箱",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "琰鸢工具箱",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Serif,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(6.dp))

            Box(
                Modifier
                    .width(48.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    WelcomeDots(isTablet = false)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = typedGreeting,
                            fontSize = 19.sp,
                            lineHeight = 32.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 1.5.sp,
                            maxLines = 6,
                            softWrap = true
                        )
                        if (!typingDone) {
                            Spacer(Modifier.height(6.dp))
                            BlinkingCursor(isTablet = false)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }

        Button(
            onClick = onContinue,
            enabled = typingDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .padding(bottom = 72.dp)
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                if (typingDone) "继续" else "请稍候",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

// ========================================================
//  本地问候词库（API 未授权且没有历史词时的兜底）
// ========================================================
private fun pickGreeting(period: String): String {
    val pool = when (period) {
        "清晨" -> listOf(
            "晨光初透，风过檐铃，宜启卷，宜缓行。",
            "晓色入窗，露犹未干，新日方长。",
            "清风送晓，竹影横阶，可携一册以消晨。",
            "天将白时，万籁初醒，愿今日事皆从容。",
            "晨烟未散，茶犹温热，且饮一盏再启程。"
        )
        "正午" -> listOf(
            "日在中天，蝉声满树，宜暂歇，宜温茶。",
            "光如洗砚，风自南来，不妨偷得半刻闲。",
            "午窗正暖，书卷微蜷，可缓一缓再续。",
            "日轮当顶，万事稍息，且与清茶片刻安。",
            "午梦初回，树影斑驳，且享这一刻宁。"
        )
        "午后" -> listOf(
            "午后闲长，窗影斜移，正好翻书半卷。",
            "日光西转，茶烟袅袅，宜静坐，宜展卷。",
            "半日浮光，落在案头，可缓缓消磨。",
            "竹影移动，风带余暖，宜与书卷相守。",
            "午后无事，听风一阵，心中自生清意。"
        )
        "薄暮" -> listOf(
            "暮色四合，灯火初上，归来即是安处。",
            "斜阳将尽，雁字归林，可缓缓收心。",
            "日沉西山，风带薄凉，宜闭卷，宜温茶。",
            "晚霞未散，街灯已明，愿此夜得安宁。",
            "黄昏入庭，竹影渐长，宜静坐一盏茶。"
        )
        else -> listOf(
            "夜静如墨，一灯如豆，宜早眠。",
            "更深露重，风声在檐，且将心事搁下。",
            "月色入帷，万籁俱寂，宜息，宜眠。",
            "长夜无声，灯芯微跳，愿君好梦。",
            "星沉天末，夜正深时，可安然入睡。"
        )
    }
    return pool.random()
}

private fun currentGreetingPeriod(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "清晨"
        in 11..13 -> "正午"
        in 14..17 -> "午后"
        in 18..22 -> "薄暮"
        else -> "夜深"
    }
}

// ========================================================
//  欢迎词管理
//
//  规则：
//  - 程序授权开启后：每天最多成功生成 5 句
//  - API 关闭/未授权：完全不发请求，直接复用最多 100 句历史
//  - 达到每日 5 句后：循环历史欢迎词
//  - 历史最多保存 100 句
//  - API 失败 / 返回空值 / 重复值：不消耗当天额度
// ========================================================
private class WelcomeGreetingManager(
    private val context: Context
) {
    private val store = WelcomeGreetingStore(context)
    private val aiService = WelcomeAiGreetingService(context)

    suspend fun getGreeting(period: String): String {
        var state = store.load()
        val today = currentDate()

        if (state.date != today) {
            state = state.copy(
                date = today,
                todayGenerated = 0
            )
            store.save(state)
        }

        // 程序授权被关闭：不联网，只复用历史 100 句。
        if (!aiService.isAuthorized()) {
            return playHistoryOrLocal(state, period)
        }

        // 每天最多成功生成 5 句。
        if (state.todayGenerated < 5) {
            repeat(3) {
                val generated = aiService.generate(period)
                if (generated.isBlank()) return@repeat

                val duplicate = state.history.any {
                    it.trim() == generated.trim()
                }
                if (duplicate) return@repeat

                val history = (state.history + generated).takeLast(100)
                state = state.copy(
                    history = history,
                    todayGenerated = state.todayGenerated + 1
                )
                store.save(state)
                return generated
            }
        }

        // 当天达到上限后，从全部历史记录中循环使用。
        return playHistoryOrLocal(state, period)
    }

    private suspend fun playHistoryOrLocal(
        state: WelcomeGreetingState,
        period: String
    ): String {
        if (state.history.isEmpty()) {
            return pickGreeting(period)
        }

        val cursor = state.playCursor.coerceIn(0, state.history.lastIndex)
        val result = state.history[cursor]
        val next = (cursor + 1) % state.history.size

        store.save(state.copy(playCursor = next))
        return result
    }

    private fun currentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

private data class WelcomeGreetingState(
    val date: String = "",
    val todayGenerated: Int = 0,
    val playCursor: Int = 0,
    val history: List<String> = emptyList()
)

private class WelcomeGreetingStore(
    context: Context
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("welcome_greeting_ai", Context.MODE_PRIVATE)

    fun load(): WelcomeGreetingState {
        val raw = prefs.getString("history", null)
        val history = runCatching {
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val text = arr.optString(i, "").trim()
                        if (text.isNotBlank()) add(text)
                    }
                }
            }
        }.getOrDefault(emptyList())

        return WelcomeGreetingState(
            date = prefs.getString("date", "") ?: "",
            todayGenerated = prefs.getInt("today_generated", 0).coerceIn(0, 5),
            playCursor = prefs.getInt("play_cursor", 0).coerceAtLeast(0),
            history = history.takeLast(100)
        )
    }

    fun save(state: WelcomeGreetingState) {
        val arr = JSONArray()
        state.history.takeLast(100).forEach(arr::put)

        prefs.edit()
            .putString("date", state.date)
            .putInt("today_generated", state.todayGenerated.coerceIn(0, 5))
            .putInt("play_cursor", state.playCursor.coerceAtLeast(0))
            .putString("history", arr.toString())
            .apply()
    }
}

// ========================================================
//  AI 欢迎词请求
//
//  直接读取“导入大模型”中的已启用平台/模型。
//  API Key 只在 Android 原生层使用，不暴露给 UI。
//  使用 OpenAI-compatible /chat/completions。
// ========================================================
private class WelcomeAiGreetingService(
    private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun isAuthorized(): Boolean {
        val auth = AuthStore.get(context)
        if (!auth.isProgramUsable) return false
        val apiId = auth.programApiId ?: return false
        val modelId = auth.programModelId ?: return false
        val settings = AiModelStore.get(context)
        val config = settings.configs.firstOrNull { it.platformId == apiId } ?: return false
        if (!config.enabled) return false
        if (!config.platform.keyOptional && config.apiKey.isBlank()) return false
        val model = settings.modelsOf(apiId).firstOrNull {
            !it.hidden && it.cleanModelId.trim() == modelId
        } ?: return false
        return model.cleanModelId.isNotBlank() && config.effectiveBaseUrl.isNotBlank()
    }

    suspend fun generate(period: String): String {
        val auth = AuthStore.get(context)
        if (!auth.isProgramUsable) return ""
        val apiId = auth.programApiId ?: return ""
        val modelId = auth.programModelId ?: return ""
        val settings = AiModelStore.get(context)
        val config = settings.configs.firstOrNull { it.platformId == apiId } ?: return ""
        if (!config.enabled) return ""
        if (!config.platform.keyOptional && config.apiKey.isBlank()) return ""
        val model = settings.modelsOf(apiId).firstOrNull {
            !it.hidden && it.cleanModelId.trim() == modelId
        } ?: return ""
        if (model.cleanModelId.isBlank()) return ""

        val platform = config.platform
        val apiKey = config.apiKey.trim()
        val baseUrl = config.effectiveBaseUrl.trim()
        if (baseUrl.isBlank()) return ""

        val endpoint = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            baseUrl.trimEnd('/') + "/chat/completions"
        }

        val systemPrompt = """
你是“琰鸢工具箱”的欢迎页诗词助手。

请为当前时段“$period”生成一句原创、雅致、自然的人性化欢迎词。
要求：
1. 中文，古诗词/古风意境。
2. 有欢迎、陪伴、从容的感觉，但不要写成客服话术。
3. 不直接引用现成名句，不模仿具体诗人。
4. 只输出一句，不要解释，不要 Markdown，不要引号。
5. 建议五言、七言或简短古风句式。
6. 控制在 8～24 个汉字左右。
""".trimIndent()

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "请为${period}再写一句新的欢迎词。")
                })
            })
            put("temperature", 0.9)
            put("max_tokens", 80)
            put("stream", false)
        }

        val builder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("X-Yanyuan-Client", "YanyuanToolbox-Welcome")
            .post(
                body.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )

        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        return runCatching {
            val response = withContext(Dispatchers.IO) {
                client.newCall(builder.build()).execute()
            }

            response.use { resp ->
                if (!resp.isSuccessful) return@runCatching ""
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@runCatching ""

                val json = JSONObject(raw)
                val choices = json.optJSONArray("choices")
                    ?: return@runCatching ""
                if (choices.length() == 0) return@runCatching ""

                val message = choices.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: return@runCatching ""

                cleanGreeting(message.optString("content", ""))
            }
        }.getOrDefault("")
    }

    private fun cleanGreeting(raw: String): String {
        var text = raw
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .trim('"', '“', '”', '‘', '’')

        val prefixes = listOf(
            "欢迎词：",
            "欢迎语：",
            "今日欢迎词：",
            "今日问候：",
            "AI："
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
            }
        }

        val end = Regex("[。！？!?]").find(text)
        if (end != null) {
            text = text.substring(0, end.range.first + 1)
        }

        return if (text.length in 4..36) text else ""
    }
}

// ========================================================
//  App 图标加载
// ========================================================
private fun loadAppIcon(context: Context): ImageBitmap? {
    return runCatching {
        val res = context.resources
        val pkg = context.packageName

        var id = res.getIdentifier("ic_launcher_round", "mipmap", pkg)
        if (id == 0) id = res.getIdentifier("ic_launcher", "mipmap", pkg)
        if (id == 0) id = res.getIdentifier("ic_launcher_round", "drawable", pkg)
        if (id == 0) id = res.getIdentifier("ic_launcher", "drawable", pkg)
        if (id == 0) return null

        val drawable = ResourcesCompat.getDrawable(res, id, null) ?: return null
        val size = 384
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }.getOrNull()
}

// ========================================================
//  打字点 / 光标
// ========================================================
@Composable
private fun WelcomeDots(isTablet: Boolean = false) {
    val t = rememberInfiniteTransition(label = "welcome-typing")
    val a1 by t.animateFloat(
        0.3f,
        1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "a1"
    )
    val a2 by t.animateFloat(
        1f,
        0.3f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "a2"
    )

    val dotSize = if (isTablet) 10.dp else 8.dp
    val dotGap = if (isTablet) 8.dp else 6.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(a1, a2, a1).forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(dotGap))
            Box(
                Modifier
                    .size(dotSize)
                    .alpha(a)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Composable
private fun BlinkingCursor(isTablet: Boolean = false) {
    val t = rememberInfiniteTransition(label = "cursor")
    val a by t.animateFloat(
        0.2f,
        1f,
        infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    val cursorWidth = if (isTablet) 3.dp else 2.dp
    val cursorHeight = if (isTablet) 24.dp else 18.dp
    Box(
        Modifier
            .size(width = cursorWidth, height = cursorHeight)
            .alpha(a)
            .background(MaterialTheme.colorScheme.primary)
    )
}
