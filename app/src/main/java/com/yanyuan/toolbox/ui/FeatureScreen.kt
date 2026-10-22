@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.yanyuan.toolbox.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yanyuan.toolbox.AppIconToggle
import com.yanyuan.toolbox.data.*
import com.yanyuan.toolbox.ui.plugin.WebPluginActivity
import com.yanyuan.toolbox.util.NotificationHelper
import com.yanyuan.toolbox.util.PermissionHelper
import com.yanyuan.toolbox.util.PluginSwitchManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

fun launchPlugin(context: Context, plugin: InstalledPlugin) {
    // 插件日志：启动记录（供"导出日志 → 插件"按插件筛选）。
    com.yanyuan.toolbox.util.LogCenter.log(
        context, com.yanyuan.toolbox.util.LogCenter.CAT_PLUGIN,
        plugin.manifest.id, "启动插件 ${plugin.manifest.name}"
    )
    val i = Intent(context, WebPluginActivity::class.java)
        .putExtra(WebPluginActivity.EXTRA_PLUGIN_ID, plugin.manifest.id)
    context.startActivity(i)
}

/** 欢迎字样（插件页顶部与平板侧栏共用，保证两处文案完全一致） */
fun welcomeText(nickname: String): String =
    if (nickname.isBlank()) "欢迎！" else "$nickname 欢迎！"

/** 平板断点：宽度 >= 600dp 视为平板/大屏 */
private val FEATURE_TABLET_BREAKPOINT = 600.dp

/** 平板导航栏从“系统导航栏底部”往上占据的总高度（含额外冗余，与设置页一致） */
private val FEATURE_NAV_TOTAL_HEIGHT_ABOVE_NAVBAR = 260.dp

/** 卡片本体高度 / 网格纵向行间距（落位动画据此估算一行的高度） */
private val CARD_HEIGHT = 124.dp
private val CARD_SPACING = 10.dp

