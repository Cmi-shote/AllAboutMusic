package com.simiscompany.allaboutmusic.data.export

import com.simiscompany.allaboutmusic.domain.model.MixTrack
import kotlin.test.Test
import kotlin.test.assertEquals

class MixMetadataTest {

    @Test
    fun forMix_listsArtistsWhenThereAreOnlyAFew() {
        val metadata = MixMetadata.forMix("Evening Set", tracks("Aurora Bloom", "Kite String"))

        assertEquals("Evening Set", metadata.title)
        assertEquals("Aurora Bloom, Kite String", metadata.artist)
        assertEquals(MixMetadata.ALBUM, metadata.album)
    }

    @Test
    fun forMix_collapsesToVariousArtistsBeyondThree() {
        val metadata = MixMetadata.forMix("Long Set", tracks("A", "B", "C", "D"))

        assertEquals("Various Artists", metadata.artist)
    }

    @Test
    fun forMix_deduplicatesRepeatedArtists() {
        val metadata = MixMetadata.forMix("Set", tracks("Aurora Bloom", "Aurora Bloom"))

        assertEquals("Aurora Bloom", metadata.artist)
    }

    @Test
    fun forMix_creditsEveryArtistInTheComment() {
        val metadata = MixMetadata.forMix("Set", tracks("A", "B", "C", "D"))

        // Attribution names them all even when the artist tag collapses.
        assertEquals("A, B, C, D — via Jamendo (CC BY)", metadata.comment)
    }

    @Test
    fun forMix_handlesTracksWithNoArtist() {
        val metadata = MixMetadata.forMix("Set", tracks("", "  "))

        assertEquals("Unknown Artist", metadata.artist)
        assertEquals("", metadata.comment)
    }

    private fun tracks(vararg artists: String): List<MixTrack> =
        artists.mapIndexed { index, artist ->
            MixTrack(
                id = "mt$index",
                mixId = "mix",
                trackId = "t$index",
                position = index,
                title = "Track $index",
                artist = artist,
                durationMs = 1000L
            )
        }
}
