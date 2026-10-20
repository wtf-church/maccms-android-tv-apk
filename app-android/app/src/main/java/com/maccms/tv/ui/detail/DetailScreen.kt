package com.maccms.tv.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.maccms.tv.data.Episode
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodParser
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.FavoriteStore
import com.maccms.tv.util.PlaybackStore
import com.maccms.tv.util.RichTextParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 详情页：背景大图 + 浮层信息 + 默认「播放/续播」按钮 + 播放源分集列表。
 *
 * 播放按钮不指定集数，交给播放页按 PlaybackStore 的观看记录续播
 * （没有记录时才从第一个源的第一集开始）。
 */
@Composable
fun DetailScreen(
    vodId: Int,
    repository: VodRepository,
    onBack: () -> Unit,
    onPlay: (Vod) -> Unit,
    onPlayEpisode: (Vod, Episode) -> Unit
) {
    var vod by remember { mutableStateOf<Vod?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            repository.getDetail(vodId).onSuccess {
                vod = it
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    LaunchedEffect(vodId) { load() }

    Box(Modifier.fillMaxSize().background(NetflixBlack)) {
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error ?: "", onRetry = { load() })
            vod != null -> DetailContent(
                vod = vod!!,
                onBack = onBack,
                onPlay = onPlay,
                onPlayEpisode = onPlayEpisode
            )
        }
    }
}

