package com.example.allaboutmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allaboutmusic.domain.model.Track
import com.example.allaboutmusic.domain.usecase.GetStreamUrlUseCase
import com.example.allaboutmusic.player.MusicPlayer
import com.example.allaboutmusic.player.PlayerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val musicPlayer: MusicPlayer,
    private val getStreamUrl: GetStreamUrlUseCase
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = musicPlayer.playerState
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition

    fun playTrack(track: Track) {
        viewModelScope.launch {
            try {
                val streamUrl = if (track.localPath != null) {
                    "" // Not needed for local files, MusicPlayer handles it
                } else {
                    getStreamUrl(track.id)
                }
                musicPlayer.playTrack(track, streamUrl)
            } catch (e: Exception) {
                // Stream URL fetch failed — player state will reflect the error
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

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }
}
