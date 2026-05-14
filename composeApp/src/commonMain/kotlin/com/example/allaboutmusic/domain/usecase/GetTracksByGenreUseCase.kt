package com.example.allaboutmusic.domain.usecase

import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.domain.model.Track

class GetTracksByGenreUseCase(private val repository: TrackRepository) {
    suspend operator fun invoke(genre: String, limit: Int = 20): List<Track> {
        return repository.getByGenre(genre, limit)
    }
}
