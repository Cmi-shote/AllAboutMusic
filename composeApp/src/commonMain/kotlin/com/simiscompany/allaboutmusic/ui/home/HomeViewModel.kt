package com.simiscompany.allaboutmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simiscompany.allaboutmusic.data.downloader.DownloadRepository
import com.simiscompany.allaboutmusic.data.downloader.InsufficientStorageException
import com.simiscompany.allaboutmusic.domain.model.DownloadItem
import com.simiscompany.allaboutmusic.domain.model.Track
import com.simiscompany.allaboutmusic.domain.usecase.GetFeaturedTracksUseCase
import com.simiscompany.allaboutmusic.domain.usecase.GetTracksByGenreUseCase
import com.simiscompany.allaboutmusic.domain.usecase.SearchTracksUseCase
import com.simiscompany.allaboutmusic.resources.Res
import com.simiscompany.allaboutmusic.resources.error_load_tracks
import com.simiscompany.allaboutmusic.resources.error_search
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class HomeUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedGenre: String? = null,
    val searchQuery: String = "",
    val downloadStates: Map<String, DownloadItem> = emptyMap()
)

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val searchTracks: SearchTracksUseCase,
    private val getFeaturedTracks: GetFeaturedTracksUseCase,
    private val getTracksByGenre: GetTracksByGenreUseCase,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    companion object {
        val GENRES = listOf("rock", "electronic", "jazz", "hiphop", "classical", "pop", "ambient", "metal", "blues", "reggae")
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchInput = MutableStateFlow("")

    init {
        loadFeatured()
        observeSearch()
        observeDownloads()
    }

    fun onSearchQueryChanged(query: String) {
        _searchInput.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            // Revert to current genre or featured
            val genre = _uiState.value.selectedGenre
            if (genre != null) selectGenre(genre) else loadFeatured()
        }
    }

    fun selectGenre(genre: String?) {
        _uiState.value = _uiState.value.copy(selectedGenre = genre, searchQuery = "")
        _searchInput.value = ""
        if (genre == null) {
            loadFeatured()
        } else {
            loadByGenre(genre)
        }
    }

    private fun loadFeatured() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tracks = getFeaturedTracks()
                _uiState.value = _uiState.value.copy(tracks = tracks, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: getString(Res.string.error_load_tracks)
                )
            }
        }
    }

    private fun loadByGenre(genre: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tracks = getTracksByGenre(genre)
                _uiState.value = _uiState.value.copy(tracks = tracks, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: getString(Res.string.error_load_tracks)
                )
            }
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            try {
                downloadRepository.enqueueDownload(track)
            } catch (e: InsufficientStorageException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloads().collect { downloads ->
                val stateMap = downloads.associateBy { it.track.id }
                _uiState.value = _uiState.value.copy(downloadStates = stateMap)
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _searchInput
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { query ->
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    try {
                        val tracks = searchTracks(query)
                        _uiState.value = _uiState.value.copy(
                            tracks = tracks,
                            isLoading = false,
                            selectedGenre = null
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: getString(Res.string.error_search)
                        )
                    }
                }
        }
    }
}
