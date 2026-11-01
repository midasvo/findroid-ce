package dev.jdtech.jellyfin.presentation.film

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadStatus
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.downloads.ActiveDownload
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadSortOrder
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsState
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.ActiveDownloadRow
import dev.jdtech.jellyfin.presentation.film.components.DownloadRow
import dev.jdtech.jellyfin.presentation.film.components.EmptyState
import dev.jdtech.jellyfin.presentation.film.components.ErrorState
import dev.jdtech.jellyfin.presentation.film.components.StorageSummary
import dev.jdtech.jellyfin.presentation.film.components.SwipeActionBackground
import dev.jdtech.jellyfin.presentation.film.components.canPlayDirectly
import dev.jdtech.jellyfin.presentation.film.components.downloadTitles
import dev.jdtech.jellyfin.presentation.film.components.groupedItemShape
import dev.jdtech.jellyfin.presentation.film.components.playDownloadedItem
import dev.jdtech.jellyfin.presentation.film.components.rememberUndoController
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import java.util.UUID

/** Queues longer than this collapse behind a "Show all" toggle. */
private const val COLLAPSED_QUEUE_SIZE = 3

private val UuidSetSaver =
    listSaver<Set<UUID>, String>(
        save = { ids -> ids.map { it.toString() } },
        restore = { ids -> ids.map(UUID::fromString).toSet() },
    )

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    onShowClick: (show: FindroidShow) -> Unit,
    onBrowseLibraryClick: () -> Unit,
    onStorageClick: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reload completed downloads each time the screen becomes visible, so edits
    // made on show/season/movie screens (deletes) are reflected when we return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadItems()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DownloadsScreenLayout(
        state = state,
        onItemClick = onItemClick,
        onShowClick = onShowClick,
        onItemsDelete = { viewModel.deleteDownloadedItems(it) },
        onSortOrderChange = { viewModel.setSortOrder(it) },
        onCancelDownload = { viewModel.cancelDownload(it) },
        onDismissDownload = { viewModel.dismissCompletedDownload(it) },
        onRetryDownload = { viewModel.retryDownload(it) },
        onRetryClick = { viewModel.loadItems() },
        // The library can't be browsed offline, so the empty state offers no way there.
        onBrowseLibraryClick = onBrowseLibraryClick.takeUnless { LocalOfflineMode.current },
        onStorageClick = onStorageClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    onItemClick: (FindroidItem) -> Unit,
    onShowClick: (FindroidShow) -> Unit,
    onItemsDelete: (List<FindroidItem>) -> Unit,
    onSortOrderChange: (DownloadSortOrder) -> Unit,
    onCancelDownload: (ActiveDownload) -> Unit,
    onDismissDownload: (ActiveDownload) -> Unit,
    onRetryDownload: (ActiveDownload) -> Unit,
    onRetryClick: () -> Unit,
    onBrowseLibraryClick: (() -> Unit)?,
    onStorageClick: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    // Cancelling and removing are deferred behind an Undo snackbar; until it resolves the
    // affected rows are hidden here.
    val undo = rememberUndoController(snackbarHostState)
    LaunchedEffect(state.queueItems, state.sections) {
        undo.retainHidden(
            state.queueItems.map { it.item.id }.toSet() +
                state.sections.flatMap { section -> section.items.map { it.id } }
        )
    }
    val queueItems = state.queueItems.filterNot { it.item.id in undo.hiddenIds }
    val sections =
        state.sections
            .map { section -> section.copy(items = section.items.filterNot { it.id in undo.hiddenIds }) }
            .filter { it.items.isNotEmpty() }
    val libraryItems = sections.flatMap { it.items }

    val cancelDownload: (ActiveDownload) -> Unit = { download ->
        undo.run(
            ids = setOf(download.item.id),
            message = context.getString(CoreR.string.download_cancelled, download.item.name),
        ) {
            onCancelDownload(download)
        }
    }
    val removeItems: (List<FindroidItem>) -> Unit = { items ->
        val message =
            items.singleOrNull()?.let { context.getString(CoreR.string.download_removed, it.name) }
                ?: context.resources.getQuantityString(
                    CoreR.plurals.downloads_removed,
                    items.size,
                    items.size,
                )
        undo.run(ids = items.map { it.id }.toSet(), message = message) { onItemsDelete(items) }
    }

    // Multi-select: entered from the top bar or by long-pressing a row.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(stateSaver = UuidSetSaver) { mutableStateOf(emptySet<UUID>()) }
    fun exitSelection() {
        selecting = false
        selectedIds = emptySet()
    }
    BackHandler(enabled = selecting) { exitSelection() }
    LaunchedEffect(libraryItems.isEmpty()) { if (libraryItems.isEmpty()) exitSelection() }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        if (selecting) {
                            stringResource(CoreR.string.downloads_selected_count, selectedIds.size)
                        } else stringResource(CoreR.string.title_download)
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = ::exitSelection) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = stringResource(CoreR.string.close),
                            )
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(
                            onClick = {
                                removeItems(libraryItems.filter { it.id in selectedIds })
                                exitSelection()
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription = stringResource(CoreR.string.remove),
                            )
                        }
                    } else {
                        if (libraryItems.isNotEmpty()) {
                            TextButton(onClick = { selecting = true }) {
                                Text(stringResource(CoreR.string.select))
                            }
                        }
                        SortMenu(sortOrder = state.sortOrder, onSortOrderChange = onSortOrderChange)
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        if (queueItems.isEmpty() && sections.isEmpty()) {
            val contentModifier = Modifier.padding(innerPadding).fillMaxSize()
            val error = state.error
            if (error != null) {
                ErrorState(error = error, onRetryClick = onRetryClick, modifier = contentModifier)
            } else if (!state.isLoading) {
                EmptyDownloads(
                    onBrowseLibraryClick = onBrowseLibraryClick,
                    modifier = contentModifier,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.storageUsedBytes > 0 || state.storageFreeBytes > 0) {
                item(key = "storage") {
                    StorageSummary(
                        moviesBytes = state.moviesBytes,
                        showsBytes = state.showsBytes,
                        usedBytes = state.storageUsedBytes,
                        freeBytes = state.storageFreeBytes,
                        totalBytes = state.storageTotalBytes,
                        isExternal = state.storageIsExternal,
                        onClick = onStorageClick,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (queueItems.isNotEmpty()) {
                downloadingSection(
                    queueItems = queueItems,
                    onCancelDownload = cancelDownload,
                    onDismissDownload = onDismissDownload,
                    onRetryDownload = onRetryDownload,
                )
            }

            for (section in sections) {
                item(key = "section:${section.id}") {
                    SectionHeader(
                        title = section.name.asString(),
                        modifier = Modifier.animateItem(),
                    )
                }
                itemsIndexed(
                    items = section.items,
                    key = { _, item -> "library:${item.id}" },
                ) { index, item ->
                    val selected = item.id in selectedIds
                    fun toggle() {
                        selectedIds = if (selected) selectedIds - item.id else selectedIds + item.id
                    }
                    LibraryRow(
                        item = item,
                        sizeBytes = state.itemSizes[item.id],
                        episodeCount = state.episodeCounts[item.id],
                        shape = groupedItemShape(index, section.items.size),
                        selected = if (selecting) selected else null,
                        onClick = {
                            when {
                                selecting -> toggle()
                                item is FindroidShow -> onShowClick(item)
                                else -> onItemClick(item)
                            }
                        },
                        onLongClick = {
                            selecting = true
                            toggle()
                        },
                        onPlayClick =
                            if (!selecting && canPlayDirectly(item)) {
                                { context.playDownloadedItem(item) }
                            } else null,
                        onRemove = { removeItems(listOf(item)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.downloadingSection(
    queueItems: List<ActiveDownload>,
    onCancelDownload: (ActiveDownload) -> Unit,
    onDismissDownload: (ActiveDownload) -> Unit,
    onRetryDownload: (ActiveDownload) -> Unit,
) {
    item(key = "downloading:header") {
        val context = LocalContext.current
        val bytesPerSecond =
            queueItems
                .filter { it.progress.status == DownloadStatus.DOWNLOADING }
                .sumOf { it.bytesPerSecond }
        SectionHeader(
            title = "${stringResource(CoreR.string.downloads_downloading)} · ${queueItems.size}",
            trailing =
                if (bytesPerSecond > 0) "${Formatter.formatShortFileSize(context, bytesPerSecond)}/s"
                else null,
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "downloading:rows") {
        // One item holding the whole group, so "Show all" can expand it in place.
        var showAll by rememberSaveable { mutableStateOf(false) }
        val collapsible = queueItems.size > COLLAPSED_QUEUE_SIZE + 1
        val visible =
            if (collapsible && !showAll) queueItems.take(COLLAPSED_QUEUE_SIZE) else queueItems
        Column(
            modifier = Modifier.animateItem(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            visible.forEachIndexed { index, download ->
                key(download.item.id) {
                    QueueRow(
                        download = download,
                        shape = groupedItemShape(index, visible.size),
                        onCancel = { onCancelDownload(download) },
                        onDismiss = { onDismissDownload(download) },
                        onRetry = { onRetryDownload(download) },
                    )
                }
            }
            if (collapsible) {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        if (showAll) stringResource(CoreR.string.downloads_show_less)
                        else stringResource(CoreR.string.downloads_show_all, queueItems.size)
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    download: ActiveDownload,
    shape: Shape,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            when (download.progress.status) {
                DownloadStatus.COMPLETED,
                DownloadStatus.FAILED -> onDismiss()
                else -> onCancel()
            }
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeActionBackground(icon = CoreR.drawable.ic_x, shape = shape) },
    ) {
        ActiveDownloadRow(
            activeDownload = download,
            shape = shape,
            onCancelClick = onCancel,
            onRetryClick = onRetry,
        )
    }
}

/** A downloaded movie or show. [selected] is non-null while multi-select is active. */
@Composable
private fun LibraryRow(
    item: FindroidItem,
    sizeBytes: Long?,
    episodeCount: Int?,
    shape: Shape,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }

    val meta =
        buildList {
                when (item) {
                    is FindroidShow ->
                        episodeCount?.let {
                            add(pluralStringResource(CoreR.plurals.downloaded_episode_count, it, it))
                        }
                    is FindroidMovie -> {
                        item.productionYear?.let { add(it.toString()) }
                        if (item.runtimeTicks > 0) {
                            add(
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    (item.runtimeTicks / Constants.TICKS_PER_MINUTE).toInt(),
                                )
                            )
                        }
                    }
                    else -> Unit
                }
                sizeBytes?.let { add(Formatter.formatShortFileSize(context, it)) }
            }
            .joinToString(" · ")
    val (title, supporting) = downloadTitles(item)

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = selected == null,
        backgroundContent = { SwipeActionBackground(icon = CoreR.drawable.ic_trash, shape = shape) },
        modifier = modifier,
    ) {
        DownloadRow(
            item = item,
            title = title,
            supporting = supporting,
            meta = meta,
            shape = shape,
            showPlaybackProgress = item !is FindroidShow,
            onPlayClick = onPlayClick,
            onClick = onClick,
            onLongClick = if (selected == null) onLongClick else null,
            trailing =
                selected?.let { isSelected ->
                    { Checkbox(checked = isSelected, onCheckedChange = { onClick() }) }
                },
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortMenu(sortOrder: DownloadSortOrder, onSortOrderChange: (DownloadSortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                contentDescription = stringResource(CoreR.string.sort_by),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DownloadSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (order) {
                                DownloadSortOrder.DATE ->
                                    stringResource(CoreR.string.sort_recently_downloaded)
                                DownloadSortOrder.NAME -> stringResource(CoreR.string.sort_name)
                                DownloadSortOrder.SIZE -> stringResource(CoreR.string.sort_size)
                            }
                        )
                    },
                    trailingIcon = {
                        if (order == sortOrder) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_check),
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloads(onBrowseLibraryClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = CoreR.drawable.ic_download,
        title = stringResource(CoreR.string.downloads_empty_title),
        modifier = modifier,
        body = stringResource(CoreR.string.downloads_empty_body),
        actions =
            onBrowseLibraryClick?.let { onClick ->
                {
                    FilledTonalButton(onClick = onClick) {
                        Text(stringResource(CoreR.string.browse_library))
                    }
                }
            },
    )
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state =
                DownloadsState(
                    queueItems =
                        listOf(
                            ActiveDownload(
                                item = dummyEpisode,
                                progress =
                                    DownloadProgress(
                                        status = DownloadStatus.DOWNLOADING,
                                        progress = 0.33f,
                                    ),
                                downloadId = 1L,
                                bytesDownloaded = 164_000_000L,
                                totalBytes = 498_000_000L,
                                bytesPerSecond = 6_400_000L,
                            )
                        ),
                    sections =
                        listOf(
                            CollectionSection(
                                id = Constants.FAVORITE_TYPE_MOVIES,
                                name = UiText.StringResource(CoreR.string.movies_label),
                                items = dummyMovies,
                            )
                        ),
                    moviesBytes = 1_500_000_000L,
                    storageUsedBytes = 1_500_000_000L,
                    storageFreeBytes = 10_000_000_000L,
                    storageTotalBytes = 64_000_000_000L,
                ),
            onItemClick = {},
            onShowClick = {},
            onItemsDelete = {},
            onSortOrderChange = {},
            onCancelDownload = {},
            onDismissDownload = {},
            onRetryDownload = {},
            onRetryClick = {},
            onBrowseLibraryClick = {},
            onStorageClick = {},
        )
    }
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutEmptyPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state = DownloadsState(),
            onItemClick = {},
            onShowClick = {},
            onItemsDelete = {},
            onSortOrderChange = {},
            onCancelDownload = {},
            onDismissDownload = {},
            onRetryDownload = {},
            onRetryClick = {},
            onBrowseLibraryClick = {},
            onStorageClick = {},
        )
    }
}
