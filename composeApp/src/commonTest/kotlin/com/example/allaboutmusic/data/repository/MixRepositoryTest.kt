package com.example.allaboutmusic.data.repository

import com.example.allaboutmusic.data.database.MixDao
import com.example.allaboutmusic.data.database.MixEntity
import com.example.allaboutmusic.data.database.MixTrackEntity
import com.example.allaboutmusic.data.database.MixWithTrackCount
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MixRepositoryTest {

    private val fakeMixDao = FakeMixDao()
    private val repository = MixRepository(fakeMixDao)

    @Test
    fun createMix_insertsAndReturnsId() = runTest {
        val id = repository.createMix("My Mix")
        assertNotNull(id)
        assertTrue(id.isNotBlank())
        assertNotNull(fakeMixDao.mixes[id])
        assertEquals("My Mix", fakeMixDao.mixes[id]?.name)
    }

    @Test
    fun createMix_withCoverImage() = runTest {
        val id = repository.createMix("Cover Mix", "/images/cover.jpg")
        assertEquals("/images/cover.jpg", fakeMixDao.mixes[id]?.coverImagePath)
    }

    @Test
    fun getMix_returnsExistingMix() = runTest {
        val id = repository.createMix("Test Mix")
        val mix = repository.getMix(id)
        assertNotNull(mix)
        assertEquals("Test Mix", mix.name)
        assertEquals(0, mix.trackCount)
    }

    @Test
    fun getMix_returnsNullForNonexistent() = runTest {
        val mix = repository.getMix("nonexistent")
        assertNull(mix)
    }

    @Test
    fun deleteMix_removesMix() = runTest {
        val id = repository.createMix("To Delete")
        assertNotNull(fakeMixDao.mixes[id])
        repository.deleteMix(id)
        assertNull(fakeMixDao.mixes[id])
    }

    @Test
    fun addTrackToMix_addsTrackAtCorrectPosition() = runTest {
        val mixId = repository.createMix("Mix")
        val track = createTrack("t1", "Song 1")

        val added = repository.addTrackToMix(mixId, track)
        assertTrue(added)

        val tracks = repository.getMixTracksList(mixId)
        assertEquals(1, tracks.size)
        assertEquals("Song 1", tracks[0].title)
        assertEquals(0, tracks[0].position)
    }

    @Test
    fun addTrackToMix_enforcesMaxLimit() = runTest {
        val mixId = repository.createMix("Big Mix")
        // Add 50 tracks (max)
        repeat(50) { i ->
            repository.addTrackToMix(mixId, createTrack("t$i", "Song $i"))
        }
        // 51st should fail
        val added = repository.addTrackToMix(mixId, createTrack("t50", "Song 50"))
        assertFalse(added)
    }

    @Test
    fun removeTrackFromMix_removesAndReorders() = runTest {
        val mixId = repository.createMix("Mix")
        repository.addTrackToMix(mixId, createTrack("t1", "Song 1"))
        repository.addTrackToMix(mixId, createTrack("t2", "Song 2"))
        repository.addTrackToMix(mixId, createTrack("t3", "Song 3"))

        val tracks = repository.getMixTracksList(mixId)
        assertEquals(3, tracks.size)

        // Remove middle track
        repository.removeTrackFromMix(tracks[1].id, mixId)

        val remaining = repository.getMixTracksList(mixId)
        assertEquals(2, remaining.size)
        assertEquals(0, remaining[0].position)
        assertEquals(1, remaining[1].position)
    }

    @Test
    fun updateCuePoints_updatesCorrectly() = runTest {
        val mixId = repository.createMix("Mix")
        repository.addTrackToMix(mixId, createTrack("t1", "Song"))

        val tracks = repository.getMixTracksList(mixId)
        val trackId = tracks[0].id

        repository.updateCuePoints(trackId, 5000L, 30000L)

        val updated = repository.getMixTracksList(mixId)
        assertEquals(5000L, updated[0].cueInMs)
        assertEquals(30000L, updated[0].cueOutMs)
    }

    @Test
    fun updateCuePoints_nullCueOutMeansPlayToEnd() = runTest {
        val mixId = repository.createMix("Mix")
        repository.addTrackToMix(mixId, createTrack("t1", "Song"))

        val tracks = repository.getMixTracksList(mixId)
        repository.updateCuePoints(tracks[0].id, 1000L, null)

        val updated = repository.getMixTracksList(mixId)
        assertEquals(1000L, updated[0].cueInMs)
        assertNull(updated[0].cueOutMs)
    }

    @Test
    fun updateMixCoverImage_updatesPath() = runTest {
        val mixId = repository.createMix("Mix")
        repository.updateMixCoverImage(mixId, "/new/cover.jpg")
        assertEquals("/new/cover.jpg", fakeMixDao.mixes[mixId]?.coverImagePath)
    }

    private fun createTrack(id: String, title: String) = Track(
        id = id,
        title = title,
        artist = "Artist",
        durationMs = 180_000L,
        localPath = "/music/$id.mp3"
    )
}

