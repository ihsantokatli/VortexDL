package com.vortexdl.app.data.repository

import android.content.Context
import com.vortexdl.app.data.local.DownloadDao
import com.vortexdl.app.data.local.DownloadEntity
import com.vortexdl.app.data.network.hls.HlsDownloader
import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.domain.model.DownloadItem
import com.vortexdl.app.domain.model.DownloadStatus
import com.vortexdl.app.domain.model.VideoQualityOption
import com.vortexdl.app.domain.repository.DownloadRepository
import com.vortexdl.app.service.DownloadForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val hlsDownloader: HlsDownloader,
) : DownloadRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val allDownloads: StateFlow<List<DownloadItem>> = downloadDao.getAllDownloads()
        .map { entities -> entities.map { it.toDomainModel() } }
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    override fun getDownload(id: String): Flow<DownloadItem?> =
        downloadDao.getDownloadByIdFlow(id).map { it?.toDomainModel() }

    override suspend fun enqueueDownload(
        media: DetectedMedia,
        selectedResolution: String?,
        selectedUrl: String?,
    ): String = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()
        val downloadUrl = selectedUrl ?: media.url
        val resolution = selectedResolution ?: media.resolution

        val entity = DownloadEntity(
            id = downloadId,
            url = downloadUrl,
            title = media.title,
            mimeType = media.mimeType,
            extension = media.extension,
            requestHeaders = media.requestHeaders,
            totalBytes = -1L,
            downloadedBytes = 0L,
            speedBytesPerSecond = 0L,
            status = DownloadStatus.QUEUED.name,
            filePath = null,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
            errorMessage = null,
            resolution = resolution,
        )

        downloadDao.insert(entity)
        DownloadForegroundService.startDownloadService(context)
        downloadId
    }

    override suspend fun fetchHlsQualityOptions(media: DetectedMedia): List<VideoQualityOption> =
        withContext(Dispatchers.IO) {
            hlsDownloader.getStreamQualities(media.url, media.requestHeaders)
        }

    override suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        DownloadForegroundService.pauseDownload(context, id)
    }

    override suspend fun resumeDownload(id: String) = withContext(Dispatchers.IO) {
        downloadDao.updateStatus(id = id, status = DownloadStatus.QUEUED.name, errorMessage = null)
        DownloadForegroundService.startDownloadService(context)
    }

    override suspend fun cancelDownload(id: String) = withContext(Dispatchers.IO) {
        DownloadForegroundService.cancelDownload(context, id)
    }

    override suspend fun retryDownload(id: String) = withContext(Dispatchers.IO) {
        downloadDao.updateStatus(id = id, status = DownloadStatus.QUEUED.name, errorMessage = null)
        DownloadForegroundService.startDownloadService(context)
    }

    override suspend fun deleteDownload(id: String, removeFile: Boolean) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getDownloadById(id)
        if (removeFile && entity?.filePath != null) {
            runCatching { File(entity.filePath).delete() }
        }
        runCatching { File(context.cacheDir, "hls_$id").deleteRecursively() }
        downloadDao.deleteById(id)
    }
}
