package com.maccms.tv.util

import android.content.Context

/**
 * 播放进度：按 (影片 id, 分集名) 保存毫秒位置，同时记住每部影片上次观看的分集，
 * 供播放页续播使用（分集名与 VodParser.urlsByName 的匹配口径一致）。
 */
object PlaybackStore {
    private const val PREF_NAME = "maccms_tv_playback"
    private const val MIN_SAVE_MS = 5_000L        // 小于 5 秒视为没看，不保存
    private const val END_TOLERANCE_MS = 10_000L  // 距结尾 10 秒内视为看完，下次从头播

    /** 上次观看的分集名；没有记录返回 null */
    fun lastEpisode(context: Context, vodId: Int): String? =
        prefs(context).getString(lastKey(vodId), null)?.takeIf { it.isNotBlank() }

    /** 该集已保存的位置；没有记录返回 0 */
    fun position(context: Context, vodId: Int, episodeName: String): Long =
        prefs(context).getLong(posKey(vodId, episodeName), 0L)

    /**
     * 保存进度并记录上次分集。
     * positionMs < 5s 或距结尾 <= 10s 时删除该集位置（视为没看/已看完）；
     * durationMs <= 0（ExoPlayer TIME_UNSET）时跳过结尾判断。
     */
    fun save(context: Context, vodId: Int, episodeName: String, positionMs: Long, durationMs: Long) {
        val ep = episodeName.trim()
        if (ep.isEmpty()) return
        val editor = prefs(context).edit().putString(lastKey(vodId), ep)
        val finished = positionMs < MIN_SAVE_MS ||
            (durationMs > 0 && durationMs - positionMs <= END_TOLERANCE_MS)
        if (finished) editor.remove(posKey(vodId, ep))
        else editor.putLong(posKey(vodId, ep), positionMs)
        editor.apply()
    }

    private fun lastKey(vodId: Int) = "last_$vodId"

    private fun posKey(vodId: Int, episodeName: String) = "pos_${vodId}_$episodeName"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
