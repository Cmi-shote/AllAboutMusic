package com.simiscompany.allaboutmusic.data.export

import com.simiscompany.allaboutmusic.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Path of the bundled logo used when a mix has no cover of its own. */
const val DEFAULT_MIX_COVER_RESOURCE = "drawable/default_mix_cover.png"

/**
 * Cover art for an export: the mix's own cover when it has one, otherwise the
 * bundled app logo, so every exported mix carries artwork. Returns null only if
 * even the bundled default can't be read.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadMixArtwork(coverImagePath: String?): ByteArray? {
    val custom = coverImagePath?.let { path -> runCatching { readFileBytes(path) }.getOrNull() }
    if (custom != null && custom.isNotEmpty()) return custom
    return runCatching { Res.readBytes(DEFAULT_MIX_COVER_RESOURCE) }.getOrNull()
}

expect suspend fun readFileBytes(path: String): ByteArray?
