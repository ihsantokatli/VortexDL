package com.vortexdl.app.domain.repository

import com.vortexdl.app.domain.model.DetectedMedia
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for media captured by the WebView sniffer.
 *
 * Implementations are called from the WebView's background request thread, so every
 * method must be safe to invoke concurrently.
 */
interface MediaRepository {

    val detectedMedia: StateFlow<List<DetectedMedia>>

    /**
     * Tries to recognise [url] as a downloadable media resource and stores it.
     * Returns the newly stored item, or `null` when the URL is not media or was
     * already captured before.
     */
    fun captureMedia(
        url: String,
        requestHeaders: Map<String, String>,
        pageUrl: String?,
        pageTitle: String?,
        defaultUserAgent: String?,
        pageCookies: String?,
    ): DetectedMedia?

    /** Removes every captured item and resets duplicate detection. */
    fun clear()
}
