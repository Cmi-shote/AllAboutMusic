package com.example.allaboutmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.data.scanner.LocalAudioScanner
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab { DOWNLOADS, DEVICE }

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.DOWNLOADS,
    val downloadedTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermission: Boolean = false,
    val permissionRequested: Boolean = false
)

class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val localAudioScanner: LocalAudioScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeDownloadedTracks()
        observeLocalTracks()
        checkPermission()
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
