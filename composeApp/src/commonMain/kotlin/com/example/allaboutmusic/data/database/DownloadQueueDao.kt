package com.example.allaboutmusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadQueueEntity)

    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    suspend fun getAllList(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE id = :id")
    suspend fun getById(id: String): DownloadQueueEntity?

    @Query("SELECT * FROM download_queue WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: String): DownloadQueueEntity?

    @Query("UPDATE download_queue SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float)

    @Query("UPDATE download_queue SET status = :status, localPath = :localPath WHERE id = :id")
    suspend fun updateCompleted(id: String, status: String, localPath: String)

    @Query("UPDATE download_queue SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateFailed(id: String, status: String, errorMessage: String)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM download_queue WHERE status = 'completed'")
    suspend fun clearCompleted()
}
