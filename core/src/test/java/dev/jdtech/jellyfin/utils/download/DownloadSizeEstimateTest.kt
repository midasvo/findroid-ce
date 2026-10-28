package dev.jdtech.jellyfin.utils.download

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [estimateCappedDownloadSize]. */
class DownloadSizeEstimateTest {

    @Test
    fun `two hours at 3 Mbps is 2_7 GB`() {
        // 2 h = 7200 s = 72_000_000_000 ticks; 3 Mbps = 375_000 bytes/s
        assertEquals(2_700_000_000L, estimateCappedDownloadSize(72_000_000_000L, 3_000_000))
    }

    @Test
    fun `unknown runtime gives no estimate`() {
        assertEquals(0L, estimateCappedDownloadSize(0L, 3_000_000))
    }

    @Test
    fun `non-positive cap gives no estimate`() {
        assertEquals(0L, estimateCappedDownloadSize(72_000_000_000L, 0))
    }
}
