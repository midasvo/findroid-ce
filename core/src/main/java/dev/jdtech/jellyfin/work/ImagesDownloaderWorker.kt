package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@HiltWorker
class ImagesDownloaderWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: JellyfinRepository,
) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient()
    override suspend fun doWork(): Result {
        val itemId = UUID.fromString(params.inputData.getString(KEY_ITEM_ID))
        return downloadImages(itemId = itemId)
    }

    private suspend fun downloadImages(itemId: UUID): Result =
        withContext(Dispatchers.IO) {
            val item = repository.getItem(itemId) ?: return@withContext Result.success()

            val basePath = "images/${item.id}"
            val baseDir = File(appContext.filesDir, basePath)

            val uris =
                listOfNotNull(
                    item.images.primary?.let { "primary" to it },
                    item.images.backdrop?.let { "backdrop" to it },
                )
            if (uris.isEmpty()) return@withContext Result.success()

            // Skip only if every expected file already exists and is non-empty.
            // A bare directory from a previous failed run would otherwise mask
            // the missing images forever.
            val allPresent = uris.all { (name, _) ->
                val file = File(baseDir, name)
                file.exists() && file.length() > 0
            }
            if (allPresent) return@withContext Result.success()

            try {
                baseDir.mkdirs()
            } catch (e: IOException) {
                Timber.e(e, "Failed to create image dir $basePath")
                return@withContext Result.retry()
            }

            var transientFailure = false
            for ((name, uri) in uris) {
                val file = File(baseDir, name)
                if (file.exists() && file.length() > 0) continue

                val request = Request.Builder().url(uri.toString()).build()
                val imageBytes =
                    try {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Timber.e("Failed to download image $uri: ${response.code}")
                                // 5xx / 408 are transient; 4xx are not.
                                if (response.code >= 500 || response.code == 408) {
                                    transientFailure = true
                                }
                                return@use null
                            }
                            response.body.bytes()
                        }
                    } catch (e: IOException) {
                        Timber.e(e, "IO error downloading $uri")
                        transientFailure = true
                        null
                    }

                if (imageBytes == null || imageBytes.isEmpty()) continue
                try {
                    file.writeBytes(imageBytes)
                } catch (e: IOException) {
                    Timber.e(e, "Failed to write image $file")
                    transientFailure = true
                }
            }

            if (transientFailure) Result.retry() else Result.success()
        }

    companion object {
        const val KEY_ITEM_ID = "KEY_ITEM_ID"
    }
}
