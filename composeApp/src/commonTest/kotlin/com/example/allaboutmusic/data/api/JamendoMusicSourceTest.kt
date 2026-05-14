package com.example.allaboutmusic.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JamendoMappingTest {

    @Test
    fun toDomain_mapsFieldsCorrectly() {
        val dto = JamendoTrackDto(
            id = "12345",
            name = "Test Song",
            artistName = "Test Artist",
            albumName = "Test Album",
            duration = 180,
            image = "https://example.com/cover.jpg",
            audio = "https://example.com/stream.mp3",
            audiodownload = "https://example.com/download.mp3",
            shareUrl = "https://example.com/share",
            licenseCcUrl = "https://creativecommons.org/licenses/by/4.0/",
            tags = "rock pop"
        )

        val track = dto.toDomain()

        assertEquals("12345", track.id)
        assertEquals("jamendo", track.source)
        assertEquals("Test Song", track.title)
        assertEquals("Test Artist", track.artist)
        assertEquals("Test Album", track.album)
        assertEquals(180_000L, track.durationMs)
        assertEquals("https://example.com/cover.jpg", track.coverUrl)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", track.licenseUrl)
        assertNull(track.localPath)
        assertNull(track.downloadedAt)
    }

    @Test
    fun toDomain_blankAlbumBecomesNull() {
        val dto = createDto(albumName = "")
        val track = dto.toDomain()
        assertNull(track.album)
    }

    @Test
    fun toDomain_blankCoverBecomesNull() {
        val dto = createDto(image = "")
        val track = dto.toDomain()
        assertNull(track.coverUrl)
    }

    @Test
    fun toDomain_durationConvertedToMs() {
        val dto = createDto(duration = 240)
        val track = dto.toDomain()
        assertEquals(240_000L, track.durationMs)
    }

    private fun createDto(
        id: String = "1",
        name: String = "Song",
        artistName: String = "Artist",
        albumName: String = "Album",
        duration: Int = 100,
        image: String = "https://img.com/a.jpg",
        audio: String = "https://audio.com/a.mp3",
        audiodownload: String = "https://dl.com/a.mp3",
        licenseCcUrl: String = "https://cc.org/by/4.0",
        tags: String = "rock"
    ) = JamendoTrackDto(
        id = id,
        name = name,
        artistName = artistName,
        albumName = albumName,
        duration = duration,
        image = image,
        audio = audio,
        audiodownload = audiodownload,
        shareUrl = "",
        licenseCcUrl = licenseCcUrl,
        tags = tags
    )
}
