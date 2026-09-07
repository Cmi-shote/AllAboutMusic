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
import kotlinx.coroutines.flow.update
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

    // Starts loading: init kicks off the featured load immediately, and an
    // empty-but-idle first frame would flash the "no tracks" empty state.
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchInput = MutableStateFlow("")

    // Bumped for every load so a slow response cannot overwrite a newer one —
    // e.g. a search still in flight when the user clears the field.
    private var requestSeq = 0

    init {
        loadFeatured()
        observeSearch()
        observeDownloads()
    }

    fun onSearchQueryChanged(query: String) {
        _searchInput.value = query
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            // Back out of search to whatever the user was browsing before it
            val genre = _uiState.value.selectedGenre
            if (genre != null) loadByGenre(genre) else loadFeatured()
        }
    }

    fun selectGenre(genre: String?) {
        _uiState.update { it.copy(selectedGenre = genre, searchQuery = "") }
        _searchInput.value = ""
        if (genre == null) {
            loadFeatured()
        } else {
            loadByGenre(genre)
        }
    }

    private fun loadFeatured() {
        val token = beginRequest()
        viewModelScope.launch {
            try {
                val tracks = getFeaturedTracks()
                applyIfCurrent(token) { it.copy(tracks = tracks, isLoading = false) }
            } catch (e: Exception) {
                val message = e.message?.takeIf { it.isNotBlank() }
                    ?: getString(Res.string.error_load_tracks)
                applyIfCurrent(token) { it.copy(isLoading = false, error = message) }
            }
        }
    }

    private fun loadByGenre(genre: String) {
        val token = beginRequest()
        viewModelScope.launch {
            try {
                val tracks = getTracksByGenre(genre)
                applyIfCurrent(token) { it.copy(tracks = tracks, isLoading = false) }
            } catch (e: Exception) {
                val message = e.message?.takeIf { it.isNotBlank() }
                    ?: getString(Res.string.error_load_tracks)
                applyIfCurrent(token) { it.copy(isLoading = false, error = message) }
            }
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            try {
                downloadRepository.enqueueDownload(track)
            } catch (e: InsufficientStorageException) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloads().collect { downloads ->
                val stateMap = downloads.associateBy { it.track.id }
                _uiState.update { it.copy(downloadStates = stateMap) }
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
                    val token = beginRequest()
                    try {
                        val tracks = searchTracks(query)
                        applyIfCurrent(token) { it.copy(tracks = tracks, isLoading = false) }
                    } catch (e: Exception) {
                        val message = e.message?.takeIf { it.isNotBlank() }
                            ?: getString(Res.string.error_search)
                        applyIfCurrent(token) { it.copy(isLoading = false, error = message) }
                    }
                }
        }
    }

    /** Marks a new load as the current one and puts the UI into its loading state. */
    private fun beginRequest(): Int {
        val token = ++requestSeq
        _uiState.update { it.copy(isLoading = true, error = null) }
        return token
    }

    private fun applyIfCurrent(token: Int, transform: (HomeUiState) -> HomeUiState) {
        if (token == requestSeq) _uiState.update(transform)
    }
}
