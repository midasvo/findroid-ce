package dev.jdtech.jellyfin.utils.download

/**
 * Estimates the size in bytes of a download transcoded down to a quality cap.
 *
 * The server only reports the original file's size, and a live transcode streams output of
 * unknown length, so the cap ([maxBitrate], bits/sec) times the runtime is the best guess.
 * Returns 0 when the runtime is unknown.
 */
internal fun estimateCappedDownloadSize(runtimeTicks: Long, maxBitrate: Int): Long {
    if (runtimeTicks <= 0L || maxBitrate <= 0) return 0L
    val durationSeconds = runtimeTicks / 10_000_000.0
    return (maxBitrate / 8.0 * durationSeconds).toLong()
}
