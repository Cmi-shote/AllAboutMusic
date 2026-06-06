package com.example.allaboutmusic.data.repository

import com.example.allaboutmusic.data.database.MixDao
import com.example.allaboutmusic.data.database.MixEntity
import com.example.allaboutmusic.data.database.MixTrackEntity
import com.example.allaboutmusic.data.database.toDomain
import com.example.allaboutmusic.data.downloader.currentTimeMillis
import com.example.allaboutmusic.domain.model.Mix
import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MixRepository(
    private val mixDao: MixDao
) {
    companion object {
        const val MAX_TRACKS_PER_MIX = 50
    }

    fun getAllMixes(): Flow<List<Mix>> {
        return mixDao.getAllMixes().map { entities ->
            entities.map { entity ->
                val count = mixDao.getMixTrackCount(entity.id)
                entity.toDomain(trackCount = count)
            }
        }
    }

    suspend fun getMix(mixId: String): Mix? {
        val entity = mixDao.getMixById(mixId) ?: return null
        val count = mixDao.getMixTrackCount(mixId)
        return entity.toDomain(trackCount = count)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createMix(name: String): String {
        val id = Uuid.random().toString()
        mixDao.insertMix(
            MixEntity(
                id = id,
                name = name,
                createdAt = currentTimeMillis()
            )
        )
        return id
    }

    suspend fun deleteMix(mixId: String) {
        mixDao.deleteMix(mixId)
    }

    suspend fun renameMix(mixId: String, name: String) {
        mixDao.renameMix(mixId, name)
    }

    fun getMixTracks(mixId: String): Flow<List<MixTrack>> {
        return mixDao.getMixTracks(mixId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMixTracksList(mixId: String): List<MixTrack> {
        return mixDao.getMixTracksList(mixId).map { it.toDomain() }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun addTrackToMix(mixId: String, track: Track): Boolean {
        val currentCount = mixDao.getMixTrackCount(mixId)
        if (currentCount >= MAX_TRACKS_PER_MIX) return false

        val mixTrackId = Uuid.random().toString()
        mixDao.insertMixTrack(
            MixTrackEntity(
                id = mixTrackId,
                mixId = mixId,
                trackId = track.id,
                position = currentCount,
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                coverUrl = track.coverUrl,
                localPath = track.localPath
            )
        )
        return true
    }

    suspend fun removeTrackFromMix(mixTrackId: String, mixId: String) {
        mixDao.removeMixTrack(mixTrackId)
        // Re-number positions
        val remaining = mixDao.getMixTracksList(mixId)
        remaining.forEachIndexed { index, track ->
            if (track.position != index) {
                mixDao.updateMixTrackPosition(track.id, index)
            }
        }
    }

    suspend fun reorderTracks(mixId: String, reorderedIds: List<String>) {
        reorderedIds.forEachIndexed { index, id ->
            mixDao.updateMixTrackPosition(id, index)
        }
    }

    suspend fun updateCuePoints(mixTrackId: String, cueInMs: Long, cueOutMs: Long?) {
        mixDao.updateCuePoints(mixTrackId, cueInMs, cueOutMs)
    }
}
