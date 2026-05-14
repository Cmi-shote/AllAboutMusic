package com.example.allaboutmusic.data.repository

import com.example.allaboutmusic.data.database.TrackDao
import com.example.allaboutmusic.data.database.toDomain
import com.example.allaboutmusic.data.database.toEntity
import com.example.allaboutmusic.domain.model.MusicSource
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackRepository(
    private val musicSource: MusicSource,
    private val trackDao: TrackDao
) {
    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> {
        return musicSource.search(query, limit)
    }

    suspend fun getTrack(id: String): Track? {
        // Check local DB first (may have download info)
        val local = trackDao.getTrackById(id)
        if (local != null) return local.toDomain()
        // Fall back to API
        return musicSource.getTrack(id)
    }

    suspend fun getStreamUrl(trackId: String): String {
        return musicSource.getStreamUrl(trackId)
    }

    suspend fun getDownloadUrl(trackId: String): String {
        return musicSource.getDownloadUrl(trackId)
    }

    suspend fun getFeatured(limit: Int = 20): List<Track> {
        return musicSource.getFeatured(limit)
    }

    suspend fun getByGenre(genre: String, limit: Int = 20): List<Track> {
        return musicSource.getByGenre(genre, limit)
    }

    suspend fun saveTrack(track: Track) {
        trackDao.insertTrack(track.toEntity())
    }

    fun getDownloadedTracks(): Flow<List<Track>> {
        return trackDao.getDownloadedTracks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun updateLocalPath(trackId: String, localPath: String, downloadedAt: Long) {
        trackDao.updateLocalPath(trackId, localPath, downloadedAt)
    }
}
