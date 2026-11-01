package dev.jdtech.jellyfin.player

import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure profile-assembly half of [DeviceProfileBuilder]. The
 * `MediaCodecList` hardware probe needs a device and is not covered here.
 */
class DeviceProfileBuilderTest {

    private val uhd60 = DolbyVisionLimit(3840, 2160, 60)

    private fun probedWithDolbyVision(vararg profiles: Pair<Int, DolbyVisionLimit>) = ProbedCodecs(
        videoCodecProfiles = mapOf("hevc" to setOf("Main 10"), "av1" to emptySet()),
        audioCodecs = setOf("aac"),
        dolbyVisionProfiles = mapOf(*profiles),
    )

    private fun rangeTypes(profile: DeviceProfile, codec: String, container: String = "mkv") =
        profile.codecProfiles
            .filter { it.codec == codec && it.container == container }
            .flatMap { it.conditions }
            .first { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
            .value.orEmpty()
            .split("|")

    private fun dolbyVisionLimits(profile: DeviceProfile, codec: String, container: String = "mkv") =
        profile.codecProfiles.filter {
            it.codec == codec && it.container == container && it.applyConditions.isNotEmpty()
        }

    private fun hevcRangeCondition(probed: ProbedCodecs) =
        DeviceProfileBuilder.buildDeviceProfile(probed)
            .codecProfiles
            .filter { it.codec == "hevc" }
            .flatMap { it.conditions }
            .firstOrNull { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }

    @Test
    fun `dolby vision stays out of direct play without a dolby vision decoder`() {
        val profile = DeviceProfileBuilder.buildDeviceProfile(probedWithDolbyVision())

        for (codec in listOf("hevc", "av1")) {
            val rangeTypes = rangeTypes(profile, codec)
            assertTrue("$codec must allow HDR10", "HDR10" in rangeTypes)
            assertFalse(
                "DV ranges leaked into $codec direct play: $rangeTypes",
                rangeTypes.any { it.startsWith("DOVI") },
            )
            assertEquals(emptyList<Any>(), dolbyVisionLimits(profile, codec))
        }
    }

    @Test
    fun `an advertised dolby vision profile only opens its own range types`() {
        val cases = mapOf(
            5 to listOf("DOVI"),
            7 to listOf("DOVIWithEL", "DOVIWithELHDR10Plus"),
            8 to listOf("DOVIWithHDR10", "DOVIWithHLG", "DOVIWithSDR", "DOVIWithHDR10Plus"),
        )

        for ((dvProfile, expected) in cases) {
            val profile = DeviceProfileBuilder.buildDeviceProfile(
                probedWithDolbyVision(dvProfile to uhd60),
            )

            assertEquals(
                "hevc range types for DV profile $dvProfile",
                expected,
                rangeTypes(profile, "hevc").filter { it.startsWith("DOVI") },
            )
            assertFalse(
                "an HEVC DV decoder says nothing about AV1",
                rangeTypes(profile, "av1").any { it.startsWith("DOVI") },
            )
        }
    }

    @Test
    fun `dolby vision profile 10 opens av1 and leaves hevc closed`() {
        val profile = DeviceProfileBuilder.buildDeviceProfile(probedWithDolbyVision(10 to uhd60))

        assertEquals(
            listOf("DOVI", "DOVIWithHDR10", "DOVIWithHLG", "DOVIWithSDR", "DOVIWithHDR10Plus"),
            rangeTypes(profile, "av1").filter { it.startsWith("DOVI") },
        )
        assertFalse(rangeTypes(profile, "hevc").any { it.startsWith("DOVI") })
    }

    @Test
    fun `av1 is range restricted even though no av1 profiles are probed`() {
        // AV1 used to get no codec profile at all, so profile 10.0 was direct-played
        // on devices that can only show it with wrong colors.
        val profile = DeviceProfileBuilder.buildDeviceProfile(probedWithDolbyVision())

        assertFalse("DOVI" in rangeTypes(profile, "av1"))
        assertFalse("DOVI" in rangeTypes(profile, "av1", container = "mp4"))
    }

    @Test
    fun `out-of-spec dolby vision is never direct-played`() {
        val profile = DeviceProfileBuilder.buildDeviceProfile(
            probedWithDolbyVision(5 to uhd60, 7 to uhd60, 8 to uhd60, 10 to uhd60),
        )

        assertFalse("DOVIInvalid" in rangeTypes(profile, "hevc"))
        assertFalse("DOVIInvalid" in rangeTypes(profile, "av1"))
    }

    @Test
    fun `dolby vision needs a container media3 reads the configuration from`() {
        val profile = DeviceProfileBuilder.buildDeviceProfile(probedWithDolbyVision(5 to uhd60))

        assertTrue("DOVI" in rangeTypes(profile, "hevc", container = "mkv"))
        assertTrue("DOVI" in rangeTypes(profile, "hevc", container = "mp4"))
        assertFalse("DOVI" in rangeTypes(profile, "hevc", container = "mpegts"))
        assertFalse("DOVI" in rangeTypes(profile, "hevc", container = "3gp"))
    }

    @Test
    fun `the switch keeps all dolby vision out of direct play whatever is advertised`() {
        val probed = probedWithDolbyVision(5 to uhd60, 8 to uhd60, 10 to uhd60)

        val profile = DeviceProfileBuilder.buildDeviceProfile(probed, directPlayDolbyVision = false)

        assertEquals(
            DeviceProfileBuilder.buildDeviceProfile(probedWithDolbyVision()).codecProfiles,
            profile.codecProfiles,
        )
    }

    @Test
    fun `dolby vision is held to the advertised level`() {
        val profile = DeviceProfileBuilder.buildDeviceProfile(
            probedWithDolbyVision(
                5 to DolbyVisionLimit(1920, 1080, 60),
                8 to DolbyVisionLimit(3840, 2160, 24),
            ),
        )

        val limits = dolbyVisionLimits(profile, "hevc").associate { limit ->
            limit.applyConditions.single().value to limit.conditions.associate {
                it.property to it.value
            }
        }

        assertEquals(
            mapOf(
                "DOVI" to mapOf(
                    ProfileConditionValue.WIDTH to "1920",
                    ProfileConditionValue.HEIGHT to "1080",
                    ProfileConditionValue.VIDEO_FRAMERATE to "60",
                ),
                "DOVIWithHDR10|DOVIWithHLG|DOVIWithSDR|DOVIWithHDR10Plus" to mapOf(
                    ProfileConditionValue.WIDTH to "3840",
                    ProfileConditionValue.HEIGHT to "2160",
                    ProfileConditionValue.VIDEO_FRAMERATE to "24",
                ),
            ),
            limits,
        )
    }

    @Test
    fun `the level limit leaves streams with an unknown range alone`() {
        // The server treats a condition on an unknown value as met unless it is
        // required, which would hold those streams to the DV limit as well.
        val profile = DeviceProfileBuilder.buildDeviceProfile(probedWithDolbyVision(5 to uhd60))

        val applyCondition = dolbyVisionLimits(profile, "hevc").single().applyConditions.single()

        assertEquals(ProfileConditionValue.VIDEO_RANGE_TYPE, applyCondition.property)
        assertTrue(applyCondition.isRequired)
    }

    @Test
    fun `hevc codec profile excludes every Dolby Vision range from direct play`() {
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("hevc" to setOf("Main", "Main 10")),
            audioCodecs = setOf("aac"),
        )

        val rangeCondition = hevcRangeCondition(probed)

        assertTrue("hevc must carry a VIDEO_RANGE_TYPE condition", rangeCondition != null)
        val value = rangeCondition!!.value.orEmpty()
        // Dolby Vision must never be advertised as direct-playable.
        assertFalse("DV ranges leaked into direct play: $value", value.contains("DOVI"))
        // Regular HDR/SDR ranges stay direct-playable so they are not needlessly transcoded.
        assertTrue(value.contains("HDR10"))
        assertTrue(value.contains("SDR"))
    }

