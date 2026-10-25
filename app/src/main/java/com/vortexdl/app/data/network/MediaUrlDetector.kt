package com.vortexdl.app.data.network

import com.vortexdl.app.domain.model.DetectedMedia
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

        val path = pathOf(url) ?: return null
        val extension = supportedExtension(path) ?: return null
        val mimeType = MIME_TYPES.getValue(extension)
        val host = safeUri(url)?.host

        return DetectedMedia(
            id = UUID.randomUUID().toString(),
            url = url,
            title = buildTitle(path, pageTitle, host),
            mimeType = mimeType,
            extension = extension,
            requestHeaders = buildHeaders(
                requestHeaders = requestHeaders,
                mediaUrl = url,
                pageUrl = pageUrl,
                pageCookies = pageCookies,
                defaultUserAgent = defaultUserAgent,
            ),
            timestamp = System.currentTimeMillis(),
        )
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
            ?.takeIf { it.isNotEmpty() && !it.startsWith("http", ignoreCase = true) && it.length <= 120 }

        return when {
            fileBase.isNotBlank() &&
                fileBase.length <= 100 &&
                fileBase.lowercase(Locale.US) !in GENERIC_FILE_NAMES -> fileBase
            cleanPageTitle != null -> cleanPageTitle
            !host.isNullOrBlank() -> host
            else -> DEFAULT_TITLE
        }
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

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"
        const val ORIGIN = "Origin"
        const val COOKIE = "Cookie"
        const val DEFAULT_TITLE = "Video"

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
        )
    }
}
