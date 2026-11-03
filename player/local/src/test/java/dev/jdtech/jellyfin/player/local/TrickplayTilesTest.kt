package dev.jdtech.jellyfin.player.local

import dev.jdtech.jellyfin.player.local.domain.TrickplayTileLocation
import dev.jdtech.jellyfin.player.local.domain.TrickplayTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrickplayTilesTest {

    // Jellyfin's defaults: 320x180 thumbnails, 10x10 per sheet, one every 10 s.
    private val tiles =
        TrickplayTiles(
            width = 320,
            height = 180,
            tileWidth = 10,
            tileHeight = 10,
            thumbnailCount = 250,
            interval = 10_000,
        )

    @Test
    fun `the start is the first cell of the first sheet`() {
        assertEquals(TrickplayTileLocation(0, 0, 0, 0), tiles.locate(0))
    }

    @Test
    fun `a position inside an interval belongs to the thumbnail it started in`() {
        assertEquals(tiles.locate(10_000), tiles.locate(19_999))
        assertEquals(1, tiles.locate(19_999)?.index)
    }

    @Test
    fun `cells run left to right, then down`() {
        // Thumbnail 9: last cell of the first row.
        assertEquals(TrickplayTileLocation(9, 0, 9 * 320, 0), tiles.locate(95_000))
        // Thumbnail 10: first cell of the second row.
        assertEquals(TrickplayTileLocation(10, 0, 0, 180), tiles.locate(100_000))
        // Thumbnail 57: row 5, column 7.
        assertEquals(TrickplayTileLocation(57, 0, 7 * 320, 5 * 180), tiles.locate(570_000))
    }

    @Test
    fun `the next sheet starts again at the top left`() {
        assertEquals(TrickplayTileLocation(99, 0, 9 * 320, 9 * 180), tiles.locate(999_999))
        assertEquals(TrickplayTileLocation(100, 1, 0, 0), tiles.locate(1_000_000))
        assertEquals(TrickplayTileLocation(213, 2, 3 * 320, 180), tiles.locate(2_130_000))
    }

    @Test
    fun `positions past the end use the last thumbnail, and before the start the first`() {
        assertEquals(TrickplayTileLocation(249, 2, 9 * 320, 4 * 180), tiles.locate(10_000_000))
        assertEquals(tiles.locate(0), tiles.locate(-5_000))
    }

    @Test
    fun `the sheet count rounds up for a partial last sheet`() {
        assertEquals(3, tiles.sheetCount)
        assertEquals(1, tiles.copy(thumbnailCount = 100).sheetCount)
        assertEquals(2, tiles.copy(thumbnailCount = 101).sheetCount)
    }

    @Test
    fun `non-square grids use the column count for the row`() {
        val wide = tiles.copy(tileWidth = 5, tileHeight = 2)
        assertEquals(TrickplayTileLocation(7, 0, 2 * 320, 180), wide.locate(70_000))
        assertEquals(TrickplayTileLocation(10, 1, 0, 0), wide.locate(100_000))
    }

    @Test
    fun `no data gives no cell`() {
        assertNull(tiles.copy(thumbnailCount = 0).locate(0))
        assertNull(tiles.copy(interval = 0).locate(0))
        assertNull(tiles.copy(tileWidth = 0).locate(0))
        assertEquals(0, tiles.copy(thumbnailCount = 0).sheetCount)
    }

    @Test
    fun `the sample size keeps the thumbnail at least as wide as shown`() {
        assertEquals(1, TrickplayTiles.sampleSize(320, 480))
        assertEquals(1, TrickplayTiles.sampleSize(320, 320))
        assertEquals(1, TrickplayTiles.sampleSize(320, 161))
        assertEquals(2, TrickplayTiles.sampleSize(320, 160))
        assertEquals(4, TrickplayTiles.sampleSize(640, 160))
        assertEquals(1, TrickplayTiles.sampleSize(320, 0))
    }
}
