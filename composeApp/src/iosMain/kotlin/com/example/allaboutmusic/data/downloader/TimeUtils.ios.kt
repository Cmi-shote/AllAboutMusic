package com.example.allaboutmusic.data.downloader

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun deleteFile(path: String): Boolean {
    return platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(path, null)
}
