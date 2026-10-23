package com.maccms.tv.util

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 直播配置与缓存元数据。
 * 默认直播源地址配置在 assets/live_config.properties（playlistUrl），
 * 用户可在设置页覆盖。
 */
object LiveSettings {
    private const val PREF_NAME = "maccms_tv_live"
    private const val CONFIG_ASSET = "live_config.properties"
    private const val CONFIG_KEY_PLAYLIST_URL = "playlistUrl"
    private const val KEY_PLAYLIST_URL = "playlist_url"
    private const val KEY_LAST_GROUP = "last_group"
    private const val KEY_LAST_CHANNEL = "last_channel"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val KEY_ETAG = "etag"
    private const val KEY_CACHED_URL = "cached_url"
    private const val KEY_CHANNEL_COUNT = "channel_count"

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedDefaultUrl: String? = null

    data class LastChannel(val group: String, val name: String)

    /** 规范化 M3U 地址：去空格、补 https、GitHub blob 转 raw、仅允许 http/https；无效返回 null。 */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val schemeFixed = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val rewritten = rewriteGithubBlobUrl(schemeFixed)
        val url = rewritten.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        return url.toString()
    }

    /**
     * 把 GitHub 网页地址 `.../blob/branch/path` 转成 raw 地址；
     * 兼容 `https://gh-proxy.org/https://github.com/.../blob/...` 这类代理前缀。
     */
    private fun rewriteGithubBlobUrl(url: String): String {
        val marker = "https://github.com/"
        val markerIndex = url.indexOf(marker, ignoreCase = true)
        if (markerIndex < 0) return url
        val prefix = url.substring(0, markerIndex)
        val rest = url.substring(markerIndex + marker.length)
        val parts = rest.split('/', limit = 5)
        if (parts.size == 5 && parts[2].equals("blob", ignoreCase = true)) {
            return prefix +
                "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts[3]}/${parts[4]}"
        }
        return url
    }

    fun getPlaylistUrl(context: Context): String {
        cachedUrl?.let { return it }
        val stored = normalize(prefs(context).getString(KEY_PLAYLIST_URL, null))
        val url = stored ?: getDefaultPlaylistUrl(context).orEmpty()
        cachedUrl = url
        return url
    }

    /** 读取 assets/live_config.properties 中配置的内置默认源；缺失/无效返回 null。 */
    fun getDefaultPlaylistUrl(context: Context): String? {
        cachedDefaultUrl?.let { return it }
        val text = runCatching {
            context.assets.open(CONFIG_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return null
        val url = parseConfigUrl(text) ?: return null
        cachedDefaultUrl = url
        return url
    }

    /** 解析配置文本（便于单测）；无 playlistUrl 或地址无效返回 null。 */
    internal fun parseConfigUrl(text: String): String? {
        val properties = java.util.Properties()
        runCatching { properties.load(text.reader()) }.getOrElse { return null }
        return normalize(properties.getProperty(CONFIG_KEY_PLAYLIST_URL))
    }

    /** 保存直播源地址；地址无效时返回 false 且不修改已有配置。 */
    fun setPlaylistUrl(context: Context, raw: String?): Boolean {
        val normalized = normalize(raw) ?: return false
        prefs(context).edit().putString(KEY_PLAYLIST_URL, normalized).apply()
        cachedUrl = normalized
        return true
    }

    fun getLastChannel(context: Context): LastChannel? {
        val sp = prefs(context)
        val name = sp.getString(KEY_LAST_CHANNEL, null)?.takeIf { it.isNotBlank() } ?: return null
        return LastChannel(
            group = sp.getString(KEY_LAST_GROUP, null).orEmpty(),
            name = name
        )
    }

    fun setLastChannel(context: Context, group: String, name: String) {
        prefs(context).edit()
            .putString(KEY_LAST_GROUP, group)
            .putString(KEY_LAST_CHANNEL, name)
            .apply()
    }

    fun getFetchedAt(context: Context): Long = prefs(context).getLong(KEY_FETCHED_AT, 0L)

    fun getEtag(context: Context): String? = prefs(context).getString(KEY_ETAG, null)

    fun getCachedUrl(context: Context): String? = prefs(context).getString(KEY_CACHED_URL, null)

    fun getChannelCount(context: Context): Int = prefs(context).getInt(KEY_CHANNEL_COUNT, 0)

    fun setCacheMeta(
        context: Context,
        sourceUrl: String,
        fetchedAt: Long,
        etag: String?,
        channelCount: Int
    ) {
        prefs(context).edit()
            .putString(KEY_CACHED_URL, sourceUrl)
            .putLong(KEY_FETCHED_AT, fetchedAt)
            .putString(KEY_ETAG, etag)
            .putInt(KEY_CHANNEL_COUNT, channelCount)
            .apply()
    }

    fun updateFetchedAt(context: Context, fetchedAt: Long) {
        prefs(context).edit().putLong(KEY_FETCHED_AT, fetchedAt).apply()
    }

    fun clearCacheMeta(context: Context) {
        prefs(context).edit()
            .remove(KEY_FETCHED_AT)
            .remove(KEY_ETAG)
            .remove(KEY_CACHED_URL)
            .remove(KEY_CHANNEL_COUNT)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
