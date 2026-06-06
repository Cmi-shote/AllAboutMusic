package com.example.allaboutmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.data.downloader.DownloadRepository
import com.example.allaboutmusic.domain.model.Track
import com.example.allaboutmusic.domain.usecase.GetStreamUrlUseCase
import com.example.allaboutmusic.player.MusicPlayer
import com.example.allaboutmusic.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val musicPlayer: MusicPlayer,
    private val getStreamUrl: GetStreamUrlUseCase,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = musicPlayer.playerState
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun playTrack(track: Track) {
        viewModelScope.launch {
            try {
                // Check if track is downloaded (in-memory track from API may not have localPath)
                val resolvedTrack = if (track.localPath != null) {
                    track
                } else {
                    downloadRepository.getTrackWithLocalPath(track) ?: track
                }

                val streamUrl = if (resolvedTrack.localPath != null) {
                    "" // Not needed for local files, MusicPlayer handles it
                } else {
                    getStreamUrl(resolvedTrack.id)
                }
                musicPlayer.playTrack(resolvedTrack, streamUrl)
            } catch (e: Exception) {
                // Stream URL fetch failed — player will show error state
            }
        }
    }

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            musicPlayer.pause()
        } else {
            musicPlayer.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        musicPlayer.seekTo(positionMs)
    }

    fun stop() {
        musicPlayer.stop()
    }

    fun downloadCurrentTrack() {
        val track = playerState.value.currentTrack ?: return
        if (track.isDownloaded) return
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                downloadRepository.enqueueDownload(track)
            } finally {
                _isDownloading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }
}
