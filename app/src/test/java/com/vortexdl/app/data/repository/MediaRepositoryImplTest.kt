package com.vortexdl.app.data.repository

import com.vortexdl.app.data.network.MediaUrlDetector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRepositoryImplTest {

    private val repository = MediaRepositoryImpl(MediaUrlDetector())

    @Test
    fun `stores detected media newest first`() {
        repository.captureMedia("https://cdn.example.com/one.mp4", emptyMap(), null, null, null, null)
        repository.captureMedia("https://cdn.example.com/two.mp4", emptyMap(), null, null, null, null)

        val stored = repository.detectedMedia.value
        assertEquals(2, stored.size)
        assertEquals("https://cdn.example.com/two.mp4", stored[0].url)
        assertEquals("https://cdn.example.com/one.mp4", stored[1].url)
    }

    @Test
    fun `ignores duplicate urls`() {
        val first = repository.captureMedia(
            "https://cdn.example.com/one.mp4",
            emptyMap(),
            null,
            null,
            null,
            null,
        )
        val duplicate = repository.captureMedia(
            "https://cdn.example.com/one.mp4#again",
            emptyMap(),
            null,
            null,
            null,
            null,
        )

        assertNotNull(first)
        assertNull(duplicate)
        assertEquals(1, repository.detectedMedia.value.size)
    }

    @Test
    fun `ignores urls that are not media`() {
        val result = repository.captureMedia(
            "https://cdn.example.com/cover.jpg",
            emptyMap(),
            null,
            null,
            null,
            null,
        )

        assertNull(result)
        assertEquals(0, repository.detectedMedia.value.size)
    }

    @Test
    fun `clear removes stored media and duplicate tracking`() {
        repository.captureMedia("https://cdn.example.com/one.mp4", emptyMap(), null, null, null, null)
        repository.clear()

        assertEquals(0, repository.detectedMedia.value.size)

        val recaptured = repository.captureMedia(
            "https://cdn.example.com/one.mp4",
            emptyMap(),
            null,
            null,
            null,
            null,
        )
        assertNotNull(recaptured)
        assertEquals(1, repository.detectedMedia.value.size)
    }

    @Test
    fun `caps the stored list at two hundred items`() {
        for (index in 1..205) {
            repository.captureMedia(
                "https://cdn.example.com/video-$index.mp4",
                emptyMap(),
                null,
                null,
                null,
                null,
            )
        }

        val stored = repository.detectedMedia.value
        assertEquals(200, stored.size)
        assertEquals("https://cdn.example.com/video-205.mp4", stored.first().url)
        assertEquals("https://cdn.example.com/video-6.mp4", stored.last().url)
    }

    @Test
    fun `captures from concurrent threads are all stored exactly once`() {
        val threadCount = 8
        val capturesPerThread = 25
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        repeat(threadCount) { thread ->
            executor.execute {
                startLatch.await()
                repeat(capturesPerThread) { index ->
                    repository.captureMedia(
                        "https://cdn.example.com/thread-$thread-video-$index.mp4",
                        emptyMap(),
                        null,
                        null,
                        null,
                        null,
                    )
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val stored = repository.detectedMedia.value
        assertEquals(threadCount * capturesPerThread, stored.size)
        assertEquals(stored.size, stored.map { it.id }.distinct().size)
        assertEquals(stored.size, stored.map { it.url }.distinct().size)
    }
}
