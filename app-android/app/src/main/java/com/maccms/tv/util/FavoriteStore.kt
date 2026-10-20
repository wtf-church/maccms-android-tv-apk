package com.maccms.tv.util

import android.content.Context
import com.maccms.tv.data.Vod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 收藏的影片快照（详情页展示所需的最小字段） */
@Serializable
data class FavoriteEntry(
    val vodId: Int,
    val name: String = "",
    val pic: String = "",
    val year: String = "",
    val remarks: String = "",
    val score: String = "",
    val addedAt: Long = 0L
) {
    fun toVod(): Vod = Vod(
        vod_id = vodId,
        vod_name = name,
        vod_pic = pic,
        vod_year = year,
        vod_remarks = remarks,
        vod_score = score
    )
}

/** 我的收藏：最近收藏排最前 */
object FavoriteStore {
    private const val PREF_NAME = "maccms_tv_favorites"
    private const val KEY = "entries"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serializer = ListSerializer(FavoriteEntry.serializer())

    fun list(context: Context): List<FavoriteEntry> = runCatching {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        json.decodeFromString(serializer, raw).sortedByDescending { it.addedAt }
    }.getOrDefault(emptyList())

    fun isFavorite(context: Context, vodId: Int): Boolean =
        list(context).any { it.vodId == vodId }

    /** 收藏/取消收藏，返回操作后的状态（true = 已收藏） */
    fun toggle(context: Context, vod: Vod): Boolean {
        val current = list(context)
        val exists = current.any { it.vodId == vod.vod_id }
        return if (exists) {
            write(context, current.filterNot { it.vodId == vod.vod_id })
            false
        } else {
            val entry = FavoriteEntry(
                vodId = vod.vod_id,
                name = vod.vod_name,
                pic = vod.vod_pic,
                year = vod.vod_year,
                remarks = vod.vod_remarks,
                score = vod.vod_score,
                addedAt = System.currentTimeMillis()
            )
            write(context, listOf(entry) + current)
            true
        }
    }

    fun remove(context: Context, vodId: Int) {
        write(context, list(context).filterNot { it.vodId == vodId })
    }

    private fun write(context: Context, entries: List<FavoriteEntry>) {
        prefs(context).edit().putString(KEY, json.encodeToString(serializer, entries)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
