package com.example.allaboutmusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM track WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM track WHERE localPath IS NOT NULL ORDER BY downloadedAt DESC")
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE localPath IS NOT NULL ORDER BY downloadedAt DESC")
    suspend fun getDownloadedTracksList(): List<TrackEntity>

    @Query("UPDATE track SET localPath = :localPath, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateLocalPath(id: String, localPath: String, downloadedAt: Long)

    @Query("UPDATE track SET localPath = NULL, downloadedAt = NULL WHERE id = :id")
    suspend fun clearLocalPath(id: String)

    @Query("DELETE FROM track WHERE id = :id")
    suspend fun deleteTrack(id: String)
}
