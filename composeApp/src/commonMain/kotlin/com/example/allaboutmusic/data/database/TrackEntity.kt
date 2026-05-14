package com.example.allaboutmusic.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track")
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: String = "jamendo",
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val coverUrl: String? = null,
    val licenseUrl: String? = null,
    val localPath: String? = null,
    val downloadedAt: Long? = null
)
