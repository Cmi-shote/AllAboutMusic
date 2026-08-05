package com.simiscompany.allaboutmusic.data.export

import com.simiscompany.allaboutmusic.domain.model.MixTrack

expect class MixExporter {
    suspend fun exportMix(
        mixName: String,
        mixTracks: List<MixTrack>,
        onProgress: (Float) -> Unit
    ): Result<String>
}
