/*
 * M3U 解析逻辑参考/改写自 lemonTV:
 * https://github.com/jia070310/lemontv
 * Copyright (c) 2020-PRESENT yaoxieyoulei
 * Licensed under the MIT License. See THIRD_PARTY_NOTICES.md (repository root).
 */
package com.maccms.tv.data

import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

/**
 * 解析 IPTV 频道列表（M3U）。
 *
 * 支持：
 * - 裸格式 `#EXTINF:-1 ,CCTV1`
 * - 带属性 `#EXTINF:-1 tvg-name="CCTV1" group-title="央视",CCTV1`
 * - `#EXTVLCOPT:http-user-agent=...` / `#EXTVLCOPT:http-referer=...`
 * - `#EXTGRP:分组`
 * - CRLF / BOM
 *
 * 同名频道合并为一个频道，多个 URL 作为备用线路。
 */
object M3uPlaylistParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)\s*=\s*["']([^"']*)["']""")
    private val UA_REGEX = Regex("""http-user-agent\s*=\s*(.+)""", RegexOption.IGNORE_CASE)
    private val REFERER_REGEX = Regex("""http-referer\s*=\s*(.+)""", RegexOption.IGNORE_CASE)

    private val GROUP_ORDER = listOf("央视", "卫视", "地方", "其他")

    private val PROVINCE_PREFIXES = listOf(
        "北京", "天津", "上海", "重庆",
        "河北", "山西", "辽宁", "吉林", "黑龙江",
        "江苏", "浙江", "安徽", "福建", "江西", "山东",
        "河南", "湖北", "湖南", "广东", "广西", "海南",
        "四川", "贵州", "云南", "西藏", "陕西", "甘肃",
        "青海", "宁夏", "新疆", "内蒙古", "香港", "澳门", "台湾"
    )

    private val LOCAL_HINTS = listOf(
        "新闻综合", "公共", "影视", "都市", "生活", "经济",
        "科教", "文旅", "少儿", "法治", "综合"
    )

    private class PendingChannel(
        val name: String,
        var group: String?,
        val logo: String?,
        var userAgent: String?,
        var referer: String?
    )

    private class MutableChannel(
        val name: String,
        val group: String,
        val logo: String?,
        val userAgent: String?,
        val referer: String?,
        val urls: LinkedHashSet<String> = LinkedHashSet()
    )

    fun parse(raw: String): LivePlaylist {
        val text = raw.removePrefix("﻿")
        val channels = LinkedHashMap<String, MutableChannel>()
        var defaultUserAgent: String? = null
        var defaultReferer: String? = null
        var pending: PendingChannel? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    val comma = line.lastIndexOf(',')
                    val rawName = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    val displayName = rawName
                        .ifBlank { attrs["tvg-name"]?.trim().orEmpty() }
                        .ifBlank { "未知频道" }
                    pending = PendingChannel(
                        name = displayName,
                        group = attrs["group-title"]?.trim()?.takeIf { it.isNotEmpty() }
                            ?.let { normalizeGroupName(it) },
                        logo = attrs["tvg-logo"]?.trim()?.takeIf { it.isNotEmpty() },
                        userAgent = attrs["http-user-agent"]?.trim()?.takeIf { it.isNotEmpty() }
                            ?: defaultUserAgent,
                        referer = attrs["http-referer"]?.trim()?.takeIf { it.isNotEmpty() }
                            ?: defaultReferer
                    )
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val ua = UA_REGEX.find(line)?.groupValues?.get(1)
                        ?.trim()?.trim('"', '\'')?.trim()?.takeIf { it.isNotEmpty() }
                    if (ua != null) {
                        defaultUserAgent = ua
                        pending?.userAgent = ua
                    }
                    val referer = REFERER_REGEX.find(line)?.groupValues?.get(1)
                        ?.trim()?.trim('"', '\'')?.trim()?.takeIf { it.isNotEmpty() }
                    if (referer != null) {
                        defaultReferer = referer
                        pending?.referer = referer
                    }
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val group = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                    if (group != null) pending?.group = normalizeGroupName(group)
                }

                line.startsWith("#") -> Unit

                else -> {
                    val current = pending
                    if (current != null) {
                        if (isPlayableUrl(line)) {
                            val group = current.group ?: inferGroup(current.name)
                            val channel = channels.getOrPut(current.name) {
                                MutableChannel(
                                    name = current.name,
                                    group = group,
                                    logo = current.logo,
                                    userAgent = current.userAgent,
                                    referer = current.referer
                                )
                            }
                            channel.urls.add(line)
                        }
                        pending = null
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            throw IllegalArgumentException("直播源格式错误：未找到有效频道")
        }

        val byGroup = LinkedHashMap<String, MutableList<LiveChannel>>()
        channels.values.forEach { channel ->
            byGroup.getOrPut(channel.group) { mutableListOf() }
                .add(
                    LiveChannel(
                        name = channel.name,
                        group = channel.group,
                        urls = channel.urls.toList(),
                        logo = channel.logo,
                        userAgent = channel.userAgent,
                        referer = channel.referer
                    )
                )
        }

        val orderedGroupNames = GROUP_ORDER.filter { byGroup.containsKey(it) } +
            byGroup.keys.filter { it !in GROUP_ORDER }
        val groups = orderedGroupNames.map { name ->
            LiveGroup(name = name, channels = byGroup.getValue(name))
        }
        return LivePlaylist(groups)
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        ATTR_REGEX.findAll(line).forEach { match ->
            attributes[match.groupValues[1].lowercase(Locale.ROOT)] = match.groupValues[2]
        }
        return attributes
    }

    private fun isPlayableUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            url.none { it.isWhitespace() }
    }

    private fun inferGroup(name: String): String {
        val n = name.trim()
        val upper = n.uppercase(Locale.ROOT)
        return when {
            upper.startsWith("CCTV") || upper.startsWith("CGTN") || n.contains("央视") -> "央视"
            n.contains("卫视") -> "卫视"
            PROVINCE_PREFIXES.any { n.startsWith(it) } || LOCAL_HINTS.any { n.contains(it) } -> "地方"
            else -> "其他"
        }
    }

    /** 把不同源的常见分组名归一化，避免出现「央视频道/央视台/央视」等多个同类分组。 */
    private fun normalizeGroupName(raw: String): String {
        val name = raw.trim()
        return when {
            name.equals("央视频道", ignoreCase = true) ||
                name.equals("央视台", ignoreCase = true) ||
                name == "央视" -> "央视"
            name.equals("卫视频道", ignoreCase = true) || name == "卫视" -> "卫视"
            name.equals("地方频道", ignoreCase = true) || name == "地方" -> "地方"
            name.equals("其他频道", ignoreCase = true) ||
                name.equals("General", ignoreCase = true) ||
                name == "其它" || name == "其他" -> "其他"
            else -> name
        }
    }
}
