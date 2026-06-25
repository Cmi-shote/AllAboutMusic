package com.example.allaboutmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.downloader.DownloadRepository
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.data.scanner.LocalAudioScanner
import com.example.allaboutmusic.domain.model.DownloadItem
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val STORAGE_WARNING_BYTES = 2L * 1024 * 1024 * 1024 // 2GB

enum class LibraryTab { DOWNLOADS, DEVICE }

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.DOWNLOADS,
    val downloadedTracks: List<Track> = emptyList(),
    val activeDownloads: List<DownloadItem> = emptyList(),
    val storageUsedBytes: Long = 0,
    val showStorageWarning: Boolean = false,
    val localTracks: List<Track> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermission: Boolean = false,
    val permissionRequested: Boolean = false
)

class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val localAudioScanner: LocalAudioScanner,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeDownloadedTracks()
        observeLocalTracks()
        observeDownloadQueue()
        checkPermission()
        refreshStorage()
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    private fun observeDownloadedTracks() {
        viewModelScope.launch {
            trackRepository.getDownloadedTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(downloadedTracks = tracks)
            }
        }
    }

    private fun observeLocalTracks() {
        viewModelScope.launch {
            trackRepository.getLocalTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(localTracks = tracks)
            }
        }
    }

    private fun observeDownloadQueue() {
        viewModelScope.launch {
            downloadRepository.getAllDownloads().collect { downloads ->
                val hadCompleted = _uiState.value.activeDownloads.count { it.status == DownloadItem.Status.COMPLETED }
                val nowCompleted = downloads.count { it.status == DownloadItem.Status.COMPLETED }
                _uiState.value = _uiState.value.copy(activeDownloads = downloads)
                if (nowCompleted != hadCompleted) {
                    delay(500)
                }
                refreshStorage()
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(downloadId) }
    }

    fun retryDownload(downloadId: String) {
        viewModelScope.launch { downloadRepository.retryDownload(downloadId) }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            downloadRepository.clearCompleted()
            refreshStorage()
        }
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            val usage = downloadRepository.getStorageUsageBytes()
            _uiState.value = _uiState.value.copy(
                storageUsedBytes = usage,
                showStorageWarning = usage > STORAGE_WARNING_BYTES
            )
        }
    }

    fun checkPermission() {
        viewModelScope.launch {
            val granted = localAudioScanner.hasPermission()
            _uiState.value = _uiState.value.copy(hasPermission = granted)
            if (granted) scanLocalLibrary()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasPermission = granted,
            permissionRequested = true
        )
        if (granted) scanLocalLibrary()
    }

    fun scanLocalLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val tracks = localAudioScanner.scanLibrary()
            trackRepository.syncLocalTracks(tracks)
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }
}
