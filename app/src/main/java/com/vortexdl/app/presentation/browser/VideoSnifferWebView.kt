package com.vortexdl.app.presentation.browser

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Compose host for the sniffing WebView.
 *
 * The WebView is recreated (with the current page reloaded) if Android kills its render
 * process, which otherwise terminates the whole app.
 */
@Composable
fun VideoSnifferWebView(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
) {
    var generation by rememberSaveable { mutableIntStateOf(0) }

    key(generation) {
        WebViewHost(
            viewModel = viewModel,
            modifier = modifier,
            onRendererProcessGone = { generation++ },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewHost(
    viewModel: BrowserViewModel,
    modifier: Modifier,
    onRendererProcessGone: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                configureForBrowsing(
                    callbacks = viewModel,
                    onRendererProcessGone = onRendererProcessGone,
                )
                setBackgroundColor(backgroundColor)
                webView = this

                val startUrl = viewModel.uiState.value.currentUrl
                    .takeIf(BrowserUrl::isValidWebUrl)
                    ?: viewModel.initialUrl()
                viewModel.onPageStarted(startUrl)
                loadUrl(startUrl)
            }
        },
        update = { view ->
            view.setBackgroundColor(backgroundColor)
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

    DisposableEffect(webView, lifecycleOwner) {
        val currentWebView = webView
        if (currentWebView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> currentWebView.onResume()
                    Lifecycle.Event.ON_PAUSE -> currentWebView.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                currentWebView.stopLoading()
                (currentWebView.parent as? ViewGroup)?.removeView(currentWebView)
                currentWebView.destroy()
                if (webView === currentWebView) {
                    webView = null
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForBrowsing(
    callbacks: BrowserWebViewCallbacks,
    onRendererProcessGone: () -> Unit,
) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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

    setBackgroundColor(AndroidColor.TRANSPARENT)
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

    val defaultUserAgent = settings.userAgentString
        ?: System.getProperty("http.agent").orEmpty()

    webViewClient = VortexDLWebViewClient(
        defaultUserAgent = defaultUserAgent,
        callbacks = callbacks,
        onRendererProcessGone = onRendererProcessGone,
    )
    webChromeClient = VortexDLWebChromeClient(callbacks)
}
