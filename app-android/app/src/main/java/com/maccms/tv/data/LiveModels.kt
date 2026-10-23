package com.maccms.tv.data

/** 下载直播源与播放 IPTV 流时使用的浏览器 UA（部分源会拒绝默认 ExoPlayer UA）。 */
const val LIVE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

/** 直播源磁盘缓存元数据。 */
data class LiveCacheMeta(
    val fetchedAt: Long,
    val channelCount: Int,
    val sourceUrl: String
)

/** 直播频道：同名频道的多条 URL 视为备用线路。 */
data class LiveChannel(
    val name: String,
    val group: String,
    val urls: List<String>,
    val logo: String? = null,
    val userAgent: String? = null,
    val referer: String? = null
) {
    /** 任一线路为 IPv6 字面地址（如 http://[2409:...]）。 */
    val isIpv6: Boolean
        get() = urls.any { it.startsWith("http://[") || it.startsWith("https://[") }
}

data class LiveGroup(
    val name: String,
    val channels: List<LiveChannel>
)

data class LivePlaylist(
    val groups: List<LiveGroup>
) {
    val allChannels: List<LiveChannel> = groups.flatMap { it.channels }
    val channelCount: Int = allChannels.size
}
