package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadataRich
import dev.jdtech.jellyfin.models.AudioCodec
import dev.jdtech.jellyfin.models.DisplayProfile
import dev.jdtech.jellyfin.models.SpatialAudio
import dev.jdtech.jellyfin.models.VideoMetadata
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun VideoMetadataBar(videoMetadata: VideoMetadata) {
    // CE: wraps instead of overflowing, now that HDR10+ and spatial audio add badges.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        videoMetadata.resolution.firstOrNull()?.apply { VideoMetadataBarItem(text = this.raw) }
        videoMetadata.videoCodecs.firstOrNull()?.apply { VideoMetadataBarItem(text = this.raw) }
        // CE: every display profile, so Dolby Vision with an HDR10+ fallback shows both.
        videoMetadata.displayProfiles.forEach { profile ->
            when (profile) {
                DisplayProfile.DOLBY_VISION ->
                    VideoMetadataBarItem(text = profile.raw, icon = CoreR.drawable.ic_dolby)
                DisplayProfile.HDR10_PLUS ->
                    VideoMetadataBarItem(
                        text = null,
                        icon = CoreR.drawable.ic_hdr10_plus,
                        contentDescription = profile.raw,
                    )
                else -> VideoMetadataBarItem(text = profile.raw)
            }
        }
        videoMetadata.audioCodecs.firstOrNull()?.apply {
            val icon =
                when (this) {
                    AudioCodec.AC3,
                    AudioCodec.EAC3,
                    AudioCodec.TRUEHD -> CoreR.drawable.ic_dolby
                    else -> null
                }
            VideoMetadataBarItem(text = this.raw, icon = icon)
        }
        videoMetadata.spatialAudio.firstOrNull()?.apply {
            val icon =
                when (this) {
                    SpatialAudio.ATMOS -> CoreR.drawable.ic_dolby
                    SpatialAudio.DTS_X -> null
                }
            VideoMetadataBarItem(text = this.raw, icon = icon)
        }
        videoMetadata.audioChannels.firstOrNull()?.apply { VideoMetadataBarItem(text = this.raw) }
    }
}

/**
 * A metadata chip. [text] may be null for an icon-only badge (a wordmark such as HDR10+); give
 * those a [contentDescription].
 */
@Composable
fun VideoMetadataBarItem(
    text: String?,
    @DrawableRes icon: Int? = null,
    contentDescription: String? = null,
) {
    Row(
        modifier =
            Modifier.clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(
                    horizontal = MaterialTheme.spacings.small,
                    vertical = MaterialTheme.spacings.extraSmall,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            if (text == null) {
                // Icon-only badges keep the height of the text ones.
                val lineHeight =
                    with(LocalDensity.current) {
                        MaterialTheme.typography.labelMedium.lineHeight.toDp()
                    }
                Box(modifier = Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(icon), contentDescription = contentDescription)
                }
            } else {
                Icon(painter = painterResource(icon), contentDescription = contentDescription)
            }
        }
        if (text != null) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun VideoMetadataBarPreview() {
    FindroidTheme { VideoMetadataBar(videoMetadata = dummyVideoMetadata) }
}

@Composable
@Preview(showBackground = true, widthDp = 320)
private fun VideoMetadataBarRichPreview() {
    FindroidTheme { VideoMetadataBar(videoMetadata = dummyVideoMetadataRich) }
}
