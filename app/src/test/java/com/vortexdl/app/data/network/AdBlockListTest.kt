package com.vortexdl.app.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockListTest {

    @Test
    fun `blocks known ad hosts and their subdomains`() {
        assertTrue(AdBlockList.isBlocked("https://doubleclick.net/pixel"))
        assertTrue(AdBlockList.isBlocked("https://ads.doubleclick.net/pixel.js"))
        assertTrue(AdBlockList.isBlocked("https://cdn.taboola.com/libtrc/unip.js"))
        assertTrue(AdBlockList.isBlocked("https://unityads.unity3d.com/v1"))
    }

    @Test
    fun `matches hosts case insensitively`() {
        assertTrue(AdBlockList.isBlocked("https://DOUBLECLICK.NET/pixel"))
    }

    @Test
    fun `does not block look alike domains`() {
        assertFalse(AdBlockList.isBlocked("https://notdoubleclick.net/video.mp4"))
        assertFalse(AdBlockList.isBlocked("https://doubleclick.net.example.com/video.mp4"))
    }

    @Test
    fun `does not block regular content`() {
        assertFalse(AdBlockList.isBlocked("https://example.com/video.mp4"))
        assertFalse(AdBlockList.isBlocked("https://cdn.example.com/hls/master.m3u8"))
    }

    @Test
    fun `invalid urls are not blocked`() {
        assertFalse(AdBlockList.isBlocked("not a url"))
        assertFalse(AdBlockList.isBlocked(""))
    }

    @Test
    fun `query string containing a blocked host does not block the page`() {
        assertFalse(AdBlockList.isBlocked("https://example.com/watch?ref=taboola.com"))
    }
}
