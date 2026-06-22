package dev.jdtech.jellyfin.utils.download

import dev.jdtech.jellyfin.di.DownloadHttpClient
import dev.jdtech.jellyfin.utils.NetworkConnectivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// Pure helpers (file-top-level so they are unit-testable) ----------------------

/**
 * Decides whether a transfer should be paused given the current network state and the
 * user's metered/roaming allowances. Offline always pauses; metered/roaming pause when
 * the corresponding allowance flag is false.
 */
internal fun shouldPauseTransfer(
    isOnline: Boolean,
    isMetered: Boolean,
    isRoaming: Boolean,
    allowMetered: Boolean,
    allowRoaming: Boolean,
): Boolean = when {
    !isOnline -> true
    isMetered && !allowMetered -> true
    isRoaming && !allowRoaming -> true
    else -> false
}

/** Plan returned by [resolveResumePlan] describing where to seek and what the total size is. */
internal data class ResumePlan(
    val startByte: Long,
    val totalBytes: Long,
    /** True when the file is already complete; the caller should treat it as SUCCESSFUL. */
    val complete: Boolean,
)

/**
 * Maps an HTTP response code to a resume plan.
 *
 * - 206 Partial Content: resume at [existingLength]; total derived from Content-Range header or
 *   as existingLength+contentLength.
 * - 200 OK: server ignored the Range header (e.g. transcode/DV case) - truncate and restart.
 * - 416 Range Not Satisfiable: file is already complete.
 * - Other codes: caller is expected to throw before calling; this is a defensive path.
 */
internal fun resolveResumePlan(
    code: Int,
    existingLength: Long,
    contentLength: Long,
    contentRangeTotal: Long,
): ResumePlan = when (code) {
    206 -> {
        val total = if (contentRangeTotal > 0) {
            contentRangeTotal
        } else if (contentLength >= 0) {
            existingLength + contentLength
        } else {
            -1L
        }
        ResumePlan(startByte = existingLength, totalBytes = total, complete = false)
    }
    200 -> ResumePlan(startByte = 0L, totalBytes = contentLength, complete = false)
    416 -> ResumePlan(startByte = existingLength, totalBytes = existingLength, complete = true)
    else -> ResumePlan(startByte = 0L, totalBytes = contentLength, complete = false)
}

/**
 * Parses the numeric total from a Content-Range response header.
 *
 * - "bytes 200-1023/1024" returns 1024
 * - wildcard total ("bytes 0-0/&#42;") or null or malformed returns -1
 */
internal fun parseContentRangeTotal(header: String?): Long {
    if (header == null) return -1L
    // Format: bytes <start>-<end>/<total>  or  bytes <start>-<end>/asterisk
    val slashIndex = header.lastIndexOf('/')
    if (slashIndex < 0) return -1L
    val totalStr = header.substring(slashIndex + 1).trim()
    if (totalStr == "*") return -1L
    return totalStr.toLongOrNull() ?: -1L
}

// Private exception used for mid-transfer pause ---------------------------------

private class PausedMidTransfer : Exception()

// Engine ------------------------------------------------------------------------

/**
 * Singleton OkHttp-backed download engine. Manages a registry of in-flight (and terminal)
 * download tasks keyed by Long id. Each task runs in the engine's own [CoroutineScope].
 *
 * Thread-safety: the registry is a [ConcurrentHashMap]. Individual task state fields are
 * @Volatile and written only by the task's own coroutine. Reads from outside are
 * eventually-consistent snapshots, acceptable for progress polling.
 */