@Composable
fun FeatureScreen(
    importEventChannel: Channel<Unit>? = null,
    showWelcome: Boolean = true,
    onToggleSidebar: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { FeatureRepository(context) }
    val firstRun = remember { FirstRunStore(context) }
    val nicknameStore = remember { NicknameStore(context) }

    var installed by remember { mutableStateOf(repository.listInstalled()) }
    var pendingPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf(nicknameStore.nickname) }
    var showImportWarning by remember { mutableStateOf(false) }

    // ★ 平板 FAB 触发导入对话框（从 Channel 接收事件）
    //    Channel 只会收到“新发送”的事件，不会因为 FeatureScreen 重建而误触发
    if (importEventChannel != null) {
        LaunchedEffect(importEventChannel) {
            for (signal in importEventChannel) {
                showImportWarning = true
            }
        }
    }

    val pickZip = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        when (val r = ZipImporter.import(context, uri)) {
            is ZipImporter.ImportResult.Success -> {
                repository.install(r.plugin)
                installed = repository.listInstalled()
                // 插件日志：安装/更新记录。
                com.yanyuan.toolbox.util.LogCenter.log(
                    context, com.yanyuan.toolbox.util.LogCenter.CAT_PLUGIN,
                    r.plugin.manifest.id, "安装/更新插件 ${r.plugin.manifest.name}"
                )

                NotificationHelper.notify(
                    context,
                    NotificationHelper.Ids.PLUGIN_INSTALLED,
                    "插件已导入",
                    "「${r.plugin.manifest.name}」安装成功"
                )
            }
            is ZipImporter.ImportResult.Failure -> {
                errorMsg = r.message
                // 插件日志：导入失败原因（排障关键）。
                com.yanyuan.toolbox.util.LogCenter.log(
                    context, com.yanyuan.toolbox.util.LogCenter.CAT_PLUGIN,
                    "import", "导入失败：${r.message}"
                )
            }
        }
    }

    fun onPluginClick(plugin: InstalledPlugin) {
        // 不检查开关：关闭状态下也能进插件设置
        val needGuide = !firstRun.isGuided(plugin.manifest.id)
                && plugin.manifest.permissions.isNotEmpty()
        if (needGuide) pendingPlugin = plugin
        else launchPlugin(context, plugin)
    }

    // ★ 从屏幕底部到平板导航栏顶边的像素高度（含额外冗余）
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val navTopFromBottomPx = with(density) {
        (navBarBottom + FEATURE_NAV_TOTAL_HEIGHT_ABOVE_NAVBAR).roundToPx()
    }

    // ★ 用 BoxWithConstraints 判断宽度：手机 → 2 列；平板 → 3 列
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= FEATURE_TABLET_BREAKPOINT
        val columnCount = if (isTablet) 3 else 2
        val gridState = rememberLazyGridState()

        // 拖动手势统一放在这个固定的父容器上。
        // 不再把手势坐标放在 LazyGrid 子项上，避免子项换列/换行时坐标系一起移动。
        var openActionId by remember { mutableStateOf<String?>(null) }
        val installedLatest = rememberUpdatedState(installed)
        val openActionLatest = rememberUpdatedState(openActionId)

        // ★ 长按操作区固定使用「翻转成面板」（另一种方案已废弃）

        // ★ 拖动排序：正在被拖动的卡片 id、浮层位置和抓取偏移。
        //   dragPointer 始终是“外层拖动容器”坐标，而不是卡片自己的局部坐标。
        //   这样卡片在网格中换列/换行后，手指坐标基准不会跟着卡片一起跳。
        var draggingId by remember { mutableStateOf<String?>(null) }
        var dragPointer by remember { mutableStateOf(Offset.Zero) }
        var dragGhostSize by remember { mutableStateOf(IntSize.Zero) }
        var dragGrab by remember { mutableStateOf(Offset.Zero) }
        var dragTopLeft by remember { mutableStateOf(Offset.Zero) }

        // 每次“真正换一次位置”才 +1。
        // 只有发生位移的那一张普通卡片拿到非零起始偏移，因此不会出现
        // “先闪到新位置，再从旧位置飞过去”的双重视觉。
        var placementAnimationToken by remember { mutableIntStateOf(0) }
        var placementStarts by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }

        // 拖动容器窗口原点。所有拖动指针都先转成这个容器的坐标。
        var dragContainerOrigin by remember { mutableStateOf(Offset.Zero) }

        // ★ 当前展开了操作区的卡片 id（同一时间只允许一张）

        // 找到“当前拖动卡片应该跨过的相邻卡片”。
        // 不再用“全局最近卡片”算法，而是只允许一次跨一个槽位。
        // 这样排序后，拖动中心会落在新的占位槽附近，不会立刻又反向跳回去。
        fun findAdjacentTarget(pointer: Offset): Int? {
            val pid = draggingId ?: return null
            val from = installed.indexOfFirst { it.manifest.id == pid }
            if (from < 0 || installed.size <= 1) return from

            val items = gridState.layoutInfo.visibleItemsInfo
            val current = items.firstOrNull { it.index == from } ?: return null

            val dragCenter = Offset(
                pointer.x - dragGrab.x + dragGhostSize.width / 2f,
                pointer.y - dragGrab.y + dragGhostSize.height / 2f
            )
            val currentCenter = Offset(
                current.offset.x + current.size.width / 2f,
                current.offset.y + current.size.height / 2f
            )
            val currentDist = (dragCenter - currentCenter).getDistance()

            val candidates = buildList {
                if (from > 0) items.firstOrNull { it.index == from - 1 }?.let { add(it) }
                if (from < installed.lastIndex) items.firstOrNull { it.index == from + 1 }?.let { add(it) }
            }
            if (candidates.isEmpty()) return from

            val candidate = candidates.minByOrNull {
                val center = Offset(
                    it.offset.x + it.size.width / 2f,
                    it.offset.y + it.size.height / 2f
                )
                (dragCenter - center).getDistance()
            } ?: return from

            val targetCenter = Offset(
                candidate.offset.x + candidate.size.width / 2f,
                candidate.offset.y + candidate.size.height / 2f
            )
            val targetDist = (dragCenter - targetCenter).getDistance()

            // 只有拖动中心真正越过“当前槽 ↔ 相邻槽”的中点才换位。
            // 加一个很小的余量，避免手指停在边界附近抖动。
            val hysteresis = minOf(current.size.width, current.size.height) * 0.04f
            return if (targetDist + hysteresis < currentDist) candidate.index else from
        }

        // ★ 一次只移动一个位置，并在“修改 installed 之前”记录受影响卡片的旧槽位。
        // 受影响卡片直接带着 old -> new 的反向偏移进入新槽位，首帧就处于旧位置，
        // 然后再动画到 0，所以不会先闪到新槽位。
        fun updateDragOrder(pointer: Offset) {
            val pid = draggingId ?: return
            val from = installed.indexOfFirst { it.manifest.id == pid }
            if (from < 0) return

            val target = findAdjacentTarget(pointer) ?: return
            if (target == from) return

            val info = gridState.layoutInfo
            val oldPositions = info.visibleItemsInfo.associate {
                it.index to Offset(it.offset.x.toFloat(), it.offset.y.toFloat())
            }

            val shiftedIndex = if (target > from) from + 1 else from - 1
            val shiftedPlugin = installed.getOrNull(shiftedIndex) ?: return
            val oldShifted = oldPositions[shiftedIndex]
            val oldDestination = oldPositions[from]

            val list = installed.toMutableList()
            val moved = list.removeAt(from)
            list.add(target.coerceIn(0, list.size), moved)
            installed = list

            if (oldShifted != null && oldDestination != null) {
                // 新槽位为 from，旧槽位为 shiftedIndex。
                placementStarts = mapOf(
                    shiftedPlugin.manifest.id to (oldShifted - oldDestination)
                )
            } else {
                placementStarts = emptyMap()
            }
            placementAnimationToken++
        }

        // 松手：拖动中的实时顺序已经是最终顺序，这里只负责结束拖动并持久化。
        fun finishMove() {
            val pid = draggingId
            if (pid != null) {
                repository.reorder(installed.map { it.manifest.id })
            }
            draggingId = null
            dragGrab = Offset.Zero
            dragPointer = Offset.Zero
            dragTopLeft = Offset.Zero
            dragGhostSize = IntSize.Zero
            placementStarts = emptyMap()
        }

        Column(Modifier.fillMaxSize().background(Color.Transparent)) {

            FeatureHeader(
                nickname = nickname,
                showWelcome = showWelcome,
                showHeaderIcon = !isTablet,
                onNicknameChange = {
                    nickname = it
                    nicknameStore.nickname = it
                },
                onToggleSidebar = onToggleSidebar
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        dragContainerOrigin = it.boundsInWindow().topLeft
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // 手指按下的这一刻，直接用父容器坐标寻找所在卡片。
                            // 这个父容器不会随着 LazyGrid 排序而改变位置，因此后续
                            // 的 change.position 始终都是同一个稳定坐标系。
                            fun itemAt(position: Offset): Int? {
                                val items = gridState.layoutInfo.visibleItemsInfo
                                return items.firstOrNull { item ->
                                    position.x >= item.offset.x &&
                                            position.x < item.offset.x + item.size.width &&
                                            position.y >= item.offset.y &&
                                            position.y < item.offset.y + item.size.height
                                }?.index
                            }

                            val downIndex = itemAt(down.position)

                            // ★ 已有卡片翻转时，按下其它任何位置就立即恢复默认卡面。
                            // 不消费这一下触摸，让被点击的其它卡片继续执行自己的点击逻辑。
                            val openedId = openActionLatest.value
                            if (openedId != null) {
                                val hitId = downIndex
                                    ?.let { installedLatest.value.getOrNull(it)?.manifest?.id }
                                if (hitId != openedId) {
                                    openActionId = null
                                    return@awaitEachGesture
                                }
                            }

                            val safeDownIndex = downIndex ?: return@awaitEachGesture
                            val startPlugin = installedLatest.value.getOrNull(safeDownIndex)
                                ?: return@awaitEachGesture
                            val startId = startPlugin.manifest.id

                            val longPress = awaitLongPressOrCancellation(down.id)
                                ?: return@awaitEachGesture

                            // ★ 长按达到阈值的这一刻就立即翻转到功能面板，
                            // 不再等待手指抬起。只有后续真正移动到 drag slop 之外时，才关闭面板并开始拖动。
                            openActionId = startId

                            // 长按成立后进入“预备拖动”状态。
                            // 只有手指真正跨过 drag slop，才算开始移动；
                            // 原地长按松手仍然会打开操作面板。
                            val startInfo = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == safeDownIndex }
                                ?: return@awaitEachGesture

                            val grab = Offset(
                                longPress.position.x - startInfo.offset.x,
                                longPress.position.y - startInfo.offset.y
                            )
                            val size = IntSize(
                                startInfo.size.width,
                                startInfo.size.height
                            )

                            // ★ 长按和单击必须彻底分开：
                            //   - 长按前松手：什么都不做，让子卡片的 clickable 正常触发单击。
                            //   - 已经长按成立后：从这一刻开始由父容器独占后续触摸，
                            //     直到松手。这样长按松手不会再被子卡片误判成单击。
                            //   - 只有超过 touch slop 才正式开始拖动，避免手指轻微抖动触发移动。
                            val slop = viewConfiguration.touchSlop
                            var startedDrag = false
                            while (true) {
                                // 用 Initial pass 抢在子卡片 clickable 前消费长按后的触摸。
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break

                                if (!change.pressed) {
                                    // 长按面板已经在达到阈值时打开；这里仅结束本次手势。
                                    if (!startedDrag) {
                                        change.consume()
                                        draggingId = null
                                        dragGrab = Offset.Zero
                                        dragPointer = Offset.Zero
                                        dragTopLeft = Offset.Zero
                                        dragGhostSize = IntSize.Zero
                                    }
                                    break
                                }

                                val current = change.position
                                val moved = current - longPress.position

                                if (!startedDrag && moved.getDistance() >= slop) {
                                    startedDrag = true
                                    // ★ 长按后如果继续移动到拖动阈值，则从功能面板模式切回拖动模式。
                                    openActionId = null
                                    change.consume()

                                    // ★ 真正开始拖动的这一帧才建立浮层。
                                    // 抓取点永远以“长按成立的原始位置”为基准，
                                    // 因此从蓝框/原卡片位置开始拖时不会突然跳一下。
                                    draggingId = startId
                                    dragGrab = grab
                                    dragGhostSize = size
                                    dragPointer = current
                                    dragTopLeft = Offset(
                                        current.x - grab.x,
                                        current.y - grab.y
                                    )
                                    updateDragOrder(dragPointer)
                                } else if (startedDrag) {
                                    change.consume()
                                    dragPointer = current
                                    dragTopLeft = Offset(
                                        current.x - grab.x,
                                        current.y - grab.y
                                    )
                                    updateDragOrder(dragPointer)
                                } else {
                                    // 长按后的轻微抖动不消费位置变化，只是继续等待 slop；
                                    // 但单击已经不可能成立，因为长按已经发生。
                                    continue
                                }

                                // 边缘自动滚动。滚动只改变网格内容，不改变父容器手指坐标。
                                if (startedDrag) {
                                    val info = gridState.layoutInfo
                                    val h = info.viewportSize.height.toFloat()
                                    val edge = 96f
                                    val y = dragPointer.y
                                    val d = when {
                                        y < edge -> -(edge - y) / edge * 22f
                                        y > h - edge -> (y - (h - edge)) / edge * 22f
                                        else -> 0f
                                    }
                                    if (d != 0f) {
                                        try { gridState.dispatchRawDelta(d) } catch (_: Exception) {}
                                    }
                                }
                            }

                            if (startedDrag) {
                                finishMove()
                            }

                        }
                    }
            ) {
                // ★ 点空白处收起已展开的操作区。
                //   放在网格之前（下层）：卡片本身可点击会优先响应，
                //   只有没被卡片覆盖的空白区域才会落到这里。
                if (openActionId != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { openActionId = null }
                    )
                }

                if (installed.isEmpty()) {
                    EmptyState()
                } else {
                    // ★ 网格：
                    //   拖动中的卡片仍然保留在列表里，只是把自己的内容替换成虚框。
                    //   这样 pointerInput 不会因为 LazyGrid 移除卡片而中断，同时其它卡片
                    //   会随着 installed 的实时顺序变化自动补位。
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(columnCount),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 6.dp, bottom = 170.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(CARD_SPACING)
                        ) {
                            itemsIndexed(installed, key = { _, it -> it.manifest.id }) { index, plugin ->
                                val columnIndex = index % columnCount
                                val pid = plugin.manifest.id
                                val isDragging = draggingId == pid

                                ToolCard(
                                    plugin = plugin,
                                    itemIndex = index,
                                    columnIndex = columnIndex,
                                    columnCount = columnCount,
                                    isTablet = isTablet,
                                    gridState = gridState,
                                    navTopFromBottomPx = navTopFromBottomPx,
                                    actionsOpen = openActionId == pid,
                                    hidden = isDragging,
                                    dragContainerOrigin = dragContainerOrigin,
                                    placementAnimationToken = placementAnimationToken,
                                    placementStart = placementStarts[pid] ?: Offset.Zero,
                                    onClick = {
                                        openActionId = null
                                        onPluginClick(plugin)
                                    },
                                    onToggleActions = { open ->
                                        openActionId = if (open) pid else null
                                    },
                                    onRequestUninstall = {
                                        // 卸载前先关掉开关、取消通知
                                        PluginSwitchManager.setSwitch(
                                            context,
                                            plugin.manifest.id,
                                            plugin.manifest.name,
                                            false
                                        )
                                        NotificationHelper.notify(
                                            context,
                                            NotificationHelper.Ids.PLUGIN_UNINSTALLED,
                                            "插件已卸载",
                                            "「${plugin.manifest.name}」已移除"
                                        )
                                        repository.uninstall(plugin.manifest.id)
                                        installed = repository.listInstalled()
                                        openActionId = null
                                        // 插件日志：卸载记录。
                                        com.yanyuan.toolbox.util.LogCenter.log(
                                            context, com.yanyuan.toolbox.util.LogCenter.CAT_PLUGIN,
                                            plugin.manifest.id, "卸载插件 ${plugin.manifest.name}"
                                        )
                                    },
                                    onMoveStart = { grabOffset, size, pointer ->
                                        draggingId = pid
                                        dragGrab = grabOffset
                                        dragGhostSize = size
                                        dragPointer = pointer
                                        dragTopLeft = Offset(
                                            pointer.x - grabOffset.x,
                                            pointer.y - grabOffset.y
                                        )
                                    },
                                    onMove = { delta ->
                                        if (draggingId == pid) {
                                            // delta 是“真实手指位移”，已在 ToolCard 内对
                                            // LazyGrid 节点自身的换行/滚动位移做了补偿。
                                            // 因此这里绝不再从当前卡片坐标反推手指位置。
                                            dragPointer += delta
                                            dragTopLeft += delta

                                            updateDragOrder(dragPointer)

                                            val info = gridState.layoutInfo
                                            val h = info.viewportSize.height.toFloat()
                                            val edge = 96f
                                            val y = dragPointer.y
                                            val d = when {
                                                y < edge -> -(edge - y) / edge * 22f
                                                y > h - edge -> (y - (h - edge)) / edge * 22f
                                                else -> 0f
                                            }
                                            if (d != 0f) {
                                                try { gridState.dispatchRawDelta(d) } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    onMoveEnd = { finishMove() }
                                )
                            }
                        }

                        // ★ 拖动浮层：独立于网格之外绘制，位置只跟手指有关。
                        val ghostPlugin = installed.firstOrNull { it.manifest.id == draggingId }
                        if (draggingId != null && ghostPlugin != null) {
                            CardDragGhost(
                                plugin = ghostPlugin,
                                topLeft = dragTopLeft,
                                size = dragGhostSize
                            )
                        }
                    }
                }

                // ★ 手机：显示右下角 FAB；平板：FAB 已在 MainActivity 中，不显示
                if (!isTablet) {
                    FloatingActionButton(
                        onClick = { showImportWarning = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .padding(bottom = 96.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "导入功能",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }

    if (showImportWarning) {
        ImportWarningDialog(
            onDismiss = { showImportWarning = false },
            onAgree = {
                showImportWarning = false
                pickZip.launch("application/zip")
            }
        )
    }

    pendingPlugin?.let { plugin ->
        FirstRunPermissionDialog(
            plugin = plugin,
            onDismiss = { pendingPlugin = null },
            onAllGranted = {
                firstRun.markGuided(plugin.manifest.id)
                pendingPlugin = null
                launchPlugin(context, plugin)
            }
        )
    }

    errorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("导入失败", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMsg = null }) { Text("知道了") }
            }
        )
    }
}

