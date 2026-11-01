package dev.jdtech.jellyfin.player.local.domain

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableBitArray
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Ac3Util
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import java.io.EOFException
import kotlin.math.min

/** How much of a frame to read to find the JOC flag. The same as Media3's Ac3Reader. */
private const val FRAME_HEADER_SIZE = 128

/**
 * How many bytes of samples to accept while still waiting for an E-AC-3 track's first frame.
 *
 * ExoPlayer can't select tracks until it has every track's format, and until then it stops loading
 * streams at DefaultLoadControl.DEFAULT_MIN_BUFFER_SIZE (about 13 MiB). If a track's audio starts
 * later in the file than that, playback fails with StuckPlayerException, so give up well before it.
 */
internal const val MAX_BYTES_WAITING_FOR_E_AC3 = 8 * 1024 * 1024

/**
 * Labels Dolby Atmos in Matroska files as `audio/eac3-joc`.
 *
 * Matroska has a single codec ID for E-AC-3, so MatroskaExtractor calls every such track
 * `audio/eac3`, Atmos or not. Only the frames themselves say which it is. ExoPlayer needs to know
 * to choose a decoder:
 * - `audio/eac3-joc` goes to a device's JOC decoder, the only one that renders Atmos in 3D.
 * - Pixels have no JOC decoder and their E-AC-3 decoder rejects JOC frames (unsupported DDP-JOC
 *   frame). Media3 knows this and leaves `audio/eac3-joc` to ffmpeg, but it can't when the track
 *   says `audio/eac3`.
 * - Passthrough sends `audio/eac3-joc` as Atmos, or as E-AC-3 to a receiver without Atmos.
 *
 * Holds back the format of each E-AC-3 track until its first frame arrives, like MatroskaExtractor
 * does for DTS. Everything else passes through untouched. This can go once MatroskaExtractor does
 * this itself: https://github.com/androidx/media/issues/2307
 */
class EAc3JocExtractorsFactory(
    private val factory: ExtractorsFactory = DefaultExtractorsFactory()
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = wrap(factory.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = wrap(factory.createExtractors(uri, responseHeaders))

    // DefaultMediaSourceFactory sets these on the factory it is given.
    @Deprecated("Deprecated in Media3")
    @Suppress("DEPRECATION")
    override fun experimentalSetTextTrackTranscodingEnabled(
        textTrackTranscodingEnabled: Boolean
    ) = apply { factory.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled) }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory) = apply {
        factory.setSubtitleParserFactory(subtitleParserFactory)
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(
        codecsToParseWithinGopSampleDependencies: Int
    ) = apply {
        factory.experimentalSetCodecsToParseWithinGopSampleDependencies(
            codecsToParseWithinGopSampleDependencies
        )
    }

    override fun setParseHagcMetadata(parseHagcMetadata: Boolean) = apply {
        factory.setParseHagcMetadata(parseHagcMetadata)
    }

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> =
        extractors
            .map { if (it.underlyingImplementation is MatroskaExtractor) EAc3JocExtractor(it) else it }
            .toTypedArray()
}

internal class EAc3JocExtractor(private val extractor: Extractor) : Extractor {
    private var output: EAc3JocExtractorOutput? = null

    override fun sniff(input: ExtractorInput) = extractor.sniff(input)

    override fun getSniffFailureDetails() = extractor.sniffFailureDetails

    override fun init(output: ExtractorOutput) {
        val jocOutput = EAc3JocExtractorOutput(output)
        this.output = jocOutput
        extractor.init(jocOutput)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val result = extractor.read(input, seekPosition)
        if (result == Extractor.RESULT_END_OF_INPUT) output?.releaseHeldFormats()
        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        output?.discardPartialFrames()
        extractor.seek(position, timeUs)
    }

    override fun release() = extractor.release()

    override fun getUnderlyingImplementation(): Extractor = extractor.underlyingImplementation
}

internal class EAc3JocExtractorOutput(output: ExtractorOutput) : ForwardingExtractorOutput(output) {
    private val tracks = HashMap<Int, EAc3JocTrackOutput>()
    private var bytesWritten = 0L

