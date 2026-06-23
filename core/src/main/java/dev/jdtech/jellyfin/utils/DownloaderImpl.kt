package dev.jdtech.jellyfin.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMediaStreamDto
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.FindroidTrickplayInfo
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidEpisodeDto
import dev.jdtech.jellyfin.models.toFindroidMediaStreamDto
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidMovieDto
import dev.jdtech.jellyfin.models.toFindroidSeasonDto
import dev.jdtech.jellyfin.models.toFindroidSegmentsDto
import dev.jdtech.jellyfin.models.toFindroidShowDto
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.models.toFindroidSourceDto
import dev.jdtech.jellyfin.models.toFindroidTrickplayInfoDto
import dev.jdtech.jellyfin.models.toFindroidUserDataDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import dev.jdtech.jellyfin.utils.download.MediaDownloadEngine
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Provider
import kotlin.Exception
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Returns true if [path] should be deleted as an orphaned `.download` file:
 * not tracked by any DB source row AND not actively being written by the engine.
 *
 * Only `.download`-suffixed files are candidates — finalized files are never deleted
 * by the sweep.
 */
internal fun shouldDeleteOrphanFile(
    path: String,
    knownPaths: Set<String>,
    liveDmPaths: Set<String>,
): Boolean {
    if (!path.endsWith(".download")) return false
    if (path in knownPaths) return false
    if (path in liveDmPaths) return false
    return true
}

/**
 * Returns true if [path] starts with one of the [mountedRoots] paths, meaning it is on
 * an accessible volume. Paths on unmounted/ejected volumes cannot be judged for
 * existence — we must not make sweep decisions about them.
 */
