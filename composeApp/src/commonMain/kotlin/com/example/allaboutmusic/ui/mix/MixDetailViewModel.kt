package com.example.allaboutmusic.ui.mix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.export.MixExporter
import com.example.allaboutmusic.data.repository.MixRepository
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.domain.model.Mix
import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MixDetailUiState(
    val mix: Mix? = null,
    val tracks: List<MixTrack> = emptyList(),
    val availableTracks: List<Track> = emptyList(),
    val showAddTrackSheet: Boolean = false,
    val expandedTrackId: String? = null,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportResult: String? = null,
    val error: String? = null
)

class MixDetailViewModel(
    private val mixRepository: MixRepository,
    private val trackRepository: TrackRepository,
    private val mixExporter: MixExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(MixDetailUiState())
    val uiState: StateFlow<MixDetailUiState> = _uiState.asStateFlow()

    private var currentMixId: String? = null
    private var exportJob: Job? = null

    fun loadMix(mixId: String) {
        if (currentMixId == mixId) return
        currentMixId = mixId
        viewModelScope.launch {
            val mix = mixRepository.getMix(mixId)
            _uiState.value = _uiState.value.copy(mix = mix)
        }
        observeMixTracks(mixId)
    }

    private fun observeMixTracks(mixId: String) {
        viewModelScope.launch {
            mixRepository.getMixTracks(mixId).collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
    }

    fun showAddTrackSheet() {
        viewModelScope.launch {
            trackRepository.getPlayableTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(
                    showAddTrackSheet = true,
                    availableTracks = tracks
                )
            }
        }
    }

    fun dismissAddTrackSheet() {
        _uiState.value = _uiState.value.copy(showAddTrackSheet = false)
    }

    fun addTrack(track: Track) {
        val mixId = currentMixId ?: return
        viewModelScope.launch {
            val added = mixRepository.addTrackToMix(mixId, track)
            if (!added) {
                _uiState.value = _uiState.value.copy(error = "Max 50 tracks per mix")
            }
            // Refresh mix metadata (track count)
            val mix = mixRepository.getMix(mixId)
            _uiState.value = _uiState.value.copy(
                mix = mix,
                showAddTrackSheet = false
            )
        }
    }

    fun removeTrack(mixTrackId: String) {
        val mixId = currentMixId ?: return
        viewModelScope.launch {
            mixRepository.removeTrackFromMix(mixTrackId, mixId)
            val mix = mixRepository.getMix(mixId)
            _uiState.value = _uiState.value.copy(mix = mix)
        }
    }

    fun toggleExpanded(mixTrackId: String) {
        val current = _uiState.value.expandedTrackId
        _uiState.value = _uiState.value.copy(
            expandedTrackId = if (current == mixTrackId) null else mixTrackId
        )
    }

    fun updateCuePoints(mixTrackId: String, cueInMs: Long, cueOutMs: Long?) {
        viewModelScope.launch {
            mixRepository.updateCuePoints(mixTrackId, cueInMs, cueOutMs)
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.tracks.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        // Update local state immediately for smooth UI
        _uiState.value = _uiState.value.copy(tracks = current)
        // Persist new order
        val mixId = currentMixId ?: return
        viewModelScope.launch {
            mixRepository.reorderTracks(mixId, current.map { it.id })
        }
    }

    fun exportMix() {
        val mix = _uiState.value.mix ?: return
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "No tracks to export")
            return
        }
        val missingLocal = tracks.filter { it.localPath == null }
        if (missingLocal.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Some tracks are not downloaded: ${missingLocal.joinToString { it.title }}"
            )
            return
        }

        exportJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportProgress = 0f)
            val result = mixExporter.exportMix(
                mixName = mix.name,
                mixTracks = tracks,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(exportProgress = progress)
                }
            )
            result.onSuccess { path ->
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = path,
                    exportProgress = 1f
                )
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) {
                    _uiState.value = _uiState.value.copy(isExporting = false, exportProgress = 0f)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        error = "Export failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.value = _uiState.value.copy(isExporting = false, exportProgress = 0f)
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
