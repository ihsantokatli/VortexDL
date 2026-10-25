package com.vortexdl.app.presentation.browser

import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
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
            currentPageUrl = url
            currentPageTitle = view.title.orEmpty()
            currentPageCookies = readCookies(url)
            callbacks.onPageStarted(url)
        }
        callbacks.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (isWebUrl(url)) {
            currentPageUrl = url
            currentPageTitle = view.title.orEmpty()
            currentPageCookies = readCookies(url)
        }
        callbacks.onPageFinished(url, view.title.orEmpty())
        callbacks.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        if (isWebUrl(url)) {
            currentPageUrl = url
            currentPageCookies = readCookies(url)
            callbacks.onUrlChanged(url)
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

        if (AdBlockList.isBlocked(url)) {
            return blockedResponse()
        }

        // Copy the map because the WebView owns the original instance. The callbacks
        // hand this immutable snapshot to a thread-safe repository.
        val headers = runCatching {
            LinkedHashMap(request.requestHeaders.orEmpty())
        }.getOrDefault(emptyMap())

        runCatching {
            callbacks.onResourceRequest(
                url = url,
                requestHeaders = headers,
                pageUrl = currentPageUrl,
                pageTitle = currentPageTitle,
                defaultUserAgent = defaultUserAgent,
                pageCookies = currentPageCookies,
            )
        }

        return null
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        if (request.isForMainFrame) {
            callbacks.onPageLoadError()
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        callbacks.onPageLoadError()
        onRendererProcessGone()
        return true
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    private fun readCookies(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Blocked by VortexDL",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )
}