// =======================================================
//  导入警告对话框
// =======================================================
@Composable
private fun ImportWarningDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text("导入前请确认", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "插件来源",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "请仅导入你自己制作或来源可信的插件包。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "插件能做什么",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                BulletLine("读取你授予的权限")
                BulletLine("在插件页面里执行 HTML/JS")
                BulletLine("读取/修改插件自身的配置")
                BulletLine("调用无障碍、悬浮窗等已授权能力")

                Spacer(Modifier.height(14.dp))

                Text(
                    "风险提示",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF4444)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "来源不明的插件可能在你授权后做出有害行为。" +
                            "继续前请再次确认该 zip 的来源。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("我已了解，继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun BulletLine(text: String) {
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "•",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

// =======================================================
//  顶部标题栏
// =======================================================
@Composable
private fun FeatureHeader(
    nickname: String,
    showWelcome: Boolean = true,
    showHeaderIcon: Boolean = true,
    onToggleSidebar: () -> Unit = {},
    onNicknameChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(nickname) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // ★ 用一个"已挂载"标记来驱动 requestFocus。
    //   之前用 delay(120) / withFrameNanos 都是在猜输入框什么时候挂载好，
    //   一旦猜早就 requestFocus 失败（异常被吞），表现就是"光标不出现、输入法不弹"。
    //   现在改成：只有 onGloballyPositioned（布局完成、节点确实存在）之后才请求焦点。
    var attached by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(editing, attached) {
        if (editing && attached) {
            try { focusRequester.requestFocus() } catch (_: Exception) { }
        }
        if (!editing) {
            attached = false
            focused = false
        }
    }
    // ★ 确实拿到焦点之后才弹输入法，不再依赖时间间隔
    LaunchedEffect(focused) {
        if (focused) {
            try { keyboard?.show() } catch (_: Exception) { }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                // 平板（头部无图标）时起点 = 16dp，与卡片左缘（网格 contentPadding start=16）对齐。
                // 欢迎字样在内容区内（右移96dp），与最左侧固定图标不重叠，顶部无需额外让位。
                start = if (showHeaderIcon) 20.dp else 16.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 平板端头部不画图标（应用图标在导航栏导轨正上方）；
        // 手机端保留原插件页图标——侧栏仅平板可用，手机不接侧栏开关。
        if (showHeaderIcon) {
            AppIconBadge(Modifier.size(48.dp))
            Spacer(Modifier.width(14.dp))
        }

        Box(Modifier.weight(1f)) {
            if (editing) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 12) draft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onNicknameChange(draft.trim())
                                editing = false
                                keyboard?.hide()
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { attached = true }
                            .onFocusChanged { focused = it.isFocused }
                            .focusRequester(focusRequester)
                    )
                    IconButton(
                        onClick = {
                            onNicknameChange(draft.trim())
                            editing = false
                            keyboard?.hide()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Check, "完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                // 欢迎字样在侧栏打开时替换为「插件」字样（同位置、同对齐规则）；
                // 只有欢迎字样可点击进入昵称编辑。
                Text(
                    text = if (showWelcome) welcomeText(nickname) else "插件",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (showWelcome) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    draft = nickname
                                    editing = true
                                }
                            } else Modifier
                        )
                )
            }
        }
    }
}

