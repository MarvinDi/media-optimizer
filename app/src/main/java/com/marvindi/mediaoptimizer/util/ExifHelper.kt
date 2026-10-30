package com.marvindi.mediaoptimizer.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
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

    private fun parseDateTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }
}
