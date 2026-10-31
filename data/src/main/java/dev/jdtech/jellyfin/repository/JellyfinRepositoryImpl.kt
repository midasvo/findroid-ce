package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.models.toFindroidCollection
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidItem
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidPerson
import dev.jdtech.jellyfin.models.toFindroidSeason
import dev.jdtech.jellyfin.models.toFindroidSegment
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.player.DeviceProfileBuilder
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder as ItemSortOrder
import org.jellyfin.sdk.model.api.UserConfiguration
import timber.log.Timber

class JellyfinRepositoryImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val deviceProfileBuilder: DeviceProfileBuilder,
) : JellyfinRepository {
    private val currentUserId: UUID
        get() = jellyfinApi.userId
            ?: throw IllegalStateException("No authenticated user. userId is null — session may have expired.")
    override suspend fun getPublicSystemInfo(): PublicSystemInfo =
        withContext(Dispatchers.IO) { jellyfinApi.systemApi.getPublicSystemInfo().content }

    override suspend fun getUserViews(): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userViewApi.getUserViews(currentUserId).content.items
        }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, currentUserId)
                .content
                .toFindroidEpisode(this@JellyfinRepositoryImpl, database)!!
        }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, currentUserId)
                .content
                .toFindroidMovie(this@JellyfinRepositoryImpl, database)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, currentUserId)
                .content
                .toFindroidShow(this@JellyfinRepositoryImpl)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, currentUserId)
                .content
                .toFindroidSeason(this@JellyfinRepositoryImpl)
        }

    override suspend fun getLibraries(): List<FindroidCollection> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi.getItems(currentUserId).content.items.mapNotNull {
                it.toFindroidCollection(this@JellyfinRepositoryImpl)
            }
        }

    override suspend fun getItem(itemId: UUID): FindroidItem? =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId = itemId, userId = currentUserId)
                .content
                .toFindroidItem(this@JellyfinRepositoryImpl)
        }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        filters: List<ItemFilter>?,
        startIndex: Int?,
        limit: Int?,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    currentUserId,
                    parentId = parentId,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                    sortBy = listOf(ItemSortBy.fromName(sortBy.sortString)),
                    sortOrder = listOf(ItemSortOrder.fromName(sortOrder.sortString)),
                    filters = filters,
                    startIndex = startIndex,
                    limit = limit,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        filters: List<ItemFilter>?,
    ): Flow<PagingData<FindroidItem>> {
        return Pager(
                config = PagingConfig(pageSize = 10, enablePlaceholders = false),
                pagingSourceFactory = {
                    ItemsPagingSource(this, parentId, includeTypes, recursive, sortBy, sortOrder, filters)
                },
            )
            .flow
    }

    override suspend fun getPerson(personId: UUID): FindroidPerson =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(personId, currentUserId)
                .content
                .toFindroidPerson(this@JellyfinRepositoryImpl)
        }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    currentUserId,
                    personIds = personIds,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getFavoriteItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    currentUserId,
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    includeItemTypes =
                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSearchItems(query: String): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    currentUserId,
                    searchTerm = query,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSuggestions(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.suggestionApi
                .getSuggestions(
                    currentUserId,
                    limit = 6,
                    type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getResumeItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getResumeItems(
                    currentUserId,
                    limit = 12,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                    imageTypeLimit = 1,
                    enableImageTypes = ROW_IMAGE_TYPES,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getLatestMedia(
                    currentUserId,
                    parentId = parentId,
                    limit = 16,
                    imageTypeLimit = 1,
                    enableImageTypes = ROW_IMAGE_TYPES,
                )
                .content
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showApi.getSeasons(seriesId, currentUserId).content.items.map {
                    it.toFindroidSeason(this@JellyfinRepositoryImpl)
                }
            } else {
                database.getSeasonsByShowId(seriesId).map {
                    it.toFindroidSeason(database, currentUserId)
                }
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showApi
                .getNextUp(
                    currentUserId,
                    limit = 24,
                    seriesId = seriesId,
                    imageTypeLimit = 1,
                    enableImageTypes = ROW_IMAGE_TYPES,
                    enableTotalRecordCount = false,
                    enableResumable = false,
                )
                .content
                .items
                .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showApi
                    .getEpisodes(
                        seriesId,
                        currentUserId,
                        seasonId = seasonId,
                        fields = fields,
                        startItemId = startItemId,
                        limit = limit,
                    )
                    .content
                    .items
                    .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl, database) }
            } else {
                database.getEpisodesBySeasonId(seasonId).map {
                    it.toFindroidEpisode(database, currentUserId)
                }
            }
        }

    override suspend fun getMediaSources(
        itemId: UUID,
        includePath: Boolean,
        transcodeDolbyVision: Boolean,
        maxBitrate: Int?,
    ): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            // The downloader's path. The profile direct-plays everything (→ original
            // file) unless transcodeDolbyVision is set (Dolby Vision routed through a
            // progressive H.264 transcode) or the user picked a download quality cap
            // (files over the cap transcoded down to it). Everything else stays the
            // original file.
            val sources = mutableListOf<FindroidSource>()
            sources.addAll(
                jellyfinApi.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = currentUserId,
                            deviceProfile =
                                deviceProfileBuilder.getDownloadProfile(
                                    transcodeDolbyVision,
                                    bitrateCapped = maxBitrate != null,
                                ),
                            maxStreamingBitrate = maxBitrate ?: 1_000_000_000,
                            enableTranscoding = transcodeDolbyVision || maxBitrate != null,
                            // Force a real video re-encode when a transcode happens —
                            // never let the server copy the DV bitstream into the
                            // container, and never let a stream copy defeat the cap.
                            allowVideoStreamCopy = false,
                            allowAudioStreamCopy = true,
                        ),
                    )
                    .content
                    .mediaSources
                    .map { it.toFindroidSource(this@JellyfinRepositoryImpl, itemId, includePath) }
            )
            sources.addAll(database.getSources(itemId).map { it.toFindroidSource(database) })
            sources
        }

    override suspend fun getPlaybackSources(itemId: UUID): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            // mpv software-decodes practically everything, so keep it on the permissive
            // direct-play profile. ExoPlayer gets the honest, hardware-probed profile so
            // the server transcodes anything it cannot direct-play, such as Dolby Vision
            // without a DV decoder.
            val useDirectPlay = appPreferences.getValue(appPreferences.playerBackend) == "mpv"
            // User-selected bitrate cap (player quality selector). 0 = original quality.
            // Files above the cap get a server-side transcode; files below it still
            // direct-play. mpv's plain profile has no transcoding profiles, so a cap
            // switches it to the variant that carries the HLS fallback.
            val maxBitrate =
                appPreferences.getValue(appPreferences.playerMaxBitrate).takeIf { it > 0 }
            val deviceProfile =
                when {
                    useDirectPlay && maxBitrate != null ->
                        deviceProfileBuilder.getDirectPlayTranscodeFallbackProfile()
                    useDirectPlay -> deviceProfileBuilder.getDirectPlayProfile()
                    else ->
                        deviceProfileBuilder.getDeviceProfile(
                            appPreferences.getValue(appPreferences.playerDirectPlayDolbyVision),
                        )
                }
            val enableTranscoding = !useDirectPlay || maxBitrate != null
            val sources = mutableListOf<FindroidSource>()
            sources.addAll(
                jellyfinApi.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = currentUserId,
                            deviceProfile = deviceProfile,
                            maxStreamingBitrate = maxBitrate
                                ?: deviceProfile.maxStreamingBitrate,
                            enableTranscoding = enableTranscoding,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                            autoOpenLiveStream = enableTranscoding,
                        ),
                    )
                    .content
                    .mediaSources
                    .map {
                        it.toFindroidSource(this@JellyfinRepositoryImpl, itemId, includePath = true)
                    }
            )
            sources.addAll(database.getSources(itemId).map { it.toFindroidSource(database) })
            sources
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videoApi.getVideoStreamUrl(
                    itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                )
            } catch (e: Exception) {
                Timber.e(e)
                ""
            }
        }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) {
            val databaseSegments = database.getSegments(itemId).map { it.toFindroidSegment() }

            if (databaseSegments.isNotEmpty()) {
                return@withContext databaseSegments
            }

            try {
                val apiSegments =
                    jellyfinApi.mediaSegmentApi.getItemSegments(itemId).content.items.map {
                        it.toFindroidSegment()
                    }

                return@withContext apiSegments
            } catch (e: Exception) {
                Timber.e(e)
                return@withContext emptyList()
            }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                try {
                    val sources = File(context.filesDir, "trickplay/$itemId").listFiles()
                    if (sources != null) {
                        return@withContext File(sources.first(), index.toString()).readBytes()
                    }
                } catch (e: Exception) { Timber.e(e, "Failed to read local trickplay data") }

                return@withContext jellyfinApi.trickPlayApi
                    .getTrickplayTileImage(itemId, width, index)
                    .content
            } catch (_: Exception) {
                return@withContext null
            }
        }

    override suspend fun postCapabilities() {
        Timber.d("Sending capabilities")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands =
                    listOf(
                        GeneralCommandType.VOLUME_UP,
                        GeneralCommandType.VOLUME_DOWN,
                        GeneralCommandType.TOGGLE_MUTE,
                        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                        GeneralCommandType.MUTE,
                        GeneralCommandType.UNMUTE,
                        GeneralCommandType.SET_VOLUME,
                        GeneralCommandType.DISPLAY_MESSAGE,
                        GeneralCommandType.PLAY,
                        GeneralCommandType.PLAY_STATE,
                        GeneralCommandType.PLAY_NEXT,
                        GeneralCommandType.PLAY_MEDIA_SOURCE,
                    ),
                supportsMediaControl = true,
            )
        }
    }

    override suspend fun postPlaybackStart(itemId: UUID) {
        Timber.d("Sending start $itemId")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = itemId,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                )
            )
        }
    }

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        Timber.d("Sending stop $itemId")
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, currentUserId, 0)
                    database.setPlayed(currentUserId, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, currentUserId, 0)
                    database.setPlayed(currentUserId, itemId, true)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, currentUserId, positionTicks)
                    database.setPlayed(currentUserId, itemId, false)
                }
            }
            try {
                jellyfinApi.sessionApi.reportPlaybackStopped(
                    PlaybackStopInfo(itemId = itemId, positionTicks = positionTicks, failed = false)
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(currentUserId, itemId, true)
            }
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        Timber.d("Posting progress of $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, currentUserId, positionTicks)
            try {
                jellyfinApi.sessionApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = itemId,
                        canSeek = true,
                        isPaused = isPaused,
                        isMuted = false,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        positionTicks = positionTicks,
                    )
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(currentUserId, itemId, true)
            }
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(currentUserId, itemId, true)
            try {
                jellyfinApi.userDataApi.markFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(currentUserId, itemId, true)
            }
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(currentUserId, itemId, false)
            try {
                jellyfinApi.userDataApi.unmarkFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(currentUserId, itemId, true)
            }
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(currentUserId, itemId, true)
            try {
                jellyfinApi.userDataApi.markPlayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(currentUserId, itemId, true)
            }
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(currentUserId, itemId, false)
            try {
                jellyfinApi.userDataApi.markUnplayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(currentUserId, itemId, true)
            }
        }
    }

    override fun getBaseUrl() = jellyfinApi.api.baseUrl.orEmpty()

    override suspend fun updateDeviceName(name: String) {
        withContext(Dispatchers.IO) {
            jellyfinApi.jellyfin.deviceInfo?.id?.let { id ->
                jellyfinApi.deviceApi.updateDeviceOptions(
                    id,
                    DeviceOptionsDto(0, customName = name),
                )
            }
        }
    }

    override suspend fun getUserConfiguration(): UserConfiguration =
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser().content.configuration!! }

    override suspend fun getDownloads(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            val serverId = appPreferences.getValue(appPreferences.currentServer)
                ?: return@withContext emptyList()
            val items = mutableListOf<FindroidItem>()
            items.addAll(
                database
                    .getMoviesByServerId(serverId)
                    .map { it.toFindroidMovie(database, currentUserId) }
            )
            items.addAll(
                database
                    .getShowsByServerId(serverId)
                    .map { it.toFindroidShow(database, currentUserId) }
            )
            items
        }

    override fun getUserId(): UUID {
        return currentUserId
    }

    private companion object {
        /**
         * The only images row cards (home rows, next up) render: `FindroidImages` reads the
         * primary tag, the first backdrop and the series primary tag. Together with
         * `imageTypeLimit = 1` this stops the server from resolving (and sending blurhashes
         * for) every backdrop, logo, thumb and banner of each item.
         */
        val ROW_IMAGE_TYPES = listOf(ImageType.PRIMARY, ImageType.BACKDROP)
    }
}
