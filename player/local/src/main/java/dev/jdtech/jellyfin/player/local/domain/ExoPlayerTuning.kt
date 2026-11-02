package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

private const val MAX_TARGET_BUFFER_BYTES = 384L * 1024 * 1024
private const val STREAMING_MAX_BUFFER_MS = 120_000

// Media3 retries on top of these, so a dead server stays on the spinner for several times as long.
private const val HTTP_CONNECT_TIMEOUT_MS = 15_000
private const val HTTP_READ_TIMEOUT_MS = 60_000

/**
 * A third of the heap, because the buffer lives on it. Media3's own target of about 138 MB is more
 * than the whole heap on 128 MB devices. The floor is deliberately its small pre-selection minimum:
 * a stock floor would let the loader fill such a heap.
 */
internal fun targetBufferBytes(maxHeapBytes: Long): Int =
    (maxHeapBytes / 3)
        .coerceAtMost(MAX_TARGET_BUFFER_BYTES)
        .coerceAtLeast(DefaultLoadControl.DEFAULT_MIN_BUFFER_SIZE.toLong())
        .toInt()

/**
 * Scales the byte limit to the heap and keeps up to 120 s of a stream loaded, so a high bitrate
 * remux has more to fall back on than Media3's time limit gives it. Low-RAM devices can't spare
 * that next to their decode buffers, and keep the stock durations.
 */
internal fun buildLoadControl(maxHeapBytes: Long, isLowRamDevice: Boolean): DefaultLoadControl {
    val builder =
        DefaultLoadControl.Builder().setTargetBufferBytes(targetBufferBytes(maxHeapBytes))
    if (!isLowRamDevice) {
        builder.setBufferDurationsMsForStreaming(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            STREAMING_MAX_BUFFER_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
    }
    return builder.build()
}

/** Gives a transcode longer than Media3's default 8 s to return its first bytes. */
internal fun buildMediaSourceFactory(context: Context): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS),
        )
    )

/**
 * Tunneling stays on except with external subtitles and lossless audio. Tunneled TrueHD/MLP
 * passthrough can be silent with no error on some AV receivers. Media3 uses one MIME type for both.
 */
internal fun shouldTunnel(hasExternalSubtitles: Boolean, audioMimeType: String?): Boolean =
    !hasExternalSubtitles && audioMimeType != MimeTypes.AUDIO_TRUEHD
