package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.utils.Downloader
import timber.log.Timber

@HiltWorker
class OrphanSweepWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloader: Downloader,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            downloader.sweepOrphans()
            Timber.d("Orphan sweep completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Orphan sweep failed")
            Result.retry()
        }
    }
}
