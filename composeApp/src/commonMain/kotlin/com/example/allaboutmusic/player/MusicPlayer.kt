package com.example.allaboutmusic.player

import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val durationMs: Long = 0,
    val isBuffering: Boolean = false,
    val error: String? = null
)

expect class MusicPlayer {
    val playerState: StateFlow<PlayerState>
    val currentPosition: StateFlow<Long>

    fun playTrack(track: Track, streamUrl: String)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
    fun release()
}
