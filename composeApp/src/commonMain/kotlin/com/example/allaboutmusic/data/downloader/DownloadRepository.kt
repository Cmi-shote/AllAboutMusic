package com.example.allaboutmusic.data.downloader

import com.example.allaboutmusic.data.database.DownloadQueueDao
import com.example.allaboutmusic.data.database.DownloadQueueEntity
import com.example.allaboutmusic.data.database.TrackDao
import com.example.allaboutmusic.data.database.toDomain
import com.example.allaboutmusic.data.database.toEntity
import com.example.allaboutmusic.domain.model.DownloadItem
import com.example.allaboutmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DownloadRepository(
    private val downloadQueueDao: DownloadQueueDao,
    private val trackDao: TrackDao,
    private val downloadManager: DownloadManager
) {
    companion object {
        private const val MIN_FREE_SPACE_BYTES = 50L * 1024 * 1024 // 50MB
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun enqueueDownload(track: Track): String {
        // Check device free space before starting
        val freeSpace = getDeviceFreeSpaceBytes()
        if (freeSpace < MIN_FREE_SPACE_BYTES) {
            throw InsufficientStorageException()
        }

        // Save track to DB first
        trackDao.insertTrack(track.toEntity())

        // Check if already queued
        val existing = downloadQueueDao.getByTrackId(track.id)
        if (existing != null && existing.status != "failed" && existing.status != "cancelled") {
            return existing.id
        }

        val downloadId = Uuid.random().toString()
        downloadQueueDao.insert(
            DownloadQueueEntity(
                id = downloadId,
                trackId = track.id,
                status = "pending",
                progress = 0f,
                createdAt = currentTimeMillis()
            )
        )

        downloadManager.startDownload(downloadId, track.id)
        return downloadId
    }

    fun getAllDownloads(): Flow<List<DownloadItem>> {
        return downloadQueueDao.getAll().map { entities ->
            entities.mapNotNull { entity ->
                val track = trackDao.getTrackById(entity.trackId)?.toDomain() ?: return@mapNotNull null
                DownloadItem(
                    id = entity.id,
                    track = track,
                    status = entity.status.toDownloadStatus(),
                    progress = entity.progress,
                    errorMessage = entity.errorMessage
                )
            }
        }
    }

    suspend fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
        downloadQueueDao.updateFailed(downloadId, "cancelled", "Cancelled by user")
    }

    suspend fun retryDownload(downloadId: String) {
        val item = downloadQueueDao.getById(downloadId) ?: return
        downloadQueueDao.updateProgress(downloadId, "pending", 0f)
        downloadManager.startDownload(downloadId, item.trackId)
    }

    suspend fun clearCompleted() {
        // Get completed entries before deleting so we can clean up track localPath
        val allItems = downloadQueueDao.getAllList()
        val completedItems = allItems.filter { it.status == "completed" }

        // Delete files and clear DB state BEFORE removing queue entries
        for (item in completedItems) {
            // Try localPath from download_queue first, fall back to track table
            val path = item.localPath
                ?: trackDao.getTrackById(item.trackId)?.localPath

            if (path != null) {
                deleteFile(path)
            }
            trackDao.clearLocalPath(item.trackId)
        }

        // Remove download queue entries last
        downloadQueueDao.clearCompleted()
    }

    suspend fun getStorageUsageBytes(): Long {
        return downloadManager.getStorageUsageBytes()
    }

    /**
     * Checks DB for an updated Track with localPath set (e.g., after download completed).
     * API-loaded Track objects don't have localPath — this resolves them against the DB.
     */
    suspend fun getTrackWithLocalPath(track: Track): Track? {
        val entity = trackDao.getTrackById(track.id) ?: return null
        val dbTrack = entity.toDomain()
        return if (dbTrack.localPath != null) dbTrack else null
    }
}

private fun String.toDownloadStatus(): DownloadItem.Status {
    return when (this) {
        "pending" -> DownloadItem.Status.PENDING
        "downloading" -> DownloadItem.Status.DOWNLOADING
        "completed" -> DownloadItem.Status.COMPLETED
        "failed" -> DownloadItem.Status.FAILED
        "cancelled" -> DownloadItem.Status.CANCELLED
        else -> DownloadItem.Status.PENDING
    }
}

expect fun currentTimeMillis(): Long
expect fun deleteFile(path: String): Boolean
expect fun getDeviceFreeSpaceBytes(): Long

class InsufficientStorageException : Exception(
    "Not enough storage space. Please free up some space and try again."
)
