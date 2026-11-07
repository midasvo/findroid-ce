package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.core.presentation.downloader.SmartDownloads
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidUserDataDto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import timber.log.Timber

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    val database: ServerDatabaseDao,
    private val jellyfinApi: JellyfinApi,
    private val smartDownloads: SmartDownloads,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val servers = database.getServers()
            var hadFailures = false

            for (server in servers) {
                val serverWithAddressesAndUsers =
                    database.getServerWithAddressesAndUsers(server.id) ?: continue
                val serverAddress =
                    serverWithAddressesAndUsers.addresses.firstOrNull {
                        it.id == server.currentServerAddressId
                    } ?: continue
                for (user in serverWithAddressesAndUsers.users) {
                    // Only the rows still marked to be synced: most runs have none, and then no
                    // item is loaded, no client is built and no request is made (#250).
                    val pending = database.getAllUserDataToBeSynced(server.id, user.id)
                    if (pending.isEmpty()) continue

                    val api = apiFor(serverAddress.address, user.accessToken, user.id)
                    if (!syncUserData(api, user.id, pending)) hadFailures = true
                }
            }

            if (hadFailures) {
                Result.retry()
            } else {
                // Progress watched offline has reached the server, so what to remove can be told.
                // Without completed downloads this is a single query and no requests.
                try {
                    smartDownloads.checkAll()
                } catch (e: Exception) {
                    Timber.e(e, "Smart Downloads check after sync failed")
                }
                Result.success()
            }
        }
    }

    /**
     * The app's own client when it is signed in as [userId] at [baseUrl], otherwise a client for
     * that user from the same Jellyfin instance. The app's client is never pointed at another user,
     * because the screens use it at the same time.
     */
    private fun apiFor(baseUrl: String, accessToken: String?, userId: UUID): ApiClient {
        val current = jellyfinApi.api
        if (
            jellyfinApi.userId == userId &&
                current.baseUrl == baseUrl &&
                current.accessToken == accessToken
        ) {
            return current
        }
        return jellyfinApi.jellyfin.createApi(
            baseUrl = baseUrl,
            accessToken = accessToken,
            httpClientOptions = current.httpClientOptions,
        )
    }

    private suspend fun syncUserData(
        api: ApiClient,
        userId: UUID,
        items: List<FindroidUserDataDto>,
    ): Boolean {
        var allOk = true
        for (userData in items) {
            try {
                api.userDataApi.updateItemUserData(
                    itemId = userData.itemId,
                    userId = userId,
                    data =
                        UpdateUserItemDataDto(
                            playbackPositionTicks = userData.playbackPositionTicks,
                            isFavorite = userData.favorite,
                            played = userData.played,
                        ),
                )

                database.setUserDataToBeSynced(userId, userData.itemId, false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync user data")
                allOk = false
            }
        }
        return allOk
    }
}
