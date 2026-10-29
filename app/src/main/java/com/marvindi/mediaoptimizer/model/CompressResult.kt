package com.marvindi.mediaoptimizer.model

import android.net.Uri

/**
 * Supported output formats. HEIF falls back to JPEG automatically
 * if the device cannot encode HEIF.
 */
enum class OutputFormat(val displayName: String, val mimeType: String) {
    HEIF("HEIF", "image/heic"),
    JPEG("JPEG", "image/jpeg"),
    WEBP("WebP", "image/webp")
}

/**
 * Result of compressing a single source image.
 */
data class CompressResult(
    val sourceUri: Uri,
    val outputUri: Uri,
    val originalBytes: Long,
    val newBytes: Long
) {
    val savedBytes: Long
        get() = (originalBytes - newBytes).coerceAtLeast(0L)
}
