package com.vortexdl.app.domain.model

/**
 * A selectable stream quality option for multi-variant media (such as HLS master playlists).
 */
data class VideoQualityOption(
    val resolution: String,
    val description: String,
    val bandwidth: Long,
    val formattedBandwidth: String,
    val url: String,
)
