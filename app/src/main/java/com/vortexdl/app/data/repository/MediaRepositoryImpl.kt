package com.vortexdl.app.data.repository

import com.vortexdl.app.data.network.MediaUrlDetector
import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.domain.repository.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe in-memory store for detected media.
 *
 * WebView invokes `shouldInterceptRequest` on a background thread while the UI reads the
 * state on the main thread. Captures are serialised with a short lock and published as an
 * immutable list through [StateFlow], which is safe for cross-thread observation.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val detector: MediaUrlDetector,
) : MediaRepository {

    private val lock = Any()
    private val capturedUrls = HashSet<String>()
    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())

    override val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia.asStateFlow()

    override fun captureMedia(
        url: String,
        requestHeaders: Map<String, String>,
        pageUrl: String?,
        pageTitle: String?,
        defaultUserAgent: String?,
        pageCookies: String?,
    ): DetectedMedia? {
        val media = detector.detect(
            url = url,
            requestHeaders = requestHeaders,
            pageUrl = pageUrl,
            pageTitle = pageTitle,
            defaultUserAgent = defaultUserAgent,
            pageCookies = pageCookies,
        ) ?: return null

        synchronized(lock) {
            if (!capturedUrls.add(media.url.substringBefore('#'))) {
                return null
            }

            val updated = ArrayList<DetectedMedia>(_detectedMedia.value.size + 1)
            updated.add(media)
            updated.addAll(_detectedMedia.value)

            if (updated.size > MAX_CAPTURED_ITEMS) {
                for (index in MAX_CAPTURED_ITEMS until updated.size) {
                    capturedUrls.remove(updated[index].url.substringBefore('#'))
                }
                updated.subList(MAX_CAPTURED_ITEMS, updated.size).clear()
            }

            _detectedMedia.value = updated
        }

        return media
    }

    override fun clear() {
        synchronized(lock) {
            capturedUrls.clear()
            _detectedMedia.value = emptyList()
        }
    }

    private companion object {
        const val MAX_CAPTURED_ITEMS = 200
    }
}
