package com.sonyphototransfer.app.thumbnails

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.sonyphototransfer.app.camera.CameraSession
import com.sonyphototransfer.camera.PhotoResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads camera-made previews (TN_/SM_/LRG_) for the gallery and viewer.
 *
 * Kept apart from transfers on purpose: it has its own small concurrency limit and
 * memory cache, and never touches original files, so a long download does not stall
 * scrolling and thumbnails never compete for memory with full-size photos.
 */
class ThumbnailLoader(private val session: CameraSession) {

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // The camera serves previews slowly (~0.5 s each); more parallel requests do not help it.
    private val permits = Semaphore(2)

    fun cached(resource: PhotoResource, maxPx: Int): Bitmap? = cache.get(key(resource, maxPx))

    suspend fun load(resource: PhotoResource, maxPx: Int): Bitmap? {
        val key = key(resource, maxPx)
        cache.get(key)?.let { return it }
        return permits.withPermit {
            cache.get(key) ?: withContext(Dispatchers.IO) {
                runCatching {
                    // Previews are small JPEGs (tens of KB to ~1 MB); reading them fully is fine.
                    val bytes = session.open(resource).use { it.input.readBytes() }
                    decode(bytes, maxPx)
                }.getOrNull()
            }?.also { cache.put(key, it) }
        }
    }

    fun clear() = cache.evictAll()

    private fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun key(resource: PhotoResource, maxPx: Int) = "${resource.url}@$maxPx"

    private companion object {
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()
    }
}
