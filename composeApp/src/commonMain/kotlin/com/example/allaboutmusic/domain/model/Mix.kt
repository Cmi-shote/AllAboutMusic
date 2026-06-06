package com.example.allaboutmusic.domain.model

data class Mix(
    val id: String,
    val name: String,
    val createdAt: Long,
    val trackCount: Int = 0
)

data class MixTrack(
    val id: String,
    val mixId: String,
    val trackId: String,
    val position: Int,
    val cueInMs: Long = 0L,
    val cueOutMs: Long? = null, // null = play to end
    val title: String,
    val artist: String,
    val durationMs: Long,
    val coverUrl: String? = null,
    val localPath: String? = null
)
