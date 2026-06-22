package com.example.allaboutmusic.player

import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.isPlaybackLikelyToKeepUp
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual class MusicPlayer {
    private val avPlayer = AVPlayer()

    private val _playerState = MutableStateFlow(PlayerState())
    actual val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var timeObserver: Any? = null
    private var endObserver: Any? = null

    // Mix state
    private var currentMixTracks: List<MixTrack> = emptyList()
    private var currentMixIndex: Int = 0
    // Cue-out is checked in the periodic observer instead of a boundary observer
    private var activeCueOutMs: Long? = null

    init {
        setupAudioSession()
        startPositionObserver()
    }

    private fun setupAudioSession() {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        } catch (_: Exception) {
            // Audio session setup failed
        }
    }

    private fun startPositionObserver() {
        val interval = CMTimeMakeWithSeconds(0.25, 1_000_000_000)
        timeObserver = avPlayer.addPeriodicTimeObserverForInterval(interval, null) { time ->
            val seconds = CMTimeGetSeconds(time)
            if (!seconds.isNaN()) {
                val positionMs = (seconds * 1000).toLong()
                _currentPosition.value = positionMs

                // Check cue-out point for mix mode
                val cueOut = activeCueOutMs
                if (cueOut != null && positionMs >= cueOut) {
                    activeCueOutMs = null // Prevent re-trigger
                    advanceToNextMixTrack()
                    return@addPeriodicTimeObserverForInterval
                }
            }

            // Update buffering + playing state
            val isPlaying = avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying
            val isBuffering = avPlayer.currentItem?.isPlaybackLikelyToKeepUp() != true

            // Pick up duration once ready
            val currentDuration = _playerState.value.durationMs
            val itemDuration = avPlayer.currentItem?.duration()?.let { CMTimeGetSeconds(it) } ?: 0.0
            val durationMs = if (!itemDuration.isNaN() && itemDuration > 0) {
                (itemDuration * 1000).toLong()
            } else {
                currentDuration
            }

            _playerState.value = _playerState.value.copy(
                isPlaying = isPlaying,
                isBuffering = isBuffering && !isPlaying,
                durationMs = durationMs
            )
        }
    }

    actual fun playTrack(track: Track, streamUrl: String) {
        currentMixTracks = emptyList()
        activeCueOutMs = null
        removeEndObserver()

        val urlString = track.localPath?.let { "file://$it" } ?: streamUrl
        val nsUrl = NSURL.URLWithString(urlString) ?: return

        val item = AVPlayerItem(uRL = nsUrl)
        avPlayer.replaceCurrentItemWithPlayerItem(item)
        avPlayer.play()

        _currentPosition.value = 0L
        _playerState.value = PlayerState(
            isPlaying = false,
            currentTrack = track,
            durationMs = 0,
            isBuffering = true,
            isMixMode = false
        )

        observeTrackEnd {
            avPlayer.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
        }
    }

    actual fun playMix(mixTracks: List<MixTrack>) {
        if (mixTracks.isEmpty()) return
        currentMixTracks = mixTracks
        currentMixIndex = 0

        val first = mixTracks.first()
        _playerState.value = PlayerState(
            isPlaying = false,
            currentTrack = mixTrackToTrack(first),
            durationMs = 0,
            isBuffering = true,
            isMixMode = true,
            mixTrackIndex = 0,
            mixTrackCount = mixTracks.size
        )

        playMixTrackAtIndex(0)
    }

    private fun playMixTrackAtIndex(index: Int) {
        if (index !in currentMixTracks.indices) {
            avPlayer.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            return
        }

        removeEndObserver()
        activeCueOutMs = null
        currentMixIndex = index
        val mt = currentMixTracks[index]

        val path = mt.localPath ?: return
        val nsUrl = NSURL.fileURLWithPath(path)

        val item = AVPlayerItem(uRL = nsUrl)
        avPlayer.replaceCurrentItemWithPlayerItem(item)

        // Seek to cue-in
        if (mt.cueInMs > 0) {
            val cueInTime = CMTimeMakeWithSeconds(mt.cueInMs / 1000.0, 1_000_000_000)
            avPlayer.seekToTime(cueInTime)
        }

        avPlayer.play()

        _currentPosition.value = 0L
        _playerState.value = _playerState.value.copy(
            currentTrack = mixTrackToTrack(mt),
            mixTrackIndex = index,
            durationMs = 0,
            isBuffering = true
        )

        // Set cue-out to be checked in the periodic observer
        if (mt.cueOutMs != null) {
            activeCueOutMs = mt.cueOutMs
        } else {
            observeTrackEnd { advanceToNextMixTrack() }
        }
    }

    private fun advanceToNextMixTrack() {
        val nextIndex = currentMixIndex + 1
        if (nextIndex < currentMixTracks.size) {
            playMixTrackAtIndex(nextIndex)
        } else {
            avPlayer.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
        }
    }

    actual fun pause() {
        avPlayer.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    actual fun resume() {
        avPlayer.play()
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }

    actual fun seekTo(positionMs: Long) {
        val time = CMTimeMakeWithSeconds(positionMs / 1000.0, 1_000_000_000)
        avPlayer.seekToTime(time)
        _currentPosition.value = positionMs
    }

    actual fun skipToNext() {
        if (currentMixTracks.isNotEmpty() && currentMixIndex < currentMixTracks.lastIndex) {
            playMixTrackAtIndex(currentMixIndex + 1)
        }
    }

    actual fun skipToPrevious() {
        if (currentMixTracks.isNotEmpty() && currentMixIndex > 0) {
            playMixTrackAtIndex(currentMixIndex - 1)
        }
    }

    actual fun stop() {
        avPlayer.pause()
        removeEndObserver()
        activeCueOutMs = null
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        currentMixTracks = emptyList()
        _playerState.value = PlayerState()
        _currentPosition.value = 0L
    }

    actual fun release() {
        stop()
        timeObserver?.let { avPlayer.removeTimeObserver(it) }
        timeObserver = null
    }

    // --- Observers ---

    private fun observeTrackEnd(onEnd: () -> Unit) {
        removeEndObserver()
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = avPlayer.currentItem,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            onEnd()
        }
    }

    private fun removeEndObserver() {
        endObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        endObserver = null
    }

    // --- Helpers ---

    private fun mixTrackToTrack(mt: MixTrack) = Track(
        id = mt.trackId,
        title = mt.title,
        artist = mt.artist,
        durationMs = mt.durationMs,
        coverUrl = mt.coverUrl,
        localPath = mt.localPath
    )
}
