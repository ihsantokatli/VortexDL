package com.vortexdl.app.data.network

import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.domain.model.ResolutionHelper
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-JVM media URL classifier.
 *
 * It intentionally has no Android dependencies: `shouldInterceptRequest` runs on a
 * WebView worker thread, so the detector is executed there and must not touch WebView
 * or UI objects.
 */
@Singleton
class MediaUrlDetector @Inject constructor() {

    fun detect(
        url: String,
        requestHeaders: Map<String, String>,
        pageUrl: String?,
        pageTitle: String?,
        defaultUserAgent: String?,
        pageCookies: String?,
    ): DetectedMedia? {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        val host = safeUri(url)?.host
        val path = pathOf(url) ?: return null

        val isPlayback = path.contains("videoplayback")
        val classification = if (isPlayback) {
            classifyGoogleVideo(url, host, pageUrl)
        } else {
            classifyQueryMime(url) ?: classifyPathExtension(url, path)
        } ?: return null

        val title = buildTitle(path, pageTitle, host)
        val resolution = classification.resolution
            ?: ResolutionHelper.extractFromUrlOrTitle(classification.canonicalUrl, title)

        return DetectedMedia(
            id = UUID.randomUUID().toString(),
            url = classification.canonicalUrl,
            title = title,
            mimeType = classification.mimeType,
            extension = classification.extension,
            requestHeaders = buildHeaders(
                requestHeaders = requestHeaders,
                mediaUrl = classification.canonicalUrl,
                pageUrl = pageUrl,
                pageCookies = pageCookies,
                defaultUserAgent = defaultUserAgent,
            ),
            timestamp = System.currentTimeMillis(),
            resolution = resolution,
        )
    }

    private fun classifyGoogleVideo(
        url: String,
        host: String?,
        pageUrl: String?,
    ): MediaClassification? {
        if (host == null || (!host.equals("googlevideo.com", ignoreCase = true) && !host.endsWith(".googlevideo.com", ignoreCase = true))) {
            return null
        }
        if (!url.contains("/videoplayback")) {
            return null
        }

        // 1. If pageUrl is on YouTube, ensure user is actually on a watch page.
        // Pings/preconnects on home page, search, feed, etc. are eliminated.
        if (isYouTubeDomain(pageUrl) && !isYouTubeWatchUrl(pageUrl)) {
            return null
        }

        val query = url.substringAfter('?', "")
        val queryParams = query.split('&').associate {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else it to ""
        }

        // 2. Reject tiny telemetry/preconnect pings (< 100KB with missing or < 3.0s duration)
        val clen = queryParams["clen"]?.toLongOrNull()
        val dur = queryParams["dur"]?.toDoubleOrNull()
        if (clen != null && clen < 100_000L && (dur == null || dur < 3.0)) {
            return null
        }

        val itag = queryParams["itag"]?.toIntOrNull()

        // 3. Google video streams must have an itag
        if (itag == null || (clen != null && clen == 0L)) {
            return null
        }

        val canonicalUrl = cleanGoogleVideoUrl(url)

        val rawMime = queryParams["mime"]
        val decodedMime = rawMime?.let { runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull() }

        val (ext, mime) = when {
            decodedMime?.contains("video/mp4", ignoreCase = true) == true -> "mp4" to "video/mp4"
            decodedMime?.contains("video/webm", ignoreCase = true) == true -> "webm" to "video/webm"
            decodedMime?.contains("audio/mp4", ignoreCase = true) == true -> "m4a" to "audio/mp4"
            decodedMime?.contains("audio/webm", ignoreCase = true) == true -> "webm" to "audio/webm"
            itag in WEBM_ITAGS -> "webm" to (if (itag in AUDIO_ITAGS) "audio/webm" else "video/webm")
            itag in AUDIO_ITAGS -> "m4a" to "audio/mp4"
            else -> "mp4" to "video/mp4"
        }

        val resolution = resolutionFromItag(itag)

        return MediaClassification(
            canonicalUrl = canonicalUrl,
            extension = ext,
            mimeType = mime,
            resolution = resolution,
        )
    }

