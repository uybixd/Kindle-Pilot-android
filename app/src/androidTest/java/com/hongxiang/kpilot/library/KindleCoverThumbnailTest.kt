package com.hongxiang.kpilot.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class KindleCoverThumbnailTest {
    @Test fun encodesJpegInsideKindleLimits() {
        val source = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 120, 110))
        }
        val encoded = try {
            val png = ByteArrayOutputStream().use { output ->
                check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            requireNotNull(KindleCoverThumbnail.encode(png))
        } finally { source.recycle() }

        assertEquals(0xFF.toByte(), encoded[0])
        assertEquals(0xD8.toByte(), encoded[1])
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        assertEquals(600, bounds.outHeight)
        assertEquals(400, bounds.outWidth)
    }

    @Test fun rejectsInvalidAndTooSmallCovers() {
        assertNull(KindleCoverThumbnail.encode(byteArrayOf(1, 2, 3)))
        val tiny = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        val png = try {
            ByteArrayOutputStream().use { output ->
                check(tiny.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { tiny.recycle() }
        assertNull(KindleCoverThumbnail.encode(png))
        assertNotNull(KindleCoverThumbnail.encode(validCover()))
    }

    private fun validCover(): ByteArray {
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
