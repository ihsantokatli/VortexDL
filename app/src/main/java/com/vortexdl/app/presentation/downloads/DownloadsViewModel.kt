package com.vortexdl.app.presentation.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexdl.app.domain.model.DownloadItem
import com.vortexdl.app.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DownloadsEvent {
    data class LaunchIntent(val intent: Intent) : DownloadsEvent
    data class ShowMessage(val messageResId: Int) : DownloadsEvent
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _events = Channel<DownloadsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            downloadRepository.allDownloads.collect { downloads ->
                _uiState.update { it.copy(allDownloads = downloads) }
            }
        }
    }

    fun selectTab(tab: DownloadsFilterTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch {
            downloadRepository.pauseDownload(id)
        }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            downloadRepository.resumeDownload(id)
        }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch {
            downloadRepository.cancelDownload(id)
        }
    }

    fun retryDownload(id: String) {
        viewModelScope.launch {
            downloadRepository.retryDownload(id)
        }
    }

    fun deleteDownload(id: String, removeFile: Boolean = true) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(id, removeFile)
        }
    }

    fun openDownloadedFile(item: DownloadItem) {
        val path = item.filePath ?: return
        val file = File(path)
        if (!file.exists()) {
            _events.trySend(DownloadsEvent.ShowMessage(com.vortexdl.app.R.string.file_not_found))
            return
        }

        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _events.trySend(DownloadsEvent.LaunchIntent(intent))
        }
    }
}
