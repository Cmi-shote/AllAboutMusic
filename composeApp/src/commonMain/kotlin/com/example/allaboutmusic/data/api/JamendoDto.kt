package com.example.allaboutmusic.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JamendoResponse(
    val headers: JamendoHeaders,
    val results: List<JamendoTrackDto>
)

@Serializable
data class JamendoHeaders(
    val status: String,
    val code: Int,
    @SerialName("error_message") val errorMessage: String = "",
    @SerialName("results_count") val resultsCount: Int = 0
)

@Serializable
data class JamendoTrackDto(
    val id: String,
    val name: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_name") val albumName: String = "",
    val duration: Int = 0,
    val image: String = "",
    val audio: String = "",
    val audiodownload: String = "",
    @SerialName("shareurl") val shareUrl: String = "",
    @SerialName("license_ccurl") val licenseCcUrl: String = "",
    val tags: String = ""
)
