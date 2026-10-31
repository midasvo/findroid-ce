package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun VersionDialog(
    versions: List<FindroidSource>,
    onSelect: (mediaSourceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        title = stringResource(CoreR.string.select_video_version_title),
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(bottom = MaterialTheme.spacings.default)
        ) {
            items(items = versions, key = { it.id }) { version ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(version.id) }
                            .padding(
                                horizontal = MaterialTheme.spacings.default,
                                vertical = MaterialTheme.spacings.small,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = version.name, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
@Preview
private fun VersionDialogPreview() {
    FindroidTheme {
        VersionDialog(
            versions =
                listOf(
                    FindroidSource(
                        id = "1",
                        name = "2160p - HEVC",
                        type = FindroidSourceType.REMOTE,
                        path = "",
                        size = 0L,
                        mediaStreams = emptyList(),
                    ),
                    FindroidSource(
                        id = "2",
                        name = "1080p - H264",
                        type = FindroidSourceType.REMOTE,
                        path = "",
                        size = 0L,
                        mediaStreams = emptyList(),
                    ),
                ),
            onSelect = {},
            onDismiss = {},
        )
    }
}
