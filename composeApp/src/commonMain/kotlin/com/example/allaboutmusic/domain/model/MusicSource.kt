package com.example.allaboutmusic.domain.model

interface MusicSource {
    suspend fun search(query: String, limit: Int = 20): List<Track>
    suspend fun getTrack(id: String): Track?
    suspend fun getStreamUrl(trackId: String): String
    suspend fun getDownloadUrl(trackId: String): String
    suspend fun getFeatured(limit: Int = 20): List<Track>
    suspend fun getByGenre(genre: String, limit: Int = 20): List<Track>
}
