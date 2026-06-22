package dev.jdtech.jellyfin.utils.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the three pure helper functions in MediaDownloadEngine.kt:
 * [shouldPauseTransfer], [resolveResumePlan], and [parseContentRangeTotal].
 *
 * These functions are file-top-level `internal` helpers so they are directly accessible
 * here (same package, test source set).
 */
class MediaDownloadEngineHelpersTest {

    // ── shouldPauseTransfer truth table ──────────────────────────────────────

    @Test
    fun `offline always pauses regardless of allowances`() {
        assertTrue(
            shouldPauseTransfer(
                isOnline = false,
                isMetered = false,
                isRoaming = false,
                allowMetered = true,
                allowRoaming = true,
            ),
        )
    }

    @Test
    fun `offline with all restrictions still pauses`() {
        assertTrue(
            shouldPauseTransfer(
                isOnline = false,
                isMetered = true,
                isRoaming = true,
                allowMetered = false,
                allowRoaming = false,
            ),
        )
    }

    @Test
    fun `metered network without allowance pauses`() {
        assertTrue(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = true,
                isRoaming = false,
                allowMetered = false,
                allowRoaming = true,
            ),
        )
    }

    @Test
    fun `roaming network without allowance pauses`() {
        assertTrue(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = false,
                isRoaming = true,
                allowMetered = true,
                allowRoaming = false,
            ),
        )
    }

    @Test
    fun `metered and roaming without either allowance pauses`() {
        assertTrue(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = true,
                isRoaming = true,
                allowMetered = false,
                allowRoaming = false,
            ),
        )
    }

    @Test
    fun `online unmetered non-roaming does not pause`() {
        assertFalse(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = false,
                isRoaming = false,
                allowMetered = false,
                allowRoaming = false,
            ),
        )
    }

    @Test
    fun `metered allowed does not pause`() {
        assertFalse(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = true,
                isRoaming = false,
                allowMetered = true,
                allowRoaming = false,
            ),
        )
    }

    @Test
    fun `roaming allowed does not pause`() {
        assertFalse(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = false,
                isRoaming = true,
                allowMetered = false,
                allowRoaming = true,
            ),
        )
    }

    @Test
    fun `metered and roaming both allowed does not pause`() {
        assertFalse(
            shouldPauseTransfer(
                isOnline = true,
                isMetered = true,
                isRoaming = true,
                allowMetered = true,
                allowRoaming = true,
            ),
        )
    }

    // ── resolveResumePlan ────────────────────────────────────────────────────

    @Test
    fun `206 with parseable Content-Range total uses that total`() {
        val plan = resolveResumePlan(
            code = 206,
            existingLength = 500L,
            contentLength = 524L,
            contentRangeTotal = 1024L,
        )
        assertEquals(500L, plan.startByte)
        assertEquals(1024L, plan.totalBytes)
        assertFalse(plan.complete)
    }

    @Test
    fun `206 without Content-Range total falls back to existingLength plus contentLength`() {
        val plan = resolveResumePlan(
            code = 206,
            existingLength = 200L,
            contentLength = 824L,
            contentRangeTotal = -1L,
        )
        assertEquals(200L, plan.startByte)
        assertEquals(1024L, plan.totalBytes)
        assertFalse(plan.complete)
    }

    @Test
    fun `206 without Content-Range and unknown contentLength yields unknown total`() {
        val plan = resolveResumePlan(
            code = 206,
            existingLength = 200L,
            contentLength = -1L,
            contentRangeTotal = -1L,
        )
        assertEquals(200L, plan.startByte)
        assertEquals(-1L, plan.totalBytes)
        assertFalse(plan.complete)
    }

    @Test
    fun `200 truncates file to 0 and uses contentLength as total`() {
        val plan = resolveResumePlan(
            code = 200,
            existingLength = 500L,
            contentLength = 2048L,
            contentRangeTotal = -1L,
        )
        assertEquals(0L, plan.startByte)
        assertEquals(2048L, plan.totalBytes)
        assertFalse(plan.complete)
    }

    @Test
    fun `200 with unknown contentLength yields unknown total`() {
        val plan = resolveResumePlan(
            code = 200,
            existingLength = 0L,
            contentLength = -1L,
            contentRangeTotal = -1L,
        )
        assertEquals(0L, plan.startByte)
        assertEquals(-1L, plan.totalBytes)
        assertFalse(plan.complete)
    }

    @Test
    fun `416 marks file as already complete`() {
        val plan = resolveResumePlan(
            code = 416,
            existingLength = 1024L,
            contentLength = -1L,
            contentRangeTotal = -1L,
        )
        assertEquals(1024L, plan.startByte)
        assertEquals(1024L, plan.totalBytes)
        assertTrue(plan.complete)
    }

    // ── parseContentRangeTotal ───────────────────────────────────────────────

    @Test
    fun `parses standard Content-Range header correctly`() {
        assertEquals(1024L, parseContentRangeTotal("bytes 200-1023/1024"))
    }

    @Test
    fun `wildcard total returns minus one`() {
        assertEquals(-1L, parseContentRangeTotal("bytes 0-0/*"))
    }

    @Test
    fun `null header returns minus one`() {
        assertEquals(-1L, parseContentRangeTotal(null))
    }

    @Test
    fun `malformed header without slash returns minus one`() {
        assertEquals(-1L, parseContentRangeTotal("bytes 200-1023"))
    }

    @Test
    fun `malformed total string returns minus one`() {
        assertEquals(-1L, parseContentRangeTotal("bytes 200-1023/abc"))
    }

    @Test
    fun `large Content-Range total is parsed correctly`() {
        assertEquals(5_368_709_120L, parseContentRangeTotal("bytes 0-999999/5368709120"))
    }

    @Test
    fun `zero total parses to zero`() {
        assertEquals(0L, parseContentRangeTotal("bytes 0-0/0"))
    }
}
