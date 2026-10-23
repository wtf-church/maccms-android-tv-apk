package com.maccms.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSettingsTest {

    @Test
    fun `keeps valid http and https urls`() {
        assertEquals("http://a.example/1.m3u8", LiveSettings.normalize("http://a.example/1.m3u8"))
        assertEquals("https://a.example/1.m3u8", LiveSettings.normalize("https://a.example/1.m3u8"))
    }

    @Test
    fun `adds https scheme when missing`() {
        assertEquals("https://a.example/1.m3u8", LiveSettings.normalize("a.example/1.m3u8"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://a.example/1.m3u8", LiveSettings.normalize("  https://a.example/1.m3u8  "))
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertNull(LiveSettings.normalize("ftp://a.example/1.m3u8"))
        assertNull(LiveSettings.normalize("file:///sdcard/1.m3u8"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(LiveSettings.normalize(null))
        assertNull(LiveSettings.normalize(""))
        assertNull(LiveSettings.normalize("   "))
    }

    @Test
    fun `parses playlist url from config text`() {
        assertEquals(
            "https://example.com/a.m3u8",
            LiveSettings.parseConfigUrl("playlistUrl=https://example.com/a.m3u8\n")
        )
    }

    @Test
    fun `returns null when config text has no playlist url`() {
        assertNull(LiveSettings.parseConfigUrl("# no url here\n"))
        assertNull(LiveSettings.parseConfigUrl("playlistUrl=not a url"))
    }

    @Test
    fun `percent encodes chinese path segments`() {
        assertEquals(
            "https://a.example/%E7%94%B5%E8%A7%86.m3u",
            LiveSettings.normalize("https://a.example/电视.m3u")
        )
    }

    @Test
    fun `rewrites github blob urls to raw`() {
        assertEquals(
            "https://raw.githubusercontent.com/example/repo/main/list.m3u",
            LiveSettings.normalize("https://github.com/example/repo/blob/main/list.m3u")
        )
    }

    @Test
    fun `rewrites github blob urls behind a gh proxy`() {
        assertEquals(
            "https://gh-proxy.org/https://raw.githubusercontent.com/example/repo/main/list.m3u",
            LiveSettings.normalize(
                "https://gh-proxy.org/https://github.com/example/repo/blob/main/list.m3u"
            )
        )
    }

    @Test
    fun `keeps gh proxy raw urls intact`() {
        val url = "https://gh-proxy.org/https://raw.githubusercontent.com/example/repo/main/list.m3u"
        assertEquals(url, LiveSettings.normalize(url))
    }
}
