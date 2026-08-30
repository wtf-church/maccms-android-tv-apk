package com.maccms.tv.util

import android.content.Context
import android.content.SharedPreferences

/**
 * API 地址管理：默认指向模拟器内 socat 转发的 maccms（10.0.2.2:8080），
 * 可在设置页改为其它地址（如真机连接宿主机局域网 IP）。
 */
object BaseUrlManager {
    private const val PREF_NAME = "maccms_tv_settings"
    private const val KEY_BASE_URL = "base_url"
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

    private var cachedUrl: String? = null

    fun getBaseUrl(context: Context): String {
        cachedUrl?.let { return it }
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val url = sp.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        cachedUrl = url
        return url
    }

    fun setBaseUrl(context: Context, url: String) {
        val normalized = url.trim().trimEnd('/')
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_BASE_URL, normalized).apply()
        cachedUrl = normalized
    }
}
