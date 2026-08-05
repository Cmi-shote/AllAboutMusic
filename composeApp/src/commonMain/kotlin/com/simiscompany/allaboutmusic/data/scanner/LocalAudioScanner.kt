package com.simiscompany.allaboutmusic.data.scanner

import com.simiscompany.allaboutmusic.domain.model.Track

expect class LocalAudioScanner {
    suspend fun scanLibrary(): List<Track>
    suspend fun hasPermission(): Boolean
}
