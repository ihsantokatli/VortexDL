package com.vortexdl.app.data.network.hls

import com.vortexdl.app.domain.model.hls.HlsPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    private val parser = HlsPlaylistParser()

    @Test
    fun `parses master playlist with multiple variants and sorts by bandwidth descending`() {
        val masterContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720p/prog_index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            https://cdn.example.com/live/1080p.m3u8
        """.trimIndent()

        val baseUrl = "https://example.com/streams/master.m3u8"
        val result = parser.parse(masterContent, baseUrl)

        assertTrue(result is HlsPlaylist.Master)
        val master = (result as HlsPlaylist.Master).playlist

        assertEquals(3, master.variants.size)

        // First should be highest bandwidth (5000000)
        val best = master.highestQualityVariant()
        assertNotNull(best)
        assertEquals(5000000L, best?.bandwidth)
        assertEquals("1920x1080", best?.resolution)
        assertEquals("https://cdn.example.com/live/1080p.m3u8", best?.url)

        // Second should be 2500000
        val second = master.variants[1]
        assertEquals(2500000L, second.bandwidth)
        assertEquals("1280x720", second.resolution)
        assertEquals("https://example.com/streams/720p/prog_index.m3u8", second.url)

        // Third should be 800000
        val third = master.variants[2]
        assertEquals(800000L, third.bandwidth)
        assertEquals("640x360", third.resolution)
        assertEquals("https://example.com/streams/360p.m3u8", third.url)
    }

    @Test
    fun `parses media playlist with segments and relative urls`() {
        val mediaContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:101
            #EXTINF:9.980,Intro
            segment_101.ts
            #EXTINF:10.000,
            segment_102.ts?token=abc
            #EXTINF:5.500,Outro
            /global/segment_103.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = "https://example.com/vod/video.m3u8"
        val result = parser.parse(mediaContent, baseUrl)

        assertTrue(result is HlsPlaylist.Media)
        val media = (result as HlsPlaylist.Media).playlist

        assertEquals(10.0, media.targetDurationSeconds, 0.001)
        assertEquals(101L, media.mediaSequence)
        assertTrue(media.isEndList)
        assertEquals(3, media.segments.size)
        assertEquals(25.48, media.totalDurationSeconds, 0.001)

        val seg0 = media.segments[0]
        assertEquals(0, seg0.index)
        assertEquals(9.98, seg0.durationSeconds, 0.001)
        assertEquals("Intro", seg0.title)
        assertEquals("https://example.com/vod/segment_101.ts", seg0.url)

        val seg1 = media.segments[1]
        assertEquals(1, seg1.index)
        assertEquals(10.0, seg1.durationSeconds, 0.001)
        assertEquals("https://example.com/vod/segment_102.ts?token=abc", seg1.url)

        val seg2 = media.segments[2]
        assertEquals(2, seg2.index)
        assertEquals(5.5, seg2.durationSeconds, 0.001)
        assertEquals("https://example.com/global/segment_103.ts", seg2.url)
    }

    @Test
    fun `parses media playlist with byte range tags`() {
        val mediaContent = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-BYTERANGE:500000@0
            #EXTINF:6.0,
            combined.ts
            #EXT-X-BYTERANGE:450000@500000
            #EXTINF:5.5,
            combined.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = "https://example.com/media.m3u8"
        val result = parser.parse(mediaContent, baseUrl)

        assertTrue(result is HlsPlaylist.Media)
        val media = (result as HlsPlaylist.Media).playlist
        assertEquals(2, media.segments.size)

        assertEquals("500000@0", media.segments[0].byteRange)
        assertEquals("450000@500000", media.segments[1].byteRange)
    }

    @Test
    fun `resolves protocol-relative and query param urls correctly`() {
        val baseUrl = "https://cdn.test.com/hls/master.m3u8?auth=123"
        assertEquals("https://other.com/chunk.ts", parser.resolveUrl(baseUrl, "//other.com/chunk.ts"))
        assertEquals("https://cdn.test.com/hls/chunk_0.ts", parser.resolveUrl(baseUrl, "chunk_0.ts"))
        assertEquals("https://cdn.test.com/root_chunk.ts", parser.resolveUrl(baseUrl, "/root_chunk.ts"))
        assertEquals("https://absolute.com/chunk.ts", parser.resolveUrl(baseUrl, "https://absolute.com/chunk.ts"))
    }

    @Test
    fun `detects live streams without endlist tag`() {
        val liveContent = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXTINF:4.0,
            live_0.ts
            #EXTINF:4.0,
            live_1.ts
        """.trimIndent()

        val result = parser.parse(liveContent, "https://live.example.com/stream.m3u8")
        assertTrue(result is HlsPlaylist.Media)
        assertFalse((result as HlsPlaylist.Media).playlist.isEndList)
    }
}
