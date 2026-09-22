package com.simiscompany.allaboutmusic.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Mp4MetadataTest {

    private val metadata = MixMetadata(
        title = "Friday Set",
        artist = "Aurora Bloom",
        album = MixMetadata.ALBUM,
        comment = "Aurora Bloom — via Jamendo (CC BY)"
    )

    private val pngArtwork = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(64) { 7 }
    private val jpegArtwork = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(32)

    /** A moov with no metadata of its own, as a muxer that writes no udta would leave it. */
    private val bareMoov = box("moov", listOf(box("mvhd", listOf(ByteArray(100)))))

    /** A moov whose udta already holds iTunes-style tags. */
    private fun taggedMoov(handler: String = "mdir", entries: List<ByteArray> = listOf(freeformEntry())) =
        box("moov", listOf(box("mvhd", listOf(ByteArray(100))), udtaBox(handler, entries)))

    // --- box structure -------------------------------------------------------

    @Test
    fun moovWithMetadata_nestsMetaAndIlstInsideUdta() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, pngArtwork))

        val udta = assertNotNull(parseBox(moov, 0).child(moov, "udta"))
        val meta = assertNotNull(udta.child(moov, "meta"))
        // meta is a full box: 4 bytes of version and flags before its children.
        val hdlr = assertNotNull(meta.child(moov, "hdlr", skip = 4))
        assertTrue(hdlr.payload(moov).decodeToString().contains("mdir"))
        assertNotNull(meta.child(moov, "ilst", skip = 4))
    }

    @Test
    fun moovWithMetadata_writesEveryTextTag() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, null))
        val ilst = ilstOf(moov)

        assertEquals("Friday Set", textValue(moov, ilst, "©nam"))
        assertEquals("Aurora Bloom", textValue(moov, ilst, "©ART"))
        assertEquals(MixMetadata.ALBUM, textValue(moov, ilst, "©alb"))
        assertEquals("Aurora Bloom — via Jamendo (CC BY)", textValue(moov, ilst, "©cmt"))
    }

    @Test
    fun moovWithMetadata_atomNamesUseTheSingleByteCopyrightSign() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, null))

        assertEquals(0xA9, moov[indexOfType(moov, "©nam")].toInt() and 0xFF)
    }

    @Test
    fun moovWithMetadata_tagsPngArtworkAsType14() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, pngArtwork))
        val data = assertNotNull(assertNotNull(ilstOf(moov).child(moov, "covr")).child(moov, "data"))

        assertEquals(14, readInt(moov, data.offset + 8))
        assertEquals(pngArtwork.size, data.size - 16)
    }

    @Test
    fun moovWithMetadata_tagsJpegArtworkAsType13() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, jpegArtwork))
        val data = assertNotNull(assertNotNull(ilstOf(moov).child(moov, "covr")).child(moov, "data"))

        assertEquals(13, readInt(moov, data.offset + 8))
    }

    @Test
    fun moovWithMetadata_skipsArtworkThatIsNotPngOrJpeg() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, ByteArray(40) { 3 }))

        assertNull(ilstOf(moov).child(moov, "covr"))
    }

    @Test
    fun moovWithMetadata_skipsEmptyTags() {
        val moov = assertNotNull(
            Mp4Metadata.moovWithMetadata(bareMoov, metadata.copy(comment = ""), null)
        )

        assertNull(ilstOf(moov).child(moov, "©cmt"))
        assertNotNull(ilstOf(moov).child(moov, "©nam"))
    }

    // --- merging into what the muxer already wrote ---------------------------

    @Test
    fun moovWithMetadata_addsAUdtaWhenTheFileHasNone() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(bareMoov, metadata, pngArtwork))

        assertEquals(1, countType(moov, "udta"))
        assertNotNull(parseBox(moov, 0).child(moov, "mvhd"))
        assertEquals("Friday Set", textValue(moov, ilstOf(moov), "©nam"))
    }

    @Test
    fun moovWithMetadata_mergesIntoAnExistingUdtaRatherThanAddingASecond() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(taggedMoov(), metadata, pngArtwork))

        // A moov may hold only one udta, and readers use the first one they find.
        assertEquals(1, countType(moov, "udta"))
        assertEquals(1, countType(moov, "ilst"))
        assertEquals("Friday Set", textValue(moov, ilstOf(moov), "©nam"))
    }

    @Test
    fun moovWithMetadata_keepsUnrelatedTagsThatAreAlreadyThere() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(taggedMoov(), metadata, null))

        assertNotNull(ilstOf(moov).child(moov, "----"))
    }

    @Test
    fun moovWithMetadata_replacesTagsItWritesItself() {
        val stale = box("©nam", listOf(dataBox(1, "Old Name".encodeToByteArray())))
        val moov = assertNotNull(
            Mp4Metadata.moovWithMetadata(taggedMoov(entries = listOf(stale)), metadata, null)
        )

        assertEquals(1, countType(moov, "©nam"))
        assertEquals("Friday Set", textValue(moov, ilstOf(moov), "©nam"))
    }

    @Test
    fun moovWithMetadata_replacesAMetaBoxThatUsesAnotherHandler() {
        // MediaMuxer writes an `mdta` meta whose ilst keys index a `keys` box; our
        // atoms can't live in that, so the whole meta is replaced.
        val moov = assertNotNull(
            Mp4Metadata.moovWithMetadata(taggedMoov(handler = "mdta"), metadata, null)
        )

        assertEquals(1, countType(moov, "meta"))
        assertNull(ilstOf(moov).child(moov, "----"))
        assertEquals("Friday Set", textValue(moov, ilstOf(moov), "©nam"))
    }

    @Test
    fun moovWithMetadata_returnsNullWhenThereIsNothingToWrite() {
        val empty = MixMetadata(title = "", artist = "", album = "", comment = "")

        assertNull(Mp4Metadata.moovWithMetadata(bareMoov, empty, null))
    }

    @Test
    fun moovWithMetadata_producesConsistentBoxSizes() {
        val moov = assertNotNull(Mp4Metadata.moovWithMetadata(taggedMoov(), metadata, pngArtwork))

        // Walking the tree would overrun the buffer if any size field were wrong.
        assertEquals(moov.size, parseBox(moov, 0).size)
        assertEquals(moov.size, totalSizeOfChildren(moov, parseBox(moov, 0), skip = 0) + 8)
    }
}

