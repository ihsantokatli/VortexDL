package com.vortexdl.app.presentation.browser

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

const val IPHONE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

/**
 * Compose host for the sniffing WebView.
 *
 * The WebView is created by [AndroidView] and destroyed by [AndroidView.onRelease] only.
 * Nothing else may destroy it: the host composable can be recomposed (or an effect can be
 * restarted) at any time while the view stays attached, and destroying the view in an effect
 * used to leave the wrapper without a child, i.e. a permanently blank page.
 *
 * The WebView is recreated (with the current page reloaded) if Android kills its render
 * process, which otherwise terminates the whole app.
 */
@Composable
fun VideoSnifferWebView(
    viewModel: BrowserViewModel,
    isTabActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var generation by rememberSaveable { mutableIntStateOf(0) }

    key(generation) {
        WebViewHost(
            viewModel = viewModel,
            isTabActive = isTabActive,
            modifier = modifier,
            onRendererProcessGone = { generation++ },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewHost(
    viewModel: BrowserViewModel,
    isTabActive: Boolean,
    modifier: Modifier,
    onRendererProcessGone: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    // Tracks the view owned by the AndroidView node so the command collector and the
    // lifecycle callbacks can reach it. It is never used as an effect key.
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                configureForBrowsing(
                    callbacks = viewModel,
                    onRendererProcessGone = onRendererProcessGone,
                )
                setBackgroundColor(backgroundColor)

                val startUrl = viewModel.uiState.value.currentUrl
                    .takeIf(BrowserUrl::isValidWebUrl)
                    ?: viewModel.initialUrl()
                if (startUrl.contains("youtube.com", ignoreCase = true) ||
                    startUrl.contains("youtu.be", ignoreCase = true) ||
                    startUrl.contains("google.com", ignoreCase = true) ||
                    startUrl.contains(".google.com", ignoreCase = true)
                ) {
                    settings.userAgentString = IPHONE_USER_AGENT
                }
                loadUrl(startUrl)

                webView = this
            }
        },
        update = { view ->
            view.setBackgroundColor(backgroundColor)
            view.visibility = if (isTabActive) View.VISIBLE else View.GONE
        },
        onRelease = { released ->
            if (webView === released) {
                webView = null
            }
            released.stopLoading()
            (released.parent as? ViewGroup)?.removeView(released)
            released.destroy()
        },
    )

    LaunchedEffect(webView) {
        val currentWebView = webView ?: return@LaunchedEffect
        viewModel.commands.collect { command ->
            when (command) {
                is BrowserCommand.LoadUrl -> currentWebView.loadUrl(command.url)
                BrowserCommand.GoBack -> if (currentWebView.canGoBack()) currentWebView.goBack()
                BrowserCommand.GoForward -> if (currentWebView.canGoForward()) currentWebView.goForward()
                BrowserCommand.Reload -> currentWebView.reload()
                BrowserCommand.StopLoading -> currentWebView.stopLoading()
            }
        }
    }

    // Pausing keeps timers, media and JS work suspended while the app is in the background
    // without touching the WebView's attachment or state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView?.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView?.onPause() }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun WebView.configureForBrowsing(
    callbacks: BrowserWebViewCallbacks,
    onRendererProcessGone: () -> Unit,
) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        loadsImagesAutomatically = true
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        setGeolocationEnabled(false)
        cacheMode = WebSettings.LOAD_DEFAULT
        textZoom = 100
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(this, true)

    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

    val rawUserAgent = settings.userAgentString
        ?: System.getProperty("http.agent").orEmpty()
    val cleanUserAgent = rawUserAgent
        .replace("; wv", "")
        .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
    settings.userAgentString = cleanUserAgent

    webViewClient = VortexDLWebViewClient(
        defaultUserAgent = cleanUserAgent,
        callbacks = callbacks,
        onRendererProcessGone = onRendererProcessGone,
    )
    webChromeClient = VortexDLWebChromeClient(callbacks)
}
