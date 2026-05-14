package com.example.allaboutmusic.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class JamendoApiService(
    private val httpClient: HttpClient,
    private val clientId: String
) {
    companion object {
        private const val BASE_URL = "https://api.jamendo.com/v3.0"
        private val REMIX_KEYWORDS = listOf("remix", "dj", "mashup", "remaster")
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

    suspend fun getFeatured(limit: Int = 20): List<JamendoTrackDto> {
        val response: JamendoResponse = httpClient.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("featured", "1")
            parameter("limit", limit)
            parameter("audioformat", "mp32")
            parameter("order", "popularity_total")
        }.body()
        response.checkError()
        return response.results.filterRemixes()
    }

    suspend fun getByGenre(genre: String, limit: Int = 20): List<JamendoTrackDto> {
        val response: JamendoResponse = httpClient.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("tags", genre)
            parameter("limit", limit)
            parameter("audioformat", "mp32")
            parameter("order", "popularity_total")
        }.body()
        response.checkError()
        return response.results.filterRemixes()
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
