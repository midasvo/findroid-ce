// Adapted from Moonfin (https://github.com/Moonfin-Client/Moonfin-Core) at 9352e95d,
// packages/moonfin_native_video/android/src/main/kotlin/org/moonfin/nativevideo/ResumeWedgePolicy.kt.
// Moonfin is licensed under GPL-2.0-or-later. The playerLive check is dropped here, because the
// recovery runs in a coroutine that ends with the player.
package dev.jdtech.jellyfin.player.local.domain

/**
 * Decides whether a resume that fell straight back into buffering is stuck.
 *
 * Some Android TV devices come back from a long pause to a decoder or bitstream track that never
 * starts again, and the player sits buffering with the film already loaded. A seek in place only
 * flushes whatever is stuck. Preparing the source again at the same spot builds the decoder and the
 * audio track from scratch, which is how the same title started fine in the first place.
 */
object ResumeWedgePolicy {
    /** How long a resume gets before its buffering counts as stuck. */
    const val CHECK_DELAY_MS = 3_000L

    /** Loaded media past the playhead that rules out a network that ran dry. */
    const val RUNWAY_FLOOR_MS = 10_000L

    /**
     * Live sources are left alone because preparing one again can land on the live edge rather
     * than where the viewer was.
     */
    fun shouldReprepare(
        stillBuffering: Boolean,
        playWhenReady: Boolean,
        bufferedAheadMs: Long,
        isLiveSource: Boolean,
    ): Boolean =
        stillBuffering && playWhenReady && !isLiveSource && bufferedAheadMs >= RUNWAY_FLOOR_MS
}
