package com.maccms.tv.util

import android.content.Context
import com.maccms.tv.data.Vod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 一条观看记录：影片快照 + 看到哪一集/什么位置 */
@Serializable
data class WatchHistoryEntry(
    val vodId: Int,
    val name: String = "",
    val pic: String = "",
    val year: String = "",
    val remarks: String = "",
    val episode: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** 观看进度 0..1；未知时长返回 0 */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun toVod(): Vod = Vod(
        vod_id = vodId,
        vod_name = name,
        vod_pic = pic,
        vod_year = year,
        vod_remarks = remarks
    )
}

/**
 * 观看历史：按影片去重、最近观看排最前，最多保留 [MAX] 条。
 * 播放页在保存进度时顺带写入，因此历史页无需再请求详情。
 */
object WatchHistoryStore {
    private const val PREF_NAME = "maccms_tv_watch_history"
    private const val KEY = "entries"
    private const val MAX = 100

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serializer = ListSerializer(WatchHistoryEntry.serializer())

    fun list(context: Context): List<WatchHistoryEntry> = runCatching {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        json.decodeFromString(serializer, raw).sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun record(context: Context, vod: Vod, episode: String, positionMs: Long, durationMs: Long) {
        if (vod.vod_id <= 0) return
        val current = list(context)
        val old = current.firstOrNull { it.vodId == vod.vod_id }
        val entry = WatchHistoryEntry(
            vodId = vod.vod_id,
            name = vod.vod_name.ifBlank { old?.name ?: "" },
            pic = vod.vod_pic.ifBlank { old?.pic ?: "" },
            year = vod.vod_year.ifBlank { old?.year ?: "" },
            remarks = vod.vod_remarks.ifBlank { old?.remarks ?: "" },
            episode = episode,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
        )
        val next = (listOf(entry) + current.filter { it.vodId != vod.vod_id }).take(MAX)
        write(context, next)
    }

    fun remove(context: Context, vodId: Int) {
        write(context, list(context).filterNot { it.vodId == vodId })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun write(context: Context, entries: List<WatchHistoryEntry>) {
        prefs(context).edit().putString(KEY, json.encodeToString(serializer, entries)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
