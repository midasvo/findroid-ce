package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The frame headers come from E-AC-3 encoded by ffmpeg. ffmpeg can't encode JOC, so [jocFrame] has
 * the flag patched in: ffprobe reports that stream as "Dolby Digital Plus + Dolby Atmos" and it
 * decodes with CRC checking on.
 *
 * MatroskaExtractor itself can't run on the JVM because it keeps its tracks in a SparseArray, so
 * these tests make the same [TrackOutput] calls it does.
 */
class EAc3JocExtractorsFactoryTest {

    private val plainFrame = frame("0b7701ff3f87c0002080200002004000")
    private val jocFrame = frame("0b7702013f87c4101100008200800008")
    private val eAc3 = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build()
    private val video = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()

    private val track = FakeTrackOutput()
    private val extractorOutput = EAc3JocExtractorOutput(FakeExtractorOutput(track))
    private val audio = extractorOutput.track(1, C.TRACK_TYPE_AUDIO)

    @Before
    fun enforceByteArrayLimits() {
        // Otherwise ParsableByteArray works this out from Build, which is null on the JVM.
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true)
    }

    @After
    fun resetByteArrayLimits() {
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
    }

    @Test
    fun `an Atmos track is labelled as JOC`() {
        audio.format(eAc3)
        audio.writeSample(jocFrame)

        assertEquals(listOf(MimeTypes.AUDIO_E_AC3_JOC), track.formats.map { it.sampleMimeType })
    }

    @Test
    fun `a plain E-AC-3 track stays E-AC-3`() {
        audio.format(eAc3)
        audio.writeSample(plainFrame)

        assertEquals(listOf(eAc3), track.formats)
    }

    @Test
    fun `samples come out unchanged`() {
        audio.format(eAc3)
        audio.writeSample(jocFrame, timeUs = 0)
        audio.writeSample(plainFrame, timeUs = 32_000)
        audio.writeSample(jocFrame, timeUs = 64_000)

        assertEquals(listOf(0L, 32_000L, 64_000L), track.sampleTimesUs)
        assertArrayEquals(jocFrame + plainFrame + jocFrame, track.data.toByteArray())
    }

    @Test
    fun `detection works when the input arrives one byte at a time`() {
        audio.format(eAc3)
        audio.writeSample(jocFrame, chunkSize = 1)

        assertEquals(MimeTypes.AUDIO_E_AC3_JOC, track.formats.single().sampleMimeType)
        assertArrayEquals(jocFrame, track.data.toByteArray())
    }

    @Test
    fun `the format reaches the player before any sample`() {
        audio.format(eAc3)
        audio.writeSample(jocFrame)

        assertEquals("format", track.events.first())
        assertEquals(1, track.events.count { it == "format" })
    }

    @Test
    fun `the format is held until the frame header is complete`() {
        audio.format(eAc3)

        audio.sampleData(ParsableByteArray(jocFrame.copyOf(100)), 100)
        assertTrue(track.formats.isEmpty())

        val rest = jocFrame.copyOfRange(100, jocFrame.size)
        audio.sampleData(ParsableByteArray(rest), rest.size)
        assertEquals(MimeTypes.AUDIO_E_AC3_JOC, track.formats.single().sampleMimeType)
        assertArrayEquals(jocFrame, track.data.toByteArray())
    }

    @Test
    fun `a frame shorter than the header is still detected`() {
        audio.format(eAc3)

        audio.sampleData(ParsableByteArray(jocFrame.copyOf(64)), 64)
        audio.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, 64, 0, null)

        assertEquals(MimeTypes.AUDIO_E_AC3_JOC, track.formats.single().sampleMimeType)
        assertEquals(listOf("format", "data", "sample"), track.events)
    }

    @Test
    fun `audio that starts too late stays E-AC-3 rather than holding up playback`() {
        val other = extractorOutput.track(2, C.TRACK_TYPE_VIDEO)
        audio.format(eAc3)
        other.format(video)

        other.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, MAX_BYTES_WAITING_FOR_E_AC3 - 1, 0, null)
        assertTrue(track.formats.isEmpty())

        other.sampleMetadata(1, 0, 1, 0, null)
        assertEquals(MimeTypes.AUDIO_E_AC3, track.formats.single().sampleMimeType)

        audio.writeSample(jocFrame)
        assertEquals(1, track.formats.size)
        assertArrayEquals(jocFrame, track.data.toByteArray())
    }

    @Test
    fun `the limit stays below the point where ExoPlayer stops loading`() {
        assertTrue(MAX_BYTES_WAITING_FOR_E_AC3 < 200 * C.DEFAULT_BUFFER_SEGMENT_SIZE)
    }

    @Test
    fun `a partial frame is dropped on seek`() {
        audio.format(eAc3)

        audio.sampleData(ParsableByteArray(jocFrame.copyOf(100)), 100)
        extractorOutput.discardPartialFrames()
        audio.sampleData(ParsableByteArray(plainFrame), plainFrame.size)

        assertEquals(MimeTypes.AUDIO_E_AC3, track.formats.single().sampleMimeType)
        assertArrayEquals(plainFrame, track.data.toByteArray())
    }

    @Test
    fun `a file that ends before the first frame still gets a format`() {
        val extractor =
            EAc3JocExtractor(
                object : EmptyFileExtractor() {
                    override fun init(output: ExtractorOutput) {
                        output.track(1, C.TRACK_TYPE_AUDIO).format(eAc3)
                    }
                }
            )
        val track = FakeTrackOutput()
        extractor.init(FakeExtractorOutput(track))
        assertTrue(track.formats.isEmpty())

        val input = DefaultExtractorInput({ _, _, _ -> C.RESULT_END_OF_INPUT }, 0, 0)
        val result = extractor.read(input, PositionHolder())

        assertEquals(Extractor.RESULT_END_OF_INPUT, result)
        assertEquals(MimeTypes.AUDIO_E_AC3, track.formats.single().sampleMimeType)
    }

    @Test
    fun `other tracks are not held back`() {
        val track = FakeTrackOutput()
        val output = EAc3JocExtractorOutput(FakeExtractorOutput(track)).track(1, C.TRACK_TYPE_VIDEO)

        output.format(video)

        assertSame(video, track.formats.single())
    }

    @Test
    fun `encrypted E-AC-3 is not inspected`() {
        val encrypted = eAc3.buildUpon().setDrmInitData(DrmInitData(emptyList())).build()

        audio.format(encrypted)

        assertSame(encrypted, track.formats.single())
    }

    @Test
    fun `a later format update keeps the JOC label`() {
        audio.format(eAc3)
        audio.writeSample(jocFrame)

        audio.format(eAc3)

        assertEquals(
            listOf(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3_JOC),
            track.formats.map { it.sampleMimeType },
        )
    }

    @Test
    fun `only real E-AC-3 JOC headers count as JOC`() {
        assertTrue(isEAc3Joc(jocFrame, jocFrame.size))
        assertFalse(isEAc3Joc(plainFrame, plainFrame.size))
        assertFalse(isEAc3Joc(ByteArray(FRAME_SIZE), FRAME_SIZE))
        assertFalse(isEAc3Joc(ByteArray(0), 0))
    }

    @Test
    fun `an unreadable frame counts as plain E-AC-3`() {
        // fscod and fscod2 are both 3, a reserved value that Ac3Util doesn't check for.
        val reservedSampleRate = frame("0b7700fff080")

        assertFalse(isEAc3Joc(reservedSampleRate, reservedSampleRate.size))
        // Nothing past the sync word.
        assertFalse(isEAc3Joc(jocFrame, 2))
    }

    @Test
    fun `only the Matroska extractor is wrapped`() {
        val mp4Extractor = Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED)
        val factory =
            EAc3JocExtractorsFactory(
                ExtractorsFactory {
                    arrayOf(MatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED), mp4Extractor)
                }
            )

        val (wrapped, untouched) = factory.createExtractors()

        assertTrue(wrapped is EAc3JocExtractor)
        assertTrue(wrapped.underlyingImplementation is MatroskaExtractor)
        assertSame(mp4Extractor, untouched)
    }

    @Test
    fun `settings from the media source factory reach the wrapped factory`() {
        var parser: SubtitleParser.Factory? = null
        val delegate =
            object : ExtractorsFactory {
                override fun createExtractors(): Array<Extractor> = emptyArray()

                override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory) =
                    apply { parser = subtitleParserFactory }
            }
        val factory = EAc3JocExtractorsFactory(delegate)

        val result = factory.setSubtitleParserFactory(SubtitleParser.Factory.UNSUPPORTED)

        assertSame(factory, result)
        assertSame(SubtitleParser.Factory.UNSUPPORTED, parser)
    }

    /** Writes [frame] the way MatroskaExtractor does: as much as the output takes at a time. */
    private fun TrackOutput.writeSample(
        frame: ByteArray,
        timeUs: Long = 0,
        chunkSize: Int = Int.MAX_VALUE,
    ) {
        var position = 0
        val reader = DataReader { buffer, offset, length ->
            val count = minOf(length, chunkSize, frame.size - position)
            frame.copyInto(buffer, offset, position, position + count)
            position += count
            count
        }
        while (position < frame.size) sampleData(reader, frame.size - position, false)
        sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, frame.size, 0, null)
    }

    /** A frame that starts with [header], longer than the part of it that gets inspected. */
    private fun frame(header: String): ByteArray {
        val bytes = header.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.copyOf(FRAME_SIZE).also { frame ->
            for (i in bytes.size until FRAME_SIZE) frame[i] = i.toByte()
        }
    }

    private companion object {
        const val FRAME_SIZE = 300
    }
}

