package com.example.allaboutmusic.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
actual class MusicPlayer(context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    actual val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val cache: SimpleCache = SimpleCache(
        File(context.cacheDir, "media_cache"),
        LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024), // 500MB
        StandaloneDatabaseProvider(context)
    )

    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playerState.value = _playerState.value.copy(
                    error = error.message,
                    isPlaying = false,
                    isBuffering = false
                )
            }
        })
    }

    actual fun playTrack(track: Track, streamUrl: String) {
        val uri = if (track.localPath != null) {
            // Offline playback from local file
            "file://${track.localPath}"
        } else {
            streamUrl
        }

        val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        _playerState.value = PlayerState(
            isPlaying = false,
            currentTrack = track,
            durationMs = 0,
            isBuffering = true
        )
    }

    actual fun pause() {
        exoPlayer.pause()
    }

    actual fun resume() {
        exoPlayer.play()
    }

    actual fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    actual fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playerState.value = PlayerState()
        _currentPosition.value = 0L
        stopPositionUpdates()
    }

    actual fun release() {
        stop()
        exoPlayer.release()
        cache.release()
    }

    private fun updateState() {
        val current = _playerState.value
        _playerState.value = current.copy(
            isPlaying = exoPlayer.isPlaying,
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
            durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else current.durationMs
        )
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionJob = scope.launch {
            while (isActive) {
                _currentPosition.value = exoPlayer.currentPosition
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }
}