    override fun track(id: Int, type: Int): TrackOutput =
        tracks.getOrPut(id) { EAc3JocTrackOutput(super.track(id, type), ::onSampleWritten) }

    fun releaseHeldFormats() = tracks.values.forEach { it.releaseHeldFormat() }

    fun discardPartialFrames() = tracks.values.forEach { it.discardPartialFrame() }

    private fun onSampleWritten(size: Int) {
        if (bytesWritten >= MAX_BYTES_WAITING_FOR_E_AC3) return
        bytesWritten += size
        if (bytesWritten >= MAX_BYTES_WAITING_FOR_E_AC3) releaseHeldFormats()
    }
}

private class EAc3JocTrackOutput(
    private val output: TrackOutput,
    private val onSampleWritten: (size: Int) -> Unit,
) : TrackOutput {
    private var heldFormat: Format? = null
    private var isJoc: Boolean? = null
    private val frameHeader = ByteArray(FRAME_HEADER_SIZE)
    private var frameHeaderSize = 0

    override fun format(format: Format) {
        val isJoc = isJoc
        when {
            format.sampleMimeType != MimeTypes.AUDIO_E_AC3 || format.drmInitData != null ->
                output.format(format)
            isJoc == null -> heldFormat = format
            else -> output.format(format.withJoc(isJoc))
        }
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int {
        if (!isHoldingFormat(sampleDataPart)) {
            return output.sampleData(input, length, allowEndOfInput, sampleDataPart)
        }
        val bytesRead =
            input.read(frameHeader, frameHeaderSize, min(length, FRAME_HEADER_SIZE - frameHeaderSize))
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return bytesRead
            throw EOFException()
        }
        frameHeaderSize += bytesRead
        if (frameHeaderSize == FRAME_HEADER_SIZE) releaseHeldFormat()
        return bytesRead
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (!isHoldingFormat(sampleDataPart)) {
            output.sampleData(data, length, sampleDataPart)
            return
        }
        val bytesToRead = min(length, FRAME_HEADER_SIZE - frameHeaderSize)
        data.readBytes(frameHeader, frameHeaderSize, bytesToRead)
        frameHeaderSize += bytesToRead
        if (frameHeaderSize == FRAME_HEADER_SIZE) releaseHeldFormat()
        if (length > bytesToRead) output.sampleData(data, length - bytesToRead, sampleDataPart)
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        // The frame was shorter than the header.
        releaseHeldFormat()
        output.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        onSampleWritten(size)
    }

    override fun durationUs(durationUs: Long) = output.durationUs(durationUs)

    /** Sends the format on, as far as the frame read so far can tell. No frame means plain E-AC-3. */
    fun releaseHeldFormat() {
        val format = heldFormat ?: return
        heldFormat = null
        val isJoc = isEAc3Joc(frameHeader, frameHeaderSize)
        this.isJoc = isJoc
        output.format(format.withJoc(isJoc))
        if (frameHeaderSize > 0) {
            output.sampleData(ParsableByteArray(frameHeader, frameHeaderSize), frameHeaderSize)
        }
    }

    fun discardPartialFrame() {
        frameHeaderSize = 0
    }

    private fun isHoldingFormat(sampleDataPart: Int): Boolean {
        if (sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) releaseHeldFormat()
        return heldFormat != null
    }

    private fun Format.withJoc(isJoc: Boolean): Format =
        if (isJoc) buildUpon().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build() else this
}

/** @return whether the first [size] bytes of [frame] start an E-AC-3 frame that carries JOC. */
internal fun isEAc3Joc(frame: ByteArray, size: Int): Boolean {
    val hasSyncWord = size >= 2 && frame[0] == 0x0B.toByte() && frame[1] == 0x77.toByte()
    if (!hasSyncWord) return false
    return try {
        Ac3Util.parseAc3SyncframeInfo(ParsableBitArray(frame, size)).mimeType ==
            MimeTypes.AUDIO_E_AC3_JOC
    } catch (_: RuntimeException) {
        // Ac3Util doesn't validate its input. A frame it can't read isn't worth failing playback.
        false
    }
}
