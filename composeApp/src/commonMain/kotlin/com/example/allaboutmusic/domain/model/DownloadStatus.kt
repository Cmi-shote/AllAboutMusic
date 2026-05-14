package com.example.allaboutmusic.domain.model

data class DownloadItem(
    val id: String,
    val track: Track,
    val status: Status,
    val progress: Float = 0f,
    val errorMessage: String? = null
) {
    enum class Status {
        PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELLED
    }
}
