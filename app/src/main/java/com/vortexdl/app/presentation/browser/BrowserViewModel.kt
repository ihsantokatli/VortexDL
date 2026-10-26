package com.vortexdl.app.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.domain.model.VideoQualityOption
import com.vortexdl.app.domain.repository.DownloadRepository
import com.vortexdl.app.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel(), BrowserWebViewCallbacks {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<BrowserCommand>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<BrowserCommand> = _commands.asSharedFlow()

    /**
     * Read once by the WebView factory. Using a one-shot value instead of a replayed
     * command prevents a re-created WebView from replaying "go back" or "reload".
     */
    @Volatile
    private var startupUrl: String = BrowserUrl.HOME_URL

    init {
        viewModelScope.launch {
            mediaRepository.detectedMedia.collect { media ->
                _uiState.update { it.copy(detectedMediaList = media) }
            }
        }
    }

    fun initialUrl(): String = startupUrl

    /** Called by [com.vortexdl.app.MainActivity] before the first composition on a cold start. */
    fun setInitialUrl(url: String) {
        if (!BrowserUrl.isValidWebUrl(url)) return
        startupUrl = url
        _uiState.update { it.copy(currentUrl = url, isLoading = true, progress = 0) }
    }

    fun onAddressSubmitted(input: String) {
        navigateTo(BrowserUrl.toNavigableUrl(input))
    }

    /** Called when a new share intent arrives while the activity is already running. */
    fun openSharedUrl(url: String) {
        if (!BrowserUrl.isValidWebUrl(url)) return
        navigateTo(url)
    }

    fun navigateTo(url: String) {
        _uiState.update { it.copy(currentUrl = url, isLoading = true, progress = 0) }
        _commands.tryEmit(BrowserCommand.LoadUrl(url))
    }

    fun goBack() {
        _uiState.update { it.copy(isLoading = true, progress = 0) }
        _commands.tryEmit(BrowserCommand.GoBack)
    }

    fun goForward() {
        _uiState.update { it.copy(isLoading = true, progress = 0) }
        _commands.tryEmit(BrowserCommand.GoForward)
    }

    fun reload() {
        _uiState.update { it.copy(isLoading = true, progress = 0) }
        _commands.tryEmit(BrowserCommand.Reload)
    }

    fun stopLoading() {
        _uiState.update { it.copy(isLoading = false, progress = 0) }
        _commands.tryEmit(BrowserCommand.StopLoading)
    }

    fun clearDetectedMedia() {
        mediaRepository.clear()
    }

    fun downloadMedia(media: DetectedMedia) {
        if (media.isStreamingFormat) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        qualitySelectionMedia = media,
                        qualityOptions = emptyList(),
                        isLoadingQualities = true,
                    )
                }
                val options = downloadRepository.fetchHlsQualityOptions(media)
                if (options.size > 1) {
                    _uiState.update {
                        it.copy(
                            qualityOptions = options,
                            isLoadingQualities = false,
                        )
                    }
                } else {
                    // 1 or no variant options found; download immediately
                    _uiState.update {
                        it.copy(
                            qualitySelectionMedia = null,
                            qualityOptions = emptyList(),
                            isLoadingQualities = false,
                        )
                    }
                    val singleOption = options.firstOrNull()
                    downloadRepository.enqueueDownload(
                        media = media,
                        selectedResolution = singleOption?.resolution ?: media.resolution,
                        selectedUrl = singleOption?.url,
                    )
                }
            }
        } else {
            viewModelScope.launch {
                downloadRepository.enqueueDownload(
                    media = media,
                    selectedResolution = media.resolution,
                )
            }
        }
    }

    fun onQualitySelected(media: DetectedMedia, option: VideoQualityOption) {
        _uiState.update {
            it.copy(
                qualitySelectionMedia = null,
                qualityOptions = emptyList(),
                isLoadingQualities = false,
            )
        }
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                media = media,
                selectedResolution = option.resolution,
                selectedUrl = option.url,
            )
        }
    }

    fun dismissQualitySelection() {
        _uiState.update {
            it.copy(
                qualitySelectionMedia = null,
                qualityOptions = emptyList(),
                isLoadingQualities = false,
            )
        }
    }

    override fun onPageStarted(url: String) {
        if (!isDisplayableUrl(url)) return
        _uiState.update {
            it.copy(
                currentUrl = url,
                isLoading = true,
                progress = 0,
            )
        }
    }

    override fun onPageFinished(url: String, title: String) {
        _uiState.update { state ->
            state.copy(
                currentUrl = if (isDisplayableUrl(url)) url else state.currentUrl,
                pageTitle = title.trim().take(MAX_TITLE_LENGTH),
                isLoading = false,
                progress = 100,
            )
        }
    }

    override fun onUrlChanged(url: String) {
        if (!isDisplayableUrl(url)) return
        _uiState.update { it.copy(currentUrl = url) }
    }

    override fun onProgressChanged(progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        _uiState.update {
            it.copy(
                progress = clamped,
                isLoading = clamped < 100,
            )
        }
    }

    override fun onTitleChanged(title: String) {
        _uiState.update { it.copy(pageTitle = title.trim().take(MAX_TITLE_LENGTH)) }
    }

    override fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update {
            it.copy(canGoBack = canGoBack, canGoForward = canGoForward)
        }
    }

    override fun onPageLoadError() {
        _uiState.update { it.copy(isLoading = false, progress = 100) }
    }

    /**
     * Runs on a WebView background thread. Only touches [MediaRepository], which is
     * synchronised, so no WebView or Compose state is accessed here.
     */
    override fun onResourceRequest(
        url: String,
        requestHeaders: Map<String, String>,
        pageUrl: String?,
        pageTitle: String?,
        defaultUserAgent: String?,
        pageCookies: String?,
    ) {
        runCatching {
            mediaRepository.captureMedia(
                url = url,
                requestHeaders = requestHeaders,
                pageUrl = pageUrl,
                pageTitle = pageTitle,
                defaultUserAgent = defaultUserAgent,
                pageCookies = pageCookies,
            )
        }
    }

    fun onMediaSelected(media: DetectedMedia) {
        if (BrowserUrl.isValidWebUrl(media.url)) {
            navigateTo(media.url)
        }
    }

    private fun isDisplayableUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    private companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}
