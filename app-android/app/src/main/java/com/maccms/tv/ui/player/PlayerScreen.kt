package com.maccms.tv.ui.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.buttons.PlaybackSpeedControl
import androidx.media3.ui.compose.material3.Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.buttons.PlaybackSpeedToggleButton
import com.maccms.tv.data.Episode
import com.maccms.tv.data.PlaySource
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodParser
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.PlaybackStore
import com.maccms.tv.util.WatchHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

/** 选集面板每页 8 列 x 3 行 */
private const val EP_COLUMNS = 8
private const val EP_ROWS = 3
private const val EP_PAGE_SIZE = EP_COLUMNS * EP_ROWS

private enum class MenuArea { SOURCES, GRID }

/**
 * 播放页：官方 Media3 Player composable（内建 Android TV D-pad 焦点导航与进度拖动）。
 *
 * 交互约定：
 * - 方向下：拉起底部选集面板（线路 + 分集），不用退回详情页
 * - 方向左右：快退/快进 10 秒；OK：暂停/继续；返回：关闭面板/退出
 * - 一集播完自动连播下一集
 * - 进度每 5 秒、暂停、切集、离开时保存，详情页「播放」据此续播
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    vod: Vod,
    episode: Episode?,
    repository: VodRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.get(context) }

    // 候选播放地址列表（m3u8 优先；播放失败自动切换下一个）
    var isPlaying by remember { mutableStateOf(false) }
    var playUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var playIndex by remember { mutableStateOf(0) }
    var playFailed by remember { mutableStateOf(false) }
    var isResolved by remember { mutableStateOf(false) }
    var switchingEpisode by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(vod.vod_name + if (episode != null) " · ${episode.name}" else "") }

    // 续播：effectiveEpName 为实际播放的分集（首页入口可能回落到上次观看的集）
    var effectiveEpName by remember { mutableStateOf(episode?.name ?: "") }
    var resumePositionMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    // 换集装载期间 clearMediaItems 会让播放器短暂进入 STATE_ENDED，
    // 用它屏蔽这段“假结束”，避免触发自动连播把刚选的集覆盖掉
    var loadingMedia by remember { mutableStateOf(false) }

    // 带播放地址的影片详情（vod 本身可能来自首页缓存，缺少 vod_play_url）
    var playVod by remember { mutableStateOf(vod) }
    var sources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }

    // 选集面板状态
    var showEpisodes by remember { mutableStateOf(false) }
    var menuSourceIdx by remember { mutableStateOf(0) }
    var menuPage by remember { mutableStateOf(0) }
    var menuFocus by remember { mutableStateOf(0) }
    var menuArea by remember { mutableStateOf(MenuArea.GRID) }

    // 控件显隐：默认显示；方向键/菜单键唤出
    var showControls by remember { mutableStateOf(true) }
    val hideJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun scheduleHideControls() {
        hideJob.value?.cancel()
        if (player.isPlaying) {
            hideJob.value = scope.launch {
                delay(CONTROLS_VISIBILITY_TIMEOUT)
                showControls = false
            }
        }
    }

    fun showControlsTemporarily() {
        showControls = true
        scheduleHideControls()
    }

    LaunchedEffect(showControls, showEpisodes, isPlaying) {
        if (showControls && !showEpisodes && isPlaying) scheduleHideControls()
        else hideJob.value?.cancel()
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    // 面板开合后把焦点收回播放器，避免焦点停留在控件上
    LaunchedEffect(showEpisodes) { runCatching { rootFocus.requestFocus() } }

    /** 保存当前进度；<5s 或距结尾 10s 内由 PlaybackStore 判定为没看/看完并清除 */
    fun saveProgress() {
        val pos = player.currentPosition.coerceAtLeast(0L)
        if (effectiveEpName.isNotBlank()) {
            PlaybackStore.save(context, vod.vod_id, effectiveEpName, pos, player.duration)
            // 观看历史同步记录（即使刚开播也写入，历史页才能“一步到位”）
            WatchHistoryStore.record(context, playVod, effectiveEpName, pos, player.duration)
        }
    }

    /**
     * 展开候选地址：
     * - 有直链（m3u8/mp4）时只用直链，避免为了 HTML 分享页去抓网页、拖慢起播
     * - 只有分享页时才逐个抓取解析出真实 m3u8
     */
    suspend fun expandUrls(raw: List<String>): List<String> {
        val cleaned = raw.filter { it.isNotBlank() }.distinct()
        val direct = cleaned.filter { VodParser.isVideoUrl(it) }
        if (direct.isNotEmpty()) return direct
        return cleaned.map { resolveHtmlPlayUrl(it) ?: it }
    }

    /**
     * 按分集名解析候选地址：优先给定线路中的同名集，再用其它线路的同名集补齐。
     * 同一集往往在另一条线路有直链 m3u8，expandUrls 会优先用直链。
     */
    suspend fun resolveEpisodeUrls(base: Vod, epName: String, source: PlaySource?): List<String> {
        val raw = mutableListOf<String>()
        source?.episodes?.filter { it.name == epName }?.forEach { raw += it.url }
        VodParser.urlsByName(base.vod_play_from, base.vod_play_url, epName)
            .forEach { if (it !in raw) raw += it }
        return expandUrls(raw)
    }

    /**
     * 装载第 [index] 个候选地址。[startPosMs] > 0 时从该位置起播。
     * 显式调用（而不是靠 effect 观察状态），保证换集只装载一次、续播位置不被覆盖。
     */
    fun loadUrl(index: Int, startPosMs: Long) {
        val url = playUrls.getOrNull(index) ?: return
        loadingMedia = true
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url), startPosMs)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * 切换到指定分集：保存当前进度 → 解析地址 → 从该集保存的位置续播。
     * 面板选中和自动连播共用。
     */
    fun playEpisode(source: PlaySource, ep: Episode) {
        saveProgress()
        val resume = PlaybackStore.position(context, vod.vod_id, ep.name)
        showEpisodes = false
        showControls = false
        hideJob.value?.cancel()
        switchingEpisode = true
        scope.launch {
            val urls = resolveEpisodeUrls(playVod, ep.name, source)
                .ifEmpty { listOfNotNull(ep.url.takeIf { it.isNotBlank() }) }
            switchingEpisode = false
            if (urls.isEmpty()) {
                playFailed = true
                showControlsTemporarily()
                return@launch
            }
            effectiveEpName = ep.name
            resumePositionMs = resume
            title = playVod.vod_name + " · " + ep.name
            playUrls = urls
            playIndex = 0
            playFailed = false
            loadUrl(0, resume)
            WatchHistoryStore.record(context, playVod, ep.name, resume, 0L)
        }
    }

    /** 打开选集面板，并把光标定位到当前播放的分集 */
    fun openEpisodes() {
        if (sources.isEmpty()) return
        var srcIdx = sources.indexOfFirst { s -> s.episodes.any { it.name == effectiveEpName } }
        if (srcIdx < 0) srcIdx = 0
        menuSourceIdx = srcIdx
        val epIdx = sources[srcIdx].episodes.indexOfFirst { it.name == effectiveEpName }
        if (epIdx >= 0) {
            menuPage = epIdx / EP_PAGE_SIZE
            menuFocus = epIdx % EP_PAGE_SIZE
        } else {
            menuPage = 0
            menuFocus = 0
        }
        menuArea = MenuArea.GRID
        showEpisodes = true
        showControls = false
        hideJob.value?.cancel()
    }

    fun closeEpisodes() {
        showEpisodes = false
        showControlsTemporarily()
    }

    fun closePanel() = closeEpisodes()

    /** 面板打开时的按键：纯游标式导航，焦点始终留在播放器根节点上 */
    fun handlePanelKey(e: KeyEvent): Boolean {
        val eps = sources.getOrNull(menuSourceIdx)?.episodes ?: emptyList()
        val pageCount = if (eps.isEmpty()) 1 else (eps.size + EP_PAGE_SIZE - 1) / EP_PAGE_SIZE
        fun pageSizeOf(page: Int) = (eps.size - page * EP_PAGE_SIZE).coerceIn(0, EP_PAGE_SIZE)

        when (e.key) {
            Key.Back, Key.Menu -> {
                closePanel()
                return true
            }
            Key.DirectionUp -> {
                if (menuArea == MenuArea.GRID) {
                    if (menuFocus / EP_COLUMNS > 0) {
                        menuFocus -= EP_COLUMNS
                    } else if (sources.size > 1) {
                        // 第一行再往上 → 线路行
                        menuArea = MenuArea.SOURCES
                    } else {
                        closePanel()
                    }
                }
                return true
            }
            Key.DirectionDown -> {
                if (menuArea == MenuArea.SOURCES) {
                    menuArea = MenuArea.GRID
                } else {
                    val rowsOnPage = (pageSizeOf(menuPage) + EP_COLUMNS - 1) / EP_COLUMNS
                    val target = menuFocus + EP_COLUMNS
                    if (menuFocus / EP_COLUMNS < rowsOnPage - 1 && target < pageSizeOf(menuPage)) {
                        menuFocus = target
                    }
                }
                return true
            }
            Key.DirectionLeft -> {
                if (menuArea == MenuArea.SOURCES) {
                    if (menuSourceIdx > 0) {
                        menuSourceIdx -= 1
                        menuPage = 0
                        menuFocus = 0
                    }
                } else if (menuFocus % EP_COLUMNS > 0) {
                    menuFocus -= 1
                } else if (menuPage > 0) {
                    menuPage -= 1
                    val ps = pageSizeOf(menuPage)
                    val target = (menuFocus / EP_COLUMNS) * EP_COLUMNS + EP_COLUMNS - 1
                    menuFocus = target.coerceAtMost((ps - 1).coerceAtLeast(0))
                }
                return true
            }
            Key.DirectionRight -> {
                if (menuArea == MenuArea.SOURCES) {
                    if (menuSourceIdx < sources.lastIndex) {
                        menuSourceIdx += 1
                        menuPage = 0
                        menuFocus = 0
                    }
                } else {
                    val ps = pageSizeOf(menuPage)
                    val col = menuFocus % EP_COLUMNS
                    if (col < EP_COLUMNS - 1 && menuFocus + 1 < ps) {
                        menuFocus += 1
                    } else if (menuPage < pageCount - 1) {
                        menuPage += 1
                        val target = (menuFocus / EP_COLUMNS) * EP_COLUMNS
                        menuFocus = target.coerceAtMost((pageSizeOf(menuPage) - 1).coerceAtLeast(0))
                    }
                }
                return true
            }
            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                if (menuArea == MenuArea.SOURCES) {
                    menuArea = MenuArea.GRID
                } else {
                    val ep = eps.getOrNull(menuPage * EP_PAGE_SIZE + menuFocus)
                    val src = sources.getOrNull(menuSourceIdx)
                    if (ep != null && src != null) playEpisode(src, ep)
                }
                return true
            }
            else -> return true
        }
    }

    // 解析播放地址（续播目标：本次点选集 > 上次观看 > 第一集）
    LaunchedEffect(vod.vod_id, episode) {
        var base = vod
        if (base.vod_play_url.isBlank()) {
            repository.getDetail(vod.vod_id).onSuccess { detail -> base = detail }
        }
        playVod = base
        val srcs = VodParser.parsePlaySources(base.vod_play_from, base.vod_play_url)
        sources = srcs
        val targetEp = episode?.name?.takeIf { it.isNotBlank() }
            ?: PlaybackStore.lastEpisode(context, vod.vod_id)
            ?: srcs.firstOrNull()?.firstEpisode?.name
        effectiveEpName = targetEp ?: ""
        resumePositionMs = targetEp?.let { PlaybackStore.position(context, vod.vod_id, it) } ?: 0L
        title = base.vod_name + if (targetEp != null) " · $targetEp" else ""
        var urls = if (targetEp != null) resolveEpisodeUrls(base, targetEp, null) else emptyList()
        if (urls.isEmpty()) {
            urls = expandUrls(VodParser.allUrls(base.vod_play_from, base.vod_play_url))
        }
        if (urls.isEmpty()) {
            urls = expandUrls(listOfNotNull(episode?.url?.takeIf { it.isNotBlank() }))
        }
        playUrls = urls
        playIndex = 0
        playFailed = false
        isResolved = true
        if (urls.isNotEmpty()) {
            loadUrl(0, resumePositionMs)
            if (targetEp != null) {
                WatchHistoryStore.record(context, base, targetEp, resumePositionMs, 0L)
            }
        }
    }

    // 播放中每 5 秒保存一次进度（onDispose 在进程被杀时不保证执行）
    LaunchedEffect(effectiveEpName, isResolved) {
        if (!isResolved) return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            if (player.isPlaying) saveProgress()
        }
    }

    // 一集播完自动连播下一集（同一线路内）
    val onEnded by rememberUpdatedState {
        val src = sources.firstOrNull { s -> s.episodes.any { it.name == effectiveEpName } }
            ?: sources.firstOrNull()
        if (src != null) {
            val idx = src.episodes.indexOfFirst { it.name == effectiveEpName }
            val next = if (idx >= 0) src.episodes.getOrNull(idx + 1) else null
            if (next != null) playEpisode(src, next)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) loadingMedia = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) loadingMedia = false
                // 只对真正播完的结束事件自动连播；换集清空媒体造成的 ENDED 忽略
                if (state == Player.STATE_ENDED && !loadingMedia) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                loadingMedia = false
                if (playIndex < playUrls.size - 1) {
                    val retryPos = player.currentPosition.coerceAtLeast(0L)
                    playIndex += 1
                    loadUrl(playIndex, retryPos)
                } else {
                    playFailed = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (showEpisodes) return@onKeyEvent handlePanelKey(keyEvent)
                    when (keyEvent.key) {
                        // 菜单键：同样拉起选集面板
                        Key.Menu -> {
                            openEpisodes()
                            true
                        }
                        // 返回键：交给系统回退（面板打开时先关面板）
                        Key.Back -> false
                        // 下键：选集面板；上键：唤出控件
                        Key.DirectionDown -> {
                            if (sources.isNotEmpty()) openEpisodes() else showControlsTemporarily()
                            true
                        }
                        Key.DirectionUp -> {
                            showControlsTemporarily()
                            true
                        }
                        // 左右键：快退/快进 10 秒
                        Key.DirectionLeft -> {
                            showControlsTemporarily()
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            showControlsTemporarily()
                            val newPos = player.currentPosition + 10_000L
                            // duration 未加载（TIME_UNSET）时不限制，避免回跳 0
                            player.seekTo(
                                if (player.duration > 0) newPos.coerceAtMost(player.duration) else newPos
                            )
                            true
                        }
                        // OK 键：暂停 / 继续
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                            showControlsTemporarily()
                            if (player.isPlaying) {
                                player.pause()
                                saveProgress()
                            } else {
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                player.play()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Player(
            player = player,
            showControls = showControls,
            modifier = Modifier.fillMaxSize(),
            topControls = { p, visible ->
                PlayerDefaults.TopControls(p, visible, Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            title,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 20.dp, end = 90.dp)
                        )
                        TextButton(
                            onClick = { openEpisodes() },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) { Text("选集", color = TextWhite) }
                    }
                }
            },
            centerControls = { p, visible -> PlayerDefaults.CenterControls(p, visible) },
            bottomControls = { p, visible ->
                PlayerDefaults.BottomControls(
                    p,
                    visible,
                    Modifier.fillMaxWidth(),
                    // 倍速切换按钮放进度条下方
                    below = { player ->
                        PlaybackSpeedControl(player) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                PlaybackSpeedToggleButton(player)
                            }
                        }
                    }
                )
            }
        )

        // 加载状态提示
        when {
            !isResolved -> StatusHint("正在获取播放地址…")
            playUrls.isEmpty() -> StatusHint("没有可用的播放地址")
            playFailed -> StatusHint("播放失败，请按 ↓ 选择其它集数或线路")
            switchingEpisode -> StatusHint("正在切换集数…")
            !showControls && !showEpisodes && isBuffering -> StatusHint("正在加载…")
            !showControls && !showEpisodes && !isPlaying -> StatusHint("按 OK 继续播放")
        }

        // 底部选集面板：线路 + 分集（方向键选择，OK 播放）
        AnimatedVisibility(
            visible = showEpisodes,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            EpisodePanel(
                sources = sources,
                sourceIndex = menuSourceIdx,
                area = menuArea,
                page = menuPage,
                focusIndex = menuFocus,
                currentEpName = effectiveEpName,
                onSelectSource = { idx ->
                    menuSourceIdx = idx
                    menuPage = 0
                    menuFocus = 0
                },
                onSelectEpisode = { src, ep -> playEpisode(src, ep) }
            )
        }
    }
}

