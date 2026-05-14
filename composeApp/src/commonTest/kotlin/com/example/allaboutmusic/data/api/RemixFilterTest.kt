package com.example.allaboutmusic.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemixFilterTest {

    private val remixKeywords = listOf("remix", "dj", "mashup", "remaster")

    private fun List<JamendoTrackDto>.filterRemixes(): List<JamendoTrackDto> {
        return filter { track ->
            val nameLower = track.name.lowercase()
            val tagsLower = track.tags.lowercase()
            remixKeywords.none { keyword ->
                nameLower.contains(keyword) || tagsLower.contains(keyword)
            }
        }
    }

    @Test
    fun filtersRemixInName() {
        val tracks = listOf(
            createDto(name = "Cool Song Remix", tags = "pop"),
            createDto(name = "Original Song", tags = "pop")
        )
        val filtered = tracks.filterRemixes()
        assertEquals(1, filtered.size)
        assertEquals("Original Song", filtered[0].name)
    }

    @Test
    fun filtersDjInTags() {
        val tracks = listOf(
            createDto(name = "Beat Drop", tags = "dj electronic"),
            createDto(name = "Chill Vibes", tags = "ambient")
        )
        val filtered = tracks.filterRemixes()
        assertEquals(1, filtered.size)
        assertEquals("Chill Vibes", filtered[0].name)
    }

    @Test
    fun filtersMashupInName() {
        val tracks = listOf(
            createDto(name = "Epic Mashup 2024", tags = "pop")
        )
        val filtered = tracks.filterRemixes()
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun filtersRemasterInTags() {
        val tracks = listOf(
            createDto(name = "Classic Hit", tags = "remaster classic rock")
        )
        val filtered = tracks.filterRemixes()
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun caseInsensitiveFiltering() {
        val tracks = listOf(
            createDto(name = "Song REMIX", tags = "pop"),
            createDto(name = "DJ Mix", tags = "electronic"),
            createDto(name = "Good Song", tags = "MASHUP")
        )
        val filtered = tracks.filterRemixes()
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun keepsOriginalTracks() {
        val tracks = listOf(
            createDto(name = "Beautiful Day", tags = "pop rock"),
            createDto(name = "Night Drive", tags = "electronic ambient"),
            createDto(name = "Jazz Cafe", tags = "jazz smooth")
        )
        val filtered = tracks.filterRemixes()
        assertEquals(3, filtered.size)
    }

    private fun createDto(
        name: String = "Song",
        tags: String = "rock"
    ) = JamendoTrackDto(
        id = "1",
        name = name,
        artistName = "Artist",
        albumName = "Album",
        duration = 100,
        image = "",
        audio = "",
        audiodownload = "",
        shareUrl = "",
        licenseCcUrl = "",
        tags = tags
    )
}
