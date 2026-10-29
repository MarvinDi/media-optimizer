package com.marvindi.mediaoptimizer.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.heifwriter.HeifWriter
import com.marvindi.mediaoptimizer.model.CompressResult
import com.marvindi.mediaoptimizer.model.OutputFormat
import com.marvindi.mediaoptimizer.util.ExifHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Converts a single source image to the requested output format,
 * preserves EXIF metadata, and publishes the result into one
 * aggregated gallery folder (Pictures/[OUTPUT_FOLDER]) via MediaStore.
 *
 * Originals are never touched or deleted.
 */
class ImageRepository(private val context: Context) {

    suspend fun compress(
        sourceUri: Uri,
        format: OutputFormat,
        quality: Int,
        preserveExif: Boolean
    ): CompressResult = withContext(Dispatchers.Default) {
        val originalBytes = querySize(sourceUri)
        val sourceName = queryDisplayName(sourceUri)

        // 1. Decode. ImageDecoder applies EXIF orientation automatically,
        //    so the output pixels are already correctly rotated.
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        val tempSuffix = if (format == OutputFormat.HEIF) ".heic" else ".tmp"
        val temp = File.createTempFile("media_opt_", tempSuffix, context.cacheDir)

        // For HEIF output the raw EXIF block is embedded during encoding.
        // The orientation tag is reset to normal because ImageDecoder already
        // rotated the pixels.
        val exifBlock =
            if (preserveExif && format == OutputFormat.HEIF) {
                ExifHelper.extractJpegExifBlock(context, sourceUri)
                    ?.let { ExifHelper.withNormalOrientation(it) }
            } else {
                null
            }

        try {
            // 2. Encode to a temp file (HEIF falls back to JPEG when unsupported).
            val actualMime = encode(bitmap, temp, format, quality, exifBlock)

            // 3. Keep shooting time / GPS / camera info on the converted file.
            //    For HEIF this is done inside encode() via HeifWriter.addExifData
            //    (ExifInterface cannot write EXIF into HEIF files); for JPEG/WebP
            //    we copy EXIF here with ExifInterface.
            if (preserveExif && actualMime != OutputFormat.HEIF.mimeType) {
                ExifHelper.copyExifFrom(context, sourceUri, temp)
            }

            // 4. Publish into the single aggregated folder.
            val takenMillis =
                if (preserveExif) ExifHelper.readTakenTimeMillis(context, sourceUri) else null
            val outputUri = publish(temp, sourceName, actualMime, takenMillis)

            val newBytes = querySize(outputUri) ?: temp.length()
            CompressResult(sourceUri, outputUri, originalBytes, newBytes)
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }

    /**
     * Encodes [bitmap] into [out]. Returns the MIME type actually written
     * (may be JPEG if HEIF encoding is not supported by the device).
     */
    private fun encode(
        bitmap: Bitmap,
        out: File,
        format: OutputFormat,
        quality: Int,
        exifBlock: ByteArray?
    ): String {
        return when (format) {
            OutputFormat.JPEG -> {
                FileOutputStream(out).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
                }
                OutputFormat.JPEG.mimeType
            }

            OutputFormat.WEBP -> {
                @Suppress("DEPRECATION")
                FileOutputStream(out).use {
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, it)
                }
                OutputFormat.WEBP.mimeType
            }

            OutputFormat.HEIF -> {
                try {
                    encodeHeif(bitmap, out, quality, exifBlock)
                    OutputFormat.HEIF.mimeType
                } catch (e: Exception) {
                    Log.w(TAG, "HEIF encoding failed, falling back to JPEG", e)
                    FileOutputStream(out).use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
                    }
                    OutputFormat.JPEG.mimeType
                }
            }
        }
    }

    /**
     * Encodes HEIF using the device encoder via HeifWriter.
     * HEVC requires even dimensions, so the bitmap is scaled when needed.
     */
    private fun encodeHeif(bitmap: Bitmap, out: File, quality: Int, exifBlock: ByteArray?) {
        val width = bitmap.width - bitmap.width % 2
        val height = bitmap.height - bitmap.height % 2
        val input =
            if (width != bitmap.width || height != bitmap.height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }

        val writer = HeifWriter.Builder(out.absolutePath, width, height, HeifWriter.INPUT_MODE_BITMAP)
            .setQuality(quality)
            .setMaxImages(1)
            .build()

        try {
            writer.start()
            writer.addBitmap(input)
            // Embed the original EXIF block (time, GPS, camera info).
            // Must be called after start() and before stop().
            if (exifBlock != null && exifBlock.isNotEmpty()) {
                writer.addExifData(0, exifBlock, 0, exifBlock.size)
            }
            writer.stop(HEIF_STOP_TIMEOUT_MS)
        } finally {
            writer.close()
            if (input !== bitmap) {
                input.recycle()
            }
        }
    }

    /**
     * Writes the encoded temp file into MediaStore under
     * Pictures/[OUTPUT_FOLDER], so all converted photos end up in one
     * folder no matter where the originals were located.
     */
    private fun publish(
        temp: File,
        suggestedName: String?,
        mimeType: String,
        takenMillis: Long?
    ): Uri {
        val displayName = buildOutputName(suggestedName, mimeType)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + OUTPUT_FOLDER
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
            if (takenMillis != null) {
                put(MediaStore.Images.Media.DATE_TAKEN, takenMillis)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create output entry in MediaStore")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(temp).use { input -> input.copyTo(out) }
            } ?: throw IOException("Unable to open output stream for $uri")

            if (takenMillis != null) {
                // Keep MediaStore timestamps aligned with the shooting time,
                // so timeline sorting matches the original photo.
                val timeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATE_ADDED, takenMillis / 1000)
                    put(MediaStore.Images.Media.DATE_MODIFIED, takenMillis / 1000)
                }
                resolver.update(uri, timeValues, null, null)
            }

            val finish = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, finish, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun buildOutputName(suggestedName: String?, mimeType: String): String {
        val ext = when (mimeType) {
            OutputFormat.HEIF.mimeType -> "heic"
            OutputFormat.WEBP.mimeType -> "webp"
            else -> "jpg"
        }
        val base = if (!suggestedName.isNullOrBlank()) {
            val dot = suggestedName.lastIndexOf('.')
            if (dot > 0) suggestedName.substring(0, dot) else suggestedName
        } else {
            "IMG_${System.currentTimeMillis()}"
        }
        return "${base}_opt.$ext"
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    return cursor.getLong(0)
                }
            }
        return 0L
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    return cursor.getString(0)
                }
            }
        return null
    }

    companion object {
        private const val TAG = "ImageRepository"
        private const val HEIF_STOP_TIMEOUT_MS = 30_000L

        /** Aggregated output folder, visible in the Gallery app. */
        const val OUTPUT_FOLDER = "MediaOptimizer"
    }
}
