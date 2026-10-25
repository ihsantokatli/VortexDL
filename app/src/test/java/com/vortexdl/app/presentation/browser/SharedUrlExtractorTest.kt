package com.vortexdl.app.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlExtractorTest {

    @Test
    fun `extracts url embedded in shared text`() {
        assertEquals(
            "https://example.com/watch?v=42",
            SharedUrlExtractor.extractFromText("Watch this: https://example.com/watch?v=42"),
        )
    }

    @Test
    fun `strips trailing punctuation`() {
        assertEquals(
            "https://example.com/video",
            SharedUrlExtractor.extractFromText("Look at https://example.com/video."),
        )
        assertEquals(
            "https://example.com/video?a=1",
            SharedUrlExtractor.extractFromText("(source: https://example.com/video?a=1)"),
        )
    }

    @Test
    fun `adds https to a shared bare domain`() {
        assertEquals(
            "https://example.com/clip",
            SharedUrlExtractor.extractFromText("example.com/clip"),
        )
    }

    @Test
    fun `returns null when no web url is present`() {
        assertNull(SharedUrlExtractor.extractFromText(null))
        assertNull(SharedUrlExtractor.extractFromText(""))
        assertNull(SharedUrlExtractor.extractFromText("just some words"))
        assertNull(SharedUrlExtractor.extractFromText("javascript:alert(1)"))
    }

    @Test
    fun `prefers the first embedded url`() {
        assertEquals(
            "https://first.example.com/a",
            SharedUrlExtractor.extractFromText(
                "https://first.example.com/a and https://second.example.com/b",
            ),
        )
    }
}