@Composable
/** 应用图标徽章：插件页顶部与平板导航/侧栏顶部【共用】。 */
fun AppIconBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember {
        try {
            val d = context.packageManager.getApplicationIcon(context.packageName)
            val w = d.intrinsicWidth.coerceAtLeast(96)
            val h = d.intrinsicHeight.coerceAtLeast(96)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            bmp
        } catch (_: Exception) { null }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Icon(
                Icons.Default.Extension, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// =======================================================
//  首次授权对话框
// =======================================================
@Composable
private fun FirstRunPermissionDialog(
    plugin: InstalledPlugin,
    onDismiss: () -> Unit,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    val manifest = plugin.manifest
    var refreshKey by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val states = remember(refreshKey, manifest) {
        manifest.permissions.map {
            it to PermissionHelper.check(
                context, it,
                usedBy = manifest.id,
                purpose = "首次使用授权检查（${manifest.name}）"
            )
        }
    }
    val allGranted = states.all { it.second }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> refreshKey++ }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(manifest.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "首次使用需要完成以下授权",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                states.forEach { (req, granted) ->
                    PermissionRow(req, granted) {
                        when (req) {
                            is PermissionRequirement.Runtime ->
                                runtimeLauncher.launch(req.permission)
                            is PermissionRequirement.Special ->
                                PermissionHelper.openSpecialSettings(
                                    context, req.type,
                                    usedBy = manifest.id,
                                    purpose = "授权弹窗跳转"
                                )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "完成所有授权后，「进入」按钮才会亮起。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAllGranted, enabled = allGranted) {
                Text(if (allGranted) "进入" else "请先完成授权")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}

@Composable
private fun PermissionRow(
    requirement: PermissionRequirement,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted) { onRequest() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            null,
            tint = if (granted) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(requirement.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                requirement.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            Text(
                "去授权",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// =======================================================
//  空状态
// =======================================================
@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Extension, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("还没有任何功能", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "点击右下角 + 号，导入 zip 插件包",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =======================================================
//  插件卡片（动态收窄：与导航栏重叠时，从左到右递减收窄）
// =======================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCard(
    plugin: InstalledPlugin,
    itemIndex: Int,
    columnIndex: Int,
    columnCount: Int,
    isTablet: Boolean,
    gridState: LazyGridState,
    navTopFromBottomPx: Int,
    actionsOpen: Boolean,
    hidden: Boolean,
    dragContainerOrigin: Offset,
    placementAnimationToken: Int,
    placementStart: Offset,
    onClick: () -> Unit,
    onToggleActions: (Boolean) -> Unit,
    onRequestUninstall: () -> Unit,
    onMoveStart: (Offset, IntSize, Offset) -> Unit,
    // 传出真实的手指位移 delta，而不是当前局部/容器绝对坐标
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit
) {
    val context = LocalContext.current
    val firstRun = remember { FirstRunStore(context) }
    val glassAlpha = GlassOpacityState.alpha

    // 订阅开关状态
    val switchVersion = PluginSwitchState.version
    var enabled by remember(switchVersion, plugin.manifest.id) {
        mutableStateOf(PluginSwitchStore.isEnabled(context, plugin.manifest.id))
    }

    val bitmap = remember(plugin.iconFile?.absolutePath) {
        plugin.iconFile?.takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    // ★ 卡片收缩已移除：平板三页改为整体固定右移（MainActivity 的 contentStartInset），
    //   卡片不再按列位置/导航重叠量逐个收窄。
    val leftPad = 0.dp

    // ★ 补位动画：起始偏移由父层在“重排前”直接算好，并作为 remember key 的初始值。
    // 这样不会出现“先闪到新位置，再开始动画”的首帧问题。
    val placementX = remember(placementAnimationToken) {
        Animatable(if (!hidden) placementStart.x else 0f)
    }
    val placementY = remember(placementAnimationToken) {
        Animatable(if (!hidden) placementStart.y else 0f)
    }
    LaunchedEffect(placementAnimationToken, hidden) {
        if (!hidden && (placementStart.x != 0f || placementStart.y != 0f)) {
            launch { placementX.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
            launch { placementY.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
        }
    }

    // pointerInput 所在节点的窗口坐标，用于把触点转换到固定拖动容器坐标。
    var cardWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var cardWindowSize by remember { mutableStateOf(IntSize.Zero) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // ★ 外层 Box 的左侧留白 = 0（动态收窄已移除，页面级统一右移见 MainActivity）。
    Box(
        Modifier
            .padding(start = leftPad)
            .fillMaxWidth()
            .height(CARD_HEIGHT)
    ) {
        Box(
            Modifier
                .onGloballyPositioned { coordinates ->
                    cardCoordinates = coordinates
                    cardWindowOrigin = coordinates.boundsInWindow().topLeft
                    cardWindowSize = coordinates.size
                }
                // 拖动手势已经统一由 FeatureScreen 外层稳定容器处理。
                // 这里不再在 LazyGrid 子项上接管 pointerInput，避免子项换位时
                // 手势坐标系跟着卡片一起移动。
                .graphicsLayer {
                    // 被拖动卡片只显示占位框，不参与补位动画；
                    // 否则它自己的布局位移会反过来影响 pointerInput 的窗口坐标。
                    translationX = if (hidden) 0f else placementX.value
                    translationY = if (hidden) 0f else placementY.value
                }
        ) {
            if (hidden) {
                // ★ 被拖动卡片占据的就是当前位置：
                //   卡片移动到哪里，虚框就跟着移动到哪里；
                //   其它卡片自然会让出/补上这个格子。
                SlotPlaceholder()
            } else {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    // ★ 固定高度：网格里做高度动画会在动画过程中反复重新测量，
                    //   实测会连带影响同排其它卡片的显示。固定高度 + 内容替换最稳。
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_HEIGHT)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // 面板打开时，点卡片 = 收起；否则正常打开插件
                            if (actionsOpen) onToggleActions(false) else onClick()
                        }
                ) {
                    // ★ 长按操作区固定为「3D 翻转成面板」。
                    //   注意：不能用 actionsOpen 直接切内容，否则长按那一刻内容瞬间替换，
                    //   动画根本看不到。翻转量由 FlipCard 内部驱动，到 90° 才换面。
                    FlipCard(
                        flipped = actionsOpen,
                        modifier = Modifier.fillMaxSize(),
                        front = {
                            CardFrontContent(
                                plugin = plugin,
                                bitmap = bitmap,
                                enabled = enabled,
                                onSwitchChange = { wanted ->
                                    enabled = wanted
                                    PluginSwitchManager.setSwitch(
                                        context,
                                        plugin.manifest.id,
                                        plugin.manifest.name,
                                        wanted
                                    )
                                }
                            )
                        },
                        back = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    plugin.manifest.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                                CardActionBar(
                                    modifier = Modifier.align(Alignment.Center).offset(y = 10.dp),
                                    isTablet = isTablet,
                                    canReauth = plugin.manifest.permissions.isNotEmpty(),
                                    onReauth = {
                                        onToggleActions(false)
                                        firstRun.resetGuided(plugin.manifest.id)
                                    },
                                    onUninstall = {
                                        onToggleActions(false)
                                        onRequestUninstall()
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 3D 翻转容器：正面 / 背面之间做一次真实的绕 Y 轴翻转。
 *
 * 实现要点：Compose 没有背面剔除，所以不能直接把 rotationY 从 0 转到 180
 * 再切内容（会看到镜像的正面）。这里用两段半圈：
 *   0° → 90°（正面可见） → 在 90°（卡片侧对屏幕、几乎不可见）瞬间换面
 *   → 90° → 180°（背面可见）
 * 换面发生在最不显眼的一帧，观感就是完整翻了一次。
 */
@Composable
private fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation = remember { Animatable(if (flipped) 180f else 0f) }
    // 平滑按最新目标值继续动画，避免长按/收起时来回跳
    LaunchedEffect(flipped) {
        rotation.animateTo(
            targetValue = if (flipped) 180f else 0f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
        )
    }
    val angle = rotation.value
    // 过 90° 之后显示背面（并且背面自身再翻 180°，让文字不镜像）
    val showingBack = angle > 90f
    // 翻到侧面（90°）时稍微放大一点，制造“悬起来翻”的深度感，避免中间发平
    val depth = 1f + 0.06f * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
    Box(
        modifier.graphicsLayer {
            rotationY = angle
            scaleX = depth
            scaleY = depth
            cameraDistance = 12f * density
        }
    ) {
        if (showingBack) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
            ) { back() }
        } else {
            Box(Modifier.fillMaxSize()) { front() }
        }
    }
}

/**
 * 卡片正面内容：图标（左）+ 开关（右），名称与描述在下方、与图标左对齐。
 */
@Composable
private fun CardFrontContent(
    plugin: InstalledPlugin,
    bitmap: Bitmap?,
    enabled: Boolean,
    onSwitchChange: (Boolean) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部：图标（左）+ 开关（右），左对齐
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(13.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.Extension, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (plugin.manifest.hasSwitch) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onSwitchChange,
                    modifier = Modifier.scale(0.65f)
                )
            }
        }

        // 底部：名称 + 作者 + 描述，与图标左对齐
        Column(Modifier.fillMaxWidth()) {
            Text(
                plugin.manifest.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                // 行高=字号：去掉默认行高（≈1.35×）的上下留白，三行才真正贴紧。
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // ★ 作者名夹在名称与描述之间；没有 author 字段的插件不占位
            if (plugin.manifest.hasAuthor) {
                // 三行（名称+作者+说明）：行间不留额外间隔，整块紧贴。
                Spacer(Modifier.height(0.dp))
                Text(
                    plugin.manifest.author,
                    fontSize = 10.sp,
                    // 行高=字号：作者行上下不留白。
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(0.dp))
            } else {
                // 两行（名称+说明，无作者）：两行之间分开一点。
                Spacer(Modifier.height(6.dp))
            }
            Text(
                plugin.manifest.description,
                fontSize = 11.sp,
                // 说明文字保持单行紧贴排版（行高=字号，行盒不额外撑高）。
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 拖动时的虚线占位框：画在卡片原本占据的格子上。
 * 用 drawBehind 画虚线描边，不引入额外依赖。
 */
@Composable
private fun SlotPlaceholder() {
    val density = LocalDensity.current
    val strokePx = with(density) { 1.5.dp.toPx() }
    val dashPx = with(density) { 7.dp.toPx() }
    val gapPx = with(density) { 5.dp.toPx() }
    val cornerPx = with(density) { 24.dp.toPx() }
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(Offset.Zero, size),
                            CornerRadius(cornerPx, cornerPx)
                        )
                    )
                }

                drawPath(path, fill)
                drawPath(
                    path = path,
                    color = outline,
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashPx, gapPx), 0f
                        )
                    )
                )
            }
    )
}

/**
 * 拖动时跟随手指的浮层。
 * 它绘制在网格之外（父 Box 的坐标系里），位置只取决于手指，
 * 所以列表实时换位不会把它带偏，也就不会出现“飞出去再弹回来”。
 */
@Composable
private fun CardDragGhost(
    plugin: InstalledPlugin,
    topLeft: Offset,
    size: IntSize
) {
    val context = LocalContext.current
    val glassAlpha = GlassOpacityState.alpha
    val bitmap = remember(plugin.iconFile?.absolutePath) {
        plugin.iconFile?.takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    val switchVersion = PluginSwitchState.version
    val enabled = remember(switchVersion, plugin.manifest.id) {
        PluginSwitchStore.isEnabled(context, plugin.manifest.id)
    }

    // 没有量到格子尺寸时给个兜底，避免浮层塌成 0
    val wDp = if (size.width > 0) with(LocalDensity.current) { size.width.toDp() } else 150.dp
    val hDp = if (size.height > 0) with(LocalDensity.current) { size.height.toDp() } else CARD_HEIGHT
    val xDp = with(LocalDensity.current) { topLeft.x.toDp() }
    val yDp = with(LocalDensity.current) { topLeft.y.toDp() }

    Box(
        Modifier
            .offset(x = xDp, y = yDp)
            .zIndex(20f)
            .size(width = wDp, height = hDp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Extension, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (plugin.manifest.hasSwitch) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = null,
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }
            Column {
                Text(
                    plugin.manifest.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                // 与卡片"无作者"两行排版一致：两行之间分开一点。
                Spacer(Modifier.height(6.dp))
                Text(
                    plugin.manifest.description,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 卡片内的操作栏：重新授权 / 卸载此功能
 * 直接嵌在卡片里（移动卡片已改为"长按卡片直接拖"，不再需要按钮）。
 */
@Composable
private fun CardActionBar(
    modifier: Modifier = Modifier,
    isTablet: Boolean,
    canReauth: Boolean,
    onReauth: () -> Unit,
    onUninstall: () -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (isTablet) 16.dp else 2.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val actionCount = if (canReauth) 2 else 1
        val itemModifier = when {
            isTablet -> Modifier.widthIn(min = 96.dp)
            actionCount > 1 -> Modifier.weight(1f)
            else -> Modifier.widthIn(min = 76.dp)
        }
        if (canReauth) {
            CardActionItem(
                modifier = itemModifier,
                isTablet = isTablet,
                icon = Icons.Default.CheckCircle,
                label = "重新授权",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onReauth
            )
        }
        CardActionItem(
            modifier = itemModifier,
            isTablet = isTablet,
            icon = Icons.Default.DeleteOutline,
            label = "卸载此功能",
            tint = MaterialTheme.colorScheme.error,
            onClick = onUninstall
        )
    }
}

@Composable
private fun CardActionItem(
    modifier: Modifier,
    isTablet: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = tint,
            maxLines = if (isTablet) 1 else 3,
            softWrap = !isTablet,
            textAlign = TextAlign.Center
        )
    }
}
