package com.vortexdl.app.domain.model

import java.util.Locale
import java.util.regex.Pattern

/**
 * Utility for parsing, extracting, and formatting video resolutions and bitrates.
 */
object ResolutionHelper {

    private val QUALITY_REGEX = Pattern.compile(
        "(?:^|[^a-zA-Z0-9])(2160p|1440p|1080p|720p|480p|360p|240p|144p|4k)(?:[^a-zA-Z0-9]|$)",
        Pattern.CASE_INSENSITIVE,
    )

    private val NUMERIC_RES_REGEX = Pattern.compile(
        "(?:^|[^a-zA-Z0-9])(2160|1440|1080|720|480|360|240)(?:[^a-zA-Z0-9]|$)",
        Pattern.CASE_INSENSITIVE,
    )

    /**
     * Maps RFC 8216 resolution string (e.g. "1920x1080") to standard quality label (e.g. "1080p").
     */
    fun formatDimensions(resolution: String?): String? {
        if (resolution.isNullOrBlank()) return null
        val parts = resolution.lowercase(Locale.US).split('x')
        if (parts.size != 2) return null

        val width = parts[0].trim().toIntOrNull() ?: return null
        val height = parts[1].trim().toIntOrNull() ?: return null
        val minDim = minOf(width, height)
        val maxDim = maxOf(width, height)

        return when {
            maxDim >= 3840 || minDim >= 2160 -> "4K"
            maxDim >= 2560 || minDim >= 1440 -> "1440p"
            maxDim >= 1920 || minDim >= 1080 -> "1080p"
            maxDim >= 1280 || minDim >= 720 -> "720p"
            maxDim >= 850 || minDim >= 480 -> "480p"
            maxDim >= 640 || minDim >= 360 -> "360p"
            minDim >= 240 -> "240p"
            minDim >= 144 -> "144p"
            else -> "${minDim}p"
        }
    }

    /**
     * Extracts resolution from URL string or title if available.
     */
    fun extractFromUrlOrTitle(url: String, title: String? = null): String? {
        // 1. Try URL match
        val urlMatcher = QUALITY_REGEX.matcher(url)
        if (urlMatcher.find()) {
            val matched = urlMatcher.group(1)
            if (matched != null) {
                return if (matched.equals("4k", ignoreCase = true)) "4K" else matched.lowercase(Locale.US)
            }
        }

        val urlNumMatcher = NUMERIC_RES_REGEX.matcher(url)
        if (urlNumMatcher.find()) {
            val matched = urlNumMatcher.group(1)
            if (matched != null) {
                return "${matched}p"
            }
        }

        // 2. Try Title match
        if (!title.isNullOrBlank()) {
            val titleMatcher = QUALITY_REGEX.matcher(title)
            if (titleMatcher.find()) {
                val matched = titleMatcher.group(1)
                if (matched != null) {
                    return if (matched.equals("4k", ignoreCase = true)) "4K" else matched.lowercase(Locale.US)
                }
            }
        }

        return null
    }

    /**
     * Formats network bandwidth in bps to human-readable string (e.g. "4.5 Mbps").
     */
    fun formatBandwidth(bandwidth: Long): String {
        if (bandwidth <= 0L) return ""
        return when {
            bandwidth >= 1_000_000L -> String.format(Locale.US, "%.1f Mbps", bandwidth / 1_000_000.0)
            bandwidth >= 1_000L -> String.format(Locale.US, "%d Kbps", bandwidth / 1_000L)
            else -> "$bandwidth bps"
        }
    }

    /**
     * Returns a human-friendly display label (e.g. "1080p (Full HD)").
     */
    fun getQualityDescription(resolution: String): String {
        return when (resolution.uppercase(Locale.US)) {
            "4K", "2160P" -> "4K Ultra HD"
            "1440P" -> "2K Quad HD"
            "1080P" -> "Full HD"
            "720P" -> "HD"
            "480P" -> "SD"
            "360P" -> "Medium"
            "240P", "144P" -> "Low"
            else -> resolution
        }
    }
}
