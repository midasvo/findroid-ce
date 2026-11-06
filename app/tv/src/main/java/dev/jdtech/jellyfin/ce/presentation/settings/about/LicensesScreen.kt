package dev.jdtech.jellyfin.ce.presentation.settings.about

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CE (#211): the open-source libraries in the TV app, from the same AboutLibraries data as the phone
 * app. Opening one shows its license.
 */
@Composable
fun LicensesScreen(navigateToLicense: (uniqueId: String) -> Unit) {
    val libraries by rememberLibraries()
    val firstItem = remember { FocusRequester() }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(start = TvSafeArea.horizontal, top = TvSafeArea.vertical, end = TvSafeArea.horizontal)
    ) {
        Text(
            text = stringResource(SettingsR.string.about_licenses),
            style = MaterialTheme.typography.displaySmall,
        )
        val list = libraries
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                contentPadding =
                    PaddingValues(top = MaterialTheme.spacings.default, bottom = TvSafeArea.vertical),
            ) {
                items(list, key = { it.uniqueId }) { library ->
                    FocusableCard(
                        onClick = { navigateToLicense(library.uniqueId) },
                        modifier =
                            Modifier.fillMaxWidth()
                                .then(
                                    if (library == list.first()) Modifier.focusRequester(firstItem)
                                    else Modifier
                                ),
                    ) {
                        LibraryRow(library)
                    }
                }
            }
            LaunchedEffect(Unit) { firstItem.requestFocus() }
        }
    }
}

@Composable
private fun LibraryRow(library: Library) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = library.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    listOfNotNull(library.organization?.name, library.artifactVersion)
                        .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = library.licenses.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/**
 * One library's license text. A license is one long text, so up and down scroll it a screen-third
 * at a time instead of moving focus.
 */
@Composable
fun LicenseScreen(uniqueId: String) {
    val libraries by rememberLibraries()
    val library = libraries?.firstOrNull { it.uniqueId == uniqueId }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(start = TvSafeArea.horizontal, top = TvSafeArea.vertical, end = TvSafeArea.horizontal)
    ) {
        Text(text = library?.name.orEmpty(), style = MaterialTheme.typography.displaySmall)
        Text(
            text = library?.licenses?.joinToString(", ") { it.name }.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val text =
            library
                ?.licenses
                ?.mapNotNull { it.licenseContent?.takeIf(String::isNotBlank) ?: it.url }
                ?.joinToString("\n\n")
                ?.ifBlank { null } ?: stringResource(SettingsR.string.about_no_license_text)
        Box(
            modifier =
                Modifier.padding(vertical = MaterialTheme.spacings.medium)
                    .fillMaxSize()
                    .border(
                        BorderStroke(2.dp, if (isFocused) Color.White else Color.Transparent),
                        RoundedCornerShape(10.dp),
                    )
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val step =
                            when (event.key.nativeKeyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> 1
                                AndroidKeyEvent.KEYCODE_DPAD_UP -> -1
                                else -> return@onPreviewKeyEvent false
                            }
                        scope.launch { scrollState.animateScrollBy(step * 300f) }
                        true
                    }
                    .focusable(interactionSource = interactionSource)
                    .verticalScroll(scrollState)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(MaterialTheme.spacings.medium),
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun rememberLibraries(): State<List<Library>?> {
    val context = LocalContext.current
    return produceState<List<Library>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadLibraries(context) }
    }
}

private fun loadLibraries(context: Context): List<Library> =
    Libs.Builder()
        .withJson(context, R.raw.aboutlibraries)
        .build()
        .libraries
        .sortedBy { it.name.lowercase() }
