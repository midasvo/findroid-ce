package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.ce.ui.itemInfoLine
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import dev.jdtech.jellyfin.utils.withImageSize
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CE Home (G13): a hero area for the focused card above the rows.
 *
 * - The hero follows focus with a short delay, so scrolling fast doesn't load every backdrop. Focus
 *   changes only recompose the hero: cards write the focused item into [HeroFocus], which nothing
 *   but the hero reads.
 * - On a return the rows refresh in the background and stay on screen until the new ones are in.
 *   Focus then goes back to the same item by ID, wherever it moved (ADR 0003).
 * - The suggestions carousel is gone: the hero shows the focused card, so suggestions are a row of
 *   their own at the end.
 * - OK on an episode opens its season (#130). Movies and shows open their page.
 */
@Composable
fun HomeScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToEpisode: (episodeId: UUID) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var initialLoad by rememberSaveable { mutableStateOf(true) }
    // Not saveable on purpose: false again on every return, until that refresh is done.
    var refreshed by remember { mutableStateOf(false) }

    LaunchedEffect(true) {
        val job = viewModel.loadData(silent = !initialLoad)
        if (initialLoad) {
            initialLoad = false
            job.join()
        } else {
            // On a slow server, focus goes back to the rows that are there.
            withTimeoutOrNull(REFRESH_WAIT_MS) { job.join() }
        }
        refreshed = true
    }

    HomeScreenLayout(
        state = state,
        refreshed = refreshed,
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> {
                    when (action.item) {
                        is FindroidMovie -> navigateToMovie(action.item.id)
                        is FindroidShow -> navigateToShow(action.item.id)
                        is FindroidEpisode -> navigateToEpisode(action.item.id)
                    }
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

/** The focused card. Written on focus changes and read only by the hero. */
@Stable
private class HeroFocus {
    var item by mutableStateOf<FindroidItem?>(null)
}

private class HomeRow(
    val key: UUID,
    val title: UiText,
    val items: List<FindroidItem>,
    val direction: Direction,
)

private fun HomeState.rows(): List<HomeRow> = buildList {
    resumeSection?.let {
        add(HomeRow(it.id, it.homeSection.name, it.homeSection.items, Direction.HORIZONTAL))
    }
    nextUpSection?.let {
        add(HomeRow(it.id, it.homeSection.name, it.homeSection.items, Direction.HORIZONTAL))
    }
    views.forEach {
        add(
            HomeRow(
                it.id,
                UiText.StringResource(FilmR.string.latest_library, it.view.name),
                it.view.items,
                Direction.VERTICAL,
            )
        )
    }
    suggestionsSection?.let {
        add(
            HomeRow(
                it.id,
                UiText.StringResource(FilmR.string.home_suggestions_title),
                it.items,
                Direction.VERTICAL,
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenLayout(
    state: HomeState,
    refreshed: Boolean,
    onAction: (HomeAction) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    focusPosition: FocusPosition = rememberFocusPosition(),
) {
    val rows = remember(state) { state.rows() }
    val hero = remember { HeroFocus() }
    // The row states, so a return can scroll the saved card into view before focusing it.
    val rowStates = remember { mutableMapOf<UUID, LazyListState>() }
    val rowSpec = LocalBringIntoViewSpec.current
    val titleSpacePx =
        with(LocalDensity.current) { (ROW_TITLE_HEIGHT + MaterialTheme.spacings.small).toPx() }
    val rowAtTop = remember(titleSpacePx) { rowAtTopSpec(titleSpacePx) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (rows.isEmpty()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Box
        }

        HomeHero(hero = hero)

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(HERO_HEIGHT))
            // The focused row moves up to just below the hero. The rows inside keep the default.
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowAtTop) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = TvSafeArea.vertical),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.fillMaxSize().testTag("ce_home_rows"),
                ) {
                    rows.forEachIndexed { rowIndex, row ->
                        item(key = row.key) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                HomeRowContent(
                                    row = row,
                                    rowIndex = rowIndex,
                                    focusPosition = focusPosition,
                                    hero = hero,
                                    rowStates = rowStates,
                                    onAction = onAction,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // The card that had focus when Home was left, read once. While Home waits for the refresh,
    // the drawer moves focus into the rows, and that card must not overwrite it.
    val left = remember { Triple(focusPosition.row, focusPosition.column, focusPosition.key) }
    RestoreFocus(position = focusPosition, ready = refreshed && rows.isNotEmpty()) { position ->
        // The saved card may have moved, even to another row (Next up to Continue watching).
        val (leftRow, leftColumn, key) = left
        val found =
            rows.withIndex().firstNotNullOfOrNull { (r, row) ->
                row.items
                    .indexOfFirst { it.id.toString() == key }
                    .takeIf { it >= 0 }
                    ?.let { c -> r to c }
            }
        if (found != null) {
            position.save(found.first, found.second, key)
        } else {
            val r = leftRow.coerceIn(0, rows.lastIndex)
            position.save(r, leftColumn.coerceIn(0, rows[r].items.lastIndex))
        }
        listState.scrollIntoView(position.row)
        withFrameNanos {}
        rowStates[rows[position.row].key]?.scrollIntoView(position.column)
    }
}

@Composable
private fun HomeRowContent(
    row: HomeRow,
    rowIndex: Int,
    focusPosition: FocusPosition,
    hero: HeroFocus,
    rowStates: MutableMap<UUID, LazyListState>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    DisposableEffect(row.key, rowState) {
        rowStates[row.key] = rowState
        onDispose { if (rowStates[row.key] === rowState) rowStates.remove(row.key) }
    }
    Column(modifier = modifier) {
        Text(
            text = row.title.asString(),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.height(ROW_TITLE_HEIGHT).padding(horizontal = TvSafeArea.horizontal),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            contentPadding = PaddingValues(horizontal = TvSafeArea.horizontal),
            modifier = Modifier.focusRestorer(),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.id }) { column, item ->
                ItemCard(
                    item = item,
                    direction = row.direction,
                    showSeriesName = row.direction == Direction.VERTICAL,
                    onClick = { onAction(HomeAction.OnItemClick(it)) },
                    modifier =
                        Modifier.focusPosition(focusPosition, rowIndex, column, item.id.toString())
                            .onFocusChanged { if (it.hasFocus) hero.item = item }
                            .testTag(homeCardTag(item.id)),
                )
            }
        }
    }
}

internal fun homeCardTag(id: UUID) = "ce_home_card_$id"

/**
 * Backdrop, logo (or title), info line and overview of the focused card. It follows focus after
 * [HERO_DELAY_MS], so moving fast along a row only loads the backdrop of the card it stops on.
 */
@Composable
private fun HomeHero(hero: HeroFocus) {
    val item by
        produceState<FindroidItem?>(initialValue = null, hero) {
            snapshotFlow { hero.item }
                .collectLatest { focused ->
                    // The first card shows straight away; after that, wait until focus rests.
                    if (value != null) delay(HERO_DELAY_MS)
                    value = focused
                }
        }
    val shown = item ?: return

    HeroBackdrop(item = shown)

    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier =
            Modifier.fillMaxWidth(HERO_TEXT_WIDTH)
                .height(HERO_HEIGHT)
                .padding(
                    start = TvSafeArea.horizontal,
                    top = TvSafeArea.vertical,
                    bottom = MaterialTheme.spacings.medium,
                )
                .testTag("ce_home_hero"),
    ) {
        HeroTitle(item = shown)
        if (shown is FindroidEpisode) {
            Text(
                text = shown.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = itemInfoLine(shown),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = .7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (shown.overview.isNotBlank()) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            Text(
                text = shown.overview,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The backdrop at the size it's shown, top right behind the hero and the first row. It's asked from
 * the server at that width, not as the original, and decoded at that size: on a weak TV box a full
 * 4K backdrop per focus change is what makes Home stutter. The previous backdrop stays as the
 * placeholder, so the switch crossfades instead of flashing the background.
 */
@Composable
private fun HeroBackdrop(item: FindroidItem) {
    val uri =
        when (item) {
            is FindroidEpisode -> item.images.showBackdrop ?: item.images.backdrop
            else -> item.images.backdrop
        } ?: return
    val context = LocalContext.current
    val widthPx = (LocalWindowInfo.current.containerSize.width * BACKDROP_WIDTH).toInt()
    val previous = remember { PreviousImage() }
    val request =
        remember(uri, widthPx) {
            ImageRequest.Builder(context)
                .data(uri.toString().withImageSize(widthPx, null))
                .placeholderMemoryCacheKey(previous.key)
                .build()
        }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        // Faded out to the left and bottom with an alpha mask, so it blends into the app's
        // gradient background instead of ending at an edge.
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { previous.key = it.result.memoryCacheKey },
            modifier =
                Modifier.fillMaxWidth(BACKDROP_WIDTH)
                    .aspectRatio(16f / 9f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(BackdropFadeLeft, blendMode = BlendMode.DstIn)
                        drawRect(BackdropFadeBottom, blendMode = BlendMode.DstIn)
                    },
        )
    }
}

private val BackdropFadeLeft = Brush.horizontalGradient(0f to Color.Transparent, .45f to Color.Black)
private val BackdropFadeBottom = Brush.verticalGradient(.5f to Color.Black, 1f to Color.Transparent)

/** The memory cache key of the backdrop on screen. Plain field: nothing recomposes on it. */
private class PreviousImage {
    var key: MemoryCache.Key? = null
}

/** The logo, at the size it's shown, or the title while there's no logo. */
@Composable
private fun HeroTitle(item: FindroidItem) {
    val title = if (item is FindroidEpisode) item.seriesName else item.name
    val logo = if (item is FindroidEpisode) item.images.showLogo else item.images.logo
    var logoFailed by remember(logo) { mutableStateOf(logo == null) }
    if (logoFailed) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val logoWidthPx = with(LocalDensity.current) { LOGO_MAX_WIDTH.roundToPx() }
        AsyncImage(
            model = logo.toString().withImageSize(logoWidthPx, null),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { logoFailed = true },
            modifier = Modifier.widthIn(max = LOGO_MAX_WIDTH).heightIn(max = LOGO_MAX_HEIGHT),
        )
    }
}

/**
 * Puts the focused row at the top of the list, just below the hero. The card asks to be shown, so
 * [titleSpace] keeps the row title above it in view.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun rowAtTopSpec(titleSpace: Float) =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float = offset - titleSpace
    }

/** Scrolls only when [index] isn't on screen, so a return keeps the old scroll position. */
private suspend fun LazyListState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

private val HERO_HEIGHT = 200.dp
private val ROW_TITLE_HEIGHT = 36.dp
private const val HERO_TEXT_WIDTH = .55f
private const val BACKDROP_WIDTH = .7f
private val LOGO_MAX_WIDTH = 320.dp
private val LOGO_MAX_HEIGHT = 80.dp
private const val HERO_DELAY_MS = 300L
private const val REFRESH_WAIT_MS = 1_500L

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeScreenLayoutPreview() {
    FindroidTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                    isLoading = false,
                ),
            refreshed = true,
            onAction = {},
        )
    }
}
