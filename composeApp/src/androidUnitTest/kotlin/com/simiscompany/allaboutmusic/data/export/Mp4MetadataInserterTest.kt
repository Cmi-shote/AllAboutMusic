package com.simiscompany.allaboutmusic.data.export

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Mp4MetadataInserterTest {

    private val metadata = MixMetadata("Friday Set", "Aurora Bloom", MixMetadata.ALBUM, "credit")
    private val artwork = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(32) { 5 }

    @Test
    fun insert_writesTagsAndLeavesMoovLast() {
        val file = mp4File(moovLast = true)
        val originalMoovSize = boxSize(file.readBytes(), moovOffset(file))

        assertTrue(Mp4MetadataInserter.insert(file, metadata, artwork))

        val bytes = file.readBytes()
        val moovOffset = moovOffset(file)
        assertTrue(boxSize(bytes, moovOffset) > originalMoovSize)
        // moov still ends exactly at EOF, so no chunk offset ahead of it moved.
        assertEquals(bytes.size, moovOffset + boxSize(bytes, moovOffset))
        assertTrue(bytes.decodeToString(moovOffset, bytes.size).contains("Friday Set"))
    }

    @Test
    fun insert_leavesMediaDataUntouched() {
        val file = mp4File(moovLast = true)
        val before = file.readBytes()
        val mdat = before.copyOfRange(FTYP.size, FTYP.size + MDAT_SIZE)

        Mp4MetadataInserter.insert(file, metadata, artwork)

        val after = file.readBytes()
        assertContentEquals(mdat, after.copyOfRange(FTYP.size, FTYP.size + MDAT_SIZE))
    }

    @Test
    fun insert_takesSpaceFromAFreeBoxSoTheMediaStaysPut() {
        // MediaMuxer's layout: ftyp, moov, free, mdat with room reserved up front.
        val file = moovFirstFile(freePadding = artwork.size + 4096)
        val sizeBefore = file.length()
        val offsetsBefore = chunkOffsetsOf(file)
        val mdatBefore = mdatBytes(file)

        assertTrue(Mp4MetadataInserter.insert(file, metadata, artwork))

        assertEquals(sizeBefore, file.length())
        assertEquals(offsetsBefore, chunkOffsetsOf(file))   // nothing moved, nothing to patch
        assertContentEquals(mdatBefore, mdatBytes(file))
        assertTrue(file.readBytes().decodeToString().contains("Friday Set"))
    }

    @Test
    fun insert_shiftsTheMediaAndPatchesChunkOffsetsWhenThereIsNoRoom() {
        val file = moovFirstFile(freePadding = 16)   // nowhere near enough for the cover
        val sizeBefore = file.length()
        val offsetsBefore = chunkOffsetsOf(file)
        val mdatBefore = mdatBytes(file)

        assertTrue(Mp4MetadataInserter.insert(file, metadata, artwork))

        val grewBy = file.length() - sizeBefore
        assertTrue(grewBy > 0)
        // Every chunk offset moved with the media it points at.
        assertEquals(offsetsBefore.map { it + grewBy }, chunkOffsetsOf(file))
        assertContentEquals(mdatBefore, mdatBytes(file))
        assertTrue(file.readBytes().decodeToString().contains("Friday Set"))
    }

    /** The mdat payload, skipping whichever header form the fixture used. */
    private fun mdatBytes(file: File): ByteArray {
        val bytes = file.readBytes()
        val at = indexOf(bytes, "mdat")
        val extended = boxSize(bytes, at) == 1
        val start = at + if (extended) 16 else 8
        return bytes.copyOfRange(start, start + MDAT_SIZE - if (extended) 16 else 8)
    }

    @Test
    fun insert_doesNothingWhenThereAreNoTagsToWrite() {
        val file = mp4File(moovLast = true)
        val before = file.readBytes()
        val empty = MixMetadata(title = "", artist = "", album = "", comment = "")

        assertFalse(Mp4MetadataInserter.insert(file, empty, null))
        assertContentEquals(before, file.readBytes())
    }

    @Test
    fun insert_handlesAnMdatWithA64BitSize() {
        // MediaMuxer writes mdat as size 1 followed by a 64-bit largesize.
        val file = moovFirstFile(freePadding = 16, extendedMdat = true)
        val sizeBefore = file.length()
        val offsetsBefore = chunkOffsetsOf(file)
        val mdatBefore = mdatBytes(file)

        assertTrue(Mp4MetadataInserter.insert(file, metadata, artwork))

        val grewBy = file.length() - sizeBefore
        assertTrue(grewBy > 0)
        assertEquals(offsetsBefore.map { it + grewBy }, chunkOffsetsOf(file))
        assertContentEquals(mdatBefore, mdatBytes(file))
        assertTrue(file.readBytes().decodeToString().contains("Friday Set"))
    }

    @Test
    fun insert_refusesATruncatedFile() {
        val file = File.createTempFile("truncated", ".m4a").apply {
            deleteOnExit()
            writeBytes(FTYP + byteArrayOf(0, 0, 0))
        }

        assertFalse(Mp4MetadataInserter.insert(file, metadata, artwork))
    }

    // --- fixtures ---

    /** ftyp, mdat, moov — what a muxer that appends its index produces. */
    private fun mp4File(moovLast: Boolean): File {
        val mdat = box("mdat", ByteArray(MDAT_SIZE - 8) { it.toByte() })
        val moov = moovBox(chunkOffsets = listOf((FTYP.size + 8).toLong()))
        val body = if (moovLast) mdat + moov else moov + mdat
        return writeFixture(FTYP + body)
    }

    /** ftyp, moov, free, mdat — MediaMuxer's layout, with reserved space up front. */
    private fun moovFirstFile(freePadding: Int, extendedMdat: Boolean = false): File {
        val moovSize = moovBox(chunkOffsets = listOf(0L)).size
        val mdatOffset = FTYP.size + moovSize + freePadding
        val headerSize = if (extendedMdat) 16 else 8
        val moov = moovBox(chunkOffsets = listOf((mdatOffset + headerSize).toLong()))
        val free = box("free", ByteArray(freePadding - 8))
        val payload = ByteArray(MDAT_SIZE - headerSize) { it.toByte() }
        val mdat = if (extendedMdat) extendedBox("mdat", payload) else box("mdat", payload)
        return writeFixture(FTYP + moov + free + mdat)
    }

    /** A box using the size-1 form: 32-bit size of 1, type, then a 64-bit size. */
    private fun extendedBox(type: String, payload: ByteArray): ByteArray {
        val size = (16 + payload.size).toLong()
        val largeSize = ByteArray(8) { (size ushr (8 * (7 - it))).toByte() }
        return intBytes(1) + type.encodeToByteArray() + largeSize + payload
    }

    private fun writeFixture(bytes: ByteArray): File =
        File.createTempFile("fixture", ".m4a").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    /** A minimal moov: moov > trak > mdia > minf > stbl > stco. */
    private fun moovBox(chunkOffsets: List<Long>): ByteArray {
        val entries = chunkOffsets.fold(ByteArray(0)) { acc, offset -> acc + intBytes(offset.toInt()) }
        val stco = box("stco", intBytes(0) + intBytes(chunkOffsets.size) + entries)
        val stbl = box("stbl", stco)
        val minf = box("minf", stbl)
        val mdia = box("mdia", minf)
        val trak = box("trak", mdia)
        return box("moov", box("mvhd", ByteArray(100)) + trak)
    }

    private fun chunkOffsetsOf(file: File): List<Long> {
        val bytes = file.readBytes()
        val at = indexOf(bytes, "stco")
        val count = boxSize(bytes, at + 8)
        return (0 until count).map { boxSize(bytes, at + 12 + it * 4).toLong() and 0xFFFFFFFFL }
    }

    private fun indexOf(bytes: ByteArray, type: String): Int {
        val needle = type.encodeToByteArray()
        outer@ for (i in 0..bytes.size - 4) {
            for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
            return i - 4
        }
        return -1
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    private fun moovOffset(file: File): Int {
        val bytes = file.readBytes()
        var offset = 0
        while (offset + 8 <= bytes.size) {
            if (bytes.copyOfRange(offset + 4, offset + 8).decodeToString() == "moov") return offset
            offset += boxSize(bytes, offset)
        }
        return -1
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return byteArrayOf(
            (size ushr 24).toByte(), (size ushr 16).toByte(),
            (size ushr 8).toByte(), size.toByte()
        ) + type.encodeToByteArray() + payload
    }

    private fun boxSize(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private companion object {
        const val MDAT_SIZE = 128
        val FTYP = byteArrayOf(0, 0, 0, 16) + "ftyp".encodeToByteArray() +
            "M4A ".encodeToByteArray() + ByteArray(4)
    }
}
