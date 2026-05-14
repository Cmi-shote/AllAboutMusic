package com.example.allaboutmusic.data.api

import com.example.allaboutmusic.domain.model.MusicSource
import com.example.allaboutmusic.domain.model.Track

class JamendoMusicSource(
    private val api: JamendoApiService
) : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> {
        return api.searchTracks(query, limit).map { it.toDomain() }
    }

    override suspend fun getTrack(id: String): Track? {
        return api.getTrackById(id)?.toDomain()
    }

    override suspend fun getStreamUrl(trackId: String): String {
        return api.getStreamUrl(trackId)
    }

    override suspend fun getDownloadUrl(trackId: String): String {
        return api.getDownloadUrl(trackId)
    }

    override suspend fun getFeatured(limit: Int): List<Track> {
        return api.getFeatured(limit).map { it.toDomain() }
    }

    override suspend fun getByGenre(genre: String, limit: Int): List<Track> {
        return api.getByGenre(genre, limit).map { it.toDomain() }
    }
}

fun JamendoTrackDto.toDomain(): Track {
    return Track(
        id = id,
        source = "jamendo",
        title = name,
        artist = artistName,
        album = albumName.ifBlank { null },
        durationMs = duration * 1000L,
        coverUrl = image.ifBlank { null },
        licenseUrl = licenseCcUrl.ifBlank { null }
    )
}
