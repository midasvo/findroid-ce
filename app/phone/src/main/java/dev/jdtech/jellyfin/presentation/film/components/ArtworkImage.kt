package dev.jdtech.jellyfin.presentation.film.components

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage

/**
 * Image that moves on to the next of [sources] when one fails to load. Downloaded images always
 * have a path, whether or not the file exists, so a null check can't pick the fallback.
 */
@Composable
fun ArtworkImage(
    sources: List<Uri?>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val candidates =
        remember(sources) { sources.filterNotNull().distinct().map { it.resolveIn(context) } }
    var index by remember(candidates) { mutableIntStateOf(0) }

    AsyncImage(
        model = candidates.getOrNull(index),
        contentDescription = contentDescription,
        contentScale = contentScale,
        onError = { if (index < candidates.lastIndex) index++ },
        modifier = modifier,
    )
}

/** Downloaded images are stored as paths relative to the files directory. */
private fun Uri.resolveIn(context: Context): Uri =
    if (scheme == null) {
        Uri.Builder().appendEncodedPath("${context.filesDir}").appendEncodedPath(path).build()
    } else this