    @Test
    fun `hevc is still range-restricted when the decoder reports no profiles`() {
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("hevc" to emptySet()),
            audioCodecs = setOf("aac"),
        )

        assertTrue(
            "DV must be excluded even with no enumerable hevc profiles",
            hevcRangeCondition(probed) != null,
        )
    }

    @Test
    fun `codecs that cannot carry Dolby Vision are not range restricted`() {
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("h264" to setOf("high", "main")),
            audioCodecs = setOf("aac"),
        )

        val h264RangeConditions = DeviceProfileBuilder.buildDeviceProfile(probed)
            .codecProfiles
            .filter { it.codec == "h264" }
            .flatMap { it.conditions }
            .filter { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }

        assertEquals(emptyList<Any>(), h264RangeConditions)
    }

    @Test
    fun `device profile direct-plays supported codecs and offers an HLS transcode`() {
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("h264" to setOf("high"), "hevc" to setOf("Main 10")),
            audioCodecs = setOf("aac"),
        )

        val profile = DeviceProfileBuilder.buildDeviceProfile(probed)

        assertTrue(
            "hevc should be a direct-play video codec",
            profile.directPlayProfiles.any { it.videoCodec?.contains("hevc") == true },
        )
        assertTrue(
            "an h264 HLS transcoding profile is required for the DV fallback",
            profile.transcodingProfiles.any {
                it.videoCodec == "h264" && it.protocol == MediaStreamProtocol.HLS
            },
        )
    }

    @Test
    fun `unsupported codecs are not advertised for direct play`() {
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("h264" to setOf("high")),
            audioCodecs = setOf("aac"),
        )

        val profile = DeviceProfileBuilder.buildDeviceProfile(probed)

        assertFalse(
            "hevc must not be direct-play when the device cannot decode it",
            profile.directPlayProfiles.any { it.videoCodec?.contains("hevc") == true },
        )
        assertTrue(profile.codecProfiles.none { it.codec == "hevc" })
    }

    @Test
    fun `image-based subtitle formats are deliverable embedded and external`() {
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("h264" to setOf("high")),
            audioCodecs = setOf("aac"),
        )

        val subtitleProfiles = DeviceProfileBuilder.buildDeviceProfile(probed).subtitleProfiles

        // PGS (BluRay sup), DVDSub (VobSub), DVB are all decoded natively by media3
        // and must be advertised as deliverable both embedded in the container and as
        // sidecar files, so the server never bitmap-transcodes them to text. Encode is
        // the burn-in fallback when the server can't embed or sidecar.
        for (format in listOf("pgssub", "dvdsub", "dvbsub")) {
            assertTrue(
                "$format must be deliverable embedded",
                subtitleProfiles.any {
                    it.format == format && it.method == SubtitleDeliveryMethod.EMBED
                },
            )
            assertTrue(
                "$format must be deliverable as external sidecar",
                subtitleProfiles.any {
                    it.format == format && it.method == SubtitleDeliveryMethod.EXTERNAL
                },
            )
            assertTrue(
                "$format must have a burn-in (Encode) fallback",
                subtitleProfiles.any {
                    it.format == format && it.method == SubtitleDeliveryMethod.ENCODE
                },
            )
        }
    }

    @Test
    fun `subtitle profiles are pinned to the expected exact set`() {
        // Locks down the exact (format, method) pairs the device profile emits.
        // Catches typos ("pgsub"), accidental drops (srt), and accidental
        // additions that a per-format presence test cannot see.
        val probed = ProbedCodecs(
            videoCodecProfiles = mapOf("h264" to setOf("high")),
            audioCodecs = setOf("aac"),
        )

        val asPairs = DeviceProfileBuilder.buildDeviceProfile(probed)
            .subtitleProfiles
            .map { it.format to it.method }
            .toSet()

        val expected = setOf(
            // Embed: text + image formats media3 can decode from the container.
            "dvbsub" to SubtitleDeliveryMethod.EMBED,
            "dvdsub" to SubtitleDeliveryMethod.EMBED,
            "pgssub" to SubtitleDeliveryMethod.EMBED,
            "srt" to SubtitleDeliveryMethod.EMBED,
            "subrip" to SubtitleDeliveryMethod.EMBED,
            "ttml" to SubtitleDeliveryMethod.EMBED,
            "ass" to SubtitleDeliveryMethod.EMBED,
            "ssa" to SubtitleDeliveryMethod.EMBED,
            // External: sidecar formats (text + image where the server can stream them).
            "dvbsub" to SubtitleDeliveryMethod.EXTERNAL,
            "dvdsub" to SubtitleDeliveryMethod.EXTERNAL,
            "pgssub" to SubtitleDeliveryMethod.EXTERNAL,
            "srt" to SubtitleDeliveryMethod.EXTERNAL,
            "subrip" to SubtitleDeliveryMethod.EXTERNAL,
            "ttml" to SubtitleDeliveryMethod.EXTERNAL,
            "vtt" to SubtitleDeliveryMethod.EXTERNAL,
            "webvtt" to SubtitleDeliveryMethod.EXTERNAL,
            "ass" to SubtitleDeliveryMethod.EXTERNAL,
            "ssa" to SubtitleDeliveryMethod.EXTERNAL,
            // Encode: burn-in fallback for image subtitles only.
            "dvbsub" to SubtitleDeliveryMethod.ENCODE,
            "dvdsub" to SubtitleDeliveryMethod.ENCODE,
            "pgssub" to SubtitleDeliveryMethod.ENCODE,
        )

        assertEquals(expected, asPairs)
    }

    @Test
    fun `direct-play profile is permissive and never transcodes`() {
        val profile = DeviceProfileBuilder.buildDirectPlayProfile()

        assertTrue(
            "mpv profile must direct-play any container",
            profile.directPlayProfiles.all { it.container == "" },
        )
        assertEquals(emptyList<Any>(), profile.transcodingProfiles)
        assertEquals(emptyList<Any>(), profile.codecProfiles)
    }

    @Test
    fun `download transcode profile keeps non-DV original but routes DV to a progressive transcode`() {
        val profile = DeviceProfileBuilder.buildDownloadTranscodeProfile()

        // Direct-plays every container, so non-DV files download as the original.
        assertTrue(profile.directPlayProfiles.any { it.type.toString().contains("VIDEO", true) })
        assertTrue(profile.directPlayProfiles.all { it.container == "" })

        // DV is excluded from direct play, so the server transcodes it instead.
        val rangeCondition = profile.codecProfiles
            .filter { it.codec == "hevc" }
            .flatMap { it.conditions }
            .firstOrNull { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
        assertTrue("DV must be excluded from direct play", rangeCondition != null)
        assertFalse(rangeCondition!!.value.orEmpty().contains("DOVI"))

        // The transcode must be a single progressive HTTP stream — DownloadManager
        // cannot reassemble HLS segments.
        val transcode = profile.transcodingProfiles.single()
        assertEquals(MediaStreamProtocol.HTTP, transcode.protocol)
        assertEquals("h264", transcode.videoCodec)
    }
}
