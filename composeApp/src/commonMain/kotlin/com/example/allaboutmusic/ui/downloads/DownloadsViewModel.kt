package com.example.allaboutmusic.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.downloader.DownloadRepository
import com.example.allaboutmusic.domain.model.DownloadItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

private const val STORAGE_WARNING_BYTES = 2L * 1024 * 1024 * 1024 // 2GB

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val storageUsedBytes: Long = 0,
    val showStorageWarning: Boolean = false
)

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
        refreshStorage()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloads()
                .collect { downloads ->
                    val hadCompleted = _uiState.value.downloads.count { it.status == DownloadItem.Status.COMPLETED }
                    val nowCompleted = downloads.count { it.status == DownloadItem.Status.COMPLETED }
                    _uiState.value = _uiState.value.copy(downloads = downloads)

                    // Refresh storage when a download newly completes or list changes size
                    if (nowCompleted != hadCompleted || downloads.size != _uiState.value.downloads.size) {
                        // Small delay to let file rename (.tmp -> .mp3) settle
                        delay(500)
                    }
                    refreshStorage()
                }
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.cancelDownload(downloadId)
        }
    }

    fun retryDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.retryDownload(downloadId)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            downloadRepository.clearCompleted()
            refreshStorage()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val usage = downloadRepository.getStorageUsageBytes()
            _uiState.value = _uiState.value.copy(
                storageUsedBytes = usage,
                showStorageWarning = usage > STORAGE_WARNING_BYTES
            )
        }
    }
}
