package com.maccms.tv.util

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 搜索关键词历史：本地保存最近 10 条，最新在前。
 * 搜索页进入时展示，按 OK 直接重新搜索。
 */
object SearchHistoryStore {
    private const val PREF_NAME = "maccms_tv_history"
    private const val KEY_KEYWORDS = "keywords"
    private const val MAX = 10

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serializer = ListSerializer(String.serializer())

    /** 最近在前；解析失败返回空列表 */
    fun get(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_KEYWORDS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** 记录一次搜索：去重后移到最前，最多保留 MAX 条；返回更新后的历史 */
    fun add(context: Context, keyword: String): List<String> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return get(context)
        val updated = (listOf(kw) + get(context).filter { it != kw }).take(MAX)
        prefs(context).edit().putString(KEY_KEYWORDS, json.encodeToString(serializer, updated)).apply()
        return updated
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_KEYWORDS).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
