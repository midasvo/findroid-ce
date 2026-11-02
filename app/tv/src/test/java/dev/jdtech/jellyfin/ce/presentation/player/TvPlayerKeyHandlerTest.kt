package dev.jdtech.jellyfin.ce.presentation.player

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_CAPTIONS
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK
import android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerKeyHandlerTest {
    private class FakeControls : TvPlayerControls {
        override var controlsVisible = false
        override var skipButtonShown = false
        override var positionMs = 600_000L
        override var durationMs = 1_500_000L
        val calls = mutableListOf<String>()
        val seeks = mutableListOf<Long>()
        var indicator: Long? = null

        override fun showControls() {
            controlsVisible = true
            calls += "show"
        }

        override fun hideControls() {
            controlsVisible = false
            calls += "hide"
        }

        override fun onUserInteraction() {
            calls += "interaction"
        }

        override fun showSeekIndicator(targetMs: Long, forward: Boolean) {
            indicator = targetMs
        }

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            this.positionMs = positionMs
        }

        override fun togglePlayPause() {
            calls += "toggle"
        }

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun next() {
            calls += "next"
        }

        override fun previous() {
            calls += "previous"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun openAudioDialog() {
            calls += "audio"
        }

        override fun openSubtitleDialog() {
            calls += "subtitles"
        }
    }

    private val controls = FakeControls()
    private var settings = TvSeekSettings(backMs = 10_000L, forwardMs = 30_000L, okPauses = false)
    private var time = 0L
    private val handler = TvPlayerKeyHandler(controls, { settings }, { time })

    private fun press(keyCode: Int) {
        handler.onKeyEvent(keyCode, ACTION_DOWN, 0)
        handler.onKeyEvent(keyCode, ACTION_UP, 0)
    }

    @Test
    fun rightSeeksForwardAndLeftSeeksBackWithTheControlsHidden() {
        press(KEYCODE_DPAD_RIGHT)
        assertEquals(listOf(630_000L), controls.seeks)

        press(KEYCODE_DPAD_LEFT)
        assertEquals(listOf(630_000L, 620_000L), controls.seeks)
        assertFalse(controls.controlsVisible)
    }

    @Test
    fun seekingStopsAtTheStartAndTheEnd() {
        controls.positionMs = 4_000L
        press(KEYCODE_DPAD_LEFT)
        controls.positionMs = 1_490_000L
        press(KEYCODE_DPAD_RIGHT)

        assertEquals(listOf(0L, 1_500_000L), controls.seeks)
    }

    @Test
    fun holdingSeeksOnceOnReleaseAndGoesFasterThanSinglePresses() {
        handler.onKeyEvent(KEYCODE_DPAD_RIGHT, ACTION_DOWN, 0)
        // 3 seconds of key repeat, an event every 50 ms.
        for (repeat in 1..60) {
            time += 50
            handler.onKeyEvent(KEYCODE_DPAD_RIGHT, ACTION_DOWN, repeat)
        }
        assertEquals("only the first press seeks while held", 1, controls.seeks.size)

        handler.onKeyEvent(KEYCODE_DPAD_RIGHT, ACTION_UP, 0)
        val moved = controls.seeks.last() - 600_000L
        // Faster than one 30 s step per second, and the indicator showed the same target.
        assertTrue("moved $moved", moved > 3 * 30_000L)
        assertEquals(controls.seeks.last(), controls.indicator)
    }

    @Test
    fun holdSpeedScalesWithTheVideoLength() {
        val episode = TvPlayerKeyHandler.holdSeekMs(30_000L, 1_500_000L, heldMs = 3_000, sinceLastMs = 50)
        val film = TvPlayerKeyHandler.holdSeekMs(30_000L, 7_200_000L, heldMs = 3_000, sinceLastMs = 50)
        assertTrue(film > episode)
        // Before the speed-up, a step-based floor applies: two steps a second.
        assertEquals(3_000L, TvPlayerKeyHandler.holdSeekMs(30_000L, 0L, heldMs = 0, sinceLastMs = 50))
    }

    @Test
    fun okShowsTheControlsAndOnlyPausesWhenSet() {
        press(KEYCODE_DPAD_CENTER)
        assertEquals(listOf("show"), controls.calls)

        controls.controlsVisible = false
        controls.calls.clear()
        settings = settings.copy(okPauses = true)
        press(KEYCODE_DPAD_CENTER)
        assertEquals(listOf("pause", "show"), controls.calls)
    }

    @Test
    fun okGoesToTheSkipButtonWhenItIsShown() {
        controls.skipButtonShown = true
        assertFalse(handler.onKeyEvent(KEYCODE_DPAD_CENTER, ACTION_DOWN, 0))
        assertFalse(controls.controlsVisible)
    }

    @Test
    fun downShowsTheControls() {
        press(KEYCODE_DPAD_DOWN)
        assertTrue(controls.controlsVisible)
    }

    @Test
    fun backHidesTheControlsFirstAndThenLeavesItToTheScreen() {
        controls.controlsVisible = true
        assertTrue(handler.onKeyEvent(KEYCODE_BACK, ACTION_DOWN, 0))
        assertTrue(handler.onKeyEvent(KEYCODE_BACK, ACTION_UP, 0))
        assertFalse(controls.controlsVisible)

        assertFalse(handler.onKeyEvent(KEYCODE_BACK, ACTION_DOWN, 0))
        assertFalse(handler.onKeyEvent(KEYCODE_BACK, ACTION_UP, 0))
    }

    @Test
    fun dpadKeysGoToTheFocusedElementWhileTheControlsAreShown() {
        controls.controlsVisible = true
        assertFalse(handler.onKeyEvent(KEYCODE_DPAD_RIGHT, ACTION_DOWN, 0))
        assertTrue(controls.seeks.isEmpty())
    }

    @Test
    fun mediaKeysWorkWithTheControlsShownOrHidden() {
        press(KEYCODE_MEDIA_PLAY_PAUSE)
        press(KEYCODE_MEDIA_NEXT)
        press(KEYCODE_CAPTIONS)
        controls.controlsVisible = true
        press(KEYCODE_MEDIA_AUDIO_TRACK)
        press(KEYCODE_MEDIA_STOP)

        assertEquals(listOf("toggle", "next", "subtitles", "audio", "stop"), controls.calls)
    }

    @Test
    fun fastForwardSeeksByTheForwardStep() {
        controls.controlsVisible = true
        press(KEYCODE_MEDIA_FAST_FORWARD)
        assertEquals(listOf(630_000L), controls.seeks)
    }

    @Test
    fun keyRepeatOfAMediaKeyDoesNotRepeatTheAction() {
        handler.onKeyEvent(KEYCODE_MEDIA_PLAY_PAUSE, ACTION_DOWN, 0)
        handler.onKeyEvent(KEYCODE_MEDIA_PLAY_PAUSE, ACTION_DOWN, 1)
        handler.onKeyEvent(KEYCODE_MEDIA_PLAY_PAUSE, ACTION_UP, 0)
        assertEquals(listOf("toggle"), controls.calls)
    }
}
