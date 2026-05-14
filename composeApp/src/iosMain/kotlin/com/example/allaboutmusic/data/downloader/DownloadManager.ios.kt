package com.example.allaboutmusic.data.downloader

actual class DownloadManager {
    actual fun startDownload(downloadId: String, trackId: String) {
        // TODO: Implement with URLSession in Phase 5
    }

    actual fun cancelDownload(downloadId: String) {
        // TODO: Implement in Phase 5
    }

    actual suspend fun getStorageUsageBytes(): Long {
        // TODO: Implement in Phase 5
        return 0L
    }
}
