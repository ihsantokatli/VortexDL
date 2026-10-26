package com.vortexdl.app.data.network.hls

import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HlsDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var downloader: HlsDownloader
    private val parser = HlsPlaylistParser()

    @Before
    fun setUp() {
        downloader = HlsDownloader(
            okHttpClient = OkHttpClient(),
            parser = parser,
        )
    }

    @Test
    fun `parseByteRangeToHeader converts byte range tags correctly`() {
        assertEquals("bytes=100000-599999", downloader.parseByteRangeToHeader("500000@100000"))
        assertEquals("bytes=0-249999", downloader.parseByteRangeToHeader("250000@0"))
        assertEquals("bytes=0-99999", downloader.parseByteRangeToHeader("100000"))
        assertNull(downloader.parseByteRangeToHeader("invalid"))
        assertNull(downloader.parseByteRangeToHeader(""))
    }

    @Test
    fun `mergeSegments concatenates segments in strict sequential order`() {
        val segmentDir = tempFolder.newFolder("hls_segments")
        val outputFile = File(tempFolder.root, "output.mp4")

        val segment1Data = byteArrayOf(1, 2, 3, 4, 5)
        val segment2Data = byteArrayOf(6, 7, 8)
        val segment3Data = byteArrayOf(9, 10, 11, 12)

        File(segmentDir, "seg_000000.ts").writeBytes(segment1Data)
        File(segmentDir, "seg_000001.ts").writeBytes(segment2Data)
        File(segmentDir, "seg_000002.ts").writeBytes(segment3Data)

        downloader.mergeSegments(segmentDir, totalSegments = 3, outputFile = outputFile)

        assertTrue(outputFile.exists())
        assertEquals(12L, outputFile.length())

        val expectedData = segment1Data + segment2Data + segment3Data
        assertArrayEquals(expectedData, outputFile.readBytes())
    }

    @Test
    fun `mergeSegments throws IOException if any segment is missing`() {
        val segmentDir = tempFolder.newFolder("hls_missing_segment")
        val outputFile = File(tempFolder.root, "output_fail.mp4")

        File(segmentDir, "seg_000000.ts").writeBytes(byteArrayOf(1, 2))
        // seg_000001 is missing!
        File(segmentDir, "seg_000002.ts").writeBytes(byteArrayOf(3, 4))

        try {
            downloader.mergeSegments(segmentDir, totalSegments = 3, outputFile = outputFile)
            fail("Expected IOException due to missing segment")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Missing segment 1") == true)
        }
    }
}
