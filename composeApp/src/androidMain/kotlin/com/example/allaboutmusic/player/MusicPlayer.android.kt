package com.example.allaboutmusic.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.allaboutmusic.domain.model.MixTrack
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

    // Store mix tracks for metadata lookup during playback
    private var currentMixTracks: List<MixTrack> = emptyList()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Update current track info when mix advances
                if (currentMixTracks.isNotEmpty()) {
                    val index = currentMediaItemIndex
                    if (index in currentMixTracks.indices) {
                        val mt = currentMixTracks[index]
                        val duration = if (duration > 0) duration else 0L
                        _playerState.value = _playerState.value.copy(
                            currentTrack = Track(
                                id = mt.trackId,
                                title = mt.title,
                                artist = mt.artist,
                                durationMs = mt.durationMs,
                                coverUrl = mt.coverUrl,
                                localPath = mt.localPath
                            ),
                            mixTrackIndex = index,
                            durationMs = duration
                        )
                    }
                }
                // Reset position for new track
                _currentPosition.value = 0L
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

    private val localDataSourceFactory = DefaultDataSource.Factory(context)

    actual fun playTrack(track: Track, streamUrl: String) {
        currentMixTracks = emptyList()

        val mediaSource = if (track.localPath != null) {
            // Local files: use direct data source, no cache
            ProgressiveMediaSource.Factory(localDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(File(track.localPath!!))))
        } else {
            ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        _playerState.value = PlayerState(
            isPlaying = false,
            currentTrack = track,
            durationMs = 0,
            isBuffering = true,
            isMixMode = false
        )
    }

    actual fun playMix(mixTracks: List<MixTrack>) {
        if (mixTracks.isEmpty()) return
        currentMixTracks = mixTracks

        val sources = mixTracks.map { mt ->
            val uri = if (mt.localPath != null) {
                Uri.fromFile(File(mt.localPath))
            } else {
                return // Mix playback requires downloaded tracks
            }

            // Local files: use direct data source, no cache
            val original = ProgressiveMediaSource.Factory(localDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))

            // ClippingMediaSource takes microseconds
            val startUs = mt.cueInMs * 1000L
            val endUs = mt.cueOutMs?.let { it * 1000L } ?: C.TIME_END_OF_SOURCE

            ClippingMediaSource(original, startUs, endUs)
        }

        val concatenated = ConcatenatingMediaSource(*sources.toTypedArray())
        exoPlayer.setMediaSource(concatenated)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        val first = mixTracks.first()
        _playerState.value = PlayerState(
            isPlaying = false,
            currentTrack = Track(
                id = first.trackId,
                title = first.title,
                artist = first.artist,
                durationMs = first.durationMs,
                coverUrl = first.coverUrl,
                localPath = first.localPath
            ),
            durationMs = 0,
            isBuffering = true,
            isMixMode = true,
            mixTrackIndex = 0,
            mixTrackCount = mixTracks.size
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

    actual fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        }
    }

    actual fun skipToPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    actual fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentMixTracks = emptyList()
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
                // Update duration if not yet known (e.g. after mix track transition)
                val current = _playerState.value
                if (current.durationMs <= 0 && exoPlayer.duration > 0) {
                    _playerState.value = current.copy(durationMs = exoPlayer.duration)
                }
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }
}
