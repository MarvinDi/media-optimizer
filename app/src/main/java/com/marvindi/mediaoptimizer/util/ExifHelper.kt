package com.marvindi.mediaoptimizer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Reads and copies EXIF metadata (shooting time, GPS location, camera info)
 * between media files, so that converted photos keep their original
 * timeline and map position in any gallery app.
 */
object ExifHelper {

    private const val TAG = "ExifHelper"
    private const val ORIENTATION_TAG = 0x0112

    /** EXIF tags copied from the original file to the converted file. */
    private val COPY_TAGS = arrayOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP
    )

    /**
     * Reads the original shooting time (DateTimeOriginal, fallback DateTime)
     * of [uri] in epoch milliseconds, or null when unavailable.
     */
    fun readTakenTimeMillis(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                parseDateTime(raw)
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to read taken time from $uri", e)
            null
        }
    }

    /**
     * Copies EXIF metadata from [sourceUri] to the image file at [targetFile].
     *
     * Orientation is intentionally NOT copied: the bitmap was already rotated
     * during decode (ImageDecoder applies EXIF orientation automatically),
     * so the output orientation is forced to normal to avoid double rotation.
     *
     * Failures are logged and ignored - losing metadata must never break
     * the conversion itself.
     */
    fun copyExifFrom(context: Context, sourceUri: Uri, targetFile: File) {
        val source = try {
            context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) }
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val target = ExifInterface(targetFile.absolutePath)
            for (tag in COPY_TAGS) {
                val value = source.getAttribute(tag)
                if (value != null) {
                    target.setAttribute(tag, value)
                }
            }
            target.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            target.saveAttributes()
        } catch (e: Exception) {
            // Some target formats may not support EXIF writing on all devices.
            android.util.Log.w(TAG, "Failed to write EXIF to ${targetFile.name}", e)
        }
    }

    /**
     * Extracts the raw EXIF block (starting with "Exif\0\0") from a JPEG
     * source file. Used for HEIF output, because ExifInterface cannot write
     * EXIF into HEIF files - HeifWriter.addExifData() needs the raw block.
     *
     * Returns null for non-JPEG sources or when no EXIF block exists.
     */
    fun extractJpegExifBlock(context: Context, uri: Uri): ByteArray? {
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            DataInputStream(BufferedInputStream(stream)).use { input ->
                readExifBlock(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Scans JPEG segments of [input] and returns the raw EXIF APP1 payload. */
    private fun readExifBlock(input: DataInputStream): ByteArray? {
        // JPEG files start with SOI: FF D8
        if (input.read() != 0xFF || input.read() != 0xD8) return null
        while (true) {
            var marker = input.read()
            if (marker == -1) return null
            if (marker != 0xFF) return null
            while (marker == 0xFF) {
                marker = input.read()
                if (marker == -1) return null
            }
            // Markers without a length field
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) continue
            // Reached image data or end of file: no EXIF block present
            if (marker == 0xDA || marker == 0xD9) return null

            val lenHi = input.read()
            val lenLo = input.read()
            if (lenHi == -1 || lenLo == -1) return null
            val segmentLength = (lenHi shl 8) or lenLo
            if (segmentLength < 2) return null

            val payload = ByteArray(segmentLength - 2)
            input.readFully(payload)

            val isExifApp1 = marker == 0xE1 &&
                payload.size >= 6 &&
                payload[0] == 'E'.code.toByte() &&
                payload[1] == 'x'.code.toByte() &&
                payload[2] == 'i'.code.toByte() &&
                payload[3] == 'f'.code.toByte() &&
                payload[4].toInt() == 0 &&
                payload[5].toInt() == 0
            if (isExifApp1) return payload
        }
        return null
    }

    /**
     * Returns a copy of [exifBlock] with the orientation tag set to normal,
     * so it matches the already-rotated output pixels. If the tag is absent
     * (meaning "normal"), the block is returned unchanged.
     */
    fun withNormalOrientation(exifBlock: ByteArray): ByteArray {
        val out = exifBlock.copyOf()
        try {
            // TIFF header starts right after "Exif\0\0"
            val tiff = 6
            if (out.size < tiff + 8) return out
            val littleEndian = when {
                out[tiff] == 'I'.code.toByte() && out[tiff + 1] == 'I'.code.toByte() -> true
                out[tiff] == 'M'.code.toByte() && out[tiff + 1] == 'M'.code.toByte() -> false
                else -> return out
            }

            fun readU16(index: Int): Int {
                val b0 = out[index].toInt() and 0xFF
                val b1 = out[index + 1].toInt() and 0xFF
                return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
            }

            fun readU32(index: Int): Long {
                val b0 = out[index].toLong() and 0xFF
                val b1 = out[index + 1].toLong() and 0xFF
                val b2 = out[index + 2].toLong() and 0xFF
                val b3 = out[index + 3].toLong() and 0xFF
                return if (littleEndian) {
                    (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                } else {
                    (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                }
            }

            if (readU16(tiff + 2) != 0x002A) return out // not a TIFF header
            val ifd0 = (tiff + readU32(tiff + 4)).toInt()
            if (ifd0 < 0 || ifd0 + 2 > out.size) return out

            val entryCount = readU16(ifd0)
            for (i in 0 until entryCount) {
                val entry = ifd0 + 2 + i * 12
                if (entry + 12 > out.size) break
                if (readU16(entry) == ORIENTATION_TAG) { // 0x0112
                    val type = readU16(entry + 2) // 3 = SHORT
                    val count = readU32(entry + 4)
                    if (type == 3 && count == 1L) {
                        // Write value 1 (normal) into the value field
                        if (littleEndian) {
                            out[entry + 8] = 1
                            out[entry + 9] = 0
                        } else {
                            out[entry + 8] = 0
                            out[entry + 9] = 1
                        }
                    }
                    return out
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to clear orientation in EXIF block", e)
        }
        return out
    }

    /**
     * Reads EXIF tags from a HEIC/HEIF source file using [ExifInterface].
     *
     * AndroidX ExifInterface 1.3.7 supports reading HEIC files via
     * `ExifInterface(InputStream)`. This method reads the shooting time,
     * GPS location, and camera make/model tags that are commonly present
     * in HEIC photos from e.g. Xiaomi 14 native camera.
     *
     * Failures are logged and an empty map is returned - this method never throws.
     */
    fun readExifFromHeic(context: Context, uri: Uri): Map<String, String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val tags = LinkedHashMap<String, String>()
                val heicTags = listOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP
                )
                for (tag in heicTags) {
                    exif.getAttribute(tag)?.let { tags[tag] = it }
                }
                tags
            } ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF from HEIC $uri", e)
            emptyMap()
        }
    }

    /**
     * Builds a minimal EXIF block (starting with "Exif\0\0") from tag→value pairs,
     * suitable for [androidx.heifwriter.HeifWriter.addExifData].
     *
     * Constructs a little-endian TIFF structure with IFD0, optional Exif IFD
     * and optional GPS IFD. ASCII values longer than 4 bytes are placed in a
     * data area at the end of the block; shorter values are stored inline.
     *
     * Returns null if [tags] is empty or if the block cannot be built.
     */
    fun exifTagsToBlock(tags: Map<String, String>): ByteArray? {
        if (tags.isEmpty()) return null

        // Maps ExifInterface constant names to TIFF tag IDs.
        val tagIdMap = mapOf(
            ExifInterface.TAG_MAKE to 0x010F,
            ExifInterface.TAG_MODEL to 0x0110,
            ExifInterface.TAG_DATETIME to 0x0132,
            ExifInterface.TAG_DATETIME_ORIGINAL to 0x9003,
            ExifInterface.TAG_GPS_LATITUDE_REF to 0x0001,
            ExifInterface.TAG_GPS_LATITUDE to 0x0002,
            ExifInterface.TAG_GPS_LONGITUDE_REF to 0x0003,
            ExifInterface.TAG_GPS_LONGITUDE to 0x0004,
            ExifInterface.TAG_GPS_ALTITUDE to 0x0005,
            ExifInterface.TAG_GPS_ALTITUDE_REF to 0x0006,
            ExifInterface.TAG_GPS_TIMESTAMP to 0x0007,
            ExifInterface.TAG_GPS_DATESTAMP to 0x001D
        )

        val ifd0Entries = LinkedHashMap<Int, ByteArray>()
        val exifIfdEntries = LinkedHashMap<Int, ByteArray>()
        val gpsIfdEntries = LinkedHashMap<Int, ByteArray>()

        for ((tagName, value) in tags) {
            val tagId = tagIdMap[tagName] ?: continue
            val bytes = value.toByteArray(StandardCharsets.US_ASCII) + 0.toByte() // null-terminated
            when {
                tagId == 0x9003 -> exifIfdEntries[tagId] = bytes
                tagId in 0x0001..0x001D -> gpsIfdEntries[tagId] = bytes
                else -> ifd0Entries[tagId] = bytes
            }
        }

        // Pointer entries in IFD0 (filled with real offsets later).
        val hasExifIfd = exifIfdEntries.isNotEmpty()
        val hasGpsIfd = gpsIfdEntries.isNotEmpty()
        if (hasExifIfd) ifd0Entries[0x8769] = ByteArray(4) // ExifIFD pointer
        if (hasGpsIfd) ifd0Entries[0x8825] = ByteArray(4) // GPSIFD pointer

        // ---- Calculate offsets (relative to TIFF header start) ----
        val tiffHeaderSize = 8
        fun ifdBlockSize(entries: Map<Int, ByteArray>): Int = 2 + entries.size * 12 + 4

        val ifd0Start = tiffHeaderSize
        val exifIfdStart = ifd0Start + ifdBlockSize(ifd0Entries)
        val gpsIfdStart = exifIfdStart + (if (hasExifIfd) ifdBlockSize(exifIfdEntries) else 0)
        val dataStart = gpsIfdStart + (if (hasGpsIfd) ifdBlockSize(gpsIfdEntries) else 0)

        // ---- Place string values in data area, track offsets ----
        var dataPos = dataStart
        val stringOffsets = LinkedHashMap<Int, Int>() // tagId → absolute TIFF offset
        val inlineValues = LinkedHashMap<Int, Int>()  // tagId → inline int value

        fun assignValue(tagId: Int, bytes: ByteArray) {
            if (bytes.size <= 4) {
                // Store inline (little-endian: first byte at lowest address)
                var intVal = 0
                for (i in bytes.indices) {
                    intVal = intVal or ((bytes[i].toInt() and 0xFF) shl (i * 8))
                }
                inlineValues[tagId] = intVal
            } else {
                stringOffsets[tagId] = dataPos
                dataPos += bytes.size
            }
        }

        for ((tagId, bytes) in ifd0Entries) {
            if (tagId == 0x8769 || tagId == 0x8825) continue // pointers handled later
            assignValue(tagId, bytes)
        }
        for ((tagId, bytes) in exifIfdEntries) assignValue(tagId, bytes)
        for ((tagId, bytes) in gpsIfdEntries) assignValue(tagId, bytes)

        val totalSize = 6 + tiffHeaderSize + ifdBlockSize(ifd0Entries)
            + (if (hasExifIfd) ifdBlockSize(exifIfdEntries) else 0)
            + (if (hasGpsIfd) ifdBlockSize(gpsIfdEntries) else 0)
            + (dataPos - dataStart)
        val out = ByteArray(totalSize)

        // ---- Write "Exif\0\0" prefix ----
        out[0] = 'E'.code.toByte()
        out[1] = 'x'.code.toByte()
        out[2] = 'i'.code.toByte()
        out[3] = 'f'.code.toByte()
        out[4] = 0.toByte()
        out[5] = 0.toByte()

        // ---- Write TIFF header (little-endian "II") ----
        out[6] = 'I'.code.toByte()
        out[7] = 'I'.code.toByte()
        out[8] = 0x2A.toByte()
        out[9] = 0x00.toByte()
        // IFD0 offset = tiffHeaderSize (8)
        out[10] = 0x08.toByte()
        out[11] = 0x00.toByte()
        out[12] = 0x00.toByte()
        out[13] = 0x00.toByte()

        // ---- Write IFD0 ----
        var pos = ifd0Start
        val ifd0EntryCount = ifd0Entries.size
        out[pos] = (ifd0EntryCount and 0xFF).toByte()
        out[pos + 1] = (ifd0EntryCount shr 8 and 0xFF).toByte()
        pos += 2

        val exifIfdAbsOffset = if (hasExifIfd) exifIfdStart else 0
        val gpsIfdAbsOffset = if (hasGpsIfd) gpsIfdStart else 0

        for ((tagId, bytes) in ifd0Entries) {
            // Tag (2 bytes, little-endian)
            out[pos] = (tagId and 0xFF).toByte()
            out[pos + 1] = (tagId shr 8 and 0xFF).toByte()
            pos += 2

            if (tagId == 0x8769) {
                // Type = LONG (4), Count = 1, Value = offset to ExifIFD
                out[pos] = 0x04.toByte(); out[pos + 1] = 0x00.toByte()
                out[pos + 2] = 0x01.toByte(); out[pos + 3] = 0x00.toByte()
                out[pos + 4] = 0x00.toByte(); out[pos + 5] = 0x00.toByte()
                out[pos + 6] = 0x00.toByte(); out[pos + 7] = 0x00.toByte()
                out[pos + 8] = (exifIfdAbsOffset and 0xFF).toByte()
                out[pos + 9] = (exifIfdAbsOffset shr 8 and 0xFF).toByte()
                out[pos + 10] = (exifIfdAbsOffset shr 16 and 0xFF).toByte()
                out[pos + 11] = (exifIfdAbsOffset shr 24 and 0xFF).toByte()
            } else if (tagId == 0x8825) {
                // Type = LONG (4), Count = 1, Value = offset to GPSIFD
                out[pos] = 0x04.toByte(); out[pos + 1] = 0x00.toByte()
                out[pos + 2] = 0x01.toByte(); out[pos + 3] = 0x00.toByte()
                out[pos + 4] = 0x00.toByte(); out[pos + 5] = 0x00.toByte()
                out[pos + 6] = 0x00.toByte(); out[pos + 7] = 0x00.toByte()
                out[pos + 8] = (gpsIfdAbsOffset and 0xFF).toByte()
                out[pos + 9] = (gpsIfdAbsOffset shr 8 and 0xFF).toByte()
                out[pos + 10] = (gpsIfdAbsOffset shr 16 and 0xFF).toByte()
                out[pos + 11] = (gpsIfdAbsOffset shr 24 and 0xFF).toByte()
            } else {
                // Type = ASCII (2)
                out[pos] = 0x02.toByte(); out[pos + 1] = 0x00.toByte()
                val count = bytes.size
                out[pos + 2] = (count and 0xFF).toByte()
                out[pos + 3] = (count shr 8 and 0xFF).toByte()
                out[pos + 4] = 0x00.toByte(); out[pos + 5] = 0x00.toByte()
                out[pos + 6] = 0x00.toByte(); out[pos + 7] = 0x00.toByte()
                if (tagId in inlineValues) {
                    val intVal = inlineValues[tagId]!!
                    out[pos + 8] = (intVal and 0xFF).toByte()
                    out[pos + 9] = (intVal shr 8 and 0xFF).toByte()
                    out[pos + 10] = (intVal shr 16 and 0xFF).toByte()
                    out[pos + 11] = (intVal shr 24 and 0xFF).toByte()
                } else {
                    val offset = stringOffsets[tagId]!!
                    out[pos + 8] = (offset and 0xFF).toByte()
                    out[pos + 9] = (offset shr 8 and 0xFF).toByte()
                    out[pos + 10] = (offset shr 16 and 0xFF).toByte()
                    out[pos + 11] = (offset shr 24 and 0xFF).toByte()
                }
            }
            pos += 12
        }

        // IFD0 next IFD offset = 0 (none)
        out[pos] = 0.toByte(); out[pos + 1] = 0.toByte()
        out[pos + 2] = 0.toByte(); out[pos + 3] = 0.toByte()
        pos += 4

        // ---- Write Exif IFD ----
        if (hasExifIfd) {
            out[pos] = (exifIfdEntries.size and 0xFF).toByte()
            out[pos + 1] = (exifIfdEntries.size shr 8 and 0xFF).toByte()
            pos += 2
            for ((tagId, bytes) in exifIfdEntries) {
                out[pos] = (tagId and 0xFF).toByte()
                out[pos + 1] = (tagId shr 8 and 0xFF).toByte()
                pos += 2
                out[pos] = 0x02.toByte(); out[pos + 1] = 0x00.toByte() // ASCII
                pos += 2
                val count = bytes.size
                out[pos] = (count and 0xFF).toByte()
                out[pos + 1] = (count shr 8 and 0xFF).toByte()
                out[pos + 2] = 0x00.toByte(); out[pos + 3] = 0x00.toByte()
                pos += 4
                val offset = stringOffsets[tagId]!!
                out[pos] = (offset and 0xFF).toByte()
                out[pos + 1] = (offset shr 8 and 0xFF).toByte()
                out[pos + 2] = (offset shr 16 and 0xFF).toByte()
                out[pos + 3] = (offset shr 24 and 0xFF).toByte()
                pos += 4
            }
            out[pos] = 0.toByte(); out[pos + 1] = 0.toByte()
            out[pos + 2] = 0.toByte(); out[pos + 3] = 0.toByte()
            pos += 4
        }

        // ---- Write GPS IFD ----
        if (hasGpsIfd) {
            out[pos] = (gpsIfdEntries.size and 0xFF).toByte()
            out[pos + 1] = (gpsIfdEntries.size shr 8 and 0xFF).toByte()
            pos += 2
            for ((tagId, bytes) in gpsIfdEntries) {
                out[pos] = (tagId and 0xFF).toByte()
                out[pos + 1] = (tagId shr 8 and 0xFF).toByte()
                pos += 2
                out[pos] = 0x02.toByte(); out[pos + 1] = 0x00.toByte() // ASCII
                pos += 2
                val count = bytes.size
                out[pos] = (count and 0xFF).toByte()
                out[pos + 1] = (count shr 8 and 0xFF).toByte()
                out[pos + 2] = 0x00.toByte(); out[pos + 3] = 0x00.toByte()
                pos += 4
                val offset = stringOffsets[tagId]!!
                out[pos] = (offset and 0xFF).toByte()
                out[pos + 1] = (offset shr 8 and 0xFF).toByte()
                out[pos + 2] = (offset shr 16 and 0xFF).toByte()
                out[pos + 3] = (offset shr 24 and 0xFF).toByte()
                pos += 4
            }
            out[pos] = 0.toByte(); out[pos + 1] = 0.toByte()
            out[pos + 2] = 0.toByte(); out[pos + 3] = 0.toByte()
            pos += 4
        }

        // ---- Write data area (ASCII strings) ----
        for ((tagId, offset) in stringOffsets) {
            val bytes = when {
                tagId in ifd0Entries -> ifd0Entries[tagId]!!
                tagId in exifIfdEntries -> exifIfdEntries[tagId]!!
                tagId in gpsIfdEntries -> gpsIfdEntries[tagId]!!
                else -> continue
            }
            System.arraycopy(bytes, 0, out, offset, bytes.size)
        }

        return out
    }

    /**
     * Extracts a raw EXIF block from a JPEG or HEIC source.
     *
     * First tries JPEG APP1 segment parsing ([extractJpegExifBlock]). If that
     * returns null (e.g. HEIC source), falls back to reading individual EXIF
     * tags via [ExifInterface] and building a block with [exifTagsToBlock].
     *
     * The orientation tag is reset to normal because ImageDecoder already
     * rotated the output pixels during decode.
     *
     * Returns null when no EXIF data is available or cannot be built.
     * Failures are logged and never throw.
     */
    fun extractExifBlock(context: Context, uri: Uri): ByteArray? {
        // Try JPEG first
        val jpegBlock = extractJpegExifBlock(context, uri)
        if (jpegBlock != null) {
            return withNormalOrientation(jpegBlock)
        }

        // Fall back to HEIC: read individual tags and build a block
        return try {
            val tags = readExifFromHeic(context, uri)
            if (tags.isEmpty()) null
            else exifTagsToBlock(tags)?.let { withNormalOrientation(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract EXIF block from HEIC $uri", e)
            null
        }
    }

    private fun parseDateTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }
}