@Singleton
class MediaDownloadEngine @Inject constructor(
    @DownloadHttpClient private val client: OkHttpClient,
    private val connectivity: NetworkConnectivity,
) {
    // Public API types ---------------------------------------------------------

    data class Snapshot(val status: Int, val bytesDownloaded: Long, val totalBytes: Long)

    data class Request(
        val id: Long,
        val url: String,
        val destFile: File,
        val allowMetered: Boolean,
        val allowRoaming: Boolean,
    )

    // Engine internals ---------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Per-task mutable state held in the registry. */
    private inner class TaskState(val request: Request) {
        @Volatile var status: Int = DownloadStatus.PENDING
        @Volatile var bytesDownloaded: Long = 0L
        @Volatile var totalBytes: Long = -1L
        var job: Job? = null

        /**
         * The in-flight OkHttp call. Held so [cancel] can interrupt the blocking
         * `source.read()` immediately — `job.cancel()` alone cannot, since the transfer
         * loop has no suspension points while reading. Without this a cancelled download
         * keeps writing for up to the read timeout, racing the file delete in deleteItem.
         */
        @Volatile var call: okhttp3.Call? = null
    }

    private val registry = ConcurrentHashMap<Long, TaskState>()

    // Public methods -----------------------------------------------------------

    /**
     * Start or resume the transfer for [request.id].
     *
     * Idempotent while the task is ACTIVE (PENDING / RUNNING / PAUSED). If the existing
     * task is TERMINAL (SUCCESSFUL / FAILED) or absent, (re)starts, picking up from the
     * partial file on disk.
     */
    @Synchronized
    fun start(request: Request) {
        val existing = registry[request.id]
        if (existing != null) {
            val status = existing.status
            if (status == DownloadStatus.PENDING ||
                status == DownloadStatus.RUNNING ||
                status == DownloadStatus.PAUSED
            ) {
                // Already active -- idempotent no-op.
                return
            }
            // Terminal: cancel/clear old call + job before replacing.
            existing.call?.cancel()
            existing.job?.cancel()
        }

        val state = TaskState(request)
        registry[request.id] = state
        state.job = scope.launch {
            runTask(state)
        }
    }

    /**
     * Abort the in-flight transfer for [id] and remove it from the registry.
     * Does NOT delete the partial file on disk. Safe to call on an unknown or terminal id.
     */
    fun cancel(id: Long) {
        val state = registry.remove(id)
        // Cancel the OkHttp call first so a blocking read unblocks immediately, then the job.
        state?.call?.cancel()
        state?.job?.cancel()
    }

    /** Returns a point-in-time [Snapshot] for [id], or null if the engine has no record. */
    fun snapshot(id: Long): Snapshot? {
        val state = registry[id] ?: return null
        return Snapshot(
            status = state.status,
            bytesDownloaded = state.bytesDownloaded,
            totalBytes = state.totalBytes,
        )
    }

    /** Returns snapshots for all [ids] that the engine has records for. Missing ids are absent. */
    fun snapshots(ids: List<Long>): Map<Long, Snapshot> {
        return ids.mapNotNull { id ->
            snapshot(id)?.let { id to it }
        }.toMap()
    }

    /** Returns the ids of all tasks currently in the registry (active and terminal). */
    fun liveTaskIds(): Set<Long> = registry.keys.toSet()

    /**
     * Returns the absolute paths of all destination files for tasks currently in the
     * registry. Used as a race-guard in orphan sweeps: any path in this set must not
     * be deleted even if it is not in the DB yet.
     */
    fun liveTaskPaths(): Set<String> =
        registry.values.map { it.request.destFile.absolutePath }.toSet()

    // Transfer loop ------------------------------------------------------------

    private suspend fun runTask(state: TaskState) {
        state.status = DownloadStatus.PENDING
        val req = state.request

        while (true) {
            if (shouldPauseTransfer(
                    isOnline = connectivity.isOnline(),
                    isMetered = connectivity.isMetered(),
                    isRoaming = connectivity.isRoaming(),
                    allowMetered = req.allowMetered,
                    allowRoaming = req.allowRoaming,
                )
            ) {
                state.status = DownloadStatus.PAUSED
                delay(2_000)
                continue
            }

            state.status = DownloadStatus.RUNNING
            try {
                httpTransfer(state)
                state.status = DownloadStatus.SUCCESSFUL
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: PausedMidTransfer) {
                // Network became metered/roaming mid-transfer; loop back to top to re-evaluate.
                continue
            } catch (e: IOException) {
                Timber.w(e, "Download failed for id=${req.id}; partial file kept for resume")
                state.status = DownloadStatus.FAILED
                return
            } catch (e: Exception) {
                Timber.e(e, "Unexpected download error for id=${req.id}")
                state.status = DownloadStatus.FAILED
                return
            }
        }
    }

    /**
     * Executes the actual HTTP byte transfer for [state]. Throws [PausedMidTransfer] if the
     * network becomes restricted mid-stream (caller loops back). Throws [IOException] on HTTP
     * error or I/O failure. On exception the partial file is left on disk for future resume.
     */
    @Throws(IOException::class, PausedMidTransfer::class)
    private fun httpTransfer(state: TaskState) {
        val req = state.request
        val existingLen = if (req.destFile.exists()) req.destFile.length() else 0L

        val httpRequest = okhttp3.Request.Builder()
            .url(req.url)
            .apply {
                if (existingLen > 0L) {
                    header("Range", "bytes=$existingLen-")
                }
            }
            .build()

        val call = client.newCall(httpRequest)
        state.call = call
        val response = call.execute()
        try {
            val code = response.code
            val contentRangeTotal = parseContentRangeTotal(response.header("Content-Range"))
            val body = response.body
            val contentLength = body.contentLength()

            when (code) {
                200, 206, 416 -> Unit // handled below
                else -> throw IOException("HTTP $code for download id=${req.id}")
            }

            val plan = resolveResumePlan(code, existingLen, contentLength, contentRangeTotal)

            if (plan.complete) {
                // 416: file already complete.
                state.bytesDownloaded = plan.startByte
                state.totalBytes = plan.totalBytes
                return
            }

            // For a 200 response (server ignored Range), truncate the file before writing.
            if (plan.startByte == 0L && req.destFile.exists()) {
                RandomAccessFile(req.destFile, "rw").use { it.setLength(0L) }
            }

            state.totalBytes = plan.totalBytes

            val source = body.source()
            val raf = RandomAccessFile(req.destFile, "rw")
            try {
                raf.seek(plan.startByte)
                state.bytesDownloaded = plan.startByte

                val buf = ByteArray(64 * 1024) // 64 KiB chunks
                var bytesSinceLastMeteredCheck = 0L
                val meteredCheckInterval = 1L * 1024 * 1024 // 1 MiB

                while (true) {
                    val n = source.read(buf)
                    if (n == -1) break

                    raf.write(buf, 0, n)
                    state.bytesDownloaded += n
                    bytesSinceLastMeteredCheck += n

                    if (bytesSinceLastMeteredCheck >= meteredCheckInterval) {
                        bytesSinceLastMeteredCheck = 0L
                        if (shouldPauseTransfer(
                                isOnline = connectivity.isOnline(),
                                isMetered = connectivity.isMetered(),
                                isRoaming = connectivity.isRoaming(),
                                allowMetered = req.allowMetered,
                                allowRoaming = req.allowRoaming,
                            )
                        ) {
                            // Keep partial; outer loop will re-evaluate and set PAUSED.
                            throw PausedMidTransfer()
                        }
                    }
                }
                // EOF reached -- success; caller sets SUCCESSFUL.
            } finally {
                raf.close()
            }
        } finally {
            response.close()
        }
    }
}
