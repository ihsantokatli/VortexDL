package com.vortexdl.app.data.network

import com.vortexdl.app.domain.model.DetectedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlDetectorTest {

    private val detector = MediaUrlDetector()

    @Test
    fun `detects mp4 and derives file name title`() {
        val media = detect("https://cdn.example.com/videos/trailer.mp4?token=abc#t=5")

        assertNotNull(media)
        assertEquals("mp4", media!!.extension)
        assertEquals("video/mp4", media.mimeType)
        assertEquals("trailer", media.title)
        assertFalse(media.isStreamingFormat)
    }

    @Test
    fun `detects streaming manifests`() {
        val hls = detect("https://cdn.example.com/live/master.m3u8")
        val dash = detect("https://cdn.example.com/live/manifest.mpd")
        val webm = detect("https://cdn.example.com/clips/demo.webm")

        assertEquals("application/vnd.apple.mpegurl", hls?.mimeType)
        assertTrue(hls!!.isStreamingFormat)
        assertEquals("application/dash+xml", dash?.mimeType)
        assertTrue(dash!!.isStreamingFormat)
        assertFalse(webm!!.isStreamingFormat)
    }

    @Test
    fun `ignores non media responses`() {
        assertNull(detect("https://example.com/index.html"))
        assertNull(detect("https://example.com/poster.jpg"))
        assertNull(detect("https://example.com/segment.ts"))
        assertNull(detect("https://example.com/"))
        assertNull(detect("data:video/mp4;base64,AAAA"))
        assertNull(detect("blob:https://example.com/1234"))
    }

    @Test
    fun `captures user agent referer and origin when missing`() {
        val media = detect(
            url = "https://cdn.example.com/trailer.mp4",
            pageUrl = "https://movies.example.com/watch/42",
            defaultUserAgent = "VortexDL/1.0",
        )

        val headers = media!!.requestHeaders
        assertEquals("VortexDL/1.0", headers["User-Agent"])
        assertEquals("https://movies.example.com/watch/42", headers["Referer"])
        assertEquals("https://movies.example.com", headers["Origin"])
        assertNull(headers["Cookie"])
    }

    @Test
    fun `keeps original request headers and drops hop by hop headers`() {
        val media = detect(
            url = "https://cdn.example.com/trailer.mp4",
            requestHeaders = mapOf(
                "User-Agent" to "Browser/2.0",
                "Referer" to "https://player.example.com/embed",
                "Origin" to "https://player.example.com",
                "Cookie" to "session=abc",
                "Accept" to "*/*",
                "Host" to "cdn.example.com",
                "Content-Length" to "0",
                "Accept-Encoding" to "gzip",
            ),
        )

        val headers = media!!.requestHeaders
        assertEquals("Browser/2.0", headers["User-Agent"])
        assertEquals("https://player.example.com/embed", headers["Referer"])
        assertEquals("https://player.example.com", headers["Origin"])
        assertEquals("session=abc", headers["Cookie"])
        assertEquals("*/*", headers["Accept"])
        assertFalse(headers.keys.any { it.equals("Host", ignoreCase = true) })
        assertFalse(headers.keys.any { it.equals("Content-Length", ignoreCase = true) })
        assertFalse(headers.keys.any { it.equals("Accept-Encoding", ignoreCase = true) })
    }

    @Test
    fun `uses page cookies only for the same host`() {
        val sameHost = detect(
            url = "https://example.com/media/trailer.mp4",
            pageUrl = "https://example.com/watch/1",
            pageCookies = "session=abc",
        )
        val otherHost = detect(
            url = "https://cdn.example.net/media/trailer.mp4",
            pageUrl = "https://example.com/watch/1",
            pageCookies = "session=abc",
        )

        assertEquals("session=abc", sameHost!!.requestHeaders["Cookie"])
        assertNull(otherHost!!.requestHeaders["Cookie"])
    }

    @Test
    fun `falls back to page title for generic file names`() {
        val media = detect(
            url = "https://cdn.example.com/hls/index.m3u8",
            pageTitle = "Great Documentary",
        )

        assertEquals("Great Documentary", media!!.title)
    }

    @Test
    fun `decodes url encoded file names`() {
        val media = detect("https://cdn.example.com/videos/my%20great%20clip.mp4")

        assertEquals("my great clip", media!!.title)
    }

    @Test
    fun `origin includes non default port`() {
        val media = detect(
            url = "https://cdn.example.com/trailer.mp4",
            pageUrl = "http://localhost:8080/watch",
        )

        assertEquals("http://localhost:8080", media!!.requestHeaders["Origin"])
    }

    @Test
    fun `accepts uppercase file extensions`() {
        val media = detect("https://cdn.example.com/TRAILER.MP4")

        assertEquals("mp4", media!!.extension)
    }

    private fun detect(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        pageUrl: String? = null,
        pageTitle: String? = null,
        defaultUserAgent: String? = null,
        pageCookies: String? = null,
    ): DetectedMedia? = detector.detect(
        url = url,
        requestHeaders = requestHeaders,
        pageUrl = pageUrl,
        pageTitle = pageTitle,
        defaultUserAgent = defaultUserAgent,
        pageCookies = pageCookies,
    )
}
