package com.maccms.tv.data

import kotlinx.serialization.Serializable

/** maccms API 顶层响应：{"code":1,"msg":"","page":1,"pagecount":1,"limit":"20","total":36,"list":[...]} */
@Serializable
data class ApiResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 1,
    val pagecount: Int = 1,
    val limit: String = "",
    val total: Int = 0,
    val list: List<Vod> = emptyList()
)

/** 影片条目（仅选取需要的字段；maccms 会返回更多字段） */
@Serializable
data class Vod(
    val vod_id: Int = 0,
    val type_id: Int = 0,
    val type_name: String? = null,
    val vod_name: String = "",
    val vod_en: String = "",
    val vod_remarks: String = "",
    val vod_actor: String = "",
    val vod_director: String = "",
    val vod_area: String = "",
    val vod_lang: String = "",
    val vod_year: String = "",
    val vod_score: String = "",
    val vod_pic: String = "",
    val vod_pic_slide: String = "",
    val vod_content: String = "",
    val vod_time: String = "",
    val vod_play_from: String = "",
    val vod_play_url: String = ""
)

/** 解析后的分集：集名 + 地址 */
data class Episode(val name: String, val url: String)

/** 播放源：源名（如 demo）+ 该源的所有分集 */
data class PlaySource(val from: String, val episodes: List<Episode>) {
    val firstEpisode: Episode? get() = episodes.firstOrNull()
}

object VodParser {

    /**
     * 从多源中挑最稳的播放地址：优先 m3u8（HLS 防盗链少），其次 MP4。
     * 找不到则回退第一个可用源。
     */
    fun pickBestUrl(playFrom: String, playUrl: String): String? {
        val sources = parsePlaySources(playFrom, playUrl)
        if (sources.isEmpty()) return null
        // 遍历所有源的所有分集，优先 m3u8
        for (source in sources) {
            for (ep in source.episodes) {
                if (ep.url.contains(".m3u8")) return ep.url
            }
        }
        return sources.first().firstEpisode?.url
    }

    /**
     * 解析 vod_play_url。
     * 格式：源组之间用 $$$ 分隔；每组内分集用 # 分隔；每集为 "集名$URL"。
     * vod_play_from 用逗号分隔的源名与之对应。
     */
    fun parsePlaySources(vodPlayFrom: String, vodPlayUrl: String): List<PlaySource> {
        if (vodPlayUrl.isBlank()) return emptyList()
        val fromNames = vodPlayFrom.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val groups = vodPlayUrl.split("$$$")

        return groups.mapIndexedNotNull { index, group ->
            val episodes = group.split("#")
                .mapNotNull { seg -> parseEpisode(seg) }
            if (episodes.isEmpty()) null
            else PlaySource(
                from = fromNames.getOrNull(index) ?: "source${index + 1}",
                episodes = episodes
            )
        }
    }

    private fun parseEpisode(seg: String): Episode? {
        val s = seg.trim()
        if (s.isEmpty()) return null
        // maccms 标准格式 "集名$URL"；老 demo 数据用 "正片@http://..."
        val dollar = s.lastIndexOf('$')
        val at = s.lastIndexOf('@')
        val sep = if (dollar > 0 && dollar < s.length - 1) dollar
        else if (at > 0 && at < s.length - 1) at
        else -1
        if (sep > 0) {
            return Episode(s.substring(0, sep).trim(), s.substring(sep + 1).trim())
        }
        // 无集名，整段视为 URL
        return if (s.startsWith("http")) Episode(s, s) else null
    }
}
