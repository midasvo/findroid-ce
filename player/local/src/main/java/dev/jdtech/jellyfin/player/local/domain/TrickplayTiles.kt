package dev.jdtech.jellyfin.player.local.domain

import kotlin.math.max

/**
 * Where the thumbnail for a playback position sits: which sprite-sheet, and the pixel offset of
 * its cell in that sheet (at full size).
 */
data class TrickplayTileLocation(
    /** Index of the thumbnail over the whole item. */
    val index: Int,
    val sheetIndex: Int,
    val offsetX: Int,
    val offsetY: Int,
)

/**
 * The layout of an item's trickplay sprite-sheets, as Jellyfin describes it: [tileWidth] x
 * [tileHeight] thumbnails of [width] x [height] pixels per sheet, one every [interval] ms.
 *
 * Pure math, so the phone and TV loaders agree on which cell belongs to a position.
 */
data class TrickplayTiles(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
) {
    val tilesPerSheet: Int = max(1, tileWidth * tileHeight)

    val sheetCount: Int =
        if (thumbnailCount <= 0) 0 else (thumbnailCount - 1) / tilesPerSheet + 1

    /** The cell for [positionMs], clamped to the first and last thumbnail; null without data. */
    fun locate(positionMs: Long): TrickplayTileLocation? {
        if (thumbnailCount <= 0 || interval <= 0 || tileWidth <= 0) return null
        val index =
            (positionMs.coerceAtLeast(0L) / interval)
                .coerceAtMost((thumbnailCount - 1).toLong())
                .toInt()
        val localIndex = index % tilesPerSheet
        return TrickplayTileLocation(
            index = index,
            sheetIndex = index / tilesPerSheet,
            offsetX = (localIndex % tileWidth) * width,
            offsetY = (localIndex / tileWidth) * height,
        )
    }

    companion object {
        /**
         * The largest power-of-two sample size that keeps a thumbnail at least [targetWidth] pixels
         * wide, for `BitmapFactory.Options.inSampleSize`. 1 when the thumbnail is already that small.
         */
        fun sampleSize(width: Int, targetWidth: Int): Int {
            if (width <= 0 || targetWidth <= 0) return 1
            var sampleSize = 1
            while (width / (sampleSize * 2) >= targetWidth) sampleSize *= 2
            return sampleSize
        }
    }
}
