package dev.jdtech.jellyfin.ce.presentation.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * CE (#126): a single-choice list for the TV player (audio, subtitles, speed). The selected option
 * has focus when it opens. Picking an option calls [onSelect] and closes the dialog; the caller
 * puts focus back on the button that opened it.
 */
@Composable
fun TvSelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(560.dp),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.large)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))

                val initial = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { if (options.isNotEmpty()) focusRequester.requestFocus() }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                    modifier = Modifier.heightIn(max = 480.dp),
                ) {
                    itemsIndexed(options) { index, option ->
                        ListItem(
                            selected = index == selectedIndex,
                            onClick = {
                                onSelect(index)
                                onDismiss()
                            },
                            headlineContent = {
                                Text(text = option, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                RadioButton(selected = index == selectedIndex, onClick = null)
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                                    .then(
                                        if (index == initial) Modifier.focusRequester(focusRequester)
                                        else Modifier
                                    ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun TvSelectionDialogPreview() {
    FindroidTheme {
        TvSelectionDialog(
            title = "Select audio track",
            options = listOf("Nederlands", "English", "English Commentary"),
            selectedIndex = 1,
            onSelect = {},
            onDismiss = {},
        )
    }
}
