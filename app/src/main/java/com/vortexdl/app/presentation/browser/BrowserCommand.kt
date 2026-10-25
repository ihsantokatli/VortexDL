package com.vortexdl.app.presentation.browser

sealed interface BrowserCommand {
    data class LoadUrl(val url: String) : BrowserCommand
    data object GoBack : BrowserCommand
    data object GoForward : BrowserCommand
    data object Reload : BrowserCommand
    data object StopLoading : BrowserCommand
}
