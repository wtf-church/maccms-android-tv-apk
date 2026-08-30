package com.maccms.tv.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.maccms.tv.data.Episode
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodParser
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import kotlinx.coroutines.launch

/** 播放页：Media3 ExoPlayer 全屏播放（支持 HLS/m3u8 与本地 MP4） */
@Composable
fun PlayerScreen(
    vod: Vod,
    episode: Episode?,
    repository: VodRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.get(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf(vod.vod_name + if (episode != null) " · ${episode.name}" else "") }
    val scope = rememberCoroutineScope()

    // 解析播放地址：优先 m3u8 源；传入分集优先
    LaunchedEffect(vod.vod_id, episode) {
        val cachedUrl = episode?.url?.takeIf { it.isNotBlank() }
            ?: VodParser.pickBestUrl(vod.vod_play_from, vod.vod_play_url)
        if (cachedUrl != null) {
            playUrl = cachedUrl
        } else {
            repository.getDetail(vod.vod_id).onSuccess { detail ->
                val url = VodParser.pickBestUrl(detail.vod_play_from, detail.vod_play_url)
                if (url != null) {
                    playUrl = url
                    if (episode == null) title = detail.vod_name
                }
            }
        }
    }

    DisposableEffect(playUrl) {
        if (playUrl != null) {
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(playUrl!!))
            player.prepare()
            player.playWhenReady = true
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
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
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // 顶部信息条
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text("‹ 返回", color = TextWhite, fontSize = 14.sp)
            Spacer(Modifier.width(20.dp))
            Text(title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (playUrl == null) {
            Text(
                "正在获取播放地址…",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            )
        } else if (!isPlaying) {
            Text(
                "正在加载…",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            )
        }
    }
}

object PlayerHolder {
    private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer {
        instance?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        instance = p
        return p
    }
}
