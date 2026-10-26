package com.vortexdl.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolutionHelperTest {

    @Test
    fun `formatDimensions correctly maps standard video resolutions`() {
        assertEquals("4K", ResolutionHelper.formatDimensions("3840x2160"))
        assertEquals("1440p", ResolutionHelper.formatDimensions("2560x1440"))
        assertEquals("1080p", ResolutionHelper.formatDimensions("1920x1080"))
        assertEquals("720p", ResolutionHelper.formatDimensions("1280x720"))
        assertEquals("480p", ResolutionHelper.formatDimensions("854x480"))
        assertEquals("480p", ResolutionHelper.formatDimensions("640x480"))
        assertEquals("360p", ResolutionHelper.formatDimensions("640x360"))
        assertEquals("240p", ResolutionHelper.formatDimensions("426x240"))
        assertEquals("144p", ResolutionHelper.formatDimensions("256x144"))

        // Invalid or null
        assertNull(ResolutionHelper.formatDimensions(null))
        assertNull(ResolutionHelper.formatDimensions(""))
        assertNull(ResolutionHelper.formatDimensions("invalid"))
        assertNull(ResolutionHelper.formatDimensions("1920"))
    }

    @Test
    fun `extractFromUrlOrTitle extracts quality tags accurately`() {
        assertEquals("1080p", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/movie_1080p.mp4"))
        assertEquals("720p", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/clips/trailer-720p.webm"))
        assertEquals("480p", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/video?res=480p&token=123"))
        assertEquals("4K", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/nature_4k.mp4"))
        assertEquals("360p", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/video/360/clip.mp4"))

        // Match from title if not in URL
        assertEquals("1080p", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/stream.mp4", "Epic Video 1080p Official"))
        assertEquals("4K", ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/stream.mp4", "Documentary (4K HDR)"))

        // No quality tag present
        assertNull(ResolutionHelper.extractFromUrlOrTitle("https://cdn.example.com/random_video.mp4", "Random Video"))
    }

    @Test
    fun `formatBandwidth formats bps into human-readable bitrates`() {
        assertEquals("5.0 Mbps", ResolutionHelper.formatBandwidth(5_000_000L))
        assertEquals("2.5 Mbps", ResolutionHelper.formatBandwidth(2_500_000L))
        assertEquals("800 Kbps", ResolutionHelper.formatBandwidth(800_000L))
        assertEquals("500 bps", ResolutionHelper.formatBandwidth(500L))
        assertEquals("", ResolutionHelper.formatBandwidth(0L))
    }

    @Test
    fun `getQualityDescription returns user-friendly descriptions`() {
        assertEquals("Full HD", ResolutionHelper.getQualityDescription("1080p"))
        assertEquals("HD", ResolutionHelper.getQualityDescription("720p"))
        assertEquals("SD", ResolutionHelper.getQualityDescription("480p"))
        assertEquals("4K Ultra HD", ResolutionHelper.getQualityDescription("4K"))
    }
}
