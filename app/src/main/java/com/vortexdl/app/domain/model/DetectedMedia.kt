package com.vortexdl.app.domain.model

/**
 * A media resource that was observed inside the in-app browser.
 *
 * The HTTP headers captured here are the ones that were attached to the original
 * WebView request. They must be replayed by the download engine, otherwise most
 * video CDNs will reject the request with a 403.
 */
data class DetectedMedia(
    val id: String,
    val url: String,
    val title: String,
    val mimeType: String,
    val extension: String,
    val requestHeaders: Map<String, String>,
    val timestamp: Long,
) {
    /** True for manifest-based streaming formats that need segment resolving before download. */
    val isStreamingFormat: Boolean
        get() = extension.equals("m3u8", ignoreCase = true) ||
            extension.equals("mpd", ignoreCase = true)
}
