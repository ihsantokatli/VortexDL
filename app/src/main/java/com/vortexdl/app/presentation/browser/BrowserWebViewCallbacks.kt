package com.vortexdl.app.presentation.browser

/**
 * Callbacks from the WebView clients into the ViewModel.
 *
 * `onResourceRequest` is invoked from a WebView worker thread. Implementations must not
 * touch the WebView or any UI object there.
 */
interface BrowserWebViewCallbacks {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String)
    fun onUrlChanged(url: String)
    fun onProgressChanged(progress: Int)
    fun onTitleChanged(title: String)
    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean)
    fun onPageLoadError()
    fun onResourceRequest(
        url: String,
        requestHeaders: Map<String, String>,
        pageUrl: String?,
        pageTitle: String?,
        defaultUserAgent: String?,
        pageCookies: String?,
    )
}
