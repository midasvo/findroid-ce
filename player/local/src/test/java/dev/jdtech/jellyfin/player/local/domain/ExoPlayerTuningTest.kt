package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoPlayerTuningTest {
    private val mb = 1024L * 1024

    @Test
    fun `the buffer target is a third of the heap`() {
        assertEquals((128 * mb / 3).toInt(), targetBufferBytes(128 * mb))
        assertEquals((512 * mb / 3).toInt(), targetBufferBytes(512 * mb))
    }

    @Test
    fun `the buffer target is capped at 384 MB`() {
        assertEquals((384 * mb).toInt(), targetBufferBytes(4096 * mb))
    }

    @Test
    fun `the buffer target has Media3's minimum as its floor`() {
        assertEquals(DefaultLoadControl.DEFAULT_MIN_BUFFER_SIZE, targetBufferBytes(16 * mb))
    }

    @Test
    fun `tunneling stays on for ordinary sources`() {
        assertTrue(shouldTunnel(hasExternalSubtitles = false, audioMimeType = null))
        assertTrue(shouldTunnel(hasExternalSubtitles = false, audioMimeType = MimeTypes.AUDIO_E_AC3))
        assertTrue(
            shouldTunnel(hasExternalSubtitles = false, audioMimeType = MimeTypes.AUDIO_E_AC3_JOC)
        )
    }

    @Test
    fun `external subtitles turn tunneling off`() {
        assertFalse(shouldTunnel(hasExternalSubtitles = true, audioMimeType = MimeTypes.AUDIO_AAC))
    }

    @Test
    fun `lossless audio turns tunneling off`() {
        assertFalse(
            shouldTunnel(hasExternalSubtitles = false, audioMimeType = MimeTypes.AUDIO_TRUEHD)
        )
    }
}
