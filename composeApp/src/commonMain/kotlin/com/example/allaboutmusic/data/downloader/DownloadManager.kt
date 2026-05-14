package com.example.allaboutmusic.data.downloader

expect class DownloadManager {
    fun startDownload(downloadId: String, trackId: String)
    fun cancelDownload(downloadId: String)
    suspend fun getStorageUsageBytes(): Long
}