private class FakeMixDao : MixDao {
    val mixes = mutableMapOf<String, MixEntity>()
    val mixTracks = mutableMapOf<String, MixTrackEntity>()
    private val _mixesFlow = MutableStateFlow(0) // trigger for flow updates

    override suspend fun insertMix(mix: MixEntity) {
        mixes[mix.id] = mix
        _mixesFlow.value++
    }

    override fun getAllMixes(): Flow<List<MixEntity>> =
        _mixesFlow.map { mixes.values.sortedByDescending { it.createdAt } }

    override fun getAllMixesWithTrackCount(): Flow<List<MixWithTrackCount>> =
        _mixesFlow.map {
            mixes.values.map { mix ->
                val count = mixTracks.values.count { it.mixId == mix.id }
                MixWithTrackCount(mix.id, mix.name, mix.createdAt, mix.coverImagePath, count)
            }.sortedByDescending { it.createdAt }
        }

    override suspend fun getMixById(mixId: String): MixEntity? = mixes[mixId]

    override suspend fun deleteMix(mixId: String) {
        mixes.remove(mixId)
        mixTracks.entries.removeAll { it.value.mixId == mixId }
        _mixesFlow.value++
    }

    override suspend fun renameMix(id: String, name: String) {
        mixes[id]?.let { mixes[id] = it.copy(name = name) }
    }

    override suspend fun updateMixCoverImage(id: String, path: String?) {
        mixes[id]?.let { mixes[id] = it.copy(coverImagePath = path) }
    }

    override suspend fun insertMixTrack(mixTrack: MixTrackEntity) {
        mixTracks[mixTrack.id] = mixTrack
        _mixesFlow.value++
    }

    override suspend fun insertMixTracks(mixTracks: List<MixTrackEntity>) {
        mixTracks.forEach { this.mixTracks[it.id] = it }
        _mixesFlow.value++
    }

    override fun getMixTracks(mixId: String): Flow<List<MixTrackEntity>> =
        _mixesFlow.map {
            mixTracks.values.filter { it.mixId == mixId }.sortedBy { it.position }
        }

    override suspend fun getMixTracksList(mixId: String): List<MixTrackEntity> =
        mixTracks.values.filter { it.mixId == mixId }.sortedBy { it.position }

    override suspend fun getMixTrackCount(mixId: String): Int =
        mixTracks.values.count { it.mixId == mixId }

    override suspend fun removeMixTrack(mixTrackId: String) {
        mixTracks.remove(mixTrackId)
        _mixesFlow.value++
    }

    override suspend fun clearMixTracks(mixId: String) {
        mixTracks.entries.removeAll { it.value.mixId == mixId }
        _mixesFlow.value++
    }

    override suspend fun updateMixTrackPosition(id: String, position: Int) {
        mixTracks[id]?.let { mixTracks[id] = it.copy(position = position) }
    }

    override suspend fun updateCuePoints(id: String, cueInMs: Long, cueOutMs: Long?) {
        mixTracks[id]?.let { mixTracks[id] = it.copy(cueInMs = cueInMs, cueOutMs = cueOutMs) }
    }
}
