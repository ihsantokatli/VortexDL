package com.vortexdl.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vortexdl.app.MainActivity
import com.vortexdl.app.R
import com.vortexdl.app.data.local.DownloadDao
import com.vortexdl.app.data.local.DownloadEntity
import com.vortexdl.app.data.network.DownloadResult
import com.vortexdl.app.data.network.FileDownloader
import com.vortexdl.app.domain.model.DownloadItem
import com.vortexdl.app.domain.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    @Inject
    lateinit var downloadDao: DownloadDao

    @Inject
    lateinit var fileDownloader: FileDownloader

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)

        when (action) {
            ACTION_START -> checkQueueAndStart()
            ACTION_PAUSE -> downloadId?.let(::pauseTask)
            ACTION_CANCEL -> downloadId?.let(::cancelTask)
        }

        return START_NOT_STICKY
    }

    private fun checkQueueAndStart() {
        serviceScope.launch {
            val pendingDownloads = downloadDao.getDownloadsByStatus(DownloadStatus.QUEUED.name)

            if (pendingDownloads.isEmpty() && activeJobs.isEmpty()) {
                stopForegroundService()
                return@launch
            }

            // Ensure foreground notification is active
            startForegroundNotification(
                title = "VortexDL",
                body = "Processing downloads...",
                progress = 0,
                speed = 0L,
            )

            val availableSlots = MAX_CONCURRENT_DOWNLOADS - activeJobs.size
            if (availableSlots > 0) {
                pendingDownloads.take(availableSlots).forEach { entity ->
                    startDownloadTask(entity)
                }
            }
        }
    }

    private fun startDownloadTask(entity: DownloadEntity) {
        if (activeJobs.containsKey(entity.id)) return
        pausedIds.remove(entity.id)

        val job = serviceScope.launch {
            downloadDao.updateStatus(entity.id, DownloadStatus.DOWNLOADING.name)

            val downloadsDir = File(
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir,
                "VortexDL",
            ).apply { mkdirs() }

            val fileName = FileDownloader.sanitizeFileName(entity.title, entity.extension)
            val targetFile = File(downloadsDir, fileName)

            val result = fileDownloader.download(
                url = entity.url,
                headers = entity.requestHeaders,
                targetFile = targetFile,
                onProgress = { downloaded, total, speed ->
                    downloadDao.updateProgress(
                        id = entity.id,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speed = speed,
                    )

                    val progressPercent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    updateNotification(
                        title = entity.title,
                        body = "${DownloadItem.formatBytes(downloaded)} / ${if (total > 0) DownloadItem.formatBytes(total) else "?"}",
                        progress = progressPercent,
                        speed = speed,
                        downloadId = entity.id,
                    )
                },
                isCancelled = { pausedIds.contains(entity.id) },
            )

            activeJobs.remove(entity.id)

            when (result) {
                is DownloadResult.Success -> {
                    downloadDao.updateStatus(
                        id = entity.id,
                        status = DownloadStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        filePath = targetFile.absolutePath,
                    )
                }
                is DownloadResult.Paused -> {
                    downloadDao.updateStatus(
                        id = entity.id,
                        status = DownloadStatus.PAUSED.name,
                        filePath = targetFile.absolutePath,
                    )
                }
                is DownloadResult.Error -> {
                    downloadDao.updateStatus(
                        id = entity.id,
                        status = DownloadStatus.FAILED.name,
                        errorMessage = result.throwable.message ?: "Download failed",
                        filePath = targetFile.absolutePath,
                    )
                }
            }

            checkQueueAndStart()
        }

        activeJobs[entity.id] = job
    }

    private fun pauseTask(downloadId: String) {
        pausedIds.add(downloadId)
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        serviceScope.launch {
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
            checkQueueAndStart()
        }
    }

    private fun cancelTask(downloadId: String) {
        pausedIds.add(downloadId)
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        serviceScope.launch {
            val entity = downloadDao.getDownloadById(downloadId)
            entity?.filePath?.let { path ->
                File(path).delete()
            }
            downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED.name)
            checkQueueAndStart()
        }
    }

    private fun startForegroundNotification(
        title: String,
        body: String,
        progress: Int,
        speed: Long,
        downloadId: String? = null,
    ) {
        val notification = buildNotification(title, body, progress, speed, downloadId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        title: String,
        body: String,
        progress: Int,
        speed: Long,
        downloadId: String?,
    ) {
        val notification = buildNotification(title, body, progress, speed, downloadId)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        body: String,
        progress: Int,
        speed: Long,
        downloadId: String?,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val speedText = if (speed > 0) " · ${DownloadItem.formatBytes(speed)}/s" else ""
        val statusText = if (progress in 1..99) "$progress%$speedText" else body

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 0..100) {
            builder.setProgress(100, progress, progress <= 0)
        }

        if (downloadId != null) {
            val pauseIntent = PendingIntent.getService(
                this,
                downloadId.hashCode(),
                Intent(this, DownloadForegroundService::class.java).apply {
                    action = ACTION_PAUSE
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Pause", pauseIntent)

            val cancelIntent = PendingIntent.getService(
                this,
                downloadId.hashCode() + 1,
                Intent(this, DownloadForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Cancel", cancelIntent)
        }

        return builder.build()
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows real-time progress and status of active downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "vortexdl_downloads"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.vortexdl.app.action.START"
        const val ACTION_PAUSE = "com.vortexdl.app.action.PAUSE"
        const val ACTION_CANCEL = "com.vortexdl.app.action.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        private const val MAX_CONCURRENT_DOWNLOADS = 2

        fun startDownloadService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
}
