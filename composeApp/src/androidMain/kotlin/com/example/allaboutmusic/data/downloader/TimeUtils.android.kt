package com.example.allaboutmusic.data.downloader

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun deleteFile(path: String): Boolean = java.io.File(path).delete()
