package com.example.allaboutmusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MixDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMix(mix: MixEntity)

    @Query("SELECT * FROM mix ORDER BY createdAt DESC")
    fun getAllMixes(): Flow<List<MixEntity>>

    @Query("SELECT m.*, COUNT(mt.id) AS trackCount FROM mix m LEFT JOIN mix_track mt ON m.id = mt.mixId GROUP BY m.id ORDER BY m.createdAt DESC")
    fun getAllMixesWithTrackCount(): Flow<List<MixWithTrackCount>>

    @Query("SELECT * FROM mix WHERE id = :mixId")
    suspend fun getMixById(mixId: String): MixEntity?

    @Query("DELETE FROM mix WHERE id = :mixId")
    suspend fun deleteMix(mixId: String)

    @Query("UPDATE mix SET name = :name WHERE id = :id")
    suspend fun renameMix(id: String, name: String)

    // Mix tracks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixTrack(mixTrack: MixTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixTracks(mixTracks: List<MixTrackEntity>)

    @Query("SELECT * FROM mix_track WHERE mixId = :mixId ORDER BY position ASC")
    fun getMixTracks(mixId: String): Flow<List<MixTrackEntity>>

    @Query("SELECT * FROM mix_track WHERE mixId = :mixId ORDER BY position ASC")
    suspend fun getMixTracksList(mixId: String): List<MixTrackEntity>

    @Query("SELECT COUNT(*) FROM mix_track WHERE mixId = :mixId")
    suspend fun getMixTrackCount(mixId: String): Int

    @Query("DELETE FROM mix_track WHERE id = :mixTrackId")
    suspend fun removeMixTrack(mixTrackId: String)

    @Query("DELETE FROM mix_track WHERE mixId = :mixId")
    suspend fun clearMixTracks(mixId: String)

    @Query("UPDATE mix_track SET position = :position WHERE id = :id")
    suspend fun updateMixTrackPosition(id: String, position: Int)

    @Query("UPDATE mix_track SET cueInMs = :cueInMs, cueOutMs = :cueOutMs WHERE id = :id")
    suspend fun updateCuePoints(id: String, cueInMs: Long, cueOutMs: Long?)
}
