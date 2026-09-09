package com.simiscompany.allaboutmusic.data.export

import com.simiscompany.allaboutmusic.domain.model.MixTrack

expect class MixExporter {
    suspend fun exportMix(
        metadata: MixMetadata,
        mixTracks: List<MixTrack>,
        artwork: ByteArray?,
        onProgress: (Float) -> Unit
    ): Result<String>
}
