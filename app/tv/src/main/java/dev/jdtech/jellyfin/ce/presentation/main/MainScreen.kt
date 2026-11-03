package dev.jdtech.jellyfin.ce.presentation.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import dev.jdtech.jellyfin.ce.presentation.film.FavoritesScreen
import dev.jdtech.jellyfin.ce.presentation.film.HomeScreen
import dev.jdtech.jellyfin.ce.presentation.film.LibraryScreen
import dev.jdtech.jellyfin.ce.presentation.film.SearchScreen
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.tvSafeAreaPadding
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.media.MediaViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * CE replacement for the upstream TV MainScreen and MediaScreen (ADR 0001, ADR 0002).
 *
 * Implements a side ModalNavigationDrawer collapsed to an icon rail and expanding on focus:
 * - Direct access to Home, Search, Favorites, and every library in one step.
 * - Profile button at the top (user + server; OK opens user switch).
 * - Settings at the bottom (OK opens SettingsRoute).
 * - Switching active content happens on OK (D-pad center), NOT on focus.
 * - Back button behavior (ADR 0002):
 *     - Other destination -> Home
 *     - Home, drawer closed -> Open drawer with focus on Home
 *     - Drawer open -> Exit app
 * - Per-destination state and focus position retained via SaveableStateProvider (ADR 0003).
 */
