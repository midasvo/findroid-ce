package dev.jdtech.jellyfin.film.presentation.downloads

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadStatus
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidEpisodeDto
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.getLocalSourcesForItems
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes run here instead of in viewModelScope: an Undo snackbar commits its delete when the
 * screen goes away, which can be just as the ViewModel is cleared.
 */
internal val downloadDeletionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val downloadQueue: DownloadQueue,
    private val downloader: Downloader,
    private val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    private var wasBusy = false

    /** The load in flight; a new load cancels it so two never run side by side. */
    private var loadJob: Job? = null

    /** Pending debounced reload requested by the queue. */
    private var reloadJob: Job? = null

    /** What the last load read, kept so a sort change can re-sort without reading again. */
    private var loaded: LoadedLibrary? = null

    /** Result of the last directory walk, reused until downloads change. */
    private var cachedWalk: StorageWalk? = null

    /** Set when a download finished or was deleted, so the next load walks the directories. */
    @Volatile private var storageDirty = true

    /** Completed queue entries already scheduled to move into the library. */
    private val handledCompletions = mutableSetOf<UUID>()

    /** Exponential moving average of each active download's transfer rate. */
    private val speedAverages = mutableMapOf<UUID, Double>()

    /** The screen's first ON_RESUME comes right after [init]'s load, so it doesn't load again. */
    private var skipNextResume = true

    /** Called on each ON_RESUME of the screen: refreshes the list when the user returns to it. */
    fun onScreenResumed() {
        if (skipNextResume) {
            skipNextResume = false
            return
        }
        loadItems()
    }

    init {
        // Load at once rather than on the screen's first ON_RESUME, which only comes after the
        // navigation transition (#256: ~200 ms later on a phone).
        loadItems()

        // Observe queue entries -> queueItems
        viewModelScope.launch {
            downloadQueue.entries.collect { entries ->
                val items = entries.map { it.toActiveDownload() }
                // Rows only change when something they show changes, so an unchanged
                // list does not need to reach the screen.
                _state.update { if (it.queueItems == items) it else it.copy(queueItems = items) }

                // Finished downloads move into the library on their own: reload so they
                // show up there, and drop the queue entry once its row has had a moment
                // to show the check mark.
                handledCompletions.retainAll(entries.map { it.id }.toSet())
                val newlyCompleted =
                    entries.filter {
                        it.state is DownloadQueue.EntryState.Completed &&
                            handledCompletions.add(it.id)
                    }
                if (newlyCompleted.isNotEmpty()) {
                    storageDirty = true
                    scheduleReload()
                    viewModelScope.launch {
                        delay(COMPLETED_LINGER_MS)
                        newlyCompleted.forEach { downloadQueue.remove(it.id) }
                    }
                }
                // When downloads finish (busy -> not busy), reload sections so newly
                // completed items appear and removed shows disappear. The pump now
                // finalizes downloads (rename + DB update) before flipping to
                // Completed, so a single reload is enough — no race window.
                val isBusy =
                    entries.any {
                        it.state is DownloadQueue.EntryState.Downloading ||
                            it.state is DownloadQueue.EntryState.Pending ||
                            it.state is DownloadQueue.EntryState.Paused
                    }
                if (isBusy) {
                    wasBusy = true
                } else if (wasBusy) {
                    wasBusy = false
                    storageDirty = true
                    scheduleReload()
                }
            }
        }
    }

    /**
     * Reload requested by the queue. A completion and the busy-to-idle transition arrive
     * together, so wait a moment and run one load for both.
     */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob =
            viewModelScope.launch {
                delay(RELOAD_DEBOUNCE_MS)
                loadItems()
            }
    }

    fun loadItems() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                // Only the very first load shows the spinner; later ones refresh in place.
                _state.update { it.copy(isLoading = loaded == null, error = null) }
                try {
                    reconcileStuckDownloads()
                    val library = readLibrary()
                    loaded = library
                    val sections =
                        withContext(Dispatchers.IO) { buildSections(library, _state.value.sortOrder) }
                    _state.update { it.withLibrary(library, sections) }

                    // The directory walk is the slow part, so the list goes out first.
                    val storageInfo = calculateStorageInfo(library.sizeSignature)
                    _state.update {
                        it.copy(
                            storageUsedBytes = storageInfo.usedBytes,
                            storageFreeBytes = storageInfo.freeBytes,
                            storageTotalBytes = storageInfo.totalBytes,
                            storageIsExternal = storageInfo.isExternal,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, error = e) }
                }
            }
    }

    private fun DownloadsState.withLibrary(
        library: LoadedLibrary,
        sections: List<CollectionSection>,
    ): DownloadsState {
        fun sectionBytes(id: Int) =
            sections.firstOrNull { it.id == id }?.items?.sumOf { library.itemSizes[it.id] ?: 0L }
                ?: 0L
        return copy(
            isLoading = false,
            sections = sections,
            itemSizes = library.itemSizes,
            episodeCounts = library.episodeCounts,
            moviesBytes = sectionBytes(Constants.FAVORITE_TYPE_MOVIES),
            showsBytes = sectionBytes(Constants.FAVORITE_TYPE_SHOWS),
        )
    }

    /**
     * Self-heal pass: any source still tagged `.download` in the DB whose final
     * file is already on disk gets fixed up. This rescues items left stuck by
     * older buggy builds, missed broadcasts, or process death between rename
     * and DB write — without it, those items would never appear in the Library
     * tab again, even after restart.
     */
    private suspend fun reconcileStuckDownloads() = withContext(Dispatchers.IO) {
        for (src in database.getActiveDownloadSources()) {
            val finalPath = src.path.removeSuffix(".download")
            val finalFile = File(finalPath)
            if (finalFile.exists() && finalFile.length() > 0) {
                database.setSourceDownloaded(src.id, finalPath, System.currentTimeMillis())
                File(src.path).takeIf { it.exists() }?.delete()
            }
        }
    }

    fun cancelDownload(activeDownload: ActiveDownload) {
        downloadQueue.remove(activeDownload.item.id)
    }

    fun dismissCompletedDownload(activeDownload: ActiveDownload) {
        // Removing the queue entry emits a new queueItems list via the observer;
        // no need to reload completed sections (the completed item was already
        // present in sections once it finished downloading).
        downloadQueue.remove(activeDownload.item.id)
    }

    fun retryDownload(activeDownload: ActiveDownload) {
        downloadQueue.retry(activeDownload.item.id)
    }


    fun setSortOrder(order: DownloadSortOrder) {
        _state.update { it.copy(sortOrder = order) }
        val library = loaded
        if (library == null) {
            // Nothing read yet; the load in progress or the first one picks up the order.
            return
        }
        viewModelScope.launch {
            val sections = withContext(Dispatchers.IO) { buildSections(library, order) }
            _state.update { it.withLibrary(library, sections) }
        }
    }

    fun deleteDownloadedItem(item: FindroidItem) = deleteDownloadedItems(listOf(item))

    fun deleteDownloadedItems(items: List<FindroidItem>) {
        downloadDeletionScope.launch {
            for (item in items) {
                if (item is FindroidShow) {
                    val userId = repository.getUserId()
                    for (epDto in database.getEpisodesByShowId(item.id)) {
                        deleteLocalCopy(epDto.toFindroidEpisode(database, userId))
                    }
                } else {
                    deleteLocalCopy(item)
                }
            }
            storageDirty = true
            loadItems()
        }
    }

    private suspend fun deleteLocalCopy(item: FindroidItem) {
        downloadQueue.remove(item.id)
        val source = item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL } ?: return
        downloader.deleteItem(item, source)
    }

    private fun DownloadQueue.Entry.toActiveDownload(): ActiveDownload {
        val downloadStatus =
            when (state) {
                is DownloadQueue.EntryState.Pending -> DownloadStatus.QUEUED
                is DownloadQueue.EntryState.Downloading -> DownloadStatus.DOWNLOADING
                is DownloadQueue.EntryState.Paused -> DownloadStatus.PAUSED
                is DownloadQueue.EntryState.Completed -> DownloadStatus.COMPLETED
                is DownloadQueue.EntryState.Failed -> DownloadStatus.FAILED
            }
        val failureText = (state as? DownloadQueue.EntryState.Failed)?.error
        // Smooth the measured rate so the time-left estimate settles instead of jumping
        // around with every poll.
        val smoothedBytesPerSecond =
            if (bytesPerSecond > 0) {
                val previous = speedAverages[id]
                val average =
                    if (previous == null) bytesPerSecond.toDouble()
                    else previous + SPEED_SMOOTHING * (bytesPerSecond - previous)
                speedAverages[id] = average
                average.toLong()
            } else {
                speedAverages.remove(id)
                0L
            }
        return ActiveDownload(
            item = item,
            progress =
                DownloadProgress(status = downloadStatus, progress = progress / 100f),
            downloadId = downloadId,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            totalBytesEstimated = totalBytesEstimated,
            isTranscode = isTranscode,
            bytesPerSecond = smoothedBytesPerSecond,
            errorText = failureText,
        )
    }

    /** Everything one load read, enough to build the sections for any sort order. */
    private class LoadedLibrary(
        val movies: List<FindroidMovie>,
        val shows: List<FindroidShow>,
        val episodesByShow: Map<UUID, List<FindroidEpisodeDto>>,
        val sourcesByItem: Map<UUID, List<FindroidSource>>,
        val itemSizes: Map<UUID, Long>,
        val episodeCounts: Map<UUID, Int>,
    ) {
        /** Changes when files were added or removed, whoever did it. */
        val sizeSignature: Long = itemSizes.values.sum() * 31 + itemSizes.size

        /** Newest file modification time per item, filled in when sorting by date. */
        val mtimes = java.util.concurrent.ConcurrentHashMap<UUID, Long>()
    }

    private class StorageInfo(
        val usedBytes: Long,
        val freeBytes: Long,
        val totalBytes: Long,
        val isExternal: Boolean,
    )

    private class StorageWalk(val signature: Long, val usedBytes: Long)

    private suspend fun readLibrary(): LoadedLibrary =
        withContext(Dispatchers.IO) {
            val items = repository.getDownloads()
            val shows = items.filterIsInstance<FindroidShow>()
            val episodesByShow = shows.associate { it.id to database.getEpisodesByShowId(it.id) }
            // One batched read for every movie and episode instead of a query per item
            // (and per pass).
            val sourcesByItem =
                database.getLocalSourcesForItems(
                    items.filter { it !is FindroidShow }.map { it.id } +
                        episodesByShow.values.flatten().map { it.id }
                )

            fun sizeOf(id: UUID): Long =
                sourcesByItem[id].orEmpty().sumOf { src ->
                    val f = File(src.path)
                    if (f.exists()) f.length() else 0L
                }
            val itemSizes = mutableMapOf<UUID, Long>()
            for (item in items) {
                val bytes =
                    if (item is FindroidShow) {
                        episodesByShow.getValue(item.id).sumOf { sizeOf(it.id) }
                    } else {
                        sizeOf(item.id)
                    }
                if (bytes > 0) itemSizes[item.id] = bytes
            }
            for (episodes in episodesByShow.values) {
                for (ep in episodes) {
                    val bytes = sizeOf(ep.id)
                    if (bytes > 0) itemSizes[ep.id] = bytes
                }
            }

            fun hasCompletedSource(id: UUID) =
                sourcesByItem[id].orEmpty().any { !it.path.endsWith(".download") }
            // A movie/show with ONLY a .download source is still being downloaded
            // and belongs in the Queue tab, not the Library tab.
            val movies = items.filterIsInstance<FindroidMovie>().filter { hasCompletedSource(it.id) }
            val episodeCounts =
                shows.associate { show ->
                    show.id to episodesByShow.getValue(show.id).count { hasCompletedSource(it.id) }
                }
            LoadedLibrary(
                movies = movies,
                shows = shows.filter { episodeCounts.getValue(it.id) > 0 },
                episodesByShow = episodesByShow,
                sourcesByItem = sourcesByItem,
                itemSizes = itemSizes,
                episodeCounts = episodeCounts,
            )
        }

    private fun buildSections(
        library: LoadedLibrary,
        sortOrder: DownloadSortOrder,
    ): List<CollectionSection> {
        val itemSizes = library.itemSizes

        fun mtimeOf(id: UUID): Long =
            library.mtimes.getOrPut(id) {
                library.sourcesByItem[id].orEmpty().maxOfOrNull { File(it.path).lastModified() }
                    ?: 0L
            }
        val sortedMovies =
            when (sortOrder) {
                DownloadSortOrder.NAME -> library.movies.sortedBy { it.name.lowercase() }
                DownloadSortOrder.DATE -> library.movies.sortedByDescending { mtimeOf(it.id) }
                DownloadSortOrder.SIZE ->
                    library.movies.sortedByDescending { itemSizes[it.id] ?: 0L }
            }
        val sortedShows =
            when (sortOrder) {
                DownloadSortOrder.NAME -> library.shows.sortedBy { it.name.lowercase() }
                DownloadSortOrder.DATE ->
                    library.shows.sortedByDescending { show ->
                        library.episodesByShow.getValue(show.id).maxOfOrNull { mtimeOf(it.id) }
                            ?: 0L
                    }
                DownloadSortOrder.SIZE ->
                    library.shows.sortedByDescending { show ->
                        library.episodesByShow.getValue(show.id).sumOf { itemSizes[it.id] ?: 0L }
                    }
            }
        val sections = mutableListOf<CollectionSection>()
        CollectionSection(
                Constants.FAVORITE_TYPE_MOVIES,
                UiText.StringResource(CoreR.string.movies_label),
                sortedMovies,
            )
            .let { if (it.items.isNotEmpty()) sections.add(it) }
        CollectionSection(
                Constants.FAVORITE_TYPE_SHOWS,
                UiText.StringResource(CoreR.string.shows_label),
                sortedShows,
            )
            .let { if (it.items.isNotEmpty()) sections.add(it) }
        return sections
    }

    private suspend fun calculateStorageInfo(signature: Long): StorageInfo =
        withContext(Dispatchers.IO) {
            val storageIndex =
                appPreferences.getValue(appPreferences.downloadStorageIndex)?.toIntOrNull() ?: 0
            val dirs = context.getExternalFilesDirs(null)
            val storageDir =
                dirs.getOrNull(storageIndex)?.takeIf {
                    Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
                } ?: dirs.getOrNull(0)

            if (storageDir == null) return@withContext StorageInfo(0L, 0L, 0L, false)

            val isExternal = Environment.isExternalStorageRemovable(storageDir)
            // Walking the directories (trickplay can be thousands of files) is only worth
            // repeating when a download finished, was deleted, or the files on disk changed.
            val walk = cachedWalk
            val usedBytes =
                if (!storageDirty && walk != null && walk.signature == signature) {
                    walk.usedBytes
                } else {
                    storageDirty = false
                    // Media files live in external storage, but trickplay tiles and
                    // cached images live under the app's internal filesDir. Count both
                    // so the reported usage matches what the user is actually storing.
                    val videoBytes = File(storageDir, "downloads").sizeBytesRecursive()
                    val trickplayBytes = File(context.filesDir, "trickplay").sizeBytesRecursive()
                    val imagesBytes = File(context.filesDir, "images").sizeBytesRecursive()
                    (videoBytes + trickplayBytes + imagesBytes).also {
                        cachedWalk = StorageWalk(signature, it)
                    }
                }

            val stats = StatFs(storageDir.path)

            StorageInfo(usedBytes, stats.availableBytes, stats.totalBytes, isExternal)
        }

    private fun File.sizeBytesRecursive(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(this)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    private companion object {
        /** How long a finished download stays in the queue showing its check mark. */
        const val COMPLETED_LINGER_MS = 1_500L

        /** Quiet time before a queue-triggered reload, so one completion loads once. */
        const val RELOAD_DEBOUNCE_MS = 300L

        /** Weight of the newest speed sample; lower is steadier but slower to react. */
        const val SPEED_SMOOTHING = 0.2
    }
}
