package dev.jdtech.jellyfin.presentation.film

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import dev.jdtech.jellyfin.presentation.film.components.StorageSummary
import dev.jdtech.jellyfin.presentation.film.components.SwipeActionBackground
import dev.jdtech.jellyfin.presentation.film.components.downloadTitles
import dev.jdtech.jellyfin.presentation.film.components.groupedItemShape
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Queues longer than this collapse behind a "Show all" toggle. */
private const val COLLAPSED_QUEUE_SIZE = 3

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    onBrowseLibraryClick: () -> Unit,
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
        onItemDelete = { viewModel.deleteDownloadedItem(it) },
        onSortOrderChange = { viewModel.setSortOrder(it) },
        onCancelDownload = { viewModel.cancelDownload(it) },
        onDismissDownload = { viewModel.dismissCompletedDownload(it) },
        onRetryDownload = { viewModel.retryDownload(it) },
        // The library can't be browsed offline, so the empty state offers no way there.
        onBrowseLibraryClick = onBrowseLibraryClick.takeUnless { LocalOfflineMode.current },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    onItemClick: (FindroidItem) -> Unit,
    onItemDelete: (FindroidItem) -> Unit,
    onSortOrderChange: (DownloadSortOrder) -> Unit,
    onCancelDownload: (ActiveDownload) -> Unit,
    onDismissDownload: (ActiveDownload) -> Unit,
    onRetryDownload: (ActiveDownload) -> Unit,
    onBrowseLibraryClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Cancelling throws away progress, so it is deferred behind an Undo snackbar.
    // Until the snackbar resolves the download keeps running but is hidden here.
    var pendingCancelIds by remember { mutableStateOf(emptySet<UUID>()) }
    LaunchedEffect(state.queueItems) {
        // Forget committed cancels once the queue has dropped them, so a later
        // re-download of the same item is not hidden.
        val queuedIds = state.queueItems.map { it.item.id }.toSet()
        pendingCancelIds = pendingCancelIds intersect queuedIds
    }
    val requestCancel: (ActiveDownload) -> Unit = { download ->
        pendingCancelIds = pendingCancelIds + download.item.id
        scope.launch {
            // Resolve any earlier snackbar first; dismissing it commits that cancel.
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                try {
                    snackbarHostState.showSnackbar(
                        message = context.getString(CoreR.string.download_cancelled, download.item.name),
                        actionLabel = context.getString(CoreR.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                } catch (e: CancellationException) {
                    // Leaving the screen counts as not undoing.
                    onCancelDownload(download)
                    throw e
                }
            if (result == SnackbarResult.ActionPerformed) {
                pendingCancelIds = pendingCancelIds - download.item.id
            } else {
                onCancelDownload(download)
            }
        }
    }
    val queueItems = state.queueItems.filterNot { it.item.id in pendingCancelIds }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_download)) },
                actions = {
                    SortMenu(sortOrder = state.sortOrder, onSortOrderChange = onSortOrderChange)
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        if (queueItems.isEmpty() && state.sections.isEmpty()) {
            if (!state.isLoading) {
                EmptyDownloads(
                    onBrowseLibraryClick = onBrowseLibraryClick,
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (queueItems.isNotEmpty()) {
                downloadingSection(
                    queueItems = queueItems,
                    onCancelDownload = requestCancel,
                    onDismissDownload = onDismissDownload,
                    onRetryDownload = onRetryDownload,
                )
            }

            for (section in state.sections) {
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
                    LibraryRow(
                        item = item,
                        sizeBytes = state.itemSizes[item.id],
                        episodeCount = state.episodeCounts[item.id],
                        shape = groupedItemShape(index, section.items.size),
                        onClick = { onItemClick(item) },
                        onDelete = { onItemDelete(item) },
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
                        index = index,
                        count = visible.size,
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
    index: Int,
    count: Int,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val shape = groupedItemShape(index, count)
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

@Composable
private fun LibraryRow(
    item: FindroidItem,
    sizeBytes: Long?,
    episodeCount: Int?,
    shape: Shape,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Swiping asks for confirmation rather than deleting, then springs back.
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            showDeleteDialog = true
            dismissState.reset()
        }
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
        backgroundContent = { SwipeActionBackground(icon = CoreR.drawable.ic_trash, shape = shape) },
        modifier = modifier,
    ) {
        Box {
            DownloadRow(
                item = item,
                title = title,
                supporting = supporting,
                meta = meta,
                shape = shape,
                showPlaybackProgress = item !is FindroidShow,
                onClick = onClick,
                onLongClick = { showMenu = true },
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(CoreR.string.remove_download)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_trash),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    },
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(CoreR.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(CoreR.string.cancel))
                }
            },
            title = { Text(stringResource(CoreR.string.remove_download)) },
            text = { Text(stringResource(CoreR.string.remove_download_message, item.name)) },
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
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier.size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_download),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(CoreR.string.downloads_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(CoreR.string.downloads_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onBrowseLibraryClick != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onBrowseLibraryClick) {
                Text(stringResource(CoreR.string.browse_library))
            }
        }
    }
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
            onItemDelete = {},
            onSortOrderChange = {},
            onCancelDownload = {},
            onDismissDownload = {},
            onRetryDownload = {},
            onBrowseLibraryClick = {},
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
            onItemDelete = {},
            onSortOrderChange = {},
            onCancelDownload = {},
            onDismissDownload = {},
            onRetryDownload = {},
            onBrowseLibraryClick = {},
        )
    }
}
