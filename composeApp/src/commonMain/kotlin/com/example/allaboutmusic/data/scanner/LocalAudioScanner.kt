package com.example.allaboutmusic.data.scanner

import com.example.allaboutmusic.domain.model.Track

expect class LocalAudioScanner {
    suspend fun scanLibrary(): List<Track>
    suspend fun hasPermission(): Boolean
}
