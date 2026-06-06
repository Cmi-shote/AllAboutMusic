package com.example.allaboutmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val downloadedTracks: List<Track> = emptyList(),
    val isEmpty: Boolean = true
)

class LibraryViewModel(
    private val trackRepository: TrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeDownloadedTracks()
    }

    private fun observeDownloadedTracks() {
        viewModelScope.launch {
            trackRepository.getDownloadedTracks().collect { tracks ->
                _uiState.value = LibraryUiState(
                    downloadedTracks = tracks,
                    isEmpty = tracks.isEmpty()
                )
            }
        }
    }
}