// --- minimal MP4 box reader, so the tests don't trust the writer's own maths ---

private data class BoxRef(val offset: Int, val size: Int, val type: String) {
    fun payload(buffer: ByteArray): ByteArray = buffer.copyOfRange(offset + 8, offset + size)

    /** Finds a direct child by type, skipping [skip] leading bytes of a full box. */
    fun child(buffer: ByteArray, type: String, skip: Int = 0): BoxRef? {
        var pos = offset + 8 + skip
        val end = offset + size
        while (pos + 8 <= end) {
            val box = parseBox(buffer, pos)
            if (box.type == type) return box
            if (box.size <= 0) return null
            pos += box.size
        }
        return null
    }
}

private fun parseBox(buffer: ByteArray, offset: Int): BoxRef {
    val size = readInt(buffer, offset)
    val type = buildString { for (i in 4..7) append((buffer[offset + i].toInt() and 0xFF).toChar()) }
    return BoxRef(offset, size, type)
}

private fun readInt(buffer: ByteArray, offset: Int): Int =
    ((buffer[offset].toInt() and 0xFF) shl 24) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
        ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
        (buffer[offset + 3].toInt() and 0xFF)

/** Walks moov > udta > meta > ilst. */
private fun ilstOf(moov: ByteArray): BoxRef {
    val udta = requireNotNull(parseBox(moov, 0).child(moov, "udta"))
    val meta = requireNotNull(udta.child(moov, "meta"))
    return requireNotNull(meta.child(moov, "ilst", skip = 4))
}

private fun textValue(buffer: ByteArray, ilst: BoxRef, type: String): String {
    val entry = requireNotNull(ilst.child(buffer, type))
    val data = requireNotNull(entry.child(buffer, "data"))
    require(readInt(buffer, data.offset + 8) == 1) { "$type is not a UTF-8 data atom" }
    return buffer.copyOfRange(data.offset + 16, data.offset + data.size).decodeToString()
}

private fun indexOfType(buffer: ByteArray, type: String): Int {
    val needle = fourCharCode(type)
    outer@ for (i in 0..buffer.size - 4) {
        for (j in needle.indices) if (buffer[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}

private fun countType(buffer: ByteArray, type: String): Int {
    val needle = fourCharCode(type)
    var count = 0
    outer@ for (i in 0..buffer.size - 4) {
        for (j in needle.indices) if (buffer[i + j] != needle[j]) continue@outer
        count++
    }
    return count
}

private fun totalSizeOfChildren(buffer: ByteArray, parent: BoxRef, skip: Int): Int {
    var pos = parent.offset + 8 + skip
    var total = skip
    while (pos + 8 <= parent.offset + parent.size) {
        val box = parseBox(buffer, pos)
        total += box.size
        pos += box.size
    }
    return total
}

// --- fixture builders ---

private fun fourCharCode(type: String) = ByteArray(4) { (type[it].code and 0xFF).toByte() }

private fun box(type: String, parts: List<ByteArray>): ByteArray {
    val size = 8 + parts.sumOf { it.size }
    val out = ByteArray(size)
    intBytes(size).copyInto(out, 0)
    fourCharCode(type).copyInto(out, 4)
    var offset = 8
    for (part in parts) {
        part.copyInto(out, offset)
        offset += part.size
    }
    return out
}

private fun intBytes(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
)

private fun dataBox(type: Int, payload: ByteArray): ByteArray =
    box("data", listOf(intBytes(type), intBytes(0), payload))

/** An iTunes free-form entry, standing in for tags a muxer already wrote. */
private fun freeformEntry(): ByteArray =
    box("----", listOf(dataBox(1, "gapless".encodeToByteArray())))

private fun handlerBox(handler: String): ByteArray = box(
    "hdlr",
    listOf(
        intBytes(0), intBytes(0), fourCharCode(handler), fourCharCode("appl"),
        intBytes(0), intBytes(0), byteArrayOf(0)
    )
)

private fun udtaBox(handler: String, entries: List<ByteArray>): ByteArray =
    box("udta", listOf(box("meta", listOf(intBytes(0), handlerBox(handler), box("ilst", entries)))))
