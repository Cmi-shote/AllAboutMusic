package com.simiscompany.allaboutmusic.data.export

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Rewrites an MP4 file's `moov` box so it carries the mix's tags and cover art.
 *
 * Where `moov` sits decides how much work that is. MediaMuxer reserves space for it
 * up front and pads the remainder with a `free` box, so the usual layout is
 * `ftyp, moov, free, mdat` — growing `moov` there would push the audio down the file
 * and invalidate every chunk offset. Three cases, cheapest first:
 *
 *  1. `moov` is last: grow it in place, nothing else moves.
 *  2. A `free` box follows it with room to spare: take the space from that box, so
 *     the media stays exactly where it is.
 *  3. Otherwise: rewrite the file with the media shifted, patching `stco`/`co64`.
 *
 * MediaMuxer writes `mdat` with a 64-bit size, so extended headers have to be read;
 * a `moov` with one is bailed on, since the rebuilt box always uses a 32-bit header.
 */
internal object Mp4MetadataInserter {

    private const val BOX_HEADER_SIZE = 8L
    private const val SIZE_EXTENDED_64_BIT = 1L
    private const val SIZE_TO_END_OF_FILE = 0L
    private const val EXTENDED_HEADER_SIZE = 16L
    private const val COPY_BUFFER_SIZE = 64 * 1024

    /** Returns true if the metadata was written. */
    fun insert(file: File, metadata: MixMetadata, artwork: ByteArray?): Boolean {
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                val boxes = topLevelBoxes(raf) ?: return false
                val moovIndex = boxes.indexOfFirst { it.type == "moov" }
                if (moovIndex < 0) return false
                val moov = boxes[moovIndex]
                // The rebuilt moov always carries a plain 32-bit header.
                if (moov.headerSize != BOX_HEADER_SIZE) return false

                val original = ByteArray(moov.size.toInt())
                raf.seek(moov.offset)
                raf.readFully(original)

                val tagged = Mp4Metadata.moovWithMetadata(original, metadata, artwork) ?: return false
                val delta = tagged.size - original.size
                val moovEnd = moov.offset + moov.size
                val next = boxes.getOrNull(moovIndex + 1)

                when {
                    moovEnd == raf.length() -> {
                        raf.seek(moov.offset)
                        raf.write(tagged)
                        raf.setLength(moov.offset + tagged.size)
                        true
                    }

                    next != null && next.isFree && next.size - delta >= BOX_HEADER_SIZE -> {
                        raf.seek(moov.offset)
                        raf.write(tagged)
                        writeFreeBoxHeader(raf, next.size - delta)
                        true
                    }

                    else -> rewriteWithShiftedMedia(file, raf, moov, tagged, delta, moovEnd)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Case 3: copy the file with the enlarged `moov` in place, sliding everything after
     * it down by [delta] and correcting the chunk offsets that point into it.
     */
    private fun rewriteWithShiftedMedia(
        file: File,
        raf: RandomAccessFile,
        moov: Box,
        tagged: ByteArray,
        delta: Int,
        moovEnd: Long
    ): Boolean {
        val shifted = Mp4Metadata.withChunkOffsetsShifted(tagged, delta, moovEnd) ?: return false

        val rewritten = File(file.parentFile, "${file.name}.tagging")
        try {
            rewritten.outputStream().use { output ->
                raf.seek(0)
                copyBytes(raf, output, moov.offset)
                output.write(shifted)
                raf.seek(moovEnd)
                copyBytes(raf, output, raf.length() - moovEnd)
            }
            // The open handle is closed by the caller's `use`; swap the file underneath it.
            if (!rewritten.renameTo(file)) {
                rewritten.copyTo(file, overwrite = true)
                rewritten.delete()
            }
            return true
        } catch (_: Exception) {
            rewritten.delete()
            return false
        }
    }

    private fun copyBytes(raf: RandomAccessFile, output: OutputStream, count: Long) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = count
        while (remaining > 0) {
            val read = raf.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun writeFreeBoxHeader(raf: RandomAccessFile, size: Long) {
        raf.writeInt(size.toInt())
        raf.write("free".encodeToByteArray())
    }

    private data class Box(
        val offset: Long,
        val size: Long,
        val type: String,
        val headerSize: Long
    ) {
        val isFree: Boolean get() = type == "free" || type == "skip"
    }

    /** Reads the top-level box list, or null if the file can't be walked cleanly. */
    private fun topLevelBoxes(raf: RandomAccessFile): List<Box>? {
        val fileLength = raf.length()
        val boxes = mutableListOf<Box>()
        var offset = 0L

        while (offset + BOX_HEADER_SIZE <= fileLength) {
            raf.seek(offset)
            val size = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { raf.readFully(it) }.decodeToString()

            var headerSize = BOX_HEADER_SIZE
            val boxSize = when (size) {
                // size 1 means the real size follows the header as a 64-bit value;
                // MediaMuxer writes mdat this way.
                SIZE_EXTENDED_64_BIT -> {
                    if (offset + EXTENDED_HEADER_SIZE > fileLength) return null
                    headerSize = EXTENDED_HEADER_SIZE
                    raf.readLong()
                }
                SIZE_TO_END_OF_FILE -> fileLength - offset
                else -> size
            }
            if (boxSize < headerSize || offset + boxSize > fileLength) return null

            boxes.add(Box(offset, boxSize, type, headerSize))
            offset += boxSize
        }

        return if (offset == fileLength) boxes else null
    }
}
