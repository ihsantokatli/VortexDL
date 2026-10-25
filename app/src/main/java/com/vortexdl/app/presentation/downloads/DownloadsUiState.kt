package com.vortexdl.app.presentation.downloads

import com.vortexdl.app.domain.model.DownloadItem
import com.vortexdl.app.domain.model.DownloadStatus

enum class DownloadsFilterTab {
    ALL,
    ACTIVE,
    COMPLETED,
}

data class DownloadsUiState(
    val selectedTab: DownloadsFilterTab = DownloadsFilterTab.ALL,
    val allDownloads: List<DownloadItem> = emptyList(),
) {
    val displayedDownloads: List<DownloadItem>
        get() = when (selectedTab) {
            DownloadsFilterTab.ALL -> allDownloads
            DownloadsFilterTab.ACTIVE -> allDownloads.filter {
                it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.PAUSED
            }
            DownloadsFilterTab.COMPLETED -> allDownloads.filter {
                it.status == DownloadStatus.COMPLETED
            }
        }

    val activeCount: Int
        get() = allDownloads.count { it.status.isActive }

    val completedCount: Int
        get() = allDownloads.count { it.status == DownloadStatus.COMPLETED }
}
