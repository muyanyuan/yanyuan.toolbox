package com.yanyuan.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanyuan.toolbox.data.AiModelEntry
import com.yanyuan.toolbox.data.AiModelPlatform
import com.yanyuan.toolbox.data.AiModelSettings
import com.yanyuan.toolbox.data.AiModelState
import com.yanyuan.toolbox.data.AiModelStore
import com.yanyuan.toolbox.data.AiPlatformConfig
import com.yanyuan.toolbox.data.AuthSettings
import com.yanyuan.toolbox.data.AuthState
import com.yanyuan.toolbox.data.AuthStore
import com.yanyuan.toolbox.data.FeatureRepository
import com.yanyuan.toolbox.data.InstalledPlugin
import com.yanyuan.toolbox.network.AiModelProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「导入大模型」二级页。
 *
 * 版式（对齐设计稿）：
 *   顶栏：标题 + 平台启用开关
 *   授权：API 平台 / 插件 / 唯一程序模型绑定
 *   选择模型平台：下拉选择
 *   API 密钥 / API 地址 / 模型列表
 */
@Composable
fun ImportModelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    val version = AiModelState.version
    var settings by remember(version) { mutableStateOf(AiModelStore.get(context)) }

    // 授权：AI / 插件（按插件单独授权）/ 程序
    val authVersion = AuthState.version
    var auth by remember(authVersion) { mutableStateOf(AuthStore.get(context)) }
    var showPluginAuthorization by remember { mutableStateOf(false) }
    var showPluginAuthorizationWarning by remember { mutableStateOf(false) }
    var pendingPluginGrant by remember { mutableStateOf<String?>(null) }
    var pendingModelEnable by remember { mutableStateOf<String?>(null) }
    var pendingApiAuthorization by remember { mutableStateOf<String?>(null) }
    var pendingProgramBinding by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingProgramEnable by remember { mutableStateOf(false) }
    var programModelMenuOpen by remember { mutableStateOf(false) }
    val pluginRepository = remember { FeatureRepository(context) }
    var installedPlugins by remember { mutableStateOf(pluginRepository.listInstalled()) }

    // 未选择平台时保持空状态，避免用户进入页面后默认误选某个平台。
    var platform by remember { mutableStateOf<AiModelPlatform?>(null) }
    var platformMenuOpen by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(false) }
    var pendingSettings by remember { mutableStateOf<AiModelSettings?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            saveJob?.cancel()
        }
    }

    fun scheduleSave(next: AiModelSettings) {
        pendingSettings = next
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            val snapshot = pendingSettings ?: return@launch
            withContext(Dispatchers.IO) {
                AiModelStore.set(context, snapshot)
            }
            pendingSettings = null
        }
    }

    val selectedPlatform = platform
    val cfg = selectedPlatform?.let { settings.configFor(it.id) }
    val models = selectedPlatform?.let { settings.modelsOf(it.id) } ?: emptyList()
    val apiAuthorized = selectedPlatform?.let {
        auth.isImportedApiAuthorized(it.id)
    } ?: false
    val availableProgramModels: (List<AiModelEntry>) -> List<AiModelEntry> = { entries ->
        entries.filter { !it.hidden && it.cleanModelId.isNotBlank() }
    }

    fun putConfig(next: AiPlatformConfig) {
        if (auth.programApiId == next.platformId &&
            (!next.enabled || (!next.platform.keyOptional && next.apiKey.isBlank()))
        ) {
            auth = auth.withoutProgramBinding()
            AuthStore.set(context, auth)
        }
        val list = settings.configs.toMutableList()
        val i = list.indexOfFirst { it.platformId == next.platformId }
        if (i >= 0) list[i] = next else list.add(next)
        val next = settings.copy(configs = list)
        settings = next
        scheduleSave(next)
    }

    fun putSettings(next: AiModelSettings) {
        settings = next
        scheduleSave(next)
    }

    fun updateModelId(entry: AiModelEntry, value: String) {
        val oldModelId = entry.cleanModelId.trim()
        val newModelId = value.removePrefix("-").trim()
        if (oldModelId.isNotBlank() && oldModelId != newModelId) {
            AiChatProStorage(context).clearSelectedModel(entry.platformId, oldModelId)
            if (auth.isProgramBinding(entry.platformId, oldModelId)) {
                auth = auth.withoutProgramBinding()
                AuthStore.set(context, auth)
            }
        }
        putSettings(
            settings.copy(
                models = settings.models.map {
                    if (it.id == entry.id) it.copy(modelId = value) else it
                }
            )
        )
    }

    fun deleteModel(entry: AiModelEntry) {
        val modelId = entry.cleanModelId.trim()
        AiChatProStorage(context).clearSelectedModel(entry.platformId, modelId.ifBlank { null })
        if (auth.isProgramBinding(entry.platformId, modelId)) {
            auth = auth.withoutProgramBinding()
            AuthStore.set(context, auth)
        }
        putSettings(settings.copy(models = settings.models.filterNot { it.id == entry.id }))
    }

    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(Modifier.weight(1f)) {
                Text("接入AI", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedPlatform == null) "连接你的 AI 模型与服务"
                    else selectedPlatform.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (cfg != null) {
                Switch(
                    checked = cfg.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) pendingModelEnable = cfg.platformId
                        else {
                            if (auth.programApiId == cfg.platformId) {
                                auth = auth.withoutProgramBinding()
                                AuthStore.set(context, auth)
                            }
                            putConfig(cfg.copy(enabled = false))
                        }
                    }
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = navBarBottom + 28.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ---------- 平台 ----------
            SectionTitle("模型平台", "先选择要配置的 AI 服务")
            Spacer(Modifier.height(8.dp))
            PlatformSelector(
                current = selectedPlatform,
                expanded = platformMenuOpen,
                onToggle = { platformMenuOpen = !platformMenuOpen },
                onPick = {
                    platform = it
                    showKey = false
                    platformMenuOpen = false
                    message = null
                }
            )

            if (selectedPlatform == null || cfg == null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("请选择一个模型平台", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "选择后会显示 API 密钥、地址和模型管理配置。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(20.dp))

                // ---------- API 设置 ----------
                SectionTitle("接口配置", "密钥与地址仅对当前模型平台生效")
                Spacer(Modifier.height(8.dp))
                SettingsGlassGroup(title = "API 设置") {
                    // API 密钥
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("API 密钥", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            if (selectedPlatform.consoleUrl.isNotBlank()) {
                                Text(
                                    "获取密钥",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        runCatching { uriHandler.openUri(selectedPlatform.consoleUrl) }
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = cfg.apiKey,
                                onValueChange = { putConfig(cfg.copy(apiKey = it)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        if (selectedPlatform.keyOptional) "本地部署可留空" else "sk-…",
                                        fontSize = 14.sp
                                    )
                                },
                                textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                                visualTransformation = if (showKey) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(
                                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            if (showKey) "隐藏密钥" else "显示密钥",
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(cfg.apiKey)) },
                                enabled = cfg.apiKey.isNotBlank(),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, "复制密钥", modifier = Modifier.size(19.dp))
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    val r = AiModelProbe.testConnection(selectedPlatform, cfg)
                                    busy = false
                                    messageOk = r.ok
                                    message = r.message
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (busy) "正在检测…" else "检测接口")
                        }
                    }

                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )

                    // API 地址
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("API 地址", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "恢复默认",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    putConfig(cfg.copy(baseUrl = ""))
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cfg.baseUrl,
                            onValueChange = { putConfig(cfg.copy(baseUrl = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(selectedPlatform.baseUrl.ifBlank { "https://…" }, fontSize = 14.sp)
                            },
                            textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                SettingsGlassGroup(title = "服务商能力") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("原生联网搜索", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "由 API 服务商和模型自行联网；仅对明确支持 Chat Completions web_search_options 的接口开启。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cfg.nativeWebSearch,
                            onCheckedChange = { putConfig(cfg.copy(nativeWebSearch = it)) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ---------- 模型管理 ----------
                SectionTitle("模型", "同步平台模型，也可以手动添加")
                Spacer(Modifier.height(8.dp))
                SettingsGlassGroup(title = selectedPlatform.displayName) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("模型列表", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (models.isEmpty()) "暂未添加模型" else "已添加 ${models.size} 个模型",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    val r = AiModelProbe.fetchModels(selectedPlatform, cfg)
                                    busy = false
                                    messageOk = r.ok
                                    message = r.message
                                    if (r.ok && r.models.isNotEmpty()) {
                                        val known = models.map { it.cleanModelId }.toSet()
                                        val added = r.models.filterNot { known.contains(it) }
                                            .map {
                                                AiModelEntry(
                                                    platformId = selectedPlatform.id,
                                                    baseUrl = cfg.effectiveBaseUrl,
                                                    apiKey = cfg.apiKey,
                                                    modelId = it
                                                )
                                            }
                                        putSettings(settings.copy(models = settings.models + added))
                                    }
                                }
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("同步")
                        }
                        IconButton(
                            onClick = {
                                putSettings(
                                    settings.copy(
                                        models = settings.models + AiModelEntry(
                                            platformId = selectedPlatform.id,
                                            baseUrl = cfg.effectiveBaseUrl,
                                            apiKey = cfg.apiKey,
                                            modelId = ""
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Add, "手动添加模型", modifier = Modifier.size(20.dp))
                        }
                    }

                    if (models.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("还没有模型", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "填好密钥后点击“同步”，或使用右侧 + 手动添加。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            models.forEach { entry ->
                                ModelRow(
                                    entry = entry,
                                    onIdChange = { v -> updateModelId(entry, v) },
                                    onToggleHide = {
                                        if (!entry.hidden && auth.isProgramBinding(entry.platformId, entry.cleanModelId)) {
                                            auth = auth.withoutProgramBinding()
                                            AuthStore.set(context, auth)
                                        }
                                        putSettings(
                                            settings.copy(
                                                models = settings.models.map {
                                                    if (it.id != entry.id) it
                                                    else it.copy(
                                                        modelId = if (it.hidden) it.cleanModelId
                                                        else "-" + it.cleanModelId
                                                    )
                                                }
                                            )
                                        )
                                    },
                                    onDelete = { deleteModel(entry) }
                                )
                            }
                        }
                    }
                }

                // ---------- API 平台授权 ----------
                Spacer(Modifier.height(20.dp))
                SectionTitle(
                    "API 授权",
                    "按当前 API 平台授权；授权后该平台下所有未隐藏模型都可以在 AI 页使用"
                )
                Spacer(Modifier.height(8.dp))
                val programApiId = auth.programApiId
                val programModelId = auth.programModelId
                val programModel = if (programApiId == selectedPlatform.id) {
                    models.firstOrNull { !it.hidden && it.cleanModelId == programModelId }
                } else null
                val canUseForProgram = cfg.enabled &&
                        (selectedPlatform.keyOptional || cfg.apiKey.isNotBlank()) &&
                        availableProgramModels(models).isNotEmpty()
                val anotherProgramApi = programApiId != null && programApiId != selectedPlatform.id

                SettingsGlassGroup(title = selectedPlatform.displayName) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (apiAuthorized) {
                                    auth = auth.withImportedApiAuthorization(selectedPlatform.id, false)
                                    AuthStore.set(context, auth)
                                } else {
                                    pendingApiAuthorization = selectedPlatform.id
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "允许此 API 平台用于 AI 对话和 Agent",
                                fontSize = 14.sp,
                                fontWeight = if (apiAuthorized) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                if (apiAuthorized) "已授权：该平台下的所有可见模型均可使用"
                                else "未授权：AI 页不能使用此平台的接口",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = apiAuthorized,
                            onCheckedChange = { enabled ->
                                if (enabled) pendingApiAuthorization = selectedPlatform.id
                                else {
                                    auth = auth.withImportedApiAuthorization(selectedPlatform.id, false)
                                    AuthStore.set(context, auth)
                                }
                            }
                        )
                    }

                    Divider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )

                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("程序授权", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        anotherProgramApi -> "程序授权已绑定其它 API：${programApiId}"
                                        programModel != null && auth.programEnabled -> "已启用：${programModel.displayName}"
                                        programModel != null -> "已选择模型但未启用：${programModel.displayName}"
                                        else -> "先选择一个模型，再打开程序授权"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = auth.programEnabled && programModel != null &&
                                        auth.programApiId == selectedPlatform.id,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        auth = auth.withProgramEnabled(false)
                                        AuthStore.set(context, auth)
                                    } else if (canUseForProgram && !anotherProgramApi) {
                                        if (programModel == null) {
                                            programModelMenuOpen = true
                                        } else {
                                            pendingProgramEnable = true
                                            pendingProgramBinding =
                                                selectedPlatform.id to programModel.cleanModelId
                                        }
                                    }
                                },
                                enabled = !anotherProgramApi && canUseForProgram
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box {
                            OutlinedButton(
                                onClick = { programModelMenuOpen = true },
                                enabled = canUseForProgram && !anotherProgramApi,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(programModel?.displayName ?: "选择此 API 下的模型")
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = programModelMenuOpen,
                                onDismissRequest = { programModelMenuOpen = false }
                            ) {
                                availableProgramModels(models).forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(candidate.displayName) },
                                        onClick = {
                                            pendingProgramEnable = auth.programEnabled &&
                                                    auth.programApiId == selectedPlatform.id
                                            pendingProgramBinding = selectedPlatform.id to candidate.cleanModelId
                                            programModelMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        if (anotherProgramApi) {
                            Text(
                                "请先在已绑定的 API 中取消程序授权，才能选择此 API。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (!canUseForProgram) {
                            Text("请先启用平台、配置密钥并添加可见模型。", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (programModel != null) {
                            TextButton(
                                onClick = {
                                    auth = auth.withoutProgramBinding()
                                    AuthStore.set(context, auth)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("取消程序授权") }
                        }
                    }
                }

                // ---------- 状态 ----------
                message?.let { m ->
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (messageOk) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (messageOk) Icons.Default.Check else Icons.Default.Close,
                                null,
                                tint = if (messageOk) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                m,
                                fontSize = 12.sp,
                                color = if (messageOk) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { message = null }) { Text("关闭", fontSize = 12.sp) }
                        }
                    }
                }

                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在请求…", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

                // ---------- 授权：始终放在页面最底部 ----------
                Spacer(Modifier.height(28.dp))
                SectionTitle("授权", "API 平台授权、插件授权和程序模型选择")
                Spacer(Modifier.height(8.dp))
                SettingsGlassGroup(title = "访问权限") {
                    ListItem(
                        headlineContent = {
                            Text("插件授权", fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            val count = auth.pluginAuthorizedIds.size
                            Text(
                                if (installedPlugins.isEmpty()) {
                                    "查看已安装插件，并逐个设置授权"
                                } else {
                                    "已授权 $count / ${installedPlugins.size} 个插件，点击进入管理"
                                },
                                fontSize = 12.sp
                            )
                        },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Icon(Icons.Default.ExpandMore, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showPluginAuthorizationWarning = true
                        }
                    )
                }

        }
    }

    if (showPluginAuthorization) {
        PluginAuthorizationDialog(
            plugins = installedPlugins,
            auth = auth,
            onAuthChange = { pluginId, enabled ->
                if (enabled) {
                    if (!auth.isPluginAuthorized(pluginId)) pendingPluginGrant = pluginId
                } else {
                    auth = auth.withPluginAuthorization(pluginId, false)
                    AuthStore.set(context, auth)
                }
            },
            onDismiss = { showPluginAuthorization = false }
        )
    }

    if (showPluginAuthorizationWarning) {
        AlertDialog(
            onDismissRequest = { showPluginAuthorizationWarning = false },
            title = { Text("插件 API 授权风险") },
            text = {
                Text("授权后，所选插件可通过宿主接口读取已导入的平台和模型信息，并请求账户状态；插件自身也可能记录或上传 API 返回内容，恶意插件还可能滥用接口、泄露数据或消耗 API 余额/Token。API 密钥不会直接返回给网页，但授权仍只应给你信任并已审阅代码的插件。是否继续管理授权？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPluginAuthorizationWarning = false
                    installedPlugins = pluginRepository.listInstalled()
                    showPluginAuthorization = true
                }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showPluginAuthorizationWarning = false }) { Text("取消") }
            }
        )
    }

    pendingPluginGrant?.let { pluginId ->
        val pluginName = installedPlugins.firstOrNull { it.manifest.id == pluginId }
            ?.manifest?.name ?: pluginId
        AlertDialog(
            onDismissRequest = { pendingPluginGrant = null },
            title = { Text("确认授权给插件") },
            text = {
                Text("“$pluginName”获准后可通过宿主 API 读取模型配置摘要，并请求账户/接口状态；插件可能记录或上传 API 返回内容，相关请求也可能消耗服务商余额或 Token。API 密钥不会以原文交给插件，但请只授权可信插件。")
            },
            confirmButton = {
                TextButton(onClick = {
                    auth = auth.withPluginAuthorization(pluginId, true)
                    AuthStore.set(context, auth)
                    pendingPluginGrant = null
                }) { Text("确认授权") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPluginGrant = null }) { Text("暂不授权") }
            }
        )
    }

    pendingApiAuthorization?.let { platformId ->
        val platformName = AiModelPlatform.fromId(platformId).displayName
        AlertDialog(
            onDismissRequest = { pendingApiAuthorization = null },
            title = { Text("授权 API 平台") },
            text = {
                Text(
                    "授权后，AI 页聊天和 Agent 可以使用 $platformName 接口下的所有可见模型发送请求，可能消耗服务商额度或 Token。API 密钥仍仅加密保存在本机，请只授权你信任的服务商。此授权不影响程序欢迎语授权。是否继续？",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    auth = auth.withImportedApiAuthorization(platformId, true)
                    AuthStore.set(context, auth)
                    pendingApiAuthorization = null
                }) { Text("确认授权") }
            },
            dismissButton = {
                TextButton(onClick = { pendingApiAuthorization = null }) { Text("取消") }
            }
        )
    }

    pendingModelEnable?.let { platformId ->
        val platformName = AiModelPlatform.fromId(platformId).displayName
        AlertDialog(
            onDismissRequest = { pendingModelEnable = null },
            title = { Text("启用模型服务") },
            text = {
                Text("启用 $platformName 后，AI 对话、账户检测或程序欢迎语可能向服务商发送请求并消耗余额/Token。API 密钥会使用 Android Keystore 保护的 AES-GCM 加密后保存在本机，且不会随备份迁移；设备已被控制或应用运行时内存被检查时仍可能泄露。仍要启用吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    putConfig(settings.configFor(platformId).copy(enabled = true))
                    pendingModelEnable = null
                }) { Text("启用") }
            },
            dismissButton = {
                TextButton(onClick = { pendingModelEnable = null }) { Text("取消") }
            }
        )
    }

    pendingProgramBinding?.let { (platformId, modelId) ->
        val platformName = AiModelPlatform.fromId(platformId).displayName
        AlertDialog(
            onDismissRequest = { pendingProgramBinding = null },
            title = { Text(if (pendingProgramEnable) "开启程序授权" else "选择程序模型") },
            text = {
                Text(
                    if (pendingProgramEnable) {
                        "欢迎页将使用 $platformName 的模型“$modelId”生成欢迎词，每天最多成功生成 5 句。请求可能消耗服务商额度或 Token；API 密钥仅加密保存在本机。程序授权同时只能绑定一个 API/模型，是否开启？"
                    } else {
                        "将 $platformName 的模型“$modelId”设为程序授权目标。关闭程序授权开关时不会发送请求；重新打开时仍需使用这个明确选择的模型。API 密钥仅加密保存在本机。是否继续？"
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    auth = auth.withProgramBinding(platformId, modelId)
                        .withProgramEnabled(pendingProgramEnable)
                    AuthStore.set(context, auth)
                    pendingProgramBinding = null
                    pendingProgramEnable = false
                }) { Text(if (pendingProgramEnable) "开启授权" else "确认选择") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingProgramBinding = null
                    pendingProgramEnable = false
                }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =======================================================
//  插件授权选择
// =======================================================
@Composable
private fun PluginAuthorizationDialog(
    plugins: List<InstalledPlugin>,
    auth: AuthSettings,
    onAuthChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Text("插件授权", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            if (plugins.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("暂未安装插件", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "安装插件后，这里会自动读取插件名称，并可逐个设置 AI 接口授权。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        "仅开启的插件可以读取导入的大模型接口。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    plugins.forEachIndexed { index, plugin ->
                        val id = plugin.manifest.id
                        val name = plugin.manifest.name.ifBlank { id }
                        val checked = auth.isPluginAuthorized(id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAuthChange(id, !checked) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                tint = if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontSize = 14.sp,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (name != id) {
                                    Text(
                                        id,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = { onAuthChange(id, it) }
                            )
                        }
                        if (index != plugins.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

// =======================================================
//  平台下拉选择
// =======================================================
@Composable
private fun PlatformSelector(
    current: AiModelPlatform?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (AiModelPlatform?) -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    1.dp,
                    if (current == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    RoundedCornerShape(18.dp)
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current?.displayName ?: "请选择",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (current == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    if (current == null) "点击选择模型服务平台" else "当前配置平台",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "展开平台列表",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "请选择",
                        fontSize = 14.sp,
                        fontWeight = if (current == null) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                },
                onClick = { onPick(null) }
            )

            AiModelPlatform.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            p.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (p == current) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (p == current) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = { onPick(p) }
                )
            }
        }
    }
}

// =======================================================
//  单个模型行
// =======================================================
@Composable
private fun ModelRow(
    entry: AiModelEntry,
    onIdChange: (String) -> Unit,
    onToggleHide: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = entry.cleanModelId,
            onValueChange = onIdChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("模型 ID，例如 deepseek-chat", fontSize = 12.sp) },
            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onToggleHide, modifier = Modifier.size(32.dp)) {
            Icon(
                if (entry.hidden) Icons.Default.Add else Icons.Default.Remove,
                if (entry.hidden) "恢复显示" else "在列表中隐藏",
                modifier = Modifier.size(18.dp),
                tint = if (entry.hidden) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "删除模型",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}
