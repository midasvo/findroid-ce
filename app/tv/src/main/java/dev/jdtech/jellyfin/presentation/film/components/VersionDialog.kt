package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun VersionDialog(
    versions: List<FindroidSource>,
    onSelect: (mediaSourceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.default)) {
                Text(
                    text = stringResource(CoreR.string.select_video_version_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(items = versions, key = { _, version -> version.id }) {
                        index,
                        version ->
                        ListItem(
                            selected = false,
                            onClick = { onSelect(version.id) },
                            headlineContent = { Text(text = version.name) },
                            modifier =
                                if (index == 0) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
}
