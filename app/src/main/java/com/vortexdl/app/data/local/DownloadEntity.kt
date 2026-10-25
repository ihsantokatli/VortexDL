package com.vortexdl.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.vortexdl.app.domain.model.DownloadItem
import com.vortexdl.app.domain.model.DownloadStatus

@Entity(tableName = "downloads")
@TypeConverters(HeaderMapConverter::class)
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val mimeType: String,
    val extension: String,
    val requestHeaders: Map<String, String>,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSecond: Long,
    val status: String,
    val filePath: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?,
) {
    fun toDomainModel(): DownloadItem {
        val parsedStatus = runCatching {
            DownloadStatus.valueOf(status)
        }.getOrDefault(DownloadStatus.FAILED)

        return DownloadItem(
            id = id,
            url = url,
            title = title,
            mimeType = mimeType,
            extension = extension,
            requestHeaders = requestHeaders,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            status = parsedStatus,
            filePath = filePath,
            createdAt = createdAt,
            completedAt = completedAt,
            errorMessage = errorMessage,
        )
    }

    companion object {
        fun fromDomainModel(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            mimeType = item.mimeType,
            extension = item.extension,
            requestHeaders = item.requestHeaders,
            totalBytes = item.totalBytes,
            downloadedBytes = item.downloadedBytes,
            speedBytesPerSecond = item.speedBytesPerSecond,
            status = item.status.name,
            filePath = item.filePath,
            createdAt = item.createdAt,
            completedAt = item.completedAt,
            errorMessage = item.errorMessage,
        )
    }
}