    private fun cleanGoogleVideoUrl(url: String): String {
        val base = url.substringBefore('?')
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return url

        val sparams = query.split('&')
            .firstOrNull { it.startsWith("sparams=") }
            ?.substringAfter("sparams=")
            ?.split(',')
            ?: emptyList()

        val sparamsIncludeRange = sparams.contains("range")

        val cleanedParams = query.split('&').filterNot { param ->
            (!sparamsIncludeRange && param.startsWith("range=")) ||
                param.startsWith("rn=") ||
                param.startsWith("rbuf=")
        }

        return if (cleanedParams.isEmpty()) base else "$base?${cleanedParams.joinToString("&")}"
    }

    private fun classifyQueryMime(url: String): MediaClassification? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null

        val rawMime = query.split('&').firstOrNull { it.startsWith("mime=") }?.substringAfter("mime=")
            ?: query.split('&').firstOrNull { it.startsWith("type=") }?.substringAfter("type=")
        val decoded = rawMime?.let { runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull() }?.lowercase(Locale.US)
            ?: return null

        return when {
            decoded.contains("video/mp4") -> MediaClassification(url, "mp4", "video/mp4")
            decoded.contains("video/webm") -> MediaClassification(url, "webm", "video/webm")
            decoded.contains("application/vnd.apple.mpegurl") || decoded.contains("x-mpegurl") -> MediaClassification(url, "m3u8", "application/vnd.apple.mpegurl")
            decoded.contains("application/dash+xml") -> MediaClassification(url, "mpd", "application/dash+xml")
            decoded.contains("audio/mp4") || decoded.contains("audio/m4a") -> MediaClassification(url, "m4a", "audio/mp4")
            else -> null
        }
    }

    private fun classifyPathExtension(url: String, path: String): MediaClassification? {
        val ext = supportedExtension(path) ?: return null
        return MediaClassification(
            canonicalUrl = url,
            extension = ext,
            mimeType = MIME_TYPES.getValue(ext),
        )
    }

    private fun resolutionFromItag(itag: Int?): String? = when (itag) {
        313 -> "4K"
        271 -> "1440p"
        137, 248 -> "1080p"
        22, 136, 247 -> "720p"
        135, 244, 245 -> "480p"
        18, 134, 243 -> "360p"
        133, 242 -> "240p"
        160, 278 -> "144p"
        140 -> "Audio (M4A)"
        251 -> "Audio (Opus)"
        else -> null
    }

    private fun supportedExtension(path: String): String? {
        val lastSegment = path.trimEnd('/').substringAfterLast('/')
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == lastSegment.lastIndex) return null
        val extension = lastSegment.substring(dotIndex + 1).lowercase(Locale.US)
        return extension.takeIf { it in MIME_TYPES }
    }

    private fun pathOf(url: String): String? {
        val uri = safeUri(url)
        if (uri != null) return uri.rawPath ?: ""

        // Some media URLs contain characters that java.net.URI rejects. Fall back to
        // plain string slicing so a legal WebView URL is not dropped unnecessarily.
        val withoutFragment = url.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        val schemeEnd = withoutQuery.indexOf("://")
        if (schemeEnd < 0) return null
        val pathStart = withoutQuery.indexOf('/', schemeEnd + 3)
        return if (pathStart >= 0) withoutQuery.substring(pathStart) else ""
    }

    private fun buildHeaders(
        requestHeaders: Map<String, String>,
        mediaUrl: String,
        pageUrl: String?,
        pageCookies: String?,
        defaultUserAgent: String?,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>(requestHeaders.size + 4)
        requestHeaders.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank() &&
                name.lowercase(Locale.US) !in EXCLUDED_REQUEST_HEADERS
            ) {
                headers[name] = value
            }
        }

        if (!headers.containsKeyIgnoreCase(USER_AGENT)) {
            val userAgent = defaultUserAgent
                ?.takeIf { it.isNotBlank() }
                ?: System.getProperty("http.agent")?.takeIf { it.isNotBlank() }
            if (userAgent != null) headers[USER_AGENT] = userAgent
        }

        if (!headers.containsKeyIgnoreCase(REFERER)) {
            pageUrl?.takeIf { it.isNotBlank() }?.let { headers[REFERER] = it }
        }

        if (!headers.containsKeyIgnoreCase(ORIGIN)) {
            originOf(pageUrl)?.let { headers[ORIGIN] = it }
        }

        if (!headers.containsKeyIgnoreCase(COOKIE) &&
            !pageCookies.isNullOrBlank() &&
            isSameHost(mediaUrl, pageUrl)
        ) {
            headers[COOKIE] = pageCookies
        }

        return headers.toMap()
    }

    private fun buildTitle(path: String, pageTitle: String?, host: String?): String {
        val lastSegment = path.trimEnd('/').substringAfterLast('/')
        val decodedName = runCatching {
            URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.name())
        }.getOrDefault(lastSegment)

        val fileBase = decodedName
            .substringBeforeLast('.', decodedName)
            .replace('-', ' ')
            .replace('_', ' ')
            .replace('+', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        val cleanPageTitle = pageTitle
            ?.trim()
            ?.substringBefore(" - YouTube")
            ?.substringBefore(" | YouTube")
            ?.substringBefore(" - YouTube Music")
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    !it.startsWith("http", ignoreCase = true) &&
                    it.length <= 120 &&
                    !it.equals("YouTube", ignoreCase = true) &&
                    !it.equals("Home", ignoreCase = true)
            }

        return when {
            fileBase.isNotBlank() &&
                fileBase.length <= 100 &&
                fileBase.lowercase(Locale.US) !in GENERIC_FILE_NAMES -> fileBase
            cleanPageTitle != null -> cleanPageTitle
            !host.isNullOrBlank() -> host
            else -> DEFAULT_TITLE
        }
    }

    private fun isYouTubeDomain(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = safeUri(url)?.host?.lowercase(Locale.US) ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    }

    private fun isYouTubeWatchUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("youtube.com/watch", ignoreCase = true) ||
            url.contains("youtu.be/", ignoreCase = true) ||
            url.contains("youtube.com/shorts/", ignoreCase = true) ||
            url.contains("youtube.com/embed/", ignoreCase = true)
    }

    private fun originOf(pageUrl: String?): String? {
        val uri = pageUrl?.let(::safeUri) ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    private fun isSameHost(first: String?, second: String?): Boolean {
        val firstHost = first?.let(::safeUri)?.host ?: return false
        val secondHost = second?.let(::safeUri)?.host ?: return false
        return firstHost.equals(secondHost, ignoreCase = true)
    }

    private fun safeUri(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private fun Map<String, String>.containsKeyIgnoreCase(key: String): Boolean =
        keys.any { it.equals(key, ignoreCase = true) }

    private data class MediaClassification(
        val canonicalUrl: String,
        val extension: String,
        val mimeType: String,
        val resolution: String? = null,
    )

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"
        const val ORIGIN = "Origin"
        const val COOKIE = "Cookie"
        const val DEFAULT_TITLE = "Video"

        val WEBM_ITAGS = setOf(248, 247, 244, 245, 243, 242, 278, 313, 271, 251)
        val AUDIO_ITAGS = setOf(140, 251)

        val MIME_TYPES = mapOf(
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "m3u8" to "application/vnd.apple.mpegurl",
            "mpd" to "application/dash+xml",
        )

        /**
         * Headers that belong to a single HTTP connection and must never be replayed
         * by the download client. Everything else is preserved for authenticated CDNs.
         */
        val EXCLUDED_REQUEST_HEADERS = setOf(
            "host",
            "connection",
            "content-length",
            "accept-encoding",
            "transfer-encoding",
            "upgrade",
            "proxy-connection",
            "keep-alive",
            "te",
            "trailer",
        )

        val GENERIC_FILE_NAMES = setOf(
            "index",
            "video",
            "playlist",
            "master",
            "manifest",
            "media",
            "stream",
            "chunklist",
            "file",
            "download",
            "watch",
            "default",
            "videoplayback",
        )
    }
}
