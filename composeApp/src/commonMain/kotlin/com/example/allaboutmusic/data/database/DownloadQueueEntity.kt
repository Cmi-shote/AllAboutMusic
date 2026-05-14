package com.example.allaboutmusic.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_queue",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"]
        )
    ],
    indices = [Index("trackId")]
)
data class DownloadQueueEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val status: String = "pending",
    val progress: Float = 0f,
    val localPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long
)
