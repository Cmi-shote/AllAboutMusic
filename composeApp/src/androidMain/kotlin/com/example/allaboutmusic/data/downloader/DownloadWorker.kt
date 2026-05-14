package com.example.allaboutmusic.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.allaboutmusic.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_TRACK_ID = "track_id"
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID_BASE = 1000
    }

    private val database: AppDatabase by inject()
    private val jamendoApi: com.example.allaboutmusic.data.api.JamendoApiService by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext Result.failure()
        val notificationId = downloadId.hashCode().and(0x7FFFFFFF) + NOTIFICATION_ID_BASE

        try {
            createNotificationChannel()

            // Update status to downloading
            database.downloadQueueDao().updateProgress(downloadId, "downloading", 0f)

            // Fetch fresh download URL — never use a cached one
            val downloadUrl = jamendoApi.getDownloadUrl(trackId)

            // Set up target file
            val musicDir = File(context.filesDir, "music")
            musicDir.mkdirs()
            val targetFile = File(musicDir, "$trackId.mp3")
            val tempFile = File(musicDir, "$trackId.mp3.tmp")

            // Chunked download with resume support
            var downloadedBytes = 0L
            if (tempFile.exists()) {
                downloadedBytes = tempFile.length()
            }

            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }
            connection.connect()

            val totalBytes = if (downloadedBytes > 0 && connection.responseCode == 206) {
                downloadedBytes + connection.contentLength
            } else {
                downloadedBytes = 0
                connection.contentLength.toLong()
            }

            // Show foreground notification
            setForeground(createForegroundInfo(notificationId, trackId, 0))

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, downloadedBytes > 0).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var currentBytes = downloadedBytes

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = (currentBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            database.downloadQueueDao().updateProgress(downloadId, "downloading", progress)
                            setForeground(createForegroundInfo(notificationId, trackId, (progress * 100).toInt()))
                        }
                    }
                }
            }

            // Rename temp to final
            tempFile.renameTo(targetFile)
            val localPath = targetFile.absolutePath

            // Update database
            database.downloadQueueDao().updateCompleted(downloadId, "completed", localPath)
            database.trackDao().updateLocalPath(trackId, localPath, System.currentTimeMillis())

            // Final notification
            showCompletedNotification(notificationId, trackId)

            Result.success()
        } catch (e: Exception) {
            // Clean up partial file on failure
            val musicDir = File(context.filesDir, "music")
            val tempFile = File(musicDir, "$trackId.mp3.tmp")
            tempFile.delete()

            database.downloadQueueDao().updateFailed(
                downloadId,
                "failed",
                e.message ?: "Download failed"
            )
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(notificationId: Int, trackId: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading track")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        return ForegroundInfo(notificationId, notification)
    }

    private fun showCompletedNotification(notificationId: Int, trackId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download complete")
            .setContentText("Track ready for offline playback")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }
}
