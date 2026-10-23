package com.maccms.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    @Test
    fun `merges duplicate channel entries into one channel with all urls`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 ,CCTV1
            http://a.example/1.m3u8
            #EXTINF:-1 ,CCTV2
            http://b.example/2.m3u8
            #EXTINF:-1 ,CCTV1
            http://a.example/2.m3u8
            """.trimIndent()
        )

        assertEquals(2, playlist.channelCount)
        val cctv1 = playlist.allChannels.single { it.name == "CCTV1" }
        assertEquals(listOf("http://a.example/1.m3u8", "http://a.example/2.m3u8"), cctv1.urls)
        assertEquals("央视", cctv1.group)
    }

    @Test
    fun `reads attributes and uses the text after the last comma as name`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,tvg-id="重温经典" tvg-name="重温经典" tvg-logo="http://logo/1.png" group-title="其他",重温经典
            http://a.example/1.m3u8
            """.trimIndent()
        )

        val channel = playlist.allChannels.single()
        assertEquals("重温经典", channel.name)
        assertEquals("其他", channel.group)
        assertEquals("http://logo/1.png", channel.logo)
    }

    @Test
    fun `handles bom and crlf line endings`() {
        val playlist = M3uPlaylistParser.parse(
            "﻿#EXTM3U\r\n#EXTINF:-1 ,湖南卫视\r\nhttp://a.example/1.m3u8\r\n"
        )

        val channel = playlist.allChannels.single()
        assertEquals("湖南卫视", channel.name)
        assertEquals("卫视", channel.group)
    }

    @Test
    fun `inherits ex tvlcopt headers and lets inline attributes override them`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (TV)
            #EXTVLCOPT:http-referer=http://ref.example/
            #EXTINF:-1 ,CCTV1
            http://a.example/1.m3u8
            #EXTINF:-1 http-user-agent="InlineUA" ,CCTV2
            http://a.example/2.m3u8
            """.trimIndent()
        )

        val channels = playlist.allChannels.associateBy { it.name }
        assertEquals("Mozilla/5.0 (TV)", channels.getValue("CCTV1").userAgent)
        assertEquals("http://ref.example/", channels.getValue("CCTV1").referer)
        assertEquals("InlineUA", channels.getValue("CCTV2").userAgent)
        assertEquals("http://ref.example/", channels.getValue("CCTV2").referer)
    }

    @Test
    fun `infers groups for cctv satellite province and other channels`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 ,CCTV1
            http://a.example/1
            #EXTINF:-1 ,湖南卫视
            http://a.example/2
            #EXTINF:-1 ,安徽宿州公共
            http://a.example/3
            #EXTINF:-1 ,CHC影迷电影
            http://a.example/4
            """.trimIndent()
        )

        assertEquals(listOf("央视", "卫视", "地方", "其他"), playlist.groups.map { it.name })
    }

    @Test
    fun `throws when no valid channel is found`() {
        assertThrows(IllegalArgumentException::class.java) {
            M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1 ,CCTV1\n")
        }
    }

    @Test
    fun `throws on empty content`() {
        assertThrows(IllegalArgumentException::class.java) {
            M3uPlaylistParser.parse("")
        }
    }

    @Test
    fun `deduplicates identical urls for the same channel`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 ,CCTV1
            http://a.example/1.m3u8
            #EXTINF:-1 ,CCTV1
            http://a.example/1.m3u8
            """.trimIndent()
        )

        assertEquals(1, playlist.allChannels.single().urls.size)
    }

    @Test
    fun `ignores non http urls`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 ,CCTV1
            rtmp://a.example/1
            #EXTINF:-1 ,CCTV2
            http://a.example/2.m3u8
            """.trimIndent()
        )

        assertEquals("CCTV2", playlist.allChannels.single().name)
    }

    @Test
    fun `extgrp overrides the inferred group`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 ,CCTV1
            #EXTGRP:自定义分组
            http://a.example/1.m3u8
            """.trimIndent()
        )

        assertEquals("自定义分组", playlist.allChannels.single().group)
    }

    @Test
    fun `parses ipv6 entries with group-title and marks the channel as ipv6`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="A01" tvg-name="CCTV1" group-title="央视",CCTV-1
            http://[2409:8087:1e03:21::2]:6060/cms001/ch00000090990000001022/index.m3u8
            #EXTINF:-1 tvg-id="D06" tvg-name="NHK World" group-title="国际",NHK World
            https://nhkwlive-ojp.akamaized.net/hls/live/2003459/nhkwlive-ojp-en/index_4M.m3u8
            """.trimIndent()
        )

        val cctv = playlist.allChannels.single { it.name == "CCTV-1" }
        val nhk = playlist.allChannels.single { it.name == "NHK World" }
        assertEquals("央视", cctv.group)
        assertTrue(cctv.isIpv6)
        assertEquals("国际", nhk.group)
        assertFalse(nhk.isIpv6)
    }

    @Test
    fun `normalizes common group names`() {
        val playlist = M3uPlaylistParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="央视频道",CCTV-1
            http://a.example/1.m3u8
            #EXTINF:-1 group-title="卫视频道",浙江卫视
            http://a.example/2.m3u8
            #EXTINF:-1 group-title="其它",凤凰中文
            http://a.example/3.m3u8
            #EXTINF:-1 group-title="General",CCTV-2
            http://a.example/4.m3u8
            """.trimIndent()
        )

        assertEquals(listOf("央视", "卫视", "其他"), playlist.groups.map { it.name })
        assertEquals(2, playlist.groups.single { it.name == "其他" }.channels.size)
    }
}
