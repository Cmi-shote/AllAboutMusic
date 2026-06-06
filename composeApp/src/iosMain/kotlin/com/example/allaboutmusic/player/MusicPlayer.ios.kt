package com.example.allaboutmusic.player

import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MusicPlayer {
    private val _playerState = MutableStateFlow(PlayerState())
    actual val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    actual fun playTrack(track: Track, streamUrl: String) {
        // TODO: Implement with AVPlayer in Phase 5
        _playerState.value = PlayerState(
            currentTrack = track,
            isPlaying = false,
            error = "iOS player not yet implemented"
        )
    }

    actual fun playMix(mixTracks: List<MixTrack>) {
        // TODO: Implement with AVQueuePlayer in Phase 5
        _playerState.value = PlayerState(
            error = "iOS mix playback not yet implemented"
        )
    }

    actual fun pause() {}
    actual fun resume() {}
    actual fun seekTo(positionMs: Long) {}
    actual fun skipToNext() {}
    actual fun skipToPrevious() {}
    actual fun stop() {
        _playerState.value = PlayerState()
        _currentPosition.value = 0L
    }
    actual fun release() { stop() }
}
