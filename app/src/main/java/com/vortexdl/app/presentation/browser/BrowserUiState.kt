package com.vortexdl.app.presentation.browser

import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.domain.model.VideoQualityOption

data class BrowserUiState(
    val currentUrl: String = BrowserUrl.HOME_URL,
    val pageTitle: String = "",
    val isLoading: Boolean = true,
    val detectedMediaList: List<DetectedMedia> = emptyList(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Int = 0,
    val qualitySelectionMedia: DetectedMedia? = null,
    val qualityOptions: List<VideoQualityOption> = emptyList(),
    val isLoadingQualities: Boolean = false,
) {
    val detectedMediaCount: Int
        get() = detectedMediaList.size
}
