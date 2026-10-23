package com.maccms.tv.ui.live

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.maccms.tv.data.LiveChannel
import com.maccms.tv.data.LivePlaylist
import com.maccms.tv.data.LiveRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.LiveSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 直播页：全屏播放 + OK 呼出频道列表。
 *
 * 交互：
 * - 列表收起：上下/CH± 直接切换频道；OK/菜单键打开列表；返回退出直播。
 * - 列表打开：上下移动、左右切分组、OK 切台并收起；菜单键刷新节目源；返回关闭。
 * - 频道多条线路播放失败时自动切换下一条。
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveScreen(
    repository: LiveRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { createLivePlayer(context) }
    val rootFocus = remember { FocusRequester() }

    var playlist by remember { mutableStateOf<LivePlaylist?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    var currentChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var currentUrlIndex by remember { mutableIntStateOf(0) }
    var loadingMedia by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var endedRetries by remember { mutableIntStateOf(0) }

    var expectedMediaId by remember { mutableStateOf<String?>(null) }
    var mediaGeneration by remember { mutableIntStateOf(0) }
    var sameUrlRetries by remember { mutableIntStateOf(0) }

    var panelOpen by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableIntStateOf(0) }
    var selectedChannel by remember { mutableIntStateOf(0) }
    var pendingPlaylist by remember { mutableStateOf<LivePlaylist?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    var notice by remember { mutableStateOf<String?>(null) }
    var bannerChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var bannerUrlIndex by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(false) }

    var bannerJob by remember { mutableStateOf<Job?>(null) }
    var noticeJob by remember { mutableStateOf<Job?>(null) }
    var zapJob by remember { mutableStateOf<Job?>(null) }

    fun showBanner(channel: LiveChannel, urlIndex: Int) {
        bannerChannel = channel
        bannerUrlIndex = urlIndex
        bannerVisible = true
        bannerJob?.cancel()
        bannerJob = scope.launch {
            delay(2500)
            bannerVisible = false
        }
    }

    fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(4000)
            notice = null
        }
    }

    fun playCurrentUrl(channel: LiveChannel, index: Int) {
        mediaGeneration += 1
        val mediaId = "live-$mediaGeneration-${channel.name}-$index"
        expectedMediaId = mediaId
        playChannel(player, channel, channel.urls[index], mediaId)
    }

    fun tune(channel: LiveChannel, urlIndex: Int = 0) {
        if (channel.urls.isEmpty()) {
            playbackError = "《${channel.name}》没有可用的播放地址"
            panelOpen = true
            return
        }
        val safeIndex = urlIndex.coerceIn(0, channel.urls.lastIndex)
        currentChannel = channel
        currentUrlIndex = safeIndex
        loadingMedia = true
        buffering = true
        playbackError = null
        endedRetries = 0
        sameUrlRetries = 0
        LiveSettings.setLastChannel(context, channel.group, channel.name)
        playCurrentUrl(channel, safeIndex)
        showBanner(channel, safeIndex)
    }

    fun clampSelection(source: LivePlaylist) {
        if (source.groups.isEmpty()) {
            selectedGroup = 0
            selectedChannel = 0
            return
        }
        selectedGroup = selectedGroup.coerceIn(0, source.groups.lastIndex)
        val channelCount = source.groups[selectedGroup].channels.size
        selectedChannel = selectedChannel.coerceIn(0, (channelCount - 1).coerceAtLeast(0))
    }

    fun applyPlaylist(updated: LivePlaylist, deferWhilePanelOpen: Boolean) {
        if (deferWhilePanelOpen && panelOpen) {
            pendingPlaylist = updated
            return
        }
        playlist = updated
        val current = currentChannel
        val sameChannel = current?.let { old ->
            updated.allChannels.firstOrNull { it.group == old.group && it.name == old.name }
        }
        if (sameChannel != null) {
            currentChannel = sameChannel
            if (currentUrlIndex > sameChannel.urls.lastIndex) currentUrlIndex = 0
        } else {
            updated.allChannels.firstOrNull()?.let { tune(it) }
        }
        clampSelection(updated)
    }

    fun openPanel() {
        val source = playlist ?: return
        val channel = currentChannel ?: source.allChannels.firstOrNull() ?: return
        val groupIndex = source.groups.indexOfFirst { it.name == channel.group }.coerceAtLeast(0)
        val channelIndex = source.groups.getOrNull(groupIndex)
            ?.channels
            ?.indexOfFirst { it.name == channel.name }
            ?.coerceAtLeast(0)
            ?: 0
        selectedGroup = groupIndex
        selectedChannel = channelIndex
        panelOpen = true
        bannerJob?.cancel()
        bannerVisible = false
    }

    fun closePanel() {
        panelOpen = false
        pendingPlaylist?.let { pending ->
            pendingPlaylist = null
            applyPlaylist(pending, deferWhilePanelOpen = false)
        }
        currentChannel?.let { showBanner(it, currentUrlIndex) }
    }

    fun selectHighlighted() {
        val group = playlist?.groups?.getOrNull(selectedGroup) ?: return
        val channel = group.channels.getOrNull(selectedChannel) ?: return
        val same = channel.group == currentChannel?.group && channel.name == currentChannel?.name
        if (!same) tune(channel)
        closePanel()
    }

    fun moveSelection(delta: Int) {
        val source = playlist ?: return
        if (source.groups.isEmpty()) return
        val group = source.groups[selectedGroup.coerceIn(0, source.groups.lastIndex)]
        if (group.channels.isEmpty()) return
        val target = selectedChannel + delta
        when {
            target in 0..group.channels.lastIndex -> selectedChannel = target
            delta > 0 -> {
                selectedGroup = (selectedGroup + 1) % source.groups.size
                selectedChannel = 0
            }
            else -> {
                selectedGroup = (selectedGroup - 1 + source.groups.size) % source.groups.size
                selectedChannel = source.groups[selectedGroup].channels.lastIndex.coerceAtLeast(0)
            }
        }
    }

    fun switchGroup(delta: Int) {
        val source = playlist ?: return
        if (source.groups.isEmpty()) return
        selectedGroup = (selectedGroup + delta + source.groups.size) % source.groups.size
        selectedChannel = 0
    }

    fun requestZap(delta: Int) {
        val source = playlist ?: return
        val all = source.allChannels
        if (all.isEmpty()) return
        val index = all.indexOfFirst {
            it.group == currentChannel?.group && it.name == currentChannel?.name
        }.let { if (it >= 0) it else 0 }
        val target = all[(index + delta + all.size) % all.size]
        showBanner(target, 0)
        zapJob?.cancel()
        zapJob = scope.launch {
            delay(200)
            tune(target)
        }
    }

    fun handlePlayerError() {
        val channel = currentChannel ?: return
        val nextIndex = currentUrlIndex + 1
        if (nextIndex < channel.urls.size) {
            sameUrlRetries = 0
            currentUrlIndex = nextIndex
            loadingMedia = true
            buffering = true
            playbackError = null
            showBanner(channel, nextIndex)
            playCurrentUrl(channel, nextIndex)
        } else if (sameUrlRetries < 1) {
            // 单线路/最后一条线路：也许只是直播片段瞬时失败，先原地重试一次
            sameUrlRetries += 1
            loadingMedia = true
            buffering = true
            playbackError = null
            showNotice("播放中断，正在重试…")
            val retryIndex = currentUrlIndex.coerceIn(0, channel.urls.lastIndex)
            scope.launch {
                delay(1500)
                if (currentChannel?.group == channel.group && currentChannel?.name == channel.name) {
                    playCurrentUrl(channel, retryIndex)
                }
            }
        } else {
            sameUrlRetries = 0
            loadingMedia = false
            buffering = false
            val hint = if (channel.isIpv6) {
                "该频道需要 IPv6 网络，请确认电视和路由器已开启 IPv6"
            } else {
                "请稍后重试或切换其他频道"
            }
            playbackError = "《${channel.name}》暂时无法播放（已尝试 ${channel.urls.size} 条线路）。$hint"
            openPanel()
        }
    }

    fun handleEnded() {
        val channel = currentChannel ?: return
        if (endedRetries < 1) {
            endedRetries += 1
            loadingMedia = true
            val index = currentUrlIndex.coerceIn(0, channel.urls.lastIndex)
            scope.launch {
                delay(1000)
                if (currentChannel?.group == channel.group && currentChannel?.name == channel.name) {
                    playCurrentUrl(channel, index)
                }
            }
        } else {
            endedRetries = 0
            handlePlayerError()
        }
    }

    fun requestRefresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            repository.refresh(force = true)
                .onSuccess { updated ->
                    applyPlaylist(updated, deferWhilePanelOpen = false)
                    showNotice("节目源已刷新：${updated.channelCount} 个频道")
                }
                .onFailure { error ->
                    showNotice(error.message ?: "刷新节目源失败，继续使用缓存")
                }
            refreshing = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    buffering = false
                    playbackError = null
                    loadingMedia = false
                }
                if (playbackState == Player.STATE_ENDED && !loadingMedia) {
                    handleEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    buffering = false
                    playbackError = null
                    loadingMedia = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val mediaId = player.currentMediaItem?.mediaId
                if (mediaId != null && mediaId != expectedMediaId) {
                    Log.d("LiveScreen", "忽略旧媒体源错误：$mediaId != $expectedMediaId")
                    return
                }
                handlePlayerError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(retryKey) {
        loading = true
        loadError = null
        runCatching { rootFocus.requestFocus() }
        repository.load()
            .onSuccess { loaded ->
                playlist = loaded
                loading = false
                val last = LiveSettings.getLastChannel(context)
                val target = loaded.allChannels.firstOrNull {
                    it.group == last?.group && it.name == last.name
                } ?: loaded.allChannels.firstOrNull { it.name == last?.name }
                    ?: loaded.allChannels.firstOrNull()
                if (target != null) tune(target)
                if (repository.needsRefresh()) {
                    scope.launch {
                        repository.refresh(force = false)
                            .onSuccess { updated ->
                                applyPlaylist(updated, deferWhilePanelOpen = true)
                            }
                            .onFailure {
                                showNotice("节目源更新失败，继续使用缓存")
                            }
                    }
                }
            }
            .onFailure { error ->
                loading = false
                loadError = error.message ?: "直播源加载失败"
            }
    }

    LaunchedEffect(Unit) {
        repeat(10) {
            delay(60)
            if (runCatching { rootFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (loadError != null) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            retryKey += 1
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        else -> true
                    }
                } else if (panelOpen) {
                    when (event.key) {
                        Key.Back -> {
                            closePanel()
                            true
                        }
                        Key.Menu -> {
                            requestRefresh()
                            true
                        }
                        Key.DirectionUp, Key.ChannelUp -> {
                            moveSelection(-1)
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            moveSelection(1)
                            true
                        }
                        Key.DirectionLeft -> {
                            switchGroup(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            switchGroup(1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            selectHighlighted()
                            true
                        }
                        else -> true
                    }
                } else {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.Menu -> {
                            openPanel()
                            true
                        }
                        Key.DirectionUp, Key.ChannelUp -> {
                            requestZap(-1)
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            requestZap(1)
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            openPanel()
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            contentScale = ContentScale.Fit
        )

        if (loading) {
            LoadingBox()
        } else if (loadError != null) {
            ErrorBox(loadError ?: "", onRetry = { retryKey += 1 })
        }

        if (buffering && !loading && loadError == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    color = NetflixRed,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("缓冲中…", color = TextWhite, fontSize = 12.sp)
            }
        }

        if (bannerVisible && bannerChannel != null) {
            ChannelBanner(
                channel = bannerChannel!!,
                urlIndex = bannerUrlIndex,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 26.dp)
            )
        }

        if (!notice.isNullOrBlank()) {
            Text(
                notice ?: "",
                color = TextWhite,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (playbackError != null && !panelOpen) {
            Text(
                playbackError ?: "",
                color = NetflixRed,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val source = playlist
            if (source != null) {
                LiveChannelOverlay(
                    playlist = source,
                    currentChannel = currentChannel,
                    selectedGroup = selectedGroup,
                    selectedChannel = selectedChannel,
                    refreshing = refreshing,
                    message = playbackError
                )
            }
        }
    }
}

@Composable
private fun ChannelBanner(
    channel: LiveChannel,
    urlIndex: Int,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LIVE",
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NetflixRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(channel.name, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${channel.group}${if (channel.isIpv6) " · IPv6" else ""} · 线路 ${(urlIndex + 1).coerceAtMost(channel.urls.size)}/${channel.urls.size} · OK 频道列表",
            color = TextGray,
            fontSize = 12.sp
        )
    }
}
