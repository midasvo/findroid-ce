package dev.jdtech.jellyfin.ce.presentation.player

import android.view.KeyEvent

/**
 * CE (#126, ADR 0004): what the remote does in the TV player. Plain Kotlin on top of
 * [TvPlayerControls], so it can be unit tested without Compose or a player.
 *
 * - Controls hidden: Left/Right seek back/forward by the configured steps, and holding a key seeks
 *   faster, scaled to the video length. OK shows the controls (and pauses, if set). Up/Down show
 *   the controls. Back hides nothing and is left to the screen.
 * - Controls shown: D-pad keys go to the focused element (the seek bar handles its own). Back hides
 *   the controls before the player is left.
 * - Media keys work in both cases.
 *
 * A hold is applied as one seek when the key is released; the indicator shows the target while the
 * key is held, so the player isn't asked to seek twenty times a second.
 */
class TvPlayerKeyHandler(
    private val controls: TvPlayerControls,
    private val settings: () -> TvSeekSettings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var hold: Hold? = null

    /** Returns true when the key was handled and must not reach the focused element. */
    fun onKeyEvent(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        if (action == KeyEvent.ACTION_UP) return onKeyUp(keyCode)
        if (action != KeyEvent.ACTION_DOWN) return false

        mediaKeyAction(keyCode)?.let {
            it(repeatCount)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Consumed on the up event, so the screen's own Back handler doesn't also fire.
            return controls.controlsVisible
        }

        if (controls.controlsVisible) {
            controls.onUserInteraction()
            return false
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seek(forward = false, repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seek(forward = true, repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // The skip-intro button has focus while the controls are hidden: let it take OK.
                if (controls.skipButtonShown) return false
                if (repeatCount == 0) {
                    if (settings().okPauses) controls.pause()
                    controls.showControls()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (repeatCount == 0) controls.showControls()
                true
            }
            else -> false
        }
    }

    private fun onKeyUp(keyCode: Int): Boolean {
        val held = hold
        if (held != null && held.keyCode == keyCode) {
            hold = null
            if (held.repeated) controls.seekTo(held.targetMs)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && controls.controlsVisible) {
            controls.hideControls()
            return true
        }
        return mediaKeyAction(keyCode) != null
    }

    private fun mediaKeyAction(keyCode: Int): ((repeatCount: Int) -> Unit)? =
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> once { controls.togglePlayPause() }
            KeyEvent.KEYCODE_MEDIA_PLAY -> once { controls.play() }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> once { controls.pause() }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { r -> seek(forward = true, r, keyCode) }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { r -> seek(forward = false, r, keyCode) }
            KeyEvent.KEYCODE_MEDIA_NEXT -> once { controls.next() }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> once { controls.previous() }
            KeyEvent.KEYCODE_MEDIA_STOP -> once { controls.stop() }
            KeyEvent.KEYCODE_CAPTIONS -> once { controls.openSubtitleDialog() }
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> once { controls.openAudioDialog() }
            else -> null
        }

    private fun once(block: () -> Unit): (Int) -> Unit = { repeatCount ->
        if (repeatCount == 0) block()
    }

    private fun seek(
        forward: Boolean,
        repeatCount: Int,
        keyCode: Int =
            if (forward) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT,
    ) {
        val step = settings().let { if (forward) it.forwardMs else it.backMs }
        val duration = controls.durationMs
        val time = now()
        val current = hold?.takeIf { it.keyCode == keyCode }

        if (repeatCount == 0 || current == null) {
            // A single press seeks right away, so quick taps feel immediate.
            val target = clamp(controls.positionMs + if (forward) step else -step, duration)
            controls.seekTo(target)
            hold = Hold(keyCode, startedAt = time, lastAt = time, targetMs = target)
            controls.showSeekIndicator(target, forward)
            return
        }

        val delta = holdSeekMs(step, duration, heldMs = time - current.startedAt, sinceLastMs = time - current.lastAt)
        val target = clamp(current.targetMs + if (forward) delta else -delta, duration)
        hold = current.copy(lastAt = time, targetMs = target, repeated = true)
        controls.showSeekIndicator(target, forward)
    }

    private fun clamp(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)

    private data class Hold(
        val keyCode: Int,
        val startedAt: Long,
        val lastAt: Long,
        val targetMs: Long,
        val repeated: Boolean = false,
    )

    companion object {
        /**
         * How far one key-repeat event moves while a key is held. The speed starts at two steps a
         * second (or the whole video in a minute, if that is faster) and after two seconds goes to
         * the whole video in 20 seconds, so a long film is as quick to cross as an episode.
         */
        fun holdSeekMs(stepMs: Long, durationMs: Long, heldMs: Long, sinceLastMs: Long): Long {
            val videoTime = if (heldMs < HOLD_SPEEDUP_AFTER_MS) 60_000.0 else 20_000.0
            val byStep = stepMs * 2 / 1_000.0
            val byLength = if (durationMs > 0) durationMs / videoTime else 0.0
            val msPerMs = maxOf(byStep, byLength)
            return (msPerMs * sinceLastMs.coerceIn(0L, MAX_REPEAT_GAP_MS)).toLong()
        }

        private const val HOLD_SPEEDUP_AFTER_MS = 2_000L

        /** Ignore long gaps (a dropped repeat), so one event never jumps far. */
        private const val MAX_REPEAT_GAP_MS = 200L
    }
}

data class TvSeekSettings(val backMs: Long, val forwardMs: Long, val okPauses: Boolean)

/** What [TvPlayerKeyHandler] can do with the player and the screen. */
interface TvPlayerControls {
    val controlsVisible: Boolean
    val skipButtonShown: Boolean
    val positionMs: Long
    val durationMs: Long

    fun showControls()

    fun hideControls()

    /** Keeps the shown controls from hiding while the user is busy with them. */
    fun onUserInteraction()

    /** Shows where a seek goes. The screen hides it again a moment after the last call. */
    fun showSeekIndicator(targetMs: Long, forward: Boolean)

    fun seekTo(positionMs: Long)

    fun togglePlayPause()

    fun play()

    fun pause()

    fun next()

    fun previous()

    fun stop()

    fun openAudioDialog()

    fun openSubtitleDialog()
}
