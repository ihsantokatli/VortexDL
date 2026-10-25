package com.vortexdl.app.presentation.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexdl.app.R
import com.vortexdl.app.domain.model.DetectedMedia
import com.vortexdl.app.presentation.components.BrowserTopBar
import com.vortexdl.app.presentation.components.DetectedMediaFab
import com.vortexdl.app.presentation.components.DetectedMediaSheet
import kotlinx.coroutines.launch

/**
 * Browser surface: URL bar, sniffing WebView, detected-media FAB and the list of
 * captured streams.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigateToDownloads: () -> Unit = {},
    isTabActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSheetVisible by rememberSaveable { mutableStateOf(false) }

    val linkCopiedMessage = stringResource(R.string.link_copied)
    val linkCannotOpenMessage = stringResource(R.string.link_cannot_open)
    val mediaClearedMessage = stringResource(R.string.detected_media_cleared)
    val downloadQueuedMessage = stringResource(R.string.download_queued)
    val viewDownloadsLabel = stringResource(R.string.action_view_downloads)
    val untitledMedia = stringResource(R.string.media_untitled)

    fun showSnackbar(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    /** Plays the sheet's hide animation before running a UI action behind it. */
    fun dismissSheetThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            isSheetVisible = false
            action()
        }
    }

    fun startDownload(media: DetectedMedia) {
        viewModel.downloadMedia(media)
        dismissSheetThen {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = downloadQueuedMessage,
                    actionLabel = viewDownloadsLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToDownloads()
                }
            }
        }
    }

    fun copyLink(media: DetectedMedia) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val label = media.title.trim().ifEmpty { untitledMedia }
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, media.url))
        dismissSheetThen { showSnackbar(linkCopiedMessage) }
    }

    fun openExternally(media: DetectedMedia) {
        dismissSheetThen {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (!started) showSnackbar(linkCannotOpenMessage)
        }
    }

    fun clearDetectedMedia() {
        viewModel.clearDetectedMedia()
        dismissSheetThen { showSnackbar(mediaClearedMessage) }
    }

    fun previewMedia(media: DetectedMedia) {
        dismissSheetThen { viewModel.onMediaSelected(media) }
    }

    BackHandler(enabled = isTabActive && uiState.canGoBack && !isSheetVisible) {
        viewModel.goBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BrowserTopBar(
                currentUrl = uiState.currentUrl,
                isLoading = uiState.isLoading,
                progress = uiState.progress,
                canGoBack = uiState.canGoBack,
                canGoForward = uiState.canGoForward,
                onNavigate = viewModel::onAddressSubmitted,
                onBack = viewModel::goBack,
                onForward = viewModel::goForward,
                onReload = viewModel::reload,
                onStopLoading = viewModel::stopLoading,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            if (uiState.detectedMediaCount > 0) {
                DetectedMediaFab(
                    count = uiState.detectedMediaCount,
                    onClick = { isSheetVisible = true },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            VideoSnifferWebView(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (isSheetVisible) {
        DetectedMediaSheet(
            mediaList = uiState.detectedMediaList,
            sheetState = sheetState,
            onDismissRequest = { isSheetVisible = false },
            onPreview = ::previewMedia,
            onDownload = ::startDownload,
            onCopyLink = ::copyLink,
            onOpenExternally = ::openExternally,
            onClearAll = ::clearDetectedMedia,
        )
    }
}
