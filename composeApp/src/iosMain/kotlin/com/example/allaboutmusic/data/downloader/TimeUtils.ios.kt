package com.example.allaboutmusic.data.downloader

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

@OptIn(ExperimentalForeignApi::class)
actual fun deleteFile(path: String): Boolean {
    return NSFileManager.defaultManager.removeItemAtPath(path, null)
}

@OptIn(ExperimentalForeignApi::class)
actual fun getDeviceFreeSpaceBytes(): Long {
    val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
    val freeSize = attrs?.get(NSFileSystemFreeSize) as? Long
    return freeSize ?: Long.MAX_VALUE
}
