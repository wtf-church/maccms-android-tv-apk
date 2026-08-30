package com.maccms.tv.util

import android.content.Context
import android.content.SharedPreferences

/**
 * API 地址管理：默认指向线上站点 https://maccms.sanxininfo.cn，
 * 可在设置页改为其它地址。
 */
object BaseUrlManager {
    private const val PREF_NAME = "maccms_tv_settings"
    private const val KEY_BASE_URL = "base_url"
    const val DEFAULT_BASE_URL = "https://maccms.sanxininfo.cn"

    /** 旧版默认地址（模拟器专用），升级安装后残留在此，自动迁移掉 */
    private const val LEGACY_BASE_URL = "http://10.0.2.2:8080"

    private var cachedUrl: String? = null

    fun getBaseUrl(context: Context): String {
        cachedUrl?.let { return if (it == LEGACY_BASE_URL) DEFAULT_BASE_URL else it }
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val url = sp.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        val normalized = if (url == LEGACY_BASE_URL) DEFAULT_BASE_URL else url
        cachedUrl = normalized
        return normalized
    }

    fun setBaseUrl(context: Context, url: String) {
        val normalized = url.trim().trimEnd('/')
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_BASE_URL, normalized).apply()
        cachedUrl = normalized
    }
}
