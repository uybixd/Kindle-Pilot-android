package com.hongxiang.kpilot.library

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** A bounded, ViewModel-owned cache; tab composition never reads or decodes cover files. */
class BookCoverLoader {
    private data class Cover(val image: ImageBitmap, val bytes: Int)

    private val maximumBytes = (Runtime.getRuntime().maxMemory() / 16)
        .coerceIn(4L * 1024 * 1024, 16L * 1024 * 1024).toInt()
    private val memory = object : LruCache<String, Cover>(maximumBytes) {
        override fun sizeOf(key: String, value: Cover) = value.bytes
    }
    private val decodeMutex = Mutex()
    private val generation = AtomicInteger()

    fun cached(path: String?): ImageBitmap? = path?.let { memory.get(it)?.image }

    suspend fun load(path: String): ImageBitmap? {
        cached(path)?.let { return it }
        // Serialize misses to avoid parallel decode bursts and duplicate loads of shared covers.
        return decodeMutex.withLock {
            cached(path)?.let { return@withLock it }
            val startedAt = generation.get()
            val cover = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                // Kindle can supply a tiny "No image available" JPEG. Apply the
                // same size rule to existing cached files as to freshly synced covers.
                if (!LibraryRules.isUsableCoverSize(bounds.outWidth, bounds.outHeight)) return@withContext null
                var sampling = 1
                while (bounds.outWidth / sampling > 600 || bounds.outHeight / sampling > 900) sampling *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampling })
                    ?.let { bitmap ->
                        bitmap.prepareToDraw()
                        Cover(bitmap.asImageBitmap(), bitmap.allocationByteCount)
                    }
            }
            if (cover != null) synchronized(memory) {
                if (startedAt == generation.get()) memory.put(path, cover)
            }
            cover?.image
        }
    }

    fun clear() = synchronized(memory) {
        generation.incrementAndGet()
        memory.evictAll()
    }
}
