package dev.jdtech.jellyfin.core.presentation.dummy

import dev.jdtech.jellyfin.models.AudioChannel
import dev.jdtech.jellyfin.models.AudioCodec
import dev.jdtech.jellyfin.models.DisplayProfile
import dev.jdtech.jellyfin.models.Resolution
import dev.jdtech.jellyfin.models.SpatialAudio
import dev.jdtech.jellyfin.models.VideoCodec
import dev.jdtech.jellyfin.models.VideoMetadata

val dummyVideoMetadata =
    VideoMetadata(
        size = 1000000000,
        videoTracks = emptyList(),
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        resolution = listOf(Resolution.HD),
        videoCodecs = listOf(VideoCodec.AV1),
        displayProfiles = listOf(DisplayProfile.HDR10),
        audioChannels = listOf(AudioChannel.CH_5_1),
        audioCodecs = listOf(AudioCodec.OPUS),
        isAtmos = listOf(false),
    )

/** CE: a 4K remux with every badge: Dolby Vision over HDR10+, TrueHD Atmos 7.1. */
val dummyVideoMetadataRich =
    VideoMetadata(
        size = 60000000000,
        videoTracks = emptyList(),
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        resolution = listOf(Resolution.UHD),
        videoCodecs = listOf(VideoCodec.HEVC),
        displayProfiles = listOf(DisplayProfile.DOLBY_VISION, DisplayProfile.HDR10_PLUS),
        audioChannels = listOf(AudioChannel.CH_7_1),
        audioCodecs = listOf(AudioCodec.TRUEHD),
        isAtmos = listOf(true),
        spatialAudio = listOf(SpatialAudio.ATMOS),
    )
