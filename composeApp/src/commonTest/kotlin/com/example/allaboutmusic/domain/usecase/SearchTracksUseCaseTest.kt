package com.example.allaboutmusic.domain.usecase

import com.example.allaboutmusic.data.database.TrackDao
import com.example.allaboutmusic.data.database.TrackEntity
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.domain.model.MusicSource
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTracksUseCaseTest {

    @Test
    fun invoke_returnsSearchResults() = runTest {
        val tracks = listOf(
            Track(id = "1", title = "Found Song", artist = "Artist", durationMs = 120_000L)
        )
        val source = StubMusicSource(searchResults = tracks)
        val dao = StubTrackDao()
        val repository = TrackRepository(source, dao)
        val useCase = SearchTracksUseCase(repository)

        val result = useCase("Found")
        assertEquals(1, result.size)
        assertEquals("Found Song", result[0].title)
    }

    @Test
    fun invoke_returnsEmptyForNoMatch() = runTest {
        val source = StubMusicSource(searchResults = emptyList())
        val dao = StubTrackDao()
        val repository = TrackRepository(source, dao)
        val useCase = SearchTracksUseCase(repository)

        val result = useCase("nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun invoke_passesLimitToRepository() = runTest {
        var capturedLimit = 0
        val source = object : StubMusicSource() {
            override suspend fun search(query: String, limit: Int): List<Track> {
                capturedLimit = limit
                return emptyList()
            }
        }
        val dao = StubTrackDao()
        val repository = TrackRepository(source, dao)
        val useCase = SearchTracksUseCase(repository)

        useCase("test", limit = 10)
        assertEquals(10, capturedLimit)
    }
}

class GetFeaturedTracksUseCaseTest {

    @Test
    fun invoke_returnsFeaturedTracks() = runTest {
        val tracks = listOf(
            Track(id = "1", title = "Featured", artist = "Artist", durationMs = 200_000L)
        )
        val source = StubMusicSource(featuredResults = tracks)
        val dao = StubTrackDao()
        val repository = TrackRepository(source, dao)
        val useCase = GetFeaturedTracksUseCase(repository)

        val result = useCase()
        assertEquals(1, result.size)
        assertEquals("Featured", result[0].title)
    }
}

class GetTracksByGenreUseCaseTest {

    @Test
    fun invoke_returnsGenreTracks() = runTest {
        val tracks = listOf(
            Track(id = "1", title = "Rock Song", artist = "Artist", durationMs = 150_000L)
        )
        val source = StubMusicSource(genreResults = tracks)
        val dao = StubTrackDao()
        val repository = TrackRepository(source, dao)
        val useCase = GetTracksByGenreUseCase(repository)

        val result = useCase("rock")
        assertEquals(1, result.size)
        assertEquals("Rock Song", result[0].title)
    }
}

class GetStreamUrlUseCaseTest {

    @Test
    fun invoke_returnsStreamUrl() = runTest {
        val source = StubMusicSource(streamUrl = "https://stream.example.com/1.mp3")
        val dao = StubTrackDao()
        val repository = TrackRepository(source, dao)
        val useCase = GetStreamUrlUseCase(repository)

        val result = useCase("1")
        assertEquals("https://stream.example.com/1.mp3", result)
    }
}

// Shared stubs for use case tests
private open class StubMusicSource(
    private val searchResults: List<Track> = emptyList(),
    private val featuredResults: List<Track> = emptyList(),
    private val genreResults: List<Track> = emptyList(),
    private val streamUrl: String = ""
) : MusicSource {
    override suspend fun search(query: String, limit: Int): List<Track> = searchResults
    override suspend fun getTrack(id: String): Track? = null
    override suspend fun getStreamUrl(trackId: String): String = streamUrl
    override suspend fun getDownloadUrl(trackId: String): String = ""
    override suspend fun getFeatured(limit: Int): List<Track> = featuredResults
    override suspend fun getByGenre(genre: String, limit: Int): List<Track> = genreResults
}

private class StubTrackDao : TrackDao {
    override suspend fun insertTrack(track: TrackEntity) {}
    override suspend fun insertTracks(tracks: List<TrackEntity>) {}
    override suspend fun getTrackById(id: String): TrackEntity? = null
    override fun getDownloadedTracks(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getDownloadedTracksList(): List<TrackEntity> = emptyList()
    override suspend fun updateLocalPath(id: String, localPath: String, downloadedAt: Long) {}
    override suspend fun clearLocalPath(id: String) {}
    override suspend fun deleteTrack(id: String) {}
    override fun getLocalTracks(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getLocalTrackIds(): List<String> = emptyList()
    override suspend fun deleteStaleLocalTracks(activeIds: List<String>) {}
}
