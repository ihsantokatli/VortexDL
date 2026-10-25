package com.vortexdl.app.presentation.browser

import android.app.AlertDialog
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout

/**
 * Chrome client with popup blocking, permission denial and minimal JavaScript dialogs.
 * All callbacks are delivered on the UI thread by the WebView framework.
 */
class VortexDLWebChromeClient(
    private val callbacks: BrowserWebViewCallbacks,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        callbacks.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        callbacks.onTitleChanged(title)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message,
    ): Boolean = false

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: android.webkit.GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        val dialog = AlertDialog.Builder(view.context)
            .setTitle(originOf(url))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setOnCancelListener { result.cancel() }
            .create()
        dialog.show()
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        val dialog = AlertDialog.Builder(view.context)
            .setTitle(originOf(url))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .create()
        dialog.show()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean {
        val input = EditText(view.context).apply {
            setText(defaultValue.orEmpty())
            setSelection(text.length)
        }
        val density = view.resources.displayMetrics.density
        val horizontalPadding = (24 * density).toInt()
        val container = FrameLayout(view.context).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(
                input,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val dialog = AlertDialog.Builder(view.context)
            .setTitle(originOf(url))
            .setMessage(message)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .create()
        dialog.show()
        return true
    }

    private fun originOf(url: String): String {
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        return host?.takeIf { it.isNotBlank() } ?: url
    }
}
