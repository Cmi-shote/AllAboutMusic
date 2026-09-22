package com.simiscompany.allaboutmusic.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun readFileBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
    val file = File(path)
    if (file.isFile) file.readBytes() else null
}
