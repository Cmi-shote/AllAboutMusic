package com.simiscompany.allaboutmusic.data.export

import com.simiscompany.allaboutmusic.domain.model.MixTrack

/**
 * Tags written into an exported mix file. Deliberately not localised: these end up
 * in the file itself, where players expect stable English conventions.
 */
data class MixMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val comment: String
) {
    companion object {
        const val ALBUM = "AllAboutMusic Mixes"
        private const val MAX_NAMED_ARTISTS = 3
        private const val VARIOUS_ARTISTS = "Various Artists"
        private const val UNKNOWN_ARTIST = "Unknown Artist"

        fun forMix(mixName: String, tracks: List<MixTrack>): MixMetadata {
            val artists = tracks.map { it.artist.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val artist = when {
                artists.isEmpty() -> UNKNOWN_ARTIST
                artists.size <= MAX_NAMED_ARTISTS -> artists.joinToString(", ")
                else -> VARIOUS_ARTISTS
            }

            // Every track keeps its Jamendo CC BY credit, exports included.
            val comment = if (artists.isEmpty()) {
                ""
            } else {
                "${artists.joinToString(", ")} — via Jamendo (CC BY)"
            }

            return MixMetadata(
                title = mixName,
                artist = artist,
                album = ALBUM,
                comment = comment
            )
        }
    }
}
