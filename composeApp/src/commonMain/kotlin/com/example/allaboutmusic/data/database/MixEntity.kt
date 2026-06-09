package com.example.allaboutmusic.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mix")
data class MixEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

data class MixWithTrackCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val trackCount: Int
)

@Entity(
    tableName = "mix_track",
    foreignKeys = [
        ForeignKey(
            entity = MixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mixId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mixId")]
)
data class MixTrackEntity(
    @PrimaryKey val id: String,
    val mixId: String,
    val trackId: String,
    val position: Int,
    val cueInMs: Long = 0L,
    val cueOutMs: Long? = null,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val coverUrl: String? = null,
    val localPath: String? = null
)
