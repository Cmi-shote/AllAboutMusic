package com.simiscompany.allaboutmusic.data.export

/**
 * Writes iTunes-style tags and cover art into an MP4 (M4A) `moov` box:
 *
 *     moov > udta > meta > (hdlr, ilst > (©nam, ©ART, ©alb, ©cmt, covr) > data)
 *
 * A `moov` may hold only one `udta`, and readers use the first one they find, so
 * the tags have to be merged into whatever the muxer already wrote rather than
 * appended alongside it. Android's MediaMuxer writes audio and its own `udta`;
 * this fills in the rest afterwards (see Mp4MetadataInserter). iOS gets the same
 * tags from AVFoundation and doesn't use this.
 */
object Mp4Metadata {

    private const val DATA_TYPE_UTF8 = 1
    private const val DATA_TYPE_JPEG = 13
    private const val DATA_TYPE_PNG = 14

    private const val BOX_HEADER_SIZE = 8
    private const val FULL_BOX_HEADER_SIZE = 12

    /** The iTunes metadata handler. Anything else in `meta` uses a layout we can't merge into. */
    private const val ITUNES_HANDLER = "mdir"

    /** Boxes on the path down to a chunk offset table. */
    private val CHUNK_OFFSET_PARENTS = setOf("moov", "trak", "mdia", "minf", "stbl")

    private const val UINT32_MAX = 0xFFFFFFFFL

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    /** `©` in an atom name is the single byte 0xA9, not its UTF-8 encoding. */
    private const val COPYRIGHT_SIGN = '©'

    /**
     * Returns [moov] with the tags merged in, or null if there's nothing to write.
     * Safe to grow: callers only use this when `moov` is the last box in the file,
     * so no chunk offset moves.
     */
    fun moovWithMetadata(moov: ByteArray, metadata: MixMetadata, artwork: ByteArray?): ByteArray? {
        val entries = ilstEntries(metadata, artwork)
        if (entries.isEmpty()) return null

        val udta = findChild(moov, BOX_HEADER_SIZE, "udta")
            ?: return box("moov", listOf(payloadOf(moov, BOX_HEADER_SIZE), buildUdta(entries)))

        val merged = mergeIntoUdta(moov.copyOfRange(udta.start, udta.end), entries)
        return box(
            "moov",
            listOf(
                moov.copyOfRange(BOX_HEADER_SIZE, udta.start),
                merged,
                moov.copyOfRange(udta.end, moov.size)
            )
        )
    }

    /**
     * Adds [delta] to every chunk offset at or past [minOffset], in both `stco` and
     * `co64` tables. Needed when a `moov` that sits before the media grows: the audio
     * slides down the file, and every offset pointing at it has to follow.
     *
     * Returns null if the tables can't be read or an offset would overflow, in which
     * case the caller must leave the file alone.
     */
    fun withChunkOffsetsShifted(moov: ByteArray, delta: Int, minOffset: Long): ByteArray? {
        val shifted = moov.copyOf()
        return if (patchChunkOffsets(shifted, BOX_HEADER_SIZE, shifted.size, delta, minOffset)) {
            shifted
        } else {
            null
        }
    }

    private fun patchChunkOffsets(
        buffer: ByteArray,
        start: Int,
        end: Int,
        delta: Int,
        minOffset: Long
    ): Boolean {
        var offset = start
        while (offset + BOX_HEADER_SIZE <= end) {
            val size = readInt(buffer, offset)
            if (size < BOX_HEADER_SIZE || offset + size > end) return false
            val type = typeOf(buffer, offset)
            val patched = when {
                type == "stco" -> patchOffsetTable(buffer, offset, size, delta, minOffset, entryBytes = 4)
                type == "co64" -> patchOffsetTable(buffer, offset, size, delta, minOffset, entryBytes = 8)
                type in CHUNK_OFFSET_PARENTS ->
                    patchChunkOffsets(buffer, offset + BOX_HEADER_SIZE, offset + size, delta, minOffset)
                else -> true
            }
            if (!patched) return false
            offset += size
        }
        return true
    }

