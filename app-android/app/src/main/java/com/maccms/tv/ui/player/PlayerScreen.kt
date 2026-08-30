package com.maccms.tv.ui.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

/** 播放页：官方 Media3 Player composable（内建 Android TV D-pad 焦点导航与进度拖动） */
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
    val playUrl = playUrls.getOrNull(playIndex)
    var title by remember { mutableStateOf(vod.vod_name + if (episode != null) " · ${episode.name}" else "") }

    // 线路/选集面板
    var sources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }
    var showMenu by remember { mutableStateOf(false) }

    // 控件显隐：默认显示；方向键/菜单键唤出
    var showControls by remember { mutableStateOf(true) }
    var anyPointerDown by remember { mutableStateOf(false) }
    val hideJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun scheduleHideControls() {
        hideJob.value?.cancel()
        if (!anyPointerDown) {
            hideJob.value = scope.launch {
                delay(CONTROLS_VISIBILITY_TIMEOUT)
                showControls = false
            }
        }
    }
    LaunchedEffect(showControls, anyPointerDown, showMenu) {
        if (showControls && !anyPointerDown && !showMenu) scheduleHideControls()
        else hideJob.value?.cancel()
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    // 解析候选播放地址（保留原逻辑：m3u8 优先、HTML 分享页展开）
    LaunchedEffect(vod.vod_id, episode) {
        var base = vod
        if (base.vod_play_url.isBlank()) {
            repository.getDetail(vod.vod_id).onSuccess { detail ->
                base = detail
                if (episode == null) title = detail.vod_name
            }
        }
        val urls = if (episode != null) {
            VodParser.urlsByName(base.vod_play_from, base.vod_play_url, episode.name)
                .ifEmpty { listOfNotNull(episode.url.takeIf { it.isNotBlank() }) }
        } else {
            VodParser.allUrls(base.vod_play_from, base.vod_play_url)
        }
        val expanded = mutableListOf<String>()
        for (u in urls) {
            if (VodParser.isVideoUrl(u)) expanded += u
            else {
                val real = withContext(Dispatchers.IO) { resolveHtmlPlayUrl(u) }
                if (real != null) expanded += real else expanded += u
            }
        }
        playUrls = expanded
        sources = VodParser.parsePlaySources(base.vod_play_from, base.vod_play_url)
        playIndex = 0
        playFailed = false
        isResolved = true
    }

    // 上一个源播放失败时自动切换下一个候选源
    LaunchedEffect(playIndex, playUrls.size) {
        if (playIndex >= playUrls.size) return@LaunchedEffect
        val url = playUrls[playIndex]
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                if (playIndex < playUrls.size - 1) playIndex += 1
                else playFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
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
                    when (keyEvent.key) {
                        // 菜单键：弹线路/选集面板
                        Key.Menu -> {
                            showMenu = !showMenu
                            showControls = !showMenu
                            true
                        }
                        // 返回键：面板打开时先关面板
                        Key.Back -> {
                            if (showMenu) { showMenu = false; showControls = true; true } else false
                        }
                        // 方向键/确认键：唤出控件并重新计时
                        Key.DirectionCenter, Key.Enter, Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionUp, Key.DirectionDown -> {
                            showControls = true
                            scheduleHideControls()
                            false
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
                            onClick = { showMenu = true },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) { Text("菜单", color = TextWhite) }
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
            playUrl == null -> StatusHint("没有可用的播放地址")
            playFailed -> StatusHint("播放失败，请尝试其它集数或源")
            !isPlaying && !showControls -> StatusHint("正在加载…")
        }

        // 菜单面板：显示线路与分集（遥控器方向键选择，OK 键切换）
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            MenuPanel(
                sources = sources,
                episode = episode,
                onSelectSource = { url ->
                    playUrls = listOf(url)
                    playIndex = 0
                    showMenu = false
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MenuPanel(
    sources: List<PlaySource>,
    episode: Episode?,
    onSelectSource: (String) -> Unit
) {
    Column(
        Modifier
            .width(320.dp)
            .padding(start = 24.dp)
            .background(Color(0xCC1A1A1A), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("线路与分集", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (sources.isEmpty()) {
            Text("暂无其它线路", color = TextGray, fontSize = 13.sp)
        } else {
            sources.forEach { s ->
                val label = buildString {
                    append("${s.from} (${s.episodes.size}集)")
                    if (episode != null) {
                        val ep = s.episodes.firstOrNull { it.name == episode.name }
                        if (ep != null) append(" · ${ep.name}")
                    }
                }
                Button(
                    onClick = { onSelectSource(s.episodes.first().url) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
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
