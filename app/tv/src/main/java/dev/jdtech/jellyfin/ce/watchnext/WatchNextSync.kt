package dev.jdtech.jellyfin.ce.watchnext

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.PlaybackEvents
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Keeps the launcher's Watch Next row (#138) in step with the server's Continue Watching and Next
 * Up. The work itself runs in [WatchNextSyncWorker]; this class decides when.
 *
 * A sync runs when the app starts, when the player has reported a stop, when the user leaves the
 * app (which also covers a changed setting or user), and every [PERIODIC_HOURS] for what was
 * watched on other devices. Each sync is two small requests, and only when there is a network.
 */
@Singleton
class WatchNextSync
@Inject
constructor(
    private val workManager: WorkManager,
    private val playbackEvents: PlaybackEvents,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Schedules the periodic sync and a sync now, and starts syncing after playback. */
    fun start() {
        if (started) return
        started = true
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchNextSyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build(),
        )
        requestSync()
        scope.launch { playbackEvents.stopped.collect { requestSync() } }
    }

    /** Runs a sync once there is a network, after the one that is running, if any. */
    fun requestSync() {
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WatchNextSyncWorker>().setConstraints(networkConstraints).build(),
        )
    }

    private companion object {
        const val WORK_NAME = "watch_next_sync"
        const val PERIODIC_WORK_NAME = "watch_next_sync_periodic"
        const val PERIODIC_HOURS = 3L
        val networkConstraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}

@HiltWorker
class WatchNextSyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val jellyfinApi: JellyfinApi,
    private val repositoryProvider: Provider<JellyfinRepository>,
    private val publisher: WatchNextPublisher,
    private val store: WatchNextStore,
) : CoroutineWorker(context, workerParams) {

    // The periodic and the one-off sync are separate unique works, so they can run at once.
    override suspend fun doWork(): Result = syncLock.withLock { sync() }

    private suspend fun sync(): Result {
        val userId = jellyfinApi.userId?.toString()
        if (!appPreferences.getValue(appPreferences.tvWatchNext) || userId == null) {
            publisher.clear()
            store.clear()
            return Result.success()
        }
        if (store.ownerUserId != userId) {
            // Another user's items must not stay on the launcher.
            publisher.clear()
            store.clear()
            store.ownerUserId = userId
        }
        val published = publisher.readPublished() ?: return Result.success()

        val candidates =
            try {
                val repository = repositoryProvider.get()
                watchNextCandidates(resume = repository.getResumeItems(), nextUp = repository.getNextUp())
            } catch (e: Exception) {
                Timber.w(e, "Watch Next: could not load the items")
                return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            }

        val dismissedBefore = store.dismissed
        val plan =
            planWatchNext(
                candidates = candidates,
                published = published,
                dismissed = dismissedBefore,
                nowMs = System.currentTimeMillis(),
            )
        Timber.d(
            "Watch Next: ${plan.inserts.size} new, ${plan.updates.size} updated, ${plan.deletes.size} removed"
        )
        store.programs = publisher.apply(plan, published, userId)
        store.updateDismissed(before = dismissedBefore, after = plan.dismissed)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
        val syncLock = Mutex()
    }
}
