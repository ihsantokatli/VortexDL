package com.vortexdl.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class FileDownloaderTest {

    @Test
    fun `sanitizes illegal file name characters`() {
        val result = FileDownloader.sanitizeFileName("My: Great / Video * Title ?", "mp4")
        assertEquals("My_ Great _ Video _ Title _._mp4".replace("__", "_").replace("._", "."), result.substringBeforeLast('.') + ".mp4")
    }

    @Test
    fun `handles empty or blank titles by defaulting to download`() {
        val result = FileDownloader.sanitizeFileName("   ", "webm")
        assertEquals("download.webm", result)
    }

    @Test
    fun `truncates long titles to 80 characters`() {
        val longTitle = "a".repeat(120)
        val result = FileDownloader.sanitizeFileName(longTitle, "mp4")
        assertEquals(80 + ".mp4".length, result.length)
        assertEquals("mp4", result.substringAfterLast('.'))
    }
}
