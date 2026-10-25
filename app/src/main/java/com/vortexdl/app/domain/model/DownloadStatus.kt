package com.vortexdl.app.domain.model

/**
 * State lifecycle of a file download.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING

    val isFinished: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED
}
