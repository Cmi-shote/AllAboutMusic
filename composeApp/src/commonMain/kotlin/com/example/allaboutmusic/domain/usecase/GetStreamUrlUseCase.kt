package com.example.allaboutmusic.domain.usecase

import com.example.allaboutmusic.data.repository.TrackRepository

class GetStreamUrlUseCase(private val repository: TrackRepository) {
    suspend operator fun invoke(trackId: String): String {
        return repository.getStreamUrl(trackId)
    }
}
