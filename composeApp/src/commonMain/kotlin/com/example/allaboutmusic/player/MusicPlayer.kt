package com.example.allaboutmusic.player

import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val durationMs: Long = 0,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val isMixMode: Boolean = false,
    val mixTrackIndex: Int = 0,
    val mixTrackCount: Int = 0
)

expect class MusicPlayer {
    val playerState: StateFlow<PlayerState>
    val currentPosition: StateFlow<Long>

    fun playTrack(track: Track, streamUrl: String)
    fun playMix(mixTracks: List<MixTrack>)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun stop()
    fun release()
}