    /** stco/co64: version and flags (4), entry count (4), then that many offsets. */
    private fun patchOffsetTable(
        buffer: ByteArray,
        boxStart: Int,
        boxSize: Int,
        delta: Int,
        minOffset: Long,
        entryBytes: Int
    ): Boolean {
        val countAt = boxStart + BOX_HEADER_SIZE + 4
        if (countAt + 4 > boxStart + boxSize) return false
        val count = readInt(buffer, countAt)
        if (count < 0) return false

        val entriesAt = countAt + 4
        if (entriesAt + count.toLong() * entryBytes > (boxStart + boxSize).toLong()) return false

        for (i in 0 until count) {
            val at = entriesAt + i * entryBytes
            val value = if (entryBytes == 4) readUInt(buffer, at) else readLong(buffer, at)
            if (value < minOffset) continue
            val moved = value + delta
            if (entryBytes == 4 && moved > UINT32_MAX) return false
            if (entryBytes == 4) writeUInt(buffer, at, moved) else writeLong(buffer, at, moved)
        }
        return true
    }

    private fun mergeIntoUdta(udta: ByteArray, entries: List<ByteArray>): ByteArray {
        val meta = findChild(udta, BOX_HEADER_SIZE, "meta")
            ?: return box("udta", listOf(payloadOf(udta, BOX_HEADER_SIZE), buildMeta(entries)))

        val merged = mergeIntoMeta(udta.copyOfRange(meta.start, meta.end), entries)
        return box(
            "udta",
            listOf(
                udta.copyOfRange(BOX_HEADER_SIZE, meta.start),
                merged,
                udta.copyOfRange(meta.end, udta.size)
            )
        )
    }

    private fun mergeIntoMeta(meta: ByteArray, entries: List<ByteArray>): ByteArray {
        val childrenStart = metaChildrenStart(meta)
        val handler = handlerType(meta, childrenStart)
        // A non-iTunes handler (MediaMuxer writes `mdta`, whose ilst keys are indexes
        // into a `keys` box) can't take our atoms, so that meta is replaced outright.
        if (handler != ITUNES_HANDLER) return buildMeta(entries)

        val ilst = findChild(meta, childrenStart, "ilst")
            ?: return box(
                "meta",
                listOf(payloadOf(meta, BOX_HEADER_SIZE), buildIlst(entries))
            )

        val merged = mergeIntoIlst(meta.copyOfRange(ilst.start, ilst.end), entries)
        return box(
            "meta",
            listOf(
                meta.copyOfRange(BOX_HEADER_SIZE, ilst.start),
                merged,
                meta.copyOfRange(ilst.end, meta.size)
            )
        )
    }

    /** Keeps entries we aren't writing (gapless info, for instance) and replaces the rest. */
    private fun mergeIntoIlst(ilst: ByteArray, entries: List<ByteArray>): ByteArray {
        val replaced = entries.map { typeOf(it, 0) }.toSet()
        val kept = childrenOf(ilst, BOX_HEADER_SIZE)
            .filter { typeOf(ilst, it.start) !in replaced }
            .map { ilst.copyOfRange(it.start, it.end) }
        return box("ilst", kept + entries)
    }

    private fun buildUdta(entries: List<ByteArray>): ByteArray =
        box("udta", listOf(buildMeta(entries)))

    private fun buildMeta(entries: List<ByteArray>): ByteArray =
        box("meta", listOf(versionAndFlags(), handlerBox(), buildIlst(entries)))

    private fun buildIlst(entries: List<ByteArray>): ByteArray = box("ilst", entries)

    private fun ilstEntries(metadata: MixMetadata, artwork: ByteArray?): List<ByteArray> {
        val entries = mutableListOf<ByteArray>()
        textEntry("${COPYRIGHT_SIGN}nam", metadata.title)?.let(entries::add)
        textEntry("${COPYRIGHT_SIGN}ART", metadata.artist)?.let(entries::add)
        textEntry("${COPYRIGHT_SIGN}alb", metadata.album)?.let(entries::add)
        textEntry("${COPYRIGHT_SIGN}cmt", metadata.comment)?.let(entries::add)
        artworkEntry(artwork)?.let(entries::add)
        return entries
    }

    private fun textEntry(name: String, value: String): ByteArray? {
        if (value.isEmpty()) return null
        return box(name, listOf(dataBox(DATA_TYPE_UTF8, value.encodeToByteArray())))
    }

    private fun artworkEntry(artwork: ByteArray?): ByteArray? {
        if (artwork == null || artwork.isEmpty()) return null
        val type = imageDataType(artwork) ?: return null
        return box("covr", listOf(dataBox(type, artwork)))
    }

