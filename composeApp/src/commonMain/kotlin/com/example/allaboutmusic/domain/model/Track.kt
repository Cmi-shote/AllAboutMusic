package com.example.allaboutmusic.domain.model

data class Track(
    val id: String,
    val source: String = "jamendo",
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val coverUrl: String? = null,
    val licenseUrl: String? = null,
    val localPath: String? = null,
    val downloadedAt: Long? = null
) {
    val isDownloaded: Boolean get() = localPath != null
}
