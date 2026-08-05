package com.simiscompany.allaboutmusic.domain.usecase

import com.simiscompany.allaboutmusic.data.repository.TrackRepository
import com.simiscompany.allaboutmusic.domain.model.Track

class GetFeaturedTracksUseCase(private val repository: TrackRepository) {
    suspend operator fun invoke(limit: Int = 20): List<Track> {
        return repository.getFeatured(limit)
    }
}
