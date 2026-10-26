package com.vortexdl.app.presentation.browser

import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vortexdl.app.data.network.AdBlockList
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * WebView client that sniffs media requests and blocks ads/popups.
 *
 * Threading contract:
 * - [shouldInterceptRequest] may run on a WebView worker thread. It must not call methods
 *   on [WebView] or touch UI state; it only forwards an immutable snapshot to the
 *   callbacks, which hand it to a synchronised repository.
 * - Every other override here runs on the UI thread.
 */
class VortexDLWebViewClient(
    private val defaultUserAgent: String,
    private val callbacks: BrowserWebViewCallbacks,
    private val onRendererProcessGone: () -> Unit,
) : WebViewClient() {

    @Volatile
    private var currentPageUrl: String? = null

    @Volatile
    private var currentPageTitle: String = ""

    @Volatile
    private var currentPageCookies: String? = null

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        if (isWebUrl(url)) {
            updateDomainUserAgent(view, url)
            currentPageUrl = url
            currentPageTitle = view.title.orEmpty()
            currentPageCookies = readCookies(url)
            callbacks.onPageStarted(url)
        }
        callbacks.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        super.onPageCommitVisible(view, url)
        if (isWebUrl(url)) {
            updateDomainUserAgent(view, url)
            currentPageUrl = url
            currentPageCookies = readCookies(url)
            if (isYouTubeWatchUrl(url)) {
                probeYouTubeStreams(view, url)
            }
        }
        callbacks.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (isWebUrl(url)) {
            updateDomainUserAgent(view, url)
            currentPageUrl = url
            currentPageTitle = view.title.orEmpty()
            currentPageCookies = readCookies(url)
            if (isYouTubeWatchUrl(url)) {
                probeYouTubeStreams(view, url)
                scheduleDelayedProbe(view, url)
            }
        }
        callbacks.onPageFinished(url, view.title.orEmpty())
        callbacks.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }


    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        if (isWebUrl(url)) {
            updateDomainUserAgent(view, url)
            currentPageUrl = url
            currentPageCookies = readCookies(url)
            callbacks.onUrlChanged(url)
            if (isYouTubeWatchUrl(url)) {
                probeYouTubeStreams(view, url)
                scheduleDelayedProbe(view, url)
            }
        }
        callbacks.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase(Locale.US) ?: return true
        return when (scheme) {
            "http", "https", "about" -> false
            // Popups and custom schemes are never opened implicitly. Nothing is launched
            // externally without an explicit user action in the app UI.
            else -> true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase(Locale.US) }.getOrNull() ?: return true
        return when (scheme) {
            "http", "https", "about" -> false
            else -> true
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        // Never intercept or block the main frame navigation
        if (request.isForMainFrame) {
            return null
        }

        if (AdBlockList.isBlocked(url)) {
            return blockedResponse()
        }

        // Copy the map because the WebView owns the original instance. The callbacks
        // hand this immutable snapshot to a thread-safe repository.
        val headers = runCatching {
            LinkedHashMap(request.requestHeaders.orEmpty())
        }.getOrDefault(emptyMap())

        val effectiveDefaultUa = if (isYouTubeDomain(currentPageUrl.orEmpty()) || isGoogleDomain(currentPageUrl.orEmpty())) {
            IPHONE_USER_AGENT
        } else {
            defaultUserAgent
        }

        runCatching {
            callbacks.onResourceRequest(
                url = url,
                requestHeaders = headers,
                pageUrl = currentPageUrl,
                pageTitle = currentPageTitle,
                defaultUserAgent = effectiveDefaultUa,
                pageCookies = currentPageCookies,
            )
        }

        return null
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            callbacks.onPageLoadError()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        callbacks.onPageLoadError()
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) {
            callbacks.onPageLoadError()
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        callbacks.onPageLoadError()
        handler.cancel()
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        callbacks.onPageLoadError()
        onRendererProcessGone()
        return true
    }

    private fun updateDomainUserAgent(view: WebView, url: String) {
        val isYouTubeOrGoogle = isYouTubeDomain(url) || isGoogleDomain(url)
        val targetUa = if (isYouTubeOrGoogle) IPHONE_USER_AGENT else defaultUserAgent
        if (view.settings.userAgentString != targetUa) {
            view.settings.userAgentString = targetUa
        }
    }

    // Helper to detect Google domains where consent block may occur.
    private fun isGoogleDomain(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.US) }.getOrNull() ?: return false
        return host == "google.com" || host.endsWith(".google.com")
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    private fun isYouTubeDomain(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.US) }.getOrNull() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    }

    private fun isYouTubeWatchUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("youtube.com/watch", ignoreCase = true) ||
            url.contains("youtu.be/", ignoreCase = true) ||
            url.contains("youtube.com/shorts/", ignoreCase = true) ||
            url.contains("youtube.com/embed/", ignoreCase = true)
    }

    private fun scheduleDelayedProbe(view: WebView, pageUrl: String) {
        view.postDelayed({
            if (isYouTubeWatchUrl(currentPageUrl)) {
                probeYouTubeStreams(view, currentPageUrl.orEmpty())
            }
        }, 10000)
    }

    private fun readCookies(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        200,
        "OK",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

    private fun probeYouTubeStreams(view: WebView, pageUrl: String) {
        val script = """
            (function() {
                try {
                    var result = [];
                    var title = "";

                    var pr = window.ytInitialPlayerResponse;
                    if (!pr && window.ytplayer && window.ytplayer.config && window.ytplayer.config.args) {
                        try { pr = JSON.parse(window.ytplayer.config.args.player_response); } catch(e){}
                    }
                    if (pr) {
                        title = (pr.videoDetails && pr.videoDetails.title) || "";
                        if (pr.streamingData) {
                            if (pr.streamingData.hlsManifestUrl) {
                                result.push({
                                    url: pr.streamingData.hlsManifestUrl,
                                    itag: null,
                                    quality: "Auto / Multi-Quality",
                                    mime: "application/vnd.apple.mpegurl"
                                });
                            }
                            var formats = pr.streamingData.formats || [];
                            for (var i = 0; i < formats.length; i++) {
                                var f = formats[i];
                                if (f && f.url) {
                                    result.push({
                                        url: f.url,
                                        itag: f.itag,
                                        quality: f.qualityLabel || (f.height ? f.height + 'p' : '360p'),
                                        mime: f.mimeType
                                    });
                                }
                            }
                        }
                    }

                    var video = document.querySelector('video');
                    if (video && video.src && video.src.indexOf('http') === 0) {
                        result.push({
                            url: video.src,
                            itag: null,
                            quality: null,
                            mime: null
                        });
                    }

                    if (!title) {
                        title = document.title || "";
                    }
                    return JSON.stringify({ title: title, formats: result });
                } catch(e) {
                    return "";
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(script) { result ->
            if (result.isNullOrBlank() || result == "\"\"" || result == "null") return@evaluateJavascript
            runCatching {
                val tokener = org.json.JSONTokener(result).nextValue()
                val rawJson = if (tokener is String) tokener else result
                val obj = org.json.JSONObject(rawJson)
                val title = obj.optString("title")
                val formats = obj.optJSONArray("formats") ?: return@evaluateJavascript
                for (i in 0 until formats.length()) {
                    val item = formats.getJSONObject(i)
                    val streamUrl = item.optString("url")
                    if (streamUrl.isNotBlank()) {
                        callbacks.onResourceRequest(
                            url = streamUrl,
                            requestHeaders = emptyMap(),
                            pageUrl = pageUrl,
                            pageTitle = title.ifBlank { currentPageTitle },
                            defaultUserAgent = IPHONE_USER_AGENT,
                            pageCookies = currentPageCookies,
                        )
                    }
                }
            }
        }
    }
}
