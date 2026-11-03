package dev.jdtech.jellyfin.ce.thememusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMusicSessionTest {

    private class FakeOutput : ThemeMusicOutput {
        val events = mutableListOf<String>()

        override fun play(url: String) {
            events += "play $url"
        }

        override fun fadeOut() {
            events += "fadeOut"
        }
    }

    private val output = FakeOutput()
    private var otherAudio = false
    private var playedCallbacks = 0

    private fun session(played: Boolean = false) =
        ThemeMusicSession(
            output = output,
            isOtherAudioActive = { otherAudio },
            played = played,
            onPlayed = { playedCallbacks++ },
        )

    private fun playing(): ThemeMusicSession =
        session().apply {
            onForeground()
            onSongFound(SONG)
        }

    @Test
    fun `starts when the song arrives while the page is in the foreground`() {
        val session = session()
        session.onForeground()
        assertTrue(output.events.isEmpty())

        session.onSongFound(SONG)

        assertEquals(listOf("play $SONG"), output.events)
        assertTrue(session.isPlaying)
        assertTrue(session.played)
        assertEquals(1, playedCallbacks)
    }

    @Test
    fun `starts when the page comes to the foreground after the song arrived`() {
        val session = session()
        session.onSongFound(SONG)
        assertTrue(output.events.isEmpty())

        session.onForeground()

        assertEquals(listOf("play $SONG"), output.events)
    }

    @Test
    fun `no song does nothing`() {
        val session = session()
        session.onForeground()
        session.onSongFound(null)
        session.onLeave()

        assertTrue(output.events.isEmpty())
        assertFalse(session.played)
    }

    @Test
    fun `stays quiet when another app is playing audio, also after it stops`() {
        otherAudio = true
        val session = session()
        session.onForeground()
        session.onSongFound(SONG)
        otherAudio = false
        session.onBackground()
        session.onForeground()

        assertTrue(output.events.isEmpty())
        assertTrue(session.played)
    }

    @Test
    fun `leaving the page fades out`() {
        val session = playing()
        session.onLeave()

        assertEquals(listOf("play $SONG", "fadeOut"), output.events)
        assertFalse(session.isPlaying)
    }

    @Test
    fun `playback fades out`() {
        val session = playing()
        session.onPlaybackStarted()

        assertEquals(listOf("play $SONG", "fadeOut"), output.events)
    }

    @Test
    fun `screensaver fades out`() {
        val session = playing()
        session.onScreensaverStarted()

        assertEquals(listOf("play $SONG", "fadeOut"), output.events)
    }

    @Test
    fun `background fades out and the foreground doesn't start it again`() {
        val session = playing()
        session.onBackground()
        session.onForeground()

        assertEquals(listOf("play $SONG", "fadeOut"), output.events)
    }

    @Test
    fun `background before the song started still lets it start on return`() {
        val session = session()
        session.onForeground()
        session.onBackground()
        session.onSongFound(SONG)
        assertTrue(output.events.isEmpty())

        session.onForeground()

        assertEquals(listOf("play $SONG"), output.events)
    }

    @Test
    fun `playback before the song arrived keeps it quiet for this visit`() {
        val session = session()
        session.onForeground()
        session.onPlaybackStarted()
        session.onSongFound(SONG)

        assertTrue(output.events.isEmpty())
        assertTrue(session.played)
        assertEquals(1, playedCallbacks)
    }

    @Test
    fun `a page that already played its song stays quiet when it comes back`() {
        val session = session(played = true)
        session.onForeground()
        session.onSongFound(SONG)

        assertTrue(output.events.isEmpty())
        assertEquals(0, playedCallbacks)
    }

    @Test
    fun `after the song ends nothing is faded out`() {
        val session = playing()
        session.onFinished()
        session.onLeave()

        assertEquals(listOf("play $SONG"), output.events)
    }

    @Test
    fun `stop triggers fade out only once`() {
        val session = playing()
        session.onPlaybackStarted()
        session.onBackground()
        session.onLeave()

        assertEquals(listOf("play $SONG", "fadeOut"), output.events)
    }

    @Test
    fun `the same song found again after a reload doesn't restart it`() {
        val session = playing()
        session.onSongFound(SONG)

        assertEquals(listOf("play $SONG"), output.events)
    }

    private companion object {
        const val SONG = "https://jellyfin.test/Audio/1/stream?static=true"
    }
}