    /** Only PNG and JPEG are valid in a `covr` atom; anything else is skipped. */
    private fun imageDataType(image: ByteArray): Int? = when {
        image.startsWith(PNG_SIGNATURE) -> DATA_TYPE_PNG
        image.startsWith(JPEG_SIGNATURE) -> DATA_TYPE_JPEG
        else -> null
    }

    private fun dataBox(dataType: Int, payload: ByteArray): ByteArray =
        box("data", listOf(intBytes(dataType), intBytes(0 /* locale */), payload))

    /** The `mdir`/`appl` handler that marks `meta` as holding iTunes metadata. */
    private fun handlerBox(): ByteArray = box(
        "hdlr",
        listOf(
            versionAndFlags(),
            intBytes(0),                  // pre_defined
            fourCharCode(ITUNES_HANDLER),
            fourCharCode("appl"),         // reserved, but Apple writes its own code here
            intBytes(0),
            intBytes(0),
            byteArrayOf(0)                // empty, null-terminated name
        )
    )

    private fun versionAndFlags(): ByteArray = intBytes(0)

    // --- box reading ---------------------------------------------------------

    private data class Region(val start: Int, val end: Int)

    private fun childrenOf(buffer: ByteArray, start: Int): List<Region> {
        val children = mutableListOf<Region>()
        var offset = start
        while (offset + BOX_HEADER_SIZE <= buffer.size) {
            val size = readInt(buffer, offset)
            if (size < BOX_HEADER_SIZE || offset + size > buffer.size) break
            children.add(Region(offset, offset + size))
            offset += size
        }
        return children
    }

    private fun findChild(buffer: ByteArray, start: Int, type: String): Region? =
        childrenOf(buffer, start).firstOrNull { typeOf(buffer, it.start) == type }

    /**
     * `meta` is a full box in the ISO layout (4 bytes of version and flags before its
     * children) but a plain container in QuickTime's. Detected by whether a valid box
     * header sits immediately after the header.
     */
    private fun metaChildrenStart(meta: ByteArray): Int {
        if (meta.size < FULL_BOX_HEADER_SIZE + BOX_HEADER_SIZE) return FULL_BOX_HEADER_SIZE
        val size = readInt(meta, BOX_HEADER_SIZE)
        val looksLikeBox = size in BOX_HEADER_SIZE..(meta.size - BOX_HEADER_SIZE) &&
            typeOf(meta, BOX_HEADER_SIZE).all { it.isLetterOrDigit() || it.code == 0xA9 }
        return if (looksLikeBox) BOX_HEADER_SIZE else FULL_BOX_HEADER_SIZE
    }

    private fun handlerType(meta: ByteArray, childrenStart: Int): String? {
        val hdlr = findChild(meta, childrenStart, "hdlr") ?: return null
        // hdlr: version+flags (4), pre_defined (4), then the handler code.
        val handlerAt = hdlr.start + BOX_HEADER_SIZE + 8
        if (handlerAt + 4 > hdlr.end) return null
        return typeOf(meta, handlerAt - 4)
    }

    private fun typeOf(buffer: ByteArray, boxStart: Int): String =
        buildString {
            for (i in 4..7) append((buffer[boxStart + i].toInt() and 0xFF).toChar())
        }

    private fun readUInt(buffer: ByteArray, offset: Int): Long =
        readInt(buffer, offset).toLong() and UINT32_MAX

    private fun readLong(buffer: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (buffer[offset + i].toLong() and 0xFF)
        return value
    }

    private fun writeUInt(buffer: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 4) buffer[offset + i] = (value ushr (8 * (3 - i))).toByte()
    }

    private fun writeLong(buffer: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) buffer[offset + i] = (value ushr (8 * (7 - i))).toByte()
    }

    private fun readInt(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 24) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
            (buffer[offset + 3].toInt() and 0xFF)

    // --- box writing ---------------------------------------------------------

    private fun payloadOf(box: ByteArray, headerSize: Int): ByteArray =
        box.copyOfRange(headerSize, box.size)

    private fun box(type: String, parts: List<ByteArray>): ByteArray {
        val size = BOX_HEADER_SIZE + parts.sumOf { it.size }
        val out = ByteArray(size)
        intBytes(size).copyInto(out, 0)
        fourCharCode(type).copyInto(out, 4)
        var offset = BOX_HEADER_SIZE
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    private fun fourCharCode(type: String): ByteArray {
        require(type.length == 4) { "Box type must be 4 characters: $type" }
        return ByteArray(4) { (type[it].code and 0xFF).toByte() }
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }
}
