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
    val vod_hits: Int = 0,
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

    private val videoExtRegex = Regex("(?i)\\.(m3u8?|mp4|flv|mkv|avi|mov|webm|rmvb|wmv|ts)([?#]|$)")

    /** 是否为可直接交给播放器的媒体 URL（m3u8/mp4 等）；false 表示 HTML 分享页等 */
    fun isVideoUrl(url: String): Boolean = videoExtRegex.containsMatchIn(url)

    /**
     * 所有候选播放地址：m3u8 排前（防盗链少），其它排后；去重。
     * 播放器依次尝试，失败自动回退下一个。
     */
    fun allUrls(playFrom: String, playUrl: String): List<String> {
        val sources = parsePlaySources(playFrom, playUrl)
        if (sources.isEmpty()) return emptyList()
        val m3u8 = mutableListOf<String>()
        val other = mutableListOf<String>()
        for (s in sources) {
            for (ep in s.episodes) {
                if (ep.url.contains(".m3u8") || ep.url.contains(".m3u")) m3u8 += ep.url
                else other += ep.url
            }
        }
        return (m3u8 + other).distinct()
    }

    /** 按分集名收集所有源中的同名分集地址（m3u8 优先），点播分集时用 */
    fun urlsByName(playFrom: String, playUrl: String, name: String): List<String> {
        val sources = parsePlaySources(playFrom, playUrl)
        val m3u8 = mutableListOf<String>()
        val other = mutableListOf<String>()
        for (s in sources) {
            for (ep in s.episodes) {
                if (ep.name == name) {
                    if (ep.url.contains(".m3u8") || ep.url.contains(".m3u")) m3u8 += ep.url
                    else other += ep.url
                }
            }
        }
        return (m3u8 + other).distinct()
    }

    /**
     * 从 HTML 分享页提取真实 m3u8 地址。
     * 支持多种分享页格式：
     * - liangzi 源：v.lfthirtytwo.com/share/xxx，页面里 "var main = '.../index.m3u8?sign=...'"
     * - 非凡源：vip.ffzy-plays.com/share/xxx，页面里 "const url = '/20260705/xxx/index.m3u8?sign=...'"
     * 优先匹配声明的 m3u8 变量（var main / const url），再兜底任意 .m3u8 字符串。
     * 相对路径（/开头或纯路径）会基于 pageUrl 的主机拼成完整 URL。
     */
    fun m3u8FromHtml(html: String, pageUrl: String): String? {
        val raw = runCatching {
            val declared = Regex("(?is)(?:var\\s+main|const\\s+url)\\s*=\\s*[\"']([^\"']+)[\"']").find(html)
                ?.groupValues?.get(1)?.trim()
            if (declared != null) {
                declared
            } else {
                Regex("(?is)[\"']([^\"']+\\.m3u8[^\"']*)[\"']").find(html)
                    ?.groupValues?.get(1)?.trim()
            }
        }.getOrNull() ?: return null

        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val host = runCatching {
            val u = java.net.URI(pageUrl)
            "${u.scheme}://${u.host}"
        }.getOrNull() ?: return null
        return if (raw.startsWith("/")) host + raw else host + "/" + raw
    }

    /** 兼容旧调用：挑最好的一条（m3u8 优先） */
    fun pickBestUrl(playFrom: String, playUrl: String): String? =
        allUrls(playFrom, playUrl).firstOrNull()

    /**
     * 解析 vod_play_url。
     * 格式：源组之间用 $$$ 分隔；每组内分集用 # 分隔；每集为 "集名$URL"。
     * vod_play_from 用逗号或 $$$ 分隔的源名与之对应（线上站点用 $$$）。
     */
    fun parsePlaySources(vodPlayFrom: String, vodPlayUrl: String): List<PlaySource> {
        if (vodPlayUrl.isBlank()) return emptyList()
        val fromNames = vodPlayFrom.split(",", "$$$").map { it.trim() }.filter { it.isNotBlank() }
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