/**
 * 选集面板：一行线路 + 8x3 分集网格（每页 24 集）。
 * 面板本身不接管焦点，导航由 PlayerScreen 的根节点统一处理。
 */
@Composable
private fun EpisodePanel(
    sources: List<PlaySource>,
    sourceIndex: Int,
    area: MenuArea,
    page: Int,
    focusIndex: Int,
    currentEpName: String,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (PlaySource, Episode) -> Unit
) {
    val source = sources.getOrNull(sourceIndex)
    val episodes = source?.episodes ?: emptyList()
    val pageCount = if (episodes.isEmpty()) 1 else (episodes.size + EP_PAGE_SIZE - 1) / EP_PAGE_SIZE
    val pageEps = episodes.drop(page * EP_PAGE_SIZE).take(EP_PAGE_SIZE)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xF2141414))
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("选集", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text(
                source?.from ?: "",
                color = TextGray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                "第 ${page + 1}/$pageCount 页 · 共 ${episodes.size} 集",
                color = TextGray,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(16.dp))
            Text("方向键选择 · OK 播放 · 返回 关闭", color = TextGray, fontSize = 12.sp)
        }

        if (sources.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sources.forEachIndexed { i, s ->
                    SourceChip(
                        name = s.from,
                        selected = i == sourceIndex,
                        focused = area == MenuArea.SOURCES && i == sourceIndex,
                        onClick = { onSelectSource(i) }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        pageEps.chunked(EP_COLUMNS).forEachIndexed { rowIdx, row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEachIndexed { colIdx, ep ->
                    EpisodeChip(
                        name = ep.name,
                        focused = area == MenuArea.GRID && rowIdx * EP_COLUMNS + colIdx == focusIndex,
                        playing = ep.name == currentEpName,
                        onClick = { source?.let { onSelectEpisode(it, ep) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 补齐末行空位，保持格子等宽
                if (row.size < EP_COLUMNS) {
                    repeat(EP_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun SourceChip(
    name: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    focused -> NetflixRed
                    selected -> NetflixRed.copy(alpha = 0.35f)
                    else -> Color(0xFF2A2A2A)
                }
            )
            .border(
                2.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EpisodeChip(
    name: String,
    focused: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> NetflixRed
                    playing -> Color(0xFF3A1414)
                    else -> Color(0xFF2A2A2A)
                }
            )
            .border(
                2.dp,
                when {
                    focused -> Color.White
                    playing -> NetflixRed
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (playing) "▶ $name" else name,
            color = TextWhite,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun BoxScope.StatusHint(text: String) {
    Text(
        text,
        color = TextGray,
        fontSize = 14.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp)
    )
}

/**
 * HTML 分享页（liangzi 源）是网页，ExoPlayer 读不了。
 * 抓取页面并从中提取真实 m3u8 地址（页面里 "var main = '...index.m3u8?sign=...'"）。
 */
private suspend fun resolveHtmlPlayUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        )
        val html = conn.inputStream.bufferedReader().use { it.readText() }
        VodParser.m3u8FromHtml(html, pageUrl)
    }.getOrNull()
}

object PlayerHolder {
    private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer {
        instance?.let { return it }
        // 全局 HTTP 请求头：浏览器 UA + maccms 站 Referer，绕过 CDN 防盗链
        val factory = DefaultHttpDataSource.Factory()
        factory.setDefaultRequestProperties(
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                "Referer" to "https://maccms.sanxininfo.cn/"
            )
        )
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
        instance = p
        return p
    }
}

private val CONTROLS_VISIBILITY_TIMEOUT = 3000.milliseconds
