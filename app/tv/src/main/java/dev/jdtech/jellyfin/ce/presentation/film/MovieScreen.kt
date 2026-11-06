package dev.jdtech.jellyfin.ce.presentation.film

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.ce.ui.PersonCard
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.models.getVersions
import dev.jdtech.jellyfin.presentation.film.components.VersionDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.format
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * CE replacement of the upstream TV movie page (ADR 0001).
 * Shows metadata, buttons, genres/crew and a cast row. Back from a person restores focus to that
 * card (ADR 0003).
 */
@Composable
fun MovieScreen(
    movieId: UUID,
    navigateToPlayer: (itemId: UUID, mediaSourceId: String?) -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(movieId) { viewModel.loadMovie(movieId = movieId) }

    MovieScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is MovieAction.Play -> {
                    navigateToPlayer(movieId, action.mediaSourceId)
                }
                is MovieAction.PlayTrailer -> {
                    try {
                        uriHandler.openUri(action.trailer)
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                is MovieAction.NavigateToPerson -> {
                    navigateToPerson(action.personId)
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun MovieScreenLayout(state: MovieState, onAction: (MovieAction) -> Unit) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales.get(0)

    val focusPosition = rememberFocusPosition(row = ROW_BUTTONS, column = 0)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollToRow: (Int) -> Unit = { row ->
        scope.launch {
            withFrameNanos {}
            listState.animateScrollToItem(row)
        }
    }

    var versionDialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        state.movie?.let { movie ->
            val versions = remember(movie.sources) { movie.getVersions() }
            var size by remember { mutableStateOf(Size.Zero) }
            Box(
                modifier =
                    Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                        size = coordinates.size.toSize()
                    }
            ) {
                AsyncImage(
                    model = movie.images.backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (size != Size.Zero) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color.Black.copy(alpha = .2f), Color.Black),
                                        center = Offset(size.width, 0f),
                                        radius = size.width * .8f,
                                    )
                                )
                    )
                }
                LazyColumn(
                    state = listState,
                    contentPadding =
                        PaddingValues(top = 112.dp, bottom = MaterialTheme.spacings.large),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    userScrollEnabled = false,
                ) {
                    item {
                        Column(
                            modifier =
                                Modifier.padding(
                                        start = MaterialTheme.spacings.default * 2,
                                        end = MaterialTheme.spacings.default * 2,
                                    )
                                    .onFocusChanged { if (it.hasFocus) scrollToRow(ROW_BUTTONS) }
                        ) {
                            Text(text = movie.name, style = MaterialTheme.typography.displayMedium)
                            if (movie.originalTitle != movie.name) {
                                movie.originalTitle?.let { originalTitle ->
                                    Text(
                                        text = originalTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.small)
                            ) {
                                movie.premiereDate?.let { premiereDate ->
                                    Text(
                                        text = premiereDate.format(locale),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Text(
                                    text =
                                        stringResource(
                                            CoreR.string.runtime_minutes,
                                            movie.runtimeTicks.div(600000000),
                                        ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                movie.officialRating?.let {
                                    Text(text = it, style = MaterialTheme.typography.labelMedium)
                                }
                                movie.communityRating?.let {
                                    Row {
                                        Icon(
                                            painter = painterResource(id = CoreR.drawable.ic_star),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(
                                            modifier =
                                                Modifier.width(MaterialTheme.spacings.extraSmall)
                                        )
                                        Text(
                                            text = String.format(locale, "%.1f", it),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                            Text(
                                text = movie.overview,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(640.dp),
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.medium),
                                modifier = Modifier.focusRestorer().focusGroup(),
                            ) {
                                val hasTrailer = movie.trailer != null
                                val trailerCol = if (hasTrailer) 1 else -1
                                val playedCol = if (hasTrailer) 2 else 1
                                val favoriteCol = if (hasTrailer) 3 else 2

                                Button(
                                    onClick = {
                                        if (versions.size > 1) {
                                            versionDialogOpen = true
                                        } else {
                                            onAction(MovieAction.Play(startFromBeginning = false))
                                        }
                                    },
                                    modifier =
                                        Modifier.focusPosition(focusPosition, ROW_BUTTONS, 0),
                                ) {
                                    Icon(
                                        painter = painterResource(id = CoreR.drawable.ic_play),
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(id = CoreR.string.play))
                                }
                                movie.trailer?.let { trailerUri ->
                                    Button(
                                        onClick = {
                                            onAction(MovieAction.PlayTrailer(trailerUri))
                                        },
                                        modifier =
                                            Modifier.focusPosition(
                                                focusPosition,
                                                ROW_BUTTONS,
                                                trailerCol,
                                            ),
                                    ) {
                                        Icon(
                                            painter = painterResource(id = CoreR.drawable.ic_film),
                                            contentDescription = null,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(id = CoreR.string.watch_trailer))
                                    }
                                }
                                Button(
                                    onClick = {
                                        when (movie.played) {
                                            true -> onAction(MovieAction.UnmarkAsPlayed)
                                            false -> onAction(MovieAction.MarkAsPlayed)
                                        }
                                    },
                                    modifier =
                                        Modifier.focusPosition(
                                            focusPosition,
                                            ROW_BUTTONS,
                                            playedCol,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(id = CoreR.drawable.ic_check),
                                        contentDescription = null,
                                        tint =
                                            if (movie.played) MaterialTheme.colorScheme.tertiary
                                            else LocalContentColor.current,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text =
                                            stringResource(
                                                id =
                                                    if (movie.played) CoreR.string.unmark_as_played
                                                    else CoreR.string.mark_as_played
                                            )
                                    )
                                }
                                Button(
                                    onClick = {
                                        when (movie.favorite) {
                                            true -> onAction(MovieAction.UnmarkAsFavorite)
                                            false -> onAction(MovieAction.MarkAsFavorite)
                                        }
                                    },
                                    modifier =
                                        Modifier.focusPosition(
                                            focusPosition,
                                            ROW_BUTTONS,
                                            favoriteCol,
                                        ),
                                ) {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id =
                                                    if (movie.favorite)
                                                        CoreR.drawable.ic_heart_filled
                                                    else CoreR.drawable.ic_heart
                                            ),
                                        contentDescription = null,
                                        tint =
                                            if (movie.favorite) MaterialTheme.colorScheme.tertiary
                                            else LocalContentColor.current,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text =
                                            stringResource(
                                                id =
                                                    if (movie.favorite)
                                                        CoreR.string.remove_from_favorites
                                                    else CoreR.string.add_to_favorites
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.large)
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(id = CoreR.string.genres),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = .5f),
                                    )
                                    Text(
                                        text = movie.genres.joinToString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                state.director?.let { director ->
                                    Column {
                                        Text(
                                            text = stringResource(id = CoreR.string.director),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = .5f),
                                        )
                                        Text(
                                            text = director.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                if (state.writers.isNotEmpty()) {
                                    Column {
                                        Text(
                                            text = stringResource(id = CoreR.string.writers),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = .5f),
                                        )
                                        Text(
                                            text = state.writers.joinToString { it.name },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                            if (state.actors.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
                                Text(
                                    text = stringResource(id = CoreR.string.cast_amp_crew),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                    }

                    if (state.actors.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.default),
                                contentPadding =
                                    PaddingValues(horizontal = MaterialTheme.spacings.default * 2),
                                modifier =
                                    Modifier.focusRestorer().onFocusChanged {
                                        if (it.hasFocus) scrollToRow(ROW_CAST)
                                    },
                            ) {
                                itemsIndexed(state.actors, key = { _, actor -> actor.id }) {
                                    index,
                                    actor ->
                                    PersonCard(
                                        person = actor,
                                        onClick = {
                                            onAction(MovieAction.NavigateToPerson(actor.id))
                                        },
                                        modifier =
                                            Modifier.focusPosition(
                                                focusPosition,
                                                ROW_CAST,
                                                index,
                                                actor.id.toString(),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            RestoreFocus(
                position = focusPosition,
                ready = state.actors.isNotEmpty() || focusPosition.row == ROW_BUTTONS,
            ) { position ->
                position.resolve(ROW_CAST, state.actors.size) { i ->
                    state.actors[i].id.toString()
                }
                listState.scrollToItem(position.row)
            }

            if (versionDialogOpen) {
                VersionDialog(
                    versions = versions,
                    onSelect = { mediaSourceId ->
                        versionDialogOpen = false
                        onAction(MovieAction.Play(mediaSourceId = mediaSourceId))
                    },
                    onDismiss = { versionDialogOpen = false },
                )
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
    }
}

private const val ROW_BUTTONS = 0
private const val ROW_CAST = 1

@Preview(device = "id:tv_1080p")
@Composable
private fun MovieScreenLayoutPreview() {
    FindroidTheme {
        MovieScreenLayout(
            state = MovieState(movie = dummyMovie, videoMetadata = dummyVideoMetadata),
            onAction = {},
        )
    }
}
