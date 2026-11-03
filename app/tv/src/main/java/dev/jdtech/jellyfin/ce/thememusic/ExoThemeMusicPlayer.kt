package dev.jdtech.jellyfin.ce.thememusic

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import timber.log.Timber

/**
 * CE (#141): a small audio-only ExoPlayer for the show page's theme song, apart from the video
 * player. It's built on [play] and released after the fade-out or at the song's end, so nothing is
 * held while the page is quiet. It doesn't request audio focus, so it never pauses another app.
 *
 * Call it on the main thread only.
 */
@OptIn(UnstableApi::class)
class ExoThemeMusicPlayer(context: Context, private val onFinished: () -> Unit) :
    ThemeMusicOutput {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var fade: Runnable? = null

    override fun play(url: String) {
        release()
        // Only an audio renderer: no video, text or metadata renderers to set up.
        val renderers = RenderersFactory { eventHandler, _, audioListener, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    eventHandler,
                    audioListener,
                )
            )
        }
        val player =
            ExoPlayer.Builder(context, renderers)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ false,
                )
                .build()
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) finish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.w(error, "Theme song failed")
                    finish()
                }
            }
        )
        player.volume = 0f
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        this.player = player
        rampVolume(to = VOLUME, durationMs = FADE_IN_MS) {}
    }

    override fun fadeOut() {
        if (player == null) return
        rampVolume(to = 0f, durationMs = FADE_OUT_MS) { release() }
    }

    /** Stop at once, without a fade. */
    fun release() {
        fade?.let(handler::removeCallbacks)
        fade = null
        player?.release()
        player = null
    }

    private fun finish() {
        release()
        onFinished()
    }

    private fun rampVolume(to: Float, durationMs: Long, then: () -> Unit) {
        fade?.let(handler::removeCallbacks)
        val player = player ?: return
        val from = player.volume
        val steps = (durationMs / STEP_MS).coerceAtLeast(1)
        var step = 0L
        val runnable =
            object : Runnable {
                override fun run() {
                    step++
                    player.volume = from + (to - from) * step / steps
                    if (step < steps) {
                        handler.postDelayed(this, STEP_MS)
                    } else {
                        fade = null
                        then()
                    }
                }
            }
        fade = runnable
        handler.postDelayed(runnable, STEP_MS)
    }

    private companion object {
        // Quiet: background to the page, well under the volume the episodes will play at.
        const val VOLUME = 0.3f
        const val FADE_IN_MS = 2_000L
        const val FADE_OUT_MS = 600L
        const val STEP_MS = 50L
    }
}
