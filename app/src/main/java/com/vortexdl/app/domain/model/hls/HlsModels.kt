package com.vortexdl.app.domain.model.hls

data class HlsVariant(
    val bandwidth: Long,
    val resolution: String?,
    val codecs: String?,
    val url: String,
)

data class HlsSegment(
    val index: Int,
    val durationSeconds: Double,
    val title: String?,
    val url: String,
    val byteRange: String? = null,
)

data class HlsMasterPlaylist(
    val variants: List<HlsVariant>,
) {
    /**
     * Returns the variant with the highest bandwidth, or null if no variants exist.
     */
    fun highestQualityVariant(): HlsVariant? = variants.maxByOrNull { it.bandwidth }
}

data class HlsMediaPlaylist(
    val targetDurationSeconds: Double,
    val mediaSequence: Long,
    val segments: List<HlsSegment>,
    val isEndList: Boolean,
) {
    val totalDurationSeconds: Double
        get() = segments.sumOf { it.durationSeconds }
}

sealed interface HlsPlaylist {
    data class Master(val playlist: HlsMasterPlaylist) : HlsPlaylist
    data class Media(val playlist: HlsMediaPlaylist) : HlsPlaylist
}
