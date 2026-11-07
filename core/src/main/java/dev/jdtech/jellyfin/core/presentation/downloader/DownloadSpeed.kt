package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.core.Constants

/** Outcome of one download-speed sample. */
internal data class SpeedSample(
    /** Transfer rate in bytes/sec to display. */
    val bytesPerSecond: Long,
    /** True when real progress was observed and the sample clock should advance. */
    val advanced: Boolean,
)

/**
 * Computes the download speed for one poll tick.
 *
 * The engine's byte counter may update less often than the queue polls (~1 s), so some
 * polls see the exact same byte count. A naive delta/elapsed then yields 0 on those polls,
 * which makes the speed + ETA flash in the UI. This keeps the last known speed across flat
 * polls and only advances the sample clock on real progress, so the next delta is measured
 * over the true interval.
 */
internal fun nextDownloadSpeed(
    prevSample: Pair<Long, Long>?,
    currentBytes: Long,
    nowMs: Long,
    previousSpeed: Long,
): SpeedSample {
    if (currentBytes < 0) return SpeedSample(previousSpeed, advanced = false)
    if (prevSample == null) return SpeedSample(previousSpeed, advanced = true)
    val (prevBytes, prevAt) = prevSample
    val deltaBytes = currentBytes - prevBytes
    val elapsedMs = nowMs - prevAt
    return if (deltaBytes > 0 && elapsedMs > 0) {
        SpeedSample((deltaBytes * 1000L / elapsedMs).coerceAtLeast(0L), advanced = true)
    } else {
        // Byte counter has not advanced since the last sample — keep the last
        // known speed instead of flashing 0.
        SpeedSample(previousSpeed, advanced = false)
    }
}

/** Relative speed change below which a speed-only difference is not worth an emission. */
private const val SPEED_EMIT_PERCENT = 10L

/**
 * Whether a new speed differs enough from the displayed one to justify re-emitting the queue
 * when nothing else (state, progress, bytes) changed. The speed is shown in the UI, so it is
 * never frozen: any change of at least 10%, or to/from zero, is emitted, and the latest speed
 * rides along with every other emission.
 */
internal fun isSignificantSpeedChange(previous: Long, current: Long): Boolean {
    if (previous == current) return false
    if (previous <= 0L || current <= 0L) return true
    return kotlin.math.abs(current - previous) * 100L >= previous * SPEED_EMIT_PERCENT
}

/** Poll every 2 s when several downloads are active, otherwise at the base interval. */
internal fun pollIntervalMs(activeDownloads: Int): Long =
    if (activeDownloads > 1) Constants.DOWNLOAD_POLL_INTERVAL_MS * 2
    else Constants.DOWNLOAD_POLL_INTERVAL_MS
