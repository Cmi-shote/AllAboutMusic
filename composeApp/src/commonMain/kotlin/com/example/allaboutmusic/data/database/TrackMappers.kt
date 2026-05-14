package com.example.allaboutmusic.data.database

import com.example.allaboutmusic.domain.model.Track

fun TrackEntity.toDomain(): Track {
    return Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        coverUrl = coverUrl,
        licenseUrl = licenseUrl,
        localPath = localPath,
        downloadedAt = downloadedAt
    )
}

fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        coverUrl = coverUrl,
        licenseUrl = licenseUrl,
        localPath = localPath,
        downloadedAt = downloadedAt
    )
}
