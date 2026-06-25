package com.example.allaboutmusic.ui.mix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.repository.MixRepository
import com.example.allaboutmusic.domain.model.Mix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MixListUiState(
    val mixes: List<Mix> = emptyList(),
    val showCreateDialog: Boolean = false
)

class MixListViewModel(
    private val mixRepository: MixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MixListUiState())
    val uiState: StateFlow<MixListUiState> = _uiState.asStateFlow()

    init {
        observeMixes()
    }

    private fun observeMixes() {
        viewModelScope.launch {
            mixRepository.getAllMixes().collect { mixes ->
                _uiState.value = _uiState.value.copy(mixes = mixes)
            }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun createMix(name: String, coverImagePath: String? = null) {
        viewModelScope.launch {
            mixRepository.createMix(name, coverImagePath)
            _uiState.value = _uiState.value.copy(showCreateDialog = false)
        }
    }

    fun deleteMix(mixId: String) {
        viewModelScope.launch {
            mixRepository.deleteMix(mixId)
        }
    }
}
