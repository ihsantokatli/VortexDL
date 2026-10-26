package com.vortexdl.app.data.network.hls

import com.vortexdl.app.domain.model.hls.HlsMasterPlaylist
import com.vortexdl.app.domain.model.hls.HlsMediaPlaylist
import com.vortexdl.app.domain.model.hls.HlsPlaylist
import com.vortexdl.app.domain.model.hls.HlsSegment
import com.vortexdl.app.domain.model.hls.HlsVariant
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class HlsPlaylistParser @Inject constructor() {

    fun parse(content: String, baseUrl: String): HlsPlaylist {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (isMasterPlaylist(lines)) {
            return HlsPlaylist.Master(parseMasterPlaylist(lines, baseUrl))
        }

        return HlsPlaylist.Media(parseMediaPlaylist(lines, baseUrl))
    }

    private fun isMasterPlaylist(lines: List<String>): Boolean {
        return lines.any { it.startsWith(TAG_STREAM_INF) }
    }

    private fun parseMasterPlaylist(lines: List<String>, baseUrl: String): HlsMasterPlaylist {
        val variants = mutableListOf<HlsVariant>()
        var pendingStreamInf: String? = null

        for (line in lines) {
            when {
                line.startsWith(TAG_STREAM_INF) -> {
                    pendingStreamInf = line.substringAfter(TAG_STREAM_INF)
                }
                line.startsWith("#") -> {
                    // Ignore other tags / comments while waiting for variant URI
                }
                pendingStreamInf != null -> {
                    val attributes = parseAttributes(pendingStreamInf)
                    val bandwidth = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0L
                    val resolution = attributes["RESOLUTION"]
                    val codecs = attributes["CODECS"]
                    val variantUrl = resolveUrl(baseUrl, line)

                    variants.add(
                        HlsVariant(
                            bandwidth = bandwidth,
                            resolution = resolution,
                            codecs = codecs,
                            url = variantUrl,
                        ),
                    )
                    pendingStreamInf = null
                }
            }
        }

        // Sort variants by bandwidth descending (highest quality first)
        variants.sortByDescending { it.bandwidth }
        return HlsMasterPlaylist(variants = variants)
    }

    private fun parseMediaPlaylist(lines: List<String>, baseUrl: String): HlsMediaPlaylist {
        var targetDuration = 0.0
        var mediaSequence = 0L
        var isEndList = false
        val segments = mutableListOf<HlsSegment>()

        var pendingExtInf: String? = null
        var pendingByteRange: String? = null
        var segmentIndex = 0

        for (line in lines) {
            when {
                line.startsWith(TAG_TARGET_DURATION) -> {
                    targetDuration = line.substringAfter(TAG_TARGET_DURATION).toDoubleOrNull() ?: 0.0
                }
                line.startsWith(TAG_MEDIA_SEQUENCE) -> {
                    mediaSequence = line.substringAfter(TAG_MEDIA_SEQUENCE).toLongOrNull() ?: 0L
                }
                line.startsWith(TAG_ENDLIST) -> {
                    isEndList = true
                }
                line.startsWith(TAG_BYTERANGE) -> {
                    pendingByteRange = line.substringAfter(TAG_BYTERANGE).trim()
                }
                line.startsWith(TAG_EXTINF) -> {
                    pendingExtInf = line.substringAfter(TAG_EXTINF)
                }
                line.startsWith("#") -> {
                    // Ignore other tags / comments
                }
                pendingExtInf != null -> {
                    val (duration, title) = parseExtInf(pendingExtInf)
                    val segmentUrl = resolveUrl(baseUrl, line)

                    segments.add(
                        HlsSegment(
                            index = segmentIndex++,
                            durationSeconds = duration,
                            title = title,
                            url = segmentUrl,
                            byteRange = pendingByteRange,
                        ),
                    )
                    pendingExtInf = null
                    pendingByteRange = null
                }
            }
        }

        return HlsMediaPlaylist(
            targetDurationSeconds = targetDuration,
            mediaSequence = mediaSequence,
            segments = segments,
            isEndList = isEndList,
        )
    }

    private fun parseExtInf(extInfValue: String): Pair<Double, String?> {
        val trimmed = extInfValue.trim()
        val commaIndex = trimmed.indexOf(',')
        val durationStr = if (commaIndex >= 0) trimmed.substring(0, commaIndex) else trimmed
        val titleStr = if (commaIndex >= 0 && commaIndex < trimmed.lastIndex) {
            trimmed.substring(commaIndex + 1).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val duration = durationStr.trim().toDoubleOrNull() ?: 0.0
        return Pair(duration, titleStr)
    }

    fun parseAttributes(attributeString: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("""([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))""")
        for (match in regex.findAll(attributeString)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            attributes[key] = value
        }
        return attributes
    }

    fun resolveUrl(baseUrl: String, uriString: String): String {
        val trimmedUri = uriString.trim()
        if (trimmedUri.startsWith("http://", ignoreCase = true) ||
            trimmedUri.startsWith("https://", ignoreCase = true)
        ) {
            return trimmedUri
        }

        // Protocol-relative URLs: //example.com/segment.ts
        if (trimmedUri.startsWith("//")) {
            val scheme = if (baseUrl.startsWith("http://", ignoreCase = true)) "http:" else "https:"
            return scheme + trimmedUri
        }

        val baseHttpUrl = baseUrl.toHttpUrlOrNull()
        if (baseHttpUrl != null) {
            val resolved = baseHttpUrl.resolve(trimmedUri)
            if (resolved != null) {
                return resolved.toString()
            }
        }

        return runCatching {
            URI.create(baseUrl).resolve(trimmedUri).toString()
        }.getOrDefault(trimmedUri)
    }

    companion object {
        private const val TAG_STREAM_INF = "#EXT-X-STREAM-INF:"
        private const val TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION:"
        private const val TAG_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE:"
        private const val TAG_EXTINF = "#EXTINF:"
        private const val TAG_BYTERANGE = "#EXT-X-BYTERANGE:"
        private const val TAG_ENDLIST = "#EXT-X-ENDLIST"
    }
}