@Composable
private fun DetailContent(
    vod: Vod,
    onBack: () -> Unit,
    onPlay: (Vod) -> Unit,
    onPlayEpisode: (Vod, Episode) -> Unit
) {
    val context = LocalContext.current
    val sources = remember(vod.vod_play_url) {
        VodParser.parsePlaySources(vod.vod_play_from, vod.vod_play_url)
    }
    var selectedSource by remember { mutableStateOf(0) }
    val currentSource = sources.getOrNull(selectedSource) ?: sources.firstOrNull()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 上次观看记录：用于「继续观看」按钮文案和选集里的标记
    val lastEpName = remember(vod.vod_id) { PlaybackStore.lastEpisode(context, vod.vod_id) }
    val lastPosMs = remember(vod.vod_id, lastEpName) {
        lastEpName?.let { PlaybackStore.position(context, vod.vod_id, it) } ?: 0L
    }
    val playLabel = when {
        lastEpName == null -> "▶  播放"
        lastPosMs > 0 -> "▶  继续观看 · $lastEpName  ${formatClock(lastPosMs)}"
        else -> "▶  播放 · $lastEpName"
    }

    // 遥控器焦点状态机：BACK(返回) / PLAY(播放+收藏按钮行) / TAB(源标签) / GRID(选集网格)
    var area by remember { mutableStateOf(Area.PLAY) }
    var tabIndex by remember { mutableStateOf(0) }
    var playFocused by remember { mutableStateOf(false) }
    // 播放按钮行内的光标：0 = 播放，1 = 收藏
    var actionIndex by remember { mutableStateOf(0) }
    var favorite by remember(vod.vod_id) { mutableStateOf(FavoriteStore.isFavorite(context, vod.vod_id)) }

    val backRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val favRequester = remember { FocusRequester() }
    val tabRequesters = remember(sources) { List(sources.size) { FocusRequester() } }

    fun moveTo(a: Area, index: Int = 0) {
        area = a
        if (a == Area.PLAY) actionIndex = index
        scope.launch {
            when (a) {
                Area.BACK -> {
                    listState.animateScrollToItem(0)
                    runCatching { backRequester.requestFocus() }
                }
                Area.PLAY -> {
                    listState.animateScrollToItem(0)
                    runCatching {
                        (if (index == 0) playRequester else favRequester).requestFocus()
                    }
                }
                Area.TAB -> {
                    listState.animateScrollToItem(1)
                    runCatching { tabRequesters.getOrNull(index)?.requestFocus() }
                }
                Area.GRID -> {
                    // 滚动到选集网格，其中的 LaunchedEffect(activate) 会聚焦对应格子
                    listState.animateScrollToItem(2)
                }
            }
        }
    }

    // 进入页面默认聚焦播放按钮。
    // 导航转场结束前焦点可能被系统重置到第一个可聚焦节点，这里短暂重试；
    // 一旦播放按钮真正拿到焦点（或用户已开始操作）就停止，避免抢焦点。
    LaunchedEffect(Unit) {
        repeat(10) {
            delay(80)
            if (playFocused || actionIndex != 0) return@LaunchedEffect
            runCatching { playRequester.requestFocus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (area) {
                    Area.BACK -> when (e.key) {
                        Key.DirectionDown, Key.DirectionRight -> {
                            moveTo(Area.PLAY)
                            true
                        }
                        Key.DirectionUp, Key.DirectionLeft -> true
                        else -> false
                    }
                    Area.PLAY -> when (e.key) {
                        Key.DirectionUp -> {
                            moveTo(Area.BACK)
                            true
                        }
                        Key.DirectionDown -> {
                            if (sources.isNotEmpty()) moveTo(Area.TAB, selectedSource)
                            true
                        }
                        Key.DirectionLeft -> {
                            if (actionIndex > 0) moveTo(Area.PLAY, actionIndex - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            if (actionIndex < 1) moveTo(Area.PLAY, actionIndex + 1)
                            true
                        }
                        else -> false
                    }
                    Area.TAB -> when (e.key) {
                        Key.DirectionLeft -> {
                            if (tabIndex > 0) moveTo(Area.TAB, tabIndex - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            if (tabIndex < sources.lastIndex) moveTo(Area.TAB, tabIndex + 1)
                            true
                        }
                        Key.DirectionUp -> {
                            moveTo(Area.PLAY)
                            true
                        }
                        Key.DirectionDown -> {
                            moveTo(Area.GRID)
                            true
                        }
                        else -> false
                    }
                    // GRID 区域由 EpisodeGrid 内部拦截处理
                    Area.GRID -> false
                }
            }
    ) {
        // 背景大图 + 浮层文字 + 默认播放按钮
        item {
            Box(Modifier.fillMaxWidth().height(460.dp)) {
                AsyncImage(
                    model = vod.vod_pic,
                    contentDescription = vod.vod_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 左侧暗化，保证标题/简介在浅色画面上也清晰
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color(0xE6000000),
                            0.55f to Color(0x80000000),
                            1f to Color(0x22000000)
                        )
                    )
                )
                // 底部渐隐到页面背景
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, NetflixBlack))
                        )
                )
                BackButton(
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                    focusRequester = backRequester
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, end = 48.dp, bottom = 28.dp)
                ) {
                    Text(
                        vod.vod_name,
                        color = TextWhite,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vod.vod_score.isNotBlank() && vod.vod_score != "0") {
                            Text(vod.vod_score, color = NetflixRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                        }
                        listOf(vod.vod_year, vod.vod_area, vod.vod_lang, vod.vod_remarks)
                            .filter { it.isNotBlank() }
                            .forEach {
                                Text(it, color = TextGray, fontSize = 14.sp)
                                Spacer(Modifier.width(12.dp))
                            }
                    }
                    if (vod.vod_content.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            RichTextParser.toPlainText(vod.vod_content),
                            color = TextWhite.copy(alpha = 0.82f),
                            fontSize = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (vod.vod_actor.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "主演：${vod.vod_actor}",
                            color = TextGray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayButton(
                            label = playLabel,
                            onClick = { onPlay(vod) },
                            focusRequester = playRequester,
                            onFocusChange = { focused ->
                                playFocused = focused
                                if (focused) {
                                    area = Area.PLAY
                                    actionIndex = 0
                                }
                            }
                        )
                        Spacer(Modifier.width(14.dp))
                        FavoriteButton(
                            favorite = favorite,
                            onClick = { favorite = FavoriteStore.toggle(context, vod) },
                            focusRequester = favRequester,
                            onFocusChange = { focused ->
                                if (focused) {
                                    area = Area.PLAY
                                    actionIndex = 1
                                }
                            }
                        )
                    }
                }
            }
        }
        if (sources.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(start = 48.dp, top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sources.forEachIndexed { i, s ->
                        SourceTab(
                            name = s.from,
                            selected = i == selectedSource,
                            onClick = { selectedSource = i },
                            focusRequester = tabRequesters.getOrNull(i),
                            onTabFocused = {
                                tabIndex = i
                                area = Area.TAB
                            }
                        )
                    }
                }
            }
            if (currentSource != null) {
                item {
                    EpisodeGrid(
                        episodes = currentSource.episodes,
                        lastEpName = lastEpName,
                        lastPosMs = lastPosMs,
                        onPlayEpisode = { ep -> onPlayEpisode(vod, ep) },
                        onExitUp = { moveTo(Area.TAB, selectedSource) },
                        activate = area == Area.GRID
                    )
                }
            }
        } else {
            item {
                Text(
                    "暂无播放源",
                    color = TextGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

private enum class Area { BACK, PLAY, TAB, GRID }

@Composable
private fun PlayButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "playBtnScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) NetflixRed else Color.Transparent,
        label = "playBtnBorder"
    )
    Box(
        Modifier
            .height(48.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color(0xFFE0E0E0))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) }
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.Black,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FavoriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "favBtnScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "favBtnBorder"
    )
    Box(
        Modifier
            .height(48.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(6.dp))
            .background(if (favorite) NetflixRed.copy(alpha = 0.85f) else Color(0xCC2A2A2A))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (favorite) "✓  已收藏" else "＋  收藏",
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "backBorder"
    )
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(Color(0xAA000000))
            .border(2.dp, borderColor, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onBack)
    ) {
        Text(
            "‹  返回",
            color = TextWhite,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SourceTab(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onTabFocused: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "srcBorder"
    )
    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> NetflixRed
                    focused -> Color(0xFF333333)
                    else -> Color(0xFF222222)
                }
            )
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onTabFocused() }
            .clickable(onClick = onClick)
    ) {
        Text(
            name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * 选集网格：每屏 6 列 x 2 行（12 集/页）。遥控器左右在页内移动，
 * 页边界处 LEFT/RIGHT 翻页并聚焦新页对应位；第一行 UP 回源 Tab。
 * 进入网格时自动聚焦第一个格子；上次观看的集会高亮并显示看到的位置。
 */
@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    lastEpName: String?,
    lastPosMs: Long,
    onPlayEpisode: (Episode) -> Unit,
    onExitUp: () -> Unit,
    activate: Boolean
) {
    val pageSize = 12
    val pageCount = (episodes.size + pageSize - 1) / pageSize
    var pageIndex by remember(episodes) { mutableStateOf(0) }
    val page = episodes.drop(pageIndex * pageSize).take(pageSize)
    // 翻页后要请求焦点的页内索引（新页组合完成后 requester 已附加）
    var pendingFocus by remember { mutableStateOf(0) }
    val cellRequesters = remember(page) { List(page.size) { FocusRequester() } }

    // 从 Tab 下行进入 / 页切换时，聚焦对应格子。
    // 网格可能仍在滚动组合中，轮询请求直到成功（requestFocus 在节点未组合时抛异常）。
    LaunchedEffect(pageIndex, page.size, pendingFocus, activate) {
        if (!activate || page.isEmpty()) return@LaunchedEffect
        repeat(5) {
            delay(60)
            val ok = runCatching {
                cellRequesters.getOrNull(pendingFocus.coerceIn(0, page.size - 1))?.requestFocus()
            }.isSuccess
            if (ok) return@LaunchedEffect
        }
    }

    fun goToPage(newPage: Int, focusIndex: Int) {
        pageIndex = newPage
        pendingFocus = focusIndex
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "选集 (${episodes.size})",
            color = TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp)
        ) {
            page.chunked(6).forEachIndexed { rowIdx, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    row.forEachIndexed { colIdx, ep ->
                        val idx = rowIdx * 6 + colIdx
                        val onKey: (KeyEvent) -> Boolean = { e ->
                            if (e.type == KeyEventType.KeyDown) {
                                when (e.key) {
                                    Key.DirectionUp -> {
                                        if (rowIdx == 0) {
                                            onExitUp()
                                            true
                                        } else false // 页内上一行由默认焦点移动处理
                                    }
                                    Key.DirectionDown -> {
                                        // 每页 2 行：第二行 DOWN 翻下一页同列
                                        if (rowIdx == 1) {
                                            if (pageIndex < pageCount - 1) {
                                                goToPage(pageIndex + 1, colIdx)
                                                true
                                            } else true
                                        } else false
                                    }
                                    Key.DirectionLeft -> {
                                        if (colIdx == 0) {
                                            if (pageIndex > 0) {
                                                val newSize = episodes.drop((pageIndex - 1) * pageSize).take(pageSize).size
                                                val target = (rowIdx * 6 + 5).coerceAtMost(newSize - 1)
                                                goToPage(pageIndex - 1, target)
                                                true
                                            } else true
                                        } else false
                                    }
                                    Key.DirectionRight -> {
                                        if (colIdx == 5) {
                                            if (pageIndex < pageCount - 1) {
                                                goToPage(pageIndex + 1, rowIdx * 6)
                                                true
                                            } else true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        val isLast = lastEpName != null && ep.name == lastEpName
                        EpisodeCell(
                            ep = ep,
                            isLastWatched = isLast,
                            lastPosMs = if (isLast) lastPosMs else 0L,
                            onPlay = { onPlayEpisode(ep) },
                            focusRequester = cellRequesters[idx],
                            onKey = onKey
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCell(
    ep: Episode,
    isLastWatched: Boolean,
    lastPosMs: Long,
    onPlay: () -> Unit,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "epCellScale"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> TextWhite
            isLastWatched -> NetflixRed
            else -> Color.Transparent
        },
        label = "epCellBorder"
    )
    Box(
        Modifier
            .size(width = 96.dp, height = 72.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> NetflixRed.copy(alpha = 0.3f)
                    isLastWatched -> Color(0xFF3A1F1F)
                    else -> Color(0xFF2A2A2A)
                }
            )
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent(onKey)
            .focusRequester(focusRequester)
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ep.name,
                color = TextWhite,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            if (isLastWatched) {
                Text(
                    if (lastPosMs > 0) formatClock(lastPosMs) else "上次观看",
                    color = NetflixRed,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** 毫秒 → mm:ss / h:mm:ss */
private fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
