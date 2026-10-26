package com.vortexdl.app.domain.repository

import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.domain.model.DownloadItem
import com.vortexdl.app.domain.model.VideoQualityOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing downloads, observing progress, and dispatching download lifecycle commands.
 */
interface DownloadRepository {

    val allDownloads: StateFlow<List<DownloadItem>>

    fun getDownload(id: String): Flow<DownloadItem?>

    suspend fun enqueueDownload(
        media: DetectedMedia,
        selectedResolution: String? = null,
        selectedUrl: String? = null,
    ): String

    suspend fun fetchHlsQualityOptions(media: DetectedMedia): List<VideoQualityOption>

    suspend fun pauseDownload(id: String)

    suspend fun resumeDownload(id: String)

    suspend fun cancelDownload(id: String)

    suspend fun retryDownload(id: String)

    suspend fun deleteDownload(id: String, removeFile: Boolean)
}