private class FakeExtractorOutput(private val firstTrack: FakeTrackOutput) : ExtractorOutput {
    private val tracks = linkedMapOf<Int, TrackOutput>()

    override fun track(id: Int, type: Int): TrackOutput =
        tracks.getOrPut(id) { if (tracks.isEmpty()) firstTrack else FakeTrackOutput() }

    override fun endTracks() = Unit

    override fun seekMap(seekMap: SeekMap) = Unit
}

private class FakeTrackOutput : TrackOutput {
    val formats = mutableListOf<Format>()
    val sampleTimesUs = mutableListOf<Long>()
    val data = ByteArrayOutputStream()
    val events = mutableListOf<String>()

    override fun format(format: Format) {
        formats += format
        events += "format"
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int {
        val buffer = ByteArray(length)
        val bytesRead = input.read(buffer, 0, length)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return bytesRead
            throw EOFException()
        }
        data.write(buffer, 0, bytesRead)
        events += "data"
        return bytesRead
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        val buffer = ByteArray(length)
        data.readBytes(buffer, 0, length)
        this.data.write(buffer)
        events += "data"
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        sampleTimesUs += timeUs
        events += "sample"
    }
}

private abstract class EmptyFileExtractor : Extractor {
    override fun sniff(input: ExtractorInput) = true

    override fun read(input: ExtractorInput, seekPosition: PositionHolder) =
        Extractor.RESULT_END_OF_INPUT

    override fun seek(position: Long, timeUs: Long) = Unit

    override fun release() = Unit
}
