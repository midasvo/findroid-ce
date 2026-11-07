package dev.jdtech.jellyfin.repository

import androidx.paging.PagingData
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

interface JellyfinRepository {
    suspend fun getPublicSystemInfo(): PublicSystemInfo

    suspend fun getUserViews(): List<BaseItemDto>

    /** Stores the current user's profile picture tag, as the server has it now. */
    suspend fun refreshCurrentUserImage()

    suspend fun getEpisode(itemId: UUID): FindroidEpisode

    suspend fun getMovie(itemId: UUID): FindroidMovie

    suspend fun getShow(itemId: UUID): FindroidShow

    suspend fun getSeason(itemId: UUID): FindroidSeason

    suspend fun getLibraries(): List<FindroidCollection>

    suspend fun getItem(itemId: UUID): FindroidItem?

    suspend fun getItems(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: List<ItemFilter>? = null,
        startIndex: Int? = null,
        limit: Int? = null,
    ): List<FindroidItem>

    /**
     * CE (#140): up to [limit] items of [includeTypes] from all libraries, in random order, that
     * have a backdrop. Online the server picks them; the default (offline) shuffles local items.
     */
    suspend fun getRandomItemsWithBackdrop(
        includeTypes: List<BaseItemKind>,
        limit: Int,
    ): List<FindroidItem> =
        getItems(includeTypes = includeTypes, recursive = true)
            .filter { it.images.backdrop != null }
            .shuffled()
            .take(limit)

    suspend fun getItemsPaging(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: List<ItemFilter>? = null,
    ): Flow<PagingData<FindroidItem>>

    /**
     * CE: the number of items that [getItems] would return, without fetching them. With
     * [nameLessThan], only those whose sort name comes before it.
     */
    suspend fun getItemCount(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        filters: List<ItemFilter>? = null,
        nameLessThan: String? = null,
    ): Int

    suspend fun getPerson(personId: UUID): FindroidPerson

    suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = true,
        limit: Int? = null,
    ): List<FindroidItem>

    suspend fun getFavoriteItems(): List<FindroidItem>

    /**
     * Searches movies and shows. With [itemKinds], it searches those kinds instead, with a separate
     * limit per kind so one kind (episodes) can't push the others out (CE, TV search). The offline
     * repository ignores [itemKinds].
     */
    suspend fun getSearchItems(
        query: String,
        itemKinds: List<BaseItemKind>? = null,
    ): List<FindroidItem>

    suspend fun getSuggestions(): List<FindroidItem>

    suspend fun getResumeItems(): List<FindroidItem>

    suspend fun getLatestMedia(parentId: UUID): List<FindroidItem>

    suspend fun getSeasons(seriesId: UUID, offline: Boolean = false): List<FindroidSeason>

    suspend fun getNextUp(seriesId: UUID? = null): List<FindroidEpisode>

    /** The episodes of one season, or with a null [seasonId] (CE, #252) of the whole series. */
    suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID?,
        fields: List<ItemFields>? = null,
        startItemId: UUID? = null,
        limit: Int? = null,
        offline: Boolean = false,
    ): List<FindroidEpisode>

    /**
     * Resolves the sources for downloading an item. Always asks for the original
     * file, except — when [transcodeDolbyVision] is set — Dolby Vision files, which
     * the server then transcodes to a device-compatible H.264 copy so they play
     * offline. Every non-DV file is still the original regardless of the flag.
     *
     * [maxBitrate] (bits/sec) is the user-selected download quality cap. Files
     * whose bitrate exceeds it get a progressive server-side transcode down to
     * the cap; files under it still download as the original. Null = original
     * quality for everything.
     */
    suspend fun getMediaSources(
        itemId: UUID,
        includePath: Boolean = false,
        transcodeDolbyVision: Boolean = false,
        maxBitrate: Int? = null,
    ): List<FindroidSource>

    /**
     * Resolves the sources to play an item. Unlike [getMediaSources] (which always
     * asks for the original file and is shared with the downloader), this sends an
     * honest device profile so the server transcodes anything the player cannot
     * direct-play — notably Dolby Vision on hardware without a working DV pipeline.
     *
     * When a downloaded copy exists and [mediaSourceId] is null or names it, the server is not
     * asked: the player plays the local copy then anyway, and waiting on the server only delays it.
     */
    suspend fun getPlaybackSources(
        itemId: UUID,
        mediaSourceId: String? = null,
    ): List<FindroidSource>

    suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String

    suspend fun getSegments(itemId: UUID): List<FindroidSegment>

    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?

    /**
     * CE (#141): a stream URL for the first theme song of [itemId] (a series), or null when the
     * server has none. Offline there is no theme music, so the default is null.
     */
    suspend fun getThemeSongUrl(itemId: UUID): String? = null

    suspend fun postCapabilities()

    suspend fun postPlaybackStart(itemId: UUID)

    suspend fun postPlaybackStop(itemId: UUID, positionTicks: Long, playedPercentage: Int)

    suspend fun postPlaybackProgress(itemId: UUID, positionTicks: Long, isPaused: Boolean)

    suspend fun markAsFavorite(itemId: UUID)

    suspend fun unmarkAsFavorite(itemId: UUID)

    suspend fun markAsPlayed(itemId: UUID)

    suspend fun markAsUnplayed(itemId: UUID)

    fun getBaseUrl(): String

    suspend fun updateDeviceName(name: String)

    suspend fun getUserConfiguration(): UserConfiguration?

    suspend fun getDownloads(): List<FindroidItem>

    fun getUserId(): UUID
}
