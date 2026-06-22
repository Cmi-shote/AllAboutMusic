package com.example.allaboutmusic.data.downloader

import com.example.allaboutmusic.data.api.JamendoApiService
import com.example.allaboutmusic.data.database.AppDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual class DownloadManager(
    private val database: AppDatabase,
    private val jamendoApi: JamendoApiService
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val musicDir: String = NSHomeDirectory() + "/Documents/music"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(
            musicDir, true, null, null
        )
    }

    actual fun startDownload(downloadId: String, trackId: String) {
        scope.launch {
            try {
                database.downloadQueueDao().updateProgress(downloadId, "downloading", 0f)

                val downloadUrl = jamendoApi.getDownloadUrl(trackId)
                val nsUrl = NSURL.URLWithString(downloadUrl) ?: throw Exception("Invalid download URL")

                val data = NSData.dataWithContentsOfURL(nsUrl)
                    ?: throw Exception("Failed to download track data")

                val targetPath = "$musicDir/$trackId.mp3"
                val success = data.writeToFile(targetPath, true)

                if (!success) throw Exception("Failed to write file to disk")

                database.downloadQueueDao().updateCompleted(downloadId, "completed", targetPath)
                database.trackDao().updateLocalPath(trackId, targetPath, currentTimeMillis())

            } catch (e: Exception) {
                val tempPath = "$musicDir/$trackId.mp3"
                NSFileManager.defaultManager.removeItemAtPath(tempPath, null)

                database.downloadQueueDao().updateFailed(
                    downloadId,
                    "failed",
                    e.message ?: "Download failed"
                )
            }
        }
    }

    actual fun cancelDownload(downloadId: String) {
        scope.launch {
            database.downloadQueueDao().updateFailed(downloadId, "cancelled", "Cancelled by user")
        }
    }

    actual suspend fun getStorageUsageBytes(): Long {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(musicDir, null) ?: return 0L

        @Suppress("UNCHECKED_CAST")
        val files = contents as List<String>
        var total = 0L
        for (fileName in files) {
            if (!fileName.endsWith(".mp3")) continue
            val attrs = fm.attributesOfItemAtPath("$musicDir/$fileName", null)
            val size = attrs?.get(NSFileSize) as? Number
            if (size != null) total += size.toLong()
        }
        return total
    }
}
