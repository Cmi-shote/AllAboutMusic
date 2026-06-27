package com.example.allaboutmusic.data.repository

import com.example.allaboutmusic.data.database.TrackDao
import com.example.allaboutmusic.data.database.TrackEntity
import com.example.allaboutmusic.domain.model.MusicSource
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrackRepositoryTest {

    private val fakeMusicSource = FakeMusicSource()
    private val fakeTrackDao = FakeTrackDao()
    private val repository = TrackRepository(fakeMusicSource, fakeTrackDao)

    @Test
    fun searchTracks_delegatesToMusicSource() = runTest {
        fakeMusicSource.searchResults = listOf(
            createTrack("1", "Song A"),
            createTrack("2", "Song B")
        )
        val result = repository.searchTracks("test")
        assertEquals(2, result.size)
        assertEquals("Song A", result[0].title)
    }

    @Test
    fun searchTracks_respectsLimit() = runTest {
        fakeMusicSource.searchResults = listOf(createTrack("1", "Song"))
        repository.searchTracks("test", limit = 5)
        assertEquals(5, fakeMusicSource.lastSearchLimit)
    }

    @Test
    fun getTrack_returnsFromDbFirst() = runTest {
        val dbTrack = createTrackEntity("1", "DB Song", localPath = "/music/1.mp3")
        fakeTrackDao.tracks["1"] = dbTrack
        fakeMusicSource.trackById = createTrack("1", "API Song")

        val result = repository.getTrack("1")
        assertNotNull(result)
        assertEquals("DB Song", result.title)
        assertEquals("/music/1.mp3", result.localPath)
    }

    @Test
    fun getTrack_fallsBackToApiWhenNotInDb() = runTest {
        fakeMusicSource.trackById = createTrack("1", "API Song")

        val result = repository.getTrack("1")
        assertNotNull(result)
        assertEquals("API Song", result.title)
    }

    @Test
    fun getTrack_returnsNullWhenNotFound() = runTest {
        fakeMusicSource.trackById = null
        val result = repository.getTrack("999")
        assertNull(result)
    }

    @Test
    fun getFeatured_delegatesToMusicSource() = runTest {
        fakeMusicSource.featuredResults = listOf(
            createTrack("1", "Featured Song")
        )
        val result = repository.getFeatured()
        assertEquals(1, result.size)
        assertEquals("Featured Song", result[0].title)
    }

    @Test
    fun getByGenre_delegatesToMusicSource() = runTest {
        fakeMusicSource.genreResults = listOf(
            createTrack("1", "Rock Song")
        )
        val result = repository.getByGenre("rock")
        assertEquals(1, result.size)
        assertEquals("rock", fakeMusicSource.lastGenre)
    }

    @Test
    fun getStreamUrl_delegatesToMusicSource() = runTest {
        fakeMusicSource.streamUrl = "https://stream.example.com/1.mp3"
        val result = repository.getStreamUrl("1")
        assertEquals("https://stream.example.com/1.mp3", result)
    }

    @Test
    fun saveTrack_insertsIntoDao() = runTest {
        val track = createTrack("1", "Test Song")
        repository.saveTrack(track)
        assertNotNull(fakeTrackDao.tracks["1"])
    }

    private fun createTrack(id: String, title: String, localPath: String? = null) = Track(
        id = id,
        title = title,
        artist = "Artist",
        durationMs = 180_000L,
        localPath = localPath
    )

    private fun createTrackEntity(
        id: String,
        title: String,
        localPath: String? = null
    ) = TrackEntity(
        id = id,
        source = "jamendo",
        title = title,
        artist = "Artist",
        album = null,
        durationMs = 180_000L,
        coverUrl = null,
        licenseUrl = null,
        localPath = localPath,
        downloadedAt = null
    )
}

private class FakeMusicSource : MusicSource {
    var searchResults: List<Track> = emptyList()
    var lastSearchLimit: Int = 0
    var trackById: Track? = null
    var streamUrl: String = ""
    var downloadUrl: String = ""
    var featuredResults: List<Track> = emptyList()
    var genreResults: List<Track> = emptyList()
    var lastGenre: String = ""

    override suspend fun search(query: String, limit: Int): List<Track> {
        lastSearchLimit = limit
        return searchResults
    }

    override suspend fun getTrack(id: String): Track? = trackById
    override suspend fun getStreamUrl(trackId: String): String = streamUrl
    override suspend fun getDownloadUrl(trackId: String): String = downloadUrl
    override suspend fun getFeatured(limit: Int): List<Track> = featuredResults
    override suspend fun getByGenre(genre: String, limit: Int): List<Track> {
        lastGenre = genre
        return genreResults
    }
}

private class FakeTrackDao : TrackDao {
    val tracks = mutableMapOf<String, TrackEntity>()
    private val _downloadedFlow = MutableStateFlow<List<TrackEntity>>(emptyList())
    private val _localFlow = MutableStateFlow<List<TrackEntity>>(emptyList())

    override suspend fun insertTrack(track: TrackEntity) {
        tracks[track.id] = track
    }

    override suspend fun insertTracks(tracks: List<TrackEntity>) {
        tracks.forEach { this.tracks[it.id] = it }
    }

    override suspend fun getTrackById(id: String): TrackEntity? = tracks[id]

    override fun getDownloadedTracks(): Flow<List<TrackEntity>> = _downloadedFlow
    override suspend fun getDownloadedTracksList(): List<TrackEntity> =
        tracks.values.filter { it.localPath != null && it.source != "local" }

    override suspend fun updateLocalPath(id: String, localPath: String, downloadedAt: Long) {
        tracks[id]?.let { tracks[id] = it.copy(localPath = localPath, downloadedAt = downloadedAt) }
    }

    override suspend fun clearLocalPath(id: String) {
        tracks[id]?.let { tracks[id] = it.copy(localPath = null, downloadedAt = null) }
    }

    override suspend fun deleteTrack(id: String) { tracks.remove(id) }
    override fun getLocalTracks(): Flow<List<TrackEntity>> = _localFlow
    override suspend fun getLocalTrackIds(): List<String> =
        tracks.values.filter { it.source == "local" }.map { it.id }

    override suspend fun deleteStaleLocalTracks(activeIds: List<String>) {
        val stale = tracks.values.filter { it.source == "local" && it.id !in activeIds }.map { it.id }
        stale.forEach { tracks.remove(it) }
    }
}