@Composable
fun MainScreen(
    navigateToSettings: () -> Unit,
    navigateToUsers: () -> Unit,
    navigateToLibrary: (libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToEpisode: (episodeId: UUID) -> Unit,
    navigateToCollection: (collectionId: UUID, name: String) -> Unit,
    mainViewModel: MainViewModel = hiltViewModel(),
    mediaViewModel: MediaViewModel = hiltViewModel(),
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val mediaState by mediaViewModel.state.collectAsState()

    LaunchedEffect(true) {
        mainViewModel.loadServerAndUser()
        mediaViewModel.loadData()
    }

    var user: User? = null
    var server: Server? = null
    if (uiState is MainViewModel.UiState.Normal) {
        val normal = uiState as MainViewModel.UiState.Normal
        user = normal.user
        server = normal.server
    }

    MainScreenLayout(
        user = user,
        server = server,
        libraries = mediaState.libraries,
        navigateToSettings = navigateToSettings,
        navigateToUsers = navigateToUsers,
        homeContent = {
            // CE Home (G13): hero for the focused card. Episodes open in their season (#130).
            HomeScreen(
                navigateToMovie = navigateToMovie,
                navigateToShow = navigateToShow,
                navigateToEpisode = navigateToEpisode,
            )
        },
        searchContent = {
            SearchScreen(
                navigateToMovie = navigateToMovie,
                navigateToShow = navigateToShow,
                navigateToEpisode = navigateToEpisode,
                navigateToCollection = navigateToCollection,
            )
        },
        favoritesContent = {
            FavoritesScreen(
                navigateToMovie = navigateToMovie,
                navigateToShow = navigateToShow,
                navigateToEpisode = navigateToEpisode,
            )
        },
        libraryContent = { library ->
            LibraryScreen(
                libraryId = library.id,
                libraryName = library.name,
                libraryType = library.type,
                navigateToLibrary = navigateToLibrary,
                navigateToMovie = navigateToMovie,
                navigateToShow = navigateToShow,
                navigateToCollection = navigateToCollection,
            )
        },
    )
}

const val DESTINATION_HOME = "home"
const val DESTINATION_SEARCH = "search"
const val DESTINATION_FAVORITES = "favorites"
fun libraryDestinationKey(libraryId: UUID): String = "library:$libraryId"

private const val ITEM_PROFILE = "profile"
private const val ITEM_SETTINGS = "settings"

val ClosedDrawerWidth = 80.dp

@Composable
fun MainScreenLayout(
    user: User?,
    server: Server?,
    libraries: List<FindroidCollection>,
    navigateToSettings: () -> Unit,
    navigateToUsers: () -> Unit,
    modifier: Modifier = Modifier,
    homeContent: @Composable () -> Unit,
    searchContent: @Composable () -> Unit,
    favoritesContent: @Composable () -> Unit,
    libraryContent: @Composable (library: FindroidCollection) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeDestinationKey by rememberSaveable { mutableStateOf(DESTINATION_HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val saveableStateHolder = rememberSaveableStateHolder()
    val profileFocusRequester = remember { FocusRequester() }
    val homeFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    // The drawer item that opened another route (profile or settings). This screen leaves the
    // composition while that route is shown, so the effect below runs again on the way back.
    var returnFocusTo by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val item = returnFocusTo ?: return@LaunchedEffect
        returnFocusTo = null
        val requester = if (item == ITEM_PROFILE) profileFocusRequester else settingsFocusRequester
        requestFocusWhenReady(requester)
    }

    val isDrawerOpen = drawerState.currentValue == DrawerValue.Open
    val isHome = activeDestinationKey == DESTINATION_HOME

    // Shows [key] and moves focus into it, so focus never stays on a node that just left the
    // composition. While the destination has nothing focusable yet (still loading), focus goes to
    // [fallback] in the drawer instead.
    fun showDestination(key: String, fallback: FocusRequester?) {
        activeDestinationKey = key
        coroutineScope.launch {
            withFrameNanos {}
            if (!contentFocusRequester.tryRequestFocus()) {
                fallback?.tryRequestFocus()
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            !isHome && isDrawerOpen -> {
                activeDestinationKey = DESTINATION_HOME
                homeFocusRequester.tryRequestFocus()
            }
            !isHome -> showDestination(DESTINATION_HOME, fallback = homeFocusRequester)
            !isDrawerOpen -> {
                coroutineScope.launch {
                    drawerState.setValue(DrawerValue.Open)
                    homeFocusRequester.tryRequestFocus()
                }
            }
            else -> {
                (context as? Activity)?.finish()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            Column(
                modifier =
                    Modifier.fillMaxHeight()
                        .focusGroup()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = TvSafeArea.vertical, horizontal = 12.dp)
                        .testTag("main_drawer_content"),
                horizontalAlignment = Alignment.Start,
            ) {
                // Profile header: user + server, OK opens user switch
                NavigationDrawerItem(
                    selected = false,
                    onClick = {
                        returnFocusTo = ITEM_PROFILE
                        navigateToUsers()
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_user),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier.focusRequester(profileFocusRequester)
                            .testTag("drawer_item_profile"),
                ) {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = user?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        server?.name?.let { serverName ->
                            Text(
                                text = serverName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable navigation list: Search, Home, Favorites, and each Library
                LazyColumn(
                    modifier =
                        Modifier.weight(1f)
                            .focusRestorer()
                            .focusProperties { down = settingsFocusRequester },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = DESTINATION_SEARCH) {
                        NavigationDrawerItem(
                            selected = activeDestinationKey == DESTINATION_SEARCH,
                            onClick = { showDestination(DESTINATION_SEARCH, fallback = null) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_search),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier = Modifier.testTag("drawer_item_search"),
                        ) {
                            Text(
                                text = stringResource(id = CoreR.string.search),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    item(key = DESTINATION_HOME) {
                        NavigationDrawerItem(
                            selected = activeDestinationKey == DESTINATION_HOME,
                            onClick = { showDestination(DESTINATION_HOME, fallback = null) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_home),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier =
                                Modifier.focusRequester(homeFocusRequester)
                                    .testTag("drawer_item_home"),
                        ) {
                            Text(
                                text = stringResource(id = CoreR.string.title_home),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    item(key = DESTINATION_FAVORITES) {
                        NavigationDrawerItem(
                            selected = activeDestinationKey == DESTINATION_FAVORITES,
                            onClick = { showDestination(DESTINATION_FAVORITES, fallback = null) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_heart),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier = Modifier.testTag("drawer_item_favorites"),
                        ) {
                            Text(
                                text = stringResource(id = CoreR.string.title_favorite),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    items(libraries, key = { it.id }) { library ->
                        val itemKey = libraryDestinationKey(library.id)
                        NavigationDrawerItem(
                            selected = activeDestinationKey == itemKey,
                            onClick = { showDestination(itemKey, fallback = null) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = library.type.drawerIconRes()),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier = Modifier.testTag("drawer_item_library_${library.id}"),
                        ) {
                            Text(
                                text = library.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom: Settings
                NavigationDrawerItem(
                    selected = false,
                    onClick = {
                        returnFocusTo = ITEM_SETTINGS
                        navigateToSettings()
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_settings),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier.focusRequester(settingsFocusRequester)
                            .testTag("drawer_item_settings"),
                ) {
                    Text(
                        text = stringResource(id = CoreR.string.title_settings),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(start = ClosedDrawerWidth)
                    .focusRequester(contentFocusRequester)
                    .focusGroup()
        ) {
            saveableStateHolder.SaveableStateProvider(activeDestinationKey) {
                when {
                    activeDestinationKey == DESTINATION_HOME -> {
                        homeContent()
                    }
                    activeDestinationKey == DESTINATION_SEARCH -> {
                        searchContent()
                    }
                    activeDestinationKey == DESTINATION_FAVORITES -> {
                        favoritesContent()
                    }
                    activeDestinationKey.startsWith("library:") -> {
                        val libraryIdString = activeDestinationKey.removePrefix("library:")
                        val library =
                            libraries.firstOrNull { it.id.toString() == libraryIdString }
                        if (library != null) {
                            libraryContent(library)
                        }
                    }
                }
            }
        }
    }
}

/** Requests focus, or returns false when [this] is not attached or nothing can take focus. */
private fun FocusRequester.tryRequestFocus(): Boolean =
    try {
        requestFocus(FocusDirection.Enter)
    } catch (_: IllegalStateException) {
        false
    }

/** Retries for a few frames, until the target has been laid out. */
private suspend fun requestFocusWhenReady(requester: FocusRequester) {
    repeat(10) {
        withFrameNanos {}
        if (requester.tryRequestFocus()) return
    }
}

/** Placeholder screen for Favorites until #137 lands. */
@Composable
fun PlaceholderDestinationScreen(
    @DrawableRes icon: Int,
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().tvSafeAreaPadding().focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(id = titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun CollectionType.drawerIconRes(): Int =
    when (this) {
        CollectionType.Movies -> CoreR.drawable.ic_film
        CollectionType.TvShows -> CoreR.drawable.ic_tv
        CollectionType.Music -> CoreR.drawable.ic_speaker
        CollectionType.Playlists -> CoreR.drawable.ic_list
        else -> CoreR.drawable.ic_library
    }
