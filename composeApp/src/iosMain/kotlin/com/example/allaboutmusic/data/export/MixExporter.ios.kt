package com.example.allaboutmusic.data.export

import com.example.allaboutmusic.domain.model.MixTrack
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVAssetExportPresetAppleM4A
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetExportSessionStatusFailed
import platform.AVFoundation.AVFileTypeAppleM4A
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.insertTimeRange
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.AVFoundation.AVMediaTypeAudio
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class MixExporter {

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun exportMix(
        mixName: String,
        mixTracks: List<MixTrack>,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            val composition = AVMutableComposition()
            val compositionTrack = composition.addMutableTrackWithMediaType(
                AVMediaTypeAudio,
                preferredTrackID = 0
            ) ?: return@withContext Result.failure(Exception("Failed to create composition track"))

            var insertTime = kCMTimeZero
            val totalTracks = mixTracks.size

            for ((index, mixTrack) in mixTracks.withIndex()) {
                val localPath = mixTrack.localPath
                    ?: return@withContext Result.failure(Exception("Track '${mixTrack.title}' has no local file"))

                val url = NSURL.fileURLWithPath(localPath)
                val asset = AVURLAsset(uRL = url, options = null)

                val assetTracks = asset.tracksWithMediaType(AVMediaTypeAudio)
                val assetAudioTrack = assetTracks.firstOrNull()
                    ?: return@withContext Result.failure(Exception("No audio track in '${mixTrack.title}'"))

                // Calculate cue points
                val cueInTime = CMTimeMake(value = mixTrack.cueInMs, timescale = 1000)
                val duration = if (mixTrack.cueOutMs != null) {
                    CMTimeMake(value = mixTrack.cueOutMs - mixTrack.cueInMs, timescale = 1000)
                } else {
                    // Duration from cueIn to end of asset
                    val assetDuration = asset.duration
                    CMTimeMake(
                        value = (assetDuration.value * 1000 / assetDuration.timescale) - mixTrack.cueInMs,
                        timescale = 1000
                    )
                }

                val timeRange = CMTimeRangeMake(start = cueInTime, duration = duration)

                @Suppress("UNCHECKED_CAST")
                compositionTrack.insertTimeRange(
                    timeRange = timeRange,
                    ofTrack = assetAudioTrack as platform.AVFoundation.AVAssetTrack,
                    atTime = insertTime,
                    error = null
                )

                insertTime = CMTimeMake(
                    value = insertTime.value * 1000 / insertTime.timescale + duration.value * 1000 / duration.timescale,
                    timescale = 1000
                )

                onProgress((index + 1).toFloat() / (totalTracks + 1))
            }

            // Export
            val sanitizedName = mixName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
            val documentsPath = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).first() as String
            val exportsDir = "$documentsPath/exports"
            NSFileManager.defaultManager.createDirectoryAtPath(exportsDir, true, null, null)
            val outputPath = "$exportsDir/$sanitizedName.m4a"
            val outputUrl = NSURL.fileURLWithPath(outputPath)

            // Delete existing file if present
            NSFileManager.defaultManager.removeItemAtPath(outputPath, null)

            val exportSession = AVAssetExportSession(
                asset = composition,
                presetName = AVAssetExportPresetAppleM4A
            )
            exportSession.outputFileType = AVFileTypeAppleM4A
            exportSession.outputURL = outputUrl

            val result = suspendCoroutine { continuation ->
                exportSession.exportAsynchronouslyWithCompletionHandler {
                    when (exportSession.status) {
                        AVAssetExportSessionStatusCompleted -> continuation.resume(Result.success(outputPath))
                        AVAssetExportSessionStatusFailed -> continuation.resume(
                            Result.failure(exportSession.error?.let { Exception(it.localizedDescription) }
                                ?: Exception("Export failed"))
                        )
                        else -> continuation.resume(Result.failure(Exception("Export status: ${exportSession.status}")))
                    }
                }
            }

            onProgress(1f)
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
