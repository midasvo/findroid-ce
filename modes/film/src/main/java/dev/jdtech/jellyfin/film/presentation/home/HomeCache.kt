package dev.jdtech.jellyfin.film.presentation.home

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.View
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * The last Home rows per server and user, in a JSON file (#251), so a cold start shows them at once
 * and the network only refreshes them. Only what the cards draw is kept: movies, shows and
 * episodes with their ids, names, image links, progress and numbers. Sources, people and the like
 * come with the refresh.
 */
@Singleton
class HomeCache @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "home-cache")

    private fun file(serverId: String, userId: UUID) = File(dir, "${serverId}_$userId.json")

    /** What Home needs to show the rows: the sections, the library rows and their order. */
    class Rows(
        val suggestions: HomeItem.Suggestions?,
        val resume: HomeItem.Section?,
        val nextUp: HomeItem.Section?,
        val views: List<HomeItem.ViewItem>,
        val rowOrder: List<String>,
    )

    suspend fun read(serverId: String, userId: UUID): Rows? =
        withContext(Dispatchers.IO) {
            try {
                val file = file(serverId, userId)
                if (!file.exists()) return@withContext null
                val json = JSONObject(file.readText())
                if (json.optInt("version") != VERSION) return@withContext null
                Rows(
                    suggestions =
                        json.optJSONObject("suggestions")?.let { obj ->
                            HomeItem.Suggestions(UUID.fromString(obj.getString("id")), items(obj))
                        },
                    resume = json.optJSONObject("resume")?.let { section(it, FilmR.string.continue_watching) },
                    nextUp = json.optJSONObject("nextUp")?.let { section(it, FilmR.string.next_up) },
                    views =
                        json.getJSONArray("views").objects().map { obj ->
                            HomeItem.ViewItem(
                                View(
                                    id = UUID.fromString(obj.getString("id")),
                                    name = obj.getString("name"),
                                    items = items(obj),
                                    type = CollectionType.valueOf(obj.getString("type")),
                                )
                            )
                        },
                    rowOrder = json.getJSONArray("rowOrder").strings(),
                )
            } catch (e: Exception) {
                // A file that doesn't parse is a cache miss, not an error.
                Timber.w(e, "Home cache unreadable")
                null
            }
        }

    suspend fun write(serverId: String, userId: UUID, state: HomeState) =
        withContext(Dispatchers.IO) {
            try {
                val json =
                    JSONObject()
                        .put("version", VERSION)
                        .put("rowOrder", JSONArray(state.rowOrder))
                        .put(
                            "views",
                            JSONArray(
                                state.views.map { item ->
                                    JSONObject()
                                        .put("id", item.view.id.toString())
                                        .put("name", item.view.name)
                                        .put("type", item.view.type.name)
                                        .put("items", items(item.view.items))
                                }
                            ),
                        )
                state.suggestionsSection?.let {
                    json.put(
                        "suggestions",
                        JSONObject().put("id", it.id.toString()).put("items", items(it.items)),
                    )
                }
                state.resumeSection?.let { json.put("resume", sectionJson(it)) }
                state.nextUpSection?.let { json.put("nextUp", sectionJson(it)) }
                dir.mkdirs()
                val target = file(serverId, userId)
                val temp = File(dir, target.name + ".tmp")
                temp.writeText(json.toString())
                if (!temp.renameTo(target)) temp.delete()
            } catch (e: Exception) {
                Timber.w(e, "Home cache not written")
            }
        }

    private fun sectionJson(section: HomeItem.Section) =
        JSONObject()
            .put("id", section.homeSection.id.toString())
            .put("items", items(section.homeSection.items))

    private fun section(obj: JSONObject, nameRes: Int): HomeItem.Section =
        HomeItem.Section(
            HomeSection(
                id = UUID.fromString(obj.getString("id")),
                name = UiText.StringResource(nameRes),
                items = items(obj),
            )
        )

    private fun items(list: List<FindroidItem>): JSONArray =
        JSONArray(list.mapNotNull { runCatching { item(it) }.getOrNull() })

    private fun item(item: FindroidItem): JSONObject? {
        val obj =
            JSONObject()
                .put("id", item.id.toString())
                .put("name", item.name)
                .put("overview", item.overview.take(OVERVIEW_MAX))
                .put("played", item.played)
                .put("favorite", item.favorite)
                .put("canPlay", item.canPlay)
                .put("canDownload", item.canDownload)
                .put("runtime", item.runtimeTicks)
                .put("position", item.playbackPositionTicks)
                .put("unplayed", item.unplayedItemCount ?: JSONObject.NULL)
                .put("images", images(item.images))
        when (item) {
            is FindroidMovie ->
                obj.put("kind", "movie").put("year", item.productionYear ?: JSONObject.NULL)
            is FindroidShow ->
                obj.put("kind", "show").put("year", item.productionYear ?: JSONObject.NULL)
            is FindroidEpisode ->
                obj.put("kind", "episode")
                    .put("index", item.indexNumber)
                    .put("indexEnd", item.indexNumberEnd ?: JSONObject.NULL)
                    .put("parentIndex", item.parentIndexNumber)
                    .put("seriesId", item.seriesId.toString())
                    .put("seriesName", item.seriesName)
                    .put("seasonId", item.seasonId.toString())
                    .put("seasonName", item.seasonName ?: JSONObject.NULL)
            else -> return null
        }
        return obj
    }

    private fun items(parent: JSONObject): List<FindroidItem> =
        parent.getJSONArray("items").objects().mapNotNull { runCatching { item(it) }.getOrNull() }

    private fun item(obj: JSONObject): FindroidItem? {
        val id = UUID.fromString(obj.getString("id"))
        val images = images(obj.getJSONObject("images"))
        val unplayed = obj.optIntOrNull("unplayed")
        val year = obj.optIntOrNull("year")
        return when (obj.getString("kind")) {
            "movie" ->
                FindroidMovie(
                    id = id,
                    name = obj.getString("name"),
                    originalTitle = null,
                    overview = obj.getString("overview"),
                    sources = emptyList(),
                    played = obj.getBoolean("played"),
                    favorite = obj.getBoolean("favorite"),
                    canPlay = obj.getBoolean("canPlay"),
                    canDownload = obj.getBoolean("canDownload"),
                    runtimeTicks = obj.getLong("runtime"),
                    playbackPositionTicks = obj.getLong("position"),
                    premiereDate = null,
                    people = emptyList(),
                    genres = emptyList(),
                    communityRating = null,
                    officialRating = null,
                    status = "",
                    productionYear = year,
                    endDate = null,
                    trailer = null,
                    unplayedItemCount = unplayed,
                    images = images,
                    chapters = emptyList(),
                    trickplayInfo = null,
                )
            "show" ->
                FindroidShow(
                    id = id,
                    name = obj.getString("name"),
                    originalTitle = null,
                    overview = obj.getString("overview"),
                    sources = emptyList(),
                    seasons = emptyList(),
                    played = obj.getBoolean("played"),
                    favorite = obj.getBoolean("favorite"),
                    canPlay = obj.getBoolean("canPlay"),
                    canDownload = obj.getBoolean("canDownload"),
                    playbackPositionTicks = obj.getLong("position"),
                    unplayedItemCount = unplayed,
                    genres = emptyList(),
                    people = emptyList(),
                    runtimeTicks = obj.getLong("runtime"),
                    communityRating = null,
                    officialRating = null,
                    status = "",
                    productionYear = year,
                    endDate = null,
                    trailer = null,
                    images = images,
                )
            "episode" ->
                FindroidEpisode(
                    id = id,
                    name = obj.getString("name"),
                    originalTitle = null,
                    overview = obj.getString("overview"),
                    indexNumber = obj.getInt("index"),
                    indexNumberEnd = obj.optIntOrNull("indexEnd"),
                    parentIndexNumber = obj.getInt("parentIndex"),
                    sources = emptyList(),
                    played = obj.getBoolean("played"),
                    favorite = obj.getBoolean("favorite"),
                    canPlay = obj.getBoolean("canPlay"),
                    canDownload = obj.getBoolean("canDownload"),
                    runtimeTicks = obj.getLong("runtime"),
                    playbackPositionTicks = obj.getLong("position"),
                    premiereDate = null,
                    seriesId = UUID.fromString(obj.getString("seriesId")),
                    seriesName = obj.getString("seriesName"),
                    seasonId = UUID.fromString(obj.getString("seasonId")),
                    seasonName = if (obj.isNull("seasonName")) null else obj.getString("seasonName"),
                    communityRating = null,
                    people = emptyList(),
                    unplayedItemCount = unplayed,
                    images = images,
                    chapters = emptyList(),
                    trickplayInfo = null,
                )
            else -> null
        }
    }

    private fun images(images: FindroidImages) =
        JSONObject()
            .put("primary", images.primary?.toString() ?: JSONObject.NULL)
            .put("backdrop", images.backdrop?.toString() ?: JSONObject.NULL)
            .put("logo", images.logo?.toString() ?: JSONObject.NULL)
            .put("showPrimary", images.showPrimary?.toString() ?: JSONObject.NULL)
            .put("showBackdrop", images.showBackdrop?.toString() ?: JSONObject.NULL)
            .put("showLogo", images.showLogo?.toString() ?: JSONObject.NULL)

    private fun images(obj: JSONObject): FindroidImages {
        fun uri(key: String) = if (obj.isNull(key)) null else Uri.parse(obj.getString(key))
        return FindroidImages(
            primary = uri("primary"),
            backdrop = uri("backdrop"),
            logo = uri("logo"),
            showPrimary = uri("showPrimary"),
            showBackdrop = uri("showBackdrop"),
            showLogo = uri("showLogo"),
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private companion object {
        const val VERSION = 1
        const val OVERVIEW_MAX = 400
    }
}
