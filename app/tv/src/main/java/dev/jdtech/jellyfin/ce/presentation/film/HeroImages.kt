package dev.jdtech.jellyfin.ce.presentation.film

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.utils.withImageSize

/**
 * The hero's backdrop and logo requests, and the prefetch of the cards around the focused one
 * (#155).
 *
 * The hero and the prefetch build their requests here, so a prefetched image is a memory cache hit
 * when the hero asks for it: same URL, same size and the URL as the memory cache key. The size is
 * explicit, not the layout's, so a request without a view decodes to the same bitmap.
 *
 * Nothing here is state: a prefetch recomposes nothing.
 */
internal class HeroImages(
    private val context: PlatformContext,
    private val imageLoader: ImageLoader,
    private val backdropWidthPx: Int,
    private val logoWidthPx: Int,
    private val logoHeightPx: Int,
) {
    /** Prefetches that may still be loading, by memory cache key. */
    private val pending = mutableMapOf<MemoryCache.Key, Disposable>()

    fun backdrop(item: FindroidItem): ImageRequest? =
        heroBackdropUrl(item, backdropWidthPx)?.let {
            request(it, backdropWidthPx, backdropWidthPx * 9 / 16, Scale.FILL)
        }

    fun logo(item: FindroidItem): ImageRequest? =
        heroLogoUrl(item, logoWidthPx)?.let { request(it, logoWidthPx, logoHeightPx, Scale.FIT) }

    private fun request(url: String, width: Int, height: Int, scale: Scale) =
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(url)
            .size(width, height)
            .scale(scale)
            .precision(Precision.INEXACT)
            .build()

    private fun requests(item: FindroidItem) = listOfNotNull(backdrop(item), logo(item))

    /** True when the item's backdrop and logo can be drawn straight from the memory cache. */
    fun isCached(item: FindroidItem): Boolean = requests(item).all { it.isCached() }

    /** Waits for a prefetch of the item's images that is still loading. */
    suspend fun awaitPrefetch(item: FindroidItem): Boolean {
        val jobs = requests(item).mapNotNull { pending[it.key()]?.takeUnless { it.isDisposed } }
        jobs.forEach { it.job.join() }
        return jobs.isNotEmpty()
    }

    /**
     * Loads the images of [neighbours] into the memory cache. Prefetches for cards that are no longer
     * near [focused] are cancelled; one for [focused] itself keeps running, as the hero waits for it.
     */
    fun prefetch(focused: FindroidItem, neighbours: List<FindroidItem>) {
        val wanted = neighbours.flatMap { requests(it) }
        val keep = (wanted + requests(focused)).mapTo(HashSet()) { it.key() }
        cancel { it !in keep }
        for (request in wanted) {
            val key = request.key()
            if (key in pending || request.isCached()) continue
            pending[key] = imageLoader.enqueue(request)
        }
    }

    /** Cancels prefetches whose key matches [which], and forgets the ones that are done. */
    fun cancel(which: (MemoryCache.Key) -> Boolean = { true }) {
        pending.entries.removeAll { (key, disposable) ->
            when {
                disposable.isDisposed -> true
                which(key) -> {
                    disposable.dispose()
                    true
                }
                else -> false
            }
        }
    }

    private fun ImageRequest.key() = MemoryCache.Key(memoryCacheKey!!, memoryCacheKeyExtras)

    private fun ImageRequest.isCached() = imageLoader.memoryCache?.get(key()) != null
}

/** The hero shows an episode on its show's backdrop and logo. */
internal fun heroBackdropUrl(item: FindroidItem, widthPx: Int): String? {
    val uri =
        when (item) {
            is FindroidEpisode -> item.images.showBackdrop ?: item.images.backdrop
            else -> item.images.backdrop
        }
    return uri?.toString()?.withImageSize(widthPx, null)
}

internal fun heroLogoUrl(item: FindroidItem, widthPx: Int): String? {
    val uri = if (item is FindroidEpisode) item.images.showLogo else item.images.logo
    return uri?.toString()?.withImageSize(widthPx, null)
}

/**
 * The cards around [index] in a row of [size], nearest first and the next card before the previous
 * one, as focus moves right more often. At most [count] on each side.
 */
internal fun neighbourIndices(index: Int, size: Int, count: Int): List<Int> = buildList {
    for (distance in 1..count) {
        (index + distance).takeIf { it < size }?.let(::add)
        (index - distance).takeIf { it >= 0 }?.let(::add)
    }
}
