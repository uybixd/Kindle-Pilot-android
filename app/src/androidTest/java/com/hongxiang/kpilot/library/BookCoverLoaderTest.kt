package com.hongxiang.kpilot.library

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BookCoverLoaderTest {
    @Test fun decodedCoverSurvivesTabRecreationWithoutReadingTheFileAgain() = runBlocking {
        val file = createCover()
        val loader = BookCoverLoader()
        try {
            val image = requireNotNull(loader.load(file.absolutePath))
            assertTrue(file.delete())
            assertSame(image, loader.cached(file.absolutePath))
            val concurrentRequests = List(4) { async { loader.load(file.absolutePath) } }.awaitAll()
            concurrentRequests.forEach { assertSame(image, it) }
            loader.clear()
            assertNull(loader.cached(file.absolutePath))
            assertNull(loader.load(file.absolutePath))
        } finally { file.delete(); loader.clear() }
    }

    @Test fun missingAndInvalidCoversUseThePlaceholder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invalid = File.createTempFile("invalid-cover-", ".jpg", context.cacheDir).apply { writeText("invalid") }
        val loader = BookCoverLoader()
        try {
            assertNull(loader.load(invalid.absolutePath))
            assertNull(loader.load(invalid.absolutePath + ".missing"))
            assertNull(loader.cached(null))
        } finally { invalid.delete(); loader.clear() }
    }

    @Test fun existingTinyKindlePlaceholderIsRejectedWithoutAnotherSync() = runBlocking {
        val file = createCover(64, 96)
        val loader = BookCoverLoader()
        try {
            assertNull(loader.load(file.absolutePath))
            assertNull(loader.cached(file.absolutePath))
        } finally { file.delete(); loader.clear() }
    }

    private fun createCover(width: Int = 120, height: Int = 180): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("cover-cache-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return file
    }
}
