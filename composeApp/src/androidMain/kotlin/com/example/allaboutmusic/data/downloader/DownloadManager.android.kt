package com.example.allaboutmusic.data.downloader

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

actual class DownloadManager(private val context: Context) {

    actual fun startDownload(downloadId: String, trackId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                    DownloadWorker.KEY_TRACK_ID to trackId
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("download_$downloadId")
            .addTag("download_track_$trackId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "download_$trackId",
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            )
    }

    actual fun cancelDownload(downloadId: String) {
        WorkManager.getInstance(context)
            .cancelAllWorkByTag("download_$downloadId")
    }

    actual suspend fun getStorageUsageBytes(): Long {
        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) return 0L
        return musicDir.listFiles()
            ?.filter { it.extension == "mp3" || it.name.endsWith(".mp3.tmp") }
            ?.sumOf { it.length() }
            ?: 0L
    }
}
