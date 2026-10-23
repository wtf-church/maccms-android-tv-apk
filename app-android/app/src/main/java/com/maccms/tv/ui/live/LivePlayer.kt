package com.maccms.tv.ui.live

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.maccms.tv.data.LIVE_USER_AGENT
import com.maccms.tv.data.LiveChannel

/**
 * 直播专用播放器：不复用 VOD 的 PlayerHolder。
 * PlayerHolder 固定发送 maccms Referer，且其生命周期与 VOD 页耦合。
 */
@UnstableApi
internal fun createLivePlayer(context: Context): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(LIVE_USER_AGENT)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(httpFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .build()
}

/** 切换频道；频道自带 UA/Referer 时构造专属 MediaSource。mediaId 用于忽略旧媒体源的错误回调。 */
@UnstableApi
internal fun playChannel(player: ExoPlayer, channel: LiveChannel, url: String, mediaId: String) {
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .setMediaId(mediaId)
        .build()
    if (!channel.userAgent.isNullOrBlank() || !channel.referer.isNullOrBlank()) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(channel.userAgent?.takeIf { it.isNotBlank() } ?: LIVE_USER_AGENT)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        channel.referer?.takeIf { it.isNotBlank() }?.let { referer ->
            httpFactory.setDefaultRequestProperties(mapOf("Referer" to referer))
        }
        val source = DefaultMediaSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
            .createMediaSource(mediaItem)
        player.setMediaSource(source)
    } else {
        player.setMediaItem(mediaItem)
    }
    player.prepare()
    player.playWhenReady = true
}
