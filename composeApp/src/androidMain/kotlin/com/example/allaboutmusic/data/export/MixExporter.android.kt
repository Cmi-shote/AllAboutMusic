package com.example.allaboutmusic.data.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.allaboutmusic.domain.model.MixTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

actual class MixExporter(private val context: Context) {

    actual suspend fun exportMix(
        mixName: String,
        mixTracks: List<MixTrack>,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = mixName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
            val tempFile = File(context.cacheDir, "${sanitizedName}_export.m4a")

            // Decode all tracks to PCM, respecting cue points
            val sampleRate = 44100
            val channels = 2
            val allPcm = mutableListOf<ShortArray>()
            val totalTracks = mixTracks.size

            for ((index, mixTrack) in mixTracks.withIndex()) {
                val localPath = mixTrack.localPath
                    ?: return@withContext Result.failure(Exception("Track '${mixTrack.title}' has no local file"))

                val pcm = decodeToTrimmedPcm(localPath, mixTrack.cueInMs, mixTrack.cueOutMs, sampleRate, channels)
                allPcm.add(pcm)
                onProgress((index + 1).toFloat() / (totalTracks * 2))
            }

            // Concatenate all PCM
            val totalSamples = allPcm.sumOf { it.size }
            val combined = ShortArray(totalSamples)
            var offset = 0
            for (pcm in allPcm) {
                pcm.copyInto(combined, offset)
                offset += pcm.size
            }

            // Encode to AAC M4A
            encodePcmToM4a(combined, sampleRate, channels, tempFile) { encodeProgress ->
                onProgress(0.5f + encodeProgress * 0.5f)
            }

            // Move to Music directory
            val outputPath = saveToMusicDir(tempFile, sanitizedName)
            tempFile.delete()

            Result.success(outputPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeToTrimmedPcm(
        filePath: String,
        cueInMs: Long,
        cueOutMs: Long?,
        targetSampleRate: Int,
        targetChannels: Int
    ): ShortArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)

        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex < 0) throw Exception("No audio track found in $filePath")

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: throw Exception("No mime type")

        // Seek to cue-in
        if (cueInMs > 0) {
            extractor.seekTo(cueInMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val cueOutUs = cueOutMs?.let { it * 1000 }
        val pcmChunks = mutableListOf<ShortArray>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val sampleTimeUs = extractor.sampleTime
                        // Stop feeding if past cue-out
                        if (cueOutUs != null && sampleTimeUs > cueOutUs) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
                val outputBuffer = decoder.getOutputBuffer(outputIndex) ?: continue
                val shortCount = bufferInfo.size / 2
                if (shortCount > 0) {
                    val shorts = ShortArray(shortCount)
                    outputBuffer.asShortBuffer().get(shorts)
                    pcmChunks.add(shorts)
                }
                decoder.releaseOutputBuffer(outputIndex, false)
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        val totalSize = pcmChunks.sumOf { it.size }
        val result = ShortArray(totalSize)
        var pos = 0
        for (chunk in pcmChunks) {
            chunk.copyInto(result, pos)
            pos += chunk.size
        }
        return result
    }

    private fun encodePcmToM4a(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val bitRate = 192_000
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val byteBuffer = ByteBuffer.allocate(pcm.size * 2)
        byteBuffer.asShortBuffer().put(pcm)
        byteBuffer.position(0)
        byteBuffer.limit(pcm.size * 2)

        val totalBytes = pcm.size * 2
        var bytesWritten = 0
        var inputDone = false
        var outputDone = false
        val chunkSize = 4096 // bytes per input buffer feed
        var presentationTimeUs = 0L
        val bytesPerSecond = sampleRate * channels * 2

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                    inputBuffer.clear()
                    val remaining = byteBuffer.remaining()
                    if (remaining <= 0) {
                        encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val size = minOf(inputBuffer.capacity(), remaining)
                        val oldLimit = byteBuffer.limit()
                        byteBuffer.limit(minOf(byteBuffer.position() + size, oldLimit))
                        inputBuffer.put(byteBuffer)
                        byteBuffer.limit(oldLimit)
                        bytesWritten += size
                        presentationTimeUs = (bytesWritten.toLong() * 1_000_000L) / bytesPerSecond
                        encoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
                        onProgress(bytesWritten.toFloat() / totalBytes)
                    }
                }
            }

            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outputIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // Skip codec config
                    } else if (muxerStarted && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        encoder.stop()
        encoder.release()
        if (muxerStarted) {
            muxer.stop()
            muxer.release()
        }
    }

    private fun saveToMusicDir(tempFile: File, mixName: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            // Use MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$mixName.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AllAboutMusic")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Failed to create MediaStore entry")

            context.contentResolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

            return "Music/AllAboutMusic/$mixName.m4a"
        } else {
            @Suppress("DEPRECATION")
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "AllAboutMusic"
            )
            musicDir.mkdirs()
            val outputFile = File(musicDir, "$mixName.m4a")
            tempFile.copyTo(outputFile, overwrite = true)
            return outputFile.absolutePath
        }
    }
}
