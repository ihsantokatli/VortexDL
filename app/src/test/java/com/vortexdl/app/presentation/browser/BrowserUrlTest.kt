package com.vortexdl.app.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlTest {

    @Test
    fun `bare domain becomes https url`() {
        assertEquals("https://example.com", BrowserUrl.toNavigableUrl("example.com"))
    }

    @Test
    fun `bare domain keeps path and query`() {
        assertEquals(
            "https://example.com/watch?v=1",
            BrowserUrl.toNavigableUrl("example.com/watch?v=1"),
        )
    }

    @Test
    fun `plain text becomes a search query`() {
        assertEquals(
            "https://www.google.com/search?q=funny+cats",
            BrowserUrl.toNavigableUrl("funny cats"),
        )
    }

    @Test
    fun `dangerous schemes are never navigated to`() {
        assertFalse(BrowserUrl.toNavigableUrl("javascript:alert(1)").startsWith("javascript:"))
        assertFalse(BrowserUrl.toNavigableUrl("file:///etc/passwd").startsWith("file:"))
        assertFalse(BrowserUrl.toNavigableUrl("intent://scan/#Intent;end").startsWith("intent:"))
    }

    @Test
    fun `blank input goes home`() {
        assertEquals(BrowserUrl.HOME_URL, BrowserUrl.toNavigableUrl("   "))
    }

    @Test
    fun `blank page stays blank`() {
        assertEquals(BrowserUrl.BLANK_PAGE, BrowserUrl.toNavigableUrl("about:blank"))
    }

    @Test
    fun `valid web urls are detected`() {
        assertTrue(BrowserUrl.isValidWebUrl("https://example.com"))
        assertTrue(BrowserUrl.isValidWebUrl("http://example.com/path"))
        assertFalse(BrowserUrl.isValidWebUrl("ftp://example.com"))
        assertFalse(BrowserUrl.isValidWebUrl("example.com"))
        assertFalse(BrowserUrl.isValidWebUrl("https://"))
        assertFalse(BrowserUrl.isValidWebUrl(""))
    }

    @Test
    fun `invalid http url falls back to search`() {
        val result = BrowserUrl.toNavigableUrl("https://")

        assertTrue(result.startsWith("https://www.google.com/search?q="))
    }
}
