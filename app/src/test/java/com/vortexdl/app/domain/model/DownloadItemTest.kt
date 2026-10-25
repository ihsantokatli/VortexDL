package com.vortexdl.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadItemTest {

    @Test
    fun `formats bytes correctly across units`() {
        assertEquals("0 B", DownloadItem.formatBytes(0L))
        assertEquals("512 B", DownloadItem.formatBytes(512L))
        assertEquals("1.0 KB", DownloadItem.formatBytes(1024L))
        assertEquals("1.5 MB", DownloadItem.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", DownloadItem.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `computes progress accurately`() {
        val item = DownloadItem(
            id = "test-1",
            url = "https://example.com/v.mp4",
            title = "Video",
            mimeType = "video/mp4",
            extension = "mp4",
            requestHeaders = emptyMap(),
            totalBytes = 1000L,
            downloadedBytes = 500L,
            speedBytesPerSecond = 1024L,
            status = DownloadStatus.DOWNLOADING,
            filePath = null,
            createdAt = 0L,
            completedAt = null,
            errorMessage = null,
        )

        assertEquals(0.5f, item.progress, 0.001f)
        assertEquals(50, item.progressPercent)
        assertEquals("1.0 KB/s", item.formattedSpeed)
        assertTrue(item.status.isActive)
        assertFalse(item.status.isFinished)
    }

    @Test
    fun `completed item without totalBytes reports full progress`() {
        val item = DownloadItem(
            id = "test-2",
            url = "https://example.com/v.mp4",
            title = "Video",
            mimeType = "video/mp4",
            extension = "mp4",
            requestHeaders = emptyMap(),
            totalBytes = -1L,
            downloadedBytes = 500L,
            speedBytesPerSecond = 0L,
            status = DownloadStatus.COMPLETED,
            filePath = "/path/to/file.mp4",
            createdAt = 0L,
            completedAt = 100L,
            errorMessage = null,
        )

        assertEquals(1f, item.progress, 0.001f)
        assertEquals(100, item.progressPercent)
        assertTrue(item.status.isFinished)
        assertFalse(item.status.isActive)
    }
}
