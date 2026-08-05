package com.simiscompany.allaboutmusic.domain.usecase

import com.simiscompany.allaboutmusic.data.repository.TrackRepository
import com.simiscompany.allaboutmusic.domain.model.Track

class SearchTracksUseCase(private val repository: TrackRepository) {
    suspend operator fun invoke(query: String, limit: Int = 20): List<Track> {
        return repository.searchTracks(query, limit)
    }
}