internal fun isUnderMountedRoot(path: String, mountedRoots: List<String>): Boolean =
    mountedRoots.any { path.startsWith("$it/") }

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepositoryProvider: Provider<JellyfinRepository>,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
    private val engine: MediaDownloadEngine,
) : Downloader {

    companion object {
        private const val PENDING_DOWNLOAD_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }

    // Resolved per use, never captured: which repository is active (online vs offline) can
    // change at runtime without this @Singleton being recreated. Capturing a fixed instance
    // here goes stale after an offline -> online switch.
    private val jellyfinRepository: JellyfinRepository
        get() = jellyfinRepositoryProvider.get()

    /**
     * Monotonically-increasing download id counter. Seeded at System.currentTimeMillis() so
     * that fresh ids never collide with ids from a previous app session (time is monotone
     * across restarts). Each new download calls incrementAndGet() so concurrent starts in the
     * same millisecond each get a distinct id.
     */
    private val idCounter = AtomicLong(System.currentTimeMillis())

    // TODO: We should probably move most (if not all) code to a worker.
    //  At this moment it is possible that some things are not downloaded due to the user leaving
    //  the current screen
    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> = coroutineScope {
        val transcodeDolbyVision =
            appPreferences.getValue(appPreferences.downloadTranscodeDolbyVision)
        val allowMetered = appPreferences.getValue(appPreferences.downloadOverMobileData)
        val allowRoaming = appPreferences.getValue(appPreferences.downloadWhenRoaming)

        // ---- RESUME BRANCH -------------------------------------------------------
        // Match the first in-progress .download source row for this item, regardless of
        // the requested sourceId. The queue dedups by item.id so there is at most one
        // active source per item; constraining on sourceId here would miss the resume for
        // multi-source items (the 2-arg overload derives sourceId from the server's first
        // source, which may differ from the one originally downloaded) and would start a
        // duplicate fresh download.
        val existingSource = try {
            database.getSources(item.id).firstOrNull { source ->
                source.path.endsWith(".download") && source.downloadId != null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to query existing source for resume check")
            null
        }

        val existingDownloadId = existingSource?.downloadId
        if (existingSource != null && existingDownloadId != null) {
            return@coroutineScope try {
                // Re-resolve the URL from the server (not persisted). Prefer the existing
                // row's own source id so we resume the same source that was started; fall
                // back to the first available source if it is no longer offered.
                val sources = jellyfinRepository
                    .getMediaSources(item.id, true, transcodeDolbyVision)
                val source = sources.firstOrNull { it.id == existingSource.id }
                    ?: sources.firstOrNull()
                    ?: throw IllegalStateException("No media sources for ${item.name} on resume")
                engine.start(
                    MediaDownloadEngine.Request(
                        id = existingDownloadId,
                        url = source.path,
                        destFile = File(existingSource.path),
                        allowMetered = allowMetered,
                        allowRoaming = allowRoaming,
                    )
                )
                // Resume external streams: restart any still-in-progress subtitle transfers.
                val resolvedSource = existingSource.toFindroidSource(database)
                resumeExternalMediaStreams(item, resolvedSource, source, allowMetered, allowRoaming)
                Pair(existingDownloadId, null)
            } catch (e: Exception) {
                // On ANY exception in the RESUME branch: do NOT delete the item.
                // Keep the partial so the next attempt can resume from where it left off.
                Timber.e(e, "Failed to resume download for ${item.name}")
                Pair(-1L, mapDownloadError(e))
            }
        }

        // ---- FRESH BRANCH --------------------------------------------------------
        // Track the minted id so we can cancel the engine task if anything after start() throws
        // (e.g. DB insert fails). Without this the engine task would keep running with no
        // DB row, writing to disk and consuming bandwidth indefinitely.
        var mintedDownloadId: Long? = null
        try {
            val source =
                jellyfinRepository
                    .getMediaSources(item.id, true, transcodeDolbyVision)
                    .first { it.id == sourceId }
            val segments = jellyfinRepository.getSegments(item.id)
            val trickplayInfo =
                if (item is FindroidSources) {
                    item.trickplayInfo?.get(sourceId)
                } else {
                    null
                }
            val dirs = context.getExternalFilesDirs(null)
            val storageLocation = run {
                // Try requested index first; silently fall back to index 0 if unavailable.
                val requested = dirs.getOrNull(storageIndex)
                if (
                    requested != null &&
                    Environment.getExternalStorageState(requested) == Environment.MEDIA_MOUNTED
                ) {
                    requested
                } else {
                    dirs.getOrNull(0)?.takeIf {
                        Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
                    }
                }
            } ?: return@coroutineScope Pair(
                -1,
                UiText.StringResource(CoreR.string.storage_unavailable),
            )
            val extension = guessExtension(source.path)
            val relativePath = buildDownloadPath(item, source.id, extension)
            val destFile = File(storageLocation, "downloads/$relativePath.download")
            destFile.parentFile?.mkdirs()
            // A leftover .download file from a previous failed/cancelled attempt with no DB
            // row is an orphan. Drop it before starting so the engine starts at byte 0.
            if (destFile.exists()) destFile.delete()
            val stats = StatFs(storageLocation.path)
            if (stats.availableBytes < source.size) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, source.size),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }

            // Mint a new id and start the engine transfer.
            val downloadId = idCounter.incrementAndGet().also { mintedDownloadId = it }
            engine.start(
                MediaDownloadEngine.Request(
                    id = downloadId,
                    url = source.path,
                    destFile = destFile,
                    allowMetered = allowMetered,
                    allowRoaming = allowRoaming,
                )
            )

            when (item) {
                is FindroidMovie -> {
                    database.insertMovie(
                        item.toFindroidMovieDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }
                is FindroidEpisode -> {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(
                        show.toFindroidShowDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toFindroidSeasonDto())
                    database.insertEpisode(
                        item.toFindroidEpisodeDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )

                    startImagesDownloader(show)
                    startImagesDownloader(season)
                }
            }

            val sourceDto = source.toFindroidSourceDto(item.id, destFile.absolutePath)

            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(item.toFindroidUserDataDto(jellyfinRepository.getUserId()))

            val resolvedStorageIndex = dirs.indexOf(storageLocation)
            downloadExternalMediaStreams(item, source, resolvedStorageIndex, allowMetered, allowRoaming)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, sourceId, trickplayInfo)
            }

            startImagesDownloader(item)
            return@coroutineScope Pair(downloadId, null)
        } catch (e: Exception) {
            // Cancel the engine task first — deleteItem() only removes rows/files via the
            // source DB row, which may not have been inserted yet.
            mintedDownloadId?.let {
                try {
                    engine.cancel(it)
                } catch (cancelError: Exception) {
                    Timber.e(cancelError, "Failed to cancel leaked engine task $it")
                }
            }
            try {
                val source = jellyfinRepository.getMediaSources(item.id).first { it.id == sourceId }
                deleteItem(item, source)
            } catch (e: Exception) { Timber.e(e, "Failed to clean up failed download") }
            Timber.e(e)
            return@coroutineScope Pair(-1, mapDownloadError(e))
        }
    }

    override suspend fun downloadItem(
        item: FindroidItem,
        storageIndex: Int,
    ): Pair<Long, UiText?> {
        val sources = try {
            jellyfinRepository.getMediaSources(
                item.id,
                includePath = true,
                transcodeDolbyVision =
                    appPreferences.getValue(appPreferences.downloadTranscodeDolbyVision),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve media sources for ${item.name}")
            return Pair(-1, mapDownloadError(e))
        }
        val sourceId = sources.firstOrNull()?.id
            ?: return Pair(-1, UiText.StringResource(CoreR.string.download_error_no_sources))
        return downloadItem(item = item, sourceId = sourceId, storageIndex = storageIndex)
    }

    override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) {
        val source =
            database.getSourceByDownloadId(downloadId)?.toFindroidSource(database) ?: return
        source.downloadId?.let { engine.cancel(it) }
        deleteItem(item, source)
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) {
        when (item) {
            is FindroidMovie -> {
                database.deleteMovie(item.id)
            }
            is FindroidEpisode -> {
                database.deleteEpisode(item.id)
                val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
                if (remainingEpisodes.isEmpty()) {
                    database.deleteSeason(item.seasonId)
                    database.deleteUserData(item.seasonId)
                    File(context.filesDir, "trickplay/${item.seasonId}").deleteRecursively()
                    File(context.filesDir, "images/${item.seasonId}").deleteRecursively()
                    val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
                    if (remainingSeasons.isEmpty()) {
                        database.deleteShow(item.seriesId)
                        database.deleteUserData(item.seriesId)
                        File(context.filesDir, "trickplay/${item.seriesId}").deleteRecursively()
                        File(context.filesDir, "images/${item.seriesId}").deleteRecursively()
                    }
                }
            }
        }

        database.deleteSource(source.id)
        val sourceFile = File(source.path)
        sourceFile.delete()
        pruneEmptyParents(sourceFile)

        val mediaStreams = database.getMediaStreamsBySourceId(source.id)
        for (mediaStream in mediaStreams) {
            // Cancel any still-running engine task for this external stream before
            // removing the DB row, otherwise we leak in-flight downloads.
            mediaStream.downloadId?.let { engine.cancel(it) }
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(source.id)

        database.deleteUserData(item.id)

        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    /**
     * Walks up the directory tree and removes empty directories until we hit
     * the `downloads/` root or a non-empty directory.
     */
    private fun pruneEmptyParents(file: File) {
        var dir = file.parentFile ?: return
        while (dir.name != "downloads" && dir.listFiles()?.isEmpty() == true) {
            dir.delete()
            dir = dir.parentFile ?: return
        }
    }

    override suspend fun getProgress(downloadId: Long?): Downloader.Progress {
        if (downloadId == null) {
            return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
        }
        val snapshot = engine.snapshot(downloadId)
            ?: return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
        val progress = when (snapshot.status) {
            DownloadStatus.SUCCESSFUL -> 100
            DownloadStatus.RUNNING ->
                if (snapshot.totalBytes > 0) {
                    snapshot.bytesDownloaded.times(100).div(snapshot.totalBytes).toInt()
                } else {
                    -1
                }
            else -> -1
        }
        return Downloader.Progress(snapshot.status, progress, snapshot.bytesDownloaded, snapshot.totalBytes)
    }

    override suspend fun getProgress(downloadIds: List<Long>): Map<Long, Downloader.Progress> {
        if (downloadIds.isEmpty()) return emptyMap()
        val snapshots = engine.snapshots(downloadIds)
        val result = mutableMapOf<Long, Downloader.Progress>()
        for (id in downloadIds) {
            val snapshot = snapshots[id]
            if (snapshot == null) {
                result[id] = Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
            } else {
                val progress = when (snapshot.status) {
                    DownloadStatus.SUCCESSFUL -> 100
                    DownloadStatus.RUNNING ->
                        if (snapshot.totalBytes > 0) {
                            snapshot.bytesDownloaded.times(100).div(snapshot.totalBytes).toInt()
                        } else {
                            -1
                        }
                    else -> -1
                }
                result[id] = Downloader.Progress(snapshot.status, progress, snapshot.bytesDownloaded, snapshot.totalBytes)
            }
        }
        return result
    }

    private fun downloadExternalMediaStreams(
        item: FindroidItem,
        source: FindroidSource,
        storageIndex: Int = 0,
        allowMetered: Boolean = false,
        allowRoaming: Boolean = false,
    ) {
        val dirs = context.getExternalFilesDirs(null)
        val storageLocation = dirs.getOrNull(storageIndex)
            ?.takeIf { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?: dirs.getOrNull(0)?.takeIf { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?: return
        val downloadsDir = File(storageLocation, "downloads")
        downloadsDir.mkdirs()

        // Fresh pass only: this is reached when no in-progress source row exists for the
        // item, so no external-stream rows exist yet. Resume of in-progress streams is
        // handled separately by resumeExternalMediaStreams().
        for (mediaStream in source.mediaStreams.filter { it.isExternal }) {
            val id = UUID.randomUUID()
            try {
                val mediaStreamPath = mediaStream.path ?: continue
                val streamExt = mediaStreamPath.substringAfterLast('.', "")
                    .takeIf { it.length in 1..5 } ?: "sub"
                val streamPath =
                    File(downloadsDir, "${sanitize(item.name)}.${source.id}.$id.$streamExt.download")
                        .absolutePath
                database.insertMediaStream(
                    mediaStream.toFindroidMediaStreamDto(id, source.id, streamPath)
                )
                val downloadId = idCounter.incrementAndGet()
                engine.start(
                    MediaDownloadEngine.Request(
                        id = downloadId,
                        url = mediaStreamPath,
                        destFile = File(streamPath),
                        allowMetered = allowMetered,
                        allowRoaming = allowRoaming,
                    )
                )
                database.setMediaStreamDownloadId(id, downloadId)
            } catch (e: Exception) {
                // One bad external stream (malformed URL, engine rejects request)
                // shouldn't kill the whole download. Drop the orphan DB row and
                // keep going with the remaining streams.
                Timber.e(
                    e,
                    "Failed to start external stream ${mediaStream.title} for ${item.name}",
                )
                try {
                    database.deleteMediaStream(id)
                } catch (_: Exception) {
                    // swallow — nothing more we can do
                }
            }
        }
    }

    /**
     * Resumes external subtitle/media-stream downloads when an item is being resumed after
     * process death. The DB rows already exist (created on the original fresh pass), so this
     * does NOT create rows — it only (re)starts the engine for rows still in progress
     * (`.download`), recovering the remote URL by matching the row back to the server's
     * external stream list. Finalized rows (no `.download` suffix) are left untouched.
     *
     * No stable id links a stored row to a server stream, so we match on
     * type/language/codec/title — unique enough for real content. Subtitle files are tiny;
     * a row whose server match cannot be found is simply dropped (its partial discarded).
     */
    private fun resumeExternalMediaStreams(
        item: FindroidItem,
        dbSource: FindroidSource,
        serverSource: FindroidSource,
        allowMetered: Boolean,
        allowRoaming: Boolean,
    ) {
        val rows = try {
            database.getMediaStreamsBySourceId(dbSource.id)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load media streams for resume of ${item.name}")
            return
        }
        val serverExternal = serverSource.mediaStreams.filter { it.isExternal }
        for (row in rows) {
            if (!row.path.endsWith(".download")) continue // already finalized
            val downloadId = row.downloadId ?: continue
            val url = serverExternal.firstOrNull { s ->
                s.type == row.type &&
                    s.language == row.language &&
                    s.codec == row.codec &&
                    s.title == row.title
            }?.path
            if (url == null) {
                Timber.w("No server match for in-progress subtitle '${row.title}'; dropping stale row")
                engine.cancel(downloadId)
                File(row.path).delete()
                try {
                    database.deleteMediaStream(row.id)
                } catch (_: Exception) {
                }
                continue
            }
            engine.start(
                MediaDownloadEngine.Request(
                    id = downloadId,
                    url = url,
                    destFile = File(row.path),
                    allowMetered = allowMetered,
                    allowRoaming = allowRoaming,
                )
            )
        }
    }

    private suspend fun downloadTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
    ) {
        val maxIndex =
            ceil(
                    trickplayInfo.thumbnailCount
                        .toDouble()
                        .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                )
                .toInt()
        val byteArrays = mutableListOf<ByteArray>()
        for (i in 0..maxIndex) {
            jellyfinRepository.getTrickplayData(itemId, trickplayInfo.width, i)?.let { byteArray ->
                byteArrays.add(byteArray)
            }
        }
        saveTrickplayData(itemId, sourceId, trickplayInfo, byteArrays)
    }

    private fun saveTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
        byteArrays: List<ByteArray>,
    ) {
        val basePath = "trickplay/$itemId/$sourceId"
        database.insertTrickplayInfo(trickplayInfo.toFindroidTrickplayInfoDto(sourceId))
        File(context.filesDir, basePath).mkdirs()
        for ((i, byteArray) in byteArrays.withIndex()) {
            val file = File(context.filesDir, "$basePath/$i")
            file.writeBytes(byteArray)
        }
    }

    override suspend fun savePendingDownload(item: FindroidItem) = withContext(Dispatchers.IO) {
        val kind =
            when (item) {
                is FindroidMovie -> "MOVIE"
                is FindroidEpisode -> "EPISODE"
                else -> return@withContext
            }
        database.insertPendingDownload(
            dev.jdtech.jellyfin.models.PendingDownloadDto(
                itemId = item.id,
                itemKind = kind,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun removePendingDownload(itemId: UUID) = withContext(Dispatchers.IO) {
        database.deletePendingDownload(itemId)
    }

    override suspend fun getPendingDownloads(): List<Pair<FindroidItem, Long>> = withContext(Dispatchers.IO) {
        val pending = database.getPendingDownloads()
        val result = mutableListOf<Pair<FindroidItem, Long>>()
        val cutoff = System.currentTimeMillis() - PENDING_DOWNLOAD_MAX_AGE_MS
        for (row in pending) {
            if (row.itemKind != "MOVIE" && row.itemKind != "EPISODE") {
                database.deletePendingDownload(row.itemId)
                continue
            }
            val resolved: FindroidItem? =
                try {
                    when (row.itemKind) {
                        "MOVIE" -> jellyfinRepository.getMovie(row.itemId)
                        else -> jellyfinRepository.getEpisode(row.itemId)
                    }
                } catch (e: Exception) {
                    // Transient (offline, server down) or permanent (item deleted) —
                    // we can't tell which. Keep the row and retry next launch, unless
                    // it has been failing for so long it's clearly dead.
                    Timber.w(e, "Failed to resolve pending download ${row.itemId}; keeping for retry")
                    null
                }
            when {
                resolved != null -> result.add(resolved to row.addedAt)
                row.addedAt < cutoff -> {
                    Timber.i("Dropping stale pending download ${row.itemId} (>30 days unresolvable)")
                    database.deletePendingDownload(row.itemId)
                }
                // else: keep the row; it will be retried on the next restoreAll().
            }
        }
        result
    }

    override suspend fun getActiveDownloads(): List<Pair<FindroidItem, Long>> = withContext(Dispatchers.IO) {
        val userId = jellyfinRepository.getUserId()
        val sources = database.getActiveDownloadSources()
        val result = mutableListOf<Pair<FindroidItem, Long>>()
        for (source in sources) {
            val downloadId = source.downloadId ?: continue
            val item: FindroidItem? =
                try {
                    database.getMovie(source.itemId).toFindroidMovie(database, userId)
                } catch (_: Exception) {
                    try {
                        database.getEpisode(source.itemId).toFindroidEpisode(database, userId)
                    } catch (_: Exception) {
                        null
                    }
                }
            if (item != null) result.add(item to downloadId)
        }
        result
    }

    // Maps exceptions to user-facing text. Raw exception messages may contain URLs,
    // hostnames, or stack fragments that shouldn't surface in the UI — log the full
    // exception via Timber and show a generic localized message instead.
    private fun mapDownloadError(e: Throwable): UiText =
        when (e) {
            is UnknownHostException, is ConnectException ->
                UiText.StringResource(CoreR.string.download_error_server_unreachable)
            is SocketTimeoutException ->
                UiText.StringResource(CoreR.string.download_error_timeout)
            is IOException ->
                UiText.StringResource(CoreR.string.download_error_network)
            else -> UiText.StringResource(CoreR.string.unknown_error)
        }

    override suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        val userId = jellyfinRepository.getUserId()

        // Live engine tasks: ids (to decide whether an active source is dead) and
        // destination paths (so we never delete a file the engine is still writing).
        // At startup these are empty — but partial files are protected by knownPaths
        // from the DB rows, so that is fine.
        val liveTaskIds = engine.liveTaskIds()
        val liveTaskPaths = engine.liveTaskPaths()

        // Download roots that are actually reachable right now. A source whose path
        // is NOT under one of these is on an ejected/unmounted volume — we cannot
        // tell whether its file exists, so we must not judge it.
        val mountedRoots = context.getExternalFilesDirs(null)
            .filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            .map { File(it, "downloads").absolutePath }

        val knownPaths = mutableSetOf<String>()
        fun remember(source: FindroidSourceDto) {
            knownPaths.add(source.path)
            for (stream in database.getMediaStreamsBySourceId(source.id)) {
                knownPaths.add(stream.path)
            }
        }

        for (source in database.getCompletedDownloadSources()) {
            remember(source)
            if (!isUnderMountedRoot(source.path, mountedRoots)) continue
            if (File(source.path).exists()) continue
            Timber.i("Sweeping completed source with missing file: ${source.path}")
            cleanupOrphanSource(source.itemId, userId)
        }

        for (source in database.getActiveDownloadSources()) {
            remember(source)
            if (!isUnderMountedRoot(source.path, mountedRoots)) continue
            val engineAlive = source.downloadId != null && source.downloadId in liveTaskIds
            if (!engineAlive && !File(source.path).exists()) {
                Timber.i("Sweeping dead active source: ${source.path}")
                cleanupOrphanSource(source.itemId, userId)
            }
        }

        // Untracked .download files anywhere under a mounted downloads root.
        for (root in mountedRoots) {
            val downloadsDir = File(root)
            if (!downloadsDir.isDirectory) continue
            downloadsDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".download") }
                .filter { shouldDeleteOrphanFile(it.absolutePath, knownPaths, liveTaskPaths) }
                .forEach { file ->
                    Timber.i("Deleting orphan download file: ${file.absolutePath}")
                    try {
                        file.delete()
                        pruneEmptyParents(file)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to delete orphan ${file.absolutePath}")
                    }
                }
        }
    }

    override suspend fun finalizeDownload(downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val source = database.getSourceByDownloadId(downloadId)
        if (source != null) {
            return@withContext finalizeSource(source)
        }
        val mediaStream = database.getMediaStreamByDownloadId(downloadId)
        if (mediaStream != null) {
            return@withContext finalizeMediaStream(mediaStream)
        }
        // Neither source nor mediaStream found — the engine entry may have outlived the DB row
        // (deleted item, sweep). Nothing to do.
        false
    }

    private suspend fun finalizeSource(source: FindroidSourceDto): Boolean {
        if (!source.path.endsWith(".download")) return true
        val finalPath = source.path.removeSuffix(".download")
        val downloadFile = File(source.path)
        val finalFile = File(finalPath)

        // If the rename already happened on disk (earlier finalize, manual recovery),
        // just sync the DB and exit.
        if (finalFile.exists() && finalFile.length() > 0 && !downloadFile.exists()) {
            database.setSourcePath(source.id, finalPath)
            return true
        }

        if (!isDownloadSuccessful(source.downloadId)) return false

        if (!renameDownloadFile(source.path, finalPath)) {
            Timber.e("Failed to rename download, deleting item. path=${source.path}")
            deleteSourceItem(source)
            return false
        }
        database.setSourcePath(source.id, finalPath)
        // Drop the terminal task from the engine registry after successful finalize.
        source.downloadId?.let { engine.cancel(it) }
        Timber.d("Finalized download at: $finalPath")
        return true
    }

    private fun finalizeMediaStream(mediaStream: FindroidMediaStreamDto): Boolean {
        if (!mediaStream.path.endsWith(".download")) return true
        val finalPath = mediaStream.path.removeSuffix(".download")
        val downloadFile = File(mediaStream.path)
        val finalFile = File(finalPath)

        if (finalFile.exists() && finalFile.length() > 0 && !downloadFile.exists()) {
            database.setMediaStreamPath(mediaStream.id, finalPath)
            return true
        }

        if (!isDownloadSuccessful(mediaStream.downloadId)) return false

        if (!renameDownloadFile(mediaStream.path, finalPath)) {
            Timber.e("Failed to rename media stream download. path=${mediaStream.path}")
            database.deleteMediaStream(mediaStream.id)
            return false
        }
        database.setMediaStreamPath(mediaStream.id, finalPath)
        // Drop the terminal task from the engine registry after successful finalize.
        mediaStream.downloadId?.let { engine.cancel(it) }
        return true
    }

    private fun isDownloadSuccessful(downloadId: Long?): Boolean {
        if (downloadId == null) return false
        return engine.snapshot(downloadId)?.status == DownloadStatus.SUCCESSFUL
    }

    /**
     * Renames `<fromPath>` to `<toPath>`. Falls back to copy+delete because
     * renameTo can fail across some external storage filesystems (FAT32/exFAT).
     */
    private fun renameDownloadFile(fromPath: String, toPath: String): Boolean {
        val src = File(fromPath)
        val dst = File(toPath)
        if (src.renameTo(dst)) return true
        return try {
            src.copyTo(dst, overwrite = true)
            src.delete()
            true
        } catch (e: Exception) {
            Timber.e(e, "copyTo fallback also failed: $fromPath -> $toPath")
            false
        }
    }

    private suspend fun deleteSourceItem(sourceDto: FindroidSourceDto) {
        val source = sourceDto.toFindroidSource(database)
        val userId = jellyfinRepository.getUserId()
        val item: FindroidItem? =
            try {
                database.getMovie(sourceDto.itemId).toFindroidMovie(database, userId)
            } catch (_: Exception) {
                try {
                    database.getEpisode(sourceDto.itemId).toFindroidEpisode(database, userId)
                } catch (_: Exception) {
                    null
                }
            }
        if (item == null) return
        deleteItem(item, source)
    }

    private suspend fun cleanupOrphanSource(itemId: UUID, userId: UUID) {
        val item: FindroidItem? =
            try {
                database.getMovieOrNull(itemId)?.toFindroidMovie(database, userId)
            } catch (_: Exception) {
                null
            } ?: try {
                database.getEpisodeOrNull(itemId)?.toFindroidEpisode(database, userId)
            } catch (_: Exception) {
                null
            }
        if (item == null) {
            // Item record is already gone — drop lingering source rows directly.
            for (source in database.getSources(itemId)) {
                database.deleteMediaStreamsBySourceId(source.id)
                database.deleteSource(source.id)
            }
            return
        }
        for (source in database.getSources(itemId).map { it.toFindroidSource(database) }) {
            try {
                deleteItem(item, source)
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete orphan item ${item.name}")
            }
        }
    }

    /**
     * Builds a Plex-style relative path for a download.
     *
     * Movies:  `Movie Name (2024)/Movie Name (2024).mkv`
     * Episodes: `Series Name/S01/S01E05 - Episode Name.mkv`
     */
    private fun buildDownloadPath(
        item: FindroidItem,
        sourceId: String,
        extension: String,
    ): String {
        return when (item) {
            is FindroidEpisode -> {
                val series = sanitize(item.seriesName.ifBlank { "Unknown Series" })
                val sNum = "S%02d".format(item.parentIndexNumber)
                val eNum = "E%02d".format(item.indexNumber)
                val epName = sanitize(item.name)
                "$series/$sNum/$sNum$eNum - $epName.$extension"
            }
            is FindroidMovie -> {
                val year = item.productionYear
                val title = sanitize(item.name)
                val folder = if (year != null) "$title ($year)" else title
                "$folder/$folder.$extension"
            }
            else -> "${item.id}.$sourceId.$extension"
        }
    }

    /** Strips characters that are illegal in FAT32/exFAT/NTFS filenames. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { "Unknown" }

    /** Extracts the file extension from a stream URL, falling back to "mkv". */
    private fun guessExtension(url: String): String {
        // Jellyfin URLs look like .../stream.mkv?static=true&...
        val path = url.substringBefore('?')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ext else "mkv"
    }

    private fun startImagesDownloader(item: FindroidItem) {
        val downloadImagesRequest =
            OneTimeWorkRequestBuilder<ImagesDownloaderWorker>()
                .setInputData(workDataOf(ImagesDownloaderWorker.KEY_ITEM_ID to item.id.toString()))
                .build()

        // Episode downloads trigger image fetches for episode + season + show,
        // and a whole season download would otherwise enqueue the same show worker
        // N times. Dedupe on itemId so we only hit the image endpoint once per item.
        workManager.enqueueUniqueWork(
            "image-download-${item.id}",
            ExistingWorkPolicy.KEEP,
            downloadImagesRequest,
        )
    }
}
