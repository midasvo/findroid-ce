package dev.jdtech.jellyfin.film.domain

import dev.jdtech.jellyfin.models.DisplayProfile
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.SpatialAudio
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMetadataParserTest {
    private fun audio(
        codec: String,
        profile: String? = null,
        displayTitle: String? = null,
        channelLayout: String? = "5.1",
    ) =
        FindroidMediaStream(
            title = "",
            displayTitle = displayTitle,
            language = "eng",
            type = MediaStreamType.AUDIO,
            codec = codec,
            profile = profile,
            isExternal = false,
            path = null,
            channelLayout = channelLayout,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
        )

    private fun video(range: VideoRangeType) =
        FindroidMediaStream(
            title = "",
            displayTitle = "4K HEVC",
            language = "",
            type = MediaStreamType.VIDEO,
            codec = "hevc",
            profile = null,
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = range,
            height = 2160,
            width = 3840,
            videoDoViTitle = null,
        )

    private fun source(vararg streams: FindroidMediaStream) =
        FindroidSource(
            id = "s",
            name = "s",
            type = FindroidSourceType.REMOTE,
            path = "",
            size = 1,
            mediaStreams = streams.toList(),
        )

    @Test
    fun `atmos is read from the profile of an eac3 stream`() = runTest {
        val metadata =
            VideoMetadataParser.parse(
                source(audio("eac3", profile = "Dolby Digital Plus + Dolby Atmos"))
            )

        assertEquals(listOf(SpatialAudio.ATMOS), metadata.spatialAudio)
    }

    @Test
    fun `atmos falls back to the display title when there is no profile`() = runTest {
        val metadata =
            VideoMetadataParser.parse(
                source(audio("eac3", profile = null, displayTitle = "English - Dolby Atmos - 5.1"))
            )

        assertEquals(listOf(SpatialAudio.ATMOS), metadata.spatialAudio)
        assertEquals(listOf(true), metadata.isAtmos)
    }

    @Test
    fun `dts x is read from the profile`() = runTest {
        val metadata =
            VideoMetadataParser.parse(source(audio("dts", profile = "DTS-HD MA + DTS:X")))

        assertEquals(listOf(SpatialAudio.DTS_X), metadata.spatialAudio)
    }

    @Test
    fun `plain dts hd has no spatial audio`() = runTest {
        val metadata = VideoMetadataParser.parse(source(audio("dts", profile = "DTS-HD MA")))

        assertTrue(metadata.spatialAudio.isEmpty())
    }

    @Test
    fun `a plain aac stream has no spatial audio`() = runTest {
        val metadata =
            VideoMetadataParser.parse(
                source(audio("aac", profile = "LC", displayTitle = "English - AAC - Stereo"))
            )

        assertTrue(metadata.spatialAudio.isEmpty())
    }

    @Test
    fun `spatial audio is deduplicated across streams`() = runTest {
        val metadata =
            VideoMetadataParser.parse(
                source(
                    audio("truehd", profile = "Dolby TrueHD + Dolby Atmos"),
                    audio("eac3", profile = "Dolby Digital Plus + Dolby Atmos"),
                )
            )

        assertEquals(listOf(SpatialAudio.ATMOS), metadata.spatialAudio)
    }

    @Test
    fun `dolby vision with hdr10 plus reports both profiles`() = runTest {
        val metadata =
            VideoMetadataParser.parse(source(video(VideoRangeType.DOVI_WITH_HDR10_PLUS)))

        assertEquals(
            listOf(DisplayProfile.DOLBY_VISION, DisplayProfile.HDR10_PLUS),
            metadata.displayProfiles,
        )
    }

    @Test
    fun `dolby vision with el and hdr10 plus reports both profiles`() = runTest {
        val metadata =
            VideoMetadataParser.parse(source(video(VideoRangeType.DOVI_WITH_ELHDR10_PLUS)))

        assertEquals(
            listOf(DisplayProfile.DOLBY_VISION, DisplayProfile.HDR10_PLUS),
            metadata.displayProfiles,
        )
    }

    @Test
    fun `plain hdr10 plus stays hdr10 plus`() = runTest {
        val metadata = VideoMetadataParser.parse(source(video(VideoRangeType.HDR10_PLUS)))

        assertEquals(listOf(DisplayProfile.HDR10_PLUS), metadata.displayProfiles)
    }

    @Test
    fun `dolby vision with hdr10 stays dolby vision only`() = runTest {
        val metadata = VideoMetadataParser.parse(source(video(VideoRangeType.DOVI_WITH_HDR10)))

        assertEquals(listOf(DisplayProfile.DOLBY_VISION), metadata.displayProfiles)
    }
}
