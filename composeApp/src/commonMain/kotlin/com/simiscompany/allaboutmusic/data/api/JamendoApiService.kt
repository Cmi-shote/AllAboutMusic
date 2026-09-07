package com.simiscompany.allaboutmusic.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay

class JamendoApiService(
    private val httpClient: HttpClient,
    private val clientId: String
) {
    companion object {
        private const val BASE_URL = "https://api.jamendo.com/v3.0"
        private val REMIX_KEYWORDS = listOf("remix", "dj", "mashup", "remaster")

        // Jamendo intermittently answers curated queries with HTTP 200,
        // status "success" and an empty result set — roughly one call in four.
        // Curated feeds are never legitimately empty, so treat an empty
        // success as transient and retry before believing it.
        private const val CURATED_ATTEMPTS = 3
        private const val CURATED_RETRY_DELAY_MS = 400L
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<JamendoTrackDto> {
        val response: JamendoResponse = httpClient.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("search", query)
            parameter("limit", limit)
            parameter("audioformat", "mp32")
            parameter("include", "musicinfo")
        }.body()
        response.checkError()
        return response.results.filterRemixes()
    }

    suspend fun getTrackById(id: String): JamendoTrackDto? {
        val response: JamendoResponse = httpClient.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("id", id)
            parameter("audioformat", "mp32")
        }.body()
        response.checkError()
        return response.results.firstOrNull()
    }

    suspend fun getStreamUrl(trackId: String): String {
        val track = getTrackById(trackId)
            ?: throw IllegalArgumentException("Track not found: $trackId")
        return track.audio
    }

    suspend fun getDownloadUrl(trackId: String): String {
        val track = getTrackById(trackId)
            ?: throw IllegalArgumentException("Track not found: $trackId")
        return track.audiodownload
    }

    suspend fun getFeatured(limit: Int = 20): List<JamendoTrackDto> = curated {
        httpClient.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("featured", "1")
            parameter("limit", limit)
            parameter("audioformat", "mp32")
            parameter("order", "popularity_total")
        }.body()
    }

    suspend fun getByGenre(genre: String, limit: Int = 20): List<JamendoTrackDto> = curated {
        httpClient.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("tags", genre)
            parameter("limit", limit)
            parameter("audioformat", "mp32")
            parameter("order", "popularity_total")
        }.body()
    }

    /**
     * Runs a curated request, retrying while the API reports success but hands
     * back nothing. Search deliberately does not go through here: an empty
     * search result is a real answer, not a failure.
     */
    private suspend fun curated(
        request: suspend () -> JamendoResponse
    ): List<JamendoTrackDto> {
        repeat(CURATED_ATTEMPTS) { attempt ->
            val response = request()
            response.checkError()
            if (response.results.isNotEmpty()) return response.results.filterRemixes()
            if (attempt < CURATED_ATTEMPTS - 1) {
                delay(CURATED_RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return emptyList()
    }

    private fun JamendoResponse.checkError() {
        if (headers.status == "failed") {
            throw JamendoApiException(headers.code, headers.errorMessage)
        }
    }

    private fun List<JamendoTrackDto>.filterRemixes(): List<JamendoTrackDto> {
        return filter { track ->
            val nameLower = track.name.lowercase()
            val tagsLower = track.tags.lowercase()
            REMIX_KEYWORDS.none { keyword ->
                nameLower.contains(keyword) || tagsLower.contains(keyword)
            }
        }
    }
}
