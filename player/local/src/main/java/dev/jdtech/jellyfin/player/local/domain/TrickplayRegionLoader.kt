package dev.jdtech.jellyfin.player.local.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import dev.jdtech.jellyfin.player.core.domain.models.TrickplayTileLoader
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The width the TV player shows trickplay thumbnails at. */
const val TV_TRICKPLAY_WIDTH_DP = 240

/**
 * CE (#134): a trickplay loader for weak TV boxes. [TrickplayLoader] keeps whole decoded
 * sprite-sheets in memory (a 10x10 sheet of 320x180 thumbnails is about 23 MB as ARGB). This one
 * keeps only the compressed sheets and decodes one cell at a time with [BitmapRegionDecoder], in
 * RGB_565 and sub-sampled to [targetWidth]. A few recent thumbnails stay cached, so moving back and
 * forth over the same spot doesn't decode again.
 */
class TrickplayRegionLoader(
    private val repository: JellyfinRepository,
    private val itemId: UUID,
    private val tiles: TrickplayTiles,
    /** The width the thumbnail is shown at, in pixels. Decoding stops at the power of two above. */
    targetWidth: Int,
) : TrickplayTileLoader {

    private val sampleSize = TrickplayTiles.sampleSize(tiles.width, targetWidth)

    // One lock for fetching and decoding: decoders aren't shared between threads, and an evicted
    // decoder can be recycled without racing a decode. Seeking asks for one thumbnail at a time,
    // so serial work costs nothing.
    private val lock = Mutex()

    private val decoders =
        object : LruCache<Int, BitmapRegionDecoder>(DECODER_CACHE_SIZE) {
            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: BitmapRegionDecoder,
                newValue: BitmapRegionDecoder?,
            ) {
                // Only called from inside [lock] (put and evictAll), so no decode is running.
                oldValue.recycle()
            }
        }

    // Cropped thumbnails, keyed by thumbnail index. Bitmaps handed out are never recycled: the UI
    // may still draw one after it leaves the cache, so the GC reclaims them.
    private val thumbnails = LruCache<Int, Bitmap>(THUMBNAIL_CACHE_SIZE)

    private val options =
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

    override suspend fun tileAt(positionMs: Long): Bitmap? {
        val location = tiles.locate(positionMs) ?: return null
        thumbnails.get(location.index)?.let {
            return it
        }
        return lock.withLock {
            thumbnails.get(location.index)?.let {
                return@withLock it
            }
            val decoder = decoderFor(location.sheetIndex) ?: return@withLock null
            withContext(Dispatchers.Default) {
                val rect =
                    Rect(
                        location.offsetX,
                        location.offsetY,
                        location.offsetX + tiles.width,
                        location.offsetY + tiles.height,
                    )
                if (rect.right > decoder.width || rect.bottom > decoder.height) {
                    Timber.w(
                        "Trickplay tile out of bounds: sheet=${location.sheetIndex} " +
                            "tile=${location.index} rect=$rect " +
                            "sheet=${decoder.width}x${decoder.height}"
                    )
                    return@withContext null
                }
                try {
                    decoder.decodeRegion(rect, options)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode trickplay tile ${location.index}")
                    null
                }
            }
                ?.also { thumbnails.put(location.index, it) }
        }
    }

    /** Fetches the sheet for [positionMs] ahead of time, so the first thumbnail shows quickly. */
    suspend fun prefetch(positionMs: Long) {
        val location = tiles.locate(positionMs) ?: return
        lock.withLock { decoderFor(location.sheetIndex) }
    }

    private suspend fun decoderFor(sheetIndex: Int): BitmapRegionDecoder? {
        decoders.get(sheetIndex)?.let {
            return it
        }
        if (sheetIndex < 0 || sheetIndex >= tiles.sheetCount) return null
        val decoder =
            withContext(Dispatchers.IO) {
                try {
                    val bytes =
                        repository.getTrickplayData(itemId, tiles.width, sheetIndex)
                            ?: return@withContext null
                    newDecoder(bytes)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load trickplay sheet $sheetIndex")
                    null
                }
            } ?: return null
        decoders.put(sheetIndex, decoder)
        return decoder
    }

    override fun release() {
        thumbnails.evictAll()
        // Called on the main thread. If a decode is running, leave the decoders to the GC
        // instead of recycling one under it.
        if (lock.tryLock()) {
            try {
                decoders.evictAll()
            } finally {
                lock.unlock()
            }
        }
    }

    private companion object {
        const val DECODER_CACHE_SIZE = 2
        const val THUMBNAIL_CACHE_SIZE = 12

        fun newDecoder(bytes: ByteArray): BitmapRegionDecoder? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
            } else {
                @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            }
    }
}
