package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.VideoQualities

/**
 * Quality picker shown when starting a download. Tapping an option starts the
 * download immediately: [onSelect] receives the bitrate cap in bits/sec, or null
 * for original quality. Files under the cap still download as the original;
 * files over it are transcoded down to it by the server.
 */
@Composable
fun DownloadQualityDialog(onSelect: (maxBitrate: Int?) -> Unit, onDismiss: () -> Unit) {
    val lazyListState = rememberLazyListState()

    BaseDialog(title = stringResource(CoreR.string.download_quality), onDismiss = onDismiss) {
        if (lazyListState.canScrollBackward) {
            HorizontalDivider()
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            state = lazyListState,
        ) {
            items(items = VideoQualities.bitrates, key = { it }) { bitrate ->
                Text(
                    text =
                        VideoQualities.label(bitrate)
                            ?: stringResource(CoreR.string.download_quality_original),
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onSelect(bitrate.takeIf { it > 0 }) }
                            .padding(
                                horizontal = MaterialTheme.spacings.default,
                                vertical = MaterialTheme.spacings.medium,
                            ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (lazyListState.canScrollForward) {
            HorizontalDivider()
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
    }
}

@Preview
@Composable
private fun DownloadQualityDialogPreview() {
    FindroidTheme { DownloadQualityDialog(onSelect = {}, onDismiss = {}) }
}
