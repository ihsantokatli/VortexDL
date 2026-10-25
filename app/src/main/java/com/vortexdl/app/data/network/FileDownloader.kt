package com.vortexdl.app.data.network

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

sealed interface DownloadResult {
    data class Success(val file: File, val totalBytes: Long) : DownloadResult
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : DownloadResult
    data class Error(val throwable: Throwable) : DownloadResult
}

@Singleton
class FileDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        targetFile: File,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long, speedBytesPerSecond: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        var existingBytes = if (targetFile.exists()) targetFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(name, value)
            }
        }

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        var response: Response? = null
        try {
            response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // If range request was not satisfiable (e.g. 416), try fresh download
                if (response.code == 416) {
                    targetFile.delete()
                    existingBytes = 0L
                    val freshRequest = requestBuilder.apply {
                        headers.forEach { (name, value) -> header(name, value) }
                    }.removeHeader("Range").build()
                    response.close()
                    response = okHttpClient.newCall(freshRequest).execute()
                }

                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}: ${response.message}")
                }
            }

            val body = response.body ?: throw IOException("Empty response body")
            val isPartial = response.code == 206
            val append = isPartial && existingBytes > 0L

            if (!append && existingBytes > 0L) {
                targetFile.delete()
                existingBytes = 0L
            }

            val contentLength = body.contentLength()
            val totalBytes = when {
                contentLength > 0L && append -> existingBytes + contentLength
                contentLength > 0L -> contentLength
                else -> -1L
            }

            targetFile.parentFile?.mkdirs()

            var downloadedBytes = existingBytes
            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastSample = 0L
            var currentSpeed = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled() || !isActive) {
                            output.flush()
                            return@withContext DownloadResult.Paused(downloadedBytes, totalBytes)
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastSample += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastProgressTime
                        if (elapsed >= PROGRESS_UPDATE_INTERVAL_MS) {
                            currentSpeed = (bytesSinceLastSample * 1000L) / elapsed
                            bytesSinceLastSample = 0L
                            lastProgressTime = now

                            onProgress(downloadedBytes, totalBytes, currentSpeed)
                        }
                    }
                    output.flush()
                }
            }

            // Final progress emission
            onProgress(downloadedBytes, totalBytes, 0L)

            return@withContext DownloadResult.Success(targetFile, downloadedBytes)
        } catch (e: Throwable) {
            if (isCancelled() || !isActive) {
                return@withContext DownloadResult.Paused(targetFile.length(), -1L)
            }
            return@withContext DownloadResult.Error(e)
        } finally {
            response?.close()
        }
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024 // 32 KB
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L

        fun sanitizeFileName(rawTitle: String, extension: String): String {
            val clean = rawTitle
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .trim()
                .take(80)
                .ifEmpty { "download" }
            val cleanExt = extension.trim().trimStart('.').lowercase()
            return "$clean.$cleanExt"
        }
    }
}
