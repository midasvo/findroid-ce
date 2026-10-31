package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Takes platform audio decoders that failed during playback out of ExoPlayer's selection, so the
 * bundled ffmpeg extension decodes those formats instead.
 *
 * A decoder can advertise a format and still die on it mid-stream: c2.dolby.eac3.decoder on the
 * Pixel 8 rejects DD+ Atmos frames ("unsupported DDP-JOC frame"), although other devices play them
 * fine with the same decoder name. So there is no list of bad decoders to keep; a decoder is ruled
 * out when it fails. ExoPlayer only chooses between renderers when it selects tracks, so the player
 * has to be prepared again afterwards. MediaCodecAudioRenderer then no longer claims the format and
 * ffmpeg takes over at the same position, without rebuilding the player.
 *
 * Broken decoders are remembered per OS build: a later device update may have fixed the decoder,
 * and a one-off failure should not cost a device its platform decoder for good.
 *
 * @param build identifies the OS build, normally [android.os.Build.FINGERPRINT].
 * @param broken the value last passed to [onBrokenChanged].
 * @param onBrokenChanged stores the broken decoders for the next player session.
 */
class AudioDecoderFallback(
    private val build: String,
    broken: Set<String>,
    private val onBrokenChanged: (Set<String>) -> Unit,
) : MediaCodecSelector {
    // Written on the main thread, read on the playback thread during track selection.
    @Volatile private var broken: Set<String> = broken

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> =
        MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            .filterNot { isBroken(it.name) }

    /**
     * Rule out the audio decoder that caused [error], if one did.
     *
     * @return `true` if preparing the player again can recover from [error]. A decoder is only ruled
     *   out once and there are finitely many, so this cannot retry forever. `false` for anything
     *   else, including a failure in ffmpeg itself.
     */
    fun onPlayerError(error: PlaybackException): Boolean =
        failedAudioDecoder(error)?.let { markBroken(it) } ?: false

    internal fun isBroken(decoderName: String): Boolean = key(decoderName) in broken

    /** @return `false` if [decoderName] was already ruled out. */
    internal fun markBroken(decoderName: String): Boolean {
        if (isBroken(decoderName)) return false
        broken = broken + key(decoderName)
        onBrokenChanged(broken)
        return true
    }

    private fun key(decoderName: String) = "$build|$decoderName"
}

/** The name of the platform audio decoder that caused [error], or `null` if none did. */
private fun failedAudioDecoder(error: PlaybackException): String? {
    // The system took the decoder back for another app; nothing is wrong with it.
    if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED) return null
    val decoder =
        when (val cause = error.cause) {
            is MediaCodecDecoderException -> cause.codecInfo
            is MediaCodecRenderer.DecoderInitializationException -> cause.codecInfo
            else -> null
        }
    return decoder?.takeIf { MimeTypes.isAudio(it.mimeType) }?.name
}
