package com.example.allaboutmusic.data.scanner

import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.MediaPlayer.MPMediaItem
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyAssetURL
import platform.MediaPlayer.MPMediaItemPropertyPersistentID
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPMediaLibrary
import platform.MediaPlayer.MPMediaQuery

actual class LocalAudioScanner {

    actual suspend fun scanLibrary(): List<Track> = withContext(Dispatchers.Default) {
        if (!hasPermission()) return@withContext emptyList()

        val query = MPMediaQuery.songsQuery()
        val items = query.items ?: return@withContext emptyList()

        items.mapNotNull { item ->
            val mediaItem = item as MPMediaItem
            val assetUrl = mediaItem.valueForProperty(MPMediaItemPropertyAssetURL) ?: return@mapNotNull null

            val persistentId = mediaItem.valueForProperty(MPMediaItemPropertyPersistentID) as? Long ?: return@mapNotNull null
            val title = mediaItem.valueForProperty(MPMediaItemPropertyTitle) as? String ?: "Unknown"
            val artist = mediaItem.valueForProperty(MPMediaItemPropertyArtist) as? String ?: "Unknown"
            val album = mediaItem.valueForProperty(MPMediaItemPropertyAlbumTitle) as? String
            val durationSec = mediaItem.valueForProperty(MPMediaItemPropertyPlaybackDuration) as? Double ?: 0.0
            val durationMs = (durationSec * 1000).toLong()

            if (durationMs < 30000) return@mapNotNull null

            Track(
                id = "local_$persistentId",
                source = "local",
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                localPath = assetUrl.toString()
            )
        }
    }

    actual suspend fun hasPermission(): Boolean {
        return MPMediaLibrary.authorizationStatus() == 3L // .authorized
    }
}
