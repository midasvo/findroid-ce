package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

// Media3 retries on top of these, so a dead server stays on the spinner for several times as long.
private const val HTTP_CONNECT_TIMEOUT_MS = 15_000
private const val HTTP_READ_TIMEOUT_MS = 60_000

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
