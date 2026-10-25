package com.vortexdl.app.domain.model

import java.util.Locale

/**
 * Domain model representing a media download task and its lifecycle.
 */
data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val mimeType: String,
    val extension: String,
    val requestHeaders: Map<String, String>,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSecond: Long,
    val status: DownloadStatus,
    val filePath: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?,
) {
    val progress: Float
        get() = when {
            totalBytes > 0L -> (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            status == DownloadStatus.COMPLETED -> 1f
            else -> 0f
        }

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedDownloaded: String
        get() = formatBytes(downloadedBytes)

    val formattedTotal: String
        get() = if (totalBytes > 0L) formatBytes(totalBytes) else "Unknown"

    val formattedSpeed: String
        get() = if (status == DownloadStatus.DOWNLOADING && speedBytesPerSecond > 0L) {
            "${formatBytes(speedBytesPerSecond)}/s"
        } else {
            ""
        }

    val isResumeSupported: Boolean
        get() = extension.equals("mp4", ignoreCase = true) ||
            extension.equals("webm", ignoreCase = true)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return if (digitGroups == 0) {
                "${bytes} B"
            } else {
                String.format(Locale.US, "%.1f %s", value, units[digitGroups])
            }
        }
    }
}
