package com.example.allaboutmusic.data.export

import com.example.allaboutmusic.domain.model.MixTrack

expect class MixExporter {
    suspend fun exportMix(
        mixName: String,
        mixTracks: List<MixTrack>,
        onProgress: (Float) -> Unit
    ): Result<String>
}
