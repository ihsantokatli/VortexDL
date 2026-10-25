package com.vortexdl.app.presentation.browser

import com.vortexdl.app.domain.model.DetectedMedia

data class BrowserUiState(
    val currentUrl: String = BrowserUrl.HOME_URL,
    val pageTitle: String = "",
    val isLoading: Boolean = true,
    val detectedMediaList: List<DetectedMedia> = emptyList(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Int = 0,
) {
    val detectedMediaCount: Int
        get() = detectedMediaList.size
}
