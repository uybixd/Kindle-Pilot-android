package com.hongxiang.kpilot.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Produces the 600-pixel JPEG thumbnail Kindle expects, mirroring the iOS ImageIO writer. */
internal object KindleCoverThumbnail {
    private const val MAX_PIXEL_SIZE = 600
    private const val MIN_PIXEL_SIZE = 100
    private const val JPEG_QUALITY = 85

    fun encode(cover: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(cover, 0, cover.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode a power-of-two sample first so a large source image never occupies
        // its full decoded size in memory, while still retaining at least 600 px.
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_PIXEL_SIZE) sampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            cover, 0, cover.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                // Keep a software bitmap so rotation, scaling and JPEG compression
                // work identically on every Android release.
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val oriented = applyExifOrientation(bitmap, cover)
        val scaled = scaleToFit(oriented, MAX_PIXEL_SIZE)
        return try {
            if (scaled.width < MIN_PIXEL_SIZE || scaled.height < MIN_PIXEL_SIZE) return null
            ByteArrayOutputStream().use { output ->
                if (scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) output.toByteArray() else null
            }
        } finally {
            if (scaled !== oriented && scaled !== bitmap) scaled.recycle()
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, source: ByteArray): Bitmap {
        val orientation = runCatching {
            ByteArrayInputStream(source).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToFit(bitmap: Bitmap, maximum: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maximum) return bitmap
        val ratio = maximum.toDouble() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }
}
