package com.example.allaboutmusic.data.database

import com.example.allaboutmusic.domain.model.Mix
import com.example.allaboutmusic.domain.model.MixTrack

fun MixEntity.toDomain(trackCount: Int = 0) = Mix(
    id = id,
    name = name,
    createdAt = createdAt,
    trackCount = trackCount
)

fun MixWithTrackCount.toDomain() = Mix(
    id = id,
    name = name,
    createdAt = createdAt,
    trackCount = trackCount
)

fun MixTrackEntity.toDomain() = MixTrack(
    id = id,
    mixId = mixId,
    trackId = trackId,
    position = position,
    cueInMs = cueInMs,
    cueOutMs = cueOutMs,
    title = title,
    artist = artist,
    durationMs = durationMs,
    coverUrl = coverUrl,
    localPath = localPath
)
