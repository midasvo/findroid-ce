package dev.jdtech.jellyfin.presentation.film

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.core.presentation.ephyra.EphyraMotion
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.film.presentation.home.orderedRows
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.EmptyState
import dev.jdtech.jellyfin.presentation.film.components.ErrorState
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeHeader
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.film.components.ProfileBottomSheet
import dev.jdtech.jellyfin.presentation.film.components.ServerSelectionBottomSheet
import dev.jdtech.jellyfin.presentation.film.components.TopContentScrim
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.ReloadOnResume
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onLibraryClick: (library: FindroidCollection) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageServers: () -> Unit,
    onItemClick: (item: FindroidItem) -> Unit,
    onAddUser: () -> Unit,
    onPublicUserClick: (username: String) -> Unit,
    onUserSwitched: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ReloadOnResume(shouldReload = { viewModel.shouldReload(stopEventFollows = true) }) { viewModel.loadData(silent = true) }

    HomeScreenLayout(
        state = state,
        baseUrl = JellyfinApi.getInstance(LocalContext.current).api.baseUrl,
        onAddUser = onAddUser,
        onPublicUserClick = onPublicUserClick,
        onUserSwitched = {
            // Home stays alive across a switch, so load the new user's rows now.
            viewModel.loadData()
            onUserSwitched()
        },
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> onItemClick(action.item)
                is HomeAction.OnLibraryClick -> onLibraryClick(action.library)
                is HomeAction.OnSearchClick -> onSearchClick()
                is HomeAction.OnSettingsClick -> onSettingsClick()
                is HomeAction.OnManageServers -> onManageServers()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenLayout(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    baseUrl: String? = null,
    onAddUser: () -> Unit = {},
    onPublicUserClick: (String) -> Unit = {},
    onUserSwitched: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val safePadding = rememberSafePadding(handleStartInsets = false)

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingTop = safePadding.top + MaterialTheme.spacings.small
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)

    val contentPaddingTop = safePadding.top + 88.dp

    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val showServerSelectionSheetState = rememberModalBottomSheetState()
    var showServerSelectionBottomSheet by remember { mutableStateOf(false) }

    val profileSheetState = rememberModalBottomSheetState()
    var showProfileBottomSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // A switch to another user dips the rows to dark and brings the new ones up (VISION §6), so it
    // reads as a change of person rather than a reload. One list, so the scroll state stays shared.
    val userFade = remember { Animatable(1f) }
    var shownUserId by remember { mutableStateOf(state.user?.id) }
    LaunchedEffect(state.user?.id) {
        if (state.user?.id != shownUserId) {
            shownUserId = state.user?.id
            userFade.animateTo(0f, tween(EphyraMotion.DURATION_SHORT, easing = EphyraMotion.EasingExit))
            userFade.animateTo(1f, tween(EphyraMotion.DURATION_MEDIUM, easing = EphyraMotion.EasingEnter))
        }
    }

    // Sections arrive in any order, and a list keeps its first item in place when another lands
    // above it. That would leave the carousel out of view, so stay at the top instead.
    // CE (#142): the rows in the order the user chose, hidden ones left out.
    val rows = remember(state) { state.orderedRows() }
    val firstItemKey = rows.firstOrNull()?.id
    DisposableEffect(firstItemKey) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            listState.requestScrollToItem(0)
        }
        onDispose {}
    }

    Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = { onAction(HomeAction.OnRetryClick) }) {
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = userFade.value }
                        .semantics { traversalIndex = 1f },
                state = listState,
                contentPadding = PaddingValues(top = contentPaddingTop, bottom = paddingBottom),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            ) {
                items(rows, key = { it.id }, contentType = { it::class }) { row ->
                    when (row) {
                        is HomeItem.Suggestions ->
                            HomeCarousel(
                                items = row.items,
                                itemsPadding = itemsPadding,
                                onAction = onAction,
                                isParentScrolling = { listState.isScrollInProgress },
                            )
                        is HomeItem.Section ->
                            HomeSection(
                                section = row.homeSection,
                                itemsPadding = itemsPadding,
                                onAction = onAction,
                                modifier = Modifier.animateItem(),
                            )
                        is HomeItem.ViewItem ->
                            HomeView(
                                view = row,
                                itemsPadding = itemsPadding,
                                onAction = onAction,
                                modifier = Modifier.animateItem(),
                            )
                        HomeItem.OfflineCard -> Unit
                    }
                }
                if (state.isEmpty && !state.isLoading) {
                    item(key = "placeholder") {
                        val placeholderModifier =
                            Modifier.fillParentMaxWidth().fillParentMaxHeight(0.75f)
                        val error = state.error
                        if (error != null) {
                            ErrorState(
                                error = error,
                                onRetryClick = { onAction(HomeAction.OnRetryClick) },
                                modifier = placeholderModifier,
                            )
                        } else {
                            EmptyState(
                                icon = CoreR.drawable.ic_home,
                                title = stringResource(CoreR.string.nothing_here_yet),
                                modifier = placeholderModifier,
                                body = stringResource(CoreR.string.home_empty_body),
                            )
                        }
                    }
                }
            }
            TopContentScrim(height = contentPaddingTop)
        }

        if (state.error != null && showErrorDialog) {
            ErrorDialog(exception = state.error!!, onDismissRequest = { showErrorDialog = false })
        }
    }

    HomeHeader(
        serverName = state.server?.name ?: "",
        isLoading = state.isLoading,
        isError = state.error != null,
        onServerClick = { showServerSelectionBottomSheet = true },
        onErrorClick = { showErrorDialog = true },
        onRetryClick = { onAction(HomeAction.OnRetryClick) },
        onSearchClick = { onAction(HomeAction.OnSearchClick) },
        onUserClick = { showProfileBottomSheet = true },
        user = state.user,
        baseUrl = baseUrl,
        modifier = Modifier.padding(start = paddingStart, top = paddingTop, end = paddingEnd),
    )

    if (showProfileBottomSheet) {
        ProfileBottomSheet(
            baseUrl = baseUrl,
            onSwitchServer = {
                showProfileBottomSheet = false
                showServerSelectionBottomSheet = true
            },
            onPublicUserClick = { username ->
                showProfileBottomSheet = false
                onPublicUserClick(username)
            },
            onAddUser = {
                showProfileBottomSheet = false
                onAddUser()
            },
            onSettings = {
                showProfileBottomSheet = false
                onAction(HomeAction.OnSettingsClick)
            },
            onUserSwitched = {
                showProfileBottomSheet = false
                onUserSwitched()
            },
            onDismissRequest = { showProfileBottomSheet = false },
            sheetState = profileSheetState,
        )
    }

    if (showServerSelectionBottomSheet) {
        ServerSelectionBottomSheet(
            currentServerId = state.server?.id ?: "",
            onUpdate = {
                onAction(HomeAction.OnRetryClick)
                scope
                    .launch { showServerSelectionSheetState.hide() }
                    .invokeOnCompletion {
                        if (!showServerSelectionSheetState.isVisible) {
                            showServerSelectionBottomSheet = false
                        }
                    }
            },
            onManage = {
                onAction(HomeAction.OnManageServers)
                scope.launch { showServerSelectionSheetState.hide() }
            },
            onDismissRequest = { showServerSelectionBottomSheet = false },
            sheetState = showServerSelectionSheetState,
        )
    }
}

@PreviewScreenSizes
@Composable
private fun HomeScreenLayoutPreview() {
    FindroidTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    server = dummyServer,
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                    isLoading = false,
                    error = Exception("Failed to load data"),
                ),
            onAction = {},
        )
    }
}
