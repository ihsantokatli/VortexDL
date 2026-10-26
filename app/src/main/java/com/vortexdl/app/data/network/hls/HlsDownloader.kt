package com.vortexdl.app.data.network.hls

import com.vortexdl.app.data.network.DownloadResult
import com.vortexdl.app.domain.model.ResolutionHelper
import com.vortexdl.app.domain.model.VideoQualityOption
import com.vortexdl.app.domain.model.hls.HlsMediaPlaylist
import com.vortexdl.app.domain.model.hls.HlsPlaylist
import com.vortexdl.app.domain.model.hls.HlsSegment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Singleton
class HlsDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val parser: HlsPlaylistParser,
) {

    suspend fun getStreamQualities(
        url: String,
        headers: Map<String, String>,
    ): List<VideoQualityOption> = withContext(Dispatchers.IO) {
        try {
            val content = fetchText(url, headers)
            val parsed = parser.parse(content, url)
            when (parsed) {
                is HlsPlaylist.Master -> {
                    parsed.playlist.variants.map { variant ->
                        val formattedRes = ResolutionHelper.formatDimensions(variant.resolution)
                            ?: ResolutionHelper.extractFromUrlOrTitle(variant.url)
                            ?: "Auto"
                        val description = ResolutionHelper.getQualityDescription(formattedRes)
                        val formattedBw = ResolutionHelper.formatBandwidth(variant.bandwidth)
                        VideoQualityOption(
                            resolution = formattedRes,
                            description = description,
                            bandwidth = variant.bandwidth,
                            formattedBandwidth = formattedBw,
                            url = variant.url,
                        )
                    }
                }
                is HlsPlaylist.Media -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        targetFile: File,
        tempDir: File,
        preferredResolution: String? = null,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long, speedBytesPerSecond: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            if (isCancelled() || !isActive) {
                return@withContext DownloadResult.Paused(0L, -1L)
            }

            // 1. Fetch and resolve to a Media Playlist
            val mediaPlaylist = fetchMediaPlaylist(url, headers, preferredResolution)
            val segments = mediaPlaylist.segments

            if (segments.isEmpty()) {
                throw IOException("HLS playlist contains no segments to download")
            }

            tempDir.mkdirs()

            // 2. Count existing segments for resume
            val totalSegments = segments.size
            val completedSegments = AtomicInteger(0)
            val downloadedBytesCounter = AtomicLong(0)

            for (segment in segments) {
                val segFile = getSegmentFile(tempDir, segment.index)
                if (segFile.exists() && segFile.length() > 0L) {
                    completedSegments.incrementAndGet()
                    downloadedBytesCounter.addAndGet(segFile.length())
                }
            }

            val lastProgressTime = AtomicLong(System.currentTimeMillis())
            val bytesSinceLastSample = AtomicLong(0L)
            val currentSpeed = AtomicLong(0L)

            // Initial progress report
            val initialBytes = downloadedBytesCounter.get()
            val initialEstimatedTotal = estimateTotalBytes(initialBytes, completedSegments.get(), totalSegments)
            onProgress(initialBytes, initialEstimatedTotal, 0L)

            // 3. Download remaining segments concurrently
            val semaphore = Semaphore(CONCURRENT_SEGMENT_DOWNLOADS)
            var downloadError: Throwable? = null

            coroutineScope {
                val tasks = segments.map { segment ->
                    async {
                        semaphore.withPermit {
                            if (isCancelled() || !isActive || downloadError != null) {
                                return@async
                            }

                            val segFile = getSegmentFile(tempDir, segment.index)
                            if (segFile.exists() && segFile.length() > 0L) {
                                return@async
                            }

                            val partFile = File(tempDir, "seg_%06d.part".format(segment.index))
                            var success = false
                            var attempt = 0

                            while (!success && attempt < MAX_SEGMENT_RETRIES && !isCancelled() && isActive && downloadError == null) {
                                attempt++
                                try {
                                    downloadSingleSegment(
                                        segment = segment,
                                        headers = headers,
                                        partFile = partFile,
                                        isCancelled = isCancelled,
                                        onBytesRead = { bytesRead ->
                                            val currentBytes = downloadedBytesCounter.addAndGet(bytesRead.toLong())
                                            val sampleBytes = bytesSinceLastSample.addAndGet(bytesRead.toLong())

                                            val now = System.currentTimeMillis()
                                            val last = lastProgressTime.get()
                                            val elapsed = now - last
                                            if (elapsed >= PROGRESS_UPDATE_INTERVAL_MS && lastProgressTime.compareAndSet(last, now)) {
                                                val speed = (sampleBytes * 1000L) / elapsed
                                                bytesSinceLastSample.set(0L)
                                                currentSpeed.set(speed)

                                                val estimatedTotal = estimateTotalBytes(
                                                    downloadedBytes = currentBytes,
                                                    completed = completedSegments.get(),
                                                    total = totalSegments,
                                                )
                                                onProgress(currentBytes, estimatedTotal, speed)
                                            }
                                        },
                                    )

                                    if (partFile.exists() && partFile.length() > 0L) {
                                        if (segFile.exists()) segFile.delete()
                                        if (partFile.renameTo(segFile)) {
                                            completedSegments.incrementAndGet()
                                            success = true
                                        }
                                    }
                                } catch (e: Throwable) {
                                    if (isCancelled() || !isActive) return@async
                                    if (attempt >= MAX_SEGMENT_RETRIES) {
                                        downloadError = e
                                    }
                                }
                            }
                        }
                    }
                }

                tasks.awaitAll()
            }

            if (isCancelled() || !isActive) {
                val currentBytes = downloadedBytesCounter.get()
                val estimatedTotal = estimateTotalBytes(currentBytes, completedSegments.get(), totalSegments)
                return@withContext DownloadResult.Paused(currentBytes, estimatedTotal)
            }

            downloadError?.let { throw it }

            // 4. Verify all segments were downloaded
            for (segment in segments) {
                val segFile = getSegmentFile(tempDir, segment.index)
                if (!segFile.exists() || segFile.length() == 0L) {
                    throw IOException("Segment ${segment.index} missing or empty after download")
                }
            }

            // 5. Merge / Concatenate segments into targetFile
            targetFile.parentFile?.mkdirs()
            val tempMergedFile = File(targetFile.parentFile, "${targetFile.name}.merging")
            if (tempMergedFile.exists()) tempMergedFile.delete()

            mergeSegments(tempDir, totalSegments, tempMergedFile)

            if (targetFile.exists()) targetFile.delete()
            if (!tempMergedFile.renameTo(targetFile)) {
                // Fallback copy if rename fails across mount points
                tempMergedFile.copyTo(targetFile, overwrite = true)
                tempMergedFile.delete()
            }

            // 6. Cleanup temp directory
            tempDir.deleteRecursively()

            val finalLength = targetFile.length()
            onProgress(finalLength, finalLength, 0L)

            return@withContext DownloadResult.Success(targetFile, finalLength)
        } catch (e: Throwable) {
            if (isCancelled() || !isActive) {
                val currentBytes = tempDir.listFiles()?.sumOf { it.length() } ?: 0L
                return@withContext DownloadResult.Paused(currentBytes, -1L)
            }
            return@withContext DownloadResult.Error(e)
        }
    }

    private fun fetchMediaPlaylist(
        url: String,
        headers: Map<String, String>,
        preferredResolution: String? = null,
    ): HlsMediaPlaylist {
        val initialContent = fetchText(url, headers)
        return when (val parsed = parser.parse(initialContent, url)) {
            is HlsPlaylist.Media -> parsed.playlist
            is HlsPlaylist.Master -> {
                val variant = if (!preferredResolution.isNullOrBlank()) {
                    parsed.playlist.variants.find { v ->
                        v.resolution == preferredResolution ||
                            ResolutionHelper.formatDimensions(v.resolution) == preferredResolution ||
                            v.resolution?.contains(preferredResolution, ignoreCase = true) == true
                    } ?: parsed.playlist.highestQualityVariant()
                } else {
                    parsed.playlist.highestQualityVariant()
                } ?: throw IOException("No stream variants found in HLS master playlist")

                val variantContent = fetchText(variant.url, headers)
                when (val variantParsed = parser.parse(variantContent, variant.url)) {
                    is HlsPlaylist.Media -> variantParsed.playlist
                    is HlsPlaylist.Master -> throw IOException("Nested master playlist is not supported")
                }
            }
        }
    }

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(name, value)
            }
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch playlist ($url): HTTP ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty playlist response ($url)")
            return body.string()
        }
    }

    private suspend fun downloadSingleSegment(
        segment: HlsSegment,
        headers: Map<String, String>,
        partFile: File,
        isCancelled: () -> Boolean,
        onBytesRead: suspend (Int) -> Unit,
    ) {
        val requestBuilder = Request.Builder().url(segment.url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(name, value)
            }
        }

        segment.byteRange?.let { range ->
            val rangeHeader = parseByteRangeToHeader(range)
            if (rangeHeader != null) {
                requestBuilder.header("Range", rangeHeader)
            }
        }

        var response: Response? = null
        try {
            response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP error ${response.code} for segment ${segment.index}")
            }

            val body = response.body ?: throw IOException("Empty body for segment ${segment.index}")

            partFile.parentFile?.mkdirs()
            FileOutputStream(partFile, false).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            throw IOException("Download cancelled during segment ${segment.index}")
                        }
                        output.write(buffer, 0, bytesRead)
                        onBytesRead(bytesRead)
                    }
                    output.flush()
                }
            }
        } finally {
            response?.close()
        }
    }

    fun mergeSegments(tempDir: File, totalSegments: Int, outputFile: File) {
        val buffer = ByteArray(MERGE_BUFFER_SIZE)
        FileOutputStream(outputFile, false).use { output ->
            for (i in 0 until totalSegments) {
                val segFile = getSegmentFile(tempDir, i)
                if (!segFile.exists() || segFile.length() == 0L) {
                    throw IOException("Missing segment $i during merge")
                }

                FileInputStream(segFile).use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            output.flush()
        }
    }

    private fun getSegmentFile(tempDir: File, index: Int): File =
        File(tempDir, "seg_%06d.ts".format(index))

    private fun estimateTotalBytes(downloadedBytes: Long, completed: Int, total: Int): Long {
        if (completed <= 0 || downloadedBytes <= 0L || total <= 0) return -1L
        if (completed >= total) return downloadedBytes
        val averageSegmentSize = downloadedBytes / completed
        return averageSegmentSize * total
    }

    fun parseByteRangeToHeader(byteRange: String): String? {
        val parts = byteRange.split('@')
        val length = parts[0].trim().toLongOrNull() ?: return null
        val offset = if (parts.size > 1) parts[1].trim().toLongOrNull() ?: 0L else 0L
        val end = offset + length - 1L
        return "bytes=$offset-$end"
    }

    companion object {
        private const val CONCURRENT_SEGMENT_DOWNLOADS = 3
        private const val MAX_SEGMENT_RETRIES = 3
        private const val BUFFER_SIZE = 32 * 1024 // 32 KB
        private const val MERGE_BUFFER_SIZE = 64 * 1024 // 64 KB
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}
