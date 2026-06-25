package com.example.allaboutmusic.data.downloader

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun deleteFile(path: String): Boolean = java.io.File(path).delete()

actual fun getDeviceFreeSpaceBytes(): Long {
    val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
    return stat.availableBytes
}
